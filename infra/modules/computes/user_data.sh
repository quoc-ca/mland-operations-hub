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

echo "=== [4/5] Khoi tao thu muc staging ==="
mkdir -p /opt/mland-staging
chown -R ubuntu:ubuntu /opt/mland-staging

echo "=== [5/5] Hoan tat bootstrap thanh cong ==="
