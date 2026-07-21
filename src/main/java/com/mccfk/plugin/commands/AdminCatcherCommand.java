package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;

public class AdminCatcherCommand implements CommandExecutor, Listener {

    private final MainPlugin plugin;
    private final NamespacedKey adminCatcherFlagKey;
    private final CarryCommand carryCommand;
    private final Gson gson;
    // 已捕捉实体ID集合（防 PlayerInteractEvent 和 PlayerInteractEntityEvent 重复捕捉）
    private final Set<Integer> capturedThisTick = new HashSet<>();

    // ========== 玩家捕捉相关 ==========
    private final NamespacedKey playerCaptureFlagKey;
    private final NamespacedKey playerCaptureTargetKey;
    private final NamespacedKey playerCaptureCatcherKey;
    private final NamespacedKey playerCaptureInvalidKey;

    // 被捕捉玩家UUID -> 捕捉数据
    private final Map<UUID, PlayerCaptureData> capturedPlayers = new HashMap<>();

    // 玩家离线后等待重进恢复的数据
    private final Map<UUID, PlayerCaptureData> pendingRestorePlayers = new HashMap<>();

    // 无生物蛋实体的对应物品贴图
    private static final Map<String, Material> FALLBACK_MATERIALS = new HashMap<>();
    static {
        // 矿车
        FALLBACK_MATERIALS.put("minecart", Material.MINECART);
        FALLBACK_MATERIALS.put("chest_minecart", Material.CHEST_MINECART);
        FALLBACK_MATERIALS.put("furnace_minecart", Material.FURNACE_MINECART);
        FALLBACK_MATERIALS.put("hopper_minecart", Material.HOPPER_MINECART);
        FALLBACK_MATERIALS.put("tnt_minecart", Material.TNT_MINECART);
        FALLBACK_MATERIALS.put("command_block_minecart", Material.COMMAND_BLOCK_MINECART);
        FALLBACK_MATERIALS.put("spawner_minecart", Material.SPAWNER);
        // 盔甲架
        FALLBACK_MATERIALS.put("armor_stand", Material.ARMOR_STAND);
        // 船
        FALLBACK_MATERIALS.put("oak_boat", Material.OAK_BOAT);
        FALLBACK_MATERIALS.put("spruce_boat", Material.SPRUCE_BOAT);
        FALLBACK_MATERIALS.put("birch_boat", Material.BIRCH_BOAT);
        FALLBACK_MATERIALS.put("jungle_boat", Material.JUNGLE_BOAT);
        FALLBACK_MATERIALS.put("acacia_boat", Material.ACACIA_BOAT);
        FALLBACK_MATERIALS.put("dark_oak_boat", Material.DARK_OAK_BOAT);
        FALLBACK_MATERIALS.put("mangrove_boat", Material.MANGROVE_BOAT);
        FALLBACK_MATERIALS.put("cherry_boat", Material.CHERRY_BOAT);
        FALLBACK_MATERIALS.put("bamboo_raft", Material.BAMBOO_RAFT);
        FALLBACK_MATERIALS.put("oak_chest_boat", Material.OAK_CHEST_BOAT);
        FALLBACK_MATERIALS.put("spruce_chest_boat", Material.SPRUCE_CHEST_BOAT);
        FALLBACK_MATERIALS.put("birch_chest_boat", Material.BIRCH_CHEST_BOAT);
        FALLBACK_MATERIALS.put("jungle_chest_boat", Material.JUNGLE_CHEST_BOAT);
        FALLBACK_MATERIALS.put("acacia_chest_boat", Material.ACACIA_CHEST_BOAT);
        FALLBACK_MATERIALS.put("dark_oak_chest_boat", Material.DARK_OAK_CHEST_BOAT);
        FALLBACK_MATERIALS.put("mangrove_chest_boat", Material.MANGROVE_CHEST_BOAT);
        FALLBACK_MATERIALS.put("cherry_chest_boat", Material.CHERRY_CHEST_BOAT);
        FALLBACK_MATERIALS.put("bamboo_chest_raft", Material.BAMBOO_CHEST_RAFT);
        // 展示框 & 画
        FALLBACK_MATERIALS.put("item_frame", Material.ITEM_FRAME);
        FALLBACK_MATERIALS.put("glow_item_frame", Material.GLOW_ITEM_FRAME);
        FALLBACK_MATERIALS.put("painting", Material.PAINTING);
        FALLBACK_MATERIALS.put("leash_knot", Material.LEAD);
        // 抛射物 & 物品
        FALLBACK_MATERIALS.put("experience_orb", Material.EXPERIENCE_BOTTLE);
        FALLBACK_MATERIALS.put("experience_bottle", Material.EXPERIENCE_BOTTLE);
        FALLBACK_MATERIALS.put("tnt", Material.TNT);
        FALLBACK_MATERIALS.put("ender_pearl", Material.ENDER_PEARL);
        FALLBACK_MATERIALS.put("eye_of_ender", Material.ENDER_EYE);
        FALLBACK_MATERIALS.put("egg", Material.EGG);
        FALLBACK_MATERIALS.put("snowball", Material.SNOWBALL);
        FALLBACK_MATERIALS.put("arrow", Material.ARROW);
        FALLBACK_MATERIALS.put("spectral_arrow", Material.SPECTRAL_ARROW);
        FALLBACK_MATERIALS.put("trident", Material.TRIDENT);
        // 火球 & 头颅
        FALLBACK_MATERIALS.put("fireball", Material.FIRE_CHARGE);
        FALLBACK_MATERIALS.put("small_fireball", Material.FIRE_CHARGE);
        FALLBACK_MATERIALS.put("dragon_fireball", Material.DRAGON_BREATH);
        FALLBACK_MATERIALS.put("wither_skull", Material.WITHER_SKELETON_SKULL);
        FALLBACK_MATERIALS.put("shulker_bullet", Material.SHULKER_SHELL);
        FALLBACK_MATERIALS.put("llama_spit", Material.LLAMA_SPAWN_EGG);
        // 其他技术性实体
        FALLBACK_MATERIALS.put("falling_block", Material.SAND);
        FALLBACK_MATERIALS.put("area_effect_cloud", Material.GLASS_BOTTLE);
        FALLBACK_MATERIALS.put("fishing_bobber", Material.FISHING_ROD);
        FALLBACK_MATERIALS.put("marker", Material.BARRIER);
        FALLBACK_MATERIALS.put("interaction", Material.BARRIER);
        FALLBACK_MATERIALS.put("text_display", Material.OAK_SIGN);
        FALLBACK_MATERIALS.put("block_display", Material.COMMAND_BLOCK);
        FALLBACK_MATERIALS.put("item_display", Material.COMMAND_BLOCK);
        FALLBACK_MATERIALS.put("lightning_bolt", Material.NETHER_STAR);
    }

    public AdminCatcherCommand(MainPlugin plugin, CarryCommand carryCommand) {
        this.plugin = plugin;
        this.carryCommand = carryCommand;
        this.adminCatcherFlagKey = new NamespacedKey(plugin, "admin_catcher_flag");
        this.playerCaptureFlagKey = new NamespacedKey(plugin, "admin_player_capture");
        this.playerCaptureTargetKey = new NamespacedKey(plugin, "admin_player_capture_target");
        this.playerCaptureCatcherKey = new NamespacedKey(plugin, "admin_player_capture_catcher");
        this.playerCaptureInvalidKey = new NamespacedKey(plugin, "admin_player_capture_invalid");
        this.gson = new GsonBuilder().create();

        // 每tick清理已捕捉记录
        plugin.getServer().getScheduler().runTaskTimer(plugin, capturedThisTick::clear, 1L, 1L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！/ This command can only be executed by players!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§c你没有权限使用此指令！/ You don't have permission to use this command!");
            return true;
        }

        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;

        meta.setDisplayName("§c§l管理员生物捕捉器");

        List<String> lore = new ArrayList<>();
        lore.add("§7可捕捉一切实体，无视黑名单与生物蛋限制");
        lore.add("");
        lore.add("§c仅管理员可用");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(adminCatcherFlagKey, PersistentDataType.BYTE, (byte) 1);

        // 附魔光效以示区别
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), item);
        }

        player.sendMessage("§a已获得管理员生物捕捉器！/ Admin catcher obtained!");
        return true;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        // 仅OP可使用
        if (!player.isOp()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(adminCatcherFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        Entity target = event.getRightClicked();

        // 捕捉玩家（管理员版实体捕捉器）
        if (target instanceof Player targetPlayer) {
            capturePlayer(player, targetPlayer);
            return;
        }

        captureEntity(player, target);
    }

    // ========== 射线追踪兜底（捕捉 PlayerInteractEntityEvent 无法触发的实体） ==========

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(adminCatcherFlagKey, PersistentDataType.BYTE)) return;

        // 射线追踪查找附近实体
        RayTraceResult result = player.rayTraceEntities(10, false);
        if (result == null || result.getHitEntity() == null) return;

        Entity target = result.getHitEntity();

        // 防重复捕捉（PlayerInteractEntityEvent 可能已处理同一实体）
        if (capturedThisTick.contains(target.getEntityId())) return;

        if (target instanceof Player targetPlayer) {
            event.setCancelled(true);
            capturePlayer(player, targetPlayer);
            return;
        }

        event.setCancelled(true);
        captureEntity(player, target);
    }

    /**
     * 捕捉实体的核心逻辑
     */
    private void captureEntity(Player player, Entity target) {
        String entityKey = target.getType().getKey().getKey();

        // 确定容器物品材质：有生物蛋用生物蛋，没有则用对应贴图，再无则村民刷怪蛋兜底
        Material containerMat = getContainerMaterial(target.getType());

        try {
            String nbtString;
            if (target instanceof LivingEntity living) {
                // LivingEntity：完整 NBT 序列化
                nbtString = carryCommand.serializeEntityFullNbt(living);
            } else {
                // 非生物实体：基础数据序列化
                nbtString = serializeNonLivingEntity(target);
            }

            ItemStack egg = new ItemStack(containerMat);
            ItemMeta eggMeta = egg.getItemMeta();
            if (eggMeta == null) {
                player.sendMessage("§c创建捕捉蛋失败！/ Failed to create capture egg!");
                return;
            }

            // 使用与 CarryCommand 相同的键，放置时会被 CarryCommand 的监听器处理
            eggMeta.getPersistentDataContainer().set(carryCommand.entityNbtKey, PersistentDataType.STRING, nbtString);
            eggMeta.getPersistentDataContainer().set(carryCommand.entityTypeKey, PersistentDataType.STRING, entityKey);
            eggMeta.getPersistentDataContainer().set(carryCommand.carryFlagKey, PersistentDataType.BYTE, (byte) 1);

            eggMeta.setDisplayName("§c§l[管理员] §6" + formatEntityName(target));

            List<String> lore = new ArrayList<>();
            if (target instanceof LivingEntity living) {
                double health = living.getHealth();
                double maxHealth = living.getMaxHealth();
                lore.add("§7生命: §a" + String.format("%.1f", health) + "§7/§a" + String.format("%.0f", maxHealth));
            }
            lore.add("");
            lore.add("§7右键点击以释放");
            lore.add("§8[" + entityKey + "]");
            eggMeta.setLore(lore);

            egg.setItemMeta(eggMeta);

            // 移除原实体
            target.remove();

            // 给予玩家
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(egg);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), egg);
            }

            player.sendMessage("§a已捕捉 " + formatEntityName(target) + " §a！/ Captured!");
            plugin.getLogger().info("§c[管理员捕捉器] 玩家 " + player.getName() + " 捕捉了 " + entityKey);

            // 记录已捕捉实体ID，防止 PlayerInteractEvent 重复捕捉
            capturedThisTick.add(target.getEntityId());

        } catch (Exception e) {
            player.sendMessage("§c捕捉失败: " + e.getMessage());
            plugin.getLogger().severe("§c[管理员捕捉器] 捕捉失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== 玩家捕捉核心逻辑 ==========

    /**
     * 捕捉玩家：传送到高空+缓降，生成村民蛋
     */
    private void capturePlayer(Player catcher, Player target) {
        if (capturedPlayers.containsKey(target.getUniqueId())) {
            catcher.sendMessage("§c该玩家已被捕捉！");
            return;
        }

        Location originalLoc = target.getLocation();

        // 传送到高空并冻结（禁重力+缓降防止掉落伤害）
        Location highLoc = new Location(target.getWorld(), originalLoc.getX(), 2000, originalLoc.getZ(), originalLoc.getYaw(), originalLoc.getPitch());
        target.teleport(highLoc);
        target.setGravity(false);
        target.setWalkSpeed(0);
        target.setFlySpeed(0);
        target.setAllowFlight(false);
        target.setInvulnerable(true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
        target.sendMessage("§c§l你被管理员捕捉了！5分钟后自动释放。");

        // 创建村民刷怪蛋
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta eggMeta = egg.getItemMeta();
        if (eggMeta == null) {
            catcher.sendMessage("§c创建捕捉蛋失败！");
            return;
        }

        eggMeta.getPersistentDataContainer().set(playerCaptureFlagKey, PersistentDataType.BYTE, (byte) 1);
        eggMeta.getPersistentDataContainer().set(playerCaptureTargetKey, PersistentDataType.STRING, target.getUniqueId().toString());
        eggMeta.getPersistentDataContainer().set(playerCaptureCatcherKey, PersistentDataType.STRING, catcher.getUniqueId().toString());

        eggMeta.setDisplayName("§c§l[管理员] §6" + target.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7类型: 玩家");
        lore.add("§7玩家: §e" + target.getName());
        lore.add("");
        lore.add("§7§o右键点击释放该玩家");
        lore.add("§c5分钟后自动释放并复位");
        eggMeta.setLore(lore);
        egg.setItemMeta(eggMeta);

        // 启动5分钟倒计时主任务
        int mainTaskId = startCaptureTimer(catcher, target, egg, originalLoc);

        // 记录捕捉数据
        capturedPlayers.put(target.getUniqueId(), new PlayerCaptureData(originalLoc, catcher.getUniqueId(), mainTaskId));

        // 给予捕捉者蛋
        Map<Integer, ItemStack> leftover = catcher.getInventory().addItem(egg);
        if (!leftover.isEmpty()) {
            catcher.getWorld().dropItem(catcher.getLocation(), egg);
        }

        catcher.sendMessage("§a已捕捉玩家 §e" + target.getName() + " §a！你有5分钟时间使用此物品。");
        plugin.getLogger().info("§c[管理员捕捉器] 管理员 " + catcher.getName() + " 捕捉了玩家 " + target.getName());
    }

    /**
     * 启动5分钟倒计时主任务
     */
    private int startCaptureTimer(Player catcher, Player target, ItemStack egg, Location originalLoc) {
        BukkitRunnable task = new BukkitRunnable() {
            int remaining = 300; // 5分钟 = 300秒

            @Override
            public void run() {
                if (!target.isOnline()) {
                    // 玩家已离线，清理数据
                    cleanupCapture(target);
                    this.cancel();
                    return;
                }

                // 倒计时提示
                if (remaining == 60) {
                    target.sendMessage("§e⚠ 还有1分钟自动释放！");
                    catcher.sendMessage("§e⚠ 还有1分钟自动释放玩家！");
                } else if (remaining == 30) {
                    target.sendMessage("§e⚠ 还有30秒自动释放！");
                    catcher.sendMessage("§e⚠ 还有30秒自动释放玩家！");
                } else if (remaining == 10) {
                    target.sendMessage("§e⚠ 还有10秒自动释放！");
                    catcher.sendMessage("§e⚠ 还有10秒自动释放玩家！");
                } else if (remaining <= 5 && remaining > 0) {
                    String msg = "§c§l" + remaining + "";
                    target.sendMessage(msg);
                    catcher.sendMessage(msg);
                }

                if (remaining <= 0) {
                    // 时间到，自动释放（复位到原始位置）
                    releasePlayer(target, false, originalLoc);
                    this.cancel();
                    return;
                }

                remaining--;
            }
        };

        int taskId = task.runTaskTimer(plugin, 0L, 20L).getTaskId();
        return taskId;
    }

    /**
     * 右键玩家捕捉蛋 → 释放玩家
     */
    @EventHandler
    public void onPlayerCaptureEggInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(playerCaptureFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        // 检查是否已失效
        if (pdc.has(playerCaptureInvalidKey, PersistentDataType.BYTE)) {
            player.sendMessage("§c这个捕捉蛋已经失效了！");
            return;
        }

        // 检查使用者是否原捕捉者
        String catcherUuidStr = pdc.get(playerCaptureCatcherKey, PersistentDataType.STRING);
        if (catcherUuidStr == null || !catcherUuidStr.equals(player.getUniqueId().toString())) {
            player.sendMessage("§c你不是这个捕捉蛋的原持有者，无法释放！");
            return;
        }

        // 获取被捕捉玩家UUID
        String targetUuidStr = pdc.get(playerCaptureTargetKey, PersistentDataType.STRING);
        if (targetUuidStr == null) {
            player.sendMessage("§c捕捉蛋数据损坏！");
            return;
        }

        UUID targetUuid = UUID.fromString(targetUuidStr);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§c被捕捉的玩家已离线！");
            return;
        }

        // 计算释放位置（放在蛋放置的位置，而非复位到原始位置）
        Location spawnLoc;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            BlockFace face = event.getBlockFace();
            if (clickedBlock == null) return;
            spawnLoc = clickedBlock.getRelative(face).getLocation().add(0.5, 0, 0.5);
        } else {
            spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2));
        }

        // 手动释放（放在蛋位置）
        releasePlayer(target, true, spawnLoc);

        // 消耗物品
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * 掉落物标记失效 → 立即释放被捕捉玩家
     */
    @EventHandler
    public void onPlayerCaptureEggDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(playerCaptureFlagKey, PersistentDataType.BYTE)) return;

        // 先获取被捕捉玩家UUID（在标记失效前读取）
        String targetUuidStr = pdc.get(playerCaptureTargetKey, PersistentDataType.STRING);

        // 标记为失效
        pdc.set(playerCaptureInvalidKey, PersistentDataType.BYTE, (byte) 1);
        meta.setDisplayName("§8§m[已失效] §7" + (meta.hasDisplayName() ? meta.getDisplayName() : ""));
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        lore.add("");
        lore.add("§c§l已失效");
        meta.setLore(lore);
        item.setItemMeta(meta);
        event.getItemDrop().setItemStack(item);

        // 让掉落物发光显示
        event.getItemDrop().setGlowing(true);

        // 立即释放被捕捉玩家（蛋已失效，不能让他一直挂天上）
        if (targetUuidStr == null) return;
        try {
            UUID targetUuid = UUID.fromString(targetUuidStr);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null || !target.isOnline()) return;

            PlayerCaptureData data = capturedPlayers.remove(targetUuid);
            if (data == null) return;

            // 取消定时任务
            Bukkit.getScheduler().cancelTask(data.mainTaskId);

            // 复位到原始位置
            Location originalLoc = data.getLocation();
            if (originalLoc != null) {
                target.teleport(originalLoc);
            }

            // 移除效果
            target.removePotionEffect(PotionEffectType.SLOW_FALLING);
            target.setGravity(true);
            target.setWalkSpeed(0.2f);
            target.setFlySpeed(0.1f);
            target.setInvulnerable(false);
            // 按游戏模式恢复飞行
            restoreFlightByGameMode(target);

            target.sendMessage("§c§l捕捉蛋已掉落失效，你已被自动释放！");
            plugin.getLogger().info("§c[管理员捕捉器] 捕捉蛋掉落失效，玩家 " + target.getName() + " 已自动释放");
        } catch (Exception e) {
            plugin.getLogger().warning("§c[管理员捕捉器] 释放失效捕捉蛋的玩家时出错: " + e.getMessage());
        }
    }

    /**
     * 发射器释放玩家捕捉蛋
     */
    @EventHandler
    public void onDispenserDispensePlayer(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(playerCaptureFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        // 检查是否已失效
        if (pdc.has(playerCaptureInvalidKey, PersistentDataType.BYTE)) return;

        String targetUuidStr = pdc.get(playerCaptureTargetKey, PersistentDataType.STRING);
        if (targetUuidStr == null) return;

        UUID targetUuid = UUID.fromString(targetUuidStr);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) return;

        // 计算发射器前方位置
        Block block = event.getBlock();
        BlockFace facing = BlockFace.NORTH;
        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            facing = directional.getFacing();
        }
        Location spawnLoc = block.getLocation().add(0.5, 0.5, 0.5).add(facing.getDirection().multiply(1.0));

        // 释放玩家到发射器前方
        releasePlayer(target, true, spawnLoc);

        // 从发射器移除已使用的物品
        if (block.getState() instanceof org.bukkit.block.Dispenser dispenser) {
            for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
                ItemStack slot = dispenser.getInventory().getItem(i);
                if (slot == null || slot.getType().isAir()) continue;
                if (!slot.hasItemMeta()) continue;
                ItemMeta slotMeta = slot.getItemMeta();
                if (slotMeta == null) continue;
                PersistentDataContainer slotPdc = slotMeta.getPersistentDataContainer();
                if (slotPdc.has(playerCaptureFlagKey, PersistentDataType.BYTE)) {
                    String slotTargetUuid = slotPdc.get(playerCaptureTargetKey, PersistentDataType.STRING);
                    if (targetUuidStr.equals(slotTargetUuid)) {
                        if (slot.getAmount() > 1) {
                            slot.setAmount(slot.getAmount() - 1);
                        } else {
                            dispenser.getInventory().setItem(i, null);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * 发射器使用管理员捕捉器 → 自动捕捉前方实体
     */
    @EventHandler
    public void onDispenserAdminCatcher(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(adminCatcherFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        Block block = event.getBlock();
        World world = block.getWorld();

        // 计算发射器朝向
        BlockFace facing = BlockFace.NORTH;
        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            facing = directional.getFacing();
        }

        // 在发射器前方 5 格内查找最近的实体
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        org.bukkit.util.Vector dir = facing.getDirection();
        Entity target = null;
        double nearest = 6.0;
        for (Entity entity : world.getNearbyEntities(origin, 5, 5, 5)) {
            org.bukkit.util.Vector toEntity = entity.getLocation().toVector().subtract(origin.toVector());
            double dot = toEntity.dot(dir);
            if (dot > 0 && dot < 5) {
                double dist = toEntity.length();
                if (dist < nearest) {
                    nearest = dist;
                    target = entity;
                }
            }
        }

        if (target == null) return;

        // 捕捉实体（放入发射器）
        captureEntityIntoDispenser(block, target);

        // 从发射器消耗管理员棒
        if (block.getState() instanceof org.bukkit.block.Dispenser dispenser) {
            for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
                ItemStack slot = dispenser.getInventory().getItem(i);
                if (slot == null || slot.getType().isAir()) continue;
                if (!slot.hasItemMeta()) continue;
                ItemMeta slotMeta = slot.getItemMeta();
                if (slotMeta == null) continue;
                if (slotMeta.getPersistentDataContainer().has(adminCatcherFlagKey, PersistentDataType.BYTE)) {
                    if (slot.getAmount() > 1) {
                        slot.setAmount(slot.getAmount() - 1);
                    } else {
                        dispenser.getInventory().setItem(i, null);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 发射器捕捉实体 → 蛋放入发射器
     */
    private void captureEntityIntoDispenser(Block block, Entity target) {
        String entityKey = target.getType().getKey().getKey();
        Material containerMat = getContainerMaterial(target.getType());

        try {
            String nbtString;
            if (target instanceof LivingEntity living) {
                nbtString = carryCommand.serializeEntityFullNbt(living);
            } else {
                nbtString = serializeNonLivingEntity(target);
            }

            ItemStack egg = new ItemStack(containerMat);
            ItemMeta eggMeta = egg.getItemMeta();
            if (eggMeta == null) return;

            eggMeta.getPersistentDataContainer().set(carryCommand.entityNbtKey, PersistentDataType.STRING, nbtString);
            eggMeta.getPersistentDataContainer().set(carryCommand.entityTypeKey, PersistentDataType.STRING, entityKey);
            eggMeta.getPersistentDataContainer().set(carryCommand.carryFlagKey, PersistentDataType.BYTE, (byte) 1);

            eggMeta.setDisplayName("§c§l[管理员] §6" + formatEntityName(target));
            List<String> lore = new ArrayList<>();
            if (target instanceof LivingEntity living) {
                double health = living.getHealth();
                double maxHealth = living.getMaxHealth();
                lore.add("§7生命: §a" + String.format("%.1f", health) + "§7/§a" + String.format("%.0f", maxHealth));
            }
            lore.add("");
            lore.add("§7右键点击以释放");
            lore.add("§8[" + entityKey + "]");
            eggMeta.setLore(lore);
            egg.setItemMeta(eggMeta);

            // 移除原实体
            target.remove();

            // 蛋放入发射器，满则掉落
            if (block.getState() instanceof org.bukkit.block.Dispenser dispenser) {
                Map<Integer, ItemStack> leftover = dispenser.getInventory().addItem(egg);
                if (!leftover.isEmpty()) {
                    block.getWorld().dropItem(block.getLocation().add(0.5, 1.0, 0.5), egg);
                }
            }

            plugin.getLogger().info("§c[管理员捕捉器] 发射器自动捕捉了 " + entityKey);
        } catch (Exception e) {
            plugin.getLogger().warning("§c[管理员捕捉器] 发射器捕捉失败: " + e.getMessage());
        }
    }

    /**
     * 玩家重进时从捕捉状态恢复
     */
    @EventHandler
    public void onPlayerJoinRestore(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerCaptureData data = pendingRestorePlayers.remove(player.getUniqueId());
        if (data == null) return;

        // 取消定时任务
        Bukkit.getScheduler().cancelTask(data.mainTaskId);

        // 复位到原始位置
        Location originalLoc = data.getLocation();
        if (originalLoc != null) {
            player.teleport(originalLoc);
        }

        // 移除效果
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.setGravity(true);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
        // 按游戏模式恢复飞行
        restoreFlightByGameMode(player);

        // 从捕捉者背包移除蛋
        Player catcher = Bukkit.getPlayer(data.catcherUuid);
        if (catcher != null && catcher.isOnline()) {
            removeCaptureEggFromInventory(catcher, player.getUniqueId());
        }

        player.sendMessage("§a§l你已从捕捉状态中恢复！");
        plugin.getLogger().info("§c[管理员捕捉器] 玩家 " + player.getName() + " 重进已恢复至原始位置");
    }

    /**
     * 释放玩家：移除效果、清理
     * @param spawnLocation 释放位置（手动释放=蛋放置位置，自动释放=原始捕捉位置）
     */
    private void releasePlayer(Player target, boolean isManual, Location spawnLocation) {
        PlayerCaptureData data = capturedPlayers.remove(target.getUniqueId());
        if (data == null) return;

        // 取消定时任务
        Bukkit.getScheduler().cancelTask(data.mainTaskId);

        // 从捕捉者背包移除蛋物品
        Player catcher = Bukkit.getPlayer(data.catcherUuid);
        if (catcher != null && catcher.isOnline()) {
            removeCaptureEggFromInventory(catcher, target.getUniqueId());
        }

        if (!target.isOnline()) return;

        // 传送玩家到指定位置
        if (spawnLocation != null) {
            target.teleport(spawnLocation);
        }

        // 移除效果
        target.removePotionEffect(PotionEffectType.SLOW_FALLING);
        target.setGravity(true);
        target.setWalkSpeed(0.2f);
        target.setFlySpeed(0.1f);
        target.setInvulnerable(false);
        // 按游戏模式恢复飞行
        restoreFlightByGameMode(target);

        // 提示
        if (isManual) {
            target.sendMessage("§a§l你已被管理员手动释放！");
            if (catcher != null && catcher.isOnline()) {
                catcher.sendMessage("§a已手动释放玩家 §e" + target.getName());
            }
        } else {
            target.sendMessage("§a§l5分钟已到，你已被自动释放！");
            if (catcher != null && catcher.isOnline()) {
                catcher.sendMessage("§e5分钟已到，玩家 §e" + target.getName() + " §e已被自动释放。");
            }
        }

        plugin.getLogger().info("§c[管理员捕捉器] 玩家 " + target.getName() + " 已" + (isManual ? "手动" : "自动") + "释放");
    }

    /**
     * 从玩家背包中移除指定玩家的捕捉蛋
     */
    private void removeCaptureEggFromInventory(Player player, UUID targetUuid) {
        String targetUuidStr = targetUuid.toString();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.has(playerCaptureFlagKey, PersistentDataType.BYTE)) continue;
            String storedTarget = pdc.get(playerCaptureTargetKey, PersistentDataType.STRING);
            if (targetUuidStr.equals(storedTarget)) {
                player.getInventory().remove(item);
                return;
            }
        }
    }

    /**
     * 插件关闭时释放所有被捕捉的玩家
     */
    public void shutdownCleanup() {
        for (Map.Entry<UUID, PlayerCaptureData> entry : new HashMap<>(capturedPlayers).entrySet()) {
            UUID targetUuid = entry.getKey();
            PlayerCaptureData data = entry.getValue();
            Bukkit.getScheduler().cancelTask(data.mainTaskId);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                Location originalLoc = data.getLocation();
                if (originalLoc != null) {
                    target.teleport(originalLoc);
                }
                target.removePotionEffect(PotionEffectType.SLOW_FALLING);
                target.setGravity(true);
                target.setWalkSpeed(0.2f);
                target.setFlySpeed(0.1f);
                target.setInvulnerable(false);
                // 按游戏模式恢复飞行
                restoreFlightByGameMode(target);
                target.sendMessage("§c§l服务器关闭，你已被自动释放！");
            }
            Player catcher = Bukkit.getPlayer(data.catcherUuid);
            if (catcher != null && catcher.isOnline()) {
                removeCaptureEggFromInventory(catcher, targetUuid);
            }
        }
        capturedPlayers.clear();
        // 清理等待恢复的数据
        pendingRestorePlayers.clear();
        plugin.getLogger().info("§c[管理员捕捉器] 插件关闭，已清理 " + capturedPlayers.size() + " 个捕捉数据");
    }

    /**
     * 清理捕捉数据（玩家离线等）
     */
    private void cleanupCapture(Player target) {
        PlayerCaptureData data = capturedPlayers.remove(target.getUniqueId());
        if (data == null) return;
        Bukkit.getScheduler().cancelTask(data.mainTaskId);
        // 保存数据，等玩家重进时恢复
        pendingRestorePlayers.put(target.getUniqueId(), data);
        plugin.getLogger().info("§c[管理员捕捉器] 玩家 " + target.getName() + " 已离线，等待重进恢复");
    }

    /**
     * 获取实体对应的容器物品材质：
     * 有生物蛋 → 生物蛋
     * 有对应物品（矿车/船等）→ 对应物品
     * 纯技术实体 → 村民刷怪蛋
     */
    private Material getContainerMaterial(EntityType type) {
        // 先尝试生物蛋
        Material egg = getSpawnEggMaterial(type);
        if (egg != null) return egg;
        // 无生物蛋则查映射表
        Material fallback = FALLBACK_MATERIALS.get(type.getKey().getKey());
        if (fallback != null) return fallback;
        // 纯技术实体用村民刷怪蛋兜底
        return Material.VILLAGER_SPAWN_EGG;
    }

    private Material getSpawnEggMaterial(EntityType type) {
        String key = type.getKey().getKey().toUpperCase() + "_SPAWN_EGG";
        try {
            return Material.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 序列化非生物实体（矿车、船、盔甲架等）的基础数据
     */
    private String serializeNonLivingEntity(Entity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity_type", entity.getType().getKey().toString());
        data.put("uuid", entity.getUniqueId().toString());
        data.put("world", entity.getWorld().getName());
        data.put("location", entity.getLocation().toVector().toString());
        data.put("yaw", entity.getLocation().getYaw());
        data.put("pitch", entity.getLocation().getPitch());
        if (entity.getCustomName() != null) {
            data.put("custom_name", entity.getCustomName());
            data.put("custom_name_visible", entity.isCustomNameVisible());
        }
        data.put("fire_ticks", entity.getFireTicks());
        data.put("fall_distance", entity.getFallDistance());
        data.put("is_sneaking", entity.isSneaking());
        data.put("is_invisible", entity.isInvisible());
        data.put("is_silent", entity.isSilent());
        data.put("is_glowing", entity.isGlowing());
        data.put("invulnerable", entity.isInvulnerable());
        return gson.toJson(data);
    }

    // ========== 被捕捉玩家数据结构 ==========
    private static class PlayerCaptureData {
        final String worldName;
        final double x, y, z;
        final float yaw, pitch;
        final UUID catcherUuid;
        final int mainTaskId;

        PlayerCaptureData(Location loc, UUID catcherUuid, int mainTaskId) {
            this.worldName = loc.getWorld().getName();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
            this.catcherUuid = catcherUuid;
            this.mainTaskId = mainTaskId;
        }

        Location getLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private String formatEntityName(Entity entity) {
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        return "§f" + entity.getName();
    }

    /**
     * 根据玩家游戏模式恢复飞行能力（创造/旁观模式可飞行）
     */
    private void restoreFlightByGameMode(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    public static Set<String> getTechnicalEntityKeys() {
        return FALLBACK_MATERIALS.keySet();
    }

    public static Material getFallbackMaterial(String entityKey) {
        return FALLBACK_MATERIALS.get(entityKey);
    }
}
