export const readImportHeaders = (file, signal) =>
  new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./importHeaders.worker.js', import.meta.url), {
      type: 'module',
    });
    const finish = (error, result) => {
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      worker.terminate();
      if (error) reject(error);
      else resolve(result);
    };
    const abort = () => finish(new DOMException('Cancelled', 'AbortError'));
    const timer = setTimeout(
      () =>
        finish(
          new Error(
            'Reading the headers took too long. Try a smaller file or export the selected sheet as CSV.'
          )
        ),
      60000
    );
    worker.onmessage = ({ data }) =>
      data.error ? finish(new Error(data.error)) : finish(null, data.sheets);
    worker.onerror = () =>
      finish(new Error('The file could not be read. Save it as CSV or XLSX and try again.'));
    if (signal?.aborted) {
      abort();
      return;
    }
    signal?.addEventListener('abort', abort, { once: true });
    worker.postMessage(file);
  });
