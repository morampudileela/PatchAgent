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

## Project Structure

```
patch-agent-ui/
├── index.html                   Vite entry point
├── package.json                 Dependencies and scripts
├── vite.config.js               Dev server + /api proxy config
└── src/
    ├── main.jsx                 React root — mounts <App />, imports Bootstrap
    ├── App.jsx                  Root component — all shared state lives here
    ├── styles/
    │   └── custom.css           CSS variables, table styles, status dots, log panel
    ├── api/
    │   └── client.js            Fetch wrappers for all /api/* endpoints
    ├── hooks/
    │   ├── useServers.js        Fetches server inventory from /api/servers
    │   ├── useJobStream.js      Manages job start + SSE EventSource lifecycle
    │   └── useAutoRefresh.js    setInterval wrapper with start/stop control
    └── components/
        ├── TopBar.jsx           Header bar with title, version badge, Reload button
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
| `allRows` | `ServerRow[]` | Full inventory from `/api/servers` |
| `clusters` | `string[]` | Distinct cluster names |
| `activeCluster` | `string` | Selected cluster filter (`"All"` or cluster name) |
| `selectedIds` | `Set<number>` | Row IDs currently selected for job execution |
| `rowStatuses` | `{ [id]: string }` | Live status cache — `"running"` / `"stopped"` / `"error"` / `"unknown"` |
| `sessions` | `PatchSession[]` | Recent job history from `/api/history` |

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

**UI loads but API calls fail (`net::ERR_CONNECTION_REFUSED`)**  
The Spring Boot backend is not running. Start it first:
```bash
cd ../patch-agent-api
mvn spring-boot:run
```

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
