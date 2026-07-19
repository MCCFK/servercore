package com.mccfk.plugin.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

public class PlayerDataManager {

    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, UUID> lastMessageTargets = new HashMap<>();

    // 家数据缓存：UUID → (家名 → 位置)
    private final Map<UUID, Map<String, Location>> homeLocations = new HashMap<>();
    // UUID → 玩家名（用于文件名映射）
    private final Map<UUID, String> playerNameCache = new HashMap<>();

    private File homeDataDir;
    private Gson gson;
    private Logger logger;

    public void init(File dataFolder, Logger logger) {
        this.logger = logger;
        homeDataDir = new File(dataFolder.getParentFile(), ".MCCFK_ALL_PLUGONSDATA/home");
        if (!homeDataDir.exists()) homeDataDir.mkdirs();
        gson = new GsonBuilder().setPrettyPrinting().create();
        loadAllFromDisk();
    }

    public void saveHomes() {
        for (Map.Entry<UUID, Map<String, Location>> entry : homeLocations.entrySet()) {
            UUID uuid = entry.getKey();
            String playerName = playerNameCache.get(uuid);
            if (playerName != null) {
                savePlayerHomesToFile(playerName, uuid, entry.getValue());
            }
        }
    }

    // ====================== JSON 读写 ======================

    private File getPlayerFile(String playerName) {
        return new File(homeDataDir, playerName + ".json");
    }

    private void savePlayerHomesToFile(String playerName, UUID uuid, Map<String, Location> homes) {
        File file = getPlayerFile(playerName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", uuid.toString());
        data.put("playerName", playerName);

        Map<String, Map<String, Object>> homesData = new LinkedHashMap<>();
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            Location loc = entry.getValue();
            Map<String, Object> locData = new LinkedHashMap<>();
            locData.put("world", loc.getWorld().getName());
            locData.put("x", loc.getX());
            locData.put("y", loc.getY());
            locData.put("z", loc.getZ());
            locData.put("yaw", (double) loc.getYaw());
            locData.put("pitch", (double) loc.getPitch());
            homesData.put(entry.getKey(), locData);
        }
        data.put("homes", homesData);

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            if (logger != null) logger.severe("保存玩家 " + playerName + " 家数据失败: " + e.getMessage());
        }
    }

    private void loadAllFromDisk() {
        homeLocations.clear();
        playerNameCache.clear();

        File[] files = homeDataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = gson.fromJson(reader, mapType);
                if (data == null) continue;

                String uuidStr = (String) data.get("uuid");
                String playerName = (String) data.get("playerName");
                if (uuidStr == null || playerName == null) continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> homesData = (Map<String, Map<String, Object>>) data.get("homes");
                if (homesData == null) continue;

                Map<String, Location> playerHomes = new HashMap<>();
                for (Map.Entry<String, Map<String, Object>> homeEntry : homesData.entrySet()) {
                    Map<String, Object> locData = homeEntry.getValue();
                    String worldName = (String) locData.get("world");
                    if (worldName == null) continue;
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    double x = ((Number) locData.get("x")).doubleValue();
                    double y = ((Number) locData.get("y")).doubleValue();
                    double z = ((Number) locData.get("z")).doubleValue();
                    float yaw = ((Number) locData.get("yaw")).floatValue();
                    float pitch = ((Number) locData.get("pitch")).floatValue();

                    playerHomes.put(homeEntry.getKey(), new Location(world, x, y, z, yaw, pitch));
                }

                if (!playerHomes.isEmpty()) {
                    homeLocations.put(uuid, playerHomes);
                    playerNameCache.put(uuid, playerName);
                }
            } catch (IOException | ClassCastException e) {
                if (logger != null) logger.warning("读取家数据文件 " + file.getName() + " 失败: " + e.getMessage());
            }
        }
    }

    private void ensurePlayerLoaded(Player player) {
        UUID uuid = player.getUniqueId();
        if (homeLocations.containsKey(uuid)) return;

        String playerName = player.getName();
        File file = getPlayerFile(playerName);
        if (!file.exists()) {
            playerNameCache.put(uuid, playerName);
            homeLocations.put(uuid, new HashMap<>());
            return;
        }

        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = gson.fromJson(reader, mapType);
            if (data == null) {
                playerNameCache.put(uuid, playerName);
                homeLocations.put(uuid, new HashMap<>());
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> homesData = (Map<String, Map<String, Object>>) data.get("homes");
            Map<String, Location> playerHomes = new HashMap<>();
            if (homesData != null) {
                for (Map.Entry<String, Map<String, Object>> homeEntry : homesData.entrySet()) {
                    Map<String, Object> locData = homeEntry.getValue();
                    String worldName = (String) locData.get("world");
                    if (worldName == null) continue;
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    double x = ((Number) locData.get("x")).doubleValue();
                    double y = ((Number) locData.get("y")).doubleValue();
                    double z = ((Number) locData.get("z")).doubleValue();
                    float yaw = ((Number) locData.get("yaw")).floatValue();
                    float pitch = ((Number) locData.get("pitch")).floatValue();

                    playerHomes.put(homeEntry.getKey(), new Location(world, x, y, z, yaw, pitch));
                }
            }
            homeLocations.put(uuid, playerHomes);
            playerNameCache.put(uuid, playerName);
        } catch (IOException | ClassCastException e) {
            if (logger != null) logger.warning("读取玩家 " + playerName + " 家数据失败: " + e.getMessage());
            playerNameCache.put(uuid, playerName);
            homeLocations.put(uuid, new HashMap<>());
        }
    }

    // ====================== 位置相关 ======================

    public void savePreviousLocation(Player player, Location location) {
        previousLocations.put(player.getUniqueId(), location);
    }

    public Location getPreviousLocation(Player player) {
        return previousLocations.get(player.getUniqueId());
    }

    public void saveDeathLocation(Player player, Location location) {
        deathLocations.put(player.getUniqueId(), location);
    }

    public Location getDeathLocation(Player player) {
        return deathLocations.get(player.getUniqueId());
    }

    public void setLastMessageTarget(Player player, Player target) {
        lastMessageTargets.put(player.getUniqueId(), target.getUniqueId());
    }

    public UUID getLastMessageTarget(Player player) {
        return lastMessageTargets.get(player.getUniqueId());
    }

    // ====================== 家系统 ======================

    public void setHomeLocation(Player player, String name, Location location) {
        ensurePlayerLoaded(player);
        homeLocations.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(name, location);
        playerNameCache.put(player.getUniqueId(), player.getName());
        // 立即写入文件
        savePlayerHomesToFile(player.getName(), player.getUniqueId(),
                homeLocations.get(player.getUniqueId()));
    }

    public void setHomeLocation(Player player, Location location) {
        setHomeLocation(player, "home", location);
    }

    public Location getHomeLocation(Player player, String name) {
        ensurePlayerLoaded(player);
        Map<String, Location> playerHomes = homeLocations.get(player.getUniqueId());
        if (playerHomes == null) return null;
        return playerHomes.get(name);
    }

    public Location getHomeLocation(Player player) {
        return getHomeLocation(player, "home");
    }

    public Set<String> getHomeNames(Player player) {
        ensurePlayerLoaded(player);
        Map<String, Location> playerHomes = homeLocations.get(player.getUniqueId());
        if (playerHomes == null) return Collections.emptySet();
        return playerHomes.keySet();
    }
}
