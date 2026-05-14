import { useState } from 'react'
import { api } from '../api/client'

/**
 * Full-page login form.
 * Matches the navy theme of the main app.
 * On success, calls onLogin(username) so App.jsx can switch views.
 */
export function LoginPage({ onLogin }) {
  const [environment, setEnvironment] = useState('nonprod')
  const [username,    setUsername]    = useState('')
  const [password,    setPassword]    = useState('')
  const [showPw,      setShowPw]      = useState(false)
  const [error,       setError]       = useState('')
  const [loading,     setLoading]     = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (!username.trim() || !password) {
      setError('Username and password are required.')
      return
    }

    setLoading(true)
    try {
      const data = await api.login(username.trim(), password, environment)
      if (data.error) {
        setError(data.error)
      } else {
        onLogin(data.username, data.environment || environment)
      }
    } catch {
      setError('Unable to contact authentication server. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>

        {/* Header */}
        <div style={styles.header}>
          <div style={styles.iconCircle}>
            {/* Shield / lock icon */}
            <svg width="26" height="26" viewBox="0 0 24 24" fill="#fff">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45
                       9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7
                       8.94V12H5V6.3l7-3.11v8.8z"/>
            </svg>
          </div>
          <h1 style={styles.title}>Patch Agent</h1>
          <p style={styles.subtitle}>Sign in with your Active Directory credentials</p>
        </div>

        {/* Error banner */}
        {error && (
          <div style={styles.errorBox}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" style={{flexShrink:0}}>
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48
                       10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
            </svg>
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit}>

          {/* Environment dropdown */}
          <div style={styles.fieldGroup}>
            <label htmlFor="pa-environment" style={styles.label}>Environment</label>
            <div style={{position:'relative', marginTop:'0.35rem'}}>
              <select
                id="pa-environment"
                value={environment}
                onChange={e => setEnvironment(e.target.value)}
                disabled={loading}
                style={styles.select}
              >
                <option value="nonprod">Non-Prod</option>
                <option value="prod">Prod</option>
              </select>
              {/* Env badge dot */}
              <span style={{
                ...styles.envDot,
                background: environment === 'prod' ? '#ef4444' : '#22c55e',
              }} />
            </div>
          </div>

          <div style={styles.fieldGroup}>
            <label htmlFor="pa-username" style={styles.label}>Username</label>
            <input
              id="pa-username"
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="domain\username or username"
              autoComplete="username"
              autoFocus
              disabled={loading}
              style={styles.input}
            />
          </div>

          <div style={{...styles.fieldGroup, marginBottom: '1.5rem'}}>
            <label htmlFor="pa-password" style={styles.label}>Password</label>
            <div style={styles.pwWrapper}>
              <input
                id="pa-password"
                type={showPw ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                disabled={loading}
                style={{...styles.input, paddingRight: '2.6rem'}}
              />
              <button
                type="button"
                onClick={() => setShowPw(v => !v)}
                style={styles.eyeBtn}
                title={showPw ? 'Hide password' : 'Show password'}
                tabIndex={-1}
              >
                {showPw ? (
                  // eye-off
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36
                             1.83l2.92 2.92c1.51-1.26 2.7-2.89
                             3.43-4.75C21.27 7.61 17 4.5 12 4.5c-1.4 0-2.74.25-3.98.7l2.16
                             2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46A11.804
                             11.804 0 001 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3
                             4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2
                             4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34
                             3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76
                             0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>
                  </svg>
                ) : (
                  // eye
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11
                             7.5s9.27-3.11 11-7.5C21.27 7.61 17 4.5 12 4.5zM12 17c-2.76
                             0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66
                             0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
                  </svg>
                )}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{...styles.submitBtn, opacity: loading ? 0.7 : 1}}
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        <p style={styles.footer}>Access is restricted to authorised personnel only.</p>
      </div>
    </div>
  )
}

// ── Inline styles (no external CSS dependency) ──────────────────────────────

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #0d1b2a 0%, #1b2d45 100%)',
    fontFamily: "'Segoe UI', system-ui, sans-serif",
    padding: '1rem',
  },
  card: {
    background: '#fff',
    borderRadius: '12px',
    boxShadow: '0 8px 40px rgba(0,0,0,0.45)',
    width: '100%',
    maxWidth: '420px',
    padding: '2.5rem 2.25rem 2rem',
  },
  header: {
    textAlign: 'center',
    marginBottom: '2rem',
  },
  iconCircle: {
    width: '54px',
    height: '54px',
    background: '#1e88e5',
    borderRadius: '50%',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: '0.75rem',
  },
  title: {
    fontSize: '1.4rem',
    fontWeight: 700,
    color: '#0d1b2a',
    margin: '0 0 0.2rem',
  },
  subtitle: {
    fontSize: '0.85rem',
    color: '#6c757d',
    margin: 0,
  },
  errorBox: {
    background: '#fff1f0',
    border: '1px solid #fca5a5',
    borderRadius: '8px',
    color: '#b91c1c',
    fontSize: '0.875rem',
    padding: '0.65rem 0.9rem',
    marginBottom: '1.25rem',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
  fieldGroup: {
    marginBottom: '1rem',
  },
  label: {
    display: 'block',
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#374151',
    marginBottom: '0.35rem',
  },
  input: {
    display: 'block',
    width: '100%',
    boxSizing: 'border-box',
    padding: '0.55rem 0.85rem',
    fontSize: '0.9rem',
    border: '1px solid #d1d5db',
    borderRadius: '8px',
    outline: 'none',
    transition: 'border-color 0.15s',
  },
  pwWrapper: {
    position: 'relative',
  },
  select: {
    display: 'block',
    width: '100%',
    boxSizing: 'border-box',
    padding: '0.55rem 2.2rem 0.55rem 0.85rem',
    fontSize: '0.9rem',
    border: '1px solid #d1d5db',
    borderRadius: '8px',
    outline: 'none',
    appearance: 'auto',
    background: '#fff',
  },
  envDot: {
    position: 'absolute',
    right: '10px',
    top: '50%',
    transform: 'translateY(-50%)',
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    pointerEvents: 'none',
  },
  eyeBtn: {
    position: 'absolute',
    right: '10px',
    top: '50%',
    transform: 'translateY(-50%)',
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: 0,
    color: '#9ca3af',
    display: 'flex',
    alignItems: 'center',
  },
  submitBtn: {
    display: 'block',
    width: '100%',
    padding: '0.65rem 1rem',
    fontSize: '0.95rem',
    fontWeight: 600,
    color: '#fff',
    background: '#1e88e5',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    transition: 'background 0.15s',
  },
  footer: {
    textAlign: 'center',
    fontSize: '0.78rem',
    color: '#9ca3af',
    marginTop: '1.5rem',
    marginBottom: 0,
  },
}
