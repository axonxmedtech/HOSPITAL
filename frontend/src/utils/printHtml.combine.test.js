import { describe, expect, it } from 'vitest';
import { combinePrintDocuments, scopeCss } from './printHtml';

describe('scopeCss', () => {
  it('scopes every rule to the wrapper and drops @page', () => {
    const css = '@page { size: A4; } html, body { margin:0; } .fld, .sheet b { color:red; }';
    expect(scopeCss(css, 'd0')).toBe('.d0, .d0 { margin:0; }.d0 .fld, .d0 .sheet b { color:red; }');
  });
});

describe('combinePrintDocuments', () => {
  const form = `<!doctype html><html><head><style>@page { size: A4; margin: 7mm; } .fld { color:red; }</style></head>
    <body><div class="fld">Form</div><script>window.print()</script></body></html>`;
  const consent = `<!doctype html><html><head><style>@page { size: A4; margin: 8mm; } .fld { color:blue; }</style></head>
    <body><div class="fld">Consent</div></body></html>`;

  it('puts each document on its own page with its own styles', () => {
    const out = new DOMParser().parseFromString(
      combinePrintDocuments([form, consent]),
      'text/html'
    );
    const sections = out.querySelectorAll('section');
    expect(sections).toHaveLength(2);
    expect(sections[0].className).toBe('print-doc-0');
    expect(sections[0].getAttribute('style')).toContain('break-after:page');
    expect(sections[1].getAttribute('style')).toBe('');
    expect(sections[1].textContent).toContain('Consent');
    const css = out.querySelector('style').textContent;
    expect(css).toContain('.print-doc-0 .fld { color:red; }');
    expect(css).toContain('.print-doc-1 .fld { color:blue; }');
    expect(css.match(/@page/g)).toHaveLength(1);
    expect(out.querySelector('script')).toBeNull();
  });
});
