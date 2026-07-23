# How to Add the Next Character

Status: CURRENT

Do not add a generic framework before a real second kit is approved.

For the approved character:

1. Write and approve the fantasy, combat loop, counterplay, controls, and VFX language.
2. Add the character id/model metadata and persistent starter-claim behavior.
3. Add server-owned action routing, validation, resources, and lifecycle state.
4. Add typed payload support without creating one receiver per visual effect.
5. Add CharacterRosterPanel data/card and localized strings.
6. Add CharacterVfxIds and CharacterVfxRecipes using VFX Core channels.
7. Register recipes through one explicit aggregate entrypoint once two characters exist.
8. Add deterministic unit tests plus GameTest/dedicated-server cases where world integration matters.
9. Run full build, docs audit, and real client smoke.
10. Update AGENTS.md only for durable decisions, SESSION.md for the handoff, and relevant Codex notes.

Extract CharacterDefinition/handler registries only where the second kit demonstrates repeated Nobara-specific branching.
