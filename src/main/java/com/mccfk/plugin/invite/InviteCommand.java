package com.mccfk.plugin.invite;

import com.apple.servercore.MainPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InviteCommand implements CommandExecutor, TabCompleter {

    private final MainPlugin plugin;
    private final InviteCodeManager inviteCodeManager;

    public InviteCommand(MainPlugin plugin, InviteCodeManager inviteCodeManager) {
        this.plugin = plugin;
        this.inviteCodeManager = inviteCodeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可使用此指令！");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player);
            case "input" -> handleInput(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6===== 邀请码系统 =====");
        player.sendMessage("§e/invite create §7- 创建邀请码");
        player.sendMessage("§e/invite input <邀请码> §7- 输入邀请码");
    }

    private void handleCreate(Player player) {
        String code = inviteCodeManager.createCode(player);
        
        // 创建带点击复制的消息
        Component clickMsg = Component.text()
                .append(Component.text("§a邀请码创建成功！\n"))
                .append(Component.text("§e你的邀请码: §f" + code)
                        .clickEvent(ClickEvent.copyToClipboard(code)))
                .append(Component.text(" §7[点击复制]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.copyToClipboard(code)))
                .append(Component.text("\n§7分享给朋友输入后可获得奖励！"))
                .build();
        
        player.sendMessage(clickMsg);
    }

    private void handleInput(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /invite input <邀请码>");
            return;
        }

        String code = args[1].toUpperCase();
        String result = inviteCodeManager.useCode(player, code);
        if (result != null) {
            player.sendMessage(result);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "input");
        }
        return new ArrayList<>();
    }
}
