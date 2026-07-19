package com.mccfk.plugin.managers;

import com.apple.servercore.MainPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActionManager implements Listener {

    private final MainPlugin plugin;
    private final Set<UUID> sittingPlayers = new HashSet<>();

    public ActionManager(MainPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ========== 坐 ==========
    public boolean isSitting(UUID uuid) { return sittingPlayers.contains(uuid); }
    public void setSitting(UUID uuid, boolean sitting) {
        if (sitting) sittingPlayers.add(uuid);
        else sittingPlayers.remove(uuid);
    }

    /**
     * 停止玩家所有进行中的动作
     */
    public void stopAllActions(Player p) {
        UUID uuid = p.getUniqueId();

        // 坐（先保存 vehicle 引用再 leaveVehicle，确保能 remove 盔甲架）
        if (sittingPlayers.remove(uuid)) {
            Entity vehicle = p.getVehicle();
            p.leaveVehicle();
            if (vehicle != null) vehicle.remove();
        }

        // 普通骑乘
        if (p.getVehicle() != null) {
            p.leaveVehicle();
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;

        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        boolean hasAction = false;

        // 坐（必须在普通骑乘之前检查，因为坐本质也是骑乘）
        if (sittingPlayers.contains(uuid)) {
            sittingPlayers.remove(uuid);
            Entity vehicle = p.getVehicle();
            p.leaveVehicle();
            if (vehicle != null) vehicle.remove();
            hasAction = true;
        }

        // 普通骑乘
        if (p.getVehicle() != null) {
            p.leaveVehicle();
            hasAction = true;
        }

        if (hasAction) {
            p.sendMessage("§a已结束当前动作");
        }
    }
}
