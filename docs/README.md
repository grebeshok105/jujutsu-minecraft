# Current Documentation

This directory contains current operational documentation plus the approved design notes whose rationale is still worth keeping. Point-in-time material — research, pre-merge reviews, handoffs, mockups, and visual targets — is not kept here; when such a document's findings are still true, they are folded into the register or the relevant current doc instead of being archived.

## Source-of-truth order

Owned by the root AGENTS.md under "Documentation Authority". If two documents disagree, use the higher source there and update the lower one.

## Files in this directory

- BUILDING_IN_SANDBOX.md — build guidance for normal and restricted containers, and the owner of the client-smoke checklist.
- KNOWN_ISSUES.md — live risk and debt register, and the owner of accepted-tradeoff rationales.
- MCP_1_21_8_PORT_SPIKE.md — decision record for the MCP 1.21.8 upstream port spike (issue #43): verdict, evidence, and the dev-only bridge facts.
- MCP_DEV_CONTROLS.md — approved design for the dev-only MCP control surface (issue #43 slice 2): tool table, fixture-reset order, autonomous entry recipe.
- PROVENANCE.md — ProjectJJK placeholder permission and replacement policy.
- THIRD_PARTY_NOTICES.md — retained third-party notices that apply to current runtime assets.
- TODO_BOOGIE_WOOGIE.md — approved design for the original Aoi Todo / Boogie Woogie slice (aimed swap, feint, melee, Black Flash bridge). Kept for rationale, superseded by the code where they differ.
- TODO_STONE_REWORK.md — design contract for the stone rework: marker system removal, the thrown stone (V / Shift+V), and the triple cyclic swap (B → Shift+B).

## Maintenance

Run the documentation validator after changing code-derived facts or docs:

```bash
python3 tools/audit_docs.py
```

The validator checks that only current docs remain, validates local links, rejects stale historical references, and compares current Codebase Codex metrics with the source tree. CI runs the same audit before the Java 21 build.
