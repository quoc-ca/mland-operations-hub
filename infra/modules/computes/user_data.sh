#!/bin/bash
set -xe

# Ghi log toàn bộ quá trình bootstrap
exec > >(tee -a /var/log/user-data.log) 2>&1

echo "=== [1/5] Cap nhat he thong va cai dat goi co ban ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    jq

echo "=== [2/5] Cai dat Docker Engine va Docker Compose ==="
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

echo "=== [3/5] Kiem tra va kich hoat AWS Systems Manager (SSM) Agent ==="
if snap list | grep -q amazon-ssm-agent; then
    snap start amazon-ssm-agent || true
else
    snap install amazon-ssm-agent --classic
fi
systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service || true
systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service || true

echo "=== [4/5] Khoi tao thu muc staging va script deploy ==="
mkdir -p /opt/mland-staging
chown -R ubuntu:ubuntu /opt/mland-staging

# Tao script deploy tu dong tai /opt/mland-staging-deploy.sh
cat << 'EOF' > /opt/mland-staging-deploy.sh
#!/bin/bash
set -e

BRANCH="${1:-develop}"
DEPLOY_LOG="/var/log/mland-deploy.log"

exec > >(tee -a "${DEPLOY_LOG}") 2>&1
echo "=================================================="
echo " [$(date -u +'%Y-%m-%dT%H:%M:%SZ')] BAT DAU DEPLOY STAGING"
echo " Target branch: ${BRANCH}"
echo "=================================================="

# 1. Clone repository nếu chưa có
if [ ! -d "/opt/mland-staging/.git" ]; then
    echo "Cloning public repository..."
    git clone https://github.com/quoc-ca/mland-operations-hub.git /opt/mland-staging
fi

cd /opt/mland-staging

# 2. Fetch và checkout branch mục tiêu
echo "Fetching origin and checking out branch ${BRANCH}..."
git fetch origin
git checkout "${BRANCH}"
git pull origin "${BRANCH}"

# 3. Cấu hình biến môi trường
if [ -f "/opt/mland-staging/.runtime.env" ]; then
    echo "Applying runtime environment variables..."
    cat /opt/mland-staging/.runtime.env > /opt/mland-staging/.env
fi

# Đảm bảo HOST_BE_PORT=80 để backend bind vào cổng 80 của EC2
if [ -f "/opt/mland-staging/.env" ]; then
    if ! grep -q "^HOST_BE_PORT=" /opt/mland-staging/.env; then
        echo "HOST_BE_PORT=80" >> /opt/mland-staging/.env
    fi
else
    echo "HOST_BE_PORT=80" > /opt/mland-staging/.env
fi

# 4. Khởi chạy Docker Compose cho db và backend từ docker-compose.yml gốc
echo "Building and starting db & backend containers..."
docker compose up -d --build db backend

echo "=================================================="
echo " [$(date -u +'%Y-%m-%dT%H:%M:%SZ')] HOAN TAT DEPLOY STAGING THANH CONG!"
echo "=================================================="
EOF

chmod +x /opt/mland-staging-deploy.sh
chown ubuntu:ubuntu /opt/mland-staging-deploy.sh

# Clone truoc repo ngay trong bootstrap
if [ ! -d "/opt/mland-staging/.git" ]; then
    git clone https://github.com/quoc-ca/mland-operations-hub.git /opt/mland-staging || true
    chown -R ubuntu:ubuntu /opt/mland-staging || true
fi

echo "=== [5/5] Hoan tat bootstrap thanh cong ==="
