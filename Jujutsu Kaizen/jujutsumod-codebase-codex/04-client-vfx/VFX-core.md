# VFX Core

Status: CURRENT

Canonical path:

server-confirmed action → VfxCue/VfxCuePayload → JujutsuClientNetworking → VfxDirector → character recipes → director-owned channels

## Canonical cue construction

`VfxCues` is the shared transport factory for common cue shapes. It owns wire-field mechanics while effect runtimes continue to own effect ids, delivery radius, timing decisions, and visual semantics.

The supported shapes are:

- `worldFixed`: an exact world origin with no anchor, offset, or orientation;
- `worldFixedDirected`: an exact world origin with orientation;
- `worldFixedDisplacement`: an exact world origin with full displacement in `anchorOffset`;
- `anchored`: an exact origin reconstructed from an entity anchor and its offset;
- `anchoredDirected`: the anchored shape with orientation.

`VfxCue` normalizes `direction`. It is therefore orientation only; it must never be the sole owner of a meaningful distance, speed, size, or displacement. `worldFixedDisplacement` stores the complete displacement in `anchorOffset`, including zero displacement as `Vec3.ZERO`; its `direction` is only the normalized orientation derived from that displacement. Packed offsets with an independent direction remain explicit emitter-level transport shapes until a later migration gives them a named factory.

Nobara's `NailTrapRuntime.collapseCue` uses `worldFixedDisplacement` for `NAIL_TRAP_COLLAPSE`: the origin is the nail position and `anchorOffset` is the full `target - nail` displacement. The collapse recipe may therefore reconstruct the exact target endpoint; the server particle trail remains an independent presentation detail.

The existing minimum intensity policy is preserved: factory output clamps intensity to at least `1` and does not add a maximum clamp. The factory is the minimum-clamp owner for callers that adopt it; existing recipes may retain a defensive read-side clamp during staged migration, and the later migration must prove when that duplicate can be removed. Factories pass `effectId`, origin, anchor data, intensity, game time, seed, and orientation through without changing wire order or adding protocol fields.

Delivery and presentation radii are separate owners. Delivery is the named server radius used to decide which clients receive a cue; presentation is the named client attenuation radius used by a recipe or channel. For every effect family:

```text
presentation radius <= delivery radius
```

Do not reuse gameplay target ranges as VFX radii. If delivery and presentation intentionally differ, both values must be named and the reason must be documented beside them. PR 5 makes the finite-radius part executable from compiled production bytecode: the contract follows concrete id references through local helpers to real networking calls, collects every delivery owner for multi-site ids, and checks presentation against the minimum owner. Direct `sendVfxCue` ids have no finite delivery radius and are checked separately. Recipe families with no proximity attenuation use explicit `PresentationKind.NONE`, rather than a numeric zero sentinel. Todo's swap delivery remains `64.0` versus clap presentation `56.0`; Megumi's delivery is `48.0`; Nobara's existing presentation families remain `40.0`, `48.0`, `56.0`, and `64.0` under recipe-owned names.

Duration ownership follows the same rule: one semantic lifetime has one named owner and feeds every consumer that represents that lifetime. Intentional sub-lifetimes use separate named values and document or test their relationship.

Each vessel registers its own recipe pack from `CharacterClientDefinition.registerClientHooks()` — Nobara's registers `NobaraVfxRecipes`, Todo's `TodoVfxRecipes`, and Megumi's `MegumiVfxRecipes` — installed once by `JujutsuCharacterClients.registerAll()` at client init, after `VfxDirector.initialize()` because the recipes register into the director it builds. The aggregate `JujutsuVfxRecipes` this replaced is deleted, so the list of who has recipes cannot drift from the list of who exists. See [Vessel definitions](../02-architecture/Vessel-definitions.md).

VfxDirector owns recipe registration, cue age/expiry, world identity, disconnect cleanup, render callbacks, and seven live channels: world, HUD, camera, first-person, particles, sound, and post-process. It does not retain a director-side collection of recipe instances: an accepted cue creates its instance, rejects expiry before start, computes late-cue age, and starts exactly once. After start, retained state belongs to the concrete channels, while level change and disconnect cleanup clear those channel owners. The removed size-64 bookkeeping cap never limited visible effects; the real world-render cap remains `VfxWorldChannel.MAX_IMPACT_FLASHES = 48`. `VfxTimeChannel` was removed because no production consumer applied its stored scale; client-global slow motion is not a VFX Core feature. Resonance's server-global hit-stop remains a separate accepted gameplay/presentation decision through `ServerTimeDilation`. Unknown ids are logged once and ignored.

NobaraVfxIds defines 21 live ids after four dead ids and their aliases were removed. TodoVfxIds defines seven live ids. MegumiVfxIds defines five Divine Dog ids. Across the three owners, 33 declared live ids remain, and all live wire strings are unchanged. Every owner exposes explicit `LIVE` and `PLANNED` sets; `PLANNED` is currently empty, and every live id must have exactly one recipe plus a production emitter reference. Recipe completeness calls the real three recipe packs against the director registry, whose duplicate registration remains a hard failure. Emitter coverage scans only compiled `src/main` bytecode at method level and follows local helper calls, so an unrelated id mention in the same class cannot satisfy coverage; comments, docs, tests, and string literals cannot satisfy it. The radius inventory reuses that production graph and resolves named delivery fields from the actual network path, including helper calls and multi-site owners. `DOGS_SUMMON` keeps one player-anchored cue for the body animation and adds one exact-origin dog-anchored cue per dog for the 16-tick shadow-open pool; `DOGS_RECALL` is likewise one still-living dog cue per 12-tick shadow-close pool, never a synthetic owner pool. `DOGS_POUNCE` is a target-anchored ring/burst emitted only after accepted server damage. The shared world channel owns both bounded ground styles and Megumi's client definition registers the shadow-mote provider. ProjectSanityTest requires age-aware real-time channel calls and rejects removed legacy managers/mixins. Six client mixins are configured; VfxDeltaTrackerMixin is intentionally absent.

## PR 5 contract hardening

`VfxCueTest` is a JUnit 5 test over the real `VfxCuePayload.STREAM_CODEC`. It covers all eight fields for world-fixed and entity-anchored cues, normalized and zero directions, stable wire strings, and an exhausted buffer. The separate `VfxCuesTest` retains the factory-only sentinel rejection check. The old `testVfxCore` JavaExec task is removed.

Packed Hairpin explosion intensity clamps depth to `1..3` and keeps the finale bit independent. Duration owners are named at the recipe sites: equal recipe/world lifetimes reuse one value, while Black Flash deliberately keeps a longer recipe lifetime than its retained world impact because camera/HUD tails outlive the world geometry. The contract tests check those relationships without freezing tuning literals or source formatting.

Client-global slow motion is deliberately absent: the former channel had writers but no consumer. Reintroducing it requires an approved design with an explicitly named consumer and lifecycle.

Effects use cue age to reject or seek late playback rather than replaying stale beats from the start. Persistent visuals are not VFX Core's job: nails are drawn by `ProjectJjkNailRenderer` (see [Nail rendering](Nail-rendering.md)), while transient compression, snap, burst, residue, camera, and sound beats belong to recipes and channels.

## HUD is not a Screen

"HUD" in this Codex means in-world combat overlays owned by `VfxDirector`, never the ClickGui menu. Do not merge the concepts: menus are Screens with input focus, HUD draws are one registered element that never takes input.

`VfxDirector` registers exactly one HUD element — `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, jujutsumod:vfx_overlay, VfxDirector::renderHud)` (VERIFIED). Adding a second `HudRenderCallback` for one ability, or a per-effect HUD singleton, is forbidden by this contract; so is a new mixin for a single flash, and so is sending any gameplay packet from HUD code.

### VfxHudChannel API

Source: `client/vfx/VfxHudChannel.java`. Status: VERIFIED API surface.

| API | Effect |
|---|---|
| `triggerSwing(proximity[, initialAgeTicks])` | short cinematic plus flash scaled by proximity |
| `triggerImpact(proximity[, initialAgeTicks])` | impact cinematic plus proximity-scaled flash |
| `triggerFlash(durationMillis, maxAlpha[, initialAgeTicks])` | full-screen flash alpha envelope |
| `triggerNausea(strength[, durationMillis], initialAgeTicks)` | nausea overlay, e.g. Resonance target-local |
| `render` | called only from the director's HUD registration |
| `clear` | on level change and disconnect |

Timing, seed, and intensity all come from the server cue. The client never damages, never applies marks, and never opens a menu from HUD code. Late packets pass `initialAgeTicks` into the channel starts, which is why most methods have an age-aware overload.

There is no cursed-energy resource bar in the current kit.

`NobaraHudState` is a client-side predicate — "is this player holding a kit item?" — not a renderer and not a Screen.

## Adding a character

Additional characters add `<Character>VfxIds` / `<Character>VfxRecipes` and wire them through the same aggregate entrypoint. Own your cue ids: `TodoBlackFlashRuntime` currently broadcasts `NobaraVfxIds.BLACK_FLASH`, which is a known cross-character seam and not a pattern to copy — see [Todo Boogie Woogie](../03-systems/Todo-Boogie-Woogie.md).

First-person hand styles are a shared channel, not a per-vessel mixin: `VfxFirstPersonChannel.Style` currently has SNAP (Nobara) and CLAP (Todo), both handled in `FirstPersonHandFxMixin`. See [Vessel render stack](Vessel-render-stack.md).
