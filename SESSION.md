# Session Handoff - Deterministic VFX Camera Tests

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/vfx-deterministic-camera`
- Branch: `test/vfx-deterministic-camera`
- Base: `7f9a18b9ce5987dd862e7590652f3cff3af1e213` (`origin/main`, after PR #39)

## Current scope

- PR 7 only: make `VfxCameraChannel` timing deterministic through a package-private millisecond `LongSupplier` and add focused JUnit coverage.
- PR 8 and PR 9 were not started. No gameplay, visual tuning, wire strings, cue counts, delivery paths, radii, durations, or camera numeric constants changed.
- The main checkout and earlier VFX worktrees were not edited.

## Contracts

- `VfxCameraChannel()` still uses `System::currentTimeMillis`; `VfxCameraChannel(LongSupplier)` is package-private for tests. All nine previous direct millisecond reads now use the supplier.
- `VfxCameraChannelTest` covers exact start, 1 ms progress, 239/240 ms swap expiry, future age, late age, overlap, yaw/pitch/FOV clamps, finite values, relative strength, and clear without sleeps.
- `MegumiVfxIds` remains in `jujutsu.mod.vfx`; `MegumiVfxRecipes` remains in `jujutsu.mod.client.vfx.megumi`. Its five live `megumi/*` strings and empty planned set remain pinned by `VfxCueTest` and completeness contracts.
- `VfxCueTest` remains JUnit 5 and uses the real `VfxCuePayload.STREAM_CODEC` for all eight fields, both anchor modes, normalized and zero directions, sentinel rejection, stable wire strings, and buffer exhaustion.
- `VfxCompletenessTest` calls the real three recipe packs against the isolated director registry and checks exact live coverage plus duplicate-registration failure.
- `VfxFactoryMigrationContractTest` imports compiled main bytecode for only the migrated runtime classes. Generic transport shapes use `VfxCues`; the two overloaded Todo body-presentation constructors remain explicit and local.
- `TodoSwapArrivalPayload` lives in shared `jujutsu.mod.vfx` because it names the wire read used by the shared world channel. Its test pins speed/width/height/direction mapping without clamping or rewriting values.
- `TodoAnimationHooksContractTest` proves all live clap routes use the caster anchor at the cue origin, preserving zero offset after a swap, and that `NO_ANCHOR` no longer falls back to a nearby player.

## Red mutations

| Mutation | Failing test or proof | Restored |
|---|---|---|
| Pass the post-swap Todo position as the clap anchor | `TodoAnimationHooksContractTest.everyLiveClapRouteUsesTheCasterAnchor` failed at line 22 | yes |
| Reconstruct Megumi's explicit offset through a position-based factory | `VfxCuesTest.anchoredWithOffsetPreservesAnExplicitTransportOffset` failed at line 84 | yes |
| Swap width and height in `TodoSwapArrivalPayload.from` | `TodoSwapArrivalPayloadTest.readsSpeedWidthHeightFromTheirExistingOffsetComponents` failed at line 19 | yes |
| Restore nearby-player `NO_ANCHOR` fallback | `TodoAnimationHooksContractTest.noAnchorClapDoesNotSelectANearbyLocalPlayer` failed at line 36 | yes |
| Restore direct generic constructor in a migrated runtime | `VfxFactoryMigrationContractTest.migratedRuntimesUseTransportFactories` failed at line 48 | yes |
| Change one `megumi/*` wire path | `VfxCueTest.liveWireStringsRemainStable` failed at line 72 | yes |
| Restore collapse offset to zero | `NailTrapCollapseTest`: 3 of 4 tests failed | yes |
| Bypass the injected clock in FOV sampling | `VfxCameraChannelTest.positiveYawNegativePitchAndUpperFovClampsRemainExact`, `.negativeYawPositivePitchAndLowerFovClampsRemainExact`, `.swapSnapIsActiveThrough239MillisecondsAndExpiresAt240` failed | yes |
| Ignore `initialAgeTicks` in heavy impact start | `VfxCameraChannelTest.lateStartMatchesAnEffectThatStartedTwoTicksEarlier` failed | yes |
| End swap FOV at 239 ms | `VfxCameraChannelTest.swapSnapIsActiveThrough239MillisecondsAndExpiresAt240` failed | yes |
| Clear impulses when an overlapping heavy impact starts | `VfxCameraChannelTest.positiveYawNegativePitchAndUpperFovClampsRemainExact`, `.overlappingImpulsesAddRotationalOffsetsWithoutReplacingEitherEffect` failed | yes |
| Change yaw clamp maximum from 9 to 10 | `VfxCameraChannelTest.positiveYawNegativePitchAndUpperFovClampsRemainExact` failed | yes |
| Retune swap strength from 0.62 to 0.99 | `VfxCameraChannelTest.relativeRotationalStrengthPreservesEffectOrder` failed | yes |

## Verification

- Focused `VfxCameraChannelTest` passes after the restored source; the six temporary red mutations above each produced a focused failure and were restored.
- Relative rotational peak baseline at equal intensity/proximity, scanned every 1 ms through 700 ms: swap `1.3960638`, explosion `2.4036567`, heavy `4.026291`, Black Flash `7.422194`.
- Focused `test --tests "jujutsu.mod.client.vfx.VfxCameraChannelTest" --no-daemon --max-workers=1 --no-watch-fs` passed.
- Full `test --no-daemon --max-workers=1 --no-watch-fs` passed.
- `clean test --no-daemon --max-workers=1 --no-watch-fs` passed.
- `qualityGate --no-daemon --max-workers=1 --no-watch-fs` and `clean qualityGate --no-daemon --max-workers=1 --no-watch-fs` passed: documentation audit, full test suite, all verification programs, and `verifyAssertionsEnabled: 32/32`.
- Manual Minecraft smoke was not run; the already-running client was not closed or changed.
