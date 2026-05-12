export function TopBar({ rowCount, onReload }) {
  return (
    <div className="topbar">
      <i className="bi bi-server fs-5"></i>
      <h1>Patch Agent</h1>
      <span className="badge-version">v2.2</span>
      <span className="ms-auto" style={{ fontSize: '.8rem', opacity: .7 }}>
        {rowCount > 0 ? `${rowCount} service rows loaded` : ''}
      </span>
      <button className="btn btn-sm btn-outline-light ms-2" onClick={onReload}>
        <i className="bi bi-arrow-clockwise"></i> Reload
      </button>
    </div>
  )
}
