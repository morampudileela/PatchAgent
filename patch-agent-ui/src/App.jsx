import { useState, useEffect, useCallback } from 'react'
import { TopBar }       from './components/TopBar'
import { ClusterFilter } from './components/ClusterFilter'
import { ServerTable }  from './components/ServerTable'
import { ActionBar }    from './components/ActionBar'
import { LogPanel }     from './components/LogPanel'
import { HistoryPanel } from './components/HistoryPanel'
import { useServers }   from './hooks/useServers'
import { useJobStream } from './hooks/useJobStream'
import { useAutoRefresh } from './hooks/useAutoRefresh'
import { api }          from './api/client'

export default function App() {
  // ── Server inventory ─────────────────────────────────────────────
  const { allRows, clusters, reload } = useServers()

  // ── Cluster filter ───────────────────────────────────────────────
  const [activeCluster, setActiveCluster] = useState('All')
  const visibleRows = activeCluster === 'All'
    ? allRows
    : allRows.filter(r => r.cluster === activeCluster)

  // ── Row selection ────────────────────────────────────────────────
  const [selectedIds, setSelectedIds] = useState(new Set())

  function toggleRow(id) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function toggleAll(checked, rows) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      rows.forEach(r => checked ? next.add(r.id) : next.delete(r.id))
      return next
    })
  }

  // ── Status checks ────────────────────────────────────────────────
  const [rowStatuses,  setRowStatuses]  = useState({})
  const [lastChecked,  setLastChecked]  = useState('')
  const [autoRefresh,  setAutoRefresh]  = useState(false)

  const doStatusCheck = useCallback(async () => {
    const checkRows = visibleRows.filter(r => r.status_cmd)
    if (!checkRows.length) return
    try {
      const data = await api.checkStatus(checkRows.map(r => r.id))
      if (data.error) { console.error('Status check:', data.error); return }
      setRowStatuses(prev => ({ ...prev, ...Object.fromEntries(
        Object.entries(data.statuses).map(([k, v]) => [parseInt(k), v])
      )}))
      const now = new Date().toLocaleTimeString()
      const lbl = activeCluster === 'All' ? 'all clusters' : activeCluster
      setLastChecked(`Last checked: ${now} (${lbl} — ${checkRows.length} of ${visibleRows.length} rows)`)
    } catch (e) {
      console.error('Status check failed:', e)
    }
  }, [visibleRows, activeCluster])

  const { startRefresh, stopRefresh } = useAutoRefresh(doStatusCheck, 60000)

  function handleAutoRefreshChange(enabled) {
    setAutoRefresh(enabled)
    if (enabled) {
      startRefresh()
      setLastChecked('Auto-refresh active (60s)...')
    } else {
      stopRefresh()
      setLastChecked('Auto-refresh off')
    }
  }

  function handleClusterChange(value) {
    setActiveCluster(value)
    if (autoRefresh) doStatusCheck()
  }

  // ── History ──────────────────────────────────────────────────────
  const [sessions, setSessions] = useState([])

  async function loadHistory() {
    try {
      const data = await api.getHistory()
      setSessions(data.sessions || [])
    } catch (_) {}
  }

  // ── Job execution ────────────────────────────────────────────────
  const onJobComplete = useCallback(() => {
    loadHistory()
    if (allRows.some(r => r.status_cmd)) doStatusCheck()
  }, [allRows, doStatusCheck])

  const { logLines, progress, startJob, clearLog } = useJobStream(onJobComplete)

  // ── Bootstrap ────────────────────────────────────────────────────
  useEffect(() => {
    reload()
    loadHistory()
  }, [])

  // ─────────────────────────────────────────────────────────────────
  return (
    <>
      <TopBar rowCount={allRows.length} onReload={reload} />

      <div className="container-fluid py-3">
        <div className="row g-3">

          {/* Left column — server list */}
          <div className="col-lg-8">
            <div className="card shadow-sm">
              <div className="card-body p-0">

                <ClusterFilter
                  clusters={clusters}
                  activeCluster={activeCluster}
                  onClusterChange={handleClusterChange}
                  rowCount={visibleRows.length}
                />

                <ServerTable
                  rows={visibleRows}
                  selectedIds={selectedIds}
                  rowStatuses={rowStatuses}
                  onToggle={toggleRow}
                  onToggleAll={toggleAll}
                />

                <ActionBar
                  allRows={allRows}
                  selectedIds={selectedIds}
                  onSelectionChange={setSelectedIds}
                  onStartJob={startJob}
                  onCheckStatus={doStatusCheck}
                  lastChecked={lastChecked}
                  autoRefresh={autoRefresh}
                  onAutoRefreshChange={handleAutoRefreshChange}
                />

              </div>
            </div>
          </div>

          {/* Right column — log + history */}
          <div className="col-lg-4">
            <LogPanel
              logLines={logLines}
              progress={progress}
              onClear={clearLog}
            />
            <HistoryPanel
              sessions={sessions}
              onRefresh={loadHistory}
            />
          </div>

        </div>
      </div>
    </>
  )
}
