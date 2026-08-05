# Todo Stone Rework — design contract

Status: CURRENT (approved before implementation)
Owner wave: `feat/todo-stone-rework`

Todo's marker system is removed entirely and replaced by a thrown stone that exists only in
flight, plus a triple cyclic swap grown out of the pair swap. This document owns the contracts;
the numbers live in `TodoProfile` and are restated here once.

## What is removed (completely)

The whole marker subsystem: the `todo_swap_marker` item, the thrown marker projectile, positional
and entity marks, `TodoSwapMarks`, `TodoMarkerSwapRuntime`, `TodoEntityMarkRuntime`, the
two-right-clicks body mark, the aimed swap's fallback onto a live mark, their registry entries,
renderer, assets, localization, lifecycle hooks, tests, docs, and constants. No compatibility
shims stay behind.

After the purge:

- `R` performs only the ordinary aimed swap with the target under the crosshair. No target — a
  plain refusal, never a fallback.
- `CharacterAbility.USE_CONTEXT(5)` keeps its wire id and its client detection (the paired right
  click), but Todo's router answers `false`. No vessel listens to it in this slice.
- Wire ids 0–8 are untouched. `TERTIARY_SNEAK(9)` is appended for Shift+V.

## The stone (`V`)

One small stone that exists only while flying.

- **Throw**: `V` with no live stone throws one from Todo's eye position along his look vector.
  Constant velocity, no gravity, no arc — a slow, readable straight line.
- **Only one**: a live stone means `V` never throws a second one; `V` becomes the self-swap.
- **Inert**: the stone deals no damage, marks nothing, never places a block, never drops an item,
  and ignores entities entirely (it flies through them). Water and fire are not terminal.
- **Ends**: on lifetime expiry, on block collision, on void, and on every Todo state cleanup. A
  landed stone never becomes an anchor — collision is a vanish, full stop.
- **Self-swap**: `V` with a live stone swaps Todo with the stone at the stone's *current*
  position, server-authoritatively. STRICT safe placement for Todo at the stone's point; if no
  safe point exists, nothing moves. On success the stone appears at Todo's old center, keeps its
  own velocity, and keeps flying with its remaining lifetime.
- **Velocity semantics** (one sentence, everywhere): a swap exchanges positions only — every
  body and the stone keep their own motion; the flight clock never resets.
- **Caster state, not hands**: the throw and both stone swaps read the caster-state half of
  `TodoSwapGates` (`casterStateBlocked`) — a spectator, dead, mounted/riding or staggered Todo is
  refused silently. Hands stay deliberately ungated: the stone is an ability cast, not an item
  use, so the clap's empty-hands rule never applies to it.
- The throw cooldown is deliberately tiny (anti-double-click), so throwing never locks the
  follow-up swap behind a long cooldown. The self-swap carries the real price.
- Momentum: the self-swap grants the existing swap-momentum window (it is a completed swap Todo
  himself made — same reasoning as the aimed swap). 

## Target ↔ stone (`Shift+V`, new wire id 9)

- Requires a live stone AND a valid crosshair target within range; otherwise a clear refusal and
  no state change at all.
- Eligibility is the aimed-swap family: alive, not spectator, not removed, not an armor stand,
  transport-safe (not mounted, not a vehicle, not leashed), finite position, line of sight, same
  dimension as both Todo and the stone.
- STRICT safe placement for the target at the stone's current position; no safe point — nothing
  moves, the stone keeps flying.
- Todo stays where he is. The stone appears at the target's old center, keeps its velocity and
  remaining lifetime. No momentum for this cast.

## Pair swap and the triple cycle (`B`, `Shift+B`)

`canonicalSlot` folding is removed: `SECONDARY_SNEAK` reaches Todo's router as itself.

- **B → B** (unchanged): first press marks the aimed body (selection with TTL), second press
  swaps the two marked bodies; Todo stays put. Pair cooldown unchanged.
- **B → Shift+B** (new): with a live selection A, casting `Shift+B` on a second target T runs the
  triple cyclic swap. The direction is fixed and test-pinned:
  - **Todo → A's position; A → T's position; T → Todo's position.**
- `Shift+B` with no selection refuses (`triple.no_first`) — it never silently degrades into `B`.
- The first selection survives the transition from `B` to `Shift+B` (it is consumed only by a
  successful pair or triple commit; TTL expiry still clears it).
- All three bodies preflight STRICT at their destinations before anything moves; one failure
  cancels the whole cast with no movement and keeps the selection.
- Commit is as atomic as the runtime allows: snapshot ×3, place ×3, restore ×3; a mid-commit
  failure rolls back every already-moved body to its snapshot and logs an error. No silent
  partial cycle.
- Motion, rotation, head yaw and fall-distance follow the accepted swap policy (bodies keep
  their own motion; fall distance resets; `hurtMarked` forces the motion packet).
- Pair and triple have separate cooldowns (below). The triple grants NO swap momentum — the
  momentum window stays a reward for swaps Todo makes with his own body (R, V).

## Placement strictness (extends the accepted policy)

SOFT still exists in exactly one place: Todo's own arrival in the aimed `R` swap. Every
destination this rework adds is STRICT: Todo at the stone (V), the target at the stone (Shift+V),
and all three bodies of the triple cycle. No defaulting overload is reintroduced.

## Numbers (source of truth: `TodoProfile`)

|Constant|Value|Meaning|
|---|---:|---|
|`STONE_SPEED_BLOCKS_PER_TICK`|0.23|4.6 blocks/s — brisk, readable|
|`STONE_LIFETIME_TICKS`|100|5 s in flight|
|`STONE_HITBOX_SIZE`|0.25f|entity bbox|
|`STONE_THROW_COOLDOWN_TICKS`|10|anti-double-click only|
|`STONE_SELF_SWAP_COOLDOWN_TICKS`|60|3 s|
|`STONE_TARGET_SWAP_COOLDOWN_TICKS`|100|5 s|
|`STONE_SWAP_RANGE`|32.0|max distance Todo↔stone for either swap|
|`STONE_TARGET_RANGE`|20.0|crosshair reach for Shift+V (matches `BOOGIE_WOOGIE_RANGE`)|
|`TRIPLE_SWAP_COOLDOWN_TICKS`|160|8 s, deliberately above pair|
|`PAIR_SWAP_COOLDOWN_TICKS`|100|unchanged|
|`PAIR_SELECTION_TTL_TICKS`|100|unchanged|
|`PAIR_MARK_PULSE_TICKS`|20|server re-emit period for the selection mark|

## Presentation (VFX Core only)

- New cue ids on `TodoVfxIds`: `STONE_THROW`, `STONE_VANISH`, `TRIPLE_SWAP` (LIVE). `PAIR_MARK`
  stays — the pair swap emits it on selection, and the server now re-emits it as a silent pulse
  every `PAIR_MARK_PULSE_TICKS` while the selection lives (trap-boundary pattern), so the first
  chosen body stays readable.
- Completed swaps (V self-swap, Shift+V target swap) ride the existing `BOOGIE_WOOGIE` clap +
  `SWAP_AFTERIMAGE`/`SWAP_ARRIVAL` endpoints — they ARE real swaps, so they earn the real-swap
  language. The feint still never emits endpoint cues.
- `TRIPLE_SWAP` is emitted three times per cast, once per cycle edge, carrying a direction so the
  A→B→C→A flow reads in-world without new HUD machinery.
- The stone renders through a small dedicated entity renderer (code geometry + own texture) and
  trails a few particles from its client tick — a persistent visual on a real entity, not a
  timeline.
- HUD: two compact chips near the hotbar through the existing `VfxDirector` HUD contribution
  channel — (1) live stone: remaining seconds + swap hint; (2) live pair selection: target +
  remaining TTL + the `⇧B` cycle hint. Client state for the pair chip is fed by the `PAIR_MARK`
  recipe (fail-open TTL cache, `ShadowBodySink` pattern); the stone chip reads the synced stone
  entity itself. No new HUD framework.

## Lifecycle (one owner, one path)

`TodoTransientState` is the single owner of Todo's transient state (pair selection + stone ref).
`TodoStateLifecycle` is the single registrar of every cleanup hook: death, respawn, dimension
change, vessel change (deselect), disconnect, server stop, plus the per-tick expiry sweep. Losing
the stone entity from a loaded chunk clears the ref. `TodoStateLifecycle` is the only registrar of
the transient-state cleanup hooks, and no second static map appears. (`TodoBoogieWoogieRuntime`
keeps its own END_WORLD_TICK/SERVER_STOPPING pair for the pending movement-sound queue —
presentation, not swap state, and older than this rework.)

## Verification

JUnit / JavaExec (honest scope — no `ServerLevel` exists in the suite, see E1):

- source-tree audit: no marker symbol, asset, lang key, registry entry or constant survives
- wire ids 0–8 byte-stable, `TERTIARY_SNEAK == 9` appended, routers exhaustive
- profile pins: stone speed in the 3–4 blocks/s band, lifetime, distinct cooldowns per slot
- pure-logic tables: target eligibility, preflight refusal moves nothing, single-stone policy,
  triple cycle direction mapping, rollback order, no-momentum pins
- VFX contracts: LIVE wire strings, completeness total, radius owners for the three new ids

Everything about real teleportation, collision, trail readability and HUD placement is manual
smoke only — the wave's SESSION.md carries the numbered checklist.
