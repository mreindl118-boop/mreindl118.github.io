# Vendored design skills

Copied from their public repositories on 2026-11-04 and kept unmodified, so they can be
refreshed by copying the same paths again. Each folder carries its upstream licence.
`understory-design/` is this repo's own skill and decides how the others apply here.

| Folder(s) | Upstream | Commit | Licence |
| --- | --- | --- | --- |
| `impeccable/` (SKILL.md, reference/) | https://github.com/pbakaus/impeccable, path `.claude/skills/impeccable/` | 9fadc41729c38fd47d7ae622b501558653f9d2e9 (skill v4.8.0) | Apache-2.0 (`impeccable/LICENSE`, `impeccable/NOTICE.md`) |
| `design-taste-frontend/` | https://github.com/Leonxlnx/taste-skill, path `skills/taste-skill/` | 12af471f627f866411b4bd785b19646d3672784c (tasteskill v2.1.5) | MIT (`design-taste-frontend/LICENSE`) |
| `emil-design-eng/`, `review-animations/`, `improve-animations/`, `find-animation-opportunities/`, `remove-animations/`, `animation-performance/`, `animation-vocabulary/`, `animate/`, `apple-design/`, `break-ui/`, `mobile-native/`, `prototype/` | https://github.com/emilkowalski/skill, path `skills/<name>/` | f1090bec277140d02ed202440185ad9d02ac1490 | MIT (`LICENSE` in each folder) |

## Left out on purpose

- **impeccable:** `scripts/` (the engine launcher downloads and runs a native binary, and the
  live-browser bundle needs it), the four subagents in `.claude/agents/`, and the hooks in
  `.claude/settings.json` that run the engine after every edit. The skill's documented
  fallback covers their absence. To add them later, copy `scripts/` back and install the hooks
  deliberately, with the owner's go-ahead.
- **taste-skill:** `evals/` and the repo's marketing assets. Its scripts are included; they
  spawn a local headless Chrome for inspection and call open image collections (Openverse,
  Wikimedia Commons, the Art Institute of Chicago, the Met, the Internet Archive, NASA) only
  when `assets.mjs` is run.
- **Emil Kowalski:** `animate-expo`, `write-swift`, `ask-sonner` and
  `pick-ui-library` are not (React Native, Swift, his React toast library and React component
  picks do not apply to this static HTML/CSS/JS site).
