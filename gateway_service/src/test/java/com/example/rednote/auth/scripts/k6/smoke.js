// smoke.js  —— k6 兼容写法（无对象展开/可选链）
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
const s2xx = new Counter('status_2xx');
const s409 = new Counter('status_409');
const s429 = new Counter('status_429');

const BASE  = __ENV.BASE_URL   || 'http://host.docker.internal:8080';
const PATH  = __ENV.READ_PATH  || '/api/feed?size=5&cursor=0';
const TOKEN = __ENV.TOKEN      || '';
const FIXED = __ENV.IDEMP      || '';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 200),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{status:ok}': ['p(95)<300'],
  },
  tags: { test: 'feed-smoke' },
};

export default function () {
  const idem = FIXED || ('k-' + __VU + '-' + __ITER + '-' + Date.now());

  // 旧式拼接 headers（k6 兼容）
  const headers = { 'Idempotency-Key': idem };
  if (TOKEN) {
    headers['Authorization'] = TOKEN;
  }

  const res = http.get(BASE + PATH, { headers });

  check(res, {
    'status 200/409/429': r => r.status === 200 || r.status === 409 || r.status === 429,
  });

  if (res.status === 409 || res.status === 429) {
    sleep(0.05);
  }
  if (res.status >= 200 && res.status < 300) s2xx.add(1);
  else if (res.status === 409) s409.add(1);
  else if (res.status === 429) s429.add(1);
}

// 写出汇总
export function handleSummary(d) {
  return { '/results/limit_test_summary.json': JSON.stringify(d, null, 2) };
}
