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
- content/projects/ulsan-hhi-campus/g-massing-contours.webp: the owner's own Rhino viewport
  capture of the seven massing blocks on the site contours (1327x712 JPEG, Display P3, as he
  uploaded it; kept in _drafts/originals/ under the same path with a .jpg extension). Converted
  to sRGB, the top 4 px rows cut (a blue Rhino selection sliver at the top-left edge), otherwise
  untouched and never enlarged: 1327x708, WebP q92. Never substitute the cleaned, upscaled,
  restyled or "developed" versions from the HHI Massing Treatments artifact (Claude made those).
"""
import io
import os
from PIL import Image, ImageCms

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


def p3_capture(src, dst, trim_top=0, quality=92):
    im = Image.open(src)
    icc = im.info.get('icc_profile')
    im = im.convert('RGB')
    if icc:   # tagged Display P3: convert, so the untagged WebP shows the same colours
        im = ImageCms.profileToProfile(im, ImageCms.ImageCmsProfile(io.BytesIO(icc)),
                                       ImageCms.createProfile('sRGB'), outputMode='RGB',
                                       renderingIntent=ImageCms.Intent.RELATIVE_COLORIMETRIC)
    if trim_top:
        im = im.crop((0, trim_top, im.width, im.height))
    im.save(dst, 'WEBP', quality=quality, method=6)
    return im.size


def main():
    jobs = [
        ('hero', lambda: flatten_white(os.path.join(ORIG, 'assets/img/hero-central-planting.webp'),
                                       path('assets/img/hero-central-planting.webp'), quality=85)),
        ('feature', lambda: flatten_white(os.path.join(ORIG, 'content/projects/proctor-landing-park/feature.webp'),
                                          path('content/projects/proctor-landing-park/feature.webp'),
                                          size=(1200, 753), quality=82)),
        ('segd', lambda: logo_png(os.path.join(ORIG, 'assets/logos/segd.png'), path('assets/logos/segd.png'), 600)),
        ('hhi-massing', lambda: p3_capture(os.path.join(ORIG, 'content/projects/ulsan-hhi-campus/g-massing-contours.jpg'),
                                           path('content/projects/ulsan-hhi-campus/g-massing-contours.webp'), trim_top=4)),
    ]
    for name, job in jobs:
        print(name, job())


if __name__ == '__main__':
    main()
