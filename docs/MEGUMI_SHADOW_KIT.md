# Megumi Shadow Kit — B Shadow Trap and Shift+B Shadow Move

Design spec for Megumi's second ability pair. Approved before implementation; this document is the
contract the code and tests follow. Balance numbers live in `MegumiProfile` — the table here names
them, the profile owns them.

## Fantasy

Megumi controls the battlefield through his own shadow: he pins a target inside a pool of liquid
shadow so the Divine Dogs can work, and he travels through shadows himself — out of sight, never
out of the world.

## Input map (wire contract)

| Input | Slot | Wire id | Behaviour |
|---|---|---:|---|
| `B` | `SECONDARY` | 2 (existing) | Shadow Trap under the aimed target |
| `Shift+B` tap (released < 6 ticks) | `SECONDARY_SNEAK` | 3 (existing) | Contextual shadow move: behind-target emerge, else free step |
| `Shift+B` hold (≥ 6 ticks) | `SECONDARY_SNEAK_HOLD` | 6 (**appended**) | Deep submerge while held |
| release after hold | `SECONDARY_SNEAK_RELEASE` | 7 (**appended**) | Early emerge request |

- Existing ids 0–5 are untouched; 6 and 7 are appended to `CharacterAbility`. No new payload:
  both new slots travel in the existing `CharacterAbilityPayload`.
- The input layer buffers a sneaking `B` press and resolves it on release (tap) or at the 6-tick
  threshold (hold). A non-sneaking `B` press still sends instantly. Cost: the sneak-tap cast
  confirms up to 0.3 s later than before — the accepted price of an honest hold gesture.
- While a shadow move is active, a repeat `SECONDARY_SNEAK` tap is treated by Megumi's router as an
  early-emerge request, same as `SECONDARY_SNEAK_RELEASE`. A spoofed or stale HOLD/RELEASE with no
  matching server state is refused by the router and does nothing.
- `SECONDARY_SNEAK_RELEASE` deliberately never carries a cooldown, so the emerge request always
  reaches the router even while `SECONDARY_SNEAK` is cooling down. Megumi does not fold slots via
  `canonicalSlot` — tap and hold are different actions sharing one cooldown key, which the router
  checks itself (`CharacterAbilityCooldowns.isReady(player, SECONDARY_SNEAK)`) before starting a
  hold.

## B — Shadow Trap

Server-authoritative zone under the aimed target.

1. Target selection: `TargetResolver.resolve(level, player, SHADOW_TRAP_RANGE, eligible)` — the
   same authoritative path Sic uses, with the same `MegumiTargetPolicy` eligibility (not self, not
   own dogs, not allies, alive, loaded, same level, not spectator) plus `hasLineOfSight`.
2. The zone is **static**: centred on the target's feet at cast time (snapped down to the ground
   within 3 blocks when the target is airborne), radius `SHADOW_TRAP_RADIUS`, lifetime
   `SHADOW_TRAP_DURATION_TICKS`. One live trap per owner; the cooldown is longer than the lifetime.
3. Every server tick the trap applies `megumi_shadow_grip` (registered `MobEffect`, HARMFUL) for
   `SHADOW_TRAP_GRIP_REFRESH_TICKS` to every **eligible** living body whose feet are inside the
   cylinder. The effect is pure vanilla attribute mathematics, mirrored to clients by vanilla:
   - `MOVEMENT_SPEED` `ADD_MULTIPLIED_TOTAL` `SHADOW_GRIP_SPEED_MULTIPLIER` (−0.75 → 25 % speed);
   - `JUMP_STRENGTH` `ADD_MULTIPLIED_TOTAL` −1.0 (jumping fully suppressed).
   Not a stun: a gripped body still walks out slowly. Leaving the cylinder lets the short effect
   expire on its own — no manual effect cleanup is ever needed.
4. Dogs get no artificial help: no teleport, no forced pounce. The slow simply makes the existing
   pounce window easier to earn.
5. Lifecycle: the trap dies on expiry, and on owner death / disconnect / dimension change /
   deselection / server stop through one teardown path. Target death or removal just means nobody
   is inside the zone any more.

## Shift+B — one technique, three contextual modes

One server runtime (`MegumiShadowMoveRuntime`), one state machine per player:

```
tap:  IDLE → SINK(8t) → HIDDEN(4t) → EMERGE(6t) → IDLE
hold: IDLE → SINK(8t) → SUBMERGED(≤ SUBMERGE_MAX_TICKS) → EMERGE(6t) → IDLE
```

- **SINK** — the player visibly sinks (third-person clip + pool VFX + sound). Taking damage during
  SINK cancels the cast on the spot: no teleport, no cooldown, state cleared. Abilities and melee
  are already locked.
- **HIDDEN / SUBMERGED** — the player is flagged vanilla-invisible (synced flag, restored honestly
  on exit unless an invisibility potion is active) and a client render gate hides the whole body.
  Ordinary damage is cancelled via `ServerLivingEntityEvents.ALLOW_DAMAGE` (bypass-invulnerability
  sources still land). Attacks (`AttackEntityCallback`) and every ability slot are refused by the
  router. Collision stays fully vanilla — **no wall clipping, ever**: the body keeps walking on the
  surface; only its presentation is a ripple.
- **EMERGE** — teleport (when the mode calls for one), restore visibility, clip + sound + VFX. The
  player is visible and vulnerable for the whole emerge beat; the exit is readable.

### Mode 1 — behind-target emerge (tap with an eligible living target under the crosshair)

- Target eligibility and LOS mirror the trap cast; range `SHADOW_STEP_TARGET_RANGE`.
- The exit point is computed **at the end of HIDDEN**, against the target's live position and
  body rotation (`yBodyRot` — the back of the body, not the head): candidates at
  `BACKSTEP_DISTANCE` behind, then ±25°, ±50°, ±75° across the rear hemisphere, each validated by
  the shared `SafeBodyPlacement` search (in-world, chunk-loaded, inside the border, collision-free;
  vertical steps 0–3). First safe candidate wins; Megumi faces the target on arrival.
- If the target died / left the level / got farther than `SHADOW_STEP_TARGET_RANGE × 2` while
  Megumi was hidden, or no rear point is safe — he emerges back at his start position (safe-checked
  with the same search; exact start as final fallback). No partial states.

### Mode 2 — free shadow step (tap with no eligible target, aiming at a surface)

- `TargetResolver` block hit within `SHADOW_STEP_RANGE`; the clip *is* the line-of-sight test —
  a point behind a wall can never be hit. Thick walls, closed rooms and unloaded chunks are
  unreachable by construction.
- The requested point is the hit position nudged out of the face, then `SafeBodyPlacement` search
  (small horizontal ring, up to 3 blocks upward). No safe point → the cast is refused before any
  state is created.
- Yaw is preserved; small obstacles and ledges are steppable because the exit search may resolve
  slightly above the aim point.

### Mode 3 — deep submerge (hold)

- Entered only through the hold gesture; checked against the shared `SECONDARY_SNEAK` cooldown.
- While SUBMERGED the player walks along surfaces with normal physics, cannot attack, cannot cast;
  a low ripple cue re-emits from the server every `SHADOW_RIPPLE_PERIOD_TICKS` at his position —
  that ripple is both the fair readability tell for everyone and the client's hide signal.
- Ends on: release, repeat tap, `SUBMERGE_MAX_TICKS` timeout, or any lifecycle event. Emerge point
  is the current position when it is still placeable (it normally is — collision never lapsed);
  otherwise the nearest safe point (`SafeBodyPlacement`, ring radius 3); the sink-entry position is
  the final fallback.

## Cooldowns

One cooldown key per input position (mirrored to the client by the existing
`AbilityCooldownPayload` path), started when the move **finishes** (EMERGE begins) or when the trap
cast succeeds:

| Action | Key | Ticks |
|---|---|---:|
| Shadow Trap | `SECONDARY` | `SHADOW_TRAP_COOLDOWN_TICKS` = 200 (10 s) |
| Shadow move, tap modes | `SECONDARY_SNEAK` | `SHADOW_STEP_COOLDOWN_TICKS` = 120 (6 s) |
| Deep submerge | `SECONDARY_SNEAK` | `SUBMERGE_COOLDOWN_TICKS` = 200 (10 s) |

A sink cancelled by damage starts no cooldown (the technique never happened).

## Baseline numbers (owned by `MegumiProfile`)

| Constant | Value |
|---|---:|
| `SHADOW_TRAP_RANGE` | 20.0 |
| `SHADOW_TRAP_RADIUS` | 2.6 |
| `SHADOW_TRAP_DURATION_TICKS` | 100 (5 s) |
| `SHADOW_TRAP_COOLDOWN_TICKS` | 200 (10 s) |
| `SHADOW_TRAP_GRIP_REFRESH_TICKS` | 8 |
| `SHADOW_GRIP_SPEED_MULTIPLIER` | −0.75 |
| `SHADOW_GRIP_JUMP_MULTIPLIER` | −1.0 |
| `SHADOW_TRAP_ZONE_PULSE_TICKS` | 40 |
| `SHADOW_STEP_TARGET_RANGE` | 20.0 |
| `SHADOW_STEP_RANGE` | 24.0 |
| `BACKSTEP_DISTANCE` | 1.75 |
| `BACKSTEP_ARC_DEGREES` | 0, ±25, ±50, ±75 |
| `SHADOW_SINK_TICKS` | 8 |
| `SHADOW_HIDDEN_TICKS` | 4 |
| `SHADOW_EMERGE_TICKS` | 6 |
| `SUBMERGE_MAX_TICKS` | 50 (2.5 s) |
| `SHADOW_RIPPLE_PERIOD_TICKS` | 5 |
| `SHADOW_STEP_COOLDOWN_TICKS` | 120 (6 s) |
| `SUBMERGE_COOLDOWN_TICKS` | 200 (10 s) |
| `EMERGE_SEARCH_RADIUS` | 3.0 |

## Shared extraction — `SafeBodyPlacement`

`TodoBoogieWoogieRuntime.findSafeDestination` moves to `jujutsu.mod.combat.SafeBodyPlacement`
(shared, vessel-free): the candidate scan (horizontal ring × upward steps), `isPlaceable`
(in-world + chunk + border + `noBlockCollision`) and the optional exact-point fallback become one
parameterised policy. Todo keeps his `Strictness` semantics and call sites (thin wrapper, same
behaviour, same tests); Megumi is the second production consumer. This satisfies the two-consumer
rule; nothing else about Todo changes and the runtimes stay unlinked.

## VFX (VFX Core only)

Seven appended live ids in `MegumiVfxIds`, recipes in `MegumiVfxRecipes`, registered from
`MegumiClientDefinition.registerClientHooks()`; every recipe keeps the one-shot-per-cue rule with
`isOpeningBeat` guards. Continuous visuals are driven by server re-emission (the Nobara trap-pulse
precedent), never by client re-ticking:

| Id | Purpose | Driver |
|---|---|---|
| `megumi/shadow_trap_open` | pool unfurls + dark burst | cast (once) |
| `megumi/shadow_trap_zone` | liquid pool + slow inward ring | server pulse every 40 t |
| `megumi/shadow_trap_grip` | pull-down motes on a gripped body | server, per gripped body every 20 t |
| `megumi/shadow_trap_close` | pool collapse | expiry/teardown (once) |
| `megumi/shadow_dive` | sink pool + dive animation trigger + first-person feedback | sink start (once) |
| `megumi/shadow_ripple` | faint moving ripple over a submerged Megumi; doubles as the client hide signal (TTL set) | server every 5 t |
| `megumi/shadow_emerge` | exit pool + burst + emerge animation trigger | emerge (once) |

- The client render gate hides a player while his ripple/dive TTL entry is fresh; the entry decays
  in a few ticks by itself, so a lost packet fails open (the body becomes visible — never the other
  way round).
- First-person feedback for the local Megumi: blur + short nausea while submerged, SIGN-style hand
  beat on dive.
- Third-person: two new short clips in `megumi_fushiguro.animation.json` (`shadow_dive`,
  `shadow_emerge`) triggered through the existing `MegumiAnimationHooks` cue path.
- Sounds are server-played (`goo_foley` for pool/dive, `implode` for close, `whoosh_hit` for
  emerge) — no new OGG assets required.

## Cleanup matrix

`MegumiShadowTrapRuntime` and `MegumiShadowMoveRuntime` register the same seven hooks the summon
runtime already uses (`AFTER_DEATH`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, `DISCONNECT`,
`SERVER_STOPPING`, `END_SERVER_TICK`, plus `onDeselected` via `MegumiDefinition`). Every teardown
restores visibility (potion-aware), clears the state map entry, and never leaves a pending
teleport: the player simply resurfaces where his body already is. The grip effect self-expires
within 8 ticks, so no entity ever needs manual effect scrubbing.

## Explicit non-goals

- No shadow dimension, no real "shadow world", no noPhysics travel, no wall clipping.
- No universal shadow framework, no new lifecycle manager, no changes to Divine Dogs / Sic /
  pounce, no Todo coupling beyond the shared vessel-free placement policy.
- No new shikigami, no melee system, no VFX Core architecture changes, no mode-selection menu.
- Exit-point preview is deliberately skipped in this slice (the ability reads well without it);
  recorded as a known limitation.

## Test plan

Pure policies get JUnit coverage (project style: facts records in, decisions out):
mode selection, target validity, behind-point math and arc fallback order, refusal with no safe
point, range/LOS gating, phase durations and transitions, submerge timeout and early release,
damage-interrupt in SINK vs absorb in HIDDEN, cooldown per mode, cleanup decisions per lifecycle
event, wire-id stability (0–5 unchanged, 6–7 appended), VFX id registration + emitter coverage,
and the existing Megumi/Todo/Nobara contract suites stay green. World-dependent behaviour
(actual teleports, collision, invisibility sync) is honestly out of JUnit reach (E1) and lands in
the manual smoke checklist in `SESSION.md`.
