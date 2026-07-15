/**
 * Print a client-built HTML document **on the current page** via a hidden iframe — the same
 * in-tab behaviour as the PDF prints (bills, case paper). Replaces the old pattern of opening a
 * blank new tab (window.open('', '_blank')) and writing the HTML into it, which left a stray tab
 * and was popup-blocked.
 *
 * Many of our HTML builders embed their own `<script>…window.print()…</script>` to auto-print in
 * the new tab. Inside the iframe that would fire in addition to this util's own print() call and
 * pop the dialog twice, so we strip such scripts and make this util the single print trigger.
 *
 * @param {string} rawHtml full HTML document string
 */
export const printHtml = (rawHtml) => {
    // Strip EVERY <script>…</script> block before writing to the iframe. This removes the
    // builders' own auto-print scripts (so the dialog fires once) and defends the print sink.
    // Loop until the string stops changing: a single pass can leave a residual "<script>" when
    // tags overlap (e.g. "<scr<script>ipt>"), which CodeQL flags as incomplete multi-character
    // sanitization. Interpolated data is already HTML-escaped by the callers (utils/escapeHtml).
    let html = String(rawHtml || '');
    let previous;
    do {
        previous = html;
        html = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
    } while (html !== previous);

    const iframe = document.createElement('iframe');
    iframe.style.position = 'fixed';
    iframe.style.left = '-10000px';
    iframe.style.width = '210mm';
    iframe.style.height = '297mm';
    iframe.style.border = '0';
    document.body.appendChild(iframe);

    const doc = iframe.contentWindow.document;
    doc.open();
    doc.write(html);
    doc.close();

    // document.write content is ready synchronously; give images/styles a moment, then print.
    setTimeout(() => {
        try { iframe.contentWindow.focus(); iframe.contentWindow.print(); } catch { /* ignore */ }
    }, 400);
    // Revoke well after the print dialog has had time to render.
    setTimeout(() => { try { iframe.remove(); } catch { /* ignore */ } }, 120000);
};

export default printHtml;
