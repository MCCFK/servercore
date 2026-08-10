package com.apple.servercore.TpAsMe;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基岩版表单UI管理器
 * 使用 Cumulus 和 Floodgate API 发送原生基岩版表单
 */
public class TpAsMe_Bedrock_ui {

    private final TpAsMe parent;
    private final FloodgateApi floodgateApi;

    public TpAsMe_Bedrock_ui(TpAsMe parent) {
        this.parent = parent;
        try {
            this.floodgateApi = FloodgateApi.getInstance();
        } catch (Exception e) {
            throw new RuntimeException("Floodgate API 未找到！");
        }
    }

    public boolean isBedrockPlayer(Player player) {
        try {
            return floodgateApi != null && floodgateApi.isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 主菜单 ======================
    public void sendMainMenu(Player p) {
        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§e传送系统")
                    .content("§7请选择要使用的功能：")
                    .button("§a传送到玩家", FormImage.Type.PATH, "textures/ui/move")
                    .button("§a邀请玩家传送", FormImage.Type.PATH, "textures/ui/move")
                    .button("§6传送设置", FormImage.Type.PATH, "textures/ui/gear")
                    .button("§c返回主菜单", FormImage.Type.PATH, "textures/ui/arrow_left")
                    .validResultHandler((SimpleFormResponse response) -> {
                        int clicked = response.clickedButtonId();
                        switch (clicked) {
                            case 0 -> sendPlayerSelectForm(p, "TPA", 1);
                            case 1 -> sendPlayerSelectForm(p, "TPHERE", 1);
                            case 2 -> sendSettingForm(p);
                            case 3 -> {
                                p.closeInventory();
                                parent.plugin.getACcraft().openMainMenu(p);
                            }
                        }
                    })
                    .build();

            floodgateApi.sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            e.printStackTrace();
            parent.openMainUI(p);
        }
    }

    // ====================== 选择玩家（TPA/TPHERE） ======================
    public void sendPlayerSelectForm(Player p, String type, int page) {
        try {
            List<Player> online = Bukkit.getOnlinePlayers().stream()
                    .filter(pl -> !pl.equals(p))
                    .collect(Collectors.toList());

            if (online.isEmpty()) {
                p.sendMessage("§c没有其他在线玩家！");
                sendMainMenu(p);
                return;
            }

            int totalPage = Math.max(1, (online.size() + 21) / 22);
            int start = (page - 1) * 22;
            int end = Math.min(start + 22, online.size());

            String typeName = type.equals("TPA") ? "传送到玩家" : "邀请玩家传送";
            String title = "§e选择玩家 - " + typeName + " 第" + page + "/" + totalPage + "页";
            String content = "§7请选择要" + (type.equals("TPA") ? "传送过去" : "邀请过来") + "的玩家：\n§8(共 " + online.size() + " 人在线)";

            SimpleForm.Builder builder = SimpleForm.builder()
                    .title(title)
                    .content(content);

            for (int i = start; i < end; i++) {
                Player target = online.get(i);
                String playerName = target.getName();
                String status = "";
                if (parent.isBlockedByMe(p, target)) {
                    status = " §c(已屏蔽)";
                }
                builder.button("§f" + playerName + status);
            }

            if (page > 1) {
                builder.button("§e上一页", FormImage.Type.PATH, "textures/ui/arrow_left");
            }
            if (page < totalPage) {
                builder.button("§e下一页", FormImage.Type.PATH, "textures/ui/arrow_right");
            }
            builder.button("§c返回主菜单", FormImage.Type.PATH, "textures/ui/arrow_left");

            builder.validResultHandler((SimpleFormResponse response) -> {
                int clicked = response.clickedButtonId();
                int playerCount = end - start;
                int navCount = 0;
                if (page > 1) navCount++;
                if (page < totalPage) navCount++;
                navCount++;

                if (clicked < playerCount) {
                    int index = start + clicked;
                    if (index < online.size()) {
                        Player target = online.get(index);
                        if (parent.isBlockedByMe(p, target)) {
                            p.sendMessage("§c你已屏蔽该玩家，无法发送请求！");
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            sendPlayerSelectForm(p, type, page);
                            return;
                        }
                        parent.sendRequest(p, target, type);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                } else {
                    int navIndex = clicked - playerCount;

                    if (page > 1 && navIndex == 0) {
                        sendPlayerSelectForm(p, type, page - 1);
                    } else if (page < totalPage && navIndex == (page > 1 ? 1 : 0)) {
                        sendPlayerSelectForm(p, type, page + 1);
                    } else {
                        sendMainMenu(p);
                    }
                }
            });

            floodgateApi.sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            e.printStackTrace();
            parent.openJavaPlayerList(p, type, page);
        }
    }

    // ====================== 传送设置菜单 ======================
    public void sendSettingForm(Player p) {
        try {
            String content = "§f请选择要执行的操作：\n\n" +
                    "§7当前状态：\n" +
                    "§8" + getCurrentStatus(p);

            SimpleForm form = SimpleForm.builder()
                    .title("§6传送设置")
                    .content(content)
                    .button("§c屏蔽 30 分钟", FormImage.Type.PATH, "textures/ui/clock")
                    .button("§c屏蔽 1 小时", FormImage.Type.PATH, "textures/ui/clock")
                    .button("§c屏蔽 1.5 小时", FormImage.Type.PATH, "textures/ui/clock")
                    .button("§c永久屏蔽", FormImage.Type.PATH, "textures/ui/lock")
                    .button("§a恢复默认", FormImage.Type.PATH, "textures/ui/refresh")
                    .button("§e屏蔽指定玩家", FormImage.Type.PATH, "textures/ui/trash")
                    .button("§a取消屏蔽玩家", FormImage.Type.PATH, "textures/ui/trash")
                    .button("§c返回主菜单", FormImage.Type.PATH, "textures/ui/arrow_left")
                    .validResultHandler((SimpleFormResponse response) -> {
                        int clicked = response.clickedButtonId();

                        if (clicked == 7) {
                            p.closeInventory();
                            parent.plugin.getACcraft().openMainMenu(p);
                            return;
                        }

                        // 处理屏蔽指定玩家和取消屏蔽玩家
                        if (clicked == 5) {
                            sendBlockPlayerList(p, "BLOCK", 1);
                            return;
                        }
                        if (clicked == 6) {
                            sendBlockPlayerList(p, "UNBLOCK", 1);
                            return;
                        }

                        String cmd = switch (clicked) {
                            case 0 -> "actp_setting block30";
                            case 1 -> "actp_setting block60";
                            case 2 -> "actp_setting block90";
                            case 3 -> "actp_setting block_forever";
                            case 4 -> "actp_setting unblock_all";
                            default -> "actp_setting";
                        };
                        Bukkit.dispatchCommand(p, cmd);
                    })
                    .closedOrInvalidResultHandler(() -> {
                        p.sendMessage("§7已取消操作");
                    })
                    .build();

            floodgateApi.sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            p.sendMessage("§c无法打开设置表单");
            e.printStackTrace();
        }
    }

    // ====================== 屏蔽管理玩家列表 ======================
    public void sendBlockPlayerList(Player p, String type, int page) {
        try {
            List<Player> online = Bukkit.getOnlinePlayers().stream()
                    .filter(pl -> !pl.equals(p))
                    .collect(Collectors.toList());

            if (online.isEmpty()) {
                p.sendMessage("§c没有其他在线玩家！");
                sendSettingForm(p);
                return;
            }

            // 如果是屏蔽列表，只显示未屏蔽的玩家；如果是取消屏蔽列表，只显示已屏蔽的玩家
            List<Player> filtered = new ArrayList<>();
            if (type.equals("BLOCK")) {
                filtered = online.stream()
                        .filter(target -> !parent.isBlockedByMe(p, target))
                        .collect(Collectors.toList());
            } else {
                filtered = online.stream()
                        .filter(target -> parent.isBlockedByMe(p, target))
                        .collect(Collectors.toList());
            }

            if (filtered.isEmpty()) {
                if (type.equals("BLOCK")) {
                    p.sendMessage("§a所有玩家都已被屏蔽！");
                } else {
                    p.sendMessage("§a没有已屏蔽的玩家！");
                }
                sendSettingForm(p);
                return;
            }

            int totalPage = Math.max(1, (filtered.size() + 21) / 22);
            int start = (page - 1) * 22;
            int end = Math.min(start + 22, filtered.size());

            String typeName = type.equals("BLOCK") ? "屏蔽玩家" : "取消屏蔽玩家";
            String title = "§e" + typeName + " - 第" + page + "/" + totalPage + "页";
            String content = "§7" + (type.equals("BLOCK") ? "选择要屏蔽的玩家" : "选择要取消屏蔽的玩家") + "：\n§8(共 " + filtered.size() + " 人)";

            SimpleForm.Builder builder = SimpleForm.builder()
                    .title(title)
                    .content(content);

            for (int i = start; i < end; i++) {
                Player target = filtered.get(i);
                builder.button("§f" + target.getName());
            }

            if (page > 1) {
                builder.button("§e上一页", FormImage.Type.PATH, "textures/ui/arrow_left");
            }
            if (page < totalPage) {
                builder.button("§e下一页", FormImage.Type.PATH, "textures/ui/arrow_right");
            }
            builder.button("§c返回设置", FormImage.Type.PATH, "textures/ui/gear");

            List<Player> finalFiltered = filtered;
            builder.validResultHandler((SimpleFormResponse response) -> {
                int clicked = response.clickedButtonId();
                int playerCount = end - start;
                int navCount = 0;
                if (page > 1) navCount++;
                if (page < totalPage) navCount++;
                navCount++;

                if (clicked < playerCount) {
                    int index = start + clicked;
                    if (index < finalFiltered.size()) {
                        Player target = finalFiltered.get(index);
                        handleBlockAction(p, target, type);
                    }
                } else {
                    int navIndex = clicked - playerCount;

                    if (page > 1 && navIndex == 0) {
                        sendBlockPlayerList(p, type, page - 1);
                    } else if (page < totalPage && navIndex == (page > 1 ? 1 : 0)) {
                        sendBlockPlayerList(p, type, page + 1);
                    } else {
                        sendSettingForm(p);
                    }
                }
            });

            floodgateApi.sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            e.printStackTrace();
            // 降级到Java版GUI
            parent.openJavaPlayerList(p, type, page);
        }
    }

    /**
     * 处理屏蔽/取消屏蔽操作
     */
    private void handleBlockAction(Player p, Player target, String type) {
        if (type.equals("BLOCK")) {
            if (!parent.isBlockedByMe(p, target)) {
                parent.blockPlayer(p, target);
                p.sendMessage("§a已屏蔽玩家: " + target.getName());
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.8f);
            } else {
                p.sendMessage("§e玩家 " + target.getName() + " 已被屏蔽！");
            }
            // 刷新列表
            sendBlockPlayerList(p, type, 1);
        } else {
            if (parent.isBlockedByMe(p, target)) {
                parent.unblockPlayer(p, target);
                p.sendMessage("§a已取消屏蔽玩家: " + target.getName());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            } else {
                p.sendMessage("§e玩家 " + target.getName() + " 未被屏蔽！");
            }
            // 刷新列表
            sendBlockPlayerList(p, type, 1);
        }
    }

    // ====================== 传送请求表单 ======================
    public void sendRequestForm(Player to, Player from, String type) {
        try {
            String displayName = from.getName();
            String requestType = type.equals("TPA") ? "传送请求" : "邀请传送请求";

            String content = "§f玩家 §e" + displayName + " §f向您发起了" + requestType + "！\n\n" +
                    "§7请求将在 §e120秒 §7后自动过期\n\n" +
                    "§7请选择：";

            SimpleForm form = SimpleForm.builder()
                    .title("§e传送请求")
                    .content(content)
                    .button("§a接受 (" + displayName + ")")
                    .button("§c拒绝")
                    .validResultHandler((SimpleFormResponse response) -> {
                        int clicked = response.clickedButtonId();
                        if (clicked == 0) {
                            Bukkit.dispatchCommand(to, "tpaaccept " + displayName);
                        } else if (clicked == 1) {
                            Bukkit.dispatchCommand(to, "tpadeny " + displayName);
                        }
                    })
                    .closedOrInvalidResultHandler(() -> {
                        // 玩家关闭了表单，什么都不做
                    })
                    .build();

            floodgateApi.sendForm(to.getUniqueId(), form);
        } catch (Exception e) {
            e.printStackTrace();
            sendFallbackMessage(to, from, type);
        }
    }

    // ====================== 辅助方法 ======================
    private String getCurrentStatus(Player p) {
        StringBuilder sb = new StringBuilder();

        if (parent.isGlobalBlocked(p)) {
            sb.append("• 全局屏蔽: §a已开启\n");
        } else {
            sb.append("• 全局屏蔽: §c已关闭\n");
        }

        Set<UUID> blocked = parent.getPlayerBlockList(p.getUniqueId());
        if (blocked != null && !blocked.isEmpty()) {
            sb.append("• 已屏蔽玩家: §e" + blocked.size() + " §7人");
        } else {
            sb.append("• 已屏蔽玩家: §c无");
        }

        return sb.toString();
    }

    public void sendFallbackMessage(Player to, Player from, String type) {
        String requestType = type.equals("TPA") ? "传送到你" : "邀请你传送";
        String displayName = from.getName();

        to.sendMessage("§6§l════════════════════════════");
        to.sendMessage("§e" + displayName + " §f向您发起了" + requestType + "请求！");
        to.sendMessage("§7请求将在 §e120秒 §7后自动过期");
        to.sendMessage("§a接受: /tpaaccept " + displayName);
        to.sendMessage("§c拒绝: /tpadeny " + displayName);
        to.sendMessage("§6§l════════════════════════════");
    }

    // ====================== 兼容 Java 版调用 ======================
    /**
     * 兼容 openPlayerList 调用
     * 根据类型决定打开哪个列表
     */
    public void sendPlayerListForm(Player p, String type, int page) {
        if (type.equals("BLOCK") || type.equals("UNBLOCK")) {
            sendBlockPlayerList(p, type, page);
        } else {
            sendPlayerSelectForm(p, type, page);
        }
    }
}