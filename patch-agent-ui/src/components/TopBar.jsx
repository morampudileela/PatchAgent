export function TopBar({ rowCount, onReload, username, environment, onLogout }) {
  const isProd = environment === 'prod'

  // Environment badge colours
  const envStyle = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.3rem',
    fontSize: '0.75rem',
    fontWeight: 700,
    letterSpacing: '0.04em',
    textTransform: 'uppercase',
    padding: '0.18rem 0.55rem',
    borderRadius: '999px',
    marginLeft: '0.6rem',
    background: isProd ? 'rgba(239,68,68,0.18)' : 'rgba(34,197,94,0.18)',
    color:      isProd ? '#fca5a5'               : '#86efac',
    border:     `1px solid ${isProd ? 'rgba(239,68,68,0.4)' : 'rgba(34,197,94,0.4)'}`,
  }

  const dotStyle = {
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    background: isProd ? '#ef4444' : '#22c55e',
    flexShrink: 0,
  }

  return (
    <div className="topbar">
      <i className="bi bi-server fs-5"></i>
      <h1>Patch Agent</h1>
      <span className="badge-version">v2.3</span>

      {/* Environment badge — always visible when logged in */}
      {environment && (
        <span style={envStyle}>
          <span style={dotStyle} />
          {isProd ? 'Prod' : 'Non-Prod'}
        </span>
      )}

      <span className="ms-auto" style={{ fontSize: '.8rem', opacity: .7 }}>
        {rowCount > 0 ? `${rowCount} service rows loaded` : ''}
      </span>

      <button className="btn btn-sm btn-outline-light ms-2" onClick={onReload}>
        <i className="bi bi-arrow-clockwise"></i> Reload
      </button>

      {username && (
        <>
          <span style={{
            fontSize: '.82rem',
            opacity: .85,
            marginLeft: '0.75rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.3rem',
          }}>
            <i className="bi bi-person-circle"></i>
            {username}
          </span>
          <button
            className="btn btn-sm btn-outline-light ms-2"
            onClick={onLogout}
            title="Sign out"
          >
            <i className="bi bi-box-arrow-right"></i> Sign out
          </button>
        </>
      )}
    </div>
  )
}
