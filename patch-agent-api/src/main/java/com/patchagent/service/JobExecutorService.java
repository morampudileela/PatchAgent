package com.patchagent.service;

import com.patchagent.config.PatchingProperties;
import com.patchagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Executes a patching job (STOP or START) in a background thread.
 * Mirrors the Python run_job() function, including:
 *   - Round-robin phase (sequential, with per-server delay)
 *   - Batch phase (parallel, bounded concurrency)
 *   - Group-scoped dependency pre-flight check (STOP only)
 *   - Per-row status pre-check (skip already-stopped services)
 *   - SSE event emission via JobState queue
 */
@Service
public class JobExecutorService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutorService.class);
    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final SshService sshService;
    private final StateService stateService;
    private final PatchingProperties props;
    private final Executor executor;

    public JobExecutorService(SshService sshService,
                              StateService stateService,
                              PatchingProperties props,
                              Executor taskExecutor) {
        this.sshService    = sshService;
        this.stateService  = stateService;
        this.props         = props;
        this.executor      = taskExecutor;
    }

    // ---------------------------------------------------------------------------
    //  Entry point  (called by the controller, runs in background via executor)
    // ---------------------------------------------------------------------------

    @Async("taskExecutor")
    public void runJob(JobState job, List<ServerRow> rows, String action, boolean dryRun) {
        try {
            executeJob(job, rows, action, dryRun);
        } catch (Exception e) {
            emit(job, "error", "Job crashed: " + e.getMessage());
            log.error("Job {} crashed", job.getJobId(), e);
            job.markError();
        }
    }

    // ---------------------------------------------------------------------------
    //  Core execution logic
    // ---------------------------------------------------------------------------

    private void executeJob(JobState job, List<ServerRow> rows,
                            String action, boolean dryRun) throws Exception {

        // --- 1. Group rows by (cluster + host) ---
        Map<String, List<ServerRow>> serverGroups = new LinkedHashMap<>();
        for (ServerRow row : rows) {
            String key = row.getCluster() + "||" + row.getHost();
            serverGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<Map.Entry<String, List<ServerRow>>> rrGroups = serverGroups.entrySet().stream()
            .filter(e -> "round_robin".equals(e.getValue().get(0).getMode()))
            .collect(Collectors.toList());

        List<Map.Entry<String, List<ServerRow>>> batchGroups = serverGroups.entrySet().stream()
            .filter(e -> !"round_robin".equals(e.getValue().get(0).getMode()))
            .collect(Collectors.toList());

        int totalServers = serverGroups.size();
        emit(job, "info",
            String.format("Job %s  action=%s  servers=%d (%d round-robin, %d batch)  dry_run=%b",
                job.getJobId().substring(0, 8), action.toUpperCase(),
                totalServers, rrGroups.size(), batchGroups.size(), dryRun));

        // Build group lookup: key -> group name (for dependency scoping)
        Map<String, String> serverGroupMap = serverGroups.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(0).getGroup() != null
                     ? e.getValue().get(0).getGroup() : ""
            ));

        int[] done = {0};   // mutable counter (array trick for lambda capture)
        double defaultDelay = props.getRoundRobinDelay();

        // --- 2. Round-robin phase ---
        if (!rrGroups.isEmpty()) {
            emit(job, "info",
                String.format("-- PHASE 1: Round-Robin  (%d servers) --", rrGroups.size()));

            // Pre-flight: check which RR servers are already stopped (STOP only)
            Set<String> alreadyStopped = new ConcurrentHashSet<>();
            if ("stop".equals(action) && !dryRun) {
                emit(job, "info", "  Pre-flight: checking round-robin server statuses ...");
                List<CompletableFuture<Void>> pfFutures = new ArrayList<>();
                for (Map.Entry<String, List<ServerRow>> entry : rrGroups) {
                    String key = entry.getKey();
                    List<ServerRow> groupRows = entry.getValue();
                    pfFutures.add(CompletableFuture.runAsync(() -> {
                        ServerRow chk = groupRows.stream()
                            .filter(r -> r.getStatusCmd() != null && !r.getStatusCmd().isBlank())
                            .findFirst().orElse(null);
                        if (chk != null) {
                            String status = sshService.checkStatus(chk);
                            if ("stopped".equals(status)) {
                                alreadyStopped.add(key);
                                emit(job, "warn",
                                    "  [pre-flight] " + key.replace("||", "/") + " is already STOPPED");
                            }
                        }
                    }, executor));
                }
                CompletableFuture.allOf(pfFutures.toArray(new CompletableFuture[0]))
                                 .get(20, TimeUnit.SECONDS);

                if (!alreadyStopped.isEmpty()) {
                    emit(job, "warn",
                        "  " + alreadyStopped.size() + " server(s) already stopped — "
                        + "dependency check active for remaining servers");
                }
            }

            Set<String> weStopped = new ConcurrentHashSet<>();

            for (int idx = 0; idx < rrGroups.size(); idx++) {
                Map.Entry<String, List<ServerRow>> entry = rrGroups.get(idx);
                String key = entry.getKey();
                List<ServerRow> groupRows = entry.getValue();
                String sname = groupRows.get(0).getServerName();
                String cluster = groupRows.get(0).getCluster();

                // Dependency check: skip if another RR server in the same group is already stopped
                if ("stop".equals(action) && !dryRun) {
                    String myGroup = serverGroupMap.getOrDefault(key, "");
                    List<String> blocking = alreadyStopped.stream()
                        .filter(k -> !k.equals(key))
                        .filter(k -> !weStopped.contains(k))
                        .filter(k -> !myGroup.isEmpty()
                                     && myGroup.equals(serverGroupMap.getOrDefault(k, "")))
                        .map(k -> k.contains("||") ? k.split("\\|\\|")[1] : k)
                        .collect(Collectors.toList());

                    if (!blocking.isEmpty()) {
                        emit(job, "warn",
                            String.format("  SKIP [%d/%d] %s / %s — other RR server(s) already stopped: %s",
                                idx + 1, rrGroups.size(), cluster, sname, String.join(", ", blocking)));
                        done[0]++;
                        emitProgress(job, done[0], totalServers);
                        continue;
                    }
                }

                emit(job, "info",
                    String.format("  [%d/%d] %s / %s (%s)",
                        idx + 1, rrGroups.size(), cluster, sname, groupRows.get(0).getHost()));

                List<JobResult> res = processServerGroup(groupRows, action, dryRun, job);
                res.forEach(job::addResult);

                if ("stop".equals(action) && res.stream().allMatch(JobResult::isOk)) {
                    weStopped.add(key);
                }

                done[0]++;
                emitProgress(job, done[0], totalServers);

                // Delay between servers (not after the last one)
                if (idx < rrGroups.size() - 1 && !dryRun) {
                    Double serverDelay = groupRows.get(0).getRrDelay();
                    double effectiveDelay = serverDelay != null ? serverDelay : defaultDelay;
                    if (effectiveDelay > 0) {
                        emit(job, "warn",
                            String.format("  ~~~ Waiting %ds before next server ~~~",
                                (int) effectiveDelay));
                        Thread.sleep((long) (effectiveDelay * 1000));
                    }
                }
            }
        }

        // --- 3. Batch phase ---
        if (!batchGroups.isEmpty()) {
            emit(job, "info",
                String.format("-- PHASE 2: Batch  (%d servers, parallel) --", batchGroups.size()));

            Semaphore sem = new Semaphore(props.getBatchMaxWorkers());
            List<CompletableFuture<List<JobResult>>> futures = new ArrayList<>();

            for (Map.Entry<String, List<ServerRow>> entry : batchGroups) {
                List<ServerRow> groupRows = entry.getValue();
                String sname = groupRows.get(0).getServerName();
                String cluster = groupRows.get(0).getCluster();
                String host = groupRows.get(0).getHost();

                futures.add(CompletableFuture.supplyAsync(() -> {
                    sem.acquireUninterruptibly();
                    try {
                        emit(job, "info",
                            String.format("  Starting %s / %s (%s) ...", cluster, sname, host));
                        List<JobResult> res = processServerGroup(groupRows, action, dryRun, job);
                        synchronized (done) {
                            done[0]++;
                            emitProgress(job, done[0], totalServers);
                        }
                        return res;
                    } finally {
                        sem.release();
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<List<JobResult>> f : futures) {
                try { f.get().forEach(job::addResult); }
                catch (Exception ignored) {}
            }
        }

        // --- 4. Done ---
        List<JobResult> results = job.getResults();
        long errors = results.stream().filter(r -> !r.isOk()).count();
        String level = errors == 0 ? "ok" : "warn";
        emit(job, level,
            String.format("-- COMPLETE  processed=%d  ok=%d  errors=%d --",
                results.size(), results.size() - errors, errors));

        // Persist session
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        stateService.saveSession(new PatchSession(action, ts, results));

        job.markDone();
    }

    // ---------------------------------------------------------------------------
    //  Execute all services for one physical server
    // ---------------------------------------------------------------------------

    private List<JobResult> processServerGroup(List<ServerRow> groupRows, String action,
                                               boolean dryRun, JobState job) {
        // Stop order: ascending ID; start order: descending ID
        List<ServerRow> ordered = new ArrayList<>(groupRows);
        if ("stop".equals(action)) {
            ordered.sort(Comparator.comparingInt(ServerRow::getId));
        } else {
            ordered.sort(Comparator.comparingInt(ServerRow::getId).reversed());
        }

        List<JobResult> results = new ArrayList<>();
        for (ServerRow row : ordered) {
            results.add(executeService(row, action, dryRun, job));
        }
        return results;
    }

    private JobResult executeService(ServerRow row, String action, boolean dryRun, JobState job) {
        String host    = row.getHost();
        String service = row.getService();
        int rowId      = row.getId();
        String cmd     = "stop".equals(action) ? row.getStopCmd() : row.getStartCmd();
        String label   = String.format("[%d] %s / %s", rowId, host, service);

        JobResult result = new JobResult(rowId, host, service, action);

        if (dryRun) {
            emit(job, "info",
                String.format("%s  [DRY-RUN] Would %s: %s", label, action, cmd));
            result.setMessage("Dry-run - would run: " + cmd);
            return result;
        }

        // Pre-stop: skip if already stopped
        if ("stop".equals(action) && row.getStatusCmd() != null && !row.getStatusCmd().isBlank()) {
            try {
                String status = sshService.checkStatus(row);
                if ("stopped".equals(status)) {
                    emit(job, "warn",
                        String.format("%s  -- Already stopped, skipping", label));
                    result.setMessage("Already stopped - skipped");
                    return result;
                }
            } catch (Exception ignored) {
                // status check failure: proceed with stop anyway
            }
        }

        try {
            SshService.CommandResult res = sshService.run(host, cmd);
            if (res.success()) {
                emit(job, "ok",
                    String.format("%s  OK  %s", label,
                        "stop".equals(action) ? "STOPPED" : "STARTED"));
                result.setMessage("Service " + action + "ped successfully");
            } else {
                String errMsg = res.errorMessage();
                emit(job, "error",
                    String.format("%s  X  Failed to %s: %s", label, action, errMsg));
                result.setError("Command failed: " + errMsg);
            }
        } catch (Exception e) {
            emit(job, "error", String.format("%s  X  %s", label, e.getMessage()));
            result.setError(e.getMessage());
        }

        return result;
    }

    // ---------------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------------

    private void emit(JobState job, String level, String message) {
        job.emit(new LogEvent(level, message));
        log.info("[{}] {}", level.toUpperCase(), message);
    }

    private void emitProgress(JobState job, int done, int total) {
        int pct = total > 0 ? (int) ((double) done / total * 100) : 100;
        job.emit(new LogEvent("info", "  Progress: " + done + "/" + total)
                    .progress(pct, total));
    }

    // ---------------------------------------------------------------------------
    //  Thread-safe Set helper
    // ---------------------------------------------------------------------------

    private static class ConcurrentHashSet<E> extends AbstractSet<E> {
        private final ConcurrentHashMap<E, Boolean> map = new ConcurrentHashMap<>();

        @Override public boolean add(E e)       { return map.put(e, Boolean.TRUE) == null; }
        @Override public boolean contains(Object o) { return map.containsKey(o); }
        @Override public boolean remove(Object o)   { return map.remove(o) != null; }
        @Override public Iterator<E> iterator() { return map.keySet().iterator(); }
        @Override public int size()             { return map.size(); }
    }
}
