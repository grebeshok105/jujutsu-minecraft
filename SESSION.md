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
- The production emitter check imports the configured `build/classes/java/main` bytecode and follows method-level concrete id-field references through local helpers to cue construction/network paths; comments, strings, and unrelated class-level mentions cannot satisfy it.
- Radius tests derive finite delivery owners from those actual network paths, preserve multi-site owners, treat direct sends separately, and use explicit `PresentationKind.NONE` for recipes without proximity attenuation. Existing values remain unchanged, including Todo delivery `64.0` versus presentation `56.0` and Megumi delivery `48.0`.
- Duration tests check shared-owner and intentional longer-recipe relationships without source-text greps or tuning-literal pins.
- Hairpin tests pin clamped depth and independent finale packing.

## Red mutations

| Mutation | Failing test or proof | Restored |
|---|---|---|
| Remove one codec field | `VfxCueTest`: 3 failures, `IndexOutOfBoundsException` | yes |
| Remove one live recipe registration | `VfxCompletenessTest.realRecipePacksRegisterEveryLiveIdExactlyOnce` | yes |
| Remove sole production emitter reference | `VfxCompletenessTest.compiledProductionEmittersCoverEveryLiveId` | yes |
| Add a live id without a recipe | `VfxCompletenessTest.realRecipePacksRegisterEveryLiveIdExactlyOnce` | yes |
| Move incomplete id to `PLANNED` | live completeness and planned visibility both passed; `VfxCueTest.plannedSetsAreEmptyForTheCurrentSlice` is the current-slice pin | yes |
| Presentation radius greater than delivery | `VfxRadiusContractTest.everyLiveIdHasOnePresentationOwnerAndFactualDeliveryOwners` | yes |
| Change one shared duration consumer | `VfxDurationContractTest.equalLifetimeUsesOneNamedValueForRecipeAndRetainedWorldState` | yes |
| Break Hairpin depth mask | `HairpinPackingContractTest.depthClampsToSupportedRangeAndRoundTrips` | yes |
| Restore collapse offset to zero | `NailTrapCollapseTest`: 3 failures | yes |

## Verification

- Focused JUnit run passed after the contract additions and follow-up: 20 tests, including `VfxCueTest`, `VfxCompletenessTest`, Hairpin packing, radius, duration, and `NailTrapCollapseTest` contracts.
- The plan's `docs/VFX_CORE_REFACTORING_PLAN.md` already labels this exact scope PR 5; the base after PR 4 is `12866b9`, so no plan renumbering is needed.
- Full `test --no-daemon --max-workers=1 --no-watch-fs` passed, followed by `qualityGate` with documentation audit, all verification programs, and `verifyAssertionsEnabled: 32/32` passing.
