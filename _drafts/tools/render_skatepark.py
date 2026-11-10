#!/usr/bin/env python3
"""Render a still of the Camp Washington Skatepark 3D model headlessly (set 8a).

It opens a page of the served site (default 404.html, which loads site.css and its fonts), imports
assets/js/skatepark/viewer.js, mounts the model in an off-screen box and calls viewer.still(), so
the still is exactly the site's own scene, lights, shadows and callouts.

Usage (serve the repo first, e.g. python3 -m http.server 8340):
  python3 _drafts/tools/render_skatepark.py OUT.webp [--base http://localhost:8340]
      [--width 1000 --height 562.5 --scale 2] [--view assembled|exploded]
      [--callouts] [--camera '{"position":[x,y,z],"target":[x,y,z],"fov":45}']
      [--background '#F3F0E8' | transparent] [--quality 82]

--width/--height are CSS pixels and --scale the pixel ratio, so the defaults give a 2000x1125
image with callout badges at their on-screen size. Output: .webp (Pillow), .png or .jpg. The
camera used is printed as JSON, so a view found in the live viewer (viewer.state()) can be
passed back in with --camera. The poster on the CRC page came from:
  python3 _drafts/tools/render_skatepark.py \\
    content/projects/cincinnati-recreation-commission/skatepark-model-poster.webp
Needs Python Playwright with a Chromium that has WebGL 2 (headless SwiftShader works).
"""
import argparse, asyncio, base64, io, json, os, sys

from playwright.async_api import async_playwright
from PIL import Image

ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
ap.add_argument('out')
ap.add_argument('--base', default='http://localhost:8340')
ap.add_argument('--page', default='404.html')
ap.add_argument('--width', type=float, default=1000)
ap.add_argument('--height', type=float, default=562.5)
ap.add_argument('--scale', type=float, default=2)
ap.add_argument('--view', choices=['assembled', 'exploded'], default='assembled')
ap.add_argument('--callouts', action='store_true', help='draw the numbered callouts (off by default)')
ap.add_argument('--camera', default=None, help='JSON {position, target, fov}')
ap.add_argument('--background', default='#F3F0E8', help="a CSS colour, or 'transparent' (PNG/WebP only)")
ap.add_argument('--quality', type=int, default=82)
ap.add_argument('--chromium', default=os.environ.get('PW_CHROMIUM', '/opt/pw-browsers/chromium'))
a = ap.parse_args()

JS = """async (o) => {
  const box = document.createElement('div');
  box.style.cssText = 'position:fixed; left:0; top:0; width:' + o.width + 'px; height:' + o.height + 'px; z-index:-1';
  document.body.appendChild(box);
  const mod = await import(new URL('assets/js/skatepark/viewer.js', location.href).href);
  const v = await mod.mount(box, { spin: false });
  await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
  const url = v.still({ width: o.width, height: o.height, scale: o.scale, view: o.view, callouts: o.callouts,
                        camera: o.camera, background: o.background === 'transparent' ? null : o.background });
  const state = v.lastStill;
  v.dispose();
  return { url, state };
}"""


async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=a.chromium)
        pg = await b.new_page(viewport={'width': max(800, int(a.width)), 'height': max(600, int(a.height))})
        errors = []
        pg.on('pageerror', lambda e: errors.append(str(e)))
        await pg.goto(f"{a.base.rstrip('/')}/{a.page}", wait_until='networkidle')
        r = await pg.evaluate(JS, {'width': a.width, 'height': a.height, 'scale': a.scale, 'view': a.view,
                                   'callouts': a.callouts, 'camera': json.loads(a.camera) if a.camera else None,
                                   'background': a.background})
        await b.close()
    if errors:
        sys.exit('page error: ' + errors[0])
    im = Image.open(io.BytesIO(base64.b64decode(r['url'].split(',', 1)[1])))
    ext = os.path.splitext(a.out)[1].lower()
    if ext == '.webp':
        im.save(a.out, 'WEBP', quality=a.quality, method=6)
    elif ext in ('.jpg', '.jpeg'):
        im.convert('RGB').save(a.out, 'JPEG', quality=a.quality, optimize=True, progressive=True)
    else:
        im.save(a.out, optimize=True)
    print(json.dumps({'out': a.out, 'size': im.size, 'bytes': os.path.getsize(a.out), 'view': a.view,
                      'camera': {k: r['state'][k] for k in ('position', 'target', 'fov')}}))


asyncio.run(main())
