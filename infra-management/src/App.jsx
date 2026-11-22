import { useCallback, useEffect, useRef, useState } from 'react'
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
  const [showDeploy, setShowDeploy] = useState(false)
  const [gitBranch, setGitBranch] = useState('develop')
  const [envRows, setEnvRows] = useState(initialEnv)
  const [polling, setPolling] = useState(true)
  const [logState, setLogState] = useState('Waiting for API connection')
  const [logContent, setLogContent] = useState('')
  const [serverStatus, setServerStatus] = useState('STOPPED')
  const [serverIp, setServerIp] = useState('')
  const [healthChecks, setHealthChecks] = useState([])
  const [lastOperation, setLastOperation] = useState(null)
  const [snapshotCommandId, setSnapshotCommandId] = useState('')
  const [isRefreshingLogs, setIsRefreshingLogs] = useState(false)
  const [autoRefreshLogs, setAutoRefreshLogs] = useState(false)
  const [logSource, setLogSource] = useState('overview')
  const [isStopping, setIsStopping] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [isDeploying, setIsDeploying] = useState(false)
  const [firebaseFileName, setFirebaseFileName] = useState(() => localStorage.getItem('infra-firebase-filename') || '')
  const [firebaseConfigContent, setFirebaseConfigContent] = useState(() => localStorage.getItem('infra-firebase-content') || '')
  const fileInputRef = useRef(null)
  const firebaseInputRef = useRef(null)

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

  const handleFirebaseUpload = (event) => {
    const file = event.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (e) => {
      const text = e.target?.result
      if (typeof text === 'string') {
        try {
          JSON.parse(text)
          setFirebaseConfigContent(text)
          setFirebaseFileName(file.name)
          localStorage.setItem('infra-firebase-filename', file.name)
          localStorage.setItem('infra-firebase-content', text)
          onNotice(`Đã đính kèm file cấu hình Firebase: "${file.name}"`)
        } catch {
          onNotice(`File "${file.name}" không phải là định dạng JSON hợp lệ. Vui lòng kiểm tra lại.`)
        }
      }
    }
    reader.onerror = () => {
      onNotice(`Không thể đọc file "${file.name}". Vui lòng thử lại.`)
    }
    reader.readAsText(file)
    event.target.value = ''
  }

  const handleRemoveFirebase = () => {
    setFirebaseConfigContent('')
    setFirebaseFileName('')
    localStorage.removeItem('infra-firebase-filename')
    localStorage.removeItem('infra-firebase-content')
    onNotice('Đã gỡ file cấu hình Firebase.')
  }

  const validCount = envRows.filter((r) => r.key.trim()).length

  const applyOperationChecks = useCallback((checks = {}) => {
    const labels = { db: 'MySQL', backend: 'Backend container' }
    const updates = Object.entries(checks).map(([key, value]) => {
      const running = value.state === 'running'
      const ready = key === 'db' ? running && value.health === 'healthy' : running
      const detail = key === 'db'
        ? `Container ${value.state}; Docker health ${value.health}`
        : `Container ${value.state}; application readiness is not exposed`
      return { name: labels[key] || key, ready, detail }
    })
    if (updates.length) {
      setHealthChecks((current) => [...current.filter((item) => !updates.some((check) => check.name === item.name)), ...updates])
    }
  }, [])

  const readOperation = useCallback(async (commandId) => {
    const response = await fetch(`${controlApiUrl}/staging/operation?commandId=${encodeURIComponent(commandId)}`, {
      headers: { Accept: 'application/json' }
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok && response.status !== 202) throw new Error(data.error || `Operation API returned HTTP ${response.status}`)
    return data
  }, [controlApiUrl])

  useEffect(() => {
    if (!polling || !controlApiUrl) return undefined
    let cancelled = false
    const pollStatus = async () => {
      try {
        const response = await fetch(`${controlApiUrl}/staging/status`, { headers: { Accept: 'application/json' } })
        const data = await response.json().catch(() => ({}))
        if (!response.ok) throw new Error(data.error || `Status API returned HTTP ${response.status}`)
        if (cancelled || !data.state) return

        const normalized = data.state.toUpperCase()
        const displayState = ({ PENDING: 'STARTING', STOPPING: 'STOPPING', 'SHUTTING-DOWN': 'STOPPING', STOPPED: 'STOPPED', TERMINATED: 'STOPPED' })[normalized] || normalized
        setServerStatus(displayState)
        setServerIp(data.publicIp || '')
        
        if (normalized === 'STOPPED') {
          setHealthChecks([
            { name: 'Control API', ready: true, detail: 'API connected' },
            { name: 'EC2 instance', ready: false, detail: 'AWS state: STOPPED' },
            { name: 'SSM agent', ready: false, detail: 'Connection: offline' },
            { name: 'MySQL', ready: false, detail: 'Instance stopped' },
            { name: 'Backend container', ready: false, detail: 'Instance stopped' }
          ])
        } else {
          setHealthChecks((current) => [
            { name: 'EC2 instance', ready: normalized === 'RUNNING', detail: `AWS state: ${normalized}` },
            { name: 'SSM agent', ready: data.ssmStatus === 'Online', detail: `Connection: ${data.ssmStatus || 'unknown'}` },
            ...current.filter((item) => item.name === 'MySQL' || item.name === 'Backend container')
          ])
        }

        setLastOperation((current) => (current?.name === 'Start' || current?.name === 'Stop')
          ? { ...current, status: displayState }
          : current)
      } catch (error) {
        if (!cancelled) setHealthChecks((current) => [
          { name: 'Control API', ready: false, detail: error.message || 'Status request failed' },
          ...current.filter((item) => item.name === 'MySQL' || item.name === 'Backend container')
        ])
      }
    }

    pollStatus()
    const timer = window.setInterval(pollStatus, 5000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl, refreshKey])

  const handleRefreshLogs = useCallback(async (sourceOverride) => {
    if (!controlApiUrl) {
      notConnected('Refresh logs')
      return
    }
    if (isRefreshingLogs || snapshotCommandId) return
    const src = sourceOverride || logSource || 'overview'
    try {
      setIsRefreshingLogs(true)
      setLogState(`Requesting SSM log snapshot (${src})`)
      const response = await fetch(`${controlApiUrl}/staging/logs?source=${encodeURIComponent(src)}`, { headers: { Accept: 'application/json' } })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || `Log API returned HTTP ${response.status}`)
      setSnapshotCommandId(data.commandId || '')
      setLogState(data.message || `Collecting logs (${src})`)
      if (!data.commandId) setIsRefreshingLogs(false)
    } catch (error) {
      setIsRefreshingLogs(false)
      setLogState(`Log request failed: ${error.message}`)
    }
  }, [controlApiUrl, isRefreshingLogs, snapshotCommandId, logSource])

  useEffect(() => {
    const commandId = lastOperation?.commandId
    if (!polling || !controlApiUrl || !commandId || ['Success', 'Failed', 'TimedOut', 'Cancelled', 'Undeliverable', 'Terminated'].includes(lastOperation.status)) return undefined
    let cancelled = false
    const pollOperation = async () => {
      try {
        const operation = await readOperation(commandId)
        if (cancelled) return
        setLastOperation((current) => current?.commandId === commandId ? { ...current, status: operation.status } : current)
        applyOperationChecks(operation.checks)
        const output = [operation.stdout?.trim(), operation.stderr?.trim() ? `STDERR:\n${operation.stderr.trim()}` : ''].filter(Boolean).join('\n\n')
        if (output) {
          setLogContent(output)
        } else if (operation.status === 'InProgress' && lastOperation?.name === 'Deploy') {
          setLogContent((current) => current || '[EC2] Lệnh Deploy đang thực thi: đang kéo git branch, nạp môi trường và build Docker Compose... Vui lòng đợi trong giây lát.')
        }
        setLogState(`SSM ${operation.status} · command ${commandId}`)
        if (['Success', 'Failed'].includes(operation.status) && lastOperation?.name === 'Deploy') {
          handleRefreshLogs()
        }
      } catch (error) {
        if (!cancelled) setLogState(`Waiting for SSM output: ${error.message}`)
      }
    }
    pollOperation()
    const timer = window.setInterval(pollOperation, 2500)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl, lastOperation?.commandId, lastOperation?.status, lastOperation?.name, readOperation, applyOperationChecks, handleRefreshLogs])

  useEffect(() => {
    if (!polling || !controlApiUrl || !snapshotCommandId) return undefined
    let cancelled = false
    const pollSnapshot = async () => {
      try {
        const operation = await readOperation(snapshotCommandId)
        if (cancelled) return
        applyOperationChecks(operation.checks)
        const output = [operation.stdout?.trim(), operation.stderr?.trim() ? `STDERR:\n${operation.stderr.trim()}` : ''].filter(Boolean).join('\n\n')
        if (output) setLogContent(output)
        setLogState(`Log snapshot ${operation.status}`)
        if (['Success', 'Failed', 'TimedOut', 'Cancelled', 'Undeliverable', 'Terminated'].includes(operation.status)) {
          setSnapshotCommandId('')
          setIsRefreshingLogs(false)
        }
      } catch (error) {
        if (!cancelled) {
          setLogState(`Log snapshot failed: ${error.message}`)
          setSnapshotCommandId('')
          setIsRefreshingLogs(false)
        }
      }
    }
    pollSnapshot()
    const timer = window.setInterval(pollSnapshot, 2500)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl, snapshotCommandId, readOperation, applyOperationChecks])

  // Safety timer to guarantee isRefreshingLogs never stays stuck
  useEffect(() => {
    if (!isRefreshingLogs) return undefined
    const timer = window.setTimeout(() => {
      setIsRefreshingLogs(false)
      setSnapshotCommandId('')
      setLogState((cur) => cur.includes('Collecting') ? 'Log collection timed out' : cur)
    }, 15000)
    return () => window.clearTimeout(timer)
  }, [isRefreshingLogs])

  // Auto-refresh logs every 5s when enabled and machine is running
  useEffect(() => {
    if (!autoRefreshLogs || !polling || !controlApiUrl || serverStatus !== 'RUNNING') return undefined
    if (isRefreshingLogs || snapshotCommandId) return undefined

    const timer = window.setTimeout(() => {
      handleRefreshLogs()
    }, 5000)
    return () => window.clearTimeout(timer)
  }, [autoRefreshLogs, polling, controlApiUrl, serverStatus, isRefreshingLogs, snapshotCommandId, handleRefreshLogs])

  const handleStart = async (event) => {
    event.preventDefault()
    setShowStart(false)
    if (!controlApiUrl) {
      notConnected('Start')
      return
    }

    try {
      setServerStatus('STARTING')
      onNotice('Đang gửi lệnh khởi động EC2 tới Lambda...')
      const response = await fetch(`${controlApiUrl}/staging/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ owner: currentUser })
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || 'Không thể khởi động qua Lambda')
      setLastOperation({ name: 'Start', status: data.status || 'STARTING', id: data.instanceId || 'ID not returned', branch: gitBranch, at: new Date().toLocaleString() })
      onNotice(data.message || 'EC2 đang trong quá trình khởi động...')
    } catch (err) {
      onNotice(`Lỗi khởi động: ${err.message}`)
      setServerStatus('STOPPED')
    }
  }

  const handleDeploy = async (event) => {
    event.preventDefault()
    setShowDeploy(false)
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
          env: envPayload,
          firebaseConfig: firebaseConfigContent || undefined
        })
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || 'Không thể gửi lệnh deploy tới Lambda')
      setLastOperation({ name: 'Deploy', status: data.status || 'DEPLOYING', id: data.commandId || 'ID not returned', commandId: data.commandId || '', branch: data.branch || gitBranch, at: new Date().toLocaleString() })
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
      setIsStopping(true)
      setServerStatus('STOPPING')
      onNotice('Đang gửi lệnh dừng EC2 và container...')
      const response = await fetch(`${controlApiUrl}/staging/stop`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || 'Không thể dừng qua Lambda')
      setLastOperation({ name: 'Stop', status: data.status || 'STOPPING', id: data.commandId || data.instanceId || 'ID not returned', commandId: data.commandId || '', branch: gitBranch, at: new Date().toLocaleString() })
      onNotice(data.composeStatus && data.composeStatus !== 'Success'
        ? `${data.message}. ${data.composeError || `Compose status: ${data.composeStatus}`}`
        : (data.message || 'Lệnh dừng EC2 đã được gửi thành công.'))
    } catch (err) {
      onNotice(`Lỗi dừng EC2: ${err.message}`)
    } finally {
      setIsStopping(false)
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
                onClick={() => setShowDeploy(true)}
                disabled={isDeploying}
                title="Choose the branch and complete runtime environment, then deploy"
              >
                {isDeploying ? 'Đang deploy...' : 'Deploy / Rebuild ⟳'}
              </button>
              <button
                className="button secondary stop-btn"
                onClick={handleStop}
                disabled={isStopping}
                title="Dừng EC2 và giải phóng containers"
              >
                {isStopping ? 'Stopping...' : 'Stop staging'}
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
              <dt>FE API path</dt>
              <dd><code>/api/* via Vercel proxy</code></dd>
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
            <span className="check-summary">{healthChecks.filter((item) => item.ready === true).length} ready · {healthChecks.filter((item) => item.ready === false).length} attention · {healthChecks.filter((item) => item.ready === null).length} waiting</span>
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
            <button className="text-button" onClick={() => setRefreshKey((key) => key + 1)}>Refresh status ↻</button>
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
            <p className="muted log-status">{logState} · {polling ? 'Trạng thái máy: polling 5s' : 'Polling tắt'} {autoRefreshLogs ? '· Auto-pull: BẬT (5s)' : ''}</p>
          </div>
          <div className="log-actions">
            <select
              value={logSource}
              onChange={(e) => {
                const newSource = e.target.value
                setLogSource(newSource)
                handleRefreshLogs(newSource)
              }}
              className="log-source-select"
              title="Chọn nguồn xem log (Console hoặc File log Spring Boot)"
            >
              <option value="overview">Console & Overview</option>
              <option value="file-all">File: All (info + error)</option>
              <option value="file-error">File: Chỉ lỗi (error.log)</option>
              <option value="file-info">File: Chỉ info (info.log)</option>
            </select>
            <button
              type="button"
              className={`text-button ${autoRefreshLogs ? 'active-toggle' : ''}`}
              onClick={() => setAutoRefreshLogs((value) => !value)}
              title="Tự động kéo snapshot log về mỗi 5 giây khi máy RUNNING"
            >
              {autoRefreshLogs ? 'Auto-pull: BẬT (5s)' : 'Auto-pull: TẮT'}
            </button>
            <button type="button" className="text-button" onClick={() => handleRefreshLogs()} disabled={isRefreshingLogs}>
              {isRefreshingLogs ? 'Collecting...' : 'Refresh logs'}
            </button>
            <button type="button" className="text-button" onClick={() => setPolling((value) => !value)}>
              {polling ? 'Pause polling' : 'Resume polling'}
            </button>
          </div>
        </div>
        <div className="terminal">
          <span className="terminal-cursor">$</span>
          <pre className="terminal-pre">
            {logContent || (logState === 'Control API is not connected' ? 'Logs unavailable until the control API is connected.' : 'Select Refresh logs to collect bootstrap and container output.')}
          </pre>
        </div>
      </section>

      {(showStart || showDeploy) && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && (setShowStart(false), setShowDeploy(false))}>
          <form className="modal" onSubmit={showStart ? handleStart : handleDeploy}>
            <div className="modal-header">
              <div>
                <p className="eyebrow">{showStart ? 'START EC2' : 'DEPLOY APPLICATION'}</p>
                <h2>{showStart ? 'Start staging' : 'Deploy staging'}</h2>
                <p className="muted">{showStart ? 'Starting EC2 does not apply branch or runtime variables.' : 'Deploy pulls the selected branch and replaces the complete runtime environment.'}</p>
              </div>
              <button type="button" className="close-button" onClick={() => (setShowStart(false), setShowDeploy(false))} aria-label="Close">×</button>
            </div>
            <div className="warning-box">
              <span>!</span>
              <p>{showStart ? 'Branch and env are only staged in this page. Click Deploy after EC2 and SSM are ready to apply them.' : 'This replaces the prior runtime env set; an empty env list clears it. Values are not logged.'}</p>
            </div>

            <div className="form-section branch-section">
              <div className="form-section-title">
                <div>
                  <h3>Git Repository Branch</h3>
                  <p className="muted">Nhánh được kéo từ GitHub khi bạn bấm Deploy.</p>
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

            <div className="form-section branch-section">
              <input
                ref={firebaseInputRef}
                type="file"
                accept=".json,application/json"
                style={{ display: 'none' }}
                onChange={handleFirebaseUpload}
              />
              <div className="form-section-title">
                <div>
                  <h3>Firebase Service Account (JSON)</h3>
                  <p className="muted">Đính kèm file cấu hình credentials Firebase Admin (tùy chọn).</p>
                </div>
                <div className="env-actions-group">
                  <button
                    type="button"
                    className="import-button"
                    onClick={() => firebaseInputRef.current?.click()}
                    title="Upload file JSON Firebase"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                    <span>{firebaseFileName ? 'Đổi file JSON' : 'Upload JSON'}</span>
                  </button>
                  {firebaseFileName && (
                    <button type="button" className="text-button text-muted-action" onClick={handleRemoveFirebase}>Gỡ bỏ</button>
                  )}
                </div>
              </div>
              <div className="branch-input-row" style={{ padding: '8px 12px', background: firebaseFileName ? '#f0fdf4' : '#fafafa', borderColor: firebaseFileName ? '#86efac' : '#cfd6df' }}>
                <span style={{ fontSize: '12px', color: firebaseFileName ? '#15803d' : 'var(--dim)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {firebaseFileName ? `✓ Đã đính kèm: ${firebaseFileName}` : 'Chưa có file (Backend sẽ dùng mock credentials nếu không đính kèm)'}
                </span>
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
                  <p className="muted">Full runtime environment for the next Deploy. Import a .env file or enter variables manually.</p>
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
              <button type="button" className="button secondary" onClick={() => (setShowStart(false), setShowDeploy(false))}>Cancel</button>
              <button type="submit" className="button primary">{showStart ? 'Start EC2' : 'Deploy / Rebuild'} <span>→</span></button>
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
