package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import net.md_5.bungee.api.ChatColor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class PublicWaypointCommand implements CommandExecutor, TabCompleter {

     public static final List<String> CATEGORIES = List.of("刷怪塔", "建筑", "机器", "商店", "其他");

    public static final String DEFAULT_DESC = "这个传送点创建者什么也没有写...";

    public static String getDescription(Map<String, Object> data) {
        String desc = (String) data.get("description");
        if (desc == null || desc.isEmpty()) return DEFAULT_DESC;
        return desc;
    }

    public static String processDescription(String input) {
        if (input == null || input.isEmpty()) return "";
        // 转换 \n 为实际换行符
        String withNewlines = input.replace("\\n", "\n");
        String translated = ChatColor.translateAlternateColorCodes('&', withNewlines);
        // 限制可见字符不超过32（不计算颜色代码和换行符）
        StringBuilder result = new StringBuilder();
        int visibleCount = 0;
        for (int i = 0; i < translated.length(); i++) {
            char c = translated.charAt(i);
            if (c == '\u00a7') {
                result.append(c);
                if (i + 1 < translated.length()) {
                    result.append(translated.charAt(++i));
                }
            } else if (c == '\n') {
                result.append(c);
            } else {
                if (visibleCount >= 32) break;
                result.append(c);
                visibleCount++;
            }
        }
        return result.toString();
    }

    private final MainPlugin plugin;
    public final File pwDir;
    public final Gson gson;
    // 待确认删除：玩家UUID → (路径点名 → 过期时间戳)
    private final Map<UUID, Map<String, Long>> pendingDeleteConfirm = new HashMap<>();
    private static final long CONFIRM_TIMEOUT = 30_000L; // 30秒超时

    // 待设置图标的玩家: UUID → 路径点名

    public PublicWaypointCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        File pluginsFolder = new File(plugin.getDataFolder().getParentFile(), ".MCCFK_ALL_PLUGONSDATA");
        this.pwDir = new File(pluginsFolder, "pw");
        if (!pwDir.exists()) pwDir.mkdirs();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // pwtp 命令 - 只传送到公开路径点
        if (command.getName().equalsIgnoreCase("pwtp")) {
            handlePwtp(player, args);
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create", "创建" -> handleCreate(player, args);
            case "tp", "传送", "传" -> handleTp(player, args);
            case "remove", "删除" -> handleRemove(player, args);
            case "tremove", "确认删除" -> handleTremove(player, args);
            case "m", "manage", "管理" -> handleManage(player, args);
            case "main", "主界面" -> openMainGUI(player);
            default -> sendHelp(player, label);
        }

        return true;
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage("§6===== 公共路径点帮助 =====");
        player.sendMessage("§e/" + label + " create|创建 <分类> <名称> [描述] §7创建公共路径点");
        player.sendMessage("§e/" + label + " tp|传送 <名称> §7传送到公共路径点");
        player.sendMessage("§e/" + label + " remove|删除 <名称> §7删除公共路径点");
        player.sendMessage("§e/" + label + " tremove|确认删除 <名称> §7确认删除自己的路径点（需二次确认）");
        player.sendMessage("§e/" + label + " m|manage|管理 <名称> ... §7管理自己的路径点");
        player.sendMessage("§e/" + label + " main|主界面 §7打开公共路径点主界面");
        player.sendMessage("§7  rename|改名 <新名称> §7改名");
        player.sendMessage("§7  delete|删除 §7删除");
        player.sendMessage("§7  permission|权限 <公开|私密> §7修改传送权限");
        player.sendMessage("§7  ban|封禁|unban|解禁 <玩家> §7封禁/解禁玩家");
        player.sendMessage("§7  icon|图标 §7手持物品发送任意内容设图标");
        player.sendMessage("§7  setlocation|设置位置 §7将路径点传送位置设为当前位置");
        player.sendMessage("§7  description|描述 <描述> §7修改描述");
        player.sendMessage("§7  list|列表 §7查看访问记录");
    }

    /** 打开公共路径点主界面 */
    private void openMainGUI(Player player) {
        if (plugin.getTpAsMe() != null) {
            plugin.getTpAsMe().openPublicWaypointUI(player);
        } else {
            player.sendMessage("§c传送系统未初始化！");
        }
    }

    // ========== create ==========

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
        player.sendMessage("§c用法: /publicwaypoint create <分类> <名称> [描述]");
            return;
        }

        String category = args[1].trim();
        if (!CATEGORIES.contains(category)) {
            player.sendMessage("§c无效分类！可选: " + String.join(", ", CATEGORIES));
            return;
        }

        String name = args[2].trim();
        if (name.isEmpty()) {
            player.sendMessage("§c名称不能为空！");
            return;
        }

        // 检查是否已存在同名路径点
        File pwFile = getWaypointFile(name);
        if (pwFile.exists()) {
            player.sendMessage("§c已存在名为 §b" + name + " §c的公共路径点！");
            return;
        }

        // 解析描述参数
        String description = "";
        if (args.length > 3) {
            description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            description = processDescription(description);
        }

        // 保存路径点数据
        Location loc = player.getLocation();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("creator", player.getName());
        data.put("category", category);
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", (double) loc.getYaw());
        data.put("pitch", (double) loc.getPitch());
        data.put("permission", "公开");
        data.put("banned_players", new ArrayList<String>());
        data.put("created_at", System.currentTimeMillis());
        if (!description.isEmpty()) data.put("description", description);

        try (FileWriter writer = new FileWriter(pwFile)) {
            gson.toJson(data, writer);
            player.sendMessage("§a已创建公共路径点 §b" + name + " §a！");
        } catch (IOException e) {
            player.sendMessage("§c创建路径点失败: " + e.getMessage());
            plugin.getLogger().severe("§c[PublicWaypoint] 保存路径点 " + name + " 失败: " + e.getMessage());
        }
    }

    private void handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /publicwaypoint tp <名称>");
            return;
        }

        String name = args[1].trim();
        Map<String, Object> data = loadWaypointData(name);
        if (data == null) {
            player.sendMessage("§c不存在名为 §b" + name + " §c的公共路径点！");
            return;
        }

        // 私密路径点仅创建者可传送
        if (!"公开".equals(data.get("permission")) && !player.getName().equals(data.get("creator"))) {
            player.sendMessage("§c该路径点不是公开的！");
            return;
        }

        // 检查是否被封禁
        @SuppressWarnings("unchecked")
        List<String> banned = (List<String>) data.get("banned_players");
        if (banned != null && banned.contains(player.getName())) {
            player.sendMessage("§c你已被该路径点的创建者封禁！");
            return;
        }

        Location loc = deserializeLocation(data);
        if (loc == null) {
            player.sendMessage("§c路径点所在世界不存在！");
            return;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());
        player.teleportAsync(loc);
        player.sendMessage("§a已传送到公共路径点 §b" + name + " §a！");
        recordVisit(data, player.getName());
        saveWaypointData(name, data);
    }

    private void handlePwtp(Player player, String[] args) {
        if (args.length == 0) {
            // 列出所有公开路径点
            List<Map<String, Object>> all = loadAllWaypoints();
            List<Map<String, Object>> publicWps = all.stream()
                    .filter(d -> "公开".equals(d.get("permission")))
                    .collect(Collectors.toList());
            if (publicWps.isEmpty()) {
                player.sendMessage("§c暂无公开路径点！");
                return;
            }
            player.sendMessage("§6===== 公开路径点列表 =====");
            for (Map<String, Object> wp : publicWps) {
                String n = (String) wp.get("name");
                String c = (String) wp.get("category");
                String cr = (String) wp.get("creator");
                player.sendMessage("§b" + n + " §7- §f" + c + " §7(by " + cr + ")");
            }
            player.sendMessage("§e/pwtp <名称> §7传送");
            return;
        }

        String name = args[0].trim();
        Map<String, Object> data = loadWaypointData(name);
        if (data == null) {
            player.sendMessage("§c不存在名为 §b" + name + " §c的路径点！");
            return;
        }

        // 检查是否公开
        if (!"公开".equals(data.get("permission"))) {
            player.sendMessage("§c该路径点不是公开的！");
            return;
        }

        // 检查是否被封禁
        @SuppressWarnings("unchecked")
        List<String> banned = (List<String>) data.get("banned_players");
        if (banned != null && banned.contains(player.getName())) {
            player.sendMessage("§c你已被该路径点的创建者封禁！");
            return;
        }

        Location loc = deserializeLocation(data);
        if (loc == null) {
            player.sendMessage("§c路径点所在世界不存在！");
            return;
        }

        plugin.getPlayerDataManager().savePreviousLocation(player, player.getLocation());
        player.teleportAsync(loc);
        player.sendMessage("§a已传送到公开路径点 §b" + name + " §a！");
        recordVisit(data, player.getName());
        saveWaypointData(name, data);
    }

    // ========== remove ==========

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /publicwaypoint remove <名称>");
            return;
        }

        String name = args[1].trim();
        File pwFile = getWaypointFile(name);
        if (!pwFile.exists()) {
            player.sendMessage("§c不存在名为 §b" + name + " §c的公共路径点！");
            return;
        }

        // 检查创建者权限
        Map<String, Object> data = loadWaypointData(name);
        if (data != null && !player.isOp()) {
            String creator = (String) data.get("creator");
            if (creator != null && !creator.equals(player.getName())) {
                player.sendMessage("§c你不是该路径点的创建者，无法删除！");
                return;
            }
        }

        // 检查是否为确认删除
        UUID uuid = player.getUniqueId();
        Map<String, Long> pending = pendingDeleteConfirm.get(uuid);
        if (pending != null) {
            Long expire = pending.get(name);
            if (expire != null && System.currentTimeMillis() < expire) {
                // 在超时内再次确认，执行删除
                pending.remove(name);
                if (pending.isEmpty()) pendingDeleteConfirm.remove(uuid);

                if (pwFile.delete()) {
                    player.sendMessage("§a已删除公共路径点 §b" + name + " §a！");
                } else {
                    player.sendMessage("§c删除路径点失败！");
                }
                return;
            }
        }

        // 未确认或已过期，提示确认
        player.sendMessage("§e⚠ 确认删除路径点 §b" + name + " §e？再次发送 §f/pw remove|删除 " + name + " §e确认删除");

        // 记录待确认
        pendingDeleteConfirm.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(name, System.currentTimeMillis() + CONFIRM_TIMEOUT);
    }

    // ========== tremove（确认删除，只能删自己的）==========

    private void handleTremove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /publicwaypoint tremove <名称>");
            return;
        }

        String name = args[1].trim();
        File pwFile = getWaypointFile(name);
        if (!pwFile.exists()) {
            player.sendMessage("§c不存在名为 §b" + name + " §c的公共路径点！");
            return;
        }

        Map<String, Object> data = loadWaypointData(name);
        if (data == null) {
            player.sendMessage("§c路径点数据损坏！");
            return;
        }

        // 只能删除自己的路径点
        String creator = (String) data.get("creator");
        if (creator == null || !creator.equals(player.getName())) {
            player.sendMessage("§c你只能删除自己创建的路径点！");
            return;
        }

        // 检查是否为确认删除
        UUID uuid = player.getUniqueId();
        Map<String, Long> pending = pendingDeleteConfirm.get(uuid);
        if (pending != null) {
            Long expire = pending.get(name);
            if (expire != null && System.currentTimeMillis() < expire) {
                // 在超时内再次确认，执行删除
                pending.remove(name);
                if (pending.isEmpty()) pendingDeleteConfirm.remove(uuid);

                if (pwFile.delete()) {
                    player.sendMessage("§a已删除公共路径点 §b" + name + " §a！");
                } else {
                    player.sendMessage("§c删除路径点失败！");
                }
                return;
            }
        }

        // 未确认或已过期，提示确认
        player.sendMessage("§e⚠ 确认删除路径点 §b" + name + " §e？再次发送 §f/pw tremove|确认删除 " + name + " §e确认删除");

        // 记录待确认
        pendingDeleteConfirm.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(name, System.currentTimeMillis() + CONFIRM_TIMEOUT);
    }

    // ========== manage（管理自己的路径点）==========

    private void handleManage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /pw m <名称> <操作> [参数]");
            return;
        }

        String name = args[1].trim();
        Map<String, Object> data = loadWaypointData(name);
        if (data == null) {
            player.sendMessage("§c不存在名为 §b" + name + " §c的路径点！");
            return;
        }

        String creator = (String) data.get("creator");
        if (creator == null || !creator.equals(player.getName())) {
            player.sendMessage("§c你只能管理自己创建的路径点！");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§c用法: /pw m " + name + " <rename|delete|permission|ban|unban|icon>");
            return;
        }

        String op = args[2].toLowerCase();
        switch (op) {
            case "rename", "改名" -> handleMRename(player, data, name, args);
            case "delete", "删除" -> handleMDelete(player, data, name);
            case "tremove", "确认删除" -> handleMTRemove(player, data, name);
            case "permission", "权限" -> handleMPermission(player, data, name, args);
            case "ban", "封禁" -> handleMBan(player, data, name, args);
            case "unban", "解禁" -> handleMUnban(player, data, name, args);
            case "icon", "图标" -> handleMIcon(player, data, name);
            case "setlocation", "setloc", "设置位置" -> handleMSetLocation(player, data, name);
            case "description", "desc", "描述" -> handleMDescription(player, data, name, args);
            case "list", "列出访问者" -> handleMList(player, data, name);
            default -> player.sendMessage("§c未知操作: " + op);
        }
    }

    private void handleMRename(Player player, Map<String, Object> data, String oldName, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /pw m " + oldName + " rename <新名称>");
            return;
        }
        String newName = args[3].trim();
        if (newName.isEmpty()) { player.sendMessage("§c名称不能为空！"); return; }

        // 重名检查
        File oldFile = getWaypointFile(oldName);
        File newFile = getWaypointFile(newName);
        if (newFile.exists() && !oldName.equals(newName)) {
            player.sendMessage("§c已存在名为 §b" + newName + " §c的路径点！");
            return;
        }

        data.put("name", newName);
        if (saveWaypointData(newName, data)) {
            oldFile.delete();
            player.sendMessage("§a已重命名为 §b" + newName + " §a！");
        } else {
            player.sendMessage("§c改名失败！");
        }
    }

    private void handleMDelete(Player player, Map<String, Object> data, String name) {
        pendingTRemove.put(player.getUniqueId(), name);
        player.sendMessage("§e⚠ 确认删除路径点 §b" + name + " §e？在聊天栏输入 §f t/yes/确认 §e确认（30秒内有效）");
        player.sendMessage("§7（输入其他内容将取消删除）");
    }

    /** 需要确认的删除 */
    public final Map<UUID, String> pendingTRemove = new HashMap<>();

    private void handleMTRemove(Player player, Map<String, Object> data, String name) {
        pendingTRemove.put(player.getUniqueId(), name);
        player.sendMessage("§e⚠ 确认删除路径点 §b" + name + " §e？");
        player.sendMessage("§e在聊天栏输入 §f t/yes/确认 §e确认（30秒内有效），其他内容取消");
    }

    /** 确认删除 */
    public boolean confirmTRemove(Player player) {
        String name = pendingTRemove.remove(player.getUniqueId());
        if (name == null) return false;

        File pwFile = getWaypointFile(name);
        if (pwFile.exists() && pwFile.delete()) {
            player.sendMessage("§a已删除路径点 §b" + name + " §a！");
        } else {
            player.sendMessage("§c删除失败！");
        }
        return true;
    }

    private void handleMPermission(Player player, Map<String, Object> data, String name, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /pw m " + name + " permission <公开|私密>");
            return;
        }
        String perm = args[3];
        if (!perm.equals("公开") && !perm.equals("私密")) {
            player.sendMessage("§c权限只能是 §e公开 §c或 §e私密");
            return;
        }
        data.put("permission", perm);
        if (saveWaypointData(name, data)) {
            player.sendMessage("§a已设置 §b" + name + " §a权限为: " + perm);
        }
    }

    // ========== 封禁管理 ==========

    /** 待处理封禁信息 */
    public static class PendingBan {
        public final String waypointName;
        public String targetPlayer;
        public long duration; // 秒，-1为永久
        public String reason;
        public long expiry; // 输入超时时间

        public PendingBan(String waypointName) {
            this.waypointName = waypointName;
            this.duration = -1;
            this.reason = "";
        }
    }

    public final Map<UUID, PendingBan> pendingBans = new HashMap<>();

    private void handleMBan(Player player, Map<String, Object> data, String name, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /pw m " + name + " ban 玩家:X 时间:Xs/y/m/d(-1永久) 原因:X");
            player.sendMessage("§7参数顺序可任意，X:用于识别哪项");
            return;
        }

        PendingBan pb = new PendingBan(name);
        pb.expiry = System.currentTimeMillis() + 60000; // 60秒超时

        // 解析参数（任意顺序）
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("玩家:")) {
                pb.targetPlayer = arg.substring(3);
            } else if (arg.startsWith("时间:")) {
                String timeStr = arg.substring(3);
                pb.duration = parseDuration(timeStr);
                if (pb.duration == Long.MIN_VALUE) {
                    player.sendMessage("§c时间格式错误！示例: 1h, 30m, 7d, -1(永久)");
                    return;
                }
            } else if (arg.startsWith("原因:")) {
                pb.reason = arg.substring(3);
            }
        }

        // 验证必填项
        if (pb.targetPlayer == null || pb.targetPlayer.isEmpty()) {
            player.sendMessage("§c必须指定玩家！格式: 玩家:XXX");
            return;
        }
        if (pb.reason == null || pb.reason.isEmpty()) {
            player.sendMessage("§c必须指定原因！格式: 原因:XXX");
            return;
        }

        // 检查是否已封禁
        @SuppressWarnings("unchecked")
        List<String> banned = (List<String>) data.get("banned_players");
        if (banned != null && banned.contains(pb.targetPlayer)) {
            player.sendMessage("§c该玩家已被封禁！");
            return;
        }

        // 存入待处理
        pendingBans.put(player.getUniqueId(), pb);
        player.sendMessage("§e⚠ 确认封禁玩家 §b" + pb.targetPlayer + " §e？");
        player.sendMessage("§7时间: " + formatDuration(pb.duration));
        player.sendMessage("§7原因: " + pb.reason);
        player.sendMessage("§e在聊天栏输入 §f t/yes/确认 §e确认（60秒内有效），其他内容取消");
    }

    private void handleMUnban(Player player, Map<String, Object> data, String name, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /pw m " + name + " unban <玩家名>");
            return;
        }
        String target = args[3];
        @SuppressWarnings("unchecked")
        List<String> banned = (List<String>) data.get("banned_players");
        if (banned == null || !banned.remove(target)) {
            player.sendMessage("§c该玩家未被封禁！"); return;
        }
        if (saveWaypointData(name, data)) {
            player.sendMessage("§a已解禁玩家 §b" + target);
        }
    }

    /** 解析时长字符串 */
    private long parseDuration(String str) {
        try {
            if (str.equals("-1")) return -1;
            
            char unit = Character.toLowerCase(str.charAt(str.length() - 1));
            long value = Long.parseLong(str.substring(0, str.length() - 1));
            
            return switch (unit) {
                case 's' -> value; // 秒
                case 'm' -> value * 60; // 分钟
                case 'h' -> value * 3600; // 小时
                case 'd' -> value * 86400; // 天
                default -> Long.MIN_VALUE;
            };
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    /** 格式化时长显示 */
    private String formatDuration(long seconds) {
        if (seconds == -1) return "永久";
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        if (seconds < 86400) return (seconds / 3600) + "小时";
        return (seconds / 86400) + "天";
    }

    /** 确认封禁 */
    public boolean confirmBan(Player player) {
        PendingBan pb = pendingBans.remove(player.getUniqueId());
        if (pb == null) return false;
        if (System.currentTimeMillis() > pb.expiry) {
            player.sendMessage("§c确认已超时！");
            return true;
        }

        Map<String, Object> data = loadWaypointData(pb.waypointName);
        if (data == null) {
            player.sendMessage("§c路径点数据已丢失！");
            return true;
        }

        @SuppressWarnings("unchecked")
        List<String> banned = (List<String>) data.get("banned_players");
        if (banned == null) {
            banned = new ArrayList<>();
            data.put("banned_players", banned);
        }
        banned.add(pb.targetPlayer);

        // 存储封禁信息（可选：用于到期自动解封）
        if (pb.duration != -1) {
            long unbanTime = System.currentTimeMillis() + (pb.duration * 1000);
            @SuppressWarnings("unchecked")
            Map<String, Object> banInfo = (Map<String, Object>) data.get("ban_info");
            if (banInfo == null) {
                banInfo = new HashMap<>();
                data.put("ban_info", banInfo);
            }
            banInfo.put(pb.targetPlayer, Map.of(
                "unban_time", unbanTime,
                "reason", pb.reason
            ));
        }

        if (saveWaypointData(pb.waypointName, data)) {
            player.sendMessage("§a已封禁玩家 §b" + pb.targetPlayer + " §a使用路径点 §b" + pb.waypointName);
            player.sendMessage("§7时间: " + formatDuration(pb.duration));
            player.sendMessage("§7原因: " + pb.reason);
        } else {
            player.sendMessage("§c封禁失败！");
        }
        return true;
    }

    // ========== icon 管理 ==========

    private void handleMSetLocation(Player player, Map<String, Object> data, String name) {
        setPendingSetLocation(player, name);
    }

    // ========== 设置传送位置确认 ==========

    public static class PendingSetLocation {
        public final String waypointName;
        public final long expiry;

        public PendingSetLocation(String waypointName, long expiry) {
            this.waypointName = waypointName;
            this.expiry = expiry;
        }
    }

    public final Map<UUID, PendingSetLocation> pendingSetLocation = new HashMap<>();

    public void setPendingSetLocation(Player player, String waypointName) {
        pendingSetLocation.put(player.getUniqueId(),
                new PendingSetLocation(waypointName, System.currentTimeMillis() + 10000));
        player.sendMessage("§e⚠ 确认将 §b" + waypointName + " §e的传送位置设为当前位置？");
        player.sendMessage("§e在聊天栏输入 §f t yes §e确认（10秒内有效），输入其他内容取消");
    }

    public boolean hasPendingSetLocation(Player player) {
        PendingSetLocation p = pendingSetLocation.get(player.getUniqueId());
        if (p == null) return false;
        if (System.currentTimeMillis() > p.expiry) {
            pendingSetLocation.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** 处理 t yes 确认，设置为玩家当前位置 */
    public boolean confirmSetLocation(Player player) {
        PendingSetLocation p = pendingSetLocation.remove(player.getUniqueId());
        if (p == null) return false;
        if (System.currentTimeMillis() > p.expiry) {
            player.sendMessage("§c确认已超时！");
            return true;
        }
        Map<String, Object> data = loadWaypointData(p.waypointName);
        if (data == null) {
            player.sendMessage("§c路径点数据已丢失！");
            return true;
        }
        Location loc = player.getLocation();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", (double) loc.getYaw());
        data.put("pitch", (double) loc.getPitch());
        if (saveWaypointData(p.waypointName, data)) {
            player.sendMessage("§a已将 §b" + p.waypointName + " §a的传送位置设为当前位置！");
        } else {
            player.sendMessage("§c更新位置失败！");
        }
        return true;
    }

    private void handleMDescription(Player player, Map<String, Object> data, String name, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /pw m " + name + " description <描述>");
            return;
        }
        String desc = processDescription(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
        data.put("description", desc);
        if (saveWaypointData(name, data)) {
            player.sendMessage("§a已更新路径点 §b" + name + " §a的描述！");
        } else {
            player.sendMessage("§c更新描述失败！");
        }
    }

    private void handleMList(Player player, Map<String, Object> data, String name) {
        List<Map.Entry<String, Integer>> visitors = getSortedVisitors(data);
        if (visitors.isEmpty()) {
            player.sendMessage("§c该路径点暂无访问记录！");
            return;
        }
        int total = visitors.stream().mapToInt(Map.Entry::getValue).sum();
        player.sendMessage("§6===== 访问记录 - §b" + name + " §6=====");
        player.sendMessage("§7总访问次数: §e" + total);
        for (Map.Entry<String, Integer> entry : visitors) {
            player.sendMessage("§f" + entry.getKey() + " §7- §e" + entry.getValue() + " §7次");
        }
    }

    // ========== icon 管理 ==========

    // 待设置图标的玩家: UUID → 路径点名
    public final Map<UUID, String> pendingIconPlayers = new HashMap<>();

     /** 设置玩家为等待图标输入状态 */
    public void setPendingIcon(Player player, String waypointName) {
        pendingIconPlayers.put(player.getUniqueId(), waypointName);
        player.sendMessage("§a请在聊天栏手持要设置的物品，发送任意内容设置图标！");
        player.sendMessage("§7（输入 §c取消 §7或 §cno §7取消设置）");
    }

    private void handleMIcon(Player player, Map<String, Object> data, String name) {
        setPendingIcon(player, name);
    }

    /** 处理图标设置聊天（TpAsMe 调用） */
    public boolean trySetIcon(Player player, String name) {
        String wpName = pendingIconPlayers.remove(player.getUniqueId());
        if (wpName == null) return false;

        // 从聊天调用时name为空，使用pending中存储的名称
        String targetName = (name == null || name.isEmpty()) ? wpName : name;
        if (!targetName.equals(wpName)) return false;

        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§c手中没有物品！");
            return true;
        }
        Map<String, Object> data = loadWaypointData(targetName);
        if (data == null) {
            player.sendMessage("§c路径点数据已丢失！");
            return true;
        }
        data.put("icon", hand.getType().name());
        if (saveWaypointData(targetName, data)) {
            player.sendMessage("§a已设置路径点 §b" + targetName + " §a图标为 " + getIconDisplayName(hand.getType()));
        }
        return true;
    }

    public boolean isPendingIcon(Player player) {
        return pendingIconPlayers.containsKey(player.getUniqueId());
    }

    // ========== 数据保存（供GUI和管理命令共用）==========

    public boolean saveWaypointData(String name, Map<String, Object> data) {
        File pwFile = getWaypointFile(name);
        try (FileWriter writer = new FileWriter(pwFile)) {
            gson.toJson(data, writer);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("§c[PublicWaypoint] 保存路径点 " + name + " 失败: " + e.getMessage());
            return false;
        }
    }

    /** 获取路径点图标材质（兼容旧数据） */
    public static String getWaypointIcon(Map<String, Object> data) {
        String icon = (String) data.get("icon");
        if (icon != null) return icon;
        String cat = (String) data.get("category");
        if (cat == null) return "ENDER_PEARL";
        return switch (cat) {
            case "刷怪塔" -> "ZOMBIE_HEAD";
            case "建筑" -> "STONE_BRICKS";
            case "机器" -> "PISTON";
            case "商店" -> "CHEST";
            default -> "COMPASS";
        };
    }

    public static String getIconDisplayName(org.bukkit.Material mat) {
        return switch (mat) {
            case SPAWNER -> "§c生成笼";
            case BRICK -> "§6砖块";
            case PISTON -> "§8活塞";
            case EMERALD -> "§a绿宝石";
            case COMPASS -> "§e指南针";
            case ENDER_PEARL -> "§d末影珍珠";
            default -> "§f" + mat.getKey().getKey();
        };
    }

    /** 记录访问者 */
    public void recordVisit(Map<String, Object> data, String playerName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> visitors = (Map<String, Object>) data.get("visitors");
        if (visitors == null) {
            visitors = new LinkedHashMap<>();
            data.put("visitors", visitors);
        }
        int count = ((Number) visitors.getOrDefault(playerName, 0)).intValue();
        visitors.put(playerName, count + 1);
    }

    /** 获取排序后的访问者列表 */
    @SuppressWarnings("unchecked")
    public static List<Map.Entry<String, Integer>> getSortedVisitors(Map<String, Object> data) {
        Map<String, Object> visitors = (Map<String, Object>) data.get("visitors");
        if (visitors == null) return Collections.emptyList();
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, Object> e : visitors.entrySet()) {
            list.add(Map.entry(e.getKey(), ((Number) e.getValue()).intValue()));
        }
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }

    // ========== 数据读写 ==========

    public File getWaypointFile(String name) {
        // 文件名过滤，防止路径穿越
        String safeName = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
        return new File(pwDir, safeName + ".json");
    }

    public Map<String, Object> loadWaypointData(String name) {
        File file = getWaypointFile(name);
        if (!file.exists()) return null;

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, mapType);
        } catch (IOException e) {
            plugin.getLogger().warning("§c[PublicWaypoint] 读取路径点 " + name + " 失败: " + e.getMessage());
            return null;
        }
    }

    public Location deserializeLocation(Map<String, Object> data) {
        String worldName = (String) data.get("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = ((Number) data.get("x")).doubleValue();
        double y = ((Number) data.get("y")).doubleValue();
        double z = ((Number) data.get("z")).doubleValue();
        float yaw = ((Number) data.get("yaw")).floatValue();
        float pitch = ((Number) data.get("pitch")).floatValue();

        return new Location(world, x, y, z, yaw, pitch);
    }

    /** 加载所有路径点数据 */
    public List<Map<String, Object>> loadAllWaypoints() {
        List<Map<String, Object>> result = new ArrayList<>();
        File[] files = pwDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = gson.fromJson(reader, mapType);
                if (data != null) result.add(data);
            } catch (IOException e) {
                plugin.getLogger().warning("§c[PublicWaypoint] 读取文件 " + file.getName() + " 失败: " + e.getMessage());
            }
        }
        return result;
    }

    /** 按分类筛选路径点 */
    public List<Map<String, Object>> loadWaypointsByCategory(String category) {
        if (category.equals("全部")) return loadAllWaypoints();
        return loadAllWaypoints().stream()
                .filter(d -> category.equals(d.get("category")))
                .collect(Collectors.toList());
    }

    /** 创建路径点（供GUI调用） */
    public boolean createWaypoint(String name, String creator, String category, Location loc) {
        return createWaypoint(name, creator, category, loc, "");
    }

    /** 创建路径点（含描述） */
    public boolean createWaypoint(String name, String creator, String category, Location loc, String description) {
        File pwFile = getWaypointFile(name);
        if (pwFile.exists()) return false;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("creator", creator);
        data.put("category", category);
        data.put("permission", "公开");
        data.put("banned_players", new ArrayList<String>());
        data.put("created_at", System.currentTimeMillis());
        data.put("icon", getWaypointIcon(data));
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", (double) loc.getYaw());
        data.put("pitch", (double) loc.getPitch());
        if (description != null && !description.isEmpty()) data.put("description", description);

        try (FileWriter writer = new FileWriter(pwFile)) {
            gson.toJson(data, writer);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("§c[PublicWaypoint] 保存路径点 " + name + " 失败: " + e.getMessage());
            return false;
        }
    }

    public File getPwDir() { return pwDir; }

    // ========== Tab补全 ==========

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return null;

        // 列出所有已创建的路径点名称
        File[] files = pwDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();

        List<String> waypointNames = new ArrayList<>();
        for (File file : files) {
            String fileName = file.getName();
            String name = fileName.substring(0, fileName.length() - 5); // 去掉 .json
            waypointNames.add(name);
        }

        // pwtp 补全公开路径点
        if (command.getName().equalsIgnoreCase("pwtp")) {
            if (args.length <= 1) {
                String partial = args.length == 0 ? "" : args[0].toLowerCase();
                List<String> result = new ArrayList<>();
                for (String n : waypointNames) {
                    if (n.toLowerCase().startsWith(partial)) {
                        // 只显示公开的
                        Map<String, Object> d = loadWaypointData(n);
                        if (d != null && "公开".equals(d.get("permission"))) {
                            result.add(n);
                        }
                    }
                }
                return result;
            }
            return Collections.emptyList();
        }

        if (args.length <= 1) {
            // 补全子命令
            String[] subCommands = {"create", "创建", "tp", "传送", "remove", "删除", "tremove", "确认删除", "m", "manage", "管理"};
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String sub : subCommands) {
                if (sub.startsWith(partial)) {
                    result.add(sub);
                }
            }
            // 只补全子命令，不补全路径名（防止误输入）
            return result;
        }

        // args.length >= 2
        String subCmd = args[0].toLowerCase();

        // 如果第一个参数是create/创建，补全分类
        if ((subCmd.equals("create") || subCmd.equals("创建")) && args.length == 2) {
            String partial = args[1].toLowerCase();
            List<String> catResult = new ArrayList<>();
            for (String cat : CATEGORIES) {
                if (cat.toLowerCase().startsWith(partial)) {
                    catResult.add(cat);
                }
            }
            return catResult;
        }

        // tp/传送 / remove/删除 / tremove/确认删除 补全路径点名称
        if (subCmd.equals("tp") || subCmd.equals("传送") || subCmd.equals("传") ||
                subCmd.equals("remove") || subCmd.equals("删除") ||
                subCmd.equals("tremove") || subCmd.equals("确认删除")) {
            List<String> result = new ArrayList<>();
            String partial = args[1].toLowerCase();
            for (String name : waypointNames) {
                if (name.toLowerCase().startsWith(partial)) {
                    result.add(name);
                }
            }
            return result;
        }

        // m/manage/管理 补全子操作
        if (subCmd.equals("m") || subCmd.equals("manage") || subCmd.equals("管理")) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase();
                List<String> result = new ArrayList<>();
                for (String n : waypointNames) {
                    if (n.toLowerCase().startsWith(partial)) result.add(n);
                }
                return result;
            }
            if (args.length == 3) {
                String partial = args[2].toLowerCase();
                List<String> ops = List.of("rename", "改名", "delete", "删除", "permission", "权限", "ban", "封禁", "unban", "解禁", "icon", "图标", "setlocation", "setloc", "设置位置", "description", "desc", "描述", "list", "列表");
                List<String> result = new ArrayList<>();
                for (String op : ops) {
                    if (op.startsWith(partial)) result.add(op);
                }
                return result;
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("permission") || args[2].equalsIgnoreCase("权限"))) {
                String partial = args[3].toLowerCase();
                List<String> result = new ArrayList<>();
                for (String p : List.of("公开", "私密")) {
                    if (p.startsWith(partial)) result.add(p);
                }
                return result;
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("unban") || args[2].equalsIgnoreCase("解禁"))) {
                String partial = args[3].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("ban") || args[2].equalsIgnoreCase("封禁"))) {
                String partial = args[3].toLowerCase();
                List<String> result = new ArrayList<>();
                // 提示格式
                if ("玩".startsWith(partial)) result.add("玩家:");
                if ("时".startsWith(partial)) result.add("时间:");
                if ("原".startsWith(partial)) result.add("原因:");
                return result;
            }
            if (args.length >= 5 && (args[2].equalsIgnoreCase("ban") || args[2].equalsIgnoreCase("封禁"))) {
                // 已输入部分参数，继续提示剩余参数
                boolean hasPlayer = false, hasTime = false, hasReason = false;
                for (int i = 3; i < args.length - 1; i++) {
                    if (args[i].startsWith("玩家:")) hasPlayer = true;
                    else if (args[i].startsWith("时间:")) hasTime = true;
                    else if (args[i].startsWith("原因:")) hasReason = true;
                }
                String partial = args[args.length - 1].toLowerCase();
                List<String> result = new ArrayList<>();
                if (!hasPlayer && "玩".startsWith(partial)) result.add("玩家:");
                if (!hasTime && "时".startsWith(partial)) result.add("时间:");
                if (!hasReason && "原".startsWith(partial)) result.add("原因:");
                return result;
            }
            // description/list/icon/setlocation/delete/rename 不需要第4个参数或第4个参数是自由文本，不补全
            return Collections.emptyList();
        }

        return null;
    }
}
