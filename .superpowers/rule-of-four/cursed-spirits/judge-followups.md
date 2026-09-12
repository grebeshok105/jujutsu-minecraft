# Fable-judge follow-ups (Phase 4 adjudication)

Judge verdict: all load-bearing claims VERIFIED (C1, C2, C4–C11); C3 (the single gate command) UNSUPPORTED for lack of an on-disk artifact — closed by teeing the final `qualityGate` log into `gate-log.txt` in this directory. Four findings below; each resolution is either a code change with evidence or an explicit acceptance.

## F1 (P2) — NATURAL + dark + below-cap → allow had no in-world oracle

**Resolution: proven by a solo-lane run, then the shared-level determinism switch stays.**

Method (one-off, restored afterwards): the spawn class's light halves were temporarily set back to `EntitySpawnReason.NATURAL`, and `src/gametest/resources/fabric.mod.json` was temporarily trimmed to entrypoint `CursedSpiritSpawnGameTests` alone, so no sibling arena could pollute the crowd radius.

```
========= 2 GAME TESTS COMPLETE IN 1.404 s ======================
BUILD SUCCESSFUL
  minecraft:always_pass ok
  jujutsumod-gametest:cursed_spirit_spawn_gate_refuses_light_peaceful_and_crowd_but_allows_dark  ok
```

With no neighbours, `NATURAL + dark + below-cap → allow` is green — the gate itself is sound. The shipped test calls the light halves with `SPAWNER` so the same assert stays deterministic when 88 arenas share the level; the cap half still runs on `NATURAL` (its refusal can only be reinforced by neighbours, never masked), and the pure `belowCap`/`belowLocalCap` predicates own the boundary values in `CursedSpiritSpawnRulesTest` (5/5).

## F2 (P3) — tautological premise after `owned.clear()`

**Resolution: removed.** The premise could not fail by construction; a comment now records why there is no "starts clean" assert and why the light oracles cannot see sibling bodies. The meaningful half (owned bodies discarded at the sweep tick, owner-scoped) is untouched.

## F3 (P3) — R4 burst floor sat below the walk-speed row (0.24)

**Resolution: differential oracle.** The test no longer compares the sampled peak against a flat floor. It freezes the peak reached *before* the victim took damage as the body's own baseline and asserts the strike adds at least 0.10 on top:

> peak ≥ baseline + 0.10 — `src/gametest/java/jujutsu/mod/gametest/CursedSpiritGameTests.java:154-160`

Standing body: baseline 0, observed peak ≈ 0.19 → green. Walking body (future geometry drift): baseline ≈ 0.24, burst ⇒ peak ≈ 0.43 ≥ 0.34 → green because the burst is still there; burst removed ⇒ 0.24 < 0.34 → red. The check now fails for exactly the defect it claims to defend.

## F4 (P3) — acquisition poll window 30 → 60 ticks

**Accepted, recorded.** The oracle under test is strike reach; the scan schedule is scaffolding. If a late scan ever appears past tick 60, that is a real investigation, not another window extension — noted here so the next author does not widen it again.

## Watch items carried into the live pass

R19-soak numbers, R26 multi-variant simultaneity, R29 per-tier sequence, R30 TPS/log silence, and the five manual-only halves (sound firing, animation feel, silhouette distinctness, hitbox-vs-render fit, world-spawn feel) — the judge's section 4 list; the live MCP pass owns them.
