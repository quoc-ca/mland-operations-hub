const fs = require('fs');
const path = require('path');

const rawBackendUrl = process.env.BACKEND_URL;

if (!rawBackendUrl || !rawBackendUrl.trim()) {
  console.error('\n======================================================================');
  console.error('[Vercel Proxy] Error: Missing BACKEND_URL environment variable.');
  console.error('To proxy API requests to the EC2 backend over HTTP, you must configure');
  console.error('BACKEND_URL (e.g. http://<your-ec2-eip>) in Vercel Project Settings:');
  console.error('  Settings -> Environment Variables -> BACKEND_URL');
  console.error('======================================================================\n');
  process.exit(1);
}

const backendUrl = rawBackendUrl.trim().replace(/\/+$/, '');
const vercelConfigPath = path.resolve(__dirname, '../vercel.json');

if (!fs.existsSync(vercelConfigPath)) {
  console.error(`[Vercel Proxy] Error: vercel.json not found at ${vercelConfigPath}`);
  process.exit(1);
}

try {
  const vercelRaw = fs.readFileSync(vercelConfigPath, 'utf8');
  const vercelConfig = JSON.parse(vercelRaw);

  let updated = false;
  if (Array.isArray(vercelConfig.rewrites)) {
    for (const rule of vercelConfig.rewrites) {
      if (rule.source === '/api/:path*') {
        rule.destination = `${backendUrl}/api/:path*`;
        updated = true;
      }
    }
  }

  if (!updated) {
    console.error('[Vercel Proxy] Error: Could not find /api/:path* rewrite rule in vercel.json');
    process.exit(1);
  }

  fs.writeFileSync(vercelConfigPath, JSON.stringify(vercelConfig, null, 2) + '\n', 'utf8');
  console.log(`[Vercel Proxy] Successfully injected BACKEND_URL: ${backendUrl} into vercel.json`);
} catch (error) {
  console.error('[Vercel Proxy] Failed to update vercel.json:', error.message);
  process.exit(1);
}
