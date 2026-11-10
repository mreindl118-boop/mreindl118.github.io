import { PLAN } from './plan-geometry.js';
// Builds the Camp Washington Skatepark massing model. Every feature lands in its own group
// (F1…F9, plus 'site' and 'slab') tagged with {n, label, layer} so the sheets can explode it,
// isolate it for blow-outs, or export it whole.
export function buildSkatepark(THREE) {
  const S = PLAN.scaleFtPerPx * 0.3048;              // metres per plan px
  const X = x => (x - 480) * S, Z = y => (y - 640) * S;
  const TOP = 0.15;                                   // slab top
  const labelOf = n => (PLAN.callouts.find(c => c.n === n) || {}).label || '';

  // procedural concrete grain — seamless (blobs wrap across the tile edges) and low-frequency, so no grid shows on big slabs
  const grain = (() => { const N = 1024, c = document.createElement('canvas'); c.width = c.height = N; const g = c.getContext('2d');
    let sd = 7; const rnd = () => { sd = (sd * 16807) % 2147483647; return (sd - 1) / 2147483646; };
    g.fillStyle = '#F2F2F2'; g.fillRect(0, 0, N, N);
    const img = g.getImageData(0, 0, N, N), d = img.data; for (let i = 0; i < d.length; i += 4) { const v = 236 + Math.floor(rnd() * 19); d[i] = d[i + 1] = d[i + 2] = v; } g.putImageData(img, 0, 0);
    const blob = (x, y, r, col) => { const gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, col); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2); };
    for (let k = 0; k < 260; k++) { const x = rnd() * N, y = rnd() * N, r = k < 60 ? 160 + rnd() * 220 : 30 + rnd() * 90, dark = rnd() < .5, col = dark ? 'rgba(40,44,48,.035)' : 'rgba(255,255,255,.06)';
      for (const ox of [-N, 0, N]) for (const oy of [-N, 0, N]) blob(x + ox, y + oy, r, col); }
    const t = new THREE.CanvasTexture(c); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(.07, .07); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; return t; })();

  // stylised kaleidoscope mural for the wall-ride (left half of the map); right half is plain concrete for the back and edges
  const muralTex = (() => { const c = document.createElement('canvas'); c.width = 2048; c.height = 384; const g = c.getContext('2d');
    g.fillStyle = '#B9BFC4'; g.fillRect(0, 0, 2048, 384); g.fillStyle = '#F6F1E6'; g.fillRect(0, 0, 922, 384);
    const pal = ['#E2477A', '#2BB3A3', '#F5C242', '#7B4FA3', '#F07F3C', '#3D6FB5', '#8CC63F'], cx = 512, cy = 192, N = 20, radii = [560, 440, 335, 245, 170, 108, 58];
    g.save(); g.beginPath(); g.rect(0, 0, 922, 384); g.clip();
    radii.forEach((r, ring) => { for (let k = 0; k < N; k++) { const a0 = k / N * Math.PI * 2, a1 = (k + 1) / N * Math.PI * 2; g.beginPath(); g.moveTo(cx, cy); g.arc(cx, cy, r, a0, a1); g.closePath(); g.fillStyle = pal[(k + ring * 3) % pal.length]; g.fill(); } });
    g.strokeStyle = 'rgba(255,255,255,.75)'; g.lineWidth = 4; for (let k = 0; k < N; k++) { const a = k / N * Math.PI * 2; g.beginPath(); g.moveTo(cx, cy); g.lineTo(cx + 600 * Math.cos(a), cy + 600 * Math.sin(a)); g.stroke(); }
    radii.forEach(r => { g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.stroke(); }); g.restore();
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; t.generateMipmaps = false; t.minFilter = THREE.LinearFilter; return t; })();

  const mat = (name, color, o = {}) => { const m = new THREE.MeshStandardMaterial({ color, roughness: o.roughness ?? .92, metalness: o.metalness ?? 0, side: THREE.DoubleSide, map: o.tex ? grain : null, transparent: !!o.opacity, opacity: o.opacity ?? 1 }); m.name = name; return m; };
  const M = {
    grass: mat('grass', '#8B9A5E', { tex: 1 }), dirt: mat('dirt', '#CDB98A', { tex: 1 }), concrete: mat('concrete', '#D3D6D8', { roughness: .95, tex: 1 }), bank: mat('concrete_bank', '#C2C8CC', { tex: 1 }),
    terracotta: mat('terracotta', '#C89778', { tex: 1 }), wash: mat('terracotta_wash', '#D6AB8C', { tex: 1, opacity: .75 }), red: mat('red_concrete', '#BE665A', { tex: 1 }), pink: mat('pink_concrete', '#D4978F', { tex: 1 }), teal: mat('teal_concrete', '#2F6360', { tex: 1 }), capTeal: mat('cap_teal', '#1F4744', { roughness: .6 }), capPink: mat('cap_rose', '#8F5A55', { roughness: .6 }), capGray: mat('cap_gray', '#3A4347', { roughness: .6 }),
    blue: mat('pool_blue', '#7DB8E0', { roughness: .55 }), kicker: mat('kicker_blue', '#A9CFEA', { roughness: .6 }), yellow: mat('bun_yellow', '#E0BD34', { roughness: .32, metalness: .05 }), bun: mat('coney_cheddar', '#E9C440', { roughness: .26, metalness: .06 }), coneyBase: mat('coney_chili', '#9C5B2C', { roughness: .26, metalness: .1 }), coneyEdge: mat('coney_seam', '#B9752D', { roughness: .3 }), sausageEnd: mat('sausage', '#8E3A22', { roughness: .38, metalness: .05 }), bunTan: mat('bun', '#C8935A', { roughness: .62 }), sausage: mat('coney_brown', '#7A3E1C', { roughness: .3, metalness: .08 }), steelEdge: mat('steel_edge', '#2B2E31', { roughness: .45, metalness: .5 }),
    wood: mat('wood', '#A8754A'), steel: mat('steel', '#4A5057', { roughness: .45, metalness: .3 }), coping: mat('coping', '#6E3F31', { roughness: .5, metalness: .15 }),
    leaf: mat('foliage', '#5E7D45'), leafLight: mat('foliage_light', '#7E9C57'), trunk: mat('trunk', '#5A4634'), stone: mat('stone', '#B9B2A5', { tex: 1 }), cap: mat('bench_cap', '#CBC5B9', { tex: 1 }),
    building: mat('building', '#BCC0C4'), asphalt: mat('asphalt', '#6B6F73'),
    muralWall: (() => { const m = new THREE.MeshStandardMaterial({ map: muralTex, roughness: .6, metalness: 0 }); m.name = 'mural_wall'; return m; })()
  };
  const stampTex = (() => { const c = document.createElement('canvas'); c.width = 1024; c.height = 128; const g = c.getContext('2d');
    g.fillStyle = '#6A3D31'; g.fillRect(0, 0, 1024, 128); g.font = 'bold 92px Georgia, "Times New Roman", serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
    const R = Math.PI / 180, drawC = (cx, cy) => { const P = (r, a) => [cx + r * Math.cos(a * R), cy + r * Math.sin(a * R)]; g.beginPath();   // wishbone C: arch comes to a single point on the back, plain radial-cut tips
      g.arc(cx, cy, 48, 50 * R, 140 * R, false); g.lineTo(cx - 58, cy); g.lineTo(...P(48, 220)); g.arc(cx, cy, 48, 220 * R, 310 * R, false);
      g.lineTo(...P(26, 310)); g.arc(cx, cy, 26, 310 * R, 220 * R, true); g.lineTo(cx - 33, cy); g.lineTo(...P(26, 140)); g.arc(cx, cy, 26, 140 * R, 50 * R, true); g.closePath();
      g.fillStyle = '#563026'; g.fill(); g.lineWidth = 2.5; g.strokeStyle = 'rgba(255,255,255,.2)'; g.stroke(); };
    for (let i = 0; i < 8; i++) drawC(i * 128 + 64, 64);
    g.fillStyle = 'rgba(255,255,255,.09)'; g.fillRect(0, 0, 1024, 6); g.fillRect(0, 122, 1024, 6);
    const t = new THREE.CanvasTexture(c); t.wrapS = THREE.RepeatWrapping; t.wrapT = THREE.ClampToEdgeWrapping; t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; return t; })();
  const stampMat = new THREE.MeshStandardMaterial({ map: stampTex, roughness: .85, metalness: 0, side: THREE.DoubleSide }); stampMat.name = 'c_stamp_band';
  // flat stamped band riding the deck around a closed rim loop; C tiles are square (band width) and repeat a whole number of times
  function stampBand(loopPts, innerPts, top, depth, width, name) {
    const N = loopPts.length; let area = 0, total = 0; const s = [0];
    for (let i = 0; i < N; i++) { const a = loopPts[i], c = loopPts[(i + 1) % N]; area += a.x * c.z - c.x * a.z; total += a.distanceTo(c); s.push(total); }
    const sgn = area > 0 ? 1 : -1, reps = Math.max(1, Math.round(total / (width * 8))), pos = [], uv = [], idx = [];
    const K = 6, t1 = Math.asin(Math.min(1, width / depth)) / (Math.PI / 2);   // arc parameter where the bank has dropped `width`
    for (let i = 0; i <= N; i++) { const o = loopPts[i % N], n = innerPts[i % N], u = s[i] / total * reps;
      for (let k = 0; k <= K; k++) { const t = .01 + (t1 - .01) * k / K, sd = 1 - Math.cos(t * Math.PI / 2), h = top - depth * Math.sin(t * Math.PI / 2) + .012;
        pos.push(o.x * (1 - sd) + n.x * sd, h, o.z * (1 - sd) + n.z * sd); uv.push(u, k / K); } }
    for (let i = 0; i < N; i++) for (let k = 0; k < K; k++) { const a = i * (K + 1) + k, c = a + K + 1; idx.push(a, c, a + 1, a + 1, c, c + 1); }
    return bufferMesh(pos, idx, uv, stampMat, name);
  }
  const mosaicMats = {};
  const group = new THREE.Group(); group.name = 'camp_washington_skatepark';
  const features = {}; let cur = group;
  // the park is modelled in its own plan frame, then turned and set into the site (PLAN.parkTf); the site context never moves
  const parkRoot = new THREE.Group(); parkRoot.name = 'park'; group.add(parkRoot);
  if (PLAN.parkTf) { const { theta, c, t } = PLAN.parkTf, a = theta * Math.PI / 180, cw = new THREE.Vector3(X(c[0]), 0, Z(c[1])), r = cw.clone().applyAxisAngle(new THREE.Vector3(0, 1, 0), -a);
    parkRoot.rotation.y = -a; parkRoot.position.set(cw.x - r.x + t[0] * S, 0, cw.z - r.z + t[1] * S); }
  parkRoot.updateMatrixWorld(true);
  const toSite = (x, z) => { const v = new THREE.Vector3(x, 0, z).applyMatrix4(parkRoot.matrixWorld); return new THREE.Vector2(v.x, v.z); };   // park-local XZ -> site XZ
  const feat = (id, n, label, layer) => { const g = new THREE.Group(); g.name = id; g.userData = { n, label, layer }; (id === 'site' ? group : parkRoot).add(g); features[id] = g; cur = g; return g; };
  const add = (geo, m, name, pos, parent) => { const mesh = new THREE.Mesh(geo, m); mesh.name = name; if (pos) mesh.position.set(...pos); (parent || cur).add(mesh); return mesh; };

  // --- geometry helpers -----------------------------------------------------
  function pathInto(d, target) {
    const t = d.match(/[MLCZ]|-?\d*\.?\d+(?:e-?\d+)?/gi); let i = 0, cmd = '';
    const n = () => parseFloat(t[i++]);
    while (i < t.length) {
      if (/^[MLCZ]$/i.test(t[i])) { cmd = t[i++].toUpperCase(); if (cmd === 'Z') { target.closePath(); continue; } }
      if (cmd === 'M') { const x = n(), y = n(); target.moveTo(X(x), Z(y)); cmd = 'L'; }
      else if (cmd === 'L') { const x = n(), y = n(); target.lineTo(X(x), Z(y)); }
      else if (cmd === 'C') { const a = n(), b = n(), c = n(), d2 = n(), e = n(), f = n(); target.bezierCurveTo(X(a), Z(b), X(c), Z(d2), X(e), Z(f)); }
      else i++;
    }
    return target;
  }
  const shapeFromPath = d => pathInto(d, new THREE.Shape());
  const holeFromPath = d => pathInto(d, new THREE.Path());
  const v3 = ([x, y], h = 0) => new THREE.Vector3(X(x), h, Z(y));
  const loop = (pts, n, closed = true) => new THREE.CatmullRomCurve3(pts.map(p => v3(p)), closed, 'catmullrom', .5).getSpacedPoints(n);
  const shapeFromV3 = (pts, target = new THREE.Shape()) => { target.moveTo(pts[0].x, pts[0].z); for (let i = 1; i < pts.length; i++) target.lineTo(pts[i].x, pts[i].z); target.closePath(); return target; };
  const shapeFromPx = pts => { const sh = new THREE.Shape(); sh.moveTo(X(pts[0][0]), Z(pts[0][1])); pts.slice(1).forEach(p => sh.lineTo(X(p[0]), Z(p[1]))); sh.closePath(); return sh; };
  function extrude(shape, depth, m, name, yBottom = 0, parent) {
    const g = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: false, curveSegments: 48 });
    g.rotateX(Math.PI / 2); g.translate(0, yBottom + depth, 0); return add(g, m, name, null, parent);
  }
  function flat(shape, m, name, y) { const g = new THREE.ShapeGeometry(shape, 24); g.rotateX(Math.PI / 2); g.translate(0, y, 0); return add(g, m, name); }
  function bufferMesh(pos, idx, uv, m, name, pos3) { const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); if (uv) g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); if (idx) g.setIndex(idx); g.computeVertexNormals(); return add(g, m, name, pos3); }
  // quarter-round transition between two aligned point strips (closed loop or open run, with capped ends)
  function bankSurface(outer, inner, top, depth, m, name, closed = true) {
    const N = outer.length, K = 28, pos = [], uv = [], idx = [], cols = closed ? N + 1 : N; let run = 0;
    for (let i = 0; i < cols; i++) { const o = outer[i % N], n = inner[i % N]; if (i) run += o.distanceTo(outer[(i - 1) % N]);
      for (let k = 0; k <= K; k++) { const t = k / K, s = 1 - Math.cos(t * Math.PI / 2), h = top - depth * Math.sin(t * Math.PI / 2);
        pos.push(o.x * (1 - s) + n.x * s, h, o.z * (1 - s) + n.z * s); uv.push(run, t * depth * 1.57); } }
    for (let i = 0; i < cols - 1; i++) for (let k = 0; k < K; k++) { const a = i * (K + 1) + k, b = a + 1, c = a + K + 1, d = c + 1; idx.push(a, c, b, b, c, d); }
    if (!closed) [0, N - 1].forEach(i => { const base = i * (K + 1), apex = base + K; for (let k = 0; k < K - 1; k++) idx.push(apex, base + k, base + k + 1); });
    return bufferMesh(pos, idx, uv, m, name);
  }
  function alignByAngle(outer, inner) {
    const c = outer.reduce((a, p) => a.add(p), new THREE.Vector3()).multiplyScalar(1 / outer.length);
    const ang = p => Math.atan2(p.z - c.z, p.x - c.x);
    return outer.map(o => { const a = ang(o); let best = inner[0], bd = 9; for (const p of inner) { let d = Math.abs(ang(p) - a); d = Math.min(d, Math.PI * 2 - d); if (d < bd) { bd = d; best = p; } } return best; });
  }
  // rounded lip half-buried in the deck — reads as a bullnose rim / pool coping, never a floating rail
  function lip(ptsV3, y, r, m, name, closed = false) { const c = new THREE.CatmullRomCurve3(ptsV3.map(p => new THREE.Vector3(p.x, y, p.z)), closed, 'catmullrom', .5); return add(new THREE.TubeGeometry(c, closed ? 220 : 72, r, 12, closed), m, name); }
  function bar(x1, y1, x2, y2, y, r, m, name) {
    const dx = X(x2) - X(x1), dz = Z(y2) - Z(y1), L = Math.hypot(dx, dz);
    const g = new THREE.CylinderGeometry(r, r, L, 12); g.rotateZ(Math.PI / 2);
    const mesh = add(g, m, name, [(X(x1) + X(x2)) / 2, y, (Z(y1) + Z(y2)) / 2]); mesh.rotation.y = -Math.atan2(dz, dx); return mesh;
  }
  const box = (w, h, d, m, name, pos, rotY = 0, parent) => { const mesh = add(new THREE.BoxGeometry(w, h, d), m, name, pos, parent); mesh.rotation.y = rotY; return mesh; };

  // --- site: turf, infield, context, trees ------------------------------------
  feat('site', 0, 'Site — turf, infield, trees, rec building', 0);
  const ground = new THREE.Shape(); ground.moveTo(X(-2500), Z(-2600)); ground.lineTo(X(3200), Z(-2600)); ground.lineTo(X(3200), Z(3300)); ground.lineTo(X(-2500), Z(3300)); ground.closePath();
  ground.holes.push(new THREE.Path(shapeFromPath(PLAN.slab).getPoints(16).map(v => toSite(v.x, v.y)))); if (PLAN.site && PLAN.site.pool) ground.holes.push(shapeFromPx(PLAN.site.pool)); if (PLAN.site && PLAN.site.kiddie) ground.holes.push(shapeFromPx(PLAN.site.kiddie));
  flat(ground, M.grass, 'ground', 0);
  if (PLAN.sand) { // infield/seeded soil with a feathered alpha edge — no hard corners where the trace met the photo frame
    const sh = shapeFromPath(PLAN.sand), pts = sh.getPoints(48), bb = new THREE.Box2().setFromPoints(pts), pad = 4, W = bb.max.x - bb.min.x + pad * 2, H = bb.max.y - bb.min.y + pad * 2, R = 512 / Math.max(W, H);
    const cv = document.createElement('canvas'); cv.width = Math.ceil(W * R); cv.height = Math.ceil(H * R); const g = cv.getContext('2d'), OFF = 8192;
    g.fillStyle = '#000'; g.fillRect(0, 0, cv.width, cv.height); g.shadowColor = '#fff'; g.shadowBlur = 2.6 * R; g.shadowOffsetX = OFF; g.fillStyle = '#fff'; g.beginPath();
    pts.forEach((p, i) => { const x = (p.x - bb.min.x + pad) * R - OFF, y = (p.y - bb.min.y + pad) * R; i ? g.lineTo(x, y) : g.moveTo(x, y); }); g.closePath(); g.fill();
    const am = new THREE.CanvasTexture(cv); am.channel = 1; const dm = M.dirt.clone(); dm.alphaMap = am; dm.transparent = true; dm.depthWrite = false; dm.name = 'dirt_infield';
    const x0 = bb.min.x - pad, x1 = bb.max.x + pad, z0 = bb.min.y - pad, z1 = bb.max.y + pad, y = .006, geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute([x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1], 3)); geo.setAttribute('uv', new THREE.Float32BufferAttribute([x0, z0, x1, z0, x1, z1, x0, z1], 2));
    geo.setAttribute('uv1', new THREE.Float32BufferAttribute([0, 1, 1, 1, 1, 0, 0, 0], 2)); geo.setIndex([0, 2, 1, 0, 3, 2]); geo.computeVertexNormals(); add(geo, dm, 'dirt_infield'); }
  // periphery (muted context): Stock Ave, entry walk, basketball court, youth services, rec center + pool, playground, paving, trees — from the CRC CAD base + aerial
  { const st = PLAN.site, P = (a, t, m, name, y0 = 0) => extrude(shapeFromPx(a), t, m, name, y0);
    const ribbon = (pts, w, m, name, y0, t = .004) => { const Lp = [], Rp = []; pts.forEach((p, i) => { const a = pts[Math.max(0, i - 1)], c = pts[Math.min(pts.length - 1, i + 1)], dx = c[0] - a[0], dy = c[1] - a[1], l = Math.hypot(dx, dy) || 1, nx = -dy / l * w / 2, ny = dx / l * w / 2; Lp.push([p[0] + nx, p[1] + ny]); Rp.push([p[0] - nx, p[1] - ny]); }); return P([...Lp, ...Rp.reverse()], t, m, name, y0); };
    const closed = a => [...a, a[0]], mm = (n, c, o) => mat(n, c, o), walk = mm('ctx_sidewalk', '#E2E1DB'), pave = mm('ctx_paving', '#DEDDD7'), line = mm('ctx_line', '#F4F4F0'), bldg = mm('ctx_building', '#D6D2CA'), bldg2 = mm('ctx_building_2', '#CCC7BF');
    P(st.asphalt, .02, mm('ctx_stock_ave', '#B4B6B8'), 'stock_ave', .004); ribbon(st.centre, 3, mm('ctx_centre_line', '#E2DAB0'), 'stock_ave_centre_line', .024); P(st.sidewalk, .12, M.concrete, 'stock_ave_sidewalk'); if (st.sidewalkN) P(st.sidewalkN, .12, M.concrete, 'stock_ave_sidewalk_n');
    if (st.entry) P(st.entry, .118, M.concrete, 'entry_walk');
    if (st.streetFence) { const FH = 1.2, fm = new THREE.MeshStandardMaterial({ color: '#3A3F44', transparent: true, opacity: .22, roughness: .9, side: THREE.DoubleSide, depthWrite: false }); fm.name = 'ctx_street_fence';
      st.streetFence.forEach(([a, c], si) => { const p = new THREE.Vector2(X(a[0]), Z(a[1])), q = new THREE.Vector2(X(c[0]), Z(c[1])), pos = [p.x, 0, p.y, q.x, 0, q.y, q.x, FH, q.y, p.x, FH, p.y];
        bufferMesh(pos, [0, 1, 2, 0, 2, 3], null, fm, 'street_fence_' + (si + 1)); const n = Math.max(1, Math.round(p.distanceTo(q) / 2.4));
        for (let k = 0; k <= n; k++) { const r = p.clone().lerp(q, k / n); add(new THREE.CylinderGeometry(.03, .03, FH, 6), M.steel, 'street_fence_post', [r.x, FH / 2, r.y]); }
        add(new THREE.TubeGeometry(new THREE.LineCurve3(new THREE.Vector3(p.x, FH, p.y), new THREE.Vector3(q.x, FH, q.y)), 1, .02, 6, false), M.steel, 'street_fence_rail_' + (si + 1)); }); } if (st.bikePad) P(st.bikePad, .118, M.concrete, 'bike_pad');   // same pour as the sidewalk, a hair lower so the joint reads as one slab
    [st.walkNS, st.forecourt, st.poolYard, st.driveway, st.southLot].forEach((a, i) => { if (!a) return; const sh = shapeFromPx(a); if (a === st.poolYard) sh.holes.push(shapeFromPx(st.pool)); if (a === st.forecourt && st.kiddie) sh.holes.push(shapeFromPx(st.kiddie)); extrude(sh, .06, pave, 'paving_' + (i + 1), 0); }); ribbon(st.path, 34, pave, 'playground_path', 0, .06);
    P(st.courtOuter, .08, pave, 'court_apron'); P(st.courtInner, .01, mm('ctx_court', '#BFD2C7', { roughness: .8 }), 'basketball_court', .08);
    st.keys.forEach((k, i) => { P(k, .005, mm('ctx_key', '#B4BCD6'), 'court_key_' + (i + 1), .09); ribbon(closed(k), 2, line, 'key_line_' + (i + 1), .096); });
    ribbon(closed(st.courtInner), 2, line, 'court_boundary', .096); ribbon(st.centreLine, 2, line, 'court_centre_line', .096); ribbon(st.circle, 2, line, 'centre_circle', .096); st.arcs.forEach((a, i) => ribbon(a, 2, line, 'three_point_arc_' + (i + 1), .096));
    // municipal pool: stone coping, sunken plaster basin sloping from the shallow end to the deep end (ends swapped per the as-built), floor lane stripes with T-ends, ladders, entry steps, deep water, lane ropes, lifeguard chair, fenced yard
    { const pq = [2, 3, 0, 1].map(i => st.pool[i]).map(([x, y]) => new THREE.Vector2(X(x), Z(y))), bil = (s, t) => pq[0].clone().lerp(pq[1], s).lerp(pq[3].clone().lerp(pq[2], s), t);
      const DECK = .06, WL = DECK - .12, ss = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }, floorY = s => DECK - 1.05 - 1.35 * ss((s - .45) / .3), NS = 48;
      const Lp = bil(0, .5).distanceTo(bil(1, .5)), Wp = bil(.5, 0).distanceTo(bil(.5, 1));
      const plaster = mm('ctx_pool_plaster', '#CFE8EF', { roughness: .6 }), stripe = mm('ctx_pool_stripe', '#1F4C78', { roughness: .6 }), cope = mm('ctx_pool_coping', '#EEEBE3', { roughness: .85 });
      const strip = (s0, s1, t0, t1, m, name, lift = .012, n = Math.max(1, Math.ceil((s1 - s0) * NS))) => { const pos = [], idx = [];
        for (let i = 0; i <= n; i++) { const s = s0 + (s1 - s0) * i / n, a = bil(s, t0), c = bil(s, t1), y = floorY(s) + lift; pos.push(a.x, y, a.y, c.x, y, c.y); }
        for (let i = 0; i < n; i++) { const a = i * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); } return bufferMesh(pos, idx, null, m, name); };
      strip(0, 1, 0, 1, plaster, 'pool_floor', 0, NS);
      const wall = (pt, sOf, n, name) => { const pos = [], idx = []; for (let i = 0; i <= n; i++) { const r = i / n, p = pt(r); pos.push(p.x, DECK, p.y, p.x, floorY(sOf(r)), p.y); }
        for (let i = 0; i < n; i++) { const a = i * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); } bufferMesh(pos, idx, null, plaster, name); };
      wall(r => bil(r, 0), r => r, NS, 'pool_wall_n'); wall(r => bil(r, 1), r => r, NS, 'pool_wall_s'); wall(r => bil(0, r), () => 0, 1, 'pool_wall_w'); wall(r => bil(1, r), () => 1, 1, 'pool_wall_e');
      for (let k = 0; k < 7; k++) { const t = (k + .5) / 7, hw = .125 / Wp, tb = .5 / Wp, e0 = 2 / Lp, e1 = 1 - 2 / Lp, hs = .125 / Lp;
        strip(e0, e1, t - hw, t + hw, stripe, 'pool_lane_stripe_' + (k + 1)); strip(e0 - hs, e0 + hs, t - tb, t + tb, stripe, 'pool_lane_t_' + (k + 1) + 'a', .014, 1); strip(e1 - hs, e1 + hs, t - tb, t + tb, stripe, 'pool_lane_t_' + (k + 1) + 'b', .014, 1); }
      [[.4, .3], [.8, .55], [1.2, .8]].forEach(([reach, drop], k) => { const sE = reach / Lp, q = [bil(0, .6), bil(sE, .6), bil(sE, .95), bil(0, .95)], sh = new THREE.Shape(); sh.moveTo(q[0].x, q[0].y); q.slice(1).forEach(p => sh.lineTo(p.x, p.y)); sh.closePath();
        const bot = floorY(0); extrude(sh, DECK - drop - bot, plaster, 'pool_entry_step_' + (k + 1), bot); });
      const cen = pq.reduce((a, p) => a.add(p), new THREE.Vector2()).multiplyScalar(.25);
      for (let i = 0; i < 4; i++) { const a = pq[i], c = pq[(i + 1) % 4], d = c.clone().sub(a), mid = a.clone().add(c).multiplyScalar(.5), n = new THREE.Vector2(d.y, -d.x).normalize(); if (n.dot(mid.clone().sub(cen)) < 0) n.negate();
        box(d.length() + .7, .07, .35, cope, 'pool_coping_' + (i + 1), [mid.x + n.x * .175, DECK + .035, mid.y + n.y * .175], -Math.atan2(d.y, d.x)); }
      [[.9, 0], [.9, 1]].forEach(([s, t], li) => { const wp = bil(s, t), inn = bil(s, .5).sub(wp).normalize(), al = bil(s + .02, t).sub(bil(s - .02, t)).normalize();
        [-.26, .26].forEach((o, ri) => { const base = wp.clone().addScaledVector(al, o), P3 = (d, y) => new THREE.Vector3(base.x + inn.x * d, y, base.y + inn.y * d);
          const c = new THREE.CatmullRomCurve3([P3(-.5, DECK), P3(-.5, DECK + .6), P3(-.32, DECK + .88), P3(-.05, DECK + .9), P3(.14, DECK + .66), P3(.17, DECK + .2), P3(.17, WL - 1)], false, 'catmullrom', .5);
          add(new THREE.TubeGeometry(c, 48, .022, 8, false), M.steel, 'pool_ladder_' + (li + 1) + '_rail_' + (ri + 1)); });
        [WL - .3, WL - .65].forEach((y, k) => { const p = wp.clone().addScaledVector(inn, .11); box(.52, .03, .08, M.steel, 'pool_ladder_' + (li + 1) + '_tread_' + (k + 1), [p.x, y, p.y], -Math.atan2(al.y, al.x)); }); });
      const water = new THREE.MeshStandardMaterial({ color: '#FFFFFF', vertexColors: true, transparent: true, opacity: .8, roughness: .04, metalness: .12, depthWrite: false, side: THREE.DoubleSide }); water.name = 'ctx_pool_water';
      const wsh = new THREE.Shape(); wsh.moveTo(pq[0].x, pq[0].y); [1, 2, 3].forEach(i => wsh.lineTo(pq[i].x, pq[i].y)); wsh.closePath();
      const wm = flat(wsh, water, 'pool_water', WL), wpa = wm.geometry.attributes.position, a0 = bil(0, .5), axv = bil(1, .5).sub(a0).normalize(), cS = new THREE.Color('#5FB8DE'), cD = new THREE.Color('#2A78AE'), cc = new THREE.Color(), wc = [];
      for (let k = 0; k < wpa.count; k++) { const s = Math.max(0, Math.min(1, ((wpa.getX(k) - a0.x) * axv.x + (wpa.getZ(k) - a0.y) * axv.y) / Lp)); cc.copy(cS).lerp(cD, s); wc.push(cc.r, cc.g, cc.b); }
      wm.geometry.setAttribute('color', new THREE.Float32BufferAttribute(wc, 3));
      const ropeTex = (() => { const cv = document.createElement('canvas'); cv.width = 64; cv.height = 4; const g = cv.getContext('2d'); ['#F4F4F2', '#C8323A', '#F4F4F2', '#2F5FA8'].forEach((c, i) => { g.fillStyle = c; g.fillRect(i * 16, 0, 16, 4); }); const t = new THREE.CanvasTexture(cv); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(Lp / 2, 1); t.colorSpace = THREE.SRGBColorSpace; return t; })();
      const ropeM = new THREE.MeshStandardMaterial({ map: ropeTex, roughness: .5 }); ropeM.name = 'ctx_lane_rope';
      for (let k = 1; k < 7; k++) { const t = k / 7, p0 = bil(0, t), p1 = bil(1, t); add(new THREE.TubeGeometry(new THREE.LineCurve3(new THREE.Vector3(p0.x, WL + .03, p0.y), new THREE.Vector3(p1.x, WL + .03, p1.y)), 1, .05, 8, false), ropeM, 'pool_lane_rope_' + k); }
      { const ed = bil(.5, 0), o = ed.clone().sub(bil(.5, .5)).normalize(), base = ed.clone().addScaledVector(o, 1.3), gm = mm('ctx_guard_chair', '#F2F2EE'), g = new THREE.Group(); g.name = 'lifeguard_chair'; g.position.set(base.x, DECK, base.y); g.rotation.y = -Math.atan2(o.y, o.x); cur.add(g);
        [[-.35, -.35], [.35, -.35], [-.35, .35], [.35, .35]].forEach(([x, z], i) => add(new THREE.BoxGeometry(.06, 1.9, .06), gm, 'guard_chair_leg_' + (i + 1), [x, .95, z], g));
        add(new THREE.BoxGeometry(.8, .06, .7), gm, 'guard_chair_seat', [0, 1.9, 0], g); add(new THREE.BoxGeometry(.06, .7, .7), gm, 'guard_chair_back', [.4, 2.25, 0], g); }
      if (st.poolFence !== false) { const yard = st.poolYard.map(([x, y]) => new THREE.Vector2(X(x), Z(y))), FH = 1.8, fm = new THREE.MeshStandardMaterial({ color: '#3A3F44', transparent: true, opacity: .22, roughness: .9, side: THREE.DoubleSide, depthWrite: false }); fm.name = 'ctx_pool_fence';
        const pos = [], idx = []; yard.forEach((p, i) => { const q = yard[(i + 1) % yard.length], b0 = pos.length / 3; pos.push(p.x, 0, p.y, q.x, 0, q.y, q.x, FH, q.y, p.x, FH, p.y); idx.push(b0, b0 + 1, b0 + 2, b0, b0 + 2, b0 + 3);
          const n = Math.max(1, Math.round(p.distanceTo(q) / 3)); for (let k = 0; k < n; k++) { const r = p.clone().lerp(q, k / n); add(new THREE.CylinderGeometry(.03, .03, FH, 6), M.steel, 'pool_fence_post', [r.x, FH / 2, r.y]); } });
        bufferMesh(pos, idx, null, fm, 'pool_fence'); } }
    P(st.playground, .05, mm('ctx_play_surface', '#D8C4B0'), 'playground_surface'); P(st.playStructure, 2.2, mm('ctx_play_structure', '#BFC7D8'), 'play_structure', .05);
    P(st.ysN, 7, bldg, 'youth_services_n'); P(st.ysS, 6.5, bldg2, 'youth_services_s');  P(st.shed, 2.4, bldg, 'shed'); if (st.poolHouse) P(st.poolHouse, 3.6, bldg2, 'pump_house'); // kiddie (wading) pool on the forecourt north of the pool yard: stone coping, 12"–18" plaster basin, entry step, two mushroom sprays
    { const pq = st.kiddie.map(([x, y]) => new THREE.Vector2(X(x), Z(y))), bil = (s, t) => pq[0].clone().lerp(pq[1], s).lerp(pq[3].clone().lerp(pq[2], s), t);
      const DECK = .06, WL = DECK - .08, ss = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }, floorY = t => DECK - .3 - .15 * ss((t - .3) / .5), NS = 24, Lk = bil(.5, 0).distanceTo(bil(.5, 1)), Wk = bil(0, .5).distanceTo(bil(1, .5));
      const plaster = mm('ctx_kiddie_plaster', '#D8F0F6', { roughness: .6 }), cope = mm('ctx_pool_coping', '#EEEBE3', { roughness: .85 });
      { const pos = [], idx = []; for (let i = 0; i <= NS; i++) { const t = i / NS, p0 = bil(0, t), p1 = bil(1, t), y = floorY(t); pos.push(p0.x, y, p0.y, p1.x, y, p1.y); }
        for (let i = 0; i < NS; i++) { const k = i * 2; idx.push(k, k + 2, k + 1, k + 1, k + 2, k + 3); } bufferMesh(pos, idx, null, plaster, 'kiddie_pool_floor'); }
      const wall = (pt, tOf, n, name) => { const pos = [], idx = []; for (let i = 0; i <= n; i++) { const r = i / n, p = pt(r); pos.push(p.x, DECK, p.y, p.x, floorY(tOf(r)), p.y); }
        for (let i = 0; i < n; i++) { const k = i * 2; idx.push(k, k + 2, k + 1, k + 1, k + 2, k + 3); } bufferMesh(pos, idx, null, plaster, name); };
      wall(r => bil(0, r), r => r, NS, 'kiddie_wall_w'); wall(r => bil(1, r), r => r, NS, 'kiddie_wall_e'); wall(r => bil(r, 0), () => 0, 1, 'kiddie_wall_n'); wall(r => bil(r, 1), () => 1, 1, 'kiddie_wall_s');
      { const tE = .45 / Lk, q = [bil(.03, 0), bil(.97, 0), bil(.97, tE), bil(.03, tE)], sh = new THREE.Shape(); sh.moveTo(q[0].x, q[0].y); q.slice(1).forEach(p => sh.lineTo(p.x, p.y)); sh.closePath(); const bot = floorY(0); extrude(sh, DECK - .15 - bot, plaster, 'kiddie_entry_step', bot); }
      const cen = pq.reduce((acc, p) => acc.add(p), new THREE.Vector2()).multiplyScalar(.25);
      for (let i = 0; i < 4; i++) { const p0 = pq[i], p1 = pq[(i + 1) % 4], d = p1.clone().sub(p0), mid = p0.clone().add(p1).multiplyScalar(.5), n = new THREE.Vector2(d.y, -d.x).normalize(); if (n.dot(mid.clone().sub(cen)) < 0) n.negate();
        box(d.length() + .6, .07, .3, cope, 'kiddie_coping_' + (i + 1), [mid.x + n.x * .15, DECK + .035, mid.y + n.y * .15], -Math.atan2(d.y, d.x)); }
      [[.5, .4, '#F2C230'], [.5, .78, '#E0532F']].forEach(([u, t, col], k) => { const c = bil(u, t), yb = floorY(t); add(new THREE.CylinderGeometry(.06, .08, 1.4, 12), mm('ctx_fountain_blue', '#3F86C6', { roughness: .5 }), 'spray_mushroom_stem_' + (k + 1), [c.x, yb + .7, c.y]);
        const cap = new THREE.SphereGeometry(.75, 24, 10, 0, Math.PI * 2, 0, Math.PI / 2); cap.scale(1, .45, 1); add(cap, mm('ctx_fountain_cap_' + (k + 1), col, { roughness: .5 }), 'spray_mushroom_cap_' + (k + 1), [c.x, yb + 1.4, c.y]); });
      const kw = new THREE.MeshStandardMaterial({ color: '#9FDCEE', transparent: true, opacity: .5, roughness: .04, metalness: .08, depthWrite: false, side: THREE.DoubleSide }); kw.name = 'ctx_kiddie_water';
      const wsh = new THREE.Shape(); wsh.moveTo(pq[0].x, pq[0].y); [1, 2, 3].forEach(i => wsh.lineTo(pq[i].x, pq[i].y)); wsh.closePath(); flat(wsh, kw, 'kiddie_pool_water', WL); } if (st.southBldg) P(st.southBldg, 6, bldg, 'south_building');
    const leafC = mm('ctx_foliage', '#8FA27A', { roughness: .95 }); leafC.flatShading = true;
    st.trees.forEach(([x, y, r], i) => { const R = r * S; add(new THREE.CylinderGeometry(.1, .16, 2.4, 8), M.trunk, 'site_tree_trunk_' + (i + 1), [X(x), 1.2, Z(y)]); const g = new THREE.IcosahedronGeometry(R, 2); g.scale(1, .8, 1); add(g, leafC, 'site_tree_canopy_' + (i + 1), [X(x), 2.4 + R * .7, Z(y)]); });
    // wider context traced from the map: parking lots and stall rows, neighbouring buildings and houses, the highway ramp and its slope
    const lotM = mm('ctx_flat_asphalt', '#B9BBBD', { roughness: .95 }), stripeM = mm('ctx_flat_stripe', '#E8E9EA', { roughness: .8 }), slopeM = mm('ctx_flat_slope', '#6F8650', { roughness: .95 }), houseM = mm('ctx_house', '#D3CEC6'), rampLine = mm('ctx_flat_ramp_line', '#E2DAB0');
    (st.extra || []).forEach((e, i) => { const nm = (e.name || e.k) + '_' + (i + 1);
      if (e.k === 'asphalt') P(e.pts, .02, lotM, 'ctx_flat_' + nm, .002);
      else if (e.k === 'stripe') ribbon(e.pts, 6, stripeM, 'ctx_flat_' + nm, .024);
      else if (e.k === 'slope') { const g = new THREE.ShapeGeometry(shapeFromPx(e.pts), 12); g.rotateX(Math.PI / 2); g.translate(0, .004, 0); add(g, slopeM, 'ctx_flat_' + nm); }
      else if (e.k === 'road') { ribbon(e.pts, e.w || 128, lotM, 'ctx_flat_' + nm, .003, .02); ribbon(e.pts, 3, rampLine, 'ctx_flat_line_' + nm, .026); }
      else if (e.k === 'bldg') P(e.pts, e.h || 7, bldg, 'ctx_' + nm);
      else if (e.k === 'house') P(e.pts, e.h || 6.5, houseM, 'ctx_' + nm); }); }

  // trees: four species mixed through the site, each canopy a noise-displaced mass with vertex-colour shading (sunlit crown, shaded underside)
  { const hash = (x, y, z) => { const s = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453; return s - Math.floor(s); };
    const noise = (x, y, z) => { const fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z), ux = x - fx, uy = y - fy, uz = z - fz, sx = ux * ux * (3 - 2 * ux), sy = uy * uy * (3 - 2 * uy), sz = uz * uz * (3 - 2 * uz); let v = 0;
      for (let a = 0; a < 2; a++) for (let c = 0; c < 2; c++) for (let d = 0; d < 2; d++) v += hash(fx + a, fy + c, fz + d) * (a ? sx : 1 - sx) * (c ? sy : 1 - sy) * (d ? sz : 1 - sz); return v; };
    const leafM = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: .95, flatShading: true }); leafM.name = 'foliage';
    const PAL = [['#4F6E38', '#86A55A'], ['#5B7A3C', '#9DB463'], ['#3F5E36', '#6F9150'], ['#6A7F3E', '#A9B867'], ['#2F4A30', '#557A46']];
    const canopy = (R, sy, seed, pal, rough = .28) => { const g = new THREE.IcosahedronGeometry(R, 4), p = g.attributes.position, col = [], lo = new THREE.Color(pal[0]), hi = new THREE.Color(pal[1]), c = new THREE.Color(), v = new THREE.Vector3();
      for (let k = 0; k < p.count; k++) { v.fromBufferAttribute(p, k).normalize(); const n = noise(v.x * 2.2 + seed, v.y * 2.2, v.z * 2.2) * .7 + noise(v.x * 6 + seed, v.y * 6, v.z * 6) * .3, d = 1 + (n - .5) * 2 * rough;
        p.setXYZ(k, v.x * R * d, v.y * R * d * sy, v.z * R * d); c.copy(lo).lerp(hi, Math.min(1, Math.max(0, (v.y * .5 + .5) * .75 + (n - .5) * .9 + .1))); col.push(c.r, c.g, c.b); }
      g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3)); g.computeVertexNormals(); return g; };
    PLAN.trees.forEach(([cx, cy, r], i) => { const R = r * S, sp = Math.floor(hash(i, 3.1, 7.7) * 4), pal = PAL[Math.floor(hash(i, 1.7, 2.3) * PAL.length)], seed = i * 3.7, t = new THREE.Group();
      t.name = 'tree_' + (i + 1); t.position.set(X(cx), 0, Z(cy)); t.rotation.y = (i * 137.508 % 360) * Math.PI / 180; t.scale.setScalar(.85 + hash(i, 9.1, 4.4) * .3); cur.add(t);
      const trunkH = [2.2, 2.6, 1.8, 1.2][sp]; add(new THREE.CylinderGeometry(.08, .14, trunkH, 8), M.trunk, 'trunk', [0, trunkH / 2, 0], t);
      if (sp === 0) {   // round shade tree: main crown + two offset lobes
        add(canopy(R, .85, seed, pal), leafM, 'canopy', [0, trunkH + R * .7, 0], t);
        [[-.45, .15, .3], [.45, .05, -.3]].forEach(([ox, oy, oz], j) => add(canopy(R * .62, .9, seed + j + 1, pal), leafM, 'canopy_lobe_' + (j + 1), [ox * R, trunkH + R * (.7 + oy), oz * R], t)); }
      else if (sp === 1) add(canopy(R * .6, 2.1, seed, pal, .22), leafM, 'canopy_columnar', [0, trunkH + R * 1.1, 0], t);   // columnar
      else if (sp === 2) {   // broad spreading crown, flat-topped, several clumps
        [[0, 0, 0, 1], [-.55, -.05, .25, .7], [.5, -.08, .35, .65], [.1, .02, -.55, .7]].forEach(([ox, oy, oz, s], j) => add(canopy(R * .75 * s, .55, seed + j, pal, .32), leafM, 'canopy_clump_' + (j + 1), [ox * R, trunkH + R * (.45 + oy), oz * R], t)); }
      else {   // conifer: stacked tiers
        [[1, 0], [.78, .75], [.52, 1.4], [.28, 1.95]].forEach(([s, y], j) => { const g = new THREE.ConeGeometry(R * .75 * s, R * .95, 10, 1); const p = g.attributes.position, col = [], lo = new THREE.Color(PAL[4][0]), hi = new THREE.Color(PAL[4][1]), c = new THREE.Color();
          for (let k = 0; k < p.count; k++) { c.copy(lo).lerp(hi, (p.getY(k) / (R * .95) + .5) * .7 + hash(k, j, i) * .25); col.push(c.r, c.g, c.b); } g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
          add(g, leafM, 'conifer_tier_' + (j + 1), [0, trunkH + R * (.4 + y * .6), 0], t); }); } }); }

  // --- slab with sunken bowls ---------------------------------------------------
  // closed loop matching the SVG drawing: uniform Catmull-Rom (1/6 handles) on smooth runs, straight at corners, every plan point kept exactly
  const edgeLoop = (pts, corners = [], n = 192) => { const cs = new Set(corners), N = pts.length, segs = [];
    for (let i = 0; i < N; i++) { const p0 = pts[(i - 1 + N) % N], p1 = pts[i], p2 = pts[(i + 1) % N], p3 = pts[(i + 2) % N], straight = cs.has(i) || cs.has((i + 1) % N);
      const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6], c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
      const at = t => straight ? [p1[0] + (p2[0] - p1[0]) * t, p1[1] + (p2[1] - p1[1]) * t] : [0, 1].map(k => { const u = 1 - t; return u * u * u * p1[k] + 3 * u * u * t * c1[k] + 3 * u * t * t * c2[k] + t * t * t * p2[k]; });
      let L = 0, q = at(0); for (let k = 1; k <= 16; k++) { const r = at(k / 16); L += Math.hypot(r[0] - q[0], r[1] - q[1]); q = r; } segs.push({ at, L }); }
    const total = segs.reduce((s, g) => s + g.L, 0), out = []; segs.forEach(g => { const m = Math.max(1, Math.round(n * g.L / total)); for (let k = 0; k < m; k++) out.push(v3(g.at(k / m))); }); return out; };
  const ringO = edgeLoop(PLAN.ringOuter, PLAN.ringOuterCorners, 192), ringI = alignByAngle(ringO, edgeLoop(PLAN.ringInner, PLAN.ringInnerCorners, 192));
  const peaShape = shapeFromPath(PLAN.peanut); const peaPts2 = peaShape.getSpacedPoints(192);
  const peaC = peaPts2.reduce((a, p) => a.add(p), new THREE.Vector2()).multiplyScalar(1 / peaPts2.length);
  const peaO = peaPts2.map(p => new THREE.Vector3(p.x, 0, p.y)), peaI = peaPts2.map(p => new THREE.Vector3(peaC.x + (p.x - peaC.x) * .5, 0, peaC.y + (p.y - peaC.y) * .5));
  feat('slab', 0, 'Concrete slab', 1);
  const slab = shapeFromPath(PLAN.slab); slab.holes.push(shapeFromV3(ringO, new THREE.Path())); slab.holes.push(holeFromPath(PLAN.peanut));
  extrude(slab, TOP, M.concrete, 'slab', 0);
  const slabPoly = shapeFromPath(PLAN.slab).getPoints(10), sdSlab = (px, pz) => { let d = Infinity, inside = false;
    for (let i = 0, j = slabPoly.length - 1; i < slabPoly.length; j = i++) { const a = slabPoly[i], c = slabPoly[j]; if ((a.y > pz) !== (c.y > pz) && px < (c.x - a.x) * (pz - a.y) / (c.y - a.y) + a.x) inside = !inside;
      const ex = c.x - a.x, ez = c.y - a.y, t = Math.max(0, Math.min(1, ((px - a.x) * ex + (pz - a.y) * ez) / (ex * ex + ez * ez || 1))); d = Math.min(d, Math.hypot(px - a.x - ex * t, pz - a.y - ez * t)); } return inside ? d : -d; };
  // south end bank — the aerial frame cut the park off here: quarter-pipe transition up from the slab, steel coping, 5' deck, grass berm down to the lawn; ends taper to the slab
  { const xa = X(470), xb = X(105), z0 = Z(0), R = 1.6, H = 1.2, dT = Math.sqrt(R * R - (R - H) * (R - H)), DK = 1.5, BM = 3.2, TP = 2.5, NX = 48, sm = u => u * u * (3 - 2 * u);
    const tp = x => sm(Math.max(0, Math.min(1, Math.min(x - xb, xa - x) / TP)));
    const grid = (pts, yOf, m, name) => { const pos = [], uv = [], idx = [], P = pts.length, arc = [0]; for (let i = 1; i < P; i++) arc.push(arc[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
      for (let j = 0; j <= NX; j++) { const x = xb + (xa - xb) * j / NX, t = tp(x); for (let i = 0; i < P; i++) { pos.push(x, yOf(pts[i], t), z0 - pts[i][0]); uv.push(x, arc[i]); } }
      for (let j = 0; j < NX; j++) for (let i = 0; i < P - 1; i++) { const a = j * P + i, c = a + P; idx.push(a, c, a + 1, a + 1, c, c + 1); } return bufferMesh(pos, idx, uv, m, name); };
    const prof = []; for (let i = 0; i <= 16; i++) { const d = dT * i / 16; prof.push([d, R - Math.sqrt(R * R - d * d)]); } prof.push([dT + DK, H]);
    grid(prof, ([d, h], t) => TOP + h * t, M.bank, 'south_end_bank');
    const berm = []; for (let i = 0; i <= 12; i++) { const u = i / 12; berm.push([dT + DK + BM * u, u]); }
    grid(berm, ([d, u], t) => Math.max(.005, (TOP + H * t) * (1 - sm(u))), M.grass, 'south_end_berm');
    add(new THREE.TubeGeometry(new THREE.LineCurve3(new THREE.Vector3(xb + TP, TOP + H + .02, z0 - dT), new THREE.Vector3(xa - TP, TOP + H + .02, z0 - dT)), 1, .05, 8, false), M.coping, 'south_end_coping'); }

  // terrain height at a plan point — slab top, or the bank/floor of a bowl
  const BOWL_D = .95, FLOOR1 = TOP - BOWL_D, PEA_D = 1.7, FLOOR4 = TOP - PEA_D;
  // F1 stepped ledges, traced from the 2026 aerial: equal shadow bands = uniform risers, four of them deck → floor (BOWL_D / 4 ≈ 9⅜″); level = risers below the deck
  const RISER = BOWL_D / 4, stairs = (PLAN.stairs || []).map(t => ({ ...t, y: TOP - t.level * RISER, p2: t.poly.map(([x, y]) => [X(x), Z(y)]) }));
  const inPoly2 = (pp, x, z) => { let c = false; for (let i = 0, j = pp.length - 1; i < pp.length; j = i++) { const a = pp[i], q = pp[j]; if ((a[1] > z) !== (q[1] > z) && x < (q[0] - a[0]) * (z - a[1]) / (q[1] - a[1]) + a[0]) c = !c; } return c; };
  const stairAt = (x, z) => stairs.find(t => inPoly2(t.p2, x, z));
  const ringC = ringO.reduce((a, p) => a.add(p), new THREE.Vector3()).multiplyScalar(1 / ringO.length);
  const peaC3 = new THREE.Vector3(peaC.x, 0, peaC.y);
  function bowlHeight(p, outer, inner, c, top, depth) {
    const a = Math.atan2(p.z - c.z, p.x - c.x); let bi = 0, bd = 9;
    for (let i = 0; i < outer.length; i++) { let d = Math.abs(Math.atan2(outer[i].z - c.z, outer[i].x - c.x) - a); d = Math.min(d, Math.PI * 2 - d); if (d < bd) { bd = d; bi = i; } }
    const ro = Math.hypot(outer[bi].x - c.x, outer[bi].z - c.z), ri = Math.hypot(inner[bi].x - c.x, inner[bi].z - c.z), rp = Math.hypot(p.x - c.x, p.z - c.z);
    if (rp >= ro) return null; if (rp <= ri) return top - depth;
    const s = (ro - rp) / (ro - ri), t = 2 / Math.PI * Math.acos(Math.max(-1, Math.min(1, 1 - s)));
    return top - depth * Math.sin(t * Math.PI / 2);
  }
  const terrainNat = (x, y) => { const p = v3([x, y]); return bowlHeight(p, ringO, ringI, ringC, TOP, BOWL_D) ?? bowlHeight(p, peaO, peaI, peaC3, TOP, PEA_D) ?? TOP; };
  const terrainY = (x, y) => { const t = stairAt(X(x), Z(y)); return t ? t.y : terrainNat(x, y); };
  // solid block whose top follows the terrain (ledges that ride a bank, hubbas)
  function prismOnTerrain(corners, thick, m, name, bottomY) {
    const top = corners.map(([x, y]) => new THREE.Vector3(X(x), terrainY(x, y) + thick, Z(y))), bot = top.map(p => new THREE.Vector3(p.x, bottomY, p.z));
    const pos = [], push = (...vs) => vs.forEach(v => pos.push(v.x, v.y, v.z));
    push(top[0], top[1], top[2], top[0], top[2], top[3]);
    for (let i = 0; i < 4; i++) { const a = top[i], b = top[(i + 1) % 4], c = bot[(i + 1) % 4], d = bot[i]; push(a, d, c, a, c, b); }
    return bufferMesh(pos, null, null, m, name);
  }

  // saw-cut control joints on the flat deck, ~15 ft panels (skipped inside the bowls and under raised features)
  { const poly = shapeFromPath(PLAN.slab).getPoints(12), inside = (x, z) => { let c = false; for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) { const a = poly[i], q = poly[j]; if ((a.y > z) !== (q.y > z) && x < (q.x - a.x) * (z - a.y) / (q.y - a.y) + a.x) c = !c; } return c; };
    const sp = 4.57, st = .4, pts = [], bb = new THREE.Box3().setFromPoints(poly.map(p => new THREE.Vector3(p.x, 0, p.y))), flat = (x, z) => inside(x, z) && terrainY(x / S + 480, z / S + 640) === TOP;
    for (let x = Math.ceil(bb.min.x / sp) * sp; x < bb.max.x; x += sp) for (let z = bb.min.z; z < bb.max.z; z += st) if (flat(x, z) && flat(x, z + st / 2) && flat(x, z + st)) pts.push(x, TOP + .004, z, x, TOP + .004, z + st);
    for (let z = Math.ceil(bb.min.z / sp) * sp; z < bb.max.z; z += sp) for (let x = bb.min.x; x < bb.max.x; x += st) if (flat(x, z) && flat(x + st / 2, z) && flat(x + st, z)) pts.push(x, TOP + .004, z, x + st, TOP + .004, z);
    const jg = new THREE.BufferGeometry(); jg.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3)); const jl = new THREE.LineSegments(jg, new THREE.LineBasicMaterial({ color: 0x9AA1A7, transparent: true, opacity: .5 })); jl.name = 'saw_cut_joints'; cur.add(jl); }

  // 1 — flow bowl: banked ring, stepped ledges descending into the bowl, centre mound with colour wash
  feat('F1', 1, labelOf(1), 2);
  // bank + stamp band drop out of sight under the stair tiers (fine 768-sample loop keeps the cut line tight)
  const nearStair = (x, z) => stairAt(x, z) || stairs.some(t => t.p2.some((a, i) => { const q = t.p2[(i + 1) % t.p2.length], ex = q[0] - a[0], ez = q[1] - a[1], u = Math.max(0, Math.min(1, ((x - a[0]) * ex + (z - a[1]) * ez) / (ex * ex + ez * ez || 1))); return Math.hypot(x - a[0] - ex * u, z - a[1] - ez * u) < .015; }));
  const sink = mesh => { const p = mesh.geometry.attributes.position; for (let k = 0; k < p.count; k++) if (nearStair(p.getX(k), p.getZ(k))) p.setY(k, FLOOR1 - .06); p.needsUpdate = true; mesh.geometry.computeVertexNormals(); return mesh; };
  { const hiO = edgeLoop(PLAN.ringOuter, PLAN.ringOuterCorners, 768), hiI = alignByAngle(hiO, edgeLoop(PLAN.ringInner, PLAN.ringInnerCorners, 768));
    sink(bankSurface(hiO, hiI, TOP, BOWL_D, M.terracotta, 'flow_bowl_bank')); sink(stampBand(hiO, hiI, TOP, BOWL_D, .34, 'flow_bowl_stamp_band')); }
  flat(shapeFromV3(ringI), M.concrete, 'flow_bowl_floor', FLOOR1);
  { const r = PLAN.dome.r * S, hgt = .5, g = new THREE.SphereGeometry(r, 48, 16, 0, Math.PI * 2, 0, Math.PI / 2); g.scale(1, hgt / r, 1);
    if (PLAN.moundWash) extrude(shapeFromPath(PLAN.moundWash), .012, M.wash, 'mound_colour_wash', FLOOR1 - .004);
    add(g, M.terracotta, 'volcano_mound', [X(PLAN.dome.x), FLOOR1, Z(PLAN.dome.y)]);
    const rim = new THREE.TorusGeometry(r + .01, .05, 10, 72); rim.rotateX(Math.PI / 2); add(rim, M.coping, 'mound_rim', [X(PLAN.dome.x), FLOOR1 + .01, Z(PLAN.dome.y)]); }
  { const a = PLAN.ringOuter.findIndex(p => p[0] === 495 && p[1] === 124), e = PLAN.ringOuter.findIndex(p => p[0] === 574 && p[1] === 548); lip(PLAN.ringOuter.slice(a, e + 1).map(p => v3(p)), TOP - .02, .075, M.coping, 'flow_bowl_coping'); }
  { const MAT = { tan: M.terracotta, tan2: M.terracotta, red: M.red, light: M.concrete, light2: M.pink }, BOT = FLOOR1 - .06, ringPoly = ringO.map(p => [p.x, p.z]);   // exact deck test (bowlHeight's nearest-angle lookup is too coarse on the stair edge)
    stairs.forEach(t => { const m = MAT[t.c] || M.terracotta;
      extrude(shapeFromPx(t.poly), t.y - BOT, m, 'stair_tier_' + t.id, BOT);   // prism sides are the risers down to lower tiers / the floor
      // walls up to whatever stands higher just outside the tier — the deck slab, or the bank at the tier ends
      const pp = t.p2, n = pp.length, pos = [], idx = [];
      for (let i = 0; i < n; i++) { const a = pp[i], q = pp[(i + 1) % n], L = Math.hypot(q[0] - a[0], q[1] - a[1]); if (L < 1e-4) continue;
        let nx = (q[1] - a[1]) / L, nz = -(q[0] - a[0]) / L; if (inPoly2(pp, (a[0] + q[0]) / 2 + nx * .02, (a[1] + q[1]) / 2 + nz * .02)) { nx = -nx; nz = -nz; }
        const K = Math.max(1, Math.ceil(L / .08)); let prev = null;
        for (let k = 0; k <= K; k++) { const x = a[0] + (q[0] - a[0]) * k / K, z = a[1] + (q[1] - a[1]) * k / K, ox = x + nx * .04, oz = z + nz * .04;
          const h = stairAt(ox, oz) ? null : inPoly2(ringPoly, ox, oz) ? terrainNat(ox / S + 480, oz / S + 640) : TOP, cur = h != null && h > t.y + .005 ? [x, z, h] : null;
          if (cur && prev) { const base = pos.length / 3; pos.push(prev[0], t.y, prev[1], prev[0], prev[2], prev[1], cur[0], t.y, cur[1], cur[0], cur[2], cur[1]); idx.push(base, base + 2, base + 1, base + 1, base + 2, base + 3); }
          prev = cur; } }
      if (pos.length) bufferMesh(pos, idx, null, m, 'stair_wall_' + t.id); }); }

  // 2 — hip bank: one smooth two-sided bank rising to the ridge (boundary samples joined to their nearest ridge point → no facets), red ridge ledge + red inlay panel
  feat('F2', 4, labelOf(4), 2);
  { const pts = PLAN.hip, [ra, rb] = PLAN.hipRidge, H = .75, K = 16, N = 120;
    const closest = p => { const dx = rb[0] - ra[0], dy = rb[1] - ra[1], t = Math.max(0, Math.min(1, ((p[0] - ra[0]) * dx + (p[1] - ra[1]) * dy) / (dx * dx + dy * dy))); return [ra[0] + dx * t, ra[1] + dy * t]; };
    const toEdge = (q, u) => { let best = Infinity; for (let i = 0; i < pts.length; i++) { const p0 = pts[i], p1 = pts[(i + 1) % pts.length], ex = p1[0] - p0[0], ey = p1[1] - p0[1], den = u[0] * ey - u[1] * ex; if (Math.abs(den) < 1e-9) continue;
      const sd = ((p0[0] - q[0]) * ey - (p0[1] - q[1]) * ex) / den, w = ((p0[0] - q[0]) * u[1] - (p0[1] - q[1]) * u[0]) / den; if (sd > 1e-6 && w >= -1e-6 && w <= 1 + 1e-6) best = Math.min(best, sd); } return best; };
    const prof = t => .55 * (1 - t) + .45 * (1 - Math.sin(t * Math.PI / 2));   // mostly planar bank with a rolled toe
    const hipH = p => { const q = closest(p), d = Math.hypot(p[0] - q[0], p[1] - q[1]); if (d < 1e-6) return H; const D = toEdge(q, [(p[0] - q[0]) / d, (p[1] - q[1]) / d]); return isFinite(D) ? H * prof(Math.min(1, d / D)) : 0; };
    const per = pts.reduce((acc, p, i) => acc + Math.hypot(pts[(i + 1) % pts.length][0] - p[0], pts[(i + 1) % pts.length][1] - p[1]), 0), rim = [];
    for (let i = 0; i < N; i++) { let d = per * i / N, hit = null; for (let j = 0; j < pts.length && !hit; j++) { const p0 = pts[j], p1 = pts[(j + 1) % pts.length], L = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]); if (d <= L) hit = [p0[0] + (p1[0] - p0[0]) * d / L, p0[1] + (p1[1] - p0[1]) * d / L]; else d -= L; } rim.push(hit || pts[0].slice()); }
    const pos = [], uv = [], idx = [];
    rim.forEach(bp => { const q = closest(bp); for (let k = 0; k <= K; k++) { const t = k / K, x = q[0] + (bp[0] - q[0]) * t, y = q[1] + (bp[1] - q[1]) * t; pos.push(X(x), TOP + H * prof(t), Z(y)); uv.push(X(x), Z(y)); } });
    for (let i = 0; i < N; i++) for (let k = 0; k < K; k++) { const p0 = i * (K + 1) + k, p1 = p0 + 1, p2 = ((i + 1) % N) * (K + 1) + k, p3 = p2 + 1; idx.push(p0, p1, p2, p1, p3, p2); }
    bufferMesh(pos, idx, uv, M.terracotta, 'hip_bank');
    const dx = X(rb[0]) - X(ra[0]), dz = Z(rb[1]) - Z(ra[1]);
    box(Math.hypot(dx, dz) + .1, .16, .34, M.red, 'hip_ridge_ledge', [(X(ra[0]) + X(rb[0])) / 2, TOP + H + .02, (Z(ra[1]) + Z(rb[1])) / 2], -Math.atan2(dz, dx));
    { const c = [331, 518], hw = 12, hh = 18, ca = Math.cos(-28 * Math.PI / 180), sa = Math.sin(-28 * Math.PI / 180), P = [], I = [], U = [], NU = 8, NV = 12;   // red inlay following the SE face
      for (let i = 0; i <= NV; i++) for (let j = 0; j <= NU; j++) { const lx = -hw + 2 * hw * j / NU, ly = -hh + 2 * hh * i / NV, x = c[0] + lx * ca - ly * sa, y = c[1] + lx * sa + ly * ca; P.push(X(x), TOP + hipH([x, y]) + .02, Z(y)); U.push(j / NU, i / NV); }
      for (let i = 0; i < NV; i++) for (let j = 0; j < NU; j++) { const p0 = i * (NU + 1) + j, p1 = p0 + 1, p2 = p0 + NU + 1, p3 = p2 + 1; I.push(p0, p1, p2, p1, p3, p2); }
      bufferMesh(P, I, U, M.red, 'hip_red_panel'); } }

  // 3 — blue wave bank & red manual pad (as built, per the drone photo + aerial): a flat red manual pad; a blue kidney "wave" — one planar bank falling
  //     from pad height at its south end to the slab at its north tip; maroon banks drop from the wave's steel crest edge to the slab, and to the bowl rim
  //     on the south. The pad is set back from the rim and rolls down into the bowl, so nothing overhangs it.
  feat('F3', 3, labelOf(3), 2);
  { const PAD_H = .38, RUN = .6, hOf = d => { const u = Math.max(0, Math.min(1, d / RUN)); return TOP + PAD_H * u * u * (3 - 2 * u); };
    const rayRing = dir => { let best = Infinity; for (let i = 0; i < ringO.length; i++) { const A = ringO[i], B = ringO[(i + 1) % ringO.length], ex = B.x - A.x, ez = B.z - A.z, den = dir.x * ez - dir.z * ex; if (Math.abs(den) < 1e-9) continue;
        const ax = A.x - ringC.x, az = A.z - ringC.z, t = (ax * ez - az * ex) / den, u = (ax * dir.z - az * dir.x) / den; if (t > 0 && u >= -1e-6 && u <= 1 + 1e-6 && t < best) best = t; } return best; };
    const ringAt = p => { const dir = new THREE.Vector3(p.x - ringC.x, 0, p.z - ringC.z).normalize(), ro = rayRing(dir); return { ro, d: Math.hypot(p.x - ringC.x, p.z - ringC.z) - ro, dir }; };
    const runsOf = (n, pred) => { const idxs = [...Array(n).keys()], s0 = idxs.findIndex(i => !pred(i)); if (s0 < 0) return [idxs]; const rot = [...idxs.slice(s0), ...idxs.slice(0, s0)], runs = []; let cur = null;
      rot.forEach(i => { if (pred(i)) (cur || (cur = [])).push(i); else if (cur) { runs.push(cur); cur = null; } }); if (cur) runs.push(cur); return runs; };
    const dens = (pts, step = .25) => { const out = []; for (let i = 0; i < pts.length; i++) { const a = v3(pts[i]), c = v3(pts[(i + 1) % pts.length]), n = Math.max(1, Math.ceil(a.distanceTo(c) / step)); for (let k = 0; k < n; k++) out.push(a.clone().lerp(c, k / n)); } return out; };
    // manual pad: the straight-edged part of the traced outline, its west edge hugging the wave
    const all = dens([[455, 560], [497, 562], [494, 592], [493, 600], [494, 668], [488, 676], [453, 668], [433, 602]]), info = all.map(ringAt), near = i => info[i].d < RUN, at = (i, dd) => ringC.clone().addScaledVector(info[i].dir, info[i].ro + dd);
    const padSh = new THREE.Shape(); all.forEach((p, i) => { const q = near(i) ? at(i, RUN) : p; i ? padSh.lineTo(q.x, q.z) : padSh.moveTo(q.x, q.z); }); padSh.closePath();
    extrude(padSh, PAD_H, M.red, 'manual_pad', TOP);
    runsOf(all.length, near).forEach((run, r) => { const K = 12, N = run.length, pos = [], uv = [], idx = [];
      let arc = 0; for (let j = 0; j < N; j++) { const i = run[j], dOut = Math.max(0, info[i].d); if (j) arc += all[i].distanceTo(all[run[j - 1]]);
        for (let k = 0; k <= K; k++) { const t = k / K, dd = RUN + (dOut - RUN) * t, q = at(i, dd); pos.push(q.x, hOf(dd) + .004 * t, q.z); uv.push(arc, dd); } }
      for (let j = 0; j < N - 1; j++) for (let k = 0; k < K; k++) { const a = j * (K + 1) + k, c = a + K + 1; idx.push(a, c, a + 1, a + 1, c, c + 1); }
      bufferMesh(pos, idx, uv, M.red, 'pad_rollover' + (r ? '_' + (r + 1) : '')); });
    // blue wave: planar, pad height at the south end down to the slab at the north tip
    const zS = Z(588), zN = Z(690), hB = z => TOP + PAD_H * Math.max(0, Math.min(1, (zN - z) / (zN - zS)));
    const kid = shapeFromPath(PLAN.pool), kg = new THREE.ShapeGeometry(kid, 24); kg.rotateX(Math.PI / 2);
    { const p = kg.attributes.position; for (let k = 0; k < p.count; k++) p.setY(k, hB(p.getZ(k)) + .004); kg.computeVertexNormals(); }
    add(kg, M.blue, 'wave_bank_blue');
    // maroon banks: ruled strip from the wave's crest (all but its pad side) out to the traced outline, clamped at the bowl rim
    const nearest = (pts, q) => { let bi = 0, bd = Infinity; pts.forEach((p, i) => { const d = p.distanceToSquared(q); if (d < bd) { bd = d; bi = i; } }); return bi; };
    const slice = (pts, i0, i1) => { const out = []; for (let i = i0; ; i = (i + 1) % pts.length) { out.push(pts[i]); if (i === i1) break; } return out; };
    const resample = (pts, n) => { const cum = [0]; for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + pts[i].distanceTo(pts[i - 1])); const L = cum[cum.length - 1], out = []; let j = 0;
      for (let k = 0; k < n; k++) { const t = L * k / (n - 1); while (j < cum.length - 2 && cum[j + 1] < t) j++; const f = (t - cum[j]) / Math.max(1e-9, cum[j + 1] - cum[j]); out.push(pts[j].clone().lerp(pts[j + 1], Math.min(1, f))); } return out; };
    const kPts = kid.getSpacedPoints(220).map(p => new THREE.Vector3(p.x, 0, p.y)), oPts = shapeFromPath(PLAN.padPath).getSpacedPoints(300).map(p => new THREE.Vector3(p.x, 0, p.y));
    const NB = 140, inR = resample(slice(kPts, nearest(kPts, v3([493, 600])), nearest(kPts, v3([494, 668]))), NB);
    const outR = resample(slice(oPts, nearest(oPts, v3([497, 562])), nearest(oPts, v3([488, 676]))), NB).map(p => { const r = ringAt(p); return r.d < 0 ? ringC.clone().addScaledVector(r.dir, r.ro) : p; });
    const maroon = mat('maroon_concrete', '#A65A55', { tex: 1 });
    { const K = 4, pos = [], uv = [], idx = []; let arc = 0;
      for (let i = 0; i < NB; i++) { const a = inR[i], c = outR[i], ha = hB(a.z) + .004, hc = TOP + .003, w = a.distanceTo(c); if (i) arc += a.distanceTo(inR[i - 1]);
        for (let k = 0; k <= K; k++) { const t = k / K, q = a.clone().lerp(c, t); pos.push(q.x, ha + (hc - ha) * t, q.z); uv.push(arc, t * w); } }
      for (let i = 0; i < NB - 1; i++) for (let k = 0; k < K; k++) { const p0 = i * (K + 1) + k, p1 = p0 + K + 1; idx.push(p0, p1, p0 + 1, p0 + 1, p1, p1 + 1); }
      bufferMesh(pos, idx, uv, maroon, 'wave_bank_maroon'); }
    add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(inR.map(p => new THREE.Vector3(p.x, hB(p.z) + .014, p.z)), false, 'catmullrom', .5), 200, .02, 8, false), mat('wave_crest_edge', '#EEF2F5', { roughness: .35, metalness: .4 }), 'wave_crest_edge');
    // light bank beside the pad (unchanged): flush with the pad top on its east edge, down to the plaza on its west edge
    { const kp = [[441, 550], [455, 560], [433, 602], [419, 594]], k = kp.map(p => v3(p)), NU = 8, NS = 24, pos = [], uv = [], idx = [], sm = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
      const clampRim = w => { const r = ringAt(w); return { q: r.d < 0 ? ringC.clone().addScaledVector(r.dir, r.ro) : w, d: Math.max(0, r.d) }; };
      for (let c = 0; c <= NU; c++) for (let r = 0; r <= NS; r++) { const u = c / NU, sv = r / NS, w = k[0].clone().lerp(k[1], u).lerp(k[3].clone().lerp(k[2], u), sv), { q, d } = clampRim(w);
        pos.push(q.x, Math.min(TOP + PAD_H * u, hOf(d)) + (u > .999 ? .002 : .004), q.z); uv.push(u * .75, sv * 2.3); }
      for (let c = 0; c < NU; c++) for (let r = 0; r < NS; r++) { const i0 = c * (NS + 1) + r, i1 = i0 + NS + 1; idx.push(i0, i1, i0 + 1, i0 + 1, i1, i1 + 1); }
      { const base = pos.length / 3, row = NS; for (let c = 0; c <= NU; c++) { const i0 = c * (NS + 1) + row; pos.push(pos[i0 * 3], pos[i0 * 3 + 1], pos[i0 * 3 + 2], pos[i0 * 3], TOP, pos[i0 * 3 + 2]); uv.push(c * .1, 0, c * .1, 1); }
        for (let c = 0; c < NU; c++) { const p0 = base + c * 2; idx.push(p0, p0 + 2, p0 + 1, p0 + 1, p0 + 2, p0 + 3); } }
      bufferMesh(pos, idx, uv, M.kicker, 'pad_side_bank'); } }

  // 4 — peanut bowl: banked walls, pool coping around the deep (smaller) lobe
  feat('F4', 2, labelOf(2), 2);
  bankSurface(peaO, peaI, TOP, PEA_D, M.bank, 'peanut_bowl_bank');
  flat(shapeFromV3(peaI), M.concrete, 'peanut_bowl_floor', FLOOR4);
  { const it = PLAN.peanutIsland; const w = it.w * S, h = it.h * S, r = .6; const sh = new THREE.Shape();
    sh.moveTo(-w / 2 + r, -h / 2); sh.lineTo(w / 2 - r, -h / 2); sh.absarc(w / 2 - r, -h / 2 + r, r, -Math.PI / 2, 0, false); sh.lineTo(w / 2, h / 2 - r); sh.absarc(w / 2 - r, h / 2 - r, r, 0, Math.PI / 2, false);
    sh.lineTo(-w / 2 + r, h / 2); sh.absarc(-w / 2 + r, h / 2 - r, r, Math.PI / 2, Math.PI, false); sh.lineTo(-w / 2, -h / 2 + r); sh.absarc(-w / 2 + r, -h / 2 + r, r, Math.PI, Math.PI * 1.5, false);
    const g = new THREE.ExtrudeGeometry(sh, { depth: .04, bevelEnabled: false, curveSegments: 12 }); g.rotateX(Math.PI / 2); g.translate(0, .04, 0);
    const mesh = add(g, M.terracotta, 'peanut_island', [X(it.x), FLOOR4, Z(it.y)]); mesh.rotation.y = -it.rot * Math.PI / 180;
    const c = PLAN.peanutCircle; add(new THREE.CylinderGeometry(c.r * S, c.r * S, .04, 40), M.terracotta, 'peanut_disc', [X(c.x), FLOOR4 + .02, Z(c.y)]); }
  stampBand(peaO, peaI, TOP, PEA_D, .38, 'peanut_stamp_band');
  // 25' north-edge extension: rim raised on a smooth hump, bank ramping up from the deck, coping carried over the raised lip; flat coping continues around the rest
  { const N = peaO.length, adir = v3([PLAN.peanutIsland.x, PLAN.peanutIsland.y]).sub(v3([PLAN.peanutCircle.x, PLAN.peanutCircle.y])).setY(0).normalize(), iN = peaO.reduce((bi, p, i) => p.dot(adir) > peaO[bi].dot(adir) ? i : bi, 0), HALF = 25 * .3048 / 2, HX = .6, LIP = .3;
    const ids = [iN]; let acc = 0; for (let k = 1; acc < HALF && k < N / 2; k++) { const i = (iN + k) % N; acc += peaO[i].distanceTo(peaO[(i - 1 + N) % N]); ids.push(i); }
    acc = 0; for (let k = 1; acc < HALF && k < N / 2; k++) { const i = (iN - k + N) % N; acc += peaO[i].distanceTo(peaO[(i + 1) % N]); ids.unshift(i); }
    const arc = [0]; for (let k = 1; k < ids.length; k++) arc.push(arc[k - 1] + peaO[ids[k]].distanceTo(peaO[ids[k - 1]])); const Lt = arc[arc.length - 1], sm = u => u * u * (3 - 2 * u);
    const H = k => HX * sm(Math.min(1, Math.min(arc[k], Lt - arc[k]) / 1.6));
    const nrm = k => { const i = ids[k], a = peaO[(i - 1 + N) % N], c = peaO[(i + 1) % N], n = new THREE.Vector3(c.z - a.z, 0, -(c.x - a.x)).normalize(); if (n.dot(new THREE.Vector3().subVectors(peaO[i], peaC3)) < 0) n.negate(); return n; };
    const pw = [], pr = [], iw = [], ir = [], Mn = ids.length;
    for (let k = 0; k < Mn; k++) { const p = peaO[ids[k]], n = nrm(k), h = H(k); let run = LIP + 2.2 * h; const toe = p.clone().addScaledVector(n, run); run -= Math.max(0, .05 - sdSlab(toe.x, toe.z)); run = Math.max(LIP + .05, run);
      const q1 = p.clone().addScaledVector(n, LIP), q2 = p.clone().addScaledVector(n, run); pw.push(p.x, TOP, p.z, p.x, TOP + h + .003, p.z); pr.push(p.x, TOP + h + .003, p.z, q1.x, TOP + h + .003, q1.z, q2.x, TOP + .003, q2.z); }
    for (let k = 0; k < Mn - 1; k++) { const a = k * 2, r = k * 3; iw.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); ir.push(r, r + 3, r + 1, r + 1, r + 3, r + 4, r + 1, r + 4, r + 2, r + 2, r + 4, r + 5); }
    bufferMesh(pw, iw, null, M.bank, 'peanut_extension_wall'); bufferMesh(pr, ir, null, M.concrete, 'peanut_extension_ramp');
    add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(ids.map((i, k) => new THREE.Vector3(peaO[i].x, TOP + H(k) + .03, peaO[i].z))), 96, .07, 10, false), M.coping, 'peanut_extension_coping');
    const rest = [peaO[ids[Mn - 1]]]; for (let k = (ids[Mn - 1] + 1) % N; k !== ids[0]; k = (k + 1) % N) rest.push(peaO[k]); rest.push(peaO[ids[0]]);
    lip(rest, TOP - .02, .075, M.coping, 'peanut_coping'); }

  // 5 — corner pocket: quarter-pipe wall above grade, ends ramped down to the slab, coping lip on top
  feat('F5', 5, labelOf(5), 2);
  { const Rc = PLAN.pocket.r * S, T = 1.3, prof = [], org = [X(PLAN.pocket.cx), 0, Z(PLAN.pocket.cy)], ph = PLAN.pocketPhi || { start: -Math.PI / 2, length: Math.PI };
    for (let k = 0; k <= 14; k++) { const a = k / 14 * Math.PI / 2; prof.push(new THREE.Vector2(Rc - T + T * Math.sin(a), TOP + T - T * Math.cos(a))); }
    prof.push(new THREE.Vector2(Rc, TOP + T + .1), new THREE.Vector2(Rc + .5, TOP + T + .1), new THREE.Vector2(Rc + .5, TOP));
    add(new THREE.LatheGeometry(prof, 44, ph.start, ph.length), M.bank, 'corner_pocket', org);
    const capL = 2.6, K = 10, P = prof.length;
    [[ph.start, -1], [ph.start + ph.length, 1]].forEach(([phi, dir], e) => { const pos = [], idx = [], uv = [], sx = Math.sin(phi), cx = Math.cos(phi);   // ends ramp down tangentially to the slab
      for (let k = 0; k <= K; k++) { const u = k / K; for (let j = 0; j < P; j++) { const p = prof[j]; pos.push(p.x * sx + dir * u * capL * cx, TOP + (p.y - TOP) * (1 - u), p.x * cx - dir * u * capL * sx); uv.push(u * capL, j * .15); } }
      for (let k = 0; k < K; k++) for (let j = 0; j < P - 1; j++) { const a = k * P + j, b = a + 1, c = a + P, d = c + 1; idx.push(a, b, c, b, d, c); }
      bufferMesh(pos, idx, uv, M.bank, 'pocket_end_ramp_' + (e + 1), org); });
    const cop = new THREE.TorusGeometry(Rc, .07, 12, 64, ph.length); cop.rotateX(Math.PI / 2); cop.rotateY(ph.start + ph.length - Math.PI / 2); add(cop, M.coping, 'pocket_coping', [org[0], TOP + T + .08, org[2]]); }

  // 6 — street: ledges, straight rails riding the terrain at an angle, curved ledge, snake rail, log
  feat('F6', 6, labelOf(6), 3);
  PLAN.ledges.forEach((l, i) => { const m = M[l.c === 'gray' ? 'bank' : l.c], cap = { teal: M.capTeal, pink: M.capPink }[l.c] || M.capGray, nm = 'ledge_' + (i + 1);
    if (l.poly) { extrude(shapeFromPx(l.poly), .40, m, nm, TOP); extrude(shapeFromPx(l.poly), .04, cap, nm + '_cap', TOP + .40); }
    else { box(l.w * S, .40, l.h * S, m, nm, [X(l.x + l.w / 2), TOP + .20, Z(l.y + l.h / 2)]); box(l.w * S + .02, .04, l.h * S + .02, cap, nm + '_cap', [X(l.x + l.w / 2), TOP + .42, Z(l.y + l.h / 2)]); } });
  function straightRail(x1, y1, x2, y2, name, r = .03, clearance = .45) {
    const a = new THREE.Vector3(X(x1), terrainY(x1, y1) + clearance, Z(y1)), b = new THREE.Vector3(X(x2), terrainY(x2, y2) + clearance, Z(y2));
    const mid = a.clone().add(b).multiplyScalar(.5), mesh = add(new THREE.CylinderGeometry(r, r, a.distanceTo(b), 16), M.steel, name, mid.toArray());
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), b.clone().sub(a).normalize());
    [a, b].forEach((p, j) => add(new THREE.SphereGeometry(r, 12, 8), M.steel, name + '_cap_' + (j + 1), p.toArray()));
    [.12, .5, .88].forEach((t, j) => { const p = a.clone().lerp(b, t), gy = terrainY(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t), h = p.y - gy; add(new THREE.CylinderGeometry(.022, .022, h, 12), M.steel, name + '_post_' + (j + 1), [p.x, gy + h / 2, p.z]); });
  }
  PLAN.rails.forEach(([x1, y1, x2, y2], i) => straightRail(x1, y1, x2, y2, 'rail_' + (i + 1)));
  { const pts = holeFromPath(PLAN.ledge).getSpacedPoints(40); const w = .27, L = [], R = [];
    for (let i = 0; i < pts.length; i++) { const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)]; const t = new THREE.Vector2(b.x - a.x, b.y - a.y).normalize(); const n = new THREE.Vector2(-t.y, t.x);
      L.push(new THREE.Vector2(pts[i].x + n.x * w, pts[i].y + n.y * w)); R.push(new THREE.Vector2(pts[i].x - n.x * w, pts[i].y - n.y * w)); }
    const sh = new THREE.Shape(); sh.moveTo(L[0].x, L[0].y); L.slice(1).forEach(p => sh.lineTo(p.x, p.y)); R.reverse().forEach(p => sh.lineTo(p.x, p.y)); sh.closePath();
    extrude(sh, .42, M.teal, 'curved_ledge', TOP); }
  { const pts = holeFromPath(PLAN.rail).getSpacedPoints(30).map(p => new THREE.Vector3(p.x, TOP + .45, p.y));
    const curve = new THREE.CatmullRomCurve3(pts, false, 'catmullrom', .5);
    add(new THREE.TubeGeometry(curve, 64, .035, 16, false), M.red, 'snake_rail');
    [0, 1].forEach((t, j) => add(new THREE.SphereGeometry(.035, 12, 8), M.red, 'snake_rail_cap_' + (j + 1), curve.getPoint(t).toArray()));
    [.1, .5, .9].forEach((t, j) => { const p = curve.getPoint(t); add(new THREE.CylinderGeometry(.022, .022, .45, 12), M.steel, 'snake_rail_post_' + (j + 1), [p.x, TOP + .225, p.z]); }); }
  { const [x1, y1, x2, y2] = PLAN.log; bar(x1, y1, x2, y2, TOP + .18, .18, M.wood, 'log_ledge'); }

  // 7 — Coney "Glizzy Gap" spine: launch kicker, coney, landing bank
  feat('F7', 7, labelOf(7), 3);
  { const sp = PLAN.spine; const g7 = new THREE.Group(); g7.name = 'coney_glizzy_gap'; g7.position.set(X(sp.cx), 0, Z(sp.cy)); g7.rotation.y = -sp.rot * Math.PI / 180; cur.add(g7);
    const lx = x => (x - sp.cx) * S, lz = y => (y - sp.cy) * S;
    const sh = new THREE.Shape(); sh.moveTo(lx(sp.poly[0][0]), lz(sp.poly[0][1])); sp.poly.slice(1).forEach(p => sh.lineTo(lx(p[0]), lz(p[1]))); sh.closePath();
    const pg = new THREE.ExtrudeGeometry(sh, { depth: .02, bevelEnabled: false }); pg.rotateX(Math.PI / 2); pg.translate(0, TOP + .02, 0); add(pg, M.terracotta, 'spine_pad', null, g7);
    { const [tx, ty, tw, th] = sp.bun, ring = new THREE.TorusGeometry(1, .035, 8, 56); ring.rotateX(Math.PI / 2); ring.scale((tw / 2 + 2) * S, 1, (th / 2 + 1.5) * S);
      add(ring, mat('coney_tray_steel', '#2A2D31', { roughness: .45, metalness: .4 }), 'coney_tray_rail', [lx(tx + tw / 2), TOP + .1, lz(ty + th / 2)], g7); }
    sp.boxes.forEach(([x, y, w, h], i) => { const z0 = lz(y), z1 = lz(y + h), W = w * S, hh = .65, launch = i === 0;   // launch is high at its +z end (next to the coney); landing high at its -z end
      const zLow = launch ? -z0 : -z1, zHigh = launch ? -z1 : -z0;
      const s2 = new THREE.Shape(); s2.moveTo(zLow, 0); s2.lineTo(zHigh, 0); s2.lineTo(zHigh, hh);
      s2.closePath();
      const g = new THREE.ExtrudeGeometry(s2, { depth: W, bevelEnabled: false }); g.rotateY(Math.PI / 2); g.translate(-W / 2 + lx(x + w / 2), TOP, 0);
      add(g, M.bank, launch ? 'launch_ramp' : 'landing_ramp', null, g7);
      box(W, .05, .06, M.steelEdge, (launch ? 'launch' : 'landing') + '_edge_angle', [lx(x + w / 2), TOP + hh - .025, zHigh + .01], 0, g7); });
    const [bx, by, bw, bh] = sp.bun, cx = lx(bx + bw / 2), cz = lz(by + bh / 2), L = bw * S;
    const blob = (sx, sy, sz, m, name, x, y, z, rot) => { const g = new THREE.SphereGeometry(1, 48, 32); g.scale(sx, sy, sz); const mesh = add(g, m, name, [cx + x, y, cz + z], g7); if (rot) mesh.rotation.set(...rot); return mesh; };
    // a hot dog: split bun (two tan halves), sausage lying in the split and poking out both ends, chili along the top, cheese heaped on
    const half = (dz, name) => { const g = new THREE.CapsuleGeometry(.32, L - .64, 8, 24); g.rotateZ(Math.PI / 2); return add(g, M.bunTan, name, [cx, TOP + .32, cz + dz], g7); };
    half(-.23, 'bun_half_left'); half(.23, 'bun_half_right');
    const dog = new THREE.CapsuleGeometry(.17, L + .16, 8, 20); dog.rotateZ(Math.PI / 2); add(dog, M.sausageEnd, 'sausage', [cx, TOP + .52, cz], g7);
    blob(L / 2 - .12, .1, .25, M.sausage, 'chili', 0, TOP + .62, 0);
    blob(L / 2 - .05, .34, .46, M.bun, 'cheese', -.08, TOP + .86, 0, [0, .05, 0]); }

  // 8 — entry wall-ride on the spine axis: flat approach, quarter-pipe transition, arched mural wall; bike racks & repair stand
  feat('F8', 8, labelOf(8), 3);
  { const wr = PLAN.wallride, W = wr.w * S, R = 1.3, HW = 1.5, TH = .2, BL = wr.berm * S;
    const g8 = new THREE.Group(); g8.name = 'entry_wallride'; g8.position.set(X(wr.x), 0, Z(wr.y)); g8.rotation.y = -wr.rot * Math.PI / 180; cur.add(g8);
    // transition on the coney side: vertical at the wall line (z=0), rolls out flat toward the spine (-z); solid carried forward under the wall
    const prof = new THREE.Shape(); prof.moveTo(TH, 0); prof.lineTo(TH, R); prof.lineTo(0, R);
    for (let j = 1; j <= 16; j++) { const a = j / 16 * Math.PI / 2; prof.lineTo(-(R - R * Math.cos(a)), R - R * Math.sin(a)); } prof.closePath();
    const tg = new THREE.ExtrudeGeometry(prof, { depth: W, bevelEnabled: false }); tg.rotateY(-Math.PI / 2); tg.translate(W / 2, TOP, 0); add(tg, M.bank, 'wallride_transition', null, g8);
    const ws = new THREE.Shape(); ws.moveTo(-W / 2, 0); ws.lineTo(W / 2, 0);
    for (let j = 0; j <= 24; j++) { const x = W / 2 - W * j / 24; ws.lineTo(x, HW - .3 * Math.pow(2 * x / W, 2)); } ws.closePath();
    const wg = new THREE.ExtrudeGeometry(ws, { depth: TH, bevelEnabled: false });
    { const p = wg.attributes.position, n = wg.attributes.normal, uv = wg.attributes.uv;   // mural on both faces, plain concrete on the edges
      for (let i = 0; i < p.count; i++) { const u = (p.getX(i) + W / 2) / W, v = Math.min(1, p.getY(i) / HW); uv.setXY(i, Math.abs(n.getZ(i)) > .5 ? u * .45 : .6 + u * .4, v); } }
    add(wg, M.muralWall, 'mural_wall', [0, TOP + R, 0], g8);
    // grass berm on the street side: climbs from the entry pad to the back of the wall
    { const NX = 28, NZ = 24, hw = W / 2 + .7, pos = [], uv = [], idx = [];
      for (let i = 0; i <= NZ; i++) { const t = i / NZ, z = TH + BL * (1 - t), sz = t * t * (3 - 2 * t);
        for (let j = 0; j <= NX; j++) { const x = -W / 2 - 5 + (W + 5) * j / NX, e = x >= -W / 2 ? 0 : Math.min(1, (-W / 2 - x) / 5), fx = .5 + .5 * Math.cos(Math.PI * e);
          const ex = x >= -W / 2 ? 0 : Math.min(1, (-W / 2 - x) / .8), cl = 1 - ex * (1 - Math.min(1, (z - TH) / 1.2)), phi = g8.rotation.y, wx = g8.position.x + x * Math.cos(phi) + z * Math.sin(phi), wz = g8.position.z - x * Math.sin(phi) + z * Math.cos(phi);
          const u = Math.max(0, Math.min(1, (sdSlab(wx, wz) + .8) / .8)), base = .005 + (TOP + .005) * u * u * (3 - 2 * u), top = base + Math.max(0, TOP + R - base) * sz * cl;
          pos.push(x, top * fx + base * (1 - fx), z); uv.push(x, z); } }
      for (let i = 0; i < NZ; i++) for (let j = 0; j < NX; j++) { const a = i * (NX + 1) + j, b = a + 1, c = a + NX + 1, d = c + 1; idx.push(a, b, c, b, d, c); }
      g8.add(bufferMesh(pos, idx, uv, M.grass, 'wallride_berm')); }
    const blk = mat('steel_black', '#1E1F22', { roughness: .5, metalness: .35 });
    // wedge curb retaining the berm along the walk: follows the berm slope from the wall down to the walk
    { const sh = new THREE.Shape(); sh.moveTo(-TH, 0); sh.lineTo(-(TH + BL), 0); for (let k = 0; k <= 16; k++) { const t = k / 16, sz = t * t * (3 - 2 * t); sh.lineTo(-(TH + BL * (1 - t)), R * sz + .06); } sh.closePath();
      const cg = new THREE.ExtrudeGeometry(sh, { depth: .35, bevelEnabled: false }); cg.rotateY(Math.PI / 2); cg.translate(W / 2, TOP, 0); add(cg, M.bank, 'wallride_wedge_curb', null, g8); }
    // black steel edge: along the wall's arched top, then down the walk-side end and around the transition profile to the slab
    { const top = []; for (let k = 0; k <= 24; k++) { const x = -W / 2 + W * k / 24; top.push(new THREE.Vector3(x, TOP + R + HW - .3 * Math.pow(2 * x / W, 2) + .02, TH / 2)); }
      add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(top), 72, .05, 8, false), blk, 'wallride_top_cap', null, g8);
      const end = [new THREE.Vector3(W / 2 + .03, TOP + R + HW - .3, 0), new THREE.Vector3(W / 2 + .03, TOP + R + .02, 0)]; for (let k = 1; k <= 12; k++) { const a = k / 12 * Math.PI / 2; end.push(new THREE.Vector3(W / 2 + .03, TOP + R - R * Math.sin(a), -(R - R * Math.cos(a)))); }
      add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(end, false, 'catmullrom', .1), 64, .035, 8, false), blk, 'wallride_end_edge', null, g8); }
    // bike rack: one black squared loop with a crossbar, set across the walk direction
    PLAN.bikeRacks.forEach(([bx, by], ri) => { const w = .32, h = .86, r = .09, V = (x, y) => new THREE.Vector3(x, y, 0), cp = new THREE.CurvePath();
      cp.add(new THREE.LineCurve3(V(-w, 0), V(-w, h - r))); cp.add(new THREE.QuadraticBezierCurve3(V(-w, h - r), V(-w, h), V(-w + r, h))); cp.add(new THREE.LineCurve3(V(-w + r, h), V(w - r, h)));
      cp.add(new THREE.QuadraticBezierCurve3(V(w - r, h), V(w, h), V(w, h - r))); cp.add(new THREE.LineCurve3(V(w, h - r), V(w, 0)));
      const g = new THREE.Group(); g.name = 'bike_rack_' + (ri + 1); g.position.set(X(bx), .12, Z(by)); cur.add(g);
      add(new THREE.TubeGeometry(cp, 64, .024, 8, false), blk, 'bike_rack_loop', null, g); const cb = add(new THREE.CylinderGeometry(.02, .02, 2 * w, 8), blk, 'bike_rack_crossbar', [0, .45, 0], g); cb.rotation.z = Math.PI / 2; });
    // repair stand: tall blue post with a black hanger arm
    { const [rx, ry] = PLAN.repairStand, blue = mat('repair_stand_blue', '#2F5FA8', { roughness: .45, metalness: .2 }), g = new THREE.Group(); g.name = 'repair_stand'; g.position.set(X(rx), .12, Z(ry)); cur.add(g);
      add(new THREE.BoxGeometry(.11, 1.32, .11), blue, 'repair_stand_post', [0, .66, 0], g); add(new THREE.BoxGeometry(.18, .13, .12), blue, 'repair_stand_head', [0, 1.38, 0], g);
      const arm = add(new THREE.BoxGeometry(.05, .05, .44), blk, 'repair_stand_arm', [0, 1.36, .22], g); arm.rotation.x = -.3; add(new THREE.BoxGeometry(.24, .05, .24), M.steel, 'repair_stand_base', [0, .025, 0], g); } }

  // 9 — fish-scale mosaic (per the site photo): overlapping colored-concrete scales with dark grout, rows nearest the benches on top, baked into one
  //     flat decal per corner so the slab edge cuts it cleanly; stacked-stone benches with cast caps; lobed "coney" seat on a rusted base
  feat('F9', 9, labelOf(9), 4);
  { const ms = PLAN.mosaics, used = new Array(ms.length).fill(false), clusters = [];
    for (let i = 0; i < ms.length; i++) { if (used[i]) continue; const q = [i], cl = []; used[i] = true; while (q.length) { const k = q.pop(); cl.push(k); ms.forEach((c, j) => { if (!used[j] && Math.hypot(c.x - ms[k].x, c.y - ms[k].y) < 60) { used[j] = true; q.push(j); } }); } clusters.push(cl.sort((a, c) => a - c)); }
    clusters.forEach((cl, ci) => { const pad = 4, x0 = Math.min(...cl.map(k => ms[k].x - ms[k].r)) - pad, x1 = Math.max(...cl.map(k => ms[k].x + ms[k].r)) + pad, y0 = Math.min(...cl.map(k => ms[k].y - ms[k].r)) - pad, y1 = Math.max(...cl.map(k => ms[k].y + ms[k].r)) + pad;
      const K = 10, cv = document.createElement('canvas'); cv.width = Math.ceil((x1 - x0) * K); cv.height = Math.ceil((y1 - y0) * K); const g = cv.getContext('2d');
      cl.forEach(k => { const c = ms[k]; g.beginPath(); g.arc((c.x - x0) * K, (c.y - y0) * K, c.r * K, 0, Math.PI * 2); g.fillStyle = c.f; g.fill(); g.lineWidth = 1.6 * K; g.strokeStyle = '#6E7173'; g.stroke(); });
      const img = g.getImageData(0, 0, cv.width, cv.height), d = img.data; let sd = 5 + ci; const rnd = () => { sd = (sd * 16807) % 2147483647; return (sd - 1) / 2147483646; };
      for (let p = 0; p < d.length; p += 4) { if (!d[p + 3]) continue; const n = (rnd() - .5) * 18; d[p] = Math.max(0, Math.min(255, d[p] + n)); d[p + 1] = Math.max(0, Math.min(255, d[p + 1] + n)); d[p + 2] = Math.max(0, Math.min(255, d[p + 2] + n)); } g.putImageData(img, 0, 0);
      const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8;
      const m = new THREE.MeshStandardMaterial({ map: t, roughness: .85, alphaTest: .5, side: THREE.DoubleSide }); m.name = 'mosaic_fish_scale_' + (ci + 1);
      const X0 = X(x0), X1 = X(x1), Z0 = Z(y0), Z1 = Z(y1), yy = .015, geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.Float32BufferAttribute([X0, yy, Z0, X1, yy, Z0, X1, yy, Z1, X0, yy, Z1], 3)); geo.setAttribute('uv', new THREE.Float32BufferAttribute([0, 1, 1, 1, 1, 0, 0, 0], 2)); geo.setIndex([0, 2, 1, 0, 3, 2]); geo.computeVertexNormals();
      add(geo, m, 'mosaic_' + (ci + 1)); }); }
  { const cv = document.createElement('canvas'); cv.width = 256; cv.height = 128; const g = cv.getContext('2d'); g.fillStyle = '#5E5850'; g.fillRect(0, 0, 256, 128);
    let sd = 11; const rnd = () => { sd = (sd * 16807) % 2147483647; return (sd - 1) / 2147483646; }, cols = ['#8C7B68', '#A3927C', '#6F655A', '#B3A58E', '#7E7466', '#9A8B76'];
    for (let y = 2; y < 128;) { const h = 14 + rnd() * 12; for (let x = -rnd() * 30; x < 256;) { const w = 22 + rnd() * 34; g.fillStyle = cols[Math.floor(rnd() * cols.length)]; g.fillRect(x + 2, y + 1, w - 4, h - 3); x += w; } y += h; }
    const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; const stoneM = new THREE.MeshStandardMaterial({ map: t, roughness: .95 }); stoneM.name = 'bench_stacked_stone';
    PLAN.benches.forEach((bn, i) => { const ry = -bn.a * Math.PI / 180; box(1.5, .38, .55, stoneM, 'bench_' + (i + 1) + '_stone_base', [X(bn.x), .19, Z(bn.y)], ry); box(1.62, .09, .66, M.cap, 'bench_' + (i + 1) + '_cap', [X(bn.x), .425, Z(bn.y)], ry); }); }
  { const sh = new THREE.Shape(); sh.moveTo(-.3, .26); sh.quadraticCurveTo(0, .17, .3, .26); sh.absarc(.3, 0, .26, Math.PI / 2, -Math.PI / 2, true); sh.quadraticCurveTo(0, -.17, -.3, -.26); sh.absarc(-.3, 0, .26, -Math.PI / 2, Math.PI / 2, true);
    const b0 = PLAN.benches[0], b1 = PLAN.benches[1] || b0, ang = -Math.atan2(Z(b1.y) - Z(b0.y), X(b1.x) - X(b0.x)), bo = PLAN.bollard;
    const mk = (dep, m, name, y0) => { const g = new THREE.ExtrudeGeometry(sh, { depth: dep, bevelEnabled: true, bevelThickness: .02, bevelSize: .02, bevelSegments: 2, curveSegments: 24 }); g.rotateX(Math.PI / 2); g.translate(0, y0 + dep, 0); const mesh = add(g, m, name, [X(bo.x), 0, Z(bo.y)]); mesh.rotation.y = ang; return mesh; };
    mk(.16, mat('coney_seat_rust', '#8A5B3C', { roughness: .9 }), 'coney_seat_base', 0); mk(.28, mat('coney_seat_concrete', '#B8BBBD', { tex: 1 }), 'coney_seat_top', .18); }

  return { group, features, terrainY, X, Z, S, TOP, PLAN, parkRoot };
}

// numbered badge sprite matching the Sheet L-1 callouts
export function makeBadge(THREE, n, worldScale = 2.2) {
  const c = document.createElement('canvas'); c.width = c.height = 128; const g = c.getContext('2d');
  g.beginPath(); g.arc(64, 64, 56, 0, Math.PI * 2); g.fillStyle = '#212D62'; g.fill(); g.lineWidth = 8; g.strokeStyle = '#FFFFFF'; g.stroke();
  g.fillStyle = '#FFFFFF'; g.font = 'bold 66px Arial, Helvetica, sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(String(n), 64, 68);
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(c), transparent: true, depthTest: false }));
  sp.scale.set(worldScale, worldScale, 1); sp.renderOrder = 20; return sp;
}
