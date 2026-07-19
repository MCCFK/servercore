package com.apple.servercore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;
import java.util.UUID;

public class ACcraft implements CommandExecutor, Listener {

    public final MainPlugin plugin;
    private static final String MENU_TITLE = "§6§l服务器主菜单";

    public ACcraft(MainPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==============================
    // 统一打开菜单（基岩版 + Java版）
    // ==============================
    public void openMainMenu(Player player) {
        if (isBedrockPlayer(player.getUniqueId())) {
            openBedrockMainMenu(player);
            return;
        }
        openJavaMainMenu(player);
    }

    // ==============================
    // 判断是否基岩版玩家
    // ==============================
    private boolean isBedrockPlayer(UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Exception e) {
            return false;
        }
    }

    // ==============================
    // 基岩版主菜单
    // ==============================
    private void openBedrockMainMenu(Player player) {
        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§6主菜单")
                    .content("§7欢迎回来，请选择功能")
                    .button("§a服务器商店")
                    .button("§c物品出售")
                    .button("§e每日签到")
                    .button("§b传送系统")
                    .button("§6工会系统")
                    .button("§d音乐系统")
                    .button("§f称号系统")          // 新增：称号系统
                    .button("§9个人信息")
                    .button("§6苹果币系统")
                    .button("§b服务器排行榜")
                    .button("§a银行")
                    .button("§7服务器公告")
                    .button("§a便捷功能")
                    .validResultHandler((SimpleFormResponse response) -> {
                        int id = response.clickedButtonId();
                        switch (id) {
                            case 0 -> plugin.economicSystem.openShop(player);
                            case 1 -> plugin.economicSystem.openSell(player);
                            case 2 -> plugin.gift.openGiftUI(player);
                            case 3 -> plugin.getTpAsMe().openMainUI(player);
                            case 4 -> plugin.guildManager.openMainMenu(player);
                            case 5 -> plugin.music.openMain(player);
                            case 6 -> player.performCommand("plt open");   // 称号系统
                            case 7 -> plugin.openPlayerInfoUI(player);
                            case 8 -> player.performCommand("applecoins info");
                            case 9 -> plugin.ranking.openRankMenu(player);
                            case 10 -> plugin.ranking.openJavaBankMenu(player);
                            case 11 -> plugin.handleBook(player);
                            case 12 -> openBedrockConvenientGUI(player);
                        }
                    })
                    .build();
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            openJavaMainMenu(player);
            e.printStackTrace();
        }
    }

    // ==============================
    // Java版主菜单
    // ==============================
    public void openJavaMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, MENU_TITLE);

        for (int i = 0; i < menu.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            menu.setItem(i, filler);
        }

        // 第一行
        menu.setItem(10, item(Material.EMERALD, "§a服务器商店"));
        menu.setItem(11, item(Material.HOPPER, "§c物品出售"));
        menu.setItem(12, item(Material.CLOCK, "§e每日签到"));
        menu.setItem(14, item(Material.ENDER_PEARL, "§b传送系统"));
        menu.setItem(16, item(Material.GOLDEN_SWORD, "§6工会系统"));

        // 第二行
        menu.setItem(19, item(Material.JUKEBOX, "§d音乐系统"));
        menu.setItem(21, item(Material.NAME_TAG, "§f称号系统"));   // 新增：称号系统
        menu.setItem(23, item(Material.PLAYER_HEAD, "§9个人信息"));
        menu.setItem(25, item(Material.GOLDEN_APPLE, "§6苹果币系统"));

        // 便捷功能
        menu.setItem(28, item(Material.COMPASS, "§a便捷功能", "§7点击打开便捷功能菜单"));

        // 第三行
        menu.setItem(32, item(Material.BOOK, "§7服务器公告"));
        menu.setItem(34, item(Material.DIAMOND, "§b服务器排行榜"));

        // 第四行 - 银行按钮（排行榜下面一格）
        ItemStack bank = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta bankMeta = bank.getItemMeta();
        bankMeta.setDisplayName("§a银行");
        bankMeta.setLore(List.of("§724点游戏 | 老虎机"));
        bankMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        bankMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        bank.setItemMeta(bankMeta);
        menu.setItem(43, bank);

        player.openInventory(menu);
    }

    // ==============================
    // 便捷功能 GUI
    // ==============================
    public void openConvenientGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6便捷功能");

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }

        inv.setItem(19, item(Material.CRAFTING_TABLE, "§a便捷工作台", "§7发送/craftingtable也有同样的效果"));
        inv.setItem(20, item(Material.ENDER_CHEST, "§5便捷末影箱", "§7发送/enderchest也有同样的效果"));
        inv.setItem(21, item(Material.SKELETON_SKULL, "§c自杀", "§7发送/suicide也有同样的效果"));
        inv.setItem(22, item(Material.GRAY_WOOL, "§b随机传送", "§7发送/rtp也有同样的效果"));
        inv.setItem(23, item(Material.GRAY_WOOL, "§b返回上一个位置", "§7发送/back也有同样的效果"));
        inv.setItem(24, item(Material.GRAY_WOOL, "§b返回死亡点", "§7发送/dback也有同样的效果"));

        // 便捷动作
        inv.setItem(30, item(Material.SADDLE, "§6骑乘", "§7/ride - 骑乘目标实体或玩家"));
        inv.setItem(31, item(Material.OAK_STAIRS, "§6坐下", "§7/sit - 原地坐下"));

        // 实体捕捉器
        inv.setItem(32, item(Material.BLAZE_ROD, "§b实体捕捉器", "§7右键获取实体捕捉器"));

        inv.setItem(49, item(Material.BARRIER, "§7⬅️ 返回主菜单"));

        p.openInventory(inv);
    }

    // ==============================
    // 基岩版便捷功能
    // ==============================
    private void openBedrockConvenientGUI(Player p) {
        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§6便捷功能")
                    .content("§7请选择要使用的功能")
                    .button("§a便捷工作台")
                    .button("§5便捷末影箱")
                    .button("§c自杀")
                    .button("§b随机传送")
                    .button("§b返回上一个位置")
                    .button("§b返回死亡点")
                    .button("§6骑乘")
                    .button("§6坐下")
                    .button("§b实体捕捉器")
                    .button("§7⬅️ 返回主菜单")
                    .validResultHandler((SimpleFormResponse response) -> {
                        switch (response.clickedButtonId()) {
                            case 0 -> p.performCommand("craftingtable");
                            case 1 -> p.performCommand("enderchest");
                            case 2 -> p.performCommand("suicide");
                            case 3 -> p.performCommand("rtp");
                            case 4 -> p.performCommand("back");
                            case 5 -> p.performCommand("dback");
                            case 6 -> p.performCommand("ride");
                            case 7 -> p.performCommand("sit");
                            case 8 -> plugin.carryCommand.giveCatcherItem(p);
                            case 9 -> openMainMenu(p);
                        }
                    })
                    .build();
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            openConvenientGUI(p);
            e.printStackTrace();
        }
    }

    // ==============================
    // 快捷创建物品
    // ==============================
    private ItemStack item(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        java.util.List<String> loreList = new java.util.ArrayList<>();
        loreList.add(lore);
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    // ==============================
    // 菜单点击事件
    // ==============================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        String title = e.getView().getTitle();
        ItemStack cur = e.getCurrentItem();

        if (title.equals(MENU_TITLE) && clicked == top) {
            e.setCancelled(true);
        }

        if (clicked != top || cur == null || !cur.hasItemMeta()) return;

        if (title.equals(MENU_TITLE)) {
            if (cur.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            p.closeInventory();

            switch (cur.getType()) {
                // 第一行
                case EMERALD -> plugin.economicSystem.openShop(p);
                case HOPPER -> plugin.economicSystem.openSell(p);
                case CLOCK -> plugin.gift.openGiftUI(p);
                case ENDER_PEARL -> plugin.getTpAsMe().openMainUI(p);
                case GOLDEN_SWORD -> plugin.openGuildMainUI(p);

                // 第二行
                case JUKEBOX -> plugin.music.openMain(p);
                case NAME_TAG -> p.performCommand("plt open");   // 新增：称号系统
                case PLAYER_HEAD -> plugin.openPlayerInfoUI(p);
                case GOLDEN_APPLE -> p.performCommand("applecoins info");

                // 第三行
                case BOOK -> plugin.handleBook(p);
                case DIAMOND -> plugin.ranking.openRankMenu(p);
                case GOLD_BLOCK -> plugin.ranking.openJavaBankMenu(p);

                // 便捷功能
                case COMPASS -> openConvenientGUI(p);
            }
        }

        // 每日签到菜单
        if (title.equals("§e每日签到")) {
            e.setCancelled(true);

            switch (cur.getType()) {
                case LIME_WOOL -> {
                    p.closeInventory();
                    int nextDay = plugin.gift.getCurrentSignDay(p) + 1;
                    plugin.gift.receiveGift(p, nextDay);
                }
                case BARRIER -> {
                    p.closeInventory();
                    openMainMenu(p);
                }
            }
        }

        // 便捷功能菜单
        if (title.equals("§6便捷功能")) {
            e.setCancelled(true);
            if (clicked != top || cur == null || !cur.hasItemMeta()) return;

            switch (cur.getType()) {
                case CRAFTING_TABLE -> {
                    p.closeInventory();
                    p.performCommand("craftingtable");
                }
                case ENDER_CHEST -> {
                    p.closeInventory();
                    p.performCommand("enderchest");
                }
                case SKELETON_SKULL -> {
                    p.closeInventory();
                    p.performCommand("suicide");
                }
                case GRAY_WOOL -> {
                    String n = cur.getItemMeta().getDisplayName();
                    p.closeInventory();
                    if (n.contains("随机传送")) p.performCommand("rtp");
                    else if (n.contains("返回上一个位置")) p.performCommand("back");
                    else if (n.contains("返回死亡点")) p.performCommand("dback");
                }
                case BARRIER -> {
                    p.closeInventory();
                    openMainMenu(p);
                }

                // 便捷动作
                case SADDLE -> {
                    p.closeInventory();
                    p.performCommand("ride");
                }
                case OAK_STAIRS -> {
                    p.closeInventory();
                    p.performCommand("sit");
                }
                case BLAZE_ROD -> {
                    p.closeInventory();
                    plugin.carryCommand.giveCatcherItem(p);
                }
            }
        }
    }

    // ==============================
    // 指令 /accraft 打开菜单
    // ==============================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c只有玩家可以使用！");
            return true;
        }
        openMainMenu(p);
        return true;
    }
}