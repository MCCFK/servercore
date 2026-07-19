package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;

public class AdminCatcherCommand implements CommandExecutor, Listener {

    private final MainPlugin plugin;
    private final NamespacedKey adminCatcherFlagKey;
    private final CarryCommand carryCommand;
    private final Gson gson;

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
        this.gson = new GsonBuilder().create();
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

        // 排除玩家
        if (target instanceof Player) {
            player.sendMessage("§c不能捕捉玩家！/ Cannot capture players!");
            return;
        }

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

        } catch (Exception e) {
            player.sendMessage("§c捕捉失败: " + e.getMessage());
            plugin.getLogger().severe("§c[管理员捕捉器] 捕捉失败: " + e.getMessage());
            e.printStackTrace();
        }
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

    private String formatEntityName(Entity entity) {
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        return "§f" + entity.getName();
    }
}
