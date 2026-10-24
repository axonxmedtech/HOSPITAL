# Bundled fonts

## NotoSansDevanagari-Regular.ttf

Embedded in prescription and case-paper PDFs so Marathi and Hindi food-timing labels
render (see `service/pdf/PdfLayoutHelper`). Without it the PDF layer falls back to
Helvetica and Devanagari text does not render.

- Family: Noto Sans Devanagari
- Copyright 2022 The Noto Project Authors (https://github.com/notofonts/devanagari)
- Licence: SIL Open Font License, Version 1.1 — full text in `OFL.txt`, retrieved from
  https://raw.githubusercontent.com/notofonts/devanagari/main/OFL.txt

`OFL.txt` ships next to the font inside the JAR: the OFL requires the copyright notice
and licence to accompany the font software wherever it is redistributed. The binary is
unmodified; do not rename it, since the OFL forbids redistributing it under a reserved
font name if it has been altered.
