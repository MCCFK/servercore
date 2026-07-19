package com.apple.servercore.economicsystem;

import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ACFly {

    private final MainPlugin plugin;
    private final Map<UUID, Integer> flightTimes = new HashMap<>(); // 剩余飞行时间（秒）
    private final Map<UUID, Boolean> flyEnabled = new HashMap<>(); // 是否开启飞行
    private File dataFile;
    private FileConfiguration dataConfig;

    public ACFly(MainPlugin plugin) {
        this.plugin = plugin;
        initDataFile();
        loadData();
        startTimer();
    }

    private void initDataFile() {
        dataFile = new File(plugin.getDataFolder(), "acfly_data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("创建 acfly_data.yml 失败!");
            }
        }
    }

    public void loadData() {
        flightTimes.clear();
        flyEnabled.clear();
        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                int time = dataConfig.getInt(uuidStr + ".time", 0);
                boolean enabled = dataConfig.getBoolean(uuidStr + ".enabled", false);
                flightTimes.put(uuid, time);
                flyEnabled.put(uuid, enabled);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效UUID: " + uuidStr);
            }
        }
    }

    public void saveData() {
        dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : flightTimes.entrySet()) {
            UUID uuid = entry.getKey();
            dataConfig.set(uuid.toString() + ".time", entry.getValue());
            dataConfig.set(uuid.toString() + ".enabled", flyEnabled.getOrDefault(uuid, false));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 acfly_data.yml 失败!");
        }
    }

    // 定时器：每秒减少飞行时间（只在飞行开启时减少）
    private void startTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Integer> entry : flightTimes.entrySet()) {
                    UUID uuid = entry.getKey();
                    int time = entry.getValue();

                    // ========== 修复：只有飞行开启时才减少时间 ==========
                    boolean enabled = flyEnabled.getOrDefault(uuid, false);
                    if (!enabled) continue; // 飞行关闭，不减少时间

                    if (time > 0) {
                        flightTimes.put(uuid, time - 1);
                        // 如果时间归零，关闭飞行
                        if (time - 1 <= 0) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline() && p.getAllowFlight()) {
                                p.setAllowFlight(false);
                                p.setFlying(false);
                                flyEnabled.put(uuid, false);
                                p.sendMessage("§c你的飞行时间已用完！");
                            }
                        }
                    } else {
                        // 时间为0但飞行还开着，强制关闭
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline() && p.getAllowFlight()) {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                            flyEnabled.put(uuid, false);
                            p.sendMessage("§c你的飞行时间已用完！");
                        }
                    }
                }
                saveData();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public int getFlightTime(Player player) {
        return flightTimes.getOrDefault(player.getUniqueId(), 0);
    }

    public String getFormattedFlightTime(Player player) {
        int seconds = getFlightTime(player);
        if (seconds <= 0) return "§c无剩余时间";
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("§a%d小时 %d分钟 %d秒", hours, minutes, secs);
    }

    public void addFlightTime(Player player, int seconds) {
        if (seconds <= 0) return;
        UUID uuid = player.getUniqueId();
        flightTimes.put(uuid, getFlightTime(player) + seconds);
        saveData();
        player.sendMessage("§a增加 " + seconds + " 秒飞行时间！");
    }

    public void setFlightTime(Player player, int seconds) {
        if (seconds < 0) seconds = 0;
        flightTimes.put(player.getUniqueId(), seconds);
        if (seconds <= 0) {
            player.setAllowFlight(false);
            player.setFlying(false);
            flyEnabled.put(player.getUniqueId(), false);
        }
        saveData();
    }

    public void toggleFlight(Player player) {
        // ========== 修复1：移除权限检查，让所有玩家都能使用 ==========
        // 如果你想保留权限系统，取消注释下面的代码，并在 plugin.yml 中添加权限
        // if (!player.hasPermission("acfly.use")) {
        //     player.sendMessage("§c你没有飞行权限！");
        //     return;
        // }

        int time = getFlightTime(player);
        if (time <= 0) {
            player.sendMessage("§c你没有剩余飞行时间！请购买飞行时间");
            return;
        }

        boolean current = flyEnabled.getOrDefault(player.getUniqueId(), false);
        boolean newState = !current;

        if (newState) {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage("§a飞行模式已开启！剩余时间: " + getFormattedFlightTime(player));
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage("§c飞行模式已关闭");
        }

        flyEnabled.put(player.getUniqueId(), newState);
        saveData();
    }

    public boolean isFlying(Player player) {
        return flyEnabled.getOrDefault(player.getUniqueId(), false);
    }

    public boolean hasFlightTime(Player player) {
        return getFlightTime(player) > 0;
    }
}