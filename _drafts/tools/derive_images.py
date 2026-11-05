#!/usr/bin/env python3
"""Web derivatives the site serves in place of (or beside) heavier source images.

Run from the repo root after replacing any of the sources below:
    python3 _drafts/tools/derive_images.py

- assets/img/hero-central-planting.webp: Home's hero drawing, flattened onto white
  (the page draws it with mix-blend-mode: multiply, so white drops into the paper and the
  hero's contour lines still show through). WebP q85, 828x819.
- content/projects/proctor-landing-park/feature.webp: the earlier Home feature image,
  flattened onto white the same way and cut to 1200x753, WebP q82.
  The alpha originals of both are kept in _drafts/originals/ (same paths).
- assets/logos/segd.png: 600w (it renders at most 286 css px), 256-colour PNG. The 2400 px
  original is in _drafts/originals/assets/logos/segd.png.
"""
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ORIG = os.path.join(ROOT, '_drafts', 'originals')


def path(*p):
    return os.path.join(ROOT, *p)


def flatten_white(src, dst, size=None, quality=85):
    im = Image.open(src).convert('RGBA')
    if size and im.size != size:
        im = im.resize(size, Image.LANCZOS)
    out = Image.alpha_composite(Image.new('RGBA', im.size, (255, 255, 255, 255)), im).convert('RGB')
    out.save(dst, 'WEBP', quality=quality, method=6)
    return out.size


def logo_png(src, dst, width):
    im = Image.open(src).convert('RGBA')
    h = round(im.size[1] * width / im.size[0])
    im = im.resize((width, h), Image.LANCZOS)
    if im.getchannel('A').getextrema() == (255, 255):
        im = im.convert('RGB').quantize(colors=256, method=Image.MEDIANCUT, dither=Image.NONE)
    im.save(dst, optimize=True)
    return width, h


def main():
    jobs = [
        ('hero', lambda: flatten_white(os.path.join(ORIG, 'assets/img/hero-central-planting.webp'),
                                       path('assets/img/hero-central-planting.webp'), quality=85)),
        ('feature', lambda: flatten_white(os.path.join(ORIG, 'content/projects/proctor-landing-park/feature.webp'),
                                          path('content/projects/proctor-landing-park/feature.webp'),
                                          size=(1200, 753), quality=82)),
        ('segd', lambda: logo_png(os.path.join(ORIG, 'assets/logos/segd.png'), path('assets/logos/segd.png'), 600)),
    ]
    for name, job in jobs:
        print(name, job())


if __name__ == '__main__':
    main()
