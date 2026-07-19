package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SitCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public SitCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // 已经在坐 -> 站起来并清除盔甲架
        if (plugin.getActionManager().isSitting(player.getUniqueId())) {
            Entity vehicle = player.getVehicle();
            player.leaveVehicle();
            if (vehicle != null) vehicle.remove();
            plugin.getActionManager().setSitting(player.getUniqueId(), false);
            player.sendMessage("§a已站起来");
            return true;
        }

        Location loc = player.getLocation();

        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);

        stand.addPassenger(player);
        plugin.getActionManager().setSitting(player.getUniqueId(), true);
        player.sendMessage("§a已坐下，蹲下结束");
        return true;
    }
}
