package com.apple.servercore.acmusic;

import com.apple.servercore.ACcraft;
import com.apple.servercore.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Music implements Listener {

    private final MainPlugin plugin;
    private final File musicFile;
    private FileConfiguration musicConfig;

    public static class MusicData {
        public Material icon;
        public String name;
        public String author;
        public String musicId;
    }

    private final Map<String, MusicData> musicMap = new HashMap<>();
    private final Set<UUID> mutedPlayers = new HashSet<>();
    private final Map<UUID, Integer> personalPage = new HashMap<>();
    private final Map<UUID, Integer> globalPage = new HashMap<>();
    private static final int PAGE_SIZE = 45;

    private long globalPlayCooldown = 0;
    private final long COOLDOWN = 5 * 60 * 1000;

    public Music(MainPlugin plugin) {
        this.plugin = plugin;
        musicFile = new File(plugin.getDataFolder(), "acmusic.yml");
        loadConfig();
        loadMusicData();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isBedrock(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private void loadConfig() {
        if (!musicFile.exists()) {
            musicConfig = YamlConfiguration.loadConfiguration(musicFile);
            musicConfig.set("music.1.id", "minecraft:blue_orchid");
            musicConfig.set("music.1.name", "§d搁浅");
            musicConfig.set("music.1.author", "周杰伦");
            musicConfig.set("music.1.musicid", "accraft:geqian");
            try {
                musicConfig.save(musicFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        musicConfig = YamlConfiguration.loadConfiguration(musicFile);
    }

    private void loadMusicData() {
        musicMap.clear();
        if (!musicConfig.contains("music")) return;
        for (String key : musicConfig.getConfigurationSection("music").getKeys(false)) {
            String path = "music." + key + ".";
            MusicData data = new MusicData();

            String id = musicConfig.getString(path + "id", "minecraft:stone");
            if (id.startsWith("minecraft:")) id = id.substring(10);
            data.icon = Material.getMaterial(id.toUpperCase());
            if (data.icon == null) data.icon = Material.STONE;

            data.name = musicConfig.getString(path + "name", "未知音乐");
            data.author = musicConfig.getString(path + "author", "");
            data.musicId = musicConfig.getString(path + "musicid", "");
            musicMap.put(key, data);
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openMusicMainUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§d音乐系统");

        ItemStack personal = createItem(Material.NOTE_BLOCK, "§a个人播放音乐", List.of("§7仅自己听到"));
        ItemStack global = createItem(Material.JUKEBOX, "§e全局播放音乐", List.of("§7全服玩家听到"));
        ItemStack mute = createItem(Material.BARRIER, isMuted(p) ? "§c已屏蔽全局音乐" : "§a已接收全局音乐", null);
        ItemStack stop = createItem(Material.RED_WOOL, "§c停止播放音乐", List.of("§7立即停止所有背景音乐"));
        ItemStack back = createItem(Material.STONE, "§7返回服务器主菜单", List.of("§7点击回到主界面"));

        inv.setItem(9, personal);
        inv.setItem(11, mute);
        inv.setItem(13, global);
        inv.setItem(15, stop);
        inv.setItem(17, back);

        p.openInventory(inv);
    }

    public void openPersonalMusicUI(Player p) {
        List<MusicData> list = new ArrayList<>(musicMap.values());
        int page = personalPage.getOrDefault(p.getUniqueId(), 0);
        Inventory inv = Bukkit.createInventory(null, 54, "§a个人音乐 §7(第" + (page + 1) + "页)");

        int start = page * PAGE_SIZE;
        int slot = 0;
        for (int i = start; i < list.size() && slot < PAGE_SIZE; i++) {
            MusicData data = list.get(i);
            inv.setItem(slot++, createItem(data.icon, data.name, List.of("§7作者: " + data.author, "§a点击播放")));
        }

        inv.setItem(45, createItem(Material.ARROW, "§f上一页", null));
        inv.setItem(53, createItem(Material.SPECTRAL_ARROW, "§f下一页", null));
        inv.setItem(49, createItem(Material.BARRIER, "§7返回音乐主页", null));

        p.openInventory(inv);
    }

    public void openGlobalMusicUI(Player p) {
        List<MusicData> list = new ArrayList<>(musicMap.values());
        int page = globalPage.getOrDefault(p.getUniqueId(), 0);
        Inventory inv = Bukkit.createInventory(null, 54, "§e全局音乐 §7(第" + (page + 1) + "页)");

        int start = page * PAGE_SIZE;
        int slot = 0;
        for (int i = start; i < list.size() && slot < PAGE_SIZE; i++) {
            MusicData data = list.get(i);
            inv.setItem(slot++, createItem(data.icon, data.name, List.of("§7作者: " + data.author, "§e点击全服播放")));
        }

        inv.setItem(45, createItem(Material.ARROW, "§f上一页", null));
        inv.setItem(53, createItem(Material.SPECTRAL_ARROW, "§f下一页", null));
        inv.setItem(49, createItem(Material.BARRIER, "§7返回音乐主页", null));

        p.openInventory(inv);
    }

    public void openBedrockMain(Player p) {
        SimpleForm form = SimpleForm.builder()
                .title("§d🎵 音乐系统")
                .button("§a个人播放音乐")
                .button("§e全局播放音乐")
                .button(isMuted(p) ? "§c已屏蔽全局音乐" : "§a已接收全局音乐")
                .button("§c停止播放音乐")
                .button("§7返回主菜单")
                .validResultHandler((response) -> {
                    int id = response.clickedButtonId();
                    switch (id) {
                        case 0 -> openBedrockPersonal(p);
                        case 1 -> openBedrockGlobal(p);
                        case 2 -> { toggleMute(p); openBedrockMain(p); }
                        case 3 -> stopMusic(p);
                        case 4 -> { p.closeInventory(); plugin.getACcraft().openMainMenu(p); }
                    }
                }).build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    public void openBedrockPersonal(Player p) {
        SimpleForm.Builder form = SimpleForm.builder().title("§a个人音乐库");
        for (MusicData data : musicMap.values()) {
            form.button(data.name + "\n§7" + data.author);
        }
        form.button("§c返回");
        form.validResultHandler((response) -> {
            int idx = response.clickedButtonId();
            List<MusicData> list = new ArrayList<>(musicMap.values());
            if (idx >= 0 && idx < list.size()) {
                playPersonal(p, list.get(idx));
            } else {
                openBedrockMain(p);
            }
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    public void openBedrockGlobal(Player p) {
        SimpleForm.Builder form = SimpleForm.builder().title("§e全服播放音乐");
        for (MusicData data : musicMap.values()) {
            form.button(data.name + "\n§7" + data.author);
        }
        form.button("§c返回");
        form.validResultHandler((response) -> {
            int idx = response.clickedButtonId();
            List<MusicData> list = new ArrayList<>(musicMap.values());
            if (idx >= 0 && idx < list.size()) {
                MusicData data = list.get(idx);
                playGlobal(data);
            } else {
                openBedrockMain(p);
            }
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }

    public void openMain(Player p) {
        if (isBedrock(p)) {
            openBedrockMain(p);
        } else {
            openMusicMainUI(p);
        }
    }

    // ========================= 【完全按你的写法重构】 =========================
    public void stopAllMusic() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.stopSound(SoundCategory.MUSIC);
        }
    }

    public void stopMusic(Player p) {
        p.stopSound(SoundCategory.MUSIC);
        p.sendMessage("§a已停止所有背景音乐！");
    }

    public void playPersonal(Player p, MusicData data) {
        if (data.musicId == null || data.musicId.isEmpty()) {
            p.sendMessage("§c音乐配置错误：musicID 为空");
            return;
        }
        stopMusic(p);

        // ✅ 完全使用你给的原生 API 写法！无指令！
        p.playSound(
                p.getLocation(),
                data.musicId,
                SoundCategory.MUSIC,
                10.0F,
                1.0F
        );

        p.sendMessage("§a正在播放: " + data.name);
    }

    public void playGlobal(MusicData data) {
        if (data.musicId == null || data.musicId.isEmpty()) {
            Bukkit.broadcastMessage("§c音乐配置错误！");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < globalPlayCooldown) {
            long left = (globalPlayCooldown - now) / 1000;
            Bukkit.broadcastMessage("§c全服播放冷却中! 剩余 " + left + " 秒");
            return;
        }

        stopAllMusic();
        globalPlayCooldown = now + COOLDOWN;

        // ✅ 全服播放也完全用你的写法！
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isMuted(p)) continue;

            p.playSound(
                    p.getLocation(),
                    data.musicId,
                    SoundCategory.MUSIC,
                    10.0F,
                    1.0F
            );
        }

        Bukkit.broadcastMessage("§e全服播放: " + data.name + " §7(5分钟内不可再次播放)");
    }

    public boolean isMuted(Player p) {
        return mutedPlayers.contains(p.getUniqueId());
    }

    public void toggleMute(Player p) {
        if (mutedPlayers.contains(p.getUniqueId())) {
            mutedPlayers.remove(p.getUniqueId());
            p.sendMessage("§a已开启全局音乐接收");
        } else {
            mutedPlayers.add(p.getUniqueId());
            p.sendMessage("§c已屏蔽全局音乐");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        Inventory clickedInv = e.getClickedInventory();
        ItemStack cur = e.getCurrentItem();

        boolean isMusicGui = title.equals("§d音乐系统") || title.startsWith("§a个人音乐") || title.startsWith("§e全局音乐");
        if (isMusicGui && clickedInv == e.getView().getTopInventory()) {
            e.setCancelled(true);
        }

        if (cur == null || cur.getType() == Material.AIR) return;
        if (!cur.hasItemMeta()) return;

        UUID uuid = p.getUniqueId();
        List<MusicData> musicList = new ArrayList<>(musicMap.values());
        int maxPage = (int) Math.ceil((double) musicList.size() / PAGE_SIZE) - 1;
        ItemMeta meta = cur.getItemMeta();

        if (title.equals("§d音乐系统")) {
            handleMainGuiClick(p, cur);
            return;
        }

        if (title.startsWith("§a个人音乐")) {
            handlePersonalGuiClick(p, cur, uuid, maxPage, meta, musicList);
        }

        if (title.startsWith("§e全局音乐")) {
            handleGlobalGuiClick(p, cur, uuid, maxPage, meta, musicList);
        }
    }

    private void handleMainGuiClick(Player p, ItemStack cur) {
        switch (cur.getType()) {
            case NOTE_BLOCK -> {
                personalPage.put(p.getUniqueId(), 0);
                openPersonalMusicUI(p);
            }
            case JUKEBOX -> {
                globalPage.put(p.getUniqueId(), 0);
                openGlobalMusicUI(p);
            }
            case BARRIER -> {
                toggleMute(p);
                openMusicMainUI(p);
            }
            case RED_WOOL -> stopMusic(p);
            case STONE -> {
                p.closeInventory();
                plugin.getACcraft().openMainMenu(p);
            }
        }
    }

    private void handlePersonalGuiClick(Player p, ItemStack cur, UUID uuid, int maxPage, ItemMeta meta, List<MusicData> musicList) {
        int now = personalPage.getOrDefault(uuid, 0);
        switch (cur.getType()) {
            case ARROW:
                personalPage.put(uuid, Math.max(0, now - 1));
                openPersonalMusicUI(p);
                break;
            case SPECTRAL_ARROW:
                personalPage.put(uuid, Math.min(maxPage, now + 1));
                openPersonalMusicUI(p);
                break;
            case BARRIER:
                openMusicMainUI(p);
                break;
            default:
                String clickName = meta.getDisplayName();
                for (MusicData data : musicList) {
                    if (data.name.equals(clickName)) {
                        playPersonal(p, data);
                        break;
                    }
                }
        }
    }

    private void handleGlobalGuiClick(Player p, ItemStack cur, UUID uuid, int maxPage, ItemMeta meta, List<MusicData> musicList) {
        int now = globalPage.getOrDefault(uuid, 0);
        switch (cur.getType()) {
            case ARROW:
                globalPage.put(uuid, Math.max(0, now - 1));
                openGlobalMusicUI(p);
                break;
            case SPECTRAL_ARROW:
                globalPage.put(uuid, Math.min(maxPage, now + 1));
                openGlobalMusicUI(p);
                break;
            case BARRIER:
                openMusicMainUI(p);
                break;
            default:
                String clickName = meta.getDisplayName();
                for (MusicData data : musicList) {
                    if (data.name.equals(clickName)) {
                        playGlobal(data);
                        break;
                    }
                }
        }
    }
}