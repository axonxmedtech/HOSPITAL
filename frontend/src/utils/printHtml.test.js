import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import printHtml from './printHtml';

const iframeDoc = () => {
  const iframe = document.querySelector('iframe');
  return iframe && (iframe.contentDocument || iframe.contentWindow.document);
};

describe('printHtml', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = '';
  });
  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('renders the document content into a hidden off-screen iframe', () => {
    printHtml('<html><body><h1 id="t">Case Paper</h1></body></html>');
    const iframe = document.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe.style.left).toBe('-10000px');
    expect(iframeDoc().querySelector('#t')?.textContent).toBe('Case Paper');
  });

  it('strips <script> elements, including the builders auto-print script', () => {
    printHtml('<html><body><p>ok</p><script>window.print()</script></body></html>');
    const doc = iframeDoc();
    expect(doc.querySelectorAll('script').length).toBe(0);
    expect(doc.querySelector('p')?.textContent).toBe('ok');
  });

  it('removes scripts written with awkward end tags a regex would miss', () => {
    // "</script bar>" and "</script >" both close a script per the HTML parser, so the parser-based
    // removal drops these (and their code) where a "</script\\s*>" regex would have leaked them.
    printHtml(
      '<html><body><p>keep</p><script>evil()</script bar><script>more()</script ></body></html>'
    );
    const doc = iframeDoc();
    expect(doc.querySelectorAll('script').length).toBe(0);
    expect(doc.documentElement.innerHTML).not.toContain('evil()');
    expect(doc.documentElement.innerHTML).not.toContain('more()');
    expect(doc.querySelector('p')?.textContent).toBe('keep');
  });

  it('keeps the document in standards mode (doctype present)', () => {
    printHtml('<html><body>x</body></html>');
    expect(iframeDoc().doctype).not.toBeNull();
  });

  it('handles null/undefined/empty input without throwing', () => {
    expect(() => printHtml(null)).not.toThrow();
    expect(() => printHtml(undefined)).not.toThrow();
    expect(() => printHtml('')).not.toThrow();
  });

  it('triggers print() on the iframe after the render delay', () => {
    printHtml('<html><body>x</body></html>');
    const win = document.querySelector('iframe').contentWindow;
    const printSpy = vi.spyOn(win, 'print').mockImplementation(() => {});
    vi.advanceTimersByTime(400);
    expect(printSpy).toHaveBeenCalledTimes(1);
  });

  it('removes the iframe after the cleanup delay', () => {
    printHtml('<html><body>x</body></html>');
    const win = document.querySelector('iframe').contentWindow;
    vi.spyOn(win, 'print').mockImplementation(() => {});
    expect(document.querySelector('iframe')).not.toBeNull();
    vi.advanceTimersByTime(120001);
    expect(document.querySelector('iframe')).toBeNull();
  });
});
