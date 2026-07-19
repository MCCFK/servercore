package com.apple.servercore.economicsystem;

import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;

public class AppleCoinsCommand implements CommandExecutor, TabCompleter {

    private final MainPlugin plugin;
    private final Apple_Coins appleCoins;
    private final ACFly acFly;

    // 传送点上限价格：2苹果币 = +1上限（与 Apple_Coins.TP_SLOT_PRICE 保持一致）
    public static final int TP_SLOT_PRICE = 2;

    // 兑换苹果币的数量选项 - public static 供 MainPlugin 访问
    public static final int[] EXCHANGE_AMOUNTS = {1, 2, 4, 8, 16, 32, 64};

    public AppleCoinsCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.appleCoins = plugin.getEconomicSystem().getAppleCoins();
        this.acFly = plugin.getEconomicSystem().getAcFly();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e===== 苹果币指令 =====");
            sender.sendMessage("§f/applecoins info - 查看苹果币信息");
            sender.sendMessage("§f/applecoins shop - 打开苹果币商店");
            sender.sendMessage("§f/applecoins exchange - 用附魔金苹果兑换苹果币");
            if (sender.hasPermission("applecoins.admin")) {
                sender.sendMessage("§f/applecoins add <玩家> <数量> - 增加苹果币");
                sender.sendMessage("§f/applecoins remove <玩家> <数量> - 减少苹果币");
                sender.sendMessage("§f/applecoins see <玩家> - 查看玩家苹果币");
                sender.sendMessage("§f/applecoins set <玩家> <数量> - 设置苹果币");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§c只有玩家可以使用此指令");
                    return true;
                }
                openAppleCoinsInfoUI(p);
                return true;
            }
            case "shop" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§c只有玩家可以使用此指令");
                    return true;
                }
                openAppleCoinsShopUI(p);
                return true;
            }
            case "exchange" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§c只有玩家可以使用此指令");
                    return true;
                }
                openExchangeUI(p);
                return true;
            }
            case "add" -> {
                if (!sender.hasPermission("applecoins.admin")) {
                    sender.sendMessage("§c你没有权限！");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c/applecoins add <玩家> <数量>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线！");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    appleCoins.addAppleCoins(target, amount);
                    sender.sendMessage("§a成功给 " + target.getName() + " 增加 " + amount + " 个苹果币");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("applecoins.admin")) {
                    sender.sendMessage("§c你没有权限！");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c/applecoins remove <玩家> <数量>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线！");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (appleCoins.removeAppleCoins(target, amount)) {
                        sender.sendMessage("§a成功从 " + target.getName() + " 减少 " + amount + " 个苹果币");
                    } else {
                        sender.sendMessage("§c玩家苹果币不足！");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                }
                return true;
            }
            case "see" -> {
                if (!sender.hasPermission("applecoins.admin")) {
                    sender.sendMessage("§c你没有权限！");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c/applecoins see <玩家>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore()) {
                    sender.sendMessage("§c玩家不存在！");
                    return true;
                }
                int coins = appleCoins.getAppleCoins(target.getUniqueId());
                sender.sendMessage("§a玩家 " + target.getName() + " 拥有 " + coins + " 个苹果币");
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission("applecoins.admin")) {
                    sender.sendMessage("§c你没有权限！");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c/applecoins set <玩家> <数量>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线！");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    appleCoins.setAppleCoins(target, amount);
                    sender.sendMessage("§a成功设置 " + target.getName() + " 的苹果币为 " + amount);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                }
                return true;
            }
            default -> {
                sender.sendMessage("§c未知指令，输入 /applecoins 查看帮助");
                return true;
            }
        }
    }

    // ====================== 判断是否基岩版 ======================
    private boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 兑换UI（自动判断版本） ======================
    public void openExchangeUI(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockExchangeUI(player);
        } else {
            openJavaExchangeUI(player);
        }
    }

    // ====================== Java版 - 兑换选择UI ======================
    private void openJavaExchangeUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§e兑换苹果币");

        // 填充玻璃边框
        for (int i = 0; i < 9; i++) {
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName("§7");
            border.setItemMeta(borderMeta);
            inv.setItem(i, border);
            inv.setItem(i + 27, border);
        }
        for (int i = 9; i < 36; i += 9) {
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName("§7");
            border.setItemMeta(borderMeta);
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        // 兑换选项
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < EXCHANGE_AMOUNTS.length && i < slots.length; i++) {
            int amount = EXCHANGE_AMOUNTS[i];
            ItemStack item = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6兑换 " + amount + " 个苹果币");
                meta.setLore(List.of(
                        "§7需要: §e" + amount + " 个附魔金苹果",
                        "",
                        "§a点击兑换"
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slots[i], item);
        }

        // ========== 返回按钮 ==========
        ItemStack backBtn = new ItemStack(Material.STONE);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§7⬅️ 返回苹果币商店");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(31, backBtn);

        // ========== 返回主菜单 ==========
        ItemStack backMain = new ItemStack(Material.BARRIER);
        ItemMeta backMainMeta = backMain.getItemMeta();
        if (backMainMeta != null) {
            backMainMeta.setDisplayName("§c⬅️ 返回服务器主菜单");
            backMain.setItemMeta(backMainMeta);
        }
        inv.setItem(32, backMain);

        // 关闭按钮
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c关闭");
            close.setItemMeta(closeMeta);
        }
        inv.setItem(33, close);

        player.openInventory(inv);
    }

    // ====================== 基岩版 - 兑换选择UI ======================
    private void openBedrockExchangeUI(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§e兑换苹果币")
                .content("§7选择要兑换的数量\n§7需要: 1个附魔金苹果 = 1个苹果币");

        for (int amount : EXCHANGE_AMOUNTS) {
            form.button("§6" + amount + " 个苹果币\n§7需要 " + amount + " 个附魔金苹果");
        }

        form.button("§7⬅️ 返回苹果币商店");

        form.validResultHandler((SimpleFormResponse response) -> {
            int id = response.clickedButtonId();
            if (id < EXCHANGE_AMOUNTS.length) {
                int amount = EXCHANGE_AMOUNTS[id];
                doExchange(player, amount);
                // 兑换后重新打开兑换界面
                openBedrockExchangeUI(player);
            } else {
                openBedrockAppleCoinsShopUI(player);
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    // ====================== 执行兑换（public 供 MainPlugin 调用） ======================
    public void doExchange(Player player, int amount) {
        if (amount <= 0) {
            player.sendMessage("§c数量必须大于0！");
            return;
        }

        // 检查玩家是否有足够的附魔金苹果
        int hasApple = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                hasApple += item.getAmount();
            }
        }

        if (hasApple < amount) {
            player.sendMessage("§c附魔金苹果不足！需要 " + amount + " 个，你只有 " + hasApple + " 个");
            return;
        }

        // 移除附魔金苹果
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                int count = item.getAmount();
                if (count <= remaining) {
                    remaining -= count;
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(count - remaining);
                    remaining = 0;
                }
            }
        }

        // 添加苹果币
        appleCoins.addAppleCoins(player, amount);
        player.sendMessage("§a成功用 " + amount + " 个附魔金苹果兑换了 " + amount + " 个苹果币！");
    }

    // ====================== 苹果币信息UI（自动判断版本） ======================
    public void openAppleCoinsInfoUI(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockAppleCoinsInfoUI(player);
        } else {
            openJavaAppleCoinsInfoUI(player);
        }
    }

    // ====================== 苹果币商店UI（自动判断版本） ======================
    public void openAppleCoinsShopUI(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockAppleCoinsShopUI(player);
        } else {
            openJavaAppleCoinsShopUI(player);
        }
    }

    // ====================== Java版 - 苹果币信息UI ======================
    private void openJavaAppleCoinsInfoUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6苹果币信息");

        // 苹果币余额
        ItemStack coinsItem = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta coinsMeta = coinsItem.getItemMeta();
        if (coinsMeta != null) {
            coinsMeta.setDisplayName("§6苹果币余额");
            coinsMeta.setLore(List.of("§7当前拥有: §e" + appleCoins.getAppleCoins(player) + " 个"));
            coinsItem.setItemMeta(coinsMeta);
        }
        inv.setItem(11, coinsItem);

        // 飞行时间
        ItemStack flyItem = new ItemStack(Material.ELYTRA);
        ItemMeta flyMeta = flyItem.getItemMeta();
        if (flyMeta != null) {
            flyMeta.setDisplayName("§b飞行时间");
            flyMeta.setLore(List.of("§7剩余时间: " + acFly.getFormattedFlightTime(player)));
            flyItem.setItemMeta(flyMeta);
        }
        inv.setItem(13, flyItem);

        // 传送点上限
        ItemStack tpSlotItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpSlotMeta = tpSlotItem.getItemMeta();
        if (tpSlotMeta != null) {
            int currentMax = plugin.getTpAsMePoint().getMaxPoints(player.getUniqueId());
            int currentPoints = plugin.getTpAsMePoint().getPoints(player).size();
            tpSlotMeta.setDisplayName("§6传送点上限");
            tpSlotMeta.setLore(List.of(
                    "§7当前上限: §e" + currentMax,
                    "§7已使用: §e" + currentPoints + "/" + currentMax,
                    "",
                    "§7使用 §e/applecoins shop §7购买更多上限",
                    "§7价格: §e" + TP_SLOT_PRICE + " 苹果币/个"
            ));
            tpSlotItem.setItemMeta(tpSlotMeta);
        }
        inv.setItem(15, tpSlotItem);

        // ========== 进入商店按钮 ==========
        ItemStack shopBtn = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta shopMeta = shopBtn.getItemMeta();
        if (shopMeta != null) {
            shopMeta.setDisplayName("§6进入苹果币商店");
            shopMeta.setLore(List.of("§7点击打开苹果币商店"));
            shopBtn.setItemMeta(shopMeta);
        }
        inv.setItem(22, shopBtn);

        // ========== 返回主菜单 ==========
        ItemStack backBtn = new ItemStack(Material.STONE);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§7⬅️ 返回服务器主菜单");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(26, backBtn);

        player.openInventory(inv);
    }

    // ====================== Java版 - 苹果币商店UI ======================
    private void openJavaAppleCoinsShopUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§6苹果币商店");

        // 填充玻璃边框
        for (int i = 0; i < 9; i++) {
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName("§7");
            border.setItemMeta(borderMeta);
            inv.setItem(i, border);
            inv.setItem(i + 27, border);
        }
        for (int i = 9; i < 36; i += 9) {
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName("§7");
            border.setItemMeta(borderMeta);
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        // 飞行时间 - 1小时
        ItemStack hour1 = new ItemStack(Material.CLOCK);
        ItemMeta h1Meta = hour1.getItemMeta();
        if (h1Meta != null) {
            h1Meta.setDisplayName("§b飞行时间 1小时");
            h1Meta.setLore(List.of("§7价格: §e" + Apple_Coins.FLIGHT_PRICE_PER_HOUR + " 苹果币", "", "§a点击购买"));
            hour1.setItemMeta(h1Meta);
        }
        inv.setItem(10, hour1);

        // 飞行时间 - 5小时
        ItemStack hour5 = new ItemStack(Material.CLOCK);
        ItemMeta h5Meta = hour5.getItemMeta();
        if (h5Meta != null) {
            h5Meta.setDisplayName("§b飞行时间 5小时");
            h5Meta.setLore(List.of("§7价格: §e" + (Apple_Coins.FLIGHT_PRICE_PER_HOUR * 5) + " 苹果币", "", "§a点击购买"));
            hour5.setItemMeta(h5Meta);
        }
        inv.setItem(12, hour5);

        // 飞行时间 - 10小时
        ItemStack hour10 = new ItemStack(Material.CLOCK);
        ItemMeta h10Meta = hour10.getItemMeta();
        if (h10Meta != null) {
            h10Meta.setDisplayName("§b飞行时间 10小时");
            h10Meta.setLore(List.of("§7价格: §e" + (Apple_Coins.FLIGHT_PRICE_PER_HOUR * 10) + " 苹果币", "", "§a点击购买"));
            hour10.setItemMeta(h10Meta);
        }
        inv.setItem(14, hour10);

        // 传送点上限 - 购买 +1 上限
        ItemStack tpSlot = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpMeta = tpSlot.getItemMeta();
        if (tpMeta != null) {
            int currentMax = plugin.getTpAsMePoint().getMaxPoints(player.getUniqueId());
            tpMeta.setDisplayName("§6增加传送点上限 +1");
            tpMeta.setLore(List.of(
                    "§7当前上限: §e" + currentMax,
                    "§7价格: §e" + TP_SLOT_PRICE + " 苹果币",
                    "",
                    "§a点击购买"
            ));
            tpSlot.setItemMeta(tpMeta);
        }
        inv.setItem(20, tpSlot);

        // ========== 兑换苹果币 - 改为进入兑换菜单 ==========
        ItemStack exchange = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        ItemMeta exMeta = exchange.getItemMeta();
        if (exMeta != null) {
            exMeta.setDisplayName("§e兑换苹果币");
            exMeta.setLore(List.of(
                    "§7使用附魔金苹果兑换苹果币",
                    "§7支持数量: 1, 2, 4, 8, 16, 32, 64",
                    "",
                    "§a点击选择兑换数量"
            ));
            exchange.setItemMeta(exMeta);
        }
        inv.setItem(22, exchange);

        // ========== 返回信息界面 ==========
        ItemStack backInfo = new ItemStack(Material.PAPER);
        ItemMeta backInfoMeta = backInfo.getItemMeta();
        if (backInfoMeta != null) {
            backInfoMeta.setDisplayName("§7⬅️ 返回苹果币信息");
            backInfo.setItemMeta(backInfoMeta);
        }
        inv.setItem(29, backInfo);

        // ========== 返回主菜单 ==========
        ItemStack backMain = new ItemStack(Material.STONE);
        ItemMeta backMainMeta = backMain.getItemMeta();
        if (backMainMeta != null) {
            backMainMeta.setDisplayName("§7⬅️ 返回服务器主菜单");
            backMain.setItemMeta(backMainMeta);
        }
        inv.setItem(31, backMain);

        // 关闭按钮
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c关闭");
            close.setItemMeta(closeMeta);
        }
        inv.setItem(32, close);

        player.openInventory(inv);
    }

    // ====================== 基岩版 - 苹果币信息UI ======================
    private void openBedrockAppleCoinsInfoUI(Player player) {
        int coins = appleCoins.getAppleCoins(player);
        String flightTime = acFly.getFormattedFlightTime(player);
        int currentMax = plugin.getTpAsMePoint().getMaxPoints(player.getUniqueId());
        int currentPoints = plugin.getTpAsMePoint().getPoints(player).size();

        SimpleForm form = SimpleForm.builder()
                .title("§6苹果币信息")
                .content("""
                        §7===== 苹果币信息 =====
                        §f苹果币: §e%d 个
                        §f飞行时间: %s
                        §f传送点: §e%d/%d
                        §7=====================
                        §7使用 /applecoins exchange 兑换
                        §7使用 /applecoins shop 打开商店
                        """.formatted(coins, flightTime, currentPoints, currentMax))
                .button("§6进入苹果币商店")
                .button("§7⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) {
                        openBedrockAppleCoinsShopUI(player);
                    } else if (id == 1) {
                        plugin.getACcraft().openMainMenu(player);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    // ====================== 基岩版 - 苹果币商店UI ======================
    private void openBedrockAppleCoinsShopUI(Player player) {
        int price1h = Apple_Coins.FLIGHT_PRICE_PER_HOUR;
        int price5h = Apple_Coins.FLIGHT_PRICE_PER_HOUR * 5;
        int price10h = Apple_Coins.FLIGHT_PRICE_PER_HOUR * 10;
        int currentMax = plugin.getTpAsMePoint().getMaxPoints(player.getUniqueId());

        SimpleForm form = SimpleForm.builder()
                .title("§6苹果币商店")
                .content("§7选择你要购买的商品")
                .button("§b飞行时间 1小时 §e" + price1h + " 苹果币")
                .button("§b飞行时间 5小时 §e" + price5h + " 苹果币")
                .button("§b飞行时间 10小时 §e" + price10h + " 苹果币")
                .button("§6增加传送点上限 +1 §e" + TP_SLOT_PRICE + " 苹果币\n§7当前上限: " + currentMax)
                .button("§e兑换苹果币\n§7选择兑换数量")
                .button("§7⬅️ 返回苹果币信息")
                .button("§7⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    switch (id) {
                        case 0 -> {
                            appleCoins.buyFlightTime(player, 1);
                            openBedrockAppleCoinsShopUI(player);
                        }
                        case 1 -> {
                            appleCoins.buyFlightTime(player, 5);
                            openBedrockAppleCoinsShopUI(player);
                        }
                        case 2 -> {
                            appleCoins.buyFlightTime(player, 10);
                            openBedrockAppleCoinsShopUI(player);
                        }
                        case 3 -> {
                            plugin.buyTpSlot(player);
                            openBedrockAppleCoinsShopUI(player);
                        }
                        case 4 -> {
                            openBedrockExchangeUI(player);
                        }
                        case 5 -> openBedrockAppleCoinsInfoUI(player);
                        case 6 -> plugin.getACcraft().openMainMenu(player);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.addAll(Arrays.asList("info", "shop", "exchange"));
            if (sender.hasPermission("applecoins.admin")) {
                list.addAll(Arrays.asList("add", "remove", "see", "set"));
            }
        }
        if (args.length == 2 && sender.hasPermission("applecoins.admin")) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("add") || subCmd.equals("remove") || subCmd.equals("see") || subCmd.equals("set")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }
}