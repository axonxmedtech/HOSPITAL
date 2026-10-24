/**
 * Tests for scripts/secret-scan.mjs.
 *
 * Every credential here is SYNTHETIC and generated in-file. No value that ever appeared in this
 * repository is reproduced, and none of these strings is valid anywhere.
 *
 * Run: node --test scripts/secret-scan.test.mjs
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const SCANNER = new URL('./secret-scan.mjs', import.meta.url).pathname;

// Built from parts so the file itself contains no scannable literal.
const SYNTH_PW = 'Qx' + '7vB' + 'nT2w' + '#zK';
const SYNTH_B64 = 'A1b2C3d4E5f6G7h8I9j0KlMnOpQrStUvWxYz' + '0987654321AbCdEfGhIjKlMnOpQrStUvWx' + '==';

/** Scan one synthetic file in a throwaway git repo; returns the scanner's findings. */
function scan(contents, name = 'sample.md') {
  const dir = mkdtempSync(join(tmpdir(), 'secret-scan-test-'));
  try {
    execFileSync('git', ['init', '-q', dir]);
    writeFileSync(join(dir, name), contents);
    execFileSync('git', ['-C', dir, 'add', name]);
    let stdout = '';
    let stderr = '';
    let code = 0;
    try {
      stdout = execFileSync('node', [SCANNER, '--all'], { cwd: dir, encoding: 'utf8' });
    } catch (e) {
      code = e.status;
      stderr = (e.stderr || '') + (e.stdout || '');
    }
    return { code, output: stdout + stderr };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

// ---- positive cases: these must be caught ----

test('A. mysql -p<secret> on a command line is detected', () => {
  const r = scan('Run: `mysql -u root -p' + SYNTH_PW + ' -D app -e "select 1"`\n');
  assert.equal(r.code, 1, 'scanner must fail the build');
  assert.match(r.output, /Password on a command line/);
});

test('B. quoted / spaced command-line password variants are detected', () => {
  for (const line of [
    'mysqldump --password=' + SYNTH_PW + ' app > dump.sql',
    'mysqladmin ping -uroot -p' + SYNTH_PW,
  ]) {
    const r = scan(line + '\n');
    assert.equal(r.code, 1, 'not detected: ' + line.slice(0, 20));
    assert.match(r.output, /Password on a command line/);
  }
});

test('C. a raw base64 signing secret in a secret context is detected', () => {
  const r = scan('The `JWT_SECRET` used to sign tokens:\n`' + SYNTH_B64 + '`\n');
  assert.equal(r.code, 1);
  assert.match(r.output, /Raw high-entropy secret/);
});

test('C2. a raw signing secret assigned to a variable is detected', () => {
  const r = scan("const S='" + SYNTH_B64 + "';\n", 'snippet.js');
  assert.equal(r.code, 1);
  assert.match(r.output, /Raw high-entropy secret/);
});

test('C3. a credential quoted in prose is detected', () => {
  const r = scan('Creds: user `root`, password `' + SYNTH_PW + '`\n');
  assert.equal(r.code, 1);
  assert.match(r.output, /Credential quoted in prose/);
});

// ---- negative cases: these must stay quiet ----

test('D. documented placeholders are allowed', () => {
  const r = scan(
    'password `<REDACTED_DB_PASSWORD>`\n' +
      '`mysql -u root -p<REDACTED_DB_PASSWORD> -D app`\n' +
      'JWT_SECRET: `<REDACTED_JWT_SECRET>`\n' +
      'export PASSWORD="${DB_PASSWORD}"\n'
  );
  assert.equal(r.code, 0, 'placeholders must not be findings:\n' + r.output);
});

test('E. the CI throwaway password stays permitted', () => {
  const r = scan(
    'MYSQL_ROOT_PASSWORD: ci-throwaway-not-a-secret\n' +
      '--health-cmd="mysqladmin ping -h 127.0.0.1 -uroot -pci-throwaway-not-a-secret"\n'
  );
  assert.equal(r.code, 0, 'CI throwaway must not be a finding:\n' + r.output);
});

test('F. ordinary long encoded content is not a secret without context', () => {
  const r = scan(
    'The bundle integrity hash is\n' + SYNTH_B64 + '\nand the build is reproducible.\n'
  );
  assert.equal(r.code, 0, 'no secret keyword nearby, so no finding:\n' + r.output);
});

test('F2. a three-part JWT is still reported by its own rule, not the raw rule', () => {
  const jwt = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0MTIzNDUifQ.c2lnbmF0dXJlX3BsYWNlaG9sZGVy'; // pragma: allowlist secret
  const r = scan('token: ' + jwt + '\n');
  assert.equal(r.code, 1);
  assert.match(r.output, /JWT \(three-part token\)/);
});

// ---- G. existing rules still fire ----

test('G. pre-existing rules are preserved', () => {
  const cases = [
    ['-----BEGIN RSA PRIVATE KEY-----', /Private key block/], // pragma: allowlist secret
    ['AKIA' + 'ABCDEFGHIJKLMNOP', /AWS access key id/],
    ['ghp_' + 'a'.repeat(36), /GitHub token/],
    ['password = "' + SYNTH_PW + 'longer"', /Hardcoded credential assignment/],
  ];
  for (const [line, rule] of cases) {
    const r = scan(line + '\n', 'x.txt');
    assert.equal(r.code, 1, 'rule stopped firing for: ' + line.slice(0, 16));
    assert.match(r.output, rule);
  }
});

test('H. findings never print the secret value itself', () => {
  const r = scan('password `' + SYNTH_PW + '`\n');
  assert.equal(r.code, 1);
  assert.ok(!r.output.includes(SYNTH_PW), 'scanner leaked the value into its own output');
});
