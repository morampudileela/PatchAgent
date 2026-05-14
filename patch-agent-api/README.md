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

### SSH

```yaml
ssh:
  username: patchuser          # fallback — overridden at runtime by AD login credentials
  password: ""                 # leave blank if using a private key
  private-key-path: ""         # e.g. ~/.ssh/patch_key
  port: 22
  connect-timeout: 15          # seconds
  command-timeout: 30          # seconds
```

### Patching

```yaml
patching:
  round-robin-delay: 5.0       # default delay (seconds) between round-robin servers
  batch-max-workers: 10        # max parallel SSH connections
  excel-path: "../servers_template.xlsx"   # relative to patch-agent-api/ working dir
  state-path:  "../patch_state.json"       # relative to patch-agent-api/ working dir
```

Both paths resolve to the `SshTool/` root when the JAR is run from inside `patch-agent-api/`.  
The Excel file is shared with the Python app — edit one file, both apps pick up the changes.

### LDAP / Active Directory

```yaml
ldap:
  environment: nonprod         # "nonprod" | "prod"  (overridden by user's login choice)

  nonprod:
    server:          "ldap://nonprod-dc01.company.com:389"
    domain:          "NONPROD"
    base-dn:         "DC=nonprod,DC=company,DC=com"
    required-group:  "CN=patch-admins,OU=Groups,DC=nonprod,DC=company,DC=com"
    recursive-group: true      # resolves nested AD group membership

  prod:
    server:          "ldap://prod-dc01.company.com:389"
    domain:          "PROD"
    base-dn:         "DC=prod,DC=company,DC=com"
    required-group:  "CN=patch-admins,OU=Groups,DC=prod,DC=company,DC=com"
    recursive-group: true
```

Replace the placeholder URLs, domains, and group DNs with your actual AD values.  
Set `required-group` to `""` to allow any valid AD user to log in.

---

## Option A — Production (single fat JAR)

One command builds the React UI, embeds it, and packages everything into a single self-contained JAR.

```bash
# From the SshTool/ folder:
cd patch-agent-api

# Build (downloads Node, builds React, packages JAR — ~2 min first time)
mvn clean package -DskipTests

# Run
java -jar target/patch-agent-api-2.3.0.jar
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

Run the backend and frontend as separate processes. Java changes restart Spring Boot; React changes hot-reload in the browser instantly.

**Terminal 1 — Spring Boot API:**
```bash
cd patch-agent-api
mvn spring-boot:run
# API listening on http://localhost:5000
```

**Terminal 2 — React dev server:**
```bash
cd patch-agent-ui
npm install       # first time only
npm run dev
# UI at http://localhost:5173  (proxies /api/* → localhost:5000)
```

Use the Vite dev server URL (**5173**), not 5000, when running in dev mode.

---

## Authentication

The app uses session-based AD authentication. On the login screen the user selects an environment (Non-Prod or Prod) and enters their AD credentials. The backend performs an NTLM bind against the configured LDAP server and checks group membership recursively. On success a server-side `HttpSession` is created — only a `JSESSIONID` cookie is sent to the browser.

Sessions expire after **8 hours**. All `/api/*` endpoints (except `/api/auth/login` and `/api/auth/logout`) require a valid session; unauthenticated requests receive HTTP 401.

SSH connections for jobs and status checks use the credentials from the user's session, not the `ssh.username` / `ssh.password` config values.

---

## Project Structure

```
patch-agent-api/
├── pom.xml
└── src/main/
    ├── java/com/patchagent/
    │   ├── PatchAgentApplication.java
    │   ├── config/
    │   │   ├── AsyncConfig.java          ThreadPoolTaskExecutor (50 threads)
    │   │   ├── LdapProperties.java       @ConfigurationProperties for ldap.*
    │   │   ├── PatchingProperties.java   @ConfigurationProperties for patching.*
    │   │   ├── SecurityConfig.java       Spring Security filter chain + CORS
    │   │   ├── SshProperties.java        @ConfigurationProperties for ssh.*
    │   │   └── WebConfig.java            MVC CORS (dev mode)
    │   ├── controller/
    │   │   ├── AuthController.java       POST /api/auth/login, /logout, GET /me
    │   │   ├── HistoryController.java    GET /api/history
    │   │   ├── JobController.java        POST /api/job/start, GET /api/job/stream/{id}
    │   │   └── ServerController.java     GET /api/servers, POST /api/status, /resolve
    │   ├── model/
    │   │   ├── JobResult.java            Single service stop/start result
    │   │   ├── JobState.java             In-memory job + BlockingQueue event queue
    │   │   ├── LogEvent.java             SSE event payload
    │   │   ├── PatchSession.java         Persisted history entry
    │   │   ├── ServerRow.java            One Excel row (includes environment field)
    │   │   └── SessionCredentials.java   AD username + password stored in HttpSession
    │   ├── service/
    │   │   ├── JobExecutorService.java   Round-robin + batch execution engine
    │   │   ├── LdapAuthService.java      JNDI NTLM bind + recursive group check
    │   │   ├── ServerInventoryService.java  Excel → ServerRow list (Apache POI)
    │   │   ├── SshService.java           SSH connect + exec (JSch mwiede fork)
    │   │   └── StateService.java         Read/write patch_state.json
    │   └── util/
    │       └── RowSelectionParser.java   Parse "1,3,5-10" → [1,3,5,6,7,8,9,10]
    └── resources/
        ├── application.yml
        └── static/                       React build copied here during mvn package
```

---

## API Reference

All endpoints return JSON. Field names use `snake_case`.

### Auth

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/auth/login` | `{ username, password, environment }` | `{ username, environment }` |
| `POST` | `/api/auth/logout` | — | `{ message }` |
| `GET`  | `/api/auth/me` | — | `{ username, environment }` |

### Servers

| Method | Path | Body | Response |
|--------|------|------|----------|
| `GET`  | `/api/servers` | — | `{ rows: [...], clusters: [...] }` — filtered to session environment |
| `POST` | `/api/status` | `{ row_ids: [1,2] }` | `{ statuses: { "1": "running" } }` |
| `POST` | `/api/resolve` | `{ selection }` | `{ ids, count }` |

### Jobs

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/job/start` | `{ selection, action, dry_run }` | `{ job_id, server_count }` |
| `GET`  | `/api/job/stream/{id}` | — | SSE `text/event-stream` |
| `GET`  | `/api/job/{id}` | — | `{ status, action, results }` |
| `GET`  | `/api/history` | — | `{ sessions: [...] }` |

**`/api/job/start` body fields:**
- `selection` — row IDs e.g. `"1,3,5-10"` or `"*"` for all
- `action` — `"stop"` or `"start"`
- `dry_run` — `true` to simulate without SSH-ing

**SSE stream events (`/api/job/stream/{id}`):**
- Data events: `data: { ts, level, message, progress?, total?, done? }`
- `level` values: `ok` `info` `warn` `error`
- End of stream: `event: done` / `data: {}`
- Keepalive every 25 s of inactivity: `event: heartbeat` / `data: {}`

---

## Troubleshooting

**`Could not find artifact com.github.mwiede:jsch`**  
Maven needs internet access on the first build to fetch dependencies from Maven Central.

**`Excel not found`**  
`servers_template.xlsx` is expected at `../servers_template.xlsx` relative to where the JAR is run from. Always run from inside `patch-agent-api/` so `..` resolves to `SshTool/`.

**Login returns 401 "Invalid username or password"**  
Verify `ldap.nonprod.server` (or `prod`) URL, `domain`, and `base-dn` in `application.yml`. Confirm the DC is reachable: `ldapsearch -H ldap://your-dc:389`.

**Login returns 401 "not a member of the required group"**  
The user authenticated but is not in `required-group`. Add them to the AD group or clear `required-group` to allow all AD users.

**Port 5000 already in use**  
Change `server.port` in `application.yml`, or kill the process:
```bash
lsof -ti:5000 | xargs kill
```

**SSH `Auth fail`**  
At runtime SSH uses the logged-in user's AD credentials. Ensure that user has SSH access to the target servers. The `ssh.username` config value is only used as a fallback when no session is present.
