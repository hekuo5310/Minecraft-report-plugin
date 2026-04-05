package com.hekuo.report.commands;

import com.hekuo.report.HekuoReport;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReportCommand implements CommandExecutor, Listener {

    private final HekuoReport plugin;
    private final Map<UUID, BanType> pendingBans = new HashMap<>();

    public ReportCommand(HekuoReport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            return sendHelp(sender);
        }

        // 管理员子命令
        switch (args[0].toLowerCase()) {
            case "check":
                return handleCheckCommand(sender);

            case "logs":
                return handleLogsCommand(sender, args);

            case "ban":
                return handleBanCommand(sender, args);

            case "page":
                return handlePageCommand(sender, args);

            case "attackpage":
                return handleLogPageCommand(sender, args, "attack");

            case "minepage":
                return handleLogPageCommand(sender, args, "mine");

            default:
                break; // 跳转到举报提交
        }

        // 举报提交: /report <玩家名称> <理由>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /report <玩家名称> <理由>");
            sender.sendMessage(ChatColor.GRAY + "输入 /report help 查看更多命令");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家才能使用此命令");
            return true;
        }

        Player reporter = (Player) sender;
        String targetName = args[0];
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        plugin.getReportManager().addReport(targetName, reporter.getName(), reason);
        reporter.sendMessage(ChatColor.GREEN + "你对 " + ChatColor.YELLOW + targetName + 
                          ChatColor.GREEN + " 的举报已提交! 理由: " + ChatColor.WHITE + reason);

        notifyAdmins(reporter.getName(), targetName, reason);
        return true;
    }

    // ========== 帮助信息 ==========

    private boolean sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "hekuo举报插件" + ChatColor.GOLD + " ===");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "玩家命令:");
        sender.sendMessage(ChatColor.WHITE + "  /report <玩家> <理由>" + ChatColor.GRAY + " - 举报玩家");
        sender.sendMessage("");
        if (sender.hasPermission("report.admin")) {
            sender.sendMessage(ChatColor.RED + "管理员命令:");
            sender.sendMessage(ChatColor.WHITE + "  /report check" + ChatColor.GRAY + " - 查看和管理举报");
            sender.sendMessage(ChatColor.WHITE + "  /report logs <玩家>" + ChatColor.GRAY + " - 查看行为和检测结果");
            sender.sendMessage(ChatColor.WHITE + "  /report ban <玩家> <perm|day|hour>" + ChatColor.GRAY + " - 封禁玩家");
            sender.sendMessage(ChatColor.WHITE + "  /report page <页码>" + ChatColor.GRAY + " - 跳转页面");
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "作者: hekuo");
        return true;
    }

    // ========== 管理员命令处理 ==========

    private boolean handleCheckCommand(CommandSender sender) {
        if (!hasAdminPerm(sender)) return true;
        plugin.getReportManager().showReports(sender, 0);
        return true;
    }

    private boolean handleLogsCommand(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /report logs <玩家>");
            return true;
        }
        plugin.getReportManager().showLogs(sender, args[1], 0);
        return true;
    }

    private boolean handleBanCommand(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /report ban <玩家> <perm|day|hour>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家能使用此交互命令");
            return true;
        }

        String targetName = args[1];
        String banType = args[2];

        if (!banType.equalsIgnoreCase("perm") && 
            !banType.equalsIgnoreCase("day") && 
            !banType.equalsIgnoreCase("hour")) {
            sender.sendMessage(ChatColor.RED + "无效的封禁类型，请使用: perm(永久), day(天), hour(小时)");
            return true;
        }

        plugin.getReportManager().initiateBan((Player) sender, targetName, banType.toLowerCase());
        return true;
    }

    private boolean handlePageCommand(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) return true;
        int page = 0;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "无效的页码");
                return true;
            }
        }
        plugin.getReportManager().showReports(sender, page);
        return true;
    }

    private boolean handleLogPageCommand(CommandSender sender, String[] args, String logType) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /report " + logType + "page <玩家> <页码>");
            return true;
        }
        int page = 0;
        try {
            page = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的页码");
            return true;
        }
        plugin.getReportManager().showLogs(sender, args[1], page);
        return true;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        if (!sender.hasPermission("report.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令!");
            return false;
        }
        return true;
    }

    // ========== 管理员通知 ==========

    private void notifyAdmins(String reporter, String target, String reason) {
        TextComponent message = new TextComponent(
            ChatColor.RED + "[举报] " + ChatColor.YELLOW + reporter + 
            ChatColor.WHITE + " 举报了 " + ChatColor.RED + target + 
            ChatColor.WHITE + ", 原因: " + ChatColor.GOLD + reason + " "
        );

        TextComponent checkBtn = new TextComponent("[查看]");
        checkBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        checkBtn.setBold(true);
        checkBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("点击查看举报列表").create()));
        checkBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report check"));
        message.addExtra(checkBtn);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("report.admin")) {
                player.spigot().sendMessage(message);
            }
        }
    }

    // ========== 封禁执行 (点击封禁按钮后通过聊天输入时长) ==========

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingBans.containsKey(uuid)) return;
        
        event.setCancelled(true);
        BanType banType = pendingBans.remove(uuid);
        String input = event.getMessage().trim().toLowerCase();

        // 取消选项
        if (input.equals("cancel") || input.equals("取消")) {
            player.sendMessage(ChatColor.YELLOW + "封禁操作已取消");
            return;
        }

        try {
            long duration = Long.parseLong(event.getMessage().trim());
            if (duration <= 0) {
                player.sendMessage(ChatColor.RED + "请输入一个正数");
                pendingBans.put(uuid, banType); // 重新添加以便重试
                return;
            }

            String targetName = banType.getTargetName();
            Player target = Bukkit.getPlayerExact(targetName);
            
            if (target != null && target.isOnline()) {
                executeBan(player, target, banType.getType(), duration);
            } else {
                offlineBan(player, targetName, banType.getType(), duration);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "输入无效，请输入数字或 输入 取消 来中止操作");
            pendingBans.put(uuid, banType); // 重新添加以便重试
        }
    }

    private void executeBan(Player admin, Player target, String type, long amount) {
        String adminName = admin.getName();
        String targetName = target.getName();
        
        switch (type.toLowerCase()) {
            case "perm": {
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "被 " + adminName + " 永久封禁", null, adminName);
                target.kickPlayer("你已被永久封禁!\n操作者: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 永久封禁了 " + ChatColor.RED + targetName, 
                                "report.admin");
                break;
            }
            case "day": {
                long expiryMs = System.currentTimeMillis() + (amount * 24 * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "被 " + adminName + " 封禁 " + amount + " 天", expiryDate, adminName);
                target.kickPlayer("你已被封禁 " + amount + " 天!\n操作者: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 封禁了 " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " " + amount + " 天", "report.admin");
                break;
            }
            case "hour": {
                long expiryMs = System.currentTimeMillis() + (amount * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "被 " + adminName + " 封禁 " + amount + " 小时", expiryDate, adminName);
                target.kickPlayer("你已被封禁 " + amount + " 小时!\n操作者: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 封禁了 " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " " + amount + " 小时", "report.admin");
                break;
            }
            default:
                admin.sendMessage(ChatColor.RED + "无效的封禁类型");
                return;
        }
        
        // 标记举报已处理
        plugin.getReportManager().markHandled(0);
    }

    private void offlineBan(Player admin, String targetName, String type, long amount) {
        String adminName = admin.getName();
        
        switch (type.toLowerCase()) {
            case "perm":
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "被 " + adminName + " 永久封禁", null, adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 永久封禁了 " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " (离线)", "report.admin");
                break;
            case "day": {
                long expiryMs = System.currentTimeMillis() + (amount * 24 * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "被 " + adminName + " 封禁 " + amount + " 天", expiryDate, adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 封禁了 " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " " + amount + " 天 (离线)", "report.admin");
                break;
            }
            case "hour": {
                long expiryMs = System.currentTimeMillis() + (amount * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "被 " + adminName + " 封禁 " + amount + " 小时", expiryDate, adminName);
                Bukkit.broadcast(ChatColor.RED + "[封禁] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " 封禁了 " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " " + amount + " 小时 (离线)", "report.admin");
                break;
            }
            default:
                admin.sendMessage(ChatColor.RED + "无效的封禁类型");
                return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.kickPlayer("你已被服务器封禁!");
        }
    }

    // ========== 公开API ==========

    public void addPendingBan(UUID uuid, BanType banType) {
        pendingBans.put(uuid, banType);
    }

    public static class BanType {
        private final String targetName;
        private final String type; // perm, day, hour

        public BanType(String targetName, String type) {
            this.targetName = targetName;
            this.type = type;
        }

        public String getTargetName() { return targetName; }
        public String getType() { return type; }
    }
}
