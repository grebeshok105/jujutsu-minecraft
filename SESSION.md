# Session Handoff - Megumi Divine Dogs Stability

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft`
- Branch: `fix/megumi-divine-dogs-stability`
- HEAD: `a99c107c847769d0df3d20838dd7fd0cb2da3b14` (`fix(megumi): harden pounce recovery and shadow motes`)
- Base: `9efbde3` (`docs: add fix plan for Megumi dogs and CurseLink payload bounds`)
- Scope: PR A, issues #30/#31 and #29 only. Issue #20 remains untouched.
- Pull request: [#47](https://github.com/grebeshok105/jujutsu-minecraft/pull/47), `OPEN` and `draft`; head is pushed to `origin/fix/megumi-divine-dogs-stability`.

## Confirmed diagnosis

- With `setNoAi(true)`, pounce velocity changed but the wolf position did not. The runtime now owns gravity, `MoverType.SELF` movement, facing, and post-move collision evaluation.
- Snapshot AABB overlap could miss a fast target. Impact now checks endpoint overlap and the target's inflated swept AABB between the previous and current positions.
- Pounce cleanup used to erase all motion and calculate knockback after cleanup. The runtime now captures pre-impact travel direction, keeps damped horizontal exit velocity only for valid contact, and resumes navigation only through explicit active runtime transitions.
- `VfxWorldChannel` fed every shadow pool into one `debugTriangleFan`; multiple pools therefore became one connected primitive. Each pool now emits independent quad sectors through `debugQuads`.
- Navigation recovery now captures the Sic command identity at launch and calls `moveTo(target, DOG_MOVEMENT_SPEED)` only after ordinary termination or valid contact passes the pure resume policy; generic cleanup never navigates.
- Post-move policy gives a valid swept impact priority over same-tick landing/collision. Invalid semantic contact receives zero exit motion, while valid contact alone may retain damped horizontal travel. Knockback preserves impact-velocity, positional, then last-steering fallback policy; the current state machine supplies zero for the unreachable third runtime fallback.
- `MegumiShadowMoteParticle` keeps its sprite, lifetime and sparse accent structure but uses neutral near-black colors and inherited world lighting; the old saturated full-bright accent is removed.

## Verification

- Focused tests now pass: `MegumiPouncePolicyTest`, `MegumiShadowPresentationTest` and `VfxWorldMegumiShadowTest`.
- Deliberate red proofs were run and restored: replaced Sic command, `recomputePath` recovery, landing-first ordering, damped invalid exit motion, and full-bright teal mote each caused the focused contract to fail.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` passed.
- `./gradlew.bat build --no-daemon --max-workers=1 --no-watch-fs` passed.
- `./gradlew.bat auditDocumentation --no-daemon --max-workers=1 --no-watch-fs` passed.
- GitHub CI for PR #47 passed for both `build` checks on head `a99c107`.
- Temporary `MEGUMI_DIAG` instrumentation has been removed from production code.
- Release JAR: `D:/WorkFlow/Jujutsu Minecraft/build/libs/jujutsumod-1.0.0.jar`.
- Installed JAR: `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.
- Build and installed JAR SHA-256: `8007611171AD13BA8AC79D160AF9969251B5E43EF73C4DE55B735609D1E60312`.

## Machine prerequisite

- Windows paging is active after reboot: automatic management is disabled and the only active pagefile is `D:/pagefile.sys` at `15360 MB` (`LastBootUpTime: 2026-07-30 18:39:57`).
- User gameplay smoke remains pending for pounce movement, target contact, miss/obstacle recovery, and two visually separate black shadows. `qualityGate` and CI do not prove in-world movement or visual appearance.
