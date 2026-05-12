package com.patchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patching")
public class PatchingProperties {

    private double roundRobinDelay = 5.0;
    private int batchMaxWorkers = 10;
    private String excelPath = "../servers_template.xlsx";
    private String statePath = "../patch_state.json";

    public double getRoundRobinDelay() { return roundRobinDelay; }
    public void setRoundRobinDelay(double roundRobinDelay) { this.roundRobinDelay = roundRobinDelay; }

    public int getBatchMaxWorkers() { return batchMaxWorkers; }
    public void setBatchMaxWorkers(int batchMaxWorkers) { this.batchMaxWorkers = batchMaxWorkers; }

    public String getExcelPath() { return excelPath; }
    public void setExcelPath(String excelPath) { this.excelPath = excelPath; }

    public String getStatePath() { return statePath; }
    public void setStatePath(String statePath) { this.statePath = statePath; }
}
