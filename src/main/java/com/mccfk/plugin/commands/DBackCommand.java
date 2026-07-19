package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DBackCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public DBackCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.dback")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        Location deathLoc = plugin.getPlayerDataManager().getDeathLocation(player);
        if (deathLoc == null) {
            player.sendMessage("§c没有可返回的死亡点！");
            return true;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());
        player.teleportAsync(deathLoc);
        player.sendMessage("§a已返回死亡点！");

        return true;
    }
}
