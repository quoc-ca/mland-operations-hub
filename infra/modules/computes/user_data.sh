#!/bin/bash
set -xeuo pipefail

exec > >(tee -a /var/log/user-data.log) 2>&1

echo "=== [1/6] Update Ubuntu and install deployment prerequisites ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release git jq python3

echo "=== [2/6] Configure 8GB swap space ==="
if ! swapon --show | grep -q '/swapfile'; then
    if [ ! -f /swapfile ]; then
        echo "Allocating 8GB swap file at /swapfile..."
        fallocate -l 8G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=8192
        chmod 600 /swapfile
        mkswap /swapfile
    fi
    swapon /swapfile || true
    if ! grep -q '/swapfile' /etc/fstab; then
        echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
fi

echo "=== [3/6] Install and enable Docker Engine ==="
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" |
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
usermod -aG docker ubuntu

echo "=== [4/6] Enable AWS Systems Manager Agent ==="
if snap list | grep -q amazon-ssm-agent; then
    snap start amazon-ssm-agent || true
else
    snap install amazon-ssm-agent --classic
fi
systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service || true
systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service || true

echo "=== [5/6] Install cloudflared package ==="
install -m 0755 -d /usr/share/keyrings
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg -o /usr/share/keyrings/cloudflare-main.gpg
echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main" > /etc/apt/sources.list.d/cloudflared.list
apt-get update -y
apt-get install -y cloudflared
# Install the remotely managed tunnel token separately on EC2, never in user data.

echo "=== [6/6] Prepare the single-user staging deploy script ==="
install -d -o ubuntu -g ubuntu -m 0750 /opt/mland-staging /opt/mland-staging-runtime
install -o ubuntu -g ubuntu -m 0600 /dev/null /var/log/mland-deploy.log

cat << 'DEPLOY_SCRIPT' > /opt/mland-staging-deploy.sh
#!/bin/bash
set -Eeuo pipefail

BRANCH="${1:-develop}"
DEPLOY_LOG="/var/log/mland-deploy.log"
RUNTIME_ENV="/opt/mland-staging-runtime/runtime-env.json"
REPO_DIR="/opt/mland-staging"

exec > >(tee "${DEPLOY_LOG}") 2>&1
echo "=================================================="
echo " [$(date -u +'%Y-%m-%dT%H:%M:%SZ')] STAGING DEPLOY START"
echo " Target branch: ${BRANCH}"
echo "=================================================="

echo "Verifying 8GB swap configuration..."
if ! swapon --show | grep -q '/swapfile'; then
    if [ ! -f /swapfile ]; then
        echo "Allocating 8GB swap file at /swapfile..."
        sudo fallocate -l 8G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=8192
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
    fi
    sudo swapon /swapfile || true
    if ! grep -q '/swapfile' /etc/fstab; then
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
    fi
fi
free -h

if [[ ! "${BRANCH}" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*$ || "${BRANCH}" == *..* || "${BRANCH}" == */ ]]; then
    echo "Invalid branch name" >&2
    exit 2
fi

if [ ! -f "${RUNTIME_ENV}" ] || [ ! -s "${RUNTIME_ENV}" ]; then
    echo "[ERROR] Missing runtime environment: ${RUNTIME_ENV} does not exist or is empty. Aborting deployment before pull and build." >&2
    exit 1
fi

if [ ! -d "${REPO_DIR}/.git" ]; then
    echo "Cloning repository as $(id -un)..."
    git clone https://github.com/quoc-ca/mland-operations-hub.git "${REPO_DIR}"
fi

cd "${REPO_DIR}"
echo "Fetching branch ${BRANCH}..."
git fetch --prune origin "${BRANCH}"
git checkout -B "${BRANCH}" "origin/${BRANCH}"

echo "Applying the submitted runtime environment"
python3 - "${RUNTIME_ENV}" "${REPO_DIR}/.env" << 'PY_ENV'
import json
import sys

source, target = sys.argv[1:]
with open(source, encoding="utf-8") as stream:
    values = json.load(stream)

def quote_compose_value(value):
    text = str(value)
    text = text.replace("\\", "\\\\").replace('"', '\\"')
    text = text.replace("$", "$$").replace("\r", "\\r").replace("\n", "\\n")
    return f'"{text}"'

with open(target, "w", encoding="utf-8") as stream:
    for key, value in values.items():
        stream.write(f"{key}={quote_compose_value(value)}\n")
PY_ENV
chmod 0600 "${REPO_DIR}/.env"
cp -f "${REPO_DIR}/.env" "${REPO_DIR}/ppswbs_backend/.env"
chmod 0600 "${REPO_DIR}/ppswbs_backend/.env"

if [ -f "/opt/mland-staging-runtime/firebase-service-account.json" ]; then
    echo "Copying Firebase service account credentials..."
    cp -f "/opt/mland-staging-runtime/firebase-service-account.json" "${REPO_DIR}/ppswbs_backend/firebase-service-account.json"
    cp -f "/opt/mland-staging-runtime/firebase-service-account.json" "${REPO_DIR}/firebase-service-account.json"
    chmod 0600 "${REPO_DIR}/ppswbs_backend/firebase-service-account.json"
fi

echo "Building and starting the MySQL and backend containers..."
docker compose build --progress=plain backend
docker compose up -d db backend
echo "Published backend port: $(docker compose port backend 8080)"
if systemctl is-active --quiet cloudflared; then
    echo "Cloudflare Tunnel service is active"
else
    echo "[WARN] Cloudflare Tunnel service is not active; configure its token on EC2" >&2
fi
echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] STAGING DEPLOY COMPLETE"
DEPLOY_SCRIPT

chmod 0750 /opt/mland-staging-deploy.sh
chown ubuntu:ubuntu /opt/mland-staging-deploy.sh

echo "=== [7/7] Install lightweight SSE Log Streamer service ==="
cat << 'LOG_STREAMER' > /opt/mland-log-streamer.py
#!/usr/bin/env python3
"""Lightweight SSE log streamer and container metadata endpoint for staging EC2."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
import glob
import hmac
import json
import os
import select
import subprocess
import threading
import time

BIND_HOST = os.environ.get("LOG_STREAM_BIND", "127.0.0.1")
BIND_PORT = int(os.environ.get("LOG_STREAM_PORT", "9090"))
AUTH_TOKEN = os.environ.get("LOG_STREAM_TOKEN", "").strip()
ALLOWED_ORIGINS = {
    origin.strip()
    for origin in os.environ.get("LOG_STREAM_ALLOWED_ORIGINS", "").split(",")
    if origin.strip()
}
REPO_DIR = os.environ.get("MLAND_REPO_DIR", "/opt/mland-staging")
DEPLOY_LOG = "/var/log/mland-deploy.log"
MAX_CONCURRENT_STREAMS = 6
STREAM_SEM = threading.Semaphore(MAX_CONCURRENT_STREAMS)


def resolve_log_file(source: str):
    if source == "deploy":
        return DEPLOY_LOG
    if source in ("error", "info"):
        filename = "error.log" if source == "error" else "info.log"
        candidates = sorted(glob.glob(f"{REPO_DIR}/ppswbs_backend/logs/*/{filename}"))
        return candidates[-1] if candidates else None
    return None


def inspect_backend_container():
    try:
        cid_proc = subprocess.run(
            ["docker", "compose", "ps", "-aq", "backend"],
            cwd=REPO_DIR,
            capture_output=True,
            text=True,
            timeout=5,
        )
        cid = cid_proc.stdout.strip()
        if not cid:
            return {"exists": False, "status": "not-created"}

        fmt = (
            '{"status":"{{.State.Status}}",'
            '"created":"{{.Created}}",'
            '"startedAt":"{{.State.StartedAt}}",'
            '"finishedAt":"{{.State.FinishedAt}}",'
            '"exitCode":{{.State.ExitCode}},'
            '"oomKilled":{{.State.OOMKilled}}}'
        )
        ins_proc = subprocess.run(
            ["docker", "inspect", "--format", fmt, cid],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if ins_proc.returncode != 0:
            return {"exists": False, "status": "unknown", "error": ins_proc.stderr.strip()}
        data = json.loads(ins_proc.stdout.strip())
        data["exists"] = True
        return data
    except Exception as exc:
        return {"exists": False, "status": "error", "error": str(exc)}


class LogStreamHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        return

    def _cors_headers(self):
        origin = self.headers.get("Origin", "")
        if ALLOWED_ORIGINS:
            if origin in ALLOWED_ORIGINS:
                self.send_header("Access-Control-Allow-Origin", origin)
                self.send_header("Vary", "Origin")
        elif origin:
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")

    def _send_json(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self._cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _is_authorized(self) -> bool:
        if not AUTH_TOKEN:
            return False
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return False
        provided = auth[7:].strip()
        return hmac.compare_digest(provided, AUTH_TOKEN)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors_headers()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/health":
            self._send_json(200, {"ok": True, "configured": bool(AUTH_TOKEN)})
            return

        if not AUTH_TOKEN:
            self._send_json(503, {"error": "LOG_STREAM_TOKEN is not configured on host"})
            return

        if not self._is_authorized():
            self._send_json(401, {"error": "Unauthorized"})
            return

        if path == "/meta":
            self._send_json(200, {
                "backend": inspect_backend_container(),
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            })
            return

        if path == "/stream":
            qs = parse_qs(parsed.query)
            source = (qs.get("source") or [""])[0]
            if source not in ("deploy", "error", "info"):
                self._send_json(400, {"error": "Invalid source. Allowed: deploy, error, info"})
                return

            try:
                lines = max(10, min(500, int((qs.get("lines") or ["200"])[0])))
            except ValueError:
                lines = 200

            if not STREAM_SEM.acquire(blocking=False):
                self._send_json(429, {"error": "Too many active log streams"})
                return

            try:
                self._handle_stream(source, lines)
            finally:
                STREAM_SEM.release()
            return

        self._send_json(404, {"error": "Not found"})

    def _write_sse(self, event: str, data: str):
        payload = f"event: {event}\n"
        for line in data.splitlines() or [""]:
            payload += f"data: {line}\n"
        payload += "\n"
        self.wfile.write(payload.encode("utf-8", errors="replace"))
        self.wfile.flush()

    def _handle_stream(self, source: str, lines: int):
        self.send_response(200)
        self._cors_headers()
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("Connection", "keep-alive")
        self.send_header("X-Accel-Buffering", "no")
        self.end_headers()

        proc = None
        try:
            target = resolve_log_file(source)
            while target is None or not os.path.exists(target):
                self._write_sse("status", f"Waiting for {source} log file...")
                time.sleep(2)
                target = resolve_log_file(source)

            self._write_sse("status", f"Streaming {source}: {os.path.basename(target)}")
            proc = subprocess.Popen(
                ["tail", "-n", str(lines), "-F", target],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                bufsize=0,
            )

            fd = proc.stdout.fileno()
            buffer = b""
            last_ping = time.monotonic()

            while True:
                ready, _, _ = select.select([fd], [], [], 1.0)
                if ready:
                    chunk = os.read(fd, 4096)
                    if not chunk:
                        break
                    buffer += chunk
                    while b"\n" in buffer:
                        line, buffer = buffer.split(b"\n", 1)
                        text = line.decode("utf-8", errors="replace")
                        self.wfile.write(f"data: {text}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    last_ping = time.monotonic()
                elif time.monotonic() - last_ping >= 15.0:
                    self.wfile.write(b": ping\n\n")
                    self.wfile.flush()
                    last_ping = time.monotonic()
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            if proc is not None and proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    proc.kill()


if __name__ == "__main__":
    server = ThreadingHTTPServer((BIND_HOST, BIND_PORT), LogStreamHandler)
    server.daemon_threads = True
    server.serve_forever()
LOG_STREAMER

chmod 0750 /opt/mland-log-streamer.py
chown ubuntu:ubuntu /opt/mland-log-streamer.py

cat << 'LOG_STREAMER_SERVICE' > /etc/systemd/system/mland-log-streamer.service
[Unit]
Description=MLAND Staging SSE Log Streamer
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
Group=ubuntu
EnvironmentFile=-/opt/mland-staging-runtime/log-streamer.env
ExecStart=/usr/bin/python3 /opt/mland-log-streamer.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
LOG_STREAMER_SERVICE

systemctl daemon-reload
systemctl enable --now mland-log-streamer

if [ ! -d /opt/mland-staging/.git ]; then
    sudo -H -u ubuntu -- git clone https://github.com/quoc-ca/mland-operations-hub.git /opt/mland-staging || \
        echo "Repository clone deferred until the first Deploy action"
fi

echo "Bootstrap complete; application containers are not started until Deploy is requested."
