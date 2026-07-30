# Megumi Divine Dogs

Status: CURRENT

## Slice boundary

Megumi is the third vessel. `PRIMARY` (`R`) summons or recalls one pack containing two separately mortal `MegumiDivineDogEntity` wolves; `PRIMARY_SNEAK` (`Shift+R`) assigns one server-selected Sic target to every living sibling. The slice adds no items, persistence, payload, input, mixin, dependency or universal summon abstraction.

The server definition, router, profile, entity and runtime live under `jujutsu.mod.character.megumi`. Client rendering and recipes live under `jujutsu.mod.client.character.megumi`. Shared edits are limited to existing content and presentation extension points; vessel dispatch, `TargetResolver` and `CharacterAbilityExecutor` remain unchanged.

## Pack identity and lifecycle

`MegumiDivineDogPack` records the level key, white and black entity UUIDs, a monotonic summon token and summon game time. Runtime identity is owner UUID plus summon token, never entity id. `MegumiSummonRuntime` keeps one owner-keyed pack map and a teardown guard.

`teardown` remains the only destructive cleanup entry point. It removes the pack record before a cross-level sweep by entity class and stored owner UUID. Manual recall transitions only dogs that belonged to that removed pack into a 12-tick `RECALLING` phase; every death, stale-state, disconnect, dimension, deselection and server-stop cleanup hard-discards immediately. `reconcile` ignores nested teardown and missing records, and retains a pack while either recorded sibling is alive.

Summon preflights both safe positions before inserting either dog. A failed second insertion rolls back without cooldown. Summon starts no cooldown; manual recall starts 240 ticks; final pack loss starts 600. A longer active deadline is never shortened, and every server cooldown start uses the existing client mirror.

## Presentation phases

Each transient dog synchronizes only presentation phase and phase age: `MATERIALIZING` for 16 ticks, `ACTIVE`, then manual `RECALLING` for 12 ticks. During materialization and recall the entity disables AI and navigation, refuses attacks and incoming damage, and is not pickable, pushable or combat-collidable. The authoritative position never moves for presentation; `MegumiDivineDogRenderer` applies only a vertical render offset from one block below the surface to the final position and back.

The vanilla wolf model, variants and textures remain in use. A summon emits `DOGS_SUMMON_BODY` for Megumi's `summon_divine_dogs` GeckoLib clip and one `DOGS_SUMMON` exact-origin cue per dog for its shadow pool; a failed client anchor lookup can therefore never turn a body cue into a second pool. Recall emits one cue per still-living dog before the phase starts. `MegumiVfxRecipes` owns five ids: `DOGS_SUMMON_BODY`, `DOGS_SUMMON`, `DOGS_RECALL`, `DOGS_SIC` and `DOGS_POUNCE`. The VFX Core world channel draws summon/recall as a filled, alpha-blended black pool from independent quad sectors, without a teal edge ring or shared triangle-fan geometry; the reusable full-bright shadow mote reuses an existing project sprite. No fullscreen effect or new callback was added.

The confirmed local-owner summon cue also triggers the shared first-person `SIGN` style for 0.80 seconds. Remote players receive only the third-person cue. Vessel change and VFX director cleanup cancel the shared first-person state.

Server-spatial sound is emitted from the authoritative owner, dog or impact position. The sequence reuses the existing ProjectJJK sound registry and vanilla `WolfSoundVariant`: shadow open, emergence accent, post-materialization vocal, Sic command/growl, accepted pounce impact and recall suction. Client recipes do not replay these sounds.

## HUD

Megumi registers a contribution into the one `VfxDirector` HUD path. The compact left-side Divine Dogs cooldown appears only for the local selected Megumi with a positive mirrored `PRIMARY` cooldown. In the current slice this implies an absent pack, because summon never starts that cooldown and it starts only when the pack is recalled or finally lost. Remaining time is derived from `ClientAbilityCooldowns.READY_AT`; there is no second timer or pack payload. It disappears at zero and follows the existing disconnect and vessel-keyed cooldown lifecycle.

## Sic and pounce

Sic uses the unchanged `TargetResolver`, then immediately re-resolves the entity and checks owner line of sight plus `MegumiTargetPolicy`. Owner, own pack dogs, allies, spectators, dead, removed, unloaded and cross-level targets are refused. Owner-defense goals may still set an ordinary wolf target, but only the separately stored Sic target UUID can authorize pounce.

Each dog owns unsynchronized, non-persistent Sic/pounce UUIDs and deadlines. Immediately before launch the server requires an `ACTIVE` current-pack dog, live loaded same-level owner, exact current Sic target, eligibility, line of sight, inclusive 3.0-8.0 block range and that dog's readiness. Launch starts at 0.92 horizontal and 0.42-0.58 vertical speed, then server-side steering keeps the horizontal motion on the live target without overwriting ballistic vertical motion. A horizontal or vertical collision, a landing after the launch tick, timeout, invalidation or teardown finishes the flight, stops its residual motion and restores ordinary ACTIVE AI where appropriate. Launch starts only that dog's 80-tick cooldown.

With AI disabled during flight, the server owns pounce motion explicitly: each tick it steers the horizontal velocity, applies profile gravity, moves through `MoverType.SELF`, updates facing, and then evaluates real collision flags. Impact uses the target's inflated swept AABB plus endpoint overlap, so a fast dog cannot tunnel through a target between ticks. The server revalidates owner, target, pack command and friendly-fire policy, then applies exactly one `playerAttack(owner)` hit for base 3.0 plus 2.0 pounce damage. Only accepted damage applies a 2.4-strength horizontal knockback based first on the pre-impact travel vector, then on the positional fallback, `CombatStagger.GLOBAL` for 6 ticks, the spatial impact sound and target-anchored `DOGS_POUNCE` cue. Completed flight keeps a damped horizontal exit velocity; ordinary termination resumes navigation only through the runtime transition while the active Sic command remains valid. A miss, timeout, invalidation, phase change or teardown emits no hit feedback. Inherited ordinary wolf melee attribution remains unchanged and is outside this polish pass.

## Tuning and follow safety

`MegumiProfile` is authoritative: Divine Dogs have 60 health, 3 attack damage and 0.34 movement speed. Their damage was not raised; durability and approach speed improve while Sic and the per-dog pounce deadline remain the pressure controls.

Every 10 ticks, a dog farther than 32 blocks gets a deterministic safe-ground search around Megumi through radius 3. A point must be loaded, floor-supported, collision-free and contain no fire or lava. Water is valid over safe ground. No result leaves position, navigation and target unchanged until the next retry; there is no exact-owner fallback.

## Evidence boundary

JUnit and architecture checks cover profile values, phase transitions and combat gates, delayed recall versus hard cleanup, cooldown HUD visibility/deadline math, Sic-only identity, pounce range/LOS/eligibility/deadlines, owner-attributed single-hit source shape, conditional impact feedback, VFX/sound/first-person registration, payload inventory and vessel boundaries.

No automated test constructs a `ServerLevel`, renders a frame, plays audio or runs two clients. Summon/recall geometry on floors, ledges, water and tight rooms; AI/collision feel; actual pounce movement and contact; mob/player kill attribution; spatial mix and duplication; HUD placement; remote synchronization; and Nobara/Todo regression remain in-game smoke.
