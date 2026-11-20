#!/usr/bin/env node
/**
 * Captures the screenshots used by in-app Help from a running AxonX instance with demo data.
 *
 *   node scripts/help/capture-screenshots.mjs <accounts.json> [baseUrl] [shotId,shotId…]
 *
 * accounts.json maps each account name used in frontend/src/help/shots.js to a demo login:
 *   { "admin": { "email": "…", "password": "…", "login": "/login/hospital" },
 *     "reception": { "email": "…", "password": "…", "dept": "RECEPTIONIST", "login": "/login/hospital" }, … }
 * Keep that file out of git. Use demo data only — screenshots ship with the app.
 *
 * Screens are opened through the app's own command bar (Ctrl+K), so every dashboard works the
 * same way. Images are written to frontend/public/help/shots/<id>.jpg.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';
import { SHOTS } from '../../frontend/src/help/shots.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'frontend/public/help/shots');
const [accountsFile, base = 'http://localhost:5173', only] = process.argv.slice(2);
if (!accountsFile) {
  console.error('usage: capture-screenshots.mjs <accounts.json> [baseUrl] [shotIds]');
  process.exit(2);
}
const accounts = JSON.parse(fs.readFileSync(accountsFile, 'utf8'));
const wanted = only ? new Set(only.split(',')) : null;
fs.mkdirSync(OUT, { recursive: true });

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const settle = (page) => page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});

async function login(browser, acc) {
  const page = await (
    await browser.newContext({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 })
  ).newPage();
  page.setDefaultTimeout(10000);
  await page.goto(base + acc.login);
  await page.fill('input[type="email"]', acc.email);
  await page.fill('input[type="password"]', acc.password);
  if (acc.dept) await page.selectOption('#role', acc.dept);
  await page.click('button[type="submit"]');
  await page.waitForURL(
    (u) => !u.pathname.startsWith('/login') && !u.pathname.endsWith('/platform/login'),
    {
      timeout: 15000,
    }
  );
  await settle(page);
  return page;
}

async function open(page, screen) {
  await page.keyboard.press('Control+k');
  const box = page.getByLabel('Go to a screen or search help');
  await box.fill(screen);
  const result = page
    .getByRole('dialog', { name: 'Command bar' })
    .locator('li button')
    .filter({ has: page.locator('span', { hasText: new RegExp(`^${escape(screen)}$`) }) })
    .first();
  await result.click();
  await settle(page);
  await page.waitForTimeout(700);
}

const browser = await chromium.launch();
const results = [];
const byAccount = new Map();
for (const shot of SHOTS) {
  if (wanted && !wanted.has(shot.id)) continue;
  if (!byAccount.has(shot.account)) byAccount.set(shot.account, []);
  byAccount.get(shot.account).push(shot);
}

for (const [name, shots] of byAccount) {
  const acc = accounts[name];
  if (!acc) {
    shots.forEach((s) => results.push(`SKIP ${s.id}: no account "${name}"`));
    continue;
  }
  let page;
  try {
    page = await login(browser, acc);
  } catch (e) {
    shots.forEach((s) => results.push(`FAIL ${s.id}: login ${String(e).split('\n')[0]}`));
    continue;
  }
  for (const shot of shots) {
    try {
      await page.keyboard.press('Escape');
      await open(page, shot.screen);
      if (shot.action) {
        await page.getByRole('button', { name: shot.action }).first().click();
        await settle(page);
        await page.waitForTimeout(600);
      }
      await page.mouse.move(1270, 790);
      await page.screenshot({ path: path.join(OUT, `${shot.id}.jpg`), type: 'jpeg', quality: 70 });
      results.push(`OK   ${shot.id}`);
      if (shot.action) {
        await page.keyboard.press('Escape');
        await page.reload();
        await settle(page);
      }
    } catch (e) {
      results.push(`FAIL ${shot.id}: ${String(e).split('\n')[0].slice(0, 160)}`);
      await page.reload().catch(() => {});
      await settle(page);
    }
  }
  await page.context().close();
}
await browser.close();
console.log(results.join('\n'));
process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
