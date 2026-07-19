package com.apple.servercore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

public class QuickMenuListener implements Listener {

    private final MainPlugin plugin;

    public QuickMenuListener(MainPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!plugin.personalSettings.isQuickMenuEnabled(uuid)) return;
        if (p.getInventory().getHeldItemSlot() != 0) return;

        long now = System.currentTimeMillis();
        long last = plugin.personalSettings.getSneakTime(uuid);

        if (now - last > 1500) {
            plugin.personalSettings.setSneakCount(uuid, 0);
        }

        plugin.personalSettings.setSneakTime(uuid, now);
        int count = plugin.personalSettings.getSneakCount(uuid) + 1;
        plugin.personalSettings.setSneakCount(uuid, count);

        if (count >= 5) {
            plugin.personalSettings.setSneakCount(uuid, 0);
            plugin.personalSettings.setSneakTime(uuid, 0L);
            plugin.getACcraft().openMainMenu(p);
        }
    }
}