package com.apple.servercore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PersonalSettings implements Listener {

    private final MainPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File settingsFile;

    // 个人设置
    private final Map<UUID, Boolean> receiveAnnounce = new HashMap<>();
    private final Map<UUID, Boolean> quickMenuEnabled = new HashMap<>();
    private final Map<UUID, Boolean> pvpEnabled = new HashMap<>();

    // ========== 传送请求UI开关（默认开启，仅Java版） ==========
    private final Map<UUID, Boolean> tpRequestGuiEnabled = new HashMap<>();

    // ========== 禁止幻翼生成（默认关闭） ==========
    private final Map<UUID, Boolean> disablePhantomSpawn = new HashMap<>();

    // ========== 是否允许被其他玩家骑乘（默认允许） ==========
    private final Map<UUID, Boolean> allowBeRidden = new HashMap<>();

    // ========== 显示实体释放提示（默认开启） ==========
    private final Map<UUID, Boolean> showEntityReleaseHint = new HashMap<>();

    // ========== 实体释放频率检测 ==========
    private final Map<UUID, Long> lastEntityReleaseTime = new HashMap<>();
    private final Map<UUID, Integer> entityReleaseCount = new HashMap<>();

    // 潜行检测
    private final Map<UUID, Integer> sneakCount = new HashMap<>();
    private final Map<UUID, Long> sneakTime = new HashMap<>();

    public PersonalSettings(MainPlugin plugin) {
        this.plugin = plugin;
        this.settingsFile = new File(plugin.getDataFolder(), "player_settings.json");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadSettings();

        // ========== 注册 PlaceholderAPI ==========
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new PVPPlaceholderExpansion(this).register();
                plugin.getLogger().info("✅ PlaceholderAPI 扩展已注册: %pvp%");
            } else {
                plugin.getLogger().warning("⚠️ PlaceholderAPI 未找到，%pvp% 占位符将不可用");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("注册 PlaceholderAPI 扩展失败: " + e.getMessage());
        }
    }

    // ====================== 加载/保存 ======================
    public void loadSettings() {
        if (!settingsFile.exists()) {
            plugin.getLogger().info("§e玩家设置文件不存在，将使用默认设置");
            return;
        }
        try (FileReader reader = new FileReader(settingsFile)) {
            Type type = new TypeToken<Map<String, Map<String, Boolean>>>() {}.getType();
            Map<String, Map<String, Boolean>> data = gson.fromJson(reader, type);
            if (data == null) {
                plugin.getLogger().info("§e玩家设置为空，将使用默认设置");
                return;
            }

            for (String uuidStr : data.keySet()) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Boolean> map = data.get(uuidStr);
                receiveAnnounce.put(uuid, map.getOrDefault("receiveAnnounce", false));
                quickMenuEnabled.put(uuid, map.getOrDefault("quickMenuEnabled", false));
                pvpEnabled.put(uuid, map.getOrDefault("pvpEnabled", false));
                // ========== 加载传送请求UI设置（默认开启） ==========
                tpRequestGuiEnabled.put(uuid, map.getOrDefault("tpRequestGuiEnabled", true));
                // ========== 加载禁止幻翼生成设置（默认关闭） ==========
                disablePhantomSpawn.put(uuid, map.getOrDefault("disablePhantomSpawn", false));
                // ========== 加载是否允许被骑设置（默认允许） ==========
                allowBeRidden.put(uuid, map.getOrDefault("allowBeRidden", true));
                // ========== 加载实体释放提示设置（默认开启） ==========
                showEntityReleaseHint.put(uuid, map.getOrDefault("showEntityReleaseHint", true));
            }
            plugin.getLogger().info("§a玩家设置已加载，共 " + data.size() + " 个玩家");
        } catch (Exception ex) {
            plugin.getLogger().severe("加载设置失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void saveSettings() {
        try {
            if (!settingsFile.exists()) {
                settingsFile.createNewFile();
            }

            // 收集所有有设置的玩家UUID
            java.util.Set<UUID> allUuids = new java.util.HashSet<>();
            allUuids.addAll(receiveAnnounce.keySet());
            allUuids.addAll(quickMenuEnabled.keySet());
            allUuids.addAll(pvpEnabled.keySet());
            allUuids.addAll(tpRequestGuiEnabled.keySet());
            allUuids.addAll(disablePhantomSpawn.keySet());
            allUuids.addAll(allowBeRidden.keySet());
            allUuids.addAll(showEntityReleaseHint.keySet());

            Map<String, Map<String, Boolean>> data = new HashMap<>();
            for (UUID uuid : allUuids) {
                Map<String, Boolean> map = new HashMap<>();
                map.put("receiveAnnounce", receiveAnnounce.getOrDefault(uuid, false));
                map.put("quickMenuEnabled", quickMenuEnabled.getOrDefault(uuid, false));
                map.put("pvpEnabled", pvpEnabled.getOrDefault(uuid, false));
                // ========== 保存传送请求UI设置 ==========
                map.put("tpRequestGuiEnabled", tpRequestGuiEnabled.getOrDefault(uuid, true));
                // ========== 保存禁止幻翼生成设置 ==========
                map.put("disablePhantomSpawn", disablePhantomSpawn.getOrDefault(uuid, false));
                // ========== 保存是否允许被骑设置 ==========
                map.put("allowBeRidden", allowBeRidden.getOrDefault(uuid, true));
                // ========== 保存实体释放提示设置 ==========
                map.put("showEntityReleaseHint", showEntityReleaseHint.getOrDefault(uuid, true));
                data.put(uuid.toString(), map);
            }
            try (FileWriter writer = new FileWriter(settingsFile)) {
                gson.toJson(data, writer);
                writer.flush();
            }
            plugin.getLogger().info("§a玩家设置已保存，共 " + data.size() + " 个玩家");
        } catch (IOException ex) {
            plugin.getLogger().severe("保存设置失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ====================== Getter/Setter ======================
    public boolean isReceiveAnnounce(UUID uuid) { return receiveAnnounce.getOrDefault(uuid, false); }
    public boolean isQuickMenuEnabled(UUID uuid) { return quickMenuEnabled.getOrDefault(uuid, false); }
    public boolean isPvpEnabled(UUID uuid) { return pvpEnabled.getOrDefault(uuid, false); }

    // ========== 传送请求UI开关 ==========
    public boolean isTpRequestGuiEnabled(UUID uuid) {
        return tpRequestGuiEnabled.getOrDefault(uuid, true);
    }
    public void setTpRequestGuiEnabled(UUID uuid, boolean enabled) {
        tpRequestGuiEnabled.put(uuid, enabled);
        saveSettings();
    }
    public void toggleTpRequestGui(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isTpRequestGuiEnabled(uuid);
        tpRequestGuiEnabled.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a传送请求UI已" + (!current ? "§a开启" : "§c关闭"));
    }

    // ========== 禁止幻翼生成开关 ==========
    public boolean isDisablePhantomSpawn(UUID uuid) {
        return disablePhantomSpawn.getOrDefault(uuid, false);
    }
    public void setDisablePhantomSpawn(UUID uuid, boolean enabled) {
        disablePhantomSpawn.put(uuid, enabled);
        saveSettings();
    }
    public void toggleDisablePhantomSpawn(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isDisablePhantomSpawn(uuid);
        disablePhantomSpawn.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a禁止幻翼生成已" + (!current ? "§a开启" : "§c关闭"));
    }

    // ========== 是否允许被其他玩家骑乘 ==========
    public boolean isAllowBeRidden(UUID uuid) {
        return allowBeRidden.getOrDefault(uuid, true);
    }
    public void setAllowBeRidden(UUID uuid, boolean enabled) {
        allowBeRidden.put(uuid, enabled);
        saveSettings();
    }
    public void toggleAllowBeRidden(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isAllowBeRidden(uuid);
        allowBeRidden.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a允许被骑乘已" + (!current ? "§a开启" : "§c关闭"));
    }

    // ========== 实体释放提示开关 ==========
    public boolean isShowEntityReleaseHint(UUID uuid) {
        return showEntityReleaseHint.getOrDefault(uuid, true);
    }
    public void setShowEntityReleaseHint(UUID uuid, boolean enabled) {
        showEntityReleaseHint.put(uuid, enabled);
        saveSettings();
    }
    public void toggleShowEntityReleaseHint(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isShowEntityReleaseHint(uuid);
        showEntityReleaseHint.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a实体释放提示已" + (!current ? "§a开启" : "§c关闭"));
    }

    // ========== 检查实体释放频率并提示 ==========
    public boolean checkAndNotifyEntityRelease(Player p) {
        UUID uuid = p.getUniqueId();
        if (!isShowEntityReleaseHint(uuid)) {
            return false; // 已关闭，不检查
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastEntityReleaseTime.get(uuid);
        Integer count = entityReleaseCount.get(uuid);

        if (lastTime == null || count == null) {
            // 首次或重置
            lastEntityReleaseTime.put(uuid, now);
            entityReleaseCount.put(uuid, 1);
            return true;
        }

        if (now - lastTime <= 1000) {
            // 1秒内
            int newCount = count + 1;
            entityReleaseCount.put(uuid, newCount);
            if (newCount >= 3) {
                // 达到3次，提示关闭
                p.sendMessage("§e⚠ 检测到频繁释放实体，如需关闭提示请在：个人设置 → 实体释放提示 关闭");
                // 重置计数
                lastEntityReleaseTime.remove(uuid);
                entityReleaseCount.remove(uuid);
                return false; // 已达到阈值，不再显示普通提示
            }
            return true;
        } else {
            // 超过1秒，重置
            lastEntityReleaseTime.put(uuid, now);
            entityReleaseCount.put(uuid, 1);
            return true;
        }
    }

    public int getSneakCount(UUID uuid) { return sneakCount.getOrDefault(uuid, 0); }
    public long getSneakTime(UUID uuid) { return sneakTime.getOrDefault(uuid, 0L); }
    public void setSneakCount(UUID uuid, int v) { sneakCount.put(uuid, v); }
    public void setSneakTime(UUID uuid, long v) { sneakTime.put(uuid, v); }

    public void removePlayerData(UUID uuid) {
        // 注意：不移除设置数据（receiveAnnounce/quickMenuEnabled/pvpEnabled/tpRequestGuiEnabled/disablePhantomSpawn）
        // 这些需要在内存中保留到 onDisable 的 saveSettings() 执行，否则重启后设置丢失
        // 只清理缓存/会话数据
        sneakCount.remove(uuid);
        sneakTime.remove(uuid);
    }

    // ====================== 切换设置 ======================
    public void togglePvp(Player p) {
        UUID uuid = p.getUniqueId();
        pvpEnabled.put(uuid, !isPvpEnabled(uuid));
        saveSettings();
    }

    public void toggleAnnounce(Player p) {
        UUID uuid = p.getUniqueId();
        receiveAnnounce.put(uuid, !isReceiveAnnounce(uuid));
        saveSettings();
    }

    public void toggleQuickMenu(Player p) {
        UUID uuid = p.getUniqueId();
        quickMenuEnabled.put(uuid, !isQuickMenuEnabled(uuid));
        sneakCount.put(uuid, 0);
        sneakTime.put(uuid, 0L);
        saveSettings();
    }

    // ====================== PVP事件监听 ======================
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        boolean a = isPvpEnabled(attacker.getUniqueId());
        boolean v = isPvpEnabled(victim.getUniqueId());

        if (!a) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("§c你未开启PVP"));
            return;
        }
        if (!v) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("§c对方未开启PVP"));
            return;
        }
    }

    // ====================== 禁止幻翼生成监听 ======================
    @EventHandler
    public void onPhantomSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != org.bukkit.entity.EntityType.PHANTOM) return;

        org.bukkit.Location loc = e.getLocation();
        for (org.bukkit.entity.Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) <= 64) { // 64格范围内
                if (isDisablePhantomSpawn(p.getUniqueId())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ====================== 加入/退出事件 ======================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        // 新玩家默认开启传送请求UI
        if (!tpRequestGuiEnabled.containsKey(uuid)) {
            tpRequestGuiEnabled.put(uuid, true);
            saveSettings();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // 不在这里移除数据，避免保存丢失
        // removePlayerData(e.getPlayer().getUniqueId());
    }

    // ====================== Java版设置界面 ======================
    public void openSettingsUI(Player p) {
        if (MainPlugin.isBedrockPlayer(p.getUniqueId())) {
            openBedrockSettings(p);
            return;
        }

        UUID uuid = p.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27, "§9个人设置");

        boolean receiveAnn = isReceiveAnnounce(uuid);
        boolean quickMenu = isQuickMenuEnabled(uuid);
        boolean pvp = isPvpEnabled(uuid);
        // ========== 传送请求UI状态 ==========
        boolean tpGui = isTpRequestGuiEnabled(uuid);
        // ========== 禁止幻翼生成状态 ==========
        boolean disablePhantom = isDisablePhantomSpawn(uuid);
        // ========== 骑乘设置状态 ==========
        boolean allowRide = isAllowBeRidden(uuid);

        ItemStack btn2 = new ItemStack(Material.PAPER);
        ItemMeta m2 = btn2.getItemMeta();
        if (m2 != null) {
            m2.setDisplayName("§e接收服务器公告: " + (receiveAnn ? "§a开启" : "§c关闭"));
            m2.setLore(List.of("§7关闭后将不再收到系统广播"));
            btn2.setItemMeta(m2);
        }

        ItemStack btn3 = new ItemStack(Material.FEATHER);
        ItemMeta m3 = btn3.getItemMeta();
        if (m3 != null) {
            m3.setDisplayName("§a快捷打开菜单: " + (quickMenu ? "§a开启" : "§c关闭"));
            m3.setLore(List.of("§72秒内潜行5次打开主菜单"));
            btn3.setItemMeta(m3);
        }

        ItemStack btn4 = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta m4 = btn4.getItemMeta();
        if (m4 != null) {
            m4.setDisplayName("§cPVP模式: " + (pvp ? "§a开启" : "§c关闭"));
            m4.setLore(List.of("§7开启后可以攻击其他玩家"));
            btn4.setItemMeta(m4);
        }

        // ========== 传送请求UI开关按钮 ==========
        ItemStack btn5 = new ItemStack(Material.CHEST);
        ItemMeta m5 = btn5.getItemMeta();
        if (m5 != null) {
            m5.setDisplayName("§b传送请求UI: " + (tpGui ? "§a开启" : "§c关闭"));
            m5.setLore(List.of(
                    "§7开启后收到传送请求时会显示箱子UI",
                    "§7关闭后只显示聊天消息提醒"
            ));
            btn5.setItemMeta(m5);
        }

        // ========== 禁止幻翼生成开关按钮 ==========
        ItemStack btn6 = new ItemStack(Material.PHANTOM_MEMBRANE);
        ItemMeta m6 = btn6.getItemMeta();
        if (m6 != null) {
            m6.setDisplayName("§d禁止幻翼生成: " + (disablePhantom ? "§a开启" : "§c关闭"));
            m6.setLore(List.of(
                    "§7开启后你附近64格内不会刷新幻翼",
                    "§7默认关闭"
            ));
            btn6.setItemMeta(m6);
        }

        // ========== 允许被骑乘开关按钮 ==========
        ItemStack btn7 = new ItemStack(Material.SADDLE);
        ItemMeta m7 = btn7.getItemMeta();
        if (m7 != null) {
            m7.setDisplayName("§6允许被骑乘: " + (allowRide ? "§a开启" : "§c关闭"));
            m7.setLore(List.of(
                    "§7关闭后其他玩家无法骑乘你",
                    "§7默认开启"
            ));
            btn7.setItemMeta(m7);
        }

        // ========== 实体释放提示开关按钮 ==========
        ItemStack btn8 = new ItemStack(Material.ENDER_EYE);
        ItemMeta m8 = btn8.getItemMeta();
        if (m8 != null) {
            boolean showHint = isShowEntityReleaseHint(uuid);
            m8.setDisplayName("§b实体释放提示: " + (showHint ? "§a开启" : "§c关闭"));
            m8.setLore(List.of(
                    "§7开启后释放实体会显示提示",
                    "§7频繁释放会自动提示关闭"
            ));
            btn8.setItemMeta(m8);
        }

        ItemStack backMain = new ItemStack(Material.STONE);
        ItemMeta backMeta = backMain.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§7⬅️ 返回服务器主菜单");
            backMain.setItemMeta(backMeta);
        }

        inv.setItem(12, btn2);
        inv.setItem(14, btn3);
        inv.setItem(16, btn4);
        inv.setItem(20, btn5);
        inv.setItem(22, btn6);
        inv.setItem(21, btn7);
        inv.setItem(23, btn8);
        inv.setItem(26, backMain);
        p.openInventory(inv);
    }

    // ====================== 基岩版设置 ======================
    public void openBedrockSettings(Player p) {
        UUID uuid = p.getUniqueId();
        CustomForm form = CustomForm.builder()
                .title("§9个人设置")
                .toggle("§e 接收服务器公告", isReceiveAnnounce(uuid))
                .toggle("§a 快捷菜单（潜行5次）", isQuickMenuEnabled(uuid))
                .toggle("§c PVP模式", isPvpEnabled(uuid))
                .toggle("§b 传送请求UI", isTpRequestGuiEnabled(uuid))
                .toggle("§d 禁止幻翼生成", isDisablePhantomSpawn(uuid))
                .toggle("§6 允许被骑乘", isAllowBeRidden(uuid))
                .toggle("§b 实体释放提示", isShowEntityReleaseHint(uuid))
                .validResultHandler((CustomFormResponse res) -> {
                    receiveAnnounce.put(uuid, res.getToggle(0));
                    quickMenuEnabled.put(uuid, res.getToggle(1));
                    pvpEnabled.put(uuid, res.getToggle(2));
                    tpRequestGuiEnabled.put(uuid, res.getToggle(3));
                    disablePhantomSpawn.put(uuid, res.getToggle(4));
                    allowBeRidden.put(uuid, res.getToggle(5));
                    showEntityReleaseHint.put(uuid, res.getToggle(6));

                    saveSettings();
                    p.sendMessage("§a✅ 设置已保存！");
                    openBedrockPlayerInfo(p);
                })
                .closedOrInvalidResultHandler(() -> openBedrockPlayerInfo(p))
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== 基岩版个人信息 ======================
    public void openBedrockPlayerInfo(Player p) {
        String name = p.getName();
        // 【已删除】称号相关变量
        int acCoins = plugin.economicSystem.getAcCoins(p);
        boolean pvp = isPvpEnabled(p.getUniqueId());

        String content = """
                §6===== 个人信息 =====
                §f玩家: §a%s
                §fAC币: §6%s
                §fPVP: %s
                §7=====================""".formatted(
                name,
                acCoins,
                pvp ? "§a已开启" : "§c已关闭"
        );

        SimpleForm form = SimpleForm.builder()
                .title("§f个人信息中心")
                .content(content)
                // 【已删除】"切换称号" 和 "显示/隐藏称号" 按钮
                .button("§b我的传送点")
                .button("§9个人设置")
                .button("§7⬅️ 返回主菜单")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    switch (id) {
                        // case 0 和 case 1 已被删除，原来的 case 2 变成 case 0
                        case 0 -> plugin.tpAsMePoint.openPlayerUI(p);
                        case 1 -> openSettingsUI(p);
                        case 2 -> plugin.getACcraft().openMainMenu(p);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== GUI点击处理 ======================
    public void handleClick(Player p, ItemStack cur) {
        if (cur == null || !cur.hasItemMeta()) return;
        String name = cur.getItemMeta().getDisplayName();

        if (name.equals("§7⬅️ 返回服务器主菜单")) {
            p.closeInventory();
            plugin.getACcraft().openMainMenu(p);
            return;
        }

        switch (cur.getType()) {
            case PAPER -> toggleAnnounce(p);
            case FEATHER -> toggleQuickMenu(p);
            case DIAMOND_SWORD -> togglePvp(p);
            case CHEST -> toggleTpRequestGui(p);
            case PHANTOM_MEMBRANE -> toggleDisablePhantomSpawn(p);
            case SADDLE -> toggleAllowBeRidden(p);
            case ENDER_EYE -> toggleShowEntityReleaseHint(p);
        }
        openSettingsUI(p);
    }

    // ==================== PlaceholderAPI 扩展 ====================
    public class PVPPlaceholderExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private final PersonalSettings settings;

        public PVPPlaceholderExpansion(PersonalSettings settings) {
            this.settings = settings;
        }

        @Override
        public String getIdentifier() {
            return "pvp";
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

            // %pvp% - 返回玩家PVP状态 (开启/关闭)
            if (identifier == null || identifier.isEmpty()) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "开启" : "关闭";
            }

            // %pvp_status% - 返回玩家PVP状态 (开启/关闭) - 别名
            if (identifier.equalsIgnoreCase("status")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "开启" : "关闭";
            }

            // %pvp_enabled% - 返回玩家PVP是否开启 (true/false)
            if (identifier.equalsIgnoreCase("enabled")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "true" : "false";
            }

            // %pvp_color% - 返回带颜色的PVP状态 (§a开启 / §c关闭)
            if (identifier.equalsIgnoreCase("color")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "§a开启" : "§c关闭";
            }

            return null;
        }
    }
}