#!/bin/bash
set -xeuo pipefail

exec > >(tee -a /var/log/user-data.log) 2>&1

echo "=== [1/4] Update Ubuntu and install deployment prerequisites ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release git jq python3

echo "=== [2/4] Install and enable Docker Engine ==="
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

echo "=== [3/4] Enable AWS Systems Manager Agent ==="
if snap list | grep -q amazon-ssm-agent; then
    snap start amazon-ssm-agent || true
else
    snap install amazon-ssm-agent --classic
fi
systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service || true
systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service || true

echo "=== [4/4] Prepare the single-user staging deploy script ==="
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

values.setdefault("HOST_BE_PORT", "80")

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

echo "Cleaning old backend logs for a fresh deployment session..."
rm -rf "${REPO_DIR}/ppswbs_backend/logs"/* 2>/dev/null || true

echo "Building and starting the MySQL and backend containers..."
docker compose build --progress=plain backend
docker compose up -d db backend
echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] STAGING DEPLOY COMPLETE"
DEPLOY_SCRIPT

chmod 0750 /opt/mland-staging-deploy.sh
chown ubuntu:ubuntu /opt/mland-staging-deploy.sh

if [ ! -d /opt/mland-staging/.git ]; then
    sudo -H -u ubuntu -- git clone https://github.com/quoc-ca/mland-operations-hub.git /opt/mland-staging || \
        echo "Repository clone deferred until the first Deploy action"
fi

echo "Bootstrap complete; application containers are not started until Deploy is requested."
