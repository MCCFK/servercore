package com.apple.servercore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 临时修复：伤害间隔冷却
 * 限制玩家在短时间内重复受到伤害，避免异常高频率伤害。
 * 冷却时间：500 毫秒 = 0.5 秒（与原版无敌帧一致）
 */
public class DamageCooldownFix implements Listener {

    // 存储每个玩家上次受伤的时间（毫秒）
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    // 冷却时间：500 毫秒 = 0.5 秒（与原版无敌帧一致）
    private static final long COOLDOWN_MS = 500;

    public DamageCooldownFix(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // 只处理玩家受伤
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastDamageTime.get(uuid);

        // 如果之前受过伤，且间隔小于冷却时间，则取消本次伤害
        if (lastTime != null && (currentTime - lastTime) < COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }

        // 更新受伤时间
        lastDamageTime.put(uuid, currentTime);
    }
}
