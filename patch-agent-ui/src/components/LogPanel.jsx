import { useEffect, useRef } from 'react'

export function LogPanel({ logLines, progress, onClear }) {
  const panelRef = useRef(null)

  // Auto-scroll to bottom on new lines
  useEffect(() => {
    if (panelRef.current) {
      panelRef.current.scrollTop = panelRef.current.scrollHeight
    }
  }, [logLines])

  return (
    <>
      {/* Progress card */}
      <div className="card shadow-sm mb-3">
        <div className="card-body py-2 px-3">
          <div className="d-flex justify-content-between align-items-center mb-1">
            <span style={{ fontSize: '.82rem', fontWeight: 600 }}>
              {progress.label || 'Idle'}
            </span>
            <span style={{ fontSize: '.78rem', color: '#6c757d' }}>
              {progress.pct > 0 ? `${progress.pct}%` : '--'}
            </span>
          </div>
          <div className="progress">
            <div
              className="progress-bar bg-success"
              style={{ width: `${progress.pct || 0}%` }}
            ></div>
          </div>
        </div>
      </div>

      {/* Log panel */}
      <div className="card shadow-sm mb-3">
        <div className="card-header d-flex justify-content-between align-items-center py-2">
          <span style={{ fontWeight: 600, fontSize: '.88rem' }}>
            <i className="bi bi-terminal"></i> Execution Log
          </span>
          <button className="btn btn-sm btn-outline-secondary py-0" onClick={onClear}>
            Clear
          </button>
        </div>
        <div className="card-body p-0">
          <div ref={panelRef} className="log-panel">
            {logLines.map(line => (
              <div key={line.key}>
                {line.ts && <span className="log-ts">{line.ts}</span>}
                <span className={`log-${line.level}`}>{line.message}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}
