package com.patchagent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory state for a running or completed job.
 *
 * The background job thread produces LogEvents by calling emit().
 * The SSE controller thread consumes them by calling takeEvent().
 * A LinkedBlockingQueue provides thread-safe handoff between them —
 * matching the Python queue.Queue pattern exactly.
 */
public class JobState {

    public enum Status { RUNNING, DONE, ERROR }

    private final String jobId;
    private final String action;
    private final boolean dryRun;
    private final String selection;

    private volatile Status status = Status.RUNNING;
    private final List<JobResult> results = new ArrayList<>();
    private final BlockingQueue<LogEvent> eventQueue = new LinkedBlockingQueue<>();

    public JobState(String jobId, String action, boolean dryRun, String selection) {
        this.jobId     = jobId;
        this.action    = action;
        this.dryRun    = dryRun;
        this.selection = selection;
    }

    // ---------------------------------------------------------------- event queue

    /** Called by the job background thread to push a log event. */
    public void emit(LogEvent event) {
        eventQueue.offer(event);
    }

    /**
     * Called by the SSE controller thread.
     * Blocks until an event is available or the timeout elapses.
     * Returns null on timeout (caller should send a heartbeat).
     * Returns LogEvent.DONE_SENTINEL when the job is finished.
     */
    public LogEvent takeEvent(long timeoutMs) throws InterruptedException {
        return eventQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ---------------------------------------------------------------- job lifecycle

    public synchronized void addResult(JobResult result) {
        results.add(result);
    }

    public void markDone() {
        this.status = Status.DONE;
        eventQueue.offer(LogEvent.DONE_SENTINEL);
    }

    public void markError() {
        this.status = Status.ERROR;
        eventQueue.offer(LogEvent.DONE_SENTINEL);
    }

    // ---------------------------------------------------------------- getters

    public String getJobId()           { return jobId; }
    public String getAction()          { return action; }
    public boolean isDryRun()          { return dryRun; }
    public String getSelection()       { return selection; }
    public Status getStatus()          { return status; }
    public List<JobResult> getResults(){ return results; }
}
