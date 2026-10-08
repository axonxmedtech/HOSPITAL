// Only headers leave this short-lived worker. Patient rows are neither retained by the UI
// nor transformed/re-uploaded. The backend always receives the original File.

function parseCsvHeaders(text) {
  const lines = text.split(/\r\n|\r|\n/).filter((l) => l.trim().length > 0);
  if (!lines.length) return [];
  const line = lines[0];
  const headers = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      headers.push(cur.trim().replace(/^["']|["']$/g, ''));
      cur = '';
    } else {
      cur += ch;
    }
  }
  headers.push(cur.trim().replace(/^["']|["']$/g, ''));
  return headers.filter((h) => h.length > 0);
}

async function readZipEntries(buffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  const entries = {};

  let eocdOffset = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) return entries;

  const cdOffset = view.getUint32(eocdOffset + 16, true);
  const cdCount = view.getUint16(eocdOffset + 10, true);

  let p = cdOffset;
  for (let i = 0; i < cdCount; i++) {
    if (view.getUint32(p, true) !== 0x02014b50) break;
    const compression = view.getUint16(p + 10, true);
    const compressedSize = view.getUint32(p + 20, true);
    const fileNameLen = view.getUint16(p + 28, true);
    const extraLen = view.getUint16(p + 30, true);
    const commentLen = view.getUint16(p + 32, true);
    const localHeaderOffset = view.getUint32(p + 42, true);

    const nameBytes = bytes.subarray(p + 46, p + 46 + fileNameLen);
    const name = new TextDecoder().decode(nameBytes);

    const localFileNameLen = view.getUint16(localHeaderOffset + 26, true);
    const localExtraLen = view.getUint16(localHeaderOffset + 28, true);
    const dataOffset = localHeaderOffset + 30 + localFileNameLen + localExtraLen;
    const compressedData = bytes.subarray(dataOffset, dataOffset + compressedSize);

    entries[name] = async () => {
      if (compression === 0) {
        return new TextDecoder().decode(compressedData);
      }
      if (compression === 8 && typeof DecompressionStream !== 'undefined') {
        const ds = new DecompressionStream('deflate-raw');
        const writer = ds.writable.getWriter();
        writer.write(compressedData);
        writer.close();
        const decompressedBuffer = await new Response(ds.readable).arrayBuffer();
        return new TextDecoder().decode(decompressedBuffer);
      }
      return '';
    };

    p += 46 + fileNameLen + extraLen + commentLen;
  }
  return entries;
}

async function parseXlsxHeaders(buffer) {
  const zip = await readZipEntries(buffer);

  const sharedStrings = [];
  if (zip['xl/sharedStrings.xml']) {
    const xml = await zip['xl/sharedStrings.xml']();
    const matches = xml.matchAll(/<si>(.*?)<\/si>/gs);
    for (const match of matches) {
      const textMatches = [...match[1].matchAll(/<t[^>]*>(.*?)<\/t>/gs)];
      sharedStrings.push(textMatches.map((m) => m[1]).join(''));
    }
  }

  const sheets = [];
  if (zip['xl/workbook.xml']) {
    const wbXml = await zip['xl/workbook.xml']();
    const sheetMatches = wbXml.matchAll(/<sheet\s+[^>]*name="([^"]+)"[^>]*sheetId="([^"]+)"/g);
    let idx = 1;
    for (const match of sheetMatches) {
      const sheetName = match[1];
      const sheetFile = `xl/worksheets/sheet${idx}.xml`;
      const headers = [];
      if (zip[sheetFile]) {
        const sheetXml = await zip[sheetFile]();
        const firstRowMatch = sheetXml.match(/<row[^>]*>(.*?)<\/row>/s);
        if (firstRowMatch) {
          const cellMatches = firstRowMatch[1].matchAll(
            /<c\s+[^>]*?(?:t="([^"]*)")?[^>]*>(?:<v>([^<]*)<\/v>|<is><t>([^<]*)<\/t><\/is>)?/g
          );
          for (const cm of cellMatches) {
            const type = cm[1];
            const val = cm[2] ?? cm[3] ?? '';
            if (type === 's' && sharedStrings[parseInt(val, 10)] !== undefined) {
              headers.push(sharedStrings[parseInt(val, 10)]);
            } else if (type === 'inlineStr' || type === 'str') {
              headers.push(val);
            } else if (val) {
              headers.push(val);
            }
          }
        }
      }
      sheets.push({
        name: sheetName,
        headers: headers.filter((h) => h !== undefined && h !== null && String(h).trim() !== ''),
      });
      idx++;
    }
  }

  if (!sheets.length) {
    sheets.push({ name: 'Sheet1', headers: [] });
  }
  return sheets;
}

self.onmessage = async ({ data: file }) => {
  try {
    if (/\.csv$/i.test(file.name)) {
      const text = await file.slice(0, 65536).text();
      const headers = parseCsvHeaders(text);
      if (!headers.length) {
        self.postMessage({
          error: 'The CSV header could not be read. Check its quoting and commas.',
        });
      } else {
        self.postMessage({ sheets: [{ name: '', headers }] });
      }
    } else {
      const buffer = await file.arrayBuffer();
      const sheets = await parseXlsxHeaders(buffer);
      self.postMessage({ sheets });
    }
  } catch {
    self.postMessage({
      error: 'The file could not be read. Save it as CSV or XLSX and try again.',
    });
  }
};
