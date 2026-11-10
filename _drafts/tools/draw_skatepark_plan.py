#!/usr/bin/env python3
"""Draw the Camp Washington Skatepark site plan (the owner's L-1 sheet) in the site's style (set 8b).

Source: the L-1 sheet's drawn overhead plan, a pure-vector SVG (no raster, no aerial), kept at
_drafts/originals/content/projects/cincinnati-recreation-commission/cw-l1-plan.svg with its metadata and
its own Arial lettering (the Annotations group) stripped. North is to the left, as on the L-1 sheet.

The callouts are the viewer's own (assets/js/skatepark/plan-geometry.js, PLAN.callouts, in park plan px),
placed with the SVG's group transforms (Park_Rotated, then Plan, then Site_Frame), which reproduce the
L-1 sheet's anchors exactly. Over the plan, in the site's fonts and tokens: pine callout discs with paper
Plex Mono numerals on 1px-scale ink leaders (anchor dots on the features), the street and context names in
mono ink with a paper halo, a north arrow (pointing left) and a scale bar in a panel box, and, on the
sheet variant, a plan key column.

Usage (from the repo root; needs Python Playwright + Chromium):
  python3 -B _drafts/tools/draw_skatepark_plan.py --web content/projects/cincinnati-recreation-commission \
      --print /path/to/print-masters
Outputs: web g-cw-plan.webp (sheet: plan + key, 2400x1600); print cw-plan-sheet.png (the same),
cw-plan.png (the plan alone, 2400x1600) and cw-plan-portrait.png (the plan alone, 1800x2400), each with
badges, north arrow and scale bar. Rendered at 2x and downsampled.
"""
import argparse, asyncio, base64, io, json, math, os, re

from playwright.async_api import async_playwright
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SVG = os.path.join(ROOT, '_drafts/originals/content/projects/cincinnati-recreation-commission/cw-l1-plan.svg')
GEOM = os.path.join(ROOT, 'assets/js/skatepark/plan-geometry.js')
FONTS = os.path.join(ROOT, 'assets/fonts')

PAPER, PANEL, INK, PINE, LINE, MID, MUTED = '#F3F0E8', '#FAF8F2', '#26292B', '#2E4A3B', '#D9D3C2', '#C7C1AE', '#5A5F58'


# ---- plan px -> SVG user units: the source SVG's own transforms ----
def park_to_svg(x, y, tf):
    a = math.radians(tf['theta']); c, t = tf['c'], tf['t']
    dx, dy = x - c[0], y - c[1]
    sx, sy = dx * math.cos(a) - dy * math.sin(a) + c[0] + t[0], dx * math.sin(a) + dy * math.cos(a) + c[1] + t[1]
    fx, fy = 1280 - sy, sx                                   # Plan: translate(1280,0) rotate(90)
    return 640 + (fx - 680) * 0.31, 480 + (fy - 150) * 0.31  # Site_Frame: translate(640,480) scale(.31) translate(-680,-150)


# badge offsets from each anchor, in SVG units (dx, dy), chosen to sit clear of the features
OFFSET = {1: (0, 36), 2: (-12, 42), 3: (-28, -22), 4: (32, -9), 5: (8, -32), 6: (33, 4), 7: (28, -22), 8: (-40, 28), 9: (-48, 30)}
# the context names, restyled from the L-1 SVG's own labels (x, y, size, rotation)
CONTEXT = [('STOCK AVE.', 262.5, 742.8, 1.25, 78.7), ('BASKETBALL', 353.3, 477.3, 0.9, 0), ('POOL', 498.8, 266.3, 0.9, 0),
           ('PLAYGROUND', 629.8, 345.7, 0.85, 0), ('KIDDIE POOL', 334.3, 316.2, 0.8, 0), ('YOUTH SERVICES', 344.5, 152.2, 0.9, 0)]


def font_face(name, fam, w):
    b = base64.b64encode(open(os.path.join(FONTS, name), 'rb').read()).decode()
    return "@font-face{font-family:'%s';font-weight:%d;src:url(data:font/woff2;base64,%s) format('woff2')}" % (fam, w, b)


def build(svg_src, plan, crop, key, W, H, hud='key'):
    """HTML for one drawing. crop = (x, y, w, h) in SVG units for the plan; key = width of the key column (px);
    hud = where the north arrow and scale bar go: 'key' (the key column's foot) or 'lr' / 'll' (a panel box on the plan)."""
    tf, cs = plan['parkTf'], plan['callouts']
    pw = W - key                                            # plan box width (CSS px)
    cx, cy, cw, ch = crop
    s = pw / cw                                             # CSS px per SVG unit
    assert abs(ch * s - H) < 1, 'crop aspect must match the plan box'
    P = lambda x, y: ((x - cx) * s, (y - cy) * s)
    u = 1                                                   # drawing unit: 1 CSS px (2 px in the 2400 px output)
    root = re.sub(r'viewBox="[^"]*"', 'viewBox="%g %g %g %g"' % crop, svg_src, count=1)
    root = re.sub(r'width="1280" height="960"', 'width="%d" height="%d" preserveAspectRatio="xMidYMid slice"' % (pw, H), root, count=1)
    plan_uri = 'data:image/svg+xml;base64,' + base64.b64encode(root.encode()).decode()
    o = []
    # context names (mono, ink, paper halo)
    for t, x, y, sz, rot in CONTEXT:
        X, Y = P(x, y)
        if not (40 * u < X < pw - 40 * u and 24 * u < Y < H - 24 * u):
            continue
        fs = 10.5 * u * sz
        o.append('<text x="%.1f" y="%.1f" transform="rotate(%g %.1f %.1f)" class="ctx" style="font-size:%.2fpx;letter-spacing:%.2fpx;stroke-width:%.2fpx">%s</text>'
                 % (X, Y, rot, X, Y, fs, fs * 0.22, fs * 0.36, t))
    # leaders, anchor dots and badges
    R = 15 * u
    for c in cs:
        ax, ay = park_to_svg(c['x'], c['y'], tf)
        dx, dy = OFFSET[c['n']]
        A, B = P(ax, ay), P(ax + dx, ay + dy)
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        if L > R + 2:
            ex, ey = B[0] - (B[0] - A[0]) / L * R, B[1] - (B[1] - A[1]) / L * R
            o.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" class="halo" style="stroke-width:%.2f"/>' % (A[0], A[1], ex, ey, 3.4 * u))
            o.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" class="lead" style="stroke-width:%.2f"/>' % (A[0], A[1], ex, ey, 1.25 * u))
        o.append('<circle cx="%.1f" cy="%.1f" r="%.2f" fill="%s" stroke="%s" stroke-width="%.2f"/>' % (A[0], A[1], 2.6 * u, INK, PAPER, 1.1 * u))
        o.append('<circle cx="%.1f" cy="%.1f" r="%.2f" fill="%s" stroke="%s" stroke-width="%.2f"/>' % (B[0], B[1], R, PINE, PAPER, 1.8 * u))
        o.append('<text x="%.1f" y="%.1f" class="num" style="font-size:%.2fpx">%d</text>' % (B[0], B[1] + 0.7 * u, 15.5 * u, c['n']))
    # north arrow (pointing left: north is to the left) + scale bar, as the viewer's HUD
    ft_per_unit = plan['scaleFtPerPx'] / 0.31               # feet per SVG unit (the plan is drawn at 31%)
    px_per_ft = s / ft_per_unit
    bar = [0, 10, 25, 50]
    bw = bar[-1] * px_per_ft
    nr = 13.5 * u
    hw, hh = 2 * nr + 20 * u + bw + 14 * u, 50 * u           # the HUD's own box
    h = []
    ncx, ncy = nr + 10 * u, hh / 2 - 5 * u
    h.append('<g transform="translate(%.1f %.1f) rotate(-90) scale(%.4f)"><circle r="13.5" fill="none" stroke="%s" stroke-width="1"/>'
             '<polygon points="0,-12 5,4 0,1 -5,4" fill="%s"/><polygon points="0,12 5,4 0,1 -5,4" fill="%s"/></g>' % (ncx, ncy, nr / 13.5, MID, PINE, MID))
    h.append('<text x="%.1f" y="%.1f" class="hud" style="font-size:%.2fpx" text-anchor="middle">N</text>' % (ncx, hh - 6 * u, 10.5 * u))
    sx0, sy0 = ncx + nr + 20 * u, hh / 2 - 8 * u
    for a, b, col in [(0, 10, INK), (10, 25, PAPER), (25, 50, INK)]:
        h.append('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="%s"/>' % (sx0 + a * px_per_ft, sy0, (b - a) * px_per_ft, 6 * u, col))
    h.append('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="none" stroke="%s" stroke-width="%.2f"/>' % (sx0, sy0, bw, 6 * u, INK, 1 * u))
    for v in bar:
        anchor = 'start' if v == bar[0] else 'end' if v == bar[-1] else 'middle'
        h.append('<text x="%.2f" y="%.2f" class="hud" style="font-size:%.2fpx" text-anchor="%s">%s</text>'
                 % (sx0 + v * px_per_ft, sy0 + 19 * u, 10 * u, anchor, '%d FT' % v if v == bar[-1] else str(v)))
    hudsvg = ''.join(h)
    if hud in ('ll', 'lr'):
        bx = 12 * u if hud == 'll' else pw - 12 * u - hw
        o.append('<g transform="translate(%.1f %.1f)"><rect width="%.1f" height="%.1f" fill="%s" stroke="%s" stroke-width="%.2f"/>%s</g>'
                 % (bx, H - 12 * u - hh, hw, hh, PANEL, LINE, 1 * u, hudsvg))
    overlay = ''.join(o)
    keyhtml = ''
    if key:
        items = ''.join('<li><span class="kb">%d</span><span>%s</span></li>' % (c['n'], c['label'].replace('&', '&amp;')) for c in cs)
        foot = ('<svg width="%.1f" height="%.1f" viewBox="0 0 %.1f %.1f" style="position:static;display:block;margin:0 0 %.1fpx -%.1fpx">%s</svg>'
                % (hw, hh, hw, hh, 12 * u, 10 * u, hudsvg)) if hud == 'key' else ''
        keyhtml = ('<div class="key" style="left:%dpx;width:%dpx"><p class="lab">Plan key</p><ol>%s</ol>'
                   '<div class="foot">%s<p class="lab">As built · August 2026</p></div></div>' % (pw, key, items, foot))
    css = (font_face('plex-mono-500-latin.woff2', 'IBM Plex Mono', 500) + font_face('plex-sans-400-latin.woff2', 'IBM Plex Sans', 400) + """
      html,body{margin:0;background:%(paper)s}
      #sheet{position:relative;width:%(W)dpx;height:%(H)dpx;overflow:hidden;background:%(paper)s}
      #sheet img,#sheet svg{position:absolute;left:0;top:0;display:block}
      .ctx{font-family:'IBM Plex Mono';font-weight:500;fill:%(ink)s;stroke:%(paper)s;paint-order:stroke;stroke-linejoin:round;text-anchor:middle;dominant-baseline:middle}
      .num{font-family:'IBM Plex Mono';font-weight:500;fill:%(paper)s;text-anchor:middle;dominant-baseline:middle}
      .hud{font-family:'IBM Plex Mono';font-weight:500;fill:%(ink)s;letter-spacing:.08em}
      .lead{stroke:%(ink)s;stroke-linecap:round}
      .halo{stroke:%(paper)s;stroke-opacity:.75;stroke-linecap:round}
      .key{position:absolute;top:0;height:%(H)dpx;box-sizing:border-box;border-left:%(rule).2fpx solid %(ink)s;padding:%(pad).1fpx %(pad).1fpx;
           font-family:'IBM Plex Sans';color:%(ink)s;display:flex;flex-direction:column}
      .lab{margin:0;font-family:'IBM Plex Mono';font-weight:500;text-transform:uppercase;font-size:%(lab).2fpx;letter-spacing:.16em;color:%(muted)s;line-height:1.6}
      .key ol{list-style:none;margin:%(gap).1fpx 0 0;padding:0;display:flex;flex-direction:column;gap:%(li).1fpx;font-size:%(fs).2fpx;line-height:1.3}
      .key li{display:flex;gap:%(kg).1fpx;align-items:flex-start}
      .kb{flex-shrink:0;margin-top:%(kbm).1fpx;width:%(kb).1fpx;height:%(kb).1fpx;border-radius:50%%;background:%(pine)s;color:%(paper)s;font:500 %(kbf).2fpx/%(kb).1fpx 'IBM Plex Mono';text-align:center}
      .foot{margin-top:auto}
    """ % dict(paper=PAPER, ink=INK, pine=PINE, muted=MUTED, W=W, H=H, rule=1.0 * u, pad=26 * u, lab=11 * u, gap=16 * u, li=12 * u,
               fs=16.5 * u, kg=11 * u, kb=24 * u, kbf=12 * u, kbm=-1.5 * u))
    html = ('<!doctype html><meta charset="utf-8"><style>%s</style><div id="sheet"><img src="%s" width="%d" height="%d" alt="">'
            '<svg width="%d" height="%d" viewBox="0 0 %d %d">%s</svg>%s</div>' % (css, plan_uri, pw, H, pw, H, pw, H, overlay, keyhtml))
    return html


async def render(html, W, H, scale):
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=os.environ.get('PW_CHROMIUM', '/opt/pw-browsers/chromium'))
        pg = await b.new_page(viewport={'width': W, 'height': H}, device_scale_factor=scale)
        await pg.set_content(html, wait_until='load')
        await pg.evaluate('document.fonts.ready')
        await pg.wait_for_timeout(300)
        png = await pg.locator('#sheet').screenshot()
        await b.close()
    return Image.open(io.BytesIO(png)).convert('RGB')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--web', default=None)
    ap.add_argument('--print', dest='prn', default=None)
    ap.add_argument('--svg', default=SVG)
    ap.add_argument('--ss', type=int, default=2, help='supersampling factor')
    a = ap.parse_args()
    src = open(a.svg).read()
    plan = json.loads(open(GEOM).read().split('=', 1)[1].strip().rstrip(';'))
    out = []
    # sheet: plan (a square crop round the park, Stock Ave on the left = north) + key column, 2400 x 1600
    W, H, key = 1200, 800, 400
    sheet = asyncio.run(render(build(src, plan, (198, 388, 500, 500), key, W, H, 'key'), W, H, 2 * a.ss)).resize((2400, 1600), Image.LANCZOS)
    # plan alone: a 3:2 crop with more of the context (the court, pool and playground), 2400 x 1600
    alone = asyncio.run(render(build(src, plan, (128, 388, 750, 500), 0, W, H, 'lr'), W, H, 2 * a.ss)).resize((2400, 1600), Image.LANCZOS)
    # plan alone, portrait (a book page): 1800 x 2400
    tall = asyncio.run(render(build(src, plan, (196, 335, 450, 600), 0, 900, 1200, 'lr'), 900, 1200, 2 * a.ss)).resize((1800, 2400), Image.LANCZOS)
    if a.web:
        f = os.path.join(a.web, 'g-cw-plan.webp'); sheet.save(f, 'WEBP', quality=84, method=6); out.append((f, sheet.size))
    if a.prn:
        f = os.path.join(a.prn, 'cw-plan-sheet.png'); sheet.save(f, optimize=True); out.append((f, sheet.size))
        f = os.path.join(a.prn, 'cw-plan.png'); alone.save(f, optimize=True); out.append((f, alone.size))
        f = os.path.join(a.prn, 'cw-plan-portrait.png'); tall.save(f, optimize=True); out.append((f, tall.size))
    for f, sz in out:
        print(json.dumps({'out': f, 'size': sz, 'bytes': os.path.getsize(f)}))


if __name__ == '__main__':
    main()
