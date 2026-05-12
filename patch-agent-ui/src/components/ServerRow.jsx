function esc(s) {
  return String(s || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function StatusDot({ status }) {
  const cls = {
    running: 'status-running', stopped: 'status-stopped',
    error:   'status-error',   unknown: 'status-unknown',
  }[status] || 'status-unknown'

  const label = {
    running: 'Running', stopped: 'Stopped',
    error:   'Error',   unknown: 'Unknown',
  }[status] || 'Unknown'

  return (
    <span className="status-cell">
      <span className={`status-dot ${cls}`} title={label}></span>
      <span style={{ fontSize: '.72rem', color: '#6c757d' }}>{label}</span>
    </span>
  )
}

export function ServerRow({ row, selected, status, onToggle }) {
  const modeCls   = row.mode === 'round_robin' ? 'mode-rr'    : 'mode-batch'
  const modeLabel = row.mode === 'round_robin' ? 'Round-Robin' : 'Batch'

  return (
    <tr className={selected ? 'selected' : ''}>
      <td>
        <input
          type="checkbox"
          checked={selected}
          onChange={() => onToggle(row.id)}
        />
      </td>
      <td>{row.id}</td>
      <td>
        <span className="badge" style={{ background: '#e8eef8', color: '#1f3864', fontWeight: 600 }}>
          {row.cluster}
        </span>
      </td>
      <td><strong>{row.server_name}</strong></td>
      <td><code style={{ fontSize: '.78rem' }}>{row.host}</code></td>
      <td><span className="svc-chip">{row.service}</span></td>
      <td><StatusDot status={status} /></td>
      <td><span className={`mode-badge ${modeCls}`}>{modeLabel}</span></td>
      <td style={{ color: '#6c757d', fontSize: '.78rem' }}>{row.notes || ''}</td>
    </tr>
  )
}
