package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RideCommand implements CommandExecutor {

    private final MainPlugin plugin;

    // 原版可骑乘生物类型
    private static final Set<String> RIDEABLE_TYPES = new HashSet<>();
    static {
        RIDEABLE_TYPES.add("HORSE");
        RIDEABLE_TYPES.add("DONKEY");
        RIDEABLE_TYPES.add("MULE");
        RIDEABLE_TYPES.add("SKELETON_HORSE");
        RIDEABLE_TYPES.add("ZOMBIE_HORSE");
        RIDEABLE_TYPES.add("LLAMA");
        RIDEABLE_TYPES.add("TRADER_LLAMA");
        RIDEABLE_TYPES.add("PIG");
        RIDEABLE_TYPES.add("STRIDER");
        RIDEABLE_TYPES.add("CAMEL");
    }

    public RideCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // ========== /fuck：甩下骑乘者 ==========
        if (command.getName().equalsIgnoreCase("fuck")) {
            if (player.getPassengers().isEmpty()) {
                player.sendMessage("§c现在没有人骑在你身上");
                return true;
            }
            List<Entity> riders = List.copyOf(player.getPassengers());
            player.eject();
            player.sendMessage("§a已将骑乘者甩下来");
            for (Entity rider : riders) {
                if (rider instanceof Player riderPlayer) {
                    riderPlayer.sendMessage("§c你被 " + player.getName() + " 甩了下来");
                }
            }
            return true;
        }

        // 已经在骑乘
        if (player.getVehicle() != null) {
            player.leaveVehicle();
            player.sendMessage("§a已解除骑乘");
            return true;
        }

        Entity target = null;

        // 有参数：指定玩家名
        if (args.length > 0) {
            Player targetPlayer = plugin.getServer().getPlayer(args[0]);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                target = targetPlayer;
            } else {
                player.sendMessage("§c找不到该玩家");
                return true;
            }
        } else {
            // 无参数：射线检测目标
            RayTraceResult ray = player.rayTraceEntities(8, false);
            if (ray != null && ray.getHitEntity() != null) {
                target = ray.getHitEntity();
            }
        }

        if (target == null) {
            player.sendMessage("§c没有找到可骑乘的目标，请看向目标或输入玩家名");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§c不能骑乘自己");
            return true;
        }

        // 目标玩家拒绝被骑乘检查
        if (target instanceof Player targetPlayer) {
            if (!plugin.getPersonalSettings().isAllowBeRidden(targetPlayer.getUniqueId())) {
                player.sendMessage("§c该玩家禁止被骑乘！");
                targetPlayer.sendMessage("§e" + player.getName() + " 试图骑乘你，但你已关闭允许被骑乘");
                return true;
            }
        }

        // 权限检查：非OP只能骑乘原版可骑乘生物或玩家
        if (!player.hasPermission("servercore.op")) {
            boolean isPlayer = target instanceof Player;
            boolean isRideable = RIDEABLE_TYPES.contains(target.getType().name());
            if (!isPlayer && !isRideable) {
                player.sendMessage("§c你不能骑乘该实体！");
                return true;
            }
        }

        target.addPassenger(player);
        player.sendMessage("§a已骑乘 " + (target instanceof Player ? target.getName() : target.getType().name()));

        // 被骑乘的玩家提示：可用 /fuck 将他甩下来
        if (target instanceof Player targetPlayer) {
            targetPlayer.sendMessage("§e" + player.getName() + " 骑到了你头上，发 §f/fuck §e将他甩下来！");
        }
        return true;
    }
}
