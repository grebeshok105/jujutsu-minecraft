# Block 4 report — grabber contact gate, smooth carry, shake fix

## Done

- Kept runner selection at the existing profile cap of 12 blocks and added `RUNNER_CONTACT_RANGE = 2.2` as a hitbox-edge cap. `RunnerEffect` now adapts `CursedSpiritAttackPolicy.inReach(...)` with tier reach (2.0/2.5/3.0), then applies the explicit edge-distance cap and performs the LOS check only on the CONTACT tick.
- Reworked the runner lifecycle to `APPROACH -> WINDUP -> CONTACT -> CARRY`. Starting an effect records intent and navigation only; CARRIED, GRIPPED, hand drops, teleport/hold, and the contact cue are committed only after the CONTACT reach+LOS re-check. A failed CONTACT ends the window cleanly and leaves the victim untouched.
- Added yaw-relative carry anchor (`forward 0.6`, `up 1.2`) and contact-point grab flash cue.
- Extended `HoldSupport` with holder, collision policy, bounded wall-clamp probes, and held-pair registration. RUNNER uses up to three probes toward the holder; TOAD keeps the prior one-step fallback-to-body semantics. Added `HeldVictimRegistry` and server `HeldVictimPushMixin` (JSON registration remains B5-owned).
- Added client `CarriedVictimSmoothing` and `HeldVictimRenderMixin`: GRIPPED victims ease toward replicated server anchors over approximately three ticks, and release continues from the current visual position before returning to vanilla coordinates.
- Confirmed the shake root cause from the Prowler SCREAMER channel: body POSITION keys contained repeated lateral/forward offsets (including ±1/±2 values), and the flater root-like channel had a -20 offset. All seven scream-enabled packs now retain rotation recoil but have positional scream channels stripped; the 0.75-block-equivalent bound is documented in `CursedSpiritClips`. GULBER/GUZZLER remain screamless controls.
- Added `runnerMustApproachBeforeGrab`, `runnerCarryAnchorClearsWalls`, and `CursedSpiritScreamClipTest`; updated the existing runner marker test for the pre-carry phases and updated the direct Toad hold test for the new policy API.

## Files touched

- `src/main/java/jujutsu/mod/cursedspirit/ability/effects/RunnerEffect.java`
- `src/main/java/jujutsu/mod/cursedspirit/ability/CursedSpiritAbilityBrain.java`
- `src/main/java/jujutsu/mod/cursedspirit/ability/CursedSpiritAbilityProfile.java`
- `src/main/java/jujutsu/mod/combat/HoldSupport.java`
- `src/main/java/jujutsu/mod/cursedspirit/hold/HeldVictimRegistry.java` (new)
- `src/main/java/jujutsu/mod/mixin/HeldVictimPushMixin.java` (new; B5 adds JSON entry)
- `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritEntity.java`
- `src/main/java/jujutsu/mod/character/megumi/MegumiToadBrain.java` (required call-site migration)
- `src/client/java/jujutsu/mod/client/render/cursedspirit/CarriedVictimSmoothing.java` (new)
- `src/client/java/jujutsu/mod/client/mixin/HeldVictimRenderMixin.java` (new; B5 adds JSON entry)
- `src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritClips.java`
- `src/client/java/jujutsu/mod/client/render/cursedspirit/anim/CursedSpirit{Prowler,FloatingCurse,Kelvin,Butcher,Blud,WalkingBed,Wistiver}Animations.java`
- `src/client/java/jujutsu/mod/client/vfx/cursedspirit/CursedSpiritVfxRecipes.java`
- `src/gametest/java/jujutsu/mod/gametest/CursedSpiritAbilityGameTests.java`
- `src/gametest/java/jujutsu/mod/gametest/CursedSpiritEffectGameTests.java`
- `src/gametest/java/jujutsu/mod/gametest/MegumiToadGameTests.java`
- `src/test/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritScreamClipTest.java` (new)

## Deviations / unresolved

- No mixin JSON or networking files were edited; B5 must register `HeldVictimPushMixin` in `jujutsumod.mixins.json` and `HeldVictimRenderMixin` in `jujutsumod.client.mixins.json` at integration.
- Worker did not run Gradle, tests, GameTest, qualityGate, MCP, or screenshot capture per the Block 4 worker constraint. Rendered-centroid/in-game proof remains a barrier task for Main.
- `MegumiToadBrain.java` is the required cross-file call-site migration for the new `HoldSupport` signature; Main accepted this as in-scope despite the original ownership-row omission.

## Main verification

Run: `gradlew.bat compileJava compileClientJava compileGametestJava test --tests "*CursedSpirit*"`

Expected: green; includes `runnerMustApproachBeforeGrab`, `runnerCarryAnchorClearsWalls`, `CursedSpiritScreamClipTest`, and existing runner/toad regressions.

Run: `gradlew.bat qualityGate`

Expected: green with B5 mixin JSON entries integrated; barrier also captures the in-game carry smoothness, wall clamp, contact flash, and scream centroid evidence.
