#!/usr/bin/env python3
"""Render the Camp Washington Skatepark stills for the site and the portfolio book (set 8b).

Every still comes from the site's own viewer (assets/js/skatepark/viewer.js): the script opens a
served page (404.html, for site.css and the self-hosted fonts), mounts the viewer off-screen, pauses
its loop and then draws each shot with the viewer's renderer, scene, lights and callouts. On top of
viewer.still() (render_skatepark.py) it adds what drawings need:

  * supersampling: each shot renders at ss x its size (default 2) and is downsampled with Lanczos;
  * callout badges redrawn at 256 px (same design: pine disc, paper ring, paper Plex Mono numeral)
    and sized in output pixels; ink pins as in the viewer;
  * leaders as thin dashed ink rods (WebGL lines are 1 px and vanish when downsampled);
  * clip: the site cut to a rectangle (renderer clipping planes), so an exploded axon reads as
    plates over the paper; the cut site plate gets a thin base so it reads as a sheet;
  * isolate: one feature alone (the artifact's detail blow-outs), orthographic from the axon view;
  * a tighter shadow frustum per shot (same key-light direction), for crisper close-up shadows;
  * 2D overlays drawn in the page in the site's fonts: the Stock Ave. label (mono, ink, paper halo),
    a north arrow in a panel box (as the viewer's HUD); optional mono layer labels ('layers', off in
    the published set: their leaders crossed the drawing, so the caption names the layers instead).

Usage (serve the repo first, e.g. python3 -m http.server 8340, and run with python3 -B):
  python3 -B _drafts/tools/render_skatepark_set.py --web content/projects/cincinnati-recreation-commission \
      --print /path/to/print-masters [--only NAME,NAME] [--base http://localhost:8340] [--preview 0.25]

The SHOTS table below is the record of every published still: name, size, camera, options. Web files
are WebP (quality 84 unless the shot says otherwise); print masters are PNG at the same or a larger
size (book pages are 1700 px wide at 200 dpi), with a transparent-background twin where the shot has
'alpha_master'. Model: Hunger Skateparks designed the park; this documents it (L-2/L-3).
"""
import argparse, asyncio, base64, io, json, os, sys

from playwright.async_api import async_playwright
from PIL import Image

PAPER = '#F3F0E8'

# Home cameras (viewer.js): assembled target PC (8.127, 0, 22.935), position (42.895, 30, -15.169), fov 45;
# exploded fov 9 at 330 m along AXON, target y 9. World: metres, Y up, true north +Z.
SHOTS = [
    # (a) hero assembled axon: a long lens from high in the south (azimuth 162 deg, 40 deg up), Stock Ave
    # behind, the pool and rec building to the east; the ground fills the frame
    {'name': 'axon', 'w': 2400, 'h': 1500, 'web': 'g-cw-axon.webp', 'print': 'cw-axon.png',
     'camera': {'position': [37.93, 75.85, -62.97], 'target': [10.0, 0.0, 23.0], 'fov': 24},
     'callouts': True, 'badge': 58, 'road': True, 'road_at': [10, 60], 'north': True, 'ui': 1.6,
     'shadow': {'c': [10, 30], 'r': 75}},
    {'name': 'poster', 'w': 2400, 'h': 1350, 'web': 'skatepark-model-poster.webp', 'print': 'cw-poster.png',
     'camera': {'position': [36.04, 70.71, -57.14], 'target': [10.0, 0.0, 23.0], 'fov': 24},
     'callouts': False, 'shadow': {'c': [10, 30], 'r': 75}, 'quality': 82},
    {'name': 'cover', 'w': 1200, 'h': 900, 'web': 'cover.webp', 'print': None, 'ss': 3,
     'camera': {'position': [38.94, 84.31, -66.05], 'target': [10.0, 0.0, 23.0], 'fov': 24},
     'callouts': False, 'shadow': {'c': [10, 30], 'r': 75}, 'quality': 84},
    # (b) exploded axon: the viewer's L-3 direction (AXON), the site cut to a plate around the park
    {'name': 'exploded', 'w': 2400, 'h': 1600, 'web': 'g-cw-exploded.webp', 'print': 'cw-exploded.png',
     'alpha_master': True, 'exploded': True, 'axon_dist': 330, 'fov': 11.6, 'target_y': 3.0,
     'target_xz': [11.0, 25.0], 'clip': {'x0': -28, 'x1': 49, 'z0': -1, 'z1': 52, 'base': 0.9},
     'callouts': True, 'badge': 58, 'leaders': True, 'north': True, 'ui': 1.6,
     'shadow': {'c': [10, 25], 'r': 55}},
    # (c) low obliques of the main features
    {'name': 'eye-1', 'w': 2400, 'h': 1500, 'web': 'g-cw-eye-1.webp', 'print': 'cw-eye-1.png',
     'camera': {'position': [42.0, 7.5, 7.0], 'target': [21.0, -0.8, 23.0], 'fov': 42},
     'shadow': {'c': [22, 22], 'r': 34}},
    {'name': 'eye-2', 'w': 2400, 'h': 1500, 'web': 'g-cw-eye-2.webp', 'print': 'cw-eye-2.png',
     'camera': {'position': [-9.0, 6.5, 27.0], 'target': [7.5, -0.8, 34.5], 'fov': 42},
     'shadow': {'c': [4, 32], 'r': 30}},
    {'name': 'eye-3', 'w': 2400, 'h': 1500, 'web': 'g-cw-eye-3.webp', 'print': 'cw-eye-3.png',
     'camera': {'position': [5.0, 8.0, 9.0], 'target': [-17.0, 0.5, 22.0], 'fov': 40},
     'shadow': {'c': [-8, 18], 'r': 32}},
]
# scattered features framed on their main cluster (world x, z and radius in metres): the street plaza's
# south-edge ledges and rails at callout 6, the bench cluster of the fish-scale mosaics at callout 9
FOCUS = {'F6': {'c': [14.0, 7.0], 'r': 13.0}, 'F9': {'c': [-0.74, 37.94], 'r': 6.0}}
# (d) the nine features alone, as the artifact's L-3 detail blow-outs (orthographic, from the axon direction)
for fid, n in [('F1', 1), ('F4', 2), ('F3', 3), ('F2', 4), ('F5', 5), ('F6', 6), ('F7', 7), ('F8', 8), ('F9', 9)]:
    SHOTS.append({'name': 'detail-%d' % n, 'w': 1200, 'h': 800, 'web': None, 'print': 'cw-detail-%d.png' % n,
                  'alpha_master': True, 'isolate': fid, 'badge': 0, 'focus': FOCUS.get(fid)})

PAGE_JS = r"""
async (args) => {
  if (!window.__sk) {
    const box = document.createElement('div');
    box.style.cssText = 'position:fixed;left:0;top:0;width:800px;height:500px;z-index:-1';
    document.body.appendChild(box);
    await document.fonts.load('500 64px "IBM Plex Mono"', '0123456789');
    await document.fonts.load('400 64px "IBM Plex Mono"', 'ABC');
    const mod = await import(new URL('assets/js/skatepark/viewer.js', location.href).href);
    const v = await mod.mount(box, { spin: false, preserveDrawingBuffer: true });
    v.pause();
    window.__sk = { v, box };
  }
  const { v } = window.__sk, T = v.three, THREE = T.THREE, R = T.renderer, scene = T.scene, cam = T.camera;
  const o = args.shot, ss = args.ss, W = Math.round(o.w * args.scale * ss), H = Math.round(o.h * args.scale * ss);
  const PINE = '#2E4A3B', PAPER = '#F3F0E8', INK = '#26292B', PANEL = '#FAF8F2', LINE = '#D9D3C2', MID = '#C7C1AE';
  const k = W / o.w;                                  // render px per output px (scale * ss)
  const ui = k * (o.ui || 1);                          // overlay unit: output px x the shot's ui factor
  const undo = [];

  // ---- view state: exploded or not (instant), camera ----
  v.setExploded(!!o.exploded, { instant: true });
  let camera = cam;
  const PC = new THREE.Vector3(8.12737476923077, 0, 22.935027692307695);
  const AXON = new THREE.Vector3(-1.1537, 1.05, -0.9952).normalize().applyAxisAngle(new THREE.Vector3(0, 1, 0), T.parkRoot.rotation.y);
  if (o.isolate) {
    const f = T.features[o.isolate];
    Object.values(T.features).forEach(g => { undo.push([g, 'visible', g.visible]); g.visible = g === f; });
    f.updateMatrixWorld(true);
    // focus: keep only the parts of a scattered feature near one point (its callout), e.g. the street plaza
    if (o.focus) f.traverse(x => { if (x.isMesh || x.isLine) { const bb = new THREE.Box3().setFromObject(x), cc = bb.getCenter(new THREE.Vector3());
      if (Math.hypot(cc.x - o.focus.c[0], cc.z - o.focus.c[1]) > o.focus.r) { undo.push([x, 'visible', x.visible]); x.visible = false; } } });
    const b = new THREE.Box3(); f.traverse(x => { if ((x.isMesh || x.isLine) && x.visible) b.expandByObject(x); });
    const c = b.getCenter(new THREE.Vector3()), rad = b.getBoundingSphere(new THREE.Sphere()).radius * 1.04;
    const asp = W / H, oc = new THREE.OrthographicCamera(-rad * asp, rad * asp, rad, -rad, 0.1, rad * 40);
    oc.position.copy(c).addScaledVector(AXON, rad * 10); oc.lookAt(c); oc.updateProjectionMatrix(); oc.updateMatrixWorld(true);
    // fit the projected box tightly (with a margin) instead of the bounding sphere
    const pts = []; for (const x of [b.min.x, b.max.x]) for (const y of [b.min.y, b.max.y]) for (const z of [b.min.z, b.max.z]) pts.push(new THREE.Vector3(x, y, z).project(oc));
    let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity; pts.forEach(p => { x0 = Math.min(x0, p.x); x1 = Math.max(x1, p.x); y0 = Math.min(y0, p.y); y1 = Math.max(y1, p.y); });
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hw = (x1 - x0) / 2 * rad * asp, hh = (y1 - y0) / 2 * rad, m = 1.1;
    const half = Math.max(hh, hw / asp) * m, ox = cx * rad * asp, oy = cy * rad;
    oc.left = ox - half * asp; oc.right = ox + half * asp; oc.top = oy + half; oc.bottom = oy - half; oc.updateProjectionMatrix(); oc.updateMatrixWorld(true);
    camera = oc;
    o.shadow = { c: [c.x, c.z], r: rad * 1.6 };
    // the shadow catcher sits just under the feature (the scene's sits under the whole site, 2.3 m down)
    T.scene.traverse(x => { if (x.isMesh && x.material && x.material.isShadowMaterial) { undo.push([x.position, 'y', x.position.y]); x.position.y = Math.min(0, b.min.y) - 0.01; } });
  } else if (o.exploded) {
    cam.fov = o.fov || 9;
    const t = new THREE.Vector3(o.target_xz ? o.target_xz[0] : PC.x, o.target_y ?? 9, o.target_xz ? o.target_xz[1] : PC.z);
    T.controls.target.copy(t); cam.position.copy(t).addScaledVector(AXON, o.axon_dist || 330);
  } else if (o.camera) {
    cam.fov = o.camera.fov; T.controls.target.fromArray(o.camera.target); cam.position.fromArray(o.camera.position);
  }
  if (camera === cam) { cam.near = 0.5; cam.far = 1500; cam.aspect = W / H; cam.updateProjectionMatrix(); cam.lookAt(T.controls.target); cam.updateMatrixWorld(true); }

  // ---- callouts: 256 px badges sized in output px; or hidden ----
  T.pins.visible = !!o.callouts && !o.isolate;
  if (T.pins.visible) {
    if (!window.__sk.hiBadges) {
      window.__sk.hiBadges = true;
      T.pins.children.filter(s => s.isSprite).forEach(sp => {
        const n = sp.name.split('_')[1], c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d');
        g.beginPath(); g.arc(128, 128, 108, 0, Math.PI * 2); g.fillStyle = PINE; g.fill();
        g.lineWidth = 16; g.strokeStyle = PAPER; g.stroke();
        g.fillStyle = PAPER; g.font = '500 128px "IBM Plex Mono", ui-monospace, monospace';
        g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(n, 128, 136);
        const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 4;
        sp.material.map.dispose(); sp.material.map = tex; sp.material.needsUpdate = true;
      });
    }
    const px = (o.badge || 46) * k, f = 1 / Math.tan(cam.fov * Math.PI / 360), s = 2 * px / (f * H);
    T.pins.children.filter(x => x.isSprite).forEach(sp => sp.scale.set(s, s, 1));
    // pins: about 2.2 output px wide at the target distance
    const d = cam.position.distanceTo(T.controls.target), mPerPx = 2 * d * Math.tan(cam.fov * Math.PI / 360) / H;
    const r = Math.max(0.03, 1.1 * k * mPerPx);
    T.pins.children.filter(x => x.isMesh).forEach(p => { undo.push([p.scale, 'x', p.scale.x], [p.scale, 'z', p.scale.z]); p.scale.x = p.scale.z = r / 0.04; });
  }

  // ---- leaders: dashed ink rods along the viewer's leader lines ----
  let rods = null;
  if (o.leaders && o.exploded) {
    rods = new THREE.Group(); scene.add(rods);
    const d = cam.position.distanceTo(T.controls.target), mPerPx = 2 * d * Math.tan(cam.fov * Math.PI / 360) / H;
    const r = 1.0 * k * mPerPx, dash = 9 * k * mPerPx, gap = 6 * k * mPerPx;
    const mat = new THREE.MeshBasicMaterial({ color: INK, transparent: true, opacity: 0.8 });
    const geo = new THREE.CylinderGeometry(r, r, 1, 6);
    T.leaders.children.forEach(l => {
      const p = l.geometry.attributes.position, a = new THREE.Vector3(p.getX(0), p.getY(0), p.getZ(0)), b = new THREE.Vector3(p.getX(1), p.getY(1), p.getZ(1));
      const L = a.distanceTo(b), dir = b.clone().sub(a).normalize();
      for (let t = 0; t < L; t += dash + gap) {
        const len = Math.min(dash, L - t), m = new THREE.Mesh(geo, mat);
        m.position.copy(a).addScaledVector(dir, t + len / 2); m.scale.y = len; m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir); rods.add(m);
      }
    });
    undo.push([T.leaders, 'visible', T.leaders.visible]); T.leaders.visible = false;
  }

  // ---- clip the site to a plate (exploded view) ----
  let base = null;
  if (o.clip) {
    const c = o.clip;
    scene.traverse(x => { if (x.isMesh && x.material && x.material.isShadowMaterial) { undo.push([x, 'visible', x.visible]); x.visible = false; } });
    // a building or tree the cut passes through would show its inside: leave those out (flat ground is cut cleanly)
    T.features.site.traverse(x => {
      if (!x.isMesh || !x.castShadow) return;
      const b = new THREE.Box3().setFromObject(x);
      if (b.min.x < c.x0 || b.max.x > c.x1 || b.min.z < c.z0 || b.max.z > c.z1) { undo.push([x, 'visible', x.visible]); x.visible = false; }
    });
    // trees are groups of meshes: hide a whole tree if any part of it was hidden
    T.features.site.children.forEach(g => { if (g.isGroup) { let cut = false; g.traverse(x => { if (x.isMesh && !x.visible) cut = true; }); if (cut) g.traverse(x => { if (x.isMesh && x.visible) { undo.push([x, 'visible', true]); x.visible = false; } }); } });
    R.clippingPlanes = [new THREE.Plane(new THREE.Vector3(1, 0, 0), -c.x0), new THREE.Plane(new THREE.Vector3(-1, 0, 0), c.x1),
                        new THREE.Plane(new THREE.Vector3(0, 0, 1), -c.z0), new THREE.Plane(new THREE.Vector3(0, 0, -1), c.z1)];
    if (c.base) {
      // the cut edge of the site plate: a thin sheet under the turf, its sides a soil tone
      const g = new THREE.BoxGeometry(c.x1 - c.x0 - 0.02, c.base, c.z1 - c.z0 - 0.02);
      const side = new THREE.MeshStandardMaterial({ color: '#8C7B63', roughness: 0.95 }), top = new THREE.MeshStandardMaterial({ color: '#B9AE98', roughness: 0.95 });
      base = new THREE.Mesh(g, [side, side, top, side, side, side]); base.position.set((c.x0 + c.x1) / 2, -c.base / 2 - 0.02, (c.z0 + c.z1) / 2);
      base.receiveShadow = false; base.castShadow = false; scene.add(base);
    }
  }

  // ---- shadows: same key direction, frustum fitted to the shot ----
  let key; scene.traverse(x => { if (x.isDirectionalLight && x.castShadow) key = x; });
  const sc = key.shadow.camera, saveSh = { p: key.position.clone(), t: key.target.position.clone(), l: sc.left, r: sc.right, b: sc.bottom, tp: sc.top };
  if (o.shadow) {
    const dir = key.position.clone().sub(key.target.position).normalize(), c = new THREE.Vector3(o.shadow.c[0], 0, o.shadow.c[1]);
    key.target.position.copy(c); key.target.updateMatrixWorld(); key.position.copy(c).addScaledVector(dir, 160); key.updateMatrixWorld();
    sc.left = sc.bottom = -o.shadow.r; sc.right = sc.top = o.shadow.r; sc.updateProjectionMatrix();
  }
  R.shadowMap.needsUpdate = true;

  // ---- render ----
  const savePR = R.getPixelRatio();
  R.setPixelRatio(1); R.setSize(W, H, false);
  if (o.background === null || args.alpha) R.setClearColor(0x000000, 0); else R.setClearColor(o.background || PAPER, 1);
  R.render(scene, camera);
  const gl3d = R.domElement;

  // ---- 2D overlays in the site's fonts ----
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H; const g = cv.getContext('2d');
  g.drawImage(gl3d, 0, 0);
  const proj = p => { const q = p.clone().project(camera); return [(q.x * 0.5 + 0.5) * W, (-q.y * 0.5 + 0.5) * H, q.z]; };
  const info = {};
  if (o.road && T.PLAN.site && T.PLAN.site.centre) {
    // Stock Ave.: on the road centre line, at the point nearest the view's target, turned to follow the road
    const S = T.PLAN.scaleFtPerPx * 0.3048, X = x => (x - 480) * S, Z = y => (y - 640) * S, C = T.PLAN.site.centre;
    const tgt = o.road_at ? new THREE.Vector3(o.road_at[0], 0, o.road_at[1]) : T.controls.target.clone().setY(0);
    let best = null;
    for (let i = 1; i < C.length; i++) {
      const s = new THREE.Line3(new THREE.Vector3(X(C[i - 1][0]), 0.05, Z(C[i - 1][1])), new THREE.Vector3(X(C[i][0]), 0.05, Z(C[i][1])));
      const q = s.closestPointToPoint(tgt, true, new THREE.Vector3()), dd = q.distanceTo(tgt); if (!best || dd < best.d) best = { d: dd, q, s };
    }
    const a = proj(best.q), dir = best.s.delta(new THREE.Vector3()).normalize(), b = proj(best.q.clone().addScaledVector(dir, 10));
    let ang = Math.atan2(b[1] - a[1], b[0] - a[0]); if (ang > Math.PI / 2) ang -= Math.PI; if (ang < -Math.PI / 2) ang += Math.PI;
    if (a[0] > 0 && a[0] < W && a[1] > 0 && a[1] < H) {
      g.save(); g.translate(a[0], a[1]); g.rotate(ang);
      const fs = (o.road_size || 15) * ui; g.font = '500 ' + fs + 'px "IBM Plex Mono"'; g.letterSpacing = (0.24 * fs) + 'px';
      g.textAlign = 'center'; g.textBaseline = 'middle';
      g.lineJoin = 'round'; g.lineWidth = 0.32 * fs; g.strokeStyle = PAPER; g.strokeText('STOCK AVE.', 0, 0);
      g.fillStyle = INK; g.fillText('STOCK AVE.', 0, 0); g.restore();
      info.road = [a[0] / k, a[1] / k];
    }
  }
  if (o.layers && o.exploded) {
    // layer names, after the artifact's L-3 note ("site, slab, bowls, street & entry, furnishings"): each label
    // sits in the left margin at the height of its layer's left-most point, with a 1px ink rule out to it
    const LY = [0, 5, 10, 15, 19], names = ['SITE', 'SLAB', 'BOWLS + BANKS', 'STREET + ENTRY', 'FURNISHINGS'];
    const fs = 13 * ui, c = o.clip, lx = 22 * ui;
    g.font = '500 ' + fs + 'px "IBM Plex Mono"'; g.letterSpacing = (0.16 * fs) + 'px'; g.textBaseline = 'middle'; g.textAlign = 'left';
    info.layers = [];
    names.forEach((nm, i) => {
      let pt;
      if (i === 0) pt = proj(new THREE.Vector3(c.x1, 0, c.z1));
      else {
        pt = [Infinity, 0];
        Object.values(T.features).forEach(f => {
          if (f.userData.layer !== i || !f.visible) return;
          f.traverse(x => {
            if (!x.isMesh || !x.visible) return;
            const pos = x.geometry.attributes.position, v3 = new THREE.Vector3();
            for (let j = 0; j < pos.count; j += 3) { v3.fromBufferAttribute(pos, j).applyMatrix4(x.matrixWorld); const q = proj(v3); if (q[0] < pt[0]) pt = q; }
          });
        });
      }
      const tw = g.measureText(nm).width;
      g.fillStyle = INK; g.fillText(nm, lx, pt[1]);
      g.strokeStyle = INK; g.lineWidth = Math.max(1, 1.0 * ui); g.beginPath();
      g.moveTo(lx + tw + 10 * ui, Math.round(pt[1]) + 0.5); g.lineTo(pt[0] - 8 * ui, Math.round(pt[1]) + 0.5); g.stroke();
      g.beginPath(); g.arc(pt[0] - 8 * ui, pt[1], 2.2 * ui, 0, Math.PI * 2); g.fillStyle = INK; g.fill();
      info.layers.push([nm, +(pt[0] / k).toFixed(1), +(pt[1] / k).toFixed(1)]);
    });
  }
  if (o.north) {
    // north arrow in a panel box at the lower left, true to the camera (as the viewer's HUD)
    const t = T.controls.target.clone().setY(0), p0 = proj(t), pn = proj(t.clone().add(new THREE.Vector3(0, 0, 5)));
    const rot = Math.atan2(pn[0] - p0[0], -(pn[1] - p0[1]));
    const u = ui, bx = 18 * u, by = H - 18 * u, bw = 58 * u, bh = 72 * u;
    g.fillStyle = PANEL; g.fillRect(bx, by - bh, bw, bh); g.strokeStyle = LINE; g.lineWidth = 1.5 * u; g.strokeRect(bx, by - bh, bw, bh);
    const cx = bx + bw / 2, cy = by - bh + 28 * u, rr = 20 * u;
    g.beginPath(); g.arc(cx, cy, rr, 0, Math.PI * 2); g.strokeStyle = MID; g.lineWidth = 1.5 * u; g.stroke();
    g.save(); g.translate(cx, cy); g.rotate(rot);
    const sN = rr / 13.5 * 1.0;
    g.beginPath(); g.moveTo(0, -12 * sN); g.lineTo(5 * sN, 4 * sN); g.lineTo(0, 1 * sN); g.lineTo(-5 * sN, 4 * sN); g.closePath(); g.fillStyle = PINE; g.fill();
    g.beginPath(); g.moveTo(0, 12 * sN); g.lineTo(5 * sN, 4 * sN); g.lineTo(0, 1 * sN); g.lineTo(-5 * sN, 4 * sN); g.closePath(); g.fillStyle = MID; g.fill();
    g.restore();
    const fs = 14 * u; g.font = '500 ' + fs + 'px "IBM Plex Mono"'; g.letterSpacing = '0px'; g.fillStyle = INK; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText('N', cx, by - 13 * u);
    info.north = (rot * 180 / Math.PI);
  }
  const url = cv.toDataURL('image/png');

  // ---- put everything back ----
  if (rods) { rods.traverse(x => { if (x.geometry) x.geometry.dispose(); }); scene.remove(rods); }
  if (base) { base.geometry.dispose(); base.material.forEach(m => m.dispose()); scene.remove(base); }
  R.clippingPlanes = [];
  undo.reverse().forEach(([obj, prop, val]) => { obj[prop] = val; });
  key.position.copy(saveSh.p); key.target.position.copy(saveSh.t); key.updateMatrixWorld(); key.target.updateMatrixWorld();
  sc.left = saveSh.l; sc.right = saveSh.r; sc.bottom = saveSh.b; sc.top = saveSh.tp; sc.updateProjectionMatrix();
  R.setPixelRatio(savePR); R.setClearColor(0x000000, 0);
  const camOut = camera.isPerspectiveCamera ? { position: cam.position.toArray().map(x => +x.toFixed(3)), target: T.controls.target.toArray().map(x => +x.toFixed(3)), fov: cam.fov } : { ortho: true };
  return { url, camera: camOut, info, size: [W, H] };
}
"""


def save(im, path, quality):
    ext = os.path.splitext(path)[1].lower()
    if ext == '.webp':
        im.save(path, 'WEBP', quality=quality, method=6)
    else:
        im.save(path, optimize=True)


async def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--web', default=None, help='folder for the web WebP files')
    ap.add_argument('--print', dest='prn', default=None, help='folder for the PNG print masters')
    ap.add_argument('--preview', type=float, default=0, help='render only a small PNG of each shot at this scale, into --print')
    ap.add_argument('--only', default='')
    ap.add_argument('--base', default='http://localhost:8340')
    ap.add_argument('--chromium', default=os.environ.get('PW_CHROMIUM', '/opt/pw-browsers/chromium'))
    ap.add_argument('--shots', default=None, help='JSON file with shot overrides, keyed by name (for trying cameras)')
    a = ap.parse_args()
    only = set(filter(None, a.only.split(',')))
    over = json.load(open(a.shots)) if a.shots else {}
    report = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=a.chromium, args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
        pg = await b.new_page(viewport={'width': 1000, 'height': 700})
        errors = []
        pg.on('pageerror', lambda e: errors.append(str(e)))
        pg.on('console', lambda m: errors.append(m.type + ': ' + m.text) if m.type in ('error', 'warning') else None)
        await pg.goto(a.base.rstrip('/') + '/404.html', wait_until='networkidle')
        for shot in SHOTS:
            if only and shot['name'] not in only:
                continue
            shot = {**shot, **over.get(shot['name'], {})}
            ss = shot.get('ss', 2)
            scale = a.preview or 1
            passes = [False] + ([True] if shot.get('alpha_master') and a.prn and not a.preview else [])
            for alpha in passes:
                r = await pg.evaluate(PAGE_JS, {'shot': shot, 'ss': 1 if a.preview else ss, 'scale': scale, 'alpha': alpha})
                im = Image.open(io.BytesIO(base64.b64decode(r['url'].split(',', 1)[1])))
                w, h = round(shot['w'] * scale), round(shot['h'] * scale)
                if im.size != (w, h):
                    im = im.resize((w, h), Image.LANCZOS)
                if not alpha:
                    im = im.convert('RGB')
                if a.preview:
                    out = os.path.join(a.prn, 'preview-' + shot['name'] + '.png'); im.save(out)
                    report.append({'name': shot['name'], 'out': out, 'camera': r['camera'], 'info': r['info']})
                    continue
                if alpha:
                    out = os.path.join(a.prn, shot['print'].replace('.png', '-alpha.png')); im.save(out, optimize=True)
                    report.append({'name': shot['name'], 'out': out, 'size': im.size, 'bytes': os.path.getsize(out)})
                    continue
                if a.web and shot.get('web'):
                    out = os.path.join(a.web, shot['web']); save(im, out, shot.get('quality', 84))
                    report.append({'name': shot['name'], 'out': out, 'size': im.size, 'bytes': os.path.getsize(out), 'camera': r['camera'], 'info': r['info']})
                if a.prn and shot.get('print'):
                    out = os.path.join(a.prn, shot['print']); im.save(out, optimize=True)
                    report.append({'name': shot['name'], 'out': out, 'size': im.size, 'bytes': os.path.getsize(out), 'camera': r['camera'], 'info': r['info']})
        await b.close()
    if errors:
        print('page errors/warnings:', *errors, sep='\n  ', file=sys.stderr)
    print(json.dumps(report, indent=1))
    if any('rror' in e for e in errors):
        sys.exit(1)


asyncio.run(main())
