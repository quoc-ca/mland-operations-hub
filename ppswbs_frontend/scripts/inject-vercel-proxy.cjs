const fs = require('fs');
const path = require('path');

const rawBackendUrl = process.env.BACKEND_URL;

if (!rawBackendUrl || !rawBackendUrl.trim()) {
  console.error('\n======================================================================');
  console.error('[Vercel Proxy] Error: Missing BACKEND_URL environment variable.');
  console.error('To proxy API and payment requests to the backend, configure');
  console.error('BACKEND_URL (e.g. https://mland-demo.io.vn) in Vercel Project Settings:');
  console.error('  Settings -> Environment Variables -> BACKEND_URL');
  console.error('======================================================================\n');
  process.exit(1);
}

const backendUrl = rawBackendUrl.trim().replace(/\/+$/, '');
if (!/^https:\/\/[^/]+$/i.test(backendUrl)) {
  console.error('[Vercel Proxy] BACKEND_URL must be an HTTPS origin without a path.');
  process.exit(1);
}
const vercelConfigPath = path.resolve(__dirname, '../vercel.json');

if (!fs.existsSync(vercelConfigPath)) {
  console.error(`[Vercel Proxy] Error: vercel.json not found at ${vercelConfigPath}`);
  process.exit(1);
}

try {
  const vercelRaw = fs.readFileSync(vercelConfigPath, 'utf8');
  const vercelConfig = JSON.parse(vercelRaw);

  const requiredSources = new Map([
    ['/api/:path*', '/api/:path*'],
    ['/payment/:path*', '/payment/:path*'],
  ]);
  if (Array.isArray(vercelConfig.rewrites)) {
    for (const rule of vercelConfig.rewrites) {
      if (requiredSources.has(rule.source)) {
        rule.destination = `${backendUrl}${requiredSources.get(rule.source)}`;
        requiredSources.delete(rule.source);
      }
    }
  }

  if (requiredSources.size > 0) {
    console.error(`[Vercel Proxy] Missing rewrite rules: ${[...requiredSources.keys()].join(', ')}`);
    process.exit(1);
  }

  fs.writeFileSync(vercelConfigPath, JSON.stringify(vercelConfig, null, 2) + '\n', 'utf8');
  console.log(`[Vercel Proxy] Successfully injected BACKEND_URL: ${backendUrl} into vercel.json`);
} catch (error) {
  console.error('[Vercel Proxy] Failed to update vercel.json:', error.message);
  process.exit(1);
}
