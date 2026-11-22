import { useEffect, useRef, useState } from 'react'
import './App.css'
import { loginWithGoogle, logoutFirebase } from './firebase.js'
import { parseEnvContent } from './envParser.js'

const AUTH_KEY = 'infra-management-auth'
const initialEnv = [{ key: '', value: '' }]
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
  const [gitBranch, setGitBranch] = useState('develop')
  const [envRows, setEnvRows] = useState(initialEnv)
  const [polling, setPolling] = useState(true)
  const [logState, setLogState] = useState('Waiting for API connection')
  const [logContent, setLogContent] = useState('')
  const [serverStatus, setServerStatus] = useState('STOPPED')
  const [serverIp, setServerIp] = useState('')
  const [healthChecks, setHealthChecks] = useState([])
  const [lastOperation, setLastOperation] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [isDeploying, setIsDeploying] = useState(false)
  const fileInputRef = useRef(null)

  const controlApiUrl = (import.meta.env.VITE_CONTROL_API_URL || '').replace(/\/$/, '')

  const updateRow = (index, field, value) => setEnvRows((rows) => rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)))
  const addRow = () => setEnvRows((rows) => [...rows, { key: '', value: '' }])
  const removeRow = (index) => setEnvRows((rows) => (rows.length === 1 ? initialEnv : rows.filter((_, i) => i !== index)))
  const notConnected = (action) => onNotice(`${action} is unavailable: Control API is not connected yet.`)

  const handleFileUpload = (event) => {
    const file = event.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (e) => {
      const text = e.target?.result
      if (typeof text === 'string') {
        const parsed = parseEnvContent(text)
        setEnvRows(parsed)
        const valid = parsed.filter((r) => r.key.trim()).length
        onNotice(`Đã import thành công ${valid} biến môi trường từ file "${file.name}"`)
      }
    }
    reader.onerror = () => {
      onNotice(`Không thể đọc file "${file.name}". Vui lòng thử lại.`)
    }
    reader.readAsText(file)
    event.target.value = ''
  }

  const validCount = envRows.filter((r) => r.key.trim()).length

  useEffect(() => {
    if (!polling) return undefined
    let cancelled = false

    const pollStatusAndLogs = async () => {
      if (!controlApiUrl) return

      // 1. Kiểm tra trạng thái máy chủ EC2 thực tế
      try {
        const statusRes = await fetch(`${controlApiUrl}/staging/status`, { headers: { Accept: 'application/json' } })
        if (!statusRes.ok) throw new Error(`Status API returned HTTP ${statusRes.status}`)
        if (statusRes.ok) {
          const statusData = await statusRes.json()
          if (!cancelled && statusData.state) {
            const normalized = statusData.state.toUpperCase()
            setServerStatus(normalized === 'PENDING' ? 'STARTING' : normalized)
            setServerIp(statusData.publicIp || '')
            setHealthChecks([
              { name: 'EC2 instance', ready: normalized === 'RUNNING', detail: `AWS state: ${normalized}` },
              { name: 'SSM agent', ready: statusData.ssmStatus === 'Online', detail: `Connection: ${statusData.ssmStatus || 'unknown'}` },
              { name: 'MySQL', ready: null, detail: 'Container health is shown in the log snapshot' },
              { name: 'Backend API', ready: null, detail: 'HTTP health endpoint is not configured' },
              { name: 'Caddy / HTTPS', ready: null, detail: 'Domain and TLS health check is not configured' }
            ])
          }
        }
      } catch (error) {
        if (!cancelled) setHealthChecks([{ name: 'Control API', ready: false, detail: error.message || 'Status request failed' }])
      }

      // 2. Lấy snapshot log container / deploy
      try {
        const logsRes = await fetch(`${controlApiUrl}/staging/logs`, { headers: { Accept: 'application/json' } })
        if (!logsRes.ok) throw new Error('Control API is not connected')
        const logsData = await logsRes.json()
        if (!cancelled) {
          setLogContent(logsData.logs || '')
          setLogState(logsData.logs ? 'Log snapshot updated' : 'Waiting for logs')
        }
      } catch {
        if (!cancelled) setLogState('Control API is not connected')
      }
    }

    pollStatusAndLogs()
    const timer = window.setInterval(pollStatusAndLogs, 5000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl, refreshKey])

  const handleStart = async (event) => {
    event.preventDefault()
    setShowStart(false)
    if (!controlApiUrl) {
      notConnected('Start')
      return
    }

    const envPayload = envRows.reduce((acc, row) => {
      const k = (row.key || '').trim()
      if (k) acc[k] = row.value ?? ''
      return acc
    }, {})

    try {
      setServerStatus('STARTING')
      onNotice(`Lệnh khởi động Staging (branch: ${gitBranch}) đã được gửi tới Lambda...`)
      const response = await fetch(`${controlApiUrl}/staging/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          owner: currentUser,
          branch: gitBranch,
          env: envPayload
        })
      })
      if (!response.ok) throw new Error('Không thể khởi động qua Lambda')
      const data = await response.json().catch(() => ({}))
      setLastOperation({ name: 'Start', status: data.status || 'SUBMITTED', id: data.instanceId || 'ID not returned', branch: data.branch || gitBranch, at: new Date().toLocaleString() })
      onNotice(data.message || 'EC2 đang trong quá trình khởi động...')
    } catch (err) {
      onNotice(`Lỗi khởi động: ${err.message}`)
      setServerStatus('STOPPED')
    }
  }

  const handleDeploy = async () => {
    if (!controlApiUrl) {
      notConnected('Deploy')
      return
    }

    const envPayload = envRows.reduce((acc, row) => {
      const k = (row.key || '').trim()
      if (k) acc[k] = row.value ?? ''
      return acc
    }, {})

    try {
      setIsDeploying(true)
      onNotice(`Đang gửi lệnh kéo code branch "${gitBranch}" và build trên EC2...`)
      const response = await fetch(`${controlApiUrl}/staging/deploy`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          owner: currentUser,
          branch: gitBranch,
          env: envPayload
        })
      })
      if (!response.ok) throw new Error('Không thể gửi lệnh deploy tới Lambda')
      const data = await response.json().catch(() => ({}))
      setLastOperation({ name: 'Deploy', status: data.status || 'SUBMITTED', id: data.commandId || 'ID not returned', branch: data.branch || gitBranch, at: new Date().toLocaleString() })
      onNotice(data.message || 'Lệnh deploy đã được gửi thành công. Đang tiến hành build container...')
    } catch (err) {
      onNotice(`Lỗi deploy: ${err.message}`)
    } finally {
      setIsDeploying(false)
    }
  }

  const handleStop = async () => {
    if (!controlApiUrl) {
      notConnected('Stop')
      return
    }
    try {
      setServerStatus('STOPPING')
      onNotice('Đang gửi lệnh dừng EC2 và container...')
      const response = await fetch(`${controlApiUrl}/staging/stop`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      })
      if (!response.ok) throw new Error('Không thể dừng qua Lambda')
      const data = await response.json().catch(() => ({}))
      setLastOperation({ name: 'Stop', status: data.status || 'SUBMITTED', id: data.instanceId || 'ID not returned', branch: gitBranch, at: new Date().toLocaleString() })
      onNotice(data.message || 'Lệnh dừng EC2 đã được gửi thành công.')
    } catch (err) {
      onNotice(`Lỗi dừng EC2: ${err.message}`)
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
            <p className="muted">
              {serverStatus === 'RUNNING'
                ? `EC2 đang chạy tại IP: ${serverIp || 'Đang lấy IP...'}. Cổng 80 phục vụ backend.`
                : 'No EC2 compute charges while stopped. EBS and public IP charges may still apply.'}
            </p>
          </div>
        </div>
        <div className="hero-actions">
          {serverStatus === 'RUNNING' ? (
            <div className="action-buttons-group">
              <button
                className="button primary deploy-btn"
                onClick={handleDeploy}
                disabled={isDeploying}
                title="Kéo code mới nhất từ GitHub và rebuild backend"
              >
                {isDeploying ? 'Đang deploy...' : 'Deploy / Rebuild ⟳'}
              </button>
              <button
                className="button secondary stop-btn"
                onClick={handleStop}
                title="Dừng EC2 và giải phóng containers"
              >
                Stop staging
              </button>
            </div>
          ) : serverStatus === 'STARTING' || serverStatus === 'STOPPING' ? (
            <button className="button primary" disabled>
              {serverStatus === 'STARTING' ? 'Đang khởi động...' : 'Đang tắt máy...'}
            </button>
          ) : (
            <button className="button primary" onClick={() => setShowStart(true)}>
              Start staging
            </button>
          )}
          <p className="action-hint">
            Chủ sở hữu: <strong>{currentUser}</strong> · Branch: <code>{gitBranch}</code>
          </p>
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
              <dt>Active Branch</dt>
              <dd><code>{gitBranch}</code></dd>
            </div>
            <div>
              <dt>Backend HTTP</dt>
              <dd>
                {serverIp ? (
                  <a href={`http://${serverIp}`} target="_blank" rel="noreferrer">
                    <code>http://{serverIp}</code>
                  </a>
                ) : (
                  <span className="empty-value">No public IP available</span>
                )}
              </dd>
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
            <span className="check-summary">{healthChecks.filter((item) => item.ready === true).length} ready · {healthChecks.filter((item) => item.ready === null).length} not configured</span>
          </div>
          <div className="check-list">
            {healthChecks.map(({ name, detail, ready }) => (
              <div className="check-row" key={name}>
                <span className="check-state">{ready === true ? '✓' : ready === false ? '!' : '—'}</span>
                <div>
                  <strong>{name}</strong>
                  <small>{detail}</small>
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
            <button className="text-button" onClick={() => setRefreshKey((key) => key + 1)}>Refresh ↻</button>
          </div>
          {lastOperation ? (
            <div className="operation-entry">
              <strong>{lastOperation.name}: {lastOperation.status}</strong>
              <p>Branch <code>{lastOperation.branch}</code> · ID <code>{lastOperation.id}</code></p>
              <small>{lastOperation.at} · See log snapshot for execution output.</small>
            </div>
          ) : <div className="empty-state">
            <div className="empty-icon">◷</div>
            <strong>No operations yet</strong>
            <p>Start an operation to see progress, operation ID and recovery state here.</p>
          </div>}
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
          <pre className="terminal-pre">
            {logContent || (logState === 'Control API is not connected' ? 'Logs unavailable until the control API is connected.' : 'Waiting for the first log snapshot...')}
          </pre>
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

            <div className="form-section branch-section">
              <div className="form-section-title">
                <div>
                  <h3>Git Repository Branch</h3>
                  <p className="muted">Nhánh code sẽ được tự động kéo từ GitHub về EC2 và build backend.</p>
                </div>
              </div>
              <div className="branch-input-row">
                <span className="branch-tag">Branch</span>
                <input
                  aria-label="Git branch"
                  value={gitBranch}
                  onChange={(e) => setGitBranch(e.target.value)}
                  placeholder="develop"
                  spellCheck={false}
                />
              </div>
            </div>
            <div className="form-section">
              <input
                ref={fileInputRef}
                type="file"
                accept=".env,.env.*,text/plain"
                style={{ display: 'none' }}
                onChange={handleFileUpload}
              />
              <div className="form-section-title">
                <div>
                  <div className="env-title-row">
                    <h3>Runtime environment</h3>
                    <span className="env-count-badge">
                      {validCount} {validCount === 1 ? 'variable' : 'variables'}
                    </span>
                  </div>
                  <p className="muted">Optional key/value pairs. Import a .env file or enter variables manually.</p>
                </div>
                <div className="env-actions-group">
                  <button
                    type="button"
                    className="import-button"
                    onClick={() => fileInputRef.current?.click()}
                    title="Upload file .env để nạp biến"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                    <span>Import .env</span>
                  </button>
                  <button type="button" className="text-button" onClick={addRow}>+ Add variable</button>
                  {envRows.length > 1 && (
                    <button type="button" className="text-button text-muted-action" onClick={() => setEnvRows(initialEnv)}>Clear all</button>
                  )}
                </div>
              </div>
              <div className="env-table">
                <div className="env-head">
                  <span>KEY</span>
                  <span>VALUE</span>
                  <span />
                </div>
                <div className="env-table-body">
                  {envRows.map((row, index) => (
                    <div className="env-row" key={index}>
                      <input
                        aria-label={`Variable key ${index + 1}`}
                        value={row.key}
                        onChange={(event) => updateRow(index, 'key', event.target.value)}
                        placeholder="DATABASE_URL"
                        spellCheck={false}
                      />
                      <input
                        aria-label={`Variable value ${index + 1}`}
                        type="text"
                        value={row.value}
                        onChange={(event) => updateRow(index, 'value', event.target.value)}
                        placeholder="Enter value"
                        spellCheck={false}
                      />
                      <button
                        type="button"
                        className="remove-button"
                        onClick={() => removeRow(index)}
                        aria-label="Remove variable"
                        title="Xóa biến này"
                      >×</button>
                    </div>
                  ))}
                </div>
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
