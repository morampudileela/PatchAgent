package com.patchagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * A single SSE log event emitted during job execution.
 * Mirrors the Python _emit() event structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEvent {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    /** Sentinel used to signal end of stream (not serialized to JSON). */
    public static final LogEvent DONE_SENTINEL = new LogEvent(null, null, null);

    private final String ts;
    private final String level;   // ok | info | warn | error
    private final String message;
    private Integer progress;
    private Integer total;
    private Boolean done;
    private Integer rowId;
    private String host;
    private String service;

    public LogEvent(String level, String message) {
        this.ts      = TS_FMT.format(Instant.now());
        this.level   = level;
        this.message = message;
    }

    private LogEvent(String ts, String level, String message) {
        // private constructor for sentinel
        this.ts      = ts;
        this.level   = level;
        this.message = message;
    }

    public boolean isSentinel() {
        return this == DONE_SENTINEL;
    }

    // ---------------------------------------------------------------- fluent setters

    public LogEvent progress(int progress, int total) {
        this.progress = progress;
        this.total    = total;
        return this;
    }

    public LogEvent done(boolean done) {
        this.done = done;
        return this;
    }

    public LogEvent rowContext(int rowId, String host, String service) {
        this.rowId   = rowId;
        this.host    = host;
        this.service = service;
        return this;
    }

    // ---------------------------------------------------------------- getters (for Jackson)

    public String getTs()       { return ts; }
    public String getLevel()    { return level; }
    public String getMessage()  { return message; }
    public Integer getProgress(){ return progress; }
    public Integer getTotal()   { return total; }
    public Boolean getDone()    { return done; }
    public Integer getRowId()   { return rowId; }
    public String getHost()     { return host; }
    public String getService()  { return service; }
}
