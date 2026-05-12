import { useRef, useEffect } from 'react'
import { ServerRow } from './ServerRow'

export function ServerTable({ rows, selectedIds, rowStatuses, onToggle, onToggleAll }) {
  const allChecked  = rows.length > 0 && rows.every(r => selectedIds.has(r.id))
  const someChecked = rows.some(r => selectedIds.has(r.id)) && !allChecked
  const selectAllRef = useRef(null)

  // Indeterminate state for the select-all checkbox
  useEffect(() => {
    if (selectAllRef.current) {
      selectAllRef.current.indeterminate = someChecked
    }
  }, [someChecked])

  if (!rows.length) {
    return (
      <div className="server-table-wrap" style={{ maxHeight: 420, overflowY: 'auto' }}>
        <table className="srv-table">
          <thead><tr>
            <th></th><th>ID</th><th>Cluster</th><th>Server</th>
            <th>Host / IP</th><th>Service</th><th>Status</th><th>Mode</th><th>Notes</th>
          </tr></thead>
          <tbody>
            <tr><td colSpan="9" className="text-center text-muted py-4">No rows</td></tr>
          </tbody>
        </table>
      </div>
    )
  }

  return (
    <div className="server-table-wrap" style={{ maxHeight: 420, overflowY: 'auto' }}>
      <table className="srv-table">
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                ref={selectAllRef}
                checked={allChecked}
                onChange={e => onToggleAll(e.target.checked, rows)}
                title="Select all visible"
              />
            </th>
            <th>ID</th>
            <th>Cluster</th>
            <th>Server</th>
            <th>Host / IP</th>
            <th>Service</th>
            <th>Status</th>
            <th>Mode</th>
            <th>Notes</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <ServerRow
              key={row.id}
              row={row}
              selected={selectedIds.has(row.id)}
              status={rowStatuses[row.id] || 'unknown'}
              onToggle={onToggle}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
