package com.apple.servercore.ranking;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
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

     // ====================== 抽奖系统 ======================
    public static final String LOTTERY_SELECT_MENU = "§6§l抽奖";
    public static final String BLIND_BOX_MENU = "§6§l盲盒";
    public static final String BIG_WHEEL_MENU = "§6§l大转盘";
    public static final String BIG_WHEEL_RESULT_MENU = "§6§l大转盘结果";

    // 盲盒奖池（从低到高）
    private static final Material[] BLIND_BOX_REWARDS = {
            Material.BARRIER, Material.COBBLESTONE, Material.COAL_ORE,
            Material.IRON_ORE, Material.COPPER_ORE,
            Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.ANCIENT_DEBRIS
    };
    // 盲盒分档直接金额
    private static final int[][] BLIND_BOX_TIER_AMOUNTS = {
            {5, 6, 7, 8, 9, 10, 12, 13, 0, 0, 0},   // 普通10
            {13, 15, 17, 18, 19, 20, 21, 25, 30, 0, 0}, // 中等20
            {0, 18, 20, 25, 27, 30, 31, 35, 37, 40, 0},// 高级30
            {0, 0, 35, 36, 37, 39, 39, 40, 42, 45, 48} // 满级40
    };
    private static final int[] BLIND_BOX_WEIGHTS = {15, 14, 12, 10, 8, 6, 5, 4, 3, 2, 1};
    // 盲盒分档权重
    private static final int[][] BLIND_BOX_TIER_WEIGHTS = {
            {3, 3, 3, 3, 3, 4, 1, 1, 0, 0, 0},   // 普通
            {4, 4, 4, 4, 6, 7, 2, 1, 1, 0, 0},   // 中等
            {0, 1, 1, 1, 1, 2,4, 7, 2, 1, 0},      // 高级
            {0, 0, 2, 2, 4, 4, 4, 9, 8, 9, 5}       // 满级
    };
    private static final int BLIND_BOX_TOTAL_WEIGHT;
    // 盲盒价格选项
    private static final int[] BLIND_BOX_PRICE_OPTIONS = {10, 20, 30, 40};
    private static final String[] BLIND_BOX_PRICE_NAMES = {"§7普通", "§a中等", "§6高级", "§c满级"};
    // 盲盒在界面中的slot位置（5x2排列）
    private static final int[] BLIND_BOX_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    // 盲盒连抽选项竖排slot
    private static final int[] DRAW_COUNT_SLOTS = {17, 26, 35, 44};

    // 大转盘奖池
    private static final Material[] WHEEL_PRIZES = {
            Material.BARRIER, Material.COAL_ORE, Material.IRON_ORE,
            Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.NETHERITE_SCRAP
    };
    // 大转盘分档直接金额
    private static final int[][] WHEEL_TIER_AMOUNTS = {
            {4, 10, 14, 15, 25, 0, 0},  // 普通15
            {15, 17, 20, 21, 25, 30, 0},// 中等20
            {0, 44, 47, 50, 52, 55, 60},// 高级50
            {0, 0, 55, 60, 61, 65, 67} // 满级60
    };
    private static final int[] WHEEL_WEIGHTS = {15, 12, 10, 8, 5, 3, 1};
    // 大转盘分档权重
    private static final int[][] WHEEL_TIER_WEIGHTS = {
            {1, 1, 2, 3, 1, 0, 0},   // 普通15
            {1, 2, 4, 3, 2, 1, 0},   // 中等20
            {0, 2, 3, 5, 2, 2, 1},      // 高级50
            {0, 0, 3, 4, 3, 2, 1}        // 满级60
    };
    private static final int WHEEL_TOTAL_WEIGHT;

    // 7x3大转盘显示
    private static final int[][] WHEEL_DISPLAY_SLOTS = {
            {10, 11, 12, 13, 14, 15, 16},
            {19, -1, -1, -1, -1, -1, 25},
            {28, 29, 30, 31, 32, 33, 34}
    };
    // 大转盘价格选项
    private static final int[] WHEEL_PRICE_OPTIONS = {15, 20, 50, 60};
    private static final String[] WHEEL_PRICE_NAMES = {"§7普通", "§a中等", "§6高级", "§c满级"};
    // 大转盘抽数选项
    private static final int[] DRAW_COUNTS = {1, 2, 5, 10};

    static {
        int bSum = 0;
        for (int w : BLIND_BOX_WEIGHTS) bSum += w;
        BLIND_BOX_TOTAL_WEIGHT = bSum;

        int wSum = 0;
        for (int w : WHEEL_WEIGHTS) wSum += w;
        WHEEL_TOTAL_WEIGHT = wSum;
    }

    // 抽奖状态映射
    private final Map<UUID, Boolean> lotteryUseAppleCoin = new HashMap<>();
    private final Map<UUID, Integer> lotteryDrawCount = new HashMap<>();
    // 盲盒状态
    private final Map<UUID, Material[]> blindBoxContents = new HashMap<>();     // 10个盒子的内容
    private final Map<UUID, boolean[]> blindBoxRevealed = new HashMap<>();      // 哪些已开
    private final Map<UUID, Integer> blindBoxRevealCount = new HashMap<>();     // 已开数量
    private final Map<UUID, Boolean> blindBoxOpening = new HashMap<>();         // 正在开盒
    // 盲盒选中价格
    private final Map<UUID, Integer> blindBoxSelectedPrice = new HashMap<>();
    // 大转盘状态
    private final Map<UUID, Boolean> wheelSpinning = new HashMap<>();
    private final Map<UUID, Boolean> wheelSkipAnim = new HashMap<>();
    // 大转盘选中价格
    private final Map<UUID, Integer> wheelSelectedPrice = new HashMap<>();

    // ====================== 老虎机 ======================
    // 符号配置：碎石、紫水晶碎片、铜、铁、绿、金、钻、下界合金
    private static final Material[] SLOT_SYMBOLS = {Material.GRAVEL, Material.AMETHYST_SHARD, Material.COPPER_INGOT,
            Material.IRON_INGOT, Material.EMERALD, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT};
    private static final double[] SLOT_PAYOUTS = {0.1, 0.5, 1.5, 2.5, 3, 4, 6, 8}; // AC币赔付倍率
    private static final double[] APPLE_PAYOUTS = {0.09, 0.4, 1.2, 2.1, 2.5, 3.4, 5.2, 6.4}; // 苹果币赔付倍率
    private static final int[] SLOT_WEIGHTS = {12, 10, 8, 6, 4, 3, 2, 1};  // 权重，越高越常见
    private static final int[] SLOT_SPIN_COUNTS = {1, 2, 5, 10};     // 连抽次数
    private static final int[] SLOT_BET_OPTIONS = {10, 20, 50, 100};  // 每注金额
    private static final int[] SLOT_GRID_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int TOTAL_WEIGHT;
    private static final int NINE_SAME_PITY_MIN = 1500;
    private static final int NINE_SAME_PITY_MAX = 2000;
    static {
        int sum = 0;
        for (int w : SLOT_WEIGHTS) sum += w;
        TOTAL_WEIGHT = sum;
    }
    private final Map<UUID, Integer> slotSpinCount = new HashMap<>();     // 当前选中连抽次数
    private final Map<UUID, Integer> slotBetPerSpin = new HashMap<>();    // 当前每注金额
    private final Map<UUID, Boolean> slotUseAppleCoin = new HashMap<>();  // true=苹果币 false=AC币
    private final Map<UUID, Boolean> slotSpinning = new HashMap<>();
    // 保底机制追踪
    private final Map<UUID, Integer> consecutiveLosses = new HashMap<>();    // 连亏计数
    private final Map<UUID, Integer> consecutiveNoWins = new HashMap<>();   // 连续未中计数
    private final Map<UUID, Integer> consecutiveWinRounds = new HashMap<>(); // 连赚轮次计数
    private final Map<UUID, Boolean> slotSkipAnim = new HashMap<>();          // 跳过动画
    // 九格全相同全局保底
    private int spinsSinceLastNineSame = 0;
    private int nineSamePityThreshold = NINE_SAME_PITY_MIN + new java.util.Random().nextInt(NINE_SAME_PITY_MAX - NINE_SAME_PITY_MIN + 1);
    // 老虎机8条赢钱线：3行 + 3列 + 2对角
    private static final int[][] WIN_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // 行
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // 列
            {0, 4, 8}, {2, 4, 6}              // 对角
    };

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
        inv.setItem(21, createItemWithLore(Material.GOLD_INGOT, "§e老虎机", List.of("§7点击进入", "§a3x3格子，8条赢钱线，最高10倍赔付！")));
        inv.setItem(23, createItemWithLore(Material.EMERALD, "§b贷款", List.of("§c暂不可用")));
        inv.setItem(25, createItemWithLore(Material.CHEST, "§d抽奖", List.of("§7点击进入", "§a盲盒 | 大转盘")));
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
                if (i == 0) meta.setLore(List.of("§aAC币: §e" + coin, "§6 冠军"));
                else if (i == 1) meta.setLore(List.of("§aAC币: §e" + coin, "§7 亚军"));
                else if (i == 2) meta.setLore(List.of("§aAC币: §e" + coin, "§6 季军"));
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
                if (i == 0) meta.setLore(List.of("§6苹果币: §e" + coin, "§6 冠军"));
                else if (i == 1) meta.setLore(List.of("§6苹果币: §e" + coin, "§7 亚军"));
                else if (i == 2) meta.setLore(List.of("§6苹果币: §e" + coin, "§6 季军"));
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
                if (i == 0) meta.setLore(List.of("§a时长: §e" + time, "§6 冠军"));
                else if (i == 1) meta.setLore(List.of("§a时长: §e" + time, "§7 亚军"));
                else if (i == 2) meta.setLore(List.of("§a时长: §e" + time, "§6 季军"));
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
                || title.equals(LOAN_MENU) || title.equals(DONATION_MENU)
                || title.equals(GAME_24_RECORDS)
                || title.equals(RANK_AC_COIN) || title.equals(RANK_PLAY_TIME)
                || title.equals(RANK_APPLE_COIN)
                || title.equals(LOTTERY_SELECT_MENU) || title.equals(BLIND_BOX_MENU)
                || title.equals(BIG_WHEEL_MENU) || title.equals(BIG_WHEEL_RESULT_MENU);
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
                openSlotMachine(p);
            } else if (cur.getType() == Material.EMERALD && name.equals("§b贷款")) {
                openSubMenu(p, LOAN_MENU, "贷款");
            } else if (cur.getType() == Material.CHEST && name.equals("§d抽奖")) {
                openLotterySelectMenu(p);
            } else if (cur.getType() == Material.GOLD_BLOCK && name.equals("§6捐款")) {
                openSubMenu(p, DONATION_MENU, "捐款");
            }
            return;
        }

        // 处理老虎机交互
        if (title.equals(SLOT_MACHINE_MENU)) {
            if (slotSpinning.getOrDefault(p.getUniqueId(), false)) {
                if (e.getSlot() == 41 && cur.getType() == Material.ARROW && name.contains("跳过")) {
                    slotSkipAnim.put(p.getUniqueId(), true);
                } else {
                    p.sendMessage("§c正在旋转中，请稍候...");
                }
                return;
            }
            // 返回银行
            if (cur.getType() == Material.BARRIER) {
                openJavaBankMenu(p);
                return;
            }
            // 跳过动画开关 - slot 41
            if (e.getSlot() == 41 && cur.getType() == Material.ARROW) {
                slotSkipAnim.put(p.getUniqueId(), !slotSkipAnim.getOrDefault(p.getUniqueId(), false));
                openSlotMachine(p);
                return;
            }
            // 旋转按钮 - slot 40
            if (e.getSlot() == 40 && cur.getType() == Material.GOLD_BLOCK && name.contains("开始旋转")) {
                spinSlotMachine(p);
                return;
            }
            // 赔付说明 - slot 14
            if (e.getSlot() == 14 && cur.getType() == Material.BOOK && name.equals("§6赔付说明")) {
                return;
            }
            // 币种切换 - slot 15（AC币）
            if (e.getSlot() == 15 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                slotUseAppleCoin.put(p.getUniqueId(), false);
                openSlotMachine(p);
                return;
            }
            // 币种切换 - slot 16（苹果币）
            if (e.getSlot() == 16 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                slotUseAppleCoin.put(p.getUniqueId(), true);
                openSlotMachine(p);
                return;
            }
            // 连抽选择（slot 23-26）
            if (e.getSlot() >= 23 && e.getSlot() <= 26) {
                for (int cnt : SLOT_SPIN_COUNTS) {
                    if (name.contains(String.valueOf(cnt) + " 连抽")) {
                        slotSpinCount.put(p.getUniqueId(), cnt);
                        openSlotMachine(p);
                        return;
                    }
                }
            }
            // 每注金额选择（slot 32-35）
            if (e.getSlot() >= 32 && e.getSlot() <= 35) {
                for (int i = SLOT_BET_OPTIONS.length - 1; i >= 0; i--) {
                    int amt = SLOT_BET_OPTIONS[i];
                    if (name.contains(String.valueOf(amt))) {
                        slotBetPerSpin.put(p.getUniqueId(), amt);
                        openSlotMachine(p);
                        return;
                    }
                }
            }
            return;
        }

        // 处理抽奖选择菜单
        if (title.equals(LOTTERY_SELECT_MENU)) {
            if (cur.getType() == Material.SHULKER_BOX && name.equals("§b§l盲盒")) {
                // 重置盲盒状态
                UUID uuid = p.getUniqueId();
                blindBoxContents.remove(uuid);
                blindBoxRevealed.remove(uuid);
                blindBoxRevealCount.remove(uuid);
                blindBoxOpening.remove(uuid);
                openBlindBoxMenu(p);
            } else if (cur.getType() == Material.NOTE_BLOCK && name.equals("§6§l大转盘")) {
                openBigWheelMenu(p);
            } else if (cur.getType() == Material.BARRIER && name.equals("§c⬅️ 返回银行")) {
                openJavaBankMenu(p);
            }
            return;
        }

        // 处理盲盒界面交互
        if (title.equals(BLIND_BOX_MENU)) {
            UUID uuid = p.getUniqueId();

            // 返回抽奖选择
            if (e.getSlot() == 49 && cur.getType() == Material.BARRIER && name.equals("§c⬅️ 返回抽奖")) {
                openLotterySelectMenu(p);
                return;
            }

            // 价格选择（slot 2-6）
            if (e.getSlot() >= 2 && e.getSlot() <= 5) {
                int idx = e.getSlot() - 2;
                if (idx >= 0 && idx < BLIND_BOX_PRICE_OPTIONS.length) {
                    blindBoxSelectedPrice.put(uuid, BLIND_BOX_PRICE_OPTIONS[idx]);
                    blindBoxContents.remove(uuid);
                    blindBoxRevealed.remove(uuid);
                    blindBoxRevealCount.remove(uuid);
                    blindBoxOpening.remove(uuid);
                    openBlindBoxMenu(p);
                    return;
                }
            }

            // 币种切换 - slot 15（AC币）
            if (e.getSlot() == 15 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                lotteryUseAppleCoin.put(uuid, false);
                blindBoxContents.remove(uuid);
                blindBoxRevealed.remove(uuid);
                blindBoxRevealCount.remove(uuid);
                blindBoxOpening.remove(uuid);
                openBlindBoxMenu(p);
                return;
            }
            // 币种切换 - slot 16（苹果币）
            if (e.getSlot() == 16 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                lotteryUseAppleCoin.put(uuid, true);
                blindBoxContents.remove(uuid);
                blindBoxRevealed.remove(uuid);
                blindBoxRevealCount.remove(uuid);
                blindBoxOpening.remove(uuid);
                openBlindBoxMenu(p);
                return;
            }

            // 连抽选择竖排右侧（slot 17, 26, 35, 44）
            int[] drawSlots = {17, 26, 35, 44};
            for (int i = 0; i < drawSlots.length; i++) {
                if (e.getSlot() == drawSlots[i]) {
                    if (i < DRAW_COUNTS.length) {
                        lotteryDrawCount.put(uuid, DRAW_COUNTS[i]);
                        blindBoxContents.remove(uuid);
                        blindBoxRevealed.remove(uuid);
                        blindBoxRevealCount.remove(uuid);
                        blindBoxOpening.remove(uuid);
                        openBlindBoxMenu(p);
                        return;
                    }
                }
            }

            // 开始抽按钮（slot 38）
            if (e.getSlot() == 38 && cur.getType() == Material.EMERALD_BLOCK && name.equals("§a§l开始抽！")) {
                startBlindBoxDraw(p);
                return;
            }

            // 点击盲盒开启
            for (int i = 0; i < 10; i++) {
                if (e.getSlot() == BLIND_BOX_SLOTS[i]) {
                    boolean[] revealed = blindBoxRevealed.get(uuid);
                    if (revealed == null) {
                        p.sendMessage("§c请先点击「开始抽！」按钮！");
                    } else if (revealed[i]) {
                        p.sendMessage("§c这个盲盒已经开启过了！");
                    } else {
                        revealBlindBox(p, i);
                    }
                    return;
                }
            }
            return;
        }

        // 处理大转盘界面交互
        if (title.equals(BIG_WHEEL_MENU)) {
            UUID uuid = p.getUniqueId();

            if (wheelSpinning.getOrDefault(uuid, false)) {
                if (e.getSlot() == 41 && cur.getType() == Material.ARROW && name.contains("跳过")) {
                    wheelSkipAnim.put(uuid, true);
                } else {
                    p.sendMessage("§c正在旋转中，请稍候...");
                }
                return;
            }

            // 返回抽奖选择
            if (e.getSlot() == 49 && cur.getType() == Material.BARRIER && name.equals("§c⬅️ 返回抽奖")) {
                openLotterySelectMenu(p);
                return;
            }

            // 跳过动画开关 - slot 41
            if (e.getSlot() == 41 && cur.getType() == Material.ARROW && name.contains("跳过")) {
                wheelSkipAnim.put(uuid, !wheelSkipAnim.getOrDefault(uuid, false));
                openBigWheelMenu(p);
                return;
            }

            // 旋转按钮 - slot 39（绿宝石块，中奖标左边）
            if (e.getSlot() == 39 && cur.getType() == Material.EMERALD_BLOCK && name.contains("开始旋转")) {
                spinBigWheel(p);
                return;
            }

            // 币种切换 - slot 52（AC币）
            if (e.getSlot() == 52 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                lotteryUseAppleCoin.put(uuid, false);
                openBigWheelMenu(p);
                return;
            }
            // 币种切换 - slot 53（苹果币）
            if (e.getSlot() == 53 && (cur.getType() == Material.APPLE || cur.getType() == Material.GOLDEN_APPLE)) {
                lotteryUseAppleCoin.put(uuid, true);
                openBigWheelMenu(p);
                return;
            }

            // 转盘等级（slot 50-51），点击50切换
            if (e.getSlot() == 50 && cur.getType() == Material.GOLD_BLOCK) {
                int curIdx = -1;
                int selPrice = wheelSelectedPrice.getOrDefault(uuid, WHEEL_PRICE_OPTIONS[0]);
                for (int i = 0; i < WHEEL_PRICE_OPTIONS.length; i++) {
                    if (WHEEL_PRICE_OPTIONS[i] == selPrice) { curIdx = i; break; }
                }
                if (curIdx < 0) curIdx = 0;
                int nextIdx = (curIdx + 1) % WHEEL_PRICE_OPTIONS.length;
                wheelSelectedPrice.put(uuid, WHEEL_PRICE_OPTIONS[nextIdx]);
                openBigWheelMenu(p);
                return;
            }

            // 抽数选择（slot 45-48）
            if (e.getSlot() >= 45 && e.getSlot() <= 48) {
                int idx = e.getSlot() - 45;
                if (idx >= 0 && idx < DRAW_COUNTS.length) {
                    lotteryDrawCount.put(uuid, DRAW_COUNTS[idx]);
                    openBigWheelMenu(p);
                    return;
                }
            }

            return;
        }

        // 处理大转盘结果界面
        if (title.equals(BIG_WHEEL_RESULT_MENU)) {
            if (e.getSlot() == 53 && cur.getType() == Material.BARRIER && name.equals("§c⬅️ 返回大转盘")) {
                openBigWheelMenu(p);
            }
            return;
        }

        // 处理子菜单返回（贷款、捐款）
        if (title.equals(LOAN_MENU) || title.equals(DONATION_MENU)) {
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

    // ====================== 抽奖选择菜单 ======================
    public void openLotterySelectMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, LOTTERY_SELECT_MENU);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7");
                filler.setItemMeta(meta);
            }
            inv.setItem(i, filler);
        }

        // 盲盒
        ItemStack blindBox = new ItemStack(Material.SHULKER_BOX);
        ItemMeta bbMeta = blindBox.getItemMeta();
        if (bbMeta != null) {
            bbMeta.setDisplayName("§b§l盲盒");
            bbMeta.setLore(List.of("§7打开10个盲盒，", "§7根据奖励大小变为不同物品！", "", "§a点击进入"));
            blindBox.setItemMeta(bbMeta);
        }
        inv.setItem(11, blindBox);

        // 大转盘
        ItemStack wheel = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta wMeta = wheel.getItemMeta();
        if (wMeta != null) {
            wMeta.setDisplayName("§6§l大转盘");
            wMeta.setLore(List.of("§7转动7x3大转盘，", "§7赢取丰厚奖励！", "", "§a点击进入"));
            wheel.setItemMeta(wMeta);
        }
        inv.setItem(15, wheel);

        // 返回银行
        ItemStack backBank = new ItemStack(Material.BARRIER);
        ItemMeta bbm = backBank.getItemMeta();
        if (bbm != null) {
            bbm.setDisplayName("§c⬅️ 返回银行");
            backBank.setItemMeta(bbm);
        }
        inv.setItem(22, backBank);

        p.openInventory(inv);
    }

    // ====================== 盲盒界面 ======================
    public void openBlindBoxMenu(Player p) {
        UUID uuid = p.getUniqueId();
        boolean useApple = lotteryUseAppleCoin.getOrDefault(uuid, false);
        String curName = useApple ? "苹果币" : "AC币";
        int balance = useApple
                ? plugin.economicSystem.getAppleCoins().getAppleCoins(p)
                : plugin.economicSystem.getAcCoins(p);
        int drawCnt = lotteryDrawCount.getOrDefault(uuid, 1);
        int selPrice = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
        int price = useApple ? Math.max(1, (int)(selPrice * 0.6)) : selPrice;
        int totalCost = drawCnt * price;

        Inventory inv = Bukkit.createInventory(null, 54, BLIND_BOX_MENU);
        // 边框
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7");
                filler.setItemMeta(meta);
            }
            inv.setItem(i, filler);
        }

        // 标题
        ItemStack titleItem = new ItemStack(Material.SHULKER_BOX);
        ItemMeta tMeta = titleItem.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName("§b§l✦ 盲盒 ✦");
            titleItem.setItemMeta(tMeta);
        }
        inv.setItem(4, titleItem);

        // 余额
        ItemStack balanceItem = new ItemStack(useApple ? Material.APPLE : Material.GOLD_NUGGET);
        ItemMeta balMeta = balanceItem.getItemMeta();
        if (balMeta != null) {
            balMeta.setDisplayName("§6余额: §e" + balance + " " + curName);
            balanceItem.setItemMeta(balMeta);
        }
        inv.setItem(0, balanceItem);

        // 价格选项（槽位2-6）
        for (int i = 0; i < BLIND_BOX_PRICE_OPTIONS.length; i++) {
            int optPrice = BLIND_BOX_PRICE_OPTIONS[i];
            boolean selected = optPrice == selPrice;
            int dispPrice = useApple ? Math.max(1, (int)(optPrice * 0.6)) : optPrice;
            ItemStack priceBtn = new ItemStack(selected ? Material.GOLD_BLOCK : Material.GOLD_NUGGET);
            ItemMeta pMeta = priceBtn.getItemMeta();
            if (pMeta != null) {
                pMeta.setDisplayName((selected ? "§a▶ " : "§7") + BLIND_BOX_PRICE_NAMES[i] + " §e" + optPrice + " " + curName + "/个");
                pMeta.setLore(List.of(selected ? "§a当前选中" : "§e点击选择"));
                priceBtn.setItemMeta(pMeta);
            }
            inv.setItem(2 + i, priceBtn);
        }

        // 10个盲盒（slot 10-14, 19-23）
        for (int i = 0; i < 10; i++) {
            ItemStack box = new ItemStack(Material.SHULKER_BOX);
            ItemMeta boxMeta = box.getItemMeta();
            if (boxMeta != null) {
                boxMeta.setDisplayName("§" + (i < 5 ? "a" : "b") + "盲盒 #" + (i + 1));
                boxMeta.setLore(List.of("§7选择抽数和价格后点击开始"));
                box.setItemMeta(boxMeta);
            }
            inv.setItem(BLIND_BOX_SLOTS[i], box);
        }

        // 币种切换 - slot 15（AC币）
        ItemStack coinBtn = new ItemStack(useApple ? Material.GOLDEN_APPLE : Material.APPLE);
        ItemMeta cMeta = coinBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName((useApple ? "§7" : "§a▶ ") + "AC币");
            cMeta.setLore(List.of(useApple ? "§e点击切换为AC币" : "§a当前使用"));
            coinBtn.setItemMeta(cMeta);
        }
        inv.setItem(15, coinBtn);

        // 币种切换 - slot 16（苹果币）
        ItemStack coinBtn2 = new ItemStack(useApple ? Material.APPLE : Material.GOLDEN_APPLE);
        ItemMeta cMeta2 = coinBtn2.getItemMeta();
        if (cMeta2 != null) {
            cMeta2.setDisplayName((useApple ? "§a▶ " : "§7") + "苹果币");
            cMeta2.setLore(List.of(useApple ? "§a当前使用" : "§e点击切换为苹果币"));
            coinBtn2.setItemMeta(cMeta2);
        }
        inv.setItem(16, coinBtn2);

        // 连抽选择竖排右侧（slot 17, 26, 35, 44）
        for (int i = 0; i < DRAW_COUNTS.length; i++) {
            int cnt = DRAW_COUNTS[i];
            int cost = cnt * price;
            boolean selected = cnt == drawCnt;
            ItemStack drawBtn = new ItemStack(selected ? Material.GOLD_BLOCK : Material.GOLD_INGOT);
            ItemMeta dMeta = drawBtn.getItemMeta();
            if (dMeta != null) {
                dMeta.setDisplayName((selected ? "§a▶ " : "§7") + cnt + " 连抽（" + cost + " " + curName + "）");
                dMeta.setLore(List.of(selected ? "§a当前选中" : "§e点击选择"));
                drawBtn.setItemMeta(dMeta);
            }
            inv.setItem(DRAW_COUNT_SLOTS[i], drawBtn);
        }

        // 奖池介绍（slot 29-33区域）
        StringBuilder introLore = new StringBuilder("§7可能抽到：");
        int bbTierForIntro = -1;
        for (int t = 0; t < BLIND_BOX_PRICE_OPTIONS.length; t++) {
            if (BLIND_BOX_PRICE_OPTIONS[t] == selPrice) { bbTierForIntro = t; break; }
        }
        if (bbTierForIntro < 0) bbTierForIntro = 0;
        for (int i = 0; i < BLIND_BOX_REWARDS.length; i++) {
            String color = getBlindBoxColor(BLIND_BOX_REWARDS[i]);
            String name = getBlindBoxName(BLIND_BOX_REWARDS[i]);
            int payAmount = getBlindBoxWinAmount(bbTierForIntro, i);
            if (lotteryUseAppleCoin.getOrDefault(uuid, false)) payAmount = Math.max(1, (int)(payAmount * 0.6));
            introLore.append(color).append(name).append("§7(").append(payAmount > 0 ? "§e+" + payAmount : "§c0").append("§7) ");
        }
        ItemStack introItem = new ItemStack(Material.BOOK);
        ItemMeta iMeta = introItem.getItemMeta();
        if (iMeta != null) {
            iMeta.setDisplayName("§6奖池介绍");
            iMeta.setLore(List.of(
                    introLore.toString(),
                    "",
                    "§7当前: " + BLIND_BOX_PRICE_NAMES[java.util.Arrays.binarySearch(BLIND_BOX_PRICE_OPTIONS, selPrice)] + " §e" + price + " " + curName + "/个",
                    "§7" + drawCnt + "连抽总价: §e" + totalCost + " " + curName
            ));
            introItem.setItemMeta(iMeta);
        }
        inv.setItem(31, introItem);

        // 开始抽按钮（slot 38）
        ItemStack startBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = startBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§a§l开始抽！");
            sMeta.setLore(List.of(
                    "§7价格: §e" + price + " " + curName + "/个",
                    "§7抽数: §e" + drawCnt + " 连抽",
                    "§7总价: §e" + totalCost + " " + curName,
                    "",
                    "§e点击开始抽取盲盒！"
            ));
            startBtn.setItemMeta(sMeta);
        }
        inv.setItem(38, startBtn);

        // 返回抽奖
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c⬅️ 返回抽奖");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    // ====================== 盲盒开始抽 ======================
    private void startBlindBoxDraw(Player p) {
        UUID uuid = p.getUniqueId();
        int drawCnt = lotteryDrawCount.getOrDefault(uuid, 1);
        int selPrice = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
        int price = lotteryUseAppleCoin.getOrDefault(uuid, false) ? Math.max(1, (int)(selPrice * 0.6)) : selPrice;
        int totalCost = drawCnt * price;
        String curName = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";

        if (blindBoxContents.containsKey(uuid)) {
            p.sendMessage("§c已经开始了，点击盲盒开启！");
            return;
        }

        if (getLotteryBalance(p) < totalCost) {
            p.sendMessage("§c" + curName + "不足！需要 " + totalCost + " " + curName);
            return;
        }

        if (!removeLotteryCurrency(p, totalCost)) {
            p.sendMessage("§c" + curName + "扣除失败！");
            return;
        }

        // 生成10个盲盒内容
        // 根据选中价格确定分档
        int bbSelPrice = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
        int bbTierIdx = -1;
        for (int t = 0; t < BLIND_BOX_PRICE_OPTIONS.length; t++) {
            if (BLIND_BOX_PRICE_OPTIONS[t] == bbSelPrice) { bbTierIdx = t; break; }
        }
        if (bbTierIdx < 0) bbTierIdx = 0;

        Material[] contents = new Material[10];
        for (int i = 0; i < 10; i++) {
            contents[i] = getWeightedBlindBoxReward(bbTierIdx);
        }
        blindBoxContents.put(uuid, contents);
        blindBoxRevealed.put(uuid, new boolean[10]);
        blindBoxRevealCount.put(uuid, 0);

        p.sendMessage("§a" + curName + "已扣除 " + totalCost + ", 开始抽取 " + drawCnt + " 个盲盒！");

        // 10连自动全部开启
        if (drawCnt == 10) {
            blindBoxOpening.put(uuid, true);
            final int[] current = {0};
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (current[0] >= 10) {
                        blindBoxOpening.put(uuid, false);
                        p.sendMessage("§a§l全部开启完毕！");
                        this.cancel();
                        return;
                    }
                    // 找未开的盒子直接开（不走revealBlindBox，避免blindBoxOpening冲突）
                    boolean[] rev = blindBoxRevealed.get(uuid);
                    for (int i = 0; i < 10; i++) {
                        if (!rev[i]) {
                            Material reward = contents[i];
                            rev[i] = true;

                            int selP = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
                            int pPrice = lotteryUseAppleCoin.getOrDefault(uuid, false) ? Math.max(1, (int)(selP * 0.6)) : selP;
                            int rIdx = getBlindBoxRewardIndex(reward);
                            int winAmount = getBlindBoxWinAmount(uuid, rIdx, lotteryUseAppleCoin.getOrDefault(uuid, false));

                            if (winAmount > 0) {
                                addLotteryCurrency(p, winAmount);
                            }

                            // 更新界面
                            Inventory inv = p.getOpenInventory().getTopInventory();
                            if (p.getOpenInventory().getTitle().equals(BLIND_BOX_MENU)) {
                                ItemStack revealItem = new ItemStack(reward);
                                ItemMeta rMeta = revealItem.getItemMeta();
                                if (rMeta != null) {
                                    String color = getBlindBoxColor(reward);
                                    String name = getBlindBoxName(reward);
                                    rMeta.setDisplayName(color + "§l" + name);
                                    List<String> lore = new ArrayList<>();
                                    lore.add("§7第 #" + (i + 1) + " 个盲盒");
                                    if (winAmount > 0) {
                                        String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                                        lore.add("§a赢得: §e+" + winAmount + " " + cur);
                                    } else {
                                        lore.add("§c未中奖");
                                    }
                                    rMeta.setLore(lore);
                                    revealItem.setItemMeta(rMeta);
                                }
                                inv.setItem(BLIND_BOX_SLOTS[i], revealItem);

                                // 更新余额
                                ItemStack balItem = inv.getItem(0);
                                if (balItem != null && balItem.hasItemMeta()) {
                                    String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                                    int newBalance = lotteryUseAppleCoin.getOrDefault(uuid, false)
                                            ? plugin.economicSystem.getAppleCoins().getAppleCoins(p)
                                            : plugin.economicSystem.getAcCoins(p);
                                    ItemMeta balMeta = balItem.getItemMeta();
                                    balMeta.setDisplayName("§6余额: §e" + newBalance + " " + cur);
                                    balItem.setItemMeta(balMeta);
                                }
                            }

                            String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                            if (winAmount > 0) {
                                p.sendMessage("§a盲盒 #" + (i + 1) + " 开出 §e" + getBlindBoxName(reward) + "§a，赢得 §e+" + winAmount + " " + cur);
                            } else {
                                p.sendMessage("§7盲盒 #" + (i + 1) + " 开出 " + getBlindBoxName(reward) + "§7，未中奖");
                            }

                            current[0]++;
                            break;
                        }
                    }
                }
            }.runTaskTimer(plugin, 5L, 3L);
        } else {
            // 非10连，更新界面让玩家手动点
            Inventory inv = p.getOpenInventory().getTopInventory();
            if (p.getOpenInventory().getTitle().equals(BLIND_BOX_MENU)) {
                // 刷新盲盒描述
                for (int i = 0; i < 10; i++) {
                    ItemStack box = inv.getItem(BLIND_BOX_SLOTS[i]);
                    if (box != null && box.hasItemMeta()) {
                        ItemMeta meta = box.getItemMeta();
                        meta.setLore(List.of("§a点击开启"));
                        box.setItemMeta(meta);
                    }
                }
                // 更新余额
                ItemStack balItem = inv.getItem(0);
                if (balItem != null && balItem.hasItemMeta()) {
                    int newBalance = getLotteryBalance(p);
                    ItemMeta balMeta = balItem.getItemMeta();
                    balMeta.setDisplayName("§6余额: §e" + newBalance + " " + curName);
                    balItem.setItemMeta(balMeta);
                }
            }
            p.sendMessage("§a请点击盲盒逐个开启！");
        }
    }

    // ====================== 盲盒开盒逻辑（非10连手动点一个开一个）======================
    private void revealBlindBox(Player p, int boxIndex) {
        UUID uuid = p.getUniqueId();
        if (blindBoxOpening.getOrDefault(uuid, false)) {
            p.sendMessage("§c正在开启中，请稍候...");
            return;
        }

        // 首次开盒时生成内容
        if (!blindBoxContents.containsKey(uuid)) {
            p.sendMessage("§c请先点击「开始抽！」按钮！");
            return;
        }

        Material[] contents = blindBoxContents.get(uuid);
        boolean[] revealed = blindBoxRevealed.get(uuid);
        int revealCount = blindBoxRevealCount.getOrDefault(uuid, 0);
        int totalDraw = lotteryDrawCount.getOrDefault(uuid, 1);

        if (revealCount >= totalDraw) {
            p.sendMessage("§c已完成所有抽数！");
            return;
        }
        if (revealed[boxIndex]) {
            p.sendMessage("§c这个盲盒已经开启过了！");
            return;
        }

        int targetIdx = boxIndex;

        blindBoxOpening.put(uuid, true);
        Material reward = contents[targetIdx];
        revealed[targetIdx] = true;

        int selPrice = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
        int price = lotteryUseAppleCoin.getOrDefault(uuid, false) ? Math.max(1, (int)(selPrice * 0.6)) : selPrice;
        int rewardIdx = getBlindBoxRewardIndex(reward);
        int winAmount = getBlindBoxWinAmount(uuid, rewardIdx, lotteryUseAppleCoin.getOrDefault(uuid, false));

        if (winAmount > 0) {
            addLotteryCurrency(p, winAmount);
        }

        // 更新界面
        Inventory inv = p.getOpenInventory().getTopInventory();
        if (p.getOpenInventory().getTitle().equals(BLIND_BOX_MENU)) {
            ItemStack revealItem = new ItemStack(reward);
            ItemMeta rMeta = revealItem.getItemMeta();
            if (rMeta != null) {
                String color = getBlindBoxColor(reward);
                String name = getBlindBoxName(reward);
                rMeta.setDisplayName(color + "§l" + name);
                List<String> lore = new ArrayList<>();
                lore.add("§7第 #" + (targetIdx + 1) + " 个盲盒");
                if (winAmount > 0) {
                    String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                    lore.add("§a赢得: §e+" + winAmount + " " + cur);
                } else {
                    lore.add("§c未中奖");
                }
                rMeta.setLore(lore);
                revealItem.setItemMeta(rMeta);
            }
            inv.setItem(BLIND_BOX_SLOTS[targetIdx], revealItem);

            // 更新余额显示
            ItemStack balItem = inv.getItem(0);
            if (balItem != null && balItem.hasItemMeta()) {
                String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                int newBalance = lotteryUseAppleCoin.getOrDefault(uuid, false)
                        ? plugin.economicSystem.getAppleCoins().getAppleCoins(p)
                        : plugin.economicSystem.getAcCoins(p);
                ItemMeta balMeta = balItem.getItemMeta();
                balMeta.setDisplayName("§6余额: §e" + newBalance + " " + cur);
                balItem.setItemMeta(balMeta);
            }
        }

        int newCount = revealCount + 1;
        blindBoxRevealCount.put(uuid, newCount);
        blindBoxOpening.put(uuid, false);

        // 发送消息
        if (winAmount > 0) {
            String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
            p.sendMessage("§a盲盒 #" + (targetIdx + 1) + " 开出 §e" + getBlindBoxName(reward) + "§a，赢得 §e+" + winAmount + " " + cur);
        } else {
            p.sendMessage("§7盲盒 #" + (targetIdx + 1) + " 开出 " + getBlindBoxName(reward) + "§7，未中奖");
        }

        // 全部开完
        if (newCount >= totalDraw) {
            p.sendMessage("§a§l全部开启完毕！");
        }
    }

    // ====================== 刷新盲盒界面 ======================
    private void refreshBlindBoxDisplay(Player p, Inventory inv) {
        UUID uuid = p.getUniqueId();
        Material[] contents = blindBoxContents.get(uuid);
        boolean[] revealed = blindBoxRevealed.get(uuid);
        if (contents == null || revealed == null) return;

        for (int i = 0; i < 10; i++) {
            if (revealed[i]) {
                Material reward = contents[i];
                ItemStack revealItem = new ItemStack(reward);
                ItemMeta rMeta = revealItem.getItemMeta();
                if (rMeta != null) {
                    String color = getBlindBoxColor(reward);
                    String name = getBlindBoxName(reward);
                    rMeta.setDisplayName(color + "§l" + name);
                    int selBBPrice2 = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
                    int price = lotteryUseAppleCoin.getOrDefault(uuid, false)
                            ? Math.max(1, (int)(selBBPrice2 * 0.6)) : selBBPrice2;
                    int rewardIdx = getBlindBoxRewardIndex(reward);
                    int winAmount = getBlindBoxWinAmount(uuid, rewardIdx, lotteryUseAppleCoin.getOrDefault(uuid, false));
                    List<String> lore = new ArrayList<>();
                    lore.add("§7第 #" + (i + 1) + " 个盲盒");
                    if (winAmount > 0) {
                        String cur = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";
                        lore.add("§a赢得: §e+" + winAmount + " " + cur);
                    } else {
                        lore.add("§c未中奖");
                    }
                    rMeta.setLore(lore);
                    revealItem.setItemMeta(rMeta);
                }
                inv.setItem(BLIND_BOX_SLOTS[i], revealItem);
            }
        }
    }

    // ====================== 盲盒辅助方法 ======================
    private Material getWeightedBlindBoxReward() {
        return getWeightedBlindBoxReward(0);
    }

    private Material getWeightedBlindBoxReward(int tierIdx) {
        int[] weights = (tierIdx >= 0 && tierIdx < BLIND_BOX_TIER_WEIGHTS.length) ? BLIND_BOX_TIER_WEIGHTS[tierIdx] : BLIND_BOX_WEIGHTS;
        int total = 0;
        for (int w : weights) total += w;
        int roll = new java.util.Random().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < BLIND_BOX_REWARDS.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return BLIND_BOX_REWARDS[i];
        }
        return BLIND_BOX_REWARDS[0];
    }

    private int getBlindBoxRewardIndex(Material mat) {
        for (int i = 0; i < BLIND_BOX_REWARDS.length; i++) {
            if (BLIND_BOX_REWARDS[i] == mat) return i;
        }
        return -1;
    }

    // 获取盲盒单次固定赢取金额（AC币），苹果币调用方自行×0.6
    private int getBlindBoxWinAmount(int tierIdx, int rewardIdx) {
        if (tierIdx < 0 || tierIdx >= BLIND_BOX_TIER_AMOUNTS.length) return 0;
        if (rewardIdx < 0 || rewardIdx >= BLIND_BOX_TIER_AMOUNTS[tierIdx].length) return 0;
        return BLIND_BOX_TIER_AMOUNTS[tierIdx][rewardIdx];
    }

    private int getBlindBoxWinAmount(UUID uuid, int rewardIdx, boolean useApple) {
        int price = blindBoxSelectedPrice.getOrDefault(uuid, BLIND_BOX_PRICE_OPTIONS[0]);
        int tierIdx = -1;
        for (int t = 0; t < BLIND_BOX_PRICE_OPTIONS.length; t++) {
            if (BLIND_BOX_PRICE_OPTIONS[t] == price) { tierIdx = t; break; }
        }
        if (tierIdx < 0) tierIdx = 0;
        int amount = getBlindBoxWinAmount(tierIdx, rewardIdx);
        return useApple ? Math.max(1, (int)(amount * 0.6)) : amount;
    }

    private String getBlindBoxColor(Material mat) {
        if (mat == Material.BARRIER) return "§8";
        if (mat == Material.COBBLESTONE) return "§7";
        if (mat == Material.COAL_ORE) return "§8";
        if (mat == Material.IRON_ORE) return "§7";
        if (mat == Material.COPPER_ORE) return "§6";
        if (mat == Material.GOLD_ORE) return "§e";
        if (mat == Material.LAPIS_ORE) return "§9";
        if (mat == Material.REDSTONE_ORE) return "§c";
        if (mat == Material.DIAMOND_ORE) return "§b";
        if (mat == Material.EMERALD_ORE) return "§a";
        if (mat == Material.ANCIENT_DEBRIS) return "§4";
        return "§f";
    }

    private String getBlindBoxName(Material mat) {
        if (mat == Material.BARRIER) return "屏障";
        if (mat == Material.COBBLESTONE) return "圆石";
        if (mat == Material.COAL_ORE) return "煤矿";
        if (mat == Material.IRON_ORE) return "铁矿";
        if (mat == Material.COPPER_ORE) return "铜矿";
        if (mat == Material.GOLD_ORE) return "金矿";
        if (mat == Material.LAPIS_ORE) return "青金石矿";
        if (mat == Material.REDSTONE_ORE) return "红石矿";
        if (mat == Material.DIAMOND_ORE) return "钻石矿";
        if (mat == Material.EMERALD_ORE) return "绿宝石矿";
        if (mat == Material.ANCIENT_DEBRIS) return "远古残骸";
        return "未知";
    }

    // ====================== 大转盘界面 ======================
    public void openBigWheelMenu(Player p) {
        UUID uuid = p.getUniqueId();
        boolean useApple = lotteryUseAppleCoin.getOrDefault(uuid, false);
        String curName = useApple ? "苹果币" : "AC币";
        int balance = useApple
                ? plugin.economicSystem.getAppleCoins().getAppleCoins(p)
                : plugin.economicSystem.getAcCoins(p);
        int drawCnt = lotteryDrawCount.getOrDefault(uuid, 1);
        int selPrice = wheelSelectedPrice.getOrDefault(uuid, WHEEL_PRICE_OPTIONS[0]);
        int price = useApple ? Math.max(1, (int)(selPrice * 0.6)) : selPrice;
        int totalCost = drawCnt * price;

        Inventory inv = Bukkit.createInventory(null, 54, BIG_WHEEL_MENU);
        // 边框
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7");
                filler.setItemMeta(meta);
            }
            inv.setItem(i, filler);
        }

        // 标题
        ItemStack titleItem = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta tMeta = titleItem.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName("§6§l✦ 大转盘 ✦");
            titleItem.setItemMeta(tMeta);
        }
        inv.setItem(4, titleItem);

        // 余额
        ItemStack balanceItem = new ItemStack(useApple ? Material.APPLE : Material.GOLD_NUGGET);
        ItemMeta balMeta = balanceItem.getItemMeta();
        if (balMeta != null) {
            balMeta.setDisplayName("§6余额: §e" + balance + " " + curName);
            balanceItem.setItemMeta(balMeta);
        }
        inv.setItem(0, balanceItem);

        // 7x3 轮盘：上行7格+中行两边+下行7格
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 7; col++) {
                int slot = WHEEL_DISPLAY_SLOTS[row][col];
                if (slot < 0) continue; // 中行中间5格空
                // 根据当前选中的等级显示对应权重的奖品
                int wheelTierForDisplay = -1;
                int selWPrice = wheelSelectedPrice.getOrDefault(uuid, WHEEL_PRICE_OPTIONS[0]);
                for (int t = 0; t < WHEEL_PRICE_OPTIONS.length; t++) {
                    if (WHEEL_PRICE_OPTIONS[t] == selWPrice) { wheelTierForDisplay = t; break; }
                }
                if (wheelTierForDisplay < 0) wheelTierForDisplay = 0;
                Material randomPrize = getWeightedWheelPrize(wheelTierForDisplay);
                ItemStack prizeItem = new ItemStack(randomPrize);
                ItemMeta pMeta = prizeItem.getItemMeta();
                if (pMeta != null) {
                    pMeta.setDisplayName(getWheelPrizeColor(randomPrize) + "§l" + getWheelPrizeName(randomPrize));
                    if (row == 1) {
                        pMeta.setLore(List.of("§e中奖位"));
                    }
                    prizeItem.setItemMeta(pMeta);
                }
                inv.setItem(slot, prizeItem);
            }
        }

        // 中奖行标（slot 40）
        ItemStack indicator = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta indMeta = indicator.getItemMeta();
        if (indMeta != null) {
            indMeta.setDisplayName("§6✦ 中奖行 ✦");
            indicator.setItemMeta(indMeta);
        }
        inv.setItem(40, indicator);

        // 跳过动画开关（slot 41）
        boolean skipEnabled = wheelSkipAnim.getOrDefault(uuid, false);
        ItemStack skipBtn = new ItemStack(Material.ARROW);
        ItemMeta skipMeta = skipBtn.getItemMeta();
        if (skipMeta != null) {
            skipMeta.setDisplayName(skipEnabled ? "§a⏩ 跳过动画: 已开启" : "§8⏩ 跳过动画: 已关闭");
            skipBtn.setItemMeta(skipMeta);
        }
        inv.setItem(41, skipBtn);

        // 开启按钮（slot 39，中奖行标的左边）
        ItemStack spinBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta spinMeta = spinBtn.getItemMeta();
        if (spinMeta != null) {
            spinMeta.setDisplayName("§a§l🎡 开始旋转（" + totalCost + " " + curName + "）");
            spinMeta.setLore(List.of("§e" + price + " " + curName + " × " + drawCnt + "次 = " + totalCost + " " + curName));
            spinBtn.setItemMeta(spinMeta);
        }
        inv.setItem(39, spinBtn);

        // 币种切换 - slot 52（AC币）slot 53（苹果币）右下角
        ItemStack acBtn = new ItemStack(useApple ? Material.GOLDEN_APPLE : Material.APPLE);
        ItemMeta acMeta = acBtn.getItemMeta();
        if (acMeta != null) {
            acMeta.setDisplayName((useApple ? "§7" : "§a▶ ") + "AC币");
            acMeta.setLore(List.of(useApple ? "§e点击切换为AC币" : "§a当前使用"));
            acBtn.setItemMeta(acMeta);
        }
        inv.setItem(52, acBtn);

        ItemStack appleBtn = new ItemStack(useApple ? Material.APPLE : Material.GOLDEN_APPLE);
        ItemMeta apMeta = appleBtn.getItemMeta();
        if (apMeta != null) {
            apMeta.setDisplayName((useApple ? "§a▶ " : "§7") + "苹果币");
            apMeta.setLore(List.of(useApple ? "§a当前使用" : "§e点击切换为苹果币"));
            appleBtn.setItemMeta(apMeta);
        }
        inv.setItem(53, appleBtn);

        // 抽数选择（最下面一行左侧 slot 45-48）
        int[] drawSlots = {45, 46, 47, 48};
        for (int i = 0; i < DRAW_COUNTS.length; i++) {
            int cnt = DRAW_COUNTS[i];
            int cost = cnt * price;
            boolean selected = cnt == drawCnt;
            ItemStack drawBtn = new ItemStack(selected ? Material.GOLD_BLOCK : Material.GOLD_INGOT);
            ItemMeta dMeta = drawBtn.getItemMeta();
            if (dMeta != null) {
                dMeta.setDisplayName((selected ? "§a▶ " : "§7") + cnt + " 连抽（" + cost + " " + curName + "）");
                dMeta.setLore(List.of(selected ? "§a当前选中" : "§e点击选择"));
                drawBtn.setItemMeta(dMeta);
            }
            inv.setItem(drawSlots[i], drawBtn);
        }

        // 转盘等级（slot 50-51），点击切换下个等级
        int curLevelIdx = -1;
        for (int i = 0; i < WHEEL_PRICE_OPTIONS.length; i++) {
            if (WHEEL_PRICE_OPTIONS[i] == selPrice) { curLevelIdx = i; break; }
        }
        if (curLevelIdx < 0) curLevelIdx = 0;
        int nextLevelIdx = (curLevelIdx + 1) % WHEEL_PRICE_OPTIONS.length;
        int nextOptPrice = WHEEL_PRICE_OPTIONS[nextLevelIdx];
        int nextDispPrice = useApple ? Math.max(1, (int)(nextOptPrice * 0.6)) : nextOptPrice;
        ItemStack levelBtn = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta lMeta = levelBtn.getItemMeta();
        if (lMeta != null) {
            int dispPrice = useApple ? Math.max(1, (int)(selPrice * 0.6)) : selPrice;
            lMeta.setDisplayName("§a▶ " + WHEEL_PRICE_NAMES[curLevelIdx] + " §e" + dispPrice + " " + curName + "/转");
            lMeta.setLore(List.of("§e点击切换 " + WHEEL_PRICE_NAMES[nextLevelIdx] + " " + nextDispPrice + " " + curName + "/转"));
            levelBtn.setItemMeta(lMeta);
        }
        inv.setItem(50, levelBtn);
        ItemStack levelInfo = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta liMeta = levelInfo.getItemMeta();
        if (liMeta != null) {
            liMeta.setDisplayName("§7当前: " + WHEEL_PRICE_NAMES[curLevelIdx]);
            liMeta.setLore(List.of("§7等级 " + (curLevelIdx + 1) + "/" + WHEEL_PRICE_OPTIONS.length));
            levelInfo.setItemMeta(liMeta);
        }
        inv.setItem(51, levelInfo);

        // 返回抽奖
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c⬅️ 返回抽奖");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    // ====================== 大转盘旋转逻辑 ======================
    private void spinBigWheel(Player p) {
        UUID uuid = p.getUniqueId();
        if (wheelSpinning.getOrDefault(uuid, false)) {
            p.sendMessage("§c正在旋转中，请稍候...");
            return;
        }

        // 决定中奖物品（中间行第3列固定为中奖），使用分档权重
        int wheelTierIdx = -1;
        int selWPrice2 = wheelSelectedPrice.getOrDefault(uuid, WHEEL_PRICE_OPTIONS[0]);
        for (int t = 0; t < WHEEL_PRICE_OPTIONS.length; t++) {
            if (WHEEL_PRICE_OPTIONS[t] == selWPrice2) { wheelTierIdx = t; break; }
        }
        if (wheelTierIdx < 0) wheelTierIdx = 0;
        int drawCnt = lotteryDrawCount.getOrDefault(uuid, DRAW_COUNTS[0]);
        int price = lotteryUseAppleCoin.getOrDefault(uuid, false) ? Math.max(1, (int)(selWPrice2 * 0.6)) : selWPrice2;
        int totalCost = drawCnt * price;
        String curName = lotteryUseAppleCoin.getOrDefault(uuid, false) ? "苹果币" : "AC币";

        if (getLotteryBalance(p) < totalCost) {
            p.sendMessage("§c" + curName + "不足！需要 " + totalCost + " " + curName);
            return;
        }

        if (!removeLotteryCurrency(p, totalCost)) {
            p.sendMessage("§c" + curName + "扣除失败！");
            return;
        }

        wheelSpinning.put(uuid, true);
        Inventory inv = p.getOpenInventory().getTopInventory();
        if (!p.getOpenInventory().getTitle().equals(BIG_WHEEL_MENU)) {
            wheelSpinning.put(uuid, false);
            return;
        }

        java.util.Random rand = new java.util.Random();
        // 生成所有结果
        List<Material[]> allResults = new ArrayList<>();
        int[] winAccum = {0};
        StringBuilder detailText = new StringBuilder();

        for (int i = 0; i < drawCnt; i++) {
            Material[] wheelState = new Material[7];
            // 决定中奖物品（中间行第3列固定为中奖），使用分档权重
            Material winPrize = getWeightedWheelPrize(wheelTierIdx);
            wheelState[3] = winPrize;  // 第4列为中奖列

            // 其他位置随机填充
            for (int j = 0; j < 7; j++) {
                if (j != 3) {
                    wheelState[j] = getWeightedWheelPrize(wheelTierIdx);
                }
            }

            allResults.add(wheelState);
            int rewardIdx = getWheelPrizeIndex(winPrize);
            int winAmount = getWheelWinAmount(uuid, rewardIdx, lotteryUseAppleCoin.getOrDefault(uuid, false));
            if (winAmount > 0) {
                addLotteryCurrency(p, winAmount);
                winAccum[0] += winAmount;
            }
            detailText.append("§7第 §e").append(i + 1).append(" §7次: ")
                    .append(getWheelPrizeColor(winPrize)).append(getWheelPrizeName(winPrize))
                    .append(winAmount > 0 ? " §a+" + winAmount + " " + curName : " §c未中奖")
                    .append("\n");
        }

        final Material[][] finalResults = allResults.toArray(new Material[0][7]);
        boolean skipAnim = wheelSkipAnim.getOrDefault(uuid, false);

        if (skipAnim) {
            // 跳过动画，直接打开结果界面
            wheelSpinning.put(uuid, false);
            openBigWheelResult(p, finalResults, drawCnt, totalCost, winAccum[0], price, curName);
            return;
        }

        // 逐个抽：每轮随机5-10帧旋转，显示结果+光效，暂停0.6s
        final int[] drawIndex = {0};
        final java.util.Random rng = new java.util.Random();

        // 开始第一轮
        processNextWheelDraw(p, inv, finalResults, drawIndex, drawCnt, totalCost, winAccum[0], price, curName, uuid, rng);
    }

    private void processNextWheelDraw(Player p, Inventory inv, Material[][] finalResults, int[] drawIndex,
                                       int drawCnt, int totalCost, int totalWin, int price, String curName,
                                       UUID uuid, java.util.Random rng) {
        if (drawIndex[0] >= drawCnt) {
            wheelSpinning.put(uuid, false);
            openBigWheelResult(p, finalResults, drawCnt, totalCost, totalWin, price, curName);
            return;
        }

        if (wheelSkipAnim.getOrDefault(uuid, false)) {
            showWheelResult(inv, finalResults[drawIndex[0]]);
            drawIndex[0]++;
            processNextWheelDraw(p, inv, finalResults, drawIndex, drawCnt, totalCost, totalWin, price, curName, uuid, rng);
            return;
        }

        int totalFrames = 5 + rng.nextInt(6); // 5~10帧
        final int[] frame = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 安全检查：玩家是否还在大转盘界面
                    String curTitle = null;
                    try {
                        if (p.getOpenInventory() != null) curTitle = p.getOpenInventory().getTitle();
                    } catch (Exception ignored) {}

                    if (curTitle == null || !curTitle.equals(BIG_WHEEL_MENU)) {
                        wheelSpinning.put(uuid, false);
                        this.cancel();
                        return;
                    }

                    if (wheelSkipAnim.getOrDefault(uuid, false)) {
                        showWheelResult(inv, finalResults[drawIndex[0]]);
                        this.cancel();
                        drawIndex[0]++;
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                                processNextWheelDraw(p, inv, finalResults, drawIndex, drawCnt, totalCost, totalWin, price, curName, uuid, rng), 1L);
                        return;
                    }

                    if (frame[0] < totalFrames) {
                        // 旋转帧
                        for (int col = 0; col < 7; col++) {
                            for (int row = 0; row < 3; row++) {
                                int slot = WHEEL_DISPLAY_SLOTS[row][col];
                                if (slot < 0) continue;
                                ItemStack item = inv.getItem(slot);
                                if (item != null) {
                                    Material randomMat = getWeightedWheelPrize();
                                    item.setType(randomMat);
                                    ItemMeta meta = item.getItemMeta();
                                    if (meta != null) {
                                        meta.setDisplayName(getWheelPrizeColor(randomMat) + "§l" + getWheelPrizeName(randomMat));
                                        meta.setLore(row == 1 ? List.of("§e中奖行") : null);
                                        meta.removeEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING);
                                        item.setItemMeta(meta);
                                    }
                                }
                            }
                        }
                        frame[0]++;
                    } else {
                        // 旋转结束，显示结果+光效
                        this.cancel();
                        showWheelResult(inv, finalResults[drawIndex[0]]);
                        // 中奖行第3列 slot 可能为-1（中间空），取上行第3列 slot=13
                        int winSlot = WHEEL_DISPLAY_SLOTS[0][3];
                        ItemStack winItem = inv.getItem(winSlot);
                        if (winItem != null) {
                            ItemMeta wMeta = winItem.getItemMeta();
                            if (wMeta != null) {
                                wMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                                wMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                                winItem.setItemMeta(wMeta);
                            }
                        }
                        drawIndex[0]++;
                        // 暂停 12 ticks (0.6s) 后下一轮
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                                processNextWheelDraw(p, inv, finalResults, drawIndex, drawCnt, totalCost, totalWin, price, curName, uuid, rng), 12L);
                    }
                } catch (Exception ex) {
                    // 异常保护：防止异常导致wheelSpinning永久锁死
                    wheelSpinning.put(uuid, false);
                    this.cancel();
                    try {
                        p.sendMessage("§c大转盘旋转时发生异常，请重试");
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // 获取偏移后的轮盘奖品（用于3行显示）
    private Material getOffsetWheelPrize(Material center, int rowOffset) {
        int centerIdx = getWheelPrizeIndex(center);
        if (centerIdx < 0) return center;
        int offset;
        switch (rowOffset) {
            case 0: offset = -1; break;  // 上一行
            case 1: offset = 0; break;   // 中间（中奖行）
            case 2: offset = 1; break;   // 下一行
            default: return center;
        }
        int idx = (centerIdx + offset + WHEEL_PRIZES.length) % WHEEL_PRIZES.length;
        return WHEEL_PRIZES[idx];
    }

    // 在7x3轮盘上显示一轮结果
    private void showWheelResult(Inventory inv, Material[] wheelState) {
        for (int col = 0; col < 7; col++) {
            Material centerMat = wheelState[col];
            for (int row = 0; row < 3; row++) {
                int slot = WHEEL_DISPLAY_SLOTS[row][col];
                if (slot < 0) continue;
                ItemStack item = inv.getItem(slot);
                if (item != null) {
                    Material offsetMat = getOffsetWheelPrize(centerMat, row);
                    item.setType(offsetMat);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(getWheelPrizeColor(offsetMat) + "§l" + getWheelPrizeName(offsetMat));
                        if (row == 1) {
                            meta.setLore(List.of("§e中奖行"));
                        } else {
                            meta.setLore(null);
                        }
                        meta.removeEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
    }

    // ====================== 大转盘结果界面 ======================
    private void openBigWheelResult(Player p, Material[][] results, int drawCnt, int totalCost, int totalWin, int price, String curName) {
        Inventory inv = Bukkit.createInventory(null, 54, BIG_WHEEL_RESULT_MENU);

        // 边框
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7");
                filler.setItemMeta(meta);
            }
            inv.setItem(i, filler);
        }

        // 标题
        ItemStack titleItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta tMeta = titleItem.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName("§6§l✦ 大转盘结果 ✦");
            titleItem.setItemMeta(tMeta);
        }
        inv.setItem(4, titleItem);

        // 净赚/亏损
        int netWin = totalWin - totalCost;
        ItemStack netItem = new ItemStack(netWin > 0 ? Material.EMERALD_BLOCK : (netWin == 0 ? Material.GOLD_BLOCK : Material.REDSTONE_BLOCK));
        ItemMeta netMeta = netItem.getItemMeta();
        if (netMeta != null) {
            if (netWin > 0) {
                netMeta.setDisplayName("§a§l净赚: §e+" + netWin + " " + curName);
            } else if (netWin == 0) {
                netMeta.setDisplayName("§e§l不赚不赔");
            } else {
                netMeta.setDisplayName("§c§l亏损: §e" + (-netWin) + " " + curName);
            }
            netMeta.setLore(List.of(
                    "§7消耗: §e" + totalCost + " " + curName,
                    "§7赢得: §e" + totalWin + " " + curName
            ));
            netItem.setItemMeta(netMeta);
        }
        inv.setItem(22, netItem);

        // 显示每次转盘结果
        for (int i = 0; i < Math.min(drawCnt, results.length); i++) {
            Material[] spinResult = results[i];
            Material winPrize = spinResult[3];  // 第4列中奖
            int rewardIdx = getWheelPrizeIndex(winPrize);
            int winAmount = getWheelWinAmount(p.getUniqueId(), rewardIdx, false);

            ItemStack resultItem = new ItemStack(winPrize);
            ItemMeta rMeta = resultItem.getItemMeta();
            if (rMeta != null) {
                rMeta.setDisplayName(getWheelPrizeColor(winPrize) + "§l" + getWheelPrizeName(winPrize));
                rMeta.setLore(List.of(
                        "§7第 §e" + (i + 1) + " §7次旋转",
                        winAmount > 0 ? "§a赢得: §e+" + winAmount + " " + curName : "§c未中奖"
                ));
                resultItem.setItemMeta(rMeta);
            }

            // 在界面中排列显示（从slot 10开始，每行5个）
            int slot = 10 + (i % 5) + (i / 5) * 9;
            if (slot < 54) {
                inv.setItem(slot, resultItem);
            }
        }

        // 返回按钮（右下角）
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c⬅️ 返回大转盘");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(53, backBtn);

        p.openInventory(inv);
    }

    // ====================== 大转盘辅助方法 ======================
    private Material getWeightedWheelPrize() {
        return getWeightedWheelPrize(0);
    }

    private Material getWeightedWheelPrize(int tierIdx) {
        int[] weights = (tierIdx >= 0 && tierIdx < WHEEL_TIER_WEIGHTS.length) ? WHEEL_TIER_WEIGHTS[tierIdx] : WHEEL_WEIGHTS;
        int total = 0;
        for (int w : weights) total += w;
        int roll = new java.util.Random().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < WHEEL_PRIZES.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return WHEEL_PRIZES[i];
        }
        return WHEEL_PRIZES[0];
    }

    private int getWheelPrizeIndex(Material mat) {
        for (int i = 0; i < WHEEL_PRIZES.length; i++) {
            if (WHEEL_PRIZES[i] == mat) return i;
        }
        return -1;
    }

    // 获取大转盘单次固定赢取金额（AC币），苹果币调用方自行×0.6
    private int getWheelWinAmount(int tierIdx, int rewardIdx) {
        if (tierIdx < 0 || tierIdx >= WHEEL_TIER_AMOUNTS.length) return 0;
        if (rewardIdx < 0 || rewardIdx >= WHEEL_TIER_AMOUNTS[tierIdx].length) return 0;
        return WHEEL_TIER_AMOUNTS[tierIdx][rewardIdx];
    }

    private int getWheelWinAmount(UUID uuid, int rewardIdx, boolean useApple) {
        int price = wheelSelectedPrice.getOrDefault(uuid, WHEEL_PRICE_OPTIONS[0]);
        int tierIdx = -1;
        for (int t = 0; t < WHEEL_PRICE_OPTIONS.length; t++) {
            if (WHEEL_PRICE_OPTIONS[t] == price) { tierIdx = t; break; }
        }
        if (tierIdx < 0) tierIdx = 0;
        int amount = getWheelWinAmount(tierIdx, rewardIdx);
        return useApple ? Math.max(1, (int)(amount * 0.6)) : amount;
    }

    private String getWheelPrizeColor(Material mat) {
        if (mat == Material.BARRIER) return "§8";
        if (mat == Material.COAL_ORE) return "§8";
        if (mat == Material.IRON_ORE) return "§7";
        if (mat == Material.GOLD_ORE) return "§e";
        if (mat == Material.DIAMOND_ORE) return "§b";
        if (mat == Material.EMERALD_ORE) return "§a";
        if (mat == Material.NETHERITE_SCRAP) return "§4";
        return "§f";
    }

    private String getWheelPrizeName(Material mat) {
        if (mat == Material.BARRIER) return "屏障";
        if (mat == Material.COAL_ORE) return "煤矿";
        if (mat == Material.IRON_ORE) return "铁矿";
        if (mat == Material.GOLD_ORE) return "金矿";
        if (mat == Material.DIAMOND_ORE) return "钻石矿";
        if (mat == Material.EMERALD_ORE) return "绿宝石矿";
        if (mat == Material.NETHERITE_SCRAP) return "下界合金碎片";
        return "未知";
    }

    // ====================== 抽奖通用辅助方法 ======================
    private int getLotteryBalance(Player p) {
        if (lotteryUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            return plugin.economicSystem.getAppleCoins().getAppleCoins(p);
        }
        return plugin.economicSystem.getAcCoins(p);
    }

    private boolean removeLotteryCurrency(Player p, int amount) {
        if (lotteryUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            return plugin.economicSystem.getAppleCoins().removeAppleCoins(p, amount);
        }
        return plugin.economicSystem.removeAcCoins(p, amount);
    }

    private void addLotteryCurrency(Player p, int amount) {
        if (lotteryUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            plugin.economicSystem.getAppleCoins().addAppleCoins(p, amount);
        } else {
            plugin.economicSystem.addAcCoins(p, amount);
        }
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

    // ====================== 老虎机 - Java版 ======================
    public void openSlotMachine(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, SLOT_MACHINE_MENU);
        // 边框
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
            inv.setItem(i, filler);
        }

        boolean useApple = slotUseAppleCoin.getOrDefault(p.getUniqueId(), false);
        String curName = useApple ? "苹果币" : "AC币";
        int balance = useApple
                ? plugin.economicSystem.getAppleCoins().getAppleCoins(p)
                : plugin.economicSystem.getAcCoins(p);
        int spinCnt = slotSpinCount.getOrDefault(p.getUniqueId(), SLOT_SPIN_COUNTS[0]);
        int betPerSpin = slotBetPerSpin.getOrDefault(p.getUniqueId(), SLOT_BET_OPTIONS[0]);
        int totalCost = spinCnt * betPerSpin;

        // 余额
        ItemStack balanceItem = new ItemStack(useApple ? Material.APPLE : Material.GOLD_NUGGET);
        ItemMeta balanceMeta = balanceItem.getItemMeta();
        balanceMeta.setDisplayName("§6余额: §e" + balance + " " + curName);
        balanceItem.setItemMeta(balanceMeta);
        inv.setItem(4, balanceItem);

        // 跳过动画开关（slot 41，放在旋转按钮右侧）
        boolean skipEnabled = slotSkipAnim.getOrDefault(p.getUniqueId(), false);
        ItemStack skipBtn = new ItemStack(Material.ARROW);
        ItemMeta skipMeta = skipBtn.getItemMeta();
        skipMeta.setDisplayName(skipEnabled ? "§a⏩ 跳过动画: 已开启" : "§8⏩ 跳过动画: 已关闭");
        skipBtn.setItemMeta(skipMeta);
        inv.setItem(41, skipBtn);

        // 标题
        ItemStack titleItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName("§6§l✦ 老虎机 ✦");
        titleMeta.setLore(List.of("§7选择连抽次数，点击金锭旋转！"));
        titleItem.setItemMeta(titleMeta);
        inv.setItem(13, titleItem);

        // 赔付说明（区分AC币/苹果币）
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoBtn.getItemMeta();
        infoMeta.setDisplayName("§6赔付说明");
        List<String> infoLore = new ArrayList<>();
        for (int i = 0; i < SLOT_SYMBOLS.length; i++) {
            String ratio = useApple ? String.format("%.2f", SLOT_PAYOUTS[i] * 0.6) : String.valueOf(SLOT_PAYOUTS[i]);
            infoLore.add(getSymbolColor(SLOT_SYMBOLS[i]) + "✧ " + formatSymbolName(SLOT_SYMBOLS[i]) + " §7x" + ratio);
        }
        infoLore.add("");
        infoLore.add(useApple ? "§c苹果币赔付为显示倍率的90%" : "§7三条连线即中奖！共8条赢钱线");
        if (useApple) infoLore.add("§7三条连线即中奖！共8条赢钱线");
        infoMeta.setLore(infoLore);
        infoBtn.setItemMeta(infoMeta);
        inv.setItem(14, infoBtn);

        // 币种切换
        Material coinMat = useApple ? Material.GOLDEN_APPLE : Material.APPLE;
        ItemStack coinBtn = new ItemStack(coinMat);
        ItemMeta coinMeta = coinBtn.getItemMeta();
        coinMeta.setDisplayName((useApple ? "§a▶ " : "§7") + "AC币");
        coinMeta.setLore(List.of(useApple ? "§e点击切换为AC币" : "§a当前使用"));
        coinBtn.setItemMeta(coinMeta);
        inv.setItem(15, coinBtn);

        ItemStack coinBtn2 = new ItemStack(useApple ? Material.APPLE : Material.GOLDEN_APPLE);
        ItemMeta coinMeta2 = coinBtn2.getItemMeta();
        coinMeta2.setDisplayName((useApple ? "§7" : "§a▶ ") + "苹果币");
        coinMeta2.setLore(List.of(useApple ? "§a当前使用" : "§e点击切换为苹果币"));
        coinBtn2.setItemMeta(coinMeta2);
        inv.setItem(16, coinBtn2);

        // 3x3 格子
        Material[] randomGrid = generateRandomGrid();
        for (int i = 0; i < 9; i++) {
            ItemStack slotItem = new ItemStack(randomGrid[i]);
            ItemMeta slotMeta = slotItem.getItemMeta();
            slotMeta.setDisplayName("§6?");
            slotMeta.setLore(List.of("§7点击金锭开始旋转！"));
            slotItem.setItemMeta(slotMeta);
            inv.setItem(SLOT_GRID_SLOTS[i], slotItem);
        }

        // 连抽按钮（1次、2次、5次、10次）
        int[] spinSlots = {23, 24, 25, 26};
        for (int i = 0; i < SLOT_SPIN_COUNTS.length; i++) {
            int cnt = SLOT_SPIN_COUNTS[i];
            int cost = cnt * betPerSpin;
            boolean selected = cnt == spinCnt;
            ItemStack btn = new ItemStack(selected ? Material.GOLD_BLOCK : Material.GOLD_INGOT);
            ItemMeta meta = btn.getItemMeta();
            meta.setDisplayName((selected ? "§a▶ " : "§7") + cnt + " 连抽（" + cost + " " + curName + "）");
            meta.setLore(List.of(selected ? "§a当前选中" : "§e点击选择"));
            btn.setItemMeta(meta);
            inv.setItem(spinSlots[i], btn);
        }

        // 旋转按钮
        ItemStack spinBtn = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta spinMeta = spinBtn.getItemMeta();
        spinMeta.setDisplayName("§6§l 开始旋转（" + totalCost + " " + curName + "）");
        spinMeta.setLore(List.of("§e" + betPerSpin + " " + curName + " × " + spinCnt + "次 = " + totalCost + " " + curName));
        spinBtn.setItemMeta(spinMeta);
        inv.setItem(40, spinBtn);

        // 每注金额选择（在连抽下面）
        int[] betSlots = {32, 33, 34, 35};
        for (int i = 0; i < SLOT_BET_OPTIONS.length; i++) {
            int amt = SLOT_BET_OPTIONS[i];
            boolean selected = amt == betPerSpin;
            ItemStack btn = new ItemStack(selected ? Material.GOLD_BLOCK : Material.GOLD_NUGGET);
            ItemMeta meta = btn.getItemMeta();
            meta.setDisplayName((selected ? "§a▶ " : "§7") + "每注 " + amt + " " + curName);
            meta.setLore(List.of(selected ? "§a当前选中" : "§e点击选择"));
            btn.setItemMeta(meta);
            inv.setItem(betSlots[i], btn);
        }



        // 返回银行
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName("§c⬅️ 返回银行");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    private Material[] generateRandomGrid() {
        Material[] grid = new Material[9];
        for (int i = 0; i < 9; i++) {
            grid[i] = getWeightedRandomSymbol();
        }
        return grid;
    }

    private Material getWeightedRandomSymbol() {
        int total = 0;
        for (int w : SLOT_WEIGHTS) total += w;
        int roll = new java.util.Random().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < SLOT_SYMBOLS.length; i++) {
            cumulative += SLOT_WEIGHTS[i];
            if (roll < cumulative) return SLOT_SYMBOLS[i];
        }
        return SLOT_SYMBOLS[SLOT_SYMBOLS.length - 1];
    }

    private String getSymbolColor(Material mat) {
        if (mat == Material.GRAVEL) return "§8";
        if (mat == Material.AMETHYST_SHARD) return "§d";
        if (mat == Material.COPPER_INGOT) return "§6";
        if (mat == Material.IRON_INGOT) return "§7";
        if (mat == Material.EMERALD) return "§a";
        if (mat == Material.GOLD_INGOT) return "§e";
        if (mat == Material.DIAMOND) return "§b";
        if (mat == Material.NETHERITE_INGOT) return "§4";
        return "§f";
    }

    private String formatSymbolName(Material mat) {
        if (mat == Material.GRAVEL) return "碎石";
        if (mat == Material.AMETHYST_SHARD) return "紫水晶碎片";
        if (mat == Material.COPPER_INGOT) return "铜";
        if (mat == Material.IRON_INGOT) return "铁";
        if (mat == Material.EMERALD) return "绿";
        if (mat == Material.GOLD_INGOT) return "金";
        if (mat == Material.DIAMOND) return "钻";
        if (mat == Material.NETHERITE_INGOT) return "下界合金";
        return "未知";
    }

    private String getCurrencyName(Player p) {
        return slotUseAppleCoin.getOrDefault(p.getUniqueId(), false) ? "苹果币" : "AC币";
    }

    private int getCurrencyBalance(Player p) {
        if (slotUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            return plugin.economicSystem.getAppleCoins().getAppleCoins(p);
        }
        return plugin.economicSystem.getAcCoins(p);
    }

    private boolean removeCurrency(Player p, int amount) {
        if (slotUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            return plugin.economicSystem.getAppleCoins().removeAppleCoins(p, amount);
        }
        return plugin.economicSystem.removeAcCoins(p, amount);
    }

    private void addCurrency(Player p, int amount) {
        if (slotUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            plugin.economicSystem.getAppleCoins().addAppleCoins(p, amount);
        } else {
            plugin.economicSystem.addAcCoins(p, amount);
        }
    }

    private int getBetPerSpin(Player p) {
        return slotBetPerSpin.getOrDefault(p.getUniqueId(), SLOT_BET_OPTIONS[0]);
    }

    private int getSlotSymbolIndex(Material mat) {
        for (int i = 0; i < SLOT_SYMBOLS.length; i++) {
            if (SLOT_SYMBOLS[i] == mat) return i;
        }
        return -1;
    }

    // ====================== 保底机制辅助方法 ======================
    // 强制至少一条赢线
    private Material[] generateWinningGrid() {
        Material[] grid = generateRandomGrid();
        java.util.Random rand = new java.util.Random();
        int symbolIdx = rand.nextInt(SLOT_SYMBOLS.length);
        Material forcedSymbol = SLOT_SYMBOLS[symbolIdx];
        int[] line = WIN_LINES[rand.nextInt(WIN_LINES.length)];
        for (int pos : line) {
            grid[pos] = forcedSymbol;
        }
        return grid;
    }

    // 强制盈利（赔付 > 下注）
    private Material[] generateProfitableGrid(int bet) {
        Material[] grid = generateRandomGrid();
        java.util.Random rand = new java.util.Random();
        // 选择赔付>1的符号（索引1-7），保证一条线即可盈利
        int[] profitableIdx = {1, 2, 3, 4, 5, 6, 7};
        Material forcedSymbol = SLOT_SYMBOLS[profitableIdx[rand.nextInt(profitableIdx.length)]];
        int[] line = WIN_LINES[rand.nextInt(WIN_LINES.length)];
        for (int pos : line) {
            grid[pos] = forcedSymbol;
        }
        return grid;
    }

    // 强制9格全相同
    private Material[] generateAllSameGrid() {
        java.util.Random rand = new java.util.Random();
        Material symbol = SLOT_SYMBOLS[rand.nextInt(SLOT_SYMBOLS.length)];
        Material[] grid = new Material[9];
        Arrays.fill(grid, symbol);
        return grid;
    }

    // 强制亏损（无赢钱线）
    private Material[] generateLosingGrid(int bet) {
        Material[] grid;
        do {
            grid = generateRandomGrid();
        } while (calculateSlotWins(grid, bet, null) > 0);
        return grid;
    }

    // 统计中奖线数
    private int countWinLines(Material[] grid) {
        int count = 0;
        for (int[] line : WIN_LINES) {
            Material first = grid[line[0]];
            if (grid[line[1]] == first && grid[line[2]] == first) {
                count++;
            }
        }
        return count;
    }

    // 判断9格是否全相同
    private boolean isAllSame(Material[] grid) {
        Material first = grid[0];
        for (int i = 1; i < 9; i++) {
            if (grid[i] != first) return false;
        }
        return true;
    }

    // 判断是否有下界合金连线
    private boolean hasNetheriteLine(Material[] grid) {
        for (int[] line : WIN_LINES) {
            if (grid[line[0]] == Material.NETHERITE_INGOT && grid[line[1]] == Material.NETHERITE_INGOT && grid[line[2]] == Material.NETHERITE_INGOT) {
                return true;
            }
        }
        return false;
    }

    // 处理九格全相同大奖
    private void handleNineSameJackpot(Player p, int spinWin, int bet) {
        String curName = getCurrencyName(p);
        java.util.Random rand = new java.util.Random();
        int betPerSpin = getBetPerSpin(p);
        int totalBetThisSpin = spinWin; // 实际这里用bet参数即可

        // 重置全相同全局保底
        spinsSinceLastNineSame = 0;
        nineSamePityThreshold = NINE_SAME_PITY_MIN + new java.util.Random().nextInt(NINE_SAME_PITY_MAX - NINE_SAME_PITY_MIN + 1);

        // 额外给予中奖者本次下注的50%-100%+10%-20%
        int bonus1 = (int)(bet * (50 + rand.nextInt(51)) / 100.0);
        int bonus2 = (int)(bet * (10 + rand.nextInt(11)) / 100.0);
        int winnerBonus = bonus1 + bonus2;

        // 给全服玩家1-5%本次总下注量
        int globalPercent = 1 + rand.nextInt(5);
        int globalBonus = (int)(bet * globalPercent / 100.0);
        // 苹果币奖励降10%
        if (slotUseAppleCoin.getOrDefault(p.getUniqueId(), false)) {
            globalBonus = (int)(globalBonus * 0.6);
        }

        // 发放奖励
        if (winnerBonus > 0) {
            addCurrency(p, winnerBonus);
        }

        // 全服播报
        int spinCnt = slotSpinCount.getOrDefault(p.getUniqueId(), SLOT_SPIN_COUNTS[0]);
        int totalWon = spinWin + winnerBonus;
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦");
        Bukkit.broadcastMessage("§6§l✦  九 格 全 相 同  ✦");
        Bukkit.broadcastMessage("§6§l✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦");
        Bukkit.broadcastMessage("§e恭喜玩家 " + p.getName() + " §a在使用 §e" + curName + "§a+§e" + spinCnt + "§a连抽时9格都是同一个物品！赚取 §e" + totalWon + "个" + curName + " §a并为全服玩家带来 §e" + globalBonus + "个" + curName + "/人 §a的收益！");

        // 全服音效
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // 给全服玩家发放
        if (globalBonus > 0) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                addCurrency(online, globalBonus);
            }
        }

        p.sendMessage("§6§l✦✦✦ 九格全相同！ ✦✦✦");
        p.sendMessage("§a基础赢得: §e" + spinWin + " " + curName);
        p.sendMessage("§a额外奖励: §e+" + winnerBonus + " " + curName);
    }

    // ====================== 老虎机 - 旋转动画 ======================
    private void spinSlotMachine(Player p) {
        UUID uuid = p.getUniqueId();
        if (slotSpinning.getOrDefault(uuid, false)) {
            p.sendMessage("§c正在旋转中，请稍候...");
            return;
        }

        int spinCnt = slotSpinCount.getOrDefault(uuid, SLOT_SPIN_COUNTS[0]);
        int totalCost = spinCnt * getBetPerSpin(p);
        String curName = getCurrencyName(p);

        if (getCurrencyBalance(p) < totalCost) {
            p.sendMessage("§c" + curName + "不足！需要 " + totalCost + " " + curName);
            return;
        }

        // 扣除
        if (!removeCurrency(p, totalCost)) {
            p.sendMessage("§c" + curName + "扣除失败！");
            return;
        }

        slotSpinning.put(uuid, true);
        Inventory inv = p.getOpenInventory().getTopInventory();
        if (!p.getOpenInventory().getTitle().equals(SLOT_MACHINE_MENU)) {
            slotSpinning.put(uuid, false);
            return;
        }

        // 跳过动画按钮
        ItemStack skipBtn = new ItemStack(Material.ARROW);
        ItemMeta skipMeta = skipBtn.getItemMeta();
        skipMeta.setDisplayName("§e⏩ 跳过动画");
        skipBtn.setItemMeta(skipMeta);
        inv.setItem(41, skipBtn);

        // 生成连抽结果（含保底机制）
        java.util.List<Material[]> allResults = new java.util.ArrayList<>();
        final int[] winAccum = {0};
        int totalSpinCost = spinCnt * getBetPerSpin(p);
        int[] lossCnt = {consecutiveLosses.getOrDefault(uuid, 0)};
        int[] noWinCnt = {consecutiveNoWins.getOrDefault(uuid, 0)};
        int betPer = getBetPerSpin(p);
        for (int i = 0; i < spinCnt; i++) {
            Material[] g;
            spinsSinceLastNineSame++;
            boolean needProfitable = lossCnt[0] >= 3 && lossCnt[0] <= 5;
            boolean needWinLine = noWinCnt[0] >= 7 && noWinCnt[0] <= 12;
            boolean needNineSamePity = spinsSinceLastNineSame >= nineSamePityThreshold;
            boolean forcedLoss = consecutiveWinRounds.getOrDefault(uuid, 0) >= 2;
            if (needNineSamePity) {
                g = generateAllSameGrid();
            } else if (forcedLoss) {
                g = generateLosingGrid(betPer);
            } else if (needProfitable) {
                g = generateProfitableGrid(betPer);
                lossCnt[0] = 0;
                noWinCnt[0] = 0;
            } else if (needWinLine) {
                g = generateWinningGrid();
                noWinCnt[0] = 0;
            } else {
                g = generateRandomGrid();
            }
            allResults.add(g);
            int spinWin = calculateSlotWins(g, betPer, p);
            winAccum[0] += spinWin;
            if (spinWin >= betPer) {
                lossCnt[0] = 0;
            } else {
                lossCnt[0]++;
            }
            if (spinWin > 0) {
                noWinCnt[0] = 0;
            } else {
                noWinCnt[0]++;
            }
        }
        consecutiveLosses.put(uuid, lossCnt[0]);
        consecutiveNoWins.put(uuid, noWinCnt[0]);
        final int FINAL_TOTAL_COST = totalSpinCost;

        // 如果跳过动画开启，直接完成
        if (slotSkipAnim.getOrDefault(uuid, false)) {
            // 处理所有结果的播报（跳过动画时结果已预生成）
            for (int i = 0; i < allResults.size(); i++) {
                Material[] result = allResults.get(i);
                int sWin = calculateSlotWins(result, betPer, p);
                int lines = countWinLines(result);
                if (lines >= 3) {
                    String cur = getCurrencyName(p);
                    Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中连中 §e" + lines + " §a线！获得 §e" + sWin + " " + cur);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
                if (hasNetheriteLine(result)) {
                    Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中抽到了 §4§l下界合金 §a！");
                }
                if (isAllSame(result)) {
                    handleNineSameJackpot(p, sWin, betPer);
                }
                if (sWin > 0) {
                    p.sendMessage("§a第 §e" + (i + 1) + " §a次中奖！赢得 §e" + sWin + " " + getCurrencyName(p));
                } else {
                    p.sendMessage("§7第 §e" + (i + 1) + " §7次未中奖");
                }
            }
            Material[] lastResult = allResults.get(allResults.size() - 1);
            updateSlotGridDisplay(inv, lastResult);
            highlightWinLines(inv, lastResult);
            completeSlotSpin(p, inv, allResults, FINAL_TOTAL_COST, winAccum[0]);
            slotSpinning.put(uuid, false);
            return;
        }

        // 连抽动画按次展示（每次结果后等待12tick = 0.6秒）
        final int[] spinState = {0, 0}; // [currentSpin, animTick] 为负时表示结果等待中
        final int ANIM_PER_SPIN = spinCnt <= 1 ? 50 : (spinCnt <= 2 ? 30 : 20);
        final int WAIT_TICKS = 12; // 0.6秒
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.getOpenInventory().getTitle().equals(SLOT_MACHINE_MENU)) {
                    slotSpinning.put(uuid, false);
                    this.cancel();
                    return;
                }

                if (slotSkipAnim.getOrDefault(uuid, false)) {
                    // 处理尚未展示的结果的播报
                    for (int i = spinState[0]; i < allResults.size(); i++) {
                        Material[] result = allResults.get(i);
                        int sWin = calculateSlotWins(result, betPer, p);
                        int lines = countWinLines(result);
                        if (lines >= 3) {
                            String cur = getCurrencyName(p);
                            Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中连中 §e" + lines + " §a线！获得 §e" + sWin + " " + cur);
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                            }
                        }
                        if (hasNetheriteLine(result)) {
                            Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中抽到了 §4§l下界合金 §a！");
                        }
                        if (isAllSame(result)) {
                            handleNineSameJackpot(p, sWin, betPer);
                        }
                    }
                    Material[] lastResult = allResults.get(allResults.size() - 1);
                    updateSlotGridDisplay(inv, lastResult);
                    highlightWinLines(inv, lastResult);
                    completeSlotSpin(p, inv, allResults, FINAL_TOTAL_COST, winAccum[0]);
                    slotSpinning.put(uuid, false);
                    this.cancel();
                    return;
                }

                if (spinState[0] >= allResults.size()) {
                    completeSlotSpin(p, inv, allResults, FINAL_TOTAL_COST, winAccum[0]);
                    slotSpinning.put(uuid, false);
                    this.cancel();
                    return;
                }

                if (spinState[1] < 0) {
                    // 等待间隔中
                    spinState[1]++;
                    if (spinState[1] == 0) {
                        // 等待结束，准备下一次旋转的格子
                        for (int i = 0; i < 9; i++) {
                            ItemStack item = inv.getItem(SLOT_GRID_SLOTS[i]);
                            if (item != null) {
                                ItemMeta meta = item.getItemMeta();
                                if (meta != null) {
                                    meta.setDisplayName("§6?");
                                    meta.setLore(List.of("§7旋转中..."));
                                    item.setItemMeta(meta);
                                }
                                inv.setItem(SLOT_GRID_SLOTS[i], item);
                            }
                        }
                    }
                    return;
                }

                if (spinState[1] >= ANIM_PER_SPIN) {
                    // 展示本次结果
                    Material[] result = allResults.get(spinState[0]);
                    updateSlotGridDisplay(inv, result);
                    highlightWinLines(inv, result);

                    int spinWin = calculateSlotWins(result, getBetPerSpin(p), p);
                    int lines = countWinLines(result);
                    // 3+连线全服播报+音效
                    if (lines >= 3) {
                        String cur = getCurrencyName(p);
                        Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中连中 §e" + lines + " §a线！获得 §e" + spinWin + " " + cur);
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        }
                    }
                     // 下界合金连线全服播报
                    if (hasNetheriteLine(result)) {
                        Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中抽到了 §4§l下界合金 §a！");
                    }
                    // 9格全相同
                    if (isAllSame(result)) {
                        handleNineSameJackpot(p, spinWin, getBetPerSpin(p));
                    }
                    if (spinWin > 0) {
                        p.sendMessage("§a第 §e" + (spinState[0] + 1) + " §a次中奖！赢得 §e" + spinWin + " " + getCurrencyName(p));
                    } else {
                        p.sendMessage("§7第 §e" + (spinState[0] + 1) + " §7次未中奖");
                    }

                    spinState[0]++;
                    if (spinState[0] >= allResults.size()) {
                        // 全部完成，直接进入完成阶段
                        return;
                    }
                    // 进入等待间隔
                    spinState[1] = -WAIT_TICKS;
                    return;
                }

                // 动画帧：随机变换格子
                for (int i = 0; i < 9; i++) {
                    Material randomMat = getWeightedRandomSymbol();
                    ItemStack item = inv.getItem(SLOT_GRID_SLOTS[i]);
                    if (item != null) {
                        item.setType(randomMat);
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(getSymbolColor(randomMat) + "§l" + formatSymbolName(randomMat));
                            item.setItemMeta(meta);
                        }
                        inv.setItem(SLOT_GRID_SLOTS[i], item);
                    }
                }
                spinState[1]++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateSlotGridDisplay(Inventory inv, Material[] grid) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = new ItemStack(grid[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getSymbolColor(grid[i]) + "§l" + formatSymbolName(grid[i]));
            item.setItemMeta(meta);
            inv.setItem(SLOT_GRID_SLOTS[i], item);
        }
    }

    private void completeSlotSpin(Player p, Inventory inv, java.util.List<Material[]> allResults, int totalCost, int allTotalWin) {
        // 赔付
        int netWin = allTotalWin - totalCost;
        UUID uuid = p.getUniqueId();
        if (allTotalWin > 0) {
            addCurrency(p, allTotalWin);
        }

        // 更新余额
        int balance = getCurrencyBalance(p);
        String curName = getCurrencyName(p);
        ItemStack balanceItem = inv.getItem(4);
        if (balanceItem != null) {
            ItemMeta meta = balanceItem.getItemMeta();
            meta.setDisplayName("§6余额: §e" + balance + " " + curName);
            balanceItem.setItemMeta(meta);
            inv.setItem(4, balanceItem);
        }



        // 更新旋转按钮
        int spinCnt = slotSpinCount.getOrDefault(p.getUniqueId(), SLOT_SPIN_COUNTS[0]);
        int newTotalCost = spinCnt * getBetPerSpin(p);
        ItemStack spinBtn = inv.getItem(40);
        if (spinBtn != null) {
            ItemMeta meta = spinBtn.getItemMeta();
            meta.setDisplayName("§6§l 开始旋转（" + newTotalCost + " " + curName + "）");
            meta.setLore(List.of("§e消耗 " + newTotalCost + " " + curName + " 旋转 " + spinCnt + " 次"));
            spinBtn.setItemMeta(meta);
            inv.setItem(40, spinBtn);
        }

        // 恢复格子（显示最后一次结果）
        if (!allResults.isEmpty()) {
            Material[] lastGrid = allResults.get(allResults.size() - 1);
            updateSlotGridDisplay(inv, lastGrid);
            highlightWinLines(inv, lastGrid);
        }

        // 显示汇总
        if (netWin > 0) {
            consecutiveWinRounds.put(uuid, consecutiveWinRounds.getOrDefault(uuid, 0) + 1);
            String playerName = p.getName();
            // 赚总下注20%+时全服播报
            if (netWin * 5 >= totalCost) {
                Bukkit.broadcastMessage("§e玩家 " + playerName + " §a在使用 §e" + curName + "§a+§e" + spinCnt + "§a连抽时赚取 §e" + netWin + "个" + curName);
            }

            p.sendMessage("§6§l🎉 老虎机 - 连抽结束！");
            p.sendMessage("§a总计消耗: §e" + totalCost + " " + curName);
            p.sendMessage("§a总计赢得: §e" + allTotalWin + " " + curName);
            p.sendMessage("§a净赚: §e" + netWin + " " + curName);

            ItemStack winDisplay = new ItemStack(Material.FIREWORK_STAR);
            ItemMeta winMeta = winDisplay.getItemMeta();
            winMeta.setDisplayName("§6§l🎉 连抽结束！");
            winMeta.setLore(List.of("§a消耗: §e" + totalCost + " " + curName, "§a赢得: §e" + allTotalWin + " " + curName, "§7净赚: §e" + netWin + " " + curName));
            winDisplay.setItemMeta(winMeta);
            inv.setItem(22, winDisplay);
        } else {
            p.sendMessage("§7老虎机 - 连抽结束。");
            p.sendMessage("§7总计消耗: §e" + totalCost + " " + curName);
            p.sendMessage("§7总计赢得: §e" + allTotalWin + " " + curName);
            p.sendMessage("§7亏损: §e" + (-netWin) + " " + curName);
            consecutiveWinRounds.put(uuid, 0);

            ItemStack loseDisplay = new ItemStack(Material.BARRIER);
            ItemMeta loseMeta = loseDisplay.getItemMeta();
            loseMeta.setDisplayName("§c连抽结束");
            loseMeta.setLore(List.of("§7消耗: §e" + totalCost + " " + curName, "§7赢得: §e" + allTotalWin + " " + curName, "§7亏损: §e" + (-netWin) + " " + curName));
            loseDisplay.setItemMeta(loseMeta);
            inv.setItem(22, loseDisplay);
        }
    }

    private void highlightWinLines(Inventory inv, Material[] grid) {
        for (int[] line : WIN_LINES) {
            Material first = grid[line[0]];
            if (grid[line[1]] == first && grid[line[2]] == first) {
                for (int pos : line) {
                    ItemStack item = inv.getItem(SLOT_GRID_SLOTS[pos]);
                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                        List<String> lore = meta.getLore();
                        if (lore == null) lore = new ArrayList<>();
                        lore.add("");
                        lore.add("§6§l✦ 中奖！");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        inv.setItem(SLOT_GRID_SLOTS[pos], item);
                    }
                }
            }
        }
    }

    private int calculateSlotWins(Material[] grid, int bet, Player p) {
        int totalWin = 0;
        double[] payouts = (p != null && slotUseAppleCoin.getOrDefault(p.getUniqueId(), false)) ? APPLE_PAYOUTS : SLOT_PAYOUTS;
        for (int[] line : WIN_LINES) {
            Material first = grid[line[0]];
            if (grid[line[1]] == first && grid[line[2]] == first) {
                int idx = getSlotSymbolIndex(first);
                if (idx >= 0) {
                    totalWin += (int)(bet * payouts[idx]);
                }
            }
        }
        return totalWin;
    }

    // ====================== 老虎机 - 基岩版 ======================
    public void openBedrockSlotMachine(Player p) {
        boolean useApple = slotUseAppleCoin.getOrDefault(p.getUniqueId(), false);
        String curName = useApple ? "苹果币" : "AC币";
        int balance = getCurrencyBalance(p);
        int betPerSpin = getBetPerSpin(p);
        int spinCnt = slotSpinCount.getOrDefault(p.getUniqueId(), SLOT_SPIN_COUNTS[0]);
        int totalCost = spinCnt * betPerSpin;

        SimpleForm.Builder form = SimpleForm.builder()
                .title("§6§l老虎机")
                .content("§6余额: §e" + balance + " " + curName + "\n§7每注: §e" + betPerSpin + " " + curName + "  × " + spinCnt + "次 = " + totalCost + " " + curName + "\n\n§7选择连抽次数：");

        for (int cnt : SLOT_SPIN_COUNTS) {
            int cost = cnt * betPerSpin;
            String prefix = (cnt == spinCnt) ? "§a▶ " : "§7";
            form.button(prefix + cnt + " 连抽（" + cost + " " + curName + "）");
        }

        // 每注金额选择
        for (int amt : SLOT_BET_OPTIONS) {
            String prefix = (amt == betPerSpin) ? "§a▶ " : "§7";
            form.button(prefix + "每注 " + amt + " " + curName);
        }

        form.button((useApple ? "§a▶ " : "§7") + "AC币  " + (useApple ? "§7" : "§a▶ ") + "苹果币");
        form.button("§6§l 开始旋转（" + totalCost + " " + curName + "）");
        form.button("§7赔付说明");
        form.button("§c⬅️ 返回银行");

        form.validResultHandler((SimpleFormResponse response) -> {
            int id = response.clickedButtonId();
            int spinCntLen = SLOT_SPIN_COUNTS.length;
            int betLen = SLOT_BET_OPTIONS.length;
            if (id < spinCntLen) {
                slotSpinCount.put(p.getUniqueId(), SLOT_SPIN_COUNTS[id]);
                openBedrockSlotMachine(p);
            } else if (id < spinCntLen + betLen) {
                int betIdx = id - spinCntLen;
                slotBetPerSpin.put(p.getUniqueId(), SLOT_BET_OPTIONS[betIdx]);
                openBedrockSlotMachine(p);
            } else if (id == spinCntLen + betLen) {
                slotUseAppleCoin.put(p.getUniqueId(), !useApple);
                openBedrockSlotMachine(p);
            } else if (id == spinCntLen + betLen + 1) {
                doBedrockSlotSpin(p);
            } else if (id == spinCntLen + betLen + 2) {
                openBedrockSlotInfo(p);
            } else {
                openJavaBankMenu(p);
            }
        });

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    private void doBedrockSlotSpin(Player p) {
        int spinCnt = slotSpinCount.getOrDefault(p.getUniqueId(), SLOT_SPIN_COUNTS[0]);
        int totalCost = spinCnt * getBetPerSpin(p);
        String curName = getCurrencyName(p);

        if (getCurrencyBalance(p) < totalCost) {
            p.sendMessage("§c" + curName + "不足！需要 " + totalCost + " " + curName);
            openBedrockSlotMachine(p);
            return;
        }
        if (!removeCurrency(p, totalCost)) {
            p.sendMessage("§c" + curName + "扣除失败！");
            return;
        }

        // 连抽生成结果（含保底机制）
        java.util.List<Material[]> allResults = new java.util.ArrayList<>();
        int allTotalWin = 0;
        StringBuilder detail = new StringBuilder();
        UUID uuid = p.getUniqueId();
        int[] lossCnt = {consecutiveLosses.getOrDefault(uuid, 0)};
        int[] noWinCnt = {consecutiveNoWins.getOrDefault(uuid, 0)};
        int betPer = getBetPerSpin(p);
        for (int i = 0; i < spinCnt; i++) {
            Material[] grid;
            spinsSinceLastNineSame++;
            boolean needProfitable = lossCnt[0] >= 3 && lossCnt[0] <= 5;
            boolean needWinLine = noWinCnt[0] >= 7 && noWinCnt[0] <= 12;
            boolean needNineSamePity = spinsSinceLastNineSame >= nineSamePityThreshold;
            boolean forcedLoss = consecutiveWinRounds.getOrDefault(uuid, 0) >= 2;
            if (needNineSamePity) {
                grid = generateAllSameGrid();
            } else if (forcedLoss) {
                grid = generateLosingGrid(betPer);
            } else if (needProfitable) {
                grid = generateProfitableGrid(betPer);
                lossCnt[0] = 0;
                noWinCnt[0] = 0;
            } else if (needWinLine) {
                grid = generateWinningGrid();
                noWinCnt[0] = 0;
            } else {
                grid = generateRandomGrid();
            }
            allResults.add(grid);
            int spinWin = calculateSlotWins(grid, betPer, p);
            allTotalWin += spinWin;

            // 更新保底计数
            if (spinWin >= betPer) {
                lossCnt[0] = 0;
            } else {
                lossCnt[0]++;
            }
            if (spinWin > 0) {
                noWinCnt[0] = 0;
            } else {
                noWinCnt[0]++;
            }

            // 3+连线/9全同检测
            boolean has3PlusLines = countWinLines(grid) >= 3;
            boolean has9Same = isAllSame(grid);
            if (has3PlusLines) {
                Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中连中 §e" + countWinLines(grid) + " §a线！获得 §e" + spinWin + " " + curName);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
            // 下界合金连线全服播报
            if (hasNetheriteLine(grid)) {
                Bukkit.broadcastMessage("§e" + p.getName() + " §a在老虎机中抽到了 §4§l下界合金 §a！");
            }
            if (has9Same) {
                handleNineSameJackpot(p, spinWin, betPer);
            }

            detail.append("§7第").append(i + 1).append("次: ");
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    Material mat = grid[row * 3 + col];
                    detail.append(getSymbolColor(mat)).append(formatSymbolName(mat)).append(" ");
                }
                if (row < 2) detail.append(" | ");
            }
            detail.append(spinWin > 0 ? " §a+ " + spinWin : " §7- ").append("\n");
        }
        consecutiveLosses.put(uuid, lossCnt[0]);
        consecutiveNoWins.put(uuid, noWinCnt[0]);

        // 赔付
        if (allTotalWin > 0) {
            addCurrency(p, allTotalWin);
        }

        int netWin = allTotalWin - totalCost;
        UUID bedrockUuid = p.getUniqueId();
        if (netWin > 0) {
            consecutiveWinRounds.put(bedrockUuid, consecutiveWinRounds.getOrDefault(bedrockUuid, 0) + 1);
        } else {
            consecutiveWinRounds.put(bedrockUuid, 0);
        }
        int finalBalance = getCurrencyBalance(p);

        StringBuilder result = new StringBuilder("§6§l=== 老虎机连抽结果 ===\n\n");
        result.append(detail);
        result.append("\n");
        if (netWin > 0) {
            result.append("§6§l 净赚 §e").append(netWin).append(" " + curName + "\n");
        } else {
            result.append("§c亏损 §e").append(-netWin).append(" " + curName + "\n");
        }
        result.append("§7消耗: §e").append(totalCost).append(" " + curName + "\n");
        result.append("§7赢得: §e").append(allTotalWin).append(" " + curName + "\n");
        result.append("§7余额: §e").append(finalBalance).append(" " + curName);

        SimpleForm form = SimpleForm.builder()
                .title("§6老虎机结果")
                .content(result.toString())
                .button("§6再玩一次")
                .button("§c⬅️ 返回银行")
                .validResultHandler((SimpleFormResponse res) -> {
                    if (res.clickedButtonId() == 0) {
                        openBedrockSlotMachine(p);
                    } else {
                        openJavaBankMenu(p);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);

        // 全服播报（赚总下注20%+时播报）
        if (netWin > 0 && netWin * 5 >= totalCost) {
            Bukkit.broadcastMessage("§e玩家 " + p.getName() + " §a在使用 §e" + curName + "§a+§e" + spinCnt + "§a连抽时赚取 §e" + netWin + "个" + curName);
        }
    }

    private void openBedrockSlotInfo(Player p) {
        StringBuilder content = new StringBuilder("§6===== 赔付说明 =====\n\n");
        for (int i = 0; i < SLOT_SYMBOLS.length; i++) {
            String color = getSymbolColor(SLOT_SYMBOLS[i]);
            content.append(color).append("✧ ").append(formatSymbolName(SLOT_SYMBOLS[i]))
                    .append(" §7x").append(SLOT_PAYOUTS[i]).append("\n");
        }
        content.append("\n§7三条连线即中奖！\n");
        content.append("§7共8条赢钱线（3行+3列+2对角）");

        SimpleForm form = SimpleForm.builder()
                .title("§6赔付说明")
                .content(content.toString())
                .button("§7⬅️ 返回老虎机")
                .validResultHandler((SimpleFormResponse res) -> openBedrockSlotMachine(p))
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }
}