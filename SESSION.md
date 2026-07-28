# Session Handoff - Megumi Divine Dogs polish

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/megumi-divine-dogs-polish`
- Branch: `feat/megumi-divine-dogs-polish`
- Base: `7b59a0d088477ee82c514e397320aa8bdb93b55b` (`origin/main` when the branch was created)
- Integration: follow-up PR only; do not merge before user review

Durable behavior lives in `AGENTS.md`. The implementation note is `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Megumi-Divine-Dogs.md`.

## Current branch facts

- Megumi keeps `R` summon/recall and `Shift+R` Sic. No input, payload, persistence, dependency, mixin or universal summon system was added.
- The imported GeckoLib player body renders at width `1.25` with unchanged height and static head. Ordinary swings cycle `punch_1`, `punch_2`, `kick`; confirmed summon triggers `summon_divine_dogs`.
- Dogs materialize for 16 ticks and manually recall for 12. Transitional phases disable AI, navigation, attacks, incoming damage and combat collision; only the renderer moves the visible body vertically.
- Each dog receives an exact-origin black shadow pool and mote burst through VFX Core. Recall contracts the filled pool inward; no teal edge ring remains. The local owner receives the shared 0.80-second first-person `SIGN`; observers receive only third-person presentation.
- Sound is server-spatial at the owner, dog or accepted impact. Existing ProjectJJK events and vanilla wolf variants provide shadow open, emergence, vocal, Sic, pounce and recall beats; client recipes do not duplicate them.
- The director-owned left cooldown HUD appears only for selected Megumi with a positive mirrored `PRIMARY` deadline. In this slice that implies no pack because summon never starts it; the HUD owns no timer or network state.
- Dog tuning is 60 HP, 3 damage and 0.34 speed.
- Pounce is server-only and Sic-only. Each dog independently launches at inclusive 3-8 block range with LOS, 0.92 horizontal and 0.42-0.58 vertical speed, steers its horizontal motion toward the live target and stops cleanly on a collision or landing. The inclusive 16-tick deadline and 80-tick cooldown remain. Confirmed impact is one owner-attributed 5-damage hit; accepted damage adds 2.4-strength knockback, 6 stagger ticks, spatial sound and target VFX.
- Ordinary inherited wolf melee attribution is unchanged; only the pounce path explicitly credits Megumi.

## Reviewable commits

1. `57a6175` `feat(megumi): integrate model presentation`
2. `dea012d` `feat(megumi): add dog presentation phases`
3. `fc0dbf2` `feat(megumi): add shadow presentation effects`
4. `998c707` `feat(megumi): add divine dog sound cues`
5. `754a5fa` `feat(megumi): add summon hand sign`
6. `f87fed7` `feat(megumi): add divine dog cooldown hud`
7. `3bc353b` `balance(megumi): tune divine dog durability`
8. `bd3813e` `feat(megumi): add Sic pounce`
9. `chore(megumi): finalize divine dog polish` - this final documentation and verification commit

## External review follow-up

- Owner-body and dog-pool summon cues have separate ids, so an unresolved client anchor cannot produce a duplicate pool. Launch facts retain only independently variable server gates; head-look and localization-presence tests are precise; documentation records the actual HUD invariant and inclusive pounce deadline.
- On 2026-07-28 the follow-up tree passed `qualityGate` (42 tasks, all JUnit, 34 assertion-enabled JavaExec programs and documentation audit). Pounce physics, practical dual-dog impact timing, knockback distance and black-pool readability remain in-game smoke evidence, not automated proof.

## Automated evidence

- Focused Megumi server/client/VFX/architecture tests, both source-set compiles, `testCharacterDefinitions` and `testCharacterClients` passed after the pounce implementation.
- The tests cover phase and combat gates, manual recall versus hard cleanup, HUD truth/deadline math, profile values, Sic target policy, per-dog pounce gates/deadlines, one owner-attributed impact source path, conditional impact feedback and presentation registration.
- On 2026-07-28 the complete Task 9 tree passed `qualityGate` (42 tasks, all JUnit, 34 assertion-enabled JavaExec programs and documentation audit) and `assemble` (remapped binary and sources JARs).
- Branch audits found exactly eight feature commits before this final commit, no tracked `.superpowers`, and no changes to `TargetResolver`, `CharacterAbilityExecutor`, shared input or payload inventory.
- Automation does not construct a `ServerLevel`, render a frame, play audio or run multiplayer. It proves contracts and pure/source/resource logic, not in-world feel.

## Required in-game smoke

- Summon both dogs on flat ground, a ledge, water and a tight room; confirm 16-tick rise, inert transition and no unsafe placement.
- Recall a full and half pack; confirm 12-tick sink, correct 240-tick cooldown and no ghost AI, collision, model, particle or sound. Kill both dogs and confirm the 600-tick loss cooldown.
- Check owner death, respawn, disconnect, dimension change, vessel deselection/reselection and server restart cleanup.
- Check Sic and pounce against a mob and player at 3, 8 and out-of-range distances; break LOS before launch, kill/remove the target in flight, recall during flight, let a pounce miss/timeout, collide with a wall/ceiling and land early.
- Confirm both siblings pounce independently, accepted impact deals one 5-damage owner-attributed hit with 2.4-strength knockback and 6-tick stagger, and refused damage emits no impact feedback. Check Iron Golem durability and that the knockback reads as roughly 5-10 blocks on flat ground.
- Confirm owner, allied player and own pack cannot be targeted or damaged.
- Verify cooldown HUD visibility/placement, first-person hands, third-person animation, model width/static head, held items/shield and no local/remote VFX or sound duplication.
- Run two-client synchronization and kill-screen attribution checks, then smoke Nobara and Todo rendering, controls and combat.
