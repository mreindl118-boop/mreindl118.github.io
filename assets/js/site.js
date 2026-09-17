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

  function loadProjects() {
    return getJSON('content/projects/index.json').then(function (slugs) {
      return Promise.all(slugs.map(function (s) { return getJSON('content/projects/' + s + '.json'); }));
    }).then(function (projects) {
      projects.sort(function (a, b) { return (b.sortKey || b.year) - (a.sortKey || a.year); });
      return projects;
    });
  }

  function vcardHref(site) {
    var vcard = 'BEGIN:VCARD\nVERSION:3.0\nFN:' + site.name + '\nORG:' + site.practice +
      '\nTITLE:' + site.discipline +
      (site.phone ? '\nTEL;TYPE=CELL:' + site.phone : '') +
      '\nEMAIL:' + site.email +
      '\nURL:' + site.linkedin + '\nADR;TYPE=WORK:;;;Cincinnati;OH;;USA\nEND:VCARD';
    return 'data:text/vcard;charset=utf-8,' + encodeURIComponent(vcard);
  }

  function bindVcard(site) {
    var href = vcardHref(site);
    Array.prototype.forEach.call(document.querySelectorAll('[data-vcard]'), function (a) {
      a.href = href;
      a.setAttribute('download', 'matthew-reindl.vcf');
    });
  }

  function yearOf(p) { return clean(p.yearLabel || String(p.year)); }
  function roleShort(p) { return clean(p.role).split('—')[0].trim(); }

  /* Project card markup shared by home + work pages. */
  function projectCard(p, withSoftware) {
    var cover = p.cover
      ? '<div class="cover"><img src="content/projects/' + esc(p.cover) + '" alt="' + esc(p.title) + ' — cover drawing" loading="lazy" onerror="this.parentNode.classList.add(\'ph\');this.parentNode.innerHTML=\'<span class=&quot;ph-label&quot;>cover — coming soon</span>\'"></div>'
      : '<div class="cover ph"><span class="ph-label">cover — coming soon</span></div>';
    return '<a class="card" href="project.html?slug=' + esc(p.slug) + '">' +
      cover +
      '<div class="card-type">' + esc(clean(p.type)) + ' · ' + esc(clean(p.scale)) + '</div>' +
      '<div class="card-titlerow"><span class="card-title">' + esc(p.title) + '</span><span class="card-year">' + esc(yearOf(p)) + '</span></div>' +
      '<div class="card-sub">' + esc(clean(p.location)) + ' · ' + esc(roleShort(p)) + '</div>' +
      (withSoftware ? '<div class="card-soft">' + esc((p.software || []).join(' · ')) + '</div>' : '') +
      '</a>';
  }

  /* Generic zoom/pan controller for a .vstage / .vsurface pair. */
  function panZoom(stage, surface, badge, opts) {
    opts = opts || {};
    var st = { zoom: 1, tx: 0, ty: 0, drag: null };
    function apply() {
      surface.style.transform = 'translate(' + st.tx + 'px,' + st.ty + 'px) scale(' + st.zoom + ')';
      if (badge) badge.textContent = Math.round(st.zoom * 100) + '% · drag to pan';
      if (opts.onZoom) opts.onZoom(st.zoom);
    }
    stage.addEventListener('pointerdown', function (e) {
      if (opts.enabled && !opts.enabled()) return;
      e.preventDefault();
      stage.setPointerCapture(e.pointerId);
      st.drag = { x: e.clientX, y: e.clientY, tx: st.tx, ty: st.ty };
      surface.classList.add('dragging');
    });
    stage.addEventListener('pointermove', function (e) {
      if (!st.drag) return;
      st.tx = st.drag.tx + e.clientX - st.drag.x;
      st.ty = st.drag.ty + e.clientY - st.drag.y;
      apply();
    });
    function endDrag() { st.drag = null; surface.classList.remove('dragging'); }
    stage.addEventListener('pointerup', endDrag);
    stage.addEventListener('pointerleave', endDrag);
    apply();
    return {
      zoomIn: function () { st.zoom = Math.min(st.zoom * 1.4, opts.max || 6); apply(); },
      zoomOut: function () { st.zoom = Math.max(st.zoom / 1.4, 0.4); apply(); },
      fit: function () { st.zoom = 1; st.tx = 0; st.ty = 0; apply(); },
      set: function (z) { st.zoom = z; apply(); },
      state: st
    };
  }

  window.US = {
    clean: clean, esc: esc, getJSON: getJSON,
    loadSite: loadSite, loadProjects: loadProjects,
    vcardHref: vcardHref, bindVcard: bindVcard,
    yearOf: yearOf, roleShort: roleShort,
    projectCard: projectCard, panZoom: panZoom
  };

  /* Nav indicator glide: on click, the square-line-circle indicator slides
     from the active tab to the clicked one, then the page navigates. The
     destination page plays a subtle settle highlight on arrival. */
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
    nav.addEventListener('click', function (e) {
      var link = e.target.closest('a');
      if (!link || !nav.contains(link)) return;
      if (link.classList.contains('active')) return;
      if (e.defaultPrevented || e.button !== 0) return;
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
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

      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          fly.style.left = (to.left - navRect.left) + 'px';
          fly.style.top = (to.top - navRect.top) + 'px';
          fly.style.width = to.width + 'px';
        });
      });
      setTimeout(go, 330);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initNavGlide);
  } else {
    initNavGlide();
  }
})();
