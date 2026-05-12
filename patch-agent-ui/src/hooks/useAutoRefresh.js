import { useRef, useCallback } from 'react'

/**
 * Manages a setInterval-based auto-refresh.
 * Returns { startRefresh, stopRefresh }
 */
export function useAutoRefresh(callback, intervalMs = 60000) {
  const timerRef = useRef(null)

  const stopRefresh = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const startRefresh = useCallback(() => {
    stopRefresh()
    callback()   // run immediately
    timerRef.current = setInterval(callback, intervalMs)
  }, [callback, intervalMs, stopRefresh])

  return { startRefresh, stopRefresh }
}
