import { useEffect, useRef, useState } from 'react'
import './App.css'
import { isFirebaseConfigured, loginWithGoogle, logoutFirebase } from './firebase.js'

const AUTH_KEY = 'infra-management-auth'
const initialEnv = [{ key: '', value: '' }]
const checks = [
  ['EC2 instance', 'AWS state is not available'],
  ['SSM agent', 'Connection is not available'],
  ['MySQL', 'Health check is not available'],
  ['Backend API', 'Health check is not available'],
  ['Caddy / HTTPS', 'Domain check is not available']
]

function StatusPill({ status }) {
  return <span className="status-pill">{status}</span>
}

function Login({ onLogin }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Đọc danh sách email được phép từ biến môi trường VITE_ALLOWED_EMAILS
  const rawAllowed = import.meta.env.VITE_ALLOWED_EMAILS || ''
  const allowedList = rawAllowed
    .split(',')
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean)

  const handleGoogleLogin = async () => {
    setLoading(true)
    setError('')
    try {
      const user = await loginWithGoogle()
      const userEmail = (user.email || '').trim().toLowerCase()

      // Kiểm tra Allowlist nếu có cấu hình trong file .env
      if (allowedList.length > 0 && !allowedList.includes(userEmail)) {
        await logoutFirebase()
        setError(`Email "${userEmail}" không nằm trong danh sách được phép truy cập (VITE_ALLOWED_EMAILS). Vui lòng liên hệ Admin.`)
        setLoading(false)
        return
      }

      sessionStorage.setItem(AUTH_KEY, userEmail)
      onLogin(userEmail)
    } catch (err) {
      setError(err?.message || 'Đăng nhập Google thất bại')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-shell">
      <div className="login-card">
        <div className="brand-mark large">M</div>
        <p className="eyebrow">MLAND / PLATFORM OPERATIONS</p>
        <h1>Staging control room</h1>
        <p className="login-copy">Đăng nhập bằng tài khoản Google được cấp quyền để quản lý môi trường Staging.</p>

        {error && <p className="form-error" role="alert">{error}</p>}

        <button
          className="button primary full google-btn"
          onClick={handleGoogleLogin}
          disabled={loading}
          type="button"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.8-2.4 3.66v3.02h3.86c2.26-2.09 3.58-5.17 3.58-9.12z" />
            <path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.86-3.02c-1.08.72-2.45 1.16-4.07 1.16-3.13 0-5.78-2.11-6.73-4.96H1.28v3.12C3.26 21.36 7.35 24 12 24z" />
            <path fill="#FBBC05" d="M5.27 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.61H1.28C.46 8.24 0 10.06 0 12s.46 3.76 1.28 5.39l3.99-3.12z" />
            <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.35 0 3.26 2.64 1.28 6.61l3.99 3.12c.95-2.85 3.6-4.98 6.73-4.98z" />
          </svg>
          <span>{loading ? 'Đang xác thực...' : 'Đăng nhập với Google'}</span>
        </button>

        <div className="dev-note">
          <strong>Bảo mật danh tính</strong>
          <span>Chỉ các email nằm trong cấu hình <code>VITE_ALLOWED_EMAILS</code> mới có thể truy cập hệ thống.</span>
        </div>
      </div>
    </main>
  )
}

function StagingPage({ onNotice, currentUser }) {
  const [showStart, setShowStart] = useState(false)
  const [envRows, setEnvRows] = useState(initialEnv)
  const [polling, setPolling] = useState(true)
  const [logState, setLogState] = useState('Waiting for API connection')
  const [serverStatus, setServerStatus] = useState('STOPPED')

  const controlApiUrl = (import.meta.env.VITE_CONTROL_API_URL || '').replace(/\/$/, '')

  const updateRow = (index, field, value) => setEnvRows((rows) => rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)))
  const addRow = () => setEnvRows((rows) => [...rows, { key: '', value: '' }])
  const removeRow = (index) => setEnvRows((rows) => (rows.length === 1 ? initialEnv : rows.filter((_, i) => i !== index)))
  const notConnected = (action) => onNotice(`${action} is unavailable: Control API is not connected yet.`)

  useEffect(() => {
    if (!polling) return undefined
    let cancelled = false

    const pollLogs = async () => {
      try {
        const endpoint = controlApiUrl ? `${controlApiUrl}/staging/logs` : '/api/staging/logs'
        const response = await fetch(endpoint, { headers: { Accept: 'application/json' } })
        if (!response.ok) throw new Error('Control API is not connected')
        const data = await response.json()
        if (!cancelled) setLogState(data.logs ? 'Log snapshot updated' : 'Waiting for logs')
      } catch {
        if (!cancelled) setLogState('Control API is not connected')
      }
    }

    pollLogs()
    const timer = window.setInterval(pollLogs, 5000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl])

  const handleStart = async (event) => {
    event.preventDefault()
    setShowStart(false)
    if (!controlApiUrl) {
      notConnected('Start')
      return
    }

    try {
      setServerStatus('STARTING')
      onNotice('Lệnh khởi động Staging đã được gửi tới Lambda...')
      const response = await fetch(`${controlApiUrl}/staging/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ owner: currentUser })
      })
      if (!response.ok) throw new Error('Không thể khởi động qua Lambda')
      onNotice('EC2 đang trong quá trình khởi động và kéo code...')
    } catch (err) {
      onNotice(`Lỗi khởi động: ${err.message}`)
      setServerStatus('STOPPED')
    }
  }

  return (
    <>
      <section className="page-intro">
        <div>
          <p className="breadcrumb">Environments <span>/</span> staging</p>
          <div className="title-line">
            <h2>Staging environment</h2>
            <StatusPill status={serverStatus} />
          </div>
          <p className="muted">One shared EC2 environment for the team. Start it when you need it, stop it when you are done.</p>
        </div>
        <div className="role-preview">Signed in as <strong>{currentUser}</strong></div>
      </section>

      <section className="hero-card">
        <div className="hero-status">
          <div className={`status-orb ${serverStatus === 'RUNNING' ? 'orb-running' : 'orb-stopped'}`}>
            <span />
          </div>
          <div>
            <p className="eyebrow">CURRENT STATE</p>
            <h3>Environment is {serverStatus.toLowerCase()}</h3>
            <p className="muted">No EC2 compute charges while stopped. EBS and public IP charges may still apply.</p>
          </div>
        </div>
        <div className="hero-actions">
          <button className="button primary" onClick={() => setShowStart(true)}>Start staging</button>
          <p className="action-hint">Chủ sở hữu hiện tại: {currentUser}</p>
        </div>
      </section>

      <div className="content-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">RESOURCE</p>
              <h3>Runtime details</h3>
            </div>
            <span className="panel-icon">◈</span>
          </div>
          <dl className="details-list">
            <div>
              <dt>Owner</dt>
              <dd>{currentUser}</dd>
            </div>
            <div>
              <dt>Endpoint</dt>
              <dd><code>api-staging.example.com</code></dd>
            </div>
            <div>
              <dt>Region</dt>
              <dd><code>ap-southeast-1</code></dd>
            </div>
            <div>
              <dt>Lambda Control</dt>
              <dd>{controlApiUrl ? <code>Connected</code> : <span className="empty-value">Not configured</span>}</dd>
            </div>
          </dl>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">READINESS</p>
              <h3>Health checks</h3>
            </div>
            <span className="check-summary">0 / 5 ready</span>
          </div>
          <div className="check-list">
            {checks.map(([name, message]) => (
              <div className="check-row" key={name}>
                <span className="check-state">—</span>
                <div>
                  <strong>{name}</strong>
                  <small>{message}</small>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="content-grid lower-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">OPERATIONS</p>
              <h3>Lifecycle timeline</h3>
            </div>
            <button className="text-button" onClick={() => notConnected('Refresh')}>Refresh ↻</button>
          </div>
          <div className="empty-state">
            <div className="empty-icon">◷</div>
            <strong>No operations yet</strong>
            <p>Start an operation to see progress, operation ID and recovery state here.</p>
          </div>
        </section>
      </div>

      <section className="panel logs-panel full-width">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">OBSERVABILITY</p>
            <h3>Log snapshot</h3>
            <p className="muted log-status">{logState} · {polling ? 'polling every 5s' : 'polling paused'}</p>
          </div>
          <div className="log-actions">
            <span className="tail-label">tail 200</span>
            <button className="text-button" onClick={() => setPolling((value) => !value)}>
              {polling ? 'Pause polling' : 'Resume polling'}
            </button>
          </div>
        </div>
        <div className="terminal">
          <span className="terminal-cursor">$</span>
          <span>{logState === 'Control API is not connected' ? 'Logs unavailable until the control API is connected.' : 'Waiting for the first log snapshot...'}</span>
        </div>
      </section>

      {showStart && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setShowStart(false)}>
          <form className="modal" onSubmit={handleStart}>
            <div className="modal-header">
              <div>
                <p className="eyebrow">CLAIM & START</p>
                <h2>Start staging</h2>
                <p className="muted">You will become the owner of this staging session.</p>
              </div>
              <button type="button" className="close-button" onClick={() => setShowStart(false)} aria-label="Close">×</button>
            </div>
            <div className="warning-box">
              <span>!</span>
              <p>Runtime values are sent to the control API and written to the EC2 runtime environment. They are not stored in this browser or returned in logs.</p>
            </div>
            <div className="form-section">
              <div className="form-section-title">
                <div>
                  <h3>Runtime environment</h3>
                  <p className="muted">Optional key/value pairs, like a deployment environment.</p>
                </div>
                <button type="button" className="text-button" onClick={addRow}>+ Add variable</button>
              </div>
              <div className="env-table">
                <div className="env-head">
                  <span>KEY</span>
                  <span>VALUE</span>
                  <span />
                </div>
                {envRows.map((row, index) => (
                  <div className="env-row" key={index}>
                    <input aria-label={`Variable key ${index + 1}`} value={row.key} onChange={(event) => updateRow(index, 'key', event.target.value)} placeholder="DATABASE_URL" />
                    <input aria-label={`Variable value ${index + 1}`} type="password" value={row.value} onChange={(event) => updateRow(index, 'value', event.target.value)} placeholder="Enter value" />
                    <button type="button" className="remove-button" onClick={() => removeRow(index)} aria-label="Remove variable">×</button>
                  </div>
                ))}
              </div>
            </div>
            <div className="modal-footer">
              <button type="button" className="button secondary" onClick={() => setShowStart(false)}>Cancel</button>
              <button type="submit" className="button primary">Confirm start <span>→</span></button>
            </div>
          </form>
        </div>
      )}
    </>
  )
}

function ArchitecturePage() {
  const frameRef = useRef(null)
  const [diagramError, setDiagramError] = useState('')

  useEffect(() => {
    let xml = ''
    fetch('/architecture/current-deployment.drawio')
      .then((response) => {
        if (!response.ok) throw new Error('Diagram file could not be loaded')
        return response.text()
      })
      .then((text) => {
        xml = text
        frameRef.current?.contentWindow?.postMessage(JSON.stringify({ action: 'load', xml }), '*')
      })
      .catch((error) => setDiagramError(error.message))

    const onMessage = (event) => {
      if (event.source !== frameRef.current?.contentWindow) return
      try {
        const message = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
        if (message.event === 'init' && xml) {
          frameRef.current.contentWindow.postMessage(JSON.stringify({ action: 'load', xml }), '*')
        }
      } catch {
        /* viewer can send non-JSON messages */
      }
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])

  return (
    <section className="architecture-page">
      <div className="architecture-heading">
        <div>
          <p className="eyebrow">SYSTEM MAP</p>
          <h2>Current deployment architecture</h2>
          <p className="muted">Fixed Draw.io source · view and zoom only.</p>
        </div>
        <span className="file-badge">architecture/current-deployment.drawio</span>
      </div>
      <div className="diagram-frame">
        {diagramError ? (
          <div className="diagram-placeholder">
            <span className="empty-icon">!</span>
            <strong>Diagram could not be loaded</strong>
            <p>{diagramError}</p>
          </div>
        ) : (
          <iframe
            ref={frameRef}
            title="Current deployment architecture"
            src="https://viewer.diagrams.net/?embed=1&proto=json&spin=1&libraries=1&readOnly=1"
            className="diagram-iframe"
          />
        )}
      </div>
    </section>
  )
}

function App() {
  const [currentUser, setCurrentUser] = useState(() => sessionStorage.getItem(AUTH_KEY))
  const [activeTab, setActiveTab] = useState('staging')
  const [notice, setNotice] = useState('Control API is ready for connection')

  const logout = async () => {
    await logoutFirebase()
    sessionStorage.removeItem(AUTH_KEY)
    setCurrentUser(null)
  }

  if (!currentUser) {
    return <Login onLogin={(email) => setCurrentUser(email)} />
  }

  const userInitial = currentUser.charAt(0).toUpperCase()

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand-block">
          <div className="brand-mark">M</div>
          <div>
            <p className="eyebrow">MLAND / PLATFORM OPERATIONS</p>
            <h1>Staging control room</h1>
          </div>
        </div>
        <div className="topbar-actions">
          <span className="connection-dot" />
          <span>{import.meta.env.VITE_CONTROL_API_URL ? 'Lambda Configured' : 'API disconnected'}</span>
          <span className="avatar" title={currentUser}>{userInitial}</span>
          <button className="logout-button" onClick={logout}>Đăng xuất</button>
        </div>
      </header>

      <nav className="tabs" aria-label="Main navigation">
        <button className={activeTab === 'staging' ? 'active' : ''} onClick={() => setActiveTab('staging')}>
          Staging control
        </button>
        <button className={activeTab === 'architecture' ? 'active' : ''} onClick={() => setActiveTab('architecture')}>
          Architecture
        </button>
      </nav>

      {notice && (
        <div className="notice" role="status">
          <span className="notice-icon">i</span>
          <span>{notice}</span>
          <button onClick={() => setNotice('')}>Dismiss</button>
        </div>
      )}

      {activeTab === 'staging' ? <StagingPage onNotice={setNotice} currentUser={currentUser} /> : <ArchitecturePage />}

      <footer className="footer">
        <span>Managed by Mland Platform</span>
        <span>Secrets are never stored in this browser</span>
        <span>v0.3 · Staging Control Console</span>
      </footer>
    </main>
  )
}

export default App
