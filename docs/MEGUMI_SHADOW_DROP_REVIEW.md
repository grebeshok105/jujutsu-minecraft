# Megumi Shadow Drop wave — consolidated review spec

Status: CURRENT — findings recorded, **fixes deliberately not applied yet** (user decision: review
round first, fixes as a separate pass against this spec).

Wave under review: `21c595a..aef9b5c` on `feat/megumi-shadow-drop` (V ability server core, client
VFX layer, dive presentation, tests/docs/seam), reviewed by four independent reviewers after smoke
round 1. Verdicts: server core **incorrect** (R1), dive presentation **incorrect** (R2), client VFX
layer correct, tests/docs/seam correct. Everything below carries the reviewer's confidence and a
ready fix so the follow-up pass is mechanical.

## R1 — `fall()` deletes the world block at the spawn position

- Severity: **major** (world grief), priority 1, confidence 0.9 (proven from 1.21.8 bytecode).
- `src/main/java/jujutsu/mod/character/megumi/MegumiShadowDropRuntime.java` (`releaseOne`).

`FallingBlockEntity.fall(level, pos, state)` unconditionally executes
`level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3)` — that is how vanilla lifts
sand off the ground. For our synthetic volley the payload is dry, so this is `setBlock(pos, AIR)`:
casting Shadow Drop on a target under any ceiling (cave, tunnel, building, tree canopy) silently
deletes the block at the hover point — including block entities and even bedrock — and a water
source there is destroyed too. Contradicts the runtime's own no-litter javadoc.

**Fix (ready):** walk down from the hover point toward the target's head and spawn at the first
dry, replaceable position; a fully sealed column skips that block instead of deleting geometry:

```java
private static void releaseOne(ServerLevel level, Vec3 point, RandomSource random) {
	BlockPos spawn = spawnPosFor(level, point);
	if (spawn == null) {
		// The column above the target is sealed; skip the block rather than delete the ceiling.
		return;
	}
	BlockState state = pickBlock(random);
	FallingBlockEntity block = FallingBlockEntity.fall(level, spawn, state);
	// ... unchanged disableDrop/setHurtsEntities tail
}

/**
 * fall() replaces the block at its spawn position, so a synthetic drop must only ever spawn
 * where there is nothing real to replace. First dry replaceable position walking down from
 * the hover point; null when the whole column is sealed.
 */
private static BlockPos spawnPosFor(ServerLevel level, Vec3 point) {
	BlockPos top = BlockPos.containing(point);
	for (int dy = 0; dy < (int) MegumiProfile.DROP_ZONE_HEIGHT_BLOCKS; dy++) {
		BlockPos pos = top.below(dy);
		BlockState state = level.getBlockState(pos);
		if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
			return pos;
		}
	}
	return null;
}
```

Side effect worth keeping: under a low ceiling the volley now spawns *below* the ceiling and still
falls, so the ability works in caves instead of eating the roof.

## R2 — interrupted dive snaps to full depth; re-cast snaps to surface

- Severity: medium (presentation contract), priority 2, confidence 0.85. Found independently by
  two reviewers.
- `src/client/java/jujutsu/mod/client/render/ShadowBodySink.java` (`beginSink`, `beginEmerge`).

`MegumiShadowMoveRuntime.cancelSink` (damage during the 8-tick SINK) broadcasts `SHADOW_EMERGE`
while the client entry is still `SINKING` at partial depth `p < 1`. `beginEmerge` replaces it with
an `EMERGING` entry starting at depth 1.0, so the still-visible body snaps from `−1.9·s(p)` to the
full −1.9 blocks in one frame (through the floor under bridges/overhangs), and the caster's camera
and veil surge to their emerge peaks. The inverse pop is also reachable: `cancelSink` starts no
cooldown, so an instant re-cast makes `beginSink` overwrite a mid-rise `EMERGING` entry back to
progress 0 (surface).

**Fix (ready):** backdate the replacement entry so its curve passes through the current depth at
the cue's authoritative time — in `beginEmerge` when the prior entry is `SINKING`:

```java
int ticks = Math.max(1, emergeTicks);
long start = startGameTime;
if (entry.state() == State.SINKING) {
	float depth = clamp01((startGameTime - entry.startGameTime()) / (float) entry.durationTicks());
	start = startGameTime - Math.round((1.0f - depth) * ticks);
}
ENTRIES.put(entityId, new Entry(State.EMERGING, start, ticks,
		clock.getAsLong() + windowTtlMillis(ticks)));
```

and mirrored in `beginSink` when the prior entry is `EMERGING`:

```java
float depth = 1.0f - clamp01((startGameTime - entry.startGameTime()) / (float) entry.durationTicks());
start = startGameTime - Math.round(depth * ticks);
```

Rounding error is under one tick of depth — invisible at 6–8 tick windows.

## R3 — `drop_zone_close` is skipped when the telegraph dies with its target

- Severity: minor (visual-only spec deviation), priority 3, confidence 0.7–0.85. Found by two
  reviewers.
- `src/main/java/jujutsu/mod/character/megumi/MegumiShadowDropRuntime.java` (`anchorOf`).

Spec Part 1: "Target dead/removed/cross-level/unloaded → close early (`drop_zone_close`, no
block)". The tick's early close routes through `anchorOf` → `liveTarget`, which filters
`isAlive()`/`isRemoved()` — a target that died mid-telegraph (corpse still present during its death
animation) anchors nothing, so `closeZone` returns before broadcasting the cue and the hovering
disc fades without its collapse beat.

**Fix (ready):** anchor the close cue over any still-present body; only a fully removed or
cross-level target stays cueless (unavoidable — there is nowhere to close over):

```java
private static Vec3 anchorOf(ServerLevel level, ShadowDrop drop) {
	if (level == null) {
		return null;
	}
	return level.getEntity(drop.targetId()) instanceof LivingEntity living ? anchorAbove(living) : null;
}
```

## R4 — dive tests race the real wall clock

- Severity: minor (flake risk), priority 3, confidence 0.78.
- `src/test/java/jujutsu/mod/client/vfx/VfxCameraChannelTest.java` (dive block).

The dive tests drive the static `ShadowBodySink` with the real system clock: the injection seam
`setClockForTests` is package-private in `jujutsu.mod.client.render`, unreachable from
`jujutsu.mod.client.vfx`. Each test body races entry TTLs (~400–800 ms windows); a GC pause or a
loaded CI runner intermittently evicts the entry mid-test (`sinkProgress` → −1, offset collapses
to 0). The channel's own clock *is* injected, so two clocks disagree inside one test. Today JUnit
runs sequentially, so this is timing-only; parallel execution would additionally race
`ShadowBodySinkTest`'s global clock swap.

**Fix (ready):** a public test fixture inside the `render` package that forwards to the
package-private seam, called from the channel test's setup/teardown:

```java
// src/test/java/jujutsu/mod/client/render/ShadowBodySinkTestClock.java
package jujutsu.mod.client.render;

import java.util.function.LongSupplier;

/** Test-only bridge so tests outside this package can pin the sink cache's TTL clock. */
public final class ShadowBodySinkTestClock {
	private ShadowBodySinkTestClock() {}

	public static void set(LongSupplier clock) {
		ShadowBodySink.setClockForTests(clock);
	}

	public static void reset() {
		ShadowBodySink.setClockForTests(null);
	}
}
```

## R5 — spec still documents the pre-decoupling `ShadowBodySink` API

- Severity: minor (doc drift), priority 3, confidence 0.95.
- `docs/MEGUMI_SHADOW_DROP.md`, "Shared contracts" rows.

The spec says `beginSink(int entityId, long startGameTime)` / `beginEmerge(int entityId, long
startGameTime)` and "Durations read `MegumiProfile.SHADOW_SINK_TICKS` / `SHADOW_EMERGE_TICKS`" —
but commit `e2974a9` deliberately decoupled the cache from the vessel: the shipped API is the
three-arg `beginSink(entityId, startGameTime, sinkTicks)` / `beginEmerge(entityId, startGameTime,
emergeTicks)`, and the callers (the Megumi recipes) pass the window lengths; the cache knows no
vessel. The spec owns the inter-package contracts, so the stale row is what the next reader
copies.

**Fix (ready):** update the API row to the three-arg signatures and replace the durations sentence
with "callers pass the window lengths; the cache knows no vessel".

## R6 — implemented `ShadowBodySink` guards have no pins

- Severity: minor (test hole), priority 3, confidence 0.7.
- `src/test/java/jujutsu/mod/client/render/ShadowBodySinkTest.java`.

Three implemented edge behaviors — one stated as an invariant in the javadoc — have zero coverage:

1. `completeSink` during `EMERGING` is ignored ("a ripple after the rise must never yank the body
   back under" — the packet-reorder invariant).
2. `completeSink` with no prior entry creates an `UNDER` hold (ripple-before-dive / late join
   snaps the body fully under).
3. `beginSink` over a live `UNDER`/`EMERGING` entry replaces it (re-dive restart).

**Fix (ready):** three short tests in `ShadowBodySinkTest` with its injected clock; when R2 lands,
two more pin the backdated depth handoff in each direction (emerge-from-partial-sink,
sink-from-partial-emerge).

## R7 — keybind label capitalization

- Severity: trivial, priority 3.
- `src/main/resources/assets/jujutsumod/lang/en_us.json`.

`"key.jujutsumod.third_technique": "Third technique"` sits between "Primary Technique" and
"Secondary Technique". **Fix:** capitalize to `"Third Technique"`.

## Non-findings worth keeping (verified clean by the reviewers)

- TERTIARY wire id 8 append-only; cooldown mirroring generic; routers exhaustive and default-free;
  V keybind gated by `screen == null` exactly like R/B; vessel seam intact across the whole diff.
- All 15 recipe ids register exactly once; drop-zone 7t recipe over a 5t pulse overlaps without
  flicker (same mechanism as trap 42t/40t); intensity ↔ radius symmetric at ×10/÷10.
- Void-black applies to exactly the trap-family consumers; the dogs' summon pool keeps its fade.
- Dive TTL fail-open bounded (~0.4–0.8 s worst case after teardown paths that send no cue); mixin
  push/pop balanced on all paths; HUD veil first-person-gated in the right layer; no per-effect
  receivers anywhere in the wave.
