# Megumi Divine Dogs

Status: CURRENT

## Slice boundary

Megumi is the third vessel and uses the vanilla player model. `PRIMARY` (`R`) summons or recalls one pack containing two separately mortal `MegumiDivineDogEntity` wolves; `PRIMARY_SNEAK` (`Shift+R`) issues Sic to every living sibling. There are no items, persistent Ten Shadows state, GeckoLib assets, new payloads, mixins, or shared summon abstraction in this slice.

The server definition, router, profile, entity, placement policy and runtime live under `jujutsu.mod.character.megumi`. The client definition and recipes live under `jujutsu.mod.client.character.megumi`. `JujutsuEntities` is the only content registry changed for the transient `.noSave()` entity.

## Pack identity and lifecycle

`MegumiDivineDogPack` records the level key, white and black entity UUIDs, a monotonic summon token and summon game time. Entity ids are never identity. `MegumiSummonRuntime` keeps one owner-keyed pack map and one teardown guard set.

`teardown` is the only destructive cleanup entry point. It removes the pack record before a cross-level sweep by entity class and stored owner UUID, discards every match, and applies at most one reason-selected cooldown. `reconcile` returns during teardown or without a pack record and retains the pack while either recorded sibling is still alive. Death, unload, owner death, respawn, level change, disconnect, server stop and vessel deselection all reach those two lifecycle paths.

## Summon, recall and cooldowns

Summon preflights both floor-supported positions before either body is inserted. Both dogs use direct constructors, UUID plus token ownership, snowy/black variants and contrasting collars. A failed second insertion rolls the first back without cooldown; a same-tick duplicate request is ignored. A later `R` recalls whatever remains.

Summon starts no cooldown. Manual recall starts 240 ticks and final pack loss starts 600. Sic starts 30 ticks. A new duration replaces an active one only when its deadline is later, and every actual server start is synchronized through the existing ability cooldown payload.

## Sic and follow safety

Sic uses the unchanged `TargetResolver`, then resolves its entity id immediately and rechecks line of sight and one Megumi-owned policy. The owner, own pack dogs, allies, spectators, dead, removed, unloaded and cross-level targets are refused. Successful Sic assigns the same target to every living sibling. Owner-defense goals use the same policy; there is no general nearest-target goal or separate target state.

Every 10 ticks, a dog farther than 32 blocks gets a deterministic safe-ground search around Megumi: center, then radius rings through 3, each ring sorted by squared distance, X and Z, with Y offsets `0,+1,-1,+2,-2,+3,-3`. A point must be loaded, have a sturdy non-hazardous floor, fit the dog AABB without block or entity collision, and contain no fire or lava. Water is valid over safe ground. No result means no teleport, navigation change or target change; ordinary pathing continues until the next check.

## Presentation and evidence boundary

The vanilla wolf renderer draws both bodies. Three VFX Core cues (`DOGS_SUMMON`, `DOGS_RECALL`, `DOGS_SIC`) resolve through `MegumiVfxRecipes` and existing particle channels. Minecraft 1.21.8 removed the standalone wolf howl constant in favor of `WolfSoundVariant`; summon and recall therefore use the active vanilla ambient variant at distinct pitches, while Sic uses that variant's growl. No sound assets are added.

JUnit and architecture checks prove pure policies, constants and vessel boundaries. They do not construct a `ServerLevel`, spawn a dog, move it, run AI, play audio or render a frame. Those behaviours remain owned by the Megumi in-game smoke pass.
