package com.apple.servercore.Gift;

import com.apple.servercore.ACcraft;
import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class Gift {

    final MainPlugin plugin;
    private FileConfiguration giftConfig;
    private final File giftFile;
    private final File playerDataFolder;

    // 【已修改】删除了 titleManager 参数
    public Gift(MainPlugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "player_gift");
        if (!playerDataFolder.exists()) playerDataFolder.mkdirs();

        this.giftFile = new File(plugin.getDataFolder(), "gift.yml");
        saveDefaultGiftConfig();
        reloadConfig();
    }

    public void reloadConfig() {
        giftConfig = YamlConfiguration.loadConfiguration(giftFile);
        InputStream in = plugin.getResource("gift.yml");
        if (in != null) {
            giftConfig.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)
            ));
        }
    }

    private void saveDefaultGiftConfig() {
        if (!giftFile.exists()) {
            plugin.saveResource("gift.yml", false);
        }
    }

    public int getCurrentSignDay(Player p) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        int max = 0;
        for (int day = 1; day <= 31; day++) {
            if (hasReceived(p, year, month, day)) {
                max = day;
            } else {
                break;
            }
        }
        return max;
    }

    public boolean hasSignedToday(Player p) {
        File f = getPlayerFile(p);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String today = LocalDate.now().toString();
        return cfg.getBoolean("lastSignDate." + today, false);
    }

    public void markSignedToday(Player p) {
        File f = getPlayerFile(p);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String today = LocalDate.now().toString();
        cfg.set("lastSignDate." + today, true);
        try {
            cfg.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==============================================
    // 双端统一入口：Java → 箱子，基岩 → 表单
    // ==============================================
    public void openGiftUI(Player p) {
        if (isBedrockPlayer(p)) {
            openBedrockGiftUI(p);
        } else {
            openJavaGiftUI(p);
        }
    }

    // 判断是否基岩玩家
    private boolean isBedrockPlayer(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ==============================================
    // Java 版 - 箱子签到 UI
    // ==============================================
    public void openJavaGiftUI(Player p) {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        boolean signedToday = hasSignedToday(p);
        int currentDay = getCurrentSignDay(p);
        int canReceiveDay = currentDay + 1;

        Inventory inv = Bukkit.createInventory(null, 27, "§e每日签到");

        // 显示信息
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e签到信息");
        List<String> lore = new ArrayList<>();
        lore.add("§f今天: " + month + "月" + day + "日");
        lore.add("§a已连续签到: " + currentDay + "天");
        if (signedToday) {
            lore.add("§c✅ 今日已签到！");
        } else {
            lore.add("§a🟢 今日可领取: 第" + canReceiveDay + "天");
            // 显示今日奖励预览
            List<String> rewardLore = getRewardLore(canReceiveDay);
            if (!rewardLore.isEmpty()) {
                lore.add("§6=== 今日奖励 ===");
                lore.addAll(rewardLore);
            }
        }
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // 领取按钮
        ItemStack receive = new ItemStack(signedToday ? Material.RED_WOOL : Material.LIME_WOOL);
        ItemMeta rMeta = receive.getItemMeta();
        rMeta.setDisplayName(signedToday ? "§c今日已领取" : "§a🟢 点击领取今日签到");
        receive.setItemMeta(rMeta);
        inv.setItem(13, receive);

        // 返回按钮
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName("§7⬅️ 返回主菜单");
        back.setItemMeta(bMeta);
        inv.setItem(26, back);

        p.openInventory(inv);
    }

    // ==============================================
    // 基岩版全屏签到 UI
    // ==============================================
    public void openBedrockGiftUI(Player p) {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        boolean signedToday = hasSignedToday(p);
        int currentDay = getCurrentSignDay(p);
        int canReceiveDay = currentDay + 1;

        StringBuilder content = new StringBuilder();
        content.append("§e===== 每日签到 =====\n");
        content.append("§f今天: ").append(month).append("月").append(day).append("日\n");
        content.append("§a已连续签到: ").append(currentDay).append(" 天\n");

        if (signedToday) {
            content.append("§c✅ 今日已签到！\n");
        } else {
            content.append("§a🟢 今日可领取: 第").append(canReceiveDay).append("天\n");
            // 显示今日奖励预览
            List<String> rewardLore = getRewardLore(canReceiveDay);
            if (!rewardLore.isEmpty()) {
                content.append("§6=== 今日奖励 ===\n");
                for (String r : rewardLore) {
                    content.append(r).append("\n");
                }
            }
        }
        content.append("§7=====================");

        SimpleForm form = SimpleForm.builder()
                .title("§6每日签到系统")
                .content(content.toString())
                .button(signedToday ? "§c今日已领取" : "§a🟢 领取今日签到")
                .button("§7⬅️ 返回服务器主菜单")
                .validResultHandler(response -> {
                    int id = response.clickedButtonId();
                    if (id == 0) {
                        if (!signedToday) receiveGift(p, canReceiveDay);
                    }
                    if (id == 1) {
                        new ACcraft(plugin).openMainMenu(p);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    // ==============================================
    // 获取第N天的奖励（新格式）
    // ==============================================
    public List<Map<String, Object>> getDayRewards(int day) {
        ConfigurationSection daySection = giftConfig.getConfigurationSection("gift." + day);
        if (daySection == null) return new ArrayList<>();

        return (List<Map<String, Object>>) daySection.getList("rewards");
    }

    public int getDayRandomValue(int day) {
        ConfigurationSection daySection = giftConfig.getConfigurationSection("gift." + day);
        if (daySection == null) return 0;
        return daySection.getInt("random", 0);
    }

    // ==============================================
    // 获取奖励描述（用于显示）
    // ==============================================
    public List<String> getRewardLore(int day) {
        List<String> lore = new ArrayList<>();
        List<Map<String, Object>> rewards = getDayRewards(day);
        if (rewards.isEmpty()) {
            lore.add("§c暂无奖励配置");
            return lore;
        }

        int random = getDayRandomValue(day);
        if (random > 0) {
            lore.add("§7随机概率: " + random + "%");
        }

        for (Map<String, Object> map : rewards) {
            String type = (String) map.get("type");
            if (type == null) continue;

            switch (type.toLowerCase()) {
                // 【已删除】称号类型
                case "accoin" -> {
                    int amount = map.get("amount") != null ? (Integer) map.get("amount") : 0;
                    lore.add("§f● AC币: §6+" + amount);
                }
                case "item", "enchant_item" -> {
                    String mat = (String) map.get("material");
                    int amount = map.get("amount") != null ? (Integer) map.get("amount") : 1;
                    lore.add("§f● 物品: §7" + mat + " ×" + amount);
                }
            }
        }
        return lore;
    }

    // ==============================================
    // 执行签到
    // ==============================================
    public boolean receiveGift(Player p, int clickDay) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        if (hasSignedToday(p)) {
            p.sendMessage("§c你今天已经签到过了！明天再来吧~");
            return false;
        }

        int nextDay = getCurrentSignDay(p) + 1;
        if (clickDay != nextDay) {
            p.sendMessage("§c请按顺序签到！当前可领：第" + nextDay + "天");
            return false;
        }

        // 检查该天是否有奖励配置
        List<Map<String, Object>> rewards = getDayRewards(clickDay);
        if (rewards.isEmpty()) {
            p.sendMessage("§c该天没有配置奖励！请联系管理员");
            return false;
        }

        // 根据概率选择奖励
        Map<String, Object> selectedReward = selectRewardByProbability(clickDay, rewards);
        if (selectedReward == null) {
            // 如果没有选中任何奖励（概率全部未命中），默认给第一个奖励
            selectedReward = rewards.get(0);
            p.sendMessage("§e运气不佳，获得保底奖励！");
        }

        // 发放奖励
        giveSingleReward(p, selectedReward);

        markReceived(p, year, month, clickDay);
        markSignedToday(p);

        p.sendMessage("§a签到成功！今日已领取，明天继续领下一天！");
        return true;
    }

    // ==============================================
    // 根据概率选择奖励
    // ==============================================
    private Map<String, Object> selectRewardByProbability(int day, List<Map<String, Object>> rewards) {
        int randomValue = getDayRandomValue(day);

        // 如果 random = 0，表示全部奖励都发放（不随机）
        if (randomValue == 0) {
            // 返回一个特殊标记，表示发放所有奖励
            return null;
        }

        // 生成随机数 0~100
        int roll = new Random().nextInt(101);

        // 如果随机数大于 randomValue，表示未命中概率，返回 null（保底）
        if (roll > randomValue) {
            return null;
        }

        // 命中概率，从奖励列表中随机选一个
        if (rewards.isEmpty()) return null;
        return rewards.get(new Random().nextInt(rewards.size()));
    }

    // ==============================================
    // 发放单个奖励（不随机时发放所有奖励）
    // ==============================================
    private void giveSingleReward(Player p, Map<String, Object> reward) {
        if (reward == null) return;

        String type = (String) reward.get("type");
        if (type == null) return;

        switch (type.toLowerCase()) {
            // 【已删除】title case 分支
            case "accoin" -> {
                int amount = reward.get("amount") != null ? (Integer) reward.get("amount") : 0;
                if (amount > 0) {
                    plugin.economicSystem.addAcCoins(p, amount);
                    p.sendMessage("§a获得 " + amount + " AC币！");
                }
            }
            case "item" -> {
                String matStr = (String) reward.get("material");
                int amount = reward.get("amount") != null ? (Integer) reward.get("amount") : 1;
                Material mat = Material.matchMaterial(matStr);
                if (mat != null) {
                    ItemStack item = new ItemStack(mat, amount);
                    giveItemToPlayer(p, item);
                    p.sendMessage("§a获得 " + mat.name() + " ×" + amount);
                }
            }
            case "enchant_item" -> {
                String matStr = (String) reward.get("material");
                int amount = reward.get("amount") != null ? (Integer) reward.get("amount") : 1;
                Material mat = Material.matchMaterial(matStr);
                if (mat == null) break;

                ItemStack item = new ItemStack(mat, amount);
                ItemMeta meta = item.getItemMeta();
                @SuppressWarnings("unchecked")
                Map<String, Integer> enchants = (Map<String, Integer>) reward.get("enchantments");
                if (enchants != null && meta != null) {
                    for (Map.Entry<String, Integer> en : enchants.entrySet()) {
                        Enchantment enchant = Enchantment.getByName(en.getKey());
                        if (enchant != null) {
                            meta.addEnchant(enchant, en.getValue(), true);
                        }
                    }
                    item.setItemMeta(meta);
                }
                giveItemToPlayer(p, item);
                p.sendMessage("§a获得附魔 " + mat.name() + " ×" + amount);
            }
        }
    }

    // ==============================================
    // 发放所有奖励（当 random = 0 时使用）
    // ==============================================
    @SuppressWarnings("unused")
    private void giveAllRewards(Player p, int day) {
        List<Map<String, Object>> rewards = getDayRewards(day);
        for (Map<String, Object> reward : rewards) {
            giveSingleReward(p, reward);
        }
        p.sendMessage("§a所有奖励已发放！");
    }

    // ==============================================
    // 给玩家物品（背包满则掉落）
    // ==============================================
    private void giveItemToPlayer(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        if (!left.isEmpty()) {
            for (ItemStack leftover : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            p.sendMessage("§e背包已满，物品已掉落在地上！");
        }
    }

    // ==============================================
    // 玩家数据文件
    // ==============================================
    public File getPlayerFile(Player p) {
        return new File(playerDataFolder, p.getUniqueId() + ".yml");
    }

    public boolean hasReceived(Player p, int year, int month, int day) {
        File f = getPlayerFile(p);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return cfg.getBoolean(year + "." + month + "." + day, false);
    }

    public void markReceived(Player p, int year, int month, int day) {
        File f = getPlayerFile(p);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        cfg.set(year + "." + month + "." + day, true);
        try {
            cfg.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}