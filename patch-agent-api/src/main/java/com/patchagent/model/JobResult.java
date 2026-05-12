package com.patchagent.model;

/**
 * Result for a single service stop/start operation.
 * Mirrors the Python execute_service() result dict.
 */
public class JobResult {

    private int id;
    private String host;
    private String service;
    private String action;
    private String status;    // "ok" or "error"
    private String message;

    public JobResult(int id, String host, String service, String action) {
        this.id      = id;
        this.host    = host;
        this.service = service;
        this.action  = action;
        this.status  = "ok";
        this.message = "";
    }

    public void setError(String message) {
        this.status  = "error";
        this.message = message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isOk() { return "ok".equals(status); }

    // ---------------------------------------------------------------- getters

    public int getId()         { return id; }
    public String getHost()    { return host; }
    public String getService() { return service; }
    public String getAction()  { return action; }
    public String getStatus()  { return status; }
    public String getMessage() { return message; }
}
