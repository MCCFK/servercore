package com.mccfk.plugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class FireworkCommand implements CommandExecutor, Listener {

    // ====================== 常量 ======================
    private static final String TITLE = "§6✦ 烟花编辑器";

    private static final FireworkEffect.Type[] TYPES = {
            FireworkEffect.Type.BALL,
            FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.STAR,
            FireworkEffect.Type.CREEPER,
            FireworkEffect.Type.BURST
    };

    private static final String[] TYPE_NAMES = {
            "§c● 球型", "§6● 大型球型", "§e★ 星型", "§a☠ 苦力怕脸", "§d✦ 爆裂"
    };

    private static final DyeColor[] DYE_COLORS = {
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
            DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
            DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK
    };

    private static final String[] COLOR_NAMES = {
            "白色", "橙色", "品红色", "淡蓝色",
            "黄色", "黄绿色", "粉色", "灰色",
            "淡灰色", "青色", "紫色", "蓝色",
            "棕色", "绿色", "红色", "黑色"
    };

    // ====================== GUI 槽位 ======================
    private static final int SLOT_INFO = 0;
    private static final int SLOT_TYPE = 2;
    private static final int SLOT_TRAIL = 3;
    private static final int SLOT_FLICKER = 4;
    private static final int SLOT_POWER_MINUS = 5;
    private static final int SLOT_POWER_VAL = 6;
    private static final int SLOT_POWER_PLUS = 7;
    private static final int SLOT_SAVE = 8;

    // 颜色选择区：9-44（3排，每排9个）
    private static final int COLOR_START = 9;
    private static final int COLOR_END = 43;
    private static final int COLOR_CLEAR_PRIMARY = 26;  // 清除主色的按钮
    private static final int COLOR_CLEAR_FADE = 44;     // 清除渐变的按钮

    // 控制按钮
    private static final int SLOT_PREV_EFFECT = 45;
    private static final int SLOT_NEXT_EFFECT = 46;
    private static final int SLOT_DELETE_EFFECT = 47;
    private static final int SLOT_ADD_EFFECT = 49;
    private static final int SLOT_PREVIEW = 51;
    private static final int SLOT_CLOSE = 53;

    // ====================== 数据 ======================
    private final JavaPlugin plugin;
    private final Map<UUID, FireworkSession> sessions = new HashMap<>();

    // ====================== 内部类 ======================
    private static class FireworkSession {
        final List<SavedEffect> effects = new ArrayList<>();
        int currentIndex = 0;  // 当前编辑的效果索引（0 <= index < effects.size()）
        int power = 2;

        // 当前正在编辑的属性
        FireworkEffect.Type editingType = FireworkEffect.Type.BALL;
        final List<Color> editingColors = new ArrayList<>();
        final List<Color> editingFadeColors = new ArrayList<>();
        boolean editingTrail = false;
        boolean editingFlicker = false;

        // 刷新 GUI 用，记录当前 GUI 引用
        Inventory openInventory;

        boolean isEditingExisting() {
            return currentIndex >= 0 && currentIndex < effects.size();
        }

        SavedEffect getCurrentSaved() {
            return isEditingExisting() ? effects.get(currentIndex) : null;
        }
    }

    private static class SavedEffect {
        FireworkEffect.Type type;
        final List<Color> colors = new ArrayList<>();
        final List<Color> fadeColors = new ArrayList<>();
        boolean trail;
        boolean flicker;

        SavedEffect(FireworkEffect.Type type, List<Color> colors, List<Color> fadeColors, boolean trail, boolean flicker) {
            this.type = type;
            this.colors.addAll(colors);
            this.fadeColors.addAll(fadeColors);
            this.trail = trail;
            this.flicker = flicker;
        }

        static SavedEffect fromEditing(FireworkSession session) {
            return new SavedEffect(
                    session.editingType,
                    session.editingColors,
                    session.editingFadeColors,
                    session.editingTrail,
                    session.editingFlicker
            );
        }

        void applyToEditing(FireworkSession session) {
            session.editingType = this.type;
            session.editingColors.clear();
            session.editingColors.addAll(this.colors);
            session.editingFadeColors.clear();
            session.editingFadeColors.addAll(this.fadeColors);
            session.editingTrail = this.trail;
            session.editingFlicker = this.flicker;
        }

        FireworkEffect build() {
            FireworkEffect.Builder b = FireworkEffect.builder()
                    .with(type)
                    .withColor(colors)
                    .trail(trail)
                    .flicker(flicker);
            if (!fadeColors.isEmpty()) {
                b.withFade(fadeColors);
            }
            return b.build();
        }
    }

    // ====================== 构造 ======================
    public FireworkCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ====================== 命令处理 ======================
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可用");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage("§c你没有权限");
            return true;
        }
        openEditor(player);
        return true;
    }

    // ====================== 打开编辑器 ======================
    private void openEditor(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.FIREWORK_ROCKET) {
            player.sendMessage("§c请手持烟花（烟花火箭）！");
            return;
        }
        FireworkMeta meta = (FireworkMeta) hand.getItemMeta();
        if (meta == null) return;

        FireworkSession session = new FireworkSession();
        session.power = Math.max(1, meta.getPower());

        // 读取现有效果
        for (FireworkEffect effect : meta.getEffects()) {
            SavedEffect se = new SavedEffect(
                    effect.getType(),
                    effect.getColors(),
                    effect.getFadeColors(),
                    effect.hasTrail(),
                    effect.hasFlicker()
            );
            session.effects.add(se);
        }

        // 如果没有效果，创建一个默认效果
        if (session.effects.isEmpty()) {
            session.effects.add(new SavedEffect(
                    FireworkEffect.Type.BALL,
                    List.of(Color.RED),
                    Collections.emptyList(),
                    false, false
            ));
        }

        // 编辑第一个效果
        session.currentIndex = 0;
        session.effects.get(0).applyToEditing(session);

        sessions.put(player.getUniqueId(), session);
        Inventory gui = buildGUI(session);
        session.openInventory = gui;
        player.openInventory(gui);
    }

    // ====================== 构建 GUI ======================
    private Inventory buildGUI(FireworkSession session) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillInventory(inv, session);
        return inv;
    }

    private void fillInventory(Inventory inv, FireworkSession session) {
        inv.clear();

        // --- 第0行：信息和控制 ---
        inv.setItem(SLOT_INFO, createInfoItem(session));
        inv.setItem(SLOT_TYPE, createTypeItem(session));
        inv.setItem(SLOT_TRAIL, createTrailItem(session));
        inv.setItem(SLOT_FLICKER, createFlickerItem(session));
        inv.setItem(SLOT_POWER_MINUS, createNamedItem(Material.RED_STAINED_GLASS_PANE, "§c减少时间", "§7点击减少飞行时间"));
        inv.setItem(SLOT_POWER_VAL, createPowerItem(session));
        inv.setItem(SLOT_POWER_PLUS, createNamedItem(Material.GREEN_STAINED_GLASS_PANE, "§a增加时间", "§7点击增加飞行时间"));
        inv.setItem(SLOT_SAVE, createNamedItem(Material.LIME_DYE, "§a§l★ 保存到手中烟花", "§7保存所有效果到手中的烟花"));

        // --- 颜色选择区：9-43 ---
        // 主色（9-25）
        for (int i = 0; i < 9; i++) {
            DyeColor dye = DYE_COLORS[i];
            boolean selected = session.editingColors.contains(dye.getColor());
            inv.setItem(9 + i, createColorItem(dye, COLOR_NAMES[i], "主色", selected));
        }
        for (int i = 9; i < 16; i++) {
            DyeColor dye = DYE_COLORS[i];
            boolean selected = session.editingColors.contains(dye.getColor());
            inv.setItem(9 + i, createColorItem(dye, COLOR_NAMES[i], "主色", selected));
        }
        inv.setItem(COLOR_CLEAR_PRIMARY, createNamedItem(Material.BARRIER, "§c清除主色", "§7点击清除所有主色"));

        // 渐变色（27-35）
        for (int i = 0; i < 9; i++) {
            DyeColor dye = DYE_COLORS[i];
            boolean selected = session.editingFadeColors.contains(dye.getColor());
            inv.setItem(27 + i, createColorItem(dye, COLOR_NAMES[i], "渐变色", selected));
        }
        for (int i = 9; i < 16; i++) {
            DyeColor dye = DYE_COLORS[i];
            boolean selected = session.editingFadeColors.contains(dye.getColor());
            inv.setItem(27 + i, createColorItem(dye, COLOR_NAMES[i], "渐变色", selected));
        }
        inv.setItem(COLOR_CLEAR_FADE, createNamedItem(Material.BARRIER, "§c清除渐变色", "§7点击清除所有渐变色"));

        // --- 控制按钮 ---
        inv.setItem(SLOT_PREV_EFFECT, createNamedItem(Material.ARROW, "§b◀ 上一效果", "§7切换到上一个效果"));
        inv.setItem(SLOT_NEXT_EFFECT, createNamedItem(Material.ARROW, "§b下一效果 ▶", "§7切换到下一个效果"));
        inv.setItem(SLOT_DELETE_EFFECT, createNamedItem(Material.TNT, "§c删除效果", "§7删除当前效果"));
        inv.setItem(SLOT_ADD_EFFECT, createNamedItem(Material.EMERALD, "§a添加新效果", "§7保存当前并新建效果"));
        inv.setItem(SLOT_PREVIEW, createPreviewItem(session));
        inv.setItem(SLOT_CLOSE, createNamedItem(Material.IRON_DOOR, "§c关闭", "§7关闭编辑器（不保存）"));

        // 剩余槽位填充玻璃板
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createNamedItem(Material.BLACK_STAINED_GLASS_PANE, " ", ""));
            }
        }
    }

    // ====================== 刷新 GUI（原地更新，不关闭物品栏） ======================
    private void refreshGUI(Player player, FireworkSession session) {
        Inventory inv = session.openInventory;
        if (inv == null || inv.getViewers().isEmpty()) {
            // 物品栏已关闭，重新打开
            openEditor(player);
            return;
        }
        // 直接更新现有物品栏内容，不关闭重开
        fillInventory(inv, session);
    }

    // ====================== 创建物品 ======================
    private ItemStack createNamedItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createColorItem(DyeColor dye, String colorName, String type, boolean selected) {
        Material pane = Material.valueOf(dye.name() + "_STAINED_GLASS_PANE");
        ItemStack item = new ItemStack(pane);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (selected) {
                meta.setDisplayName("§a§l" + colorName + " §7(" + type + ")");
                meta.setLore(List.of(
                        "§a✓ 已添加",
                        "§7左键添加到主色",
                        "§7右键添加到渐变色"
                ));
            } else {
                meta.setDisplayName("§f" + colorName);
                meta.setLore(List.of(
                        "§7左键添加到主色",
                        "§7右键添加到渐变色"
                ));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(FireworkSession session) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6✦ 烟花编辑器");
            List<String> lore = new ArrayList<>();
            lore.add("§7效果: §e" + (session.currentIndex + 1) + "§7/§e" + session.effects.size());
            lore.add("§7飞行时间: §e" + session.power);
            lore.add("");
            lore.add("§7颜色: " + session.editingColors.stream()
                    .map(c -> "§" + getColorCode(c) + "●")
                    .collect(Collectors.joining(" ")));
            if (!session.editingFadeColors.isEmpty()) {
                lore.add("§7渐变色: " + session.editingFadeColors.stream()
                        .map(c -> "§" + getColorCode(c) + "●")
                        .collect(Collectors.joining(" ")));
            }
            lore.add("");
            lore.add("§e左键点击染色玻璃添加颜色");
            lore.add("§e右键点击染色玻璃添加渐变色");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private char getColorCode(Color color) {
        int rgb = color.asRGB();
        return switch (rgb) {
            case 0xF0F0F0 -> 'f';  // WHITE
            case 0xFFA500 -> '6';  // ORANGE
            case 0xFF00FF -> 'd';  // MAGENTA
            case 0x80C0FF -> '9';  // LIGHT_BLUE (approximate)
            case 0xFFFF00 -> 'e';  // YELLOW
            case 0x00FF00 -> 'a';  // LIME
            case 0xFFB6C1 -> 'd';  // PINK (approximate)
            case 0x808080 -> '8';  // GRAY
            case 0xC0C0C0 -> '7';  // LIGHT_GRAY (approximate)
            case 0x008080 -> '3';  // CYAN (approximate)
            case 0x800080 -> '5';  // PURPLE
            case 0x0000FF -> '1';  // BLUE (approximate)
            case 0x8B4513 -> '6';  // BROWN (approximate)
            case 0x008000 -> '2';  // GREEN (approximate)
            case 0xFF0000 -> 'c';  // RED
            case 0x000000 -> '0';  // BLACK
            default -> 'f';
        };
    }

    private ItemStack createTypeItem(FireworkSession session) {
        int typeIndex = getTypeIndex(session.editingType);
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b效果类型: " + TYPE_NAMES[typeIndex]);
            meta.setLore(List.of("§7点击切换到: " + TYPE_NAMES[(typeIndex + 1) % TYPES.length]));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getTypeIndex(FireworkEffect.Type type) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i] == type) return i;
        }
        return 0;
    }

    private ItemStack createTrailItem(FireworkSession session) {
        ItemStack item = new ItemStack(session.editingTrail ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(session.editingTrail ? "§a拖尾: ✔" : "§c拖尾: ✘");
            meta.setLore(List.of("§7点击切换拖尾效果"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFlickerItem(FireworkSession session) {
        ItemStack item = new ItemStack(session.editingFlicker ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(session.editingFlicker ? "§a闪烁: ✔" : "§c闪烁: ✘");
            meta.setLore(List.of("§7点击切换闪烁效果"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPowerItem(FireworkSession session) {
        int p = session.power;
        ItemStack item = new ItemStack(Material.REPEATER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b飞行时间: §e" + p);
            String bar = "§a" + "▌".repeat(Math.min(p, 10)) + "§7" + "▌".repeat(Math.max(0, 10 - Math.min(p, 10)));
            meta.setLore(List.of(bar, "§7时间越长飞得越高"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPreviewItem(FireworkSession session) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        if (meta != null) {
            FireworkEffect.Builder b = FireworkEffect.builder()
                    .with(session.editingType)
                    .withColor(session.editingColors.isEmpty() ? List.of(Color.RED) : session.editingColors)
                    .trail(session.editingTrail)
                    .flicker(session.editingFlicker);
            if (!session.editingFadeColors.isEmpty()) {
                b.withFade(session.editingFadeColors);
            }
            meta.setEffect(b.build());
            meta.setDisplayName("§b预览当前效果");
            List<String> lore = new ArrayList<>();
            lore.add("§7类型: " + TYPE_NAMES[getTypeIndex(session.editingType)]);
            lore.add("§7拖尾: " + (session.editingTrail ? "§a✔" : "§c✘"));
            lore.add("§7闪烁: " + (session.editingFlicker ? "§a✔" : "§c✘"));
            lore.add("§7主色: §f" + session.editingColors.size() + " 种");
            lore.add("§7渐变色: §f" + session.editingFadeColors.size() + " 种");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ====================== 点击处理 ======================
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        FireworkSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ClickType click = event.getClick();

        // --- 第0行控制 ---
        if (slot == SLOT_TYPE) {
            int idx = getTypeIndex(session.editingType);
            session.editingType = TYPES[(idx + 1) % TYPES.length];
            refreshGUI(player, session);
            return;
        }

        if (slot == SLOT_TRAIL) {
            session.editingTrail = !session.editingTrail;
            refreshGUI(player, session);
            return;
        }

        if (slot == SLOT_FLICKER) {
            session.editingFlicker = !session.editingFlicker;
            refreshGUI(player, session);
            return;
        }

        if (slot == SLOT_POWER_MINUS) {
            if (session.power > 1) {
                session.power--;
                refreshGUI(player, session);
            }
            return;
        }

        if (slot == SLOT_POWER_PLUS) {
            if (session.power < 10) {
                session.power++;
                refreshGUI(player, session);
            }
            return;
        }

        if (slot == SLOT_SAVE) {
            saveToHand(player, session);
            return;
        }

        // --- 颜色选择区 ---
        if (slot >= 9 && slot <= 25) {
            int colorIdx = slot - 9;
            if (colorIdx < 16) {
                DyeColor dye = DYE_COLORS[colorIdx];
                Color c = dye.getColor();
                if (c == null) return;
                if (click.isLeftClick()) {
                    if (!session.editingColors.contains(c)) {
                        session.editingColors.add(c);
                    } else {
                        session.editingColors.remove(c);
                    }
                } else if (click.isRightClick()) {
                    if (!session.editingFadeColors.contains(c)) {
                        session.editingFadeColors.add(c);
                    } else {
                        session.editingFadeColors.remove(c);
                    }
                }
                refreshGUI(player, session);
            }
            return;
        }

        if (slot >= 27 && slot <= 43) {
            int colorIdx = slot - 27;
            if (colorIdx < 16) {
                DyeColor dye = DYE_COLORS[colorIdx];
                Color c = dye.getColor();
                if (c == null) return;
                if (click.isLeftClick()) {
                    if (!session.editingFadeColors.contains(c)) {
                        session.editingFadeColors.add(c);
                    } else {
                        session.editingFadeColors.remove(c);
                    }
                } else if (click.isRightClick()) {
                    if (!session.editingColors.contains(c)) {
                        session.editingColors.add(c);
                    } else {
                        session.editingColors.remove(c);
                    }
                }
                refreshGUI(player, session);
            }
            return;
        }

        if (slot == COLOR_CLEAR_PRIMARY) {
            session.editingColors.clear();
            refreshGUI(player, session);
            return;
        }

        if (slot == COLOR_CLEAR_FADE) {
            session.editingFadeColors.clear();
            refreshGUI(player, session);
            return;
        }

        // --- 控制按钮 ---
        if (slot == SLOT_PREV_EFFECT) {
            if (session.effects.size() <= 1) {
                player.sendMessage("§c只有一个效果，无法切换");
                return;
            }
            commitEditing(session);
            int prev = (session.currentIndex - 1 + session.effects.size()) % session.effects.size();
            session.currentIndex = prev;
            session.effects.get(prev).applyToEditing(session);
            refreshGUI(player, session);
            return;
        }

        if (slot == SLOT_NEXT_EFFECT) {
            if (session.effects.size() <= 1) {
                player.sendMessage("§c只有一个效果，无法切换");
                return;
            }
            commitEditing(session);
            int next = (session.currentIndex + 1) % session.effects.size();
            session.currentIndex = next;
            session.effects.get(next).applyToEditing(session);
            refreshGUI(player, session);
            return;
        }

        if (slot == SLOT_DELETE_EFFECT) {
            if (session.effects.size() <= 1) {
                player.sendMessage("§c至少保留一个效果！");
                return;
            }
            session.effects.remove(session.currentIndex);
            if (session.currentIndex >= session.effects.size()) {
                session.currentIndex = session.effects.size() - 1;
            }
            session.effects.get(session.currentIndex).applyToEditing(session);
            refreshGUI(player, session);
            player.sendMessage("§a已删除效果");
            return;
        }

        if (slot == SLOT_ADD_EFFECT) {
            commitEditing(session);
            SavedEffect newEffect = new SavedEffect(
                    FireworkEffect.Type.BALL,
                    List.of(Color.RED),
                    Collections.emptyList(),
                    false, false
            );
            session.effects.add(newEffect);
            session.currentIndex = session.effects.size() - 1;
            newEffect.applyToEditing(session);
            refreshGUI(player, session);
            player.sendMessage("§a已添加新效果");
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            player.sendMessage("§c已取消编辑");
            return;
        }
    }

    // ====================== 处理 SLOT_NEXT_EFFECT ======================
    // 注意: SLOT_NEXT_EFFECT 槽位是 46，需要在刷新时设置
    // 在 buildGUI 中补充第0行

    // ====================== 提交编辑到当前效果 ======================
    private void commitEditing(FireworkSession session) {
        if (session.isEditingExisting()) {
            SavedEffect se = session.effects.get(session.currentIndex);
            se.type = session.editingType;
            se.colors.clear();
            se.colors.addAll(session.editingColors);
            se.fadeColors.clear();
            se.fadeColors.addAll(session.editingFadeColors);
            se.trail = session.editingTrail;
            se.flicker = session.editingFlicker;
        }
    }

    // ====================== 保存到手中 ======================
    private void saveToHand(Player player, FireworkSession session) {
        // 先提交当前编辑
        commitEditing(session);

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.FIREWORK_ROCKET) {
            player.sendMessage("§c手中没有烟花火箭！");
            player.closeInventory();
            return;
        }

        FireworkMeta meta = (FireworkMeta) hand.getItemMeta();
        if (meta == null) return;

        // 清除现有效果
        meta.clearEffects();

        // 添加所有效果
        boolean hasError = false;
        for (SavedEffect se : session.effects) {
            try {
                if (se.colors.isEmpty()) {
                    player.sendMessage("§c效果至少需要一种主色，已跳过空效果");
                    hasError = true;
                    continue;
                }
                FireworkEffect effect = se.build();
                meta.addEffect(effect);
            } catch (Exception e) {
                player.sendMessage("§c效果创建失败: " + e.getMessage());
                hasError = true;
            }
        }

        meta.setPower(session.power);
        hand.setItemMeta(meta);

        // 清理会话
        sessions.remove(player.getUniqueId());
        player.closeInventory();

        if (!hasError) {
            player.sendMessage("§a✓ 烟花已保存！");
        } else {
            player.sendMessage("§e部分效果保存完成（存在跳过的问题）");
        }
    }

    // ====================== 关闭清理 ======================
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        Player player = (Player) event.getPlayer();
        // 不立即清除 - 如果在 GUI 外点击保存按钮后关闭，session 已被清除
        // 如果直接关闭，清除 session
        if (sessions.containsKey(player.getUniqueId())) {
            // 延迟清除，避免立即清除导致异步问题
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sessions.remove(player.getUniqueId());
            }, 2L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }
}
