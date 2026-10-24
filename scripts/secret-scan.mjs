#!/usr/bin/env node
/**
 * Lightweight, cross-platform local secret scanner (pre-commit).
 *
 * Scans STAGED changes (default) or all tracked files (`--all`) for high-confidence
 * secret patterns and forbidden files, so credentials never reach GitHub. Tuned for
 * LOW false positives: generic patterns require an assignment + quotes + length.
 *
 * Two shapes defeated an earlier version of this scanner and are now covered explicitly:
 * a password passed as a command-line flag (`mysql -p<value>`), which carries no quotes and
 * therefore never matched the assignment rule, and a raw high-entropy signing key written on
 * its own line, which is not a three-part JWT. Both are proven by scripts/secret-scan.test.mjs.
 *
 * Bypass a specific line (use sparingly, only for genuine false positives) by adding
 * a trailing comment:  pragma: allowlist secret
 *
 * This is a fast local gate, NOT a replacement for CI secret scanning (Gitleaks).
 */
import { execSync } from 'node:child_process';
import { readFileSync, existsSync, statSync } from 'node:fs';

const ALL = process.argv.includes('--all');
const ALLOW = /pragma:\s*allowlist secret/i;

// Files that must never be committed (real secret material), by basename/path.
const FORBIDDEN_FILE = [
  /^\.env(\.|$)(?!example|sample|template)/i, // .env, .env.local ... but NOT .env.example
  /\.pem$/i,
  /\.(key|keystore|jks|p12|pfx)$/i,
  /(^|\/)id_rsa$/i,
];

// Skip these paths entirely (build output, deps, binaries, or example files).
const SKIP_PATH =
  /(^|\/)(node_modules|dist|coverage|target|build)\/|\.min\.js$|package-lock\.json$|\.(png|jpe?g|gif|ico|pdf|jar|class|woff2?|ttf|eot|svg)$|\.example$|\.sample$/i;

// A control character in the first chunk => treat the file as binary and skip it.
const BINARY = /[\x00-\x08\x0e-\x1f]/;

// High-confidence secret content patterns.
const RULES = [
  { name: 'Private key block', re: /-----BEGIN (?:RSA |EC |OPENSSH |PGP |DSA )?PRIVATE KEY-----/ },
  { name: 'AWS access key id', re: /\bAKIA[0-9A-Z]{16}\b/ },
  { name: 'GitHub token', re: /\bgh[pousr]_[A-Za-z0-9]{36,}\b/ },
  { name: 'Slack token', re: /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/ },
  { name: 'Google API key', re: /\bAIza[0-9A-Za-z_-]{35}\b/ },
  { name: 'Stripe secret key', re: /\bsk_(?:live|test)_[0-9a-zA-Z]{16,}\b/ },
  {
    name: 'JWT (three-part token)',
    re: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/,
  },
  {
    name: 'Hardcoded credential assignment',
    re: /\b(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key)\b\s*[:=]\s*["'][^"'\s]{8,}["']/i,
  },
  // `mysql -pHunter2`, `mysqladmin ping -uroot -pHunter2`, `--password=Hunter2`. No quotes and  // pragma: allowlist secret
  // no `=` after the keyword, so the assignment rule above cannot see it. Case-sensitive on the
  // flag: `-P` is a port, `-p` is a password.
  {
    name: 'Password on a command line',
    re: /\b(?:mysql|mysqldump|mysqladmin|psql|mongosh|redis-cli)\b[^\n]{0,120}?(?:-p(?!assword\b)|--password[= ])["']?([^\s"'`]{4,})/, // pragma: allowlist secret
    valueGroup: 1,
  },
  // `password \`Hunter2\`` in prose: a quoted value with no assignment operator, which the rule
  // above requires. Backticks count as quotes; markdown documentation is where this shape lives.
  // The keyword must be adjacent to the value, or every `getItem('token', ...)` becomes a finding.
  {
    name: 'Credential quoted in prose',
    re: /\b(?:password|passwd|passphrase|credential)\b\s+[`"']([^`"'\s]{8,})[`"']/i,
    valueGroup: 1,
  },
];

// A value that is obviously not a real credential: placeholders, redactions, and the
// deliberately-public throwaways used by CI services.
const NOT_A_SECRET =
  /^(?:\$\{|<|\{\{)|^(?:your[_-]?|xxx+|changeme|placeholder|example|sample|dummy|todo|redacted|none|null|undefined)/i;
const HARMLESS_VALUE = /throwaway|not[_-]?a[_-]?secret|^ci[_-]|example\.com|localhost/i;

/**
 * Shannon entropy in bits per character. A base64 credential sits near 5-6; prose, hashes of
 * repeated characters and long identifiers sit lower. Used only to keep the raw-secret rule
 * from firing on every long encoded string in the documentation.
 */
function entropy(value) {
  const counts = new Map();
  for (const ch of value) counts.set(ch, (counts.get(ch) || 0) + 1);
  let bits = 0;
  for (const n of counts.values()) {
    const pr = n / value.length;
    bits -= pr * Math.log2(pr);
  }
  return bits;
}

// A raw signing key: 40+ base64 characters that are NOT a three-part JWT and carry no keyword of
// their own. Alone that describes plenty of harmless content, so it is a finding only when the
// surrounding lines are talking about a secret, or it is assigned to something.
const RAW_SECRET = /(?:^|[\s`'"=(])([A-Za-z0-9+/]{40,}={0,2})(?:[\s`'",);]|$)/;
// No word boundaries: the keyword is usually welded into an identifier (JWT_SECRET, apiKey) or
// pluralised ("sign tokens"), and `_` counts as a word character so \b would never fire there.
// Permissiveness is safe here because the rule already requires a 40+ character high-entropy blob.
const SECRET_CONTEXT = /(?:secret|jwt|signing|sign |token|api[_-]?key|private[_-]?key|credential|password|passphrase)/i;
const ASSIGNED = /(?:=|:)\s*["'`]$/;
const CONTEXT_LINES = 5;

// Placeholders that should NOT trip the credential rule (env examples, templates).
const PLACEHOLDER =
  /["'](?:\$\{[^}]+\}|<[^>]+>|your[_-]?|xxx+|changeme|placeholder|example|test|dummy|todo)/i;

function stagedFiles() {
  return execSync('git diff --cached --name-only --diff-filter=ACM', { encoding: 'utf8' })
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
}
function allFiles() {
  return execSync('git ls-files', { encoding: 'utf8' })
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
}
function contentOf(file) {
  try {
    if (ALL) return existsSync(file) ? readFileSync(file, 'utf8') : '';
    return execSync('git show :"' + file + '"', { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 });
  } catch {
    return '';
  }
}

const files = ALL ? allFiles() : stagedFiles();
const findings = [];

for (const file of files) {
  const base = file.split('/').pop();
  if (FORBIDDEN_FILE.some((re) => re.test(base) || re.test(file))) {
    findings.push({ file, line: 0, rule: 'Forbidden file (secret material / .env)' });
    continue;
  }
  if (SKIP_PATH.test(file)) continue;
  try {
    if (ALL && existsSync(file) && statSync(file).size > 2 * 1024 * 1024) continue;
  } catch {
    /* ignore */
  }

  const text = contentOf(file);
  if (!text || BINARY.test(text.slice(0, 8000))) continue;

  const lines = text.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (ALLOW.test(line)) continue;
    for (const rule of RULES) {
      const m = rule.re.exec(line);
      if (!m) continue;
      if (rule.name.startsWith('Hardcoded') && PLACEHOLDER.test(line)) continue;
      // Rules that capture the value judge the value, not the whole line: a line may legitimately
      // mention a placeholder next to something real.
      if (rule.valueGroup) {
        const value = m[rule.valueGroup] || '';
        if (NOT_A_SECRET.test(value) || HARMLESS_VALUE.test(value)) continue;
      }
      findings.push({ file, line: i + 1, rule: rule.name });
    }

    const raw = RAW_SECRET.exec(line);
    if (raw) {
      const value = raw[1];
      const before = lines.slice(Math.max(0, i - CONTEXT_LINES), i).join('\n');
      const contextual = SECRET_CONTEXT.test(line) || SECRET_CONTEXT.test(before);
      const assigned = ASSIGNED.test(line.slice(0, raw.index + raw[0].indexOf(value)));
      if (
        (contextual || assigned) &&
        !NOT_A_SECRET.test(value) &&
        !HARMLESS_VALUE.test(value) &&
        !/^eyJ/.test(value) &&
        entropy(value) >= 4.2
      ) {
        findings.push({ file, line: i + 1, rule: 'Raw high-entropy secret' });
      }
    }
  }
}

if (findings.length) {
  console.error('\nX  Potential secrets detected - commit blocked:\n');
  for (const f of findings) {
    console.error('  ' + f.file + (f.line ? ':' + f.line : '') + '  ->  ' + f.rule);
  }
  console.error(
    '\n  Remove the secret (use an environment variable / .env, which is gitignored).' +
      '\n  Genuine false positive? Append "pragma: allowlist secret" to that line, or' +
      '\n  bypass this one commit with  git commit --no-verify  (discouraged).\n'
  );
  process.exit(1);
}

if (ALL) console.log('OK secret-scan: no secrets found in tracked files.');
process.exit(0);
