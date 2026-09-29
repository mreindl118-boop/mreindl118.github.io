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

`portfolio.html` shows the portfolio PDF as pre-rendered webps
(`assets/portfolio/page-NN.webp`) with a download of
`assets/Matthew_Reindl_Portfolio.pdf`; a replacement PDF goes through the
privacy pass, then re-render the webps (PyMuPDF at 170 dpi, quality 80).

Pages are static HTML + `content/site.json` and `content/projects/*.json`
(`index.json` lists slugs). `hidden: true` unlists a project everywhere while
its direct project.html URL keeps working. `[CONFIRM ...]` markers in JSON are
stripped at render by `US.clean()` — use them for unverified facts.
