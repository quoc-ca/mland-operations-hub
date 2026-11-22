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
    let stdout = invocation.StandardOutputContent || "";
    let nextOffset = null;
    const offsetMatch = stdout.match(/__OFFSET__\|(\d+)/);
    if (offsetMatch) {
      nextOffset = parseInt(offsetMatch[1], 10);
      stdout = stdout.replace(/\r?\n?__OFFSET__\|\d+\r?\n?/, "");
    }
    const checks = Object.fromEntries(
      [...stdout.matchAll(/^CHECK\|([^|\r\n]+)\|([^|\r\n]+)\|([^\r\n]+)/gm)]
        .map((match) => [match[1], { state: match[2], health: match[3] }])
    );
    stdout = stdout.replace(/^CHECK\|[^\r\n]+\r?\n?/gm, "").trimEnd();

    return {
      commandId,
      status: invocation.Status || "Unknown",
      statusDetails: invocation.StatusDetails || "",
      responseCode: invocation.ResponseCode ?? null,
      stdout,
      stderr: invocation.StandardErrorContent || "",
      nextOffset,
      checks
    };
  } catch (error) {
    if (error.name === "InvocationDoesNotExist") {
      return { commandId, status: "Pending", statusDetails: "SSM invocation is initializing", responseCode: null, stdout: "", stderr: "", nextOffset: null, checks: {} };
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

      const firebaseConfig = typeof body.firebaseConfig === "string" ? body.firebaseConfig.trim() : "";
      if (firebaseConfig) {
        try {
          JSON.parse(firebaseConfig);
        } catch {
          return jsonResponse(400, { error: "Invalid firebaseConfig: must be a valid JSON string" });
        }
      }

      const encodedEnv = Buffer.from(JSON.stringify(runtimeEnv)).toString("base64");
      const commands = [
        "install -d -o ubuntu -g ubuntu -m 0750 /opt/mland-staging /opt/mland-staging-runtime",
        `printf '%s' '${encodedEnv}' | base64 -d > /opt/mland-staging-runtime/runtime-env.json`,
        "chown ubuntu:ubuntu /opt/mland-staging-runtime/runtime-env.json && chmod 600 /opt/mland-staging-runtime/runtime-env.json"
      ];

      if (firebaseConfig) {
        const encodedFirebase = Buffer.from(firebaseConfig).toString("base64");
        commands.push(`printf '%s' '${encodedFirebase}' | base64 -d > /opt/mland-staging-runtime/firebase-service-account.json`);
        commands.push("chown ubuntu:ubuntu /opt/mland-staging-runtime/firebase-service-account.json && chmod 600 /opt/mland-staging-runtime/firebase-service-account.json");
        commands.push("mkdir -p /opt/mland-staging/ppswbs_backend && cp -f /opt/mland-staging-runtime/firebase-service-account.json /opt/mland-staging/ppswbs_backend/firebase-service-account.json && cp -f /opt/mland-staging-runtime/firebase-service-account.json /opt/mland-staging/firebase-service-account.json && chown -R ubuntu:ubuntu /opt/mland-staging/ppswbs_backend/firebase-service-account.json 2>/dev/null || true");
      }

      commands.push(`sudo -H -u ubuntu -- /opt/mland-staging-deploy.sh '${branch}'`);

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
      const source = getQueryParameter(event, "source") || "overview";
      const rawOffset = getQueryParameter(event, "offset");
      const offset = Math.max(0, parseInt(rawOffset || "0", 10) || 0);

      const checkCommand = "cd /opt/mland-staging 2>/dev/null || exit 0; for svc in db backend; do cid=$(sudo -H -u ubuntu -- docker compose ps -aq \"$svc\"); if [ -z \"$cid\" ]; then printf 'CHECK|%s|not-created|not-created\\n' \"$svc\"; else state=$(docker inspect --format '{{.State.Status}}' \"$cid\" 2>/dev/null || echo unknown); health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' \"$cid\" 2>/dev/null || echo unknown); printf 'CHECK|%s|%s|%s\\n' \"$svc\" \"$state\" \"$health\"; fi; done";

      let commands;
      if (source === "overview") {
        commands = [
          checkCommand,
          "TARGET_FILE=/var/log/mland-deploy.log",
          "if [ -f \"$TARGET_FILE\" ]; then",
          "  FILE_SIZE=$(wc -c < \"$TARGET_FILE\" 2>/dev/null || echo 0)",
          `  OFFSET=${offset}`,
          "  if [ \"$FILE_SIZE\" -lt \"$OFFSET\" ]; then OFFSET=0; fi",
          "  tail -c +$((OFFSET + 1)) \"$TARGET_FILE\"",
          "  printf '\\n__OFFSET__|%s\\n' \"$FILE_SIZE\"",
          "else",
          "  echo 'No deploy log yet'",
          "  printf '\\n__OFFSET__|0\\n'",
          "fi"
        ];
      } else if (["file-error", "file-info", "file-warning"].includes(source)) {
        const fileName = source === "file-error" ? "error.log" : source === "file-warning" ? "warning.log" : "info.log";
        commands = [
          checkCommand,
          "LOG_DIR=$(ls -td /opt/mland-staging/ppswbs_backend/logs/*/ 2>/dev/null | head -n 1)",
          `TARGET_FILE="$LOG_DIR/${fileName}"`,
          "if [ -n \"$LOG_DIR\" ] && [ -f \"$TARGET_FILE\" ]; then",
          "  FILE_SIZE=$(wc -c < \"$TARGET_FILE\" 2>/dev/null || echo 0)",
          `  OFFSET=${offset}`,
          "  if [ \"$FILE_SIZE\" -lt \"$OFFSET\" ]; then OFFSET=0; fi",
          "  tail -c +$((OFFSET + 1)) \"$TARGET_FILE\"",
          "  printf '\\n__OFFSET__|%s\\n' \"$FILE_SIZE\"",
          "else",
          `  echo 'No ${fileName} found in ' "$LOG_DIR"`,
          "  printf '\\n__OFFSET__|0\\n'",
          "fi"
        ];
      } else if (source === "file-all") {
        commands = [
          checkCommand,
          "LOG_DIR=$(ls -td /opt/mland-staging/ppswbs_backend/logs/*/ 2>/dev/null | head -n 1)",
          "if [ -n \"$LOG_DIR\" ] && [ -d \"$LOG_DIR\" ]; then echo \"=== BACKEND FILE LOGS ($LOG_DIR) ===\"; for fn in error.log warning.log info.log; do if [ -f \"$LOG_DIR/$fn\" ] && [ -s \"$LOG_DIR/$fn\" ]; then echo \"--- $fn (last 50 lines) ---\"; tail -n 50 \"$LOG_DIR/$fn\"; echo ''; fi; done; else echo 'Backend log files directory not found yet in /opt/mland-staging/ppswbs_backend/logs'; fi"
        ];
      } else {
        commands = [
          "echo '=== DEPLOY LOG (last 60 lines) ==='; if [ -f /var/log/mland-deploy.log ]; then tail -n 60 /var/log/mland-deploy.log; else echo 'No deploy log yet'; fi",
          checkCommand,
          "echo ''; echo '=== CONTAINER STATUS ==='; cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose ps -a 2>&1 || echo 'Compose project not available'",
          "echo ''; echo '=== BACKEND LOGS (last 60 lines) ==='; cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose logs --tail=60 --no-color backend 2>&1 || echo 'Backend container logs not available yet'",
          "echo ''; echo '=== MYSQL LOGS (last 20 lines) ==='; cd /opt/mland-staging && sudo -H -u ubuntu -- docker compose logs --tail=20 --no-color db 2>&1 || echo 'MySQL container logs not available yet'"
        ];
      }

      const sent = await sendSsm(new SendCommandCommand({
        InstanceIds: [instanceId],
        DocumentName: "AWS-RunShellScript",
        Parameters: { commands }
      }));
      return jsonResponse(202, {
        commandId: sent.Command?.CommandId,
        status: "COLLECTING_LOGS",
        source,
        offset,
        message: `Log snapshot (${source}, offset ${offset}) command accepted`
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
