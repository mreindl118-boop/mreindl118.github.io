# Understory Design Studio — site notes for Claude

Static portfolio site for Matthew Reindl, served by GitHub Pages from `main`
(custom domain www.understorydesignstudio.com). No build step; Jekyll's default
pass serves files as-is and skips underscore directories (`_drafts/` holds
unserved design sources).

## Deploy flow

Work on branch `claude/website-launch-design-0meyp9`, commit, push, then:
`git checkout main && git merge --ff-only <branch> && git push origin main`,
and return to the branch. The Pages CDN caches for ~10 minutes.

## Backups and rollback

Pushing tags or extra branches is refused here (HTTP 403), so the rollback points are commits
in `main`'s own history. Deploys are fast-forwards, so these never disappear:

| Point | What was live |
| --- | --- |
| `e94522e` | Before the design-review pass 7 (Nov 2026; site files identical to `d00c5fe`) |
| `1f66f99` | After Sam's feedback and the pass-6 fixes |
| `aa4fd37` | Before Sam's feedback (serif headings, page-flipper on Projects) |

To put the website back to a point, without touching planLAB and without rewriting history,
on the working branch:
`git restore --source=<point> --staged --worktree -- . ':(exclude)planlab' ':(exclude)planlab-android' ':(exclude).github'`,
then commit ("Roll back the website to <point>"), push, and fast-forward `main` as above.
`git restore` with `--staged` also deletes files the point didn't have, so the tree matches it
exactly. To drop one change set of pass 7 instead, `git revert` its commits newest first:
set 1 `0bcbde9`; set 2 `54e05cc 5b5b50c 4afab8d` (2c `36eea40` and its fix `7688ae4` are
already reverted by `ab8eb0a 93e8b53`; revert those two to bring the full-width band back);
set 3 `54b9167 50ba932`; set 4 `3bb3832 2591708 71fa6a6`; set 5 `3e3a4f1 8057876 96c3db1 e86212b
acd1b32 ddfd52b 7f2099f`; set 6 `7da0632 1586b8d 100f576`; set 7 `59a35e9 2ae578f`; set 8
`0148a6f 3c50be8`; set 9 `c7edd74 a149b34 56b2b82 e2a3d74`. Later sets build on earlier ones
(6 on 4 and 5, 9a on 4a), so re-run the audit harness after any revert.

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
`assets/Matthew_Reindl_Portfolio.pdf` built from the same 37 pages as the
download. Spread 0 is the cover; spread k shows pages 2k and 2k+1, and the
viewer counts book pages ("PP. 12-13 / 37"). A page facing a blank page is
published alone and centred ("P. 24"); the viewer reads
`assets/portfolio/spreads.json` (written by the pipeline) for the spread list,
page labels and captions. The export's false page-curl bevel (an 80 px shadow
at every page's spine edge) is divided out at load; the PDF keeps its blank
pages. Every project opens on a spread
of its own: blank pages 25 and 28 separate Lunkenheimer, Hyundai and Evans,
and Hyundai's two pages were moved across the spine (gutter shading mirrored,
running-header labels swapped). Pages 18-19 are the Weaver laser-cut model
spread. Pages 21-22 are the Camp Washington Skatepark (below). The Contents page numbers are
re-set to match (Proctor 03, Buttes-Chaumont 12, West End 14, Weaver 16, CRC 20, Lunkenheimer 24,
Hyundai 26, Evans 29, Green Roof 32). The Projects page no longer embeds the
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
560x761) but not the "(17 MB)" label beside that band's PDF link in work.html;
fix that label by hand when the PDF size changes, along with its copies in the
load-failure note (`loadNote` in `assets/js/site.js`), the `<noscript>` notes
in work.html, project.html and portfolio.html, and portfolio.html's top Download
button. The viewer's project jump row and caption links read the captions'
"NN Title" form (first spread per number; titles matched by prefix against the
listed projects), so keep that form when captions change.

The pipeline writes the 35-page book; `_drafts/tools/insert_crc_skatepark_pages.py` (set 8d) then puts
the Camp Washington Skatepark at the head of the CRC section: new p. 21, the L-1 site plan (5.4) with a
plan key, and new p. 22, the exploded axon, the assembled axon and an eye-level model view (5.5-5.7)
with a credit and note row; the installation photos move to p. 23 (now 5.8, 5.9), CRC's title-page
subtitle and Contents sub-line lead with the skatepark, and the later Contents numbers move by two.
Everything is set in the book's measured style (its docstring has the numbers; the running heads and
meta rows copy p. 20's) with the IBM Plex TTFs in `_drafts/tools/fonts/`, which reproduce the
pipeline's own captions within JPEG noise (`plex-text.ttf` is IBM Plex Sans Text, `plex-mono.ttf` IBM
Plex Mono Regular). The two new pages are kept as masters in `_drafts/originals/portfolio/`; `--compose
<print dir>` re-draws them from render_skatepark_set.py's print masters and a fresh plan drawing. Run it
with no options on the repo's 35-page book (untouched pages keep their JPEG streams and untouched spreads
their files), or with `--pages <PF_OUT>` right after the pipeline; it refuses a book that already has
the pages. It writes the PDF, spreads 00-18, spreads.json (spreads 10 and 11 are both "05 Cincinnati
Recreation Commission — ...") and CRC's `pf-10`/`pf-11`; the other projects' `pf-NN` files keep the
35-page numbers (Lunkenheimer's `pf-11` is now spread 12, and so on), so their JSON stays as it is.

Portfolio spreads also feed the project pages
(`content/projects/<slug>/pf-NN.webp`), and the individual figures cut from the
boards (`content/projects/<slug>/g-*.webp`, `gallery` + `focus` in each JSON)
fill both the Projects-page blowouts and each project page's "Drawings" section.
Each `gallery` and `images` entry carries `w`/`h`, the file's pixel size, so the
page holds the figure's box (and a phone's drawing stage its shape) before the
image arrives; set them (Pillow's `Image.open(f).size`) when adding or replacing
a file. An entry without them still shows, it just moves the page as it loads.
That section leaves out any gallery file already on the page (`images`, `models`,
compare views, plus the `ON_PAGE` list in `assets/js/project.js`: Proctor's
inline master plan, Evans's two concept plans and CRC's numbered skatepark axon,
which is the 3D panel's poster view). Proctor's master plan is not
in its `images` (it shows once, inline).

The Proctor capstone booklet on its project page is 11 PDF parts
(`assets/booklet/Part_NN.pdf`, pdf.js), with each part's page count in
`assets/booklet/parts.json` (`{"parts":[5,7,7,3,4,2,1,3,2,6,5],"page":[612,792]}`,
45 pages). The viewer loads no PDF until the reader comes within 200 px of it
(or uses its controls), then fetches only the part holding the current spread
and prefetches the next part once that spread is drawn. Update parts.json
whenever the booklet is re-exported (`pdfinfo` gives the counts); the console
warns when a part disagrees with it.

The CRC project page leads with the Camp Washington Skatepark 3D model (`model3d` in its JSON:
title, subtitle, sheet, credit, note, canvas label, poster with `w`/`h`, logo, `module`). project.js
shows the poster and imports `assets/js/skatepark/viewer.js` only when "Open the 3D model" is
pressed (it fetches the module graph first, so Retry works after a network failure);
`plan-geometry.js` and `skatepark-builder.js` beside it are the owner's artifact modules, unchanged
but for an import path. three.js r184 is self-hosted in `assets/vendor/three-0.184.0/` (npm
three@0.184.0; the one edit is OrbitControls' import path). One-off stills of the model:
`python3 _drafts/tools/render_skatepark.py OUT.webp` (options in its docstring). The published
stills (the poster, the card cover, `g-cw-axon`, `g-cw-exploded`, `g-cw-eye-1..3`, and the book's PNG
masters plus nine per-feature details) all come from `python3 -B _drafts/tools/render_skatepark_set.py
--web content/projects/cincinnati-recreation-commission --print <dir>`, whose SHOTS table records
every camera; they are drawn by the viewer itself (2x supersampled). The site plan `g-cw-plan.webp`
(plan + key) and its print masters come from `python3 -B _drafts/tools/draw_skatepark_plan.py`, which
draws the L-1 sheet's vector plan (`_drafts/originals/content/projects/cincinnati-recreation-commission/
cw-l1-plan.svg`, north to the left) with the viewer's callouts placed by the SVG's own transforms.
Re-run both after changing the model, the callouts or their labels. Credit, from the
owner: Hunger Skateparks designed the park; Matthew documented it for the Cincinnati Recreation
Commission (L-1 site plan, L-2 3D massing, L-3 exploded axon), so never say or imply he designed it.
Hunger's concept rendering and the aerial photo in the owner's artifact are never published, and
its August 2026 site photos aren't approved for the site.

The skatepark is the lead work of the CRC project (one project, not a separate one): its summary
and focus open with the documentation, then the GIS and planting work. `gallery` starts with the
skatepark (`g-cw-plan`, `g-cw-exploded`, `g-cw-axon`, `g-cw-eye-1..3`), so the Projects blowout
opens on the plan; its link reads "Project page: open the 3D model" (`PAGE_LINK` in work.html).
`g-cw-plan` is also the first `images` entry, so on the page the plan gets the zoomable drawing set
(the book's two CRC spreads, `pf-10` and `pf-11`, are its second and third) and the Drawings section starts at the
exploded axon; `g-cw-axon` is in `ON_PAGE` because the poster above is the same view. The card
cover is the render set's `cover.webp` (CRC isn't among Home's three Recent Work cards at its
sortKey). Captions follow plan-geometry.js's feature names; feature 7 is "Coney gap jump" there (so
in the viewer's plan key and the redrawn `g-cw-plan.webp`) and "the Coney gap" in captions, until
the owner settles "Glizzy" against the L-1 sheet's "Glitzy". The card's scale reads "Skatepark
documentation": `roleShort()` cuts the role at its em dash, so without it the card would set "design
support" under the skatepark cover with no credit; at 23 characters the type line stays on one line
from 390 px up. "About 15,000 sq ft" is the
L-1 sheet's figure and matches the slab outline in plan-geometry.js (about 15,150 sq ft at
`scaleFtPerPx`); it carries a `[CONFIRM]` marker in `model3d.subtitle` until the owner checks it.
The L-1 sheet's other claims (first public skatepark in Cincinnati, the opening date, the
Cincinnati Skatepark Project as co-builder) stay off the site until the owner confirms them; the
role wording and the skatepark's tools wait in `[CONFIRM]` markers (`software` entries go through
`US.clean()` like every other field, so a marker there renders nothing). The model's credit line
(`model3d.credit`) is the owner's choice of November 2026, over wordings that named the AI
assistance: "Park design: Hunger Skateparks. Documentation for the Cincinnati Recreation
Commission." The Projects blowout prints `model3d.credit` and `model3d.note` under the summary, and
the site plan's caption names Hunger Skateparks as well. On stages under 900 px
the viewer's exploded view lifts callout 6 on a 7 m longer pin (`liftPins()` in viewer.js) so it
clears callout 4; the render scripts never call it, so the published stills don't change.

The CRC gallery also carries the Ryan Sports Complex (Hometown Huddle) planting plan, which the
owner drew in CAD and CRC allows on the site: `g-ryan-plan.webp` (whole plan + 22-kind key) and
`g-ryan-shelter.webp` (shelter-beds enlargement), placed just before the installed-planting photo
`g-h19_p1`. Both come from `python3 -I _drafts/tools/draw_ryan_planting.py --dxf <the owner's DXF>
--web content/projects/cincinnati-recreation-commission --print <dir>` (needs ezdxf); it checks every
count against its SCHEDULE and draws no prices. The DXF itself is never committed (it carries a priced
nursery list). A written-section row can hold one figure (`sections[].figure` with `file`, `w`, `h`,
`alt`, `caption`), kept out of `gallery`, so neither Drawings nor the blowout repeats it: CRC's
"Tools and process" row uses it for `rec-finder.webp`, a still of the Rec Finder prototype's opening
map that CRC allows (rebuilt by `_drafts/tools/render_rec_finder.py`, which keeps the masthead to the
brand, the counts and the Present button; the site never links the tool).
Hyundai's `g-massing-contours.webp` is the owner's Rhino capture (his own model), 1327x708 and
never shown larger.

Some served images are derivatives of sources kept in `_drafts/originals/`
(same paths): Home's hero drawing (`assets/img/hero-central-planting.webp`)
and the older feature image (`content/projects/proctor-landing-park/feature.webp`,
1200x753) are flattened onto white (no alpha; the pages draw them with
`mix-blend-mode: multiply`, so white drops into the paper), and
`assets/logos/segd.png` is a 600 px cut of the 2400 px logo, and Hyundai's
`g-massing-contours.webp` is the owner's Rhino JPG converted from Display P3 to sRGB with its top
4 rows (a selection sliver) cut. After replacing a source there, run
`python3 _drafts/tools/derive_images.py` from the repo root.

Pages are static HTML + `content/site.json` and `content/projects/*.json`
(`index.json` lists slugs). `hidden: true` unlists a project everywhere while
its direct project.html URL keeps working. `[CONFIRM ...]` markers in JSON are
stripped at render by `US.clean()` — use them for unverified facts.
