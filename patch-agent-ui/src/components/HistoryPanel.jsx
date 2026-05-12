export function HistoryPanel({ sessions, onRefresh }) {
  return (
    <div className="card shadow-sm">
      <div className="card-header py-2 d-flex justify-content-between align-items-center">
        <span style={{ fontWeight: 600, fontSize: '.88rem' }}>
          <i className="bi bi-clock-history"></i> Recent Runs
        </span>
        <button className="btn btn-sm btn-outline-secondary py-0" onClick={onRefresh}>
          Refresh
        </button>
      </div>
      <div className="card-body py-2 px-3">
        {!sessions.length ? (
          <span className="text-muted" style={{ fontSize: '.8rem' }}>No history yet</span>
        ) : (
          sessions.slice(0, 8).map((s, i) => {
            const sum      = s.summary || {}
            const ts       = (s.started_at || '').replace('T', ' ').substring(0, 16)
            const act      = (s.action || '').toUpperCase()
            const actColor = s.action === 'stop' ? '#dc3545' : '#198754'
            const errs     = sum.errors || 0
            return (
              <div key={i} className="hist-item">
                <span style={{ color: actColor, fontWeight: 700 }}>{act}</span>
                &nbsp;·&nbsp; {sum.total || 0} rows
                &nbsp;·&nbsp; <span style={{ color: '#198754' }}>{sum.ok || 0} ok</span>
                {errs > 0 && (
                  <>&nbsp;·&nbsp;<span style={{ color: '#dc3545' }}>{errs} err</span></>
                )}
                <span className="float-end text-muted">{ts}</span>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
