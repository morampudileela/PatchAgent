import { useState, useRef } from 'react'

function parseRangeLocal(sel, allIds) {
  const validSet = new Set(allIds)
  const result   = new Set()
  sel.split(',').forEach(part => {
    part = part.trim()
    if (part.includes('-')) {
      const [lo, hi] = part.split('-').map(s => parseInt(s.trim()))
      for (let i = lo; i <= hi; i++) if (validSet.has(i)) result.add(i)
    } else {
      const n = parseInt(part)
      if (!isNaN(n) && validSet.has(n)) result.add(n)
    }
  })
  return [...result]
}

export function ActionBar({
  allRows, selectedIds, onSelectionChange,
  onStartJob, onCheckStatus,
  lastChecked, autoRefresh, onAutoRefreshChange,
}) {
  const [rangeInput, setRangeInput] = useState('')
  const [dryRun,     setDryRun]     = useState(false)
  const [checking,   setChecking]   = useState(false)

  const selCount = selectedIds.size

  function applyRange() {
    const raw = rangeInput.trim()
    if (raw === '') {
      onSelectionChange(new Set())
    } else if (raw === '*') {
      onSelectionChange(new Set(allRows.map(r => r.id)))
    } else {
      const allIds   = allRows.map(r => r.id)
      const resolved = parseRangeLocal(raw, allIds)
      onSelectionChange(new Set(resolved))
    }
  }

  async function handleCheckStatus() {
    setChecking(true)
    await onCheckStatus()
    setChecking(false)
  }

  return (
    <div className="p-3">
      <div className="action-bar">
        <label>Row selection:</label>
        <input
          type="text"
          className="range-input"
          placeholder="e.g.  1,3,5-10  or  *"
          value={rangeInput}
          onChange={e => setRangeInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && applyRange()}
          title="Comma-separated IDs or ranges. Blank = deselect all. * = select all."
        />
        <button className="btn btn-sm btn-outline-secondary" onClick={applyRange}>
          <i className="bi bi-filter"></i> Apply
        </button>
        <span className="sel-count">
          {selCount} row{selCount !== 1 ? 's' : ''} selected
        </span>

        <button
          className="btn btn-sm btn-outline-info"
          onClick={handleCheckStatus}
          disabled={checking}
          title="Check live status for active cluster via SSH"
        >
          {checking
            ? <><i className="bi bi-hourglass-split"></i> Checking...</>
            : <><i className="bi bi-activity"></i> Check Status</>
          }
        </button>

        <label className="d-flex align-items-center gap-1"
               title="Auto-refresh status every 60s for active cluster only">
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={e => onAutoRefreshChange(e.target.checked)}
          />
          <span style={{ fontSize: '.82rem' }}>Auto (60s)</span>
        </label>

        <div className="ms-auto d-flex gap-2 flex-wrap">
          <label className="d-flex align-items-center gap-1 me-1"
                 title="Simulates actions without SSH-ing">
            <input type="checkbox" checked={dryRun} onChange={e => setDryRun(e.target.checked)} />
            <span style={{ fontSize: '.82rem' }}>Dry-run</span>
          </label>
          <button className="btn btn-sm btn-stop px-3"
                  onClick={() => onStartJob(selectedIds, 'stop', dryRun)}>
            <i className="bi bi-stop-circle"></i> STOP Services
          </button>
          <button className="btn btn-sm btn-start px-3"
                  onClick={() => onStartJob(selectedIds, 'start', dryRun)}>
            <i className="bi bi-play-circle"></i> START Services
          </button>
        </div>
      </div>

      <div className="mt-2 status-footer">
        <span className="status-dot status-running"></span> Running &nbsp;
        <span className="status-dot status-stopped"></span> Stopped &nbsp;
        <span className="status-dot status-error"></span> Error &nbsp;
        <span className="status-dot status-unknown"></span> Unknown
        &nbsp;—&nbsp;
        <span>{lastChecked || 'Not checked yet'}</span>
        &nbsp;—&nbsp; Add <em>Status Check Command</em> column to Excel to enable
      </div>
    </div>
  )
}
