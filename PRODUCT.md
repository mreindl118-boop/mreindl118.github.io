# Product

<!-- impeccable:product-schema 1 -->

Understory Design Studio is the portfolio site of Matthew Reindl, an emerging landscape architect
(MLA, University of Cincinnati DAAP, 2026) based in Cincinnati, OH. Live at
www.understorydesignstudio.com. Facts below come from the site's own content
(`content/site.json`, `content/projects/*.json`) and the owner's standing instructions in
`CLAUDE.md`. Lines marked *(inferred)* have not been confirmed by the owner yet.

## Platform

web

## Users

- **Primary:** principals, studio leads and hiring managers at landscape architecture and
  planning firms deciding whether to interview an emerging designer. They skim the work fast,
  open one or two projects in depth, and look for the résumé and a way to get in touch.
  *(inferred from the site's structure: portfolio, résumé, contact)*
- **Secondary:** public-sector clients and collaborators (the Cincinnati Recreation
  Commission work), award juries and faculty following up on DAAPworks and SEGD recognition.
  *(inferred)*

## Product Purpose

Show a body of landscape architecture work clearly enough that a reviewer comes away with
Matthew's design thinking (water, ecology, recreation and public life as one system), sees that
he can produce professional drawings, and has the résumé, the downloadable portfolio and his
contact details within one click. Success is a reviewer reaching out.

## Positioning

Ecological public landscapes where flood resilience, recreation, planting systems and clear
visual storytelling work together. The studio name is the argument: the understory is the
vegetation layer beneath the canopy, the quiet stratum that does most of the ecological work.
The award-recognised capstone (Proctor Landing Park) leads the work.

## Operating Context

- Reviewers arrive from a résumé, LinkedIn, an email or a job application, on a laptop or a
  phone, often with several candidates open in tabs.
- The work is presented the way the profession presents it: drawing sets, boards, a capstone
  booklet, a 35-page portfolio book (web spread viewer + PDF), project pages with spreads,
  zoomable drawings, models and a compare slider.
- The site is static HTML + CSS + vanilla JS on GitHub Pages; content lives in JSON. No build
  step, no framework, no analytics.

## Capabilities and Constraints

- Four tabs: Home, Projects (work.html + the portfolio viewer), About (with contact at
  `#contact`), Experience (résumé, with skills at `#skills`).
- Ten project records; `hidden: true` unlists one while its URL keeps working.
- The portfolio book is rebuilt by `_drafts/tools/rebuild_portfolio.py`; never hand-edit
  spreads.
- **Change approval:** no change to a base site file (pages, CSS/JS, content JSON, images,
  PDFs) without a visual before/after comparison and the owner's approval. Stage in a
  worktree, compare, ask, then fast-forward only approved commits.

## Brand Commitments

- Name: **Understory Design Studio** (wordmark "Understory" + "Design Studio"); the
  square–line–circle mark and rule motif come from the owner's design board.
- Voice: plain, specific, professional; the work and the drawings carry the claims.
- The visual system in `DESIGN.md` is the owner's committed identity, not a draft to be
  restyled.

## Evidence on Hand

- Real work only: drawings, boards, renders, model photos and booklet spreads under
  `content/projects/` and `assets/`.
- Real recognition: DAAPworks 2026 Directors' Choice (Excellence in Landscape Architecture),
  SEGD Honorable Mention, selection for the Design Museum of Chicago DAAPworks exhibition, all
  for Proctor Landing Park.
- Never invent clients, quotes, testimonials, metrics, awards, team members or project facts.
  Unverified facts in JSON are wrapped in `[CONFIRM ...]` markers (stripped at render).

## Product Principles

1. The work leads; the interface recedes.
2. Every drawing is shown whole and legible: no cropped boards, clipped captions or blurry
   enlargements.
3. A reviewer gets from any page to the résumé, the PDF and the contact details in one step.
4. Truthful to the record: every claim is backed by a drawing, a document or the owner.

## Accessibility & Inclusion

WCAG 2.2 AA. The site passed an axe-core audit with zero violations in October 2026; keep it
there (contrast tokens, skip link, landmarks, visible focus, keyboard-operable viewers and
sliders, tap targets of at least 24 px on phones, `prefers-reduced-motion` respected).

### Privacy (standing, non-negotiable)

No personal phone number, no @mail.uc.edu address, no street address on the HTML résumé, no
family members or partners in the bio (the dog, Tabitha, is fine). The only contact email is
mreindl118@gmail.com.
