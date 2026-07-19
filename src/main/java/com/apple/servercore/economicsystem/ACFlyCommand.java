package com.apple.servercore.economicsystem;

import com.apple.servercore.MainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ACFlyCommand implements CommandExecutor {

    private final MainPlugin plugin;
    private final ACFly acFly;

    public ACFlyCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.acFly = plugin.getEconomicSystem().getAcFly();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此指令");
            return true;
        }

        acFly.toggleFlight(player);
        return true;
    }
}