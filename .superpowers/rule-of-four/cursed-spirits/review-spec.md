# review-spec.md — Phase 3 summary and adjudication

Four read-only reviewers ran on the finished tree (`feat/cursed-spirits`). Lane evidence for Phase 3 is Main's single `qualityGate` batch (first attempt blocked by the stale MOC metrics, which is a Main-owned doc obligation; the verification re-run happens after the Phase-4 fix wave).

## Verdicts as delivered

| Reviewer | Scope | Verdict |
|---|---|---|
| ReviewBlock1 | assets / resources / provenance (R2, R22, R23-data, R28) | **GO** with a required docs fix |
| ReviewBlock2 | server core (R1, R3–R5, R11–R17, R20, R24, R25) | **GO** — "correctness correct", no P0/P1 |
| ReviewBlock3 | client render stack (R2-render, R21, R24-visual) | **GO** (static half; live half → MANUAL_VERIFY in the lane) |
| ReviewBlock45 | spawn + integration + cross-block (R6–R10, R16-live, R17-prog, R18, R19, R26–R30) | **NO-GO until the sweep fix + my integration scenarios land** |

## Findings and Main's verdicts

Legend: **CONFIRMED** (fix scheduled/done), **REJECTED** (with reason), **MANUAL** (live-pass check).

### Block 1
1. P2 `docs/PROVENANCE.md:128` guzzler_death sha256 truncated (60 hex, `b8ac` lost) — **CONFIRMED, FIXED** (commit `99347a4`; whole 43-entry manifest re-verified, 0 mismatches).
2. P2 `docs/PROVENANCE.md:141` walking_bed_scream sha256 mistyped — **CONFIRMED, FIXED** (same commit).
3. P3 contract test does not pin manifest hashes nor the absence of extra files — **CONFIRMED (minor)**: the hash class is now covered by the manifest validation above; the "no extra files" assert is added to the Block-1 contract test in the same fix wave.

### Block 2
4. P2 `block-2-report.md` committed truncated (`...[truncated 7375 chars]`) — **CONFIRMED**: ServerCore re-emits the missing tail (R4 re-pin, per-scenario red-proofs, lane counts); R27 cannot close for that block until it lands.
5. P3 `CursedSpiritVariantTest` needs `Bootstrap` to pass standalone — **CONFIRMED** → `FixSmallServer`.
6. P3 STRIKE applies without a reach re-check (LOS drop deliberate) — **CONFIRMED** → `FixSmallServer` adds a strike-time reach guard; the no-LOS-recheck behaviour stays documented.

### Block 3
7. P3 renderer ctor bakes the rig map twice (18 models for 9) — **CONFIRMED** → `FixClientP3`.
8. P3 `clipKeysUniquePerVariant` cannot fail by construction and duplicates the rig test's count — **CONFIRMED** → `FixClientP3` replaces it with independent per-variant clip-count literals.
9. P3 no value-level pins (clip lengths) → a halved keyframe time stays green — **CONFIRMED** → `FixClientP3` pins exact `lengthInSeconds()` per clip.

### Block 4 + 5 + cross-block
10. P1 spawn GameTest radius sweep discards neighbouring arenas' bodies (9 lane failures) — **CONFIRMED, FIXED** (`e19f32c`, owner-scoped cleanup, oracles unchanged).
11. P2 premise/sweep asserts still count by type over 56 blocks (> production 48) — **CONFIRMED** → `FixSmallServer` scopes them to owned bodies / the production radius.
12. P1 missing live scenarios: R8 resonance, R7 mis-aim, R16 ranking — **CONFIRMED** → `FixIntegrationOracles`.
13. P2 Hairpin oracle accepts `HP drop OR marks consumed`, and production returns SUCCESS with zero nails — **CONFIRMED** → strict HP delta + embedded-nail premise.
14. P2 remnant proof lacks the tag-membership assert, all-tier repetition and the orphan-nail sweep — **CONFIRMED** → `FixIntegrationOracles`.
15. P2 Todo Black Flash band (1.0–40.0) cannot discriminate the forced bonus — **CONFIRMED** → forced-vs-unforced differential.

## Accepted limits (re-stated, not defects)

Spawn light gate follows `PathfinderMob` walk-value (dim 8–12 allowed; glowstone refuses) — recorded, not "fixed" (re-implementing vanilla's rolled darkness was explicitly rejected at plan time). FLOATING_CURSE glides (no walk clip) and is rigged from `Modelcurse_ghost`. `WISTIVER.GRAVE` not shipped (no driver). No VFX. Balance is first-pass. Stagger floor 1 tick. `VesselBoundaryTest` carries a scoped `render → cursedspirit` exception.

## MANUAL_VERIFY (Main's live MCP pass — expected result named)

- R23 sound firing/coherence (per tier: ambient/hurt/death, scream where authored) — audible, matched to the variant.
- R21 animation timing/weight and R24 hitbox visual fit — attacks read, no floaty/cut motion; aimed hits land where the model is.
- R19/R30 world spawn feel + caps — night census ≤ `MAX_SPIRITS_NEARBY` per area, TPS ≥ 18, zero `jujutsumod` ERROR lines.
- R26 multi-variant simultaneity — ≥3 variants of one tier on screen with their mechanics live.
- Per-tier acceptance sequence T1 → T2 → T3 → combined (recorded deviation D8).
