//package com.apple.servercore.title;
//
//import com.apple.servercore.MainPlugin;
//import org.bukkit.Bukkit;
//import org.bukkit.Material;
//import org.bukkit.Sound;
//import org.bukkit.configuration.ConfigurationSection;
//import org.bukkit.configuration.file.FileConfiguration;
//import org.bukkit.configuration.file.YamlConfiguration;
//import org.bukkit.entity.Player;
//import org.bukkit.inventory.Inventory;
//import org.bukkit.inventory.ItemStack;
//import org.bukkit.inventory.meta.ItemMeta;
//import org.geysermc.cumulus.form.SimpleForm;
//import org.geysermc.cumulus.util.FormImage;
//import org.geysermc.floodgate.api.FloodgateApi;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.*;
//
//public class Title {
//    public static final int GUI_SIZE = 54;
//    public static final int PER_PAGE = 45;
//    public static final String GUI_TITLE = "§6称号系统";
//
//    // 昵称管理器
//    public static class NickManager {
//        final MainPlugin plugin;
//        private final File file;
//        private final Map<UUID, String> nicknames = new HashMap<>();
//
//        public NickManager(MainPlugin plugin) {
//            this.plugin = plugin;
//            this.file = new File(plugin.getDataFolder(), "nick_data.yml");
//            loadData();
//        }
//
//        public void setNickname(Player p, String nick) {
//            UUID uid = p.getUniqueId();
//            nicknames.put(uid, nick);
//            saveData();
//        }
//
//        public String getNickname(Player p) {
//            return nicknames.getOrDefault(p.getUniqueId(), p.getName());
//        }
//
//        public void saveData() {
//            FileConfiguration cfg = new YamlConfiguration();
//            for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
//                cfg.set(entry.getKey().toString() + ".nick", entry.getValue());
//            }
//            try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
//        }
//
//        public void loadData() {
//            if (!file.exists()) return;
//            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
//            for (String uid : cfg.getKeys(false)) {
//                try {
//                    UUID uuid = UUID.fromString(uid);
//                    nicknames.put(uuid, cfg.getString(uid + ".nick"));
//                } catch (Exception ignored) {}
//            }
//        }
//    }
//
//    // 消耗管理器
//    public static class CostManager {
//        private final MainPlugin plugin;
//        private final File file;
//        private Material material = Material.DIAMOND;
//        private int amount = 64;
//
//        public CostManager(MainPlugin plugin) {
//            this.plugin = plugin;
//            this.file = new File(plugin.getDataFolder(), "cost.yml");
//            loadData();
//        }
//
//        public void setCost(Material mat, int amt) {
//            material = mat;
//            amount = Math.max(1, amt);
//            saveData();
//        }
//
//        public boolean consume(Player p) {
//            int remaining = amount;
//            for (ItemStack item : p.getInventory().getContents()) {
//                if (item == null || item.getType() != material) continue;
//                if (item.getAmount() <= remaining) {
//                    remaining -= item.getAmount();
//                    item.setAmount(0);
//                } else {
//                    item.setAmount(item.getAmount() - remaining);
//                    remaining = 0;
//                    break;
//                }
//            }
//            return remaining == 0;
//        }
//
//        public void saveData() {
//            FileConfiguration cfg = new YamlConfiguration();
//            cfg.set("material", material.name());
//            cfg.set("amount", amount);
//            try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
//        }
//
//        public void loadData() {
//            if (!file.exists()) return;
//            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
//            if (cfg.contains("material")) {
//                try { material = Material.valueOf(cfg.getString("material")); } catch (Exception ignored) {}
//            }
//            if (cfg.contains("amount")) amount = cfg.getInt("amount");
//        }
//
//        public Material getCostMaterial() { return material; }
//        public int getCostAmount() { return amount; }
//    }
//
//    // 称号配置
//    public static class TitleConfig {
//        public static class TitleData {
//            private final Material material;
//            private final int amount;
//
//            public TitleData(Material mat, int amt) {
//                material = mat;
//                amount = amt;
//            }
//
//            public Material getMaterial() { return material; }
//            public int getAmount() { return amount; }
//        }
//
//        private final MainPlugin plugin;
//        private final File file;
//        private final Map<String, TitleData> titleMap = new HashMap<>();
//
//        public TitleConfig(MainPlugin plugin) {
//            this.plugin = plugin;
//            this.file = new File(plugin.getDataFolder(), "titles.yml");
//            initConfig();
//            loadTitles();
//        }
//
//        private void initConfig() {
//            if (!file.exists()) {
//                FileConfiguration cfg = new YamlConfiguration();
//                cfg.set("titles.&6[新手].material", "DIAMOND");
//                cfg.set("titles.&6[新手].amount", 10);
//                cfg.set("titles.&c[VIP].material", "GOLD_INGOT");
//                cfg.set("titles.&c[VIP].amount", 32);
//                cfg.set("titles.&5[君临天下].material", "EMERALD");
//                cfg.set("titles.&5[君临天下].amount", 64);
//                try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
//            }
//        }
//
//        public void loadTitles() {
//            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
//            ConfigurationSection section = cfg.getConfigurationSection("titles");
//            if (section != null) {
//                for (String key : section.getKeys(false)) {
//                    try {
//                        Material mat = Material.valueOf(section.getString(key + ".material", "DIAMOND").toUpperCase());
//                        int amt = section.getInt(key + ".amount", 10);
//                        titleMap.put(key, new TitleData(mat, amt));
//                    } catch (Exception ignored) {}
//                }
//            }
//        }
//
//        public Map<String, TitleData> getTitleMap() { return titleMap; }
//        public boolean hasTitle(String name) { return titleMap.containsKey(name); }
//        public TitleData getTitleData(String name) { return titleMap.getOrDefault(name, null); }
//    }
//
//    // 称号管理器
//    public static class TitleManager {
//        private final MainPlugin plugin;
//        public final NickManager nickManager;
//        public final TitleConfig titleConfig;
//        private final File file;
//        private final Map<UUID, Set<String>> unlockedTitles = new HashMap<>();
//        private final Map<UUID, String> equippedTitle = new HashMap<>();
//        private final Map<UUID, Boolean> hideTitle = new HashMap<>();
//        private final Map<UUID, Integer> pageMap = new HashMap<>();
//
//        public TitleManager(MainPlugin plugin, NickManager nickManager) {
//            this.plugin = plugin;
//            this.nickManager = nickManager;
//            this.titleConfig = new TitleConfig(plugin);
//            this.file = new File(plugin.getDataFolder(), "title_data.yml");
//            loadData();
//        }
//
//        // ==============================
//        // 【已补全】三个缺失的方法
//        // ==============================
//        public Set<String> getUnlockedTitles(Player p) {
//            return unlockedTitles.getOrDefault(p.getUniqueId(), new HashSet<>());
//        }
//
//        public void toggleTitleVisibility(Player p) {
//            UUID uid = p.getUniqueId();
//            hideTitle.put(uid, !isTitleHidden(p));
//            saveData();
//            syncPlayerDisplayName(p);
//        }
//
//        public void unequipTitle(Player p) {
//            UUID uid = p.getUniqueId();
//            equippedTitle.remove(uid);
//            saveData();
//            syncPlayerDisplayName(p);
//            p.sendMessage("§a已卸下当前称号");
//        }
//
//        // 自动判断打开界面
//        public void openTitleMenu(Player p) {
//            if (isBedrockPlayer(p)) {
//                openBedrockTitleForm(p);
//            } else {
//                openTitleGUI(p, 1);
//            }
//        }
//
//        // Java背包GUI
//        public void openTitleGUI(Player p, int page) {
//            Map<String, TitleConfig.TitleData> allTitles = titleConfig.getTitleMap();
//            List<Map.Entry<String, TitleConfig.TitleData>> list = new ArrayList<>(allTitles.entrySet());
//
//            int totalPage = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
//            if (page < 1) page = 1;
//            if (page > totalPage) page = totalPage;
//            pageMap.put(p.getUniqueId(), page);
//
//            Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE + " §7(" + page + "/" + totalPage + ")");
//            int start = (page - 1) * PER_PAGE;
//
//            for (int i = 0; i < PER_PAGE; i++) {
//                int idx = start + i;
//                if (idx >= list.size()) break;
//
//                var entry = list.get(idx);
//                String display = entry.getKey();
//                TitleConfig.TitleData data = entry.getValue();
//                boolean unlocked = hasUnlockedTitle(p, display);
//                boolean equipped = display.equals(getEquippedTitle(p));
//
//                ItemStack item = new ItemStack(unlocked ? Material.NAME_TAG : Material.GRAY_DYE);
//                ItemMeta meta = item.getItemMeta();
//                meta.setDisplayName(display.replace('&', '§'));
//
//                List<String> lore = new ArrayList<>();
//                if (unlocked) {
//                    lore.add(equipped ? "§a已装备" : "§e点击装备");
//                } else {
//                    lore.add("§c未解锁");
//                    lore.add("§7消耗: " + data.getAmount() + "个 " + data.getMaterial().name());
//                    lore.add("§e点击购买解锁");
//                }
//                meta.setLore(lore);
//                item.setItemMeta(meta);
//                inv.setItem(i, item);
//            }
//
//            if (page > 1) {
//                ItemStack prev = new ItemStack(Material.ARROW);
//                ItemMeta m = prev.getItemMeta();
//                m.setDisplayName("§a上一页");
//                prev.setItemMeta(m);
//                inv.setItem(45, prev);
//            }
//            if (page < totalPage) {
//                ItemStack next = new ItemStack(Material.ARROW);
//                ItemMeta m = next.getItemMeta();
//                m.setDisplayName("§a下一页");
//                next.setItemMeta(m);
//                inv.setItem(53, next);
//            }
//
//            ItemStack back = new ItemStack(Material.STONE);
//            ItemMeta backMeta = back.getItemMeta();
//            backMeta.setDisplayName("§7返回服务器主菜单");
//            back.setItemMeta(backMeta);
//            inv.setItem(49, back);
//
//            p.openInventory(inv);
//        }
//
//        // 基岩表单（无任何emoji）
//        public void openBedrockTitleForm(Player p) {
//            SimpleForm.Builder builder = SimpleForm.builder()
//                    .title("§6称号系统")
//                    .content("§7点击称号即可购买或装备");
//
//            List<Map.Entry<String, TitleConfig.TitleData>> all = new ArrayList<>(titleConfig.getTitleMap().entrySet());
//
//            for (Map.Entry<String, TitleConfig.TitleData> entry : all) {
//                String name = entry.getKey().replace('&', '§');
//                boolean unlock = hasUnlockedTitle(p, entry.getKey());
//                boolean equip = entry.getKey().equals(getEquippedTitle(p));
//
//                String btnText;
//                if (equip) {
//                    btnText = name + "\n§a当前已装备";
//                } else if (unlock) {
//                    btnText = name + "\n§e点击进行装备";
//                } else {
//                    btnText = name + "\n§c未解锁，点击购买";
//                }
//                builder.button(btnText, FormImage.Type.PATH, "textures/ui/name_tag.png");
//            }
//
//            builder.button("§c返回主菜单", FormImage.Type.PATH, "textures/ui/arrow_left.png");
//
//            builder.validResultHandler((response) -> {
//                int idx = response.clickedButtonId();
//                if (idx == all.size()) {
//                    p.closeInventory();
//                    plugin.getACcraft().openMainMenu(p);
//                    return;
//                }
//
//                if (idx >= 0 && idx < all.size()) {
//                    String titleName = all.get(idx).getKey();
//                    if (hasUnlockedTitle(p, titleName)) {
//                        equipTitle(p, titleName);
//                        p.sendMessage("§a已装备称号: " + titleName.replace('&', '§'));
//                    } else {
//                        buyTitle(p, titleName);
//                    }
//                    openBedrockTitleForm(p);
//                }
//            });
//
//            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
//        }
//
//        public boolean isBedrockPlayer(Player p) {
//            try {
//                return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
//            } catch (Exception e) {
//                return false;
//            }
//        }
//
//        public int getPage(Player p) {
//            return pageMap.getOrDefault(p.getUniqueId(), 1);
//        }
//
//        // 购买逻辑
//        public boolean buyTitle(Player p, String titleName) {
//            TitleConfig.TitleData data = titleConfig.getTitleData(titleName);
//            if (data == null) {
//                p.sendMessage("§c错误：该称号不存在！");
//                return false;
//            }
//
//            Material needMat = data.getMaterial();
//            int needAmt = data.getAmount();
//
//            if (!hasEnoughItem(p, needMat, needAmt)) {
//                p.sendMessage("§c你没有足够的物品：需要 " + needAmt + " 个 " + needMat.name());
//                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
//                return false;
//            }
//
//            takeItem(p, needMat, needAmt);
//            unlockTitle(p, titleName);
//            p.sendMessage("§a成功解锁称号：" + titleName.replace('&', '§'));
//            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
//            return true;
//        }
//
//        private boolean hasEnoughItem(Player p, Material mat, int amount) {
//            int total = 0;
//            for (ItemStack item : p.getInventory().getContents()) {
//                if (item != null && item.getType() == mat) {
//                    total += item.getAmount();
//                }
//            }
//            return total >= amount;
//        }
//
//        private void takeItem(Player p, Material mat, int amount) {
//            int left = amount;
//            for (ItemStack item : p.getInventory().getContents()) {
//                if (item == null || item.getType() != mat) continue;
//                if (item.getAmount() <= left) {
//                    left -= item.getAmount();
//                    item.setAmount(0);
//                } else {
//                    item.setAmount(item.getAmount() - left);
//                    left = 0;
//                    break;
//                }
//            }
//        }
//
//        public void unlockTitle(Player p, String title) {
//            unlockedTitles.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(title);
//            saveData();
//            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
//        }
//
//        public void equipTitle(Player p, String title) {
//            UUID uid = p.getUniqueId();
//            if (hasUnlockedTitle(p, title)) {
//                equippedTitle.put(uid, title);
//                saveData();
//                syncPlayerDisplayName(p);
//                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 2.0f);
//            }
//        }
//
//        public void syncPlayerDisplayName(Player p) {
//            UUID uid = p.getUniqueId();
//            String nick = nickManager.getNickname(p);
//            String title = equippedTitle.get(uid);
//            boolean hide = isTitleHidden(p);
//
//            String display = nick;
//            if (!hide && title != null) display = title.replace('&', '§') + "§r " + nick;
//
//            p.setDisplayName(display);
//            p.setPlayerListName(display);
//            p.setCustomName(display);
//            p.setCustomNameVisible(true);
//        }
//
//        public boolean hasUnlockedTitle(Player p, String title) {
//            return unlockedTitles.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(title);
//        }
//
//        public String getEquippedTitle(Player p) {
//            return equippedTitle.get(p.getUniqueId());
//        }
//
//        public boolean isTitleHidden(Player p) {
//            return hideTitle.getOrDefault(p.getUniqueId(), false);
//        }
//
//        public void saveData() {
//            FileConfiguration cfg = new YamlConfiguration();
//            for (UUID uid : unlockedTitles.keySet()) {
//                cfg.set(uid.toString() + ".unlocked", new ArrayList<>(unlockedTitles.get(uid)));
//                cfg.set(uid.toString() + ".equipped", equippedTitle.get(uid));
//            }
//            try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
//        }
//
//        public void loadData() {
//            if (!file.exists()) return;
//            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
//            for (String uid : cfg.getKeys(false)) {
//                try {
//                    UUID uuid = UUID.fromString(uid);
//                    Set<String> titles = new HashSet<>(cfg.getStringList(uid + ".unlocked"));
//                    unlockedTitles.put(uuid, titles);
//                    equippedTitle.put(uuid, cfg.getString(uid + ".equipped"));
//                } catch (Exception ignored) {}
//            }
//        }
//    }
//
//    // 配置管理器
//    public static class ConfigManager {
//        private final List<String> forbiddenNames;
//
//        public ConfigManager(MainPlugin plugin) {
//            plugin.saveDefaultConfig();
//            forbiddenNames = plugin.getConfig().getStringList("forbidden-names");
//        }
//
//        public boolean isForbidden(String name) {
//            String lowerName = name.toLowerCase();
//            for (String f : forbiddenNames) {
//                if (lowerName.contains(f.toLowerCase())) return true;
//            }
//            return false;
//        }
//    }
//}