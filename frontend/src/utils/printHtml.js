/**
 * Print a client-built HTML document **on the current page** via a hidden iframe — the same
 * in-tab behaviour as the PDF prints (bills, case paper). Replaces the old pattern of opening a
 * blank new tab (window.open('', '_blank')) and writing the HTML into it, which left a stray tab
 * and was popup-blocked.
 *
 * Many of our HTML builders embed their own `<script>…window.print()…</script>` to auto-print in
 * the new tab. Inside the iframe that would fire in addition to this util's own print() call and
 * pop the dialog twice, so we strip every script and make this util the single print trigger.
 *
 * @param {string} rawHtml full HTML document string
 */
export const printHtml = (rawHtml) => {
  // Remove every <script> before the document reaches the iframe, using the browser's own HTML
  // parser rather than a regex. DOMParser.parseFromString does NOT execute scripts, so parsing is
  // safe, and querySelectorAll('script') finds every script element the parser recognises —
  // including the awkward forms a regex misses ("</script bar>", overlapping "<scr<script>…"),
  // with none of the reappearance foot-guns of string replacement (this is the robust fix for
  // CodeQL js/incomplete-multi-character-sanitization + js/bad-tag-filter). Interpolated data is
  // also HTML-escaped by the callers (utils/escapeHtml).
  const parsed = new DOMParser().parseFromString(String(rawHtml || ''), 'text/html');
  parsed.querySelectorAll('script').forEach((script) => script.remove());

  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.left = '-10000px';
  iframe.style.width = '210mm';
  iframe.style.height = '297mm';
  iframe.style.border = '0';
  document.body.appendChild(iframe);

  const doc = iframe.contentDocument || iframe.contentWindow.document;
  // Force standards mode (a bare iframe is quirks mode, which changes print layout): write only a
  // constant doctype, then swap the iframe's <html> for the parsed, script-free one. The doctype
  // node survives replaceChild, so standards mode sticks. No HTML string is written to the sink.
  doc.open();
  doc.write('<!DOCTYPE html>');
  doc.close();
  doc.replaceChild(doc.importNode(parsed.documentElement, true), doc.documentElement);

  // Content is in place synchronously; give images/styles a moment, then print.
  setTimeout(() => {
    try {
      iframe.contentWindow.focus();
      iframe.contentWindow.print();
    } catch {
      /* ignore */
    }
  }, 400);
  // Revoke well after the print dialog has had time to render.
  setTimeout(() => {
    try {
      iframe.remove();
    } catch {
      /* ignore */
    }
  }, 120000);
};

export default printHtml;
