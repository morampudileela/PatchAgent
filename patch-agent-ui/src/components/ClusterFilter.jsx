export function ClusterFilter({ clusters, activeCluster, onClusterChange, rowCount }) {
  return (
    <div className="cluster-bar">
      <label htmlFor="clusterSelect">
        <i className="bi bi-diagram-3"></i> Cluster:
      </label>
      <select
        id="clusterSelect"
        className="form-select form-select-sm cluster-select"
        value={activeCluster}
        onChange={e => onClusterChange(e.target.value)}
      >
        <option value="All">All Clusters</option>
        {clusters.map(c => (
          <option key={c} value={c}>{c}</option>
        ))}
      </select>
      <span style={{ fontSize: '.78rem', color: '#6c757d' }}>
        {rowCount} row{rowCount !== 1 ? 's' : ''}
      </span>
    </div>
  )
}
