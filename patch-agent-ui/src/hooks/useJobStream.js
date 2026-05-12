import { useState, useRef, useCallback } from 'react'
import { api } from '../api/client'

/**
 * Manages a job lifecycle: start → SSE stream → complete.
 * Returns { logLines, progress, running, startJob, clearLog }
 */
export function useJobStream(onJobComplete) {
  const [logLines, setLogLines] = useState([])
  const [progress, setProgress] = useState({ pct: 0, label: 'Idle' })
  const [running,  setRunning]  = useState(false)
  const esRef = useRef(null)

  const clearLog = useCallback(() => setLogLines([]), [])

  const appendLog = useCallback((level, message, ts) => {
    setLogLines(prev => [...prev, { level, message, ts, key: Date.now() + Math.random() }])
  }, [])

  const startJob = useCallback(async (selectedIds, action, dryRun) => {
    if (selectedIds.size === 0) {
      alert('Please select at least one server row first.')
      return
    }

    const selArray = [...selectedIds].sort((a, b) => a - b)
    const selStr   = selArray.join(',')

    const confirmed = window.confirm(
      `${dryRun ? '[DRY-RUN] ' : ''}${action.toUpperCase()} services on ${selArray.length} selected row(s)?\n\nRows: ${selStr}`
    )
    if (!confirmed) return

    clearLog()
    setProgress({ pct: 0, label: `Running -- ${action.toUpperCase()} (${selArray.length} rows)` })
    setRunning(true)

    // Close any existing stream
    if (esRef.current) { esRef.current.close(); esRef.current = null }

    try {
      const data = await api.startJob(selStr, action, dryRun)
      if (data.error) { appendLog('error', 'Error: ' + data.error); setRunning(false); return }

      const es = new EventSource(`/api/job/stream/${data.job_id}`)
      esRef.current = es

      es.onmessage = (e) => {
        const ev = JSON.parse(e.data)
        appendLog(ev.level, ev.message, ev.ts)

        if (ev.progress !== undefined) {
          setProgress({
            pct:   ev.progress,
            label: ev.done ? 'Complete' : `Running -- ${ev.progress}%`,
          })
        }
        if (ev.done) {
          es.close()
          esRef.current = null
          setRunning(false)
          if (onJobComplete) onJobComplete()
        }
      }

      es.addEventListener('done', () => {
        es.close()
        esRef.current = null
        setRunning(false)
        if (onJobComplete) onJobComplete()
      })

      es.onerror = () => {
        es.close()
        esRef.current = null
        setRunning(false)
      }

    } catch (e) {
      appendLog('error', 'Failed to start job: ' + e.message)
      setRunning(false)
    }
  }, [clearLog, appendLog, onJobComplete])

  return { logLines, progress, running, startJob, clearLog }
}
