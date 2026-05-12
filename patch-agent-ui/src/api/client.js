/**
 * API client — thin fetch wrappers for all /api/* endpoints.
 * All functions return parsed JSON or throw on error.
 */

const BASE = ''  // relative URLs; Vite proxy handles /api/* in dev

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify(body),
  })
  return res.json()
}

async function get(path) {
  const res = await fetch(BASE + path)
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
}
