---
name: understory-design
description: House rules for any design, UI, layout, typography, colour, motion or polish work on the Understory Design Studio site (understorydesignstudio.com). Load it before using impeccable, design-taste-frontend or Emil Kowalski's skills on this repo; it says which of their rules apply here, which the brand overrides, and how changes get approved.
---

# Understory design work

This repo has three vendored design skill sets in `.claude/skills/`:

| Skill | Use it for | Source |
| --- | --- | --- |
| `impeccable` | Design vocabulary and commands: `audit`, `critique`, `polish`, `typeset`, `layout`, `colorize`, `quieter`, `bolder`, `distill`, `harden`, `adapt`, `clarify`, `optimize`, `animate`, `delight` | pbakaus/impeccable (Apache-2.0) |
| `design-taste-frontend` | Art direction from a study of 247 award-gallery sites; the review loop; `lint.mjs` and the real-browser `inspect.mjs` | Leonxlnx/taste-skill (MIT) |
| `emil-design-eng` and its siblings | Interaction and motion craft: easing, durations, press states, reduced motion, perf, mobile feel, breaking UI with worst-case data | emilkowalski/skill (MIT) |

Upstream versions and why parts were left out are recorded in `.claude/skills/VENDORED.md`.

## Order of authority

1. The owner's own words in this conversation.
2. `CLAUDE.md`: the change-approval rule, the privacy rules, the portfolio pipeline.
3. `PRODUCT.md` (who the site is for, what it must never invent) and `DESIGN.md` (the
   committed visual system; tokens live in `assets/css/site.css` `:root`).
4. The three skill sets' rules.

Both impeccable ("the brief wins", "refinement preserves") and the taste skill (`--keep
palette,type`, `--allow <rule>` for a brand's own devices) defer to an existing brand. This
site is a refinement project, not a redesign: keep the identity and improve the craft inside it.

## Where the brand overrides the skills

These are part of the owner's committed identity. Do not remove or restyle them on your own;
raise them as findings at most, and only change them after the owner says so.

| Skill rule | On this site | Why |
| --- | --- | --- |
| taste: no monospace; impeccable: "monospace as costume" | **Kept** for labels, dates, counters, nav and figure numbers (DESIGN.md, the Title Block Rule). Never for running text or headings. | It is drawing-sheet lettering, the site's annotation voice. |
| taste: no tiny uppercase tracked labels; no eyebrows. impeccable: kicker/eyebrow is a ban | **Open to change.** The eyebrows above headings (`.kicker`, `.feature-tag`) may be proposed for removal or rewriting, with a before/after for approval. Labels that annotate data (`.meta-label`, dates, counters, figure numbers) stay under the Title Block Rule. | The owner chose to let the skills propose changes here (Nov 2026). |
| taste: no thin rules, no 1px boxes | **Kept**: 1px ink rules and the square–line–circle motif are the structural language. | From the owner's design board. |
| taste: "assistant palette" (cream ground with terracotta/rust) | **Kept**: paper, ink, pine, clay and moss are the brand palette. | Landscape-material colours chosen by the owner. |
| taste/impeccable: no index numbering | **Allowed only for real sequences**: the six practice phases, project numbers in the portfolio book. | The order is information. |
| taste: floor of four or more real pictures per page, motion floor of eight moving elements | **Not enforced as a gate.** The work is the imagery; motion stays restrained (see below). | A portfolio in Experience mode. |

Everything else in the three skills applies in full, including: contrast at or above 4.5:1,
visible focus, keyboard access, headings on every section, equal outer margins, two-line
headlines, no gradients, glass, glows or resting shadows, square corners, no invented proof.

## Mode

- Home (`index.html`): impeccable **Persuade** (a reviewer decides to look further).
- Projects, project pages, portfolio, booklet: **Experience** (the work leads, the interface
  recedes).
- About and Experience (résumé): **Read**.

## Motion on this site (Emil Kowalski's rules)

The site's motion is the nav indicator glide, the hero contour drift, the viewers' pan/zoom and
the compare slider. Apply Emil's framework strictly: no animation on keyboard-driven or
high-frequency actions; ease-out for entrances with a strong custom curve
(`cubic-bezier(0.23, 1, 0.32, 1)`); UI motion under 300 ms; only transform and opacity; hover
effects behind `@media (hover: hover) and (pointer: fine)`; a `prefers-reduced-motion` path for
everything. Report reviews in Emil's required `| Before | After | Why |` table.

## Running the tools here

- **impeccable:** its engine launcher and hooks are intentionally not installed (they download
  and run a native binary on every edit). Its own fallback applies: say once that context
  loading did not run, then read `PRODUCT.md` and `DESIGN.md` directly and follow the command's
  reference file.
- **design-taste-frontend:** needs Node 22+ and Chromium. In the cloud container:
  `CHROME_PATH=/opt/pw-browsers/chromium node .claude/skills/design-taste-frontend/scripts/inspect.mjs <url> --out .taste-skill/before`
  and `node .claude/skills/design-taste-frontend/scripts/lint.mjs <files>`. Serve the site with
  `python3 -m http.server` first. Read its findings through the override table above; a FAIL
  on a pinned brand device is a known exception, not a bug. Working files go in `.taste-skill/`
  (gitignored, never served).
- **Site audit harness:** `_drafts/tools/site_audit.py` (axe-core, screenshots at desktop and
  phone, crop and tap-target checks).

## The change loop (required)

1. Stage the change in a worktree (`git worktree add <scratch>/stageN -b staging-passN`).
2. Run the relevant skill's checks plus the audit harness on the staged copy.
3. Publish a before/after comparison (screenshots at desktop and phone width) and ask the owner.
4. Fast-forward only the approved commits, deploy per `CLAUDE.md`, and confirm the live files.

## What a good request looks like

- "Review the project page" → impeccable `critique` + `audit`, taste's review special case
  (inspect the before, critique, the three changes that would move the page most), Emil's
  table for any motion. Report only; no edits.
- "Polish the portfolio viewer" → impeccable `polish` + Emil's component rules, staged and
  compared.
- "Make the home page less generic" → taste's review run with `--keep palette,type`, inside the
  override table.
- "Animate X" → Emil's `animate`, then `review-animations`.
- "Break the project cards" → Emil's `break-ui` with worst-case titles and captions.
