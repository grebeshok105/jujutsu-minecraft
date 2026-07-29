# Session Handoff - VFX Package and Factory Migration

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/vfx-package-and-factory-migration`
- Branch: `refactor/vfx-package-and-factory-migration`
- Base: `929788f77796578a3f580191171f872e0f2a8df1` (`origin/main`, after PR #38)

## Current scope

- PR 6 only: normalize Megumi VFX packages, migrate generic cue construction to `VfxCues`, add the Todo arrival read model, and settle the Todo `NO_ANCHOR` clap fallback.
- PR 7-9 were not started. No gameplay, visual tuning, wire strings, cue counts, delivery paths, radii, or durations were changed.
- The main checkout and `vfx-contract-hardening` worktree were not edited.

## Contracts

- `MegumiVfxIds` now lives in `jujutsu.mod.vfx`; `MegumiVfxRecipes` now lives in `jujutsu.mod.client.vfx.megumi`. Its five live `megumi/*` strings and empty planned set remain pinned by `VfxCueTest` and completeness contracts.
- `VfxCueTest` remains JUnit 5 and uses the real `VfxCuePayload.STREAM_CODEC` for all eight fields, both anchor modes, normalized and zero directions, sentinel rejection, stable wire strings, and buffer exhaustion.
- `VfxCompletenessTest` calls the real three recipe packs against the isolated director registry and checks exact live coverage plus duplicate-registration failure.
- `VfxFactoryMigrationContractTest` checks only the migrated runtime files. Generic transport shapes use `VfxCues`; the two overloaded Todo body-presentation constructors remain explicit and local.
- `TodoSwapArrivalPayloadTest` pins speed/width/height/direction mapping without clamping or rewriting values.
- `TodoAnimationHooksContractTest` proves all live clap routes use the caster-anchored helper and that `NO_ANCHOR` no longer falls back to a nearby player.

## Red mutations

| Mutation | Failing test or proof | Restored |
|---|---|---|
| Swap width and height in `TodoSwapArrivalPayload.from` | `TodoSwapArrivalPayloadTest.readsSpeedWidthHeightFromTheirExistingOffsetComponents` failed at line 19 | yes |
| Restore nearby-player `NO_ANCHOR` fallback | `TodoAnimationHooksContractTest.noAnchorClapDoesNotSelectANearbyLocalPlayer` failed at line 36 | yes |
| Restore direct generic constructor in a migrated runtime | `VfxFactoryMigrationContractTest.migratedRuntimesUseTransportFactories` failed at line 31 | yes |
| Change one `megumi/*` wire path | `VfxCueTest.liveWireStringsRemainStable` failed at line 72 | yes |
| Restore collapse offset to zero | `NailTrapCollapseTest`: 3 of 4 tests failed | yes |

## Verification

- `compileJava` passes outside the sandbox after the sandbox reported its 1G heap limit; no source compilation errors were reported.
- Focused PR 6 tests pass: `TodoSwapArrivalPayloadTest`, `VfxFactoryMigrationContractTest`, and `TodoAnimationHooksContractTest`.
- Full `test --no-daemon --max-workers=1 --no-watch-fs` passed.
- `qualityGate --no-daemon --max-workers=1 --no-watch-fs` passed: documentation audit, full test suite, all verification programs, and `verifyAssertionsEnabled: 32/32`.
