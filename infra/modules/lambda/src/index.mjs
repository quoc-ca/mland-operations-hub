import { EC2Client, StartInstancesCommand, StopInstancesCommand, DescribeInstancesCommand } from "@aws-sdk/client-ec2";
import { SSMClient, SendCommandCommand, GetCommandInvocationCommand, DescribeInstanceInformationCommand } from "@aws-sdk/client-ssm";

const region = process.env.AWS_REGION || "ap-southeast-1";
const getEc2InstanceId = () => process.env.STAGING_EC2_ID || "";

const ec2 = new EC2Client({ region });
const ssm = new SSMClient({ region });

// CORS is handled by the Lambda Function URL configuration in Terraform.
const responseHeaders = { "Content-Type": "application/json" };

export const handler = async (event) => {
  const ec2InstanceId = getEc2InstanceId();
  const headers = responseHeaders;

  const path = event.rawPath || event.path || "";
  const method = event.requestContext?.http?.method || event.httpMethod || "GET";

  try {
    // 1. Lấy trạng thái máy chủ EC2 (GET /staging/status)
    if (method === "GET" && path.endsWith("/status")) {
      const result = await ec2.send(new DescribeInstancesCommand({ InstanceIds: [ec2InstanceId] }));
      const instance = result.Reservations?.[0]?.Instances?.[0];
      const state = instance?.State?.Name || "unknown";
      const publicIp = instance?.PublicIpAddress || null;
      let ssmStatus = "not-applicable";
      if (state === "running") {
        const managed = await ssm.send(new DescribeInstanceInformationCommand({
          Filters: [{ Key: "InstanceIds", Values: [ec2InstanceId] }]
        }));
        ssmStatus = managed.InstanceInformationList?.[0]?.PingStatus || "offline";
      }
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          instanceId: ec2InstanceId,
          state,
          publicIp,
          ssmStatus,
          timestamp: new Date().toISOString()
        })
      };
    }

    // 2. Khởi động môi trường Staging (POST /staging/start)
    if (method === "POST" && path.endsWith("/start")) {
      let body = {};
      try {
        body = typeof event.body === "string" ? JSON.parse(event.body || "{}") : (event.body || {});
      } catch (e) {
        console.warn("Loi parse JSON body trong /staging/start:", e.message);
      }

      const branch = (body?.branch || "develop").trim();
      const runtimeEnv = body?.env && typeof body.env === "object" ? body.env : {};
      const envKeys = Object.keys(runtimeEnv);

      if (envKeys.length > 0) {
        console.log(`[START] Nhan duoc ${envKeys.length} bien moi truong cho EC2:`, envKeys.join(", "));
      }

      await ec2.send(new StartInstancesCommand({ InstanceIds: [ec2InstanceId] }));
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          message: envKeys.length > 0
            ? `Lệnh khởi động EC2 đã được gửi thành công (đã nhận ${envKeys.length} biến môi trường, branch: ${branch})`
            : `Lệnh khởi động EC2 đã được gửi thành công (branch: ${branch})`,
          instanceId: ec2InstanceId,
          status: "STARTING",
          branch,
          envCount: envKeys.length
        })
      };
    }

    // 3. Kéo code từ GitHub và build/deploy Staging (POST /staging/deploy)
    if (method === "POST" && path.endsWith("/deploy")) {
      let body = {};
      try {
        body = typeof event.body === "string" ? JSON.parse(event.body || "{}") : (event.body || {});
      } catch (e) {
        console.warn("Loi parse JSON body trong /staging/deploy:", e.message);
      }

      const branch = (body?.branch || "develop").trim().replace(/[^a-zA-Z0-9_\-\/]/g, "");
      const runtimeEnv = body?.env && typeof body.env === "object" ? body.env : {};
      const envKeys = Object.keys(runtimeEnv);

      const commands = [
        "mkdir -p /opt/mland-staging"
      ];

      if (envKeys.length > 0) {
        const envContent = envKeys.map((k) => `${k}=${runtimeEnv[k]}`).join("\n");
        const b64 = Buffer.from(envContent).toString("base64");
        commands.push(`echo "${b64}" | base64 -d > /opt/mland-staging/.runtime.env`);
        console.log(`[DEPLOY] Da cap nhat ${envKeys.length} bien moi truong cho branch: ${branch}`);
      }

      // Đảm bảo script deploy tồn tại trên máy chủ
      commands.push(`if [ ! -f /opt/mland-staging-deploy.sh ]; then
cat << 'EOF' > /opt/mland-staging-deploy.sh
#!/bin/bash
set -e
BRANCH="\${1:-develop}"
DEPLOY_LOG="/var/log/mland-deploy.log"
exec > >(tee -a "\${DEPLOY_LOG}") 2>&1
echo "=== BAT DAU DEPLOY STAGING (\${BRANCH}) ==="
if [ ! -d "/opt/mland-staging/.git" ]; then
    sudo -u ubuntu git clone https://github.com/quoc-ca/mland-operations-hub.git /opt/mland-staging
fi
chown -R ubuntu:ubuntu /opt/mland-staging
cd /opt/mland-staging
git -c safe.directory=/opt/mland-staging fetch origin
git -c safe.directory=/opt/mland-staging checkout "\${BRANCH}"
git -c safe.directory=/opt/mland-staging pull origin "\${BRANCH}"
chown -R ubuntu:ubuntu /opt/mland-staging
if [ -f "/opt/mland-staging/.runtime.env" ]; then
    cat /opt/mland-staging/.runtime.env > /opt/mland-staging/.env
fi
if [ -f "/opt/mland-staging/.env" ]; then
    if ! grep -q "^HOST_BE_PORT=" /opt/mland-staging/.env; then
        echo "HOST_BE_PORT=80" >> /opt/mland-staging/.env
    fi
else
    echo "HOST_BE_PORT=80" > /opt/mland-staging/.env
fi
docker compose up -d --build db backend
echo "=== HOAN TAT DEPLOY STAGING THANH CONG! ==="
EOF
chmod +x /opt/mland-staging-deploy.sh
fi`);

      commands.push("chown -R ubuntu:ubuntu /opt/mland-staging")
      commands.push(`/bin/bash /opt/mland-staging-deploy.sh "${branch}"`);

      const sendCmd = await ssm.send(new SendCommandCommand({
        InstanceIds: [ec2InstanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: { commands }
      }));

      const commandId = sendCmd.Command?.CommandId || "unknown";

      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          message: `Lệnh kéo code (${branch}) và build backend đang chạy ngầm trên EC2`,
          instanceId: ec2InstanceId,
          commandId,
          status: "DEPLOYING",
          branch,
          envCount: envKeys.length
        })
      };
    }

    // 4. Dừng môi trường Staging (POST /staging/stop)
    if (method === "POST" && path.endsWith("/stop")) {
      // Gửi lệnh docker compose down qua SSM trước khi tắt máy
      try {
        await ssm.send(new SendCommandCommand({
          InstanceIds: [ec2InstanceId],
          DocumentName: "AWS-RunShellScript",
          Parameters: {
            commands: [
              "cd /opt/mland-staging && docker compose down || true"
            ]
          }
        }));
      } catch (e) {
        console.warn("Khong the gui SSM down truoc khi stop:", e.message);
      }

      await ec2.send(new StopInstancesCommand({ InstanceIds: [ec2InstanceId] }));
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          message: "Lệnh dừng EC2 đã được gửi thành công",
          instanceId: ec2InstanceId,
          status: "STOPPING"
        })
      };
    }

    // 5. Lấy snapshot Logs gần nhất (GET /staging/logs)
    if (method === "GET" && path.endsWith("/logs")) {
      const sendCmd = await ssm.send(new SendCommandCommand({
        InstanceIds: [ec2InstanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: {
          commands: [
              "echo '=== DEPLOY LOG (last 120 lines) ==='; if [ -f /var/log/mland-deploy.log ]; then tail -n 120 /var/log/mland-deploy.log; else echo 'No deploy log yet'; fi; echo ''; echo '=== BOOTSTRAP LOG (last 50 lines) ==='; if [ -f /var/log/user-data.log ]; then tail -n 50 /var/log/user-data.log; else echo 'No bootstrap log yet'; fi; echo ''; echo '=== CONTAINER STATUS ==='; cd /opt/mland-staging && sudo -u ubuntu docker compose ps -a 2>&1 || echo 'Compose project not available'; echo ''; echo '=== CONTAINER LOGS ==='; cd /opt/mland-staging && sudo -u ubuntu docker compose logs --tail=100 --no-color 2>&1 || echo 'Container logs not available yet'"
          ]
        }
      }));

      const commandId = sendCmd.Command?.CommandId;
      // Đợi ngắn để lấy output
      await new Promise(r => setTimeout(r, 2000));
      let output = "Logs are being collected...";
      try {
        const inv = await ssm.send(new GetCommandInvocationCommand({
          CommandId: commandId,
          InstanceId: ec2InstanceId
        }));
        output = [
          `SSM command: ${inv.Status || "Unknown"} (${commandId})`,
          inv.StandardOutputContent?.trim(),
          inv.StandardErrorContent?.trim() ? `STDERR:\n${inv.StandardErrorContent.trim()}` : ""
        ].filter(Boolean).join("\n\n") || "Command completed without output.";
      } catch (err) {
        output = "Waiting for SSM command invocation: " + err.message;
      }

      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({ logs: output, timestamp: new Date().toISOString() })
      };
    }

    // Mặc định trả về thông tin API
    return {
      statusCode: 200,
      headers,
      body: JSON.stringify({
        name: "MLAND Staging Control API",
        instanceId: ec2InstanceId,
        endpoints: [
          "GET /staging/status",
          "POST /staging/start",
          "POST /staging/deploy",
          "POST /staging/stop",
          "GET /staging/logs"
        ]
      })
    };
  } catch (error) {
    console.error("Loi xu ly Lambda:", error);
    return {
      statusCode: 500,
      headers,
      body: JSON.stringify({ error: error.message, stack: error.stack })
    };
  }
};
