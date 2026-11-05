# Understory Design Studio — site notes for Claude

Static portfolio site for Matthew Reindl, served by GitHub Pages from `main`
(custom domain www.understorydesignstudio.com). No build step; Jekyll's default
pass serves files as-is and skips underscore directories (`_drafts/` holds
unserved design sources).

## Deploy flow

Work on branch `claude/website-launch-design-0meyp9`, commit, push, then:
`git checkout main && git merge --ff-only <branch> && git push origin main`,
and return to the branch. The Pages CDN caches for ~10 minutes.

## Drop box pipeline (design → implementation)

The owner uploads redesigned pages, model photos, and PDFs to a private
claude.ai artifact ("Understory Drop Box"):

- Artifact URL: https://claude.ai/artifact/WDsJSVnov14p5fHDLkDdkK
- Wake trigger (fires into the original session): `trig_01QfnPowYL7tzfeboyLRRTQR`

Procedure when asked to "check the drop box" (or when the trigger fires):

1. Read the artifact's db collection `files` (ArtifactData). Each row:
   `{name, asset, bytes, served, uploadedAt, status, note}` — `asset` is the
   asset-store id; rows with `status: "new"` are unprocessed.
2. Fetch each new file with the Artifact tool: `action: "read"`, `url` above,
   `path: <asset id>` (saves locally). Rows named `*.html` were uploaded as
   `text/plain` — rename to the row's `name` after download.
   Big or unsupported files (`served: "chunked"`) arrive as pieces: fetch
   every id in the row's `chunks` the same way, save the row JSON, then run
   `python3 _drafts/tools/reassemble_dropbox.py row.json <piece dir> <out>`
   (strips each piece's `USCHUNK1` header, checks per-piece SHA-256 and the
   total size). Not every drop is for the website — HD source files may be
   for Claude's use only; ask if the destination isn't clear.
3. Implement: design `.dc.html` pages get ported into the production pages
   (and a copy kept in `_drafts/`); photos get optimized to webp and wired
   into `content/projects/*` (see the `models` array + Models gallery on
   project.html); a new résumé PDF replaces `assets/Matthew_Reindl_Resume.pdf`
   AFTER the privacy pass below.
4. Deploy via the flow above, then mark each row done in the artifact db:
   `update` with `status: "processed"` and a short `note` saying where it
   landed (pass `if_version`).

A second, brand-agnostic uploader exists for big source files that aren't
necessarily for the website: "Large File Drop",
https://claude.ai/artifact/1o5W1WqSY4YnYsarKEjWQM (Volume 1) and
https://claude.ai/artifact/SPDR2AZtTKbWxBLYD15d31 (Volume 2), 1 GB each, linked
to each other; wake trigger `trig_01D64f3dJS5JimHMY8puvPnE` covers both.
The pages pre-check free space, clean up a failed upload's pieces, and offer
"remove picked-up files" to reclaim room. Same `files` collection schema and chunk
protocol as the drop box; fetch each asset id one at a time (batch `paths`
reads don't reach uploaded assets) and ask the owner what each file is for.

Canva is connected but its export downloads (`export-download.canva.com`) are
blocked by this environment's network policy until the owner allows that host.

## Change approval (standing rule from the owner)

Don't change any base site file (pages, CSS/JS, content JSON, images, PDFs)
without first showing the owner a visual before/after comparison and getting
approval. Work in a staging copy (`git worktree add`), audit it, publish the
comparisons, ask, then fast-forward only the approved commits. The audit
harness (axe-core + full-page screenshots + crop/tap-target checks) lives in
the session scratchpad; rebuild it from `_drafts/tools/` notes if needed.

## Design skills

`.claude/skills/` vendors three design skill sets (provenance in `.claude/skills/VENDORED.md`):
Impeccable (`impeccable`: audit, critique, polish, typeset, layout, colorize, …), the taste skill
(`design-taste-frontend`: art direction from 247 award-gallery sites, `lint.mjs` and the
real-browser `inspect.mjs`) and Emil Kowalski's design-engineering skills (`emil-design-eng`,
`review-animations`, `break-ui`, `mobile-native`, …). Load `understory-design` first: it sets the
order of authority (owner > this file > PRODUCT.md/DESIGN.md > the skills), lists the brand
devices that override the skills' house rules (mono annotation labels, the rule motif and 1px
ink rules, the paper/pine/leaf/clay/moss palette), and the run commands for this container.
`PRODUCT.md` and `DESIGN.md` at the root are the product brief and the design system (both
excluded from the served site in `_config.yml`). Skill findings are proposals: the change-
approval rule below still applies to every edit they suggest.

## Privacy rules (standing, non-negotiable)

- No personal phone number anywhere on the site or in hosted PDFs. Any phone
  number found in a newly uploaded résumé PDF must be redacted (PyMuPDF
  redaction annotations) before the file is hosted.
- No university (@mail.uc.edu) email anywhere; redact from PDFs likewise.
- No street address in the HTML résumé.
- The bio names no family members or partners; the dog (Tabitha) is fine.
- Contact email is mreindl118@gmail.com only.

## Content architecture

Nav is four tabs: Home, Projects (work.html, links to the portfolio),
About (carries contact at `#contact`; contact.html redirects), Experience
(resume.html, carries skills at `#skills`; skills.html redirects).

`portfolio.html` is a booklet-style spread viewer (like the capstone
booklet) over `assets/portfolio/spread-NN.webp`, with
`assets/Matthew_Reindl_Portfolio.pdf` built from the same 35 pages as the
download. Spread 0 is the cover; spread k shows pages 2k and 2k+1, and the
viewer counts book pages ("PP. 12-13 / 35"). A page facing a blank page is
published alone and centred ("P. 22"); the viewer reads
`assets/portfolio/spreads.json` (written by the pipeline) for the spread list,
page labels and captions. The export's false page-curl bevel (an 80 px shadow
at every page's spine edge) is divided out at load; the PDF keeps its blank
pages. Every project opens on a spread
of its own: blank pages 23 and 26 separate Lunkenheimer, Hyundai and Evans,
and Hyundai's two pages were moved across the spine (gutter shading mirrored,
running-header labels swapped). Pages 18-19 are the Weaver laser-cut model
spread. The Contents page numbers are re-set to match (Proctor 03,
Buttes-Chaumont 12, West End 14, Weaver 16, CRC 20, Lunkenheimer 22,
Hyundai 24, Evans 27, Green Roof 30). The Projects page no longer embeds the
book or its per-project tags (removed November 2026, Sam Kremer's feedback); it
links to portfolio.html from its header and from the Portfolio Book band after
the grid.

The owner's export clips every bottom caption at the page edge. Each page is
extended 110 px and the clipped captions are re-set in place exactly in the
book's style: figure numbers IBM Plex Mono 24.5 px in pine (48,69,56), text
IBM Plex Sans 29.5 px, 34 px gap, on one baseline; only the caption's own
text box is painted (never the image above). The full pipeline (captions,
contents numbers, pagination, Hyundai swap, model spread, cover headshot,
colophon closer, spreads, PDF) is `_drafts/tools/rebuild_portfolio.py`, run
on the export's half pages rendered at 200 dpi. A re-export goes through the
privacy pass first, then this script (update its transcriptions if captions
change). The script also writes `assets/portfolio/cover-thumb.webp` (the
Portfolio Book band's cover, spread 0 at 560 px wide; work.html hard-codes
560x761) but not the "(16 MB)" label beside that band's PDF link in work.html;
fix that label by hand when the PDF size changes, along with its copies in the
load-failure note (`loadNote` in `assets/js/site.js`) and the `<noscript>` notes
in work.html, project.html and portfolio.html. Portfolio spreads also feed the project pages
(`content/projects/<slug>/pf-NN.webp`), and the Projects-page blowouts use
individual figures cut from the boards (`content/projects/<slug>/g-*.webp`,
`gallery` + `focus` in each JSON).

The Proctor capstone booklet on its project page is 11 PDF parts
(`assets/booklet/Part_NN.pdf`, pdf.js), with each part's page count in
`assets/booklet/parts.json` (`{"parts":[5,7,7,3,4,2,1,3,2,6,5],"page":[612,792]}`,
45 pages). The viewer loads no PDF until the reader comes within 200 px of it
(or uses its controls), then fetches only the part holding the current spread
and prefetches the next part once that spread is drawn. Update parts.json
whenever the booklet is re-exported (`pdfinfo` gives the counts); the console
warns when a part disagrees with it.

Some served images are derivatives of sources kept in `_drafts/originals/`
(same paths): Home's hero drawing (`assets/img/hero-central-planting.webp`)
and the older feature image (`content/projects/proctor-landing-park/feature.webp`,
1200x753) are flattened onto white (no alpha; the pages draw them with
`mix-blend-mode: multiply`, so white drops into the paper), and
`assets/logos/segd.png` is a 600 px cut of the 2400 px logo. After replacing a
source there, run `python3 _drafts/tools/derive_images.py` from the repo root.

Pages are static HTML + `content/site.json` and `content/projects/*.json`
(`index.json` lists slugs). `hidden: true` unlists a project everywhere while
its direct project.html URL keeps working. `[CONFIRM ...]` markers in JSON are
stripped at render by `US.clean()` — use them for unverified facts.
