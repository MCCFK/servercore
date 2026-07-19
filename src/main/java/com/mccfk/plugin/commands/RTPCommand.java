package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RTPCommand implements CommandExecutor {

    private final MainPlugin plugin;
    private static final int RADIUS = 10000;
    private static final int MAX_ATTEMPTS = 15;
    private static final long COOLDOWN_MILLIS = 5000;
    private static final int CHUNK_LOAD_RADIUS = 1;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RTPCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!sender.hasPermission("mccfk.rtp")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        long now = System.currentTimeMillis();
        long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUsed < COOLDOWN_MILLIS) {
            int remaining = (int) ((COOLDOWN_MILLIS - (now - lastUsed)) / 1000) + 1;
            player.sendMessage("§c请等待 " + remaining + " 秒后再使用！");
            return true;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());

        World world = player.getWorld();
        player.sendMessage("§e正在寻找安全位置...");

        final int[] attempts = {0};
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            attempts[0]++;
            Location loc = getRandomSafeLocation(world);
            if (loc != null) {
                task.cancel();
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.sendMessage("§e正在预加载区块...");
                preloadChunks(loc).thenRun(() -> {
                    plugin.getServer().getGlobalRegionScheduler().run(plugin, (scheduledTask) -> {
                        if (player.isOnline()) {
                            player.teleportAsync(loc);
                            player.sendMessage("§a你已被随机传送到 §b" + loc.getBlockX() + "§a, §b" + loc.getBlockZ() + "§a 附近！");
                        }
                    });
                });
            } else if (attempts[0] >= MAX_ATTEMPTS) {
                task.cancel();
                player.sendMessage("§c无法找到安全的传送位置，请重试！");
            }
        }, 1L, 1L);

        return true;
    }

    private CompletableFuture<Void> preloadChunks(Location location) {
        World world = location.getWorld();
        if (world == null) return CompletableFuture.completedFuture(null);

        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;

        int total = (CHUNK_LOAD_RADIUS * 2 + 1) * (CHUNK_LOAD_RADIUS * 2 + 1);
        CompletableFuture<?>[] futures = new CompletableFuture<?>[total];

        int index = 0;
        for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
            for (int dz = -CHUNK_LOAD_RADIUS; dz <= CHUNK_LOAD_RADIUS; dz++) {
                futures[index++] = world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz);
            }
        }

        return CompletableFuture.allOf(futures);
    }

    private Location getRandomSafeLocation(World world) {
        World.Environment env = world.getEnvironment();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int x = ThreadLocalRandom.current().nextInt(-RADIUS, RADIUS + 1);
        int z = ThreadLocalRandom.current().nextInt(-RADIUS, RADIUS + 1);

        if (env == World.Environment.NETHER) {
            for (int i = 0; i < 30; i++) {
                int y = ThreadLocalRandom.current().nextInt(minY + 1, maxY);
                Block below = world.getBlockAt(x, y - 1, z);
                Block ground = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);

                if (below.getType().isSolid()
                    && ground.getType().isAir()
                    && above.getType().isAir()
                    && below.getType() != Material.LAVA
                    && below.getType() != Material.MAGMA_BLOCK) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        } else {
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (ground.getType().isSolid()
                && above.getType().isAir()
                && head.getType().isAir()
                && ground.getType() != Material.LAVA
                && ground.getType() != Material.MAGMA_BLOCK) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }

            if (ground.getType() == Material.WATER
                && above.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
}
