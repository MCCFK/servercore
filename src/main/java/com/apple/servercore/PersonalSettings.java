package com.apple.servercore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
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
                tpRequestGuiEnabled.put(uuid, map.getOrDefault("tpRequestGuiEnabled", true));
                disablePhantomSpawn.put(uuid, map.getOrDefault("disablePhantomSpawn", false));
                allowBeRidden.put(uuid, map.getOrDefault("allowBeRidden", true));
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

            java.util.Set<UUID> allUuids = new java.util.HashSet<>();
            allUuids.addAll(receiveAnnounce.keySet());
            allUuids.addAll(quickMenuEnabled.keySet());
            allUuids.addAll(pvpEnabled.keySet());
            allUuids.addAll(tpRequestGuiEnabled.keySet());
            allUuids.addAll(disablePhantomSpawn.keySet());
            allUuids.addAll(allowBeRidden.keySet());

            Map<String, Map<String, Boolean>> data = new HashMap<>();
            for (UUID uuid : allUuids) {
                Map<String, Boolean> map = new HashMap<>();
                map.put("receiveAnnounce", receiveAnnounce.getOrDefault(uuid, false));
                map.put("quickMenuEnabled", quickMenuEnabled.getOrDefault(uuid, false));
                map.put("pvpEnabled", pvpEnabled.getOrDefault(uuid, false));
                map.put("tpRequestGuiEnabled", tpRequestGuiEnabled.getOrDefault(uuid, true));
                map.put("disablePhantomSpawn", disablePhantomSpawn.getOrDefault(uuid, false));
                map.put("allowBeRidden", allowBeRidden.getOrDefault(uuid, true));
                data.put(uuid.toString(), map);
            }
            try (FileWriter writer = new FileWriter(settingsFile)) {
                gson.toJson(data, writer);
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
    public boolean isTpRequestGuiEnabled(UUID uuid) { return tpRequestGuiEnabled.getOrDefault(uuid, true); }
    public boolean isDisablePhantomSpawn(UUID uuid) { return disablePhantomSpawn.getOrDefault(uuid, false); }
    public boolean isAllowBeRidden(UUID uuid) { return allowBeRidden.getOrDefault(uuid, true); }

    public void setTpRequestGuiEnabled(UUID uuid, boolean enabled) {
        tpRequestGuiEnabled.put(uuid, enabled);
        saveSettings();
    }
    public void setDisablePhantomSpawn(UUID uuid, boolean enabled) {
        disablePhantomSpawn.put(uuid, enabled);
        saveSettings();
    }
    public void setAllowBeRidden(UUID uuid, boolean enabled) {
        allowBeRidden.put(uuid, enabled);
        saveSettings();
    }

    public void toggleTpRequestGui(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isTpRequestGuiEnabled(uuid);
        tpRequestGuiEnabled.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a传送请求UI已" + (!current ? "§a开启" : "§c关闭"));
    }

    public void toggleDisablePhantomSpawn(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isDisablePhantomSpawn(uuid);
        disablePhantomSpawn.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a禁止幻翼生成已" + (!current ? "§a开启" : "§c关闭"));
    }

    public void toggleAllowBeRidden(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = isAllowBeRidden(uuid);
        allowBeRidden.put(uuid, !current);
        saveSettings();
        p.sendMessage("§a允许被骑乘已" + (!current ? "§a开启" : "§c关闭"));
    }

    public int getSneakCount(UUID uuid) { return sneakCount.getOrDefault(uuid, 0); }
    public long getSneakTime(UUID uuid) { return sneakTime.getOrDefault(uuid, 0L); }
    public void setSneakCount(UUID uuid, int v) { sneakCount.put(uuid, v); }
    public void setSneakTime(UUID uuid, long v) { sneakTime.put(uuid, v); }

    public void removePlayerData(UUID uuid) {
        sneakCount.remove(uuid);
        sneakTime.remove(uuid);
    }

    // ====================== 切换设置 ======================
    public void togglePvp(Player p) {
        UUID uuid = p.getUniqueId();
        pvpEnabled.put(uuid, !isPvpEnabled(uuid));
        saveSettings();
        p.sendMessage("§aPVP模式已" + (isPvpEnabled(uuid) ? "§a开启" : "§c关闭"));
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

    // ====================== 检查是否可以攻击玩家 ======================
    private boolean canAttack(Player attacker, Player victim) {
        boolean a = isPvpEnabled(attacker.getUniqueId());
        boolean v = isPvpEnabled(victim.getUniqueId());
        return a && v;
    }

    private void sendNoPvpMessage(Player attacker, String reason) {
        attacker.sendActionBar(Component.text("§c" + reason));
    }

    // ====================== 安全距离计算 ======================
    /**
     * 安全计算两个位置的距离，如果不在同一世界则返回 Double.MAX_VALUE
     */
    private double safeDistance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return Double.MAX_VALUE;
        if (!loc1.getWorld().equals(loc2.getWorld())) return Double.MAX_VALUE;
        return loc1.distance(loc2);
    }

    // ====================== PVP事件监听 - 近战攻击 ======================
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        // 检查攻击者是否是玩家
        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) e.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        } else if (e.getDamager() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) e.getDamager();
            if (tnt.getSource() instanceof Player) {
                attacker = (Player) tnt.getSource();
            }
        }

        // 如果不是玩家造成的伤害，放行
        if (attacker == null) return;

        // 检查受害者是否是玩家
        if (!(e.getEntity() instanceof Player victim)) return;

        // 检查PVP是否开启
        if (!canAttack(attacker, victim)) {
            e.setCancelled(true);
            if (!isPvpEnabled(attacker.getUniqueId())) {
                sendNoPvpMessage(attacker, "你未开启PVP！");
            } else {
                sendNoPvpMessage(attacker, "对方未开启PVP！");
            }
        }
    }

    // ====================== PVP事件监听 - 弓箭等投射物 ======================
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        if (!(projectile.getShooter() instanceof Player attacker)) return;

        if (!(e.getHitEntity() instanceof Player victim)) return;

        // 让 EntityDamageByEntityEvent 来处理伤害取消
        // 但我们需要在投射物命中前就取消，防止箭矢附着
        if (!canAttack(attacker, victim)) {
            e.setCancelled(true);
            projectile.remove();
            if (!isPvpEnabled(attacker.getUniqueId())) {
                sendNoPvpMessage(attacker, "你未开启PVP！");
            } else {
                sendNoPvpMessage(attacker, "对方未开启PVP！");
            }
        }
    }

    // ====================== PVP事件监听 - 药水效果 ======================
    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        // 检查药水投掷者是否是玩家
        if (!(e.getEntity().getShooter() instanceof Player attacker)) return;

        // 获取药水效果
        java.util.Collection<org.bukkit.potion.PotionEffect> effects = e.getEntity().getEffects();

        // 如果没有效果，直接放行（可能是水瓶子）
        if (effects == null || effects.isEmpty()) return;

        // 检查是否有伤害性效果
        boolean hasHarmfulEffect = false;
        for (org.bukkit.potion.PotionEffect effect : effects) {
            if (isHarmfulPotion(effect.getType())) {
                hasHarmfulEffect = true;
                break;
            }
        }

        // 只有伤害性药水才进行PVP检查
        if (!hasHarmfulEffect) return;

        // 检查药水影响的实体
        for (LivingEntity entity : e.getAffectedEntities()) {
            if (entity instanceof Player victim) {
                // 不要检查自己
                if (victim.equals(attacker)) continue;

                if (!canAttack(attacker, victim)) {
                    e.setCancelled(true);
                    // 移除药水
                    e.getEntity().remove();
                    if (!isPvpEnabled(attacker.getUniqueId())) {
                        sendNoPvpMessage(attacker, "你未开启PVP！");
                    } else {
                        sendNoPvpMessage(attacker, "对方未开启PVP！");
                    }
                    return;
                }
            }
        }
    }

    /**
     * 判断是否为伤害性药水效果
     */
    private boolean isHarmfulPotion(org.bukkit.potion.PotionEffectType type) {
        if (type == null) return false;

        String name = type.getKey().getKey().toLowerCase();
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }

        switch (name) {
            case "instant_damage":
            case "poison":
            case "wither":
            case "weakness":
            case "slowness":
            case "hunger":
            case "nausea":
            case "blindness":
            case "levitation":
            case "unluck":
            case "darkness":
            case "bad_omen":
            case "trial_omen":
            case "wind_charged":
            case "weaving":
            case "oozing":
            case "infested":
            case "raid_omen":
                return true;
            default:
                return false;
        }
    }

    // ====================== PVP事件监听 - 范围伤害（爆炸等） ======================
    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        DamageCause cause = e.getCause();

        // 检查是否是范围伤害（爆炸、火焰等）
        boolean isAreaDamage = cause == DamageCause.ENTITY_EXPLOSION ||
                cause == DamageCause.BLOCK_EXPLOSION ||
                cause == DamageCause.FIRE ||
                cause == DamageCause.FIRE_TICK ||
                cause == DamageCause.LAVA ||
                cause == DamageCause.HOT_FLOOR ||
                cause == DamageCause.MAGIC;

        // 如果是环境伤害，检查是否由玩家引起
        if (isAreaDamage) {
            // 检查受害者附近是否有玩家（只检查同一世界的玩家）
            for (Player nearby : Bukkit.getOnlinePlayers()) {
                if (nearby.equals(victim)) continue;
                // 使用安全距离计算，避免跨世界异常
                if (safeDistance(nearby.getLocation(), victim.getLocation()) < 10) {
                    if (!canAttack(nearby, victim)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    // ====================== PVP事件监听 - 玩家交互（放置岩浆等） ======================
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player attacker = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;

        Material type = item.getType();

        // 检查是否在放置可能伤害玩家的方块
        boolean isDangerous = type == Material.LAVA_BUCKET ||
                type == Material.FLINT_AND_STEEL ||
                type == Material.FIRE_CHARGE ||
                type == Material.TNT ||
                type == Material.RESPAWN_ANCHOR;

        if (!isDangerous) return;

        // 检查目标位置附近是否有玩家
        var targetBlock = e.getClickedBlock();
        if (targetBlock == null) return;

        for (Player victim : Bukkit.getOnlinePlayers()) {
            if (victim.equals(attacker)) continue;
            // 使用安全距离计算，避免跨世界异常
            if (safeDistance(victim.getLocation(), targetBlock.getLocation()) < 5) {
                if (!canAttack(attacker, victim)) {
                    e.setCancelled(true);
                    if (!isPvpEnabled(attacker.getUniqueId())) {
                        sendNoPvpMessage(attacker, "你未开启PVP！");
                    } else {
                        sendNoPvpMessage(attacker, "对方未开启PVP！");
                    }
                    return;
                }
            }
        }
    }

    // ====================== PVP事件监听 - 玩家放置TNT ======================
    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.TNT) return;
        if (!(e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG)) return;

        Location tntLoc = e.getLocation();

        // 检查TNT周围是否有未开启PVP的玩家
        for (Player victim : Bukkit.getOnlinePlayers()) {
            // 使用安全距离计算，避免跨世界异常
            if (safeDistance(victim.getLocation(), tntLoc) < 10) {
                // 检查是否有玩家在附近放置了TNT
                for (Player attacker : Bukkit.getOnlinePlayers()) {
                    if (attacker.equals(victim)) continue;
                    if (!canAttack(attacker, victim)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    // ====================== PVP事件监听 - 雪球/鸡蛋击退 ======================
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        Projectile proj = e.getEntity();
        if (!(proj.getShooter() instanceof Player attacker)) return;

        // 检查是否是雪球或鸡蛋（可以用来造成击退）
        if (proj instanceof Snowball || proj instanceof Egg) {
            // 这些投射物本身不造成伤害，但可能导致摔落伤害
            // 我们标记它们，如果命中玩家且PVP未开启则取消
            // 实际取消在 ProjectileHitEvent 中处理
        }
    }

    // ====================== PVP事件监听 - 摔落伤害检查 ======================
    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // 检查是否因为其他玩家的攻击导致摔落
        for (Player attacker : Bukkit.getOnlinePlayers()) {
            if (attacker.equals(victim)) continue;
            // 使用安全距离计算，避免跨世界异常
            if (safeDistance(attacker.getLocation(), victim.getLocation()) < 10) {
                if (!canAttack(attacker, victim)) {
                    // 不取消摔落伤害，因为可能是自然摔落
                    // 只在确定是玩家导致的情况下取消
                }
            }
        }
    }

    // ====================== 禁止幻翼生成监听 ======================
    @EventHandler
    public void onPhantomSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.PHANTOM) return;

        Location loc = e.getLocation();
        for (Player p : loc.getWorld().getPlayers()) {
            // 使用安全距离计算，避免跨世界异常
            if (safeDistance(p.getLocation(), loc) <= 64) {
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
        if (!tpRequestGuiEnabled.containsKey(uuid)) {
            tpRequestGuiEnabled.put(uuid, true);
            saveSettings();
        }
        if (!allowBeRidden.containsKey(uuid)) {
            allowBeRidden.put(uuid, true);
            saveSettings();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // 不在这里移除数据，避免保存丢失
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
        boolean tpGui = isTpRequestGuiEnabled(uuid);
        boolean disablePhantom = isDisablePhantomSpawn(uuid);
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
            m4.setLore(List.of(
                    "§7开启后可以攻击其他玩家",
                    "§7包括近战、弓箭、药水、岩浆等"
            ));
            btn4.setItemMeta(m4);
        }

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
                .validResultHandler((CustomFormResponse res) -> {
                    receiveAnnounce.put(uuid, res.getToggle(0));
                    quickMenuEnabled.put(uuid, res.getToggle(1));
                    pvpEnabled.put(uuid, res.getToggle(2));
                    tpRequestGuiEnabled.put(uuid, res.getToggle(3));
                    disablePhantomSpawn.put(uuid, res.getToggle(4));
                    allowBeRidden.put(uuid, res.getToggle(5));

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
                .button("§b我的传送点")
                .button("§9个人设置")
                .button("§7⬅️ 返回主菜单")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    switch (id) {
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

            if (identifier == null || identifier.isEmpty()) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "开启" : "关闭";
            }

            if (identifier.equalsIgnoreCase("status")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "开启" : "关闭";
            }

            if (identifier.equalsIgnoreCase("enabled")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "true" : "false";
            }

            if (identifier.equalsIgnoreCase("color")) {
                boolean enabled = settings.isPvpEnabled(player.getUniqueId());
                return enabled ? "§a开启" : "§c关闭";
            }

            return null;
        }
    }
}