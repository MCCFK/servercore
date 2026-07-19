package com.apple.servercore.guild;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Guilds implements CommandExecutor, TabCompleter, Listener {

    // ==================== 颜色常量 ====================
    private static final String C1 = "§6";
    private static final String C2 = "§a";
    private static final String C3 = "§c";
    private static final String C4 = "§7";
    private static final String C5 = "§b";
    private static final String C6 = "§e";
    private static final String C7 = "§f";

    // ==================== 配置常量 ====================
    private static final int MAX_DEPUTY = 2;
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 12;
    private static final String NAME_REGEX = "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$";
    private static final long SAVE_INTERVAL = 6000;
    private static final long INVITE_EXPIRE_TIME = 60000;

    private final MainPlugin plugin;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Type listType = new TypeToken<List<Guild>>() {}.getType();

    // ==================== 数据存储 ====================
    private final Map<String, Guild> guilds = new HashMap<>();
    private final Map<UUID, String> playerGuild = new HashMap<>();
    private final Map<UUID, InviteData> invites = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean dirty = false;

    // ==================== UI 类 ====================
    private final Guild_BedrockUI bedrockUI;

    // ==================== 邀请数据结构 ====================
    private static class InviteData {
        String guildName;
        long time;

        InviteData(String guildName, long time) {
            this.guildName = guildName;
            this.time = time;
        }
    }

    public Guilds(MainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "guilds.json");
        this.bedrockUI = new Guild_BedrockUI(plugin, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        load();
        startAutoSave();

        // ========== 注册 PlaceholderAPI ==========
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new GuildPlaceholderExpansion(this).register();
                plugin.getLogger().info("✅ PlaceholderAPI 扩展已注册: %guild%");
            } else {
                plugin.getLogger().warning("⚠️ PlaceholderAPI 未找到，%guild% 占位符将不可用");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("注册 PlaceholderAPI 扩展失败: " + e.getMessage());
        }
    }

    // ==================== 事件监听 ====================
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldown.remove(e.getPlayer().getUniqueId());
        invites.remove(e.getPlayer().getUniqueId());
    }

    // ==================== 数据持久化 ====================
    private void load() {
        lock.writeLock().lock();
        try {
            if (!dataFile.exists()) {
                plugin.getLogger().info("公会数据文件不存在，创建新文件");
                return;
            }
            try (FileReader reader = new FileReader(dataFile)) {
                List<Guild> list = gson.fromJson(reader, listType);
                if (list == null || list.isEmpty()) {
                    plugin.getLogger().info("公会数据为空");
                    return;
                }
                for (Guild g : list) {
                    if (g.name == null || g.name.isEmpty()) continue;
                    if (g.leader == null) continue;
                    if (g.members == null || g.members.isEmpty()) continue;

                    if (g.deputies == null) g.deputies = new HashSet<>();
                    if (g.members == null) g.members = new HashSet<>();
                    if (g.applications == null) g.applications = new HashSet<>();
                    if (g.pointWorld == null) g.pointWorld = "";

                    guilds.put(g.name, g);
                    for (UUID u : g.members) {
                        playerGuild.put(u, g.name);
                    }
                }
                plugin.getLogger().info("✅ 成功加载 " + guilds.size() + " 个公会");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("数据加载失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void loadGuilds() {
        load();
    }

    public void save() {
        if (!dirty) return;
        lock.writeLock().lock();
        try {
            List<Guild> list = new ArrayList<>(guilds.values());
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(list, writer);
                dirty = false;
                plugin.getLogger().info("✅ 公会数据已保存 (" + list.size() + " 个公会)");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("保存失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void forceSave() {
        lock.writeLock().lock();
        try {
            List<Guild> list = new ArrayList<>(guilds.values());
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(list, writer);
                dirty = false;
                plugin.getLogger().info("✅ 公会数据已强制保存 (" + list.size() + " 个公会)");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("强制保存失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, SAVE_INTERVAL, SAVE_INTERVAL);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanExpiredInvites, 100, 100);
    }

    // ==================== 清理过期邀请 ====================
    private void cleanExpiredInvites() {
        long now = System.currentTimeMillis();
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<UUID, InviteData>> it = invites.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, InviteData> entry = it.next();
                if (now - entry.getValue().time > INVITE_EXPIRE_TIME) {
                    it.remove();
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        send(p, C3 + "你的公会邀请已过期（超过60秒）");
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void shutdownSave() {
        plugin.getLogger().info("正在保存公会数据...");
        forceSave();
        plugin.getLogger().info("公会数据保存完成");
    }

    // ==================== 工具方法 ====================
    private boolean isBedrock(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private String name(UUID u) {
        if (u == null) return "未知";
        return Bukkit.getOfflinePlayer(u).getName() != null ? Bukkit.getOfflinePlayer(u).getName() : "未知";
    }

    private boolean validName(String s) {
        return s != null && s.length() >= NAME_MIN && s.length() <= NAME_MAX && s.matches(NAME_REGEX);
    }

    public Guild getGuild(Player p) {
        if (p == null) return null;
        String n = playerGuild.get(p.getUniqueId());
        return n == null ? null : guilds.get(n);
    }

    private Guild getGuild(String name) {
        return guilds.get(name);
    }

    public boolean isLeader(Player p) {
        Guild g = getGuild(p);
        return g != null && g.leader != null && g.leader.equals(p.getUniqueId());
    }

    public boolean isDeputy(Player p) {
        Guild g = getGuild(p);
        return g != null && g.deputies != null && g.deputies.contains(p.getUniqueId());
    }

    public boolean isManager(Player p) {
        return isLeader(p) || isDeputy(p);
    }

    private boolean cooldown(Player p) {
        long now = System.currentTimeMillis();
        if (cooldown.getOrDefault(p.getUniqueId(), 0L) + 1000 > now) {
            p.sendMessage(C3 + "操作过快，请稍后");
            return false;
        }
        cooldown.put(p.getUniqueId(), now);
        return true;
    }

    // ==================== 消息发送系统 ====================
    private void send(Player p, String msg) {
        if (p == null || msg == null) return;
        try {
            p.sendMessage(msg);
        } catch (Exception e) {
            plugin.getLogger().warning("发送纯文本消息失败: " + e.getMessage());
        }
    }

    private void send(Player p, TextComponent... comps) {
        if (p == null || comps == null || comps.length == 0) return;

        if (isBedrock(p)) {
            StringBuilder sb = new StringBuilder();
            for (TextComponent c : comps) {
                if (c != null) sb.append(c.getText());
            }
            String plain = sb.toString();
            if (!plain.isBlank()) {
                p.sendMessage(plain);
            }
            return;
        }

        try {
            p.spigot().sendMessage(comps);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (TextComponent c : comps) {
                if (c != null) sb.append(c.getText());
            }
            p.sendMessage(sb.toString());
            plugin.getLogger().warning("发送组件消息失败，已降级纯文本: " + e.getMessage());
        }
    }

    private void broadcast(Guild g, String msg) {
        if (g == null || msg == null) return;
        for (UUID u : g.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) send(p, msg);
        }
    }

    private void broadcast(Guild g, TextComponent... comps) {
        if (g == null || comps == null || comps.length == 0) return;
        for (UUID u : g.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) send(p, comps);
        }
    }

    // ==================== 点击消息构建器 ====================
    private TextComponent text(String s) {
        return new TextComponent(s);
    }

    private TextComponent click(String text, String color, String cmd, String hover) {
        TextComponent c = new TextComponent(color + text);
        c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        if (hover != null) {
            c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(hover).create()));
        }
        return c;
    }

    private TextComponent suggest(String text, String color, String cmd, String hover) {
        TextComponent c = new TextComponent(color + text);
        c.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd));
        if (hover != null) {
            c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(hover).create()));
        }
        return c;
    }

    // ==================== 核心业务方法 ====================

    // ===== 创建公会 - 所有人可用 =====
    public void create(Player p, String name) {
        if (!validName(name)) {
            send(p, C3 + "名称2-12位中英文数字");
            return;
        }
        if (!cooldown(p)) return;

        lock.writeLock().lock();
        try {
            if (guilds.containsKey(name)) {
                send(p, C3 + "名称已存在");
                return;
            }
            if (playerGuild.containsKey(p.getUniqueId())) {
                send(p, C3 + "你已在公会中");
                return;
            }

            Guild g = new Guild();
            g.name = name;
            g.leader = p.getUniqueId();
            g.members = new HashSet<>();
            g.members.add(p.getUniqueId());
            g.deputies = new HashSet<>();
            g.applications = new HashSet<>();
            g.pointWorld = "";

            guilds.put(name, g);
            playerGuild.put(p.getUniqueId(), name);
            dirty = true;

            plugin.getLogger().info("✅ 公会创建: " + name + "，会长: " + p.getName());
        } finally {
            lock.writeLock().unlock();
        }

        send(p, C2 + "公会 " + name + " 创建成功！");
    }

    // ===== 邀请玩家 - 需要会长或副会长 =====
    public void invite(Player p, Player target) {
        plugin.getLogger().info("=== invite 开始 ===");
        plugin.getLogger().info("邀请人: " + p.getName() + ", 目标: " + target.getName());

        if (!cooldown(p)) return;

        Guild g;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            plugin.getLogger().info("公会: " + (g == null ? "null" : g.name));

            if (g == null) {
                send(p, C3 + "你不在公会中");
                return;
            }
            if (!isManager(p)) {
                send(p, C3 + "需要会长或副会长");
                return;
            }
            if (playerGuild.containsKey(target.getUniqueId())) {
                send(p, C3 + "对方已有公会");
                return;
            }
            if (invites.containsKey(target.getUniqueId())) {
                send(p, C3 + "已邀请过");
                return;
            }

            invites.put(target.getUniqueId(), new InviteData(g.name, System.currentTimeMillis()));
            plugin.getLogger().info("✅ 邀请已存入 invites，大小: " + invites.size());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        plugin.getLogger().info("邀请后 invites 包含目标: " + invites.containsKey(target.getUniqueId()));

        send(p, C2 + "已邀请 " + target.getName() + "，邀请将在60秒后过期");

        try {
            if (isBedrock(target)) {
                bedrockUI.openInviteForm(target, g.name, p.getName());
            } else {
                TextComponent[] inviteMsg = new TextComponent[]{
                        text(C1 + "========== 公会邀请 ==========\n"),
                        text(C1 + "公会: " + C7 + g.name + "\n"),
                        text(C4 + "邀请人: " + C7 + p.getName() + "\n"),
                        text(C4 + "过期时间: " + C3 + "60秒\n\n"),
                        click("[接受]", C2, "/guild acceptinvite " + g.name, C4 + "点击接受邀请"),
                        text(" "),
                        click("[拒绝]", C3, "/guild declineguild " + g.name, C4 + "点击拒绝邀请"),
                        text("\n" + C1 + "================================")
                };
                send(target, inviteMsg);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("发送邀请消息失败: " + e.getMessage());
            send(target, C1 + "你收到公会 " + g.name + " 的邀请，输入 /guild acceptinvite " + g.name + " 接受（60秒内有效）");
        }
    }

    // ===== 接受邀请 =====
    public void acceptInvite(Player p, String guildName) {
        if (!cooldown(p)) return;

        InviteData inviteData;
        lock.writeLock().lock();
        try {
            inviteData = invites.get(p.getUniqueId());
            if (inviteData == null) {
                send(p, C3 + "你没有收到任何公会邀请");
                return;
            }

            long elapsed = System.currentTimeMillis() - inviteData.time;
            if (elapsed > INVITE_EXPIRE_TIME) {
                invites.remove(p.getUniqueId());
                send(p, C3 + "邀请已过期（超过60秒），请重新申请");
                return;
            }

            if (!inviteData.guildName.equalsIgnoreCase(guildName)) {
                send(p, C3 + "你没有收到 " + guildName + " 的邀请");
                long remaining = (inviteData.time + INVITE_EXPIRE_TIME - System.currentTimeMillis()) / 1000;
                send(p, C6 + "你当前收到的邀请来自: " + C7 + inviteData.guildName + C6 + "，剩余 " + remaining + " 秒");
                return;
            }

            Guild g = guilds.get(guildName);
            if (g == null) {
                invites.remove(p.getUniqueId());
                send(p, C3 + "公会已解散");
                return;
            }
            if (playerGuild.containsKey(p.getUniqueId())) {
                invites.remove(p.getUniqueId());
                send(p, C3 + "你已有公会");
                return;
            }

            invites.remove(p.getUniqueId());
            g.members.add(p.getUniqueId());
            playerGuild.put(p.getUniqueId(), guildName);
            dirty = true;

            plugin.getLogger().info("✅ " + p.getName() + " 接受了邀请，加入公会 " + guildName);
        } finally {
            lock.writeLock().unlock();
        }

        Guild g = guilds.get(guildName);
        if (g != null) {
            broadcast(g, C2 + p.getName() + " 加入了公会！");
        }
        send(p, C2 + "成功加入公会 " + guildName + " ！");
    }

    // ===== 拒绝邀请 =====
    public void decline(Player p) {
        lock.writeLock().lock();
        try {
            if (invites.remove(p.getUniqueId()) != null) {
                send(p, C3 + "已拒绝邀请");
            } else {
                send(p, C3 + "没有待拒绝的邀请");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ===== 拒绝指定公会的邀请 =====
    public void declineGuild(Player p, String guildName) {
        lock.writeLock().lock();
        try {
            InviteData invite = invites.get(p.getUniqueId());
            if (invite == null) {
                send(p, C3 + "你没有收到任何公会邀请");
                return;
            }

            if (!invite.guildName.equalsIgnoreCase(guildName)) {
                send(p, C3 + "你没有收到 " + guildName + " 的邀请");
                send(p, C6 + "你当前收到的邀请来自: " + C7 + invite.guildName);
                return;
            }

            invites.remove(p.getUniqueId());
            send(p, C3 + "已拒绝 " + guildName + " 的邀请");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ===== 退出公会 =====
    public void leave(Player p) {
        if (isLeader(p)) {
            send(p, C3 + "会长请使用 /guild disband 解散公会");
            return;
        }

        String guildName;
        lock.writeLock().lock();
        try {
            guildName = playerGuild.remove(p.getUniqueId());
            if (guildName == null) {
                send(p, C3 + "你不在公会中");
                return;
            }

            Guild g = guilds.get(guildName);
            if (g != null) {
                g.members.remove(p.getUniqueId());
                g.deputies.remove(p.getUniqueId());
                if (g.members.isEmpty()) {
                    guilds.remove(guildName);
                }
                dirty = true;
            }
        } finally {
            lock.writeLock().unlock();
        }

        Guild g = guilds.get(guildName);
        if (g != null) {
            broadcast(g, C6 + p.getName() + " 退出了公会");
        }
        send(p, C2 + "已退出公会");
    }

    // ===== 踢出成员 - 需要会长或副会长 =====
    public void kick(Player p, Player target) {
        if (!cooldown(p)) return;

        Guild g = null;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            if (g == null || !isManager(p)) {
                send(p, C3 + "需要会长或副会长");
                return;
            }
            if (g.leader.equals(target.getUniqueId())) {
                send(p, C3 + "不能踢出会长");
                return;
            }
            if (!g.members.contains(target.getUniqueId())) {
                send(p, C3 + "目标不在公会中");
                return;
            }

            g.members.remove(target.getUniqueId());
            g.deputies.remove(target.getUniqueId());
            playerGuild.remove(target.getUniqueId());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null) {
            broadcast(g, C3 + target.getName() + " 被 " + p.getName() + " 踢出公会");
        }
        send(target, C3 + "你被踢出公会");
        send(p, C2 + "已踢出");
    }

    // ===== 设置副会长 - 仅会长 =====
    public void setDeputy(Player p, Player target) {
        if (!cooldown(p)) return;

        Guild g = null;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            if (g == null || !g.leader.equals(p.getUniqueId())) {
                send(p, C3 + "需要会长");
                return;
            }
            if (p.getUniqueId().equals(target.getUniqueId())) {
                send(p, C3 + "不能设置自己为副会长");
                return;
            }
            if (!g.members.contains(target.getUniqueId())) {
                send(p, C3 + "目标不在公会中");
                return;
            }
            if (g.deputies.size() >= MAX_DEPUTY) {
                send(p, C3 + "副会长已达上限 " + MAX_DEPUTY + " 人");
                return;
            }
            if (g.deputies.contains(target.getUniqueId())) {
                send(p, C3 + "已是副会长");
                return;
            }

            g.deputies.add(target.getUniqueId());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null) {
            broadcast(g, C1 + target.getName() + " 被任命为副会长");
        }
        send(p, C2 + "设置成功");
        send(target, C2 + "你被任命为副会长");
    }

    // ===== 撤销副会长 - 仅会长 =====
    public void removeDeputy(Player p, Player target) {
        if (!cooldown(p)) return;

        Guild g = null;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            if (g == null || !g.leader.equals(p.getUniqueId())) {
                send(p, C3 + "需要会长");
                return;
            }
            if (!g.deputies.contains(target.getUniqueId())) {
                send(p, C3 + "目标不是副会长");
                return;
            }

            g.deputies.remove(target.getUniqueId());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null) {
            broadcast(g, C6 + target.getName() + " 被撤销副会长");
        }
        send(p, C2 + "已撤销");
        send(target, C3 + "你被撤销副会长");
    }

    // ===== 解散公会 - 仅会长 =====
    public void disband(Player p) {
        if (!cooldown(p)) return;

        String guildName = null;
        List<UUID> members = new ArrayList<>();
        lock.writeLock().lock();
        try {
            Guild g = getGuild(p);
            if (g == null || !g.leader.equals(p.getUniqueId())) {
                send(p, C3 + "需要会长");
                return;
            }

            guildName = g.name;
            members = new ArrayList<>(g.members);

            for (UUID u : members) {
                playerGuild.remove(u);
                invites.remove(u);
            }
            guilds.remove(guildName);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        for (UUID u : members) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) send(pl, C3 + "公会 " + guildName + " 已被解散");
        }
        send(p, C3 + "公会已解散");
    }

    // ===== 申请加入 =====
    public void apply(Player p, String name) {
        if (!cooldown(p)) return;

        Guild g = null;
        lock.writeLock().lock();
        try {
            g = guilds.get(name);
            if (g == null) {
                send(p, C3 + "公会不存在");
                return;
            }
            if (playerGuild.containsKey(p.getUniqueId())) {
                send(p, C3 + "你已有公会");
                return;
            }
            if (g.applications.contains(p.getUniqueId())) {
                send(p, C3 + "已申请过");
                return;
            }

            g.applications.add(p.getUniqueId());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null) {
            TextComponent notice = text(C6 + p.getName() + " 申请加入公会 ");
            notice.addExtra(click("[同意]", C2, "/guild accept " + p.getName(), C4 + "点击同意"));
            notice.addExtra(text(" "));
            notice.addExtra(click("[拒绝]", C3, "/guild decline " + p.getName(), C4 + "点击拒绝"));

            for (UUID u : g.members) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && (g.leader.equals(u) || g.deputies.contains(u))) {
                    send(pl, notice);
                }
            }
        }
        send(p, C2 + "申请已提交，等待审批");
    }

    // ===== 同意申请 - 需要会长或副会长 =====
    public void acceptApply(Player p, String targetName) {
        if (!cooldown(p)) return;

        Guild g = null;
        UUID target = null;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            if (g == null || !isManager(p)) {
                send(p, C3 + "无权限");
                return;
            }

            for (UUID u : g.applications) {
                if (name(u).equalsIgnoreCase(targetName)) {
                    target = u;
                    break;
                }
            }
            if (target == null) {
                send(p, C3 + "没有该申请");
                return;
            }

            g.applications.remove(target);
            g.members.add(target);
            playerGuild.put(target, g.name);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null && target != null) {
            Player pl = Bukkit.getPlayer(target);
            if (pl != null) send(pl, C2 + "你已加入 " + g.name);
            broadcast(g, C2 + name(target) + " 加入了公会！");
            send(p, C2 + "已同意");
        }
    }

    // ===== 拒绝申请 - 需要会长或副会长 =====
    public void declineApply(Player p, String targetName) {
        if (!cooldown(p)) return;

        Guild g = null;
        UUID target = null;
        lock.writeLock().lock();
        try {
            g = getGuild(p);
            if (g == null || !isManager(p)) {
                send(p, C3 + "无权限");
                return;
            }

            for (UUID u : g.applications) {
                if (name(u).equalsIgnoreCase(targetName)) {
                    target = u;
                    break;
                }
            }
            if (target == null) {
                send(p, C3 + "没有该申请");
                return;
            }

            g.applications.remove(target);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }

        if (g != null && target != null) {
            Player pl = Bukkit.getPlayer(target);
            if (pl != null) send(pl, C3 + "你的申请被拒绝");
            send(p, C2 + "已拒绝");
        }
    }

    // ===== 公会聊天 =====
    public void chat(Player p, String msg) {
        if (msg.trim().isEmpty()) return;

        Guild g = getGuild(p);
        if (g == null) {
            send(p, C3 + "你不在公会中");
            return;
        }

        TextComponent comp = text(C1 + "【公会】");
        comp.addExtra(click(p.getName(), C7, "/guild info " + p.getName(), C4 + "点击查看"));
        comp.addExtra(text(C4 + ":" + msg));

        for (UUID u : g.members) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) send(pl, comp);
        }
    }

    // ==================== 公会传送点 ====================

    // 设置公会传送点（会长专用）- 自动获取玩家当前位置
    public void setGuildPoint(Player p) {
        if (!isLeader(p)) {
            send(p, C3 + "只有会长可以设置传送点");
            return;
        }

        Guild g = getGuild(p);
        if (g == null) {
            send(p, C3 + "你不在公会中");
            return;
        }

        Location loc = p.getLocation();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        String world = p.getWorld().getName();

        lock.writeLock().lock();
        try {
            g.pointX = x;
            g.pointY = y;
            g.pointZ = z;
            g.pointWorld = world;
            dirty = true;
            send(p, C2 + "公会传送点已设置为当前位置: " + C7 + String.format("%.1f", x) + " " + String.format("%.1f", y) + " " + String.format("%.1f", z) + " §7(" + world + ")");
            broadcast(g, C6 + "会长 " + p.getName() + " 设置了公会传送点");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 移除公会传送点（会长专用）
    public void removeGuildPoint(Player p) {
        if (!isLeader(p)) {
            send(p, C3 + "只有会长可以移除传送点");
            return;
        }

        Guild g = getGuild(p);
        if (g == null) {
            send(p, C3 + "你不在公会中");
            return;
        }

        if (g.pointWorld == null || g.pointWorld.isEmpty() || g.pointX == 0) {
            send(p, C3 + "公会尚未设置传送点");
            return;
        }

        lock.writeLock().lock();
        try {
            g.pointX = 0;
            g.pointY = 0;
            g.pointZ = 0;
            g.pointWorld = "";
            dirty = true;
            send(p, C3 + "公会传送点已移除");
            broadcast(g, C6 + "会长 " + p.getName() + " 移除了公会传送点");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 传送到公会传送点（成员专用）
    public void teleportToGuildPoint(Player p) {
        Guild g = getGuild(p);
        if (g == null) {
            send(p, C3 + "你不在公会中");
            return;
        }

        if (g.pointWorld == null || g.pointWorld.isEmpty() || g.pointX == 0) {
            send(p, C3 + "公会尚未设置传送点");
            return;
        }

        if (!isBedrock(p)) {
            if (!cooldown(p)) return;
        }

        try {
            World world = Bukkit.getWorld(g.pointWorld);
            if (world == null) {
                send(p, C3 + "传送点所在世界不存在");
                return;
            }

            Location loc = new Location(world, g.pointX, g.pointY, g.pointZ);
            p.teleport(loc);
            send(p, C2 + "已传送到公会传送点");
        } catch (Exception e) {
            send(p, C3 + "传送失败: " + e.getMessage());
            plugin.getLogger().warning("传送失败: " + e.getMessage());
        }
    }

    // ==================== 查询方法 ====================

    public List<Guild> getAllGuilds() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(guilds.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getPlayerName(UUID uuid) {
        return name(uuid);
    }

    public void list(Player p) {
        lock.readLock().lock();
        try {
            if (guilds.isEmpty()) {
                send(p, C3 + "暂无公会");
                return;
            }

            if (isBedrock(p)) {
                bedrockUI.openGuildListUI(p);
                return;
            }

            send(p, text(C1 + "========== 公会列表 ==========\n"));

            for (Guild g : guilds.values()) {
                TextComponent line = text(C7 + g.name + C4 + " (" + g.members.size() + "人) ");
                line.addExtra(click("[查看]", C2, "/guild info " + g.name, C4 + "点击查看详情"));
                line.addExtra(text(" "));
                line.addExtra(click("[申请]", C5, "/guild apply " + g.name, C4 + "点击申请加入"));
                line.addExtra(text("\n"));
                send(p, line);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void info(Player p, String name) {
        lock.readLock().lock();
        try {
            Guild g = name == null ? getGuild(p) : guilds.get(name);
            if (g == null) {
                send(p, C3 + "公会不存在");
                return;
            }

            if (isBedrock(p)) {
                bedrockUI.openMyGuildInfoUI(p);
                return;
            }

            String leader = name(g.leader);
            StringBuilder deputies = new StringBuilder();
            for (UUID u : g.deputies) {
                deputies.append(C7).append(name(u)).append(C4).append(" ");
            }

            send(p,
                    text(C1 + "========== " + g.name + " ==========\n"),
                    text(C7 + "会长: " + C1 + leader + "\n"),
                    text(C7 + "副会长: " + (deputies.length() > 0 ? deputies.toString() : C4 + "无") + "\n"),
                    text(C7 + "成员: " + C4 + g.members.size() + "人\n\n"),
                    text(C7 + "成员列表:\n")
            );

            int i = 0;
            for (UUID u : g.members) {
                if (i++ >= 20) {
                    send(p, text(C4 + "... 还有 " + (g.members.size() - 20) + " 人"));
                    break;
                }
                send(p, text(C4 + "  ▪ " + C7 + name(u)));
            }

            if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                send(p, text(C6 + "传送点: §7" + String.format("%.1f", g.pointX) + " " + String.format("%.1f", g.pointY) + " " + String.format("%.1f", g.pointZ) + " §7(" + g.pointWorld + ")"));
            }

            TextComponent actions = text("\n" + C4 + "[");
            if (!playerGuild.containsKey(p.getUniqueId())) {
                actions.addExtra(click("申请加入", C2, "/guild apply " + g.name, C4 + "点击申请"));
            } else if (getGuild(p) != null && getGuild(p).name.equals(g.name)) {
                if (isLeader(p)) {
                    actions.addExtra(click("解散公会", C3, "/guild disband", C4 + "点击解散"));
                    actions.addExtra(text(" "));
                    actions.addExtra(click("设置传送点", C5, "/guild cpoint", C4 + "点击设置当前位置为传送点"));
                    if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                        actions.addExtra(text(" "));
                        actions.addExtra(click("移除传送点", C3, "/guild dpoint", C4 + "点击移除公会传送点"));
                    }
                } else {
                    actions.addExtra(click("退出公会", C3, "/guild leave", C4 + "点击退出"));
                    if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                        actions.addExtra(text(" "));
                        actions.addExtra(click("传送至公会", C5, "/guild jpoint", C4 + "点击传送"));
                    }
                }
            }
            actions.addExtra(text(C4 + "]"));
            send(p, actions);

            send(p, text(C1 + "================================"));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void applyList(Player p) {
        Guild g = getGuild(p);
        if (g == null || !isManager(p)) {
            send(p, C3 + "无权限");
            return;
        }

        lock.readLock().lock();
        try {
            if (g.applications.isEmpty()) {
                send(p, C3 + "暂无申请");
                return;
            }

            if (isBedrock(p)) {
                bedrockUI.openApplyListUI(p);
                return;
            }

            send(p, text(C1 + "========== 申请列表 =========="));
            for (UUID u : g.applications) {
                TextComponent line = text(C7 + name(u) + " ");
                line.addExtra(click("[同意]", C2, "/guild accept " + name(u), C4 + "点击同意"));
                line.addExtra(text(" "));
                line.addExtra(click("[拒绝]", C3, "/guild decline " + name(u), C4 + "点击拒绝"));
                send(p, line);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    // ===== 帮助菜单 =====
    public void help(Player p) {
        if (isBedrock(p)) {
            bedrockUI.openMainMenu(p);
            return;
        }

        send(p,
                text(C1 + "========== 公会帮助 ==========\n"),
                suggest("创建公会", C2, "/guild create ", C4 + "/guild create <名称>"),
                text(C4 + " - 创建公会\n"),
                suggest("申请加入", C2, "/guild apply ", C4 + "/guild apply <公会名>"),
                text(C4 + " - 申请加入公会\n"),
                suggest("邀请玩家", C2, "/guild invite ", C4 + "/guild invite <玩家>"),
                text(C4 + " - 邀请玩家加入\n"),
                suggest("接受邀请", C2, "/guild acceptinvite ", C4 + "/guild acceptinvite <公会名>"),
                text(C4 + " - 接受公会邀请\n"),
                suggest("拒绝邀请", C3, "/guild declineguild ", C4 + "/guild declineguild <公会名>"),
                text(C4 + " - 拒绝指定公会邀请\n"),
                click("拒绝邀请(无参数)", C3, "/guild decline", C4 + "点击拒绝当前邀请"),
                text(C4 + " - 拒绝当前收到的邀请\n"),
                click("退出公会", C3, "/guild leave", C4 + "点击退出公会"),
                text(C4 + " - 退出当前公会\n"),
                click("公会列表", C5, "/guild list", C4 + "查看所有公会"),
                text(C4 + " - 查看公会列表\n"),
                click("公会信息", C5, "/guild info", C4 + "查看当前公会"),
                text(C4 + " - 查看公会信息\n"),
                suggest("公会聊天", C5, "/guild chat ", C4 + "/guild chat <消息>"),
                text(C4 + " - 公会频道聊天"),
                click("设置传送点", C5, "/guild cpoint", C4 + "点击设置当前位置为公会传送点"),
                text(C4 + " - 设置当前位置为公会传送点\n"),
                click("移除传送点", C3, "/guild dpoint", C4 + "点击移除公会传送点"),
                text(C4 + " - 移除公会传送点\n"),
                click("传送至公会", C5, "/guild jpoint", C4 + "点击传送到公会传送点"),
                text(C4 + " - 传送到公会传送点\n")
        );

        Guild g = getGuild(p);
        if (g != null && isManager(p)) {
            send(p, text("\n" + C1 + "========== 管理帮助 =========="));
            if (isLeader(p)) {
                send(p,
                        suggest("设置副会长", C2, "/guild setdeputy ", C4 + "/guild setdeputy <玩家>"),
                        suggest("撤销副会长", C3, "/guild removedeputy ", C4 + "/guild removedeputy <玩家>"),
                        click("解散公会", C3, "/guild disband", C4 + "点击解散公会"),
                        click("设置传送点", C5, "/guild cpoint", C4 + "点击设置当前位置为传送点"),
                        click("移除传送点", C3, "/guild dpoint", C4 + "点击移除公会传送点")
                );
            }
            send(p,
                    suggest("踢出成员", C3, "/guild kick ", C4 + "/guild kick <玩家>"),
                    click("申请列表", C5, "/guild applylist", C4 + "查看申请"),
                    suggest("同意申请", C2, "/guild accept ", C4 + "/guild accept <玩家>"),
                    suggest("拒绝申请", C3, "/guild decline ", C4 + "/guild decline <玩家>")
            );
        }
    }

    // ==================== 基岩表单（已迁移到 Guild_BedrockUI） ====================
    private void sendInviteForm(Player p, String guildName, String inviterName) {
        bedrockUI.openInviteForm(p, guildName, inviterName);
    }

    private void sendGuildListForm(Player p) {
        bedrockUI.openGuildListUI(p);
    }

    private void sendInfoForm(Player p, Guild g) {
        bedrockUI.openMyGuildInfoUI(p);
    }

    private void sendApplyListForm(Player p, Guild g) {
        bedrockUI.openApplyListUI(p);
    }

    public void openMainMenu(Player p) {
        if (isBedrock(p)) {
            bedrockUI.openMainMenu(p);
        } else {
            help(p);
        }
    }

    // ==================== Tab补全 ====================
    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String[] cmds = {"create", "apply", "invite", "accept", "acceptinvite", "decline", "declineguild", "leave",
                    "kick", "setdeputy", "removedeputy", "chat", "list", "info", "applylist", "help", "cpoint", "dpoint", "jpoint", "disband"};
            for (String c : cmds) {
                if (c.startsWith(args[0].toLowerCase())) list.add(c);
            }
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("setdeputy") || sub.equals("removedeputy") ||
                    sub.equals("accept") || sub.equals("decline")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        list.add(p.getName());
                    }
                }
            }
            if ((sub.equals("acceptinvite") || sub.equals("declineguild")) && sender instanceof Player player) {
                InviteData invite = invites.get(player.getUniqueId());
                if (invite != null && invite.guildName.toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(invite.guildName);
                }
            }
            if (sub.equals("apply") || sub.equals("info")) {
                lock.readLock().lock();
                try {
                    for (String n : guilds.keySet()) {
                        if (n.toLowerCase().startsWith(args[1].toLowerCase())) list.add(n);
                    }
                } finally {
                    lock.readLock().unlock();
                }
            }
        }
        return list;
    }

    // ==================== 指令入口 ====================
    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C3 + "仅玩家可用");
            return true;
        }

        if (args.length == 0) {
            help(p);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "create" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild create <名称>");
                        return true;
                    }
                    create(p, args[1]);
                }
                case "invite" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild invite <玩家>");
                        return true;
                    }
                    Player t = Bukkit.getPlayer(args[1]);
                    if (t == null) {
                        send(p, C3 + "玩家不在线");
                        return true;
                    }
                    invite(p, t);
                }
                case "acceptinvite" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild acceptinvite <公会名>");
                        InviteData invite = invites.get(p.getUniqueId());
                        if (invite != null) {
                            long remaining = (invite.time + INVITE_EXPIRE_TIME - System.currentTimeMillis()) / 1000;
                            if (remaining > 0) {
                                send(p, C6 + "你当前收到的邀请来自: " + C7 + invite.guildName);
                                send(p, C6 + "剩余时间: " + C7 + remaining + C6 + " 秒");
                                send(p, C6 + "输入: " + C7 + "/guild acceptinvite " + invite.guildName + C6 + " 接受");
                            } else {
                                invites.remove(p.getUniqueId());
                                send(p, C3 + "邀请已过期");
                            }
                        } else {
                            send(p, C4 + "你没有收到任何邀请");
                        }
                        return true;
                    }
                    acceptInvite(p, args[1]);
                }
                case "accept" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild accept <玩家> - 同意玩家申请");
                        return true;
                    }
                    acceptApply(p, args[1]);
                }
                case "decline" -> {
                    if (args.length > 1) {
                        declineApply(p, args[1]);
                    } else {
                        decline(p);
                    }
                }
                case "declineguild" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild declineguild <公会名>");
                        InviteData invite = invites.get(p.getUniqueId());
                        if (invite != null) {
                            long remaining = (invite.time + INVITE_EXPIRE_TIME - System.currentTimeMillis()) / 1000;
                            if (remaining > 0) {
                                send(p, C6 + "你当前收到的邀请来自: " + C7 + invite.guildName);
                                send(p, C6 + "剩余时间: " + C7 + remaining + C6 + " 秒");
                                send(p, C6 + "输入: " + C7 + "/guild declineguild " + invite.guildName + C6 + " 拒绝");
                            } else {
                                invites.remove(p.getUniqueId());
                                send(p, C3 + "邀请已过期");
                            }
                        } else {
                            send(p, C4 + "你没有收到任何邀请");
                        }
                        return true;
                    }
                    declineGuild(p, args[1]);
                }
                case "leave" -> leave(p);
                case "kick" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild kick <玩家>");
                        return true;
                    }
                    Player t = Bukkit.getPlayer(args[1]);
                    if (t == null) {
                        send(p, C3 + "玩家不在线");
                        return true;
                    }
                    kick(p, t);
                }
                case "setdeputy" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild setdeputy <玩家>");
                        return true;
                    }
                    Player t = Bukkit.getPlayer(args[1]);
                    if (t == null) {
                        send(p, C3 + "玩家不在线");
                        return true;
                    }
                    setDeputy(p, t);
                }
                case "removedeputy" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild removedeputy <玩家>");
                        return true;
                    }
                    Player t = Bukkit.getPlayer(args[1]);
                    if (t == null) {
                        send(p, C3 + "玩家不在线");
                        return true;
                    }
                    removeDeputy(p, t);
                }
                case "disband" -> disband(p);
                case "chat" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild chat <消息>");
                        return true;
                    }
                    chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
                case "list" -> list(p);
                case "info" -> {
                    if (args.length > 1) info(p, args[1]);
                    else info(p, null);
                }
                case "apply" -> {
                    if (args.length < 2) {
                        send(p, C3 + "/guild apply <公会名>");
                        return true;
                    }
                    apply(p, args[1]);
                }
                case "applylist" -> applyList(p);
                case "help" -> help(p);
                case "cpoint" -> setGuildPoint(p);
                case "dpoint" -> removeGuildPoint(p);
                case "jpoint" -> teleportToGuildPoint(p);
                default -> send(p, C3 + "未知命令，输入 /guild help 查看帮助");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("指令执行异常: " + e.getMessage());
            send(p, C3 + "指令执行异常，请查看后台日志");
            e.printStackTrace();
        }
        return true;
    }

    // ==================== 实体类 ====================
    public static class Guild {
        public String name;
        public UUID leader;
        public Set<UUID> deputies = new HashSet<>();
        public Set<UUID> members = new HashSet<>();
        public Set<UUID> applications = new HashSet<>();

        // ===== 公会传送点 =====
        public double pointX;
        public double pointY;
        public double pointZ;
        public String pointWorld = "";
    }

    // ==================== PlaceholderAPI 扩展 ====================
    public class GuildPlaceholderExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private final Guilds guilds;

        public GuildPlaceholderExpansion(Guilds guilds) {
            this.guilds = guilds;
        }

        @Override
        public String getIdentifier() {
            return "guild";
        }

        @Override
        public String getAuthor() {
            return "ServerCore";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return "";
            }

            // %guild% - 返回玩家所在公会名称，无则显示"无业游民"
            if (identifier == null || identifier.isEmpty()) {
                Guild g = guilds.getGuild(player);
                if (g == null) {
                    return "无业游民";
                }
                return g.name;
            }

            // %guild_name% - 返回玩家所在公会名称（别名）
            if (identifier.equalsIgnoreCase("name")) {
                Guild g = guilds.getGuild(player);
                if (g == null) {
                    return "无业游民";
                }
                return g.name;
            }

            // %guild_leader% - 返回公会会长名称
            if (identifier.equalsIgnoreCase("leader")) {
                Guild g = guilds.getGuild(player);
                if (g == null || g.leader == null) {
                    return "无公会";
                }
                return guilds.name(g.leader);
            }

            // %guild_members% - 返回公会成员数量，无则显示0
            if (identifier.equalsIgnoreCase("members")) {
                Guild g = guilds.getGuild(player);
                if (g == null) {
                    return "0";
                }
                return String.valueOf(g.members.size());
            }

            // %guild_has% - 返回玩家是否有公会 (true/false)
            if (identifier.equalsIgnoreCase("has")) {
                Guild g = guilds.getGuild(player);
                return g != null ? "true" : "false";
            }

            return null;
        }
    }
}