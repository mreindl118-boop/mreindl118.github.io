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
3. Implement: design `.dc.html` pages get ported into the production pages
   (and a copy kept in `_drafts/`); photos get optimized to webp and wired
   into `content/projects/*` (see the `models` array + Models gallery on
   project.html); a new résumé PDF replaces `assets/Matthew_Reindl_Resume.pdf`
   AFTER the privacy pass below.
4. Deploy via the flow above, then mark each row done in the artifact db:
   `update` with `status: "processed"` and a short `note` saying where it
   landed (pass `if_version`).

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
booklet) over `assets/portfolio/spread-NN.webp` (00 = cover, 01-08 spreads,
09 = Weaver laser-cut model spread added by Claude, 10-16 spreads incl.
colophon), with `assets/Matthew_Reindl_Portfolio.pdf` rebuilt from the same
half pages (33 pp) as the download. The owner's export clipped every
bottom caption at the page edge, so each half page is extended 110 px and
the clipped captions are re-typeset in IBM Plex Sans Text (transcriptions
live in the session history; a future re-export needs the same pass unless
the source gains a bottom margin). The owner's PDF exports have had each spread
clipped at the page edge; the full spreads were recovered by stitching the
left/right half pages of the 31-page A4 export (2k, 2k+1 -> spread k at
200 dpi). A corrected re-export replaces this pipeline: privacy pass, then
re-render spreads and rebuild the download PDF. Portfolio spreads also
feed the project pages (`content/projects/<slug>/pf-NN.webp`), and the
Projects-page blowouts use individual figures cut from the boards
(`content/projects/<slug>/g-*.webp`, `gallery` + `focus` in each JSON).
The final colophon page carries a composited closer (small b&w headshot +
about block above the name) added by Claude on both spread-15.webp and the
download PDF's last page — re-apply it after any portfolio re-export.

Pages are static HTML + `content/site.json` and `content/projects/*.json`
(`index.json` lists slugs). `hidden: true` unlists a project everywhere while
its direct project.html URL keeps working. `[CONFIRM ...]` markers in JSON are
stripped at render by `US.clean()` — use them for unverified facts.
