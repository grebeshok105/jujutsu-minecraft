# Megumi Shadow Polish + Shadow Drop — 5-part spec

Approved before implementation. Three features on top of the shadow kit (PR #55): void-black pools,
a smooth visible dive (first and third person), and a new `V` ability — Shadow Drop. Numbers live in
`MegumiProfile`; this document owns the contracts between the five work packages.

Scout evidence this spec relies on:
- Pools render through `RenderType.debugQuads()` — vertex-colored, untextured, unlit, ordinary
  translucent blend. Full opacity is pure `setColor(0,0,0,255)`; nothing else in the pipeline
  lightens it. (`VfxWorldChannel.render` obtains the buffer at :40.)
- `FallingBlockEntity.fall(Level, BlockPos, BlockState)` spawns a falling entity with the given
  state and does NOT touch the world block at `pos`. `disableDrop()` makes landing place no block
  and drop no item. `setHurtsEntities(perDistance, max)` arms vanilla crush damage (anvil default
  2.0f/40). A non-`Fallable` state (clay) still falls and renders fine.
- `setForcedPose` does not exist in 1.21.8. The proven first-person seam is the existing
  `HairpinCameraMixin` (`@Inject(method="setup", at=@At("TAIL"))` on `Camera`) plus
  `HairpinGameRendererMixin` for FOV; `VfxCameraChannel` currently carries yaw/pitch/FOV impulses.
- The proven third-person seam is `CharacterSkinAnimationMixin`'s `@WrapMethod` around
  `LivingEntityRenderer.render` — `PoseStack` is a parameter, translate before `original.call`.
- The client learns SINK timing from the `SHADOW_DIVE` cue itself: `VfxCue.startGameTime` is the
  server game time at emission, `ClientLevel.getGameTime()` is readable every frame.
  `SHADOW_SINK_TICKS`=8, `SHADOW_HIDDEN_TICKS`=4, `SHADOW_EMERGE_TICKS`=6.

## Shared contracts (all parts)

- New wire id: `TERTIARY(8)` appended to `CharacterAbility`. Never renumber 0–7.
- New profile constants (Part 1 adds them; other parts reference by name):
  `DROP_RANGE=20.0`, `DROP_ZONE_HEIGHT_BLOCKS=4.0`, `DROP_ZONE_RADIUS=1.2`,
  `DROP_TELEGRAPH_TICKS=20`, `DROP_ZONE_PULSE_TICKS=5`, `DROP_COOLDOWN_TICKS=160`,
  `DROP_SOFT_DAMAGE_PER_BLOCK=1.0f`, `DROP_SOFT_DAMAGE_MAX=5`,
  `DROP_WEIGHT_SAND=40`, `DROP_WEIGHT_GRAVEL=30`, `DROP_WEIGHT_CLAY=20`, `DROP_WEIGHT_ANVIL=10`.
- New VFX ids (Part 5 declares, Part 1 emits): `megumi/drop_zone_open`, `megumi/drop_zone`,
  `megumi/drop_zone_close`. All broadcast at `MegumiProfile.VFX_DELIVERY_RADIUS`, world-fixed,
  intensity = radius in tenths (12), reusing the existing `MEGUMI_SHADOW_TRAP_OPEN/POOL/CLOSE`
  impact styles — no new ImpactStyle, no new channels.
- New client sink-state cache: `jujutsu.mod.client.render.ShadowBodySink` (Part 3 creates; Part 4
  reads). API: `beginSink(int entityId, long startGameTime)`, `completeSink(int entityId)`,
  `beginEmerge(int entityId, long startGameTime)`, `reset(int entityId)`,
  `float sinkProgress(int entityId, long gameTime)` → 0..1 while sinking, 1 while under, -1 when
  absent; `float emergeProgress(int entityId, long gameTime)` → 0..1 while rising, -1 otherwise.
  Durations read `MegumiProfile.SHADOW_SINK_TICKS` / `SHADOW_EMERGE_TICKS`. Entries are
  self-expiring (TTL like `HiddenBodyRenderGate`) so a lost emerge cue fails open to visible.
- Recipe hook ownership: Part 3 edits ONLY the existing `shadowDive`/`shadowRipple`/`shadowEmerge`
  recipe bodies (adds ShadowBodySink calls). Part 5 ONLY appends the three new drop recipes and
  their `register()` lines. Do not touch each other's methods; coordinate over hub if unsure.

## Part 1 — V ability server core (owner: main agent; the heavy part)

`CharacterAbility.TERTIARY(8)`; `JujutsuKeybinds` third technique key on `V`
(`key.jujutsumod.third_technique`, instant send on press, no sneak variant, no hold gesture);
router arms: Nobara `case TERTIARY -> false;` (own arm — her verifier demands it), Todo folds it
into his refusal list, Megumi routes to the new runtime.

`MegumiShadowDropRuntime` (new, trap-skeleton): one active drop per owner
(`ConcurrentHashMap<UUID, ShadowDrop>`). `tryCast`: resolve target exactly like the trap (range
`DROP_RANGE`, `MegumiSummonRuntime.isEligibleTarget`, owner line of sight), refuse with
`message.jujutsumod.megumi.drop.no_target` otherwise. On accept: record
`{owner, targetUuid, level key, castGameTime}`, start `DROP_COOLDOWN_TICKS` on `TERTIARY`
immediately, emit `drop_zone_open` at the anchor point (target head + `DROP_ZONE_HEIGHT_BLOCKS`),
play `PROJECTJJK_WHOOSH_VORTEX` (0.8f, 0.9f).

Tick: the zone FOLLOWS the live target — re-emit `drop_zone` every `DROP_ZONE_PULSE_TICKS` at the
current head+height point. Target dead/removed/cross-level/unloaded → close early (`drop_zone_close`,
no block). At `castGameTime + DROP_TELEGRAPH_TICKS`: pick the block by the four weights, spawn
`FallingBlockEntity.fall(level, BlockPos.containing(head + height), state)` with zero motion,
`disableDrop()` (no world garbage, ever), `setHurtsEntities(DROP_SOFT_DAMAGE_PER_BLOCK,
DROP_SOFT_DAMAGE_MAX)` for sand/gravel/clay and vanilla `2.0f/40` for the anvil; play
`PROJECTJJK_CINEMATIC_WHOOSH` (0.7f, 1.05f) at the spawn point, emit `drop_zone_close`, drop state.
The falling entity itself is the payload — vanilla renders, falls, crushes and cleans it up.

Lifecycle: owner death/respawn/dimension change/disconnect/server stop/deselect all clear the state
and close the zone cue — copy the trap's registration block verbatim. `MegumiDefinition`: register
the runtime, tear it down in `onDeselected`.

Also Part 1: this spec, Codex note, AGENTS.md facts, SESSION.md, integration, gate, jar, PR.

## Part 2 — Void-black pools (worker)

`ShadowWorldEffects`: every trap-family pool (both `renderShadowTrapPool` overloads — open/close
sweep and constant zone) renders at FULL opacity: alpha 255 across the pool's whole life, killing
the 0.72–0.80 breath and the opacity ramp for these paths ONLY. Blackness must read as a hole in
the world: `setColor(0,0,0,255)`. Keep the fade-in/out on the dogs' decorative summon pool
(`renderMegumiShadowPool`) untouched. The open/close sweep keeps animating RADIUS (unfurl/collapse)
— the surface just must never be translucent while it exists.
Check `ShadowWorldEffectsTest` and any opacity pins (`shadowPoolOpacity` tests pin the summon pool
curve — those stay; add pins that the trap paths ignore opacity, i.e. a test asserting the
trap-pool vertex alpha is constant 255 via whatever seam the test file already uses).
NOT in scope: recipes, runtime, styles enum.

## Part 3 — Smooth dive, third person (worker)

Create `ShadowBodySink` (contract above). Hook it from the three existing Megumi recipes:
`shadowDive` → `beginSink(anchorId, cue.startGameTime())`; `shadowRipple` → `completeSink(anchorId)`
(idempotent; also refreshes the under-TTL); `shadowEmerge` → `beginEmerge(anchorId,
cue.startGameTime())` then existing markRevealed stays.

`CharacterSkinAnimationMixin`: before `original.call` (after the hidden-gate early return), for a
`PlayerRenderState`, read `sinkProgress`/`emergeProgress` and `matrices.translate(0, -1.9f * eased,
0)` — eased = smoothstep of progress; sinking goes 0→1 (body fully under at the end of SINK, which
is exactly when the first ripple hides it), emerging plays 1→0 over `SHADOW_EMERGE_TICKS`. Wrap in
push/popPose only if not already inside the scale guard's push — reuse the existing structure.
The nametag/layers all live inside the wrapped render, so they sink with the body for free.
A body that is neither sinking nor emerging must take the exact current code path (zero overhead).
Add a plain-JUnit test for the progress/easing math (pure functions on ShadowBodySink — clock/game
time injected the way HiddenBodyRenderGate-style classes are tested here, no Minecraft boot).

## Part 4 — Smooth dive, first person (worker)

Local caster only; read `ShadowBodySink`, never write it.

Camera: extend `VfxCameraChannel` with a vertical dive offset — `float diveOffsetBlocks()` computed
from ShadowBodySink for the camera entity (this keeps the channel the single camera authority; no
new manager). Consume it in the existing `HairpinCameraMixin` `setup` TAIL: `setPosition(pos.x,
pos.y - offset, pos.z)`. Curve: during SINK the camera drops smoothly to **-0.85** blocks (eased,
never below — the camera must stay out of the ground so no x-ray/suffocation overlay); while under
(HIDDEN/SUBMERGED) it holds at **-0.35** with a fast 3-tick ease from the sink bottom (the shadow
glide); during EMERGE it rises -0.5 → 0 over the emerge window; absent state = exact 0 and zero
extra work per frame.

Darkness: a full-screen black fade following the same beats, rendered as a HUD contribution
(`VfxDirector.registerHudContribution`, pattern: `MegumiCooldownHud` for registration and the
nausea overlay in `VfxHudChannel.renderNausea` for the draw): alpha 0→0.75 across SINK, ease down
to 0.25 and hold while under, 0.45→0 across EMERGE. Pure black `0x000000` fill, drawn under the
crosshair layer like nausea. Register from `MegumiClientDefinition.registerClientHooks()`.
No new mixins beyond the camera consumption line; FOV stays untouched.
Add a deterministic test for the curve math if the channel already has clock-seam tests — extend
that file, same style.

## Part 5 — Drop ability client + tests (worker)

`MegumiVfxIds`: append `DROP_ZONE_OPEN("megumi/drop_zone_open")`, `DROP_ZONE("megumi/drop_zone")`,
`DROP_ZONE_CLOSE("megumi/drop_zone_close")` to the fields and the LIVE set (order: after
SHADOW_EMERGE). `MegumiVfxRecipes`: three one-shot recipes behind `isOpeningBeat`, reusing the
existing trap ImpactStyles at the cue origin (the zone hangs in the air where the cue says — the
pool renderer does not care about ground): open = `MEGUMI_SHADOW_TRAP_OPEN` 10t + a 6-mote ring;
zone = `MEGUMI_SHADOW_POOL` 7t (pulse period 5 + slack, so the hovering disc never blinks) + 2
motes drifting DOWN from the disc rim (negative y velocity — the tell that something will fall);
close = `MEGUMI_SHADOW_TRAP_CLOSE` 8t. Durations as named constants. Register all three.

Roster: 6th ability row on Megumi's card — `JujutsuCharacterIcons.BOOM`,
`screen.jujutsumod.character_select.ability.shadow_drop`, key label `"V"`.
Lang (en_us + ru_ru): `key.jujutsumod.third_technique` ("Third technique" / "Третья техника"),
the roster label ("Shadow Drop" / "Теневой обвал"), and
`message.jujutsumod.megumi.drop.no_target` ("No shadow can reach that target" / consistent with
existing Megumi refusal strings).

Tests (exact expectations from the input-contract scout):
- `CharacterAbilityWireFormatTest`: SHIPPED_IDS += TERTIARY→8 (derived tests then cover 0..8).
- `MegumiAbilitySlotsTest`: rename six→seven, assert `case TERTIARY ->` reaches the drop runtime.
- `VfxCueTest.liveWireStringsRemainStable`: += the three drop wire strings.
- `VfxCompletenessTest`: live-id totals 43 → 46 in both assertions.
- `VfxRadiusContractTest`: the three ids join the "Megumi shadow kit" finiteNone group; delivery
  radius pins follow the existing megumi pattern (all broadcast at VFX_DELIVERY_RADIUS).
- `MegumiProfileTest`: pin the new DROP_* constants to the table above; invariants — weights sum
  100, telegraph < cooldown, zone radius < trap radius.
- `CharacterClientRegistryTest` needs NO edit (derives 6 from the router) — run nothing; just keep
  the card at exactly the slots the router answers.
NOT in scope: keybind file, routers, runtime, profile (Part 1 owns those; write tests against the
contract names in this spec).

## Manual smoke additions (SESSION.md gets the checklist)

Void-black pools read as holes; sink visibly lowers the body then the camera; V on a target opens a
following overhead disc, one weighted block falls after 1 s, anvil hurts, nothing litters the
world; V refusals (no target, cooldown) match Megumi's message style; Nobara/Todo V = silent no-op.
