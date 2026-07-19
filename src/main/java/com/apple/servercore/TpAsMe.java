package com.apple.servercore;

import com.apple.servercore.guild.Guilds;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.geysermc.floodgate.api.FloodgateApi;

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

    private boolean isOurGuiTitle(String title) {
        return title.equals("§e传送菜单")
                || title.equals("§6传送设置")
                || title.startsWith("§e选择玩家")
                || title.equals("§e传送请求");
    }

    private Inventory createGui(int size, String title) {
        Inventory inv = Bukkit.createInventory(null, size, title);
        ourGuis.add(inv);
        return inv;
    }

    public void removeGui(Inventory inv) {
        ourGuis.remove(inv);
    }

    public void openMainUI(Player p) {
        Inventory inv = createGui(9, "§e传送菜单");
        setItem(inv, 2, Material.ENDER_PEARL, "§a传送到玩家", "§7点击向他人发送传送请求");
        setItem(inv, 4, Material.ENDER_EYE, "§a邀请玩家传送", "§7让对方传送到你身边");
        setItem(inv, 6, Material.COMPASS, "§6传送设置", "§7屏蔽/管理传送请求");
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
            if (name.contains("传送到玩家")) {
                runLater(() -> openPlayerList(p, "TPA", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            }
            if (name.contains("邀请玩家传送")) {
                runLater(() -> openPlayerList(p, "TPHERE", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            }
            if (name.contains("传送设置")) {
                runLater(() -> openSettingUI(p));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            }
            return;
        }

        if (title.startsWith("§e选择玩家")) {
            String[] titleParts = title.split(" - ");
            if (titleParts.length < 2) return;
            String type = titleParts[1].split(" ")[0];
            int page = pageCache.getOrDefault(p.getUniqueId(), 1);

            if (name.contains("上一页")) {
                runLater(() -> openPlayerList(p, type, page - 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.contains("下一页")) {
                runLater(() -> openPlayerList(p, type, page + 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.contains("返回菜单")) {
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

            if (name.contains("接受")) {
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

            if (name.contains("30 分钟")) {
                globalBlock.put(p.getUniqueId(), now + 30 * 60 * 1000L);
                successMsg = "§a30分钟全局传送屏蔽已设置成功！";
            }
            if (name.contains("1 小时")) {
                globalBlock.put(p.getUniqueId(), now + 60 * 60 * 1000L);
                successMsg = "§a1小时全局传送屏蔽已设置成功！";
            }
            if (name.contains("1.5 小时")) {
                globalBlock.put(p.getUniqueId(), now + 90 * 60 * 1000L);
                successMsg = "§a1.5小时全局传送屏蔽已设置成功！";
            }
            if (name.contains("永久屏蔽")) {
                globalBlock.put(p.getUniqueId(), now + 3153600000000L);
                successMsg = "§a永久全局传送屏蔽已设置成功！";
            }
            if (name.contains("恢复默认")) {
                globalBlock.remove(p.getUniqueId());
                playerBlock.remove(p.getUniqueId());
                successMsg = "§a所有传送屏蔽已恢复默认！";
            }
            if (name.contains("屏蔽指定玩家")) {
                runLater(() -> openPlayerList(p, "BLOCK", 1));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (name.contains("取消屏蔽玩家")) {
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
}