package com.apple.servercore.guild;

import com.apple.servercore.MainPlugin;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Guild_BedrockUI {

    private final MainPlugin plugin;
    private final Guilds guilds;

    public Guild_BedrockUI(MainPlugin plugin, Guilds guilds) {
        this.plugin = plugin;
        this.guilds = guilds;
    }

    private boolean isBedrock(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private String getPlayerName(UUID uuid) {
        return guilds.getPlayerName(uuid);
    }

    // ==================== 主菜单 ====================
    public void openMainMenu(Player p) {
        if (!isBedrock(p)) return;

        SimpleForm form = SimpleForm.builder()
                .title("§6公会系统")
                .content("§7请选择你要进行的操作")
                .button("§a创建公会")
                .button("§e公会列表")
                .button("§b我的公会信息")
                .button("§d管理面板")
                .button("§f我的申请")
                .button("§c退出公会")
                .button("§6公会传送点")
                .button("§7返回主菜单")
                .validResultHandler((SimpleFormResponse res) -> {
                    int id = res.clickedButtonId();
                    switch (id) {
                        case 0 -> openCreateGuildUI(p);
                        case 1 -> openGuildListUI(p);
                        case 2 -> openMyGuildInfoUI(p);
                        case 3 -> openManagePanelUI(p);
                        case 4 -> openMyApplicationsUI(p);
                        case 5 -> handleLeaveGuild(p);
                        case 6 -> openGuildTeleportUI(p);
                        case 7 -> plugin.getACcraft().openMainMenu(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送基岩版主菜单失败: " + e.getMessage());
            p.sendMessage("§c打开菜单失败，请使用 /guild help 查看指令");
        }
    }

    // ==================== 公会传送点 UI ====================
    public void openGuildTeleportUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null) {
            p.sendMessage("§c你还没有加入公会");
            openMainMenu(p);
            return;
        }

        if (g.pointWorld == null || g.pointWorld.isEmpty() || g.pointX == 0) {
            if (guilds.isLeader(p)) {
                SimpleForm form = SimpleForm.builder()
                        .title("§6公会传送点")
                        .content("§c公会尚未设置传送点\n\n§7作为会长，点击下方按钮设置当前位置为传送点")
                        .button("§6设置当前位置为传送点")
                        .button("§7返回主菜单")
                        .validResultHandler((SimpleFormResponse res) -> {
                            if (res.clickedButtonId() == 0) {
                                guilds.setGuildPoint(p);
                            }
                            openMainMenu(p);
                        })
                        .closedOrInvalidResultHandler(() -> openMainMenu(p))
                        .build();
                try {
                    FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
                } catch (Exception e) {
                    plugin.getLogger().warning("发送传送点表单失败: " + e.getMessage());
                    p.sendMessage("§c打开表单失败，请使用 /guild cpoint 设置当前位置为传送点");
                }
            } else {
                SimpleForm form = SimpleForm.builder()
                        .title("§6公会传送点")
                        .content("§c公会尚未设置传送点\n\n§7请联系会长设置")
                        .button("§7返回主菜单")
                        .validResultHandler((SimpleFormResponse res) -> openMainMenu(p))
                        .closedOrInvalidResultHandler(() -> openMainMenu(p))
                        .build();
                try {
                    FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
                } catch (Exception e) {
                    plugin.getLogger().warning("发送传送点表单失败: " + e.getMessage());
                }
            }
            return;
        }

        SimpleForm form = SimpleForm.builder()
                .title("§6公会传送点")
                .content("§f公会: §6" + g.name + "\n"
                        + "§f坐标: §a" + String.format("%.1f", g.pointX) + " §7" + String.format("%.1f", g.pointY) + " §7" + String.format("%.1f", g.pointZ) + "\n"
                        + "§f世界: §b" + g.pointWorld + "\n\n"
                        + "§7点击下方按钮传送")
                .button("§b点击传送")
                .button("§7返回主菜单")
                .validResultHandler((SimpleFormResponse res) -> {
                    if (res.clickedButtonId() == 0) {
                        guilds.teleportToGuildPoint(p);
                    }
                    openMainMenu(p);
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送传送点表单失败: " + e.getMessage());
            p.sendMessage("§c打开传送点失败，请使用 /guild jpoint 传送");
        }
    }

    // ==================== 创建公会 ====================
    public void openCreateGuildUI(Player p) {
        if (!isBedrock(p)) return;

        CustomForm form = CustomForm.builder()
                .title("§a创建公会")
                .input("公会名称（2-12位中英文或数字）")
                .validResultHandler((CustomFormResponse res) -> {
                    String name = res.getInput(0).trim();
                    if (name.isEmpty()) {
                        p.sendMessage("§c公会名称不能为空！");
                        return;
                    }
                    guilds.create(p, name);
                    openMainMenu(p);
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送创建公会表单失败: " + e.getMessage());
            p.sendMessage("§c打开表单失败，请使用 /guild create <名称> 创建");
        }
    }

    // ==================== 公会列表 ====================
    public void openGuildListUI(Player p) {
        if (!isBedrock(p)) return;

        List<Guilds.Guild> list = new ArrayList<>(guilds.getAllGuilds());

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§e公会列表");

        if (list.isEmpty()) {
            builder.content("§c暂无公会");
        } else {
            StringBuilder content = new StringBuilder();
            content.append("§7点击公会名称申请加入\n\n");
            for (int i = 0; i < Math.min(list.size(), 20); i++) {
                Guilds.Guild g = list.get(i);
                content.append("§f").append(g.name)
                        .append(" §7(").append(g.members.size()).append("人)\n");
            }
            if (list.size() > 20) {
                content.append("§7... 还有 ").append(list.size() - 20).append(" 个公会");
            }
            builder.content(content.toString());

            for (Guilds.Guild g : list) {
                builder.button("§f" + g.name + "\n§7" + g.members.size() + " 名成员");
            }
        }

        builder.button("§7返回主菜单")
                .validResultHandler((SimpleFormResponse res) -> {
                    int idx = res.clickedButtonId();
                    if (idx < list.size()) {
                        Guilds.Guild target = list.get(idx);
                        if (guilds.getGuild(p) != null) {
                            p.sendMessage("§c你已加入公会，不能申请加入其他公会");
                            openMainMenu(p);
                            return;
                        }
                        if (target.applications.contains(p.getUniqueId())) {
                            p.sendMessage("§c你已申请过该公会");
                            openMainMenu(p);
                            return;
                        }
                        guilds.apply(p, target.name);
                        p.sendMessage("§a已申请加入 " + target.name + "，等待会长审批");
                        openMainMenu(p);
                    } else {
                        openMainMenu(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送公会列表表单失败: " + e.getMessage());
            p.sendMessage("§c打开表单失败，请使用 /guild list 查看");
        }
    }

    // ==================== 我的公会信息 ====================
    public void openMyGuildInfoUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null) {
            p.sendMessage("§c你还没有加入公会");
            openMainMenu(p);
            return;
        }

        String leaderName = getPlayerName(g.leader);
        StringBuilder deputies = new StringBuilder();
        for (UUID u : g.deputies) {
            String name = getPlayerName(u);
            if (name != null) {
                deputies.append(name).append(" ");
            }
        }

        StringBuilder content = new StringBuilder();
        content.append("§6===== ").append(g.name).append(" =====\n\n");
        content.append("§f会长: §6").append(leaderName).append("\n");
        content.append("§f副会长: §e").append(deputies.length() > 0 ? deputies : "无").append("\n");
        content.append("§f成员: §7").append(g.members.size()).append("人\n\n");

        if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
            content.append("§6传送点: §a" + String.format("%.1f", g.pointX) + " §7" + String.format("%.1f", g.pointY) + " §7" + String.format("%.1f", g.pointZ)
                    + " §7(").append(g.pointWorld).append(")\n\n");
        }

        content.append("§7成员列表:\n");

        int count = 0;
        for (UUID u : g.members) {
            if (count >= 20) {
                content.append("§7... 还有 ").append(g.members.size() - 20).append(" 人");
                break;
            }
            String name = getPlayerName(u);
            if (name != null) {
                content.append("§f▪ ").append(name).append("\n");
                count++;
            }
        }

        SimpleForm form = SimpleForm.builder()
                .title("§b我的公会")
                .content(content.toString())
                .button("§7返回主菜单")
                .validResultHandler((SimpleFormResponse res) -> openMainMenu(p))
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送公会信息表单失败: " + e.getMessage());
            guilds.info(p, null);
        }
    }

    // ==================== 管理面板 ====================
    public void openManagePanelUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null) {
            p.sendMessage("§c你还没有加入公会");
            openMainMenu(p);
            return;
        }

        if (!guilds.isManager(p)) {
            p.sendMessage("§c你不是会长或副会长，无管理权限");
            openMainMenu(p);
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§d管理面板 - " + g.name);

        StringBuilder content = new StringBuilder();
        content.append("§7待审批申请: §e").append(g.applications.size()).append(" 个\n");
        if (guilds.isLeader(p)) {
            content.append("§7副会长: §e").append(g.deputies.size()).append("/2 人\n");
            if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                content.append("§7传送点: §a已设置\n");
            } else {
                content.append("§7传送点: §c未设置\n");
            }
        }
        content.append("\n§7请选择操作：");

        builder.content(content.toString());
        builder.button("§a申请列表 (" + g.applications.size() + ")");
        builder.button("§e踢出成员");
        if (guilds.isLeader(p)) {
            builder.button("§6设置副会长");
            builder.button("§c撤销副会长");
            builder.button("§6设置传送点");
            if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                builder.button("§c移除传送点");
            }
            builder.button("§c解散公会");
        }
        builder.button("§7返回主菜单");

        builder.validResultHandler((SimpleFormResponse res) -> {
                    int id = res.clickedButtonId();
                    if (id == 0) {
                        openApplyListUI(p);
                    } else if (id == 1) {
                        openKickMemberUI(p);
                    } else if (id == 2 && guilds.isLeader(p)) {
                        openSetDeputyUI(p);
                    } else if (id == 3 && guilds.isLeader(p)) {
                        openRemoveDeputyUI(p);
                    } else if (id == 4 && guilds.isLeader(p)) {
                        guilds.setGuildPoint(p);
                        openManagePanelUI(p);
                    } else if (id == 5 && guilds.isLeader(p)) {
                        // 判断是"移除传送点"还是"解散公会"
                        if (g.pointWorld != null && !g.pointWorld.isEmpty() && g.pointX != 0) {
                            guilds.removeGuildPoint(p);
                            openManagePanelUI(p);
                        } else {
                            openDisbandConfirmUI(p);
                        }
                    } else if (id == 6 && guilds.isLeader(p)) {
                        openDisbandConfirmUI(p);
                    } else {
                        openMainMenu(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送管理面板表单失败: " + e.getMessage());
            p.sendMessage("§c打开管理面板失败");
        }
    }

    // ==================== 申请列表 ====================
    public void openApplyListUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null || !guilds.isManager(p)) {
            p.sendMessage("§c无权限");
            openMainMenu(p);
            return;
        }

        if (g.applications.isEmpty()) {
            p.sendMessage("§c暂无申请");
            openManagePanelUI(p);
            return;
        }

        List<UUID> list = new ArrayList<>(g.applications);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§a申请列表 (" + list.size() + ")");

        StringBuilder content = new StringBuilder();
        content.append("§7点击玩家名进行处理\n\n");
        for (UUID u : list) {
            String name = getPlayerName(u);
            content.append("§f").append(name).append("\n");
        }
        builder.content(content.toString());

        for (UUID u : list) {
            String name = getPlayerName(u);
            builder.button("§f" + name + "\n§a点击同意 | §c点击拒绝");
        }
        builder.button("§7返回管理面板");

        builder.validResultHandler((SimpleFormResponse res) -> {
                    int idx = res.clickedButtonId();
                    if (idx < list.size()) {
                        UUID target = list.get(idx);
                        String targetName = getPlayerName(target);
                        openProcessApplyUI(p, g, target, targetName);
                    } else {
                        openManagePanelUI(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openManagePanelUI(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送申请列表表单失败: " + e.getMessage());
            guilds.applyList(p);
        }
    }

    // ==================== 处理申请选项 ====================
    private void openProcessApplyUI(Player p, Guilds.Guild g, UUID target, String targetName) {
        SimpleForm form = SimpleForm.builder()
                .title("§a处理申请")
                .content("§f玩家: §6" + targetName + "\n§f申请加入: §6" + g.name)
                .button("§a同意")
                .button("§c拒绝")
                .button("§7返回")
                .validResultHandler((SimpleFormResponse res) -> {
                    int id = res.clickedButtonId();
                    if (id == 0) {
                        guilds.acceptApply(p, targetName);
                        p.sendMessage("§a已同意 " + targetName + " 的申请");
                    } else if (id == 1) {
                        guilds.declineApply(p, targetName);
                        p.sendMessage("§c已拒绝 " + targetName + " 的申请");
                    }
                    openApplyListUI(p);
                })
                .closedOrInvalidResultHandler(() -> openApplyListUI(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送处理申请表单失败: " + e.getMessage());
        }
    }

    // ==================== 我的申请 ====================
    public void openMyApplicationsUI(Player p) {
        if (!isBedrock(p)) return;

        if (guilds.getGuild(p) != null) {
            p.sendMessage("§a你已加入公会");
            openMainMenu(p);
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("§7你已申请的公会:\n\n");
        boolean has = false;

        for (Guilds.Guild g : guilds.getAllGuilds()) {
            if (g.applications.contains(p.getUniqueId())) {
                content.append("§f▪ ").append(g.name).append("\n");
                has = true;
            }
        }

        if (!has) {
            content.append("§c暂无申请");
        }

        SimpleForm form = SimpleForm.builder()
                .title("§f我的申请")
                .content(content.toString())
                .button("§7返回主菜单")
                .validResultHandler((SimpleFormResponse res) -> openMainMenu(p))
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送我的申请表单失败: " + e.getMessage());
        }
    }

    // ==================== 踢出成员 ====================
    private void openKickMemberUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null || !guilds.isManager(p)) {
            p.sendMessage("§c无权限");
            openMainMenu(p);
            return;
        }

        List<UUID> members = new ArrayList<>(g.members);
        members.remove(p.getUniqueId());
        if (guilds.isLeader(p)) {
            members.remove(g.leader);
        }

        if (members.isEmpty()) {
            p.sendMessage("§c没有可踢出的成员");
            openManagePanelUI(p);
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§e踢出成员");

        StringBuilder content = new StringBuilder();
        content.append("§7点击玩家踢出\n\n");
        for (UUID u : members) {
            String name = getPlayerName(u);
            content.append("§f").append(name).append("\n");
        }
        builder.content(content.toString());

        for (UUID u : members) {
            String name = getPlayerName(u);
            builder.button("§f" + name + "\n§c点击踢出");
        }
        builder.button("§7返回管理面板");

        builder.validResultHandler((SimpleFormResponse res) -> {
                    int idx = res.clickedButtonId();
                    if (idx < members.size()) {
                        UUID target = members.get(idx);
                        Player targetPlayer = org.bukkit.Bukkit.getPlayer(target);
                        if (targetPlayer != null) {
                            guilds.kick(p, targetPlayer);
                        } else {
                            g.members.remove(target);
                            g.deputies.remove(target);
                            p.sendMessage("§c玩家已离线，已从公会移除");
                        }
                        openManagePanelUI(p);
                    } else {
                        openManagePanelUI(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openManagePanelUI(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送踢出成员表单失败: " + e.getMessage());
        }
    }

    // ==================== 设置副会长 ====================
    private void openSetDeputyUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null || !guilds.isLeader(p)) {
            p.sendMessage("§c需要会长权限");
            openMainMenu(p);
            return;
        }

        if (g.deputies.size() >= 2) {
            p.sendMessage("§c副会长已达上限 2 人");
            openManagePanelUI(p);
            return;
        }

        List<UUID> candidates = new ArrayList<>(g.members);
        candidates.remove(p.getUniqueId());
        candidates.removeAll(g.deputies);

        if (candidates.isEmpty()) {
            p.sendMessage("§c没有可设置的成员");
            openManagePanelUI(p);
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§6设置副会长");

        StringBuilder content = new StringBuilder();
        content.append("§7当前副会长: §e").append(g.deputies.size()).append("/2 人\n");
        content.append("§7点击玩家设置为副会长\n\n");
        for (UUID u : candidates) {
            String name = getPlayerName(u);
            content.append("§f").append(name).append("\n");
        }
        builder.content(content.toString());

        for (UUID u : candidates) {
            String name = getPlayerName(u);
            builder.button("§f" + name + "\n§6点击设为副会长");
        }
        builder.button("§7返回管理面板");

        builder.validResultHandler((SimpleFormResponse res) -> {
                    int idx = res.clickedButtonId();
                    if (idx < candidates.size()) {
                        UUID target = candidates.get(idx);
                        Player targetPlayer = org.bukkit.Bukkit.getPlayer(target);
                        if (targetPlayer != null) {
                            guilds.setDeputy(p, targetPlayer);
                        } else {
                            p.sendMessage("§c玩家不在线");
                        }
                        openManagePanelUI(p);
                    } else {
                        openManagePanelUI(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openManagePanelUI(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送设置副会长表单失败: " + e.getMessage());
        }
    }

    // ==================== 撤销副会长 ====================
    private void openRemoveDeputyUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null || !guilds.isLeader(p)) {
            p.sendMessage("§c需要会长权限");
            openMainMenu(p);
            return;
        }

        if (g.deputies.isEmpty()) {
            p.sendMessage("§c没有副会长可撤销");
            openManagePanelUI(p);
            return;
        }

        List<UUID> deputies = new ArrayList<>(g.deputies);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§c撤销副会长");

        StringBuilder content = new StringBuilder();
        content.append("§7点击副会长撤销职位\n\n");
        for (UUID u : deputies) {
            String name = getPlayerName(u);
            content.append("§f").append(name).append("\n");
        }
        builder.content(content.toString());

        for (UUID u : deputies) {
            String name = getPlayerName(u);
            builder.button("§f" + name + "\n§c点击撤销");
        }
        builder.button("§7返回管理面板");

        builder.validResultHandler((SimpleFormResponse res) -> {
                    int idx = res.clickedButtonId();
                    if (idx < deputies.size()) {
                        UUID target = deputies.get(idx);
                        Player targetPlayer = org.bukkit.Bukkit.getPlayer(target);
                        if (targetPlayer != null) {
                            guilds.removeDeputy(p, targetPlayer);
                        } else {
                            p.sendMessage("§c玩家不在线，已从副会长中移除");
                            g.deputies.remove(target);
                        }
                        openManagePanelUI(p);
                    } else {
                        openManagePanelUI(p);
                    }
                })
                .closedOrInvalidResultHandler(() -> openManagePanelUI(p));

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("发送撤销副会长表单失败: " + e.getMessage());
        }
    }

    // ==================== 解散公会确认 ====================
    private void openDisbandConfirmUI(Player p) {
        if (!isBedrock(p)) return;

        Guilds.Guild g = guilds.getGuild(p);
        if (g == null || !guilds.isLeader(p)) {
            p.sendMessage("§c需要会长权限");
            openMainMenu(p);
            return;
        }

        SimpleForm form = SimpleForm.builder()
                .title("§c解散公会确认")
                .content("§c⚠️ 确定要解散 " + g.name + " 吗？\n§7此操作不可恢复！\n\n§7成员数: §e" + g.members.size() + " 人")
                .button("§c确认解散")
                .button("§a取消")
                .validResultHandler((SimpleFormResponse res) -> {
                    if (res.clickedButtonId() == 0) {
                        guilds.disband(p);
                        p.sendMessage("§c公会已解散");
                    }
                    openMainMenu(p);
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送解散确认表单失败: " + e.getMessage());
        }
    }

    // ==================== 退出公会 ====================
    private void handleLeaveGuild(Player p) {
        Guilds.Guild g = guilds.getGuild(p);
        if (g == null) {
            p.sendMessage("§c你还没有加入公会");
            openMainMenu(p);
            return;
        }

        if (guilds.isLeader(p)) {
            p.sendMessage("§c会长不能退出公会，请先解散或转让会长");
            openMainMenu(p);
            return;
        }

        SimpleForm form = SimpleForm.builder()
                .title("§c退出公会确认")
                .content("§c确定要退出 " + g.name + " 吗？")
                .button("§c确认退出")
                .button("§a取消")
                .validResultHandler((SimpleFormResponse res) -> {
                    if (res.clickedButtonId() == 0) {
                        guilds.leave(p);
                        p.sendMessage("§a已退出公会");
                    }
                    openMainMenu(p);
                })
                .closedOrInvalidResultHandler(() -> openMainMenu(p))
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送退出公会表单失败: " + e.getMessage());
        }
    }

    // ==================== 邀请表单 ====================
    public void openInviteForm(Player p, String guildName, String inviterName) {
        if (!isBedrock(p)) return;

        SimpleForm form = SimpleForm.builder()
                .title("§6公会邀请")
                .content("§f公会: §6" + guildName + "\n§f邀请人: §a" + inviterName + "\n§c60秒内有效")
                .button("§a接受")
                .button("§c拒绝")
                .validResultHandler((SimpleFormResponse res) -> {
                    if (res.clickedButtonId() == 0) {
                        guilds.acceptInvite(p, guildName);
                    } else {
                        guilds.declineGuild(p, guildName);
                    }
                })
                .closedOrInvalidResultHandler(() -> {
                    p.sendMessage("§c你已关闭邀请，如需接受请使用 /guild acceptinvite " + guildName);
                })
                .build();

        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Exception e) {
            plugin.getLogger().warning("发送邀请表单失败: " + e.getMessage());
            p.sendMessage("§6你收到公会 " + guildName + " 的邀请，输入 /guild acceptinvite " + guildName + " 接受（60秒内有效）");
        }
    }
}