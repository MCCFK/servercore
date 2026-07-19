package com.apple.servercore.economicsystem;

import com.apple.servercore.MainPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EconomicSystem {

    public static final String SHOP_INV_PREFIX = "§6AC商店 ";
    public static final String QUANTITY_INV_NAME = "§6购买数量";
    public static final String SELL_INV_PREFIX = "§c出售物品 ";

    // ========== 本地存储（作为 Vault 不可用时的降级备份） ==========
    private final Map<UUID, Integer> localBackup = new HashMap<>();

    protected Map<String, Integer> sellPrices = new HashMap<>();
    protected List<ShopItem> shopItems = new ArrayList<>();
    protected Map<UUID, ShopItem> playerSelectedShopItem = new HashMap<>();

    private Economy vaultEconomy;
    private boolean vaultAvailable = false;
    final MainPlugin plugin;

    private File acCoinDataFile;
    private FileConfiguration acCoinDataConfig;
    private File shopConfigFile;
    private FileConfiguration shopConfig;
    private File sellConfigFile;
    private FileConfiguration sellConfig;

    // ========== 苹果币和飞行系统 ==========
    private Apple_Coins appleCoins;
    private ACFly acFly;

    public static class ShopItem {
        private final String name;
        private final Material material;
        private final int amount;
        private final int price;
        private final String lore;
        private final Map<Enchantment, Integer> enchantments;

        public ShopItem(String name, Material material, int amount, int price, String lore, Map<Enchantment, Integer> enchantments) {
            this.name = name;
            this.material = material;
            this.amount = amount;
            this.price = price;
            this.lore = lore;
            this.enchantments = enchantments;
        }

        public String getName() { return name; }
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public int getPrice() { return price; }
        public String getLore() { return lore; }
        public Map<Enchantment, Integer> getEnchantments() { return enchantments; }
    }

    public EconomicSystem(MainPlugin plugin) {
        this.plugin = plugin;
        setupVault();
        Bukkit.getPluginManager().registerEvents(new ShopInventoryListener(this), plugin);
        initConfigFiles();
        loadLocalBackup();   // 加载本地备份数据
        loadSellPrices();
        loadShopItems();
        registerJoinSyncListener();

        // ========== 初始化苹果币和飞行系统 ==========
        this.appleCoins = new Apple_Coins(plugin);
        this.acFly = new ACFly(plugin);

        // 如果 Vault 可用，从本地备份同步所有在线玩家的数据
        if (vaultAvailable) {
            syncAllOnlinePlayers();
            plugin.getLogger().info("§a[AC经济] Vault 已连接，使用 Vault 作为主存储");
        } else {
            plugin.getLogger().warning("§e[AC经济] Vault 不可用，使用本地备份作为存储");
        }
    }

    // ========== 获取苹果币和飞行系统 ==========
    public Apple_Coins getAppleCoins() {
        return appleCoins;
    }

    public ACFly getAcFly() {
        return acFly;
    }

    // ========== 上线同步监听 ==========
    private void registerJoinSyncListener() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(PlayerJoinEvent event) {
                syncPlayerOnJoin(event.getPlayer());
            }
        }, plugin);
    }

    /**
     * 玩家加入时的同步策略：
     * 1. Vault 可用：将本地备份导入 Vault（仅当 Vault 余额为0且本地有数据时）
     * 2. Vault 不可用：使用本地备份数据
     */
    private void syncPlayerOnJoin(Player player) {
        UUID uuid = player.getUniqueId();

        if (vaultAvailable) {
            // ===== Vault 可用模式 =====
            double vaultBalance = vaultEconomy.getBalance(player);

            // 如果本地有备份数据且 Vault 余额为0，则从本地导入
            if (localBackup.containsKey(uuid) && vaultBalance <= 0) {
                int localBalance = localBackup.get(uuid);
                if (localBalance > 0) {
                    vaultEconomy.depositPlayer(player, localBalance);
                    plugin.getLogger().info("§a[AC经济] 玩家 " + player.getName() + " 的 " + localBalance + " AC币 已从本地备份导入 Vault");
                }
                // 导入后清除本地备份，避免重复导入
                localBackup.remove(uuid);
                saveLocalBackup();
            }

            // 如果 Vault 有数据但本地没有备份，同步到本地（作为备份）
            if (vaultBalance > 0 && !localBackup.containsKey(uuid)) {
                localBackup.put(uuid, (int) vaultBalance);
                saveLocalBackup();
            }
        } else {
            // ===== Vault 不可用：使用本地备份 =====
            // 确保玩家有本地备份数据
            if (!localBackup.containsKey(uuid)) {
                localBackup.put(uuid, 0);
                saveLocalBackup();
            }
        }
    }

    /**
     * 同步所有在线玩家数据
     */
    private void syncAllOnlinePlayers() {
        if (!vaultAvailable) {
            plugin.getLogger().info("§e[AC经济] Vault 不可用，跳过同步");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncPlayerOnJoin(p);
        }
        plugin.getLogger().info("§a[AC经济] 已同步所有在线玩家数据到 Vault");
    }

    // ======================================
    // Vault 经济初始化
    // ======================================
    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("§c未找到 Vault 插件！将使用本地存储模式！");
            vaultAvailable = false;
            return;
        }
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            vaultEconomy = registration.getProvider();
            vaultAvailable = true;
            plugin.getLogger().info("§aVault 经济系统已连接！");
        } else {
            plugin.getLogger().severe("§cVault Economy 服务未注册！将使用本地存储模式！");
            vaultAvailable = false;
        }
    }

    public boolean isVaultEnabled() {
        return vaultAvailable && vaultEconomy != null;
    }

    /**
     * 获取当前使用的存储模式
     */
    public String getStorageMode() {
        return vaultAvailable ? "Vault" : "本地备份";
    }

    // ======================================
    // 双端自动判断
    // ======================================
    public boolean isBedrock(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    public void openShop(Player p) {
        if (isBedrock(p)) {
            openBedrockShop(p);
        } else {
            openShop(p, 1);
        }
    }

    public void openSell(Player p) {
        if (isBedrock(p)) {
            openBedrockSell(p);
        } else {
            openSellGUI(p, 1);
        }
    }

    // ======================================
    // 配置文件初始化
    // ======================================
    private void initConfigFiles() {
        acCoinDataFile = new File(plugin.getDataFolder(), "ac_coins.yml");
        acCoinDataConfig = YamlConfiguration.loadConfiguration(acCoinDataFile);

        sellConfigFile = new File(plugin.getDataFolder(), "sell_prices.yml");
        sellConfig = YamlConfiguration.loadConfiguration(sellConfigFile);
        if (!sellConfigFile.exists()) {
            sellConfig.set("DIAMOND", 100);
            sellConfig.set("EMERALD", 50);
            sellConfig.set("GOLD_INGOT", 10);
            sellConfig.set("IRON_INGOT", 5);
            sellConfig.set("COAL", 1);
            try { sellConfig.save(sellConfigFile); } catch (IOException e) { e.printStackTrace(); }
        }

        shopConfigFile = new File(plugin.getDataFolder(), "shop_items.yml");
        shopConfig = YamlConfiguration.loadConfiguration(shopConfigFile);
        if (!shopConfigFile.exists()) {
            shopConfig.set("1.name", "锋利钻石剑");
            shopConfig.set("1.material", "DIAMOND_SWORD");
            shopConfig.set("1.amount", 1);
            shopConfig.set("1.price", 800);
            shopConfig.set("1.lore", "锋利V 耐久III");
            shopConfig.set("1.enchantments.minecraft:sharpness", 5);
            shopConfig.set("1.enchantments.minecraft:unbreaking", 3);

            shopConfig.set("2.name", "效率钻石镐");
            shopConfig.set("2.material", "DIAMOND_PICKAXE");
            shopConfig.set("2.amount", 1);
            shopConfig.set("2.price", 700);
            shopConfig.set("2.lore", "效率V 耐久III");
            shopConfig.set("2.enchantments.minecraft:efficiency", 5);
            shopConfig.set("2.enchantments.minecraft:unbreaking", 3);

            shopConfig.set("3.name", "金苹果");
            shopConfig.set("3.material", "GOLDEN_APPLE");
            shopConfig.set("3.amount", 1);
            shopConfig.set("3.price", 50);
            shopConfig.set("3.lore", "恢复生命值");

            shopConfig.set("4.name", "附魔金苹果");
            shopConfig.set("4.material", "ENCHANTED_GOLDEN_APPLE");
            shopConfig.set("4.amount", 1);
            shopConfig.set("4.price", 500);
            shopConfig.set("4.lore", "超强恢复效果");

            shopConfig.set("5.name", "钻石胸甲");
            shopConfig.set("5.material", "DIAMOND_CHESTPLATE");
            shopConfig.set("5.amount", 1);
            shopConfig.set("5.price", 600);
            shopConfig.set("5.lore", "保护IV 耐久III");
            shopConfig.set("5.enchantments.minecraft:protection", 4);
            shopConfig.set("5.enchantments.minecraft:unbreaking", 3);

            try { shopConfig.save(shopConfigFile); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // ======================================
    // 本地备份数据加载/保存（Vault 不可用时降级使用）
    // ======================================
    public void loadLocalBackup() {
        localBackup.clear();
        if (!acCoinDataFile.exists()) {
            plugin.getLogger().info("§7[AC经济] 本地备份文件不存在，将创建新文件");
            return;
        }

        for (String uuidStr : acCoinDataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                int coins = acCoinDataConfig.getInt(uuidStr, 0);
                localBackup.put(uuid, coins);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效UUID: " + uuidStr);
            }
        }
        plugin.getLogger().info("§7[AC经济] 加载了 " + localBackup.size() + " 个玩家的本地备份数据");
    }

    public void saveLocalBackup() {
        acCoinDataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : localBackup.entrySet()) {
            acCoinDataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            acCoinDataConfig.save(acCoinDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("§c保存本地备份失败: " + e.getMessage());
        }
    }

    public void loadSellPrices() {
        sellPrices.clear();
        for (String key : sellConfig.getKeys(false)) {
            sellPrices.put(key.toUpperCase(), sellConfig.getInt(key, 0));
        }
    }

    public void loadShopItems() {
        shopItems.clear();
        for (String key : shopConfig.getKeys(false)) {
            if (key.matches("\\d+")) {
                String path = key + ".";
                String name = shopConfig.getString(path + "name", "?");
                Material material = Material.getMaterial(shopConfig.getString(path + "material", "STONE"));
                int amount = shopConfig.getInt(path + "amount", 1);
                int price = shopConfig.getInt(path + "price", 0);
                String lore = shopConfig.getString(path + "lore", "");

                Map<Enchantment, Integer> enchants = new HashMap<>();
                if (shopConfig.contains(path + "enchantments")) {
                    ConfigurationSection sec = shopConfig.getConfigurationSection(path + "enchantments");
                    if (sec != null) {
                        for (String keyEnchant : sec.getKeys(false)) {
                            NamespacedKey nk = NamespacedKey.fromString(keyEnchant);
                            if (nk == null) continue;
                            Enchantment e = Enchantment.getByKey(nk);
                            if (e == null) continue;
                            int lvl = sec.getInt(keyEnchant, 1);
                            if (lvl < 1) lvl = 1;
                            if (lvl > e.getMaxLevel()) lvl = e.getMaxLevel();
                            enchants.put(e, lvl);
                        }
                    }
                }

                if (material != null) {
                    shopItems.add(new ShopItem(name, material, amount, price, lore, enchants));
                }
            }
        }
    }

    // ======================================
    // 基岩版商店
    // ======================================
    public void openBedrockShop(Player p) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§6AC商店")
                .content("§a余额: " + getAcCoins(p) + " AC币\n§7存储模式: " + getStorageMode());

        form.button("§6搜索商品");
        form.button("§7返回服务器主菜单");

        for (ShopItem si : shopItems) {
            form.button("§a" + si.getName() + "\n§e" + si.getPrice() + " AC币");
        }

        form.validResultHandler((response) -> {
            int idx = response.clickedButtonId();
            if (idx == 0) {
                openBedrockShopSearch(p);
                return;
            }
            if (idx == 1) {
                plugin.getACcraft().openMainMenu(p);
                return;
            }
            int itemIndex = idx - 2;
            if (itemIndex < 0 || itemIndex >= shopItems.size()) return;
            ShopItem si = shopItems.get(itemIndex);
            openBedrockBuyQuantity(p, si);
        });

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    public void openBedrockShopSearch(Player p) {
        org.geysermc.cumulus.form.CustomForm form = org.geysermc.cumulus.form.CustomForm.builder()
                .title("§6搜索商品")
                .input("输入商品名称")
                .validResultHandler((response) -> {
                    String keyword = response.getInput(0).trim().toLowerCase();
                    openBedrockShopSearchResult(p, keyword);
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    public void openBedrockShopSearchResult(Player p, String keyword) {
        List<ShopItem> results = new ArrayList<>();
        for (ShopItem item : shopItems) {
            if (item.getName().toLowerCase().contains(keyword)) {
                results.add(item);
            }
        }

        SimpleForm.Builder builder = SimpleForm.builder().title("§6搜索结果");
        if (results.isEmpty()) {
            builder.content("§c没有找到商品");
        } else {
            builder.content("§a找到 " + results.size() + " 个商品");
            for (ShopItem item : results) {
                builder.button(item.getName() + "\n§e" + item.getPrice() + " AC币");
            }
        }
        builder.button("§7返回商店");

        builder.validResultHandler((response) -> {
            int id = response.clickedButtonId();
            if (id == results.size()) {
                openBedrockShop(p);
                return;
            }
            if (id >= 0 && id < results.size()) {
                openBedrockBuyQuantity(p, results.get(id));
            }
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
    }

    public void openBedrockBuyQuantity(Player p, ShopItem si) {
        SimpleForm form = SimpleForm.builder()
                .title("§6购买: " + si.getName())
                .content("§7单价: " + si.getPrice() + "\n§a余额: " + getAcCoins(p))
                .button("§7返回商店")
                .button("§a购买 1 个")
                .button("§a购买 8 个")
                .button("§a购买 16 个")
                .button("§a购买 32 个")
                .button("§a购买 64 个")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) {
                        openBedrockShop(p);
                        return;
                    }
                    int[] qList = {1,8,16,32,64};
                    if (id >=1 && id <=5) {
                        buyItem(p, si, qList[id-1]);
                    }
                }).build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    public void openBedrockSell(Player p) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§c出售物品")
                .content("§a余额: " + getAcCoins(p) + " AC币");

        form.button("§7返回服务器主菜单");
        for (String key : sellPrices.keySet()) {
            Material mat = Material.getMaterial(key);
            if (mat == null) continue;
            int price = sellPrices.get(key);
            form.button("§a" + mat.name() + "\n§e" + price + " AC币/个");
        }

        form.validResultHandler((response) -> {
            int idx = response.clickedButtonId();
            if (idx == 0) {
                plugin.getACcraft().openMainMenu(p);
                return;
            }
            List<String> list = new ArrayList<>(sellPrices.keySet());
            int itemIndex = idx -1;
            if (itemIndex <0 || itemIndex >= list.size()) return;
            openBedrockSellQuantity(p, list.get(itemIndex));
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    public void openBedrockSellQuantity(Player p, String matName) {
        SimpleForm form = SimpleForm.builder()
                .title("§c出售: " + matName)
                .content("§7单价: " + sellPrices.getOrDefault(matName,0))
                .button("§7返回出售")
                .button("§c出售 1 个")
                .button("§c出售 8 个")
                .button("§c出售 16 个")
                .button("§c出售 32 个")
                .button("§c出售 64 个")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    if (id ==0) {
                        openBedrockSell(p);
                        return;
                    }
                    int[] qList = {1,8,16,32,64};
                    if (id >=1 && id <=5) {
                        sellItem(p, matName, qList[id-1]);
                    }
                }).build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ======================================
    // Java商店GUI
    // ======================================
    public void openShop(Player player, int page) {
        int perPage = 45;
        int totalPage = (int) Math.ceil((double) shopItems.size() / perPage);
        if (page <1) page=1;
        if (page>totalPage) page=totalPage;

        Inventory inv = Bukkit.createInventory(null,54, SHOP_INV_PREFIX + page+"/"+totalPage);
        int start = (page-1)*perPage;
        int end = Math.min(start+perPage, shopItems.size());

        for (int i=start;i<end;i++) {
            ShopItem si = shopItems.get(i);
            ItemStack is = new ItemStack(si.getMaterial(), si.getAmount());
            ItemMeta meta = is.getItemMeta();
            if (meta==null) continue;

            meta.setDisplayName("§6"+si.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7单价: "+si.getPrice()+" AC币");
            lore.add("§7"+si.getLore());
            if (!si.getEnchantments().isEmpty()) {
                lore.add("§b附魔:");
                si.getEnchantments().forEach((e,l)->lore.add("§7- "+e.getKey().getKey()+" "+l));
            }
            lore.add("");
            lore.add("§a左键 = 买1个");
            lore.add("§e右键 = 选择数量");
            meta.setLore(lore);
            for (Map.Entry<Enchantment,Integer> en : si.getEnchantments().entrySet()) {
                meta.addEnchant(en.getKey(),en.getValue(),true);
            }
            is.setItemMeta(meta);
            inv.setItem(i-start,is);
        }

        for (int i=end-start;i<45;i++) {
            ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = f.getItemMeta();
            m.setDisplayName("§7");
            f.setItemMeta(m);
            inv.setItem(i,f);
        }

        if (page>1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            m.setDisplayName("§a上一页");
            prev.setItemMeta(m);
            inv.setItem(45,prev);
        }
        if (page<totalPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            m.setDisplayName("§a下一页");
            next.setItemMeta(m);
            inv.setItem(53,next);
        }

        ItemStack back = new ItemStack(Material.STONE);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7⬅️ 返回服务器主菜单");
        back.setItemMeta(bm);
        inv.setItem(49,back);

        player.openInventory(inv);
    }

    public void openQuantitySelector(Player player, ShopItem shopItem) {
        playerSelectedShopItem.put(player.getUniqueId(), shopItem);
        Inventory inv = Bukkit.createInventory(null,9, QUANTITY_INV_NAME);
        int[] amounts = {1,8,16,32,64};
        for (int i=0;i<amounts.length;i++) {
            int q = amounts[i];
            ItemStack is = new ItemStack(Material.GOLD_NUGGET);
            ItemMeta meta = is.getItemMeta();
            meta.setDisplayName("§6购买 "+q+" 个");
            List<String> lore = new ArrayList<>();
            lore.add("§7总价: "+q*shopItem.getPrice()+" AC币");
            is.setItemMeta(meta);
            inv.setItem(i,is);
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta c = close.getItemMeta();
        c.setDisplayName("§c关闭");
        close.setItemMeta(c);
        inv.setItem(8,close);
        player.openInventory(inv);
    }

    public void openSellGUI(Player player, int page) {
        List<Map.Entry<String,Integer>> list = new ArrayList<>(sellPrices.entrySet());
        int perPage=45;
        int totalPage=Math.max(1,(list.size()+perPage-1)/perPage);
        if (page<1) page=1;
        if (page>totalPage) page=totalPage;

        Inventory inv = Bukkit.createInventory(null,54, SELL_INV_PREFIX + page+"/"+totalPage);
        int start=(page-1)*perPage;
        for (int i=0;i<perPage;i++) {
            int idx=start+i;
            if (idx>=list.size()) break;
            var e=list.get(idx);
            Material mat=Material.getMaterial(e.getKey());
            int price=e.getValue();
            if (mat==null||price<=0) continue;

            ItemStack item=new ItemStack(mat);
            ItemMeta meta=item.getItemMeta();
            if (meta==null) continue;
            meta.setDisplayName("§6出售 "+mat.name());
            List<String> lore=new ArrayList<>();
            lore.add("§7单价: "+price+" AC币/个");
            lore.add("§a左键 = 出售1个");
            lore.add("§eShift+左键 = 出售一组");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i,item);
        }

        if (page>1) {
            ItemStack prev=new ItemStack(Material.ARROW);
            ItemMeta m=prev.getItemMeta();
            m.setDisplayName("§a上一页");
            prev.setItemMeta(m);
            inv.setItem(45,prev);
        }
        if (page<totalPage) {
            ItemStack next=new ItemStack(Material.ARROW);
            ItemMeta m=next.getItemMeta();
            m.setDisplayName("§a下一页");
            next.setItemMeta(m);
            inv.setItem(53,next);
        }

        ItemStack back=new ItemStack(Material.STONE);
        ItemMeta bm=back.getItemMeta();
        bm.setDisplayName("§7⬅️ 返回服务器主菜单");
        back.setItemMeta(bm);
        inv.setItem(49,back);

        player.openInventory(inv);
    }

    public void openSellQuantityGUI(Player player, Material material) {
        Inventory inv=Bukkit.createInventory(null,9,"§c出售数量 - "+material.name());
        int[] amounts={1,8,16,32,64};
        for (int i=0;i<amounts.length;i++) {
            ItemStack item=new ItemStack(Material.GOLD_NUGGET);
            ItemMeta meta=item.getItemMeta();
            meta.setDisplayName("§e出售 "+amounts[i]+" 个");
            item.setItemMeta(meta);
            inv.setItem(i,item);
        }
        ItemStack close=new ItemStack(Material.BARRIER);
        ItemMeta c=close.getItemMeta();
        c.setDisplayName("§c关闭");
        close.setItemMeta(c);
        inv.setItem(8,close);
        player.openInventory(inv);
    }

    public int getSellPage(String title) {
        if (!title.startsWith(SELL_INV_PREFIX)) return 1;
        try {
            return Integer.parseInt(title.replace(SELL_INV_PREFIX,"").split("/")[0]);
        } catch (Exception e) {
            return 1;
        }
    }

    // ======================================
    // 核心：AC币 操作（自动选择存储方式）
    // ======================================

    /**
     * 获取玩家AC币余额
     * - Vault 可用：从 Vault 获取
     * - Vault 不可用：从本地备份获取
     */
    public int getAcCoins(Player player) {
        UUID uuid = player.getUniqueId();

        if (vaultAvailable) {
            // Vault 可用：从 Vault 获取
            return (int) vaultEconomy.getBalance(player);
        } else {
            // Vault 不可用：从本地备份获取
            return localBackup.getOrDefault(uuid, 0);
        }
    }

    /**
     * 增加玩家AC币
     * - Vault 可用：存入 Vault，同时更新本地备份
     * - Vault 不可用：存入本地备份
     */
    public void addAcCoins(Player player, int amount) {
        if (amount < 0) return;
        UUID uuid = player.getUniqueId();

        if (vaultAvailable) {
            // Vault 可用：存入 Vault
            vaultEconomy.depositPlayer(player, amount);
            // 同步更新本地备份（保持一致性）
            int newBalance = (int) vaultEconomy.getBalance(player);
            localBackup.put(uuid, newBalance);
        } else {
            // Vault 不可用：存入本地备份
            int current = localBackup.getOrDefault(uuid, 0);
            localBackup.put(uuid, current + amount);
        }
        saveLocalBackup();
    }

    /**
     * 扣除玩家AC币
     * - Vault 可用：从 Vault 扣除，同时更新本地备份
     * - Vault 不可用：从本地备份扣除
     * @return 是否扣除成功
     */
    public boolean removeAcCoins(Player player, int amount) {
        if (amount < 0) return false;
        UUID uuid = player.getUniqueId();

        if (vaultAvailable) {
            // Vault 可用：从 Vault 扣除
            double current = vaultEconomy.getBalance(player);
            if (current < amount) return false;
            vaultEconomy.withdrawPlayer(player, amount);
            // 同步更新本地备份（保持一致性）
            int newBalance = (int) vaultEconomy.getBalance(player);
            localBackup.put(uuid, newBalance);
        } else {
            // Vault 不可用：从本地备份扣除
            int current = localBackup.getOrDefault(uuid, 0);
            if (current < amount) return false;
            localBackup.put(uuid, current - amount);
        }
        saveLocalBackup();
        return true;
    }

    /**
     * 设置玩家AC币
     * - Vault 可用：设置 Vault 余额，同时更新本地备份
     * - Vault 不可用：设置本地备份
     */
    public void setAcCoins(Player player, int amount) {
        if (amount < 0) amount = 0;
        UUID uuid = player.getUniqueId();

        if (vaultAvailable) {
            // Vault 可用：设置 Vault 余额
            double current = vaultEconomy.getBalance(player);
            if (current > 0) {
                vaultEconomy.withdrawPlayer(player, current);
            }
            if (amount > 0) {
                vaultEconomy.depositPlayer(player, amount);
            }
            // 同步更新本地备份（保持一致性）
            localBackup.put(uuid, amount);
        } else {
            // Vault 不可用：设置本地备份
            localBackup.put(uuid, amount);
        }
        saveLocalBackup();
    }

    // ======================================
    // 出售 & 购买
    // ======================================
    public void sellItem(Player player, String materialKey, int quantity) {
        if (quantity <= 0) {
            player.sendMessage("§c数量必须大于0");
            return;
        }
        Material m = Material.getMaterial(materialKey.toUpperCase());
        if (m == null) return;
        int per = sellPrices.getOrDefault(m.name(), 0);
        if (per <= 0) return;

        int count = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null || i.getType() != m) continue;
            int a = i.getAmount();
            if (a <= quantity) {
                count += a;
                quantity -= a;
                i.setAmount(0);
            } else {
                count += quantity;
                i.setAmount(a - quantity);
                quantity = 0;
                break;
            }
            if (quantity <= 0) break;
        }

        if (count == 0) {
            player.sendMessage("§c你没有该物品");
            return;
        }

        int total = count * per;
        addAcCoins(player, total);
        player.sendMessage("§a成功出售 " + count + " 个，获得 " + total + " AC币");
    }

    public void buyItem(Player player, ShopItem si, int quantity) {
        int total = si.getPrice() * quantity;
        if (getAcCoins(player) < total) {
            player.sendMessage("§cAC币不足！");
            return;
        }
        removeAcCoins(player, total);

        ItemStack give = new ItemStack(si.getMaterial(), si.getAmount() * quantity);
        ItemMeta meta = give.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> entry : si.getEnchantments().entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            give.setItemMeta(meta);
        }

        Map<Integer, ItemStack> left = player.getInventory().addItem(give);
        if (!left.isEmpty()) {
            left.values().forEach(it -> player.getWorld().dropItem(player.getLocation(), it));
        }
        player.sendMessage("§a购买成功！");
    }

    public ShopItem getShopItem(String name) {
        for (ShopItem si : shopItems) {
            if (si.getName().equalsIgnoreCase(name)) return si;
        }
        return null;
    }

    public ShopItem getPlayerSelectedShopItem(Player player) {
        return playerSelectedShopItem.get(player.getUniqueId());
    }

    public void clearPlayerSelectedShopItem(Player player) {
        playerSelectedShopItem.remove(player.getUniqueId());
    }

    public int getShopPageFromInvName(String invName) {
        if (!invName.startsWith(SHOP_INV_PREFIX)) return 1;
        try {
            String pageStr = invName.replace(SHOP_INV_PREFIX, "").split("/")[0];
            return Integer.parseInt(pageStr);
        } catch (Exception e) {
            return 1;
        }
    }

    // ======================================
    // Getter 方法
    // ======================================
    public Map<String, Integer> getSellPrices() { return sellPrices; }
    public List<ShopItem> getShopItems() { return new ArrayList<>(shopItems); }

    /**
     * 获取本地备份数据（用于调试）
     */
    public Map<UUID, Integer> getLocalBackup() {
        return new HashMap<>(localBackup);
    }

    /**
     * 手动强制同步所有在线玩家数据（管理员命令可用）
     */
    public void forceSyncAllPlayers() {
        if (!vaultAvailable) {
            plugin.getLogger().warning("§e[AC经济] Vault 不可用，无法同步");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            double vaultBalance = vaultEconomy.getBalance(p);
            localBackup.put(uuid, (int) vaultBalance);
        }
        saveLocalBackup();
        plugin.getLogger().info("§a[AC经济] 已强制同步所有在线玩家数据到本地备份");
    }

    /**
     * 手动从本地备份恢复 Vault 数据
     */
    public void restoreFromBackup() {
        if (!vaultAvailable) {
            plugin.getLogger().warning("§e[AC经济] Vault 不可用，无法恢复");
            return;
        }
        for (Map.Entry<UUID, Integer> entry : localBackup.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                double current = vaultEconomy.getBalance(p);
                if (current > 0) {
                    vaultEconomy.withdrawPlayer(p, current);
                }
                if (entry.getValue() > 0) {
                    vaultEconomy.depositPlayer(p, entry.getValue());
                }
            }
        }
        plugin.getLogger().info("§a[AC经济] 已从本地备份恢复 Vault 数据");
    }
}