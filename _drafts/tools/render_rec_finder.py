"""The CRC page's Rec Finder still (set 8h): the prototype's opening map at desktop size.

    python3 -I _drafts/tools/render_rec_finder.py <artifact dir> \
        content/projects/cincinnati-recreation-commission/rec-finder.webp

<artifact dir> holds a copy of the owner's CRC Rec Finder artifact as published: its index.html and
plans/crc-logo.png (read both with the Artifact tool). The page is self-contained except for d3,
which it loads from jsDelivr (d3@7.9.0); every other non-local request is blocked. The rec-center
plan previews it warms in the background are only drawn when zoomed in, so they aren't needed.

Only the opening map is captured: no card, tooltip or site map open, the masthead cut down to the
brand, the counts and the Present button (the rest hidden in place, so nothing moves), none of the
generated site models, no phone numbers.
The 7 s "Point at an icon" hint has faded by the capture. 1440x1030 is the height at which the side panel's bottom edge falls in
the gap after "Site types" (y 1022-1036), so no sentence is cut. Drawn at 2x, then 1800x1288 WebP.
"""
import asyncio
import sys
from pathlib import Path

from PIL import Image
from playwright.async_api import async_playwright

W, H, DPR = 1440, 1030, 2
OUT_W = 1800
D3 = 'https://cdn.jsdelivr.net/npm/d3@7.9.0/'


async def capture(index_html, png):
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/opt/pw-browsers/chromium')
        ctx = await b.new_context(viewport={'width': W, 'height': H}, device_scale_factor=DPR)
        pg = await ctx.new_page()
        errors = []

        async def route(r):
            if r.request.url.startswith(('file://', 'data:', 'blob:', D3)):
                await r.continue_()
            else:
                await r.abort()
        await pg.route('**/*', route)
        pg.on('pageerror', lambda e: errors.append(str(e)))
        await pg.goto(index_html.resolve().as_uri(), wait_until='load', timeout=60000)
        mode0 = await pg.evaluate('document.body.dataset.mode')   # the view the page opens on
        await pg.wait_for_timeout(9500)
        state = await pg.evaluate('''() => ({
          mode: document.body.dataset.mode,
          hint: document.getElementById('hint').hidden,
          card: document.getElementById('card').hidden,
          tip: document.getElementById('tip').hidden })''')
        if errors or state != {'mode': mode0, 'hint': True, 'card': True, 'tip': True}:
            sys.exit(f'not a clean opening view: {state} {errors[:3]}')
        # the masthead keeps only the brand, the counts and the Present button
        await pg.add_style_tag(content='header.mast > *:not(.brand-plate):not(.brand):not(#mastStats):not(#presBtn)'
                               ' { visibility: hidden !important; }')
        await pg.wait_for_timeout(300)
        await pg.screenshot(path=str(png))
        await b.close()


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    if not (src / 'plans' / 'crc-logo.png').exists():
        sys.exit('plans/crc-logo.png is missing beside index.html (the header would show its text fallback)')
    png = out.with_suffix('.capture.png')
    asyncio.run(capture(src / 'index.html', png))
    im = Image.open(png).convert('RGB')
    im = im.resize((OUT_W, round(im.height * OUT_W / im.width)), Image.LANCZOS)
    im.save(out, 'WEBP', quality=88, method=6)
    png.unlink()
    print(out, im.size)


if __name__ == '__main__':
    main()
