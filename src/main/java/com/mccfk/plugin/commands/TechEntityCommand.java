package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TechEntityCommand implements CommandExecutor {

    private final MainPlugin plugin;
    private final CarryCommand carryCommand;

    public TechEntityCommand(MainPlugin plugin, CarryCommand carryCommand) {
        this.plugin = plugin;
        this.carryCommand = carryCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.isOp()) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        // 收集所有技术实体蛋
        List<ItemStack> eggs = new ArrayList<>();
        for (String entityKey : AdminCatcherCommand.getTechnicalEntityKeys()) {
            ItemStack egg = createTechEgg(entityKey);
            if (egg != null) {
                eggs.add(egg);
            }
        }

        if (eggs.isEmpty()) {
            sender.sendMessage("§c没有可用的技术实体！");
            return true;
        }

        // 按潜隐盒分装（每盒27格）
        int boxesNeeded = (eggs.size() + 26) / 27;
        for (int box = 0; box < boxesNeeded; box++) {
            ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
            ItemMeta shulkerMeta = shulker.getItemMeta();
            if (shulkerMeta == null) continue;

            shulkerMeta.setDisplayName("§6技术实体蛋 §7[" + (box + 1) + "/" + boxesNeeded + "]");
            shulker.setItemMeta(shulkerMeta);

            // 创建潜隐盒容器
            Inventory shulkerInv = Bukkit.createInventory(null, 27, "技术实体蛋 " + (box + 1) + "/" + boxesNeeded);

            int start = box * 27;
            int end = Math.min(start + 27, eggs.size());
            for (int i = start; i < end; i++) {
                shulkerInv.addItem(eggs.get(i));
            }

            // 将潜隐盒内容存入物品
            org.bukkit.inventory.meta.BlockStateMeta blockMeta = (org.bukkit.inventory.meta.BlockStateMeta) shulker.getItemMeta();
            if (blockMeta == null) continue;
            org.bukkit.block.ShulkerBox shulkerState = (org.bukkit.block.ShulkerBox) blockMeta.getBlockState();
            shulkerState.getInventory().setContents(shulkerInv.getContents());
            blockMeta.setBlockState(shulkerState);
            shulker.setItemMeta(blockMeta);

            Map<Integer, ItemStack> leftover = target.getInventory().addItem(shulker);
            if (!leftover.isEmpty()) {
                target.getWorld().dropItem(target.getLocation(), shulker);
            }
        }

        target.sendMessage("§a已获得 " + eggs.size() + " 个技术实体蛋，共 " + boxesNeeded + " 盒！");
        plugin.getLogger().info("§c[技术实体] 管理员 " + target.getName() + " 获取了 " + eggs.size() + " 个技术实体蛋");

        return true;
    }

    private ItemStack createTechEgg(String entityKey) {
        // 查找对应 EntityType
        EntityType type = null;
        for (EntityType et : EntityType.values()) {
            if (et.getKey().getKey().equals(entityKey)) {
                type = et;
                break;
            }
        }
        if (type == null) return null;

        // 确定容器材质
        Material mat = trySpawnEgg(entityKey);
        if (mat == null) mat = AdminCatcherCommand.getFallbackMaterial(entityKey);
        if (mat == null) mat = Material.VILLAGER_SPAWN_EGG;

        ItemStack egg = new ItemStack(mat);
        ItemMeta meta = egg.getItemMeta();
        if (meta == null) return null;

        // NBT 数据（最小化，仅含实体类型）
        String nbtJson = "{\"entity_type\":\"minecraft:" + entityKey + "\"}";

        meta.getPersistentDataContainer().set(carryCommand.entityNbtKey, PersistentDataType.STRING, nbtJson);
        meta.getPersistentDataContainer().set(carryCommand.entityTypeKey, PersistentDataType.STRING, entityKey);
        meta.getPersistentDataContainer().set(carryCommand.carryFlagKey, PersistentDataType.BYTE, (byte) 1);

        meta.setDisplayName("§6" + formatKey(entityKey));

        List<String> lore = new ArrayList<>();
        lore.add("§7右键点击以释放");
        lore.add("§8[" + entityKey + "]");
        meta.setLore(lore);

        egg.setItemMeta(meta);
        return egg;
    }

    private Material trySpawnEgg(String entityKey) {
        String upper = entityKey.toUpperCase() + "_SPAWN_EGG";
        try {
            return Material.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatKey(String key) {
        String[] parts = key.split("_");
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
}
