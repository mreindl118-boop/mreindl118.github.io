#!/usr/bin/env python3
"""Ryan Sports Complex, Hometown Huddle planting: two clean sheets drawn from the CAD planting base (set 8f, mock-up).

Source: the owner's CAD planting base, "Ryan_Sports_Complex_Planting_Symbol_Blocks.dxf" (kept off the site; if it
arrives in pieces, `cat` them in order). AutoCAD 2018 DXF; the
header says feet but the geometry is in inches; GIS base layers from CAGIS. Read with ezdxf (pip install ezdxf).

What is drawn: the CAGIS base (2 ft and 10 ft contours, paving, building footprints), the CAD's existing trees (dashed),
benches and planting zones, and the plants: each symbol is its CAD block's own linework at its inserted size (the
mature spread), one colour per kind, a light canopy disc, and the block's own code (P043, AS2, ...) where the symbol is
big enough to carry it. The CAD's symbol palette at the drawing's lower left (one of each library symbol beside a
nursery list with prices) is left out; what remains is exactly the schedule's plants (checked against SCHEDULE).
No prices or cost totals appear anywhere. North is the CAD's +Y (the CAGIS base is grid-north up).

Sheets (17 x 11 in at 72 css px per inch, 1224 x 792):
  plan      the whole planting plan, the plant key in a band under it, and the title block
  shelter   enlargement of the beds around the shelter and its entry walk; a plant cut by the frame is drawn in grey
            and not counted in that sheet's key
Outputs: <web>/g-ryan-plan.webp, g-ryan-shelter.webp (2400 x 1553); <print>/ryan-planting-plan.png,
ryan-planting-shelter.png (5100 x 3300, 300 dpi at 17 x 11).

Usage (from the repo root; needs ezdxf, Pillow and Python Playwright + Chromium):
  python3 -I _drafts/tools/draw_ryan_planting.py --dxf ryan.dxf --web content/projects/cincinnati-recreation-commission \
      --print /path/to/print-masters
"""
import argparse, asyncio, base64, collections, html, io, json, os

import ezdxf
from ezdxf import path as ezpath
from playwright.async_api import async_playwright
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONTS = os.path.join(ROOT, 'assets/fonts')

PAPER, PANEL, INK, PINE, LINE, SOFT, MID, DARK, MUTED, FAINT = (
    '#F3F0E8', '#FAF8F2', '#26292B', '#2E4A3B', '#D9D3C2', '#E8E3D4', '#C7C1AE', '#454A4D', '#5A5F58', '#6E6A5C')
W, H = 1224, 792                      # css px = points; the sheet is 17 x 11 in

# kind: block -> (code shown, key name, group, colour). Names follow the CAD schedule, put in key order.
KINDS = {
    'PLSYM_P024_REDBUD':                 ('P024', 'Redbud, Eastern',                 'T', '#7D3F8F'),
    'PLSYM_P068_MAGNOLIA':               ('P068', 'Magnolia, Sweetbay',              'T', '#3E7A4C'),
    'PLSYM_P032_DOGWOOD':                ('P032', 'Dogwood, Cherokee Brave',         'T', '#B0496A'),
    'PLSYM_P070_APPLE':                  ('P070', 'Apple, Gibson Golden',            'T', '#9C6526'),
    'PLSYM_P079_CHERRY':                 ('P079', 'Cherry, Rainier',                 'T', '#9E4646'),
    'PLSYM_P063_CRAPE':                  ('P063', 'Crape myrtle, Colorama Scarlet',  'T', '#B5303E'),
    'PLSYM_P001_MAPLE':                  ('P001', 'Japanese maple, Tamukeyama',      'T', '#6D2E46'),
    'PLSYM_P102_ARBOR':                  ('P102', 'Arborvitae, Green Giant',         'T', '#2D5C3C'),
    'PLSYM_P035_BBUSH':                  ('P035', 'Burning bush, dwarf',             'S', '#8C3A22'),
    'PLSYM_P085_ELDERBERRY':             ('P085', 'Elderberry, Black Lace',          'S', '#523468'),
    'PLSYM_GENERIC_BUTTERFLY_BUSH_31P666': ('BU5', 'Butterfly bush',                 'S', '#2D7A9E'),
    'PLSYM_GENERIC_HYDRANGEA_31P666':    ('HY5', 'Hydrangea',                        'S', '#5560AE'),
    'PLSYM_GENERIC_JUNIPER_37P999':      ('JU6', 'Juniper',                          'S', '#3B6E62'),
    'PLSYM_P054_HOLLY':                  ('P054', 'Holly, Sky Pencil',               'S', '#2E4A3B'),
    'PLSYM_P093_ARBOR':                  ('P093', "Arborvitae, Anna’s Magic Ball", 'S', '#9A800F'),
    'PLSYM_GENERIC_SPIREA_19P0':         ('SP3', 'Spirea',                           'S', '#B8741A'),
    'PLSYM_P043_HOSTA':                  ('P043', 'Hosta',                           'P', '#3A8676'),
    'PLSYM_P004_ASTER':                  ('P004', 'Aster, Grape Crush',              'P', '#7F54B3'),
    'PLSYM_P034_CONEFLOWER':             ('P034', 'Coneflower, Prairie Splendor',    'P', '#B0507B'),
    'PLSYM_P031_COREOPSIS':              ('P031', 'Coreopsis, American Dream',       'P', '#C0586A'),
    'PLSYM_GENERIC_ASTILBE_12P666':      ('AS2', 'Astilbe',                          'P', '#A5462A'),
    'PLSYM_GENERIC_GRASS_25P333':        ('GR4', 'Ornamental grass',                 'P', '#6A8A2C'),
}
GROUPS = [('T', 'Trees'), ('S', 'Shrubs'), ('P', 'Perennials and grasses')]
# the schedule's counts (ryan-planting-schedule.csv); the drawing's own count must agree
SCHEDULE = {'P093': 13, 'P102': 3, 'P004': 17, 'P035': 6, 'P034': 9, 'P031': 3, 'P063': 9, 'P032': 3, 'P024': 1, 'P085': 5,
            'P070': 5, 'P043': 25, 'P054': 4, 'P001': 4, 'P068': 9, 'P079': 5, 'AS2': 11, 'BU5': 12, 'HY5': 4, 'JU6': 9,
            'GR4': 14, 'SP3': 3}
TITLE = 'Ryan Sports Complex · Hometown Huddle planting · Cincinnati Recreation Commission'
SYM_R = 0.94                           # the blocks' unit symbols reach ~0.94 of their insert scale


# ---- the DXF -> drawable primitives (model units: inches) ----
def in_palette(x, y):
    return x < 29500 and y < 24500          # the symbol palette and nursery list sit far to the lower left


def prims(e):
    t = e.dxftype()
    if t == 'CIRCLE':
        c = e.dxf.center
        return [('c', c.x, c.y, e.dxf.radius)]
    if t == 'LINE':
        a, b = e.dxf.start, e.dxf.end
        return [('p', [(a.x, a.y), (b.x, b.y)], False)]
    if t in ('LWPOLYLINE', 'POLYLINE', 'ARC', 'ELLIPSE', 'SPLINE'):
        pts = [(v.x, v.y) for v in ezpath.make_path(e).flattening(distance=0.5)]
        closed = bool(getattr(e, 'closed', False)) or (len(pts) > 2 and pts[0] == pts[-1])
        return [('p', pts, closed)] if len(pts) > 1 else []
    return []


def load(dxf):
    doc = ezdxf.readfile(dxf)
    msp = doc.modelspace()
    base = collections.defaultdict(list)
    for e in msp:
        if e.dxftype() in ('INSERT', 'OLE2FRAME', 'TEXT', 'MTEXT', 'POINT'):
            continue
        for pr in prims(e):
            base[e.dxf.layer].append((pr, e.dxf.linetype))
    plants = []
    for e in msp.query('INSERT'):
        x, y = e.dxf.insert.x, e.dxf.insert.y
        if in_palette(x, y):
            continue
        sub, code = [], None
        for v in e.virtual_entities():
            if v.dxftype() == 'TEXT':
                code = v.dxf.text
            else:
                sub.extend(prims(v))
        plants.append(dict(block=e.dxf.name, x=x, y=y, s=e.dxf.xscale, code=code, prims=sub))
    unit = {}
    for name in set(p['block'] for p in plants):
        unit[name] = [pr for v in doc.blocks.get(name) if v.dxftype() != 'TEXT' for pr in prims(v)]
    return dict(base=base, plants=plants, unit=unit)


def esc(s):
    return html.escape(s, quote=True)


class Plot:
    """Model (inches, +Y north) -> plan box css px."""

    def __init__(self, win, pw, ph, ox=0, oy=0):
        x0, y0, x1, y1 = win
        self.s = min(pw / (x1 - x0), ph / (y1 - y0))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        self.x0, self.y1 = cx - pw / 2 / self.s, cy + ph / 2 / self.s
        self.pw, self.ph, self.ox, self.oy = pw, ph, ox, oy
        self.mx0, self.mx1 = self.x0, self.x0 + pw / self.s
        self.my0, self.my1 = self.y1 - ph / self.s, self.y1

    def P(self, x, y):
        return (self.ox + (x - self.x0) * self.s, self.oy + (self.y1 - y) * self.s)

    def seen(self, xs, ys, pad=0):
        return max(xs) >= self.mx0 - pad and min(xs) <= self.mx1 + pad and max(ys) >= self.my0 - pad and min(ys) <= self.my1 + pad

    def d(self, pts, closed):
        q = [self.P(x, y) for x, y in pts]
        out, last = [], None
        for X, Y in q:                                     # drop sub-0.15px steps
            if last and abs(X - last[0]) < 0.15 and abs(Y - last[1]) < 0.15:
                continue
            out.append('%.2f %.2f' % (X, Y)); last = (X, Y)
        if len(out) < 2:
            return ''
        return 'M' + 'L'.join(out) + ('Z' if closed else '')


def layer_paths(pl, items, closed_only=False, dedupe=False):
    seen, ds = set(), []
    for pr, lt in items:
        if pr[0] != 'p':
            continue
        pts, closed = pr[1], pr[2]
        if closed_only and not closed:
            continue
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        if not pl.seen(xs, ys):
            continue
        if dedupe:
            key = (len(pts), round(sum(xs)), round(sum(ys)))
            if key in seen:
                continue
            seen.add(key)
        dd = pl.d(pts, closed)
        if dd:
            ds.append(dd)
    return ds


def plan_svg(D, pl, opt):
    """The plan's SVG elements (already in sheet css px), clipped to the plan box by the caller."""
    B, o = D['base'], []
    u = opt.get('u', 1.0)                                  # line-weight factor
    # contours: 2 ft faint, 10 ft a step stronger
    o.append('<path d="%s" fill="none" stroke="%s" stroke-width="%.2f"/>' % (''.join(layer_paths(pl, B['Hamilton_County_2ft_Contours_(2020) selection'])), LINE, 0.45 * u))
    o.append('<path d="%s" fill="none" stroke="%s" stroke-width="%.2f"/>' % (''.join(layer_paths(pl, B['Hamilton County 10 Ft Contours selection'])), MID, 0.7 * u))
    # paving: roads, driveways and lots, walks (one even-odd path per layer so courtyards stay open)
    for lay in ('Road_Pavement selection', 'Driveways selection', 'Sidewalks selection'):
        ds = layer_paths(pl, B[lay], dedupe=True)
        o.append('<path d="%s" fill="%s" fill-rule="evenodd" stroke="%s" stroke-width="%.2f" stroke-linejoin="round"/>' % (''.join(ds), PANEL, '#9C9A8E', 0.55 * u))
    # buildings
    o.append('<path d="%s" fill="%s" stroke="%s" stroke-width="%.2f" stroke-linejoin="round"/>' % (''.join(layer_paths(pl, B['Building_Footprints selection'])), SOFT, DARK, 0.75 * u))
    # existing trees: dashed canopy circles, as in the CAD
    for pr, lt in B['Existing Trees']:
        if pr[0] == 'c':
            _, x, y, r = pr
            if pl.seen([x - r, x + r], [y - r, y + r]):
                X, Y = pl.P(x, y)
                o.append('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="none" stroke="%s" stroke-opacity=".8" stroke-width="%.2f" stroke-dasharray="%.2f %.2f"/>'
                         % (X, Y, r * pl.s, '#8A9A5B', 0.55 * u, 3.2 * u, 2.2 * u))
    # benches
    for pr, lt in B['Bench Pads'] + B['Bench swing']:
        if pr[0] == 'c':
            X, Y = pl.P(pr[1], pr[2])
            o.append('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="%s" stroke="%s" stroke-width="%.2f"/>' % (X, Y, pr[3] * pl.s, PANEL, DARK, 0.5 * u))
        else:
            dd = pl.d(pr[1], pr[2])
            if dd:
                o.append('<path d="%s" fill="%s" stroke="%s" stroke-width="%.2f"/>' % (dd, PANEL if pr[2] else 'none', DARK, 0.5 * u))
    # planting beds: closed zones get a leaf tint; every zone edge is drawn
    zones = B['planting zones']
    o.append('<path d="%s" fill="#7A9A3C" fill-opacity=".10" stroke="none"/>' % ''.join(layer_paths(pl, zones, closed_only=True)))
    o.append('<path d="%s" fill="none" stroke="%s" stroke-width="%.2f" stroke-linejoin="round"/>' % (''.join(layer_paths(pl, zones)), '#8A5B3A', 0.95 * u))
    # plants: biggest first, so the small ones sit on top
    labels = []
    for p in sorted(D['plants'], key=lambda p: -p['s']):
        code, name, grp, col = KINDS[p['block']]
        r = SYM_R * p['s']
        if not pl.seen([p['x'] - r, p['x'] + r], [p['y'] - r, p['y'] + r]):
            continue
        X, Y = pl.P(p['x'], p['y'])
        R = r * pl.s
        sw = max(0.28, min(0.8, R / 22)) * u
        ghost = not (pl.mx0 <= p['x'] <= pl.mx1 and pl.my0 <= p['y'] <= pl.my1)   # cut by the frame: context only, not counted
        if ghost:
            col = MID
        o.append('<g stroke="%s" fill="none" stroke-width="%.2f" stroke-linejoin="round" stroke-linecap="round">' % (col, sw))
        if not ghost:
            o.append('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="%s" fill-opacity=".16" stroke="none"/>' % (X, Y, R, col))
        for pr in p['prims']:
            if pr[0] == 'c':
                cx, cy = pl.P(pr[1], pr[2])
                o.append('<circle cx="%.2f" cy="%.2f" r="%.2f"/>' % (cx, cy, pr[3] * pl.s))
            else:
                dd = pl.d(pr[1], pr[2])
                if dd:
                    o.append('<path d="%s"/>' % dd)
        o.append('</g>')
        fs = min(opt.get('code_max', 9.5), R * 0.55)
        if fs >= opt.get('code_min', 5.6) and not ghost:
            labels.append('<text x="%.2f" y="%.2f" class="code" style="font-size:%.2fpx;stroke-width:%.2fpx" fill="%s">%s</text>'
                          % (X, Y + fs * 0.05, fs, fs * 0.42, col, code))
    o.extend(labels)
    return ''.join(o)


def kind_counts(D, pl=None):
    c = collections.Counter()
    for p in D['plants']:
        if pl is None or (pl.mx0 <= p['x'] <= pl.mx1 and pl.my0 <= p['y'] <= pl.my1):
            c[KINDS[p['block']][0]] += 1
    return c


def mini_symbol(D, block, size):
    """The block's own linework as a small inline SVG for the key."""
    code, name, grp, col = KINDS[block]
    k = size / 2 / 1.0
    parts = ['<circle cx="0" cy="0" r="%.3f" fill="%s" fill-opacity=".16" stroke="none"/>' % (SYM_R, col)]
    for pr in D['unit'][block]:
        if pr[0] == 'c':
            parts.append('<circle cx="%.4f" cy="%.4f" r="%.4f"/>' % (pr[1], -pr[2], pr[3]))
        else:
            parts.append('<path d="M%s%s"/>' % ('L'.join('%.4f %.4f' % (x, -y) for x, y in pr[1]), 'Z' if pr[2] else ''))
    return ('<svg class="sym" width="%d" height="%d" viewBox="-1 -1 2 2" aria-hidden="true"><g fill="none" stroke="%s" stroke-width="%.4f" '
            'stroke-linejoin="round" stroke-linecap="round">%s</g></svg>' % (size, size, col, 1.15 / size * 2, ''.join(parts)))


def north_scale(pl, bar, unit_label='FT'):
    """North arrow (CAD +Y) and a graphic scale; returns (svg, w, h)."""
    px_ft = pl.s * 12
    bw = bar[-1] * px_ft
    nr = 12.5
    w, h = 2 * nr + 22 + bw + 16, 46
    ncx, ncy = nr + 2, nr + 2
    s = ['<g transform="translate(%.1f %.1f)"><circle r="%.1f" fill="none" stroke="%s" stroke-width="1"/>'
         '<polygon points="0,-11 4.6,3.6 0,1 -4.6,3.6" fill="%s"/><polygon points="0,11 4.6,3.6 0,1 -4.6,3.6" fill="%s"/></g>'
         % (ncx, ncy, nr, MID, PINE, MID)]
    s.append('<text x="%.1f" y="%.1f" class="hud" text-anchor="middle">N</text>' % (ncx, 2 * nr + 15))
    sx0, sy0 = ncx + nr + 20, ncy - 7
    segs = list(zip(bar[:-1], bar[1:]))
    for i, (a, b) in enumerate(segs):
        s.append('<rect x="%.2f" y="%.2f" width="%.2f" height="5.5" fill="%s"/>' % (sx0 + a * px_ft, sy0, (b - a) * px_ft, INK if i % 2 == 0 else PAPER))
    s.append('<rect x="%.2f" y="%.2f" width="%.2f" height="5.5" fill="none" stroke="%s" stroke-width="1"/>' % (sx0, sy0, bw, INK))
    for v in bar:
        anchor = 'start' if v == bar[0] else 'end' if v == bar[-1] else 'middle'
        s.append('<text x="%.2f" y="%.2f" class="hud" text-anchor="%s">%s</text>' % (sx0 + v * px_ft, sy0 + 18, anchor, ('%d %s' % (v, unit_label)) if v == bar[-1] else str(v)))
    return ''.join(s), w, h


def key_rows(D, counts, size):
    rows = []
    for g, gname in GROUPS:
        items = [(b, k) for b, k in KINDS.items() if k[2] == g and counts.get(k[0])]
        if not items:
            continue
        lis = ''.join('<li>%s<span class="kc">%s</span><span class="kn">%s</span><span class="kq">%d</span></li>'
                      % (mini_symbol(D, b, size), k[0], esc(k[1]), counts[k[0]]) for b, k in items)
        rows.append('<div class="grp"><p class="lab">%s</p><ul>%s</ul></div>' % (gname, lis))
    return rows


def font_face(fonts, name, fam, w):
    b = base64.b64encode(open(os.path.join(fonts, name), 'rb').read()).decode()
    return "@font-face{font-family:'%s';font-weight:%d;src:url(data:font/woff2;base64,%s) format('woff2')}" % (fam, w, b)


def css(fonts):
    return (font_face(fonts, 'plex-mono-400-latin.woff2', 'IBM Plex Mono', 400) + font_face(fonts, 'plex-mono-500-latin.woff2', 'IBM Plex Mono', 500)
            + font_face(fonts, 'plex-sans-400-latin.woff2', 'IBM Plex Sans', 400) + font_face(fonts, 'plex-sans-600-latin.woff2', 'IBM Plex Sans', 600)
            + font_face(fonts, 'plex-sans-700-latin.woff2', 'IBM Plex Sans', 700) + """
  html,body{margin:0;background:%(paper)s}
  #sheet{position:relative;width:%(W)dpx;height:%(H)dpx;overflow:hidden;background:%(paper)s;color:%(ink)s;font-family:'IBM Plex Sans'}
  #sheet > svg{position:absolute;left:0;top:0;display:block}
  .code{font-family:'IBM Plex Mono';font-weight:500;text-anchor:middle;dominant-baseline:middle;stroke:%(paper)s;paint-order:stroke;stroke-linejoin:round;letter-spacing:.02em}
  .ctx{font-family:'IBM Plex Mono';font-weight:500;fill:%(ink)s;stroke:%(paper)s;paint-order:stroke;stroke-linejoin:round;letter-spacing:.16em;font-size:9.5px}
  .hud{font-family:'IBM Plex Mono';font-weight:500;fill:%(ink)s;letter-spacing:.08em;font-size:9.5px}
  .lab{margin:0;font-family:'IBM Plex Mono';font-weight:500;text-transform:uppercase;font-size:9.5px;letter-spacing:.16em;color:%(muted)s;line-height:1.5}
  .grp ul{list-style:none;margin:6px 0 0;padding:0;display:flex;flex-direction:column;gap:3px}
  .grp li{display:grid;grid-template-columns:auto 34px 1fr auto;align-items:center;column-gap:7px;font-size:12px;line-height:1.25;white-space:nowrap}
  .sym{display:block}
  .kc{font-family:'IBM Plex Mono';font-weight:400;font-size:10px;letter-spacing:.04em;color:%(muted)s}
  .kq{font-family:'IBM Plex Mono';font-weight:500;font-size:11px;text-align:right;min-width:18px}
  .tb h2{margin:0;font-size:17px;line-height:1.2;font-weight:700;letter-spacing:-.015em}
  .tb .sub{margin:3px 0 0;font-size:11.5px;line-height:1.4;color:%(ink)s}
  .tb .note{margin:6px 0 0;font-size:10.5px;line-height:1.4;color:%(muted)s}
""" % dict(paper=PAPER, ink=INK, muted=MUTED, W=W, H=H))


def overall(D):
    xs = [p['x'] for p in D['plants']]; ys = [p['y'] for p in D['plants']]
    pad_x, pad_y = 30 * 12, 26 * 12
    win = (min(xs) - pad_x, min(ys) - pad_y, max(xs) + pad_x, max(ys) + pad_y)
    PH = 540
    pl = Plot(win, W, PH)
    o = [plan_svg(D, pl, dict(u=0.9, code_min=6.2, code_max=8.5))]
    # the enlargement's frame, and the areas named as on the owner's sheets
    ep = Plot(enlargement_window(D), SHELTER_PW, H)       # the enlargement's own frame, exactly
    a, b = pl.P(ep.mx0, ep.my1), pl.P(ep.mx1, ep.my0)
    o.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="none" stroke="%s" stroke-width="1" stroke-dasharray="5 3"/>' % (a[0], a[1], b[0] - a[0], b[1] - a[1], INK))
    # two short lines, so the label stays within the box's width and clear of the trees beside it
    o.append('<text x="%.1f" y="%.1f" class="ctx">ENLARGEMENT</text>' % (a[0], a[1] - 18))
    o.append('<text x="%.1f" y="%.1f" class="ctx">SHELTER BEDS</text>' % (a[0], a[1] - 6))
    for t, x, y, anchor in AREAS:
        X, Y = pl.P(x, y)
        for i, line in enumerate(t.split('/')):
            o.append('<text x="%.1f" y="%.1f" class="ctx" text-anchor="%s">%s</text>' % (X, Y + i * 12, anchor, line))
    hud, hw, hh = north_scale(pl, [0, 25, 50, 100])
    counts = kind_counts(D)
    for k, v in SCHEDULE.items():
        assert counts[k] == v, (k, counts[k], v)
    rows = key_rows(D, counts, 15)
    band = ('<div style="position:absolute;left:0;top:%dpx;width:%dpx;height:%dpx;box-sizing:border-box;border-top:1px solid %s;'
            'display:grid;grid-template-columns:1fr 1fr 1fr 292px;column-gap:26px;padding:16px 24px 0">%s'
            '<div class="tb" style="border-left:1px solid %s;padding-left:22px;display:flex;flex-direction:column">'
            '<p class="lab">Plant key · codes as in the CAD</p>'
            '<p class="note" style="margin-top:4px">Counts are plants placed on the plan. AS2, BU5, GR4, HY5, JU6 and SP3 are the CAD’s generic symbols; no variety is given for them.</p>'
            '<div style="margin-top:auto;padding-bottom:16px"><svg width="%.1f" height="%.1f" viewBox="0 0 %.1f %.1f" style="display:block;margin-bottom:10px">%s</svg>'
            '<h2>Planting plan</h2><p class="sub">%s</p><p class="note">Drawn from the CAD planting base · base data: CAGIS</p></div></div></div>'
            % (PH, W, H - PH, INK, ''.join(rows), LINE, hw, hh, hw, hh, hud, esc(TITLE)))
    return pl, ''.join(o), band


def enlargement_window(D):
    # the shelter beds: the plants within ~45 ft of the shelter's centre, plus a margin
    return (44270, 28840, 45445, 29935)


AREAS = [  # (label, model x, model y, anchor) — the area names of the owner's sheets; '/' breaks a line
    ('MINDFULNESS/GARDEN', 47600, 31930, 'start'),
]


SHELTER_PW = 850


def shelter(D):
    PW = SHELTER_PW
    pl = Plot(enlargement_window(D), PW, H)
    o = [plan_svg(D, pl, dict(u=1.15, code_min=4.6, code_max=9.0))]
    X, Y = pl.P(44748, 29323)
    o.append('<text x="%.1f" y="%.1f" class="ctx" text-anchor="middle" style="font-size:10.5px">SHELTER</text>' % (X, Y + 3))
    hud, hw, hh = north_scale(pl, [0, 5, 10, 20])
    counts = kind_counts(D, pl)
    rows = key_rows(D, counts, 17)
    col = ('<div style="position:absolute;left:%dpx;top:0;width:%dpx;height:%dpx;box-sizing:border-box;border-left:1px solid %s;padding:24px 24px 0;'
           'display:flex;flex-direction:column;gap:14px"><p class="lab">Plant key · plants in this enlargement</p>%s'
           '<p class="note" style="margin:0;font-size:10.5px;line-height:1.4;color:%s">Codes as in the CAD. AS2, GR4, JU6 and SP3 are generic symbols; no variety is given.</p>'
           '<div class="tb" style="margin-top:auto;padding:14px 0 18px;border-top:1px solid %s"><svg width="%.1f" height="%.1f" viewBox="0 0 %.1f %.1f" style="display:block;margin-bottom:10px">%s</svg>'
           '<h2>Planting plan · shelter beds</h2><p class="sub">%s</p><p class="note">Enlargement, drawn from the CAD planting base · base data: CAGIS</p></div></div>'
           % (PW, W - PW, H, INK, ''.join(rows), MUTED, LINE, hw, hh, hw, hh, hud, esc(TITLE)))
    return pl, ''.join(o), col


def page(fonts, pl, plan, side):
    return ('<!doctype html><meta charset="utf-8"><style>%s</style><div id="sheet">'
            '<svg width="%d" height="%d" viewBox="0 0 %d %d"><defs><clipPath id="pc"><rect width="%d" height="%d"/></clipPath></defs>'
            '<g clip-path="url(#pc)">%s</g></svg>%s</div>' % (css(fonts), W, H, W, H, pl.pw, pl.ph, plan, side))


async def render(pages, scale):
    out = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=os.environ.get('PW_CHROMIUM', '/opt/pw-browsers/chromium'))
        for h in pages:
            pg = await b.new_page(viewport={'width': W, 'height': H}, device_scale_factor=scale)
            await pg.set_content(h, wait_until='load')
            await pg.evaluate('document.fonts.ready')
            await pg.wait_for_timeout(300)
            png = await pg.locator('#sheet').screenshot()
            out.append(Image.open(io.BytesIO(png)).convert('RGB'))
            await pg.close()
        await b.close()
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--dxf', required=True, help='the joined DXF base')
    ap.add_argument('--fonts', default=FONTS)
    ap.add_argument('--web'); ap.add_argument('--print', dest='prn'); ap.add_argument('--html', help='also write the sheets as HTML here')
    a = ap.parse_args()
    D = load(a.dxf)
    sheets = [('plan', overall(D)), ('shelter', shelter(D))]
    pages = [page(a.fonts, *s) for _, s in sheets]
    if a.html:
        for (n, _), h in zip(sheets, pages):
            open(os.path.join(a.html, 'ryan-%s.html' % n), 'w').write(h)
    imgs = asyncio.run(render(pages, 300 / 72))           # 5100 x 3300
    for (n, _), im in zip(sheets, imgs):
        if a.prn:
            f = os.path.join(a.prn, 'ryan-planting-%s.png' % n); im.save(f, optimize=True); print(json.dumps({'out': f, 'size': im.size}))
        if a.web:
            w = im.resize((2400, round(2400 * im.height / im.width)), Image.LANCZOS)
            f = os.path.join(a.web, 'g-ryan-%s.webp' % n); w.save(f, 'WEBP', quality=86, method=6); print(json.dumps({'out': f, 'size': w.size, 'bytes': os.path.getsize(f)}))


if __name__ == '__main__':
    main()
