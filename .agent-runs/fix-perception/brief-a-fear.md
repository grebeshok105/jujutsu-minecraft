# Brief A — Fear look-inversion (CRITICAL, #90 п.1)

RUN_ID: `fix-perception` · Worktree: `D:/WorkFlow/jm-wt-perception` (branch `fix/review-perception`) · Artifact: `.agent-runs/fix-perception/worker-a-fear.md`

## Task

`src/client/java/jujutsu/mod/client/mixin/FearMouseMixin.java:41-45`: the `@ModifyVariable` on `MouseHandler.turnPlayer(D)V` modifies the **time-delta** local used for smooth-camera interpolation (javap-verified in the post-merge review), NOT the actual look deltas. The real deltas are `MouseHandler.accumulatedDX`/`accumulatedDY` consumed by `LocalPlayer.turn(DD)V` (via `player.turn(...)` call inside `turnPlayer`). Consequence: with smooth camera OFF the mixin is a **no-op** — fear does not invert the player's look.

## Fix requirement

- Retarget the inversion at the real deltas. Options (pick after javap on the mapped 1.21.8 jar — verify signatures yourself, reviewer's notes may drift):
  - inject/modify in `LocalPlayer.turn(DD)V` (negate both doubles when fear inversion active), or
  - `@ModifyVariable`/accessor on `accumulatedDX`/`accumulatedDY` at the correct injection point inside `turnPlayer`.
- Keep the mixin narrow with a javadoc justifying why a mixin is needed (input inversion cannot be done via Fabric events) and documenting the javap-verified target signature.
- Preserve existing enable/disable semantics of the current mixin (when fear inversion is inactive, zero behavior change — early-out).
- Check `client/curse/fear/*` (FearClientState/FearInputMap) for the flag the mixin reads; reuse, don't duplicate.

## Env

- Windows bash. `java` on PATH is 1.8 — useless. Gradle: `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...`
- javap: use temurin21 jdk's javap (`~/scoop/apps/temurin21-jdk/current/bin/javap`). Mapped jar lives under loom cache (`.gradle/loom-cache/` in project or `~/.gradle/caches/fabric-loom/`); locate the 1.21.8 mapped minecraft jar and `javap -c` `net.minecraft.client.player.LocalPlayer#turn` and `net.minecraft.client.MouseHandler#turnPlayer` to confirm exact local/signature before writing the mixin.

## Allowed files

- `src/client/java/jujutsu/mod/client/mixin/Fear*.java` (edit FearMouseMixin; add a new `Fear*` client mixin ONLY if the fix truly needs a second injection site)
- `src/client/java/jujutsu/mod/client/curse/fear/*`
- client mixin json (`src/client/resources/*.mixins.json` or similar) if you add a mixin
- `src/test/java/jujutsu/mod/client/curse/fear/*` (new test file allowed)

**Do NOT edit:** anything under `src/main`, `CursedSpirit*`, Megumi*, ability/effects. Other workers own those.

## Test

Client-side look inversion can't GameTest (input pipeline is client runtime). Write the strongest honest oracle available in `src/test`: e.g. extract the negate-decision into a tiny pure function (`FearInputMap` or a small helper) and JUnit it — including the "inversion inactive → identity" side. Prove it non-vacuous (red-mutant: flipping the predicate must fail the test).

## Verify

- `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileJava compileTestJava` — wait, client code: `compileClientJava`/`compileTestJava` — run the compile tasks that cover your files plus `./gradlew test --tests '*Fear*'`.
- Commit: small, conventional, English, e.g. `fix(client): invert fear look via LocalPlayer.turn deltas`. `git add` ONLY your files.

## Report

Write the full report to the Artifact path (Summary / Detail incl. javap evidence of chosen target / Evidence: commands+outputs / Rejected-Open). Return ≤10 lines: status, artifact path, commit hashes, concerns.
