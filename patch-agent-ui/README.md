# Patch Agent UI — React + Vite

React 18 frontend for the Linux Server Patching Agent.  
In production it is embedded inside the Spring Boot JAR and served automatically.  
In development it runs as a standalone Vite dev server with hot reload.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Node | 20+     | `node -v` |
| npm  | 10+     | `npm -v` |

> For **production builds** you do not need to run anything here —  
> `mvn clean package` in `../patch-agent-api/` builds and embeds the UI automatically.  
> This folder is only used when developing the frontend independently.

---

## Option A — Dev server (recommended for UI development)

Starts a Vite dev server with hot reload. All `/api/*` requests are proxied
to the Spring Boot backend running on port 5000.

```bash
# From the SshTool/ folder:
cd patch-agent-ui

# Install dependencies (first time only, or after package.json changes)
npm install

# Start the dev server
npm run dev
```

Open **http://localhost:5173**

> The Spring Boot API must be running on port 5000 for the UI to work.  
> Start it first: `cd ../patch-agent-api && mvn spring-boot:run`

Any change you save to a `.jsx`, `.js`, or `.css` file inside `src/` instantly
reflects in the browser without a page reload.

---

## Option B — Production build (standalone)

Builds a static bundle in `dist/`. Normally you don't run this manually —
`mvn package` in the API project does it for you — but it's useful for testing
the built output locally.

```bash
cd patch-agent-ui

npm install        # if not already done
npm run build      # outputs to dist/
npm run preview    # serves dist/ locally on http://localhost:4173
```

---

## Authentication flow

The UI implements a full session-based login flow backed by Active Directory:

1. On load, `App.jsx` calls `GET /api/auth/me` to check for an existing session.
2. If no session exists (or a `401` is received on any API call), the app renders `LoginPage` instead of the main dashboard.
3. `LoginPage` presents an **Environment** dropdown (`Non-Prod` / `Prod`), username, and password fields.
4. On successful login the server returns `{ username, environment }`. The app stores both in state and renders the main dashboard.
5. The active environment is shown as a persistent colour-coded badge in `TopBar` (green = Non-Prod, red = Prod).
6. The **Sign out** button calls `POST /api/auth/logout`, which destroys the server-side session, then returns to the login page.

> The browser never holds AD credentials. Only a `JSESSIONID` session cookie is stored, which the server invalidates after 8 hours.

---

## Project Structure

```
patch-agent-ui/
├── index.html                   Vite entry point
├── package.json                 Dependencies and scripts
├── vite.config.js               Dev server + /api proxy config
└── src/
    ├── main.jsx                 React root — mounts <App />, imports Bootstrap
    ├── App.jsx                  Root component — auth state + all shared state
    ├── styles/
    │   └── custom.css           CSS variables, table styles, status dots, log panel
    ├── api/
    │   └── client.js            Fetch wrappers for all /api/* endpoints + auth
    ├── hooks/
    │   ├── useServers.js        Fetches server inventory from /api/servers
    │   ├── useJobStream.js      Manages job start + SSE EventSource lifecycle
    │   └── useAutoRefresh.js    setInterval wrapper with start/stop control
    └── components/
        ├── LoginPage.jsx        Full-page AD login form with environment dropdown
        ├── TopBar.jsx           Header — title, environment badge, username, sign-out
        ├── ClusterFilter.jsx    Cluster dropdown + visible row count
        ├── ServerTable.jsx      <table> with select-all checkbox, sticky header
        ├── ServerRow.jsx        Single <tr> — checkbox, status dot, mode badge
        ├── ActionBar.jsx        Range input, Apply, Check Status, Stop/Start buttons
        ├── LogPanel.jsx         Dark terminal log panel + progress bar
        └── HistoryPanel.jsx     Recent runs list
```

---

## State overview

All shared state lives in `App.jsx` and flows down as props:

| State | Type | Description |
|-------|------|-------------|
| `authUser` | `null \| false \| { username, environment }` | `null` = probing session, `false` = not logged in, object = authenticated |
| `allRows` | `ServerRow[]` | Inventory from `/api/servers` — pre-filtered to session environment |
| `clusters` | `string[]` | Distinct cluster names for the active environment |
| `activeCluster` | `string` | Selected cluster filter (`"All"` or a cluster name) |
| `selectedIds` | `Set<number>` | Row IDs currently selected for job execution |
| `rowStatuses` | `{ [id]: string }` | Live status cache — `"running"` / `"stopped"` / `"error"` / `"unknown"` |
| `sessions` | `PatchSession[]` | Recent job history from `/api/history` |

---

## API client (`src/api/client.js`)

All fetch calls include `credentials: 'same-origin'` and an `X-Requested-With` header so the session cookie is sent automatically. Any `401` response on an `/api/*` endpoint dispatches a custom `auth:expired` window event, which `App.jsx` listens for to redirect back to the login page without requiring each call site to handle auth.

Auth calls:

```js
api.login(username, password, environment)  // POST /api/auth/login
api.logout()                                // POST /api/auth/logout
api.me()                                    // GET  /api/auth/me
```

---

## Proxy configuration

In dev mode, `vite.config.js` proxies all `/api/*` requests to the backend:

```js
proxy: {
  '/api': {
    target: 'http://localhost:5000',
    changeOrigin: true,
  }
}
```

If your Spring Boot API runs on a different port, update the `target` here.

---

## Adding a new component

1. Create `src/components/MyComponent.jsx`
2. Export a named function: `export function MyComponent({ ...props }) { ... }`
3. Import and use it in `App.jsx` or another component

All components use plain Bootstrap 5 classes for layout and styling.
Custom classes (`.status-dot`, `.mode-badge`, `.log-panel`, etc.) are in `src/styles/custom.css`.

---

## Troubleshooting

**`npm install` fails**  
Make sure you are running Node 20+. Check with `node -v`.

**Login page never appears / blank screen on load**  
Open the browser console. If `GET /api/auth/me` returns a network error, the Spring Boot backend is not running. Start it first:
```bash
cd ../patch-agent-api
mvn spring-boot:run
```

**UI loads but API calls fail (`net::ERR_CONNECTION_REFUSED`)**  
Same cause — the Spring Boot backend is not running on port 5000.

**Login fails with "Unable to contact authentication server"**  
The backend cannot reach the LDAP server configured in `application.yml`. Check network connectivity to the AD domain controller.

**Changes not reflecting in the browser**  
Vite hot-reload should update automatically. If it doesn't, try a hard refresh
(`Cmd+Shift+R` on Mac) or restart the dev server.

**Port 5173 already in use**  
Change the port in `vite.config.js`:
```js
server: {
  port: 3000,   // or any free port
  ...
}
```
