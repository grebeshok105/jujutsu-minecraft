# Nail Entity Lifecycle

← [[00-MOC]] · [[Nobara-combat-expansion]] · [[Nobara-runtime-flow]] · [[../04-client-vfx/Nail-rendering]]

**Source prefix:** `.worktrees/nobara-cinematic-slice/src/main/java/jujutsu/mod/character/nobara/projectjjk/`

## Contract

`ProjectJjkNailEntity` is a persistent server entity. A nail is not merely a mark or a client effect: it is prepared, launched, embedded, saved, reloaded, and only discarded after a terminal lifecycle result. Its typed `NailAnchor` supports `NONE`, `ENTITY`, `BLOCK`, and `RUNTIME_OBJECT` anchors.

| Anchor | Stable data | Temporary absence | Terminal removal |
|---|---|---|---|
| Entity | target UUID, cached entity id, local offset/forward | unloaded or unresolved entity stays dormant | confirmed death/final removal |
| Block | dimension, position, face, block-state signature, local frame | not applicable while the world is loaded | changed/incompatible block state |
| Runtime object | resolver type + object UUID + local frame | absent resolver/object is unavailable, nail remains | resolver reports removed/invalid |

**Source:** `NailAnchor.java:9-45`, `NailRuntimeAnchorRegistry.java:10-53`, `ProjectJjkNailEntity.java:289-366,431-484`. **Status:** VERIFIED.

## State and persistence

The entity synchronizes flying, embedded, forward direction, host entity id, and local attachment vectors to clients. Its additional-save data writes the anchor kind plus the UUID, block identity, dimension/face/state signature, or runtime resolver identity required for reconstruction.

| State | Source | Status |
|---|---|---|
| Prepared/launch flags and owner | `ProjectJjkNailEntity.java:65-102` | VERIFIED |
| Synched flight/embedded data | `ProjectJjkNailEntity.java:29-31,279-287` | VERIFIED |
| Anchor save/load | `ProjectJjkNailEntity.java:289-366` | VERIFIED |
| Entity UUID rebind and cached-id refresh | `ProjectJjkNailEntity.java:462-484` | VERIFIED |
| Confirmed-removal registry preserves chunk unload and dimension change | `NailAnchorLifecycle.java:14-36` | VERIFIED |

## Lifecycle

1. Holding the nail item calls `tickPreparing`; one real nail is prepared every 10 use ticks after the first, up to eight.
2. The hammer can launch a nearby prepared nail. Flight hit detection embeds it in an entity or block; callers may attach a nail to a registered runtime object.
3. An embedded nail resolves its anchor each server tick. On a temporary absence it retains its last position and identity. A resolved entity refreshes its cached ID; a resolved block/runtime object updates position and facing.
4. Hairpin consumption, age expiry, invalidated block, confirmed entity removal, or runtime resolver terminal result discards the nail.

`NailRuntimeAnchorRegistry.register(type, resolver)` is the extension point for non-entity objects. Duplicate resolver types fail fast; a missing resolver is intentionally non-terminal.

## Combat ownership

Prepared and embedded nails remain owned by their spawning player UUID. Enlarge and Boom enumerate concrete owned nails, not an abstract target mark. Each successful nail activation uses the dedicated `jujutsumod:hairpin` damage type, tagged `minecraft:bypasses_cooldown`, so invulnerability frames do not suppress later nail hits in the same ability.

**Source:** `ProjectJjkNobaraProfile.java:3-14,27-38`, `ProjectJjkRitualRuntime.java:95-142,191-279`, `data/jujutsumod/damage_type/hairpin.json`, `data/minecraft/tags/damage_type/bypasses_cooldown.json`. **Status:** VERIFIED.

## Verification and unknowns

`NailAnchorTest` covers UUID rebinding, temporary absence, confirmed removal, block invalidation, runtime-object resolution, and save/load behavior. Manual play still needs to verify entity/chunk unload/reload and runtime-object presentation in a real world; see [[../01-meta/Uncertainties]].

---
tags: #jujutsumod #nail #anchors #verified
