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

const LOG_TABS = [
  { id: 'deploy', label: 'Deploy', ssmSource: 'overview', hint: 'mland-deploy.log' },
  { id: 'error', label: 'BE Error', ssmSource: 'file-error', hint: 'error.log' },
  { id: 'info', label: 'BE Info', ssmSource: 'file-info', hint: 'info.log' }
]

const MAX_LOG_LINES = 1500

function formatContainerTime(isoString) {
  if (!isoString || isoString.startsWith('0001-')) return '—'
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return isoString
  return date.toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
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
  const [backendMeta, setBackendMeta] = useState(null)
  const [lastOperation, setLastOperation] = useState(null)
  const [snapshotCommandId, setSnapshotCommandId] = useState('')
  const [isRefreshingLogs, setIsRefreshingLogs] = useState(false)
  const [liveStreamEnabled, setLiveStreamEnabled] = useState(true)
  const [autoScroll, setAutoScroll] = useState(true)
  const [logSource, setLogSource] = useState('deploy')
  const [activeLogSource, setActiveLogSource] = useState('overview')
  const [logOffsets, setLogOffsets] = useState({
    overview: 0,
    'file-error': 0,
    'file-info': 0
  })
  const [cooldown, setCooldown] = useState(0)
  const [isStopping, setIsStopping] = useState(false)
  const [isDeploying, setIsDeploying] = useState(false)
  const [firebaseFileName, setFirebaseFileName] = useState(() => localStorage.getItem('infra-firebase-filename') || '')
  const [firebaseConfigContent, setFirebaseConfigContent] = useState(() => localStorage.getItem('infra-firebase-content') || '')
  const fileInputRef = useRef(null)
  const firebaseInputRef = useRef(null)
  const terminalPreRef = useRef(null)

  const controlApiUrl = (import.meta.env.VITE_CONTROL_API_URL || '').replace(/\/$/, '')
  const logStreamUrl = (import.meta.env.VITE_LOG_STREAM_URL || '').replace(/\/$/, '')
  const logStreamToken = (import.meta.env.VITE_LOG_STREAM_TOKEN || '').trim()
  const hasStreamConfig = Boolean(logStreamUrl && logStreamToken)

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

  const currentLogTab = LOG_TABS.find((tab) => tab.id === logSource) || LOG_TABS[0]

  const appendLogLines = useCallback((lines) => {
    if (!lines.length) return
    setLogContent((prev) => {
      const existing = prev ? prev.split('\n') : []
      const merged = existing.concat(lines)
      return merged.slice(-MAX_LOG_LINES).join('\n')
    })
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
          setBackendMeta(null)
        }

        setLastOperation((current) => (current?.name === 'Start' || current?.name === 'Stop')
          ? { ...current, status: displayState }
          : current)
      } catch {
        // Ignore transient status polling failures
      }
    }

    pollStatus()
    const timer = window.setInterval(pollStatus, 5000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [polling, controlApiUrl])

  // Poll backend container metadata (last deploy / created / startedAt / status) via log streamer
  useEffect(() => {
    if (!hasStreamConfig || !polling || serverStatus !== 'RUNNING') return undefined
    let cancelled = false

    const fetchMeta = async () => {
      try {
        const response = await fetch(`${logStreamUrl}/meta`, {
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${logStreamToken}`
          }
        })
        if (!response.ok) return
        const data = await response.json().catch(() => null)
        if (!cancelled && data?.backend) {
          setBackendMeta(data.backend)
        }
      } catch {
        // Ignore transient meta fetch failures while tunnel or service starts
      }
    }

    fetchMeta()
    const timer = window.setInterval(fetchMeta, 10000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [hasStreamConfig, logStreamUrl, logStreamToken, polling, serverStatus])

  // Live SSE log streaming for selected tab (deploy | error | info)
  useEffect(() => {
    if (!hasStreamConfig || !liveStreamEnabled || serverStatus !== 'RUNNING') return undefined

    const controller = new AbortController()
    let active = true

    const connectStream = async () => {
      try {
        const response = await fetch(
          `${logStreamUrl}/stream?source=${encodeURIComponent(currentLogTab.id)}&lines=200`,
          {
            headers: {
              Accept: 'text/event-stream',
              Authorization: `Bearer ${logStreamToken}`
            },
            signal: controller.signal
          }
        )
        if (!response.ok || !response.body) {
          throw new Error(`SSE HTTP ${response.status}`)
        }
        if (active) {
          setLogContent('')
          setLogState(`Live SSE · Đang stream ${currentLogTab.label}...`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        let currentEvent = 'message'
        let dataLines = []
        const pendingLines = []

        const flushEvent = () => {
          if (!dataLines.length) {
            currentEvent = 'message'
            return
          }
          const text = dataLines.join('\n')
          if (currentEvent === 'status') {
            setLogState(`Live SSE · ${text}`)
          } else if (text) {
            pendingLines.push(text)
          }
          currentEvent = 'message'
          dataLines = []
        }

        while (active) {
          const { value, done } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const rawLines = buffer.split(/\r?\n/)
          buffer = rawLines.pop() || ''

          for (const line of rawLines) {
            if (line === '') {
              flushEvent()
            } else if (line.startsWith(':')) {
              // SSE heartbeat comment
            } else if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              dataLines.push(line.slice(5).replace(/^ /, ''))
            }
          }

          if (pendingLines.length) {
            appendLogLines(pendingLines.splice(0, pendingLines.length))
          }
        }
      } catch (error) {
        if (active && error.name !== 'AbortError') {
          setLogState(`SSE ngắt kết nối (${error.message}) — có thể dùng SSM Snapshot`)
        }
      }
    }

    connectStream()
    return () => {
      active = false
      controller.abort()
    }
  }, [hasStreamConfig, logStreamUrl, logStreamToken, liveStreamEnabled, serverStatus, currentLogTab.id, currentLogTab.label, appendLogLines])

  // Auto-scroll terminal when new log lines arrive
  useEffect(() => {
    if (!autoScroll || !terminalPreRef.current) return
    terminalPreRef.current.scrollTop = terminalPreRef.current.scrollHeight
  }, [logContent, autoScroll])

  // Cooldown countdown timer (15s) for SSM fallback
  useEffect(() => {
    if (cooldown <= 0) return undefined
    const timer = window.setInterval(() => {
      setCooldown((prev) => Math.max(0, prev - 1))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [cooldown])

  const handleRefreshLogs = useCallback(async (sourceOverride, isFinalPull = false) => {
    if (!controlApiUrl) {
      notConnected('Refresh logs')
      return
    }
    if (isRefreshingLogs || snapshotCommandId) return
    if (!isFinalPull && cooldown > 0) return

    const src = sourceOverride || currentLogTab.ssmSource || 'overview'
    const offset = logOffsets[src] || 0
    try {
      setIsRefreshingLogs(true)
      setActiveLogSource(src)
      setLogState(`Đang yêu cầu SSM snapshot (${src}, offset: ${offset} bytes)...`)
      const response = await fetch(`${controlApiUrl}/staging/logs?source=${encodeURIComponent(src)}&offset=${encodeURIComponent(offset)}`, { headers: { Accept: 'application/json' } })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || `Log API returned HTTP ${response.status}`)
      setSnapshotCommandId(data.commandId || '')
      setLogState(data.message || `Đang gom log từ host (${src})`)
      if (!isFinalPull) {
        setCooldown(15)
      }
      if (!data.commandId) setIsRefreshingLogs(false)
    } catch (error) {
      setIsRefreshingLogs(false)
      setLogState(`Lỗi yêu cầu log: ${error.message}`)
    }
  }, [controlApiUrl, isRefreshingLogs, snapshotCommandId, currentLogTab.ssmSource, logOffsets, cooldown])

  useEffect(() => {
    const commandId = lastOperation?.commandId
    if (!polling || !controlApiUrl || !commandId || ['Success', 'Failed', 'TimedOut', 'Cancelled', 'Undeliverable', 'Terminated'].includes(lastOperation.status)) return undefined
    let cancelled = false
    const pollOperation = async () => {
      try {
        const operation = await readOperation(commandId)
        if (cancelled) return
        setLastOperation((current) => current?.commandId === commandId ? { ...current, status: operation.status } : current)
        if (lastOperation?.name === 'Deploy' && operation.status === 'Failed' && operation.stderr?.trim()) {
          setLogContent((prev) => `${prev}\n\n[DEPLOY THẤT BẠI]\n${operation.stderr.trim()}`)
        }
        if (['Success', 'Failed'].includes(operation.status) && lastOperation?.name === 'Deploy') {
          if (!hasStreamConfig || !liveStreamEnabled) {
            handleRefreshLogs('overview', true)
          }
          setCooldown(0)
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
  }, [polling, controlApiUrl, lastOperation?.commandId, lastOperation?.status, lastOperation?.name, readOperation, handleRefreshLogs, hasStreamConfig, liveStreamEnabled])

  useEffect(() => {
    if (!polling || !controlApiUrl || !snapshotCommandId) return undefined
    let cancelled = false
    const pollSnapshot = async () => {
      try {
        const operation = await readOperation(snapshotCommandId)
        if (cancelled) return
        const output = [operation.stdout?.trim(), operation.stderr?.trim() ? `STDERR:\n${operation.stderr.trim()}` : ''].filter(Boolean).join('\n\n')
        if (output) {
          const currentOffset = logOffsets[activeLogSource] || 0
          if (currentOffset === 0) {
            setLogContent(output)
          } else {
            setLogContent((prev) => (prev ? `${prev}\n${output}` : output))
          }
        }
        if (typeof operation.nextOffset === 'number' && !Number.isNaN(operation.nextOffset)) {
          setLogOffsets((prev) => ({ ...prev, [activeLogSource]: operation.nextOffset }))
        }
        const hasMoreText = operation.hasMore ? ` (còn ${operation.fileSize - operation.nextOffset} bytes mới)` : ''
        setLogState(`SSM Snapshot ${operation.status} · ${activeLogSource} offset: ${operation.nextOffset ?? logOffsets[activeLogSource] ?? 0} bytes${hasMoreText}`)
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
  }, [polling, controlApiUrl, snapshotCommandId, readOperation, logOffsets, activeLogSource])

  // Safety timer to guarantee isRefreshingLogs never stays stuck
  useEffect(() => {
    if (!isRefreshingLogs) return undefined
    const timer = window.setTimeout(() => {
      setIsRefreshingLogs(false)
      setSnapshotCommandId('')
      setLogState((cur) => cur.includes('Collecting') || cur.includes('Đang') ? 'Log collection timed out' : cur)
    }, 15000)
    return () => window.clearTimeout(timer)
  }, [isRefreshingLogs])

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
      setLogOffsets({ overview: 0, 'file-error': 0, 'file-info': 0 })
      setLogSource('deploy')
      setActiveLogSource('overview')
      setCooldown(0)
      setLogContent('[EC2] Khởi động phiên Deploy mới...\n')
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

      <section className="panel full-width">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">RESOURCE</p>
            <h3>Runtime details</h3>
          </div>
          <span className="panel-icon">◈</span>
        </div>
        <dl className="details-list runtime-details-grid">
          <div>
            <dt>Owner</dt>
            <dd>{currentUser}</dd>
          </div>
          <div>
            <dt>Active Branch</dt>
            <dd><code>{gitBranch}</code></dd>
          </div>
          <div>
            <dt>Last Container Deploy</dt>
            <dd>
              {serverStatus !== 'RUNNING' ? (
                <span className="empty-value">EC2 is {serverStatus.toLowerCase()}</span>
              ) : !hasStreamConfig ? (
                <span className="empty-value">Log streamer not configured</span>
              ) : backendMeta?.exists ? (
                <>
                  <code>{formatContainerTime(backendMeta.created || backendMeta.startedAt)}</code>
                  <span className={`container-meta-badge ${backendMeta.status === 'running' ? 'running' : 'dead'}`}>
                    {backendMeta.status}
                    {backendMeta.status !== 'running' && typeof backendMeta.exitCode === 'number' ? ` (code ${backendMeta.exitCode})` : ''}
                    {backendMeta.oomKilled ? ' · OOMKilled' : ''}
                  </span>
                </>
              ) : backendMeta ? (
                <span className="empty-value">{backendMeta.status || 'not-created'}</span>
              ) : (
                <span className="empty-value">Đang kiểm tra container...</span>
              )}
            </dd>
          </div>
          <div>
            <dt>Backend Public IP</dt>
            <dd>
              {serverIp ? <code>{serverIp}</code> : <span className="empty-value">No public IP available</span>}
            </dd>
          </div>
          <div>
            <dt>FE API path</dt>
            <dd><code>/api/* via Vercel proxy</code></dd>
          </div>
          <div>
            <dt>Lambda Control</dt>
            <dd>{controlApiUrl ? <code>Connected</code> : <span className="empty-value">Not configured</span>}</dd>
          </div>
        </dl>
      </section>

      <section className="panel logs-panel full-width">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">OBSERVABILITY</p>
            <h3>Live logs</h3>
            <p className="muted log-status">
              {hasStreamConfig && liveStreamEnabled && serverStatus === 'RUNNING' ? (
                <span className="live-badge"><span className="live-dot" />{logState}</span>
              ) : logState}
              {' · '}
              {polling ? 'EC2 status: polling 5s' : 'Polling tắt'}
            </p>
          </div>
          <div className="log-actions">
            <div className="log-subtabs" role="tablist" aria-label="Log sources">
              {LOG_TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={logSource === tab.id}
                  className={`log-subtab ${logSource === tab.id ? 'active' : ''}`}
                  onClick={() => {
                    setLogSource(tab.id)
                    if (!hasStreamConfig || !liveStreamEnabled) {
                      handleRefreshLogs(tab.ssmSource, true)
                    }
                  }}
                  title={tab.hint}
                >
                  {tab.label}
                </button>
              ))}
            </div>
            {hasStreamConfig && (
              <button
                type="button"
                className={`text-button ${liveStreamEnabled ? 'active-toggle' : ''}`}
                onClick={() => setLiveStreamEnabled((value) => !value)}
                title="Bật/tắt stream log realtime qua SSE"
              >
                {liveStreamEnabled ? 'Live SSE: BẬT' : 'Live SSE: TẮT'}
              </button>
            )}
            <button
              type="button"
              className={`text-button ${autoScroll ? 'active-toggle' : ''}`}
              onClick={() => setAutoScroll((value) => !value)}
              title="Tự động cuộn xuống dòng log mới nhất"
            >
              {autoScroll ? 'Auto-scroll: BẬT' : 'Auto-scroll: TẮT'}
            </button>
            <button
              type="button"
              className={`text-button ${cooldown > 0 ? 'cooldown-active' : ''}`}
              onClick={() => handleRefreshLogs(currentLogTab.ssmSource)}
              disabled={isRefreshingLogs || cooldown > 0}
              title={cooldown > 0 ? `Vui lòng đợi ${cooldown}s trước khi lấy snapshot tiếp theo` : 'Fallback: lấy log snapshot qua SSM'}
            >
              {isRefreshingLogs ? 'Collecting...' : cooldown > 0 ? `SSM (${cooldown}s)` : 'SSM Snapshot ↻'}
            </button>
            <button
              type="button"
              className="text-button text-muted-action"
              onClick={() => {
                setLogOffsets((prev) => ({ ...prev, [currentLogTab.ssmSource]: 0 }))
                setLogContent('')
              }}
              title="Xóa nội dung terminal hiện tại"
            >
              Clear
            </button>
            <button type="button" className="text-button" onClick={() => setPolling((value) => !value)}>
              {polling ? 'Pause polling' : 'Resume polling'}
            </button>
          </div>
        </div>
        <div className="terminal">
          <span className="terminal-cursor">$</span>
          <pre className="terminal-pre" ref={terminalPreRef}>
            {logContent || (
              serverStatus !== 'RUNNING'
                ? `EC2 is ${serverStatus.toLowerCase()}. Start EC2 to stream live logs.`
                : hasStreamConfig
                  ? 'Waiting for log stream...'
                  : 'Click SSM Snapshot to collect logs, or configure VITE_LOG_STREAM_URL & VITE_LOG_STREAM_TOKEN for realtime SSE.'
            )}
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
