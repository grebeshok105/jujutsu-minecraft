# Combat Timing and Black Flash

← [[00-MOC]] · [[Nobara-overview]] · [[Nobara-runtime-flow]] · [[Nobara-combat-expansion]]

## Server timing contract

`NobaraActionTimeline` centralizes an action's impact tick, recovery end, and Black Flash input bounds. The current horizontal timeline impacts at tick 3 and recovers at 8; overhead impacts at tick 8 and recovers at 16. Prepared launch has immediate impact semantics with 10-tick recovery. Doll strike and self resonance use a 14-tick windup and 20-tick recovery.

`BlackFlashWindow` is single-use and accepts only server game time within its inclusive `[opensAt, closesAt]` range. The profile currently opens at impact and closes two ticks later. It stores the target UUID, eligible impact kind, and base damage; it never allows a client to supply damage or a target.

**Source:** `NobaraActionTimeline.java:3-34`, `BlackFlashWindow.java:5-31`, `ProjectJjkNobaraProfile.java:53-62`. **Status:** VERIFIED.

## Hammer routing

| Priority | Result | Damage / effect |
|---:|---|---|
| 1 | active Black Flash window | multiplier bonus or flight amplification |
| 2 | nearby owned prepared nail | launch nail and open prepared-nail window |
| 3 | owned embedded nail on looked-at target | 4 damage, drive deeper, heavy stagger |
| 4 | alternating swing | horizontal 5 or overhead 8; light/heavy stagger |

The runtime preserves a per-player alternation state, tracks one pending delayed attack, and clears pending/window/alternation maps on server stop. A staggered player cannot start another Nobara action.

**Source:** `NobaraHammerCombatRuntime.java:25-180`, `CombatStagger.java`, `ProjectJjkNobaraActions.java:20-36`. **Status:** VERIFIED.

## Focus

Successful Black Flash grants `jujutsumod.black_flash_focus` as a server player tag. It is persistent with the player entity, sent on grant and join through `BlackFlashFocusPayload`, mirrored by `ClientBlackFlashFocus`, and cleared from the client on disconnect. This slice exposes `BlackFlashFocus.hasFocus` as an extension read API but defines no additional focus passive yet.

**Source:** `BlackFlashFocus.java:7-14`, `JujutsuNetworking.java:27-29`, `JujutsuClientNetworking.java:25-30`. **Status:** VERIFIED.

## Black Flash VFX composition

The `black_flash` cue carries `direction` (normalized attack vector: nail velocity for prepared-nail BF, player lookAngle for hammer BF). World geometry is **fixed in world space** at `cue.origin()` (does not follow the player). The client recipe (48 ticks lifetime) composes:

**Opening beat (one-shot):**
- World: `ImpactStyle.BLACK_FLASH` (28 ticks visible geometry) - compression ring, 5 directional blades fanning along direction, 3 lightning variants (forked/spiral/cascade selected by seed%3), micro-bolt sparks, expanding shockwave ring + inner blood ring
- Particles: FLASH x4, CRIT x8, BF_IMPACT x7, BF_LIGHTNING x10+4 (directional), BF_SPARK x16+8 (directional along cue.direction())
- Sound (5 layers): BLACK_FLASH_IMPACT (0.62 pitch), BLACK_FLASH_IMPACT_2 (0.78), SNAP (1.4), DEEP_EXPLOSION (0.48), WHOOSH_VORTEX (0.35, thunder rumble)
- GeckoLib: `triggerAction(entity, "black_flash")`

**On start (age-aware, proximity > 0.01):**
- Camera: `triggerBlackFlash(8, proximity)` - 4 impulses over ~560ms + aftershock at +400ms, FOV punch -12 (350ms) / +8 (450ms)
- HUD: `triggerImpact` + `triggerFlash(250ms, 220*proximity alpha)` + `triggerNausea(0.8)`
- Post-process: vanilla blur 300ms * proximity
- First-person: `triggerSnap` only if anchor == local player (caster-only hand snap)

**Palette (blood-black, additive-safe):**
- CORE: 80/30/30 (dark warm, does not saturate water)
- CRIMSON: 160/10/25
- RED: 120/0/8
- BLOOD: 90/0/12
- SPARK: 180/20/30
- VOID: 8/0/2

World geometry uses `directionalBasis(cue.direction())` with degeneracy fallback (NORTH as up ref when |forward dot UP| > 0.98). Lightning angles are deterministic via `pseudoRandom(cue.seed())`. Colors use Black Flash palette, not cursed blue.

**Source:** `NobaraVfxRecipes.java` (blackFlash method), `VfxWorldChannel.java` (renderBlackFlash + 3 lightning variants), `VfxCameraChannel.java` (triggerBlackFlash). **Status:** VERIFIED.

## Shockwave ring implementation (BF signature effect)

The expanding shockwave ring is the visual centerpiece of Black Flash. Implementation in `VfxWorldChannel.renderBlackFlash`, Phase D (progress 0.38-1.0):

**Geometry:** A directional ring rendered perpendicular to `cue.direction()` using `renderDirectionalRing()`. The ring expands from radius 0.5 to 4.3 blocks over the effect lifetime. Built from 20 ribbon segments arranged in a circle, each segment is a camera-billboarded quad (via `sideVector()` cross product with view direction).

**Two-layer composition:**
1. Outer ring: void-colored halo (4/0/2) at 0.2x alpha + blood-colored edge (90/0/12) at 0.55x alpha. Expands 0.5 -> 4.3 blocks.
2. Inner blood ring (first 50% of ring progress only): crimson edge (160/10/25) + blood dark layer, radius 0.3 -> 1.5 blocks, counter-rotating phase (-4x speed). Creates a "double shockwave" read.

**Fade:** Alpha follows `(1 - ringProgress)^2` quadratic decay - bright at start, smooth disappearance. The ring is visible for ~62% of the total 28-tick world impact duration.

**Why it reads as "explosion wave":** The ring starts small and bright at the impact point, expands rapidly outward (3.8 blocks over ~17 ticks), and fades quadratically. The counter-rotating inner ring adds depth. The directional basis means the ring is always perpendicular to the attack direction - a horizontal slash produces a vertical ring, an overhead produces a horizontal ring. This spatial relationship between slash direction and ring orientation sells the "force radiating from impact" read.

**Additive blending note:** Uses `RenderType.lightning()` (GL_SRC_ALPHA, GL_ONE). The dark palette values (max 160/10/25) ensure the ring reads as a deep crimson pulse without saturating the framebuffer or destroying transparent water pixels underneath.

## Verification

`BlackFlashWindowTest` covers early/on-time/late input and one-shot consumption. Manual QA remains required for perceptible timing, multiplayer observation, and animation alignment.

---
tags: #jujutsumod #combat #black-flash #timing #verified
