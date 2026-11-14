#!/usr/bin/env node
/**
 * API error-handling guard — fails CI when frontend code handles axios errors unsafely.
 *
 * Every user-facing API error message must come from `extractApiError` / `safeLoadMessage`
 * (frontend/src/utils/apiError.js). Hand-rolled handling caused real production bugs:
 *   - rendering `err.response.data` (an object) crashed screens with React #31;
 *   - `err.message` showed axios transport text ("Request failed with status code 500");
 *   - `JSON.stringify(...)` of an error body showed raw JSON; string bodies showed nginx HTML.
 * Importing axios directly also bypasses apiClient (auth header, tenant routing, 401, retries).
 *
 * Rules (non-test files under frontend/src):
 *   no-response-data     `<x>.response.data` / `<x>?.response?.data` — use extractApiError(err, fallback)
 *   no-raw-error-message `toastError(err.message)`, `setError(err.message || …)` and similar
 *   no-stringify-error   `JSON.stringify(err…)` / `JSON.stringify(<x>.response…)`
 *   no-direct-axios      `import … from 'axios'` / `import('axios')` outside services/apiService.js
 *
 * A deliberate exception must say why, on the same line or the line above:
 *   // api-error-guard-ignore: <reason>
 *
 * Usage:  node scripts/ci/check-api-error-handling.mjs [srcDir]   (default: frontend/src)
 *         node scripts/ci/check-api-error-handling.mjs --self-test
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const IGNORE = /api-error-guard-ignore:\s*\S/;
const SOURCE = /\.(jsx?|tsx?)$/;
const TEST = /\.(test|spec)\.(jsx?|tsx?)$|(^|[/\\])(test|__tests__|__mocks__)[/\\]/;

// Files that ARE the sanctioned implementation.
const EXEMPT_FILES = new Set(['utils/apiError.js', 'services/apiService.js']);

const UI_SINK = String.raw`(?:toastError|toast\.(?:error|warning|warn|info)|showError|setError|setErrors|setErr\w*|setMsg|setMessage|alert)`;
const ERR = String.raw`(?:err|error|e|ex|exc|reason)`;

export const RULES = [
  {
    id: 'no-response-data',
    re: /\b[A-Za-z_$][\w$]*\??\.response\??\.data\b/,
    hint: 'use extractApiError(err, "fallback") from utils/apiError',
  },
  {
    id: 'no-raw-error-message',
    re: new RegExp(String.raw`${UI_SINK}\(\s*(?:\{[^}]*:\s*)?${ERR}\??\.message\b`),
    hint: 'use extractApiError(err, "fallback") — err.message is axios transport text',
  },
  {
    id: 'no-stringify-error',
    re: new RegExp(
      String.raw`JSON\.stringify\(\s*(?:${ERR}\b|[A-Za-z_$][\w$]*\??\.response\b|errData\b)`
    ),
    hint: 'never show a serialised error body; use extractApiError',
  },
  {
    id: 'no-direct-axios',
    re: /(?:from\s+['"]axios['"]|import\(\s*['"]axios['"]\s*\)|require\(\s*['"]axios['"]\s*\))/,
    hint: 'import apiClient from services/apiService so interceptors apply',
  },
];

const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });

/** Return violations for one file's text. `rel` uses forward slashes, relative to srcDir. */
export function checkSource(rel, text) {
  if (EXEMPT_FILES.has(rel)) return [];
  const lines = text.split('\n');
  const out = [];
  lines.forEach((line, i) => {
    const code = line.replace(/\/\/.*$/, ''); // ignore trailing line comments
    for (const rule of RULES) {
      if (!rule.re.test(code)) continue;
      if (IGNORE.test(line) || IGNORE.test(lines[i - 1] || '')) continue;
      out.push({ file: rel, line: i + 1, rule: rule.id, hint: rule.hint, text: line.trim() });
    }
  });
  return out;
}

export function checkTree(srcDir) {
  return walk(srcDir)
    .filter((p) => SOURCE.test(p) && !TEST.test(p))
    .flatMap((p) => checkSource(relative(srcDir, p).split(sep).join('/'), readFileSync(p, 'utf8')));
}

function selfTest() {
  const cases = [
    // [description, rel, source, expected rule ids]
    [
      'raw body to state',
      'components/A.jsx',
      "setError(err.response?.data || 'x');",
      ['no-response-data'],
    ],
    [
      'hand-rolled body parse',
      'pages/B.jsx',
      'const data = err.response?.data;',
      ['no-response-data'],
    ],
    [
      'optional chain from e',
      'pages/B.jsx',
      "toastError(e?.response?.data?.error || 'x');",
      ['no-response-data'],
    ],
    ['raw message toast', 'pages/C.jsx', 'toastError(err.message);', ['no-raw-error-message']],
    [
      'raw message fallback',
      'pages/C.jsx',
      "setError(error.message || 'x');",
      ['no-raw-error-message'],
    ],
    [
      'raw message in object',
      'pages/C.jsx',
      'setErrors({ submit: err.message });',
      ['no-raw-error-message'],
    ],
    [
      'stringify body',
      'pages/D.jsx',
      'JSON.stringify(err.response.data)',
      ['no-response-data', 'no-stringify-error'],
    ],
    ['stringify errData', 'pages/D.jsx', 'return JSON.stringify(errData);', ['no-stringify-error']],
    ['direct axios import', 'components/E.jsx', "import axios from 'axios';", ['no-direct-axios']],
    [
      'dynamic axios import',
      'components/E.jsx',
      "const a = (await import('axios')).default;",
      ['no-direct-axios'],
    ],
    // allowed
    ['helper call', 'pages/F.jsx', "toastError(extractApiError(err, 'Failed'));", []],
    ['success path in service', 'services/x.js', 'return response.data;', []],
    ['status check is fine', 'pages/G.jsx', 'if (err.response?.status === 404) return;', []],
    ['comment mention is fine', 'pages/G.jsx', '// never read err.response.data here', []],
    ['own validation error', 'pages/G.jsx', "setError('Select a ward first');", []],
    [
      'ignored same line',
      'components/H.jsx',
      "import axios from 'axios'; // api-error-guard-ignore: Cloudinary upload",
      [],
    ],
    [
      'ignored line above',
      'utils/I.js',
      '// api-error-guard-ignore: reads conflicts list\nconst c = err?.response?.data?.conflicts;',
      [],
    ],
    [
      'ignore needs a reason',
      'utils/I.js',
      '// api-error-guard-ignore:\nconst c = err.response.data;',
      ['no-response-data'],
    ],
    ['helper itself exempt', 'utils/apiError.js', 'const data = err?.response?.data;', []],
    ['client itself exempt', 'services/apiService.js', "import axios from 'axios';", []],
  ];
  let failed = 0;
  for (const [name, rel, src, expected] of cases) {
    const got = [...new Set(checkSource(rel, src).map((v) => v.rule))].sort();
    const want = [...expected].sort();
    const ok = JSON.stringify(got) === JSON.stringify(want);
    if (!ok) failed += 1;
    console.log(
      `${ok ? 'ok  ' : 'FAIL'} ${name}${ok ? '' : `  expected ${JSON.stringify(want)} got ${JSON.stringify(got)}`}`
    );
  }
  console.log(`\nself-test: ${cases.length - failed}/${cases.length} passed`);
  return failed === 0;
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  if (process.argv.includes('--self-test')) process.exit(selfTest() ? 0 : 1);

  const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
  const srcDir = resolve(process.argv[2] || join(repoRoot, 'frontend', 'src'));
  const violations = checkTree(srcDir);
  const summary = process.env.GITHUB_STEP_SUMMARY;

  if (violations.length === 0) {
    console.log(`API error guard: OK (${srcDir})`);
    process.exit(0);
  }
  for (const v of violations) {
    // GitHub annotation: shows inline on the PR diff.
    console.log(
      `::error file=frontend/src/${v.file},line=${v.line},title=${v.rule}::${v.hint} — ${v.text}`
    );
  }
  console.error(
    `\nAPI error guard: ${violations.length} violation(s). See scripts/ci/check-api-error-handling.mjs.`
  );
  if (summary) {
    const rows = violations
      .map((v) => `| \`${v.file}:${v.line}\` | ${v.rule} | ${v.hint} |`)
      .join('\n');
    const { appendFileSync } = await import('node:fs');
    appendFileSync(
      summary,
      `## API error guard — ${violations.length} violation(s)\n\n| Location | Rule | Fix |\n| --- | --- | --- |\n${rows}\n`
    );
  }
  process.exit(1);
}
