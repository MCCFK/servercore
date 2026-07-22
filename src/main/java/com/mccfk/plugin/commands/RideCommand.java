package com.mccfk.plugin.commands;

import com.apple.servercore.MainPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class RideCommand implements CommandExecutor {

    private final MainPlugin plugin;
    private final Gson gson;
    private final File blacklistFile;
    private final Set<String> blacklist = new HashSet<>();

    // 默认黑名单：技术实体 + Boss（盔甲架除外，玩家可骑）
    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "wither",
            "minecart", "chest_minecart", "furnace_minecart", "hopper_minecart",
            "tnt_minecart", "command_block_minecart", "spawner_minecart",
            "oak_boat", "spruce_boat", "birch_boat", "jungle_boat",
            "acacia_boat", "dark_oak_boat", "mangrove_boat", "cherry_boat",
            "bamboo_raft", "oak_chest_boat", "spruce_chest_boat",
            "birch_chest_boat", "jungle_chest_boat", "acacia_chest_boat",
            "dark_oak_chest_boat", "mangrove_chest_boat", "cherry_chest_boat",
            "bamboo_chest_raft",
            "experience_orb", "experience_bottle", "area_effect_cloud",
            "item_frame", "glow_item_frame", "painting", "leash_knot",
            "item", "arrow", "spectral_arrow", "trident",
            "snowball", "ender_pearl", "eye_of_ender", "egg",
            "fireball", "small_fireball", "dragon_fireball", "wither_skull",
            "llama_spit", "shulker_bullet",
            "falling_block", "fishing_bobber", "lightning_bolt",
            "marker", "interaction", "text_display", "block_display", "item_display"
    ));

    public RideCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        File pluginsFolder = new File(plugin.getServer().getPluginsFolder(), ".MCCFK_ALL_PLUGONSDATA");
        File rideFolder = new File(pluginsFolder, "ride");
        if (!rideFolder.exists()) rideFolder.mkdirs();
        this.blacklistFile = new File(rideFolder, "user_blacklist.json");
        loadBlacklist();

        // 每5秒自动从文件重载黑名单
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::reloadBlacklistFromFile, 100L, 100L);
    }

    private void loadBlacklist() {
        blacklist.clear();
        if (!blacklistFile.exists()) {
            blacklist.addAll(DEFAULT_BLACKLIST);
            saveBlacklist();
            plugin.getLogger().info("§7[骑乘] 已创建默认黑名单文件，共 " + blacklist.size() + " 个实体");
            return;
        }
        try (FileReader reader = new FileReader(blacklistFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null && !loaded.isEmpty()) {
                blacklist.addAll(loaded);
            } else {
                blacklist.addAll(DEFAULT_BLACKLIST);
            }
            plugin.getLogger().info("§7[骑乘] 已加载 " + blacklist.size() + " 个黑名单实体");
        } catch (IOException e) {
            plugin.getLogger().severe("§c[骑乘] 加载黑名单失败: " + e.getMessage());
            blacklist.addAll(DEFAULT_BLACKLIST);
        }
    }

    private void reloadBlacklistFromFile() {
        try (FileReader reader = new FileReader(blacklistFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            if (loaded != null && !loaded.isEmpty()) {
                blacklist.clear();
                blacklist.addAll(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("§e[骑乘] 定时重载黑名单失败: " + e.getMessage());
        }
    }

    private void saveBlacklist() {
        try (FileWriter writer = new FileWriter(blacklistFile)) {
            gson.toJson(new ArrayList<>(blacklist), writer);
        } catch (IOException e) {
            plugin.getLogger().severe("§c[骑乘] 保存黑名单失败: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        // 已经在骑乘 → 解除骑乘
        if (player.getVehicle() != null) {
            player.leaveVehicle();
            player.sendMessage("§a已解除骑乘");
            return true;
        }

        // 有人在骑乘你 → 全部甩下来
        if (!player.getPassengers().isEmpty()) {
            List<Entity> riders = List.copyOf(player.getPassengers());
            player.eject();
            player.sendMessage("§a已将骑乘者全部甩下来");
            for (Entity rider : riders) {
                if (rider instanceof Player riderPlayer) {
                    riderPlayer.sendMessage("§c你被 " + player.getName() + " 甩了下来");
                }
            }
            return true;
        }

        Entity target = null;

        // 有参数：指定玩家名
        if (args.length > 0) {
            Player targetPlayer = plugin.getServer().getPlayer(args[0]);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                target = targetPlayer;
            } else {
                player.sendMessage("§c找不到该玩家");
                return true;
            }
        } else {
            // 无参数：射线检测目标
            RayTraceResult ray = player.rayTraceEntities(8, false);
            if (ray != null && ray.getHitEntity() != null) {
                target = ray.getHitEntity();
            }
        }

        if (target == null) {
            player.sendMessage("§c没有找到可骑乘的目标，请看向目标或输入玩家名");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§c不能骑乘自己");
            return true;
        }

        // 目标玩家拒绝被骑乘检查
        if (target instanceof Player targetPlayer) {
            if (!plugin.getPersonalSettings().isAllowBeRidden(targetPlayer.getUniqueId())) {
                player.sendMessage("§c该玩家禁止被骑乘！");
                targetPlayer.sendMessage("§e" + player.getName() + " 试图骑乘你，但你已关闭允许被骑乘");
                return true;
            }
        }

        // 权限检查：非OP不能骑黑名单中的实体（默认全部可骑）
        if (!player.hasPermission("servercore.op")) {
            boolean isPlayer = target instanceof Player;
            boolean isInBlacklist = blacklist.contains(target.getType().getKey().getKey());
            if (!isPlayer && isInBlacklist) {
                player.sendMessage("§c你不能骑乘该实体！");
                return true;
            }
        }

        // 如果目标已有人骑乘，自动叠到最上面的人头上（避免重叠）
        Entity rideTarget = target;
        while (!rideTarget.getPassengers().isEmpty()) {
            rideTarget = rideTarget.getPassengers().get(0);
        }

        rideTarget.addPassenger(player);
        player.sendMessage("§a已骑乘 " + (rideTarget instanceof Player ? ((Player) rideTarget).getName() : rideTarget.getType().name()));

        // 被骑的人提示可用 /fuck 甩下
        if (rideTarget instanceof Player riddenPlayer) {
            riddenPlayer.sendMessage("§e" + player.getName() + " 骑到了你头上，可用 §f/fuck §e甩下来");
        }
        return true;
    }
}
