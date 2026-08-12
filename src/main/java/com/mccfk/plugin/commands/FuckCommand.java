package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FuckCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public FuckCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // 获取所有骑乘在玩家身上的乘客（复制一份，避免遍历时修改集合）
        List<Entity> passengers = new ArrayList<>(player.getPassengers());
        if (passengers.isEmpty()) {
            player.sendMessage("§c现在没有人骑乘你");
            return true;
        }

        for (Entity passenger : passengers) {
            player.removePassenger(passenger);
            if (passenger instanceof Player p) {
                p.sendMessage("§e" + player.getName() + " 把你甩了下来！");
            }
        }
        player.sendMessage("§a已甩下 " + passengers.size() + " 个骑乘者");
        return true;
    }
}
