package com.hekuo.report.managers;

import com.hekuo.report.HekuoReport;
import com.hekuo.report.commands.ReportCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

public class ReportManager {

    private final HekuoReport plugin;
    private File reportFile;
    private FileConfiguration reportConfig;
    private List<Map<String, Object>> reports;
    private PlayerActivityTracker activityTracker;

    public ReportManager(HekuoReport plugin) {
        this.plugin = plugin;
        this.reports = new ArrayList<>();
        loadReports();
        this.activityTracker = new PlayerActivityTracker(plugin);
    }

    public PlayerActivityTracker getActivityTracker() {
        return activityTracker;
    }

    private void loadReports() {
        reportFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                reportFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create reports.yml!", e);
            }
        }
        reportConfig = YamlConfiguration.loadConfiguration(reportFile);
        
        if (reportConfig.contains("reports")) {
            reports = (List<Map<String, Object>>) reportConfig.getList("reports", new ArrayList<>());
        }
    }

    public void saveReports() {
        reportConfig.set("reports", reports);
        try {
            reportConfig.save(reportFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save reports.yml!", e);
        }
    }

    public synchronized void addReport(String targetName, String reporterName, String reason) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", UUID.randomUUID().toString());
        report.put("target", targetName);
        report.put("reporter", reporterName);
        report.put("reason", reason);
        report.put("timestamp", System.currentTimeMillis());
        report.put("handled", false);
        reports.add(report);
        saveReports();
    }

    public void showReports(CommandSender sender, int page) {
        final int pageSize = 5;
        List<Map<String, Object>> unhandledReports = getUnhandledReports();
        
        if (unhandledReports.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No pending reports at this time.");
            return;
        }

        int totalPages = (int) Math.ceil((double) unhandledReports.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, unhandledReports.size());
        List<Map<String, Object>> pageReports = unhandledReports.subList(fromIndex, toIndex);

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "Reports" + 
                          ChatColor.GOLD + " (" + (page + 1) + "/" + totalPages + ") ===");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < pageReports.size(); i++) {
            Map<String, Object> report = pageReports.get(i);
            int index = fromIndex + i + 1;
            
            String target = (String) report.get("target");
            String reporter = (String) report.get("reporter");
            String reason = (String) report.get("reason");
            long timestamp = (Long) report.getOrDefault("timestamp", System.currentTimeMillis());

            TextComponent line = new TextComponent(
                ChatColor.GRAY + "[" + index + "] " +
                ChatColor.RED + "Target: " + ChatColor.YELLOW + target + "  " +
                ChatColor.BLUE + "Reporter: " + ChatColor.YELLOW + reporter + "  " +
                ChatColor.GREEN + "Reason: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "   Time: " + sdf.format(new Date(timestamp))
            );

            TextComponent logsBtn = new TextComponent("[View Logs]");
            logsBtn.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            logsBtn.setBold(true);
            logsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Click to view player's recent activity and detection results").create()));
            logsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/report logs " + target));
            line.addExtra(new TextComponent("\n" + ChatColor.GRAY + "   "));
            line.addExtra(logsBtn);

            // Ban buttons
            TextComponent permBan = createBanButton("[Perm Ban]", "perm", target, net.md_5.bungee.api.ChatColor.RED);
            TextComponent dayBan = createBanButton("[Ban Days]", "day", target, net.md_5.bungee.api.ChatColor.GOLD);
            TextComponent hourBan = createBanButton("[Ban Hours]", "hour", target, net.md_5.bungee.api.ChatColor.YELLOW);

            line.addExtra(new TextComponent("  "));
            line.addExtra(permBan);
            line.addExtra(new TextComponent("  "));
            line.addExtra(dayBan);
            line.addExtra(new TextComponent("  "));
            line.addExtra(hourBan);

            if (sender instanceof Player) {
                ((Player) sender).spigot().sendMessage(line);
            } else {
                sender.sendMessage(line.toPlainText());
            }
            sender.sendMessage("");
        }

        // Pagination buttons
        TextComponent pagination = new TextComponent("");
        if (page > 0) {
            TextComponent prevBtn = new TextComponent("[<< Prev]");
            prevBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report page " + (page - 1)));
            prevBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Go to previous page").create()));
            pagination.addExtra(prevBtn);
        }
        if (page > 0 && page < totalPages - 1) {
            pagination.addExtra(new TextComponent("     "));
        }
        if (page < totalPages - 1) {
            TextComponent nextBtn = new TextComponent("[Next >>]");
            nextBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            nextBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report page " + (page + 1)));
            nextBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Go to next page").create()));
            pagination.addExtra(nextBtn);
        }
        
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(pagination);
        }
    }

    /**
     * Show player activity logs with algorithm detection results
     * Displays attack data, CPS, mining, and flags suspicious behavior in RED
     */
    public void showLogs(CommandSender sender, String playerName, int logPage) {
        final int LOG_PAGE_SIZE = 10; // Events per page
        
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " is not online.");
            return;
        }

        PlayerActivityTracker.PlayerActivityData data = activityTracker.getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "No activity data available for " + playerName);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (5 * 60 * 1000); // 5 minutes before report

        // === Header ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.BOLD + "ACTIVITY LOG" + 
                          ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.WHITE + "  Player: " + ChatColor.YELLOW + playerName + 
                          ChatColor.GRAY + " | Window: +/- 5 minutes from report time");
        sender.sendMessage("");

        // === ALGORITHM DETECTION RESULTS (Top priority - flagged items in RED) ===
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.RED + " [ DETECTION RESULTS ] " + 
                          ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + "--------------------");
        
        if (!data.detections.isEmpty()) {
            for (PlayerActivityTracker.DetectionResult detection : data.detections) {
                if (detection.isFlagged) {
                    // FLAGGED - Show in RED
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.RED + "  [" + ChatColor.BOLD + "!! FLAGGED !! " + 
                                     ChatColor.RED + "] " + detection.category);
                    sender.sendMessage(ChatColor.RED + "  Severity: " + ChatColor.DARK_RED + 
                                     ChatColor.BOLD + detection.severity);
                    sender.sendMessage(ChatColor.RED + "  Value: " + ChatColor.WHITE + detection.value);
                    sender.sendMessage(ChatColor.RED + "  Details: " + ChatColor.GRAY + detection.description);
                    
                    // Visual warning indicator
                    TextComponent warning = new TextComponent(
                        "\n  " + ChatColor.DARK_RED + ">>> " + 
                        ChatColor.RED + "REQUIRES ADMIN ATTENTION" + 
                        ChatColor.DARK_RED + " <<<"
                    );
                    warning.setColor(net.md_5.bungee.api.ChatColor.RED);
                    warning.setBold(true);
                    if (sender instanceof Player) {
                        warning.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                            "/report ban " + playerName + " perm"));
                        warning.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder("Click to permanently ban this player").create()));
                        ((Player) sender).spigot().sendMessage(warning);
                    } else {
                        sender.sendMessage(warning.toPlainText());
                    }
                } else {
                    // Suspicious but not flagged - show in YELLOW
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.YELLOW + "  [!] " + detection.category);
                    sender.sendMessage(ChatColor.YELLOW + "  Severity: " + ChatColor.GOLD + detection.severity);
                    sender.sendMessage(ChatColor.YELLOW + "  Value: " + ChatColor.WHITE + detection.value);
                    sender.sendMessage(ChatColor.YELLOW + "  Details: " + ChatColor.GRAY + detection.description);
                }
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + "  No suspicious activity detected.");
        }
        sender.sendMessage("");

        // === COMBAT STATISTICS ===
        sender.sendMessage(ChatColor.DARK_BLUE + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.BLUE + " [ COMBAT DATA ] " + 
                          ChatColor.DARK_BLUE + "" + ChatColor.STRIKETHROUGH + "--------------------");
        
        // CPS Display with color coding based on threshold
        String cpsDisplay;
        if (data.currentCPS >= PlayerActivityTracker.EXTREME_CPS_THRESHOLD) {
            cpsDisplay = ChatColor.RED + "" + ChatColor.BOLD + data.currentCPS + 
                        " CPS (EXTREME - Autoclicker detected!)";
        } else if (data.currentCPS >= PlayerActivityTracker.HIGH_CPS_THRESHOLD) {
            cpsDisplay = ChatColor.YELLOW + "" + data.currentCPS + 
                        " CPS (High - Possible autoclicker)";
        } else {
            cpsDisplay = ChatColor.GREEN + "" + data.currentCPS + " CPS (Normal)";
        }
        sender.sendMessage(ChatColor.AQUA + "  Click Rate: " + cpsDisplay);

        // Attack rate display
        String attackDisplay;
        if (data.currentAttackRate > PlayerActivityTracker.ATTACK_THRESHOLD) {
            attackDisplay = ChatColor.RED + "" + ChatColor.BOLD + 
                           data.currentAttackRate + " attacks/5s (ABNORMAL)";
        } else {
            attackDisplay = ChatColor.GREEN + "" + data.currentAttackRate + " attacks/5s";
        }
        sender.sendMessage(ChatColor.AQUA + "  Attack Rate: " + attackDisplay);

        // Recent attack log (paginated)
        sender.sendMessage(ChatColor.AQUA + "  Recent Attacks:");
        List<PlayerActivityTracker.ActivityEvent> recentAttacks = new ArrayList<>(data.attackEvents);
        Collections.reverse(recentAttacks); // Most recent first
        
        int fromIdx = logPage * LOG_PAGE_SIZE;
        int toIdx = Math.min(fromIdx + LOG_PAGE_SIZE, recentAttacks.size());
        int totalAttackPages = Math.max(1, (int) Math.ceil((double) recentAttacks.size() / LOG_PAGE_SIZE));
        
        if (fromIdx >= recentAttacks.size()) {
            fromIdx = 0;
            toIdx = Math.min(LOG_PAGE_SIZE, recentAttacks.size());
        }
        
        for (int i = fromIdx; i < toIdx; i++) {
            PlayerActivityTracker.ActivityEvent event = recentAttacks.get(i);
            sender.sendMessage(ChatColor.GRAY + "    " + sdf.format(new Date(event.timestamp)) + 
                             " -> " + ChatColor.RED + event.target + 
                             ChatColor.GRAY + (event.details != null ? " (" + event.details + ")" : "") +
                             ChatColor.DARK_GRAY + " " + event.location);
        }
        
        // Attack log pagination
        showLogPagination(sender, recentAttacks.size(), logPage, totalAttackPages, 
                         "attack", playerName, LOG_PAGE_SIZE);

        // === MINING DATA ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.GREEN + " [ MINING DATA ] " + 
                          ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "--------------------");

        // Mining rate display
        String miningDisplay;
        if (data.currentMiningRate > PlayerActivityTracker.MINING_THRESHOLD) {
            miningDisplay = ChatColor.RED + "" + ChatColor.BOLD + 
                           data.currentMiningRate + " blocks/5s (ABNORMAL - Nuker suspected!)";
        } else {
            miningDisplay = ChatColor.GREEN + "" + data.currentMiningRate + " blocks/5s";
        }
        sender.sendMessage(ChatColor.AQUA + "  Mining Rate: " + miningDisplay);

        // Recent mining log
        sender.sendMessage(ChatColor.AQUA + "  Recent Blocks Broken:");
        List<PlayerActivityTracker.ActivityEvent> recentMining = new ArrayList<>(data.miningEvents);
        Collections.reverse(recentMining);
        
        fromIdx = logPage * LOG_PAGE_SIZE;
        toIdx = Math.min(fromIdx + LOG_PAGE_SIZE, recentMining.size());
        int totalMiningPages = Math.max(1, (int) Math.ceil((double) recentMining.size() / LOG_PAGE_SIZE));
        
        if (fromIdx >= recentMining.size()) {
            fromIdx = 0;
            toIdx = Math.min(LOG_PAGE_SIZE, recentMining.size());
        }
        
        for (int i = fromIdx; i < toIdx; i++) {
            PlayerActivityTracker.ActivityEvent event = recentMining.get(i);
            sender.sendMessage(ChatColor.GRAY + "    " + sdf.format(new Date(event.timestamp)) + 
                             " -> " + ChatColor.GREEN + event.target +
                             ChatColor.DARK_GRAY + " " + event.location);
        }
        
        // Mining log pagination
        showLogPagination(sender, recentMining.size(), logPage, totalMiningPages, 
                         "mine", playerName, LOG_PAGE_SIZE);

        // === SUSPICIOUS MOVEMENTS ===
        if (!data.suspiciousEvents.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + 
                              "--------------------" + ChatColor.RESET + 
                              ChatColor.RED + " [ SUSPICIOUS MOVEMENTS ] " + 
                              ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + "--------------------");
            
            List<PlayerActivityTracker.ActivityEvent> recentSuspicious = new ArrayList<>(data.suspiciousEvents);
            Collections.reverse(recentSuspicious);
            
            fromIdx = logPage * LOG_PAGE_SIZE;
            toIdx = Math.min(fromIdx + LOG_PAGE_SIZE, recentSuspicious.size());
            
            for (int i = fromIdx; i < toIdx && i < 20; i++) { // Limit to last 20 suspicious events
                PlayerActivityTracker.ActivityEvent event = recentSuspicious.get(i);
                sender.sendMessage(ChatColor.RED + "    " + sdf.format(new Date(event.timestamp)) + 
                                 " -> " + ChatColor.BOLD + event.target + 
                                 (event.details != null ? " " + event.details : "") +
                                 ChatColor.DARK_GRAY + " " + event.location);
            }
        }

        // === Footer with quick actions ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "----------");
        
        TextComponent footer = new TextComponent("");
        TextComponent backToReports = new TextComponent("[Back to Reports]");
        backToReports.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        backToReports.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report check"));
        backToReports.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("Return to reports list").create()));
        footer.addExtra(backToReports);
        
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(footer);
        }
        sender.sendMessage("");
    }

    private void showLogPagination(CommandSender sender, int totalItems, int currentPage, 
                                   int totalPages, String logType, String playerName, int pageSize) {
        if (totalPages <= 1) return;
        
        TextComponent pagination = new TextComponent(ChatColor.GRAY + "  Page " + (currentPage + 1) + 
                                                     "/" + totalPages + ": ");
        
        if (currentPage > 0) {
            TextComponent prevBtn = new TextComponent("[Prev]");
            prevBtn.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/report " + logType + "page " + playerName + " " + (currentPage - 1)));
            pagination.addExtra(prevBtn);
        }
        
        if (currentPage < totalPages - 1) {
            pagination.addExtra(new TextComponent(" "));
            TextComponent nextBtn = new TextComponent("[Next]");
            nextBtn.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            nextBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/report " + logType + "page " + playerName + " " + (currentPage + 1)));
            pagination.addExtra(nextBtn);
        }
        
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(pagination);
        }
    }

    private TextComponent createBanButton(String text, String type, String target, net.md_5.bungee.api.ChatColor color) {
        TextComponent btn = new TextComponent(text);
        btn.setColor(color);
        btn.setBold(false);
        String hoverText = type.equals("perm") ? "Permanently ban this player" : 
                          "Ban this player in " + type + "(s)";
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder(hoverText).create()));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
            "/report ban " + target + " " + type));
        return btn;
    }

    private List<Map<String, Object>> getUnhandledReports() {
        List<Map<String, Object>> unhandled = new ArrayList<>();
        for (Map<String, Object> report : reports) {
            if (!(Boolean) report.getOrDefault("handled", false)) {
                unhandled.add(report);
            }
        }
        return unhandled;
    }

    public void initiateBan(Player admin, String targetName, String banType) {
        String message;
        switch (banType.toLowerCase()) {
            case "perm":
                message = ChatColor.RED + "Enter confirmation or type 'cancel' to abort permanent ban for " + 
                         ChatColor.YELLOW + targetName;
                break;
            case "day":
                message = ChatColor.GOLD + "Enter number of days to ban " + ChatColor.YELLOW + targetName + 
                         ChatColor.GOLD + " (or 'cancel'):";
                break;
            case "hour":
                message = ChatColor.YELLOW + "Enter number of hours to ban " + ChatColor.YELLOW + targetName + 
                         ChatColor.YELLOW + " (or 'cancel'):";
                break;
            default:
                admin.sendMessage(ChatColor.RED + "Invalid ban type.");
                return;
        }
        
        admin.sendMessage(message);
        admin.sendMessage(ChatColor.GRAY + "Type in chat to continue...");
        
        ReportCommand mainCommand = (ReportCommand) plugin.getCommand("report").getExecutor();
        mainCommand.addPendingBan(admin.getUniqueId(), new ReportCommand.BanType(targetName, banType));
    }

    public void markHandled(int index) {
        List<Map<String, Object>> unhandled = getUnhandledReports();
        if (index >= 0 && index < unhandled.size()) {
            Map<String, Object> report = unhandled.get(index);
            report.put("handled", true);
            saveReports();
        }
    }

    public List<Map<String, Object>> getReports() {
        return Collections.unmodifiableList(reports);
    }
}
