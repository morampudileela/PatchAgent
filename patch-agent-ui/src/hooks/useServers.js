import { useState, useCallback } from 'react'
import { api } from '../api/client'

/**
 * Loads and caches the server inventory from /api/servers.
 * Returns { allRows, clusters, loading, error, reload }
 */
export function useServers() {
  const [allRows,  setAllRows]  = useState([])
  const [clusters, setClusters] = useState([])
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.getServers()
      if (data.error) throw new Error(data.error)
      setAllRows(data.rows  || [])
      setClusters(data.clusters || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  return { allRows, clusters, loading, error, reload }
}
