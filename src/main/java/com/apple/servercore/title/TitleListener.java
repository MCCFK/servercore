//package com.apple.servercore.title;
//
//import com.apple.servercore.MainPlugin;
//import org.bukkit.Material;
//import org.bukkit.entity.Player;
//import org.bukkit.event.EventHandler;
//import org.bukkit.event.Listener;
//import org.bukkit.event.inventory.InventoryClickEvent;
//import org.bukkit.inventory.Inventory;
//import org.bukkit.inventory.ItemStack;
//import org.bukkit.inventory.meta.ItemMeta;
//
//public class TitleListener implements Listener {
//
//    private final Title.TitleManager titleManager;
//
//    public TitleListener(Title.TitleManager titleManager) {
//        this.titleManager = titleManager;
//    }
//
//    @EventHandler
//    public void onClick(InventoryClickEvent e) {
//        if (!(e.getWhoClicked() instanceof Player p)) return;
//
//        Inventory top = e.getView().getTopInventory();
//        Inventory clicked = e.getClickedInventory();
//        String title = e.getView().getTitle();
//
//        if (!title.startsWith(Title.GUI_TITLE) || clicked != top) return;
//
//        e.setCancelled(true);
//        ItemStack cur = e.getCurrentItem();
//        if (cur == null || cur.getType() == Material.AIR) return;
//        ItemMeta meta = cur.getItemMeta();
//        if (meta == null) return;
//
//        if (cur.getType() == Material.STONE && meta.getDisplayName().equals("§7返回服务器主菜单")) {
//            p.closeInventory();
//            ((MainPlugin) titleManager.nickManager.plugin).getACcraft().openMainMenu(p);
//            return;
//        }
//
//        if (meta.getDisplayName().equals("§a上一页")) {
//            titleManager.openTitleGUI(p, titleManager.getPage(p) - 1);
//            return;
//        }
//        if (meta.getDisplayName().equals("§a下一页")) {
//            titleManager.openTitleGUI(p, titleManager.getPage(p) + 1);
//            return;
//        }
//
//        String display = meta.getDisplayName();
//        if (titleManager.hasUnlockedTitle(p, display)) {
//            titleManager.equipTitle(p, display);
//        } else {
//            titleManager.buyTitle(p, display);
//        }
//        titleManager.openTitleGUI(p, titleManager.getPage(p));
//    }
//}