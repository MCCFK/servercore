package com.apple.servercore.economicsystem;

import org.bukkit.entity.Player;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class AppleCoinsPlaceholderExpansion extends PlaceholderExpansion {

    private final Apple_Coins appleCoins;

    public AppleCoinsPlaceholderExpansion(Apple_Coins appleCoins) {
        this.appleCoins = appleCoins;
    }

    @Override
    public String getIdentifier() {
        return "apple_coins";
    }

    @Override
    public String getAuthor() {
        return "ServerCore";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // 持久化：插件重载不丢失注册
    @Override
    public boolean persist() {
        return false;
    }

    // 移除错误的 requiredPlugin，返回 null
    @Override
    public String getRequiredPlugin() {
        return null;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        System.out.println("🔍 [PAPI] apple_coins 请求 | 玩家:" + (player != null ? player.getName() : "null") + " | 参数:" + identifier);
        if (player == null) return null;

        int coins = appleCoins.getAppleCoins(player);

        // %apple_coins%
        if (identifier == null || identifier.isBlank()) {
            return String.valueOf(coins);
        }
        // %apple_coins_amount%
        if ("amount".equalsIgnoreCase(identifier)) {
            return String.valueOf(coins);
        }
        // %apple_coins_formatted%
        if ("formatted".equalsIgnoreCase(identifier)) {
            return String.format("%,d", coins);
        }
        // %apple_coins_has%
        if ("has".equalsIgnoreCase(identifier)) {
            return coins > 0 ? "true" : "false";
        }
        // %apple_coins_color%
        if ("color".equalsIgnoreCase(identifier)) {
            return coins > 0 ? "§a" + coins : "§c" + coins;
        }

        return null;
    }
}