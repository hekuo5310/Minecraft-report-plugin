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
                plugin.getLogger().log(Level.SEVERE, "无法创建 reports.yml 文件!", e);
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
            plugin.getLogger().log(Level.SEVERE, "无法保存 reports.yml 文件!", e);
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
            sender.sendMessage(ChatColor.YELLOW + "当前没有待处理的举报");
            return;
        }

        int totalPages = (int) Math.ceil((double) unhandledReports.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, unhandledReports.size());
        List<Map<String, Object>> pageReports = unhandledReports.subList(fromIndex, toIndex);

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "举报列表" + 
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
                ChatColor.RED + "被举报人: " + ChatColor.YELLOW + target + "  " +
                ChatColor.BLUE + "举报人: " + ChatColor.YELLOW + reporter + "  " +
                ChatColor.GREEN + "理由: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "   时间: " + sdf.format(new Date(timestamp))
            );

            TextComponent logsBtn = new TextComponent("[查看日志]");
            logsBtn.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            logsBtn.setBold(true);
            logsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("点击查看玩家近期行为和检测结果").create()));
            logsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/report logs " + target));
            line.addExtra(new TextComponent("\n" + ChatColor.GRAY + "   "));
            line.addExtra(logsBtn);

            // 封禁按钮
            TextComponent permBan = createBanButton("[永久封禁]", "perm", target, net.md_5.bungee.api.ChatColor.RED);
            TextComponent dayBan = createBanButton("[封禁天数]", "day", target, net.md_5.bungee.api.ChatColor.GOLD);
            TextComponent hourBan = createBanButton("[封禁小时]", "hour", target, net.md_5.bungee.api.ChatColor.YELLOW);

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

        // 分页按钮
        TextComponent pagination = new TextComponent("");
        if (page > 0) {
            TextComponent prevBtn = new TextComponent("[<< 上一页]");
            prevBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report page " + (page - 1)));
            prevBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("跳转到上一页").create()));
            pagination.addExtra(prevBtn);
        }
        if (page > 0 && page < totalPages - 1) {
            pagination.addExtra(new TextComponent("     "));
        }
        if (page < totalPages - 1) {
            TextComponent nextBtn = new TextComponent("[下一页 >>]");
            nextBtn.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            nextBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report page " + (page + 1)));
            nextBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("跳转到下一页").create()));
            pagination.addExtra(nextBtn);
        }
        
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(pagination);
        }
    }

    /**
     * 显示玩家活动日志和算法检测结果
     * 显示攻击数据、CPS、挖掘等，异常数据标红
     */
    public void showLogs(CommandSender sender, String playerName, int logPage) {
        final int LOG_PAGE_SIZE = 10; // 每页事件数
        
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "玩家 " + playerName + " 不在线");
            return;
        }

        PlayerActivityTracker.PlayerActivityData data = activityTracker.getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "暂无 " + playerName + " 的活动数据");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        // === 标题 ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.BOLD + "玩家活动日志" + 
                          ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.WHITE + "  玩家: " + ChatColor.YELLOW + playerName + 
                          ChatColor.GRAY + " | 查看范围: 举报时间前后5分钟");
        sender.sendMessage("");

        // === 算法检测结果 (标红显示异常项) ===
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.RED + " [ 检测结果 ] " + 
                          ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + "--------------------");
        
        if (!data.detections.isEmpty()) {
            for (PlayerActivityTracker.DetectionResult detection : data.detections) {
                if (detection.isFlagged) {
                    // 标记为异常 - 红色显示
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.RED + "  [" + ChatColor.BOLD + "!! 警告 !! " + 
                                     ChatColor.RED + "] " + detection.category);
                    sender.sendMessage(ChatColor.RED + "  级别: " + ChatColor.DARK_RED + 
                                     ChatColor.BOLD + detection.severity);
                    sender.sendMessage(ChatColor.RED + "  数值: " + ChatColor.WHITE + detection.value);
                    sender.sendMessage(ChatColor.RED + "  说明: " + ChatColor.GRAY + detection.description);
                    
                    // 警告提示（可点击直接封禁）
                    TextComponent warning = new TextComponent(
                        "\n  " + ChatColor.DARK_RED + ">>> " + 
                        ChatColor.RED + "需要管理员处理" + 
                        ChatColor.DARK_RED + " <<<"
                    );
                    warning.setColor(net.md_5.bungee.api.ChatColor.RED);
                    warning.setBold(true);
                    if (sender instanceof Player) {
                        warning.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                            "/report ban " + playerName + " perm"));
                        warning.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder("点击永久封禁此玩家").create()));
                        ((Player) sender).spigot().sendMessage(warning);
                    } else {
                        sender.sendMessage(warning.toPlainText());
                    }
                } else {
                    // 可疑但未标记 - 黄色显示
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.YELLOW + "  [!] " + detection.category);
                    sender.sendMessage(ChatColor.YELLOW + "  级别: " + ChatColor.GOLD + detection.severity);
                    sender.sendMessage(ChatColor.YELLOW + "  数值: " + ChatColor.WHITE + detection.value);
                    sender.sendMessage(ChatColor.YELLOW + "  说明: " + ChatColor.GRAY + detection.description);
                }
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + "  未检测到可疑行为");
        }
        sender.sendMessage("");

        // === 战斗数据 ===
        sender.sendMessage(ChatColor.DARK_BLUE + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.BLUE + " [ 战斗数据 ] " + 
                          ChatColor.DARK_BLUE + "" + ChatColor.STRIKETHROUGH + "--------------------");
        
        // CPS 显示，根据阈值变色
        String cpsDisplay;
        if (data.currentCPS >= PlayerActivityTracker.EXTREME_CPS_THRESHOLD) {
            cpsDisplay = ChatColor.RED + "" + ChatColor.BOLD + data.currentCPS + 
                        " CPS (极高 - 可能使用自动点击器!)";
        } else if (data.currentCPS >= PlayerActivityTracker.HIGH_CPS_THRESHOLD) {
            cpsDisplay = ChatColor.YELLOW + "" + data.currentCPS + 
                        " CPS (较高 - 可能使用自动点击器)";
        } else {
            cpsDisplay = ChatColor.GREEN + "" + data.currentCPS + " CPS (正常)";
        }
        sender.sendMessage(ChatColor.AQUA + "  点击频率(CPS): " + cpsDisplay);

        // 攻击频率显示
        String attackDisplay;
        if (data.currentAttackRate > PlayerActivityTracker.ATTACK_THRESHOLD) {
            attackDisplay = ChatColor.RED + "" + ChatColor.BOLD + 
                           data.currentAttackRate + " 次/5秒 (异常!)";
        } else {
            attackDisplay = ChatColor.GREEN + "" + data.currentAttackRate + " 次/5秒";
        }
        sender.sendMessage(ChatColor.AQUA + "  攻击频率: " + attackDisplay);

        // 近期攻击记录(分页)
        sender.sendMessage(ChatColor.AQUA + "  近期攻击记录:");
        List<PlayerActivityTracker.ActivityEvent> recentAttacks = new ArrayList<>(data.attackEvents);
        Collections.reverse(recentAttacks); // 最新的在前
        
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
        
        // 攻击记录分页
        showLogPagination(sender, recentAttacks.size(), logPage, totalAttackPages, 
                         "attack", playerName, LOG_PAGE_SIZE);

        // === 挖掘数据 ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + 
                          "--------------------" + ChatColor.RESET + 
                          ChatColor.GREEN + " [ 挖掘数据 ] " + 
                          ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "--------------------");

        // 挖掘频率显示
        String miningDisplay;
        if (data.currentMiningRate > PlayerActivityTracker.MINING_THRESHOLD) {
            miningDisplay = ChatColor.RED + "" + ChatColor.BOLD + 
                           data.currentMiningRate + " 块/5秒 (异常 - 可能使用破坏加速!)";
        } else {
            miningDisplay = ChatColor.GREEN + "" + data.currentMiningRate + " 块/5秒";
        }
        sender.sendMessage(ChatColor.AQUA + "  挖掘速度: " + miningDisplay);

        // 近期挖掘记录
        sender.sendMessage(ChatColor.AQUA + "  近期破坏方块:");
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
        
        // 挖掘记录分页
        showLogPagination(sender, recentMining.size(), logPage, totalMiningPages, 
                         "mine", playerName, LOG_PAGE_SIZE);

        // === 异常移动 ===
        if (!data.suspiciousEvents.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + 
                              "--------------------" + ChatColor.RESET + 
                              ChatColor.RED + " [ 异常移动 ] " + 
                              ChatColor.DARK_RED + "" + ChatColor.STRIKETHROUGH + "--------------------");
            
            List<PlayerActivityTracker.ActivityEvent> recentSuspicious = new ArrayList<>(data.suspiciousEvents);
            Collections.reverse(recentSuspicious);
            
            fromIdx = logPage * LOG_PAGE_SIZE;
            toIdx = Math.min(fromIdx + LOG_PAGE_SIZE, recentSuspicious.size());
            
            for (int i = fromIdx; i < toIdx && i < 20; i++) { // 只显示最近20条
                PlayerActivityTracker.ActivityEvent event = recentSuspicious.get(i);
                sender.sendMessage(ChatColor.RED + "    " + sdf.format(new Date(event.timestamp)) + 
                                 " -> " + ChatColor.BOLD + event.target + 
                                 (event.details != null ? " " + event.details : "") +
                                 ChatColor.DARK_GRAY + " " + event.location);
            }
        }

        // === 底部快捷操作 ===
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "----------");
        
        TextComponent footer = new TextComponent("");
        TextComponent backToReports = new TextComponent("[返回举报列表]");
        backToReports.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        backToReports.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report check"));
        backToReports.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("返回举报列表").create()));
        footer.addExtra(backToReports);
        
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(footer);
        }
        sender.sendMessage("");
    }

    private void showLogPagination(CommandSender sender, int totalItems, int currentPage, 
                                   int totalPages, String logType, String playerName, int pageSize) {
        if (totalPages <= 1) return;
        
        TextComponent pagination = new TextComponent(ChatColor.GRAY + "  第 " + (currentPage + 1) + " / " + totalPages + " 页: ");
        
        if (currentPage > 0) {
            TextComponent prevBtn = new TextComponent("[上一页]");
            prevBtn.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/report " + logType + "page " + playerName + " " + (currentPage - 1)));
            pagination.addExtra(prevBtn);
        }
        
        if (currentPage < totalPages - 1) {
            pagination.addExtra(new TextComponent(" "));
            TextComponent nextBtn = new TextComponent("[下一页]");
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
        String hoverText = type.equals("perm") ? "永久封禁此玩家" : 
                          "封禁此玩家 " + type + "(单位)";
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
                message = ChatColor.RED + "输入确认或输入 取消 中止对 " + 
                         ChatColor.YELLOW + targetName + ChatColor.RED + " 的永久封禁";
                break;
            case "day":
                message = ChatColor.GOLD + "输入封禁天数来封禁 " + ChatColor.YELLOW + targetName + 
                         ChatColor.GOLD + " (或 输入 取消):";
                break;
            case "hour":
                message = ChatColor.YELLOW + "输入封禁小时数来封禁 " + ChatColor.YELLOW + targetName + 
                         ChatColor.YELLOW + " (或 输入 取消):";
                break;
            default:
                admin.sendMessage(ChatColor.RED + "无效的封禁类型");
                return;
        }
        
        admin.sendMessage(message);
        admin.sendMessage(ChatColor.GRAY + "在聊天框中输入以继续...");
        
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
