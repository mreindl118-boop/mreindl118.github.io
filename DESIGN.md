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
- **Fired Clay** (clay): hover and active states, the active filter chip, the ring of the rule
  motif and of the current-tab marker, and the inner arc of the brand mark. Apart from those
  marks it signals "this is where you are / what you're touching", rarely anything else.
- **Leaf** (leaf): the brand mark's middle arc, and the line of the rule motif and of the
  current-tab marker on paper. Decorative marks only: at 2.8:1 on paper it never carries text.

### Tertiary
- **Spring Moss** (moss): on the dark footer only (links on hover, focus ring, the outer arc of
  the footer's brand mark). Never on paper, where it fails contrast.

### Neutral
- **Drafting Paper** (paper): the page ground.
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

**Character:** One engineered sans in two registers: bold and tracked tight for titles, the way
the wordmark sets "Understory", and regular for reading. A mono that behaves like drawing-sheet
lettering does the annotating.

### Hierarchy
- **Display** (600, clamp(32px, 4.4vw, 52px), 1.12, -0.025em): the home hero statement only. It
  is a full sentence, so it takes the semibold weight.
- **Headline** (700, 24–60px, -0.02em): page and section titles (`.h-head`).
- **Title** (700, 17–20px, -0.01 to -0.02em): project card, feature and lightbox titles. The
  footer name (26px) and brand (18px) use the same face at -0.02em.
- **Body** (400, 14.5–18px, ~1.6): reading text, held to about 66–68ch.
- **Label** (400–500, 9–12px, 0.06–0.24em tracking, uppercase): the annotation voice: kickers,
  meta labels, nav links, counters, dates, captions' figure numbers.

### Named Rules
**The Wordmark Rule.** Headings are set in the wordmark's face, IBM Plex Sans Bold at -0.02em, so
a title and the brand read as one voice. Source Serif 4 was retired in November 2026 (Sam
Kremer's feedback, approved by the owner) and is no longer requested from Google Fonts. Don't
bring a serif back for headings.

**The Title Block Rule.** Monospace is the annotation voice of a drawing set: labels, dates,
counters, figure numbers. It is never used for running text or headings.

## Layout

One centred container (1240px) with 32px gutters, 18px on phones (640px breakpoint). Grids use
`auto-fit` with generous minimums (cards ~270–340px, meta cells 165px, process steps 170px) and
collapse to one column on phones. Sections are framed by a full-width ink rule or the rule motif
above a header row (`.rowhead`: title left, a mono link right). The nav is sticky: a 96%-opaque
paper bar with a light backdrop blur and a hairline underneath.

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
- **Mono link:** uppercase mono 12px pine link with an arrow ("View project →").

### Chips (filters)
- **Style:** uppercase mono 12px, no fill, 2px underline; the active chip is ink with a clay
  underline.

### Cards / Containers
- **Corner Style:** square.
- **Background:** paper; covers sit in a 4:3 panel frame with a 1px line border.
- **Shadow Strategy:** none.
- **Border:** a 1px ink rule on top of each card.
- **Internal Padding:** 16px above the content.

### Navigation
- Wordmark left (brand mark + "Understory / Design Studio"), four mono links right; the current
  tab carries the square–line–circle indicator in the mark's colours (pine square, leaf line,
  clay ring), which glides to the next tab on click (honours reduced motion).

### Viewers (signature component)
Drawing set, portfolio book, capstone booklet and lightbox share one viewer: an ink-bordered
panel, a toolbar of 40px square buttons, a pan-and-zoom stage, a mono page counter
("PP. 12–13 / 35"), a scrubber and a caption footer.

### Compare slider
Two registered images wiped by a draggable divider with a round ink-ringed handle; keyboard
operable through a range input; mono tags name each side.

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
