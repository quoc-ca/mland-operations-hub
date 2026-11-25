// ST-NFR-PERF-001: workshop schedule lookup, 100 concurrent users, every response < 2s (p95).
// Read-only (public GETs). Run: k6 run -e BASE_URL=http://localhost:8080 ppswbs-testing/performance/st-nfr-perf-001.js
import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1`;

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '2m', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{name:schedule}': ['p(95)<2000'],
    'http_req_failed{name:schedule}': ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

// Only packages that really have sessions are queried, so an empty database cannot produce a pass.
export function setup() {
  const response = http.get(`${BASE}/workshop-packages`);
  if (response.status !== 200) fail(`package list answered ${response.status}`);
  const packages = response.json();
  // Same request the booking page sends: one package at one of its branches.
  const urls = packages
    .flatMap((p) => (p.branchIds || []).map((b) => `${BASE}/workshop-packages/${p.packageId}/sessions?locationId=${b}`))
    .filter((url) => {
      const sessions = http.get(url);
      return sessions.status === 200 && sessions.json().length > 0;
    });
  if (urls.length === 0) fail('no published package has a bookable session; seed data first');
  return { urls };
}

export default function ({ urls }) {
  const url = urls[Math.floor(Math.random() * urls.length)];
  const response = http.get(url, { tags: { name: 'schedule' } });
  check(response, { 'status 200': (r) => r.status === 200 });
  sleep(1);
}
