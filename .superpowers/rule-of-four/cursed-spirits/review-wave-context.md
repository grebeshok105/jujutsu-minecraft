# Review wave (Phase 3) — shared context for reviewers + QA

## What was built

`jujutsu-minecraft` (`D:/WorkFlow/jujutsu-minecraft`), branch `feat/cursed-spirits`, feature: three hostile cursed-spirit gameplay tiers (`lesser_cursed_spirit` / `cursed_spirit` / `greater_cursed_spirit`) with presentation variants imported from the Sons of Sins asset pack, natural spawning, combat-core integration (Nobara / Todo / Megumi), tests + red-proofs, docs/provenance.

Authoritative artifacts (read the ones your scope needs):
- `.superpowers/rule-of-four/cursed-spirits/implementation-plan.md` (rev 3; frozen contracts, decision records D1–D8, ownership matrix)
- `.superpowers/rule-of-four/cursed-spirits/plan-review.md` (Phase 1.5 verdicts, accepted/rejected findings)
- `progress.md` (phase status, deviation log, orchestration rules), `live-protocol.md`, `port-map.md`, `integration-recipes.md`
- Worker reports: `block-1-report.md` … `block-4-report.md` (+ `block-3-report.md` deviations), `block-5` evidence is landing.
- Scout evidence: `scout-1-report.md` … `scout-4-report.md` (`scout-4-report.md` carries `R1..R30`, the traceability anchor).

## Your job

Read-only audit of the artifacts in your scope. Verify claims instead of trusting them: re-run the cheap checks you need (compile, JUnit, audits), spot-check that `Факт:` lines match reality, read the actual diff, and hunt real defects (spec violations, structural damage, regressions, missing/weak tests, unreachable code, silently narrowed scope).

Findings contract: priority `P0..P3` + `file:line` + what breaks + counter-proposal + confidence. Verdict per requirement (`R* → PASS/FAIL/MANUAL_VERIFY_REQUIRED` with evidence) and one block-level verdict for your scope: **GO / NO-GO / MANUAL_VERIFY_REQUIRED** (`MANUAL_VERIFY_REQUIRED` is a normal outcome — name the manual check and its expected result).

Rules of engagement:
- **Do NOT run the in-game lane** (`runGameTest` / `runClient` / MCP dev lane) — it belongs to Main and a batch result will be handed to you as `build/test-results/gametest/junit.xml` evidence. Compile + JUnit + audits are yours to run.
- Do not edit project files. Write your report to `.superpowers/rule-of-four/cursed-spirits/<your-report>.md` and summarise it in your final answer.
- Prefer precision over volume: a short list of real, reproducible findings beats a long list of style notes.
- Known accepted limits (do not re-report as new): spawn light gate follows `PathfinderMob` walk-value (dim 8–12 allowed, glowstone refuses); no VFX for cursed spirits; FLOATING_CURSE glides (no walk clip) and its rig comes from `Modelcurse_ghost`; `WISTIVER.GRAVE` deliberately not shipped; balance numbers are first-pass and tunable in `CursedSpiritProfile`/`CursedSpiritVariant`; stagger floor is 1 tick; `VesselBoundaryTest` gained a scoped `render → cursedspirit` exception (documented).
