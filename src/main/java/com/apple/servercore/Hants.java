package com.apple.servercore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class Hants {

    private final MainPlugin plugin;
    private final File file;
    private final List<String> lines = new ArrayList<>();
    private final Random random = new Random();
    private int lastBroadcastMinute = -1;

    public Hants(MainPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hants.txt");
        createFileIfNotExists();
        loadLines();
        startTimer();
    }

    private void createFileIfNotExists() {
        try {
            if (!file.exists()) {
                file.createNewFile();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8))) {
                    writer.write("&a欢迎来到服务器！\n");
                    writer.write("&b记得开启PVP才能打架哦～\n");
                    writer.write("&d建筑使我们快乐！\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadLines() {
        lines.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int minute = java.time.LocalTime.now().getMinute();
                if ((minute == 0 || minute == 20 || minute == 40) && minute != lastBroadcastMinute) {
                    broadcastRandomLine();
                    lastBroadcastMinute = minute;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void broadcastRandomLine() {
        if (lines.isEmpty()) return;
        String text = lines.get(random.nextInt(lines.size())).replace("&", "§");

        for (Player p : Bukkit.getOnlinePlayers()) {
            // 这里使用主类的公告接收开关
            if (plugin.announceToggle.getOrDefault(p.getUniqueId(), true)) {
                p.sendMessage("§6[系统公告] §f" + text);
            }
        }
    }
}