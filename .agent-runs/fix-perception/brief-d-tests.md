# Brief D — Test verification & oracle completion (post-implementation)

RUN_ID: `fix-perception` · Worktree: `D:/WorkFlow/jm-wt-perception` (branch `fix/review-perception`) · Artifact: `.agent-runs/fix-perception/tester-d-tests.md`

## Context

Three workers landed review-fixes in the cursed-spirit perception domain. Their reports:
- `.agent-runs/fix-perception/worker-a-fear.md` — fear look inversion (client mixin)
- `.agent-runs/fix-perception/worker-b-server.md` — sound sinks, projectile owner chain, perceives↔canInteract seam, entity misc
- `.agent-runs/fix-perception/worker-c-render.md` — shadow + F3+B hitbox leak

Read all three artifacts FIRST, then the diff `git diff 61ef797...HEAD` in the worktree.

## Job

1. **Verify claimed tests exist and run.** `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew test` — must be green. Then run the perception GameTest classes (check `build.gradle`/`gradlew tasks` for the GameTest lane task name — historically `runGameTest`/`qualityGate` includes it; run ONLY the relevant lane if it can be filtered).
2. **Red-mutant proofs** for every NEW oracle the workers added (and any required by spec they missed — add them):
   - non-perceiver receives no step/fall/swim sounds of the spirit;
   - projectile owned by a non-perceiver neither damages nor deflects off the spirit;
   - seam: (perceives=true, interacts=false) → no damage either direction, not targeted; perceives gates still cover render/sound/tracking;
   - NBT junk: grade=99, NaN stats, missing keys, missing RollSeed → valid re-roll fallback;
   - fear look inversion: inversion actually negates the real look deltas (client-side — JUnit on the extracted seam; full input-pipeline red proof may be impossible headless — say so honestly).
   For each: mutate the production check (invert condition / remove gate), re-run the test, confirm RED, restore, confirm GREEN. Record mutant + result per oracle in the artifact.
3. **Fill gaps within allowed test files only:** `src/test/java/jujutsu/mod/cursedspirit/**`, `src/test/java/jujutsu/mod/client/**`, `src/gametest/java/jujutsu/mod/gametest/CursedSpirit*GameTests.java`, `CursedSpiritTestFixtures.java`. If a needed oracle requires a production seam change — do NOT edit production code; report it as a gap.
4. **Do NOT edit production code.** Your commits may only touch test files. Small conventional commits (`test(cursed-saga): ...`), `git add` only your files.

## Env

Windows bash; `java` on PATH = 1.8 — always `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...`. GameTest lane may collide on port/lock with parallel runs — retry once, report honestly if persistent.

## Report

Artifact: Summary / Detail per oracle (mutant → red/green evidence) / Evidence: commands+outputs / Rejected-Open (oracles impossible without prod seam, flaky lanes). Return ≤10 lines.
