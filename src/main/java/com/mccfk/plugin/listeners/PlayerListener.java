package com.mccfk.plugin.listeners;

import com.apple.servercore.MainPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final MainPlugin plugin;

    public PlayerListener(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getPlayerDataManager().saveDeathLocation(player, player.getLocation());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (plugin.getOpenWorkbenchPlayers().remove(player.getUniqueId())) {
            InventoryView view = event.getView();
            if (view.getType() == InventoryType.WORKBENCH) {
                for (int i = 1; i <= 9; i++) {
                    ItemStack item = view.getInventory(0).getItem(i);
                    if (item != null && !item.getType().isAir()) {
                        view.getInventory(0).setItem(i, null);
                        player.getInventory().addItem(item).values().forEach(
                            leftover -> player.getWorld().dropItem(player.getLocation(), leftover)
                        );
                    }
                }
            }
        }
    }
}
