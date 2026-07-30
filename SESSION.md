# Session Handoff - Megumi Divine Dogs Stability

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft`
- Branch: `fix/megumi-divine-dogs-stability`
- Base: `9efbde3` (`docs: add fix plan for Megumi dogs and CurseLink payload bounds`)
- Scope: PR A, issues #30/#31 and #29 only. Issue #20 remains untouched.

## Confirmed diagnosis

- With `setNoAi(true)`, pounce velocity changed but the wolf position did not. The runtime now owns gravity, `MoverType.SELF` movement, facing, and post-move collision evaluation.
- Snapshot AABB overlap could miss a fast target. Impact now checks endpoint overlap and the target's inflated swept AABB between the previous and current positions.
- Pounce cleanup used to erase all motion and calculate knockback after cleanup. The runtime now captures pre-impact travel direction, keeps a damped horizontal exit velocity, and resumes navigation only through explicit active runtime transitions.
- `VfxWorldChannel` fed every shadow pool into one `debugTriangleFan`; multiple pools therefore became one connected primitive. Each pool now emits independent quad sectors through `debugQuads`.
- Navigation recovery now captures the Sic command identity at launch and calls `moveTo(target, DOG_MOVEMENT_SPEED)` only after ordinary termination or valid contact passes the pure resume policy; generic cleanup never navigates.
- Post-move policy gives a valid swept impact priority over same-tick landing/collision. Invalid semantic contact receives zero exit motion, while valid contact alone may retain damped horizontal travel. Knockback preserves impact-velocity, positional, then last-steering fallback policy; the current state machine supplies zero for the unreachable third runtime fallback.
- `MegumiShadowMoteParticle` keeps its sprite, lifetime and sparse accent structure but uses neutral near-black colors and inherited world lighting; the old saturated full-bright accent is removed.

## Verification

- Focused regression tests were intentionally red before the implementation: two failures for explicit pounce movement/swept impact and independent shadow primitives.
- Focused tests now pass: `MegumiPouncePolicyTest` and `VfxWorldMegumiShadowTest`.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` passed.
- `./gradlew.bat build --no-daemon --max-workers=1 --no-watch-fs` passed.
- Temporary `MEGUMI_DIAG` instrumentation has been removed from production code.
- Release JAR: `D:/WorkFlow/Jujutsu Minecraft/build/libs/jujutsumod-1.0.0.jar`.
- Installed JAR: `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.
- Build and installed JAR SHA-256: `A58C35BD452998F422F2269CC0830691C46D6C5FE527ED3614A10DBE4735742D`.

## Machine prerequisite

- Windows paging is configured for one fixed `D:/pagefile.sys` at `15360/15360 MB`, with automatic pagefile management disabled so the configured D: file is used. The old C: file remains active until reboot; restart Windows before relying on the new commit limit.
- After reboot, run the user gameplay smoke for pounce movement, target contact, recovery, and visually separate black shadows. `qualityGate` does not prove in-world movement or visual separation.
