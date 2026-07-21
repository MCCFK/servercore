package com.apple.servercore;

import com.apple.servercore.acmusic.Music;
import com.apple.servercore.economicsystem.*;
import com.apple.servercore.guild.Guilds;
import com.apple.servercore.Gift.Gift;
import com.apple.servercore.Gift.GiftListener;
import com.apple.servercore.ranking.Ranking;
import com.mccfk.plugin.commands.*;
import com.mccfk.plugin.commands.CarryCommand;
import com.mccfk.plugin.invite.InviteCodeManager;
import com.mccfk.plugin.invite.InviteCommand;
import com.mccfk.plugin.listeners.PlayerListener;
import com.mccfk.plugin.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bukkit.GameMode.SURVIVAL;
import static org.bukkit.Material.*;

public class MainPlugin extends JavaPlugin {

    // ====================== 核心模块 ======================
    // 【已删除】titleManager
    public Guilds guildManager;
    public EconomicSystem economicSystem;
    public TpAsMe tpAsMe;
    public TpAsMePoint tpAsMePoint;
    public Gift gift;
    public Music music;
    private ACcraft acCraft;
    public Ranking ranking;
    public PersonalSettings personalSettings;
    public InviteCodeManager inviteCodeManager;

    // ====================== MCCFK 模块 ======================
    private PlayerDataManager playerDataManager;
    private final Set<UUID> openWorkbenchPlayers = new HashSet<>();
    public com.mccfk.plugin.managers.ActionManager actionManager;
    public com.mccfk.plugin.commands.CarryCommand carryCommand;
    public com.mccfk.plugin.commands.AdminCatcherCommand adminCatcherCommand;

    // 服务器配置
    public String serverName = "§5Architecture Craft";
    public String qqGroup = "895992564";

    // 公告书本
    private File bookConfigFile;
    private FileConfiguration bookConfig;

    // AC币商店分页
    private final Map<UUID, Integer> playerShopPages = new HashMap<>();

    public final Map<UUID, Boolean> announceToggle = new HashMap<>();

    // ====================== 插件启用 ======================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "accoin" -> Arrays.asList("see","give","take","set");
            case "guild" -> guildManager.onTabComplete(sender, cmd, label, args);
            case "acservercore" -> List.of("reload");
            default -> Collections.emptyList();
        };
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("插件文件夹创建失败！");
        }

        // 初始化配置
        initConfigFiles();

        // =========================================================
        // 【重要】优先初始化经济系统（包含 PAPI 变量注册）
        // 必须放在最前面，确保在 onEnable 同步阶段完成注册
        // =========================================================
        economicSystem = new EconomicSystem(this);

        // 个人设置（包含PVP等）
        personalSettings = new PersonalSettings(this);

        // ======================
        // 【已删除】称号系统初始化
        // ======================

        // 初始化其他系统
        guildManager = new Guilds(this);
        tpAsMe = new TpAsMe(this, guildManager);
        tpAsMePoint = new TpAsMePoint(this);
        music = new Music(this);
        gift = new Gift(this);
        new QuickMenuListener(this);

        // 主菜单 & 排行榜
        acCraft = new ACcraft(this);
        new Hants(this);
        ranking = new Ranking(this);
        getCommand("accraft").setExecutor(acCraft);

        // 邀请码系统
        inviteCodeManager = new InviteCodeManager(this);

        // 动作管理（骑行/坐下/趴下/躺下）
        actionManager = new com.mccfk.plugin.managers.ActionManager(this);

        // 注册指令 & 监听器
        registerCommands();
        registerListeners();

        // ====================== MCCFK 模块初始化 ======================
        playerDataManager = new PlayerDataManager();
        playerDataManager.init(getDataFolder(), getLogger());

        try {
            MsgCommand msgCommand = new MsgCommand(this);

            setExecutorIfExists("rtp", new RTPCommand(this));
            setExecutorIfExists("back", new BackCommand(this));
            setExecutorIfExists("dback", new DBackCommand(this));
            setExecutorIfExists("enderchest", new EnderChestCommand());
            setExecutorIfExists("craftingtable", new CraftingTableCommand(this));
            setExecutorIfExists("suicide", new SuicideCommand());
            setExecutorIfExists("msg", msgCommand);
            setExecutorIfExists("w", msgCommand);

            // ========== 便捷动作指令 ==========
            setExecutorIfExists("ride", new com.mccfk.plugin.commands.RideCommand(this));
            setExecutorIfExists("sit", new com.mccfk.plugin.commands.SitCommand(this));

            // ========== 搬运指令 ==========
            carryCommand = new CarryCommand(this);
            setExecutorIfExists("carry", carryCommand);
            setExecutorIfExists("fuckcarry", carryCommand);
            setExecutorIfExists("unfuckcarry", carryCommand);
            setExecutorIfExists("bancarry", carryCommand);
            setExecutorIfExists("unbancarry", carryCommand);
            getServer().getPluginManager().registerEvents(carryCommand, this);

            // ========== 管理员生物捕捉器 ==========
            adminCatcherCommand = new AdminCatcherCommand(this, carryCommand);
            setExecutorIfExists("admincatcher", adminCatcherCommand);
            getServer().getPluginManager().registerEvents(adminCatcherCommand, this);

            // ========== 获取技术实体 ==========
            TechEntityCommand techEntityCommand = new TechEntityCommand(this, carryCommand);
            setExecutorIfExists("获取技术实体", techEntityCommand);

            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        } catch (Exception e) {
            getLogger().severe("MCCFK 模块初始化失败: " + e.getMessage());
            e.printStackTrace();
        }

        // ========== 额外兜底方案 ==========
        // 插件全部加载完成后二次注册，确保 PAPI 缓存捕获
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (economicSystem != null && economicSystem.getAppleCoins() != null) {
                economicSystem.getAppleCoins().registerPlaceholderAPI();
                getLogger().info("[兜底] 二次注册苹果币PAPI变量");
            }
        }, 1);

        getLogger().info("§aServerCore 插件已成功启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("§e开始保存所有模块数据...");

        // 1. 停止插件全部定时任务，公会内部任务由 shutdownSave 统一关闭
        try {
            Bukkit.getScheduler().cancelTasks(this);
            getLogger().info("§7所有定时任务已停止");
        } catch (Exception e) {
            getLogger().severe("停止定时任务失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. 有序保存任务队列
        Map<String, Runnable> saveTasks = new LinkedHashMap<>();

        // ========== MCCFK 清理 ==========
        saveTasks.put("MCCFK工作台数据", () -> openWorkbenchPlayers.clear());
        saveTasks.put("管理员捕捉器", () -> {
            if (adminCatcherCommand != null) adminCatcherCommand.shutdownCleanup();
        });
        saveTasks.put("玩家家数据", () -> {
            if (playerDataManager != null) playerDataManager.saveHomes();
        });

        // ========== TpAsMe 清理任务（先停止，避免在保存过程中干扰） ==========
        saveTasks.put("TpAsMe清理任务", () -> {
            if (tpAsMe != null) {
                try {
                    tpAsMe.stopCleanupTask();
                    getLogger().info("  §7TpAsMe 清理任务已停止");
                } catch (Exception e) {
                    getLogger().severe("  §c停止 TpAsMe 清理任务失败: " + e.getMessage());
                }
            }
        });

        // 【已删除】称号保存

        // 公会模块：shutdownSave 内部自动停止自身任务 + 强制同步落地全部数据
        saveTasks.put("公会", () -> {
            if (guildManager != null) guildManager.shutdownSave();
        });

        // 经济相关模块
        // 【已修改】saveAcCoinData() → saveLocalBackup()
        saveTasks.put("经济AC币", () -> {
            if (economicSystem != null) economicSystem.saveLocalBackup();
        });
        saveTasks.put("苹果币", () -> {
            if (economicSystem != null && economicSystem.getAppleCoins() != null) {
                economicSystem.getAppleCoins().saveData();
            }
        });
        saveTasks.put("飞行时长", () -> {
            if (economicSystem != null && economicSystem.getAcFly() != null) {
                economicSystem.getAcFly().saveData();
            }
        });

        saveTasks.put("传送积分", () -> {
            if (tpAsMePoint != null) tpAsMePoint.saveConfig();
        });
        saveTasks.put("玩家个人设置", () -> {
            if (personalSettings != null) personalSettings.saveSettings();
        });
        saveTasks.put("邀请码数据", () -> {
            if (inviteCodeManager != null) inviteCodeManager.saveCodes();
        });
        saveTasks.put("服务器配置", this::saveServerConfig);

        int success = 0, fail = 0;
        long totalStart = System.currentTimeMillis();
        int totalModule = saveTasks.size();

        for (Map.Entry<String, Runnable> entry : saveTasks.entrySet()) {
            String moduleName = entry.getKey();
            long start = System.currentTimeMillis();
            try {
                getLogger().info("  §7正在保存【" + moduleName + "】数据...");
                entry.getValue().run();
                long cost = System.currentTimeMillis() - start;
                success++;
                getLogger().info("  §a✅ 【" + moduleName + "】保存完成，耗时 " + cost + " ms");
            } catch (Exception e) {
                fail++;
                getLogger().severe("  §c❌ 【" + moduleName + "】保存失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        long totalCost = System.currentTimeMillis() - totalStart;
        getLogger().info("§e========================================");
        if (fail == 0) {
            getLogger().info("§a✅ 全部 " + totalModule + " 个模块数据保存成功，总耗时 " + totalCost + " ms");
        } else {
            getLogger().warning("§e⚠️ 保存汇总：总计 " + totalModule + " 个模块，成功 " + success + " 个，失败 " + fail + " 个，总耗时 " + totalCost + " ms");
            getLogger().warning("§e⚠️ 请检查上方错误日志，失败模块数据可能丢失！");
        }
        getLogger().info("§e========================================");
        getLogger().info("§cServerCore 插件已正常关闭！");
    }

    // ====================== 配置文件 ======================
    private void initConfigFiles() {
        File serverFile = new File(getDataFolder(), "server_config.yml");
        FileConfiguration serverCfg = YamlConfiguration.loadConfiguration(serverFile);
        serverName = serverCfg.getString("server-name", serverName);
        qqGroup = serverCfg.getString("qq-group", qqGroup);

        if (!serverFile.exists()) {
            serverCfg.set("server-name", serverName);
            serverCfg.set("qq-group", qqGroup);
            try {
                serverCfg.save(serverFile);
            } catch (IOException e) {
                getLogger().severe("保存服务器配置失败");
            }
        }

        bookConfigFile = new File(getDataFolder(), "book.yml");
        bookConfig = YamlConfiguration.loadConfiguration(bookConfigFile);
        if (!bookConfigFile.exists()) {
            bookConfig.set("page1", "§6=== 服务器公告 ===");
            bookConfig.set("page2", "§a欢迎来到服务器");
            try {
                bookConfig.save(bookConfigFile);
            } catch (IOException e) {
                getLogger().severe("保存公告失败");
            }
        }
    }

    // ====================== 指令注册 ======================
    private void registerCommands() {
        // ========== guild 命令单独注册到 guildManager ==========
        try {
            PluginCommand guildCmd = getCommand("guild");
            if (guildCmd != null) {
                guildCmd.setExecutor(guildManager);
                guildCmd.setTabCompleter(guildManager);
                getLogger().info("✅ guild 命令已注册到 Guilds");
            } else {
                getLogger().severe("❌ guild 命令未在 plugin.yml 中注册！");
            }
        } catch (Exception e) {
            getLogger().severe("注册 guild 命令失败: " + e.getMessage());
        }

        // ========== 其他命令 ==========
        Map<String, CommandExecutor> commands = new HashMap<>();
        commands.put("actp", tpAsMe);
        commands.put("actpasme", tpAsMePoint);
        commands.put("acservercore", this);
        commands.put("gift", this);
        commands.put("pvp", this);
        commands.put("公告", this);
        commands.put("accoin", this);

        for (Map.Entry<String, CommandExecutor> entry : commands.entrySet()) {
            try {
                PluginCommand cmd = getCommand(entry.getKey());
                if (cmd != null) {
                    cmd.setExecutor(entry.getValue());
                    if (entry.getValue() instanceof TabCompleter tc) {
                        cmd.setTabCompleter(tc);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("注册命令 " + entry.getKey() + " 失败: " + e.getMessage());
            }
        }

        // ========== 新增：传送请求指令（注册到 TpAsMe） ==========
        String[] tpCommands = {"tpa", "tphere", "tpaaccept", "tpadeny"};
        for (String cmdName : tpCommands) {
            try {
                PluginCommand cmd = getCommand(cmdName);
                if (cmd != null) {
                    cmd.setExecutor(tpAsMe);
                    cmd.setTabCompleter(tpAsMe);
                    getLogger().info("✅ " + cmdName + " 命令已注册到 TpAsMe");
                } else {
                    getLogger().warning("⚠️ 命令 " + cmdName + " 未在 plugin.yml 中定义！");
                }
            } catch (Exception e) {
                getLogger().warning("注册命令 " + cmdName + " 失败: " + e.getMessage());
            }
        }

        // ========== 苹果币和飞行指令 ==========
        try {
            AppleCoinsCommand appleCoinsCmd = new AppleCoinsCommand(this);
            PluginCommand appleCoinsPluginCmd = getCommand("applecoins");
            if (appleCoinsPluginCmd != null) {
                appleCoinsPluginCmd.setExecutor(appleCoinsCmd);
                appleCoinsPluginCmd.setTabCompleter(appleCoinsCmd);
                getLogger().info("✅ applecoins 命令已注册");
            }

            ACFlyCommand acFlyCmd = new ACFlyCommand(this);
            PluginCommand acFlyPluginCmd = getCommand("acfly");
            if (acFlyPluginCmd != null) {
                acFlyPluginCmd.setExecutor(acFlyCmd);
                getLogger().info("✅ acfly 命令已注册");
            }
        } catch (Exception e) {
            getLogger().severe("注册苹果币/飞行指令失败: " + e.getMessage());
        }

        // ========== 邀请码指令 ==========
        try {
            InviteCommand inviteCmd = new InviteCommand(this, inviteCodeManager);
            PluginCommand invitePluginCmd = getCommand("invite");
            if (invitePluginCmd != null) {
                invitePluginCmd.setExecutor(inviteCmd);
                invitePluginCmd.setTabCompleter(inviteCmd);
                getLogger().info("✅ invite 命令已注册");
            }
        } catch (Exception e) {
            getLogger().severe("注册 invite 命令失败: " + e.getMessage());
        }
    }

    // ====================== 指令处理 ====================
    private boolean handleGuild(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可用");
            return true;
        }

        if (args.length == 0) {
            showGuildHelp(p);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "create" -> guildCreate(p, args);
            case "apply" -> guildApply(p, args);
            case "leave" -> guildLeave(p);
            case "disband" -> guildDisband(p);
            case "chat" -> guildChat(p, args);
            case "list" -> guildList(p);
            case "info" -> guildInfo(p, args);
            case "invite" -> guildInvite(p, args);
            case "kick" -> guildKick(p, args);
            case "accept" -> guildAccept(p, args);
            case "decline" -> guildDecline(p, args);
            case "applylist" -> guildApplyList(p);
            case "acceptinvite" -> {
                if (args.length < 2) {
                    p.sendMessage("§c/guild acceptinvite <公会名>");
                    yield true;
                }
                guildManager.acceptInvite(p, args[1]);
                yield true;
            }
            case "declineguild" -> {
                if (args.length < 2) {
                    p.sendMessage("§c/guild declineguild <公会名>");
                    yield true;
                }
                guildManager.declineGuild(p, args[1]);
                yield true;
            }
            default -> {
                showGuildHelp(p);
                yield true;
            }
        };
    }

    // ====================== 指令处理 ======================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "acservercore" -> handleCore(sender, args);
            case "gift" -> handleGift(sender);
            case "pvp" -> handlePvp(sender);
            case "公告" -> handleBook(sender);
            case "guild" -> handleGuild(sender, args);
            case "accoin" -> handleAccoin(sender, args);
            default -> false;
        };
    }

    // ====================== AC币管理 ======================
    private boolean handleAccoin(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c你没有权限使用此指令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("""
                    §6===== AC币管理指令 =====
                    §e/accoin see <玩家> §7查看玩家AC币
                    §e/accoin give <玩家> <数量> §7给予AC币
                    §e/accoin take <玩家> <数量> §7扣除AC币
                    §e/accoin set <玩家> <数量> §7设置AC币""");
            return true;
        }

        Player target;
        int amount;

        switch (args[0].toLowerCase()) {
            case "see" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /accoin see <玩家>");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或不存在！");
                    return true;
                }
                sender.sendMessage("§a" + target.getName() + " 的AC币: §f" + economicSystem.getAcCoins(target));
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /accoin give <玩家> <数量>");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或不存在！");
                    return true;
                }
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                    return true;
                }
                if (amount < 0) {
                    sender.sendMessage("§c数量不能为负数！");
                    return true;
                }
                economicSystem.addAcCoins(target, amount);
                sender.sendMessage("§a成功给予 " + target.getName() + " " + amount + " AC币！");
                target.sendMessage("§a管理员给予了你 " + amount + " AC币！");
            }
            case "take" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /accoin take <玩家> <数量>");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或不存在！");
                    return true;
                }
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                    return true;
                }
                if (amount < 0) {
                    sender.sendMessage("§c数量不能为负数！");
                    return true;
                }
                boolean success = economicSystem.removeAcCoins(target, amount);
                if (success) {
                    sender.sendMessage("§a成功扣除 " + target.getName() + " " + amount + " AC币！");
                    target.sendMessage("§c管理员扣除了你 " + amount + " AC币！");
                } else {
                    sender.sendMessage("§c玩家AC币不足！");
                }
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /accoin set <玩家> <数量>");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或不存在！");
                    return true;
                }
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c数量必须是数字！");
                    return true;
                }
                amount = Math.max(amount, 0);
                economicSystem.setAcCoins(target, amount);
                sender.sendMessage("§a成功将 " + target.getName() + " 的AC币设置为: " + amount);
                target.sendMessage("§e管理员将你的AC币设置为: " + amount);
            }
            default -> sender.sendMessage("§c未知指令，输入 /accoin 查看帮助");
        }
        return true;
    }

    // ====================== 指令实现 ======================
    private boolean handleCore(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c无权");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§c用法: /acservercore reload");
            return true;
        }

        try {
            // 1. 重载插件配置
            reloadConfig();
            sender.sendMessage("§7✓ 插件配置已重载");

            // 2. 重载经济数据
            // 【已修改】loadAcCoinData() → loadLocalBackup()
            economicSystem.loadLocalBackup();
            sender.sendMessage("§7✓ AC币数据已重载");

            // 3. 重载公会数据
            guildManager.loadGuilds();
            sender.sendMessage("§7✓ 公会数据已重载");

            // 4. 重载传送点数据
            tpAsMePoint.loadAllData();
            sender.sendMessage("§7✓ 传送点数据已重载");

            // 5. 重载签到数据
            gift.reloadConfig();
            sender.sendMessage("§7✓ 签到配置已重载");

            // 6. 重载个人设置
            personalSettings.loadSettings();
            sender.sendMessage("§7✓ 个人设置已重载");

            // 【已删除】计分板更新

            sender.sendMessage("§a✅ 插件全部模块重载完成！");

        } catch (Exception e) {
            sender.sendMessage("§c❌ 重载过程中发生错误，请查看后台日志");
            getLogger().severe("重载失败: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private boolean handleGift(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家");
            return true;
        }
        gift.openGiftUI(p);
        return true;
    }

    private boolean handlePvp(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        personalSettings.togglePvp(p);
        return true;
    }

    boolean handleBook(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        ItemStack book = new ItemStack(WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return true;

        meta.setTitle("§6服务器公告");
        meta.setAuthor("§e系统");
        List<Component> pages = new ArrayList<>();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        for (String s : getBookContent()) {
            pages.add(serializer.deserialize(s));
        }
        meta.pages(pages);
        book.setItemMeta(meta);
        p.openBook(book);
        return true;
    }

    // ====================== 工会功能 ======================
    private void showGuildHelp(Player p) {
        p.sendMessage("""
            §6===== 工会帮助 =====
            §e/guild create <名字> §7创建
            §e/guild apply <名字> §7申请加入
            §e/guild leave §7离开
            §e/guild disband §7解散
            §e/guild chat <消息> §7聊天
            §e/guild list §7列表
            §e/guild info [公会名] §7信息
            §e/guild invite <玩家> §7邀请
            §e/guild kick <玩家> §7踢出
            §e/guild accept <玩家> §7同意申请
            §e/guild decline <玩家> §7拒绝申请
            §e/guild applylist §7申请列表""");
    }

    private boolean guildCreate(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入名字"); return true; }
        guildManager.create(p, a[1]);
        // 【已删除】personalSettings.updatePlayerScoreboard(p);
        return true;
    }

    private boolean guildApply(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入工会名"); return true; }
        guildManager.apply(p, a[1]);
        return true;
    }

    private boolean guildLeave(Player p) {
        guildManager.leave(p);
        // 【已删除】personalSettings.updatePlayerScoreboard(p);
        return true;
    }

    private boolean guildDisband(Player p) {
        guildManager.disband(p);
        // 【已删除】personalSettings.updatePlayerScoreboard(p);
        return true;
    }

    private boolean guildChat(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入消息"); return true; }
        String message = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        guildManager.chat(p, message);
        return true;
    }

    public boolean guildList(Player p) {
        guildManager.list(p);
        return true;
    }

    public boolean guildInfo(Player p, String[] a) {
        if (a.length < 2) {
            guildManager.info(p, null);
        } else {
            guildManager.info(p, a[1]);
        }
        return true;
    }

    private boolean guildInvite(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入玩家"); return true; }
        Player target = Bukkit.getPlayer(a[1]);
        if (target == null) { p.sendMessage("§c不在线"); return true; }
        guildManager.invite(p, target);
        return true;
    }

    private boolean guildKick(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入玩家"); return true; }
        Player target = Bukkit.getPlayer(a[1]);
        if (target == null) { p.sendMessage("§c不在线"); return true; }
        guildManager.kick(p, target);
        // 【已删除】if (target.isOnline()) personalSettings.updatePlayerScoreboard(target);
        return true;
    }

    private boolean guildApplyList(Player p) {
        guildManager.applyList(p);
        return true;
    }

    private boolean guildAccept(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入玩家"); return true; }
        guildManager.acceptApply(p, a[1]);
        return true;
    }

    private boolean guildDecline(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage("§c输入玩家"); return true; }
        guildManager.declineApply(p, a[1]);
        return true;
    }

    // ====================== 【已删除】称号相关方法 ======================
    // openTitleShop() 已删除
    // openTitleUI() 已删除

    public void openPlayerInfoUI(Player p) {
        if (MainPlugin.isBedrockPlayer(p.getUniqueId())) {
            personalSettings.openBedrockPlayerInfo(p);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§f个人信息");

        // 【已删除】称号按钮

        ItemStack tpBtn = new ItemStack(ENDER_PEARL);
        ItemMeta tpMeta = tpBtn.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setDisplayName("§b我的传送点");
            tpMeta.setLore(List.of("§7打开个人传送点菜单"));
            tpBtn.setItemMeta(tpMeta);
        }

        ItemStack settingBtn = new ItemStack(COMPASS);
        ItemMeta sMeta = settingBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§9个人设置");
            sMeta.setLore(List.of("§7公告、快捷菜单、PVP等设置"));
            settingBtn.setItemMeta(sMeta);
        }

        ItemStack back = new ItemStack(BARRIER);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§7⬅️ 返回主菜单");
            back.setItemMeta(bMeta);
        }

        // 重新布局：传送点放在中间，设置放在旁边
        inv.setItem(12, tpBtn);
        inv.setItem(14, settingBtn);
        inv.setItem(26, back);

        p.openInventory(inv);
    }

    public void openPlayerSettings(Player p) {
        personalSettings.openSettingsUI(p);
    }

    public void openGuildMainUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6工会系统");

        ItemStack create = new ItemStack(GREEN_WOOL);
        ItemMeta cMeta = create.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§a创建工会");
            create.setItemMeta(cMeta);
        }

        ItemStack list = new ItemStack(BOOK);
        ItemMeta lMeta = list.getItemMeta();
        if (lMeta != null) {
            lMeta.setDisplayName("§e工会列表");
            list.setItemMeta(lMeta);
        }

        ItemStack info = new ItemStack(PAPER);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.setDisplayName("§f我的工会信息");
            info.setItemMeta(iMeta);
        }

        inv.setItem(10, create);
        inv.setItem(13, list);
        inv.setItem(16, info);
        p.openInventory(inv);
    }

    // ====================== 核心监听器 ======================
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new CoreListener(), this);
        getServer().getPluginManager().registerEvents(new GiftListener(gift), this);
    }

    private class CoreListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            Player p = e.getPlayer();
            checkGameMode(p);

            Bukkit.getScheduler().runTaskLater(MainPlugin.this, () -> {
                if (!gift.hasSignedToday(p)) {
                    p.sendMessage("§e今日未签到，使用 /gift 签到！");
                }
            }, 20L);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e){
            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();
            personalSettings.removePlayerData(uuid);
            playerShopPages.remove(uuid);

            // 退出时关闭飞行状态，防止离线后继续扣时间
            if (economicSystem != null && economicSystem.getAcFly() != null) {
                ACFly acFly = economicSystem.getAcFly();
                if (acFly.isFlying(p)) {
                    acFly.toggleFlight(p);
                }
            }
        }

        // ⚠️ 注意：这里没有 onChat 方法
        // 所以聊天不受插件控制，使用 Minecraft 默认格式

        @EventHandler
        public void onClick(InventoryClickEvent e){
            if (!(e.getWhoClicked() instanceof Player p)) return;

            Inventory top = e.getView().getTopInventory();
            Inventory clicked = e.getClickedInventory();
            String title = e.getView().getTitle();
            ItemStack cur = e.getCurrentItem();

            if (clicked != top || cur == null || !cur.hasItemMeta()) return;

            // 只处理插件 GUI
            if (!isPluginGui(title)) return;
            e.setCancelled(true);

            handleGuiClick(p, title, cur);
        }

        @EventHandler
        public void onTp(PlayerTeleportEvent e){ checkGameMode(e.getPlayer()); }

        @EventHandler
        public void onWorld(PlayerChangedWorldEvent e){ checkGameMode(e.getPlayer()); }
    }

    // ====================== GUI点击处理 ======================
    private void handleGuiClick(Player p, String title, ItemStack currentItem) {
        if (title.equals("§e兑换苹果币")) {
            String displayName = currentItem.getItemMeta().getDisplayName();
            if (displayName == null) return;

            for (int amount : AppleCoinsCommand.EXCHANGE_AMOUNTS) {
                if (displayName.equals("§6兑换 " + amount + " 个苹果币")) {
                    p.closeInventory();
                    AppleCoinsCommand cmd = new AppleCoinsCommand(this);
                    cmd.doExchange(p, amount);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        p.performCommand("applecoins exchange");
                    }, 2L);
                    return;
                }
            }

            if (displayName.equals("§7⬅️ 返回苹果币商店")) {
                p.closeInventory();
                p.performCommand("applecoins shop");
                return;
            }
            if (displayName.equals("§c⬅️ 返回服务器主菜单") || displayName.equals("§7⬅️ 返回服务器主菜单")) {
                p.closeInventory();
                getACcraft().openMainMenu(p);
                return;
            }
            if (displayName.equals("§c关闭")) {
                p.closeInventory();
            }
            return;
        }

        // ========== 苹果币信息 ==========
        if (title.equals("§6苹果币信息")) {
            String displayName = currentItem.getItemMeta().getDisplayName();
            if (displayName == null) return;

            switch (displayName) {
                case "§6进入苹果币商店" -> {
                    p.closeInventory();
                    p.performCommand("applecoins shop");
                }
                case "§7⬅️ 返回服务器主菜单" -> {
                    p.closeInventory();
                    getACcraft().openMainMenu(p);
                }
                case "§c关闭" -> p.closeInventory();
                default -> {
                    if (currentItem.getType() == Material.BARRIER) {
                        p.closeInventory();
                    }
                }
            }
            return;
        }

        // ========== 苹果币商店 ==========
        if (title.equals("§6苹果币商店")) {
            String displayName = currentItem.getItemMeta().getDisplayName();
            if (displayName == null) return;

            switch (displayName) {
                case "§b飞行时间 1小时" -> {
                    p.closeInventory();
                    economicSystem.getAppleCoins().buyFlightTime(p, 1);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        p.performCommand("applecoins shop");
                    }, 2L);
                }
                case "§b飞行时间 5小时" -> {
                    p.closeInventory();
                    economicSystem.getAppleCoins().buyFlightTime(p, 5);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        p.performCommand("applecoins shop");
                    }, 2L);
                }
                case "§b飞行时间 10小时" -> {
                    p.closeInventory();
                    economicSystem.getAppleCoins().buyFlightTime(p, 10);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        p.performCommand("applecoins shop");
                    }, 2L);
                }
                case "§6增加传送点上限 +1" -> {
                    p.closeInventory();
                    buyTpSlot(p);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        p.performCommand("applecoins shop");
                    }, 2L);
                }
                case "§e兑换苹果币" -> {
                    p.closeInventory();
                    p.performCommand("applecoins exchange");
                }
                case "§7⬅️ 返回苹果币信息" -> {
                    p.closeInventory();
                    p.performCommand("applecoins info");
                }
                case "§7⬅️ 返回服务器主菜单" -> {
                    p.closeInventory();
                    getACcraft().openMainMenu(p);
                }
                case "§c关闭" -> p.closeInventory();
                default -> {
                    if (currentItem.getType() == Material.BARRIER) {
                        p.closeInventory();
                    }
                }
            }
            return;
        }

        // ========== 每日签到 GUI ==========
        if (title.equals("§e每日签到")) {
            if (currentItem.getType() == Material.BARRIER) {
                p.closeInventory();
                getACcraft().openMainMenu(p);
            } else if (currentItem.getType() == Material.LIME_WOOL) {
                int nextDay = gift.getCurrentSignDay(p) + 1;
                gift.receiveGift(p, nextDay);
                Bukkit.getScheduler().runTaskLater(this, () -> gift.openGiftUI(p), 2L);
            }
            return;
        }

        if (title.equals("§6工会列表")) {
            ItemMeta meta = currentItem.getItemMeta();
            if (meta == null) return;
            String guildName = meta.getDisplayName().replace("§6", "").trim();
            p.closeInventory();
            guildManager.apply(p, guildName);
            return;
        }

        if (title.equals("§f个人信息")) {
            switch (currentItem.getType()) {
                // 【已删除】NAME_TAG 和 REDSTONE_TORCH 称号相关按钮
                case ENDER_PEARL -> tpAsMePoint.openPlayerUI(p);
                case COMPASS -> openPlayerSettings(p);
                case BARRIER -> {
                    p.closeInventory();
                    getACcraft().openMainMenu(p);
                }
            }
            return;
        }

        if (title.equals("§9个人设置")) {
            if (currentItem.getItemMeta().getDisplayName().equals("§7⬅️ 返回服务器主菜单")) {
                p.closeInventory();
                getACcraft().openMainMenu(p);
                return;
            }
            personalSettings.handleClick(p, currentItem);
            return;
        }

        if (title.contains("传送") || title.contains("玩家")) {
            tpAsMe.handleClick(p, currentItem);
            return;
        }

        // 【已删除】称号商店和切换称号的 GUI 处理

        if (title.equals("§6工会系统")) {
            p.closeInventory();
            switch (currentItem.getType()) {
                case GREEN_WOOL -> p.sendMessage("§e请输入工会名字：/guild create <名字>");
                case BOOK -> guildList(p);
                case PAPER -> guildInfo(p, new String[]{});
            }
        }
    }

    // ====================== 购买传送点上限 ======================
    public void buyTpSlot(Player player) {
        Apple_Coins appleCoins = economicSystem.getAppleCoins();
        int price = Apple_Coins.TP_SLOT_PRICE;

        if (appleCoins.getAppleCoins(player) < price) {
            player.sendMessage("§c苹果币不足！需要 " + price + " 个苹果币增加1个传送点");
            return;
        }

        if (!appleCoins.removeAppleCoins(player, price)) {
            player.sendMessage("§c扣除苹果币失败！");
            return;
        }

        int currentMax = tpAsMePoint.getMaxPoints(player.getUniqueId());
        tpAsMePoint.setMaxPoints(player.getUniqueId(), currentMax + 1);

        player.sendMessage("§a成功购买 +1 传送点上限！");
        player.sendMessage("§a当前传送点上限: " + tpAsMePoint.getMaxPoints(player.getUniqueId()));
    }

    // ====================== 工具方法 ======================
    private boolean isPluginGui(String title) {
        return title.equals("§6工会列表")
                || title.startsWith("§6AC商店")
                || title.startsWith("§c物品出售")
                // 【已删除】称号相关GUI
                || title.equals("§f个人信息")
                || title.equals("§6工会系统")
                || title.equals("§6我的传送点")
                || title.equals("§c确认删除？")
                || title.equals("§6传送点操作")
                || title.equals("§e每日签到")
                || title.equals("§9个人设置")
                || title.equals("§6苹果币信息")
                || title.equals("§6苹果币商店")
                || title.equals("§e兑换苹果币")
                || title.equals("§6便捷功能");
    }

    private boolean consumeItem(Player p, Material material, int amount) {
        if (material == null || amount <= 0) return false;
        int remaining = amount;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            int count = item.getAmount();
            if (count <= remaining) {
                remaining -= count;
                item.setAmount(0);
            } else {
                item.setAmount(count - remaining);
                remaining = 0;
                break;
            }
            if (remaining <= 0) break;
        }
        return remaining <= 0;
    }

    private void checkGameMode(Player p){
        if(p.isOp()) return;
        String worldName = p.getWorld().getName().toLowerCase();
        if(worldName.contains("logging")) {
            p.setGameMode(GameMode.ADVENTURE);
        } else if(worldName.contains("playing")) {
            p.setGameMode(SURVIVAL);
        }
    }

    private List<String> getBookContent(){
        List<String> content = new ArrayList<>();
        List<String> keys = new ArrayList<>(bookConfig.getKeys(false));
        keys.sort((s1, s2) -> {
            try {
                int p1 = Integer.parseInt(s1.replace("page", ""));
                int p2 = Integer.parseInt(s2.replace("page", ""));
                return Integer.compare(p1, p2);
            } catch (NumberFormatException e) {
                return s1.compareTo(s2);
            }
        });

        for(String key : keys) {
            content.add(bookConfig.getString(key, "").replace("&","§"));
        }
        return content.isEmpty() ? List.of("§6暂无公告") : content;
    }

    private void saveServerConfig(){
        File file = new File(getDataFolder(), "server_config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("server-name", serverName);
        config.set("qq-group", qqGroup);
        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().severe("保存服务器配置失败");
        }
    }

    // 【已删除】startScoreboardUpdateTask() 方法

    // ====================== Getter ======================
    private void setExecutorIfExists(String commandName, CommandExecutor executor) {
        try {
            PluginCommand cmd = getCommand(commandName);
            if (cmd != null) {
                cmd.setExecutor(executor);
            } else {
                getLogger().warning("命令 " + commandName + " 未在 plugin.yml 中注册！");
            }
        } catch (Exception e) {
            getLogger().warning("注册命令 " + commandName + " 失败: " + e.getMessage());
        }
    }

    public TpAsMe getTpAsMe() {return tpAsMe;}
    public TpAsMePoint getTpAsMePoint() {return tpAsMePoint;}
    public ACcraft getACcraft() { return acCraft; }
    public EconomicSystem getEconomicSystem() {
        return economicSystem;
    }
    public static boolean isBedrockPlayer(UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Exception e) {
            return false;
        }
    }
    // ========== 新增：获取 PersonalSettings ==========
    public PersonalSettings getPersonalSettings() {
        return personalSettings;
    }

    // ====================== MCCFK Getters ======================
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public Set<UUID> getOpenWorkbenchPlayers() {
        return openWorkbenchPlayers;
    }

    public com.mccfk.plugin.managers.ActionManager getActionManager() {
        return actionManager;
    }
}