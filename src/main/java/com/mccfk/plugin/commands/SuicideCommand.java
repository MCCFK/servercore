package com.mccfk.plugin.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SuicideCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.suicide")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.getType() == Material.TOTEM_OF_UNDYING) {
            player.getInventory().setItemInMainHand(null);
            player.getInventory().addItem(mainHand).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        }
        if (offHand.getType() == Material.TOTEM_OF_UNDYING) {
            player.getInventory().setItemInOffHand(null);
            player.getInventory().addItem(offHand).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        }

        player.setHealth(0);
        return true;
    }
}
