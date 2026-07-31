# Session Handoff - Megumi Divine Dogs Stability

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft`
- Branch: `fix/megumi-divine-dogs-stability`
- Base: `9efbde3` (`docs: add fix plan for Megumi dogs and CurseLink payload bounds`)
- Scope: PR A, issues #30/#31 and #29 only. Issue #20 remains untouched. The implementation commit SHA is intentionally omitted until this pass is committed.
- Pull request: [#47](https://github.com/grebeshok105/jujutsu-minecraft/pull/47), `OPEN` and `draft`; head is pushed to `origin/fix/megumi-divine-dogs-stability`.

## Confirmed diagnosis

- With `setNoAi(true)`, pounce velocity changed but the wolf position did not. The runtime now owns gravity, `MoverType.SELF` movement, facing, and post-move collision evaluation.
- Snapshot AABB overlap could miss a fast target. Impact now checks endpoint overlap and the target's inflated swept AABB between the previous and current positions.
- Pounce cleanup used to erase all motion and calculate knockback after cleanup. The runtime now captures collision-resolved post-move velocity, keeps damped horizontal exit velocity for ordinary airborne and valid-contact completion where policy permits, zeros grounded ordinary/invalid/cleanup exits, and resumes navigation only through explicit active runtime transitions.
- `VfxWorldChannel` fed every shadow pool into one `debugTriangleFan`; multiple pools therefore became one connected primitive. Each pool now emits independent quad sectors through `debugQuads`.
- Navigation recovery now captures the Sic command identity at launch and calls `moveTo(target, NAVIGATION_SPEED_MODIFIER)` only after ordinary termination or valid contact passes the pure resume policy; generic cleanup never navigates. The modifier is explicitly `1.0`, separate from the 0.34 movement attribute.
- Post-move policy gives a valid swept impact priority over same-tick landing/collision. The first reachable movement tick is elapsed tick 1, so an early ground flag alone does not cancel it; real collision flags still do. Invalid semantic contact and cleanup receive zero exit motion, while airborne ordinary completion and valid contact may retain resolved damped horizontal travel.
- `MegumiShadowMoteParticle` uses a dedicated `megumi_shadow_spot` sprite, neutral near-black colors, a one-in-ten accent population and inherited world lighting; Sic/pounce use dark dust without the bright teal ring.

## Verification

- Focused tests pass after this pass: `MegumiPouncePolicyTest`, `MegumiProfileTest`, `MegumiShadowPresentationTest` and `VfxWorldMegumiShadowTest` (16 tests, 0 failures).
- RED evidence before production edits: the focused test compile failed because the current head had no `NAVIGATION_SPEED_MODIFIER` or independent `exitVelocity` policy; the stale navigation and first-tick contracts were then corrected and returned to green.
- Full `qualityGate`, `build` and `auditDocumentation` for this pass remain pending.
- Release JAR and installed JAR hashes remain to be refreshed after the final build.

## Machine prerequisite

- Windows paging is active after reboot: automatic management is disabled and the only active pagefile is `D:/pagefile.sys` at `15360 MB` (`LastBootUpTime: 2026-07-30 18:39:57`).
- User gameplay smoke remains pending for both dogs, mid-range Sic, open ground, steps/slopes, obstacles, miss/collision, timeout/recovery, post-resume pause, knockback direction, two separate pools, summon/recall presentation and dense-black readability. `qualityGate` and CI do not prove in-world movement or visual appearance.
