#!/usr/bin/env python3
"""Re-set the contact line on the portfolio book's closing page (p. 37) with the site's contact email.

The closer (drawn by rebuild_portfolio.py, "h31 closer") ends with one grey line under the about note:
'<email>  ·  understorydesignstudio.com', IBM Plex Sans Text 26 px in (90, 95, 88) at x 470, 16 px under
the note's last line. This script repaints that line's box in the page's own paper and sets the new line
in the same face, size, colour and origin, on the published pixels of both copies:

  assets/Matthew_Reindl_Portfolio.pdf   page 37's JPEG stream is overwritten in place (quality 88, as the
                                        book tools write; same image object, so no extra copy is left in
                                        the file); every other page keeps its JPEG stream byte for byte
  assets/portfolio/spread-18.webp       pages 36-37; re-encoded at quality 85, as the book tools write

Usage (repo root):  python3 -I _drafts/tools/swap_book_email.py \
                        [--old mreindl118@gmail.com] [--new understorydesignstudio@gmail.com] [--check DIR]
The face is _drafts/tools/fonts/IBMPlexSans-Text.ttf (IBM Plex Sans, "Text" weight 450; --font to use
another copy). The script refuses to run unless the old line is found where the pipeline drew it, and
writes both files only after every check passes.
"""
import argparse, io, os, sys

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFont

PDF = 'assets/Matthew_Reindl_Portfolio.pdf'
SPREAD = 'assets/portfolio/spread-18.webp'
PAGE = 37
W, HH = 1700, 2310
GREY = (90, 95, 88)
NOTE = ("I’m a landscape designer from Cincinnati — MLA, University of Cincinnati DAAP, 2026. "
        "I design landscapes that bring a site’s story to the surface, shaping ecology and everyday "
        "public life together. Thank you for reading.")
TAIL = '  ·  understorydesignstudio.com'


def wrap(text, maxw, f):
    d = ImageDraw.Draw(Image.new('RGB', (4, 4)))
    words, lines, cur = text.split(' '), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= maxw:
            cur = t
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


def mask(text, origin, f):
    m = Image.new('L', (W, HH), 0)
    ImageDraw.Draw(m).text(origin, text, font=f, fill=255)
    return np.asarray(m) > 96


def swap(arr, old, new, origin, f26):
    """arr: a 1700x2310 page. Returns the edited copy and the score of the old line's match."""
    a = arr.copy()
    d = ImageDraw.Draw(Image.new('RGB', (4, 4)))
    ob = d.textbbox(origin, old, font=f26)
    x0, y0, x1, y1 = ob[0] - 6, ob[1] - 6, ob[2] + 6, ob[3] + 6
    g = a[..., :3].astype(float).mean(axis=2)
    m = mask(old, origin, f26)
    ink, bg = g[m].mean(), g[y0:y1, x0:x1][~m[y0:y1, x0:x1]].mean()
    score = bg - ink                                     # the line's glyphs sit darker than the paper round them
    ring = np.concatenate([a[y0 - 4:y0, x0:x1].reshape(-1, 3), a[y1:y1 + 4, x0:x1].reshape(-1, 3)])
    paper = np.median(ring, axis=0).astype(np.uint8)
    a[y0:y1, x0:x1] = paper
    img = Image.fromarray(a)
    ImageDraw.Draw(img).text(origin, new, font=f26, fill=GREY)
    return np.asarray(img).copy(), score, (x0, y0, x1, y1), paper


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--font', default='_drafts/tools/fonts/IBMPlexSans-Text.ttf')
    ap.add_argument('--old', default='mreindl118@gmail.com')
    ap.add_argument('--new', default='understorydesignstudio@gmail.com')
    ap.add_argument('--check', help='write before/after crops here')
    o = ap.parse_args()
    f30, f26 = ImageFont.truetype(o.font, 30), ImageFont.truetype(o.font, 26)
    ty = 1582 + 44 * len(wrap(NOTE, 1000, f30))
    origin = (470, ty + 16)
    old, new = o.old + TAIL, o.new + TAIL

    doc = pymupdf.open(PDF)
    if doc.page_count != PAGE:
        sys.exit(f'expected a {PAGE}-page book, got {doc.page_count}')
    before = [doc.xref_stream_raw(doc[i].get_images()[0][0]) for i in range(doc.page_count)]
    xref = doc[PAGE - 1].get_images()[0][0]
    pdf_px = np.asarray(Image.open(io.BytesIO(before[PAGE - 1])).convert('RGB'))
    sp = np.asarray(Image.open(SPREAD).convert('RGB'))
    if pdf_px.shape != (HH, W, 3) or sp.shape != (HH, 2 * W, 3):
        sys.exit(f'unexpected sizes {pdf_px.shape} {sp.shape}')

    new_pdf, s1, box, p1 = swap(pdf_px, old, new, origin, f26)
    new_web, s2, _, p2 = swap(sp[:, W:], old, new, origin, f26)
    print('old line match (paper minus ink, grey levels): pdf %.1f, web %.1f; box %s; paper %s %s'
          % (s1, s2, box, p1.tolist(), p2.tolist()))
    if min(s1, s2) < 80:                                 # a found line scores ~125; an absent one ~40
        sys.exit('the old line is not where the pipeline drew it; nothing written')

    buf = io.BytesIO(); Image.fromarray(new_pdf).save(buf, 'JPEG', quality=88)
    jpg = buf.getvalue()
    keys = {k: doc.xref_get_key(xref, k) for k in ('Filter', 'Width', 'Height', 'ColorSpace', 'BitsPerComponent')}
    nobj = doc.xref_length()
    # overwrite the image object's stream in place: the page keeps one image, drawn as before
    doc.update_stream(xref, jpg, compress=False)
    doc.xref_set_key(xref, 'Filter', '/DCTDecode')
    doc.xref_set_key(xref, 'DecodeParms', 'null')
    tmp_pdf, tmp_webp = PDF + '.tmp', SPREAD + '.tmp'
    try:
        doc.save(tmp_pdf, garbage=4, deflate=True)
        doc.close()
        chk = pymupdf.open(tmp_pdf)
        imgs = [chk[i].get_images() for i in range(chk.page_count)]
        after = [chk.xref_stream_raw(im[0][0]) for im in imgs]
        x2 = imgs[PAGE - 1][0][0]
        keys2 = {k: chk.xref_get_key(x2, k) for k in keys}
        drawn = np.asarray(Image.frombytes('RGB', (W, HH), chk[PAGE - 1].get_pixmap(
            matrix=pymupdf.Matrix(W / chk[PAGE - 1].rect.width, HH / chk[PAGE - 1].rect.height), colorspace='rgb').samples))
        problems = []
        if chk.page_count != PAGE: problems.append(f'{chk.page_count} pages')
        if any(len(im) != 1 for im in imgs): problems.append('a page holds more than one image')
        if sum(a == b for a, b in zip(before, after)) != PAGE - 1: problems.append('other page streams changed')
        if after[PAGE - 1] != jpg: problems.append(f'p. {PAGE} stream is not the new JPEG')
        if keys2 != keys: problems.append(f'image dictionary changed {keys} -> {keys2}')
        if chk.xref_length() > nobj: problems.append(f'objects grew {nobj} -> {chk.xref_length()}')
        if np.abs(drawn.astype(int) - new_pdf.astype(int)).mean() > 3: problems.append('p. %d does not draw the new page' % PAGE)
        chk.close()
        if problems:
            sys.exit('nothing written: ' + '; '.join(problems))
        out = sp.copy(); out[:, W:] = new_web
        Image.fromarray(out).save(tmp_webp, 'WEBP', quality=85)
        os.replace(tmp_pdf, PDF)
        os.replace(tmp_webp, SPREAD)
    finally:
        for f in (tmp_pdf, tmp_webp):
            if os.path.exists(f):
                os.remove(f)
    print('wrote', PDF, os.path.getsize(PDF), 'bytes;', SPREAD, os.path.getsize(SPREAD), 'bytes')

    if o.check:
        os.makedirs(o.check, exist_ok=True)
        x0, y0, x1, y1 = box
        crop = (x0 - 360, y0 - 240, x1 + 240, y1 + 80)
        Image.fromarray(pdf_px).crop(crop).save(os.path.join(o.check, 'p37-before.png'))
        Image.fromarray(new_pdf).crop(crop).save(os.path.join(o.check, 'p37-after.png'))


if __name__ == '__main__':
    main()
