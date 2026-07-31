# Session Handoff - Megumi Divine Dogs Stability

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft`
- Branch: `fix/megumi-divine-dogs-stability`
- Base: `9efbde3` (`docs: add fix plan for Megumi dogs and CurseLink payload bounds`)
- Scope: PR A, issues #30/#31 and #29 only. Issue #20 remains untouched.
- Implementation commits: `ad9a14d` (`fix(megumi): use post-move displacement for pounce`), `7a9588d` (`docs(megumi): clarify post-move displacement contract`), `c3945d9` (`fix(vfx): separate world buffer render passes`) and `57034bc` (`docs(vfx): document sequential world buffers`).
- Current head: `57034bc`.
- Pull request: [#47](https://github.com/grebeshok105/jujutsu-minecraft/pull/47), `OPEN` and `draft`; head is pushed to `origin/fix/megumi-divine-dogs-stability`.

## Confirmed diagnosis

- With `setNoAi(true)`, pounce velocity changed but the wolf position did not. The runtime now owns gravity, `MoverType.SELF` movement, facing, and post-move collision evaluation.
- Snapshot AABB overlap could miss a fast target. Impact now checks endpoint overlap and the target's inflated swept AABB between the previous and current positions.
- Pounce cleanup used to erase all motion and calculate knockback after cleanup. The runtime now derives actual post-move displacement from the dog's position before and after `move()` (rather than treating `getDeltaMovement()` as collision-resolved), keeps damped horizontal exit motion for ordinary airborne and valid-contact completion where policy permits, zeros grounded ordinary/invalid/cleanup exits, and resumes navigation only through explicit active runtime transitions.
- `VfxWorldChannel` fed every shadow pool into one `debugTriangleFan`; multiple pools therefore became one connected primitive. Each pool now emits independent quad sectors through `debugQuads`.
- The shared `MultiBufferSource` can finish the previous `BufferBuilder` when a second render type is acquired. World VFX now renders lightning styles fully, acquires `debugQuads`, then renders shadow styles in a second pass; no stale `VertexConsumer` is reused.
- Navigation recovery now captures the Sic command identity at launch and calls `moveTo(target, NAVIGATION_SPEED_MODIFIER)` only after ordinary termination or valid contact passes the pure resume policy; generic cleanup never navigates. The modifier is explicitly `1.0`, separate from the 0.34 movement attribute.
- Post-move policy gives a valid swept impact priority over same-tick landing/collision. The first reachable movement tick is elapsed tick 1, so an early ground flag alone does not cancel it; real collision flags still do. Invalid semantic contact and cleanup receive zero exit motion, while airborne ordinary completion and valid contact may retain damped horizontal travel derived from the actual post-move displacement.
- `MegumiShadowMoteParticle` uses a dedicated `megumi_shadow_spot` sprite, neutral near-black colors, a one-in-ten accent population and inherited world lighting; Sic/pounce use dark dust without the bright teal ring.

## Verification

- Focused tests pass after this pass: `VfxWorldSplitContractTest`, `VfxWorldGeometryTest`, `VfxWorldMegumiShadowTest` and `NailTrapCollapseTest`, plus the existing Megumi focused suite (all selected tests green); the new buffer-order regression has RED/GREEN evidence.
- RED evidence before the production edit: the new displacement assertion failed at `MegumiPouncePolicyTest.java:113` while the old `getDeltaMovement()` assertion passed; the runtime then returned the focused suite to green.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` passed on current code/docs state at `57034bc`.
- `./gradlew.bat build --no-daemon --max-workers=1 --no-watch-fs` passed and produced `D:/WorkFlow/Jujutsu Minecraft/build/libs/jujutsumod-1.0.0.jar`.
- `./gradlew.bat auditDocumentation --no-daemon --max-workers=1 --no-watch-fs` passed.
- Build and installed JAR SHA-256: `F570CDE7A50626720E3A37770F7E4B5A0D342A1DC75BF40B98AA230F3AA9306F`.
- The installed JAR at `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar` matches the build JAR byte-for-byte.

## Machine prerequisite

- Windows paging is active after reboot: automatic management is disabled and the only active pagefile is `D:/pagefile.sys` at `15360 MB` (`LastBootUpTime: 2026-07-30 18:39:57`).
- User gameplay smoke remains pending for both dogs, mid-range Sic, open ground, steps/slopes, obstacles, miss/collision, timeout/recovery, post-resume pause, knockback direction, two separate pools, summon/recall presentation and dense-black readability. `qualityGate` and CI do not prove in-world movement or visual appearance.
