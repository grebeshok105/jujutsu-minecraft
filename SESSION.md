# Session Handoff - VFX Contract Hardening

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/vfx-contract-hardening`
- Branch: `test/vfx-contract-hardening`
- Base: `12866b9d2bcb9084cb8f67eb3ebedf5e307552ea` (`origin/main`, after PR 4)

## Current scope

- PR 5 only: harden transport, lifecycle enumeration, recipe completeness, production emitter coverage, Hairpin packing, radius ownership, and duration ownership.
- PR 6-9 were not started. No gameplay or visual tuning was changed.
- The main checkout was not edited.

## Contracts

- `VfxCueTest` is JUnit 5 and uses the real `VfxCuePayload.STREAM_CODEC` for all eight fields, both anchor modes, normalized and zero directions, sentinel rejection, stable wire strings, and buffer exhaustion.
- `NobaraVfxIds`, `TodoVfxIds`, and `MegumiVfxIds` expose `LIVE`/`PLANNED`: 21 + 7 + 5 live ids, 0 planned ids, 33 total. Wire strings and codec field order are unchanged.
- `VfxCompletenessTest` calls the real three recipe packs against the isolated director registry and checks exact live coverage plus duplicate-registration failure.
- The production emitter check imports only `build/classes/java/main` bytecode and pairs concrete id-field references with cue/factory/network paths; comments and strings cannot satisfy it.
- Radius tests pin Todo delivery `64.0` versus presentation `56.0`, Megumi delivery `48.0`, and Nobara's existing `40.0`/`48.0`/`56.0`/`64.0` presentation families without changing values.
- Duration tests pin shared equal-lifetime owners and Black Flash's intentional `48` recipe / `28` retained-world split.
- Hairpin tests pin clamped depth and independent finale packing.

## Red mutations

| Mutation | Failing test or proof | Restored |
|---|---|---|
| Remove one codec field | `VfxCueTest`: 3 failures, `IndexOutOfBoundsException` | yes |
| Remove one live recipe registration | `VfxCompletenessTest.realRecipePacksRegisterEveryLiveIdExactlyOnce` | yes |
| Remove sole production emitter reference | `VfxCompletenessTest.compiledProductionEmittersCoverEveryLiveId` | yes |
| Add a live id without a recipe | `VfxCompletenessTest.realRecipePacksRegisterEveryLiveIdExactlyOnce` | yes |
| Move incomplete id to `PLANNED` | live completeness and planned visibility both passed; `VfxCueTest.plannedSetsAreEmptyForTheCurrentSlice` is the current-slice pin | yes |
| Presentation radius greater than delivery | `VfxRadiusContractTest.presentationNeverExceedsDelivery` | yes |
| Change one shared duration consumer | `VfxDurationContractTest.equalLifetimeUsesOneNamedValueForRecipeAndRetainedWorldState` | yes |
| Break Hairpin depth mask | `HairpinPackingContractTest.depthClampsToSupportedRangeAndRoundTrips` | yes |
| Restore collapse offset to zero | `NailTrapCollapseTest`: 3 failures | yes |

## Verification

- Focused JUnit run passed after the contract additions: `VfxCueTest` (5), `VfxCompletenessTest` (5), `HairpinPackingContractTest` (2), `VfxRadiusContractTest` (1), and `VfxDurationContractTest` (2).
- Full `test --no-daemon` and `qualityGate --no-daemon` remain required before commit.
