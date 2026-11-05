/* Understory Designs — shared content + viewer helpers (no framework). */
(function () {
  'use strict';

  var CONFIRM_RE = /\s*\[\s*confirm[^\]]*\]/gi;

  function clean(s) {
    return String(s == null ? '' : s).replace(CONFIRM_RE, '').replace(/\s{2,}/g, ' ').trim();
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function getJSON(path) {
    return fetch(path).then(function (r) {
      if (!r.ok) throw new Error('fetch failed: ' + path);
      return r.json();
    });
  }

  function loadSite() { return getJSON('content/site.json'); }

  /* One bad or missing project file (a drop-box edit, say) costs only that project: the rest
     still load, and the list carries the slugs that failed as .failed. It rejects only when
     nothing loads. */
  function loadProjects() {
    return getJSON('content/projects/index.json').then(function (slugs) {
      return Promise.allSettled(slugs.map(function (s) { return getJSON('content/projects/' + s + '.json'); }))
        .then(function (results) {
          var projects = [], failed = [];
          results.forEach(function (r, i) {
            if (r.status === 'fulfilled' && r.value && typeof r.value.slug === 'string') projects.push(r.value);
            else {
              failed.push(slugs[i]);
              console.warn('project content failed: ' + slugs[i], r.reason ? (r.reason.message || r.reason) : 'no slug');
            }
          });
          if (!projects.length) throw new Error('no project content loaded');
          projects.sort(function (a, b) { return (b.sortKey || b.year || 0) - (a.sortKey || a.year || 0); });
          projects.failed = failed;
          return projects;
        });
    });
  }

  /* Where project content should be when it can't load (Projects grid, a project page, whose
     h1 then carries the lead): the portfolio book and its PDF still carry the work. The
     <noscript> notes repeat it. */
  function loadNote(noLead) {
    return '<div class="load-note">' + (noLead ? '' : '<p class="mono-note">Projects didn\u2019t load.</p>') + '<div class="load-note-links">' +
      '<a class="link-mono" href="portfolio.html">Flip through the portfolio book\u00a0\u2192</a>' +
      '<a class="link-mono" href="assets/Matthew_Reindl_Portfolio.pdf" download="Matthew_Reindl_Portfolio.pdf">Download PDF\u00a0\u2193 ' +
      '<span style="color:var(--faint); white-space:nowrap">(16\u00a0MB)</span></a></div></div>';
  }

  /* A figure or model photo that fails to load takes the covers' hatched placeholder and says so
     ("Figure didn't load"); "coming soon" stays for a cover the data doesn't have yet */
  function imgPh(img, label) {
    var ph = document.createElement('div');
    ph.className = 'cover-ph img-ph';
    ph.innerHTML = '<span class="ph-label">' + esc(label.charAt(0).toUpperCase() + label.slice(1)) + ' didn\u2019t load</span>';
    if (img.parentNode) img.parentNode.replaceChild(ph, img);
  }

  function foldB64(b64) {
    // vCard continuation lines: fold long base64 onto space-prefixed lines
    return b64.replace(/(.{72})/g, '$1\n ');
  }

  function vcardHref(site, photoB64) {
    var vcard = 'BEGIN:VCARD\nVERSION:3.0\nFN:' + site.name + '\nORG:' + site.practice +
      '\nTITLE:' + site.discipline +
      (site.phone ? '\nTEL;TYPE=CELL:' + site.phone : '') +
      '\nEMAIL:' + site.email +
      '\nURL:' + site.linkedin +
      (photoB64 ? '\nPHOTO;ENCODING=b;TYPE=JPEG:' + foldB64(photoB64) : '') +
      '\nADR;TYPE=WORK:;;;Cincinnati;OH;;USA\nEND:VCARD';
    return 'data:text/vcard;charset=utf-8,' + encodeURIComponent(vcard);
  }

  function bindVcard(site) {
    var apply = function (photoB64) {
      var href = vcardHref(site, photoB64);
      Array.prototype.forEach.call(document.querySelectorAll('[data-vcard]'), function (a) {
        a.href = href;
        a.setAttribute('download', 'matthew-reindl.vcf');
      });
    };
    apply(null); // usable immediately; upgraded with the photo once it loads
    fetch('assets/img/contact-photo.jpg').then(function (r) {
      if (!r.ok) throw new Error('photo missing');
      return r.blob();
    }).then(function (blob) {
      return new Promise(function (res, rej) {
        var fr = new FileReader();
        fr.onload = function () { res(String(fr.result).split(',')[1]); };
        fr.onerror = rej;
        fr.readAsDataURL(blob);
      });
    }).then(apply).catch(function () { /* keep the photo-less vCard */ });
  }

  /* Portfolio spreads, as built by _drafts/tools/rebuild_portfolio.py: spread 0 is the
     cover, spread k holds book pages 2k and 2k+1, and a page facing a blank page is
     published alone (centred by the viewer). Falls back to plain pairs if the manifest
     can't be fetched. */
  var portfolioP = null;
  function pad2(n) { return String(n).padStart(2, '0'); }
  function loadPortfolio() {
    if (portfolioP) return portfolioP;
    portfolioP = getJSON('assets/portfolio/spreads.json').catch(function () {
      var spreads = [{ src: 'spread-00.webp', pages: [1], caption: 'Cover' }];
      for (var k = 1; k <= 17; k++) spreads.push({ src: 'spread-' + pad2(k) + '.webp', pages: [2 * k, 2 * k + 1], caption: '' });
      return { pages: 35, spreads: spreads };
    }).then(function (m) {
      m.src = function (k) { return 'assets/portfolio/' + m.spreads[k].src; };
      m.range = function (k) { var p = m.spreads[k].pages; return [p[0], p[p.length - 1]]; };
      m.label = function (k) {
        var r = m.range(k);
        return r[0] === r[1] ? 'P. ' + pad2(r[0]) : 'PP. ' + pad2(r[0]) + '\u2013' + pad2(r[1]);
      };
      m.spreadOf = function (page) {
        for (var k = 0; k < m.spreads.length; k++) {
          if (m.spreads[k].pages.indexOf(page) >= 0) return k;
        }
        return Math.min(Math.floor(page / 2), m.spreads.length - 1);   // a blank page: its spread
      };
      return m;
    });
    return portfolioP;
  }

  function yearOf(p) { return clean(p.yearLabel || (p.year != null ? String(p.year) : '')); }
  function roleShort(p) { return clean(p.role).split('—')[0].trim(); }

  /* Project card markup shared by home + work pages. Passing `lede` (a sentence) makes it
     the Projects page's lead card: the text is wrapped beside the cover and the sentence
     added, shown only where the grid gives that card a full row (site.css .card-lead).
     `level` is the title's heading level: 2 on Projects, 3 under Home's "Recent Work" h2.
     The cover is decorative beside the title (alt=""), so the link's name is the card's own
     text, read once, in the order it is seen (WCAG 2.5.3: the name holds the visible text). */
  function projectCard(p, withSoftware, lede, level) {
    var h = 'h' + (level === 2 ? 2 : 3);
    var cover = p.cover
      ? '<div class="cover"><img src="content/projects/' + esc(p.cover) + '" alt="" loading="lazy" onerror="this.parentNode.classList.add(\'ph\');this.parentNode.innerHTML=\'<span class=&quot;ph-label&quot;>cover — coming soon</span>\'"></div>'
      : '<div class="cover ph"><span class="ph-label">cover — coming soon</span></div>';
    var lead = lede != null;
    return '<a class="card' + (lead ? ' card-lead' : '') + '" href="project.html?slug=' + esc(p.slug) + '">' +
      cover + (lead ? '<div class="card-text">' : '') +
      '<div class="card-type">' + [clean(p.type), clean(p.scale)].filter(Boolean).map(esc).join(' · ') + '</div>' +
      '<div class="card-titlerow"><' + h + ' class="card-title">' + esc(p.title) + '</' + h + '><span class="card-year">' + esc(yearOf(p)) + '</span></div>' +
      '<div class="card-sub">' + [clean(p.location), roleShort(p)].filter(Boolean).map(esc).join(' · ') + '</div>' +
      (withSoftware ? '<div class="card-soft">' + esc((p.software || []).join(' · ')) + '</div>' : '') +
      (lead ? (lede ? '<p class="card-lede">' + esc(lede) + '</p>' : '') + '</div>' : '') +
      '</a>';
  }

  /* Generic zoom/pan controller for a .vstage / .vsurface pair. At fit (100% or less)
     the stage takes no drag, so a finger on it scrolls the page (site.css: touch-action
     pan-y pinch-zoom); once zoomed in, the stage is .zoomed and one pointer pans it.
     The stage also takes focus: zoomed in, the arrow keys pan it a tenth of its size; at
     fit, left and right call opts.prev / opts.next (the viewer's own pages); + (or =) and -
     zoom, 0 fits. Keyboard actions land at once, never animated. */
  function panZoom(stage, surface, badge, opts) {
    opts = opts || {};
    var st = { zoom: 1, tx: 0, ty: 0, drag: null };
    function apply() {
      surface.style.transform = 'translate(' + st.tx + 'px,' + st.ty + 'px) scale(' + st.zoom + ')';
      stage.classList.toggle('zoomed', st.zoom > 1);
      if (badge) badge.textContent = Math.round(st.zoom * 100) + '%' + (st.zoom > 1 ? ' · drag to pan' : '');
      if (opts.onZoom) opts.onZoom(st.zoom);
    }
    // apply a change with the surface's transition switched off for that one step
    function instant(change) {
      surface.classList.add('dragging');
      change();
      void surface.offsetWidth;
      if (!st.drag) surface.classList.remove('dragging');
    }
    function zoomIn() { st.zoom = Math.min(st.zoom * 1.4, opts.max || 6); apply(); }
    function zoomOut() { st.zoom = Math.max(st.zoom / 1.4, 0.4); apply(); }
    stage.tabIndex = 0;
    stage.setAttribute('role', 'group');
    stage.setAttribute('aria-label', 'Drawing viewer. Arrow keys pan when zoomed, plus and minus zoom, 0 fits.');
    stage.addEventListener('keydown', function (e) {
      // only the stage itself (not a button inside it, like Open booklet or Retry), and never a
      // browser shortcut (Ctrl/Cmd + zooms the page, Alt+arrow goes back)
      if (e.target !== stage || e.altKey || e.ctrlKey || e.metaKey) return;
      var k = e.key, zoomed = st.zoom > 1;
      var pan = { ArrowLeft: [1, 0], ArrowRight: [-1, 0], ArrowUp: [0, 1], ArrowDown: [0, -1] }[k];
      if (pan && zoomed) {
        instant(function () {
          st.tx += pan[0] * stage.clientWidth * 0.1;
          st.ty += pan[1] * stage.clientHeight * 0.1;
          apply();
        });
      } else if (k === 'ArrowLeft' && opts.prev) opts.prev();
      else if (k === 'ArrowRight' && opts.next) opts.next();
      else if (k === '+' || k === '=') instant(zoomIn);
      else if (k === '-') instant(zoomOut);
      else if (k === '0') fit(true);
      else return;
      e.preventDefault();
    });
    stage.addEventListener('pointerdown', function (e) {
      if (opts.enabled && !opts.enabled()) return;
      // nothing to pan at fit, and a second finger never takes over (or jumps) the drag
      if (st.zoom <= 1 || st.drag) return;
      e.preventDefault();
      stage.setPointerCapture(e.pointerId);
      st.drag = { id: e.pointerId, x: e.clientX, y: e.clientY, tx: st.tx, ty: st.ty };
      surface.classList.add('dragging');
    });
    stage.addEventListener('pointermove', function (e) {
      if (!st.drag || e.pointerId !== st.drag.id) return;
      st.tx = st.drag.tx + e.clientX - st.drag.x;
      st.ty = st.drag.ty + e.clientY - st.drag.y;
      apply();
    });
    function endDrag(e) {
      if (st.drag && e && e.pointerId !== st.drag.id) return;
      st.drag = null; surface.classList.remove('dragging');
    }
    stage.addEventListener('pointerup', endDrag);
    stage.addEventListener('pointerleave', endDrag);
    stage.addEventListener('pointercancel', endDrag);
    stage.addEventListener('lostpointercapture', endDrag);
    /* fit(true) lands at fit with no transition: a new page or drawing must appear at
       fit, not shrink from the last one's zoom. The FIT button calls fit() and eases. */
    function fit(now) {
      st.zoom = 1; st.tx = 0; st.ty = 0; st.drag = null;
      if (now === true) {
        instant(apply);
      } else {
        surface.classList.remove('dragging');
        apply();
      }
    }
    apply();
    return {
      zoomIn: zoomIn,
      zoomOut: zoomOut,
      fit: fit,
      set: function (z) { st.zoom = z; apply(); },
      state: st
    };
  }

  window.US = {
    clean: clean, esc: esc, getJSON: getJSON,
    loadSite: loadSite, loadProjects: loadProjects, loadNote: loadNote, imgPh: imgPh,
    vcardHref: vcardHref, bindVcard: bindVcard,
    yearOf: yearOf, roleShort: roleShort,
    projectCard: projectCard, panZoom: panZoom, loadPortfolio: loadPortfolio
  };

  /* Nav indicator glide: on a mouse or touch click, the square-line-circle
     indicator slides from the active tab to the clicked one (200ms), then the
     page navigates; the destination page eases the tab label from clay to ink.
     Keyboard Enter (click detail 0) navigates at once: no motion on keyboard
     actions. */
  function initNavGlide() {
    var nav = document.querySelector('.navlinks');
    if (!nav) return;

    try {
      if (sessionStorage.getItem('us-nav-glide')) {
        sessionStorage.removeItem('us-nav-glide');
        var act = nav.querySelector('a.active');
        if (act) act.classList.add('arrived');
      }
    } catch (e) { /* storage unavailable — skip the settle effect */ }

    var gliding = false;
    /* A page restored from the back/forward cache comes back mid-glide: clear the
       flying copy, the clay target and the hidden indicator so the tabs work again. */
    window.addEventListener('pageshow', function (e) {
      if (!e.persisted) return;
      gliding = false;
      Array.prototype.forEach.call(nav.querySelectorAll('.ind-fly'), function (f) { f.remove(); });
      Array.prototype.forEach.call(nav.querySelectorAll('a.arriving'), function (a) { a.classList.remove('arriving'); });
      Array.prototype.forEach.call(nav.querySelectorAll('.ind'), function (i) { i.style.opacity = ''; });
    });
    nav.addEventListener('click', function (e) {
      var link = e.target.closest('a');
      if (!link || !nav.contains(link)) return;
      if (link.classList.contains('active')) return;
      if (e.defaultPrevented || e.button !== 0) return;
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      if (e.detail === 0) return;   // keyboard Enter: the link navigates natively, no hold
      if (gliding) { e.preventDefault(); return; }
      gliding = true;
      e.preventDefault();
      try { sessionStorage.setItem('us-nav-glide', '1'); } catch (err) {}

      var cur = nav.querySelector('a.active');
      var fromInd = cur ? cur.querySelector('.ind') : null;
      var toInd = link.querySelector('.ind');
      var navRect = nav.getBoundingClientRect();
      var from = (fromInd || toInd).getBoundingClientRect();
      var to = toInd.getBoundingClientRect();

      var fly = document.createElement('span');
      fly.className = 'ind ind-fly';
      fly.innerHTML = '<i class="sq"></i><i class="ln"></i><i class="ci"></i>';
      fly.style.left = (from.left - navRect.left) + 'px';
      fly.style.top = (from.top - navRect.top) + 'px';
      fly.style.width = from.width + 'px';
      nav.appendChild(fly);
      if (fromInd) fromInd.style.opacity = '0';
      link.classList.add('arriving');

      var go = function () { window.location.href = link.href; };
      var reduced = false;
      try { reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (err) {}
      if (reduced) { go(); return; }

      // left/top stay at the old tab; the glide is a transform (plus the width)
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          fly.style.transform = 'translate(' + (to.left - from.left) + 'px,' + (to.top - from.top) + 'px)';
          fly.style.width = to.width + 'px';
        });
      });
      setTimeout(go, 200);
    });
  }

  /* Expose the sticky nav's real height (it wraps to two rows on narrow phones) as
     --nav-h, which html's scroll-padding-top uses to land anchors clear of it. */
  function initNavHeight() {
    var n = document.querySelector('.nav');
    if (!n) return;
    function setNavH() { document.documentElement.style.setProperty('--nav-h', n.offsetHeight + 'px'); }
    setNavH();
    if (window.ResizeObserver) new ResizeObserver(setNavH).observe(n);
  }

  function init() { initNavHeight(); initNavGlide(); }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
