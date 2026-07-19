package com.mccfk.plugin.invite;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class InviteCodeManager {

    private final MainPlugin plugin;
    private final File dataFolder;
    private final File codesFile;
    private final Gson gson;

    private final List<InviteCode> codes = new ArrayList<>();

    public InviteCodeManager(MainPlugin plugin) {
        this.plugin = plugin;
        // 存储路径: plugins\.MCCFK_ALL_PLUGONSDATA\invitecodes
        File pluginsFolder = new File(plugin.getServer().getPluginsFolder(), ".MCCFK_ALL_PLUGONSDATA");
        this.dataFolder = new File(pluginsFolder, "invitecodes");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.codesFile = new File(dataFolder, "codes.json");
        loadCodes();
    }

    // ========== 数据加载/保存 ==========

    public void loadCodes() {
        codes.clear();
        if (!codesFile.exists()) {
            plugin.getLogger().info("§7[邀请码] codes.json 不存在，将创建新文件");
            saveCodes();
            return;
        }
        try (FileReader reader = new FileReader(codesFile)) {
            Type listType = new TypeToken<List<InviteCode>>() {}.getType();
            List<InviteCode> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                codes.addAll(loaded);
            }
            plugin.getLogger().info("§7[邀请码] 已加载 " + codes.size() + " 个邀请码");
        } catch (IOException e) {
            plugin.getLogger().severe("§c[邀请码] 加载 codes.json 失败: " + e.getMessage());
        }
    }

    public void saveCodes() {
        try (FileWriter writer = new FileWriter(codesFile)) {
            gson.toJson(codes, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("§c[邀请码] 保存 codes.json 失败: " + e.getMessage());
        }
    }

    // ========== 生成邀请码 ==========

    /**
     * 生成8位随机字母数字邀请码
     */
    private String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(8);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成唯一邀请码（避免重复）
     */
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = generateCode();
            attempts++;
            if (attempts > 100) {
                // 极端情况：如果尝试100次仍无法生成唯一码，增加长度
                String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                StringBuilder sb = new StringBuilder(10);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 10; i++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                code = sb.toString();
                break;
            }
        } while (isCodeUsed(code));
        return code;
    }

    private boolean isCodeUsed(String code) {
        for (InviteCode ic : codes) {
            if (ic.code.equalsIgnoreCase(code)) return true;
        }
        return false;
    }

    /**
     * 创建邀请码
     */
    public String createCode(Player creator) {
        String code = generateUniqueCode();
        InviteCode ic = new InviteCode();
        ic.code = code;
        ic.creator = creator.getUniqueId().toString();
        ic.creatorName = creator.getName();
        ic.createTime = System.currentTimeMillis();
        ic.maxUses = 1;
        ic.usedPlayers = new ArrayList<>();
        ic.usedBy = null;
        ic.usedByName = null;
        codes.add(ic);
        saveCodes();
        return code;
    }

    // ========== 使用邀请码 ==========

    /**
     * 尝试使用邀请码
     * @return 成功/失败消息
     */
    public String useCode(Player player, String code) {
        // 查找邀请码
        InviteCode targetCode = null;
        for (InviteCode ic : codes) {
            if (ic.code.equalsIgnoreCase(code)) {
                targetCode = ic;
                break;
            }
        }

        if (targetCode == null) {
            return "§c邀请码不存在！";
        }

        // 检查是否已达最大使用次数
        if (targetCode.usedPlayers.size() >= targetCode.maxUses) {
            return "§c该邀请码已被使用完毕！";
        }

        // 检查是否已使用过该码
        String uuidStr = player.getUniqueId().toString();
        if (targetCode.usedPlayers.contains(uuidStr)) {
            return "§c你已经使用过该邀请码了！";
        }

        // 不能使用自己的邀请码
        if (targetCode.creator.equals(uuidStr)) {
            return "§c你不能使用自己的邀请码！";
        }

        // 检查是否已被其他玩家邀请过（即是否已经使用过别人的邀请码）
        for (InviteCode ic : codes) {
            if (ic.usedPlayers.contains(uuidStr)) {
                return "§c你已经被其他玩家邀请过了，不能再使用邀请码！";
            }
        }

        // 检查目标邀请码的创建者是否曾使用过当前玩家的邀请码
        // 即：创建者不能使用被他邀请过的玩家创建的邀请码
        for (InviteCode ic : codes) {
            if (ic.creator.equals(uuidStr) && ic.usedPlayers.contains(targetCode.creator)) {
                return "§c你不能使用被你邀请过的玩家创建的邀请码！";
            }
        }

        // 直接发放奖励
        completeInviteCodeUse(player, targetCode, uuidStr);
        return "§a邀请码使用成功！获得奖励：\n§e+ 2 苹果币\n§e+ 666 AC币";
    }

    /**
     * 完成邀请码使用流程（发放奖励等）
     */
    private void completeInviteCodeUse(Player player, InviteCode targetCode, String uuidStr) {
        // 给使用者的奖励：2苹果币 + 666AC币
        plugin.getEconomicSystem().getAppleCoins().addAppleCoins(player, 2);
        plugin.getEconomicSystem().addAcCoins(player, 666);

        // 记录使用
        targetCode.usedPlayers.add(uuidStr);
        targetCode.usedBy = uuidStr;
        targetCode.usedByName = player.getName();
        saveCodes();

        // 通知创建者（如果在线）
        Player creator = Bukkit.getPlayer(UUID.fromString(targetCode.creator));
        if (creator != null && creator.isOnline()) {
            creator.sendMessage("§a玩家 " + player.getName() + " 使用了你的邀请码！");
        }
    }

    /**
     * 获取玩家创建的邀请码列表
     */
    public List<InviteCode> getPlayerCodes(Player player) {
        String uuidStr = player.getUniqueId().toString();
        List<InviteCode> result = new ArrayList<>();
        for (InviteCode ic : codes) {
            if (ic.creator.equals(uuidStr)) {
                result.add(ic);
            }
        }
        return result;
    }

    // ========== 内部类 ==========

    public static class InviteCode {
        private String code;
        private String creator;
        private String creatorName;
        private long createTime;
        private int maxUses;
        private List<String> usedPlayers;
        private String usedBy;
        private String usedByName;

        public String getCode() { return code; }
        public String getCreator() { return creator; }
        public String getCreatorName() { return creatorName; }
        public long getCreateTime() { return createTime; }
        public int getMaxUses() { return maxUses; }
        public List<String> getUsedPlayers() { return usedPlayers; }
        public String getUsedBy() { return usedBy; }
        public String getUsedByName() { return usedByName; }
    }
}
