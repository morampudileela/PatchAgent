# Patch Agent API — Spring Boot

Spring Boot 3 REST backend for the Linux Server Patching Agent.  
Serves both the API (`/api/*`) and the built React UI from a single fat JAR.

---

## Prerequisites

| Tool  | Version | Check |
|-------|---------|-------|
| Java  | 17+     | `java -version` |
| Maven | 3.9+    | `mvn -version` |

> Node / npm are **not** required on your machine for production builds —  
> the `frontend-maven-plugin` downloads them automatically during `mvn package`.  
> They are only needed if you run the React dev server separately (see Option B below).

---

## Configuration

Before running, edit **`src/main/resources/application.yml`**:

```yaml
ssh:
  username: patchuser          # Linux user with sudo access on target servers
  password: "yourpassword"     # Leave blank if using a private key
  private-key-path: ""         # e.g. ~/.ssh/patch_key  (blank = use password)
  port: 22
  connect-timeout: 15          # seconds
  command-timeout: 30          # seconds

patching:
  round-robin-delay: 5.0       # default delay (seconds) between round-robin servers
  batch-max-workers: 10        # max parallel SSH connections
  excel-path: "../servers_template.xlsx"
  state-path:  "../patch_state.json"
```

**Path note:** `excel-path` and `state-path` are resolved relative to the directory
you run the JAR from. The defaults (`../`) assume you `cd patch-agent-api` first,
so `..` points to the parent `SshTool/` folder where those files already live.

---

## Option A — Production (single fat JAR)

This is the normal way to run. One command builds the React UI, embeds it, and
packages everything into a single self-contained JAR.

```bash
# From the SshTool/ folder:
cd patch-agent-api

# Build (downloads Node, builds React, packages JAR — takes ~2 min first time)
mvn clean package -DskipTests

# Run
java -jar target/patch-agent-api-2.2.0.jar
```

Open **http://localhost:5000**

What `mvn package` does automatically:
1. Downloads Node 20 + npm into a local `.node/` cache (first run only)
2. Runs `npm install` in `../patch-agent-ui/`
3. Runs `npm run build` — outputs to `../patch-agent-ui/dist/`
4. Copies `dist/` into `target/classes/static/`
5. Packages everything as a fat JAR via `spring-boot-maven-plugin`

To skip the React build (backend-only iteration):
```bash
mvn clean package -DskipTests -Dskip.npm
```

---

## Option B — Development (hot reload on both sides)

Run the backend and frontend as separate processes. Changes to Java files
restart Spring Boot; changes to React files hot-reload in the browser instantly.

**Terminal 1 — Spring Boot API:**
```bash
cd patch-agent-api
mvn spring-boot:run
# API listening on http://localhost:5000
```

**Terminal 2 — React dev server (see patch-agent-ui/README.md):**
```bash
cd patch-agent-ui
npm install       # first time only
npm run dev
# UI at http://localhost:5173  (proxies /api/* → localhost:5000)
```

Use the React dev server URL (**5173**), not 5000, when running in dev mode.

---

## Project Structure

```
patch-agent-api/
├── pom.xml                          Maven build file + frontend plugin
└── src/main/
    ├── java/com/patchagent/
    │   ├── PatchAgentApplication.java   Entry point
    │   ├── config/
    │   │   ├── SshProperties.java       SSH settings (@ConfigurationProperties)
    │   │   ├── PatchingProperties.java  Patching settings
    │   │   ├── AsyncConfig.java         ThreadPoolTaskExecutor (50 threads)
    │   │   └── WebConfig.java           CORS for dev mode
    │   ├── model/
    │   │   ├── ServerRow.java           One Excel row
    │   │   ├── JobState.java            In-memory job + event queue
    │   │   ├── LogEvent.java            SSE event payload
    │   │   ├── JobResult.java           Single service stop/start result
    │   │   └── PatchSession.java        Persisted history entry
    │   ├── service/
    │   │   ├── ServerInventoryService.java  Excel → ServerRow list (Apache POI)
    │   │   ├── SshService.java              SSH connect + exec (JSch)
    │   │   ├── JobExecutorService.java      Round-robin + batch execution engine
    │   │   └── StateService.java            Read/write patch_state.json
    │   ├── controller/
    │   │   ├── ServerController.java    GET /api/servers, POST /api/status, POST /api/resolve
    │   │   ├── JobController.java       POST /api/job/start, GET /api/job/stream/{id}, GET /api/job/{id}
    │   │   └── HistoryController.java   GET /api/history
    │   └── util/
    │       └── RowSelectionParser.java  Parse "1,3,5-10" → [1,3,5,6,7,8,9,10]
    └── resources/
        ├── application.yml
        └── static/                      React build is copied here during mvn package
```

---

## API Reference

All endpoints return JSON. Field names use `snake_case`.

| Method | Path | Body | Response |
|--------|------|------|----------|
| `GET`  | `/api/servers` | — | `{ rows: [...], clusters: [...] }` |
| `POST` | `/api/status` | `{ row_ids: [1,2] }` | `{ statuses: { "1": "running" } }` |
| `POST` | `/api/job/start` | `{ selection, action, dry_run }` | `{ job_id, server_count }` |
| `GET`  | `/api/job/stream/{id}` | — | SSE text/event-stream |
| `GET`  | `/api/job/{id}` | — | `{ status, action, results }` |
| `GET`  | `/api/history` | — | `{ sessions: [...] }` |
| `POST` | `/api/resolve` | `{ selection }` | `{ ids, count }` |

**`/api/job/start` body fields:**
- `selection` — row IDs string e.g. `"1,3,5-10"` or `"*"` for all
- `action` — `"stop"` or `"start"`
- `dry_run` — `true` to simulate without SSH-ing

**SSE stream events:**
- Regular events arrive as `data: {...}` with fields: `ts`, `level` (`ok|info|warn|error`), `message`, optional `progress`, `total`, `done`
- Stream ends with a named `done` event: `event: done\ndata: {}`
- Heartbeat every 25s of inactivity: `event: heartbeat\ndata: {}`

---

## Troubleshooting

**`Could not find artifact com.github.mwiede:jsch`**  
Maven needs internet access on first build to download dependencies from Maven Central.

**`Excel not found`**  
Check `patching.excel-path` in `application.yml`. Run the JAR from inside `patch-agent-api/`
so the default `../servers_template.xlsx` resolves correctly.

**Port 5000 already in use**  
Change `server.port` in `application.yml`, or kill the process using the port:
```bash
lsof -ti:5000 | xargs kill
```

**SSH `Auth fail`**  
Verify `ssh.username` + `ssh.password` (or `ssh.private-key-path`) in `application.yml`
match the credentials on your target servers.
