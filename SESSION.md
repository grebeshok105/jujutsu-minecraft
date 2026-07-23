# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: fix/persistence-nail-lifecycle-docs-sync
- Base: main at 2faa275
- Change commits:
  - fix(character): persist selection and starter claims
  - fix(nobara): bound embedded nail lifecycle
  - test(nobara): verify indexed trap exclusion
  - docs(project): synchronize current source of truth
  - chore(project): add local documentation audit
- Product target: private play for one or two people

## Current product state

- Fabric 1.21.8, Java 21, mod id jujutsumod.
- Playable vessels: Nobara and None.
- N opens the single ClickGui product menu. The Neon Dashboard and Key V path are retired.
- Character selection is sent through SelectCharacterPayload and remains server-authoritative.
- Selection persists across reconnects/restarts through the Fabric Data Attachment API.
- The Nobara starter hammer, doll, and nails are granted once per player; re-selecting Nobara does not refill them.
- Nobara controls: R directed Hairpin, B mass Hairpin, Shift+R Self Resonance, Shift+B Nail Trap, hammer left click contextual melee.
- Transient combat presentation uses VfxCue → VfxDirector → NobaraVfxRecipes and shared director channels.
- Resonance intentionally changes the global server tick rate for hit-stop. This is accepted for the current 1–2 player target.
- Ordinary loaded embedded nails expire after 1200 ticks, are capped at 30 per owner, and are resolved through EmbeddedNailRegistry rather than level.getAllEntities().

## Asset and provenance decisions

- ProjectJJK placeholder models/assets are used with permission from the author and are intended to be replaced later.
- They are not automatically covered by the repository CC0 declaration.
- Rich-Modern-derived code/assets still need a provenance review before a public release.
- Do not remove the ProjectJJK placeholders as an unapproved cleanup; do not expand the imported set casually.

## Documentation authority

1. Current code and passing tests.
2. AGENTS.md for durable rules.
3. This SESSION.md for the active handoff.
4. Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md for current architecture.
5. docs/specs/2026-07-19-known-issues-and-tech-debt.md for live debt.
6. Dated research, reviews, plans, specs, mockups, and old handoffs are historical references.

Use docs/README.md for the archive map. Run python3 tools/audit_docs.py after documentation changes.

## Verification status

Completed on 2026-07-23:

- ./gradlew build --no-daemon --rerun-tasks — BUILD SUCCESSFUL, 30 tasks executed, all 19 custom verification programs passed.
- python3 tools/audit_docs.py — passed for 100 Markdown files and 61 historical records.
- git diff --check — passed.

A real client smoke test was not run, so rendering and gameplay feel remain unverified in-game.

## Next product steps

1. In-game smoke test selection persistence, one-time starter claims, nail TTL/cap, directed Hairpin, and mass Hairpin.
2. Decide the second character only after the current Nobara slice is stable.
3. Replace temporary ProjectJJK placeholders when original assets are available.
4. Resolve Rich-Modern provenance before any public distribution.
