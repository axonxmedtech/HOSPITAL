import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * The Content-Security-Policy (frontend/nginx/csp.conf) allows two inline scripts by their
 * SHA-256 hash. If either text changes without the hash being updated, the browser blocks it
 * once CSP is enforced: iPhone printing stops working, or broken logos show on printouts.
 */
const root = resolve(__dirname, '../..');
const csp = readFileSync(resolve(root, 'nginx/csp.conf'), 'utf8');
const policy = csp.match(/set \$hms_csp "([^"]+)";/)?.[1] ?? '';
const scriptSrc = policy.split(';').find((d) => d.trim().startsWith('script-src')) ?? '';
const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const p = resolve(dir, name);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });
const sha256 = (text) => `'sha256-${createHash('sha256').update(text, 'utf8').digest('base64')}'`;

describe('CSP script hashes', () => {
  it('allows the iOS print trigger that printHtml injects', () => {
    const source = readFileSync(resolve(root, 'src/utils/printHtml.js'), 'utf8');
    const trigger = source.match(/trigger\.textContent\s*=\s*'([^']+)'/)?.[1];
    expect(trigger, 'printHtml.js no longer sets trigger.textContent').toBeTruthy();
    expect(scriptSrc).toContain(sha256(trigger));
  });

  it("allows the print templates' logo onerror handler, and no other inline handler exists", () => {
    const handler = "this.style.display='none'";
    expect(scriptSrc).toContain("'unsafe-hashes'");
    expect(scriptSrc).toContain(sha256(handler));

    // Inline handlers in HTML strings (print templates). JSX handlers (onError={…}) are fine.
    const others = walk(resolve(root, 'src'))
      .filter((p) => /\.(js|jsx)$/.test(p) && !/\.test\./.test(p))
      .flatMap((p) =>
        [...readFileSync(p, 'utf8').matchAll(/\bon[a-z]+=\\?"([^"\\]*)/g)].map((m) => m[1])
      )
      .filter((code) => code !== handler);
    expect(others, 'new inline handler: add its hash to csp.conf or use addEventListener').toEqual(
      []
    );
  });

  it('never allows arbitrary inline or eval script', () => {
    expect(scriptSrc).not.toMatch(/'unsafe-inline'|'unsafe-eval'/);
  });
});
