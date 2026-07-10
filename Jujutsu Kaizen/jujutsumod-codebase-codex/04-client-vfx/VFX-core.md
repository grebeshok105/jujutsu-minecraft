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
| `VfxCue` | effect ID, immutable world-origin fallback, optional entity ID anchor, world-space anchor offset, intensity, server game time, seed | `vfx/VfxCue.java:6-15` | VERIFIED |
| `VfxCuePayload` | typed S2C serialization of exactly one cue | `network/VfxCuePayload.java:9-40` | VERIFIED |
| `JujutsuNetworking.broadcastVfxCue` / `sendVfxCue` | radius-filtered or direct server send, both capability-gated | `network/JujutsuNetworking.java:38-60` | VERIFIED |
| `VfxAnchorResolver` | resolve a live anchor as `anchor position + anchorOffset`, otherwise use the immutable cue origin | `vfx/VfxAnchorResolver.java:9-15`; `client/vfx/VfxWorldChannel.java:34-69` | VERIFIED |
| `VfxTimeline` | calculate late-packet age, admit opening beats only while age is `< 2` ticks, offset realtime clocks, and reject expired instances | `vfx/VfxTimeline.java:10-27`; `VfxTimelineTest.java:17-43` | VERIFIED |

The cue is visual-only. It never carries damage, marks, cooldowns, entity spawning, or other gameplay authority.

Anchor semantics are explicit. Unanchored cues use `VfxCue.NO_ANCHOR` with `Vec3.ZERO`. Anchored server cues store `origin.subtract(anchor.position())`; while the entity exists, the client resolves `anchor.position().add(cue.anchorOffset())`. If the entity is missing or despawned, resolution returns the original immutable `cue.origin()`. This preserves eye/center displacement while an anchor moves without introducing local-space rotation, bones, or attachment types (`ProjectJjkNobaraRuntime.java:233-238`; `ProjectJjkRitualRuntime.java:601-606`; `VfxAnchorResolverTest.java:16-40`).

## Client director

`VfxDirector` is initialized once from `JujutsuModClient`, then Nobara registers recipes before client packet receivers. It owns a 64-instance bound, unknown-ID warning-once behavior, expiry cleanup, the one `AFTER_ENTITIES` world callback, and the HUD callback. It tracks `ClientLevel` by object identity: a changed level clears every active instance/channel before rebinding, while `level == null` and disconnect both clear and reset the tracked level to `null` (`VfxDirector.java:25-148`).

For every non-expired late cue, the director computes and passes the actual `initialAgeTicks` into the recipe (`VfxDirector.java:59-82`). Nobara recipes suppress elapsed one-shot sound/particle opening beats at age `>= 2` ticks but pass the age into all 23 HUD, camera/FOV, first-person, and post-process starts, whose realtime timestamps are offset instead of restarting from zero (`NobaraVfxRecipes.java:41-234`; `VfxTimeline.java:18-27`). World impact geometry remains active for the cue's remaining server-time phase. Each render resolves the retained cue's current entity anchor plus its world-space offset and falls back to `cue.origin()` after despawn (`VfxWorldChannel.java:34-69`; `VfxAnchorResolver.java:9-15`). The first-person snap lasts `0.75s` and traverses the complete `0..15` phase scale (`VfxFirstPersonChannel.java:14-59`; guard `ProjectSanityTest.java:380-393`).

| Channel | Role | Source | Status |
|---|---|---|---|
| World | transient rings, ribbons, blades with per-render live-anchor resolution | `client/vfx/VfxWorldChannel.java:34-69` via `VfxDirector.java:101-103` | VERIFIED |
| Particles | density-scaled local burst/ring helpers | `VfxParticleChannel.java`, `VfxContext.java:76-83` | VERIFIED |
| Sound | local no-falloff SFX | `VfxSoundChannel.java`, `VfxContext.java:92-94` | VERIFIED |
| HUD | impact/swing flash and vignette | `VfxHudChannel.java`, `VfxDirector.java:47,105-107` | VERIFIED |
| Camera/FOV | narrow existing camera/game renderer mixins read director offsets | `HairpinCameraMixin.java:26-27`, `HairpinGameRendererMixin.java:15` | VERIFIED |
| First person | 0.75-second snap over the full `0..15` phase; narrow existing hand mixin reads the director pose | `VfxFirstPersonChannel.java:14-59`, `NobaraFirstPersonSnapMixin.java:24` | VERIFIED |
| Post-process | age-aware vanilla blur requested only by recipes and rendered by the director; runtime/linkage failure disables blur for the client session while all shaderless channels continue | `VfxPostProcessChannel.java:7-46`, `VfxDirector.java` | VERIFIED |

`VfxQuality` maps the vanilla particle setting to full, reduced (0.58), and minimal (0.28) density. Individual recipes use proximity to omit local spectacle at distance. Quality and culling only change client rendering, never cue delivery or gameplay.

## Agent authoring contract

Required path: **stable ID -> server-confirmed cue -> client recipe -> automated and in-game verification**.

1. Add a stable `ResourceLocation` to an appropriate `*VfxIds` class.
2. At the server-confirmed gameplay point, build a `VfxCue` with server game time and a server seed, then call `JujutsuNetworking.broadcastVfxCue` or `sendVfxCue`. Use `Vec3.ZERO` for an unanchored cue; for an entity anchor store `origin.subtract(anchor.position())`.
3. Add a `VfxRecipe` registration. The recipe returns a `VfxInstance`; its starter receives `VfxContext` and uses only director channels.
4. Add assertion coverage for ID/registration and any new pure timeline/anchor policy.
5. Update this note, the character VFX note, MOC, and affected networking/boundary docs.

### Forbidden shortcuts

- Do **not** register a packet receiver per effect.
- Do **not** couple an ability directly to a renderer, client channel, render callback, or static VFX manager; the ability emits only a cue.
- Do **not** register `WorldRenderEvents`, HUD callbacks, or a new mixin from a recipe.
- Do **not** mutate gameplay state or send packets from the client recipe.
- Do **not** reintroduce `ProjectJjkNobaraImpulsePayload` or the removed Hairpin static managers.
- Do **not** add a JSON/DSL editor, preview mode, generic GeckoLib bone attachment, or external shader dependency in V1.

Post-process is intentionally a narrow internal channel, not an authoring framework. Recipes may request the existing age-aware blur, but must keep world/HUD/camera/particle composition complete when blur disables itself.

## Nobara reference scenes

| Scene | IDs | VFX language | Status |
|---|---|---|---|
| Hammer / launch | `hammer`, `impact`, `impact_sound` | forged-metal beat, cyan-white hit, camera/HUD response | VERIFIED |
| Resonance / link | `resonance_channel`, `resonance_strike`, `link_bind`, `detonate` | cursed-energy pulse, binding ring, particle burst, target-origin timing | VERIFIED |
| Enlarge / Boom | `enlarge`, `explosion`, `first_person_snap` | cyan rings, ribbons/blades, shards, sound stack, HUD/camera, caster hand snap | VERIFIED |
| Straw Doll ritual | `remnant_drop`, `ritual_bind`, `doll_strike`, `resonance_release` | trace pickup, constricting bind, effigy puncture, dark-center/cyan-fracture remote release | VERIFIED |

All 14 IDs are registered in `NobaraVfxRecipes.java:25-38`. Enlarge now stages compression, a held frame, then blades/rings; Explosion stages implosion then staggered shell breaks. `ProjectJjkNailRenderer` remains state-driven for persistent real nail aura, renders a compressed-energy envelope around prepared/flying nails, and shares `VfxPalette`; embedded nails deliberately have no broad aura.

## Verification

- Pure assertion tasks cover cue codec/seed/anchor offset, timeline age/expiry/opening-window/realtime offsets, zero/non-zero live-anchor resolution, immutable-origin fallback, quality scaling, registration, transport guards, lifecycle cleanup, all 23 age-aware timed-channel calls, blur fallback wiring, straw-doll resource completeness, and legacy-path absence.
- `ProjectSanityTest.java:303-393` prevents an accidental return to the old payload/static-manager path and checks the current timeline, lifecycle, live-anchor, and first-person wiring; legacy absence assertions are at `ProjectSanityTest.java:372-377`.
- Anchor-offset TDD reproduced the eye-height loss, then produced the expected RED because the seven-field constructor and `anchorOffset()` did not yet exist. Focused GREEN `testVfxAnchor testVfxCore testVfxTimeline --no-daemon` passed after `a2cf61c`.
- The prior VFX Core baseline closed with seven assertion tasks. The Straw Doll expansion adds two assertion tasks; fresh full verification and independent review are recorded in the current session handoff.
- Standard Gradle `runClient` was blocked by Windows system commit limit `errno=1455`. A terminal-only direct launch of the same generated Loom client config, without a Gradle daemon and with `-Xms128m -Xmx1024m`, reached `JujutsuMod initialized`, LWJGL, OpenAL, resource reload, and atlas creation. `logs/2026-07-10-1.log.gz` contained no `ERROR`/`FATAL`; the process was intentionally stopped with Ctrl+C. This was startup smoke, not gameplay QA.
- Hammer/launch, Straw Doll acquisition/ritual, Enlarge/Boom, live anchor death/despawn, blur availability, reduced particles, and two-client observation remain **UNKNOWN** because UI automation is prohibited. Compilation and startup logs are not gameplay verification.

---
tags: #jujutsumod #vfx #nobara #architecture #verified
