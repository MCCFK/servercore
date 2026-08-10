package com.apple.servercore.ranking;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

// 24点游戏记录类
class Game24Record {
    String playerName;
    int playCount;
    int bestTime; // 最短耗时（秒）
}

public class Ranking implements Listener {

    private final MainPlugin plugin;
    private final File timeFile;
    private final File game24File;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Long> playTime = new HashMap<>();
    private final Map<UUID, Long> loginTime = new HashMap<>();
    private final Map<UUID, Game24Record> game24Records = new HashMap<>();
    private final Map<UUID, Long> game24StartTime = new HashMap<>();

    // Vault 经济系统
    private Economy vaultEconomy;
    private boolean vaultAvailable = false;

    public static final String RANK_MENU = "§6§l排行榜选择";
    public static final String BANK_MENU = "§6§l银行";
    public static final String GAME_24_MENU = "§6§l24点游戏";
    public static final String GAME_24_RECORDS = "§6§l24点排行榜 TOP10";
    public static final String SLOT_MACHINE_MENU = "§6§l老虎机";
    public static final String LOAN_MENU = "§6§l贷款";
    public static final String LOTTERY_MENU = "§6§l抽奖";
    public static final String DONATION_MENU = "§6§l捐款";
    public static final String RANK_AC_COIN = "§6§lAC币排行榜 TOP10";
    public static final String RANK_PLAY_TIME = "§6§l在线时间排行榜 TOP10";
    public static final String RANK_APPLE_COIN = "§6§l苹果币排行榜 TOP10";

    public Ranking(MainPlugin plugin) {
        this.plugin = plugin;
        this.timeFile = new File(plugin.getDataFolder(), "play_time.json");
        this.game24File = new File(plugin.getDataFolder(), "game24_records.json");
        setupVault();
        loadTimeData();
        loadGame24Records();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ======================================
    // Vault 经济初始化
    // ======================================
    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("§e[排行榜] Vault 未找到，AC币排行榜将使用本地数据");
            vaultAvailable = false;
            return;
        }
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            vaultEconomy = registration.getProvider();
            vaultAvailable = true;
            plugin.getLogger().info("§a[排行榜] Vault 经济系统已连接");
        } else {
            plugin.getLogger().info("§e[排行榜] Vault Economy 服务未注册，AC币排行榜将使用本地数据");
            vaultAvailable = false;
        }
    }

    public void loadTimeData() {
        if (!timeFile.exists()) return;
        try (Reader r = new FileReader(timeFile)) {
            Type type = new TypeToken<Map<UUID, Long>>() {}.getType();
            Map<UUID, Long> data = gson.fromJson(r, type);
            if (data != null) {
                playTime.clear();
                playTime.putAll(data);
            }
        } catch (Exception ignored) {}
    }

    public void saveTimeData() {
        try (Writer w = new FileWriter(timeFile)) {
            gson.toJson(playTime, w);
        } catch (IOException ignored) {}
    }

    // ====================== 24点游戏记录加载/保存 ======================
    public void loadGame24Records() {
        if (!game24File.exists()) return;
        try (Reader r = new FileReader(game24File)) {
            Type type = new TypeToken<Map<UUID, Game24Record>>() {}.getType();
            Map<UUID, Game24Record> data = gson.fromJson(r, type);
            if (data != null) {
                game24Records.clear();
                game24Records.putAll(data);
            }
        } catch (Exception ignored) {}
    }

    public void saveGame24Records() {
        try (Writer w = new FileWriter(game24File)) {
            gson.toJson(game24Records, w);
        } catch (IOException ignored) {}
    }

    // 记录玩家24点成绩
    public void recordGame24Result(Player p, int timeSeconds) {
        UUID uuid = p.getUniqueId();
        Game24Record record = game24Records.get(uuid);

        if (record == null) {
            record = new Game24Record();
            record.playerName = p.getName();
            record.playCount = 0;
            record.bestTime = Integer.MAX_VALUE;
        }

        record.playCount++;
        if (timeSeconds < record.bestTime) {
            record.bestTime = timeSeconds;
        }
        record.playerName = p.getName(); // 更新名字

        game24Records.put(uuid, record);
        saveGame24Records();

        // 计算AC币奖励（根据耗时，0-500）
        int reward = calculateReward(timeSeconds);
        if (reward > 0) {
            plugin.economicSystem.addAcCoins(p, reward);
            p.sendMessage("§a完成24点！用时" + timeSeconds + "秒，获得 §e" + reward + " AC币");
        } else {
            p.sendMessage("§a完成24点！用时" + timeSeconds + "秒");
        }
    }

    private int calculateReward(int timeSeconds) {
        // 越快奖励越多：30秒内500，每多1秒减少10，最低0
        if (timeSeconds <= 30) return 500;
        if (timeSeconds >= 80) return 0;
        return Math.max(0, 500 - (timeSeconds - 30) * 10);
    }

    public List<Map.Entry<UUID, Game24Record>> getTopGame24Records() {
        return game24Records.entrySet().stream()
                .sorted((a, b) -> Integer.compare(a.getValue().bestTime, b.getValue().bestTime))
                .limit(10)
                .collect(Collectors.toList());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        loginTime.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginTime.containsKey(uuid)) return;
        long sec = (System.currentTimeMillis() - loginTime.get(uuid)) / 1000;
        playTime.put(uuid, playTime.getOrDefault(uuid, 0L) + sec);
        loginTime.remove(uuid);
        saveTimeData();
    }

    public long getTotalSeconds(Player p) {
        UUID uuid = p.getUniqueId();
        long total = playTime.getOrDefault(uuid, 0L);

        if (loginTime.containsKey(uuid)) {
            long currentSession = (System.currentTimeMillis() - loginTime.get(uuid)) / 1000;
            total += currentSession;
        }
        return total;
    }

    public String format(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        } else if (m > 0) {
            return m + "m " + s + "s";
        } else {
            return s + "s";
        }
    }

    // ======================================
    // AC币排行榜 - 支持 Vault 同步
    // ======================================
    public List<Map.Entry<UUID, Integer>> getTopAc() {
        Map<UUID, Integer> acCoinMap = new HashMap<>();

        if (vaultAvailable && vaultEconomy != null) {
            // ===== 从 Vault 获取所有在线玩家的 AC币 =====
            // Vault 不直接支持遍历所有玩家，所以需要结合本地存储
            // 优先从本地备份获取所有玩家列表，然后从 Vault 获取余额

            // 1. 从本地备份获取所有玩家 UUID
            Map<UUID, Integer> localBackup = plugin.economicSystem.getLocalBackup();
            if (localBackup != null && !localBackup.isEmpty()) {
                for (Map.Entry<UUID, Integer> entry : localBackup.entrySet()) {
                    UUID uuid = entry.getKey();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        // 在线玩家从 Vault 获取实时余额
                        int balance = (int) vaultEconomy.getBalance(p);
                        acCoinMap.put(uuid, balance);
                    } else {
                        // 离线玩家使用本地备份数据
                        acCoinMap.put(uuid, entry.getValue());
                    }
                }
            }

            // 2. 补充在线但本地备份中没有的玩家
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (!acCoinMap.containsKey(uuid)) {
                    int balance = (int) vaultEconomy.getBalance(p);
                    acCoinMap.put(uuid, balance);
                }
            }
        } else {
            // ===== Vault 不可用，使用本地数据 =====
            Map<UUID, Integer> localBackup = plugin.economicSystem.getLocalBackup();
            if (localBackup != null) {
                acCoinMap.putAll(localBackup);
            }
        }

        return acCoinMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)  // 只显示有余额的玩家
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ========== 苹果币排行榜 ==========
    public List<Map.Entry<UUID, Integer>> getTopAppleCoin() {
        Map<UUID, Integer> appleCoinMap = new HashMap<>();
        if (plugin.economicSystem.getAppleCoins() != null) {
            for (UUID uuid : plugin.economicSystem.getAppleCoins().getAllPlayers()) {
                int coins = plugin.economicSystem.getAppleCoins().getAppleCoins(uuid);
                appleCoinMap.put(uuid, coins);
            }
        }
        return appleCoinMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, Long>> getTopTime() {
        Map<UUID, Long> temp = new HashMap<>(playTime);
        for (UUID u : loginTime.keySet()) {
            long current = (System.currentTimeMillis() - loginTime.get(u)) / 1000;
            temp.put(u, playTime.getOrDefault(u, 0L) + current);
        }
        return temp.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ====================== 判断是否基岩版 ======================
    private boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 统一入口 ======================
    public void openRankMenu(Player p) {
        if (isBedrockPlayer(p)) {
            openBedrockRankMenu(p);
        } else {
            openJavaRankMenu(p);
        }
    }

    // ====================== Java版 - 排行榜选择菜单 ======================
    public void openJavaRankMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, RANK_MENU);
        inv.setItem(10, createItem(Material.EMERALD, "§aAC币排行榜"));
        inv.setItem(13, createItem(Material.GOLDEN_APPLE, "§6苹果币排行榜"));
        inv.setItem(16, createItem(Material.CLOCK, "§e在线时间排行榜"));
        p.openInventory(inv);
    }

    // ====================== Java版 - 银行菜单 ======================
    public void openJavaBankMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, BANK_MENU);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }
        // 第一行功能按钮
        inv.setItem(19, createItemWithLore(Material.PAPER, "§a24点游戏", List.of("§7点击开始游戏")));
        inv.setItem(21, createItemWithLore(Material.GOLD_INGOT, "§e老虎机", List.of("§7开发中")));
        inv.setItem(23, createItemWithLore(Material.EMERALD, "§b贷款", List.of("§c暂不可用")));
        inv.setItem(25, createItemWithLore(Material.CHEST, "§d抽奖", List.of("§c暂不可用")));
        inv.setItem(32, createItemWithLore(Material.GOLD_BLOCK, "§6捐款", List.of("§c暂不可用")));

        ItemStack backMain = new ItemStack(Material.BARRIER);
        ItemMeta bmm = backMain.getItemMeta();
        bmm.setDisplayName("§c⬅️ 返回服务器主菜单");
        backMain.setItemMeta(bmm);
        inv.setItem(53, backMain);
        p.openInventory(inv);
    }

    // ====================== Java版 - 子菜单（通用）======================
    private void openSubMenu(Player p, String title, String featureName) {
        Inventory inv = Bukkit.createInventory(null, 54, title);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }
        // 中间显示功能名称
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e" + featureName);
        infoMeta.setLore(List.of("§c功能开发中..."));
        info.setItemMeta(infoMeta);
        inv.setItem(22, info);

        ItemStack backBank = new ItemStack(Material.BARRIER);
        ItemMeta bbm = backBank.getItemMeta();
        bbm.setDisplayName("§c⬅️ 返回银行");
        backBank.setItemMeta(bbm);
        inv.setItem(53, backBank);
        p.openInventory(inv);
    }

    // ====================== Java版 - 24点游戏 ======================
    public void open24GameMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GAME_24_MENU);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }

        // 生成4个随机数字（1-9），确保有解
        java.util.Random random = new java.util.Random();
        int[] numbers = new int[4];
        String solution;
        int attempts = 0;
        do {
            for (int i = 0; i < 4; i++) {
                numbers[i] = random.nextInt(9) + 1;
            }
            solution = findSolution(numbers);
            attempts++;
            // 最多尝试100次，避免死循环
            if (attempts > 100) {
                // 使用默认有解的题目
                numbers = new int[]{3, 3, 8, 8};
                solution = findSolution(numbers);
                break;
            }
        } while ("无解".equals(solution));

        // 记录开始时间
        game24StartTime.put(p.getUniqueId(), System.currentTimeMillis());

        // 第一行：当前输入显示和排行榜按钮
        ItemStack display = new ItemStack(Material.NAME_TAG);
        ItemMeta displayMeta = display.getItemMeta();
        displayMeta.setDisplayName("§e当前输入: §f(空)");
        displayMeta.setLore(List.of("§7点击数字和运算符开始解题"));
        display.setItemMeta(displayMeta);
        inv.setItem(3, display);

        // 排行榜按钮
        ItemStack records = new ItemStack(Material.DIAMOND);
        ItemMeta recordsMeta = records.getItemMeta();
        recordsMeta.setDisplayName("§b查看排行榜");
        recordsMeta.setLore(List.of("§7点击查看24点排行榜"));
        records.setItemMeta(recordsMeta);
        inv.setItem(5, records);

        // 第二行：4个数字
        Material[] numberMaterials = {Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL};
        for (int i = 0; i < 4; i++) {
            ItemStack numItem = new ItemStack(numberMaterials[i]);
            ItemMeta numMeta = numItem.getItemMeta();
            numMeta.setDisplayName("§6数字: " + numbers[i]);
            numMeta.setLore(List.of("§7使用这4个数字通过加减乘除计算出24"));
            numItem.setItemMeta(numMeta);
            inv.setItem(10 + i * 2, numItem);
        }

        // 第三行：运算符和括号
        inv.setItem(19, createOperatorItem(Material.PAPER, "§a+", "加号"));
        inv.setItem(20, createOperatorItem(Material.PAPER, "§c-", "减号"));
        inv.setItem(21, createOperatorItem(Material.PAPER, "§e*", "乘号"));
        inv.setItem(22, createOperatorItem(Material.PAPER, "§b/", "除号"));
        inv.setItem(23, createOperatorItem(Material.PAPER, "§d(", "左括号"));
        inv.setItem(24, createOperatorItem(Material.PAPER, "§d)", "右括号"));

        // 第四行：操作按钮
        ItemStack verify = new ItemStack(Material.EMERALD);
        ItemMeta verifyMeta = verify.getItemMeta();
        verifyMeta.setDisplayName("§a验证答案");
        verifyMeta.setLore(List.of("§7检查你的解答是否正确"));
        verify.setItemMeta(verifyMeta);
        inv.setItem(28, verify);

        ItemStack undo = new ItemStack(Material.BARRIER);
        ItemMeta undoMeta = undo.getItemMeta();
        undoMeta.setDisplayName("§c撤销上一步");
        undoMeta.setLore(List.of("§7撤销最后一次输入"));
        undo.setItemMeta(undoMeta);
        inv.setItem(29, undo);

        ItemStack reset = new ItemStack(Material.ARROW);
        ItemMeta resetMeta = reset.getItemMeta();
        resetMeta.setDisplayName("§b重新输入");
        resetMeta.setLore(List.of("§7清空当前输入"));
        reset.setItemMeta(resetMeta);
        inv.setItem(30, reset);

        // 第五行：提示和答案
        ItemStack hint = new ItemStack(Material.BOOK);
        ItemMeta hintMeta = hint.getItemMeta();
        hintMeta.setDisplayName("§e查看提示");
        hintMeta.setLore(List.of("§7点击查看解题思路"));
        hint.setItemMeta(hintMeta);
        inv.setItem(37, hint);

        ItemStack answer = new ItemStack(Material.GOLD_INGOT);
        ItemMeta answerMeta = answer.getItemMeta();
        answerMeta.setDisplayName("§6查看答案");
        answerMeta.setLore(List.of("§7点击显示一个可行解法"));
        answer.setItemMeta(answerMeta);
        inv.setItem(38, answer);

        ItemStack restart = new ItemStack(Material.CLOCK);
        ItemMeta restartMeta = restart.getItemMeta();
        restartMeta.setDisplayName("§b重新出题");
        restartMeta.setLore(List.of("§7生成新的4个数字"));
        restart.setItemMeta(restartMeta);
        inv.setItem(39, restart);

        ItemStack backBank = new ItemStack(Material.BARRIER);
        ItemMeta bbm = backBank.getItemMeta();
        bbm.setDisplayName("§c⬅️ 返回银行");
        backBank.setItemMeta(bbm);
        inv.setItem(53, backBank);

        // 存储当前题目数据到玩家元数据
        p.setMetadata("24game_numbers", new org.bukkit.metadata.FixedMetadataValue(plugin, numbers));
        p.setMetadata("24game_input", new org.bukkit.metadata.FixedMetadataValue(plugin, new StringBuilder()));

        p.openInventory(inv);
    }

    private ItemStack createOperatorItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§7" + description));
        item.setItemMeta(meta);
        return item;
    }

    // ====================== 基岩版 - 排行榜选择菜单 ======================
    private void openBedrockRankMenu(Player p) {
        SimpleForm form = SimpleForm.builder()
                .title("§6排行榜")
                .content("§7选择你要查看的排行榜")
                .button("§aAC币排行榜")
                .button("§6苹果币排行榜")
                .button("§e在线时间排行榜")
                .button("§7⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    switch (id) {
                        case 0 -> openBedrockAcRank(p);
                        case 1 -> openBedrockAppleCoinRank(p);
                        case 2 -> openBedrockTimeRank(p);
                        case 3 -> plugin.getACcraft().openMainMenu(p);
                    }
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== Java版 - AC币排行榜 ======================
    public void openAcRank(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, RANK_AC_COIN);
        List<Map.Entry<UUID, Integer>> list = getTopAc();

        if (list.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName("§c暂无数据");
            emptyMeta.setLore(List.of("§7还没有玩家拥有AC币"));
            empty.setItemMeta(emptyMeta);
            inv.setItem(22, empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                int coin = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";

                ItemStack item = new ItemStack(i < 3 ? Material.GOLD_BLOCK : Material.GOLD_INGOT);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6#" + (i + 1) + " §f" + name);
                meta.setLore(Collections.singletonList("§aAC币: §e" + coin));
                // 前三名添加特殊标记
                if (i == 0) meta.setLore(List.of("§aAC币: §e" + coin, "§6👑 冠军"));
                else if (i == 1) meta.setLore(List.of("§aAC币: §e" + coin, "§7🥈 亚军"));
                else if (i == 2) meta.setLore(List.of("§aAC币: §e" + coin, "§6🥉 季军"));
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }

        // 显示存储模式
        String mode = plugin.economicSystem.isVaultEnabled() ? "§aVault" : "§e本地备份";
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§7数据来源: " + mode);
        info.setItemMeta(infoMeta);
        inv.setItem(45, info);

        ItemStack back = new ItemStack(Material.STONE);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7⬅️ 返回排行榜");
        back.setItemMeta(bm);
        inv.setItem(49, back);

        ItemStack backMain = new ItemStack(Material.BARRIER);
        ItemMeta bmm = backMain.getItemMeta();
        bmm.setDisplayName("§c⬅️ 返回服务器主菜单");
        backMain.setItemMeta(bmm);
        inv.setItem(48, backMain);

        p.openInventory(inv);
    }

    // ====================== 基岩版 - AC币排行榜 ======================
    private void openBedrockAcRank(Player p) {
        List<Map.Entry<UUID, Integer>> list = getTopAc();
        StringBuilder content = new StringBuilder("§6===== AC币排行榜 =====\n\n");

        if (list.isEmpty()) {
            content.append("§c暂无数据");
        } else {
            // 显示存储模式
            String mode = plugin.economicSystem.isVaultEnabled() ? "Vault" : "本地备份";
            content.append("§7数据来源: §f").append(mode).append("\n\n");

            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                int coin = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";
                String prefix = "";
                if (i == 0) prefix = "👑 ";
                else if (i == 1) prefix = "🥈 ";
                else if (i == 2) prefix = "🥉 ";
                content.append("§6#").append(i + 1).append(" ").append(prefix)
                        .append("§f").append(name)
                        .append(" §e").append(coin).append(" AC币\n");
            }
        }

        SimpleForm form = SimpleForm.builder()
                .title("§aAC币排行榜")
                .content(content.toString())
                .button("§7⬅️ 返回排行榜")
                .button("§c⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) openBedrockRankMenu(p);
                    else if (id == 1) plugin.getACcraft().openMainMenu(p);
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== 苹果币排行榜 - Java版 ======================
    public void openAppleCoinRank(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, RANK_APPLE_COIN);
        List<Map.Entry<UUID, Integer>> list = getTopAppleCoin();

        if (list.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName("§c暂无数据");
            emptyMeta.setLore(List.of("§7还没有玩家拥有苹果币"));
            empty.setItemMeta(emptyMeta);
            inv.setItem(22, empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                int coin = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";

                ItemStack item = new ItemStack(i < 3 ? Material.GOLD_BLOCK : Material.GOLDEN_APPLE);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6#" + (i + 1) + " §f" + name);
                if (i == 0) meta.setLore(List.of("§6苹果币: §e" + coin, "§6👑 冠军"));
                else if (i == 1) meta.setLore(List.of("§6苹果币: §e" + coin, "§7🥈 亚军"));
                else if (i == 2) meta.setLore(List.of("§6苹果币: §e" + coin, "§6🥉 季军"));
                else meta.setLore(Collections.singletonList("§6苹果币: §e" + coin));
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }

        ItemStack back = new ItemStack(Material.STONE);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7⬅️ 返回排行榜");
        back.setItemMeta(bm);
        inv.setItem(49, back);

        ItemStack backMain = new ItemStack(Material.BARRIER);
        ItemMeta bmm = backMain.getItemMeta();
        bmm.setDisplayName("§c⬅️ 返回服务器主菜单");
        backMain.setItemMeta(bmm);
        inv.setItem(48, backMain);

        p.openInventory(inv);
    }

    // ====================== 苹果币排行榜 - 基岩版 ======================
    private void openBedrockAppleCoinRank(Player p) {
        List<Map.Entry<UUID, Integer>> list = getTopAppleCoin();
        StringBuilder content = new StringBuilder("§6===== 苹果币排行榜 =====\n\n");

        if (list.isEmpty()) {
            content.append("§c暂无数据");
        } else {
            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                int coin = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";
                String prefix = "";
                if (i == 0) prefix = "👑 ";
                else if (i == 1) prefix = "🥈 ";
                else if (i == 2) prefix = "🥉 ";
                content.append("§6#").append(i + 1).append(" ").append(prefix)
                        .append("§f").append(name)
                        .append(" §e").append(coin).append(" 苹果币\n");
            }
        }

        SimpleForm form = SimpleForm.builder()
                .title("§6苹果币排行榜")
                .content(content.toString())
                .button("§7⬅️ 返回排行榜")
                .button("§c⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) openBedrockRankMenu(p);
                    else if (id == 1) plugin.getACcraft().openMainMenu(p);
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== Java版 - 在线时间排行榜 ======================
    public void openTimeRank(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, RANK_PLAY_TIME);
        List<Map.Entry<UUID, Long>> list = getTopTime();

        if (list.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName("§c暂无数据");
            emptyMeta.setLore(List.of("§7还没有玩家在线记录"));
            empty.setItemMeta(emptyMeta);
            inv.setItem(22, empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                long sec = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";
                String time = format(sec);

                ItemStack item = new ItemStack(i < 3 ? Material.GOLD_BLOCK : Material.CLOCK);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6#" + (i + 1) + " §f" + name);
                if (i == 0) meta.setLore(List.of("§a时长: §e" + time, "§6👑 冠军"));
                else if (i == 1) meta.setLore(List.of("§a时长: §e" + time, "§7🥈 亚军"));
                else if (i == 2) meta.setLore(List.of("§a时长: §e" + time, "§6🥉 季军"));
                else meta.setLore(Collections.singletonList("§a时长: §e" + time));
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }

        ItemStack back = new ItemStack(Material.STONE);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7⬅️ 返回排行榜");
        back.setItemMeta(bm);
        inv.setItem(49, back);

        ItemStack backMain = new ItemStack(Material.BARRIER);
        ItemMeta bmm = backMain.getItemMeta();
        bmm.setDisplayName("§c⬅️ 返回服务器主菜单");
        backMain.setItemMeta(bmm);
        inv.setItem(48, backMain);

        p.openInventory(inv);
    }

    // ====================== 基岩版 - 在线时间排行榜 ======================
    private void openBedrockTimeRank(Player p) {
        List<Map.Entry<UUID, Long>> list = getTopTime();
        StringBuilder content = new StringBuilder("§6===== 在线时间排行榜 =====\n\n");

        if (list.isEmpty()) {
            content.append("§c暂无数据");
        } else {
            for (int i = 0; i < list.size(); i++) {
                UUID uuid = list.get(i).getKey();
                long sec = list.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "未知";
                String prefix = "";
                if (i == 0) prefix = "👑 ";
                else if (i == 1) prefix = "🥈 ";
                else if (i == 2) prefix = "🥉 ";
                content.append("§6#").append(i + 1).append(" ").append(prefix)
                        .append("§f").append(name)
                        .append(" §e").append(format(sec)).append("\n");
            }
        }

        SimpleForm form = SimpleForm.builder()
                .title("§e在线时间排行榜")
                .content(content.toString())
                .button("§7⬅️ 返回排行榜")
                .button("§c⬅️ 返回主菜单")
                .validResultHandler((SimpleFormResponse response) -> {
                    int id = response.clickedButtonId();
                    if (id == 0) openBedrockRankMenu(p);
                    else if (id == 1) plugin.getACcraft().openMainMenu(p);
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ====================== GUI点击监听 ======================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        String title = e.getView().getTitle();
        ItemStack cur = e.getCurrentItem();

        // 只锁自己GUI
        boolean isRankingGui = title.equals(RANK_MENU) || title.equals(BANK_MENU)
                || title.equals(GAME_24_MENU) || title.equals(SLOT_MACHINE_MENU)
                || title.equals(LOAN_MENU) || title.equals(LOTTERY_MENU)
                || title.equals(DONATION_MENU) || title.equals(GAME_24_RECORDS)
                || title.equals(RANK_AC_COIN) || title.equals(RANK_PLAY_TIME)
                || title.equals(RANK_APPLE_COIN);
        if (isRankingGui && clicked == top) {
            e.setCancelled(true);
        }

        // 玩家背包放行
        if (clicked != top || cur == null || !cur.hasItemMeta()) return;

        String name = cur.getItemMeta().getDisplayName();

        if (title.equals(RANK_MENU)) {
            if (name.equals("§aAC币排行榜")) openAcRank(p);
            if (name.equals("§6苹果币排行榜")) openAppleCoinRank(p);
            if (name.equals("§e在线时间排行榜")) openTimeRank(p);
        }

        if (title.equals(BANK_MENU)) {
            if (cur.getType() == Material.BARRIER) {
                p.closeInventory();
                plugin.getACcraft().openMainMenu(p);
            } else if (cur.getType() == Material.PAPER && name.equals("§a24点游戏")) {
                open24GameMenu(p);
            } else if (cur.getType() == Material.GOLD_INGOT && name.equals("§e老虎机")) {
                openSubMenu(p, SLOT_MACHINE_MENU, "老虎机");
            } else if (cur.getType() == Material.EMERALD && name.equals("§b贷款")) {
                openSubMenu(p, LOAN_MENU, "贷款");
            } else if (cur.getType() == Material.CHEST && name.equals("§d抽奖")) {
                openSubMenu(p, LOTTERY_MENU, "抽奖");
            } else if (cur.getType() == Material.GOLD_BLOCK && name.equals("§6捐款")) {
                openSubMenu(p, DONATION_MENU, "捐款");
            }
            return;
        }

        // 处理子菜单返回
        if (title.equals(SLOT_MACHINE_MENU) ||
                title.equals(LOAN_MENU) || title.equals(LOTTERY_MENU) || title.equals(DONATION_MENU)) {
            if (cur.getType() == Material.BARRIER) {
                openJavaBankMenu(p);
            }
            return;
        }

        // 处理24点游戏交互
        if (title.equals(GAME_24_MENU)) {
            e.setCancelled(true);
            Inventory currentInv = e.getView().getTopInventory();

            if (cur.getType() == Material.BARRIER) {
                if (name.equals("§c⬅️ 返回银行")) {
                    openJavaBankMenu(p);
                } else if (name.equals("§c撤销上一步")) {
                    // 撤销最后输入的字符
                    if (p.hasMetadata("24game_input")) {
                        StringBuilder input = (StringBuilder) p.getMetadata("24game_input").get(0).value();
                        if (input.length() > 0) {
                            input.deleteCharAt(input.length() - 1);
                            updateInputDisplay(currentInv, input.toString());
                        }
                    }
                }
            } else if (cur.getType() == Material.ARROW && name.equals("§b重新输入")) {
                // 清空当前输入
                if (p.hasMetadata("24game_input")) {
                    StringBuilder input = (StringBuilder) p.getMetadata("24game_input").get(0).value();
                    input.setLength(0);
                    updateInputDisplay(currentInv, "");
                }
            } else if (cur.getType() == Material.CLOCK && name.equals("§b重新出题")) {
                open24GameMenu(p);
            } else if (cur.getType() == Material.EMERALD && name.equals("§a验证答案")) {
                // 验证答案
                if (p.hasMetadata("24game_input") && p.hasMetadata("24game_numbers")) {
                    StringBuilder input = (StringBuilder) p.getMetadata("24game_input").get(0).value();
                    int[] numbers = (int[]) p.getMetadata("24game_numbers").get(0).value();
                    String result = evaluateExpression(input.toString(), numbers);
                    if (result != null) {
                        p.sendMessage(result);
                        // 如果正确，记录成绩
                        if (result.startsWith("§a正确！")) {
                            long startTime = game24StartTime.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
                            int timeSeconds = (int) ((System.currentTimeMillis() - startTime) / 1000);
                            recordGame24Result(p, timeSeconds);
                            // 重新出题
                            Bukkit.getScheduler().runTaskLater(plugin, () -> open24GameMenu(p), 20L);
                        }
                    } else {
                        p.sendMessage("§c表达式有误，请检查！");
                    }
                }
            } else if (cur.getType() == Material.DIAMOND && name.equals("§b查看排行榜")) {
                openGame24Records(p);
            } else if (cur.getType() == Material.GOLD_INGOT && name.equals("§6查看答案")) {
                // 获取当前题目数字
                if (p.hasMetadata("24game_numbers")) {
                    int[] numbers = (int[]) p.getMetadata("24game_numbers").get(0).value();
                    String solution = findSolution(numbers);
                    p.sendMessage("§6一个可行解法: §e" + solution);
                    p.sendMessage("§c查看答案后此题无效，请重新出题！");
                    // 1秒后自动重新出题
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open24GameMenu(p), 20L);
                }
            } else if (cur.getType() == Material.BOOK && name.equals("§e查看提示")) {
                p.sendMessage("§e提示: 尝试将4个数字通过加减乘除组合成24");
                p.sendMessage("§7例如: (3+3)*(8-4) = 24");
            } else if (cur.getType() == Material.PAPER) {
                // 运算符或括号
                if (p.hasMetadata("24game_input")) {
                    StringBuilder input = (StringBuilder) p.getMetadata("24game_input").get(0).value();
                    if (name.equals("§a+")) input.append("+");
                    else if (name.equals("§c-")) input.append("-");
                    else if (name.equals("§e*")) input.append("*");
                    else if (name.equals("§b/")) input.append("/");
                    else if (name.equals("§d(")) input.append("(");
                    else if (name.equals("§d)")) input.append(")");
                    updateInputDisplay(currentInv, input.toString());
                }
            } else if (cur.getType() == Material.RED_WOOL || cur.getType() == Material.BLUE_WOOL ||
                    cur.getType() == Material.GREEN_WOOL || cur.getType() == Material.YELLOW_WOOL) {
                // 数字
                if (p.hasMetadata("24game_input")) {
                    StringBuilder input = (StringBuilder) p.getMetadata("24game_input").get(0).value();
                    String numStr = name.replace("§6数字: ", "");
                    input.append(numStr);
                    updateInputDisplay(currentInv, input.toString());
                }
            }
            return;
        }

        // 处理24点排行榜返回
        if (title.equals(GAME_24_RECORDS)) {
            if (cur.getType() == Material.BARRIER && name.equals("§c⬅️ 返回24点游戏")) {
                open24GameMenu(p);
            }
            return;
        }

        if (name.equals("§7⬅️ 返回排行榜")) openRankMenu(p);
        if (name.equals("§c⬅️ 返回服务器主菜单")) {
            p.closeInventory();
            plugin.getACcraft().openMainMenu(p);
        }
    }

    private ItemStack createItem(Material m, String name) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItemWithLore(Material m, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ====================== 24点求解算法 ======================
    private String findSolution(int[] numbers) {
        char[] ops = {'+', '-', '*', '/'};
        for (char op1 : ops) {
            for (char op2 : ops) {
                for (char op3 : ops) {
                    String result = tryCombination(numbers[0], numbers[1], numbers[2], numbers[3], op1, op2, op3);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return "无解";
    }

    private String tryCombination(double a, double b, double c, double d, char op1, char op2, char op3) {
        // ((a op1 b) op2 c) op3 d
        double result = calculate(calculate(calculate(a, b, op1), c, op2), d, op3);
        if (Math.abs(result - 24) < 0.0001) {
            return String.format("((%.0f %c %.0f) %c %.0f) %c %.0f = 24", a, op1, b, op2, c, op3, d);
        }

        // (a op1 (b op2 c)) op3 d
        result = calculate(calculate(a, calculate(b, c, op2), op1), d, op3);
        if (Math.abs(result - 24) < 0.0001) {
            return String.format("(%.0f %c (%.0f %c %.0f)) %c %.0f = 24", a, op1, b, op2, c, op3, d);
        }

        // (a op1 b) op2 (c op3 d)
        result = calculate(calculate(a, b, op1), calculate(c, d, op3), op2);
        if (Math.abs(result - 24) < 0.0001) {
            return String.format("(%.0f %c %.0f) %c (%.0f %c %.0f) = 24", a, op1, b, op2, c, op3, d);
        }

        // a op1 ((b op2 c) op3 d)
        result = calculate(a, calculate(calculate(b, c, op2), d, op3), op1);
        if (Math.abs(result - 24) < 0.0001) {
            return String.format("%.0f %c ((%.0f %c %.0f) %c %.0f) = 24", a, op1, b, op2, c, op3, d);
        }

        // a op1 (b op2 (c op3 d))
        result = calculate(a, calculate(b, calculate(c, d, op3), op2), op1);
        if (Math.abs(result - 24) < 0.0001) {
            return String.format("%.0f %c (%.0f %c (%.0f %c %.0f)) = 24", a, op1, b, op2, c, op3, d);
        }

        return null;
    }

    private double calculate(double a, double b, char op) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) return Double.NaN;
                return a / b;
            default: return Double.NaN;
        }
    }

    // ====================== 更新输入显示 ======================
    private void updateInputDisplay(Inventory inv, String input) {
        ItemStack display = inv.getItem(3);
        if (display != null && display.hasItemMeta()) {
            ItemMeta meta = display.getItemMeta();
            String displayText = input.isEmpty() ? "§e当前输入: §f(空)" : "§e当前输入: §f" + input;
            meta.setDisplayName(displayText);
            display.setItemMeta(meta);
        }
    }

    // ====================== 验证表达式 ======================
    private String evaluateExpression(String expr, int[] numbers) {
        try {
            // 去除空格
            expr = expr.replaceAll("\\s+", "");

            // 检查是否使用了所有数字
            String tempExpr = expr.replaceAll("[^0-9]", "");
            java.util.List<Character> usedDigits = new java.util.ArrayList<>();
            for (char c : tempExpr.toCharArray()) {
                usedDigits.add(c);
            }

            java.util.List<Character> availableDigits = new java.util.ArrayList<>();
            for (int num : numbers) {
                availableDigits.add(Character.forDigit(num, 10));
            }

            // 排序比较
            java.util.Collections.sort(usedDigits);
            java.util.Collections.sort(availableDigits);

            if (!usedDigits.equals(availableDigits)) {
                return "§c错误：必须使用且仅使用给出的4个数字！";
            }

            // 计算表达式结果
            double result = evaluateMathExpression(expr);

            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return "§c错误：表达式计算异常！（表达式: " + expr + "）";
            }

            if (Math.abs(result - 24) < 0.0001) {
                return "§a正确！ " + expr + " = 24";
            } else {
                return String.format("§c错误！ %s = %.2f ≠ 24", expr, result);
            }
        } catch (Exception e) {
            return "§c表达式格式有误，请检查！（" + e.getMessage() + "）";
        }
    }

    private double evaluateMathExpression(String expr) {
        try {
            // 使用 Nashorn JavaScript 引擎计算
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = null;
            String[] engineNames = {"JavaScript", "js", "nashorn", "graal.js"};
            for (String name : engineNames) {
                engine = manager.getEngineByName(name);
                if (engine != null) break;
            }

            if (engine == null) {
                return manualEvaluate(expr);
            }

            Object result = engine.eval(expr);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
            return Double.NaN;
        } catch (Exception e) {
            try {
                return manualEvaluate(expr);
            } catch (Exception ex) {
                return Double.NaN;
            }
        }
    }

    // 手动表达式求值（备用方案）
    private double manualEvaluate(String expr) {
        int[] pos = {0};
        return parseAddSub(expr, pos);
    }

    private double parseAddSub(String expr, int[] pos) {
        double result = parseMulDiv(expr, pos);
        while (pos[0] < expr.length()) {
            char c = expr.charAt(pos[0]);
            if (c == '+') {
                pos[0]++;
                result += parseMulDiv(expr, pos);
            } else if (c == '-') {
                pos[0]++;
                result -= parseMulDiv(expr, pos);
            } else {
                break;
            }
        }
        return result;
    }

    private double parseMulDiv(String expr, int[] pos) {
        double result = parsePrimary(expr, pos);
        while (pos[0] < expr.length()) {
            char c = expr.charAt(pos[0]);
            if (c == '*') {
                pos[0]++;
                result *= parsePrimary(expr, pos);
            } else if (c == '/') {
                pos[0]++;
                double divisor = parsePrimary(expr, pos);
                if (divisor == 0) return Double.NaN;
                result /= divisor;
            } else {
                break;
            }
        }
        return result;
    }

    private double parsePrimary(String expr, int[] pos) {
        if (pos[0] >= expr.length()) return 0;

        char c = expr.charAt(pos[0]);
        if (c == '(') {
            pos[0]++;
            double result = parseAddSub(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') {
                pos[0]++;
            }
            return result;
        } else if (Character.isDigit(c)) {
            int start = pos[0];
            while (pos[0] < expr.length() && Character.isDigit(expr.charAt(pos[0]))) {
                pos[0]++;
            }
            return Integer.parseInt(expr.substring(start, pos[0]));
        }
        return 0;
    }

    // ====================== Java版 - 24点排行榜 ======================
    public void openGame24Records(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GAME_24_RECORDS);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }

        List<Map.Entry<UUID, Game24Record>> topRecords = getTopGame24Records();

        if (topRecords.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName("§c暂无记录");
            emptyMeta.setLore(List.of("§7完成24点游戏后会自动记录"));
            empty.setItemMeta(emptyMeta);
            inv.setItem(22, empty);
        } else {
            Material[] rankMaterials = {
                    Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.DIAMOND_BLOCK,
                    Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.REDSTONE_BLOCK,
                    Material.COAL_BLOCK, Material.OBSIDIAN, Material.END_CRYSTAL, Material.BEACON
            };

            for (int i = 0; i < topRecords.size(); i++) {
                UUID uuid = topRecords.get(i).getKey();
                Game24Record record = topRecords.get(i).getValue();

                ItemStack item = new ItemStack(rankMaterials[i % rankMaterials.length]);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6#" + (i + 1) + " §f" + record.playerName);
                List<String> lore = new ArrayList<>();
                lore.add("§a游玩次数: §e" + record.playCount);
                lore.add("§b最短耗时: §e" + record.bestTime + "秒");
                int reward = calculateReward(record.bestTime);
                lore.add("§6奖励AC币: §e" + reward);
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }

        ItemStack backBank = new ItemStack(Material.BARRIER);
        ItemMeta bbm = backBank.getItemMeta();
        bbm.setDisplayName("§c⬅️ 返回24点游戏");
        backBank.setItemMeta(bbm);
        inv.setItem(53, backBank);

        p.openInventory(inv);
    }
}