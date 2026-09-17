/* Project detail page: content, drawing-set viewer, master-plan lightbox,
   and the pdf.js capstone-booklet viewer (Proctor Landing Park). */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var clean = US.clean, esc = US.esc;

  /* ---------------- Drawing set viewer ---------------- */
  var dw = { project: null, idx: 0, pz: null };

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
    img.onerror = function () {
      surface.innerHTML =
        '<div style="width:900px; max-width:86vw; aspect-ratio:16/10; background:repeating-linear-gradient(45deg,#EDE9DB 0 8px,#F3F0E8 8px 16px); border:1px solid #C7C1AE; display:flex; align-items:center; justify-content:center">' +
        '<span style="font-family:var(--mono); font-size:12px; color:var(--muted); text-align:center; padding:0 20px">' +
        esc(im.drawing_type) + ' — drawing coming soon</span></div>';
    };
    surface.appendChild(img);
    $('dw-num').textContent = String(dw.idx + 1).padStart(2, '0');
    $('dw-caption').textContent = clean(im.caption);
    var scale = clean(im.scale);
    $('dw-meta').textContent = [im.drawing_type, scale, im.software].filter(Boolean).join(' · ');
    dw.pz.fit();
    Array.prototype.forEach.call($('dw-thumbs').children, function (b, i) {
      b.classList.toggle('active', i === dw.idx);
    });
  }

  function dwInit(p) {
    dw.project = p;
    $('dw-count').textContent = String(p.images.length).padStart(2, '0');
    $('dw-thumbs').innerHTML = p.images.map(function (im, k) {
      return '<button class="thumbbtn' + (k === 0 ? ' active' : '') + '" data-k="' + k + '">' + esc(dwLabel(p.images, k)) + '</button>';
    }).join('');
    $('dw-thumbs').addEventListener('click', function (e) {
      var b = e.target.closest('.thumbbtn');
      if (b) dwShow(parseInt(b.getAttribute('data-k'), 10));
    });
    dw.pz = US.panZoom($('dw-stage'), $('dw-surface'), $('dw-badge'));
    $('dw-prev').onclick = function () { dwShow(dw.idx - 1); };
    $('dw-next').onclick = function () { dwShow(dw.idx + 1); };
    $('dw-zin').onclick = dw.pz.zoomIn;
    $('dw-zout').onclick = dw.pz.zoomOut;
    $('dw-fit').onclick = dw.pz.fit;
    dwShow(0);
  }

  /* ---------------- Master plan lightbox ---------------- */
  function mpInit() {
    var pz = US.panZoom($('mp-stage'), $('mp-surface'), $('mp-badge'));
    function open() { $('mp-box').hidden = false; pz.fit(); }
    function close() { $('mp-box').hidden = true; }
    $('mp-open').onclick = open;
    $('mp-inline').onclick = open;
    $('mp-close').onclick = close;
    $('mp-box').addEventListener('click', function (e) { if (e.target === $('mp-box')) close(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); });
    $('mp-zin').onclick = pz.zoomIn;
    $('mp-zout').onclick = pz.zoomOut;
    $('mp-fit').onclick = pz.fit;
  }

  /* ---------------- Capstone booklet (pdf.js) ---------------- */
  var bk = {
    started: false, docs: [], total: 0, idx: 0,
    pages: [], tiers: {}, rendering: {}, retried: {},
    loadQ: Promise.resolve(), renderQ: Promise.resolve(),
    lib: null, pz: null, failed: false
  };
  var BK_PARTS = 11;
  var PDFJS = new URL('assets/vendor/pdfjs/pdf.min.mjs', location.href).href;
  var PDFJS_WORKER = new URL('assets/vendor/pdfjs/pdf.worker.min.mjs', location.href).href;

  function bkFile(i) { return 'assets/booklet/Part_' + String(i + 1).padStart(2, '0') + '.pdf'; }

  function bkUnits() {
    var t = bk.total, u = [];
    if (t) { u.push([0]); for (var i = 1; i < t; i += 2) u.push(i + 1 < t ? [i, i + 1] : [i]); }
    return u;
  }

  function bkStatus(msg) { $('bk-status').textContent = msg || ''; }

  function bkLib() {
    if (bk.lib) return Promise.resolve(bk.lib);
    return import(PDFJS).then(function (lib) {
      lib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;
      bk.lib = lib;
      return lib;
    });
  }

  function bkStart() {
    if (bk.started) return;
    bk.started = true;
    $('bk-intro').hidden = true;
    bkStatus('Loading part 1 / ' + BK_PARTS);
    bk.loadQ = bk.loadQ.then(function () { return bkLib(); }).catch(function (e) {
      console.warn('booklet library failed', e && (e.message || e));
      bk.failed = true;
      bkStatus('Could not load the booklet viewer');
      bkPaint();
      throw e;
    });
    for (var i = 0; i < BK_PARTS; i++) {
      (function (i) {
        bk.loadQ = bk.loadQ.then(function () {
          bkStatus('Loading part ' + (i + 1) + ' / ' + BK_PARTS);
          return fetch(bkFile(i)).then(function (r) {
            if (!r.ok) throw new Error('missing part ' + (i + 1));
            return r.arrayBuffer();
          }).then(function (buf) {
            return bk.lib.getDocument({ data: buf }).promise;
          }).then(function (doc) {
            bk.docs.push(doc);
            bk.total = bk.docs.reduce(function (s, d) { return s + d.numPages; }, 0);
            $('bk-count').textContent = String(bk.total).padStart(2, '0');
            $('bk-scrub').max = Math.max(bkUnits().length - 1, 0);
            if (i === BK_PARTS - 1) bkStatus('');
            bkEnsureCurrent();
            bkPaint();
          });
        }).catch(function (e) {
          console.warn('booklet part failed', e && (e.message || e));
        });
      })(i);
    }
  }

  function bkPageCanvas(i, w) {
    var k = i;
    for (var d = 0; d < bk.docs.length; d++) {
      var doc = bk.docs[d];
      if (k < doc.numPages) {
        return doc.getPage(k + 1).then(function (page) {
          var vp0 = page.getViewport({ scale: 1 });
          var vp = page.getViewport({ scale: w / vp0.width });
          var c = document.createElement('canvas');
          c.width = Math.round(vp.width); c.height = Math.round(vp.height);
          return page.render({ canvasContext: c.getContext('2d'), viewport: vp }).promise.then(function () { return c; });
        });
      }
      k -= doc.numPages;
    }
    return Promise.reject(new Error('page ' + (i + 1) + ' not loaded yet'));
  }

  function bkRender(i, targetW) {
    var w = targetW || 1500;
    var key = i + ':' + w;
    if (bk.rendering[key]) return;
    if ((bk.tiers[i] || 0) >= w) return;
    bk.rendering[key] = true;
    bk.renderQ = bk.renderQ.then(function () {
      var covered = bk.docs.reduce(function (s, d) { return s + d.numPages; }, 0);
      if (covered <= i) { delete bk.rendering[key]; return; }
      return bkPageCanvas(i, w).then(function (c) {
        bk.tiers[i] = w;
        bk.pages[i] = c.toDataURL('image/jpeg', 0.85);
        bk.failed = false;
        delete bk.rendering[key];
        bkPaint();
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

  function bkEnsureCurrent() {
    var units = bkUnits();
    var cur = (units[bk.idx] || []).concat(units[bk.idx + 1] || []);
    cur.forEach(function (o) { if (!bk.pages[o]) bkRender(o); });
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
      '<div style="font-family:var(--serif); font-weight:400; font-size:30px; letter-spacing:-.01em; margin-top:10px">Recognition</div>' +
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
    $('bk-num').textContent = unit.length
      ? unit.map(function (o) { return String(o + 1).padStart(2, '0'); }).join('–') : '··';
  }

  function bkGo(n) {
    var units = bkUnits();
    bk.idx = Math.min(Math.max(n, 0), Math.max(units.length - 1, 0));
    bk.failed = false;
    bk.pz.fit();
    $('bk-scrub').value = bk.idx;
    bkEnsureCurrent();
    bkPaint();
  }

  function bkInit() {
    bk.pz = US.panZoom($('bk-stage'), $('bk-surface'), $('bk-badge'), {
      max: 8,
      enabled: function () { return bk.started; },
      onZoom: function (z) {
        if (z > 2.2) (bkUnits()[bk.idx] || []).forEach(function (o) { bkRender(o, 3200); });
      }
    });
    $('bk-start').onclick = bkStart;
    $('bk-prev').onclick = function () { bkGo(bk.idx - 1); };
    $('bk-next').onclick = function () { bkGo(bk.idx + 1); };
    $('bk-zin').onclick = bk.pz.zoomIn;
    $('bk-zout').onclick = bk.pz.zoomOut;
    $('bk-fit').onclick = bk.pz.fit;
    $('bk-scrub').addEventListener('input', function (e) {
      if (!bk.started) { bkStart(); }
      bkGo(parseInt(e.target.value, 10) || 0);
    });
    $('bk-retry').onclick = function () {
      bk.retried = {}; bk.failed = false;
      bkStatus('Recovering viewer…');
      if (!bk.lib || !bk.docs.length) {
        bk.started = false;
        bk.loadQ = Promise.resolve();
        bkStart();
      } else {
        bkEnsureCurrent();
      }
      bkPaint();
    };
  }

  /* ---------------- Page assembly ---------------- */
  function metaCell(label, value, mono) {
    return '<div><div class="meta-label">' + esc(label) + '</div>' +
      '<div class="meta-value' + (mono ? ' mono' : '') + '">' + esc(value) + '</div></div>';
  }

  function renderProject(p, order) {
    document.title = p.title + ' — Understory Design Studio';
    $('p-title').textContent = p.title;
    $('p-meta').innerHTML =
      metaCell('Location', clean(p.location)) +
      metaCell('Year', US.yearOf(p)) +
      metaCell('Role', clean(p.role)) +
      metaCell('Scope / scale', clean(p.scale)) +
      metaCell('Collaborators', clean(p.collaborators) || '—') +
      metaCell('Software', (p.software || []).join(' · '), true);
    $('p-summary').textContent = clean(p.summary);

    var logos = '<div style="display:flex; gap:34px; align-items:center; flex-wrap:wrap; margin-top:20px; border-top:1px solid var(--line); padding-top:18px">' +
      '<img src="assets/logos/daapworks.jpg" alt="DAAPWorks" title="DAAPWorks" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '<img src="assets/logos/segd.png" alt="SEGD" title="SEGD" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '<img src="assets/logos/design-museum-chicago.png" alt="Design Museum of Chicago" title="Design Museum of Chicago" style="height:150px; width:auto; display:block; mix-blend-mode:multiply">' +
      '</div>';
    $('p-sections').innerHTML = (p.sections || []).map(function (s) {
      var body = clean(s.body);
      if (!body) return '';
      return '<div style="margin-top:32px; max-width:68ch">' +
        '<h2 class="h-serif" style="font-size:24px; margin:0 0 8px">' + esc(s.heading) + '</h2>' +
        '<p style="font-size:16px; line-height:1.65; margin:0; text-wrap:pretty">' + esc(body) + '</p>' +
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
    }

    dwInit(p);

    if (p.slug === 'proctor-landing-park') {
      $('mp-section').hidden = false;
      mpInit();
      bkInit();
    }
  }

  var slug = new URLSearchParams(location.search).get('slug') || 'proctor-landing-park';
  US.loadProjects().then(function (projects) {
    var chosen = null;
    projects.forEach(function (x) { if (x.slug === slug) chosen = x; });
    renderProject(chosen || projects[0], projects);
  }).catch(function (e) { console.warn('content load failed', e); });
})();
