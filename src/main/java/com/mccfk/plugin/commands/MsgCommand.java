package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class MsgCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public MsgCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.msg")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /" + label + " <玩家> <消息>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§c玩家 " + args[0] + " 不在线！");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§c不能给自己发送私聊消息！");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        player.sendMessage("§7[私聊] §b我 §7→ §b" + target.getName() + "§7: §f" + message);
        target.sendMessage("§7[私聊] §b" + player.getName() + " §7→ §b我§7: §f" + message);

        plugin.getPlayerDataManager().setLastMessageTarget(player, target);
        plugin.getPlayerDataManager().setLastMessageTarget(target, player);

        return true;
    }
}
