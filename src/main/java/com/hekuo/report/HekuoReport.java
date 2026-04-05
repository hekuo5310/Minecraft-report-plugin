package com.hekuo.report;

import com.hekuo.report.commands.ReportCommand;
import com.hekuo.report.managers.ReportManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HekuoReport extends JavaPlugin {

    private static HekuoReport instance;
    private ReportManager reportManager;

    @Override
    public void onEnable() {
        instance = this;
        this.reportManager = new ReportManager(this);

        getCommand("report").setExecutor(new ReportCommand(this));
        getServer().getPluginManager().registerEvents(new ReportCommand(this), this);
        
        // Register activity tracker for anti-cheat detection
        getServer().getPluginManager().registerEvents(
            reportManager.getActivityTracker(), this);

        getLogger().info("hekuo举报插件已启用!");
    }

    @Override
    public void onDisable() {
        if (reportManager != null) {
            reportManager.saveReports();
        }
        getLogger().info("hekuo举报插件已禁用!");
    }

    public static HekuoReport getInstance() {
        return instance;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }
}
