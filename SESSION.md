# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: fix/persistence-nail-lifecycle-docs-sync
- Base: main at 2faa275
- Implementation commits:
  - 9ee028d — persist character selection and one-time starter claims
  - 8739116 — bound embedded nail lifecycle and replace the global entity scan
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

Final verification for this branch must include:

- ./gradlew build --no-daemon --rerun-tasks
- python3 tools/audit_docs.py
- git diff --check

Do not claim in-game rendering or gameplay feel until a real client smoke test is performed.

## Next product steps

1. In-game smoke test selection persistence, one-time starter claims, nail TTL/cap, directed Hairpin, and mass Hairpin.
2. Decide the second character only after the current Nobara slice is stable.
3. Replace temporary ProjectJJK placeholders when original assets are available.
4. Resolve Rich-Modern provenance before any public distribution.
