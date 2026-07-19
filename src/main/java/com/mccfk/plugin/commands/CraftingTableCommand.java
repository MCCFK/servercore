package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CraftingTableCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public CraftingTableCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.craftingtable")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        plugin.getOpenWorkbenchPlayers().add(player.getUniqueId());
        player.openWorkbench(player.getLocation(), true);
        player.sendMessage("§a已打开工作台！");
        return true;
    }
}
