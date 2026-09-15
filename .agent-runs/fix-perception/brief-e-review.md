# Brief E — Integrated review (spec-axis mandatory)

RUN_ID: `fix-perception` · Worktree: `D:/WorkFlow/jm-wt-perception` (branch `fix/review-perception`) · Artifact: `.agent-runs/fix-perception/reviewer-e.md`

## What to review

`git diff 61ef797ffe193547c51e848dbcfff4639b6fa66a..HEAD` in the worktree — the full integrated diff of review-fixes for the cursed-spirit perception domain (Fabric mod, MC 1.21.8, Java 21).

## Spec axis (mandatory — check each against the diff)

Sources: `D:/WorkFlow/jujutsu-minecraft/audit/review-3pr/reviewer-pr89-perception.md` (F1–F4), `reviewer-pr89-grade-spawn.md` (F3–F4), and this task list:

1. Fear look inversion must negate the real look deltas (`LocalPlayer.turn(DD)V` args), not the smooth-camera time delta; inactive fear = zero behavior change.
2. Non-perceiver must not hear spirit step/fall/swim/thorns sounds — all `Entity.playSound` sinks routed per-perceiver.
3. Projectiles (arrow/trident/TNT) of a non-perceiver must neither damage the spirit nor deflect/stick — owner chain walked.
4. Non-perceiver sees no shadow, no F3+B hitbox, no fire flame.
5. Seam: interaction gates (damage both ways, swing, push, collide, setTarget) → `canInteract`; sensory (tracking/render/sound/VFX/look) → `perceives`.
6. Entity misc: day-roll only NATURAL/CHUNK_GENERATION; missing RollSeed → re-roll (not seed 0); `isAlive()` before `brain.tick`; `@Override` on finalizeSpawn; LookAtPlayerGoal rechecks perceiver.
7. New oracles non-vacuous; no weakened existing tests.

Out of scope by design (do not flag as missing): #90 п.8 damage-to-non-perceiver AC conflict; `CursedSpiritAttackGoal.java`, ability/effects internals, Megumi*, Shelter*, Spawn*.

## Quality axis

Real bugs, regressions, mixin correctness (descriptors, side-safety — `CursedSpiritProjectileMixin` is main-side, must be server-safe), contract break risk of the seam split (anything still reading `perceives` that should read `interacts`, or vice versa — list every call site verdict), test vacuity.

Severity P0–P3 with file:line evidence. Worker self-reports are at `.agent-runs/fix-perception/worker-*.md` — treat as claims, verify against the diff, don't relay.

## Report

Artifact: verdict per spec item (✅/❌/⚠️-unverifiable) + findings list + evidence. Return ≤10 lines.
