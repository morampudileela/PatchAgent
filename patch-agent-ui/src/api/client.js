/**
 * API client — thin fetch wrappers for all /api/* endpoints.
 * All functions return parsed JSON or throw on error.
 *
 * 401 handling: any /api/* response with status 401 dispatches a custom
 * "auth:expired" event on window so App.jsx can redirect to the login page
 * without coupling every call site to auth logic.
 */

const BASE = ''  // relative URLs; Vite proxy handles /api/* in dev

function dispatch401() {
  window.dispatchEvent(new CustomEvent('auth:expired'))
}

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method:      'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type':   'application/json',
      'X-Requested-With': 'XMLHttpRequest',   // prevents CSRF on simple CORS requests
    },
    body: JSON.stringify(body),
  })
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    dispatch401()
    return { error: 'Unauthorized', login_required: true }
  }
  return res.json()
}

async function get(path) {
  const res = await fetch(BASE + path, {
    credentials: 'same-origin',
    headers: { 'X-Requested-With': 'XMLHttpRequest' },
  })
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    dispatch401()
    return { error: 'Unauthorized', login_required: true }
  }
  return res.json()
}

export const api = {
  /** GET /api/servers — returns { rows, clusters } */
  getServers: () => get('/api/servers'),

  /** POST /api/status — returns { statuses: { "1": "running", ... } } */
  checkStatus: (rowIds) => post('/api/status', { row_ids: rowIds }),

  /** POST /api/job/start — returns { job_id, server_count } */
  startJob: (selection, action, dryRun) =>
    post('/api/job/start', { selection, action, dry_run: dryRun }),

  /** GET /api/job/:id — returns { status, action, results } */
  getJob: (jobId) => get(`/api/job/${jobId}`),

  /** GET /api/history — returns { sessions: [...] } */
  getHistory: () => get('/api/history'),

  /** POST /api/resolve — returns { ids, count } */
  resolve: (selection) => post('/api/resolve', { selection }),

  // ── Auth ──────────────────────────────────────────────────────────

  /** POST /api/auth/login — returns { username, environment } or { error } */
  login: (username, password, environment = 'nonprod') =>
    post('/api/auth/login', { username, password, environment }),

  /** POST /api/auth/logout — invalidates server session */
  logout: () => post('/api/auth/logout', {}),

  /** GET /api/auth/me — returns { username } or 401 */
  me: () => get('/api/auth/me'),
}
