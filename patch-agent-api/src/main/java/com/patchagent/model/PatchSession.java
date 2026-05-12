package com.patchagent.model;

import java.util.List;
import java.util.Map;

/**
 * A persisted record of one completed job run.
 * Written to / read from patch_state.json.
 */
public class PatchSession {

    private String action;
    private String startedAt;
    private List<JobResult> results;
    private Map<String, Object> summary;

    public PatchSession() {}

    public PatchSession(String action, String startedAt,
                        List<JobResult> results) {
        this.action     = action;
        this.startedAt  = startedAt;
        this.results    = results;

        long ok     = results.stream().filter(JobResult::isOk).count();
        long errors = results.size() - ok;
        this.summary = Map.of(
            "total",  results.size(),
            "ok",     ok,
            "errors", errors
        );
    }

    public String getAction()               { return action; }
    public void setAction(String action)    { this.action = action; }

    public String getStartedAt()                    { return startedAt; }
    public void setStartedAt(String startedAt)      { this.startedAt = startedAt; }

    public List<JobResult> getResults()                 { return results; }
    public void setResults(List<JobResult> results)     { this.results = results; }

    public Map<String, Object> getSummary()                     { return summary; }
    public void setSummary(Map<String, Object> summary)         { this.summary = summary; }
}
