# Needs: npm i axe-core@4 @fontsource/source-serif-4 @fontsource/ibm-plex-sans @fontsource/ibm-plex-mono (next to this file)
"""Site audit: screenshots (desktop + phone, real fonts), axe-core, image/crop/overflow checks.
Usage: python3 audit.py <label> [base_url]"""
import asyncio, json, os, sys, glob
from playwright.async_api import async_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
LABEL = sys.argv[1] if len(sys.argv) > 1 else 'run'
BASE = sys.argv[2] if len(sys.argv) > 2 else 'http://localhost:8321'
OUT = os.path.join(HERE, LABEL)
os.makedirs(OUT, exist_ok=True)
REPO = '/home/user/mreindl118.github.io'
AXE = open(os.path.join(HERE, 'node_modules/axe-core/axe.min.js')).read()
FS = os.path.join(HERE, 'node_modules/@fontsource')

FACES = [('Source Serif 4', 'source-serif-4', w) for w in (400, 600, 700)] + \
        [('IBM Plex Sans', 'ibm-plex-sans', w) for w in (400, 500, 600, 700)] + \
        [('IBM Plex Mono', 'ibm-plex-mono', w) for w in (400, 500)]
CSS = ''
for fam, pkg, w in FACES:
    CSS += ("@font-face{font-family:'%s';font-style:normal;font-weight:%d;font-display:swap;"
            "src:url(https://fonts.gstatic.com/local/%s-latin-%d-normal.woff2) format('woff2');}\n" % (fam, w, pkg, w))

slugs = json.load(open(f'{REPO}/content/projects/index.json'))
PAGES = ['index.html', 'work.html', 'portfolio.html', 'about.html', 'resume.html', '404.html'] + \
        [f'project.html?slug={s}' for s in slugs]
VIEWS = [('desktop', 1440, 900), ('phone', 390, 844)]

CHECK_JS = r"""
() => {
  const out = {broken: [], noalt: [], cropped: [], hscroll: false, clippedText: [], smallText: 0, tapTargets: []};
  out.hscroll = document.documentElement.scrollWidth > window.innerWidth + 1;
  for (const img of document.images) {
    const r = img.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    const name = (img.getAttribute('src') || '').split('/').slice(-2).join('/');
    if (img.complete && img.naturalWidth === 0) out.broken.push(name);
    if (!img.hasAttribute('alt')) out.noalt.push(name);
    const cs = getComputedStyle(img);
    if (cs.objectFit === 'cover' && img.naturalWidth) {
      const ir = img.naturalWidth / img.naturalHeight, br = r.width / r.height;
      const kept = ir > br ? br / ir : ir / br;
      if (kept < 0.85) out.cropped.push({img: name, keptPct: Math.round(kept * 100), box: [Math.round(r.width), Math.round(r.height)]});
    }
  }
  for (const el of document.querySelectorAll('body *')) {
    const cs = getComputedStyle(el);
    if (el.children.length === 0 && el.textContent.trim() && (cs.overflow === 'hidden' || cs.textOverflow === 'ellipsis')
        && (el.scrollWidth > el.clientWidth + 2 || el.scrollHeight > el.clientHeight + 2) && el.clientWidth > 0)
      out.clippedText.push(el.tagName + ': ' + el.textContent.trim().slice(0, 50));
    if (el.children.length === 0 && el.textContent.trim() && parseFloat(cs.fontSize) < 11 && el.getClientRects().length) out.smallText++;
  }
  if (window.innerWidth < 500) {
    for (const a of document.querySelectorAll('a, button, input, [role=button]')) {
      const r = a.getBoundingClientRect();
      if (r.width && r.height && (r.width < 24 || r.height < 24) && getComputedStyle(a).visibility !== 'hidden')
        out.tapTargets.push((a.textContent || a.getAttribute('aria-label') || a.tagName).trim().slice(0, 30) + ` ${Math.round(r.width)}x${Math.round(r.height)}`);
    }
  }
  return out;
}
"""


async def fonts(route):
    url = route.request.url
    if 'fonts.googleapis.com' in url:
        await route.fulfill(status=200, content_type='text/css', body=CSS)
    elif 'fonts.gstatic.com/local/' in url:
        f = url.rsplit('/', 1)[1]
        pkg = f.split('-latin-')[0]
        await route.fulfill(status=200, content_type='font/woff2', body=open(f'{FS}/{pkg}/files/{f}', 'rb').read())
    else:
        await route.abort()


async def audit_page(b, page_path, view, w, h, report):
    ctx = await b.new_context(viewport={'width': w, 'height': h})
    pg = await ctx.new_page()
    await pg.route('https://fonts.googleapis.com/**', fonts)
    await pg.route('https://fonts.gstatic.com/**', fonts)
    errs, bad = [], []
    pg.on('pageerror', lambda e: errs.append(str(e)[:160]))
    pg.on('response', lambda r: bad.append(f'{r.status} {r.url.split("/")[-1][:60]}') if r.status >= 400 and 'localhost' in r.url else None)
    await pg.goto(f'{BASE}/{page_path}', wait_until='networkidle')
    await pg.wait_for_timeout(1200)
    await pg.evaluate("window.scrollTo(0, document.body.scrollHeight)")
    await pg.wait_for_timeout(800)
    await pg.evaluate("window.scrollTo(0, 0)")
    await pg.wait_for_timeout(300)
    key = page_path.replace('.html', '').replace('?slug=', '-')
    await pg.screenshot(path=f'{OUT}/{key}-{view}.png', full_page=True, animations='disabled')
    checks = await pg.evaluate(CHECK_JS)
    await pg.add_script_tag(content=AXE)
    axe = await pg.evaluate("""async () => { const r = await axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa','best-practice']}});
        return r.violations.map(v => ({id: v.id, impact: v.impact, help: v.help, n: v.nodes.length, targets: v.nodes.slice(0,4).map(n => n.target.join(' '))})); }""")
    report[f'{key}|{view}'] = {'checks': checks, 'axe': axe, 'pageerrors': errs, 'http': bad}
    # blowouts on the Projects page
    if page_path == 'work.html':
        n = await pg.evaluate("document.querySelectorAll('#grid .card').length")
        for i in range(n):
            await pg.evaluate(f"document.querySelectorAll('#grid .card')[{i}].click()")
            await pg.wait_for_timeout(900)
            title = await pg.evaluate("document.getElementById('bo-title').textContent")
            body = await pg.evaluate("(() => { const b = document.getElementById('bo-body'); b.scrollTop = b.scrollHeight; return b.scrollHeight; })()")
            await pg.wait_for_timeout(700)
            await pg.evaluate("document.getElementById('bo-body').scrollTop = 0")
            await pg.wait_for_timeout(200)
            await pg.screenshot(path=f'{OUT}/blowout-{i:02d}-{view}.png', animations='disabled')
            c = await pg.evaluate(CHECK_JS)
            report[f'blowout-{i:02d} {title}|{view}'] = {'checks': {k: c[k] for k in ('broken', 'noalt', 'cropped')}}
            await pg.keyboard.press('Escape')
            await pg.wait_for_timeout(200)
    await ctx.close()


async def main():
    report = {}
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/opt/pw-browsers/chromium')
        for page_path in PAGES:
            for view, w, h in VIEWS:
                await audit_page(b, page_path, view, w, h, report)
        await b.close()
    json.dump(report, open(f'{OUT}/report.json', 'w'), indent=1)
    # summary
    axe_tot = {}
    for k, v in report.items():
        for a in v.get('axe', []):
            axe_tot.setdefault(a['id'], [a['impact'], a['help'], 0, set()])
            axe_tot[a['id']][2] += a['n']; axe_tot[a['id']][3].add(k.split('|')[0])
    print('=== AXE violations (id, impact, total nodes, pages) ===')
    for i, (imp, hlp, n, pages) in sorted(axe_tot.items(), key=lambda x: -x[1][2]):
        print(f'{i:28s} {imp:9s} {n:4d}  {hlp[:60]} | {sorted(pages)[:6]}')
    print('=== per page checks ===')
    for k, v in report.items():
        c = v['checks']
        flags = [f'{f}={c[f]}' for f in ('broken', 'noalt', 'cropped', 'clippedText', 'tapTargets') if c.get(f)]
        if c.get('hscroll'): flags.append('HSCROLL')
        if v.get('pageerrors'): flags.append('ERR=' + str(v['pageerrors']))
        if v.get('http'): flags.append('HTTP=' + str(v['http']))
        if flags: print(k, '::', ' '.join(flags)[:600])

asyncio.run(main())
