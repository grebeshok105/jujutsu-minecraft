# Brief C — Render leak: shadow + F3+B hitbox for non-perceivers

RUN_ID: `fix-perception` · Worktree: `D:/WorkFlow/jm-wt-perception` (branch `fix/review-perception`) · Artifact: `.agent-runs/fix-perception/worker-c-render.md`

## Task

`src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritRenderer.java:39-46`: the early-return for non-perceivers covers only the model draw. `EntityRenderDispatcher` still renders the entity **shadow** and the **F3+B hitbox** past that return — a non-perceiver sees a floating shadow / debug hitbox of an invisible spirit, leaking its position.

## Fix requirement

- A non-perceiver must see NO shadow and NO F3+B hitbox of the spirit.
- Investigate 1.21.8 render pipeline (javap the mapped jar — `EntityRenderDispatcher`, `EntityRenderer`, `LevelRenderer`): shadow radius/shape and hitbox paths. Prefer renderer-side solutions in your owned files:
  - shadow: e.g. `getShadowRadius(...)`/`shadowRadius`-equivalent returning 0 for non-perceivers, if that fully suppresses it (verify dispatcher checks radius>0 / `shouldShowName`-style gates).
  - hitbox: verify what gates `renderHitBoxes` per-entity. If no renderer-side seam exists, a narrow client mixin is justified — create `src/client/java/jujutsu/mod/client/mixin/CursedSpirit*Mixin.java` (narrow, javap-verified target, javadoc why Fabric API can't do it) and register it in the client mixins json.
- Reuse `CurseRenderGate` as the single decision point — no second perception check implementation.

## Env

- Windows bash. Gradle: `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...` (PATH java = 1.8, useless). javap from temurin21 `bin/javap`; mapped 1.21.8 jar under loom cache (`.gradle/loom-cache/` or `~/.gradle/caches/fabric-loom/`).

## Allowed files

- `src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritRenderer.java`
- `src/client/java/jujutsu/mod/client/render/cursedspirit/CurseRenderGate.java`
- new `src/client/java/jujutsu/mod/client/mixin/CursedSpirit*Mixin.java` only if proven necessary + client mixins json registration
- `src/test/java/jujutsu/mod/client/render/cursedspirit/*` (extend `CurseRenderGateTest` or add a test)

**Do NOT edit:** `src/main/**`, Fear*, other renderer files, Megumi*. Other workers own server + fear.

## Test

Extend the render-gate tests: oracle that the shadow/hitbox decision for a non-perceiver is "hidden" (whatever seam you pick must be unit-testable — e.g. a `CurseRenderGate.allowsShadow/HiddenFromView` predicate consulted by both paths). Prove non-vacuous (red-mutant note).

## Verify

`JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileClientJava compileTestJava test --tests '*CurseRenderGate*'` (or the tasks that cover your files). Small conventional English commit(s), `git add` only your files.

## Report

Full report → Artifact path (Summary / Detail incl. javap evidence / Evidence / Rejected-Open). Return ≤10 lines: status, artifact path, commits, concerns.
