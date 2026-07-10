# VFX Core — Nobara Reference Implementation

← [[00-MOC]] · [[Hairpin-effects]] · [[../02-architecture/Networking]] · [[../02-architecture/Client-server-boundaries]] · [[../05-reference/Public-api-surface]]

Prefix: `.worktrees/nobara-cinematic-slice/` on branch `codex/nobara-cinematic-slice`.

## Purpose

VFX Core is the only transient combat-effect path. A server-confirmed ability emits a typed `VfxCue`, the Fabric S2C payload carries it, and the client-only director turns it into a short Java recipe. Future agents add an ID, a server cue, one recipe, tests, and documentation — not a packet switch, render callback, HUD singleton, camera mixin, and particle helper per effect.

```mermaid
flowchart LR
  S["Server ability"] --> C["VfxCue"]
  C --> P["VfxCuePayload"]
  P --> D["VfxDirector (client)"]
  D --> R["VfxRecipe / VfxInstance"]
  R --> W["world, particles, sound, HUD, camera, first-person"]
```

## Shared contract

| Type | Responsibility | Source | Status |
|---|---|---|---|
| `VfxCue` | effect ID, world origin, optional entity ID anchor, intensity, server game time, seed | `vfx/VfxCue.java:6-14` | VERIFIED |
| `VfxCuePayload` | typed S2C serialization of exactly one cue | `network/VfxCuePayload.java:9-40` | VERIFIED |
| `JujutsuNetworking.broadcastVfxCue` / `sendVfxCue` | radius-filtered or direct server send, both capability-gated | `network/JujutsuNetworking.java:38-60` | VERIFIED |
| `VfxAnchorResolver` | use a live client anchor when present, otherwise the immutable cue origin | `vfx/VfxAnchorResolver.java:9-15` | VERIFIED |
| `VfxTimeline` | calculate late-packet age and reject expired instances | `vfx/VfxTimeline.java`; `VfxTimelineTest.java` | VERIFIED |

The cue is visual-only. It never carries damage, marks, cooldowns, entity spawning, or other gameplay authority.

## Client director

`VfxDirector` is initialized once from `JujutsuModClient`, then Nobara registers recipes before client packet receivers. It owns a 64-instance bound, unknown-ID warning-once behavior, expiry cleanup, world-unavailable cleanup, disconnect cleanup, the one `AFTER_ENTITIES` world callback, and the HUD callback.

| Channel | Role | Source | Status |
|---|---|---|---|
| World | transient rings, ribbons, blades | `client/vfx/VfxWorldChannel.java` via `VfxDirector.java:44,101-103` | VERIFIED |
| Particles | density-scaled local burst/ring helpers | `VfxParticleChannel.java`, `VfxContext.java:76-83` | VERIFIED |
| Sound | local no-falloff SFX | `VfxSoundChannel.java`, `VfxContext.java:85-87` | VERIFIED |
| HUD | impact/swing flash and vignette | `VfxHudChannel.java`, `VfxDirector.java:45,105-107` | VERIFIED |
| Camera/FOV | narrow existing camera/game renderer mixins read director offsets | `HairpinCameraMixin.java:26-27`, `HairpinGameRendererMixin.java:15` | VERIFIED |
| First person | narrow existing hand mixin reads the director pose | `NobaraFirstPersonSnapMixin.java:24` | VERIFIED |

`VfxQuality` maps the vanilla particle setting to full, reduced (0.58), and minimal (0.28) density. Individual recipes use proximity to omit local spectacle at distance. Quality and culling only change client rendering, never cue delivery or gameplay.

## Agent authoring contract

Required path: **stable ID -> server-confirmed cue -> client recipe -> automated and in-game verification**.

1. Add a stable `ResourceLocation` to an appropriate `*VfxIds` class.
2. At the server-confirmed gameplay point, build a `VfxCue` with server game time and a server seed, then call `JujutsuNetworking.broadcastVfxCue` or `sendVfxCue`.
3. Add a `VfxRecipe` registration. The recipe returns a `VfxInstance`; its starter receives `VfxContext` and uses only director channels.
4. Add assertion coverage for ID/registration and any new pure timeline/anchor policy.
5. Update this note, the character VFX note, MOC, and affected networking/boundary docs.

### Forbidden shortcuts

- Do **not** register a packet receiver per effect.
- Do **not** couple an ability directly to a renderer, client channel, render callback, or static VFX manager; the ability emits only a cue.
- Do **not** register `WorldRenderEvents`, HUD callbacks, or a new mixin from a recipe.
- Do **not** mutate gameplay state or send packets from the client recipe.
- Do **not** reintroduce `ProjectJjkNobaraImpulsePayload` or the removed Hairpin static managers.
- Do **not** add a JSON/DSL editor, preview mode, generic GeckoLib bone attachment, or shader dependency in V1.

A later shader/post-process spike may add an internal backend behind the director only after a compatible Fabric 1.21.8 route is independently validated. It is not an authoring API today.

## Nobara reference scenes

| Scene | IDs | VFX language | Status |
|---|---|---|---|
| Hammer / launch | `hammer`, `impact`, `impact_sound` | forged-metal beat, cyan-white hit, camera/HUD response | VERIFIED |
| Resonance / link | `resonance_channel`, `resonance_strike`, `link_bind`, `detonate` | cursed-energy pulse, binding ring, particle burst, target-origin timing | VERIFIED |
| Enlarge / Boom | `enlarge`, `explosion`, `first_person_snap` | cyan rings, ribbons/blades, shards, sound stack, HUD/camera, caster hand snap | VERIFIED |

All ten IDs are registered in `NobaraVfxRecipes.java:23-34`. `ProjectJjkNailRenderer` remains state-driven for persistent real nail aura and shares `VfxPalette`; it is deliberately not forced into a transient timeline.

## Verification

- Pure assertion tasks cover cue codec/seed, timeline age/expiry, anchor fallback, quality scaling, registration, transport guards, and legacy-path absence.
- `ProjectSanityTest.java:305-362,405` prevents an accidental return to the old payload/static-manager path and checks all core Nobara reference points.
- On 2026-07-10, `check` and `build --no-daemon -x test` were successful and all seven assertion tasks passed.
- The same pass completed startup/log smoke through mod, LWJGL, OpenAL, resource, and atlas initialization without a logged fatal/error. It did not perform gameplay interaction.
- Hammer/launch, resonance/link, Enlarge/Boom, live anchor death/despawn, reduced particles, and two-client observation remain **UNKNOWN** because UI automation was explicitly prohibited. A startup log is not gameplay verification.

---
tags: #jujutsumod #vfx #nobara #architecture #verified
