/* Project detail page: content, drawing-set viewer, master-plan lightbox, the on-demand 3D model
   dialog (CRC's skatepark), and the pdf.js capstone-booklet viewer (Proctor Landing Park). */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var clean = US.clean, esc = US.esc;

  /* ---------------- Drawing set viewer ---------------- */
  var dw = { project: null, idx: 0, pz: null, img: null, w: 0 };
  var PHONE = window.matchMedia('(max-width: 640px)');

  /* On phones the stage takes the drawing's own shape (up to 70svh, site.css) instead of a
     fixed-height letterbox, so a landscape spread isn't floating in empty paper. The drawing's
     w/h in the JSON give that shape before it arrives, so nothing below moves when it lands (and
     a late load of an earlier drawing can't re-shape the stage); without them, its own size. */
  function dwShape() {
    var stage = $('dw-stage'), cur = dw.project && dw.project.images[dw.idx], im = dw.img;
    var w = cur && +cur.w, h = cur && +cur.h;
    if (!(w && h) && im && im.naturalWidth) { w = im.naturalWidth; h = im.naturalHeight; }
    if (PHONE.matches && w && h) {
      stage.style.aspectRatio = w + ' / ' + h;
      stage.style.height = 'auto';
    } else {
      stage.style.aspectRatio = '';
      stage.style.height = '';
    }
  }

  function dwLabel(images, k) {
    var im = images[k];
    var same = images.filter(function (x) { return x.drawing_type === im.drawing_type; });
    if (same.length <= 1) return im.drawing_type;
    var nth = images.slice(0, k + 1).filter(function (x) { return x.drawing_type === im.drawing_type; }).length;
    return im.drawing_type + ' ' + nth;
  }

  function dwShow(k) {
    var p = dw.project;
    dw.idx = Math.min(Math.max(k, 0), p.images.length - 1);
    var im = p.images[dw.idx];
    var surface = $('dw-surface');
    surface.innerHTML = '';
    var img = document.createElement('img');
    img.src = 'content/projects/' + im.file;
    img.alt = clean(im.caption);
    img.draggable = false;
    img.onload = function () { dw.img = img; dwShape(); dw.pz.fit(true); };
    img.onerror = function () {
      surface.innerHTML =
        '<div style="width:900px; max-width:86vw; aspect-ratio:16/10; background:repeating-linear-gradient(45deg,#EDE9DB 0 8px,#F3F0E8 8px 16px); border:1px solid #C7C1AE; display:flex; align-items:center; justify-content:center">' +
        '<span style="font-family:var(--mono); font-size:12px; color:var(--muted); text-align:center; padding:0 20px">' +
        esc(im.drawing_type) + ' — drawing didn\u2019t load</span></div>';
    };
    surface.appendChild(img);
    dwShape();
    $('dw-num').textContent = String(dw.idx + 1).padStart(2, '0');
    $('dw-caption').textContent = clean(im.caption);
    var scale = clean(im.scale);
    $('dw-meta').textContent = [im.drawing_type, scale, clean(im.software)].filter(Boolean).join(' · ');
    dw.pz.fit(true);
    Array.prototype.forEach.call($('dw-thumbs').children, function (b, i) {
      b.classList.toggle('active', i === dw.idx);
      b.setAttribute('aria-pressed', i === dw.idx ? 'true' : 'false');
    });
  }

  function dwInit(p) {
    dw.project = p;
    $('dw-count').textContent = String(p.images.length).padStart(2, '0');
    $('dw-thumbs').innerHTML = p.images.map(function (im, k) {
      return '<button class="thumbbtn' + (k === 0 ? ' active' : '') + '" data-k="' + k + '" aria-pressed="' + (k === 0) + '">' + esc(dwLabel(p.images, k)) + '</button>';
    }).join('');
    $('dw-thumbs').addEventListener('click', function (e) {
      var b = e.target.closest('.thumbbtn');
      if (b) dwShow(parseInt(b.getAttribute('data-k'), 10));
    });
    dw.pz = US.panZoom($('dw-stage'), $('dw-surface'), $('dw-badge'), {
      prev: function () { dwShow(dw.idx - 1); },
      next: function () { dwShow(dw.idx + 1); }
    });
    $('dw-prev').onclick = function () { dwShow(dw.idx - 1); };
    $('dw-next').onclick = function () { dwShow(dw.idx + 1); };
    $('dw-zin').onclick = dw.pz.zoomIn;
    $('dw-zout').onclick = dw.pz.zoomOut;
    $('dw-fit').onclick = dw.pz.fit;
    // re-shape and re-fit when the width really changes (rotation, a resized window), not when
    // a phone's toolbar slides away mid-scroll, which would throw away the reader's zoom
    dw.w = window.innerWidth;
    function reshape() {
      if (window.innerWidth === dw.w) return;
      dw.w = window.innerWidth;
      dwShape();
      dw.pz.fit(true);
    }
    window.addEventListener('resize', reshape);
    window.addEventListener('orientationchange', reshape);
    dwShow(0);
  }

  /* ---------------- Master plan lightbox ---------------- */
  function mpInit() {
    var pz = US.panZoom($('mp-stage'), $('mp-surface'), $('mp-badge'));
    var opener = null;
    // an aria-modal dialog takes focus, locks the page behind it and gives focus back on close;
    // opened from the keyboard (click detail 0), it appears without its entrance
    function open(e) {
      opener = document.activeElement;
      $('mp-box').toggleAttribute('data-instant', !!(e && e.detail === 0));
      $('mp-box').hidden = false;
      document.body.style.overflow = 'hidden';
      pz.fit(true);
      $('mp-close').focus();
    }
    function close() {
      if ($('mp-box').hidden) return;
      $('mp-box').hidden = true;
      document.body.style.overflow = '';
      if (opener && opener.focus) opener.focus();
    }
    $('mp-open').onclick = open;
    $('mp-inline').onclick = open;   // a mouse shortcut; the Fullscreen button is its keyboard twin
    $('mp-close').onclick = close;
    $('mp-box').addEventListener('click', function (e) { if (e.target === $('mp-box')) close(); });
    document.addEventListener('keydown', function (e) {
      if ($('mp-box').hidden) return;
      if (e.key === 'Escape') close();
      if (e.key === 'Tab') {   // Tab cycles the box's buttons and its focusable stage
        var f = $('mp-box').querySelectorAll('button, [tabindex="0"]');
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
        else if (!$('mp-box').contains(document.activeElement)) { e.preventDefault(); first.focus(); }
      }
    });
    $('mp-zin').onclick = pz.zoomIn;
    $('mp-zout').onclick = pz.zoomOut;
    $('mp-fit').onclick = pz.fit;
  }

  /* ---------------- 3D model (on demand) ----------------
     A project whose JSON has a "model3d" entry shows its poster still and an "Open the 3D model"
     button. Nothing of three.js or the model loads until that click: the module the entry names
     is imported then and mounted in the #m3-box dialog, which behaves like the master-plan box
     (focus moves in, Tab cycles, Escape or Close hands focus back, the page behind stops
     scrolling), so the canvas can keep every gesture without trapping the page's scroll. While
     the dialog is closed the viewer stops drawing. Without WebGL 2 the poster stays and says so. */
  var m3 = { cfg: null, viewer: null, loading: false, opener: null, gl: null, tries: 0 };

  function m3HasWebGL() {
    if (m3.gl !== null) return m3.gl;
    var ok = false;
    try {
      var gl = document.createElement('canvas').getContext('webgl2');   // three.js r163+ needs WebGL 2
      if (gl) {
        ok = true;
        var lose = gl.getExtension('WEBGL_lose_context');
        if (lose) lose.loseContext();
      }
    } catch (e) { ok = false; }
    m3.gl = ok;
    return ok;
  }

  function m3Status(msg, retry) {
    $('m3-status').hidden = !msg;
    $('m3-retry').hidden = !retry;
    // set the text a frame after the overlay shows, so the status is announced
    requestAnimationFrame(function () { $('m3-status-text').textContent = msg || ''; });
  }

  function m3Sync() {
    var on = !!(m3.viewer && m3.viewer.exploded);
    $('m3-axon').setAttribute('aria-pressed', on ? 'true' : 'false');
    $('m3-axon').classList.toggle('active', on);
    $('m3-axon').disabled = $('m3-reset').disabled = !m3.viewer;
  }

  /* A failed import() is remembered for the page's life, for every file in the module's graph, so
     the files are fetched first (which also fills the HTTP cache the import then reads): a network
     failure stops here, where Retry can try again, and import() only runs once they all arrived. */
  function m3Preflight(url, seen) {
    if (seen[url]) return Promise.resolve();
    seen[url] = 1;
    return fetch(url).then(function (r) {
      if (!r.ok) throw new Error('missing ' + url);
      return r.text();
    }).then(function (src) {
      var deps = [], re = /(?:\bfrom\s*|\bimport\s*)["'](\.{0,2}\/[^"']+)["']/g, m;
      while ((m = re.exec(src))) deps.push(new URL(m[1], url).href);
      return Promise.all(deps.map(function (d) { return m3Preflight(d, seen); }));
    });
  }

  function m3Load() {
    if (m3.loading || m3.viewer) return;
    m3.loading = true;
    m3Status('Loading the 3D model…');
    // the poster holds the stage while the model loads (the page already has it)
    $('m3-stage-poster').src = $('m3-poster-img').currentSrc || $('m3-poster-img').src;
    $('m3-stage-poster').hidden = false;
    var url = new URL(m3.cfg.module, location.href).href;
    if (m3.tries) url += '?retry=' + m3.tries;   // in case an import() itself failed
    m3Preflight(url, {}).then(function () {
      return import(url);
    }).then(function (mod) {
      return mod.mount($('m3-stage'), {
        key: $('m3-key'),
        label: clean(m3.cfg.label) || undefined,
        describedBy: 'm3-key-title m3-key'
      });
    }).then(function (v) {
      m3.viewer = v;
      m3.loading = false;
      $('m3-stage-poster').hidden = true;
      m3Status('');
      m3Sync();
      if ($('m3-box').hidden) v.pause();
    }, function (e) {
      m3.loading = false;
      m3.tries++;
      console.warn('3D model failed', e && (e.message || e));
      Array.prototype.forEach.call($('m3-stage').querySelectorAll('.m3-canvas, .m3-hud, .m3-road'), function (n) { n.remove(); });
      $('m3-key').innerHTML = '';
      m3Status('The 3D model didn’t load.', true);
    });
  }

  function m3Open(e) {
    if (!m3.viewer && !m3HasWebGL()) {
      $('m3-msg').textContent = 'The 3D view isn’t available on this device. The still below shows the model.';
      return;
    }
    // the poster is a mouse shortcut; focus comes back to the button, its keyboard twin
    m3.opener = e && e.currentTarget === $('m3-poster') ? $('m3-open') : document.activeElement;
    $('m3-box').toggleAttribute('data-instant', !!(e && e.detail === 0));
    $('m3-box').hidden = false;
    document.body.style.overflow = 'hidden';
    $('m3-close').focus();
    if (m3.viewer) m3.viewer.resume();
    else m3Load();
  }

  function m3Close() {
    if ($('m3-box').hidden) return;
    $('m3-box').hidden = true;
    document.body.style.overflow = '';
    if (m3.viewer) m3.viewer.pause();
    if (m3.opener && m3.opener.focus) m3.opener.focus();
  }

  function m3Init(c) {
    m3.cfg = c;
    var title = clean(c.title), poster = c.poster || {}, logo = c.logo || {};
    $('m3-section').hidden = false;
    $('m3-title').textContent = title;
    $('m3-sub').textContent = clean(c.subtitle);
    $('m3-sheet').textContent = clean(c.sheet);
    $('m3-box-sheet').textContent = clean(c.sheet);
    $('m3-box-title').textContent = title;
    var img = $('m3-poster-img');
    if (+poster.w > 0 && +poster.h > 0) { img.width = +poster.w; img.height = +poster.h; }   // holds its box before it arrives
    img.alt = clean(poster.alt);
    img.onerror = function () { US.imgPh(img, 'poster'); };
    img.src = 'content/projects/' + poster.file;
    if (logo.file) {
      $('m3-logo').src = logo.file;
      if (+logo.w > 0 && +logo.h > 0) { $('m3-logo').width = +logo.w; $('m3-logo').height = +logo.h; }
      $('m3-logo').hidden = false;
    }
    $('m3-credit').textContent = clean(c.credit);
    $('m3-note').textContent = clean(c.note);
    $('m3-box-credit').textContent = [clean(c.credit), clean(c.note)].filter(Boolean).join(' ');
    $('m3-open').onclick = m3Open;
    $('m3-poster').onclick = m3Open;
    $('m3-close').onclick = m3Close;
    $('m3-retry').onclick = m3Load;
    $('m3-axon').onclick = function (e) {
      if (!m3.viewer) return;
      // a keyboard press (click detail 0) changes the view at once, without the lift
      m3.viewer.setExploded(!m3.viewer.exploded, { instant: e.detail === 0 });
      m3Sync();
    };
    $('m3-reset').onclick = function () { if (m3.viewer) m3.viewer.reset(); };
    $('m3-box').addEventListener('click', function (e) { if (e.target === $('m3-box')) m3Close(); });
    document.addEventListener('keydown', function (e) {
      if ($('m3-box').hidden) return;
      if (e.key === 'Escape') { m3Close(); return; }
      if (e.key === 'Tab') {   // Tab cycles the dialog's buttons and the model's canvas
        var f = Array.prototype.filter.call($('m3-box').querySelectorAll('button, [tabindex="0"]'), function (n) {
          return !n.disabled && n.getClientRects().length;
        });
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
        else if (!$('m3-box').contains(document.activeElement)) { e.preventDefault(); first.focus(); }
      }
    });
  }

  /* ---------------- Capstone booklet (pdf.js) ----------------
     The booklet is 11 PDF parts (assets/booklet/Part_NN.pdf); parts.json lists their page
     counts, so the page total and the scrubber are known before any PDF loads. Nothing heavy
     loads until the reader nears the viewer (or presses Open booklet, an arrow or the
     scrubber). Then only the part holding the current spread is fetched; once that spread is
     on screen the next part is prefetched, one fetch at a time. */
  var bk = {
    started: false, parts: null, offsets: [], total: 0, idx: 0,
    docs: [], job: null, wait: {}, skip: {},
    pages: [], tiers: {}, rendering: {}, retried: {},
    renderQ: Promise.resolve(), manifestP: null,
    lib: null, libP: null, libTries: 0, pz: null, io: null, failed: false
  };
  var PDFJS = new URL('assets/vendor/pdfjs/pdf.min.mjs', location.href).href;
  var PDFJS_WORKER = new URL('assets/vendor/pdfjs/pdf.worker.min.mjs', location.href).href;

  function bkFile(i) { return 'assets/booklet/Part_' + String(i + 1).padStart(2, '0') + '.pdf'; }
  function pad2(n) { return String(n).padStart(2, '0'); }

  function bkUnits() {
    var t = bk.total, u = [];
    if (t) { u.push([0]); for (var i = 1; i < t; i += 2) u.push(i + 1 < t ? [i, i + 1] : [i]); }
    return u;
  }

  function bkStatus(msg) { $('bk-status').textContent = msg || ''; }

  function bkManifest() {
    if (bk.manifestP) return bk.manifestP;
    bk.manifestP = US.getJSON('assets/booklet/parts.json').then(function (m) {
      var parts = (m && m.parts) || [];
      if (!parts.length || parts.some(function (n) { return !(n > 0); })) throw new Error('bad booklet manifest');
      var t = 0;
      bk.offsets = parts.map(function (n) { var o = t; t += n; return o; });
      bk.parts = parts;
      bk.total = t;
      $('bk-count').textContent = pad2(t);
      $('bk-scrub').max = Math.max(bkUnits().length - 1, 0);
      if (!bk.started) $('bk-num').textContent = pad2(1);
      return bk.parts;
    }).catch(function (e) {
      bk.manifestP = null;   // let Retry ask again
      throw e;
    });
    return bk.manifestP;
  }

  // page index (0-based, whole booklet) -> part index
  function bkPartOf(i) {
    for (var p = bk.parts.length - 1; p > 0; p--) if (i >= bk.offsets[p]) return p;
    return 0;
  }

  // a failed import() stays failed for that URL, so a retry asks for a fresh one
  function bkLib() {
    if (bk.lib) return Promise.resolve(bk.lib);
    if (!bk.libP) {
      bk.libP = import(PDFJS + (bk.libTries ? '?retry=' + bk.libTries : '')).then(function (lib) {
        lib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;
        bk.lib = lib;
        return lib;
      }, function (e) {
        bk.libP = null;
        bk.libTries = (bk.libTries || 0) + 1;
        throw e;
      });
    }
    return bk.libP;
  }

  /* Choose the one part to fetch next: the current spread's first missing part, or, once
     that spread is on screen, the part after it. A fetch in flight that the current spread
     doesn't need gives way when the reader jumps to a spread that needs a different part. */
  function bkPump() {
    if (!bk.started || !bk.parts || bk.failed) return;
    var unit = bkUnits()[bk.idx] || [];
    var need = [];
    unit.forEach(function (o) {
      var p = bkPartOf(o);
      if (!bk.docs[p] && need.indexOf(p) < 0) need.push(p);
    });
    var want = null, prefetch = false;
    if (need.length) {
      want = need[0];
    } else if (unit.length && unit.every(function (o) { return bk.pages[o]; })) {
      var nx = bkPartOf(unit[unit.length - 1]) + 1;
      if (nx < bk.parts.length && !bk.docs[nx] && !bk.skip[nx]) { want = nx; prefetch = true; }
    }
    var job = bk.job;
    if (job) {
      if (job.part === want) { if (!prefetch) job.prefetch = false; return; }
      if (want === null || prefetch || need.indexOf(job.part) >= 0 || !job.ctrl) return;
      job.aborted = true;
      job.ctrl.abort();
      bk.job = null;
    }
    if (want === null || bk.wait[want]) return;
    bkFetchPart(want, prefetch);
  }

  function bkFetchPart(part, prefetch) {
    var ctrl = window.AbortController ? new AbortController() : null;
    var job = { part: part, prefetch: prefetch, ctrl: ctrl, aborted: false };
    var libFailed = false;
    bk.job = job;
    bkStatus('Loading part ' + (part + 1) + ' / ' + bk.parts.length);
    bkLib().catch(function (e) { libFailed = true; throw e; }).then(function () {
      return fetch(bkFile(part), ctrl ? { signal: ctrl.signal } : undefined);
    }).then(function (r) {
      if (!r.ok) throw new Error('missing part ' + (part + 1));
      return r.arrayBuffer();
    }).then(function (buf) {
      return bk.lib.getDocument({ data: buf }).promise;
    }).then(function (doc) {
      if (doc.numPages !== bk.parts[part]) {
        console.warn('booklet part ' + (part + 1) + ' has ' + doc.numPages + ' pages; parts.json says ' + bk.parts[part]);
      }
      bk.docs[part] = doc;
      done('');
    }, function (e) {
      if (job.aborted) return;
      console.warn(libFailed ? 'booklet library failed' : 'booklet part failed', e && (e.message || e));
      var key = 'part:' + part;
      if (libFailed) {
        bk.failed = true;
      } else if (!bk.retried[key]) {
        bk.retried[key] = true;           // one quiet retry after 3 s
        bk.wait[part] = true;
        setTimeout(function () { delete bk.wait[part]; bkPump(); }, 3000);
      } else if (job.prefetch) {
        bk.skip[part] = true;             // stop prefetching it; fetched again when needed
      } else {
        bk.failed = true;
      }
      done(libFailed ? 'Could not load the booklet viewer' : '');
    });
    function done(msg) {
      if (bk.job !== job) return;
      bk.job = null;
      bkStatus(msg);
      bkEnsureCurrent();
      bkPaint();
    }
  }

  function bkStart() {
    if (bk.started) return;
    bk.started = true;
    if (bk.io) { bk.io.disconnect(); bk.io = null; }
    $('bk-intro').hidden = true;
    bkPaint();
    bkManifest().then(function () {
      bkGo(bk.idx);
    }, function (e) {
      console.warn('booklet manifest failed', e && (e.message || e));
      bk.failed = true;
      bkPaint();
    });
  }

  function bkPageCanvas(i, w) {
    var part = bkPartOf(i), doc = bk.docs[part];
    if (!doc) return Promise.reject(new Error('page ' + (i + 1) + ' not loaded yet'));
    return doc.getPage(i - bk.offsets[part] + 1).then(function (page) {
      var vp0 = page.getViewport({ scale: 1 });
      var vp = page.getViewport({ scale: w / vp0.width });
      var c = document.createElement('canvas');
      c.width = Math.round(vp.width); c.height = Math.round(vp.height);
      return page.render({ canvasContext: c.getContext('2d'), viewport: vp }).promise.then(function () { return c; });
    });
  }

  function bkRender(i, targetW) {
    var w = targetW || 1500;
    var key = i + ':' + w;
    if (bk.rendering[key]) return;
    if ((bk.tiers[i] || 0) >= w) return;
    bk.rendering[key] = true;
    bk.renderQ = bk.renderQ.then(function () {
      if (!bk.docs[bkPartOf(i)]) { delete bk.rendering[key]; return; }
      return bkPageCanvas(i, w).then(function (c) {
        bk.tiers[i] = w;
        bk.pages[i] = c.toDataURL('image/jpeg', 0.85);
        bk.failed = false;
        delete bk.rendering[key];
        if (!bk.job) bkStatus('');
        bkPaint();
        bkPump();   // the spread is on screen: time to prefetch the next part
      }).catch(function (e) {
        delete bk.rendering[key];
        console.warn('booklet render failed', e && (e.message || e));
        if (!bk.retried[key]) {
          bk.retried[key] = true;
          setTimeout(function () { if (!bk.pages[i]) bkRender(i, targetW); }, 3000);
        } else {
          bk.failed = true;
          bkPaint();
        }
      });
    });
  }

  // render the current spread and the next one from the parts already loaded, then fetch
  function bkEnsureCurrent() {
    if (!bk.parts) return;
    var units = bkUnits();
    var cur = (units[bk.idx] || []).concat(units[bk.idx + 1] || []);
    cur.forEach(function (o) { if (!bk.pages[o] && bk.docs[bkPartOf(o)]) bkRender(o); });
    bkPump();
  }

  function bkAwardsPanel() {
    var el = document.createElement('div');
    el.style.cssText = 'height:100%; max-width:50%; aspect-ratio:612/792; background:#FFF; box-sizing:border-box; padding:5.5% 5%; display:flex; flex-direction:column; overflow:hidden; color:#26292B; font-family:var(--sans)';
    function row(src, name, desc, h) {
      return '<div style="display:flex; align-items:center; gap:16px">' +
        '<div style="width:150px; display:flex; justify-content:center; flex-shrink:0">' +
        '<img src="' + src + '" alt="' + esc(name) + '" style="height:' + h + '; width:auto; max-width:150px; object-fit:contain; mix-blend-mode:multiply; display:block"></div>' +
        '<div><div style="font-weight:600; font-size:14.5px">' + esc(name) + '</div>' +
        '<div style="font-size:12.5px; color:#5A5F58; margin-top:2px; line-height:1.45">' + esc(desc) + '</div></div></div>';
    }
    el.innerHTML =
      '<div style="font-family:var(--mono); font-size:10px; letter-spacing:.16em; text-transform:uppercase; color:#2E4A3B">Proctor Landing Park · MLA Capstone</div>' +
      '<div class="h-head" style="font-size:clamp(20px,5.5vw,30px); margin-top:10px">Recognition</div>' +
      '<div style="border-top:1px solid #26292B; margin-top:14px"></div>' +
      '<div style="display:flex; flex-direction:column; gap:20px; margin-top:24px">' +
      row('assets/logos/daapworks.jpg', 'DAAPWorks 2026', "Director's Choice Gallery selection", '100px') +
      row('assets/logos/segd.png', 'SEGD', 'Experiential design honorable mention', '68px') +
      row('assets/logos/design-museum-chicago.png', 'Design Museum of Chicago', 'Selected for DAAPworks exhibition', '100px') +
      '</div>';
    return el;
  }

  function bkPaint() {
    if (!bk.started) return;
    var units = bkUnits();
    var unit = units[bk.idx] || [];
    var shown = unit.filter(function (o) { return bk.pages[o]; });
    var surface = $('bk-surface');
    surface.innerHTML = '';
    var hasPage = shown.length > 0;
    if (hasPage) {
      var box = document.createElement('div');
      box.style.cssText = 'width:100%; height:100%; display:flex; align-items:center; justify-content:center';
      if (bk.idx === 0) box.appendChild(bkAwardsPanel());
      shown.forEach(function (o) {
        var img = document.createElement('img');
        img.src = bk.pages[o];
        img.alt = 'Booklet page ' + (o + 1);
        img.draggable = false;
        img.style.cssText = 'max-width:' + ((unit.length === 2 || bk.idx === 0) ? '50%' : '100%') +
          '; max-height:100%; width:auto; height:auto; display:block; border:none; background:#FFF; user-select:none';
        box.appendChild(img);
      });
      surface.appendChild(box);
    }
    $('bk-loading').hidden = !(bk.started && !hasPage && !bk.failed);
    $('bk-failedbox').hidden = !(bk.started && !hasPage && bk.failed);
    $('bk-num').textContent = unit.length ? unit.map(function (o) { return pad2(o + 1); }).join('–') : '··';
  }

  function bkGo(n) {
    var units = bkUnits();
    bk.idx = Math.min(Math.max(n, 0), Math.max(units.length - 1, 0));
    bk.failed = false;
    bk.pz.fit(true);
    $('bk-scrub').value = bk.idx;
    bkEnsureCurrent();
    bkPaint();
  }

  function bkInit() {
    bk.pz = US.panZoom($('bk-stage'), $('bk-surface'), $('bk-badge'), {
      max: 8,
      enabled: function () { return bk.started; },
      prev: function () { bkStart(); bkGo(bk.idx - 1); },
      next: function () { bkStart(); bkGo(bk.idx + 1); },
      onZoom: function (z) {
        if (z > 2.2) (bkUnits()[bk.idx] || []).forEach(function (o) { bkRender(o, 3200); });
      }
    });
    $('bk-start').onclick = bkStart;
    $('bk-prev').onclick = function () { bkStart(); bkGo(bk.idx - 1); };
    $('bk-next').onclick = function () { bkStart(); bkGo(bk.idx + 1); };
    $('bk-zin').onclick = bk.pz.zoomIn;
    $('bk-zout').onclick = bk.pz.zoomOut;
    $('bk-fit').onclick = bk.pz.fit;
    $('bk-scrub').addEventListener('input', function (e) {
      bkStart();
      bkGo(parseInt(e.target.value, 10) || 0);
    });
    $('bk-retry').onclick = function () {
      bk.retried = {}; bk.skip = {}; bk.failed = false;
      bkStatus('Recovering viewer…');
      bkPaint();
      bkManifest().then(function () { bkGo(bk.idx); }, function (e) {
        console.warn('booklet manifest failed', e && (e.message || e));
        bk.failed = true;
        bkStatus('');
        bkPaint();
      });
    };
    // the page total and scrubber come from the manifest (tiny); the PDFs wait for the reader
    bkManifest().catch(function (e) { console.warn('booklet manifest failed', e && (e.message || e)); });
    if ('IntersectionObserver' in window) {
      bk.io = new IntersectionObserver(function (entries) {
        if (entries.some(function (en) { return en.isIntersecting; })) bkStart();
      }, { rootMargin: '0px 0px 200px 0px' });
      bk.io.observe($('bk-stage'));
    } else {
      bkStart();
    }
  }

  /* ---------------- Page assembly ---------------- */
  // shown elsewhere on its page: Proctor's master plan is the inline #mp-section sheet,
  // Evans's two concept plans are the compare slider's views (the same drawings, cut wider), and
  // CRC's numbered skatepark axon is the 3D panel's poster view (the blowout, with no poster, keeps it)
  var ON_PAGE = {
    'proctor-landing-park/master-plan.webp': 1,
    'evans-park/g-h23_f1.webp': 1,
    'evans-park/g-h25_f1.webp': 1,
    'cincinnati-recreation-commission/g-cw-axon.webp': 1
  };
  function projectFigures(p) {
    var seen = {};
    (p.images || []).concat(p.models || [], (p.compare && p.compare.views) || []).forEach(function (x) { seen[x.file] = 1; });
    return (p.gallery || []).filter(function (g) { return g && g.file && !seen[g.file] && !ON_PAGE[g.file]; });
  }

  function metaCell(label, value, mono) {
    return '<div><div class="meta-label">' + esc(label) + '</div>' +
      '<div class="meta-value' + (mono ? ' mono' : '') + '">' + esc(value) + '</div></div>';
  }

  /* Compare slider: views share one frame (aligned upstream), the right-hand image is
     clipped at the divider. With three or more views each side gets a picker. */
  function cmpInit(c) {
    var views = c.views, ia = c.start ? c.start[0] : 0, ib = c.start ? c.start[1] : 1;
    var stage = $('cmp-stage'), cut = 50, drag = false;
    $('cmp-section').hidden = false;
    $('cmp-title').textContent = clean(c.title) || 'Compare';
    $('cmp-note').textContent = clean(c.note);
    function setCut(v) {
      cut = Math.max(0, Math.min(100, v));
      stage.style.setProperty('--cut', cut + '%');
      $('cmp-range').value = cut;
      $('cmp-range').setAttribute('aria-valuetext', Math.round(100 - cut) + '% ' + clean(views[ib].label));
    }
    function apply() {
      var a = views[ia], b = views[ib];
      // the stage holds the plan's shape before the images arrive (w/h in the JSON)
      if (a.w && a.h) {
        stage.style.aspectRatio = a.w + ' / ' + a.h;
        $('cmp-section').style.setProperty('--ar', a.w / a.h);   // site.css sizes the stage to the screen
      }
      $('cmp-img-a').src = 'content/projects/' + a.file;
      $('cmp-img-a').alt = clean(a.caption || a.label);
      $('cmp-img-b').src = 'content/projects/' + b.file;
      $('cmp-img-b').alt = clean(b.caption || b.label);
      $('cmp-tag-a').textContent = clean(a.label);
      $('cmp-tag-b').textContent = clean(b.label);
      $('cmp-caption').textContent = 'Left: ' + clean(a.caption || a.label) + ' \u00b7 Right: ' + clean(b.caption || b.label);
      ['a', 'b'].forEach(function (side) {
        var me = side === 'a' ? ia : ib, other = side === 'a' ? ib : ia;
        Array.prototype.forEach.call($('cmp-chips-' + side).children, function (btn, i) {
          btn.setAttribute('aria-pressed', i === me ? 'true' : 'false');
          btn.disabled = i === other;
        });
      });
      setCut(cut);
    }
    if (views.length > 2) {
      $('cmp-pick').hidden = false;
      ['a', 'b'].forEach(function (side) {
        $('cmp-chips-' + side).innerHTML = views.map(function (v, i) {
          return '<button type="button" data-i="' + i + '">' + esc(clean(v.label)) + '</button>';
        }).join('');
        $('cmp-chips-' + side).addEventListener('click', function (e) {
          var btn = e.target.closest('button');
          if (!btn || btn.disabled) return;
          if (side === 'a') ia = +btn.getAttribute('data-i'); else ib = +btn.getAttribute('data-i');
          apply();
        });
      });
    }
    function fromEvent(e) {
      var r = stage.getBoundingClientRect();
      setCut((e.clientX - r.left) / r.width * 100);
    }
    stage.addEventListener('pointerdown', function (e) {
      drag = true;
      try { stage.setPointerCapture(e.pointerId); } catch (err) {}
      fromEvent(e);
    });
    stage.addEventListener('pointermove', function (e) { if (drag) fromEvent(e); });
    stage.addEventListener('pointerup', function () { drag = false; });
    stage.addEventListener('pointercancel', function () { drag = false; });
    $('cmp-range').addEventListener('input', function (e) { setCut(parseFloat(e.target.value)); });
    // the arrow keys move the divider 5% a press (Shift: 1%); Page Up/Down and Home/End stay native
    $('cmp-range').addEventListener('keydown', function (e) {
      var d = { ArrowLeft: -1, ArrowDown: -1, ArrowRight: 1, ArrowUp: 1 }[e.key];
      if (!d || e.altKey || e.ctrlKey || e.metaKey) return;
      e.preventDefault();
      setCut(Math.round(cut + d * (e.shiftKey ? 1 : 5)));
    });
    apply();
  }

  function renderProject(p, order) {
    document.title = p.title + ' — Understory Design Studio';
    $('p-title').textContent = p.title;
    $('p-meta').innerHTML =
      metaCell('Location', clean(p.location) || '—') +
      metaCell('Year', US.yearOf(p) || '—') +
      metaCell('Role', clean(p.role)) +
      metaCell('Scope / scale', clean(p.scale)) +
      (clean(p.collaborators) ? metaCell('Collaborators', clean(p.collaborators)) : '') +
      metaCell('Software', (p.software || []).map(clean).filter(Boolean).join(' · '), true);
    $('p-summary').textContent = clean(p.summary);

    var logos = '<div class="p-row-logos" style="display:flex; gap:34px; align-items:center; flex-wrap:wrap; margin-top:12px; border-top:1px solid var(--line); padding-top:18px">' +
      '<img src="assets/logos/daapworks.jpg" alt="DAAPWorks" title="DAAPWorks" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '<img src="assets/logos/segd.png" alt="SEGD" title="SEGD" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '<img src="assets/logos/design-museum-chicago.png" alt="Design Museum of Chicago" title="Design Museum of Chicago" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '</div>';
    // a row's one figure (CRC's Rec Finder still): its w/h hold the box, the caption sits under it
    function rowFigure(f) {
      var cap = clean(f.caption), fw = +f.w, fh = +f.h, sized = fw > 0 && fh > 0;
      return '<figure class="p-row-fig">' +
        '<img src="content/projects/' + esc(f.file) + '"' + (sized ? ' width="' + fw + '" height="' + fh + '"' : '') +
        ' alt="' + esc(clean(f.alt) || cap) + '" loading="lazy" decoding="async" onerror="US.imgPh(this,\'figure\')">' +
        (cap ? '<figcaption class="fig-cap">' + esc(cap) + '</figcaption>' : '') + '</figure>';
    }
    $('p-sections').innerHTML = (p.sections || []).map(function (s) {
      var body = clean(s.body), fig = s.figure && s.figure.file ? s.figure : null;
      if (!body && !fig) return '';
      // a row: the heading in the left third, the text (and a figure) in the right two-thirds (site.css .p-row)
      return '<div class="p-row">' +
        '<h2 class="h-head">' + esc(s.heading) + '</h2>' +
        (body ? '<p>' + esc(body) + '</p>' : '') +
        (fig ? rowFigure(fig) : '') +
        (/recognition/i.test(s.heading) ? logos : '') +
        '</div>';
    }).join('');

    // Hidden projects stay reachable by direct URL but are marked unlisted;
    // prev/next only cycles the visible set and disappears when it can't.
    $('p-unlisted').hidden = !p.hidden;
    var visible = order.filter(function (x) { return !x.hidden; });
    var i = visible.findIndex(function (x) { return x.slug === p.slug; });
    if (i < 0 || visible.length < 2) {
      $('p-nav').hidden = true;
    } else {
      var prev = visible[(i - 1 + visible.length) % visible.length];
      var next = visible[(i + 1) % visible.length];
      $('p-prev').href = 'project.html?slug=' + encodeURIComponent(prev.slug);
      $('p-prev-title').textContent = prev.title;
      $('p-next').href = 'project.html?slug=' + encodeURIComponent(next.slug);
      $('p-next-title').textContent = next.title;
      $('p-nav').hidden = false;
    }

    // the 3D model leads the page when the project has one (CRC: the skatepark)
    if (p.model3d && p.model3d.module && p.model3d.poster) m3Init(p.model3d);

    if (p.compare && (p.compare.views || []).length >= 2) cmpInit(p.compare);

    if (p.images && p.images.length) {   // the section stays hidden for model-only projects
      $('dw-section').hidden = false;
      dwInit(p);
    }

    // The figures cut from the boards (the Projects-page blowout's gallery), each at its own size.
    // A file already on this page (drawing set, models, compare views, Proctor's inline master
    // plan) is left out. They lead when the drawing set is one spread or none; otherwise they
    // follow it, and a lone spread below them is titled for what it is. A 3D model stays first.
    var figs = projectFigures(p);
    if (figs.length) {
      var sec = document.createElement('section');
      sec.id = 'p-figs';
      sec.setAttribute('aria-labelledby', 'p-figs-title');
      sec.innerHTML = '<h2 class="h-head" id="p-figs-title" style="font-size:26px; margin:48px 0 14px">Drawings</h2>' +
        '<div style="display:flex; flex-direction:column; gap:26px; max-width:960px">' +
        figs.map(function (g) {
          var cap = clean(g.caption), gw = +g.w, gh = +g.h, sized = gw > 0 && gh > 0;
          // with its w/h from the JSON a figure holds its box before it arrives: its own width
          // (the border outside it, so the drawing keeps its own pixels), at most the column
          return '<figure style="margin:0">' +
            '<img src="content/projects/' + esc(g.file) + '"' + (sized ? ' width="' + gw + '" height="' + gh + '"' : '') +
            ' alt="' + esc(cap) + '" loading="lazy" onerror="US.imgPh(this,\'figure\')" style="' +
            (sized ? 'box-sizing:content-box; max-width:calc(100% - 2px); height:auto' : 'max-width:100%; width:auto; height:auto') +
            '; display:block; border:1px solid var(--line); background:#FFF">' +
            '<figcaption class="fig-cap">' + esc(cap) + '</figcaption></figure>';
        }).join('') + '</div>';
      var nImages = (p.images || []).length;
      var lead = $('m3-section').hidden ? $('p-meta') : $('m3-section');
      var after = nImages <= 1 ? lead : $('dw-section');
      after.parentNode.insertBefore(sec, after.nextSibling);
      if (nImages === 1) {
        $('dw-title').textContent = 'Portfolio spread';
        $('dw-thumbs').hidden = true;
      }
    }

    // Models gallery: physical + digital models declared in the project JSON
    var models = p.models || [];
    if (models.length) {
      $('p-models').hidden = false;
      $('p-models-grid').innerHTML = models.map(function (m) {
        var cap = clean(m.caption);
        return '<figure style="margin:0">' +
          '<img src="content/projects/' + m.file + '"' + (+m.w > 0 && +m.h > 0 ? ' width="' + (+m.w) + '" height="' + (+m.h) + '"' : '') + ' alt="' + esc(cap) + '" loading="lazy" onerror="US.imgPh(this,\'model photo\')" style="width:100%; height:auto; display:block; border:1px solid var(--line); background:#FFF">' +
          '<figcaption class="fig-cap">' + esc(cap) +
          (m.medium ? ' · ' + esc(clean(m.medium)) : '') + '</figcaption></figure>';
      }).join('');
    }

    if (p.slug === 'proctor-landing-park') {
      $('mp-section').hidden = false;
      mpInit();
      bkInit();   // the booklet opens itself as the reader nears it; the intro is the idle state
    }
  }

  // a slug that names no project: say so (and keep the URL out of search), never show another
  function renderMissing() {
    document.title = 'Project not found — Understory Design Studio';
    var robots = document.createElement('meta');
    robots.name = 'robots'; robots.content = 'noindex';
    document.head.appendChild(robots);
    $('p-title').textContent = 'Project not found';
    $('p-meta').hidden = true;
    $('p-summary').style.marginTop = '20px';
    $('p-summary').innerHTML = '<a class="link-mono" href="work.html">All projects\u00a0\u2192</a>';
  }

  // the content itself failed (this project's file, or all of them)
  function renderFailed() {
    $('p-title').textContent = 'Projects didn\u2019t load';
    $('p-meta').innerHTML = US.loadNote(true);
  }

  var slug = new URLSearchParams(location.search).get('slug') || 'proctor-landing-park';
  US.loadProjects().then(function (projects) {
    var chosen = null;
    projects.forEach(function (x) { if (x.slug === slug) chosen = x; });
    if (chosen) renderProject(chosen, projects);
    else if (projects.failed.indexOf(slug) >= 0) renderFailed();
    else renderMissing();
  }).catch(function (e) { console.warn('content load failed', e); renderFailed(); });
})();
