#!/usr/bin/env node
// 표준 입력을 로그 파일에 붙이고, 날짜가 바뀌면 <파일>.<날짜> 로 넘긴다. 30일(LOG_KEEP_DAYS)이 지난 파일은 지운다.
// vite 개발 서버는 표준 출력에만 쓰므로 bin/startup.sh 가 출력을 이 스크립트로 흘려 보낸다.
//
//   node bin/rolling-log.mjs <로그 파일>
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, statSync, unlinkSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { createInterface } from 'node:readline';

const file = process.argv[2];
if (!file) {
  console.error('사용법: rolling-log.mjs <로그 파일>');
  process.exit(2);
}
const keepDays = Number(process.env.LOG_KEEP_DAYS ?? 30);
const dir = dirname(file);
mkdirSync(dir, { recursive: true });

const pad = (n) => String(n).padStart(2, '0');
const day = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const time = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

// 지금 파일이 어느 날짜의 것인가. 어제 끄고 오늘 띄웠으면 첫 줄을 쓰기 전에 어제 날짜로 넘긴다.
let fileDay = existsSync(file) ? day(statSync(file).mtime) : day(new Date());

function roll(today) {
  if (existsSync(file)) {
    const rolled = `${file}.${fileDay}`;
    if (existsSync(rolled)) {
      // 같은 날짜로 이미 넘긴 파일이 있으면 덮어쓰지 않고 뒤에 붙인다
      appendFileSync(rolled, readFileSync(file));
      unlinkSync(file);
    } else {
      renameSync(file, rolled);
    }
  }
  fileDay = today;

  const prefix = `${basename(file)}.`;
  const limit = Date.now() - keepDays * 86_400_000;
  for (const name of readdirSync(dir)) {
    if (!name.startsWith(prefix) || !/^\d{4}-\d{2}-\d{2}$/.test(name.slice(prefix.length))) continue;
    const path = join(dir, name);
    if (statSync(path).mtimeMs < limit) unlinkSync(path);
  }
}

// 터미널이 닫히거나 Ctrl+C 가 와도 계속 쓴다. 입력이 끝나면(개발 서버가 꺼지면) 같이 끝난다.
process.on('SIGHUP', () => {});
process.on('SIGINT', () => {});

createInterface({ input: process.stdin, crlfDelay: Infinity }).on('line', (line) => {
  const now = new Date();
  const today = day(now);
  if (today !== fileDay) roll(today);
  appendFileSync(file, `${today} ${time(now)} ${line}\n`);
});
