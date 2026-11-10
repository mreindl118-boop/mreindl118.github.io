/* Camp Washington Skatepark: the site's 3D viewer (CRC project page, set 8a).
   Replaces the artifact's three-d-stage.js + skatepark-model.js with one module that the project
   page imports on demand (the "Open the 3D model" button): same scene, lights, shadows and framing,
   the assembled / exploded-axon toggle with its layered lift and dashed leaders, numbered callouts,
   a north arrow and scale bar, the Stock Ave label, and keyboard orbit. Interface colours are the
   site's tokens; the model's own materials (skatepark-builder.js) are left as drawn.

   mount(stageEl, opts) -> Promise<viewer>
     stageEl  an empty-ish, positioned box; the canvas, HUD and road label are added to it
     opts.key           an <ol> to fill with the plan key (the nine callout labels)
     opts.label         the canvas's accessible name
     opts.describedBy   id(s) for the canvas's aria-describedby (the plan key)
     opts.spin          the opening turn into the home view (default true; never under reduced motion)
     opts.preserveDrawingBuffer  keep the last frame readable (stills harnesses)
   viewer: setExploded(on, {instant}), reset(), orbit(dTheta, dPhi), zoom(f), pause(), resume(),
           resize(), still({...}) -> data URL, state(), dispose(), and .three (scene objects).
   Stills: _drafts/tools/render_skatepark.py drives still() headlessly. */
import * as THREE from '../../vendor/three-0.184.0/build/three.module.min.js';
import { OrbitControls } from '../../vendor/three-0.184.0/examples/jsm/controls/OrbitControls.js';
import { buildSkatepark } from './skatepark-builder.js';

// site tokens (assets/css/site.css :root)
const INK = '#26292B', PINE = '#2E4A3B', PAPER = '#F3F0E8';

const LAYER_Y = [0, 5, 10, 15, 19];                 // exploded-axon lift per layer (m), as the artifact
const EXPLODE_MS = 700;                              // the lift, on the site's ease-out curve
const SPIN_MS = 4500, SPIN_FROM = -0.42;             // opening turn: from 24 deg off into the home view
const ORBIT_STEP = THREE.MathUtils.degToRad(10), TILT_STEP = THREE.MathUtils.degToRad(5), ZOOM_STEP = 1.2;
const FLAT = /^(ground|dirt_infield|stock_ave|stock_ave_sidewalk|plaza|court_apron|basketball_court|pool_deck|pool_water|mosaic|pool_fence|pool_lane_rope|entry_walk|parking|ctx_flat)/;

// cubic-bezier(0.23, 1, 0.32, 1), the site's strong ease-out, solved for y at x
function easeOut(x) {
  const ax = 0.23, ay = 1, bx = 0.32, by = 1;
  const cx = 3 * ax, bxx = 3 * (bx - ax) - cx, axx = 1 - cx - bxx;
  const cy = 3 * ay, byy = 3 * (by - ay) - cy, ayy = 1 - cy - byy;
  let t = x;
  for (let i = 0; i < 8; i++) {
    const fx = ((axx * t + bxx) * t + cx) * t - x, d = (3 * axx * t + 2 * bxx) * t + cx;
    if (Math.abs(fx) < 1e-5 || !d) break;
    t -= fx / d;
  }
  t = Math.min(1, Math.max(0, t));
  return ((ayy * t + byy) * t + cy) * t;
}
const easeInOut = x => 0.5 - 0.5 * Math.cos(Math.PI * x);
const reducedMotion = () => !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

// numbered callout badge: pine disc, paper ring and numeral in the site's mono (drawn once per number)
function badgeSprite(n) {
  const c = document.createElement('canvas'); c.width = c.height = 128; const g = c.getContext('2d');
  g.beginPath(); g.arc(64, 64, 54, 0, Math.PI * 2); g.fillStyle = PINE; g.fill();
  g.lineWidth = 8; g.strokeStyle = PAPER; g.stroke();
  g.fillStyle = PAPER; g.font = '500 64px "IBM Plex Mono", ui-monospace, monospace';
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(String(n), 64, 68);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false, sizeAttenuation: false }));
  sp.renderOrder = 20; sp.name = 'callout_' + n;
  return sp;
}

function el(tag, cls, html) { const e = document.createElement(tag); if (cls) e.className = cls; if (html) e.innerHTML = html; return e; }
function escHtml(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

export async function mount(stage, opts = {}) {
  // a mount that fails part-way gives back the WebGL context it took, so a Retry doesn't stack them
  const made = [];
  try { return await build(stage, opts, made); }
  catch (e) {
    made.forEach(r => { try { r.dispose(); r.forceContextLoss(); r.domElement.remove(); } catch (_) { /* already gone */ } });
    throw e;
  }
}

async function build(stage, opts, made) {
  // the badges draw their numerals in Plex Mono; give the face a moment to arrive (it is on the page)
  try {
    if (document.fonts && document.fonts.load) {
      await Promise.race([document.fonts.load('500 64px "IBM Plex Mono"', '0123456789'), new Promise(r => setTimeout(r, 1500))]);
    }
  } catch (e) { /* the fallback mono is fine */ }

  /* ---------- renderer, scene, lights: as three-d-stage.js ---------- */
  const renderer = new THREE.WebGLRenderer({
    logarithmicDepthBuffer: true, antialias: true, alpha: true,
    preserveDrawingBuffer: !!opts.preserveDrawingBuffer
  });
  made.push(renderer);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;       // PCFSoftShadowMap is deprecated in r184 and falls back to this
  renderer.shadowMap.autoUpdate = false;              // the scene only moves when it explodes: redraw shadows then
  renderer.setClearColor(0x000000, 0);                // the stage's paper shows through
  const canvas = renderer.domElement;
  canvas.className = 'm3-canvas';
  canvas.tabIndex = 0;
  canvas.setAttribute('role', 'img');
  canvas.setAttribute('aria-label', opts.label || '3D model of Camp Washington Skatepark. Arrow keys orbit, plus and minus zoom, 0 resets the view.');
  if (opts.describedBy) canvas.setAttribute('aria-describedby', opts.describedBy);
  stage.insertBefore(canvas, stage.firstChild);

  const scene = new THREE.Scene();
  const cam = new THREE.PerspectiveCamera(45, 1, 0.01, 500);
  cam.position.set(3, 2.2, 4);
  const controls = new OrbitControls(cam, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;

  scene.add(new THREE.HemisphereLight(0xffffff, 0xd8d2c4, 1.0));
  const key = new THREE.DirectionalLight(0xffffff, 2.2);
  key.position.set(4, 7, 5);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.bias = -0.0002;
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xfff4e6, 0.5);
  fill.position.set(-5, 3, -4);
  scene.add(fill);
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(200, 200), new THREE.ShadowMaterial({ opacity: 0.18 }));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  /* ---------- the model (setObject) ---------- */
  const { group, features, terrainY, X, Z, PLAN, parkRoot = group } = buildSkatepark(THREE);
  group.traverse(o => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  const box = new THREE.Box3().setFromObject(group);
  if (!box.isEmpty()) ground.position.y = box.min.y;
  scene.add(group);
  group.updateMatrixWorld(true);

  /* ---------- views, shadows (skatepark-model.js) ---------- */
  const Y_UP = new THREE.Vector3(0, 1, 0), parkRot = parkRoot === group ? 0 : parkRoot.rotation.y;
  const PC = parkRoot.localToWorld(new THREE.Vector3(X(496.9), 0, Z(640)));                        // park centre in the site
  const AXON = new THREE.Vector3(-1.1537, 1.05, -0.9952).normalize().applyAxisAngle(Y_UP, parkRot);   // exploded-axon view direction
  const HOME = new THREE.Vector3(-39.06, 30, -33.69).applyAxisAngle(Y_UP, parkRot);                  // default orbit offset
  const baseFov = cam.fov;
  {
    key.shadow.normalBias = 0; key.shadow.bias = -0.0005;
    const sc = key.shadow.camera, dir = key.position.clone().sub(key.target.position).normalize(), c = new THREE.Vector3(-5, 0, -18);
    key.target.position.copy(c); key.target.updateMatrixWorld(); scene.add(key.target);
    key.position.copy(c).addScaledVector(dir, 160); key.updateMatrixWorld();
    sc.left = sc.bottom = -135; sc.right = sc.top = 135; sc.near = 1; sc.far = 400; sc.updateProjectionMatrix();
    const size = Math.min(4096, (renderer.capabilities.maxTextureSize || 4096) / 2);
    key.shadow.mapSize.set(size, size);
    group.traverse(o => { if (o.isMesh && FLAT.test(o.name)) o.castShadow = false; });
  }

  const ids = Object.keys(features).filter(k => features[k].userData.n).sort((a, b) => features[a].userData.n - features[b].userData.n);
  const layerOfN = {}; ids.forEach(k => { layerOfN[features[k].userData.n] = features[k].userData.layer; });

  /* ---------- numbered callouts: screen-constant badges on ink pins ---------- */
  const pinsGroup = new THREE.Group(); pinsGroup.name = 'callouts'; parkRoot.add(pinsGroup);
  const pins = []; let badgePx = 30;
  const pinMat = new THREE.MeshBasicMaterial({ color: INK });
  PLAN.callouts.forEach(c => {
    const gy = terrainY(c.x, c.y), h = 3.4, sp = badgeSprite(c.n);
    sp.position.set(X(c.x), gy + h, Z(c.y)); pinsGroup.add(sp);
    const pin = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, h - 1.2, 8), pinMat);
    pin.position.set(X(c.x), gy + (h - 1.2) / 2, Z(c.y)); pinsGroup.add(pin);
    pins.push({ sp, pin, n: c.n, y0: sp.position.y, py0: pin.position.y, ph: h - 1.2, layer: layerOfN[c.n] ?? 0 });
  });
  if (opts.key) {
    opts.key.innerHTML = PLAN.callouts.map(c =>
      '<li><span class="m3-badge">' + c.n + '</span><span>' + escHtml(c.label) + '</span></li>').join('');
  }
  // badges keep a constant screen size (30 px assembled, 22 px exploded, as the artifact), a little
  // smaller on a narrow stage, never under 24 / 20 px
  const badgeScale = (W, H) => {
    const px = Math.max(exploded ? 20 : 24, badgePx * Math.min(1, W / 900)), f = 1 / Math.tan(cam.fov * Math.PI / 360);
    return 2 * px / (f * H);
  };

  /* ---------- leaders: dashed drops from each lifted feature to its footprint ---------- */
  const centres = {};
  ids.forEach(id => { const b = new THREE.Box3().setFromObject(features[id]), c = b.getCenter(new THREE.Vector3()); centres[id] = { x: c.x, z: c.z, base: Math.max(0, b.min.y) }; });
  {
    const c6 = PLAN.callouts.find(c => c.n === 6);
    if (centres.F6 && c6) { const w6 = parkRoot.localToWorld(new THREE.Vector3(X(c6.x), 0, Z(c6.y))); centres.F6.x = w6.x; centres.F6.z = w6.z; }
  }
  const leaders = new THREE.Group(); leaders.name = 'leaders'; leaders.visible = false; scene.add(leaders);
  const leaderMat = new THREE.LineDashedMaterial({ color: INK, dashSize: 0.5, gapSize: 0.3, transparent: true, opacity: 0.75 });
  const leaderOf = {};
  ids.forEach(id => { const l = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), new THREE.Vector3()]), leaderMat); leaders.add(l); leaderOf[id] = l; });

  let explode = 0;
  function applyExplode(e) {
    explode = e;
    Object.values(features).forEach(f => { f.position.y = LAYER_Y[f.userData.layer] * e; });
    pins.forEach(p => { const dy = LAYER_Y[p.layer] * e; p.sp.position.y = p.y0 + dy; p.pin.position.y = p.py0 + dy; p.pin.scale.y = 1; });
    leaders.visible = e > 0.02;
    ids.forEach(id => {
      const c = centres[id], pos = leaderOf[id].geometry.attributes.position;
      pos.setXYZ(0, c.x, 0.16, c.z); pos.setXYZ(1, c.x, LAYER_Y[features[id].userData.layer] * e + c.base, c.z); pos.needsUpdate = true;
      leaderOf[id].computeLineDistances();
    });
    renderer.shadowMap.needsUpdate = true;
  }

  /* ---------- HUD (north arrow + scale bar) and the Stock Ave label, in HTML over the canvas ---------- */
  const hud = el('div', 'm3-hud',
    '<div class="m3-north"><svg width="30" height="30" viewBox="-15 -15 30 30" aria-hidden="true"><circle r="13.5" fill="none" stroke="#C7C1AE" stroke-width="1"></circle>' +
    '<g class="m3-needle"><polygon points="0,-12 5,4 0,1 -5,4" fill="' + PINE + '"></polygon><polygon points="0,12 5,4 0,1 -5,4" fill="#C7C1AE"></polygon></g></svg><span>N</span></div>' +
    '<div class="m3-scale"><div class="m3-bar"><i></i><i></i><i></i></div><div class="m3-scale-lab"><span>0</span><span class="m3-len">25 FT</span></div></div>');
  hud.setAttribute('aria-hidden', 'true');
  stage.appendChild(hud);
  const needle = hud.querySelector('.m3-needle'), bar = hud.querySelector('.m3-bar'), len = hud.querySelector('.m3-len');
  const road = el('div', 'm3-road'); road.textContent = 'Stock Ave.'; road.setAttribute('aria-hidden', 'true');
  stage.appendChild(road);

  const roadC = PLAN.site && PLAN.site.centre;
  const roadSegs = roadC && roadC.slice(1).map((q, i) => new THREE.Line3(new THREE.Vector3(X(roadC[i][0]), 0.05, Z(roadC[i][1])), new THREE.Vector3(X(q[0]), 0.05, Z(q[1]))));
  const _rp = new THREE.Vector3(), _rq = new THREE.Vector3(), _rd = new THREE.Vector3(), _rt = new THREE.Vector3();
  function updateRoadLabel(W, H) {
    if (!roadSegs) return;
    let seg = roadSegs[0], bd = Infinity;
    roadSegs.forEach(s => { s.closestPointToPoint(controls.target, true, _rt); const d = _rt.distanceToSquared(controls.target); if (d < bd) { bd = d; seg = s; } });
    seg.closestPointToPoint(controls.target, true, _rp); seg.delta(_rd).normalize(); _rq.copy(_rp).addScaledVector(_rd, 10);
    const a = _rp.clone().project(cam), b = _rq.clone().project(cam);
    if (a.z > 1 || b.z > 1) { road.style.display = 'none'; return; }
    const ax = (a.x * 0.5 + 0.5) * W, ay = (-a.y * 0.5 + 0.5) * H, bx = (b.x * 0.5 + 0.5) * W, by = (-b.y * 0.5 + 0.5) * H;
    if (ax < 40 || ax > W - 40 || ay < 20 || ay > H - 20 || Math.hypot(bx - ax, by - ay) < 14) { road.style.display = 'none'; return; }
    let ang = Math.atan2(by - ay, bx - ax) * 180 / Math.PI; if (ang > 90) ang -= 180; if (ang < -90) ang += 180;
    road.style.display = 'block';
    road.style.transform = 'translate(' + ax.toFixed(1) + 'px,' + ay.toFixed(1) + 'px) translate(-50%,-50%) rotate(' + ang.toFixed(1) + 'deg)';
  }
  // true north is +Z (plan +y in the site frame); the bar is 5/10/25/50/100/200 ft, whichever spans ~70-160 px at the orbit target
  const _a = new THREE.Vector3(), _b = new THREE.Vector3(), _r = new THREE.Vector3(), _n = new THREE.Vector3(0, 0, 5);
  function updateHud(W, H) {
    const t = controls.target, scr = v => [(v.x * 0.5 + 0.5) * W, (-v.y * 0.5 + 0.5) * H];
    const p0 = scr(_a.copy(t).project(cam)), pn = scr(_b.copy(t).add(_n).project(cam));
    needle.setAttribute('transform', 'rotate(' + (Math.atan2(pn[0] - p0[0], -(pn[1] - p0[1])) * 180 / Math.PI).toFixed(1) + ')');
    _r.setFromMatrixColumn(cam.matrixWorld, 0).setY(0).normalize();
    const p1 = scr(_b.copy(t).addScaledVector(_r, 0.3048).project(cam)), pxPerFt = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
    if (!(pxPerFt > 0)) return;
    const ft = [5, 10, 25, 50, 100, 200].find(v => v * pxPerFt >= 70) || 200;
    bar.style.width = Math.min(160, ft * pxPerFt).toFixed(0) + 'px';
    len.textContent = ft + ' FT';
  }

  /* ---------- views ---------- */
  let exploded = false, anim = null, spin = null, dirty = true, running = false, last = 0, atHome = false;
  function limits() {
    controls.minDistance = exploded ? 120 : 12;
    controls.maxDistance = (exploded ? 900 : 240) * fit();
    controls.maxPolarAngle = Math.PI * 0.48;           // stay above the ground plane
  }
  // the artifact's views are framed for a landscape stage; a narrower one (a phone) steps back so
  // the park keeps its width on screen
  const fit = () => cam.aspect >= 1.6 ? 1 : Math.pow(1.6 / cam.aspect, 0.8);
  function homeView() {
    spin = null;
    // drop any drag momentum still coasting, or the camera would drift off the home view
    const damp = controls.enableDamping; controls.enableDamping = false; controls.update(); controls.enableDamping = damp;
    const k = fit();
    if (exploded) { cam.fov = 9; controls.target.copy(PC).setY(9); cam.position.copy(controls.target).addScaledVector(AXON, 330 * k); }
    else { cam.fov = baseFov; controls.target.copy(PC); cam.position.copy(PC).addScaledVector(HOME, k); }
    cam.near = 0.5; cam.far = 1200; cam.updateProjectionMatrix();
    limits();
    controls.update();
    atHome = true;
    dirty = true;
  }
  function setExploded(on, o = {}) {
    on = !!on;
    spin = null;
    if (on === exploded && !o.force) { homeView(); return; }
    exploded = on; badgePx = on ? 22 : 30;
    homeView();
    const to = on ? 1 : 0;
    if (o.instant || reducedMotion()) { anim = null; applyExplode(to); }
    else anim = { from: explode, to, t0: performance.now() };
    dirty = true;
  }

  // keyboard and buttons move the camera at once (no motion on keyboard actions): orbit about the target
  const _off = new THREE.Vector3(), _sph = new THREE.Spherical();
  function orbit(dTheta, dPhi) {
    spin = null; atHome = false;
    _off.copy(cam.position).sub(controls.target); _sph.setFromVector3(_off);
    _sph.theta += dTheta;
    _sph.phi = Math.min(controls.maxPolarAngle, Math.max(0.05, _sph.phi + dPhi));
    _off.setFromSpherical(_sph); cam.position.copy(controls.target).add(_off);
    controls.update(); dirty = true;
  }
  function zoom(f) {
    spin = null; atHome = false;
    _off.copy(cam.position).sub(controls.target);
    const d = Math.min(controls.maxDistance, Math.max(controls.minDistance, _off.length() * f));
    cam.position.copy(controls.target).add(_off.setLength(d));
    controls.update(); dirty = true;
  }
  canvas.addEventListener('keydown', e => {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key;
    if (k === 'ArrowLeft') orbit(ORBIT_STEP, 0);             // the model turns the way the arrow points, as a drag would
    else if (k === 'ArrowRight') orbit(-ORBIT_STEP, 0);
    else if (k === 'ArrowUp') orbit(0, TILT_STEP);
    else if (k === 'ArrowDown') orbit(0, -TILT_STEP);
    else if (k === '+' || k === '=') zoom(1 / ZOOM_STEP);
    else if (k === '-' || k === '_') zoom(ZOOM_STEP);
    else if (k === '0') homeView();
    else return;
    e.preventDefault();
  });
  controls.addEventListener('start', () => { spin = null; atHome = false; });
  controls.addEventListener('change', () => { dirty = true; });

  /* ---------- size and the render loop (draws only when something changed) ---------- */
  let W = 0, H = 0;
  function resize() {
    const w = stage.clientWidth, h = stage.clientHeight;
    if (!w || !h) return;
    if (w === W && h === H) return;
    W = w; H = h;
    renderer.setSize(w, h);
    cam.aspect = w / h; cam.updateProjectionMatrix();
    // a camera still on its home view is reframed for the new shape (a phone turned on its side);
    // one the reader has moved stays where they left it. The opening turn runs on the assembled home
    // view (offset HOME x fit()), so mid-turn only its offset is rescaled and it lands on the new framing
    if (atHome) {
      if (spin) { spin.offset.copy(HOME).multiplyScalar(fit()); limits(); }
      else homeView();
    }
    dirty = true;
  }
  const ro = new ResizeObserver(resize);
  ro.observe(stage);

  // On a stage under 900 px wide, where the badges are at their smallest, the exploded axon would
  // draw badge 6 (street plaza) over 4 (hip bank): there 6, a layer higher, rides a longer pin. Wider
  // stages, still() at its usual sizes and the render scripts (which skip draw()) keep the plain pins.
  const NARROW_LIFT = { 6: 7 };                        // extra pin (m) at full explode
  function liftPins(narrow) {
    pins.forEach(p => {
      if (!NARROW_LIFT[p.n]) return;
      const dy = LAYER_Y[p.layer] * explode, up = narrow ? NARROW_LIFT[p.n] * explode : 0;
      p.sp.position.y = p.y0 + dy + up; p.pin.position.y = p.py0 + dy + up / 2; p.pin.scale.y = 1 + up / p.ph;
    });
  }
  function draw(w, h) {
    const s = badgeScale(w, h); pins.forEach(p => p.sp.scale.set(s, s, 1));
    liftPins(w < 900);
    renderer.render(scene, cam);
  }
  function frame(now) {
    const dt = last ? Math.min(0.1, (now - last) / 1000) : 0; last = now;
    if (spin) {
      const x = Math.min(1, (now - spin.t0) / SPIN_MS), a = SPIN_FROM * (1 - easeInOut(x));
      cam.position.copy(controls.target).add(_off.copy(spin.offset).applyAxisAngle(Y_UP, a));
      if (x >= 1) spin = null;
      dirty = true;
    }
    if (anim) {
      const x = Math.min(1, (now - anim.t0) / EXPLODE_MS);
      applyExplode(anim.from + (anim.to - anim.from) * easeOut(x));
      if (x >= 1) anim = null;
      dirty = true;
    }
    if (controls.update(dt)) dirty = true;
    if (dirty && W && H) {
      dirty = false;
      draw(W, H);
      updateHud(W, H);
      updateRoadLabel(W, H);
    }
  }
  function resume() {
    running = true; last = 0; resize(); dirty = true;
    renderer.setAnimationLoop(frame);
  }
  function pause() { running = false; renderer.setAnimationLoop(null); }

  /* ---------- stills: render a frame at any size, off the visible canvas size ----------
     o: {width, height (CSS px), scale (pixel ratio), view: 'assembled'|'exploded' (its home view),
         camera: {position, target, fov} (applied after view), callouts (default true),
         background (CSS colour; default transparent), type, quality}. Returns a data URL; the
         live view is put back afterwards. viewer.lastStill holds the camera it used. */
  let lastStill = null;
  function still(o = {}) {
    const w = +o.width || 1600, h = +o.height || 900, scale = +o.scale || 1;   // the image is floor(w * scale) x floor(h * scale)
    const saved = {
      exploded, explode, pr: renderer.getPixelRatio(), pos: cam.position.clone(), tgt: controls.target.clone(), fov: cam.fov,
      near: cam.near, far: cam.far, pins: pinsGroup.visible, badgePx, clear: renderer.getClearAlpha(), atHome
    };
    cam.aspect = w / h; cam.updateProjectionMatrix();
    if (o.view === 'exploded' || o.view === 'assembled') {
      exploded = o.view === 'exploded'; badgePx = exploded ? 22 : 30;
      homeView(); anim = null; applyExplode(exploded ? 1 : 0);
    }
    if (o.camera) {
      if (o.camera.fov) cam.fov = o.camera.fov;
      if (o.camera.target) controls.target.fromArray(o.camera.target);
      if (o.camera.position) cam.position.fromArray(o.camera.position);
      controls.update();
    }
    pinsGroup.visible = o.callouts !== false;
    if (o.background) renderer.setClearColor(o.background, 1);
    renderer.setPixelRatio(scale);
    renderer.setSize(w, h, false);
    cam.aspect = w / h; cam.updateProjectionMatrix();
    renderer.shadowMap.needsUpdate = true;
    draw(w, h);
    const url = canvas.toDataURL(o.type || 'image/png', o.quality);
    lastStill = { position: cam.position.toArray(), target: controls.target.toArray(), fov: cam.fov, exploded, width: w, height: h, scale };
    // put everything back for the live view
    renderer.setClearColor(0x000000, saved.clear);
    pinsGroup.visible = saved.pins;
    exploded = saved.exploded; badgePx = saved.badgePx; applyExplode(saved.explode);
    cam.fov = saved.fov; cam.near = saved.near; cam.far = saved.far;
    cam.position.copy(saved.pos); controls.target.copy(saved.tgt);
    renderer.setPixelRatio(saved.pr);
    atHome = saved.atHome;                             // so the resize below keeps a moved camera where it was
    W = H = 0; resize(); limits();
    controls.update();
    dirty = true;
    if (running) frame(performance.now()); else if (W && H) draw(W, H);
    return url;
  }

  function dispose() {
    pause(); ro.disconnect(); controls.dispose();
    scene.traverse(o => {
      if (o.geometry) o.geometry.dispose();
      const m = o.material; (Array.isArray(m) ? m : m ? [m] : []).forEach(mm => { if (mm.map) mm.map.dispose(); if (mm.alphaMap) mm.alphaMap.dispose(); mm.dispose(); });
    });
    renderer.dispose();
    [canvas, hud, road].forEach(n => n.remove());
  }

  // start on the home view (sized to the stage); the opening turn swings in from 24 degrees off
  // unless motion is reduced
  resize();
  homeView();
  applyExplode(0);
  if (opts.spin !== false && !reducedMotion()) {
    spin = { t0: performance.now(), offset: cam.position.clone().sub(controls.target) };
    cam.position.copy(controls.target).add(_off.copy(spin.offset).applyAxisAngle(Y_UP, SPIN_FROM));
    controls.update();
  }
  resume();

  return {
    setExploded, reset: homeView, orbit, zoom, pause, resume, resize, still, dispose,
    get exploded() { return exploded; },
    get lastStill() { return lastStill; },
    state: () => ({ exploded, position: cam.position.toArray(), target: controls.target.toArray(), fov: cam.fov }),
    three: { THREE, renderer, scene, camera: cam, controls, group, features, parkRoot, PLAN, pins: pinsGroup, leaders }
  };
}
