package com.apple.servercore.economicsystem;

import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Apple_Coins {

    private final MainPlugin plugin;
    private final Map<UUID, Integer> appleCoins = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    // ========== 价格配置 ==========
    public static final int FLIGHT_PRICE_PER_HOUR = 8;
    public static final int TP_SLOT_PRICE = 2;
    public static final int FLIGHT_DURATION_PER_HOUR = 3600;

    private AppleCoinsPlaceholderExpansion expansion;

    public Apple_Coins(MainPlugin plugin) {
        this.plugin = plugin;
        initDataFile();
        loadData();

        // ========== 注册 PlaceholderAPI（同步注册，不延迟） ==========
        registerPlaceholderAPI();
    }

    // 暴露给外部，用于兜底二次注册
    public void registerPlaceholderAPI() {
        try {
            var papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null || !papi.isEnabled()) {
                plugin.getLogger().warning("⚠️ PlaceholderAPI 未加载，苹果币变量不可用");
                return;
            }

            // 注销旧实例防止冲突
            if (expansion != null) {
                try {
                    expansion.unregister();
                } catch (Exception e) {
                    plugin.getLogger().warning("注销旧苹果币PAPI扩展异常: " + e.getMessage());
                }
            }

            expansion = new AppleCoinsPlaceholderExpansion(this);
            // 同步主线程直接注册，不使用调度延迟
            boolean success = expansion.register();
            if (success) {
                plugin.getLogger().info("✅ PlaceholderAPI 扩展已注册: %apple_coins%");
            } else {
                plugin.getLogger().severe("❌ %apple_coins% PAPI扩展注册返回false");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("注册苹果币PAPI扩展失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initDataFile() {
        dataFile = new File(plugin.getDataFolder(), "apple_coins.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("创建 apple_coins.yml 失败!");
            }
        }
    }

    public void loadData() {
        appleCoins.clear();
        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                int amount = dataConfig.getInt(uuidStr, 0);
                appleCoins.put(uuid, amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效UUID: " + uuidStr);
            }
        }
    }

    public void saveData() {
        dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : appleCoins.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 apple_coins.yml 失败!");
        }
    }

    public int getAppleCoins(Player player) {
        return appleCoins.getOrDefault(player.getUniqueId(), 0);
    }

    public int getAppleCoins(UUID uuid) {
        return appleCoins.getOrDefault(uuid, 0);
    }

    public void addAppleCoins(Player player, int amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        appleCoins.put(uuid, getAppleCoins(player) + amount);
        saveData();
        player.sendMessage("§a获得 " + amount + " 个苹果币！当前: " + getAppleCoins(player));
    }

    public boolean removeAppleCoins(Player player, int amount) {
        if (amount <= 0) return false;
        int current = getAppleCoins(player);
        if (current < amount) return false;
        appleCoins.put(player.getUniqueId(), current - amount);
        saveData();
        return true;
    }

    public void setAppleCoins(Player player, int amount) {
        if (amount < 0) amount = 0;
        appleCoins.put(player.getUniqueId(), amount);
        saveData();
    }

    public void setAppleCoins(UUID uuid, int amount) {
        if (amount < 0) amount = 0;
        appleCoins.put(uuid, amount);
        saveData();
    }

    public boolean exchangeEnchantedGoldenApple(Player player) {
        boolean hasApple = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                int amount = item.getAmount();
                if (amount > 0) {
                    if (amount > 1) {
                        item.setAmount(amount - 1);
                    } else {
                        player.getInventory().setItem(i, null);
                    }
                    hasApple = true;
                    break;
                }
            }
        }

        if (!hasApple) {
            player.sendMessage("§c你背包里没有附魔金苹果！");
            return false;
        }

        addAppleCoins(player, 1);
        player.sendMessage("§a成功用1个附魔金苹果兑换了1个苹果币！");
        return true;
    }

    public boolean buyFlightTime(Player player, int hours) {
        if (hours <= 0) {
            player.sendMessage("§c小时数必须大于0！");
            return false;
        }

        int totalPrice = hours * FLIGHT_PRICE_PER_HOUR;
        if (getAppleCoins(player) < totalPrice) {
            player.sendMessage("§c苹果币不足！需要 " + totalPrice + " 个苹果币（" + FLIGHT_PRICE_PER_HOUR + "苹果币/小时）");
            return false;
        }

        if (!removeAppleCoins(player, totalPrice)) {
            player.sendMessage("§c扣除苹果币失败！");
            return false;
        }

        ACFly flyManager = plugin.getEconomicSystem().getAcFly();
        if (flyManager != null) {
            int seconds = hours * FLIGHT_DURATION_PER_HOUR;
            flyManager.addFlightTime(player, seconds);
            player.sendMessage("§a成功购买 " + hours + " 小时飞行时间！");
            player.sendMessage("§a当前剩余飞行时间: " + flyManager.getFormattedFlightTime(player));
        } else {
            player.sendMessage("§c飞行系统未初始化！");
            return false;
        }
        return true;
    }

    public boolean buyTpSlot(Player player) {
        int price = TP_SLOT_PRICE;
        if (getAppleCoins(player) < price) {
            player.sendMessage("§c苹果币不足！需要 " + price + " 个苹果币增加1个传送点");
            return false;
        }

        if (!removeAppleCoins(player, price)) {
            player.sendMessage("§c扣除苹果币失败！");
            return false;
        }

        int currentMax = plugin.tpAsMePoint.getMaxPoints(player.getUniqueId());
        plugin.tpAsMePoint.setMaxPoints(player.getUniqueId(), currentMax + 1);

        player.sendMessage("§a成功增加1个传送点上限！当前上限: " + (currentMax + 1));
        player.sendMessage("§a消耗: " + price + " 个苹果币");
        return true;
    }

    public Set<UUID> getAllPlayers() {
        return new HashSet<>(appleCoins.keySet());
    }
}