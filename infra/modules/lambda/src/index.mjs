import { EC2Client, StartInstancesCommand, StopInstancesCommand, DescribeInstancesCommand } from "@aws-sdk/client-ec2";
import { SSMClient, SendCommandCommand, GetCommandInvocationCommand } from "@aws-sdk/client-ssm";

const region = process.env.AWS_REGION || "ap-southeast-1";
const ec2InstanceId = process.env.STAGING_EC2_ID;
const teamKeyHash = process.env.TEAM_KEY || "";
const allowedOrigins = (process.env.ALLOWED_ORIGINS || "*").split(",").map(o => o.trim());

const ec2 = new EC2Client({ region });
const ssm = new SSMClient({ region });

function buildCorsHeaders(requestOrigin) {
  let origin = "*";
  if (allowedOrigins.length > 0 && !allowedOrigins.includes("*")) {
    if (requestOrigin && allowedOrigins.includes(requestOrigin)) {
      origin = requestOrigin;
    } else {
      origin = allowedOrigins[0];
    }
  }
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type,Authorization,X-Team-Key",
    "Content-Type": "application/json"
  };
}

export const handler = async (event) => {
  const requestOrigin = event.headers?.origin || event.headers?.Origin || "";
  const headers = buildCorsHeaders(requestOrigin);

  // Xử lý CORS Preflight
  if (event.requestContext?.http?.method === "OPTIONS" || event.httpMethod === "OPTIONS") {
    return { statusCode: 200, headers, body: JSON.stringify({ message: "OK" }) };
  }

  const path = event.rawPath || event.path || "";
  const method = event.requestContext?.http?.method || event.httpMethod || "GET";

  try {
    // 1. Lấy trạng thái máy chủ EC2 (GET /staging/status)
    if (method === "GET" && path.endsWith("/status")) {
      const result = await ec2.send(new DescribeInstancesCommand({ InstanceIds: [ec2InstanceId] }));
      const state = result.Reservations?.[0]?.Instances?.[0]?.State?.Name || "unknown";
      const publicIp = result.Reservations?.[0]?.Instances?.[0]?.PublicIpAddress || null;
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          instanceId: ec2InstanceId,
          state,
          publicIp,
          timestamp: new Date().toISOString()
        })
      };
    }

    // 2. Khởi động môi trường Staging (POST /staging/start)
    if (method === "POST" && path.endsWith("/start")) {
      await ec2.send(new StartInstancesCommand({ InstanceIds: [ec2InstanceId] }));
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          message: "Lệnh khởi động EC2 đã được gửi thành công",
          instanceId: ec2InstanceId,
          status: "STARTING"
        })
      };
    }

    // 3. Dừng môi trường Staging (POST /staging/stop)
    if (method === "POST" && path.endsWith("/stop")) {
      // Gửi lệnh docker compose down qua SSM trước khi tắt máy
      try {
        await ssm.send(new SendCommandCommand({
          InstanceIds: [ec2InstanceId],
          DocumentName: "AWS-RunShellScript",
          Parameters: {
            commands: [
              "cd /opt/mland-staging && docker compose -f docker-compose.yml -f docker-compose.prod.yml down || true"
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

    // 4. Lấy snapshot Logs gần nhất (GET /staging/logs)
    if (method === "GET" && path.endsWith("/logs")) {
      const sendCmd = await ssm.send(new SendCommandCommand({
        InstanceIds: [ec2InstanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: {
          commands: [
            "cd /opt/mland-staging && docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=200 --no-color 2>&1 || echo 'Container logs not available yet'"
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
        output = inv.StandardOutputContent || inv.StandardErrorContent || output;
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
        endpoints: ["GET /staging/status", "POST /staging/start", "POST /staging/stop", "GET /staging/logs"]
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
