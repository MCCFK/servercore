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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.DyeColor;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class CarryCommand implements CommandExecutor, Listener {

    private final MainPlugin plugin;
    final NamespacedKey entityNbtKey;
    final NamespacedKey entityTypeKey;
    final NamespacedKey carryFlagKey;
    private final NamespacedKey catcherFlagKey;
    private final File blacklistFile;
    private final File bannedPlayersFile;
    private final Gson gson;
    private final Set<String> blacklist = new HashSet<>();
    private final Set<String> bannedPlayers = new HashSet<>();

    // 村民搬运确认缓存: 玩家UUID -> (实体UUID, 时间戳)
    private final Map<UUID, Map.Entry<UUID, Long>> villagerConfirmMap = new HashMap<>();

    // 没有 NMS 反射字段 — 纯 Bukkit API

    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "ender_dragon", "wither",
            "minecart", "chest_minecart", "furnace_minecart", "hopper_minecart",
            "tnt_minecart", "command_block_minecart", "spawner_minecart",
            "armor_stand", "player",
            "boat", "oak_boat", "spruce_boat", "birch_boat", "jungle_boat",
            "acacia_boat", "dark_oak_boat", "mangrove_boat", "cherry_boat",
            "bamboo_raft", "chest_boat", "oak_chest_boat", "spruce_chest_boat",
            "birch_chest_boat", "jungle_chest_boat", "acacia_chest_boat",
            "dark_oak_chest_boat", "mangrove_chest_boat", "cherry_chest_boat",
            "bamboo_chest_raft",
            "experience_orb", "experience_bottle", "area_effect_cloud",
            "leash_knot", "painting", "item_frame", "glow_item_frame",
            "item", "arrow", "spectral_arrow", "trident",
            "snowball", "ender_pearl", "eye_of_ender", "egg",
            "fireball", "small_fireball", "dragon_fireball", "wither_skull",
            "llama_spit", "falling_block", "tnt", "shulker_bullet",
            "fishing_bobber", "lightning_bolt", "marker", "interaction",
            "text_display", "block_display", "item_display"
    ));

    public CarryCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.entityNbtKey = new NamespacedKey(plugin, "carry_nbt");
        this.entityTypeKey = new NamespacedKey(plugin, "carry_type");
        this.carryFlagKey = new NamespacedKey(plugin, "carry_flag");
        this.catcherFlagKey = new NamespacedKey(plugin, "catcher_flag");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        File pluginsFolder = new File(plugin.getServer().getPluginsFolder(), ".MCCFK_ALL_PLUGONSDATA");
        File carryFolder = new File(pluginsFolder, "carry");
        if (!carryFolder.exists()) carryFolder.mkdirs();
        this.blacklistFile = new File(carryFolder, "blacklist.json");
        this.bannedPlayersFile = new File(carryFolder, "banned_players.json");
        loadBlacklist();
        loadBannedPlayers();

        // 每5秒自动从文件重载黑名单
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::reloadBlacklistFromFile, 100L, 100L);
    }

    // ========== 黑名单管理 ==========

    private void loadBlacklist() {
        blacklist.clear();
        if (!blacklistFile.exists()) {
            // 文件不存在时使用默认黑名单并保存
            blacklist.addAll(DEFAULT_BLACKLIST);
            saveBlacklist();
            plugin.getLogger().info("§7[搬运] 已创建默认黑名单文件，共 " + blacklist.size() + " 个实体");
            return;
        }
        try (FileReader reader = new FileReader(blacklistFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null && !loaded.isEmpty()) {
                blacklist.addAll(loaded);
            } else {
                // 文件为空或格式异常时回退默认
                blacklist.addAll(DEFAULT_BLACKLIST);
            }
            plugin.getLogger().info("§7[搬运] 已加载 " + blacklist.size() + " 个黑名单实体");
        } catch (IOException e) {
            plugin.getLogger().severe("§c[搬运] 加载黑名单失败: " + e.getMessage());
            blacklist.addAll(DEFAULT_BLACKLIST);
        }
    }

    private void reloadBlacklistFromFile() {
        try (FileReader reader = new FileReader(blacklistFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null && !loaded.isEmpty()) {
                blacklist.clear();
                blacklist.addAll(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("§e[搬运] 定时重载黑名单失败: " + e.getMessage());
        }
    }

    private void saveBlacklist() {
        try (FileWriter writer = new FileWriter(blacklistFile)) {
            gson.toJson(new ArrayList<>(blacklist), writer);
        } catch (IOException e) {
            plugin.getLogger().severe("§c[搬运] 保存黑名单失败: " + e.getMessage());
        }
    }

    // ========== 玩家禁用管理 ==========

    private void loadBannedPlayers() {
        bannedPlayers.clear();
        if (!bannedPlayersFile.exists()) {
            saveBannedPlayers();
            return;
        }
        try (FileReader reader = new FileReader(bannedPlayersFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                bannedPlayers.addAll(loaded);
            }
            plugin.getLogger().info("§7[搬运] 已加载 " + bannedPlayers.size() + " 个被禁用玩家");
        } catch (IOException e) {
            plugin.getLogger().severe("§c[搬运] 加载禁用玩家列表失败: " + e.getMessage());
        }
    }

    private void saveBannedPlayers() {
        try (FileWriter writer = new FileWriter(bannedPlayersFile)) {
            gson.toJson(new ArrayList<>(bannedPlayers), writer);
        } catch (IOException e) {
            plugin.getLogger().severe("§c[搬运] 保存禁用玩家列表失败: " + e.getMessage());
        }
    }

    // ========== 生物蛋检查 ==========

    private Material getSpawnEggMaterial(EntityType type) {
        String key = type.getKey().getKey().toUpperCase() + "_SPAWN_EGG";
        try {
            return Material.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ========== 格式化属性描述 ==========

    private String formatEntityName(Entity entity) {
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        // entity.getName() 返回翻译后的名称（中文客户端显示中文名）
        return "§f" + entity.getName();
    }

    private List<String> buildLore(LivingEntity living) {
        List<String> lore = new ArrayList<>();

        // 生命值
        double health = living.getHealth();
        double maxHealth = living.getMaxHealth();
        lore.add("§7生命: §a" + String.format("%.1f", health) + "§7/§a" + String.format("%.0f", maxHealth));

        // 特殊属性
        if (living instanceof Panda panda) {
            lore.add("§7基因: §e" + formatFriendly(panda.getMainGene().name()));
            lore.add("§7隐藏基因: §e" + formatFriendly(panda.getHiddenGene().name()));
        }
        if (living instanceof Wolf wolf) {
            if (wolf.getVariant() instanceof org.bukkit.Keyed) {
                lore.add("§7变种: §e" + formatFriendly(((org.bukkit.Keyed) wolf.getVariant()).getKey().getKey()));
            }
            if (wolf.isTamed() && wolf.getOwner() != null) {
                lore.add("§7主人: §e" + wolf.getOwner().getName());
            }
        }
        if (living instanceof Cat cat) {
            lore.add("§7种类: §e" + formatFriendly(cat.getCatType().name()));
        }
        if (living instanceof Axolotl axolotl) {
            lore.add("§7颜色: §e" + formatFriendly(axolotl.getVariant().name()));
        }
        if (living instanceof TropicalFish fish) {
            lore.add("§7花纹: §e" + formatFriendly(fish.getPattern().name()));
        }
        if (living instanceof Rabbit rabbit) {
            lore.add("§7种类: §e" + formatFriendly(rabbit.getRabbitType().name()));
        }
        if (living instanceof MushroomCow mooshroom) {
            lore.add("§7种类: §e" + formatFriendly(mooshroom.getVariant().name()));
        }
        if (living instanceof Frog frog) {
            lore.add("§7变种: §e" + formatFriendly(frog.getVariant().name()));
        }
        if (living instanceof Parrot parrot) {
            lore.add("§7颜色: §e" + formatFriendly(parrot.getVariant().name()));
        }
        if (living instanceof Horse h) {
            lore.add("§7颜色: §e" + formatFriendly(h.getColor().name()));
            lore.add("§7花纹: §e" + formatFriendly(h.getStyle().name()));
        }
        if (living instanceof Llama llama) {
            lore.add("§7颜色: §e" + formatFriendly(llama.getColor().name()));
            lore.add("§7强度: §e" + llama.getStrength());
        }
        if (living instanceof Villager villager) {
            lore.add("§7职业: §e" + formatFriendly(villager.getProfession().name()));
            lore.add("§7等级: §e" + villager.getVillagerLevel());
        }
        if (living instanceof ZombieVillager zv) {
            lore.add("§7职业: §e" + formatFriendly(zv.getVillagerProfession().name()));
        }
        if (living instanceof Sheep sheep) {
            lore.add("§7颜色: §e" + formatFriendly(sheep.getColor().name()));
        }
        if (living instanceof Slime slime) {
            lore.add("§7大小: §e" + slime.getSize());
        }
        if (living instanceof Phantom phantom) {
            lore.add("§7大小: §e" + phantom.getSize());
        }
        if (living instanceof Goat goat) {
            lore.add("§7角: §e" + (goat.hasLeftHorn() ? "有" : "无") + "/" + (goat.hasRightHorn() ? "有" : "无"));
        }

        // 幼年
        if (living instanceof Ageable ageable && !ageable.isAdult()) {
            lore.add("§7幼年");
        }

        return lore;
    }

    private String formatFriendly(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ========== 村民搬运确认机制 ==========

    private boolean confirmVillagerCarry(Player player, Entity target) {
        if (!(target instanceof Villager) && !(target instanceof ZombieVillager)) {
            return true; // 非村民直接放行
        }

        UUID playerId = player.getUniqueId();
        UUID entityId = target.getUniqueId();
        long now = System.currentTimeMillis();

        Map.Entry<UUID, Long> lastConfirm = villagerConfirmMap.get(playerId);
        if (lastConfirm != null && lastConfirm.getKey().equals(entityId) && (now - lastConfirm.getValue()) < 5000) {
            // 5秒内确认了同一个村民，放行并清除记录
            villagerConfirmMap.remove(playerId);
            return true;
        }

        // 没确认过或过期了，记录并提示
        villagerConfirmMap.put(playerId, new AbstractMap.SimpleEntry<>(entityId, now));
        player.sendMessage("§c⚠ 搬运村民会丢失交易、职业等数据！5秒内再次操作确认搬运");
        return false;
    }

    // ========== 指令处理 ==========

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase();

        // ========== 禁用/启用玩家搬运 ==========
        if (cmd.equals("fuckcarry") || cmd.equals("bancarry") || cmd.equals("unfuckcarry") || cmd.equals("unbancarry")) {
            if (!sender.isOp()) {
                sender.sendMessage("§c你没有权限使用此命令！");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§c用法: /" + label + " <玩家名>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§c玩家 " + args[0] + " 不在线！");
                return true;
            }
            String uuidStr = target.getUniqueId().toString();
            boolean isBan = cmd.equals("fuckcarry") || cmd.equals("bancarry");
            if (isBan) {
                if (bannedPlayers.contains(uuidStr)) {
                    sender.sendMessage("§c玩家 " + target.getName() + " 已被禁止使用搬运！");
                    return true;
                }
                bannedPlayers.add(uuidStr);
                saveBannedPlayers();
                sender.sendMessage("§a已禁止玩家 " + target.getName() + " 使用搬运！");
                plugin.getLogger().info("§c[搬运] 玩家 " + target.getName() + " 被禁止使用搬运，执行者: " + sender.getName());
            } else {
                if (!bannedPlayers.remove(uuidStr)) {
                    sender.sendMessage("§c玩家 " + target.getName() + " 未被禁止使用搬运！");
                    return true;
                }
                saveBannedPlayers();
                sender.sendMessage("§a已允许玩家 " + target.getName() + " 使用搬运！");
                plugin.getLogger().info("§a[搬运] 玩家 " + target.getName() + " 被允许使用搬运，执行者: " + sender.getName());
            }
            return true;
        }

        // ========== 搬运指令 ==========
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // 检查玩家是否被禁止搬运
        if (bannedPlayers.contains(player.getUniqueId().toString())) {
            player.sendMessage("§c你已被禁止使用搬运！");
            return true;
        }

        RayTraceResult result = player.rayTraceEntities(10, false);
        if (result == null || result.getHitEntity() == null) {
            player.sendMessage("§c请看向一个实体！");
            return true;
        }

        Entity target = result.getHitEntity();

        if (target instanceof Player) {
            player.sendMessage("§c不能将玩家变成生物蛋！");
            return true;
        }

        String entityKey = target.getType().getKey().getKey();
        if (blacklist.contains(entityKey)) {
            player.sendMessage("§c该实体已被列入黑名单，无法搬运！");
            return true;
        }

        Material spawnEggMat = getSpawnEggMaterial(target.getType());
        if (spawnEggMat == null) {
            player.sendMessage("§c该实体没有对应的生物蛋！");
            return true;
        }

        if (!(target instanceof LivingEntity living)) {
            player.sendMessage("§c只能搬运生物类实体！");
            return true;
        }

        // 村民二次确认
        if (!confirmVillagerCarry(player, target)) {
            return true;
        }

        try {
            // 完整 NBT 序列化（包含所有数据：盔甲、背包、自定义名、药水效果、属性等）
            String nbtString = serializeEntityFullNbt(living);

            ItemStack egg = new ItemStack(spawnEggMat);
            ItemMeta meta = egg.getItemMeta();
            if (meta == null) {
                player.sendMessage("§c无法创建生物蛋！");
                return true;
            }

            meta.getPersistentDataContainer().set(entityNbtKey, PersistentDataType.STRING, nbtString);
            meta.getPersistentDataContainer().set(entityTypeKey, PersistentDataType.STRING, entityKey);
            meta.getPersistentDataContainer().set(carryFlagKey, PersistentDataType.BYTE, (byte) 1);

            meta.setDisplayName("§6" + formatEntityName(target));

            List<String> lore = buildLore(living);
            lore.add("");
            lore.add("§7右键点击以释放");
            lore.add("§8[" + entityKey + "]");
            meta.setLore(lore);

            egg.setItemMeta(meta);

            // 移除原实体
            target.remove();

            // 给予玩家
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(egg);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), egg);
            }

            player.sendMessage("§a已将 " + formatEntityName(target) + " §a变为生物蛋！");

        } catch (Exception e) {
            player.sendMessage("§c搬运失败: " + e.getMessage());
            plugin.getLogger().severe("§c[搬运] 序列化实体失败: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    // ========== 放置生物蛋监听器 ==========

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(carryFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        String nbtString = meta.getPersistentDataContainer().get(entityNbtKey, PersistentDataType.STRING);
        String typeStr = meta.getPersistentDataContainer().get(entityTypeKey, PersistentDataType.STRING);
        if (nbtString == null || typeStr == null) {
            player.sendMessage("§c生物蛋数据损坏！");
            return;
        }

        Location spawnLoc;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            BlockFace face = event.getBlockFace();
            if (clickedBlock == null) return;
            spawnLoc = clickedBlock.getRelative(face).getLocation().add(0.5, 0, 0.5);
        } else {
            // 空气右键：在玩家视线前方生成
            spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2));
        }

        try {
            // 完整 NBT 反序列化还原实体（全部状态完全恢复）
            Entity spawned = deserializeAndSpawn(spawnLoc.getWorld(), spawnLoc, nbtString);

            // 非潜行时投掷物以发射方式释放
            if (spawned != null && !player.isSneaking() && spawned instanceof Projectile) {
                Vector dir = player.getLocation().getDirection().normalize().multiply(2.0);
                spawned.setVelocity(dir);
            }

            // 检查并显示实体释放提示（含频率检测）
            boolean shouldShowHint = plugin.getPersonalSettings().checkAndNotifyEntityRelease(player);
            if (shouldShowHint) {
                player.sendMessage("§a已释放实体！");
            }

            // 消耗物品
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

        } catch (Exception e) {
            player.sendMessage("§c实体释放失败: " + e.getMessage());
            plugin.getLogger().severe("§c[搬运] 释放实体失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== 实体捕捉器 ==========

    public void giveCatcherItem(Player player) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§6§l实体捕捉器");
        List<String> lore = new ArrayList<>();
        lore.add("§7右键生物将其变为生物蛋");
        lore.add("§7相当于发送 /carry");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(catcherFlagKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), item);
        }
        player.sendMessage("§a已获得实体捕捉器！");
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(catcherFlagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        Entity target = event.getRightClicked();

        // 检查玩家是否被禁止搬运
        if (bannedPlayers.contains(player.getUniqueId().toString())) {
            player.sendMessage("§c你已被禁止使用搬运！");
            return;
        }

        if (target instanceof Player) {
            player.sendMessage("§c不能将玩家变成生物蛋！");
            return;
        }

        String entityKey = target.getType().getKey().getKey();
        if (blacklist.contains(entityKey)) {
            player.sendMessage("§c该实体已被列入黑名单，无法捕捉！");
            return;
        }

        Material spawnEggMat = getSpawnEggMaterial(target.getType());
        if (spawnEggMat == null) {
            player.sendMessage("§c该实体没有对应的生物蛋！");
            return;
        }

        if (!(target instanceof LivingEntity living)) {
            player.sendMessage("§c只能捕捉生物类实体！");
            return;
        }

        // 村民二次确认
        if (!confirmVillagerCarry(player, target)) {
            return;
        }

        try {
            String nbtString = serializeEntityFullNbt(living);

            ItemStack egg = new ItemStack(spawnEggMat);
            ItemMeta eggMeta = egg.getItemMeta();
            if (eggMeta == null) {
                player.sendMessage("§c无法创建生物蛋！");
                return;
            }

            eggMeta.getPersistentDataContainer().set(entityNbtKey, PersistentDataType.STRING, nbtString);
            eggMeta.getPersistentDataContainer().set(entityTypeKey, PersistentDataType.STRING, entityKey);
            eggMeta.getPersistentDataContainer().set(carryFlagKey, PersistentDataType.BYTE, (byte) 1);

            eggMeta.setDisplayName("§6" + formatEntityName(target));

            List<String> lore = buildLore(living);
            lore.add("");
            lore.add("§7右键点击以释放");
            lore.add("§8[" + entityKey + "]");
            eggMeta.setLore(lore);

            egg.setItemMeta(eggMeta);
            target.remove();

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(egg);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), egg);
            }

            player.sendMessage("§a已将 " + formatEntityName(target) + " §a变为生物蛋！");

        } catch (Exception e) {
            player.sendMessage("§c捕捉失败: " + e.getMessage());
            plugin.getLogger().severe("§c[搬运] 捕捉失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== 发射器支持 ==========

    @EventHandler
    public void onDispenserDispenseEntity(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(carryFlagKey, PersistentDataType.BYTE)) return;

        String nbtString = meta.getPersistentDataContainer().get(entityNbtKey, PersistentDataType.STRING);
        String typeStr = meta.getPersistentDataContainer().get(entityTypeKey, PersistentDataType.STRING);
        if (nbtString == null || typeStr == null) return;

        event.setCancelled(true);

        Block block = event.getBlock();
        BlockFace facing = BlockFace.NORTH;
        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            facing = directional.getFacing();
        }
        Location spawnLoc = block.getLocation().add(0.5, 0.5, 0.5).add(facing.getDirection().multiply(1.0));

        try {
            deserializeAndSpawn(spawnLoc.getWorld(), spawnLoc, nbtString);
        } catch (Exception e) {
            plugin.getLogger().warning("§c[搬运] 发射器释放实体失败: " + e.getMessage());
            return;
        }

        // 从发射器移除已使用的物品
        if (block.getState() instanceof org.bukkit.block.Dispenser dispenser) {
            for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
                ItemStack slot = dispenser.getInventory().getItem(i);
                if (slot == null || slot.getType().isAir()) continue;
                if (!slot.hasItemMeta()) continue;
                ItemMeta slotMeta = slot.getItemMeta();
                if (slotMeta == null) continue;
                if (slotMeta.getPersistentDataContainer().has(carryFlagKey, PersistentDataType.BYTE)) {
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

    // ========== 序列化实体（纯 Bukkit API） ==========
    public String serializeEntityFullNbt(LivingEntity living) {
        Map<String, Object> data = new LinkedHashMap<>();

        // ---- 基础信息 ----
        data.put("entity_type", living.getType().getKey().toString());
        data.put("uuid", living.getUniqueId().toString());
        data.put("world", living.getWorld().getName());
        data.put("location", living.getLocation().toVector().toString());
        data.put("yaw", living.getLocation().getYaw());
        data.put("pitch", living.getLocation().getPitch());

        // ---- 生命值 ----
        data.put("health", living.getHealth());
        data.put("max_health", living.getMaxHealth());

        // ---- 自定义名称 ----
        if (living.getCustomName() != null) {
            data.put("custom_name", living.getCustomName());
            data.put("custom_name_visible", living.isCustomNameVisible());
        }

        // ---- 重力 / 是否着火 ----
        data.put("gravity", living.hasGravity());
        data.put("fire_ticks", living.getFireTicks());
        data.put("fall_distance", living.getFallDistance());

        // ---- 实体状态 ----
        data.put("is_sneaking", living.isSneaking());
        data.put("is_swimming", living.isSwimming());
        data.put("is_gliding", living.isGliding());
        data.put("is_invisible", living.isInvisible());
        data.put("is_silent", living.isSilent());

        // ---- 年龄 ----
        if (living instanceof Ageable ageable) {
            data.put("is_baby", !ageable.isAdult());
            data.put("age", ageable.getAge());
            data.put("age_lock", ageable.getAgeLock());
        }

        // ---- 装备 ----
        if (living.getEquipment() != null) {
            data.put("helmet", serializeItem(living.getEquipment().getHelmet()));
            data.put("chestplate", serializeItem(living.getEquipment().getChestplate()));
            data.put("leggings", serializeItem(living.getEquipment().getLeggings()));
            data.put("boots", serializeItem(living.getEquipment().getBoots()));
            data.put("main_hand", serializeItem(living.getEquipment().getItemInMainHand()));
            data.put("off_hand", serializeItem(living.getEquipment().getItemInOffHand()));
            data.put("item_drop_chance", living.getEquipment().getItemInMainHandDropChance());
        }

        // ---- 药水效果 ----
        List<Map<String, Object>> potionEffects = new ArrayList<>();
        for (PotionEffect effect : living.getActivePotionEffects()) {
            Map<String, Object> effectData = new LinkedHashMap<>();
            effectData.put("type", effect.getType().getKey().toString());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            effectData.put("ambient", effect.isAmbient());
            effectData.put("particles", effect.hasParticles());
            effectData.put("icon", effect.hasIcon());
            potionEffects.add(effectData);
        }
        if (!potionEffects.isEmpty()) {
            data.put("potion_effects", potionEffects);
        }

        // ---- 特殊实体数据 ----
        saveSpecialEntityData(living, data);

        // ---- 转为 JSON ----
        return gson.toJson(data);
    }

    // ========== 序列化物品 ==========
    private Map<String, Object> serializeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", item.getType().getKey().toString());
        data.put("amount", item.getAmount());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();

            if (meta.hasDisplayName()) {
                data.put("display_name", meta.getDisplayName());
            }
            if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                data.put("lore", meta.getLore());
            }
            if (meta.hasEnchants() && !meta.getEnchants().isEmpty()) {
                Map<String, Integer> enchants = new LinkedHashMap<>();
                meta.getEnchants().forEach((enchant, level) ->
                    enchants.put(enchant.getKey().toString(), level)
                );
                data.put("enchants", enchants);
            }
            if (meta.hasCustomModelData()) {
                data.put("custom_model_data", meta.getCustomModelData());
            }
            if (!meta.getItemFlags().isEmpty()) {
                List<String> flags = new ArrayList<>();
                for (ItemFlag flag : meta.getItemFlags()) {
                    flags.add(flag.name());
                }
                data.put("item_flags", flags);
            }

            // 耐久度（仅对可损耗物品）
            if (item.getType().getMaxDurability() > 0 && meta instanceof Damageable damageable) {
                data.put("damage", damageable.getDamage());
            }

            // 附魔书特殊处理
            if (item.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta bookMeta) {
                Map<String, Integer> storedEnchants = new LinkedHashMap<>();
                bookMeta.getStoredEnchants().forEach((enchant, level) ->
                    storedEnchants.put(enchant.getKey().toString(), level)
                );
                if (!storedEnchants.isEmpty()) {
                    data.put("stored_enchants", storedEnchants);
                }
            }
        }

        return data;
    }

    // ========== 保存特殊实体数据 ==========
    private void saveSpecialEntityData(LivingEntity living, Map<String, Object> data) {
        // ---- 狼 ----
        if (living instanceof Wolf wolf) {
            data.put("wolf_variant", wolf.getVariant().getKey().toString());
            data.put("wolf_tamed", wolf.isTamed());
            data.put("wolf_angry", wolf.isAngry());
            data.put("wolf_sitting", wolf.isSitting());
            data.put("wolf_health", wolf.getHealth());
            if (wolf.isTamed() && wolf.getOwner() != null) {
                data.put("wolf_owner", wolf.getOwner().getUniqueId().toString());
            }
        }

        // ---- 猫 ----
        if (living instanceof Cat cat) {
            data.put("cat_type", cat.getCatType().name());
            data.put("cat_tamed", cat.isTamed());
            data.put("cat_sitting", cat.isSitting());
            if (cat.isTamed() && cat.getOwner() != null) {
                data.put("cat_owner", cat.getOwner().getUniqueId().toString());
            }
        }

        // ---- 鹦鹉 ----
        if (living instanceof Parrot parrot) {
            data.put("parrot_variant", parrot.getVariant().name());
            data.put("parrot_tamed", parrot.isTamed());
            if (parrot.isTamed() && parrot.getOwner() != null) {
                data.put("parrot_owner", parrot.getOwner().getUniqueId().toString());
            }
        }

        // ---- 马科动物 (马/驴/骡/僵尸马/骷髅马) ----
        if (living instanceof AbstractHorse horse) {
            data.put("horse_tamed", horse.isTamed());
            data.put("horse_tamed", horse.isTamed());
            if (horse.isTamed() && horse.getOwner() != null) {
                data.put("horse_owner", horse.getOwner().getUniqueId().toString());
            }
            data.put("horse_jump_strength", horse.getJumpStrength());
            data.put("horse_max_health", horse.getMaxHealth());

            AttributeInstance moveAttr = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (moveAttr != null) {
                data.put("horse_movement_speed", moveAttr.getBaseValue());
            }

            if (horse instanceof Horse normalHorse) {
                data.put("horse_color", normalHorse.getColor().name());
                data.put("horse_style", normalHorse.getStyle().name());
            }
            if (horse instanceof ChestedHorse chested) {
                data.put("horse_chested", chested.isCarryingChest());
            }
            if (horse instanceof Llama llama) {
                data.put("llama_color", llama.getColor().name());
                data.put("llama_strength", llama.getStrength());
            }
            if (horse instanceof Camel camel) {
                data.put("camel_pitch", camel.getPitch());
                data.put("camel_dash", camel.isDashing());
            }

            // ---- 保存马科动物的背包(鞍/盔甲/箱子物品) ----
            if (horse.getInventory() != null) {
                ItemStack[] contents = horse.getInventory().getContents();
                List<Map<String, Object>> invList = new ArrayList<>();
                for (ItemStack item : contents) {
                    invList.add(serializeItem(item));
                }
                data.put("horse_inventory", invList);
            }
        }

        // ---- 羊 ----
        if (living instanceof Sheep sheep) {
            data.put("sheep_color", sheep.getColor().name());
            data.put("sheep_sheared", sheep.isSheared());
        }

        // ---- 村民 ----
        if (living instanceof Villager villager) {
            data.put("villager_profession", villager.getProfession().name());
            data.put("villager_type", villager.getVillagerType().getKey().toString());
            data.put("villager_level", villager.getVillagerLevel());
            data.put("villager_experience", villager.getVillagerExperience());
            data.put("villager_restocks", villager.getRestocksToday());

            // ---- 村民交易内容 ----
            List<MerchantRecipe> recipes = villager.getRecipes();
            plugin.getLogger().info("§a[搬运] 村民当前交易数: " + (recipes != null ? recipes.size() : 0));
            if (recipes != null && !recipes.isEmpty()) {
                List<Map<String, Object>> recipeList = new ArrayList<>();
                for (MerchantRecipe recipe : recipes) {
                    Map<String, Object> recipeData = new LinkedHashMap<>();
                    recipeData.put("result", serializeItem(recipe.getResult()));
                    List<ItemStack> ingredients = recipe.getIngredients();
                    recipeData.put("ingredient0", serializeItem(ingredients.get(0)));
                    if (ingredients.size() > 1) {
                        recipeData.put("ingredient1", serializeItem(ingredients.get(1)));
                    }
                    recipeData.put("max_uses", recipe.getMaxUses());
                    recipeData.put("uses", recipe.getUses());
                    recipeData.put("experience_reward", recipe.hasExperienceReward());
                    recipeData.put("villager_experience", recipe.getVillagerExperience());
                    recipeData.put("price_multiplier", recipe.getPriceMultiplier());
                    recipeData.put("demand", recipe.getDemand());
                    recipeData.put("special_price", recipe.getSpecialPrice());
                    recipeList.add(recipeData);
                }
                data.put("villager_recipes", recipeList);
            }
        }

        // ---- 僵尸村民 ----
        if (living instanceof ZombieVillager zombieVillager) {
            data.put("zombie_villager_profession", zombieVillager.getVillagerProfession().name());
            data.put("zombie_villager_type", zombieVillager.getVillagerType().getKey().toString());
            data.put("zombie_villager_converting", zombieVillager.isConverting());
            if (zombieVillager.isConverting()) {
                data.put("zombie_villager_conversion_time", zombieVillager.getConversionTime());
            }
        }

        // ---- 熊猫 ----
        if (living instanceof Panda panda) {
            data.put("panda_main_gene", panda.getMainGene().name());
            data.put("panda_hidden_gene", panda.getHiddenGene().name());
            data.put("panda_sneeze", panda.isSneezing());
            data.put("panda_scared", panda.isScared());
            data.put("panda_rolling", panda.isRolling());
        }

        // ---- 狐狸 ----
        if (living instanceof Fox fox) {
            data.put("fox_type", fox.getFoxType().name());
            data.put("fox_sitting", fox.isSitting());
            data.put("fox_crouching", fox.isCrouching());
            data.put("fox_sleeping", fox.isSleeping());
        }

        // ---- 海豚 ----
        if (living instanceof Dolphin dolphin) {
            data.put("dolphin_has_fish", dolphin.hasFish());
            data.put("dolphin_moistness", dolphin.getMoistness());
        }

        // ---- 美西螈 ----
        if (living instanceof Axolotl axolotl) {
            data.put("axolotl_variant", axolotl.getVariant().name());
            data.put("axolotl_playing_dead", axolotl.isPlayingDead());
        }

        // ---- 青蛙 ----
        if (living instanceof Frog frog) {
            data.put("frog_variant", frog.getVariant().name());
        }

        // ---- 嗅探兽 ----
        if (living instanceof Sniffer sniffer) {
            data.put("sniffer_egg", sniffer.getState().name());
        }

        // ---- 炽足兽 ----
        if (living instanceof Strider strider) {
            data.put("strider_saddled", strider.hasSaddle());
            data.put("strider_boost_time", strider.getBoostTicks());
        }

        // ---- 山羊 ----
        if (living instanceof Goat goat) {
            data.put("goat_has_left_horn", goat.hasLeftHorn());
            data.put("goat_has_right_horn", goat.hasRightHorn());
            data.put("goat_screaming", goat.isScreaming());
        }

        // ---- 蜜蜂 ----
        if (living instanceof Bee bee) {
            data.put("bee_anger", bee.getAnger());
            data.put("bee_has_nectar", bee.hasNectar());
            data.put("bee_age", bee.getAge());
        }

        // ---- 盔甲架 ----
        if (living instanceof ArmorStand armorStand) {
            data.put("armor_stand_arms", armorStand.hasArms());
            data.put("armor_stand_base_plate", armorStand.hasBasePlate());
            data.put("armor_stand_small", armorStand.isSmall());
            data.put("armor_stand_marker", armorStand.isMarker());
            data.put("armor_stand_visible", armorStand.isVisible());
            data.put("armor_stand_pose", serializePose(armorStand));
        }

        // ---- 猪灵 ----
        if (living instanceof Piglin piglin) {
            data.put("piglin_baby", piglin.isBaby());
            data.put("piglin_dancing", piglin.isDancing());
            data.put("piglin_immune", piglin.isImmuneToZombification());
        }

        // ---- 猪灵蛮兵 ----
        if (living instanceof PiglinBrute piglinBrute) {
            data.put("piglin_brute_baby", piglinBrute.isBaby());
            data.put("piglin_brute_immune", piglinBrute.isImmuneToZombification());
        }


        // ---- 史莱姆/岩浆怪 ----
        if (living instanceof Slime slime) {
            data.put("slime_size", slime.getSize());
        }

        // ---- 幻翼 ----
        if (living instanceof Phantom phantom) {
            data.put("phantom_size", phantom.getSize());
        }

        // ---- 恼鬼 ----
        if (living instanceof Vex vex) {
            data.put("vex_charging", vex.isCharging());
        }

        // ---- 潜影贝 ----
        if (living instanceof Shulker shulker) {
            DyeColor color = shulker.getColor();
            if (color != null) {
                data.put("shulker_color", color.name());
            }
            data.put("shulker_peek", shulker.getPeek());
        }

        // ---- 蜘蛛 ----
        if (living instanceof Spider spider) {
            data.put("spider_climbing", spider.isClimbing());
        }

        // ---- 洞穴蜘蛛 ----
        if (living instanceof CaveSpider caveSpider) {
            data.put("cave_spider_climbing", caveSpider.isClimbing());
        }

        // ---- 末影人 ----
        if (living instanceof Enderman enderman) {
            if (enderman.getCarriedBlock() != null) {
                data.put("enderman_carried_block", enderman.getCarriedBlock().getMaterial().getKey().toString());
            }
            data.put("enderman_screaming", enderman.isScreaming());
        }

        // ---- 铁傀儡 ----
        if (living instanceof IronGolem ironGolem) {
            data.put("iron_golem_player_created", ironGolem.isPlayerCreated());
        }

        // ---- 苦力怕 ----
        if (living instanceof Creeper creeper) {
            data.put("creeper_powered", creeper.isPowered());
        }

    }

    // ========== 序列化盔甲架姿势 ==========
    private Map<String, Object> serializePose(ArmorStand armorStand) {
        Map<String, Object> pose = new LinkedHashMap<>();
        pose.put("head", serializeAngle(armorStand.getHeadPose()));
        pose.put("body", serializeAngle(armorStand.getBodyPose()));
        pose.put("left_arm", serializeAngle(armorStand.getLeftArmPose()));
        pose.put("right_arm", serializeAngle(armorStand.getRightArmPose()));
        pose.put("left_leg", serializeAngle(armorStand.getLeftLegPose()));
        pose.put("right_leg", serializeAngle(armorStand.getRightLegPose()));
        return pose;
    }

    private Map<String, Float> serializeAngle(EulerAngle angle) {
        Map<String, Float> data = new LinkedHashMap<>();
        data.put("x", (float) angle.getX());
        data.put("y", (float) angle.getY());
        data.put("z", (float) angle.getZ());
        return data;
    }

    // ========== 反序列化实体（纯 Bukkit API） ==========
    private Entity deserializeAndSpawn(World world, Location loc, String jsonData) throws Exception {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(jsonData, type);

        // ---- 获取实体类型 ----
        String entityTypeStr = (String) data.get("entity_type");
        if (entityTypeStr == null) {
            throw new Exception("缺少实体类型");
        }

        String cleanType = entityTypeStr.replace("minecraft:", "");
        EntityType entityType = null;
        for (EntityType et : EntityType.values()) {
            if (et.getKey().getKey().equals(cleanType)) {
                entityType = et;
                break;
            }
        }
        if (entityType == null) {
            throw new Exception("未知实体类型: " + cleanType);
        }

        // ---- 生成实体 ----
        Entity spawned = world.spawnEntity(loc, entityType, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (spawned == null) {
            throw new Exception("实体生成失败");
        }

        if (!(spawned instanceof LivingEntity living)) {
            return spawned;
        }

        // ---- 恢复生命值 ----
        if (data.containsKey("health")) {
            double health = ((Number) data.get("health")).doubleValue();
            living.setHealth(Math.min(health, living.getMaxHealth()));
        }

        // ---- 恢复自定义名称 ----
        if (data.containsKey("custom_name")) {
            living.setCustomName((String) data.get("custom_name"));
            if (data.containsKey("custom_name_visible")) {
                living.setCustomNameVisible((boolean) data.get("custom_name_visible"));
            }
        }

        // ---- 恢复实体状态 ----
        if (data.containsKey("is_sneaking")) living.setSneaking((boolean) data.get("is_sneaking"));
        if (data.containsKey("is_swimming")) living.setSwimming((boolean) data.get("is_swimming"));
        if (data.containsKey("is_gliding")) living.setGliding((boolean) data.get("is_gliding"));
        if (data.containsKey("is_invisible")) living.setInvisible((boolean) data.get("is_invisible"));
        if (data.containsKey("is_silent")) living.setSilent((boolean) data.get("is_silent"));
        if (data.containsKey("gravity")) living.setGravity((boolean) data.get("gravity"));

        // ---- 恢复着火 ----
        if (data.containsKey("fire_ticks")) {
            int fireTicks = ((Number) data.get("fire_ticks")).intValue();
            if (fireTicks > 0) {
                living.setFireTicks(fireTicks);
            }
        }

        // ---- 恢复年龄 ----
        if (living instanceof Ageable ageable) {
            if (data.containsKey("age")) {
                int age = ((Number) data.get("age")).intValue();
                ageable.setAge(age);
            }
            if (data.containsKey("age_lock")) {
                ageable.setAgeLock((boolean) data.get("age_lock"));
            }
        }

        // ---- 恢复装备 ----
        if (living.getEquipment() != null) {
            if (data.containsKey("helmet")) {
                living.getEquipment().setHelmet(deserializeItem((Map<String, Object>) data.get("helmet")));
            }
            if (data.containsKey("chestplate")) {
                living.getEquipment().setChestplate(deserializeItem((Map<String, Object>) data.get("chestplate")));
            }
            if (data.containsKey("leggings")) {
                living.getEquipment().setLeggings(deserializeItem((Map<String, Object>) data.get("leggings")));
            }
            if (data.containsKey("boots")) {
                living.getEquipment().setBoots(deserializeItem((Map<String, Object>) data.get("boots")));
            }
            if (data.containsKey("main_hand")) {
                living.getEquipment().setItemInMainHand(deserializeItem((Map<String, Object>) data.get("main_hand")));
            }
            if (data.containsKey("off_hand")) {
                living.getEquipment().setItemInOffHand(deserializeItem((Map<String, Object>) data.get("off_hand")));
            }
            if (data.containsKey("item_drop_chance")) {
                float chance = ((Number) data.get("item_drop_chance")).floatValue();
                living.getEquipment().setItemInMainHandDropChance(chance);
            }
        }

        // ---- 恢复药水效果 ----
        if (data.containsKey("potion_effects")) {
            List<Map<String, Object>> effects = (List<Map<String, Object>>) data.get("potion_effects");
            for (Map<String, Object> effectData : effects) {
                try {
                    String typeKey = (String) effectData.get("type");
                    int duration = ((Number) effectData.get("duration")).intValue();
                    int amplifier = ((Number) effectData.get("amplifier")).intValue();
                    boolean ambient = (boolean) effectData.get("ambient");
                    boolean particles = (boolean) effectData.get("particles");
                    boolean icon = (boolean) effectData.get("icon");

                    PotionEffectType effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(
                        typeKey.replace("minecraft:", "")
                    ));
                    if (effectType != null) {
                        PotionEffect effect = new PotionEffect(effectType, duration, amplifier, ambient, particles, icon);
                        living.addPotionEffect(effect);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e[搬运] 恢复药水效果失败: " + e.getMessage());
                }
            }
        }

        // ---- 恢复特殊实体数据 ----
        restoreSpecialEntityData(living, data);

        // ---- 移动到正确位置 ----
        spawned.teleport(loc);

        return spawned;
    }

    // ========== 反序列化物品 ==========
    private ItemStack deserializeItem(Map<String, Object> data) {
        if (data == null) return null;

        try {
            String typeStr = (String) data.get("type");
            if (typeStr == null) return null;

            Material material = null;
            // 方式1: 旧式名称解析（兼容 Paper 1.20 及以下）
            String legacyName = typeStr.replace("minecraft:", "").toUpperCase();
            material = Material.getMaterial(legacyName);
            // 方式2: NamespacedKey 解析（Paper 1.21+ 必需）
            if (material == null) {
                NamespacedKey key = NamespacedKey.fromString(typeStr);
                if (key != null) {
                    for (Material m : Material.values()) {
                        if (m.getKey().equals(key)) {
                            material = m;
                            break;
                        }
                    }
                }
            }
            if (material == null || material.isAir()) return null;

            int amount = data.containsKey("amount") ? ((Number) data.get("amount")).intValue() : 1;
            ItemStack item = new ItemStack(material, amount);

            // 恢复耐久度
            if (data.containsKey("damage") && item.getItemMeta() instanceof Damageable damageable) {
                short damage = ((Number) data.get("damage")).shortValue();
                damageable.setDamage(damage);
                item.setItemMeta((ItemMeta) damageable);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // 恢复自定义名称
                if (data.containsKey("display_name")) {
                    meta.setDisplayName((String) data.get("display_name"));
                }

                // 恢复 Lore
                if (data.containsKey("lore")) {
                    @SuppressWarnings("unchecked")
                    List<String> lore = (List<String>) data.get("lore");
                    meta.setLore(lore);
                }

                // 恢复附魔
                if (data.containsKey("enchants")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> enchants = (Map<String, Integer>) data.get("enchants");
                    for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                        Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(
                            entry.getKey().replace("minecraft:", "")
                        ));
                        if (enchant != null) {
                            meta.addEnchant(enchant, entry.getValue(), true);
                        }
                    }
                }

                // 恢复自定义模型数据
                if (data.containsKey("custom_model_data")) {
                    meta.setCustomModelData(((Number) data.get("custom_model_data")).intValue());
                }

                // 恢复物品标志
                if (data.containsKey("item_flags")) {
                    @SuppressWarnings("unchecked")
                    List<String> flags = (List<String>) data.get("item_flags");
                    for (String flag : flags) {
                        try {
                            meta.addItemFlags(ItemFlag.valueOf(flag));
                        } catch (Exception ignored) {}
                    }
                }

                // 恢复附魔书
                if (material == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta bookMeta) {
                    if (data.containsKey("stored_enchants")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> storedEnchants = (Map<String, Integer>) data.get("stored_enchants");
                        for (Map.Entry<String, Integer> entry : storedEnchants.entrySet()) {
                            Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(
                                entry.getKey().replace("minecraft:", "")
                            ));
                            if (enchant != null) {
                                bookMeta.addStoredEnchant(enchant, entry.getValue(), true);
                            }
                        }
                        item.setItemMeta(bookMeta);
                        return item;
                    }
                }

                item.setItemMeta(meta);
            }

            return item;

        } catch (Exception e) {
            plugin.getLogger().warning("§e[搬运] 反序列化物品失败: " + e.getMessage());
            return null;
        }
    }

    // ========== 恢复特殊实体数据 ==========
    private void restoreSpecialEntityData(LivingEntity living, Map<String, Object> data) {
        // ---- 狼 ----
        if (living instanceof Wolf wolf) {
            if (data.containsKey("wolf_tamed") && (boolean) data.get("wolf_tamed")) {
                wolf.setTamed(true);
                if (data.containsKey("wolf_owner")) {
                    tryRestoreOwner(wolf, UUID.fromString((String) data.get("wolf_owner")));
                }
            }
            if (data.containsKey("wolf_angry")) wolf.setAngry((boolean) data.get("wolf_angry"));
            if (data.containsKey("wolf_sitting")) wolf.setSitting((boolean) data.get("wolf_sitting"));
            if (data.containsKey("wolf_health")) {
                wolf.setHealth(Math.min(((Number) data.get("wolf_health")).doubleValue(), wolf.getMaxHealth()));
            }
        }

        // ---- 猫 ----
        if (living instanceof Cat cat) {
            if (data.containsKey("cat_tamed") && (boolean) data.get("cat_tamed")) {
                cat.setTamed(true);
                if (data.containsKey("cat_owner")) {
                    tryRestoreOwner(cat, UUID.fromString((String) data.get("cat_owner")));
                }
            }
            if (data.containsKey("cat_type")) {
                try { cat.setCatType(Cat.Type.valueOf((String) data.get("cat_type"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("cat_sitting")) cat.setSitting((boolean) data.get("cat_sitting"));
        }

        // ---- 鹦鹉 ----
        if (living instanceof Parrot parrot) {
            if (data.containsKey("parrot_tamed") && (boolean) data.get("parrot_tamed")) {
                parrot.setTamed(true);
                if (data.containsKey("parrot_owner")) {
                    tryRestoreOwner(parrot, UUID.fromString((String) data.get("parrot_owner")));
                }
            }
            if (data.containsKey("parrot_variant")) {
                try { parrot.setVariant(Parrot.Variant.valueOf((String) data.get("parrot_variant"))); } catch (Exception ignored) {}
            }
        }

        // ---- 马科动物 ----
        if (living instanceof AbstractHorse horse) {
            if (data.containsKey("horse_tamed") && (boolean) data.get("horse_tamed")) {
                horse.setTamed(true);
                if (data.containsKey("horse_owner")) {
                    tryRestoreOwner(horse, UUID.fromString((String) data.get("horse_owner")));
                }
            }
            if (data.containsKey("horse_jump_strength")) {
                double jump = ((Number) data.get("horse_jump_strength")).doubleValue();
                horse.setJumpStrength(jump);
            }
            if (data.containsKey("horse_max_health")) {
                try {
                    double maxHealth = ((Number) data.get("horse_max_health")).doubleValue();
                    horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
                } catch (Exception ignored) {}
            }
            if (data.containsKey("horse_movement_speed")) {
                double speed = ((Number) data.get("horse_movement_speed")).doubleValue();
                horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
            }

            if (horse instanceof Horse normalHorse) {
                if (data.containsKey("horse_color")) {
                    try { normalHorse.setColor(Horse.Color.valueOf((String) data.get("horse_color"))); } catch (Exception ignored) {}
                }
                if (data.containsKey("horse_style")) {
                    try { normalHorse.setStyle(Horse.Style.valueOf((String) data.get("horse_style"))); } catch (Exception ignored) {}
                }
            }
            if (horse instanceof ChestedHorse chested) {
                if (data.containsKey("horse_chested")) chested.setCarryingChest((boolean) data.get("horse_chested"));
            }
            if (horse instanceof Llama llama) {
                if (data.containsKey("llama_color")) {
                    try { llama.setColor(Llama.Color.valueOf((String) data.get("llama_color"))); } catch (Exception ignored) {}
                }
                if (data.containsKey("llama_strength")) {
                    llama.setStrength(((Number) data.get("llama_strength")).intValue());
                }
            }
            if (horse instanceof Camel camel) {
                if (data.containsKey("camel_dash")) camel.setDashing((boolean) data.get("camel_dash"));
            }

            // ---- 恢复马科动物的背包(鞍/盔甲/箱子物品) ----
            if (horse.getInventory() != null && data.containsKey("horse_inventory")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> invList = (List<Map<String, Object>>) data.get("horse_inventory");
                for (int i = 0; i < invList.size(); i++) {
                    ItemStack item = deserializeItem(invList.get(i));
                    if (item != null) {
                        horse.getInventory().setItem(i, item);
                    }
                }
            }
        }

        // ---- 羊 ----
        if (living instanceof Sheep sheep) {
            if (data.containsKey("sheep_color")) {
                try { sheep.setColor(DyeColor.valueOf((String) data.get("sheep_color"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("sheep_sheared")) sheep.setSheared((boolean) data.get("sheep_sheared"));
        }

        // ---- 村民 ----
        if (living instanceof Villager villager) {
            // 1. 先用 Registry API 恢复职业（Paper 1.21.8 废弃了 valueOf）
            if (data.containsKey("villager_profession")) {
                String profName = (String) data.get("villager_profession");
                if (profName != null) {
                    try {
                        NamespacedKey profKey = NamespacedKey.fromString("minecraft:" + profName.toLowerCase());
                        if (profKey != null) {
                            Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(profKey);
                            if (profession != null) {
                                villager.setProfession(profession);
                                plugin.getLogger().info("§a[搬运] 村民职业 -> " + profName);
                            } else {
                                plugin.getLogger().warning("§e[搬运] 未找到职业: " + profName);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e[搬运] 设置村民职业失败: " + e.getMessage());
                    }
                }
            }

            // 2. 用 Registry API 恢复村民类型（生物群系）
            if (data.containsKey("villager_type")) {
                String vt = (String) data.get("villager_type");
                if (vt != null) {
                    try {
                        NamespacedKey typeKey = NamespacedKey.fromString(vt);
                        if (typeKey != null) {
                            Villager.Type villagerType = Registry.VILLAGER_TYPE.get(typeKey);
                            if (villagerType != null) {
                                villager.setVillagerType(villagerType);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e[搬运] 设置村民类型失败: " + e.getMessage());
                    }
                }
            }

            // 3. 恢复等级（必须在职业之后，否则等级被重置）
            if (data.containsKey("villager_level")) villager.setVillagerLevel(((Number) data.get("villager_level")).intValue());
            // 4. 恢复经验
            if (data.containsKey("villager_experience")) villager.setVillagerExperience(((Number) data.get("villager_experience")).intValue());
            // 5. 恢复补货次数
            if (data.containsKey("villager_restocks")) villager.setRestocksToday(((Number) data.get("villager_restocks")).intValue());

            // 6. 最后恢复交易内容（在职业/等级都设好后一次性覆盖，延迟 1tick 防 Paper 初始化覆盖）
            if (data.containsKey("villager_recipes")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> recipeList = (List<Map<String, Object>>) data.get("villager_recipes");
                plugin.getLogger().info("§a[搬运] 待恢复交易数据数: " + recipeList.size());
                List<MerchantRecipe> recipes = new ArrayList<>();
                for (Map<String, Object> recipeData : recipeList) {
                    ItemStack result = deserializeItem((Map<String, Object>) recipeData.get("result"));
                    ItemStack ingredient0 = deserializeItem((Map<String, Object>) recipeData.get("ingredient0"));

                    if (result != null && ingredient0 != null) {
                        int uses = ((Number) recipeData.getOrDefault("uses", 0)).intValue();
                        int maxUses = ((Number) recipeData.getOrDefault("max_uses", 0)).intValue();
                        boolean expReward = recipeData.containsKey("experience_reward") && (boolean) recipeData.get("experience_reward");
                        int villExp = ((Number) recipeData.getOrDefault("villager_experience", 0)).intValue();
                        float priceMult = ((Number) recipeData.getOrDefault("price_multiplier", 0)).floatValue();
                        int demand = ((Number) recipeData.getOrDefault("demand", 0)).intValue();
                        int specialPrice = ((Number) recipeData.getOrDefault("special_price", 0)).intValue();

                        // Paper 1.21.8 完整构造器：result, uses, maxUses, experienceReward, villagerExperience, priceMultiplier, demand, specialPrice
                        MerchantRecipe recipe = new MerchantRecipe(result, uses, maxUses, expReward, villExp, priceMult, demand, specialPrice);

                        recipe.addIngredient(ingredient0);

                        if (recipeData.containsKey("ingredient1")) {
                            ItemStack ingredient1 = deserializeItem((Map<String, Object>) recipeData.get("ingredient1"));
                            if (ingredient1 != null) {
                                recipe.addIngredient(ingredient1);
                            }
                        }

                        recipes.add(recipe);
                    }
                }
                if (!recipes.isEmpty()) {
                    villager.setRecipes(recipes);
                    plugin.getLogger().info("§a[搬运] 恢复 " + recipes.size() + " 个交易");
                    // 重复设置 40tick(2秒) 覆盖村民大脑周期性重置
                    List<MerchantRecipe> finalRecipes = recipes;
                    final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];
                    taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                        int tick = 0;
                        @Override
                        public void run() {
                            villager.setRecipes(finalRecipes);
                            tick++;
                            if (tick >= 40) {
                                plugin.getLogger().info("§a[搬运] 交易锁定: " + villager.getRecipes().size() + " 个");
                                if (taskRef[0] != null) taskRef[0].cancel();
                            }
                        }
                    }, 1L, 1L);
                }
            }
        }

        // ---- 僵尸村民 ----
        if (living instanceof ZombieVillager zombieVillager) {
            if (data.containsKey("zombie_villager_profession")) {
                String profName = (String) data.get("zombie_villager_profession");
                if (profName != null) {
                    try {
                        NamespacedKey profKey = NamespacedKey.fromString("minecraft:" + profName.toLowerCase());
                        if (profKey != null) {
                            Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(profKey);
                            if (profession != null) {
                                zombieVillager.setVillagerProfession(profession);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e[搬运] 设置僵尸村民职业失败: " + e.getMessage());
                    }
                }
            }
            if (data.containsKey("zombie_villager_type")) {
                String vt = (String) data.get("zombie_villager_type");
                if (vt != null) {
                    try {
                        NamespacedKey typeKey = NamespacedKey.fromString(vt);
                        if (typeKey != null) {
                            Villager.Type villagerType = Registry.VILLAGER_TYPE.get(typeKey);
                            if (villagerType != null) {
                                zombieVillager.setVillagerType(villagerType);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e[搬运] 设置僵尸村民类型失败: " + e.getMessage());
                    }
                }
            }
            if (data.containsKey("zombie_villager_converting") && (boolean) data.get("zombie_villager_converting")) {
                zombieVillager.setConversionTime(0);
            }
        }

        // ---- 熊猫 ----
        if (living instanceof Panda panda) {
            if (data.containsKey("panda_main_gene")) {
                try { panda.setMainGene(Panda.Gene.valueOf((String) data.get("panda_main_gene"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("panda_hidden_gene")) {
                try { panda.setHiddenGene(Panda.Gene.valueOf((String) data.get("panda_hidden_gene"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("panda_sneeze")) panda.setSneezing((boolean) data.get("panda_sneeze"));
            if (data.containsKey("panda_rolling")) panda.setRolling((boolean) data.get("panda_rolling"));
        }

        // ---- 狐狸 ----
        if (living instanceof Fox fox) {
            if (data.containsKey("fox_type")) {
                try { fox.setFoxType(Fox.Type.valueOf((String) data.get("fox_type"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("fox_sitting")) fox.setSitting((boolean) data.get("fox_sitting"));
            if (data.containsKey("fox_crouching")) fox.setCrouching((boolean) data.get("fox_crouching"));
            if (data.containsKey("fox_sleeping")) fox.setSleeping((boolean) data.get("fox_sleeping"));
        }

        // ---- 海豚 ----
        if (living instanceof Dolphin dolphin) {
            if (data.containsKey("dolphin_moistness")) dolphin.setMoistness(((Number) data.get("dolphin_moistness")).intValue());
        }

        // ---- 美西螈 ----
        if (living instanceof Axolotl axolotl) {
            if (data.containsKey("axolotl_variant")) {
                try { axolotl.setVariant(Axolotl.Variant.valueOf((String) data.get("axolotl_variant"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("axolotl_playing_dead")) axolotl.setPlayingDead((boolean) data.get("axolotl_playing_dead"));
        }

        // ---- 青蛙 ----
        if (living instanceof Frog frog) {
            if (data.containsKey("frog_variant")) {
                try { frog.setVariant(Frog.Variant.valueOf((String) data.get("frog_variant"))); } catch (Exception ignored) {}
            }
        }

        // ---- 嗅探兽 ----
        if (living instanceof Sniffer sniffer) {
            if (data.containsKey("sniffer_egg")) {
                try { sniffer.setState(Sniffer.State.valueOf((String) data.get("sniffer_egg"))); } catch (Exception ignored) {}
            }
        }

        // ---- 炽足兽 ----
        if (living instanceof Strider strider) {
            if (data.containsKey("strider_saddled")) strider.setSaddle((boolean) data.get("strider_saddled"));
            if (data.containsKey("strider_boost_time")) strider.setBoostTicks(((Number) data.get("strider_boost_time")).intValue());
        }

        // ---- 山羊 ----
        if (living instanceof Goat goat) {
            if (data.containsKey("goat_has_left_horn")) goat.setLeftHorn((boolean) data.get("goat_has_left_horn"));
            if (data.containsKey("goat_has_right_horn")) goat.setRightHorn((boolean) data.get("goat_has_right_horn"));
            if (data.containsKey("goat_screaming")) goat.setScreaming((boolean) data.get("goat_screaming"));
        }

        // ---- 蜜蜂 ----
        if (living instanceof Bee bee) {
            if (data.containsKey("bee_anger")) bee.setAnger(((Number) data.get("bee_anger")).intValue());
            if (data.containsKey("bee_has_nectar")) bee.setHasNectar((boolean) data.get("bee_has_nectar"));
            if (data.containsKey("bee_age")) bee.setAge(((Number) data.get("bee_age")).intValue());
        }

        // ---- 盔甲架 ----
        if (living instanceof ArmorStand armorStand) {
            if (data.containsKey("armor_stand_arms")) armorStand.setArms((boolean) data.get("armor_stand_arms"));
            if (data.containsKey("armor_stand_base_plate")) armorStand.setBasePlate((boolean) data.get("armor_stand_base_plate"));
            if (data.containsKey("armor_stand_small")) armorStand.setSmall((boolean) data.get("armor_stand_small"));
            if (data.containsKey("armor_stand_marker")) armorStand.setMarker((boolean) data.get("armor_stand_marker"));
            if (data.containsKey("armor_stand_visible")) armorStand.setVisible((boolean) data.get("armor_stand_visible"));
            if (data.containsKey("armor_stand_pose")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> poseData = (Map<String, Object>) data.get("armor_stand_pose");
                armorStand.setHeadPose(deserializeAngle((Map<String, Float>) poseData.get("head")));
                armorStand.setBodyPose(deserializeAngle((Map<String, Float>) poseData.get("body")));
                armorStand.setLeftArmPose(deserializeAngle((Map<String, Float>) poseData.get("left_arm")));
                armorStand.setRightArmPose(deserializeAngle((Map<String, Float>) poseData.get("right_arm")));
                armorStand.setLeftLegPose(deserializeAngle((Map<String, Float>) poseData.get("left_leg")));
                armorStand.setRightLegPose(deserializeAngle((Map<String, Float>) poseData.get("right_leg")));
            }
        }

        // ---- 猪灵 ----
        if (living instanceof Piglin piglin) {
            if (data.containsKey("piglin_baby")) piglin.setBaby((boolean) data.get("piglin_baby"));
            if (data.containsKey("piglin_dancing")) piglin.setDancing((boolean) data.get("piglin_dancing"));
            if (data.containsKey("piglin_immune")) piglin.setImmuneToZombification((boolean) data.get("piglin_immune"));
        }

        // ---- 猪灵蛮兵 ----
        if (living instanceof PiglinBrute piglinBrute) {
            if (data.containsKey("piglin_brute_baby")) piglinBrute.setBaby((boolean) data.get("piglin_brute_baby"));
            if (data.containsKey("piglin_brute_immune")) piglinBrute.setImmuneToZombification((boolean) data.get("piglin_brute_immune"));
        }

        // ---- 凋灵骷髅 ----

        // ---- 史莱姆/岩浆怪 ----
        if (living instanceof Slime slime) {
            if (data.containsKey("slime_size")) slime.setSize(((Number) data.get("slime_size")).intValue());
        }

        // ---- 幻翼 ----
        if (living instanceof Phantom phantom) {
            if (data.containsKey("phantom_size")) phantom.setSize(((Number) data.get("phantom_size")).intValue());
        }

        // ---- 恼鬼 ----
        if (living instanceof Vex vex) {
            if (data.containsKey("vex_charging")) vex.setCharging((boolean) data.get("vex_charging"));
        }

        // ---- 潜影贝 ----
        if (living instanceof Shulker shulker) {
            if (data.containsKey("shulker_color")) {
                try { shulker.setColor(DyeColor.valueOf((String) data.get("shulker_color"))); } catch (Exception ignored) {}
            }
            if (data.containsKey("shulker_peek")) shulker.setPeek(((Number) data.get("shulker_peek")).intValue());
        }

        // ---- 末影人 ----
        if (living instanceof Enderman enderman) {
            if (data.containsKey("enderman_carried_block")) {
                try {
                    String blockStr = (String) data.get("enderman_carried_block");
                    blockStr = blockStr.replace("minecraft:", "").toUpperCase();
                    Material material = Material.getMaterial(blockStr);
                    if (material != null) {
                        enderman.setCarriedBlock(material.createBlockData());
                    }
                } catch (Exception ignored) {}
            }
            if (data.containsKey("enderman_screaming")) enderman.setScreaming((boolean) data.get("enderman_screaming"));
        }

        // ---- 铁傀儡 ----
        if (living instanceof IronGolem ironGolem) {
            if (data.containsKey("iron_golem_player_created")) {
                ironGolem.setPlayerCreated((boolean) data.get("iron_golem_player_created"));
            }
        }

        // ---- 苦力怕 ----
        if (living instanceof Creeper creeper) {
            if (data.containsKey("creeper_powered")) {
                creeper.setPowered((boolean) data.get("creeper_powered"));
            }
        }
    }

    // ========== 辅助方法 ==========
    private void tryRestoreOwner(Tameable animal, UUID ownerId) {
        // 尝试在线查找玩家，如果不在线则跳过
        if (ownerId == null) return;
        try {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                animal.setOwner(owner);
            } else {
                // 玩家不在线，尝试从离线数据恢复（部分服务端支持）
                // 这里简单处理，只恢复在线玩家
                plugin.getLogger().fine("§e[搬运] 所有者不在线: " + ownerId);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("§e[搬运] 恢复所有者失败: " + e.getMessage());
        }
    }

    private EulerAngle deserializeAngle(Map<String, Float> data) {
        if (data == null) return EulerAngle.ZERO;
        float x = data.containsKey("x") ? data.get("x") : 0;
        float y = data.containsKey("y") ? data.get("y") : 0;
        float z = data.containsKey("z") ? data.get("z") : 0;
        return new EulerAngle(x, y, z);
    }
}
