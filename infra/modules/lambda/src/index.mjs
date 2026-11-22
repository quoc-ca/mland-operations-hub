import { EC2Client, StartInstancesCommand, StopInstancesCommand, DescribeInstancesCommand } from "@aws-sdk/client-ec2";
import {
  SSMClient,
  SendCommandCommand,
  GetCommandInvocationCommand,
  DescribeInstanceInformationCommand
} from "@aws-sdk/client-ssm";

const region = process.env.AWS_REGION || "ap-southeast-1";
const getEc2InstanceId = () => process.env.STAGING_EC2_ID || "";
const ec2 = new EC2Client({ region });
const ssm = new SSMClient({ region });
const sendEc2 = (command) => globalThis.__mockEC2Send?.(command) ?? ec2.send(command);
const sendSsm = (command) => globalThis.__mockSSMSend?.(command) ?? ssm.send(command);
const responseHeaders = { "Content-Type": "application/json" };
const terminalSsmStatuses = new Set([
  "Success", "Cancelled", "TimedOut", "Failed", "Cancelling", "Undeliverable", "Terminated"
]);

const jsonResponse = (statusCode, body) => ({
  statusCode,
  headers: responseHeaders,
  body: JSON.stringify(body)
});

const parseBody = (event) => {
  if (typeof event.body !== "string") return event.body || {};
  try {
    return JSON.parse(event.body || "{}");
  } catch {
    return {};
  }
};

const getQueryParameter = (event, name) => {
  if (event.queryStringParameters?.[name]) return event.queryStringParameters[name];
  const raw = event.rawQueryString || "";
  return new URLSearchParams(raw).get(name) || "";
};

const getInvocation = async (commandId, instanceId) => sendSsm(new GetCommandInvocationCommand({
  CommandId: commandId,
  InstanceId: instanceId
}));

const collectInvocation = async (commandId, instanceId) => {
  try {
    const invocation = await getInvocation(commandId, instanceId);
    return {
      commandId,
      status: invocation.Status || "Unknown",
      statusDetails: invocation.StatusDetails || "",
      responseCode: invocation.ResponseCode ?? null,
      stdout: invocation.StandardOutputContent || "",
      stderr: invocation.StandardErrorContent || "",
      checks: Object.fromEntries(
        [...(invocation.StandardOutputContent || "").matchAll(/^CHECK\|([^|\r\n]+)\|([^|\r\n]+)\|([^\r\n]+)/gm)]
          .map((match) => [match[1], { state: match[2], health: match[3] }])
      )
    };
  } catch (error) {
    if (error.name === "InvocationDoesNotExist") {
      return { commandId, status: "Pending", statusDetails: "SSM invocation is initializing", responseCode: null, stdout: "", stderr: "" };
    }
    throw error;
  }
};

const waitForInvocation = async (commandId, instanceId, timeoutMs = 90000) => {
  const deadline = Date.now() + timeoutMs;
  let result = { commandId, status: "Pending", responseCode: null, stdout: "", stderr: "" };

  while (Date.now() < deadline) {
    try {
      result = await collectInvocation(commandId, instanceId);
      if (terminalSsmStatuses.has(result.status)) return result;
    } catch (error) {
      if (error.name !== "InvocationDoesNotExist") throw error;
    }
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }

  return { ...result, status: "TimedOut", statusDetails: "Timed out waiting for SSM command completion" };
};

export const handler = async (event) => {
  const instanceId = getEc2InstanceId();
  const path = event.rawPath || event.path || "";
  const method = event.requestContext?.http?.method || event.httpMethod || "GET";

  try {
    if (method === "GET" && path.endsWith("/status")) {
      const result = await sendEc2(new DescribeInstancesCommand({ InstanceIds: [instanceId] }));
      const instance = result.Reservations?.[0]?.Instances?.[0];
      const state = instance?.State?.Name || "unknown";
      let ssmStatus = "not-applicable";

      if (state === "running") {
        const managed = await sendSsm(new DescribeInstanceInformationCommand({
          Filters: [{ Key: "InstanceIds", Values: [instanceId] }]
        }));
        ssmStatus = managed.InstanceInformationList?.[0]?.PingStatus || "offline";
      }

      return jsonResponse(200, {
        instanceId,
        state,
        publicIp: instance?.PublicIpAddress || null,
        ssmStatus,
        timestamp: new Date().toISOString()
      });
    }

    if (method === "POST" && path.endsWith("/start")) {
      await sendEc2(new StartInstancesCommand({ InstanceIds: [instanceId] }));
      return jsonResponse(200, {
        message: "EC2 start request accepted. Deploy the application after the instance and SSM agent are ready.",
        instanceId,
        status: "STARTING"
      });
    }

    if (method === "POST" && path.endsWith("/deploy")) {
      const body = parseBody(event);
      const branch = (body.branch || "develop").trim();
      if (!/^[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(branch) || branch.includes("..") || branch.endsWith("/")) {
        return jsonResponse(400, { error: "Invalid branch name" });
      }

      const runtimeEnv = body.env && typeof body.env === "object" && !Array.isArray(body.env) ? body.env : {};
      const envEntries = Object.entries(runtimeEnv);
      if (envEntries.length === 0) {
        return jsonResponse(400, {
          error: "Deployment rejected: 'env' object containing required environment variables is mandatory."
        });
      }

      for (const [key, value] of envEntries) {
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key) || value === null || typeof value === "object") {
          return jsonResponse(400, { error: `Invalid runtime environment entry: ${key}` });
        }
      }

      const encodedEnv = Buffer.from(JSON.stringify(runtimeEnv)).toString("base64");
      const commands = [
        "install -d -o ubuntu -g ubuntu -m 0750 /opt/mland-staging /opt/mland-staging-runtime",
        `printf '%s' '${encodedEnv}' | base64 -d > /opt/mland-staging-runtime/runtime-env.json`,
        "chown ubuntu:ubuntu /opt/mland-staging-runtime/runtime-env.json && chmod 600 /opt/mland-staging-runtime/runtime-env.json",
        `sudo -H -u ubuntu -- /opt/mland-staging-deploy.sh '${branch}'`
      ];

      const sent = await sendSsm(new SendCommandCommand({
        InstanceIds: [instanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: { commands }
      }));
      const commandId = sent.Command?.CommandId;

      return jsonResponse(202, {
        message: "Deploy command accepted by SSM",
        instanceId,
        commandId,
        status: "DEPLOYING",
        branch,
        envCount: envEntries.length
      });
    }

    if (method === "GET" && path.endsWith("/operation")) {
      const commandId = getQueryParameter(event, "commandId");
      if (!commandId) return jsonResponse(400, { error: "commandId is required" });
      const invocation = await collectInvocation(commandId, instanceId);
      return jsonResponse(invocation.status === "Pending" ? 202 : 200, invocation);
    }

    if (method === "GET" && path.endsWith("/logs")) {
      const commands = [
        "echo '=== DEPLOY LOG (last 120 lines) ==='; if [ -f /var/log/mland-deploy.log ]; then tail -n 120 /var/log/mland-deploy.log; else echo 'No deploy log yet'; fi",
        "echo ''; echo '=== BOOTSTRAP LOG (last 50 lines) ==='; if [ -f /var/log/user-data.log ]; then tail -n 50 /var/log/user-data.log; else echo 'No bootstrap log yet'; fi",
        "cd /opt/mland-staging 2>/dev/null || exit 0; for svc in db backend; do cid=$(sudo -H -u ubuntu -- docker compose ps -aq \"$svc\"); if [ -z \"$cid\" ]; then printf 'CHECK|%s|not-created|not-created\\n' \"$svc\"; else state=$(docker inspect --format '{{.State.Status}}' \"$cid\" 2>/dev/null || echo unknown); health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' \"$cid\" 2>/dev/null || echo unknown); printf 'CHECK|%s|%s|%s\\n' \"$svc\" \"$state\" \"$health\"; fi; done",
        "echo ''; echo '=== CONTAINER STATUS ==='; cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose ps -a 2>&1 || echo 'Compose project not available'",
        "echo ''; echo '=== CONTAINER LOGS ==='; cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose logs --tail=100 --no-color 2>&1 || echo 'Container logs not available yet'"
      ];
      const sent = await sendSsm(new SendCommandCommand({
        InstanceIds: [instanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: { commands }
      }));
      return jsonResponse(202, {
        commandId: sent.Command?.CommandId,
        status: "COLLECTING_LOGS",
        message: "Log snapshot command accepted"
      });
    }

    if (method === "POST" && path.endsWith("/stop")) {
      let ssmResult = null;
      try {
        const sent = await sendSsm(new SendCommandCommand({
          InstanceIds: [instanceId],
          DocumentName: "AWS-RunShellScript",
          Parameters: {
            commands: [
              "cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose down"
            ]
          }
        }));
        if (sent.Command?.CommandId) {
          ssmResult = await waitForInvocation(sent.Command.CommandId, instanceId);
        }
      } catch (error) {
        ssmResult = { status: "Failed", statusDetails: error.message, responseCode: null, stdout: "", stderr: error.message };
      }

      await sendEc2(new StopInstancesCommand({ InstanceIds: [instanceId] }));
      return jsonResponse(200, {
        message: ssmResult?.status === "Success"
          ? "Containers stopped and EC2 stop request accepted"
          : "EC2 stop request accepted; container shutdown did not complete successfully",
        instanceId,
        commandId: ssmResult?.commandId || null,
        composeStatus: ssmResult?.status || "Unavailable",
        composeError: ssmResult?.stderr || ssmResult?.statusDetails || "",
        status: "STOPPING"
      });
    }

    return jsonResponse(200, {
      name: "MLAND Staging Control API",
      instanceId,
      endpoints: [
        "GET /staging/status",
        "POST /staging/start",
        "POST /staging/deploy",
        "POST /staging/stop",
        "GET /staging/operation?commandId=...",
        "GET /staging/logs"
      ]
    });
  } catch (error) {
    console.error("Lambda control API error:", error);
    return jsonResponse(500, { error: error.message || "Internal control API error" });
  }
};
