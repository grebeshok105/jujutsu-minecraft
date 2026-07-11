# Public API Surface (for future changes)

← [[00-MOC]] · [[../04-client-vfx/VFX-core]]

These are safe **product extension points**, not merely Java-public symbols.

## Prefer calling / extending

| Surface | Why | Source |
|---|---|---|
| `JujutsuMod.id` | resource locations | `JujutsuMod.java` |
| `JujutsuCharacter` enum | new characters | enum file |
| `CharacterSelectionManager.select` | selection side effects | manager |
| `ProjectJjkNobaraProfile` constants | balance tuning | profile file |
| `ProjectJjkNobaraRuntime` / `ProjectJjkRitualRuntime` | server nail/Hairpin combat flow | runtime files |
| `ProjectJjkStrawDollRuntime` + `ProjectJjkRitualPolicy` | server Resonance acquisition/ritual flow and pure validation boundary | ritual files |
| `TargetResolver` | aiming | combat |
| `NobaraVfxIds` (or future character `*VfxIds`) | stable visual event vocabulary | `vfx/NobaraVfxIds.java:6-20` |
| `JujutsuNetworking.broadcastVfxCue` / `sendVfxCue` | only generic server-to-client VFX sends | `network/JujutsuNetworking.java:38-59` |
| `VfxDirector.register` + `VfxRecipe` + `VfxInstance` | typed client composition point | `client/vfx/VfxDirector.java:50-54` |
| `VfxContext` | world/particles/sound/HUD/camera/first-person/internal post-process/time channels | `client/vfx/VfxContext.java` |
| `VfxPalette` | shared cursed-energy colors for compatible persistent renderers | `client/vfx/VfxPalette.java` |
| `NailRuntimeAnchorRegistry.register` | attach a nail to a stable non-entity runtime object | `projectjjk/NailRuntimeAnchorRegistry.java` |
| `CurseLinkRegistry` | source-owned explicit curse-link lifecycle | `curse/CurseLinkRegistry.java` |
| `BlackFlashFocus.hasFocus` | read persistent Black Flash focus without client authority | `combat/BlackFlashFocus.java` |

**Status:** VERIFIED as current extension hubs.

## VFX authoring minimum

```java
// 1. central ID
public static final ResourceLocation ABILITY_HIT = JujutsuMod.id("character/ability_hit");

// 2. server-confirmed action
JujutsuNetworking.broadcastVfxCue(level, origin, 64.0,
    new VfxCue(ABILITY_HIT, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, intensity,
        level.getGameTime(), level.random.nextLong()));

// 3. client registration
VfxDirector.register(ABILITY_HIT, cue -> VfxInstance.of(12,
    (context, initialAge) -> context.hud().triggerImpact(1.0f)));
```

For an entity-anchored cue, pass `anchor.getId()` and the world-space offset `origin.subtract(anchor.position())`. The resolver follows the live entity as `anchor.position() + anchorOffset`; if the entity is unavailable it renders at the immutable `origin` fallback. Do not invent an attachment type or rotating/bone offset for this contract.

Then add tests and update the character/VFX docs. Do not touch client networking or register another render callback for this normal case.

## Avoid direct coupling

| Anti-pattern | Why |
|---|---|
| Client writing marks map | server-authoritative |
| Spawning nails from client | desync |
| Ability directly calling a client renderer/HUD/camera singleton | breaks dedicated server and core contract |
| Recipe registering packet callbacks, world render events, or a mixin | director owns lifecycle/callbacks |
| New Fabric impl imports | AGENTS forbid |
| Broad new mixins | only if public API is insufficient |
| Copying ProjectJJK decompile | ARR |

## Loadout extension

`ProjectJjkNobaraLoadout.ensureStarterTools` — called on selecting Nobara.
**Status:** VERIFIED

The starter loadout grants one reusable Straw Doll but never grants a target-bound remnant. Remnants remain earned server gameplay state.

## Commands

`JujutsuCommands` — debug/give/hairpin probes. Not a player-progression API.

## Combat extension contracts

`CurseLinkRegistry.createLink/removeLink/removeLinksOwnedBy/linksForParticipant` is a server API. `NailRuntimeAnchorRegistry.register` must return `RESOLVED`, `TEMPORARILY_UNAVAILABLE`, `CONFIRMED_REMOVED`, or `INVALID` deliberately: missing objects must not be reported as removed. `BlackFlashFocus.hasFocus` is read-only for later systems; this slice grants focus but does not assign it a passive bonus.

---
tags: #jujutsumod #api #vfx #verified
