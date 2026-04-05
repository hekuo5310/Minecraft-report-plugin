package com.hekuo.report;

import com.hekuo.report.commands.ReportCommand;
import com.hekuo.report.managers.ReportManager;
import net.md_5.bungee.api.plugin.Plugin;

public class HekuoReport extends Plugin {

    private static HekuoReport instance;
    private ReportManager reportManager;

    @Override
    public void onEnable() {
        instance = this;
        this.reportManager = new ReportManager(this);

        // 注册举报命令
        getProxy().getPluginManager().registerCommand(this, new ReportCommand(this, "report"));

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
