package com.hekuo.report.managers;

import com.hekuo.report.HekuoReport;
import com.hekuo.report.commands.ReportCommand;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportManager {

    private final HekuoReport plugin;
    private List<Map<String, Object>> reports = new ArrayList<>();
    private File dataFile;
    
    // 等待封禁时长输入的玩家
    private Map<UUID, BanInputData> waitingForBanInput = new HashMap<>();

    // 分页配置
    private static final int REPORTS_PER_PAGE = 5;

    public ReportManager(HekuoReport plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "reports.json");
        
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        loadReports();
    }

    /**
     * 添加举报记录
     */
    public void addReport(String targetName, String reporterName, String reason, long timestamp, String serverName) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("target", targetName);
        report.put("reporter", reporterName);
        report.put("reason", reason);
        report.put("timestamp", timestamp);
        report.put("server", serverName);
        report.put("handled", false);

        reports.add(report);
        saveReports();
    }

    /**
     * 显示举报列表（分页）
     */
    public void showReportList(ProxiedPlayer player, int page) {
        List<Map<String, Object>> unhandledReports = getUnhandledReports();

        if (unhandledReports.isEmpty()) {
            player.sendMessage(new TextComponent(ChatColor.GRAY + "========================================"));
            player.sendMessage(new TextComponent(ChatColor.GREEN + "  当前没有待处理的举报!"));
            player.sendMessage(new TextComponent(ChatColor.GRAY + "========================================"));
            return;
        }

        int totalPages = (int) Math.ceil((double) unhandledReports.size() / REPORTS_PER_PAGE);

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int fromIndex = (page - 1) * REPORTS_PER_PAGE;
        int toIndex = Math.min(fromIndex + REPORTS_PER_PAGE, unhandledReports.size());
        List<Map<String, Object>> pageReports = unhandledReports.subList(fromIndex, toIndex);

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getDefault());

        // 标题
        player.sendMessage(new TextComponent(""));
        TextComponent title = new TextComponent(
            ChatColor.RED + "====== " + ChatColor.BOLD + "举报列表 (" +
            ChatColor.YELLOW + page + "/" + totalPages +
            ChatColor.RED + ") " + unhandledReports.size() + "条待处理" + ChatColor.RESET + ChatColor.RED + " ======");
        player.sendMessage(title);
        player.sendMessage(new TextComponent(""));

        // 列表内容
        for (int i = 0; i < pageReports.size(); i++) {
            Map<String, Object> report = pageReports.get(i);
            String target = (String) report.get("target");
            String reporter = (String) report.get("reporter");
            String reason = (String) report.get("reason");
            String server = (String) report.getOrDefault("server", "未知");
            long timestamp = (long) report.get("timestamp");

            int index = fromIndex + i + 1;

            ComponentBuilder line = new ComponentBuilder("");

            // 序号
            line.append(new TextComponent(
                ChatColor.GRAY + String.format("[%02d] ", index)));

            // 被举报人名称
            TextComponent targetComp = new TextComponent(ChatColor.RED + "" + ChatColor.BOLD + target);
            targetComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "被举报人: " + target)));
            line.append(targetComp);

            // 举报人
            line.append(new TextComponent(ChatColor.GRAY + " 被 "));

            TextComponent reporterComp = new TextComponent(ChatColor.GREEN + reporter);
            reporterComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "举报人: " + reporter)));
            line.append(reporterComp);

            // 服务器信息
            line.append(new TextComponent(ChatColor.GRAY + " [" + ChatColor.AQUA + server + ChatColor.GRAY + "]"));

            // 理由
            line.append(new TextComponent("\n" + ChatColor.GRAY + "   理由: " + ChatColor.WHITE + reason));
            
            // 时间
            String timeStr = sdf.format(new Date(timestamp));
            line.append(new TextComponent(ChatColor.GRAY + "   时间: " + ChatColor.YELLOW + timeStr));

            // [查看日志] 按钮
            TextComponent viewLogsBtn = new TextComponent(
                ChatColor.GOLD + "  [" + ChatColor.BOLD + "查看日志" + ChatColor.RESET + ChatColor.GOLD + "]");
            viewLogsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report logs " + index));
            viewLogsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.YELLOW + "点击查看该玩家前后5分钟的操作记录")));
            viewLogsBtn.setBold(false);
            line.append(viewLogsBtn);

            // [封禁] 按钮 - 永久封禁
            TextComponent permBanBtn = new TextComponent(
                ChatColor.DARK_RED + " [" + ChatColor.BOLD + "永久封禁" + ChatColor.RESET + ChatColor.DARK_RED + "]");
            permBanBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report ban perm " + target));
            permBanBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.RED + "点击永久封禁该玩家")));
            permBanBtn.setBold(false);
            line.append(permBanBtn);

            // [封禁X天] 按钮
            TextComponent banDaysBtn = new TextComponent(
                ChatColor.RED + " [" + ChatColor.BOLD + "封禁天数" + ChatColor.RESET + ChatColor.RED + "]");
            banDaysBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report ban days " + target));
            banDaysBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GOLD + "点击后输入封禁天数")));
            banDaysBtn.setBold(false);
            line.append(banDaysBtn);

            // [封禁X小时] 按钮
            TextComponent banHoursBtn = new TextComponent(
                ChatColor.YELLOW + " [" + ChatColor.BOLD + "封禁时数" + ChatColor.RESET + ChatColor.YELLOW + "]");
            banHoursBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report ban hours " + target));
            banHoursBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GOLD + "点击后输入封禁小时数")));
            banHoursBtn.setBold(false);
            line.append(banHoursBtn);

            // 分隔线
            line.append(new TextComponent("\n" + ChatColor.DARK_GRAY + "----------------------------------------\n"));

            player.sendMessage(line.create());
        }

        // 翻页按钮
        player.sendMessage(new TextComponent(""));
        ComponentBuilder pagination = new ComponentBuilder("");

        if (page > 1) {
            TextComponent prevBtn = new TextComponent(
                ChatColor.GREEN + "<< 上一页 ");
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report check " + (page - 1)));
            prevBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "跳转到第 " + (page - 1) + " 页")));
            pagination.append(prevBtn);
        } else {
            pagination.append(new TextComponent(ChatColor.DARK_GRAY + "<< 上一页 "));
        }

        pagination.append(new TextComponent(ChatColor.WHITE + "  |  " + 
            ChatColor.GOLD + "第 " + page + "/" + totalPages + " 页  " + 
            ChatColor.WHITE + "|  "));

        if (page < totalPages) {
            TextComponent nextBtn = new TextComponent(
                ChatColor.GREEN + "下一页 >>");
            nextBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/report check " + (page + 1)));
            nextBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "跳转到第 " + (page + 1) + " 页")));
            pagination.append(nextBtn);
        } else {
            pagination.append(new TextComponent(ChatColor.DARK_GRAY + "下一页 >>"));
        }

        player.sendMessage(pagination.create());
        player.sendMessage(new TextComponent(""));
    }

    /**
     * 获取未处理的举报列表
     */
    public List<Map<String, Object>> getUnhandledReports() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> report : reports) {
            Boolean handled = (Boolean) report.getOrDefault("handled", false);
            if (!handled) {
                result.add(report);
            }
        }
        return result;
    }

    /**
     * 获取指定索引的举报记录（从所有未处理举报中查找）
     */
    public Map<String, Object> getReportByDisplayIndex(int displayIndex) {
        List<Map<String, Object>> unhandled = getUnhandledReports();
        if (displayIndex >= 1 && displayIndex <= unhandled.size()) {
            return unhandled.get(displayIndex - 1);
        }
        return null;
    }

    // ========== 封禁交互相关 ==========

    /**
     * 设置玩家等待封禁时长输入状态
     */
    public void setWaitingForBanInput(UUID playerUUID, String targetName, BanType banType) {
        waitingForBanInput.put(playerUUID, new BanInputData(targetName, banType));
    }

    /**
     * 检查玩家是否正在等待输入封禁时长
     */
    public boolean isWaitingForBanInput(UUID playerUUID) {
        return waitingForBanInput.containsKey(playerUUID);
    }

    /**
     * 获取玩家待处理的封禁类型
     */
    public BanType getPlayerPendingBanType(UUID playerUUID) {
        BanInputData data = waitingForBanInput.get(playerUUID);
        return data != null ? data.banType : null;
    }

    /**
     * 获取玩家待处理封禁的目标玩家名
     */
    public String getPlayerPendingBanTarget(UUID playerUUID) {
        BanInputData data = waitingForBanInput.get(playerUUID);
        return data != null ? data.targetName : null;
    }

    /**
     * 清除等待状态
     */
    public void clearWaitingForBanInput(UUID playerUUID) {
        waitingForBanInput.remove(playerUUID);
    }

    // ========== 数据持久化 ==========

    /**
     * 保存举报数据到文件
     */
    @SuppressWarnings("unchecked")
    public void saveReports() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < reports.size(); i++) {
                sb.append("  {\n");
                Map<String, Object> report = reports.get(i);
                int j = 0;
                for (Map.Entry<String, Object> entry : report.entrySet()) {
                    Object val = entry.getValue();
                    String valueStr;
                    if (val instanceof String) {
                        valueStr = "\"" + escapeJson((String) val) + "\"";
                    } else if (val instanceof Long || val instanceof Integer) {
                        valueStr = String.valueOf(val);
                    } else if (val instanceof Boolean) {
                        valueStr = String.valueOf(val);
                    } else {
                        valueStr = "\"" + escapeJson(String.valueOf(val)) + "\"";
                    }
                    sb.append("    \"").append(entry.getKey()).append("\": ").append(valueStr);
                    if (j++ < report.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("  }");
                if (i < reports.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");

            try (FileWriter fw = new FileWriter(dataFile);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(sb.toString());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("保存举报数据失败: " + e.getMessage());
        }
    }

    /**
     * 从文件加载举报数据
     */
    @SuppressWarnings("unchecked")
    public void loadReports() {
        if (!dataFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            // 简单JSON解析
            reports = parseSimpleJsonArray(content.toString());

        } catch (IOException e) {
            plugin.getLogger().warning("加载举报数据失败，将创建新文件: " + e.getMessage());
            reports = new ArrayList<>();
        }
    }

    /**
     * 简易JSON数组解析器
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSimpleJsonArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return result;

        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        // 按对象分割
        List<String> objects = splitObjects(json);

        for (String objStr : objects) {
            Map<String, Object> obj = parseSimpleJsonObject(objStr.trim());
            if (!obj.isEmpty()) {
                result.add(obj);
            }
        }

        return result;
    }

    /**
     * 解析单个JSON对象
     */
    private Map<String, Object> parseSimpleJsonObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return map;

        json = json.substring(1, json.length() - 1).trim();

        // 简单分割键值对
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') depth++;
            if (c == ',' && depth % 2 == 0) {
                parseKeyValue(current.toString().trim(), map);
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parseKeyValue(current.toString().trim(), map);
        }

        return map;
    }

    /**
     * 解析键值对
     */
    private void parseKeyValue(String pair, Map<String, Object> map) {
        int colonIdx = pair.indexOf(':');
        if (colonIdx < 0) return;

        String key = pair.substring(0, colonIdx).trim().replace("\"", "");
        String value = pair.substring(colonIdx + 1).trim();

        if (value.startsWith("\"") && value.endsWith("\"")) {
            map.put(key, unescapeJson(value.substring(1, value.length() - 1)));
        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            map.put(key, Boolean.parseBoolean(value));
        } else {
            try {
                map.put(key, Long.parseLong(value));
            } catch (NumberFormatException e) {
                map.put(key, value);
            }
        }
    }

    /**
     * 分割JSON数组中的对象
     */
    private List<String> splitObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0 && c == ',') {
                objects.add(json.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < json.length()) {
            objects.add(json.substring(start).trim());
        }

        return objects;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    /**
     * 封禁类型枚举
     */
    public enum BanType {
        PERM, DAYS, HOURS
    }

    /**
     * 封禁输入等待数据
     */
    public static class BanInputData {
        public final String targetName;
        public final BanType banType;

        public BanInputData(String targetName, BanType banType) {
            this.targetName = targetName;
            this.banType = banType;
        }
    }
}
