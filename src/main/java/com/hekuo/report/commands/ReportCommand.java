package com.hekuo.report.commands;

import com.hekuo.report.HekuoReport;
import com.hekuo.report.managers.ReportManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportCommand extends Command implements TabExecutor {

    private final HekuoReport plugin;
    private final ReportManager reportManager;
    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.RED + "举报" + ChatColor.GRAY + "] ";

    public ReportCommand(HekuoReport plugin, String name) {
        super(name);
        this.plugin = plugin;
        this.reportManager = plugin.getReportManager();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // 无参数 - 显示帮助
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        // help 命令
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        // 管理员命令: /report check
        if (args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("report.admin")) {
                sender.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "你没有权限使用此命令!"));
                return;
            }
            reportManager.showReportList((ProxiedPlayer) sender, 1);
            return;
        }

        // 举报命令: /report <玩家名称> <理由>
        if (args.length < 2) {
            sender.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "用法: /report <玩家名称> <理由>"));
            return;
        }

        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "只有玩家才能执行此命令!"));
            return;
        }

        String targetName = args[0];
        StringBuilder reason = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reason.append(args[i]).append(" ");
        }

        submitReport((ProxiedPlayer) sender, targetName, reason.toString().trim());
    }

    /**
     * 提交举报
     */
    private void submitReport(ProxiedPlayer reporter, String targetName, String reason) {
        // 检查被举报玩家是否在线
        ProxiedPlayer target = getProxy().getPlayer(targetName);

        String finalTargetName = targetName;
        if (target != null) {
            finalTargetName = target.getName(); // 使用正确的大小写

            // 不能举报自己
            if (target.getUniqueId().equals(reporter.getUniqueId())) {
                reporter.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "你不能举报自己!"));
                return;
            }
        }

        long now = System.currentTimeMillis();

        // 添加举报记录
        reportManager.addReport(
            finalTargetName,
            reporter.getName(),
            reason,
            now,
            target != null ? target.getServer().getInfo().getName() : "未知"
        );

        // 通知举报人
        reporter.sendMessage(new TextComponent(PREFIX + ChatColor.GREEN +
            "举报已提交! 被举报人: " + ChatColor.YELLOW + finalTargetName +
            ChatColor.GREEN + ", 理由: " + ChatColor.YELLOW + reason));

        // 通知所有在线管理员
        notifyAdmins(reporter.getName(), finalTargetName, reason);
    }

    /**
     * 通知在线管理员有新举报
     */
    private void notifyAdmins(String reporterName, String targetName, String reason) {
        TextComponent notifyMsg = new TextComponent(PREFIX + ChatColor.RED + "新举报! ");
        
        TextComponent clickHint = new TextComponent(ChatColor.YELLOW + "[点击查看]");
        clickHint.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report check"));
        clickHint.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.GRAY + "点击查看所有举报")));

        notifyMsg.addExtra(clickHint);

        for (ProxiedPlayer player : getProxy().getPlayers()) {
            if (player.hasPermission("report.admin")) {
                player.sendMessage(notifyMsg);
            }
        }
    }

    /**
     * 显示帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(new TextComponent(""));
        sender.sendMessage(new TextComponent(ChatColor.RED + "====== " + ChatColor.BOLD + "hekuo举报系统" + ChatColor.RESET + ChatColor.RED + " ======"));
        sender.sendMessage(new TextComponent(""));
        
        // 帮助标题行
        sender.sendMessage(new TextComponent(ChatColor.GOLD + "命令列表:"));
        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "  /report <玩家> <理由>" + ChatColor.WHITE + " - 举报指定玩家"));
        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "  /report help" + ChatColor.WHITE + " - 显示此帮助信息"));
        
        if (sender.hasPermission("report.admin")) {
            sender.sendMessage(new TextComponent(""));
            sender.sendMessage(new TextComponent(ChatColor.RED + "管理员命令:"));
            sender.sendMessage(new TextComponent(ChatColor.YELLOW + "  /report check" + ChatColor.WHITE + " - 查看/处理举报列表"));
        }
        
        sender.sendMessage(new TextComponent(""));
        sender.sendMessage(new TextComponent(ChatColor.GRAY + "开发者: hekuo"));
        sender.sendMessage(new TextComponent(""));
    }

    /**
     * 处理封禁时长输入（通过聊天消息）
     */
    public boolean handleBanInput(ProxiedPlayer player, String message) {
        if (!reportManager.isWaitingForBanInput(player.getUniqueId())) {
            return false;
        }

        BanType banType = reportManager.getPlayerPendingBanType(player.getUniqueId());
        String targetName = reportManager.getPlayerPendingBanTarget(player.getUniqueId());

        try {
            double value = Double.parseDouble(message.replace(",", "."));

            if (value <= 0) {
                player.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "请输入一个大于0的数字!"));
                return true;
            }

            switch (banType) {
                case DAYS:
                    executeTempBan(player, targetName, value * 24 * 3600000L,
                        "封禁 " + (int)value + " 天");
                    break;
                case HOURS:
                    executeTempBan(player, targetName, value * 3600000L,
                        "封禁 " + (int)value + " 小时");
                    break;
                default:
                    break;
            }
            
            reportManager.clearWaitingForBanInput(player.getUniqueId());

        } catch (NumberFormatException e) {
            player.sendMessage(new TextComponent(PREFIX + ChatColor.RED + "无效数字! 请输入一个有效的数字，或输入 'cancel' 取消。"));
        }
        return true;
    }

    /**
     * 执行临时封禁
     */
    private void executeTempBan(ProxiedPlayer admin, String playerName, long durationMs, String durationText) {
        ProxiedPlayer target = getProxy().getPlayer(playerName);
        
        String kickMessage = "\n" + ChatColor.RED + "========== 封禁通知 ==========\n" +
            ChatColor.GRAY + "你已被服务器封禁\n" +
            ChatColor.GRAY + "原因: " + ChatColor.RED + "管理员操作处理举报\n" +
            ChatColor.GRAY + "时长: " + ChatColor.YELLOW + durationText + "\n" +
            ChatColor.GRAY + "操作者: " + ChatColor.YELLOW + admin.getName() + "\n" +
            ChatColor.RED + "================================\n";

        // 在线玩家直接踢出
        if (target != null) {
            target.disconnect(new TextComponent(kickMessage));
            getProxy().getServers().values().forEach(serverInfo -> 
                serverInfo.sendData("hekuo:ban", (playerName + "|" + System.currentTimeMillis() + durationMs).getBytes()));
        }

        admin.sendMessage(new TextComponent(PREFIX + ChatColor.GREEN +
            "已封禁玩家 " + ChatColor.YELLOW + playerName + ChatColor.GREEN +
            ", 时长: " + ChatColor.YELLOW + durationText));
    }

    /**
     * 执行永久封禁
     */
    public void executePermBan(ProxiedPlayer admin, String playerName) {
        ProxiedPlayer target = getProxy().getPlayer(playerName);

        String kickMessage = "\n" + ChatColor.RED + "========== 永久封禁通知 ==========\n" +
            ChatColor.GRAY + "你已被服务器永久封禁\n" +
            ChatColor.GRAY + "原因: " + ChatColor.RED + "管理员操作处理举报\n" +
            ChatColor.GRAY + "操作者: " + ChatColor.YELLOW + admin.getName() + "\n" +
            ChatColor.RED + "====================================\n";

        if (target != null) {
            target.disconnect(new TextComponent(kickMessage));
            getProxy().getServers().values().forEach(serverInfo -> 
                serverInfo.sendData("hekuo:ban", (playerName + "|perm").getBytes()));
        }

        admin.sendMessage(new TextComponent(PREFIX + ChatColor.GREEN +
            "已永久封禁玩家 " + ChatColor.YELLOW + playerName));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if (sender.hasPermission("report.admin")) {
                completions.add("check");
                completions.add("help");
            } else {
                completions.add("<玩家名称>");
            }
        }
        
        return completions;
    }
}
