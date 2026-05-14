package com.patchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one service row from the Excel inventory.
 * Field names use camelCase; Jackson's SNAKE_CASE strategy serializes them
 * correctly for the frontend (e.g. serverName -> server_name).
 */
public class ServerRow {

    private int id;
    private String cluster;
    private String serverName;
    private String host;
    private String service;
    private String stopCmd;
    private String startCmd;
    private String statusCmd;
    private String mode;       // "round_robin" or "batch"
    private Double rrDelay;   // null = use config default
    private String group;
    private String notes;
    /** "nonprod" or "prod" — matches the column added in v2.3 of the Excel template. */
    private String environment;

    // ---------------------------------------------------------------- getters

    public int getId() { return id; }
    public String getCluster() { return cluster; }
    public String getServerName() { return serverName; }
    public String getHost() { return host; }
    public String getService() { return service; }
    public String getStopCmd() { return stopCmd; }
    public String getStartCmd() { return startCmd; }
    public String getStatusCmd() { return statusCmd; }
    public String getMode() { return mode; }
    public Double getRrDelay() { return rrDelay; }
    public String getGroup() { return group; }
    public String getNotes() { return notes; }
    public String getEnvironment() { return environment; }

    // ---------------------------------------------------------------- setters

    public void setId(int id) { this.id = id; }
    public void setCluster(String cluster) { this.cluster = cluster; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public void setHost(String host) { this.host = host; }
    public void setService(String service) { this.service = service; }
    public void setStopCmd(String stopCmd) { this.stopCmd = stopCmd; }
    public void setStartCmd(String startCmd) { this.startCmd = startCmd; }
    public void setStatusCmd(String statusCmd) { this.statusCmd = statusCmd; }
    public void setMode(String mode) { this.mode = mode; }
    public void setRrDelay(Double rrDelay) { this.rrDelay = rrDelay; }
    public void setGroup(String group) { this.group = group; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
