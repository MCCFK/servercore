package com.apple.servercore.economicsystem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.regex.Pattern;

public class ShopInventoryListener implements Listener {

    private final EconomicSystem eco;
    private final Pattern quantityPattern = Pattern.compile("§6购买 (\\d+) 个");

    public ShopInventoryListener(EconomicSystem eco) {
        this.eco = eco;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        String t = e.getView().getTitle();
        ItemStack cur = e.getCurrentItem();

        boolean isShopGui = t.startsWith(EconomicSystem.SHOP_INV_PREFIX)
                || t.equals(EconomicSystem.QUANTITY_INV_NAME)
                || t.startsWith(EconomicSystem.SELL_INV_PREFIX)
                || t.startsWith("§c出售数量 - ");

        // 只锁【自己GUI上半部分】
        if (isShopGui && clicked == top) {
            e.setCancelled(true);
        }

        // 玩家背包 → 直接放行
        if (!isShopGui || clicked != top || cur == null || cur.getType() == Material.AIR) return;

        ItemMeta meta = cur.getItemMeta();
        if (meta == null) return;

        if (cur.getType() == Material.STONE && meta.getDisplayName().equals("§7⬅️ 返回服务器主菜单")) {
            p.closeInventory();
            eco.plugin.getACcraft().openMainMenu(p);
            return;
        }

        if (t.startsWith(EconomicSystem.SHOP_INV_PREFIX)) {
            if (meta.getDisplayName().equals("§a上一页")) {
                eco.openShop(p, eco.getShopPageFromInvName(t)-1); return;
            }
            if (meta.getDisplayName().equals("§a下一页")) {
                eco.openShop(p, eco.getShopPageFromInvName(t)+1); return;
            }

            String name = meta.getDisplayName().replace("§6","");
            EconomicSystem.ShopItem item = eco.getShopItem(name);
            if (item == null) return;

            if (e.getClick() == ClickType.LEFT) {
                eco.buyItem(p, item, 1);
            } else if (e.getClick() == ClickType.RIGHT) {
                eco.openQuantitySelector(p, item);
            }
        }

        else if (t.equals(EconomicSystem.QUANTITY_INV_NAME)) {
            if (meta.getDisplayName().equals("§c关闭")) {
                p.closeInventory(); return;
            }
            var m = quantityPattern.matcher(meta.getDisplayName());
            if (m.find()) {
                int q = Integer.parseInt(m.group(1));
                EconomicSystem.ShopItem item = eco.getPlayerSelectedShopItem(p);
                if (item != null) eco.buyItem(p, item, q);
                p.closeInventory();
            }
        }

        else if (t.startsWith("§c出售数量 - ")) {
            String matName = t.replace("§c出售数量 - ","").trim();
            if (meta.getDisplayName().contains("1")) eco.sellItem(p,matName,1);
            if (meta.getDisplayName().contains("8")) eco.sellItem(p,matName,8);
            if (meta.getDisplayName().contains("16")) eco.sellItem(p,matName,16);
            if (meta.getDisplayName().contains("32")) eco.sellItem(p,matName,32);
            if (meta.getDisplayName().contains("64")) eco.sellItem(p,matName,64);
            p.closeInventory();
        }

        else if (t.startsWith(EconomicSystem.SELL_INV_PREFIX)) {
            if (meta.getDisplayName().equals("§a上一页")) {
                eco.openSellGUI(p, eco.getSellPage(t)-1); return;
            }
            if (meta.getDisplayName().equals("§a下一页")) {
                eco.openSellGUI(p, eco.getSellPage(t)+1); return;
            }

            String matName = meta.getDisplayName().replace("§6出售 ","").trim();
            Material material = Material.getMaterial(matName);
            if (material == null) return;

            if (e.getClick() == ClickType.LEFT) {
                if (e.isShiftClick()) eco.sellItem(p,matName,64);
                else eco.sellItem(p,matName,1);
            } else {
                eco.openSellQuantityGUI(p, material);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(EconomicSystem.QUANTITY_INV_NAME)) {
            eco.clearPlayerSelectedShopItem((Player)e.getPlayer());
        }
    }
}