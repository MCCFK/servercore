package com.apple.servercore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TpAsMePoint implements Listener, CommandExecutor {

    private final MainPlugin plugin;
    private File configFile;
    private FileConfiguration config;
    private final HashMap<UUID, List<TpPoint>> playerPoints = new HashMap<>();
    private final HashMap<UUID, Integer> selectedPointMap = new HashMap<>();
    private final Map<UUID, Integer> pendingRenamePlayers = new HashMap<>();
    // 死亡点存储
    private final HashMap<UUID, Location> lastDeathLocation = new HashMap<>();

    // 默认传送点上限
    private static final int DEFAULT_MAX_POINTS = 5;

    public static class TpPoint {
        public String name, world;
        public double x,y,z;
        public float yaw,pitch;
        public TpPoint(String name, String world, double x, double y, double z, float yaw, float pitch) {
            this.name = name; this.world = world; this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch;
        }
    }

    public TpAsMePoint(MainPlugin plugin) {
        this.plugin = plugin;
        initConfig();
        loadAllData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void initConfig() {
        configFile = new File(plugin.getDataFolder(), "actpasme.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        if (!configFile.exists()) {
            config.set("default-max-points", DEFAULT_MAX_POINTS);
            saveConfig();
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getMaxPoints(UUID uuid) {
        // 先检查玩家是否有单独配置
        String path = uuid.toString() + ".max-points";
        if (config.contains(path)) {
            return config.getInt(path, DEFAULT_MAX_POINTS);
        }
        // 返回默认值
        return config.getInt("default-max-points", DEFAULT_MAX_POINTS);
    }

    /**
     * 设置玩家传送点上限（管理员用）
     */
    public void setMaxPoints(UUID uuid, int maxPoints) {
        if (maxPoints < 1) maxPoints = 1;
        config.set(uuid.toString() + ".max-points", maxPoints);
        saveConfig();
    }

    /**
     * 重置玩家传送点上限为默认值
     */
    public void resetMaxPoints(UUID uuid) {
        config.set(uuid.toString() + ".max-points", null);
        saveConfig();
    }

    public void loadAllData() {
        playerPoints.clear();
        for (String key : config.getKeys(false)) {
            // 跳过配置项
            if (key.equals("default-max-points")) continue;
            // 跳过 max-points 配置（在 playerPoints 加载时忽略）
            if (key.endsWith(".max-points")) continue;

            try {
                UUID uuid = UUID.fromString(key);
                List<TpPoint> list = new ArrayList<>();
                if (config.contains(key + ".points")) {
                    for (String index : config.getConfigurationSection(key + ".points").getKeys(false)) {
                        String path = key + ".points." + index;
                        TpPoint point = new TpPoint(
                                config.getString(path + ".name"),
                                config.getString(path + ".world"),
                                config.getDouble(path + ".x"),
                                config.getDouble(path + ".y"),
                                config.getDouble(path + ".z"),
                                (float) config.getDouble(path + ".yaw"),
                                (float) config.getDouble(path + ".pitch")
                        );
                        list.add(point);
                    }
                }
                playerPoints.put(uuid, list);
            } catch (Exception ignored) {}
        }
    }

    public void savePlayerData(UUID uuid) {
        List<TpPoint> points = playerPoints.getOrDefault(uuid, new ArrayList<>());
        String path = uuid.toString();
        config.set(path + ".points", null);
        for (int i=0;i<points.size();i++) {
            TpPoint p = points.get(i);
            config.set(path + ".points." + i + ".name", p.name);
            config.set(path + ".points." + i + ".world", p.world);
            config.set(path + ".points." + i + ".x", p.x);
            config.set(path + ".points." + i + ".y", p.y);
            config.set(path + ".points." + i + ".z", p.z);
            config.set(path + ".points." + i + ".yaw", p.yaw);
            config.set(path + ".points." + i + ".pitch", p.pitch);
        }
        saveConfig();
    }

    public List<TpPoint> getPoints(Player p) {
        return playerPoints.getOrDefault(p.getUniqueId(), new ArrayList<>());
    }

    public boolean addPoint(Player p) {
        List<TpPoint> list = getPoints(p);
        if (list.size() >= getMaxPoints(p.getUniqueId())) return false;
        Location loc = p.getLocation();
        TpPoint point = new TpPoint("传送点-"+(list.size()+1), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        list.add(point);
        playerPoints.put(p.getUniqueId(), list);
        savePlayerData(p.getUniqueId());
        return true;
    }

    public boolean addPoint(Player p, String name) {
        List<TpPoint> list = getPoints(p);
        if (list.size() >= getMaxPoints(p.getUniqueId())) return false;
        // 检查重名
        for (TpPoint point : list) {
            if (point.name.equalsIgnoreCase(name)) {
                return false;
            }
        }
        Location loc = p.getLocation();
        TpPoint point = new TpPoint(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        list.add(point);
        playerPoints.put(p.getUniqueId(), list);
        savePlayerData(p.getUniqueId());
        return true;
    }

    public void deletePoint(Player p, int index) {
        List<TpPoint> list = getPoints(p);
        if (index <0 || index >= list.size()) return;
        list.remove(index);
        savePlayerData(p.getUniqueId());
        p.sendMessage("§a✅ 传送点已删除！");
    }

    public void renamePoint(Player p, int index, String newName) {
        List<TpPoint> list = getPoints(p);
        if (index < 0 || index >= list.size()) return;
        // 检查重名
        for (int i = 0; i < list.size(); i++) {
            if (i != index && list.get(i).name.equalsIgnoreCase(newName)) {
                p.sendMessage("§c已存在同名传送点！");
                return;
            }
        }
        list.get(index).name = newName;
        savePlayerData(p.getUniqueId());
        p.sendMessage("§a✅ 传送点已重命名为 §b" + newName + "§a！");
    }

    // ====================== 死亡点记录 ======================
    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getPlayer();
        lastDeathLocation.put(p.getUniqueId(), p.getLocation());
    }

    // 传送回死亡点
    public void teleportToDeathPoint(Player p) {
        if (!lastDeathLocation.containsKey(p.getUniqueId())) {
            p.sendMessage("§c❌ 未找到死亡点！");
            return;
        }
        Location loc = lastDeathLocation.get(p.getUniqueId());
        if (loc.getWorld() == null) {
            p.sendMessage("§c❌ 世界不存在，无法传送！");
            return;
        }
        p.teleport(loc);
        p.sendMessage("§a✅ 已返回死亡点！");
    }

    // ====================== 自动识别：Java/基岩版 ======================
    public boolean isBedrockPlayer(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 统一对外入口：自动判断界面 ======================
    public void openPlayerUI(Player p) {
        if (isBedrockPlayer(p)) {
            openMainUI(p);
        } else {
            openJavaPlayerUI(p);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可使用");
            return true;
        }
        if (args.length == 0) {
            openPlayerUI(p);
            return true;
        }
        String sub = args[0].toLowerCase();

        // add 指令
        if (sub.equals("add")) {
            if (args.length < 2) {
                p.sendMessage("§c用法: /actpasme add <传送点名称>");
                return true;
            }
            String pointName = args[1];

            List<TpPoint> points = getPoints(p);
            if (points.size() >= getMaxPoints(p.getUniqueId())) {
                p.sendMessage("§c传送点已满！最多 " + getMaxPoints(p.getUniqueId()) + " 个");
                return true;
            }
            for (TpPoint point : points) {
                if (point.name.equalsIgnoreCase(pointName)) {
                    p.sendMessage("§c已存在同名传送点！");
                    return true;
                }
            }

            // 创建传送点
            Location loc = p.getLocation();
            points.add(new TpPoint(pointName, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
            savePlayerData(p.getUniqueId());
            p.sendMessage("§a✅ 传送点 '" + pointName + "' 创建成功！");
            return true;
        }

        // remove 指令
        if (sub.equals("remove")) {
            if (args.length < 2) {
                p.sendMessage("§c用法: /actpasme remove <传送点名称>");
                return true;
            }
            String pointName = args[1];

            List<TpPoint> points = getPoints(p);
            int index = -1;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).name.equalsIgnoreCase(pointName)) {
                    index = i;
                    break;
                }
            }
            if (index == -1) {
                p.sendMessage("§c未找到名为 '" + pointName + "' 的传送点");
                return true;
            }

            points.remove(index);
            savePlayerData(p.getUniqueId());
            p.sendMessage("§a✅ 传送点 '" + pointName + "' 已删除！");
            return true;
        }

        // tp 指令 - 传送到指定传送点
        if (sub.equals("tp")) {
            if (args.length < 2) {
                p.sendMessage("§c用法: /actpasme tp <传送点名称>");
                return true;
            }
            String pointName = args[1];

            List<TpPoint> points = getPoints(p);
            TpPoint targetPoint = null;
            for (TpPoint point : points) {
                if (point.name.equalsIgnoreCase(pointName)) {
                    targetPoint = point;
                    break;
                }
            }
            if (targetPoint == null) {
                p.sendMessage("§c未找到名为 '" + pointName + "' 的传送点");
                return true;
            }

            World w = Bukkit.getWorld(targetPoint.world);
            if (w != null) {
                p.teleport(new Location(w, targetPoint.x, targetPoint.y, targetPoint.z, targetPoint.yaw, targetPoint.pitch));
                p.sendMessage("§a✅ 已传送到 §b" + targetPoint.name + "§a！");
            } else {
                p.sendMessage("§c 世界不存在，无法传送！");
            }
            return true;
        }

        // setname 指令 - 重命名传送点
        if (sub.equals("setname")) {
            if (args.length < 3) {
                p.sendMessage("§c用法: /actpasme setname <旧名称> <新名称>");
                return true;
            }
            String oldName = args[1];
            String newName = args[2];

            List<TpPoint> points = getPoints(p);
            int index = -1;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).name.equalsIgnoreCase(oldName)) {
                    index = i;
                    break;
                }
            }
            if (index == -1) {
                p.sendMessage("§c未找到名为 '" + oldName + "' 的传送点");
                return true;
            }

            renamePoint(p, index, newName);
            return true;
        }

        // ========== 管理员指令：设置传送点上限 ==========
        if (sub.equals("setmax") && p.isOp()) {
            if (args.length < 3) {
                p.sendMessage("§c用法: /actpasme setmax <玩家> <数量>");
                p.sendMessage("§c数量为 0 则重置为默认值");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                p.sendMessage("§c玩家不在线！");
                return true;
            }
            try {
                int max = Integer.parseInt(args[2]);
                if (max <= 0) {
                    resetMaxPoints(target.getUniqueId());
                    p.sendMessage("§a已重置 " + target.getName() + " 的传送点上限为默认值: " + getMaxPoints(target.getUniqueId()));
                } else {
                    setMaxPoints(target.getUniqueId(), max);
                    p.sendMessage("§a已设置 " + target.getName() + " 的传送点上限为: " + max);
                }
            } catch (NumberFormatException e) {
                p.sendMessage("§c数量必须是数字！");
            }
            return true;
        }

        if (sub.equals("setpoint")) {
            tryCreatePoint(p);
            return true;
        }
        if (sub.equals("point")) {
            openPlayerUI(p);
            return true;
        }
        if (sub.equals("backdeath")) {
            teleportToDeathPoint(p);
            return true;
        }

        // 帮助
        p.sendMessage("§e===== 个人传送点 =====");
        p.sendMessage("§f/actpasme - 打开菜单");
        p.sendMessage("§f/actpasme add <名称> - 添加传送点");
        p.sendMessage("§f/actpasme remove <名称> - 删除传送点");
        p.sendMessage("§f/actpasme tp <名称> - 传送到传送点");
        p.sendMessage("§f/actpasme setname <旧名> <新名> - 重命名传送点");
        p.sendMessage("§f/actpasme setpoint - 创建传送点");
        p.sendMessage("§f/actpasme backdeath - 回到死亡点");
        if (p.isOp()) {
            p.sendMessage("§c/actpasme setmax <玩家> <数量> - 设置传送点上限 (0=重置)");
        }
        return true;
    }

    // ====================== Java版箱子GUI（原版 + 死亡点） ======================
    public void openJavaPlayerUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6我的传送点");
        List<TpPoint> points = getPoints(p);

        for (int i=0;i<points.size();i++) {
            TpPoint point = points.get(i);
            inv.setItem(i, createButton(Material.ENDER_PEARL, "§a"+point.name, List.of("§7点击操作")));
        }

        inv.setItem(25, createButton(Material.SKELETON_SKULL, "§c返回死亡点", List.of("§7点击回到上一次死亡位置")));
        inv.setItem(26, createButton(Material.LIME_DYE, "§a创建传送点", List.of("§7当前: " + points.size() + "/" + getMaxPoints(p.getUniqueId()))));
        p.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openTpDeleteUI(Player p, int idx) {
        selectedPointMap.put(p.getUniqueId(), idx);
        Inventory inv = Bukkit.createInventory(null, 9, "§6传送点操作");
        inv.setItem(3, createButton(Material.ENDER_EYE, "§a传送", null));
        inv.setItem(4, createButton(Material.ANVIL, "§e改名", null));
        inv.setItem(5, createButton(Material.BARRIER, "§c删除", null));
        p.openInventory(inv);
    }

    public void openDeleteConfirmUI(Player p, int idx) {
        selectedPointMap.put(p.getUniqueId(), idx);
        Inventory inv = Bukkit.createInventory(null, 9, "§c确认删除？");
        inv.setItem(3, createButton(Material.LIME_CONCRETE, "§a确认", null));
        inv.setItem(5, createButton(Material.RED_CONCRETE, "§c取消", null));
        p.openInventory(inv);
    }

    public void tryCreatePoint(Player p) {
        List<TpPoint> list = getPoints(p);
        int max = getMaxPoints(p.getUniqueId());

        if (list.size() >= max) {
            p.sendMessage("§c❌ 已达到最大传送点数量：" + max);
            return;
        }

        addPoint(p);
        p.sendMessage("§a✅ 传送点创建成功！");
        if (!isBedrockPlayer(p)) {
            openJavaPlayerUI(p);
        }
    }

    // ====================== 基岩版全屏UI（+死亡点） ======================
    public void openMainUI(Player p) {
        List<TpPoint> points = getPoints(p);
        int max = getMaxPoints(p.getUniqueId());

        SimpleForm.Builder form = SimpleForm.builder()
                .title("§6✨ 我的传送点系统")
                .content("§f已解锁传送点: " + points.size() + "/" + max);

        for (TpPoint point : points) {
            form.button("§a🟢 " + point.name);
        }

        form.button("§c💀 返回死亡点");
        form.button("§a➕ 创建传送点");
        form.button("§7⬅️ 返回主菜单");

        form.validResultHandler((response) -> {
            int id = response.clickedButtonId();
            if (id < points.size()) {
                openPointOperateUI(p, id);
                return;
            }
            if (id == points.size()) {
                teleportToDeathPoint(p);
                return;
            }
            if (id == points.size() + 1) {
                tryCreatePoint(p);
                return;
            }
            if (id == points.size() + 2) {
                new ACcraft(plugin).openMainMenu(p);
            }
        });

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    public void openPointOperateUI(Player p, int index) {
        TpPoint point = getPoints(p).get(index);

        SimpleForm form = SimpleForm.builder()
                .title("§6操作: " + point.name)
                .content("§f世界: " + point.world + "\n§7坐标: " +
                        String.format("%.1f", point.x) + " / " +
                        String.format("%.1f", point.y) + " / " +
                        String.format("%.1f", point.z))
                .button("§a➡️ 传送至此")
                .button("§e✏️ 改名")
                .button("§c🗑️ 删除此点")
                .button("§7⬅️ 返回列表")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) {
                        World w = Bukkit.getWorld(point.world);
                        if (w != null) {
                            p.teleport(new Location(w, point.x, point.y, point.z, point.yaw, point.pitch));
                            p.sendMessage("§a✅ 传送成功！");
                        }
                    }
                    if (id == 1) {
                        pendingRenamePlayers.put(p.getUniqueId(), index);
                        p.sendMessage("§a请在聊天栏输入新的传送点名称：");
                        p.sendMessage("§7输入 §c取消 §7或 §cno §7以取消操作");
                        return;
                    }
                    if (id == 2) {
                        deletePoint(p, index);
                    }
                    if (id == 3) {
                        openMainUI(p);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== Java版箱子GUI监听 ======================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = e.getView().getTitle();
        Inventory topInv = e.getView().getTopInventory();
        Inventory clickedInv = e.getClickedInventory();

        boolean isPluginGui = (topInv.getHolder() == null);
        if (isPluginGui && clickedInv == topInv) {
            e.setCancelled(true);
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        if (t.equals("§6我的传送点")) {
            if (item.getType() == Material.LIME_DYE) {
                tryCreatePoint(p);
                return;
            }
            if (item.getType() == Material.SKELETON_SKULL) {
                teleportToDeathPoint(p);
                p.closeInventory();
                return;
            }
            if (item.getType() == Material.ENDER_PEARL) {
                openTpDeleteUI(p, e.getSlot());
            }
            return;
        }

        if (t.equals("§6传送点操作")) {
            int idx = selectedPointMap.getOrDefault(p.getUniqueId(), -1);
            if (item.getType() == Material.ENDER_EYE) {
                TpPoint point = getPoints(p).get(idx);
                World w = Bukkit.getWorld(point.world);
                if (w != null) {
                    p.teleport(new Location(w, point.x, point.y, point.z, point.yaw, point.pitch));
                }
                p.closeInventory();
            }
            if (item.getType() == Material.ANVIL) {
                p.closeInventory();
                pendingRenamePlayers.put(p.getUniqueId(), idx);
                p.sendMessage("§a请在聊天栏输入新的传送点名称：");
                p.sendMessage("§7输入 §c取消 §7或 §cno §7以取消操作");
                return;
            }
            if (item.getType() == Material.BARRIER) {
                openDeleteConfirmUI(p, idx);
            }
            return;
        }

        if (t.equals("§c确认删除？")) {
            int idx = selectedPointMap.getOrDefault(p.getUniqueId(), -1);
            if (item.getType() == Material.LIME_CONCRETE) {
                deletePoint(p, idx);
            }
            p.closeInventory();
            openJavaPlayerUI(p);
        }
    }

    // ====================== 聊天拦截改名 ======================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Integer idx = pendingRenamePlayers.remove(player.getUniqueId());
        if (idx == null) return;

        event.setCancelled(true);
        String newName = event.getMessage().trim();

        if (newName.isEmpty()) {
            player.sendMessage("§c名称不能为空！");
            return;
        }

        if (newName.equalsIgnoreCase("取消") || newName.equalsIgnoreCase("no")) {
            player.sendMessage("§c已取消改名！");
            return;
        }

        String finalNewName = newName;
        int finalIdx = idx;
        plugin.getServer().getGlobalRegionScheduler().run(plugin, (task) -> {
            renamePoint(player, finalIdx, finalNewName);
        });
    }
}
