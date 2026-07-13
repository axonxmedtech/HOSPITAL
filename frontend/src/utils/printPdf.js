import apiClient from '../services/apiService';

/**
 * Fetch a PDF from the API and open the browser's print dialog for it **on the current page**,
 * via a hidden iframe — the same way consultation documents and the clinical forms print.
 *
 * Why not window.open(url): that leaves the user on a stray tab, is blocked by popup blockers,
 * and needed the JWT in the query string to authenticate. Here the blob is fetched through
 * apiClient (Authorization header), so no token ever lands in a URL.
 *
 * @param {string} endpointPath API path, e.g. `/hospital/billing/12/pdf`
 * @returns {Promise<boolean>} true once the print dialog was invoked, false if the fetch failed
 */
/**
 * Print a PDF Blob you already have (e.g. from a service that returns responseType:'blob')
 * on the current page via a hidden iframe. Same in-tab behaviour as printPdf.
 * @param {Blob} blob a PDF blob
 * @returns {Promise<boolean>}
 */
export const printBlob = (blob) => new Promise((resolve) => {
    try {
        const blobUrl = URL.createObjectURL(blob);
        const iframe = document.createElement('iframe');
        iframe.style.position = 'fixed';
        iframe.style.left = '-10000px';
        iframe.style.width = '210mm';
        iframe.style.height = '297mm';
        iframe.style.border = '0';
        iframe.onload = () => {
            setTimeout(() => {
                try { iframe.contentWindow.focus(); iframe.contentWindow.print(); } catch { /* ignore */ }
                resolve(true);
            }, 300);
            setTimeout(() => { URL.revokeObjectURL(blobUrl); iframe.remove(); }, 120000);
        };
        iframe.src = blobUrl;
        document.body.appendChild(iframe);
    } catch { resolve(false); }
});

export const printPdf = (endpointPath) =>
    apiClient.get(endpointPath, { responseType: 'blob' })
        .then((resp) => printBlob(new Blob([resp.data], { type: 'application/pdf' })))
        .catch(() => false);

export default printPdf;
