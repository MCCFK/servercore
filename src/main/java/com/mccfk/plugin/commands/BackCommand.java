package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BackCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public BackCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.back")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        Location backLoc = plugin.getPlayerDataManager().getPreviousLocation(player);
        if (backLoc == null) {
            player.sendMessage("§c没有可返回的位置！");
            return true;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());
        player.teleportAsync(backLoc);
        player.sendMessage("§a已返回上一个位置！");

        return true;
    }
}
