import { afterEach, describe, expect, it, vi } from 'vitest';

// Minimal stored ZIP fixtures exercise the actual parser, including hostile property names.
function zip(files) {
  const encoder = new TextEncoder();
  const parts = [];
  const directory = [];
  let offset = 0;
  for (const [name, text] of files) {
    const filename = encoder.encode(name);
    const payload = encoder.encode(text);
    const local = new Uint8Array(30 + filename.length + payload.length);
    const l = new DataView(local.buffer);
    l.setUint32(0, 0x04034b50, true);
    l.setUint32(18, payload.length, true);
    l.setUint32(22, payload.length, true);
    l.setUint16(26, filename.length, true);
    local.set(filename, 30);
    local.set(payload, 30 + filename.length);
    const central = new Uint8Array(46 + filename.length);
    const c = new DataView(central.buffer);
    c.setUint32(0, 0x02014b50, true);
    c.setUint32(20, payload.length, true);
    c.setUint32(24, payload.length, true);
    c.setUint16(28, filename.length, true);
    c.setUint32(42, offset, true);
    central.set(filename, 46);
    parts.push(local);
    directory.push(central);
    offset += local.length;
  }
  const end = new Uint8Array(22);
  const e = new DataView(end.buffer);
  e.setUint32(0, 0x06054b50, true);
  e.setUint16(10, files.length, true);
  e.setUint32(16, offset, true);
  const all = [...parts, ...directory, end];
  const output = new Uint8Array(all.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const part of all) {
    output.set(part, at);
    at += part.length;
  }
  return output.buffer;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('ZIP entry names remain data', () => {
  it('prototype and constructor names cannot replace dictionary properties', async () => {
    vi.stubGlobal('self', {});
    const { readZipEntries } = await import('./importHeaders.worker.js');
    const entries = await readZipEntries(
      zip([
        ['__proto__', 'hostile prototype'],
        ['constructor', 'hostile constructor'],
        ['toString', 'hostile string method'],
        ['xl/workbook.xml', 'workbook'],
      ])
    );
    expect(Object.getPrototypeOf(entries)).toBe(Map.prototype);
    expect(entries.constructor).toBe(Map);
    expect(await entries.get('__proto__')()).toBe('hostile prototype');
    expect(await entries.get('constructor')()).toBe('hostile constructor');
    expect(await entries.get('xl/workbook.xml')()).toBe('workbook');
    expect(entries.get('missing-entry')).toBeUndefined();
  });

  it('still reads workbook headers with hostile unrelated entries present', async () => {
    const worker = { postMessage: vi.fn() };
    vi.stubGlobal('self', worker);
    await import('./importHeaders.worker.js');
    const buffer = zip([
      ['__proto__', 'untrusted'],
      ['xl/workbook.xml', '<sheets><sheet name="Synthetic" sheetId="1"/></sheets>'],
      ['xl/worksheets/sheet1.xml', '<row><c t="inlineStr"><is><t>Patient Name</t></is></c></row>'],
    ]);
    await worker.onmessage({ data: { name: 'synthetic.xlsx', arrayBuffer: async () => buffer } });
    expect(worker.postMessage).toHaveBeenCalledWith({
      sheets: [{ name: 'Synthetic', headers: ['Patient Name'] }],
    });
  });
});
