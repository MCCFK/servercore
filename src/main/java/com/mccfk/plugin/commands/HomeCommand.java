package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final MainPlugin plugin;
    // 待确认覆盖：玩家UUID → (家名 → 过期时间戳)
    private final Map<UUID, Map<String, Long>> pendingOverwrite = new HashMap<>();
    private static final long CONFIRM_TIMEOUT = 30_000L; // 30秒超时

    public HomeCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("sethome")) {
            return handleSetHome(player, args);
        } else {
            return handleHome(player, args);
        }
    }

    private boolean handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("mccfk.sethome")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        String homeName = (args.length > 0 && !args[0].isEmpty()) ? args[0] : "home";

        // 检查是否为确认覆盖
        boolean isConfirm = false;
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            isConfirm = true;
        }

        Location existing = plugin.getPlayerDataManager().getHomeLocation(player, homeName);
        if (existing != null && !isConfirm) {
            // 检查是否是重复发送同一条指令（自然确认）
            Map<String, Long> pending = pendingOverwrite.get(player.getUniqueId());
            if (pending != null) {
                Long expire = pending.get(homeName);
                if (expire != null && System.currentTimeMillis() < expire) {
                    // 玩家在超时内再次输入相同指令，视为确认
                    pending.remove(homeName);
                    if (pending.isEmpty()) pendingOverwrite.remove(player.getUniqueId());
                    doSetHome(player, homeName);
                    return true;
                }
            }

            // 提示覆盖确认
            player.sendMessage("§e⚠ 已存在名为 §b" + homeName + " §e的家！");
            player.sendMessage("§7再次发送 §f/sethome " + homeName + " §7或点击下方按钮确认覆盖：");

            Component confirmMsg = Component.text()
                    .content("[§a确认覆盖§r]")
                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/sethome " + homeName + " confirm"))
                    .build();
            player.sendMessage(confirmMsg);

            // 记录待确认
            pendingOverwrite.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                    .put(homeName, System.currentTimeMillis() + CONFIRM_TIMEOUT);
            return true;
        }

        // 清除待确认记录
        Map<String, Long> pending = pendingOverwrite.get(player.getUniqueId());
        if (pending != null) {
            pending.remove(homeName);
            if (pending.isEmpty()) pendingOverwrite.remove(player.getUniqueId());
        }

        doSetHome(player, homeName);
        return true;
    }

    private void doSetHome(Player player, String homeName) {
        plugin.getPlayerDataManager().setHomeLocation(player, homeName, player.getLocation());
        player.sendMessage("§a已设置家 §b" + homeName + " §a位置！");
    }

    private boolean handleHome(Player player, String[] args) {
        if (!player.hasPermission("mccfk.home")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        String homeName = (args.length > 0 && !args[0].isEmpty()) ? args[0] : "home";

        Location homeLoc = plugin.getPlayerDataManager().getHomeLocation(player, homeName);
        if (homeLoc == null) {
            if (homeName.equals("home")) {
                player.sendMessage("§c你还没有设置家，请使用 /sethome [名称] 设置！");
            } else {
                player.sendMessage("§c你没有名为 §b" + homeName + " §c的家！");
            }
            return true;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());
        player.teleportAsync(homeLoc);
        player.sendMessage("§a已传送到家 §b" + homeName + "§a！");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return null;
        if (args.length != 1) return null;

        String cmdName = command.getName().toLowerCase();
        if (!cmdName.equals("home")) return null;

        Set<String> homeNames = plugin.getPlayerDataManager().getHomeNames(player);
        if (homeNames.isEmpty()) return null;

        String partial = args[0].toLowerCase();
        List<String> result = new ArrayList<>();
        for (String name : homeNames) {
            if (name.toLowerCase().startsWith(partial)) {
                result.add(name);
            }
        }
        return result;
    }
}
