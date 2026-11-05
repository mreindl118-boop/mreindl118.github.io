---
name: Understory Design Studio
description: Portfolio of landscape architect Matthew Reindl, set like a professional drawing set.
colors:
  paper: "#F3F0E8"
  panel: "#FAF8F2"
  ink: "#26292B"
  muted: "#5A5F58"
  faint: "#6E6A5C"
  line: "#D9D3C2"
  line-soft: "#E8E3D4"
  line-mid: "#C7C1AE"
  line-dark: "#454A4D"
  pine: "#2E4A3B"
  leaf: "#7A9A3C"
  clay: "#B4603A"
  moss: "#B7C95A"
typography:
  display:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "clamp(32px, 4.4vw, 52px)"
    fontWeight: 600
    lineHeight: 1.12
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontWeight: 700
    letterSpacing: "-0.02em"
  title:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "20px"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.015em"
  body:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
  label:
    fontFamily: "'IBM Plex Mono', ui-monospace, monospace"
    fontSize: "12px"
    fontWeight: 400
    letterSpacing: "0.16em"
rounded:
  none: "0"
spacing:
  gutter-desktop: "32px"
  gutter-phone: "18px"
  container: "1240px"
components:
  button-primary:
    backgroundColor: "{colors.pine}"
    textColor: "{colors.paper}"
    rounded: "{rounded.none}"
    padding: "0 26px"
    height: "46px"
  button-primary-hover:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
  button-outline:
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "0 26px"
    height: "46px"
  button-outline-hover:
    textColor: "{colors.clay}"
  viewer-button:
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    size: "40px"
---

# Design System: Understory Design Studio

Scanned from `assets/css/site.css` and the rendered pages (November 2026). The tokens above are
normative; `site.css` `:root` is their source of truth. The North Star name below is a working
title until the owner confirms it.

## Overview

**Creative North Star: "The Drawing Set"**

The site is laid out like a landscape architect's sheet set: a warm paper ground, ink linework,
a title-block voice for labels, and drawings presented whole, the way they would be pinned up
at a review. Headings are set in the wordmark's face, IBM Plex Sans Bold tracked tight, so the
titles and the brand speak in one voice; the same sans at regular weight carries the reading; a
monospace small-caps voice does the annotating (sheet labels, dates,
figure numbers, viewer counters), the way lettering does on a drawing.

Density is calm and generous. Colour is almost entirely paper and ink; pine is the working
accent, clay marks the active and hovered state, and moss appears only on the dark footer. The
brand mark's three arcs (pine, leaf, clay) also colour the rule motif and the current-tab marker
on paper. Depth is flat: sheets are separated by ink rules and tonal panels, not by shadows.

**Key Characteristics:**
- Paper-and-ink palette with plant- and soil-derived accents (pine, leaf, clay, moss).
- The square–line–circle rule motif and brand mark, from the owner's design board; on paper the
  motif takes the mark's colours in order (pine square, leaf line, clay ring).
- Square corners everywhere; 1px ink rules as the structural line.
- Drawings and boards shown whole, zoomable, never cropped.

## Colors

A warm, low-chroma paper-and-ink palette with accents taken from landscape materials.

### Primary
- **Pine Ink** (pine): links, the primary button, labels on paper, figure numbers in the
  portfolio book, focus rings, the square of the rule motif and of the current-tab marker, and
  the outer arc of the brand mark on paper. The colour of the work's voice.

### Secondary
- **Fired Clay** (clay): hover and active states, the ring of the rule motif and of the
  current-tab marker, and the inner arc of the brand mark. Apart from those marks it signals
  "this is where you are / what you're touching", rarely anything else.
- **Leaf** (leaf): the brand mark's middle arc, and the line of the rule motif and of the
  current-tab marker on paper. Decorative marks only: at 2.8:1 on paper it never carries text.

### Tertiary
- **Spring Moss** (moss): on the dark footer only (links on hover, focus ring, the outer arc of
  the footer's brand mark). Never on paper, where it fails contrast.

### Neutral
- **Drafting Paper** (paper): the page ground, and every page's `theme-color`, so a phone's browser
  chrome matches the paper nav.
- **Trace Panel** (panel): viewer and card surfaces, one tone step above paper.
- **Drawing Ink** (ink): body text, headings, structural rules, the dark footer field.
- **Graphite** (muted) and **Faded Graphite** (faint): secondary text and labels; faint is
  tuned to 4.75:1 on paper (and overridden to #A6A291 on the dark footer).
- **Rule tones** (line, line-soft, line-mid, line-dark): hairlines inside components, from
  quietest to strongest.

### Named Rules
**The Paper-and-Ink Rule.** At least nine-tenths of any screen is paper, ink or the work itself;
the accents are signals, not fields.

**The Clay Means Here Rule.** Clay is reserved for hover, active and current state, and for the
logo-coloured marks (the brand mark's inner arc, the ring of the rule motif on paper and of the
current-tab marker), where it always sits beside pine and leaf.

**The Logo Colours Rule.** Pine, leaf and clay appear together, in the mark's order, only in the
brand mark, the rule motif on paper and the current-tab marker. The dark footer's motif keeps its
single tone. Adopted November 2026 from Sam Kremer's portfolio feedback, approved by the owner.

## Typography

**Heading and Body Font:** IBM Plex Sans (with system-ui, sans-serif), the wordmark's face
**Label/Mono Font:** IBM Plex Mono (with ui-monospace, monospace)
**Files:** self-hosted latin subsets in `assets/fonts/` (SIL OFL 1.1, `OFL.txt` beside them):
Sans 400/500/600/700 and Mono 400/500, declared at the top of `site.css` with `font-display: swap`
and Google Fonts' latin `unicode-range` (so ← → ↗ ⤢ ✕ come from the system font). Every page
preloads Sans 400 and Mono 400. No third-party font requests.

**Character:** One engineered sans in two registers: bold and tracked tight for titles, the way
the wordmark sets "Understory", and regular for reading. A mono that behaves like drawing-sheet
lettering does the annotating.

### Hierarchy
- **Display** (600, clamp(32px, 4.4vw, 52px), 1.12, -0.025em): the home hero statement only. It
  is a full sentence, so it takes the semibold weight.
- **Headline** (700, 24–60px, -0.02em): page and section titles (`.h-head`).
- **Title** (700, 17–20px, -0.01 to -0.02em): project card and lightbox titles; Home's featured
  project title (an h2) runs at 30px under its full-width plan, and the Projects lead card's at 28px. The footer name (26px) and brand (18px) use the same face at -0.02em.
- **Body** (400, 14.5–18px, ~1.6): reading text, held to about 66–68ch. A project page's
  summary sentence, under its title, runs larger: 19px ink (17px on phones), 1.55, 60ch.
- **Notes and captions** (400, sans, muted): a sentence-length note under a heading is
  `.note-sans` (15px, 1.55); figure and model captions are `.fig-cap` (13.5px, 1.5), as the book
  sets them. Only a figure number in a caption would take the mono, in pine; `.mono-note` stays
  for short labels and counts ("10 projects, newest first.").
- **Label** (400–500, 9–12px, 0.06–0.24em tracking, uppercase): the annotation voice: kickers,
  meta labels, nav links, counters, dates, captions' figure numbers.

### Named Rules
**The Wordmark Rule.** Headings are set in the wordmark's face, IBM Plex Sans Bold at -0.02em, so
a title and the brand read as one voice. Source Serif 4 was retired in November 2026 (Sam
Kremer's feedback, approved by the owner) and is no longer loaded. Don't bring a serif back for
headings.

**The Title Block Rule.** Monospace is the annotation voice of a drawing set: labels, dates,
counters, figure numbers. It is never used for running text or headings.

## Layout

One centred container (1240px) with 32px gutters, 18px on phones (640px breakpoint). Grids use
`auto-fit` with generous minimums (cards ~270–340px, meta cells 165px) and
collapse to one column on phones (the project meta grid keeps two, with its last cell, the
software, across both; an empty field is left out, not shown as "—"); a minimum is written `min(<n>px, 100%)` so a track never outgrows
a 320px screen. Sections are framed by a full-width ink rule or the rule motif
above a header row (`.rowhead`: title left, a mono link right). The nav is sticky: a 96%-opaque
paper bar with a light backdrop blur and a hairline underneath. It is the only sticky element.
In-page anchors (`#contact`, `#skills`) land 18px below it: `html` takes `scroll-padding-top` from
the nav's measured height (`--nav-h`, set by `site.js`; 66px, 53px at 476–640px, 96px on two rows
at 474px and below), so sections carry no scroll margins of their own. The jump glides (`scroll-behavior:
smooth`) only for visitors who haven't asked for reduced motion.

On Home the featured project is the page's one full-width picture: its whole master plan at the
container width (never cropped), with the title in the left third and its sentence in the right
two-thirds underneath (one column on phones), so it doesn't repeat the hero's text-and-picture split.
Home ends on contact, not a list: on the panel ground under the rule motif, "Get in Touch" sets the
email address as a Headline-weight link (700, clamp(28px, 4.4vw, 56px)) in the left two-thirds, with
one ink line and the Résumé and Portfolio book links in the right third (one column on phones).

On Projects the grid is the only control. From 960px it is three even columns and the first card
(the capstone, by sortKey) takes the first row: its cover across two columns, and in the third
its type, a 28px title with the year, the sub line and the summary's opening sentence (16px,
ink); the other nine fill an even 3×3. Below 960px every card is the same size and the sentence
is hidden.

A project page opens on its title (clamp(28px, 5vw, 56px), no kicker; an unlisted project's
badge sits on its own line under it), the summary sentence, then the meta grid. The figures cut
from the boards (the blowout's gallery) follow under "Drawings", one column at their own size
(at most 960px, never upscaled; each holds its box from the `w`/`h` its JSON entry gives, so nothing
moves as they arrive), each with its caption; a figure whose drawing is already on the
page is left out. They come straight after the meta grid when the drawing set is empty or a
single portfolio spread (which then follows them as "Portfolio spread", without its one tab),
and after the drawing set otherwise. Proctor's master plan appears once, as the inline sheet
under the meta grid. The written sections are rows: the heading (22px) in the left third, the
text (64ch) in the right two-thirds, each under a 1px line (one column on phones).

About opens on the name (no kicker) and one 18px ink sentence that says what he is, where, and
the degree. Its top row and its body share one grid (`auto-fit`, 320px minimum, 56px gap), so
the page keeps two left edges: the name, the headshot and Recognition on the first; contact and
the bio on the second. "Get in touch" and "Recognition" are 22px headings over a 1px ink rule,
not mono labels, and the headshot carries no caption. Below 760px, where that grid is one
column, the bio comes before the headshot and Recognition (`.about-bio`), so a phone reads name,
contact, bio. The résumé's Download PDF ↓ and Print are the standard primary and outline buttons
(44px tall). Under the sheet, the tools section (`#skills`, the target of skills.html) is headed
"Tools and fluency", with one 16px ink sentence (sans, not mono) over the graded tool lists; the
sheet's own mono "Skills" heading stays the only "Skills" on the page.

## Elevation & Depth

Flat by default. Depth comes from tone (paper → panel), ink rules and the dark footer field.
The only shadow is the résumé sheet sitting on its desk (`0 2px 14px rgba(38,41,43,.14)`), which
is a literal sheet of paper; the compare-slider handle carries a 1px ink ring.

### Named Rules
**The Pinned-Up Rule.** Surfaces are sheets on a wall: flat, ruled, never floating.

## Shapes

Square corners throughout; the only circles are the motif's circle, the compare-slider handle
and the nav indicator. Borders are 1px (1.5px on the outline button and the thick motif).
Diagonal hatching marks placeholders.

## Components

### Buttons
- **Shape:** square (0 radius), 46px tall.
- **Primary:** pine fill, paper text, IBM Plex Sans 500 15px, 26px side padding.
- **Hover / Focus:** fill shifts to ink; 2px pine focus ring offset 3px.
- **Outline:** 1.5px ink border; hover turns border and text clay.
- **Hover is for mice, press is for everyone.** Every hover colour sits behind
  `@media (hover: hover) and (pointer: fine)`, so a tap never leaves a clay button behind, and has
  an `:active` twin that shows the same colour only while a finger or button is down. The browser's
  tap flash is off (`-webkit-tap-highlight-color: transparent`). Viewer buttons, drawing tabs and
  chips have no hover; they press to clay (border and text), like the outline button.
- **Press give:** buttons, viewer buttons, tabs and chips also scale to 0.97 while pressed and
  return over 160ms on `cubic-bezier(0.23, 1, 0.32, 1)`; pointer presses only (`:not(:focus-visible)`,
  so a Space-key press stays still), and none under reduced motion, where the colour remains.
- **Mono link:** uppercase mono 12px pine link with an arrow ("View project →"). The arrow says
  where it goes: → opens a page, ↓ downloads a file ("Download PDF ↓", "vCard ↓"), ↗ leaves the site.
- **Tap targets:** on phones and on touch tablets (`pointer: coarse`), mono, footer and contact links
  take a 24px tap height (5px above and below) and range inputs 28px. The nav tabs are already 45px
  tall from 641px, so only the phone nav takes its compact form.

### Chips (filters)
- **Status:** retired from the Projects page (November 2026). With one type per project, 8 of the
  9 chips returned a single card, so the grid itself is the only choice; each card still names
  its type. The code is dormant behind `SHOW_FILTERS = false` in `work.html` (and `.chip` stays in
  `site.css`); bring it back only with 3–4 owner-defined groups that each hold several projects.
- **Style (dormant):** uppercase mono 12px, no fill, 2px underline; the active chip is ink with a
  clay underline.

### Cards / Containers
- **Corner Style:** square.
- **Background:** paper; covers sit in a 4:3 panel frame with a 1px line border.
- **Shadow Strategy:** none.
- **Border:** a 1px ink rule on top of each card.
- **Internal Padding:** 16px above the content.
- **Year label:** mono, always on one line; the title beside it wraps instead. Empty fields are
  left out of the card's `·` lines rather than leaving a stray separator.
- **Title:** a real heading (an h2 on Projects, an h3 under Home's "Recent Work" h2), styled by
  `.card-title` alone. The cover is decorative beside the title (`alt=""`), so the card link's name
  is its own text, read once in the order it is seen (it must hold the visible text, WCAG 2.5.3).

### Navigation
- Wordmark left (brand mark + "Understory / Design Studio"), four mono links right; the current
  tab carries the square–line–circle indicator in the mark's colours (pine square, leaf line,
  clay ring). On a mouse or touch click it glides to the next tab, moved by transform over 200ms on
  `cubic-bezier(0.23, 1, 0.32, 1)`, and the page loads as it lands; the new page eases the tab label
  from clay to ink over 200ms (the marker itself doesn't replay). Keyboard Enter navigates at once,
  with no glide, and reduced motion skips the glide and the settle. The current tab is
  `aria-current="page"` (on a project page and the portfolio book, that is Projects).

### Viewers (signature component)
Drawing set, portfolio book, capstone booklet and lightbox share one viewer: an ink-bordered
panel, a toolbar of 40px square buttons, a pan-and-zoom stage, a mono page counter
("PP. 12–13 / 35"), a scrubber and a caption footer.

- **At fit the stage is part of the page.** It takes no drag at 100% or less, so a finger on it
  scrolls the page and the phone's own pinch works (`touch-action: pan-y pinch-zoom`), and no zoom
  badge shows (at 100% or less there is nothing to pan, and on a phone the stage hugs the drawing).
  Zoomed in, the stage is `.zoomed`, keeps every gesture, one pointer pans it and the badge appears
  ("140% · drag to pan"); a second finger never takes over the drag. The full-screen lightbox's
  stage always keeps its gestures, so the page behind it never scrolls.
- **Motion:** the zoom buttons and FIT ease over 180ms on `cubic-bezier(0.23, 1, 0.32, 1)` (none
  under reduced motion). A new page or drawing (arrows, scrubber, tabs, keys) lands at fit with no
  transition, never shrinking from the last one's zoom.
- **Controls are not content:** viewer buttons, tabs and chips take `touch-action: manipulation`
  (quick taps flip pages, not zoom the page) and `user-select: none`; page zoom stays enabled.
- **Keyboard:** each stage is focusable (Tab reaches it after the toolbar; a 2px pine ring just
  inside its edge, drawn as an overlay above the drawing so a zoomed sheet never hides it, under
  the badge, which reads "140% · drag or arrow keys to pan" while the stage has keyboard focus)
  and is a group named "Drawing viewer. Arrow keys pan when zoomed, plus and minus zoom, 0
  fits." Zoomed in, the arrow keys pan a tenth of the stage; at fit, ← and → turn the viewer's own
  pages (drawing set, portfolio book, booklet); + or = and − zoom, 0 fits. None of it animates
  (no motion on keyboard actions). The portfolio's page-wide ← and → skip form fields and a
  zoomed book. In the full-screen lightbox, Tab cycles its buttons and its stage.
- **Names and states:** the arrow buttons are named "Previous/Next drawing" or "Previous/Next
  spread" (the glyph is `aria-hidden`), FIT is "Fit to view", Close is "Close"; the counters
  ("DWG 01 / 08", "PP. 12–13 / 35") are polite live regions, and the drawing tabs carry
  `aria-pressed` with their active state.
- **Phones:** the drawing set's toolbar sits on one row from about 383px (10px padding, 6px gaps),
  and its stage takes the drawing's own shape (the `w`/`h` of its JSON entry, at most 70svh, set
  before the drawing arrives; without them the image's own ratio once it loads, a spread's
  3400 / 2310 until then) instead of a fixed-height letterbox. It re-shapes and lands at fit
  when the width changes, not when a phone's toolbar slides away.
  The portfolio and booklet toolbars keep their spacing: their counters change width between
  single pages and spreads, so a tighter bar would flip between one and two rows as you page.
- **Loading:** the capstone booklet waits on its intro ("Open booklet") until the reader comes
  within 200px of it or uses its arrows or scrubber; it then reads "Rendering…" until the spread
  is drawn, with "Loading part n / 11" in the toolbar. A part that fails twice shows the failed
  box and its Retry button.
- **Portfolio book (portfolio.html):** the page opens on the book, not on furniture: no kicker,
  the H1 at the shared page-title size, one `.note-sans` sentence that carries the years, and the
  plain primary button "Download PDF ↓ (16 MB)" (the one under the book reads "Download PDF ↓").
  From 641px the stage is `max(min(68vh, 640px), min(100svh − 455px, 1000px))` (`− 395px` from
  960px, where the header is one row): never smaller than the shared tall stage, and on taller
  screens it grows only as far as keeps the scrubber and caption bar in the first view (725px at
  820×1180, 685px at 1920×1080, 1000px from 1395px of height), still inside the 1240 container.
  On short desktop screens (1440×900 and below) the shared size wins, so the whole viewer fits one
  screen under the nav but its foot starts below the first view. Under the viewer, a row of the drawing
  set's `.thumbbtn` jumps opens each project's first spread; the buttons come from spreads.json,
  one per "NN Title" caption number (its first spread), so they follow the Contents page, and the
  current project's is active (none on the cover, Other work or the colophon); a jump made while
  the stage's top is above the screen (a phone, scrolled to the row) brings the stage back, just
  under the nav (instantly under reduced motion). A project spread's
  caption links to its project page, with a mono "Project page →" at the right of the foot
  (matched by title prefix against the listed projects); other captions stay plain text.

The Projects page's blowout is the lightbox panel without a stage: it opens on the project's
first figure, then the mono meta line, a mono link to the full project page (named for what
that page adds, e.g. "Project page: compare slider + drawings →"), the summary, a "What it
shows" heading over the focus list, and the remaining figures. Its body is focusable and
scrolls from the keyboard (the scroll keys also work from the toolbar buttons).

Both lightboxes (the blowout and Proctor's full-screen master plan) behave as modal dialogs: on
open, focus moves to the close button, Tab cycles inside, the page behind stops scrolling, and
Escape or Close hands focus back to the control that opened it. Opened with a mouse or a tap, a
lightbox eases in: the scrim fades and the sheet rises 6px into place over 200ms on
`cubic-bezier(0.23, 1, 0.32, 1)` (`@starting-style`; older browsers open at once). Closing,
stepping to the next project and a keyboard open (`data-instant`) are instant, and reduced motion
gets no entrance.

### Compare slider
Two registered images wiped by a draggable divider with a round ink-ringed handle; keyboard
operable through a range input (the arrows move the divider 5% a press, Shift+arrows 1%; Page
Up/Down and Home/End are the browser's own); mono tags name each side. Each view's `w`/`h` in the JSON gives
the stage its shape before the images arrive, and its width: the stage is as wide as the screen's
height allows, `min(100%, (100svh − 150px) × w/h)`, centred, so both plans show whole under the
nav (about 838×750 at 1440×900); the range input below takes the same width so its thumb tracks
the divider.

### Loading and failure states
- **No jumps:** what the content JSON fills holds a viewport of height while empty: the Projects
  `#grid`, and on a project page the title (`#p-title`), under which everything else it fills
  sits (the empty meta grid takes no room). Nothing on screen moves when the content lands, and
  sections that may not apply (drawing set, prev/next) start hidden.
- **Content that can't load:** one bad project file costs only that project. With nothing to
  show, the Projects grid (and a project page, under an h1 "Projects didn’t load") gives the mono
  note "Projects didn’t load." and two mono links, "Flip through the portfolio book →" and
  "Download PDF ↓ (16 MB)". The same note is the `<noscript>` fallback on the Projects and
  project pages; the portfolio's stage offers just the PDF link.
- **Unknown project:** h1 "Project not found" and "All projects →"; the page is `noindex`.
- **A missing image:** a portfolio spread shows "Couldn’t load this spread" and a Retry button
  (44px) in the stage; blowout figures and model photos take the covers' hatched placeholder,
  saying "Figure didn’t load" or "Model photo didn’t load"; a card cover that fails says "Cover
  didn’t load" and a drawing-set sheet "… drawing didn’t load" ("coming soon" is only for a cover
  the data doesn't have yet).

## Do's and Don'ts

### Do:
- **Do** take every colour, font and size from the `:root` tokens in `site.css`.
- **Do** show drawings whole and legible, with their figure numbers and captions.
- **Do** keep square corners and 1px ink rules as the structural language.
- **Do** keep text contrast at or above 4.5:1 (faint is the floor on paper).

### Don't:
- **Don't** add rounded cards, drop shadows on resting surfaces, gradients or glass panels.
- **Don't** put moss on the paper ground, or set text in leaf (2.8:1 on paper).
- **Don't** set running text or headings in monospace.
- **Don't** crop, stretch or upscale a drawing until it blurs.
