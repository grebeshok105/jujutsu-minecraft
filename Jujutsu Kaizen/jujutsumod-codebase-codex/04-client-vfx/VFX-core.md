# VFX Core

Status: CURRENT - FREEZE BLOCKED pending real client/world smoke and PR 8 visual comparison

Code baseline: `11b4d5ae5f3871ef77a58f55533e700fd68d0c27` (PR 8 squash merge, 2026-07-30)

This is the canonical VFX Core contract. It describes the current code and passing automated contracts; the real client smoke checklist is owned by [BUILDING_IN_SANDBOX](../../../docs/BUILDING_IN_SANDBOX.md). A green `qualityGate` does not prove a rendered frame or in-world gameplay.

## 1. Purpose and authority boundary

VFX Core owns transient presentation only. Server gameplay decides whether an action succeeds and emits a visual-only cue. The client never deals damage, applies marks, starts cooldowns, selects targets, moves entities, or decides ability success. Persistent visuals that follow a real entity or gameplay state stay on that entity/state renderer.

The authority order is current code and tests, `AGENTS.md`, `SESSION.md`, this Codex, and `docs/KNOWN_ISSUES.md`.

## 2. Canonical cue path

```text
server-confirmed action
    -> VfxCue
    -> VfxCuePayload
    -> JujutsuClientNetworking
    -> VfxDirector
    -> <Character>VfxRecipes
    -> director-owned channels
```

Each vessel registers its recipe pack from `CharacterClientDefinition.registerClientHooks()`. Client bootstrap initializes `VfxDirector` first and then installs the three vessel packs through `JujutsuCharacterClients.registerAll()`.

## 3. Eight-field transport contract

`VfxCuePayload` carries one cue whose wire data has exactly eight fields, in fixed order:

1. `effectId`
2. `origin`
3. `anchorEntityId`
4. `anchorOffset`
5. `intensity`
6. `startGameTime`
7. `seed`
8. `direction`

`VfxCue` normalizes `direction`. It is orientation, never the sole owner of meaningful distance, speed, size, or displacement. Full displacement belongs in `anchorOffset`; `NO_ANCHOR` remains the explicit world-fixed sentinel. Field order and live id strings are wire compatibility contracts.

Delivery radius is selected by the server emitter. Presentation attenuation is selected by the client recipe/channel. Presentation radius must not exceed delivery radius, and gameplay target ranges must not be reused accidentally as visual radii.

## 4. Id lifecycle

The three live sets are registry-owned and currently contain:

| Owner | Live ids | Planned ids |
|---|---:|---:|
| Nobara | 21 | empty |
| Todo | 7 | empty |
| Megumi | 5 | empty |
| **Total** | **33** | **empty** |

Every live id has exactly one recipe and a production emitter reference. A planned id, if introduced during staged work, must be explicit, temporary, and tied to a follow-up reference; it must not be presented as complete. Removing an id removes its constant, recipe, test pin, documentation and emitter references together.

## 5. Recipe registration

The recipe packs are:

- `NobaraVfxRecipes`
- `TodoVfxRecipes`
- `MegumiVfxRecipes`

There is no aggregate `JujutsuVfxRecipes`. Duplicate registration is a hard failure. Effect-specific receivers, packet types, HUD callbacks, camera managers and VFX mixins are not part of the recipe contract.

## 6. Director and seven channels

`VfxDirector` owns registration, unknown-id logging, cue expiry and late-cue age, level identity, the single HUD overlay registration, and cleanup. Its complete live channel surface is:

1. `VfxWorldChannel`
2. `VfxHudChannel`
3. `VfxCameraChannel`
4. `VfxFirstPersonChannel`
5. `VfxParticleChannel`
6. `VfxSoundChannel`
7. `VfxPostProcessChannel`

The director retains no active-instance collection. An accepted cue creates its instance, rejects expiry before start, computes initial age, and starts once. Retained presentation state belongs to the concrete channel that owns it; level change and disconnect clear those owners. `VfxTimeChannel`, `VfxDirector.timeScale()`, and the old client-global slow-motion path do not exist.

## 7. Lifecycle and cleanup

Channels own their real retained state and clear it on level change and disconnect. The world channel owns its `ImpactFlash` list, age/expiry/progress/fade, anchor resolution, camera-relative center, buffer acquisition, and exhaustive style dispatch. Its retained world cap is `MAX_IMPACT_FLASHES = 48`; this is the real visual-state bound and is not the removed size-64 bookkeeping cap.

## 8. Radius and duration ownership

Delivery and presentation radii are named separately. Shared semantic lifetimes have one named owner. Intentional sub-lifetimes, such as Black Flash recipe and retained-world tails, remain separately named and covered by tests. No anonymous duplicated timing pair is allowed to drift.

## 9. Canonical cue factories

`VfxCues` owns transport mechanics for common shapes:

- `worldFixed`
- `worldFixedDirected`
- `worldFixedDisplacement`
- `anchored`
- `anchoredWithOffset`
- `anchoredDirected`

The factory owns anchor sentinel handling, anchor delta calculation, intensity minimum clamping, game time, seed and normalized orientation. It does not hide effect semantics behind effect-specific APIs.

## 10. Todo narrow read model

`TodoSwapArrivalPayload.from(cue)` is the narrow named reader for the overloaded `SWAP_ARRIVAL` offset. It exposes speed, body width, body height and direction without changing the eight-field wire format or introducing a general typed-cue hierarchy. A landed marker remains a reusable anchor; a body mark expires and is consumed on use. The Todo feint shares the clap cue, while completion-only afterimage and arrival cues are emitted only by completed swaps.

## 11. Camera determinism

`VfxCameraChannel` uses `System::currentTimeMillis` in production and a package-private `LongSupplier` seam in tests. The seam makes starts, overlap, expiry boundaries, age clamps, strength and clear behavior deterministic without changing constants, curves or production timing.

## 12. World rendering families

`VfxWorldChannel` remains lifecycle owner and exhaustive dispatcher. Five files hold the extracted visual families:

| Styles | Owner |
|---|---|
| `HAMMER_SEND`, `ENLARGE`, `EXPLOSION`, `RITUAL_BIND`, `DOLL_STRIKE`, `RESONANCE_RELEASE` | `HairpinWorldEffects` |
| `BLACK_FLASH` | `BlackFlashWorldEffects` |
| `BOOGIE_WOOGIE`, `SWAP_AFTERIMAGE`, `SWAP_ARRIVAL` | `SwapWorldEffects` |
| `MEGUMI_SHADOW_OPEN`, `MEGUMI_SHADOW_CLOSE` | `ShadowWorldEffects` |
| shared `sideVector`, `directionalBasis`, `addRibbon`, `renderDirectionalRing` | `VfxWorldGeometry` |

The extraction preserves visual constants, formulas, curves, seed mixing, segment counts, RenderTypes and vertex order. `ImpactStyle` and `worldFixed` ownership remain in the channel. The cap remains 48.

## 13. Persistent versus transient presentation

VFX Core is for transient combat cues. Embedded nails, vessel bodies, Divine Dogs and other stateful visuals render from their entity or gameplay state. A new vessel must not create a lifecycle manager to retain a visual that belongs to an entity/state renderer.

## 14. Tests and verification

New tests use JUnit 5. Legacy JavaExec verification programs remain where migration is deliberately gradual; all of them and the JUnit suite run under `qualityGate`. The documentation audit and `verifyAssertionsEnabled` audit are also part of that gate.

The current automated evidence includes codec, cue, live-id, recipe, emitter, radius, duration, camera, arrival, collapse, vessel, world-family and source-boundary contracts. It proves code shape and pure logic, not a real `ServerLevel`, cast, rendered frame or multiplayer feel.

The PR 8 numeric comparison recorded 554 baseline tokens and 554 current tokens with no differences. Its nine temporary red mutations were recorded as failing and restored. These are mechanical proofs, not a substitute for the required client comparison.

## 15. Accepted limitations

- No automated world smoke exists. `qualityGate` does not construct a real `ServerLevel`, cast an ability or render a frame; the GameTest/in-world gap remains open in `docs/KNOWN_ISSUES.md`.
- Client-global slow motion is deliberately absent. Reintroducing it requires an approved design with a named consumer and lifecycle; do not revive `VfxTimeChannel` through a mixin.
- The real retained world cap is 48. Reopen it only with reproducible presentation loss or profiling evidence.
- Seven channels are the current complete surface. A new channel requires design review and evidence that existing channels cannot express the presentation.
- Performance baseline for 1/16/32/48 retained world effects was not collected. No performance claim is made; future optimization requires profiling evidence.
- Todo locality and the shared Black Flash cue remain accepted seams documented in the relevant Codex notes and `docs/KNOWN_ISSUES.md`.

## 16. Extension rules

Frozen architecture does not prohibit new effects or vessels. Normal character work extends the existing seams:

1. add `<Character>VfxIds` with explicit `LIVE` and `PLANNED` sets;
2. add `<Character>VfxRecipes` and register it from the client definition;
3. use the existing cue transport, director and seven channels;
4. add a world style through the existing world-style boundary when needed;
5. use existing registries for assets;
6. add regression tests for bug fixes.

No new vessel receives its own packet, receiver, callback, camera manager, HUD singleton, lifecycle manager or VFX-specific mixin.

## 17. Architectural freeze rule

The architecture may be reopened only by evidence. The following require separate design review:

- a new director channel;
- a new packet or wire field;
- a new render or HUD callback;
- a VFX-specific mixin;
- data-driven recipes;
- a new lifecycle manager;
- client gameplay authority;
- global client time scaling;
- custom batching or rendering infrastructure;
- a general typed-cue hierarchy.

Frozen internals may be reconsidered only when profiling shows a measurable problem, a reproducible burst demonstrates important presentation loss at cap 48, multiplayer demonstrates that Todo locality is systematically wrong, a future vessel cannot express its presentation through seven channels, or planned-id workflow becomes a recurring bottleneck.

The current repository status is `FREEZE BLOCKED` because the full smoke matrix and baseline/current PR 8 visual comparison were not run in an accessible Minecraft client/world. Do not change this status to `FROZEN` from automated tests alone.
