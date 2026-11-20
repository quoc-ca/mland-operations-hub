import { useEffect, useRef, useState } from 'react'
import './App.css'

const AUTH_KEY = 'infra-management-auth'
const initialEnv = [{ key: '', value: '' }]
const checks = [['EC2 instance', 'AWS state is not available'], ['SSM agent', 'Connection is not available'], ['MySQL', 'Health check is not available'], ['Backend API', 'Health check is not available'], ['Caddy / HTTPS', 'Domain check is not available']]

function StatusPill({ status }) { return <span className="status-pill">{status}</span> }

function Login({ onLogin }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const submit = (event) => {
    event.preventDefault()
    if (email === 'a@gmail.com' && password === '123123123') { sessionStorage.setItem(AUTH_KEY, 'demo-member'); onLogin(); return }
    setError('Invalid demo credentials. Use a@gmail.com / 123123123.')
  }
  return <main className="login-shell"><div className="login-card"><div className="brand-mark large">M</div><p className="eyebrow">MLAND / PLATFORM OPERATIONS</p><h1>Staging control room</h1><p className="login-copy">Sign in to manage the shared staging environment.</p><form onSubmit={submit} className="login-form"><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" autoComplete="email" required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter password" autoComplete="current-password" required /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="button primary full" type="submit">Sign in</button></form><div className="dev-note"><strong>Development login</strong><span>Google Sign-In only will replace this test form in production.</span></div></div></main>
}

function StagingPage({ onNotice }) {
  const [showStart, setShowStart] = useState(false)
  const [envRows, setEnvRows] = useState(initialEnv)
  const [polling, setPolling] = useState(true)
  const [logState, setLogState] = useState('Waiting for API connection')
  const updateRow = (index, field, value) => setEnvRows((rows) => rows.map((row, i) => i === index ? { ...row, [field]: value } : row))
  const addRow = () => setEnvRows((rows) => [...rows, { key: '', value: '' }])
  const removeRow = (index) => setEnvRows((rows) => rows.length === 1 ? initialEnv : rows.filter((_, i) => i !== index))
  const notConnected = (action) => onNotice(`${action} is unavailable: Control API is not connected yet.`)
  useEffect(() => {
    if (!polling) return undefined
    let cancelled = false
    const pollLogs = async () => {
      try {
        const response = await fetch('/api/staging/logs', { headers: { Accept: 'application/json' } })
        if (!response.ok) throw new Error('Control API is not connected')
        if (!cancelled) setLogState('Log snapshot updated')
      } catch {
        if (!cancelled) setLogState('Control API is not connected')
      }
    }
    pollLogs()
    const timer = window.setInterval(pollLogs, 5000)
    return () => { cancelled = true; window.clearInterval(timer) }
  }, [polling])
  const handleStart = (event) => { event.preventDefault(); setShowStart(false); notConnected('Start') }
  return <>
    <section className="page-intro"><div><p className="breadcrumb">Environments <span>/</span> staging</p><div className="title-line"><h2>Staging environment</h2><StatusPill status="STOPPED" /></div><p className="muted">One shared EC2 environment for the team. Start it when you need it, stop it when you are done.</p></div><div className="role-preview">Signed in as member</div></section>
    <section className="hero-card"><div className="hero-status"><div className="status-orb orb-stopped"><span /></div><div><p className="eyebrow">CURRENT STATE</p><h3>Environment is stopped</h3><p className="muted">No EC2 compute charges while stopped. EBS and public IP charges may still apply.</p></div></div><div className="hero-actions"><button className="button primary" onClick={() => setShowStart(true)}>Start staging</button><p className="action-hint">No active owner. The first successful start claims the lock.</p></div></section>
    <div className="content-grid"><section className="panel"><div className="panel-heading"><div><p className="eyebrow">RESOURCE</p><h3>Runtime details</h3></div><span className="panel-icon">◈</span></div><dl className="details-list"><div><dt>Owner</dt><dd className="empty-value">No active owner</dd></div><div><dt>Session ID</dt><dd className="empty-value">—</dd></div><div><dt>Endpoint</dt><dd><code>api-staging.example.com</code></dd></div><div><dt>Region</dt><dd><code>ap-southeast-1</code></dd></div><div><dt>Last operation</dt><dd className="empty-value">No operation recorded</dd></div></dl></section><section className="panel"><div className="panel-heading"><div><p className="eyebrow">READINESS</p><h3>Health checks</h3></div><span className="check-summary">0 / 5 ready</span></div><div className="check-list">{checks.map(([name, message]) => <div className="check-row" key={name}><span className="check-state">—</span><div><strong>{name}</strong><small>{message}</small></div></div>)}</div></section></div>
    <div className="content-grid lower-grid"><section className="panel"><div className="panel-heading"><div><p className="eyebrow">OPERATIONS</p><h3>Lifecycle timeline</h3></div><button className="text-button" onClick={() => notConnected('Refresh')}>Refresh ↻</button></div><div className="empty-state"><div className="empty-icon">◷</div><strong>No operations yet</strong><p>Start an operation to see progress, operation ID and recovery state here.</p></div></section></div><section className="panel logs-panel full-width"><div className="panel-heading"><div><p className="eyebrow">OBSERVABILITY</p><h3>Log snapshot</h3><p className="muted log-status">{logState} · {polling ? 'polling every 5s' : 'polling paused'}</p></div><div className="log-actions"><span className="tail-label">tail 200</span><button className="text-button" onClick={() => setPolling((value) => !value)}>{polling ? 'Pause polling' : 'Resume polling'}</button></div></div><div className="terminal"><span className="terminal-cursor">$</span><span>{logState === 'Control API is not connected' ? 'Logs unavailable until the control API is connected.' : 'Waiting for the first log snapshot...'}</span></div></section>
    {showStart && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setShowStart(false)}><form className="modal" onSubmit={handleStart}><div className="modal-header"><div><p className="eyebrow">CLAIM & START</p><h2>Start staging</h2><p className="muted">You will become the owner of this staging session.</p></div><button type="button" className="close-button" onClick={() => setShowStart(false)} aria-label="Close">×</button></div><div className="warning-box"><span>!</span><p>Runtime values are sent to the control API and written to the EC2 runtime environment. They are not stored in this browser or returned in logs.</p></div><div className="form-section"><div className="form-section-title"><div><h3>Runtime environment</h3><p className="muted">Optional key/value pairs, like a deployment environment.</p></div><button type="button" className="text-button" onClick={addRow}>+ Add variable</button></div><div className="env-table"><div className="env-head"><span>KEY</span><span>VALUE</span><span /></div>{envRows.map((row, index) => <div className="env-row" key={index}><input aria-label={`Variable key ${index + 1}`} value={row.key} onChange={(event) => updateRow(index, 'key', event.target.value)} placeholder="DATABASE_URL" /><input aria-label={`Variable value ${index + 1}`} type="password" value={row.value} onChange={(event) => updateRow(index, 'value', event.target.value)} placeholder="Enter value" /><button type="button" className="remove-button" onClick={() => removeRow(index)} aria-label="Remove variable">×</button></div>)}</div></div><div className="modal-footer"><button type="button" className="button secondary" onClick={() => setShowStart(false)}>Cancel</button><button type="submit" className="button primary">Confirm start <span>→</span></button></div></form></div>}
  </>
}

function ArchitecturePage() {
  const frameRef = useRef(null)
  const [diagramError, setDiagramError] = useState('')
  useEffect(() => {
    let xml = ''
    fetch('/architecture/current-deployment.drawio').then((response) => {
      if (!response.ok) throw new Error('Diagram file could not be loaded')
      return response.text()
    }).then((text) => {
      xml = text
      frameRef.current?.contentWindow?.postMessage(JSON.stringify({ action: 'load', xml }), '*')
    }).catch((error) => setDiagramError(error.message))
    const onMessage = (event) => {
      if (event.source !== frameRef.current?.contentWindow) return
      try {
        const message = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
        if (message.event === 'init' && xml) frameRef.current.contentWindow.postMessage(JSON.stringify({ action: 'load', xml }), '*')
      } catch { /* viewer can send non-JSON messages */ }
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])
  return <section className="architecture-page"><div className="architecture-heading"><div><p className="eyebrow">SYSTEM MAP</p><h2>Current deployment architecture</h2><p className="muted">Fixed Draw.io source · view and zoom only.</p></div><span className="file-badge">architecture/current-deployment.drawio</span></div><div className="diagram-frame">{diagramError ? <div className="diagram-placeholder"><span className="empty-icon">!</span><strong>Diagram could not be loaded</strong><p>{diagramError}</p></div> : <iframe ref={frameRef} title="Current deployment architecture" src="https://viewer.diagrams.net/?embed=1&proto=json&spin=1&libraries=1&readOnly=1" className="diagram-iframe" />}</div></section>
}

function App() {
  const [authenticated, setAuthenticated] = useState(() => sessionStorage.getItem(AUTH_KEY) === 'demo-member')
  const [activeTab, setActiveTab] = useState('staging')
  const [notice, setNotice] = useState('Control API is not connected')
  const logout = () => { sessionStorage.removeItem(AUTH_KEY); setAuthenticated(false) }
  if (!authenticated) return <Login onLogin={() => setAuthenticated(true)} />
  return <main className="app-shell"><header className="topbar"><div className="brand-block"><div className="brand-mark">M</div><div><p className="eyebrow">MLAND / PLATFORM OPERATIONS</p><h1>Staging control room</h1></div></div><div className="topbar-actions"><span className="connection-dot" /><span>API disconnected</span><span className="avatar">M</span><button className="logout-button" onClick={logout}>Log out</button></div></header><nav className="tabs" aria-label="Main navigation"><button className={activeTab === 'staging' ? 'active' : ''} onClick={() => setActiveTab('staging')}>Staging control</button><button className={activeTab === 'architecture' ? 'active' : ''} onClick={() => setActiveTab('architecture')}>Architecture</button></nav>{notice && <div className="notice" role="status"><span className="notice-icon">i</span><span>{notice}</span><button onClick={() => setNotice('')}>Dismiss</button></div>}{activeTab === 'staging' ? <StagingPage onNotice={setNotice} /> : <ArchitecturePage />}<footer className="footer"><span>Managed by Mland Platform</span><span>Secrets are never stored in this browser</span><span>v0.2 · MVP console</span></footer></main>
}

export default App
