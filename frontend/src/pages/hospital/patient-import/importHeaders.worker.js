import Papa from 'papaparse';
import readExcelFile from 'read-excel-file/web-worker';

// Only headers leave this short-lived worker. Patient rows are neither retained by the UI
// nor transformed/re-uploaded. The backend always receives the original File.
self.onmessage = async ({ data: file }) => {
  try {
    if (/\.csv$/i.test(file.name)) {
      Papa.parse(file, {
        delimiter: ',',
        skipEmptyLines: 'greedy',
        preview: 1,
        complete: (result) => {
          if (result.errors.length)
            self.postMessage({
              error: 'The CSV header could not be read. Check its quoting and commas.',
            });
          else self.postMessage({ sheets: [{ name: '', headers: result.data[0] || [] }] });
        },
        error: () => self.postMessage({ error: 'The CSV file could not be read.' }),
      });
    } else {
      const sheets = await readExcelFile(await file.arrayBuffer());
      self.postMessage({
        sheets: sheets.map(({ sheet, data }) => ({
          name: sheet,
          headers:
            data.find((row) =>
              row.some((value) => value !== null && String(value).trim() !== '')
            ) || [],
        })),
      });
    }
  } catch {
    self.postMessage({
      error: 'The file could not be read. Save it as CSV or XLSX and try again.',
    });
  }
};
