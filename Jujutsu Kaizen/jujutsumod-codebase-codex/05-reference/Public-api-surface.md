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
| `ProjectJjkNobaraRuntime` / `ProjectJjkRitualRuntime` | server combat flow | runtime files |
| `TargetResolver` | aiming | combat |
| `NobaraVfxIds` (or future character `*VfxIds`) | stable visual event vocabulary | `vfx/NobaraVfxIds.java:6-15` |
| `JujutsuNetworking.broadcastVfxCue` / `sendVfxCue` | only generic server-to-client VFX sends | `network/JujutsuNetworking.java:38-59` |
| `VfxDirector.register` + `VfxRecipe` + `VfxInstance` | typed client composition point | `client/vfx/VfxDirector.java:50-54` |
| `VfxContext` | world/particles/sound/HUD/camera/first-person channels | `client/vfx/VfxContext.java` |
| `VfxPalette` | shared cursed-energy colors for compatible persistent renderers | `client/vfx/VfxPalette.java` |

**Status:** VERIFIED as current extension hubs.

## VFX authoring minimum

```java
// 1. central ID
public static final ResourceLocation ABILITY_HIT = JujutsuMod.id("character/ability_hit");

// 2. server-confirmed action
JujutsuNetworking.broadcastVfxCue(level, origin, 64.0,
    new VfxCue(ABILITY_HIT, origin, VfxCue.NO_ANCHOR, intensity,
        level.getGameTime(), level.random.nextLong()));

// 3. client registration
VfxDirector.register(ABILITY_HIT, cue -> VfxInstance.of(12,
    (context, initialAge) -> context.hud().triggerImpact(1.0f)));
```

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

## Commands

`JujutsuCommands` — debug/give/hairpin probes. Not a player-progression API.

---
tags: #jujutsumod #api #vfx #verified
