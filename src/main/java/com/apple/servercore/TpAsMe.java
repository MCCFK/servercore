package com.apple.servercore;

import com.apple.servercore.guild.Guilds;
import com.mccfk.plugin.commands.PublicWaypointCommand;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class TpAsMe implements CommandExecutor, Listener, TabCompleter {

    private final MainPlugin plugin;  // 改为 MainPlugin 类型
    private final Guilds guildManager;
    private final Set<Inventory> ourGuis = new HashSet<>();

    // 请求存储：目标玩家UUID -> 发起者UUID
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, UUID> tphereRequests = new HashMap<>();

    // 请求时间戳：目标玩家UUID -> 请求发起时间（毫秒）
    private final Map<UUID, Long> requestTime = new HashMap<>();

    // 冷却系统
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private static final int CD = 10;

    // 请求超时时间（秒）
    private static final int REQUEST_TIMEOUT = 120;

    // 屏蔽系统
    private final Map<UUID, Long> globalBlock = new HashMap<>();
    private final Map<UUID, Set<UUID>> playerBlock = new HashMap<>();
    private final Map<UUID, Set<UUID>> blockedBy = new HashMap<>();

    private final Map<UUID, Integer> pageCache = new HashMap<>();

    // 公共路径点
    private PublicWaypointCommand publicWaypointCommand;
    // 正在创建路径点的玩家：UUID → (选中的分类)
    public final Map<UUID, String> pendingPwCreate = new HashMap<>();
    // 正在输入描述的玩家：UUID → 路径点名
    public final Map<UUID, String> pendingPwDesc = new HashMap<>();
    // 创建中设置图标的玩家：UUID → 路径点名
    public final Map<UUID, String> pendingCreationIcon = new HashMap<>();
    // 待确认删除的玩家：UUID → (路径点名, 过期时间戳)
    public final Map<UUID, Map.Entry<String, Long>> pendingDeleteConfirm = new HashMap<>();

    // 公共路径点分类页面缓存：UUID → (分类, 页码)
    private final Map<UUID, Map.Entry<String, Integer>> pwPageCache = new HashMap<>();

    // 定时任务
    private BukkitTask cleanupTask;

    public TpAsMe(MainPlugin plugin, Guilds guildManager) {  // 改为 MainPlugin 类型
        this.plugin = plugin;
        this.guildManager = guildManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 启动请求清理任务
        startCleanupTask();
    }

    // ========== 启动清理任务 ==========
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredRequests();
            }
        }.runTaskTimer(plugin, 20L, 100L);
    }

    // ========== 清理过期请求 ==========
    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        long timeoutMillis = REQUEST_TIMEOUT * 1000L;
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : tpaRequests.entrySet()) {
            UUID targetId = entry.getKey();
            Long time = requestTime.get(targetId);
            if (time != null && now - time > timeoutMillis) {
                toRemove.add(targetId);
                notifyExpired(entry.getValue(), targetId, "传送");
            }
        }

        for (Map.Entry<UUID, UUID> entry : tphereRequests.entrySet()) {
            UUID targetId = entry.getKey();
            Long time = requestTime.get(targetId);
            if (time != null && now - time > timeoutMillis) {
                if (!toRemove.contains(targetId)) {
                    toRemove.add(targetId);
                }
                notifyExpired(entry.getValue(), targetId, "邀请传送");
            }
        }

        for (UUID uuid : toRemove) {
            tpaRequests.remove(uuid);
            tphereRequests.remove(uuid);
            requestTime.remove(uuid);
        }
    }

    private void notifyExpired(UUID fromId, UUID toId, String type) {
        Player from = Bukkit.getPlayer(fromId);
        Player to = Bukkit.getPlayer(toId);
        if (from != null && from.isOnline()) {
            from.sendMessage("§c你的" + type + "请求已超时（" + REQUEST_TIMEOUT + "秒）！");
            from.playSound(from.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
        if (to != null && to.isOnline()) {
            to.sendMessage("§c" + type + "请求已超时（" + REQUEST_TIMEOUT + "秒）！");
            to.playSound(to.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            if (to.getOpenInventory().getTitle().equals("§e传送请求")) {
                to.closeInventory();
            }
        }
    }

    // ========== 停止清理任务 ==========
    public void stopCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可使用！");
            return true;
        }

        String cmdName = cmd.getName().toLowerCase();

        switch (cmdName) {
            case "actp":
                openMainUI(p);
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return true;

            case "tpa":
                if (args.length < 1) {
                    p.sendMessage("§c用法: /tpa <玩家名>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    p.sendMessage("§c玩家 " + args[0] + " 不在线或不存在！");
                    return true;
                }
                if (target.equals(p)) {
                    p.sendMessage("§c你不能传送给自己！");
                    return true;
                }
                sendRequest(p, target, "TPA");
                return true;

            case "tphere":
                if (args.length < 1) {
                    p.sendMessage("§c用法: /tphere <玩家名>");
                    return true;
                }
                Player targetHere = Bukkit.getPlayerExact(args[0]);
                if (targetHere == null) {
                    p.sendMessage("§c玩家 " + args[0] + " 不在线或不存在！");
                    return true;
                }
                if (targetHere.equals(p)) {
                    p.sendMessage("§c你不能邀请自己！");
                    return true;
                }
                sendRequest(p, targetHere, "TPHERE");
                return true;

            case "tpaaccept":
                return handleTpaResponse(p, args, true);

            case "tpadeny":
                return handleTpaResponse(p, args, false);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();

        String cmdName = cmd.getName().toLowerCase();

        if (args.length == 1) {
            switch (cmdName) {
                case "tpa":
                case "tphere":
                    String partial = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(pl -> !pl.equals(p))
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());

                case "tpaaccept":
                case "tpadeny":
                    // 返回请求者名字
                    UUID requesterId = tpaRequests.getOrDefault(p.getUniqueId(), tphereRequests.get(p.getUniqueId()));
                    if (requesterId != null) {
                        Player requester = Bukkit.getPlayer(requesterId);
                        if (requester != null) {
                            String partialName = args[0].toLowerCase();
                            if (requester.getName().toLowerCase().startsWith(partialName)) {
                                return Collections.singletonList(requester.getName());
                            }
                        }
                    }
                    return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    // ========== 处理基岩版点击响应 ==========
    private boolean handleTpaResponse(Player p, String[] args, boolean accept) {
        String targetName = args.length > 0 ? args[0] : null;

        // 查找是谁向当前玩家发起了请求
        UUID requesterId = tpaRequests.get(p.getUniqueId());

        if (requesterId == null) {
            requesterId = tphereRequests.get(p.getUniqueId());
        }

        if (requesterId == null) {
            p.sendMessage("§c没有待处理的传送请求！");
            return true;
        }

        // 检查请求是否已过期
        Long time = requestTime.get(p.getUniqueId());
        if (time == null || System.currentTimeMillis() - time > REQUEST_TIMEOUT * 1000L) {
            p.sendMessage("§c传送请求已超时（" + REQUEST_TIMEOUT + "秒）！");
            clearRequest(p);
            return true;
        }

        Player from = Bukkit.getPlayer(requesterId);
        if (from == null) {
            p.sendMessage("§c请求已过期！");
            clearRequest(p);
            return true;
        }

        // 如果指定了玩家名，验证是否匹配
        if (targetName != null && !from.getName().equalsIgnoreCase(targetName)) {
            p.sendMessage("§c传送请求不匹配！");
            return true;
        }

        boolean isTpa = tpaRequests.containsKey(p.getUniqueId());
        clearRequest(p);

        if (accept) {
            if (isTpa) {
                from.teleport(p);
                from.sendMessage("§a传送成功！");
                from.playSound(from.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } else {
                p.teleport(from);
                p.sendMessage("§a传送成功！");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else {
            from.sendMessage("§c" + p.getName() + " 拒绝了你的传送请求！");
            from.playSound(from.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
        return true;
    }

    // ========== 判断是否为基岩版玩家 ==========
    private boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ========== GUI事件处理 ==========
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;

        Inventory topInv = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        String title = event.getView().getTitle();

        boolean isPluginGui = (topInv.getHolder() == null);
        if (isPluginGui && isOurGuiTitle(title) && clickedInv == topInv) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            // 我的地标 - 右键管理
            if (title.equals("§3我的地标") && event.isRightClick() && item != null && item.hasItemMeta()) {
                String itemName = item.getItemMeta().getDisplayName();
                if (!itemName.equals("§c返回")) {
                    String wpName = itemName.replace("§a", "").replace("§b", "").trim();
                    Map<String, Object> data = publicWaypointCommand != null ? publicWaypointCommand.loadWaypointData(wpName) : null;
                    if (data != null) {
                        runLater(() -> openWaypointManageUI(p, wpName, data));
                        return;
                    }
                }
            }
            handleClick(p, item);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory topInv = event.getView().getTopInventory();
        String title = event.getView().getTitle();

        boolean isPluginGui = (topInv.getHolder() == null);
        if (isPluginGui && isOurGuiTitle(title)) {
            event.setCancelled(true);
        }
    }

    public boolean isOurGuiTitle(String title) {
        return title.equals("§e传送菜单")
                || title.equals("§6传送设置")
                || title.startsWith("§e选择玩家")
                || title.equals("§e传送请求")
                || title.equals("§5公共路径点")
                || title.startsWith("§5公共路径点 - ")
                || title.equals("§e选择创建分类")
                || title.equals("§3我的地标")
                || title.startsWith("§3管理地标 - ")
                || title.startsWith("§3访问记录 - ");
    }

    private Inventory createGui(int size, String title) {
        Inventory inv = Bukkit.createInventory(null, size, title);
        ourGuis.add(inv);
        return inv;
    }

    public void removeGui(Inventory inv) {
        ourGuis.remove(inv);
    }

    public void setPublicWaypointCommand(PublicWaypointCommand cmd) {
        this.publicWaypointCommand = cmd;
    }

    public void openMainUI(Player p) {
        Inventory inv = createGui(9, "§e传送菜单");
        setItem(inv, 2, Material.ENDER_PEARL, "§a传送到玩家", "§7点击向他人发送传送请求");
        setItem(inv, 4, Material.ENDER_EYE, "§a邀请玩家传送", "§7让对方传送到你身边");
        setItem(inv, 6, Material.COMPASS, "§6传送设置", "§7屏蔽/管理传送请求");
        setItem(inv, 8, Material.WHITE_BANNER, "§5公共路径点", "§7查看所有玩家创建的公共路径点");
        p.openInventory(inv);
    }

    public void openPlayerList(Player p, String type, int page) {
        List<Player> online = Bukkit.getOnlinePlayers().stream()
                .filter(pl -> !pl.equals(p))
                .collect(Collectors.toList());
        int totalPage = Math.max(1, (online.size() + 21) / 22);
        Inventory inv = createGui(27, "§e选择玩家 - " + type + " 第" + page + "页");

        int start = (page - 1) * 22;
        int slot = 0;
        for (int i = start; i < start + 22 && i < online.size(); i++) {
            Player target = online.get(i);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName("§f" + target.getName());
            List<String> lore = new ArrayList<>();
            if (type.equals("BLOCK") && isBlockedByMe(p, target)) {
                lore.add("§7✅ 已屏蔽");
            } else if (type.equals("UNBLOCK") && isBlockedByMe(p, target)) {
                lore.add("§7❌ 可取消屏蔽");
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }

        if (page > 1) setItem(inv, 24, Material.ARROW, "§e← 上一页", "§7返回第" + (page - 1) + "页");
        if (page < totalPage) setItem(inv, 25, Material.ARROW, "§e下一页 →", "§7前往第" + (page + 1) + "页");
        setItem(inv, 26, Material.BARRIER, "§c返回菜单", "");

        p.openInventory(inv);
        pageCache.put(p.getUniqueId(), page);
    }

    // ========== 传送请求UI（支持Java版GUI + 聊天点击 + 基岩版表单） ==========
    public void openRequestUI(Player to, Player from, String type) {
        // 记录请求时间
        requestTime.put(to.getUniqueId(), System.currentTimeMillis());

        String requestType = type.equals("TPA") ? "传送到你" : "邀请你传送";
        String displayName = from.getName();

        // ========== 1. 发送可点击的聊天消息（所有版本） ==========
        sendClickableChatMessage(to, from, type);

        // ========== 2. 根据客户端类型和设置显示不同UI ==========
        if (isBedrockPlayer(to)) {
            // 基岩版：只发送表单UI
            sendBedrockFormUI(to, from, type);
        } else {
            // Java版：检查是否开启GUI
            boolean guiEnabled = plugin.getPersonalSettings().isTpRequestGuiEnabled(to.getUniqueId());

            if (guiEnabled) {
                // 打开GUI界面
                Inventory inv = createGui(9, "§e传送请求");
                if (type.equals("TPA")) {
                    setItem(inv, 3, Material.LIME_CONCRETE, "§a接受传送", "§7允许 " + from.getName() + " 传送到你");
                } else {
                    setItem(inv, 3, Material.LIME_CONCRETE, "§a接受邀请", "§7传送到 " + from.getName() + " 身边");
                }
                setItem(inv, 5, Material.RED_CONCRETE, "§c拒绝请求", "");
                to.openInventory(inv);
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f, 1.8f);
                to.sendMessage("§7💡 你也可以点击聊天框中的 [接受] 或 [拒绝] 按钮！");
            } else {
                // 不打开GUI，只通过聊天消息处理
                to.sendMessage("§7💡 点击聊天框中的 [接受] 或 [拒绝] 按钮处理请求！");
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f, 1.8f);
            }
        }
    }

    // ========== Java版：发送可点击的聊天消息（使用BungeeCord API） ==========
    private void sendClickableChatMessage(Player to, Player from, String type) {
        String requestType = type.equals("TPA") ? "传送" : "邀请传送";
        String displayName = from.getName();

        // 发送分隔线
        to.sendMessage("§6§l════════════════════════════");

        // 发送请求信息
        to.sendMessage("§e" + displayName + " §f向您发起了" + requestType + "请求！");
        to.sendMessage("§7请求将在 §e" + REQUEST_TIMEOUT + "秒 §7后自动过期");

        // 创建可点击的"接受"按钮
        TextComponent acceptMsg = new TextComponent("§a[✅ 接受]");
        acceptMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaaccept " + from.getName()));
        acceptMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§a点击接受" + requestType + "请求")));

        // 创建可点击的"拒绝"按钮
        TextComponent denyMsg = new TextComponent("§c[❌ 拒绝]");
        denyMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny " + from.getName()));
        denyMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§c点击拒绝" + requestType + "请求")));

        // 组合并发送
        TextComponent message = new TextComponent("");
        message.addExtra(acceptMsg);
        message.addExtra("   ");
        message.addExtra(denyMsg);

        to.spigot().sendMessage(message);

        // 发送分隔线
        to.sendMessage("§6§l════════════════════════════");

        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f, 1.8f);
    }

    // ========== 基岩版：发送表单UI ==========
    private void sendBedrockFormUI(Player to, Player from, String type) {
        String requestType = type.equals("TPA") ? "传送到你" : "邀请你传送";
        String displayName = from.getName();

        // 基岩版使用简洁的文本提示（因为基岩版不支持点击文本，所以告诉玩家使用命令）
        to.sendMessage("§6§l════════════════════════════");
        to.sendMessage("§e" + displayName + " §f向您发起了" + requestType + "请求！");
        to.sendMessage("§7请求将在 §e" + REQUEST_TIMEOUT + "秒 §7后自动过期");
        to.sendMessage("§a接受: /tpaaccept " + from.getName());
        to.sendMessage("§c拒绝: /tpadeny " + from.getName());
        to.sendMessage("§6§l════════════════════════════");

        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f, 1.8f);
    }

    public void openSettingUI(Player p) {
        Inventory inv = createGui(18, "§6传送设置");
        setItem(inv, 0, Material.RED_CONCRETE, "§c屏蔽 30 分钟", "§7期间不接收任何传送请求");
        setItem(inv, 1, Material.RED_CONCRETE, "§c屏蔽 1 小时", "§7期间不接收任何传送请求");
        setItem(inv, 2, Material.RED_CONCRETE, "§c屏蔽 1.5 小时", "§7期间不接收任何传送请求");
        setItem(inv, 3, Material.RED_CONCRETE, "§c永久屏蔽", "§7永远不接收任何传送请求");
        setItem(inv, 5, Material.LIME_CONCRETE, "§a恢复默认", "§7清除所有屏蔽设置");
        setItem(inv, 7, Material.PLAYER_HEAD, "§e屏蔽指定玩家", "§7选择你要屏蔽的玩家");
        setItem(inv, 8, Material.PLAYER_HEAD, "§a取消屏蔽玩家", "§7选择你要取消屏蔽的玩家");
        p.openInventory(inv);
    }

    public void handleClick(Player p, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        String name = item.getItemMeta().getDisplayName();
        String title = p.getOpenInventory().getTitle();

        if (title.equals("§e传送菜单")) {
            if (name.equals("§a传送到玩家")) {
                runLater(() -> openPlayerList(p, "TPA", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            } else if (name.equals("§a邀请玩家传送")) {
                runLater(() -> openPlayerList(p, "TPHERE", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            } else if (name.equals("§6传送设置")) {
                runLater(() -> openSettingUI(p));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            } else if (name.equals("§5公共路径点")) {
                runLater(() -> openPublicWaypointUI(p));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            }
            return;
        }

        if (title.startsWith("§e选择玩家")) {
            String[] titleParts = title.split(" - ");
            if (titleParts.length < 2) return;
            String type = titleParts[1].split(" ")[0];
            int page = pageCache.getOrDefault(p.getUniqueId(), 1);

            if (name.equals("§e← 上一页")) {
                runLater(() -> openPlayerList(p, type, page - 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.equals("§e下一页 →")) {
                runLater(() -> openPlayerList(p, type, page + 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.equals("§c返回菜单")) {
                if (type.equals("BLOCK") || type.equals("UNBLOCK")) {
                    runLater(() -> openSettingUI(p));
                } else {
                    runLater(() -> openMainUI(p));
                }
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }

            if (item.getType() == Material.PLAYER_HEAD) {
                String tName = name.replace("§f", "").trim();
                Player target = Bukkit.getPlayerExact(tName);
                if (target == null) {
                    p.sendMessage("§c玩家已离线！");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    runLater(p::closeInventory);
                    return;
                }

                switch (type) {
                    case "BLOCK" -> {
                        if (!isBlockedByMe(p, target)) {
                            blockPlayer(p, target);
                            p.sendMessage("§a已屏蔽玩家: " + target.getName());
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.8f);
                        }
                    }
                    case "UNBLOCK" -> {
                        if (isBlockedByMe(p, target)) {
                            unblockPlayer(p, target);
                            p.sendMessage("§a已取消屏蔽玩家: " + target.getName());
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                        }
                    }
                    case "TPA", "TPHERE" -> {
                        sendRequest(p, target, type);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                }
                runLater(p::closeInventory);
            }
            return;
        }

        if (title.equals("§e传送请求")) {
            // 检查请求是否已过期
            Long time = requestTime.get(p.getUniqueId());
            if (time == null || System.currentTimeMillis() - time > REQUEST_TIMEOUT * 1000L) {
                p.sendMessage("§c传送请求已超时（" + REQUEST_TIMEOUT + "秒）！");
                clearRequest(p);
                runLater(p::closeInventory);
                return;
            }

            Player from = getRequester(p);
            if (from == null) {
                p.sendMessage("§c请求已过期！");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                runLater(p::closeInventory);
                return;
            }

            boolean isTpa = tpaRequests.containsKey(p.getUniqueId());
            clearRequest(p);

            if (name.equals("§a接受传送") || name.equals("§a接受邀请")) {
                if (isTpa) {
                    from.teleport(p);
                    from.sendMessage("§a传送成功！");
                    from.playSound(from.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                } else {
                    p.teleport(from);
                    p.sendMessage("§a传送成功！");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                runLater(p::closeInventory);
                return;
            } else {
                from.sendMessage("§c" + p.getName() + " 拒绝了你的传送请求！");
                from.playSound(from.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            runLater(p::closeInventory);
            return;
        }

        if (title.equals("§6传送设置")) {
            long now = System.currentTimeMillis();
            String successMsg = "";

            if (name.equals("§c屏蔽 30 分钟")) {
                globalBlock.put(p.getUniqueId(), now + 30 * 60 * 1000L);
                successMsg = "§a30分钟全局传送屏蔽已设置成功！";
            } else if (name.equals("§c屏蔽 1 小时")) {
                globalBlock.put(p.getUniqueId(), now + 60 * 60 * 1000L);
                successMsg = "§a1小时全局传送屏蔽已设置成功！";
            } else if (name.equals("§c屏蔽 1.5 小时")) {
                globalBlock.put(p.getUniqueId(), now + 90 * 60 * 1000L);
                successMsg = "§a1.5小时全局传送屏蔽已设置成功！";
            } else if (name.equals("§c永久屏蔽")) {
                globalBlock.put(p.getUniqueId(), now + 3153600000000L);
                successMsg = "§a永久全局传送屏蔽已设置成功！";
            } else if (name.equals("§a恢复默认")) {
                globalBlock.remove(p.getUniqueId());
                playerBlock.remove(p.getUniqueId());
                successMsg = "§a所有传送屏蔽已恢复默认！";
            } else if (name.equals("§e屏蔽指定玩家")) {
                runLater(() -> openPlayerList(p, "BLOCK", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            } else if (name.equals("§a取消屏蔽玩家")) {
                runLater(() -> openPlayerList(p, "UNBLOCK", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }

            if (!successMsg.isEmpty()) {
                p.sendMessage(successMsg);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.8f);
            }
            runLater(p::closeInventory);
        }

        // ========== 公共路径点分类GUI ==========
        if (title.equals("§5公共路径点")) {
            if (publicWaypointCommand == null) return;

            // 分类按钮（精确匹配分类名称）
            String rawName = name.replace("§f", "");
            if (PublicWaypointCommand.CATEGORIES.contains(rawName) || rawName.equals("全部")) {
                String cat = rawName;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                p.closeInventory();
                // 延迟一tick后打开新GUI，确保旧GUI完全关闭
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            openPublicWaypointCategoryView(p, cat, 1);
                        } catch (Exception ex) {
                            p.sendMessage("§c打开分类视图时出错: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }.runTaskLater(plugin, 2);
                return;
            }
            if (name.equals("§a创建")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                runLater(() -> openPublicWaypointCreateGUI(p));
                return;
            }
            if (name.equals("§3我的地标")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                runLater(() -> openMyWaypointsUI(p));
                return;
            }
            if (name.equals("§c返回")) {
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                runLater(() -> openMainUI(p));
                return;
            }
            return;
        }

        // ========== 公共路径点分类查看 ==========
        if (title.startsWith("§5公共路径点 - ")) {
            if (publicWaypointCommand == null) {
                p.sendMessage("§c[DEBUG] publicWaypointCommand is null!");
                return;
            }

            String titleCategory = title.substring("§5公共路径点 - ".length());
            String category = titleCategory;
            Map.Entry<String, Integer> cached = pwPageCache.get(p.getUniqueId());
            int page = (cached != null && cached.getKey().equals(category)) ? cached.getValue() : 1;

            if (name.equals("§a创建")) {
                runLater(() -> openPublicWaypointCreateGUI(p));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                return;
            }
            if (name.equals("§e← 上一页")) {
                runLater(() -> openPublicWaypointCategoryView(p, category, page - 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.equals("§e下一页 →")) {
                runLater(() -> openPublicWaypointCategoryView(p, category, page + 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.equals("§c返回")) {
                runLater(() -> openPublicWaypointUI(p));
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }
            // 点击路径点条目 -> 传送（检查权限和封禁）
            // 只有包含"创建者"lore的才是路径点条目
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return;
            }
            List<String> lore = item.getItemMeta().getLore();
            boolean isWaypoint = false;
            for (String line : lore) {
                if (line.contains("创建者")) {
                    isWaypoint = true;
                    break;
                }
            }
            if (!isWaypoint) return;
            
            String wpName = name.replace("§a", "").replace("§b", "").trim();
            p.sendMessage("§7[DEBUG] 点击路径点: " + wpName + " | 原始名称: " + name);
            Map<String, Object> data = publicWaypointCommand.loadWaypointData(wpName);
            if (data == null) {
                p.sendMessage("§c[DEBUG] 数据为 null! 文件名: " + wpName);
            }
            if (data != null) {
                // 私密路径点仅创建者可传送
                if (!"公开".equals(data.get("permission")) && !p.getName().equals(data.get("creator"))) {
                    p.sendMessage("§c该路径点不是公开的！");
                    runLater(p::closeInventory);
                    return;
                }
                // 检查封禁
                @SuppressWarnings("unchecked")
                List<String> banned = (List<String>) data.get("banned_players");
                if (banned != null && banned.contains(p.getName())) {
                    p.sendMessage("§c你已被该路径点的创建者封禁！");
                    runLater(p::closeInventory);
                    return;
                }
                Location loc = publicWaypointCommand.deserializeLocation(data);
                if (loc != null) {
                    plugin.getPlayerDataManager().savePreviousLocation(p, p.getLocation());
                    p.teleportAsync(loc);
                    p.sendMessage("§a已传送到公共路径点 §b" + wpName + " §a！");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    publicWaypointCommand.recordVisit(data, p.getName());
                    publicWaypointCommand.saveWaypointData(wpName, data);
                } else {
                    p.sendMessage("§c路径点所在世界不存在！");
                }
            } else {
                p.sendMessage("§c路径点数据已丢失！");
            }
            runLater(p::closeInventory);
            return;
        }

        // ========== 创建路径点 - 选择分类 ==========
        if (title.equals("§e选择创建分类")) {
            for (String cat : PublicWaypointCommand.CATEGORIES) {
                if (name.equals("§f" + cat)) {
                    pendingPwCreate.put(p.getUniqueId(), cat);
                    p.closeInventory();
                    p.sendMessage("§a请在聊天栏输入要创建的路径点名称：");
                    p.sendMessage("§7（输入 §c取消 §7或 §cno §7取消创建）");
                    return;
                }
            }
            if (name.equals("§c取消")) {
                pendingPwCreate.remove(p.getUniqueId());
                p.closeInventory();
                runLater(() -> openPublicWaypointUI(p));
            }
            return;
        }

        // ========== 我的地标（左键传送，右键管理已在onInventoryClick处理）==========
        if (title.equals("§3我的地标")) {
            if (name.equals("§c返回")) {
                runLater(() -> openPublicWaypointUI(p));
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }
            // 左键传送
            String wpName = name.replace("§a", "").replace("§b", "").trim();
            Map<String, Object> data = publicWaypointCommand != null ? publicWaypointCommand.loadWaypointData(wpName) : null;
            if (data != null) {
                Location loc = publicWaypointCommand.deserializeLocation(data);
                if (loc != null) {
                    plugin.getPlayerDataManager().savePreviousLocation(p, p.getLocation());
                    p.teleportAsync(loc);
                    p.sendMessage("§a已传送到路径点 §b" + wpName + " §a！");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    publicWaypointCommand.recordVisit(data, p.getName());
                    publicWaypointCommand.saveWaypointData(wpName, data);
                } else {
                    p.sendMessage("§c路径点所在世界不存在！");
                }
                runLater(p::closeInventory);
            }
            return;
        }

        // ========== 路径点管理界面 ==========
        if (title.startsWith("§3管理地标 - ")) {
            String wpName = title.substring("§3管理地标 - ".length());
            Map<String, Object> data = publicWaypointCommand.loadWaypointData(wpName);
            if (data == null) { p.sendMessage("§c路径点数据已丢失！"); return; }

            if (name.equals("§b设置传送位置")) {
                p.closeInventory();
                publicWaypointCommand.setPendingSetLocation(p, wpName);
                return;
            }
            if (name.equals("§a传送")) {
                Location loc = publicWaypointCommand.deserializeLocation(data);
                if (loc != null) {
                    plugin.getPlayerDataManager().savePreviousLocation(p, p.getLocation());
                    p.teleportAsync(loc);
                    p.sendMessage("§a已传送到 §b" + wpName + " §a！");
                }
                runLater(p::closeInventory);
                return;
            }
            if (name.equals("§e改名")) {
                p.closeInventory();
                p.sendMessage("§6可使用指令: §e/pw m " + wpName + " rename <新名称>");
                return;
            }
            if (name.equals("§c删除")) {
                p.closeInventory();
                pendingDeleteConfirm.put(p.getUniqueId(),
                        new AbstractMap.SimpleEntry<>(wpName, System.currentTimeMillis() + 30000));
                p.sendMessage("§e⚠ 确认删除路径点 §b" + wpName + " §e？在聊天栏输入 §f t/yes/确认 §e确认（30秒内有效）");
                p.sendMessage("§7（输入其他内容将取消删除）");
                return;
            }
            if (name.startsWith("§6权限:")) {
                String cur = (String) data.get("permission");
                String newPerm = "公开".equals(cur) ? "私密" : "公开";
                data.put("permission", newPerm);
                if (publicWaypointCommand.saveWaypointData(wpName, data)) {
                    p.sendMessage("§a已切换为 " + newPerm + " 权限");
                    runLater(() -> openWaypointManageUI(p, wpName, data));
                }
                return;
            }
            if (name.equals("§d修改图标")) {
                p.closeInventory();
                publicWaypointCommand.setPendingIcon(p, wpName);
                return;
            }
            if (name.equals("§e✎ 修改描述")) {
                p.closeInventory();
                pendingPwDesc.put(p.getUniqueId(), wpName);
                p.sendMessage("§a请在聊天栏输入新的路径点描述（支持 §f& §a颜色代码，最长32字）：");
                p.sendMessage("§7（输入 §c取消 §7或 §cno §7取消修改）");
                return;
            }
            if (name.equals("§3访问记录")) {
                runLater(() -> openWaypointVisitorsUI(p, wpName));
                return;
            }
            if (name.equals("§c封禁玩家")) {
                p.closeInventory();
                p.sendMessage("§6可使用指令: §e/pw m " + wpName + " ban 玩家:X 时间:Xs/y/m/d(-1永久) 原因:X");
                p.sendMessage("§7参数顺序可任意，X:用于识别哪项");
                return;
            }
            if (name.equals("§7返回")) {
                runLater(() -> openMyWaypointsUI(p));
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }
            return;
        }

        // ========== 访问记录界面 ==========
        if (title.startsWith("§3访问记录 - ")) {
            if (name.equals("§7返回")) {
                String wpName = title.substring("§3访问记录 - ".length());
                Map<String, Object> data = publicWaypointCommand != null ? publicWaypointCommand.loadWaypointData(wpName) : null;
                if (data != null) {
                    runLater(() -> openWaypointManageUI(p, wpName, data));
                } else {
                    runLater(() -> openMyWaypointsUI(p));
                }
                return;
            }
            return;
        }
    }

    private void runLater(Runnable runnable) {
        new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskLater(plugin, 1);
    }

    private void blockPlayer(Player blocker, Player target) {
        playerBlock.computeIfAbsent(blocker.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
        blockedBy.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(blocker.getUniqueId());
    }

    private void unblockPlayer(Player blocker, Player target) {
        Set<UUID> set = playerBlock.get(blocker.getUniqueId());
        if (set != null) set.remove(target.getUniqueId());
        Set<UUID> bySet = blockedBy.get(target.getUniqueId());
        if (bySet != null) bySet.remove(blocker.getUniqueId());
    }

    private boolean isBlockedByMe(Player me, Player target) {
        Set<UUID> set = playerBlock.get(me.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    public void sendRequest(Player from, Player to, String type) {
        if (isGlobalBlocked(to)) {
            from.sendMessage("§c" + to.getName() + " 开启了全局传送屏蔽，无法发送请求！");
            from.playSound(from.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (isPlayerBlocked(to, from)) {
            from.sendMessage("§c" + to.getName() + " 已将你单独屏蔽，无法发送请求！");
            from.playSound(from.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        if (cooldown.containsKey(from.getUniqueId()) && now - cooldown.get(from.getUniqueId()) < CD) {
            long remaining = CD - (now - cooldown.get(from.getUniqueId()));
            from.sendMessage("§c冷却中... 剩余 " + remaining + " 秒");
            from.playSound(from.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            return;
        }
        cooldown.put(from.getUniqueId(), now);

        if (type.equals("TPA")) {
            tpaRequests.put(to.getUniqueId(), from.getUniqueId());
        } else {
            tphereRequests.put(to.getUniqueId(), from.getUniqueId());
        }

        from.sendMessage("§a已向 " + to.getName() + " 发送" + (type.equals("TPA") ? "传送" : "邀请传送") + "请求！");
        from.sendMessage("§7等待对方回应，请求将在 " + REQUEST_TIMEOUT + " 秒后自动过期！");
        runLater(() -> openRequestUI(to, from, type));
    }

    private boolean isGlobalBlocked(Player target) {
        if (globalBlock.containsKey(target.getUniqueId())) {
            long end = globalBlock.get(target.getUniqueId());
            if (System.currentTimeMillis() < end) return true;
            globalBlock.remove(target.getUniqueId());
        }
        return false;
    }

    private boolean isPlayerBlocked(Player target, Player from) {
        Set<UUID> set = playerBlock.get(target.getUniqueId());
        return set != null && set.contains(from.getUniqueId());
    }

    public boolean isBlocked(Player target, Player from) {
        return isGlobalBlocked(target) || isPlayerBlocked(target, from);
    }

    public Player getRequester(Player target) {
        UUID id = tpaRequests.getOrDefault(target.getUniqueId(), tphereRequests.get(target.getUniqueId()));
        return id == null ? null : Bukkit.getPlayer(id);
    }

    public void clearRequest(Player target) {
        tpaRequests.remove(target.getUniqueId());
        tphereRequests.remove(target.getUniqueId());
        requestTime.remove(target.getUniqueId());
    }

    public void setItem(Inventory inv, int slot, Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    /** 设置带描述的路径点物品（支持\n换行） */
    private void setWaypointItem(Inventory inv, int slot, Material mat, String name, Map<String, Object> wp, String... baseLore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>(Arrays.asList(baseLore));
        String desc = PublicWaypointCommand.getDescription(wp);
        String[] lines = desc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                lore.add("§7描述: " + lines[i]);
            } else {
                lore.add("§7  " + lines[i]);
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    // ====================== 公共路径点 GUI ======================

    /** 打开分类选择界面 */
    public void openPublicWaypointUI(Player p) {
        if (publicWaypointCommand == null) return;
        Inventory inv = createGui(18, "§5公共路径点");
        List<String> cats = PublicWaypointCommand.CATEGORIES;
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i);
            Material mat = switch (cat) {
                case "刷怪塔" -> Material.ZOMBIE_HEAD;
                case "建筑" -> Material.STONE_BRICKS;
                case "机器" -> Material.PISTON;
                case "商店" -> Material.CHEST;
                default -> Material.WHITE_BANNER;
            };
            setItem(inv, i, mat, "§f" + cat, "§7点击查看该分类的路径点");
        }
        setItem(inv, 5, Material.WHITE_BANNER, "§f全部", "§7查看所有公共路径点");
        setItem(inv, 11, Material.FILLED_MAP, "§3我的地标", "§7管理你创建的路径点");
        setItem(inv, 13, Material.LIME_DYE, "§a创建", "§7创建新的公共路径点");
        setItem(inv, 17, Material.BARRIER, "§c返回", "§7返回传送菜单");
        p.openInventory(inv);
    }

    /** 打开分类查看界面（带分页） */
    public void openPublicWaypointCategoryView(Player p, String category, int page) {
        if (publicWaypointCommand == null) return;
        List<Map<String, Object>> allWps = publicWaypointCommand.loadWaypointsByCategory(category);
        int perPage = 18;
        int totalPage = Math.max(1, (allWps.size() + perPage - 1) / perPage);
        if (page < 1) page = 1;
        if (page > totalPage) page = totalPage;

        Inventory inv = createGui(27, "§5公共路径点 - " + category);
        int start = (page - 1) * perPage;
        int slot = 0;
        for (int i = start; i < start + perPage && i < allWps.size(); i++) {
            Map<String, Object> wp = allWps.get(i);
            String wpName = (String) wp.get("name");
            String creator = (String) wp.get("creator");
            Material icon = Material.ENDER_PEARL;
            String cat = (String) wp.get("category");
            if (cat != null) {
                switch (cat) {
                    case "刷怪塔" -> icon = Material.ZOMBIE_HEAD;
                    case "建筑" -> icon = Material.STONE_BRICKS;
                    case "机器" -> icon = Material.PISTON;
                    case "商店" -> icon = Material.CHEST;
                    case "其他" -> icon = Material.COMPASS;
                }
            }
            String locStr = "未知";
            Object xObj = wp.get("x"), yObj = wp.get("y"), zObj = wp.get("z");
            if (xObj != null && yObj != null && zObj != null) {
                locStr = String.format("%.0f, %.0f, %.0f",
                        ((Number) xObj).doubleValue(),
                        ((Number) yObj).doubleValue(),
                        ((Number) zObj).doubleValue());
            }
            // 计算访问人数
            int totalVisits = 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> visitors = (Map<String, Object>) wp.get("visitors");
            if (visitors != null) {
                for (Object count : visitors.values()) {
                    if (count instanceof Number) {
                        totalVisits += ((Number) count).intValue();
                    }
                }
            }
            // 格式化创建时间
            String timeStr = "未知";
            Object created = wp.get("created_at");
            if (created != null) {
                long ts = ((Number) created).longValue();
                timeStr = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm").format(new java.util.Date(ts));
            }
            setWaypointItem(inv, slot, icon, "§b" + wpName, wp,
                    "§7创建者: §f" + creator,
                    "§7坐标: §f" + locStr,
                    "§7分类: §f" + (cat != null ? cat : "未分类"),
                    "§7访问人数: §e" + totalVisits,
                    "§7创建时间: §e" + timeStr,
                    "",
                    "§e点击传送");
            slot++;
        }

        // 底部导航栏
        setItem(inv, 18, Material.LIME_DYE, "§a创建", "§7创建新的公共路径点");
        if (page > 1) {
            setItem(inv, 21, Material.ARROW, "§e← 上一页", "§7第 " + (page - 1) + " 页");
        }
        setItem(inv, 22, Material.PAPER, "§7第 " + page + "§7/" + totalPage + " 页", "");
        if (page < totalPage) {
            setItem(inv, 23, Material.ARROW, "§e下一页 →", "§7第 " + (page + 1) + " 页");
        }
        setItem(inv, 26, Material.BARRIER, "§c返回", "§7返回分类选择");

        p.openInventory(inv);
        pwPageCache.put(p.getUniqueId(), new AbstractMap.SimpleEntry<>(category, page));
    }

    /** 打开创建路径点 - 选择分类界面 */
    public void openPublicWaypointCreateGUI(Player p) {
        Inventory inv = createGui(9, "§e选择创建分类");
        List<String> cats = PublicWaypointCommand.CATEGORIES;
        for (int i = 0; i < cats.size(); i++) {
            setItem(inv, i, Material.WHITE_BANNER, "§f" + cats.get(i), "§7点击选择分类");
        }
        setItem(inv, 8, Material.BARRIER, "§c取消", "§7取消创建");
        p.openInventory(inv);
    }

    /** 打开我的地标列表 */
    public void openMyWaypointsUI(Player p) {
        if (publicWaypointCommand == null) return;
        List<Map<String, Object>> all = publicWaypointCommand.loadAllWaypoints();
        List<Map<String, Object>> mine = all.stream()
                .filter(d -> p.getName().equals(d.get("creator")))
                .collect(Collectors.toList());

        Inventory inv = createGui(27, "§3我的地标");
        int slot = 0;
        for (Map<String, Object> wp : mine) {
            if (slot >= 26) break;
            String wpName = (String) wp.get("name");
            String cat = (String) wp.get("category");
            String perm = (String) wp.get("permission");
            if (perm == null) perm = "公开";
            Material icon;
            try { icon = Material.valueOf(PublicWaypointCommand.getWaypointIcon(wp)); } catch (Exception e) { icon = Material.ENDER_PEARL; }
            // 计算访问人数
            int totalVisits = 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> visitors = (Map<String, Object>) wp.get("visitors");
            if (visitors != null) {
                for (Object count : visitors.values()) {
                    totalVisits += ((Number) count).intValue();
                }
            }
            // 格式化创建时间
            String timeStr = "未知";
            Object created = wp.get("created_at");
            if (created != null) {
                long ts = ((Number) created).longValue();
                timeStr = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm").format(new java.util.Date(ts));
            }
            setWaypointItem(inv, slot, icon, "§b" + wpName, wp,
                    "§7分类: " + cat,
                    "§7权限: " + ("私密".equals(perm) ? "§c私密" : "§a公开"),
                    "§7访问人数: §e" + totalVisits,
                    "§7创建时间: §e" + timeStr,
                    "",
                    "§e左键传送 §7| §c右键管理");
            slot++;
        }
        setItem(inv, 26, Material.BARRIER, "§c返回", "§7返回上一层");
        p.openInventory(inv);
    }

    /** 打开路径点管理界面 */
    public void openWaypointManageUI(Player p, String name, Map<String, Object> data) {
        Inventory inv = createGui(18, "§3管理地标 - " + name);
        String perm = (String) data.get("permission");
        if (perm == null) perm = "公开";
        String iconName = PublicWaypointCommand.getWaypointIcon(data);
        Material iconMat = Material.ENDER_PEARL;
        try { iconMat = Material.valueOf(iconName); } catch (Exception ignored) {}

        setItem(inv, 0, iconMat, "§b" + name, "§7分类: " + data.get("category"));
        // 描述按钮（支持\n换行）
        {
            ItemStack descItem = new ItemStack(Material.OAK_SIGN);
            ItemMeta descMeta = descItem.getItemMeta();
            descMeta.setDisplayName("§e✎ 修改描述");
            List<String> descLore = new ArrayList<>();
            String desc = PublicWaypointCommand.getDescription(data);
            String[] descLines = desc.split("\n", -1);
            for (int i = 0; i < descLines.length; i++) {
                descLore.add((i == 0 ? "§7当前: " : "§7  ") + descLines[i]);
            }
            descMeta.setLore(descLore);
            descItem.setItemMeta(descMeta);
            inv.setItem(1, descItem);
        }
        setItem(inv, 2, Material.ENDER_EYE, "§a传送", "§7传送到此路径点");
        setItem(inv, 3, Material.ANVIL, "§e改名", "§7使用指令改名");
        setItem(inv, 4, Material.BARRIER, "§c删除", "§7删除此路径点");
        setItem(inv, 5, Material.valueOf("私密".equals(perm) ? "RED_CONCRETE" : "LIME_CONCRETE"), "§6权限: " + perm, "§7点击切换公开/私密");
        setItem(inv, 6, Material.END_CRYSTAL, "§b设置传送位置", "§7将路径点更新到当前位置");
        setItem(inv, 7, Material.ITEM_FRAME, "§d修改图标", "§7手持物品后点击");
        setItem(inv, 9, Material.KNOWLEDGE_BOOK, "§3访问记录", "§7查看所有访问者");
        setItem(inv, 10, Material.BARRIER, "§c封禁玩家", "§7使用指令封禁指定玩家");
        setItem(inv, 8, Material.ARROW, "§7返回", "§7返回我的地标");
        p.openInventory(inv);
    }

    /** 打开访问者列表界面 */
    public void openWaypointVisitorsUI(Player p, String wpName) {
        if (publicWaypointCommand == null) return;
        Map<String, Object> data = publicWaypointCommand.loadWaypointData(wpName);
        if (data == null) { p.sendMessage("§c路径点数据已丢失！"); return; }

        List<Map.Entry<String, Integer>> visitors = PublicWaypointCommand.getSortedVisitors(data);
        int perPage = 18;
        int totalPage = Math.max(1, (visitors.size() + perPage - 1) / perPage);

        Inventory inv = createGui(27, "§3访问记录 - " + wpName);
        int slot = 0;
        for (int i = 0; i < perPage && i < visitors.size(); i++) {
            Map.Entry<String, Integer> entry = visitors.get(i);
            String visitorName = entry.getKey();
            int count = entry.getValue();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName("§f" + visitorName);

            Player online = Bukkit.getPlayerExact(visitorName);
            if (online != null) {
                meta.setOwningPlayer(online);
            }

            meta.setLore(Arrays.asList("§7访问次数: §e" + count));
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }

        setItem(inv, 26, Material.ARROW, "§7返回", "§7返回管理界面");
        p.openInventory(inv);
    }

    // ====================== 聊天监听 ======================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPwCreateChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 设置传送位置确认（最高优先）
        if (publicWaypointCommand != null && publicWaypointCommand.hasPendingSetLocation(player)) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            if (msg.equalsIgnoreCase("t") || msg.equalsIgnoreCase("yes") || msg.equalsIgnoreCase("确认")) {
                publicWaypointCommand.confirmSetLocation(player);
            } else {
                publicWaypointCommand.pendingSetLocation.remove(player.getUniqueId());
                player.sendMessage("§c已取消设置传送位置！");
            }
            return;
        }

        // 封禁确认
        if (publicWaypointCommand != null && publicWaypointCommand.pendingBans.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            if (msg.equalsIgnoreCase("t") || msg.equalsIgnoreCase("yes") || msg.equalsIgnoreCase("确认")) {
                publicWaypointCommand.confirmBan(player);
            } else {
                publicWaypointCommand.pendingBans.remove(player.getUniqueId());
                player.sendMessage("§c已取消封禁！");
            }
            return;
        }

        // 待确认删除（来自 /pw m <name> delete / tremove）
        if (publicWaypointCommand != null && publicWaypointCommand.pendingTRemove.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            if (msg.equalsIgnoreCase("t") || msg.equalsIgnoreCase("yes") || msg.equalsIgnoreCase("确认")) {
                publicWaypointCommand.confirmTRemove(player);
            } else {
                publicWaypointCommand.pendingTRemove.remove(player.getUniqueId());
                player.sendMessage("§c已取消删除！");
            }
            return;
        }

        // 删除确认（来自GUI管理界面）
        Map.Entry<String, Long> pendingDel = pendingDeleteConfirm.get(player.getUniqueId());
        if (pendingDel != null) {
            event.setCancelled(true);
            pendingDeleteConfirm.remove(player.getUniqueId());
            String msg = event.getMessage().trim();
            if (msg.equalsIgnoreCase("t") || msg.equalsIgnoreCase("yes") || msg.equalsIgnoreCase("确认")) {
                if (System.currentTimeMillis() > pendingDel.getValue()) {
                    player.sendMessage("§c确认已超时！");
                    return;
                }
                String wpName = pendingDel.getKey();
                File f = publicWaypointCommand.getWaypointFile(wpName);
                if (f.delete()) {
                    player.sendMessage("§a已删除路径点 §b" + wpName + " §a！");
                } else {
                    player.sendMessage("§c删除失败！");
                }
            } else {
                player.sendMessage("§c已取消删除！");
            }
            return;
        }

        // 创建中的图标设置（优先于普通图标设置）
        String creationIconName = pendingCreationIcon.get(player.getUniqueId());
        if (creationIconName != null && publicWaypointCommand != null && publicWaypointCommand.isPendingIcon(player)) {
            event.setCancelled(true);
            publicWaypointCommand.pendingIconPlayers.remove(player.getUniqueId());
            pendingCreationIcon.remove(player.getUniqueId());
            String msg = event.getMessage().trim();

            if (msg.equalsIgnoreCase("取消") || msg.equalsIgnoreCase("no")) {
                player.sendMessage("§c已取消设置图标，将使用默认图标！");
            } else {
                org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
                String iconType;
                if (hand.getType().isAir()) {
                    iconType = "WHITE_WOOL";
                    player.sendMessage("§a手中无物品，已使用白色羊毛作为图标！");
                } else {
                    iconType = hand.getType().name();
                    player.sendMessage("§a已设置图标为 " + PublicWaypointCommand.getIconDisplayName(hand.getType()));
                }
                Map<String, Object> data = publicWaypointCommand.loadWaypointData(creationIconName);
                if (data != null) {
                    data.put("icon", iconType);
                    publicWaypointCommand.saveWaypointData(creationIconName, data);
                }
            }
            // 继续到描述输入
            pendingPwDesc.put(player.getUniqueId(), creationIconName);
            player.sendMessage("§a请在聊天栏输入路径点描述（支持 §f& §a颜色代码，最长32字）：");
            player.sendMessage("§7（输入 §c取消 §7或 §cno §7跳过使用默认描述）");
            return;
        }

        // 图标设置（优先处理）
        if (publicWaypointCommand != null && publicWaypointCommand.isPendingIcon(player)) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            if (msg.equalsIgnoreCase("取消") || msg.equalsIgnoreCase("no")) {
                player.sendMessage("§c已取消设置图标");
                return;
            }
            // trySetIcon 会用手中物品设图标，聊天内容无所谓
            publicWaypointCommand.trySetIcon(player, "");
            return;
        }

        // 描述输入（创建后或管理界面调用）
        String descName = pendingPwDesc.get(player.getUniqueId());
        if (descName != null) {
            event.setCancelled(true);
            pendingPwDesc.remove(player.getUniqueId());
            String msg = event.getMessage().trim();

            if (msg.equalsIgnoreCase("取消") || msg.equalsIgnoreCase("no")) {
                player.sendMessage("§a将使用默认描述！");
            } else {
                String desc = PublicWaypointCommand.processDescription(msg);
                Map<String, Object> data = publicWaypointCommand != null ? publicWaypointCommand.loadWaypointData(descName) : null;
                if (data != null) {
                    data.put("description", desc);
                    publicWaypointCommand.saveWaypointData(descName, data);
                    player.sendMessage("§a已设置描述！");
                } else {
                    player.sendMessage("§c路径点数据已丢失！");
                }
            }
            runLater(() -> openPublicWaypointUI(player));
            return;
        }

        // 创建路径点
        String category = pendingPwCreate.remove(player.getUniqueId());
        if (category == null) return;

        event.setCancelled(true);
        String name = event.getMessage().trim();

        if (name.isEmpty()) {
            player.sendMessage("§c名称不能为空！");
            return;
        }

        if (name.equalsIgnoreCase("取消") || name.equalsIgnoreCase("no")) {
            player.sendMessage("§c已取消创建！");
            runLater(() -> openPublicWaypointUI(player));
            return;
        }

        String finalName = name;
        String finalCategory = category;
        plugin.getServer().getGlobalRegionScheduler().run(plugin, (task) -> {
            boolean success = publicWaypointCommand.createWaypoint(finalName, player.getName(), finalCategory, player.getLocation());
            if (success) {
                player.sendMessage("§a已创建公共路径点 §b" + finalName + " §a（分类: " + finalCategory + "）");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                // 提示设置图标
                pendingCreationIcon.put(player.getUniqueId(), finalName);
                publicWaypointCommand.pendingIconPlayers.put(player.getUniqueId(), finalName);
                player.sendMessage("§a请在聊天栏手持要设置的物品，发送任意内容设置图标！");
                player.sendMessage("§7（输入 §c取消 §7或 §cno §7跳过，手持无物品将使用白色羊毛）");
            } else {
                player.sendMessage("§c已存在同名路径点 §b" + finalName + " §c！");
                runLater(() -> openPublicWaypointUI(player));
            }
        });
    }
}