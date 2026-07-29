# Session Handoff - VFX Director Lifecycle

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/vfx-remove-active-instances`
- Branch: `refactor/vfx-remove-active-instances`
- Base: `fa3f26c0283ab4974700d2ee0661b4a334b3d04c` (`origin/main`, after PR #36)

## Current scope

- PR 4 only: remove the director's fake recipe bookkeeping lifecycle.
- Removed symbols: `MAX_ACTIVE_` + `INSTANCES`, `ACTIVE_` + `INSTANCES`, and `Active` + `Instance`; no replacement collection was introduced.
- `receive` now resolves the recipe, creates the instance, rejects expiry, computes `initialAgeTicks`, starts once through package-private `startResolvedCue`, and retains nothing in the director.
- Real retained state remains owned by the seven channels. `SOUND.tick(client)`, level binding/reset, disconnect cleanup, unknown-id warning policy, and `VfxWorldChannel.MAX_IMPACT_FLASHES = 48` remain intact.

## Changed files

- `src/client/java/jujutsu/mod/client/vfx/VfxDirector.java`
- `src/test/java/jujutsu/mod/client/vfx/VfxDirectorLifecycleTest.java`
- `src/test/java/jujutsu/mod/ProjectSanityTest.java`
- `src/test/java/jujutsu/mod/architecture/VesselBoundaryTest.java`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`
- `SESSION.md`

## JUnit coverage

- Accepted cue at age 0: create once, start once, age 0.
- Late valid cue: start once with age 7.
- Exact expiry boundary: create once, reject, never start.
- One tick before expiry: start once with age 19.
- Future cue: start once with clamped age 0.
- `ProjectSanityTest` structurally checks director-side absence, one production start call, unknown-id guard, `SOUND.tick(client)`, and all seven channel clears.

## Red mutations

- Duplicate start call: 4 of 5 lifecycle tests failed; the duplicate was restored.
- Expiry changed from `>=` to `>`: the exact-boundary test failed; `>=` was restored.
- Structural retention mutation: adding the removed list token made `testProjectSanity` fail with `VfxDirector must not retain a fake record list`.
- Structural unknown-id mutation: replacing `UNKNOWN_EFFECT_IDS.add` with `contains` failed with `Unknown VFX ids must still be deduplicated and warned once`.
- Structural sound mutation: removing `SOUND.tick(client)` failed with `VfxDirector tick must retain sound-duck lifecycle work`.
- Structural cleanup mutation: removing `WORLD.clear()` failed with `VfxDirector must clear real channel WORLD`.
- Structural start mutation: duplicating `instance.start` failed with `Accepted VFX instances must have one production start call`.

## Verification

- Baseline: `./gradlew.bat testProjectSanity testVfxTimeline testVfxSoundDuck --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat test --tests jujutsu.mod.client.vfx.VfxDirectorLifecycleTest --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat testVfxTimeline --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat testProjectSanity --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat testVfxSoundDuck --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat test --no-daemon` — `BUILD SUCCESSFUL`.
- `./gradlew.bat qualityGate --no-daemon` — `BUILD SUCCESSFUL`; documentation audit reports 116 main, 175 client, 56 test Java files, 33 verification programs, and 21 Nobara VFX ids.
- Manual client smoke: not run; the PR body must retain the requested cue/channel/lifecycle checklist.
- PR 5 is not started.
