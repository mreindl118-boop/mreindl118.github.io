#!/usr/bin/env python3
"""Set 8d: put the Camp Washington Skatepark into the portfolio book, after rebuild_portfolio.py.

The skatepark leads the CRC section, right after its title page:

  * new p. 21 (right): the L-1 site plan with its nine numbered callouts, caption 5.4, and a plan key
    set like the title pages' meta row (ink rule, mono label, thin column rule);
  * new p. 22 (left): the L-3 exploded axon (5.5), the L-2 assembled axon (5.6) and an eye-level view of
    the massing model (5.7), with a credit and note row in the same meta-row style;
  * the old p. 21 (installation photos) becomes p. 23, its figure numbers 5.5 and 5.6 re-set as 5.8 and 5.9;
  * p. 20 (CRC title page): the mono subtitle now leads with the skatepark;
  * p. 2 (Contents): CRC's sub-line leads with the skatepark, and the page numbers after it move by two
    (Lunkenheimer 24, Hyundai 26, Evans 29, Green Roof 32);
  * every later page moves by two: 37 pages, blank pages 25 and 28, 19 spreads.

Everything is set in the book's own measured style (fits against the published pages, 4x supersampled):
running heads IBM Plex Mono 24 px, 3 px tracking, baseline 143.5, left at x 120, right labels ending at
x 1580 with their trailing tracking, rule rows 175-178 (copied from the pages themselves); figure
captions exactly as rebuild_portfolio.py sets them (Plex Mono 24.5 px figure numbers in pine
(48,69,56), Plex Sans Text 29.5 px text in (53,57,53), 34 px gap, 38 px leading, baseline 51 px below
the image); meta rows as p. 20 sets them (2 px ink rule, Plex Mono 22.5 px labels with 3.5 px tracking,
Plex Sans values (30 px there, 27 px in the new rows), 3 px column rules); paper (243,240,231); images
on the 120-1580 measure from y 263, the last caption on baseline 2205, inside the 110 px caption
extension below 2200.

Credit, from the owner: Hunger Skateparks designed the park; Matthew documented it for the Cincinnati
Recreation Commission (L-1 site plan, L-2 3D massing, L-3 exploded axon). Nothing here says or implies
he designed it. Feature 7 is "Coney gap" everywhere (the nickname is unsettled: "Glizzy" in the owner's notes, "Glitzy"
on the L-1 sheet). Hunger's rendering, the aerial photo and the August 2026 site photos are never used.

Inputs
  default      the repo's current 35-page book: assets/Matthew_Reindl_Portfolio.pdf (each page's JPEG) and
               assets/portfolio/spread-NN.webp. Untouched pages keep their JPEG streams byte for byte, and
               untouched spreads are the same files under their new numbers. An edited page is re-encoded
               from its own published pixels (PDF: the page JPEG; web: its half of the old spread), so the
               rest of that page moves only by codec noise.
  --pages DIR  rebuild_portfolio.py's lossless page PNGs (its PF_OUT, p01.png .. p35.png) instead; every
               output is then encoded from them, as the pipeline does (run it right after the pipeline, whose
               35-page spreads.json still supplies the spread captions).
  The two new pages come from their masters in _drafts/originals/portfolio/ (PNG, 1700 x 2310).
  --compose PRINT_DIR re-draws those masters first: PRINT_DIR is render_skatepark_set.py's --print folder
               (cw-exploded-alpha.png, cw-axon.png, cw-eye-2.png); the plan is drawn fresh by
               draw_skatepark_plan.py's build() (needs Python Playwright + Chromium).

Outputs (in the repo): the 37-page PDF, spread-00..18.webp + spreads.json, the CRC project's pf-10.webp and
pf-11.webp. --out DIR also writes the 37 final pages as PNG. The script refuses a book that already has
the skatepark pages (spreads.json or the PDF not at 35 pages).

Run from the repo root:
  python3 -B _drafts/tools/insert_crc_skatepark_pages.py [--compose PRINT_DIR] [--pages DIR] [--out DIR]
Then fix the "(NN MB)" PDF size labels by hand if the printed size changed (CLAUDE.md lists them).
Fonts: _drafts/tools/fonts/ (IBM Plex TTFs from npm @ibm/plex-sans 1.1.0 / @ibm/plex-mono 2.5.0, OFL).
"""
import argparse, glob, io, json, math, os, shutil, sys

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, '_drafts/tools')
FONTS = os.path.join(TOOLS, 'fonts')
MASTERS = os.path.join(ROOT, '_drafts/originals/portfolio')
NEW_PAGES = {21: 'p21-camp-washington-site-plan.png', 22: 'p22-camp-washington-massing-model.png'}
PDF = 'assets/Matthew_Reindl_Portfolio.pdf'
PF_DIR = 'assets/portfolio'
CRC = 'content/projects/cincinnati-recreation-commission'

W, H, EXT = 1700, 2200, 110
HH = H + EXT
PAPER = np.array([243, 240, 231], np.uint8)
SANS_TEXT, SANS, MONO, MONO_MED = 'IBMPlexSans-Text.ttf', 'IBMPlexSans-Regular.ttf', 'IBMPlexMono-Regular.ttf', 'IBMPlexMono-Medium.ttf'

# rebuild_portfolio.py's caption style (re-measured: re-setting its captions this way matches them within JPEG noise)
CAP_SIZE, CAP_INK, NUM_SIZE, NUM_INK, CAP_GAP, LEAD = 29.5, (53, 57, 53), 24.5, (48, 69, 56), 34, 38
CAP_DROP, CAP_TO_IMG = 51, 70          # image bottom -> caption baseline; caption baseline -> next image
# the export's own lettering, fitted on pp. 2, 20 and 21: (font, px, tracking, colour)
HEAD = (MONO, 24, 3, (104, 104, 96))         # running heads, baseline 143.5
SUBTITLE = (MONO, 25.5, 3.176, (61, 75, 63))   # title-page subtitle (p. 20: x 345, baseline 516)
TOC_SUB = (SANS, 28.75, 0.05, (101, 102, 93))  # Contents sub-lines (CRC: x 274.25, baseline 1507.5)
LABEL = (MONO, 22.5, 3.5, (140, 136, 125))     # meta labels (TYPE, ROLE ...), baseline rule + 46.5
VALUE = (SANS, 30, 0, (49, 49, 45))            # meta values, first baseline rule + 91.5
FIGNUM = (MONO, 26, 0, (66, 78, 66))           # the export's own figure numbers (p. 21's "5.5")
META_RULE = [(108, 107, 102), (32, 31, 26), (142, 141, 136)]   # p. 20 rows 599-601
COL_RULE = [(216, 214, 199), (215, 213, 198), (222, 220, 205)]  # p. 20 columns 486-488
PINE = (46, 74, 59)
X0, X1, TOP, LAST_BASE = 120, 1580, 263, 2205
HEAD_ROWS = (96, 192)                  # the running head band, copied from a page of the same side

# the skatepark's plan key: plan-geometry.js callout labels, feature 7 without the unsettled nickname
KEY = ['Flow bowl, mound & stepped ledges', 'Peanut bowl — 3.5′ & 7′ ends, C-stamp band',
       'Blue wave bank & red manual pad', 'Hip bank with red kicker', 'Corner quarter-pipe pocket',
       'Street plaza — ledges, rails, pad', 'Coney gap jump', 'Mural wall-ride, bike racks & entry',
       'Fish-scale mosaic seating']
CAPTIONS = {
    '5.4': 'Camp Washington Skatepark, site plan (L-1) as built August 2026; park design by Hunger Skateparks',
    '5.5': 'Exploded axon (L-3): the massing model lifted in five layers — site, concrete slab, bowls and '
           'banks (1–5), street features and entry (6–8), mosaic seating (9)',
    '5.6': '3D massing model (L-2), the nine features numbered; Stock Ave behind',
    '5.7': 'Massing model view: the peanut bowl (2) and the fish-scale mosaic seating (9)',
}
CREDIT = 'Park design: Hunger Skateparks. Documentation for the Cincinnati Recreation Commission.'
NOTE = 'Massing from the L-1 plan geometry and August 2026 photos; heights and transitions estimated, not for construction.'
# the plan: draw_skatepark_plan.build() at 800 x 840 CSS px, drawn at 4x, set 1460 px wide
PLAN_CROP, PLAN_CSS, PLAN_HUD = (205, 409, 445, 445 * 840 / 800), (800, 840), 'll'

SPREAD_CAPTIONS = {10: '05 Cincinnati Recreation Commission — Camp Washington Skatepark site plan',
                   11: '05 Cincinnati Recreation Commission — skatepark massing model + planting installation'}
REPORT = []


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def paint(a, x0, y0, x1, y1):
    a[int(y0):int(y1), int(x0):int(x1)] = PAPER


def text_ss(a, s, style, x, base, ss=4):
    """Set one line in the export's manner (unhinted, fractional position) by 4x supersampling."""
    name, size, track, color = style
    f = font(name, size * ss)
    adv = [f.getlength(c) / ss for c in s]
    width = sum(adv) + track * (len(s) - 1)
    asc, desc = (v / ss for v in f.getmetrics())
    bx0, by0 = int(math.floor(x)) - 3, int(math.floor(base - asc)) - 3
    bx1, by1 = int(math.ceil(x + width)) + 4, int(math.ceil(base + desc)) + 3
    img = Image.new('L', ((bx1 - bx0) * ss, (by1 - by0) * ss), 0)
    d = ImageDraw.Draw(img)
    if track:
        xx = (x - bx0) * ss
        for c, w in zip(s, adv):
            d.text((xx, (base - by0) * ss), c, font=f, fill=255, anchor='ls')
            xx += (w + track) * ss
    else:
        d.text(((x - bx0) * ss, (base - by0) * ss), s, font=f, fill=255, anchor='ls')
    r = np.asarray(img.resize((bx1 - bx0, by1 - by0), Image.BOX)).astype(float)[..., None] / 255
    reg = a[by0:by1, bx0:bx1].astype(float)
    a[by0:by1, bx0:bx1] = (reg * (1 - r) + np.array(color, float) * r + 0.5).astype(np.uint8)
    return width


def text_width(s, style):
    name, size, track, _ = style
    f = font(name, size * 4)
    return sum(f.getlength(c) for c in s) / 4 + track * (len(s) - 1)


def wrap(s, maxw, measure):
    words, lines, cur = s.split(' '), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if measure(t) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


def caption(a, x0, base, num, text, xlimit):
    """A figure caption exactly as rebuild_portfolio.py sets one (PIL at 1x, anchor 'ls')."""
    img = Image.fromarray(a)
    d = ImageDraw.Draw(img)
    f, fm = font(SANS_TEXT, CAP_SIZE), font(MONO, NUM_SIZE)
    tx = x0 + d.textlength(num, font=fm) + CAP_GAP
    lines = wrap(text, xlimit - tx, lambda t: d.textlength(t, font=f))
    d.text((x0, base), num, font=fm, fill=NUM_INK, anchor='ls')
    for i, ln in enumerate(lines):
        d.text((tx, base + i * LEAD), ln, font=f, fill=CAP_INK, anchor='ls')
    a[:] = np.asarray(img)
    REPORT.append(('caption', num, len(lines), int(base + (len(lines) - 1) * LEAD)))
    return base + (len(lines) - 1) * LEAD


def hline(a, y, x0, x1, rows):
    for i, c in enumerate(rows):
        a[y + i, x0:x1] = c


def vline(a, x, y0, y1, cols):
    for i, c in enumerate(cols):
        a[y0:y1, x + i] = c


def head_label(a, label):
    """Replace a running head's right-hand label (it ends at x 1580 with its trailing tracking)."""
    paint(a, 1100, 118, 1584, 152)
    n = len(label)
    x = X1 - 0.25 - n * (font(MONO, 96).getlength('M') / 4 + HEAD[2])
    text_ss(a, label, HEAD, x, 143.5)


def paste_rgb(a, im, x, y):
    arr = np.asarray(im.convert('RGB'))
    a[y:y + arr.shape[0], x:x + arr.shape[1]] = arr


def paste_rgba(a, im, x, y):
    """Composite a premultiplied-resized RGBA image onto the page at fractional-free (x, y)."""
    arr = np.asarray(im).astype(float)
    h, w = arr.shape[:2]
    sx0, sy0 = max(0, -x), max(0, -y)
    dx0, dy0 = max(0, x), max(0, y)
    dx1, dy1 = min(W, x + w), min(HH, y + h)
    src = arr[sy0:sy0 + dy1 - dy0, sx0:sx0 + dx1 - dx0]
    al = src[..., 3:4] / 255
    reg = a[dy0:dy1, dx0:dx1].astype(float)
    a[dy0:dy1, dx0:dx1] = (reg * (1 - al) + src[..., :3] * al + 0.5).astype(np.uint8)


def meta_block(a, y_rule, cols, label_style=LABEL, value_style=VALUE, lead=LEAD):
    """A row in the title pages' meta style: ink rule, then per column a mono label and its value lines.
    cols = [(x, width, label or None, [lines])]; column rules sit 25 px before each later column."""
    hline(a, y_rule - 1, X0 - 1, X1, META_RULE)
    last = y_rule
    for k, (x, wdt, lab, lines) in enumerate(cols):
        if lab:
            text_ss(a, lab, label_style, x, y_rule + 46.5)
        for i, ln in enumerate(lines):
            text_ss(a, ln, value_style, x, y_rule + 91.5 + i * lead)
            last = max(last, y_rule + 91.5 + i * lead)
    for x, _, _, _ in cols[1:]:
        vline(a, x - 25, y_rule + 3, int(last + 9), COL_RULE)
    return last


# ---------------------------------------------------------------- edits to existing pages
def edit_contents(a):
    """p. 2: CRC's sub-line leads with the skatepark; the page numbers after CRC move by two."""
    paint(a, 268, 1478, 790, 1519)
    text_ss(a, 'Camp Washington Skatepark, GIS and planting', TOC_SUB, 274.25, 1507.5)
    # numbers exactly as rebuild_portfolio.py sets them (Plex Sans Text 28, right-aligned in the export's boxes)
    rows = [((1549, 1599, 1577, 1618), '24'), ((1549, 1766, 1578, 1784), '26'),
            ((1549, 2004, 1577, 2023), '29'), ((1549, 2170, 1577, 2189), '32')]
    for (x0, y0, x1, y1), _ in rows:
        paint(a, x0 - 8, y0 - 6, x1 + 5, y1 + 7)
    img = Image.fromarray(a)
    d = ImageDraw.Draw(img)
    fnum = font(SANS_TEXT, 28)
    for (x0, y0, x1, y1), label in rows:
        bb = d.textbbox((0, 0), label, font=fnum)
        d.text((x1 - bb[2] + 1, y0 - bb[1]), label, font=fnum, fill=(35, 38, 39))
    a[:] = np.asarray(img)


def edit_title(a):
    """p. 20: the mono subtitle under the CRC title leads with the skatepark."""
    paint(a, 339, 490, 1040, 524)
    text_ss(a, 'CAMP WASHINGTON SKATEPARK · GIS · PLANTING', SUBTITLE, 345, 516)


def edit_installation(a):
    """Old p. 21 (now p. 23): figure numbers 5.5 -> 5.8 and 5.6 -> 5.9, only the number glyphs repainted."""
    # 5.5 is the export's own: replace its last digit in the export's style
    paint(a, 149, 1524, 169, 1555)
    text_ss(a, '8', FIGNUM, 119.5 + 2 * 0.6 * FIGNUM[1], 1550.25)
    # 5.6 was set by rebuild_portfolio.py in the new margin: re-set the number as it did
    paint(a, 116, 2220, 176, 2251)
    img = Image.fromarray(a)
    d = ImageDraw.Draw(img)
    base = H + 22 - d.textbbox((0, 0), 'Tree planting day', font=font(SANS_TEXT, CAP_SIZE), anchor='ls')[1]
    d.text((122, base), '5.9', font=font(MONO, NUM_SIZE), fill=NUM_INK, anchor='ls')
    a[:] = np.asarray(img)


# ---------------------------------------------------------------- the two new pages
def blank_page():
    return np.repeat(np.repeat(PAPER[None, None], W, axis=1), HH, axis=0).copy()


def plan_render():
    sys.path.insert(0, TOOLS)
    import asyncio
    import draw_skatepark_plan as D
    plan = json.loads(open(D.GEOM).read().split('=', 1)[1].strip().rstrip(';'))
    cw, ch = PLAN_CSS
    im = asyncio.run(D.render(D.build(open(D.SVG).read(), plan, PLAN_CROP, 0, cw, ch, PLAN_HUD), cw, ch, 4))
    return im.resize((X1 - X0, round((X1 - X0) * ch / cw)), Image.LANCZOS)


def compose_p21(right_template):
    a = blank_page()
    a[HEAD_ROWS[0]:HEAD_ROWS[1]] = right_template[HEAD_ROWS[0]:HEAD_ROWS[1]]
    head_label(a, 'CAMP WASHINGTON SKATEPARK')
    plan = plan_render()
    paste_rgb(a, plan, X0, TOP)
    base = caption(a, X0, TOP + plan.height + CAP_DROP, '5.4', CAPTIONS['5.4'], X1)
    # plan key, in two columns (bowls and banks 1-5 | street, entry and seating 6-9), last line on 2205
    pitch, n_rows = 48, 5
    y_rule = round(LAST_BASE - 91.5 - (n_rows - 1) * pitch)
    assert y_rule - base >= 60, ('plan key too close to the caption', y_rule, base)
    hline(a, y_rule - 1, X0 - 1, X1, META_RULE)
    text_ss(a, 'PLAN KEY', LABEL, X0, y_rule + 46.5)
    item = (SANS, 27, 0, VALUE[3])
    cols = [X0, 875]
    img_r = 18
    for i, label in enumerate(KEY):
        c, r = divmod(i, n_rows)
        x, b = cols[c], y_rule + 91.5 + r * pitch
        disc(a, x + img_r, b - 9.5, img_r, str(i + 1))
        tx = x + 2 * img_r + 16
        assert text_width(label, item) <= (cols[1] - 45 if c == 0 else X1) - tx, label
        text_ss(a, label, item, tx, b)
    vline(a, cols[1] - 25, y_rule + 3, int(LAST_BASE + 9), COL_RULE)
    REPORT.append(('p21 plan', plan.size, 'caption base', base, 'key rule', y_rule))
    return a


def disc(a, cx, cy, r, numeral, ss=4):
    """A callout badge as the plan draws them: pine disc, paper Plex Mono 500 numeral."""
    S = int(2 * r + 6)
    x0, y0 = int(round(cx - S / 2)), int(round(cy - S / 2))
    img = Image.new('L', (S * ss, S * ss), 0)
    ImageDraw.Draw(img).ellipse([(cx - x0 - r) * ss, (cy - y0 - r) * ss, (cx - x0 + r) * ss, (cy - y0 + r) * ss], fill=255)
    m = np.asarray(img.resize((S, S), Image.BOX)).astype(float)[..., None] / 255
    reg = a[y0:y0 + S, x0:x0 + S].astype(float)
    a[y0:y0 + S, x0:x0 + S] = (reg * (1 - m) + np.array(PINE, float) * m + 0.5).astype(np.uint8)
    f = font(MONO_MED, 20 * ss)
    tw = f.getlength(numeral) / ss
    bb = f.getbbox(numeral, anchor='ls')
    text_ss(a, numeral, (MONO_MED, 20, 0, tuple(int(v) for v in PAPER)), cx - tw / 2, cy - (bb[1] + bb[3]) / 2 / ss)


def compose_p22(left_template, prn):
    a = blank_page()
    a[HEAD_ROWS[0]:HEAD_ROWS[1]] = left_template[HEAD_ROWS[0]:HEAD_ROWS[1]]
    head_label(a, 'MASSING MODEL')
    # exploded axon on the book's paper: its drawn extent (alpha bbox) set to the full 1460 px measure
    ex = Image.open(os.path.join(prn, 'cw-exploded-alpha.png')).convert('RGBA')
    bx0, by0, bx1, by1 = Image.fromarray(np.asarray(ex)[..., 3]).point(lambda v: 255 if v > 8 else 0).getbbox()
    s = (X1 - X0) / (bx1 - bx0)
    exs = ex.convert('RGBa').resize((round(ex.width * s), round(ex.height * s)), Image.LANCZOS).convert('RGBA')
    ox, oy = round(X0 - bx0 * s), round(TOP - by0 * s)
    paste_rgba(a, exs, ox, oy)
    bottom = oy + round(by1 * s)
    base = caption(a, X0, bottom + CAP_DROP, '5.5', CAPTIONS['5.5'], X1)
    # two smaller views, side by side with the book's 50 px gutter
    top2 = base + CAP_TO_IMG
    cw = (X1 - X0 - 50) // 2
    bases = []
    for k, (f, num) in enumerate([('cw-axon.png', '5.6'), ('cw-eye-2.png', '5.7')]):
        im = Image.open(os.path.join(prn, f)).convert('RGB')
        im = im.resize((cw, round(cw * im.height / im.width)), Image.LANCZOS)
        x = X0 + k * (cw + 50)
        paste_rgb(a, im, x, top2)
        bases.append(caption(a, x, top2 + im.height + CAP_DROP, num, CAPTIONS[num], x + cw))
    # credit and note, in the meta-row style, last line on 2205
    colw = (X1 - X0) // 2
    vs = (SANS, 27, 0, VALUE[3])
    c1 = wrap(CREDIT, colw - 50, lambda t: text_width(t, vs))
    c2 = wrap(NOTE, colw - 25, lambda t: text_width(t, vs))
    nl = max(len(c1), len(c2))
    y_rule = round(LAST_BASE - 91.5 - (nl - 1) * LEAD)
    assert y_rule - max(bases) >= 60, ('note row too close to the captions', y_rule, bases)
    meta_block(a, y_rule, [(X0, colw, 'CREDIT', c1), (X0 + colw + 25, colw, 'NOTE', c2)], value_style=vs)
    REPORT.append(('p22 exploded', exs.size, (ox, oy), 'content bottom', bottom, 'views top', top2, 'note rule', y_rule))
    return a


# ---------------------------------------------------------------- book I/O
def jpeg_bytes(a):
    buf = io.BytesIO()
    Image.fromarray(a).save(buf, 'JPEG', quality=88)
    return buf.getvalue()


def load_repo_book(repo):
    """The published 35-page book: per page its JPEG stream, its PDF pixels and its pixels in the old spread."""
    man = json.load(open(os.path.join(repo, PF_DIR, 'spreads.json')))
    doc = pymupdf.open(os.path.join(repo, PDF))
    if man['pages'] != 35 or len(doc) != 35:
        sys.exit('the book is not the 35-page build rebuild_portfolio.py writes (already inserted?): pages=%s pdf=%d'
                 % (man['pages'], len(doc)))
    jpg = [doc.extract_image(p.get_images(full=True)[0][0])['image'] for p in doc]
    spreads_bytes = [open(os.path.join(repo, PF_DIR, s['src']), 'rb').read() for s in man['spreads']]
    web = {}
    for s, b in zip(man['spreads'], spreads_bytes):
        im = np.asarray(Image.open(io.BytesIO(b)).convert('RGB'))
        for j, p in enumerate(s['pages']):
            web[p] = im[:, j * W:(j + 1) * W].copy()
    pdfpx = {i + 1: np.asarray(Image.open(io.BytesIO(b)).convert('RGB')).copy() for i, b in enumerate(jpg)}
    return man, jpg, spreads_bytes, pdfpx, web


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--repo', default=ROOT)
    ap.add_argument('--pages', help="rebuild_portfolio.py's page PNGs (p01.png .. p35.png)")
    ap.add_argument('--compose', metavar='PRINT_DIR', help='re-draw the two new page masters from these print masters')
    ap.add_argument('--out', help='also write the 37 final pages here as PNG')
    a = ap.parse_args()
    repo = a.repo

    man, jpg, old_spreads, pdfpx, web = load_repo_book(repo)
    if a.pages:          # the pipeline's lossless pages replace the published pixels everywhere
        masters = {p: np.asarray(Image.open(os.path.join(a.pages, 'p%02d.png' % p)).convert('RGB')).copy() for p in range(1, 36)}
        pdfpx, web, jpg, old_spreads = masters, masters, None, None

    if a.compose:
        os.makedirs(MASTERS, exist_ok=True)
        tmpl_r, tmpl_l = pdfpx[21], pdfpx[20]     # running heads from a right and a left page of this section
        for p, arr in ((21, compose_p21(tmpl_r)), (22, compose_p22(tmpl_l, a.compose))):
            Image.fromarray(arr).save(os.path.join(MASTERS, NEW_PAGES[p]), optimize=True)
    new = {p: np.asarray(Image.open(os.path.join(MASTERS, n)).convert('RGB')).copy() for p, n in NEW_PAGES.items()}
    for p, arr in new.items():
        assert arr.shape == (HH, W, 3), (p, arr.shape)

    # old page -> new page; edits per old page
    edits = {2: edit_contents, 20: edit_title, 21: edit_installation}
    order = list(range(1, 21)) + ['n21', 'n22'] + list(range(21, 36))     # 37 entries
    assert len(order) == 37
    blank_old = {23, 26}                                                  # rebuild_portfolio.py's blank pages
    pages_pdf, pages_web, src_jpg = [], [], []
    for o in order:
        if isinstance(o, str):
            arr = new[int(o[1:])]
            pages_pdf.append(arr); pages_web.append(arr); src_jpg.append(None)
            continue
        if o in edits:
            pa, wa = pdfpx[o].copy(), web.get(o, pdfpx[o]).copy()
            edits[o](pa)
            if wa is not pa:
                edits[o](wa)
            pages_pdf.append(pa); pages_web.append(wa); src_jpg.append(None)
        else:
            pages_pdf.append(pdfpx[o]); pages_web.append(web.get(o)); src_jpg.append(jpg[o - 1] if jpg else None)
    is_blank = [isinstance(o, int) and o in blank_old for o in order]
    n = len(order)

    # PDF: untouched pages keep their JPEG streams; the PDF keeps its blank pages (print imposition)
    doc = pymupdf.open()
    kf = 72 / 200
    for arr, j in zip(pages_pdf, src_jpg):
        b = j if j is not None else jpeg_bytes(arr)
        pg = doc.new_page(width=W * kf, height=HH * kf)
        pg.insert_image(pg.rect, stream=b)
    # spreads: 0 = cover, k = pages 2k and 2k+1; a page facing a blank is shown alone
    manifest, files = [], []
    old_index = {tuple(s['pages']): k for k, s in enumerate(man['spreads'])}
    old_caps = {tuple(s['pages']): s['caption'] for s in man['spreads']}
    for k in range(0, n // 2 + 1):
        pg = [1] if k == 0 else [p for p in (2 * k, 2 * k + 1) if p <= n]
        shown = [p for p in pg if not is_blank[p - 1]]
        olds = tuple(order[p - 1] for p in shown)
        untouched = all(isinstance(o, int) and o not in edits for o in olds)
        name = 'spread-%02d.webp' % k
        if old_spreads is not None and untouched and olds in old_index:
            data = old_spreads[old_index[olds]]
        else:
            buf = io.BytesIO()
            Image.fromarray(np.concatenate([pages_web[p - 1] for p in shown], axis=1)).save(buf, 'WEBP', quality=85)
            data = buf.getvalue()
        cap = SPREAD_CAPTIONS.get(k) or old_caps[olds]
        files.append((name, data))
        manifest.append({'src': name, 'pages': shown, 'caption': cap})
    assert len(files) == 19, len(files)
    for f in glob.glob(os.path.join(repo, PF_DIR, 'spread-*.webp')):
        os.remove(f)
    for name, data in files:
        open(os.path.join(repo, PF_DIR, name), 'wb').write(data)
    with open(os.path.join(repo, PF_DIR, 'spreads.json'), 'w') as fh:
        json.dump({'pages': n, 'spreads': manifest}, fh, indent=1)
        fh.write('\n')
    # the CRC project page shows both of its spreads (other projects' pf files keep their names and content)
    for k in (10, 11):
        shutil.copyfile(os.path.join(repo, PF_DIR, 'spread-%02d.webp' % k), os.path.join(repo, CRC, 'pf-%02d.webp' % k))
    doc.save(os.path.join(repo, PDF), garbage=4, deflate=True)
    if a.out:
        os.makedirs(a.out, exist_ok=True)
        for i, arr in enumerate(pages_pdf, 1):
            Image.fromarray(arr).save(os.path.join(a.out, 'p%02d.png' % i))
    size = os.path.getsize(os.path.join(repo, PDF))
    for r in REPORT:
        print(r)
    print('pages', n, 'spreads', len(files), 'pdf pages', len(doc), 'pdf', size, 'bytes =',
          round(size / 1e6, 1), 'MB (label "(%d MB)")' % round(size / 1e6))


if __name__ == '__main__':
    main()
