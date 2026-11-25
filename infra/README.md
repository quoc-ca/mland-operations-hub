# Cloudflare Tunnel rollout (staging)

Public backend hostname: `https://mland-demo.io.vn`. The EC2 host runs a
remotely managed `cloudflared` systemd service. Its published application must
use **HTTP** service URL `http://127.0.0.1:8080`; TLS ends at Cloudflare and
Docker publishes Spring only on the host loopback address.

The tunnel token is installed separately on EC2 using the command supplied by
Cloudflare Dashboard. Never place it in Terraform, user data, the Lambda deploy
request, repository files, or the backend `.env`. `user_data.sh` installs the
`cloudflared` package for *new* instances but does not register the tunnel.

## Existing EC2

`user_data.sh` created `/opt/mland-staging-deploy.sh` only when this instance
was launched. Editing user data does not replace that existing script. It still
fetches the selected branch and runs Docker Compose, so a Deploy action can
activate the new loopback-only port mapping. Its old log cleanup and lack of
Tunnel status reporting remain until the host helper is updated separately via
SSM. Do not destroy the instance to refresh user data: its root EBS volume is
configured to be deleted on termination, and the MySQL named volume lives on
that disk.

Before cutover, check on EC2 via SSM:

```sh
sudo systemctl is-active cloudflared
sudo -iu ubuntu
cd /opt/mland-staging
docker compose ps
docker compose port backend 8080
curl -i http://127.0.0.1:8080/api/v1/workshop-packages
```

The backend should publish `127.0.0.1:8080`; the public hostname should return
the same API response. Deploy the Vercel frontend with both `/api` and `/payment`
rewrites targeting the HTTPS hostname. If Vercel's Build Command invokes
`inject-vercel-proxy.cjs`, set `BACKEND_URL=https://mland-demo.io.vn` in that
Vercel environment before redeployment; the script rejects HTTP origins.

**Only after** the frontend API and VNPay return/IPN flows work through the
Tunnel, run `terraform plan` and apply the security group change that removes
public port 80. Inspect the plan for any EC2 replacement (`-/+`) and stop if it
appears. Terraform may stop/start the existing instance when its user data
changes, but the bootstrap script does not run again by default. Never use
`terraform destroy` to roll out this change.
