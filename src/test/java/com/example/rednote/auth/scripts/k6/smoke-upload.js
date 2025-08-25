// k6/smoke-upload.js  —— 修复：在 init 阶段用 open() 读取图片
// 强制 multipart 版本：使用 jslib FormData
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

// ---- 环境变量 ----
const BASE       = __ENV.BASE_URL   || 'http://host.docker.internal:8080';
const WRITE_PATH = __ENV.WRITE_PATH || '/api/notes';
const TOKEN      = __ENV.TOKEN      || '';
const USER_ID    = __ENV.USER_ID    || '11';
const IDEMP      = __ENV.IDEMP      || '';
const IMG1_PATH  = __ENV.IMG1       || 'assets/pic1.jpg';
const IMG2_PATH  = __ENV.IMG2       || 'assets/pic2.jpg';

// ---- init 阶段读取文件（open 只能在这里调用）----
function basename(p){var i=Math.max(p.lastIndexOf('/'),p.lastIndexOf('\\'));return i>=0?p.substring(i+1):p;}
function mime(p){p=p.toLowerCase(); if(p.endsWith('.png'))return 'image/png'; if(p.endsWith('.gif'))return 'image/gif'; return 'image/jpeg';}
function loadFile(path){
  try { const data = open(path, 'b'); return { data, name: basename(path), type: mime(path) }; }
  catch(e){ return null; }
}
const FILE1 = loadFile(IMG1_PATH);
const FILE2 = loadFile(IMG2_PATH);

// ---- 场景 ----
export const options = {
  scenarios: {
    upload: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 5),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: Number(__ENV.VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 200),
      exec: 'upload',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{scenario:upload}': ['p(95)<1500'],
  },
};

const s2xx=new Counter('status_2xx'), s409=new Counter('status_409'), s429=new Counter('status_429'), s4xx=new Counter('status_4xx'), s5xx=new Counter('status_5xx');

export function upload() {
  const idem = IDEMP || ('note-' + __VU + '-' + __ITER + '-' + Date.now());

  const fd = new FormData();
  fd.append('userId', String(USER_ID));
  fd.append('title',  'k6-title-' + __VU + '-' + __ITER);
  fd.append('content','k6-content at ' + new Date().toISOString());

  // 必须至少有一张图，否则后端会 400
  if (FILE1) fd.append('images', http.file(FILE1.data, FILE1.name, FILE1.type));
  if (FILE2) fd.append('images', http.file(FILE2.data, FILE2.name, FILE2.type));

  const headers = { 'Idempotency-Key': idem, 'Content-Type': 'multipart/form-data; boundary=' + fd.boundary };
  if (TOKEN) headers['Authorization'] = TOKEN;

  const res = http.post(BASE + WRITE_PATH, fd.body(), { headers });

  if (res.status >= 200 && res.status < 300) s2xx.add(1);
  else if (res.status === 409) s409.add(1);
  else if (res.status === 429) s429.add(1);
  else if (res.status >= 400 && res.status < 500) s4xx.add(1);
  else if (res.status >= 500) s5xx.add(1);

  check(res, { 'status 2xx/409/429': r => (r.status >= 200 && r.status < 300) || r.status === 409 || r.status === 429 });

  // 仅首批样本打印一次，便于排查
  if (__VU === 1 && __ITER < 2) {
    console.log('SENT Content-Type:', headers['Content-Type']);
    console.log('IMG1 ok?', !!FILE1, 'IMG2 ok?', !!FILE2);
    console.log('RESP', res.status, (res.body || '').substring(0, 300));
  }

  if (res.status === 409 || res.status === 429) sleep(0.1);
}

export function handleSummary(d){ return { '/results/Upload_summary.json': JSON.stringify(d, null, 2) }; }
