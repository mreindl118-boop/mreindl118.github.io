"""Rebuild the portfolio pages from the owner's export (v3halves, 1700x2200 @200dpi):
tight caption re-typesetting, contents renumbering, project pagination with
blank pages, Hyundai side swap, Weaver model spread, closer, spreads + PDF."""
import io, os, glob, json, shutil, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps
import pymupdf

S = '/tmp/claude-0/-home-user-mreindl118-github-io/27912a11-e1ee-5965-b7ca-05d1c446573a'
SP = f'{S}/scratchpad'
SRC = f'{SP}/v3halves'
OUT = f'{SP}/fixed2'
REPO = '/home/user/mreindl118.github.io'
os.makedirs(OUT, exist_ok=True)
W, H, EXT = 1700, 2200, 110
HH = H + EXT
FONT = f'{SP}/plex-text.ttf'
F = lambda n: ImageFont.truetype(FONT, n)
INK = (38, 41, 43)
CAP_INK = (53, 57, 53)
NUM_INK = (48, 69, 56)
MONO = f'{SP}/plex-mono.ttf'
FM = lambda n: ImageFont.truetype(MONO, n)
NUM_SIZE = 24.5
REPORT = []


def load(h):
    return Image.open(f'{SRC}/h{h:02d}.png').convert('RGB')


def profile(img):
    a = np.asarray(img).astype(float)
    top = a[0:100]
    std = top[:, 200:1500].std(axis=1).mean(axis=1)
    rows = np.argsort(std)[:25]
    return np.median(top[rows], axis=0)          # (W,3) paper incl. spine shading


def extend(img, prof):
    a = np.asarray(img).astype(np.uint8)
    ext = np.repeat(prof[None].astype(np.uint8), EXT, axis=0)
    return np.concatenate([a, ext], axis=0)


def paint(arr, x0, y0, x1, y1, prof):
    x0, x1 = max(0, int(x0)), min(W, int(x1))
    arr[int(y0):int(y1), x0:x1] = prof[x0:x1].astype(np.uint8)[None]


def detect_top(g, xa, xb, nlines):
    """Glyph top of a caption block that the export clipped at the page edge."""
    y_lo = 2199 - (nlines * 38 + 34)
    reg = g[y_lo:H, xa:xb] < 140
    keep = reg.mean(axis=0) < 0.6                  # drop leader/vertical lines
    rows = reg[:, keep].any(axis=1)
    top, gap = None, 0
    for i in range(len(rows) - 1, -1, -1):
        if rows[i]:
            top, gap = y_lo + i, 0
        else:
            gap += 1
            if top is not None and gap >= 14:
                break
    return top


def measured_end(g, top, xa, xb):
    reg = g[top:min(top + 30, H), xa:xb] < 140
    keep = g[2050:H, xa:xb].__lt__(140).mean(axis=0) < 0.6
    cols = np.where(reg.any(axis=0) & keep)[0]
    return xa + cols[-1] if len(cols) else None


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


def set_caption(arr, g, prof, x0, xlimit, num, text, nlines=None, color=CAP_INK, gap=34, fixed=29.5):
    """Repaint and re-set one clipped caption; only touches its own text box."""
    f = F(fixed)
    d = ImageDraw.Draw(Image.new('RGB', (4, 4)))
    numw = d.textlength(num, font=FM(NUM_SIZE)) if num else 0
    tx = x0 + (numw + gap if num else 0)
    lines = text if isinstance(text, list) else wrap(text, xlimit - tx, f)
    top = detect_top(g, x0, xlimit, len(lines))
    if top is None or top < 2199 - (len(lines) * 38 + 34):
        REPORT.append(('NO CAPTION FOUND', x0, num, str(text)[:30]))
        return None
    end = measured_end(g, top, x0, xlimit) or xlimit
    rend_end = max(tx + d.textlength(l, font=f) for l in lines)
    paint(arr, x0 - 6, top - 6, min(max(end, rend_end) + 18, xlimit + 12, 1592), H, prof)
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)
    base = top - d.textbbox((0, 0), lines[0], font=f, anchor='ls')[1]
    if num:
        d.text((x0, base), num, font=FM(NUM_SIZE), fill=NUM_INK, anchor='ls')
    for l in lines:
        d.text((tx, base), l, font=f, fill=color, anchor='ls')
        base += 38
    arr[:] = np.asarray(img)
    REPORT.append(('caption', num or '-', int(top), len(lines), int(rend_end), int(end)))
    return top


TEXTS = {
    3: [('1.1', 'Master plan linework, AutoCAD')],
    8: [('1.4', 'Site program and feature systems, axonometric')],
    9: [('1.5', 'Planting zones and schemes: planned plantings, Paddy Creek wetlands, woodland core')],
    10: [('1.7', 'Arched boardwalk across the wetland, perspective, elevation, and section')],
    11: [('1.9', 'Exhibition wall, DAAPWorks'), ('1.11', 'Printed project booklet')],
    12: [('2.1', 'Terrain study model, carved contours around the lake basin')],
    13: [('2.5', 'Section through the belvedere cliff')],
    14: [('3.1', 'Site plan, canopy and bus bays beside Laurel Playground')],
    15: [('3.3', 'Canopy and planted plaza from the crossing')],
    16: [('4.1', 'Site plan, amphitheater rings and planted rooms')],
    17: [('4.3', 'Amphitheater seating, aerial from the street edge')],
    18: [('5.2', 'GIS analysis, series A'), ('5.3', 'GIS analysis, series B')],
    20: [('6.2', 'Site model'), ('6.3', ['Model detail: bowl, terraced seating, covered', 'stage'])],
    21: [('7.1', 'Site plan, Mipo-dong wastewater re-expansion (detail)')],
    22: [('7.2', 'Circular economies: closing the loop, material and energy flows')],
    23: [('8.1', ['Concept 1, dam removal: Mill Creek returned to a wetland corridor, with fields, skate and splash, and', 'crossings along the loop'])],
    24: [('8.4', 'Concept 1, wetland loop and crossings, plan detail')],
    26: [('9.1', 'Roof plan: planted beds, HVAC screen, elevator core, and paved loop')],
    27: [('9.4', ['Existing roof, Clarendon Park Community', 'Center, Chicago']), ('9.5', 'Eye level along the planted beds')],
    28: [('e', 'Clay terrain study')],
    29: [('g', 'Drapery study, charcoal'), ('h', 'Portrait, charcoal')],
}
COLS = {29: [122, 874]}


def clusters(mask, gap):
    xs = np.where(mask)[0]
    if len(xs) == 0:
        return []
    out, s, p = [], xs[0], xs[0]
    for x in xs[1:]:
        if x - p > gap:
            out.append((s, p))
            s = x
        p = x
    out.append((s, p))
    return out


def caption_pages():
    pages = {}
    for h in range(1, 32):
        img = load(h)
        prof = profile(img)
        g = np.asarray(img.convert('L'))
        arr = extend(img, prof)
        if h in TEXTS:
            caps = TEXTS[h]
            xs = COLS.get(h) or [int(c[0]) for c in clusters((g[2150:H] < 120).any(axis=0), 120) if c[0] > 60]
            if len(xs) < len(caps):
                xs = (xs + [124, 822])[:len(caps)]
            for k, (num, txt) in enumerate(caps):
                xlim = xs[k + 1] - 40 if k + 1 < len(xs) else 1578
                set_caption(arr, g, prof, xs[k], xlim, num, txt)
        pages[h] = (arr, prof, g)
    return pages


def fix_specials(pages):
    # h19: caption fell entirely below the edge -> set it in the new margin
    arr, prof, g = pages[19]
    img = Image.fromarray(arr); d = ImageDraw.Draw(img); f = F(29.5)
    base = H + 22 - d.textbbox((0, 0), 'Tree planting day', font=f, anchor='ls')[1]
    d.text((122, base), '5.6', font=FM(NUM_SIZE), fill=NUM_INK, anchor='ls')
    d.text((122 + d.textlength('5.6', font=FM(NUM_SIZE)) + 34, base), 'Tree planting day', font=f, fill=CAP_INK, anchor='ls')
    pages[19] = (np.asarray(img).copy(), prof, g)

    # h02 contents: renumber + Green Roof subtitle
    arr, prof, g = pages[2]
    img = Image.fromarray(arr); d = ImageDraw.Draw(img)
    fnum = F(28)
    rows = [((1548, 1361, 1577, 1380), '20'), ((1549, 1599, 1577, 1618), '22'),
            ((1549, 1766, 1578, 1784), '24'), ((1549, 2004, 1577, 2023), '27'),
            ((1549, 2170, 1577, 2189), '30')]
    a = np.asarray(img).copy()
    for (x0, y0, x1, y1), _ in rows:
        paint(a, x0 - 8, y0 - 6, x1 + 5, y1 + 7, prof)
    img = Image.fromarray(a); d = ImageDraw.Draw(img)
    for (x0, y0, x1, y1), label in rows:
        bb = d.textbbox((0, 0), label, font=fnum)
        d.text((x1 - bb[2] + 1, y0 - bb[1]), label, font=fnum, fill=(35, 38, 39))
    # subtitle: same size/offset/colour as the Evans subtitle (number top 2004 -> glyphs 2058)
    sub = g[2058:2085, 200:1200] < 170
    cols = np.where(sub.any(axis=0))[0]
    sx0, sx1 = 200 + cols[0], 200 + cols[-1]
    pix = np.asarray(Image.fromarray(pages[2][0][2058:2085, sx0:sx1 + 1]).convert('L'))
    yy, xx = np.unravel_index(np.argmin(pix), pix.shape)
    mcol = tuple(int(v) for v in pages[2][0][2058 + yy, sx0 + xx])
    target = sx1 - sx0
    best = None
    for size in [s / 2 for s in range(44, 60)]:
        w = d.textlength('Mill Creek dam, two concepts · Cincinnati, OH', font=F(size))
        if best is None or abs(w - target) < best[0]:
            best = (abs(w - target), size)
    fs = F(best[1])
    bt = d.textbbox((0, 0), 'Living architecture', font=fs)[1]
    d.text((sx0, 2170 + 54 - bt), 'Living architecture · Clarendon Park, Chicago', font=fs, fill=mcol)
    REPORT.append(('contents subtitle size', best[1], 'x', int(sx0), 'color', mcol))
    pages[2] = (np.asarray(img).copy(), prof, g)

    # h30 colophon: clipped value lines under TYPEFACES / TOOLS / EDITION
    arr, prof, g = pages[30]
    top = detect_top(g, 121, 600, 2); end = measured_end(g, top, 121, 600)
    dd = ImageDraw.Draw(Image.new('RGB', (4, 4)))
    csize = min([z / 2 for z in range(44, 64)], key=lambda z: abs(121 + dd.textlength('Source Serif 4, IBM Plex Sans,', font=F(z)) - end))
    REPORT.append(('colophon size', csize, 'line1 end', int(end)))
    for x0, xlim, lines in [(121, 600, ['Source Serif 4, IBM Plex Sans,', 'IBM Plex Mono']),
                            (625, 1104, ['AutoCAD, Rhino, ArcGIS Pro,', 'Illustrator, Photoshop']),
                            (1129, 1578, ['September 2026'])]:
        set_caption(arr, g, prof, x0, xlim, '', lines, color=INK, fixed=csize)
    pages[30] = (arr, prof, g)

    # h01 cover: re-place the clipped headshot whole
    arr, prof, g = pages[1]
    src = np.asarray(load(1)).astype(int)
    bg = np.median(src[60:100, 200:1500].reshape(-1, 3), axis=0)
    reg = src[1600:H, 1150:1650]
    m = np.abs(reg - bg).sum(axis=2) > 30
    r = np.where(m.mean(axis=1) > 0.25)[0]; c = np.where(m.mean(axis=0) > 0.25)[0]
    y0, x0, x1 = 1600 + r[0], 1150 + c[0], 1150 + c[-1]
    pw = x1 - x0 + 1
    hs = Image.open(f'{REPO}/assets/img/headshot.webp').convert('L'); w, hh = hs.size; side = min(w, hh)
    hs = ImageOps.autocontrast(hs.crop(((w - side) // 2, 0, (w - side) // 2 + side, side)).resize((pw, pw), Image.LANCZOS), cutoff=1).convert('RGB')
    img = Image.fromarray(arr); img.paste(hs, (int(x0), int(y0)))
    pages[1] = (np.asarray(img).copy(), prof, g)
    REPORT.append(('cover headshot', int(x0), int(y0), pw))

    # h31 closer: small b&w headshot + about note above the name
    arr, prof, g = pages[31]
    img = Image.fromarray(arr); d = ImageDraw.Draw(img)
    hs = Image.open(f'{REPO}/assets/img/headshot.webp').convert('L'); w, hh = hs.size; side = min(w, hh)
    hs = ImageOps.autocontrast(hs.crop(((w - side) // 2, 0, (w - side) // 2 + side, side)).resize((300, 300), Image.LANCZOS), cutoff=1).convert('RGB')
    img.paste(hs, (124, 1560)); d = ImageDraw.Draw(img)
    text = ("I’m a landscape designer from Cincinnati — MLA, University of Cincinnati DAAP, 2026. "
            "I design landscapes that bring a site’s story to the surface, shaping ecology and everyday "
            "public life together. Thank you for reading.")
    ty = 1582
    for ln in wrap(text, 1000, F(30)):
        d.text((470, ty), ln, font=F(30), fill=INK); ty += 44
    d.text((470, ty + 16), 'mreindl118@gmail.com  ·  understorydesignstudio.com', font=F(26), fill=(90, 95, 88))
    pages[31] = (np.asarray(img).copy(), prof, g)


def swap_side(arr, prof):
    """Move a half page across the spine: mirror its baked gutter shading."""
    flat = np.median(prof[400:1300], axis=0)
    s = prof / flat
    s2 = s[::-1]
    out = arr.astype(float) * (s2 / s)[None]
    return np.clip(out, 0, 255).astype(np.uint8), (flat * s2)


def header_left_label(arr):
    g = np.asarray(Image.fromarray(arr).convert('L'))
    band = g[60:160, 100:1600] < 150
    rows = np.where(band.any(axis=1))[0]
    y0, y1 = 60 + rows[0], 60 + rows[-1]
    hb = g[y0:y1 + 1, 100:1600] < 150
    cl = clusters(hb.any(axis=0), 160)
    x0, x1 = 100 + cl[0][0], 100 + cl[0][1]
    # rule line lies below the text; keep label rows only (rows with sparse ink)
    lab = [y for y in range(y0, y1 + 1) if (g[y, 100:1600] < 150).mean() < 0.5]
    return int(min(lab)), int(max(lab)), int(x0), int(x1)


def hyundai_swap(pages):
    a21, p21, g21 = pages[21]
    a22, p22, g22 = pages[22]
    n21, np21 = swap_side(a21, p21)        # title page: right -> left
    n22, np22 = swap_side(a22, p22)        # diagram:    left -> right
    # left-hand pages carry "MATTHEW REINDL - PORTFOLIO", right-hand pages the project label
    L21 = header_left_label(n21); L22 = header_left_label(n22)
    y0 = min(L21[0], L22[0]) - 3; y1 = max(L21[1], L22[1]) + 3
    x1 = max(L21[3], L22[3]) + 6
    patch21 = n21[y0:y1, 118:x1].copy(); patch22 = n22[y0:y1, 118:x1].copy()
    paint(n21, 118, y0, x1, y1, np21); paint(n22, 118, y0, x1, y1, np22)
    # patches carry no shading at these columns, so they transplant cleanly
    n21[y0:y1, 118:x1] = patch22; n22[y0:y1, 118:x1] = patch21
    pages[21] = (n21, np21, g21); pages[22] = (n22, np22, g22)
    REPORT.append(('hyundai swapped; header rows', y0, y1, 'label x1', x1))


def blank(prof_src):
    return np.repeat(prof_src[None].astype(np.uint8), HH, axis=0)


def model_spread(pages):
    profL, profR = pages[12][1], pages[13][1]
    L, R = blank(profL), blank(profR)
    sp = np.concatenate([L, R], axis=1)
    img = Image.fromarray(sp); d = ImageDraw.Draw(img)
    hdr = F(24)

    def caps_ls(x, y, text, right=None):
        t = text.upper(); spx = 7
        ws = [d.textlength(c, font=hdr) for c in t]
        total = sum(ws) + spx * (len(t) - 1)
        cx = (right - total) if right else x
        for c, w in zip(t, ws):
            d.text((cx, y), c, font=hdr, fill=INK); cx += w + spx

    def header(x0, x1, left, right):
        caps_ls(x0, 108, left); caps_ls(None, 108, right, right=x1)
        d.line([(x0, 196), (x1, 196)], fill=INK, width=2)

    header(124, 1576, 'Matthew Reindl · Portfolio', 'Model')
    header(1824, 3276, '04 · Weaver Recreation Redesign', 'Model')
    ph = Image.open(f'{S}/images/11.webp').convert('RGB'); pw = 1900
    ph = ph.resize((pw, int(ph.height * pw / ph.width)), Image.LANCZOS)
    px, py = (W * 2 - pw) // 2, 320
    img.paste(ph, (px, py)); d = ImageDraw.Draw(img)
    f = F(29.5); base = py + ph.height + 26 - d.textbbox((0, 0), 'Laser-cut', font=f, anchor='ls')[1]
    d.text((px, base), '4.4', font=FM(NUM_SIZE), fill=NUM_INK, anchor='ls')
    d.text((px + d.textlength('4.4', font=FM(NUM_SIZE)) + 34, base), 'Laser-cut chipboard site model', font=f, fill=CAP_INK, anchor='ls')
    a = np.asarray(img)
    return a[:, :W].copy(), a[:, W:].copy()


def main():
    pages = caption_pages()
    fix_specials(pages)
    hyundai_swap(pages)
    mL, mR = model_spread(pages)
    bL, bR = blank(pages[12][1]), blank(pages[13][1])
    order = ([pages[1][0]] + [pages[h][0] for h in range(2, 18)] + [mL, mR]
             + [pages[18][0], pages[19][0], pages[20][0], bR, pages[21][0], pages[22][0], bL]
             + [pages[h][0] for h in range(23, 32)])
    assert len(order) == 35, len(order)
    for i, a in enumerate(order, 1):
        Image.fromarray(a).save(f'{OUT}/p{i:02d}.png')
    # spreads: 0 = cover, k = pages 2k, 2k+1
    for f in glob.glob(f'{REPO}/assets/portfolio/spread-*.webp'):
        os.remove(f)
    Image.fromarray(order[0]).save(f'{REPO}/assets/portfolio/spread-00.webp', quality=85)
    for k in range(1, 18):
        sp = np.concatenate([order[2 * k - 1], order[2 * k]], axis=1)
        Image.fromarray(sp).save(f'{REPO}/assets/portfolio/spread-{k:02d}.webp', quality=85)
    doc = pymupdf.open(); kf = 72 / 200
    for a in order:
        im = Image.fromarray(a); buf = io.BytesIO(); im.save(buf, 'JPEG', quality=88)
        pg = doc.new_page(width=im.width * kf, height=im.height * kf); pg.insert_image(pg.rect, stream=buf.getvalue())
    doc.save(f'{REPO}/assets/Matthew_Reindl_Portfolio.pdf', garbage=4, deflate=True)
    for r in REPORT:
        print(r)
    print('pages', len(order), 'spreads', len(glob.glob(f'{REPO}/assets/portfolio/spread-*.webp')),
          'pdf', len(doc), round(os.path.getsize(f'{REPO}/assets/Matthew_Reindl_Portfolio.pdf') / 1048576, 1), 'MB')


if __name__ == '__main__':
    main()
