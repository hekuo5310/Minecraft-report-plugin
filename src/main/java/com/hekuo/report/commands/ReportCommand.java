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

        // Admin subcommands
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
                break; // Fall through to report submission
        }

        // Report submission: /report <player> <reason>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /report <player> <reason>");
            sender.sendMessage(ChatColor.GRAY + "Use /report help for more commands.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
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
        reporter.sendMessage(ChatColor.GREEN + "Your report against " + ChatColor.YELLOW + targetName + 
                          ChatColor.GREEN + " has been submitted. Reason: " + ChatColor.WHITE + reason);

        notifyAdmins(reporter.getName(), targetName, reason);
        return true;
    }

    // ========== HELP ==========

    private boolean sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "Hekuo's Report" + ChatColor.GOLD + " ===");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Player Commands:");
        sender.sendMessage(ChatColor.WHITE + "  /report <player> <reason>" + ChatColor.GRAY + " - Report a player");
        sender.sendMessage("");
        if (sender.hasPermission("report.admin")) {
            sender.sendMessage(ChatColor.RED + "Admin Commands:");
            sender.sendMessage(ChatColor.WHITE + "  /report check" + ChatColor.GRAY + " - View and manage reports");
            sender.sendMessage(ChatColor.WHITE + "  /report logs <player>" + ChatColor.GRAY + " - View activity & detection");
            sender.sendMessage(ChatColor.WHITE + "  /report ban <player> <perm|day|hour>" + ChatColor.GRAY + " - Ban a player");
            sender.sendMessage(ChatColor.WHITE + "  /report page <num>" + ChatColor.GRAY + " - Go to report page");
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Plugin developed by: hekuo");
        return true;
    }

    // ========== ADMIN COMMAND HANDLERS ==========

    private boolean handleCheckCommand(CommandSender sender) {
        if (!hasAdminPerm(sender)) return true;
        plugin.getReportManager().showReports(sender, 0);
        return true;
    }

    private boolean handleLogsCommand(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /report logs <player>");
            return true;
        }
        plugin.getReportManager().showLogs(sender, args[1], 0);
        return true;
    }

    private boolean handleBanCommand(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /report ban <player> <perm|day|hour>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command interactively.");
            return true;
        }

        String targetName = args[1];
        String banType = args[2];

        if (!banType.equalsIgnoreCase("perm") && 
            !banType.equalsIgnoreCase("day") && 
            !banType.equalsIgnoreCase("hour")) {
            sender.sendMessage(ChatColor.RED + "Invalid ban type. Use: perm, day, or hour.");
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
                sender.sendMessage(ChatColor.RED + "Invalid page number.");
                return true;
            }
        }
        plugin.getReportManager().showReports(sender, page);
        return true;
    }

    private boolean handleLogPageCommand(CommandSender sender, String[] args, String logType) {
        if (!hasAdminPerm(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /report " + logType + "page <player> <page>");
            return true;
        }
        int page = 0;
        try {
            page = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid page number.");
            return true;
        }
        plugin.getReportManager().showLogs(sender, args[1], page);
        return true;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        if (!sender.hasPermission("report.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return false;
        }
        return true;
    }

    // ========== ADMIN NOTIFICATIONS ==========

    private void notifyAdmins(String reporter, String target, String reason) {
        TextComponent message = new TextComponent(
            ChatColor.RED + "[Report] " + ChatColor.YELLOW + reporter + 
            ChatColor.WHITE + " reported " + ChatColor.RED + target + 
            ChatColor.WHITE + " for: " + ChatColor.GOLD + reason + " "
        );

        TextComponent checkBtn = new TextComponent("[Check]");
        checkBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        checkBtn.setBold(true);
        checkBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("Click to view reports").create()));
        checkBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report check"));
        message.addExtra(checkBtn);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("report.admin")) {
                player.spigot().sendMessage(message);
            }
        }
    }

    // ========== BAN EXECUTION (via chat input after clicking ban button) ==========

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingBans.containsKey(uuid)) return;
        
        event.setCancelled(true);
        BanType banType = pendingBans.remove(uuid);
        String input = event.getMessage().trim().toLowerCase();

        // Cancel option
        if (input.equals("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Ban operation cancelled.");
            return;
        }

        try {
            long duration = Long.parseLong(event.getMessage().trim());
            if (duration <= 0) {
                player.sendMessage(ChatColor.RED + "Duration must be a positive number.");
                pendingBans.put(uuid, banType); // Re-add so they can try again
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
            player.sendMessage(ChatColor.RED + "Invalid number. Please enter a valid duration or type 'cancel'.");
            pendingBans.put(uuid, banType); // Re-add so they can try again
        }
    }

    private void executeBan(Player admin, Player target, String type, long amount) {
        String adminName = admin.getName();
        String targetName = target.getName();
        
        switch (type.toLowerCase()) {
            case "perm": {
                // Permanent ban via BanList API
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "Permanently banned by: " + adminName, null, adminName);
                target.kickPlayer("You have been permanently banned.\nBanned by: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " permanently banned " + ChatColor.RED + targetName, 
                                "report.admin");
                break;
            }
            case "day": {
                long expiryMs = System.currentTimeMillis() + (amount * 24 * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "Banned for " + amount + " day(s) by: " + adminName, expiryDate, adminName);
                target.kickPlayer("You have been banned for " + amount + " day(s).\nBanned by: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " banned " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " for " + amount + " day(s)", "report.admin");
                break;
            }
            case "hour": {
                long expiryMs = System.currentTimeMillis() + (amount * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName,
                    "Banned for " + amount + " hour(s) by: " + adminName, expiryDate, adminName);
                target.kickPlayer("You have been banned for " + amount + " hour(s).\nBanned by: " + adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " banned " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " for " + amount + " hour(s)", "report.admin");
                break;
            }
            default:
                admin.sendMessage(ChatColor.RED + "Invalid ban type.");
                return;
        }
        
        // Mark the report as handled
        plugin.getReportManager().markHandled(0);
    }

    private void offlineBan(Player admin, String targetName, String type, long amount) {
        String adminName = admin.getName();
        
        switch (type.toLowerCase()) {
            case "perm":
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "Permanently banned by: " + adminName, null, adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " permanently banned " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " (offline)", "report.admin");
                break;
            case "day": {
                long expiryMs = System.currentTimeMillis() + (amount * 24 * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "Banned for " + amount + " day(s) by: " + adminName, expiryDate, adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " banned " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " for " + amount + " days (offline)", "report.admin");
                break;
            }
            case "hour": {
                long expiryMs = System.currentTimeMillis() + (amount * 60 * 60 * 1000L);
                Date expiryDate = new Date(expiryMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, 
                    "Banned for " + amount + " hour(s) by: " + adminName, expiryDate, adminName);
                Bukkit.broadcast(ChatColor.RED + "[Ban] " + ChatColor.YELLOW + adminName + 
                                ChatColor.WHITE + " banned " + ChatColor.RED + targetName + 
                                ChatColor.WHITE + " for " + amount + " hours (offline)", "report.admin");
                break;
            }
            default:
                admin.sendMessage(ChatColor.RED + "Invalid ban type.");
                return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.kickPlayer("You have been banned from the server.");
        }
    }

    // ========== PUBLIC API ==========

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
