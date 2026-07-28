# Current Documentation

This directory contains current operational documentation plus the approved design notes whose rationale is still worth keeping. Point-in-time material — research, pre-merge reviews, handoffs, mockups, and visual targets — is not kept here; when such a document's findings are still true, they are folded into the register or the relevant current doc instead of being archived.

## Source-of-truth order

Owned by the root AGENTS.md under "Documentation Authority". If two documents disagree, use the higher source there and update the lower one.

## Files in this directory

- BUILDING_IN_SANDBOX.md — build guidance for normal and restricted containers, and the owner of the client-smoke checklist.
- HIGH_PRIORITY_PLAN.md — the code-verified remediation plans for the two high-priority register entries: decoding limits for the curse-link options packet, and the runtime/world verification backlog. Point-in-time by design; delete it when both plans have landed.
- KNOWN_ISSUES.md — live risk and debt register, and the owner of accepted-tradeoff rationales.
- PROVENANCE.md — ProjectJJK placeholder permission and replacement policy.
- THIRD_PARTY_NOTICES.md — retained third-party notices that apply to current runtime assets.
- TODO_BOOGIE_WOOGIE.md — approved design for the Aoi Todo / Boogie Woogie slice, now implemented and merged. Kept for rationale, superseded by the code where they differ.
- TODO_COMPLETION_CHECKLIST.md — the finite plan for finishing Aoi Todo: what the 2026-07-27 audit confirmed, the eight defects it found, and the measurable definition of done. Point-in-time by design; delete it when its last item closes.

## Maintenance

Run the documentation validator after changing code-derived facts or docs:

```bash
python3 tools/audit_docs.py
```

The validator checks that only current docs remain, validates local links, rejects stale historical references, and compares current Codebase Codex metrics with the source tree. CI runs the same audit before the Java 21 build.
