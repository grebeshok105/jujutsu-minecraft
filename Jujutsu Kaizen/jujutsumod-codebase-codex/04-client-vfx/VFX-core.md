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

Delivery and presentation radii are separate owners. Delivery is the server radius used to decide which clients receive a cue; presentation is the client attenuation radius used by a recipe or channel. For every effect family:

```text
presentation radius <= delivery radius
```

Do not reuse gameplay target ranges as VFX radii. If delivery and presentation intentionally differ, both values must be named and the reason must be documented beside them. In PR 1 this is a target contract, not a claim that every existing literal has been audited; the existing call sites remain unchanged, and the later radius-hardening work must enumerate and test each live family.

Duration ownership follows the same rule: one semantic lifetime has one named owner and feeds every consumer that represents that lifetime. Intentional sub-lifetimes use separate named values and document or test their relationship.

Each vessel registers its own recipe pack from `CharacterClientDefinition.registerClientHooks()` — Nobara's registers `NobaraVfxRecipes`, Todo's `TodoVfxRecipes` — installed once by `JujutsuCharacterClients.registerAll()` at client init, after `VfxDirector.initialize()` because the recipes register into the director it builds. The aggregate `JujutsuVfxRecipes` this replaced is deleted, so the list of who has recipes cannot drift from the list of who exists. See [Vessel definitions](../02-architecture/Vessel-definitions.md).

VfxDirector owns recipe registration, active-instance cap 64, cue age/expiry, world identity, disconnect cleanup, render callbacks, and shared channels. Unknown ids are logged once and ignored.

NobaraVfxIds defines 25 ids. TodoVfxIds defines four: `todo/boogie_woogie`, `todo/swap_endpoint`, `todo/feint_tell`, and `todo/pair_mark`. MegumiVfxIds defines four Divine Dog ids. `DOGS_SUMMON` keeps one player-anchored cue for the body animation and adds one exact-origin dog-anchored cue per dog for the 16-tick shadow-open pool; `DOGS_RECALL` is likewise one still-living dog cue per 12-tick shadow-close pool, never a synthetic owner pool. `DOGS_POUNCE` is a target-anchored ring/burst emitted only after accepted server damage. The shared world channel owns both bounded ground styles and Megumi's client definition registers the shadow-mote provider. ProjectSanityTest requires age-aware real-time channel calls and rejects removed legacy managers/mixins. Six client mixins are configured; VfxDeltaTrackerMixin is intentionally absent.

VfxTimeChannel is a bounded client VFX primitive, but production code must not scale global Minecraft DeltaTracker time. Resonance gameplay hit-stop is separately and intentionally server-global through ServerTimeDilation.

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
