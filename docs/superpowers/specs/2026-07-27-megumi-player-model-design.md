# Megumi Player Model Integration

Status: APPROVED DESIGN

## Goal

Add the user-provided Megumi Fushiguro Blockbench body and GeckoLib animations to PR #16 without changing Divine Dogs gameplay, shared dispatch, networking, or the server authority boundary.

## Source Assets

- `C:/Users/KOMP1/Desktop/megumi_fushiguro_blockbench.zip`
  - `megumi_fushiguro.bbmodel`
  - one 128 x 128 model texture
  - eight clips: idle, walk, run, combat idle, two punches, kick, and Divine Dogs summon
- `C:/Users/KOMP1/Downloads/jujutsu-kaisen-megumi-fushiguro.png`
  - 64 x 64 vanilla-layout skin used for first-person arms and the roster portrait

Only runtime assets belong in the mod resources. Preview renders and the `.bbmodel` source remain outside the runtime pack.

## Runtime Presentation

Megumi opts into the existing `CharacterPlayerGeoRenderer` stack from `MegumiClientDefinition.createRenderer`. His renderer, model, animatable, and held-item layer live under `jujutsu.mod.client.render.megumi`; no shared render dispatch changes are allowed.

The exported resources use the existing indexed GeckoLib roots:

- `assets/jujutsumod/geckolib/models/megumi/megumi_fushiguro.geo.json`
- `assets/jujutsumod/geckolib/animations/megumi/megumi_fushiguro.animation.json`
- `assets/jujutsumod/textures/entity/character/megumi_fushiguro.png`
- `assets/jujutsumod/textures/entity/character/megumi.png`

The model texture is used only by the GeckoLib body. The vanilla-layout skin is declared once by `MegumiClientDefinition.playerSkin()` and is also used by the roster entry.

## Animation Routing

The base controller selects `idle`, `walk`, and `run` with the same movement evidence used by the existing vessel animatables: GeckoLib movement, horizontal velocity, sprinting, and vanilla walk animation speed.

Every ordinary vanilla attack cycles deterministically per rendered player:

1. `punch_1`
2. `punch_2`
3. `kick`
4. repeat

`MegumiPlayerGeoRenderer` owns a weak per-player swing record. It advances only on the rising edge of a new vanilla swing and writes the selected index to a Megumi-only GeckoLib render ticket. Repeated render calls during the same swing keep the same clip, so an animation cannot switch midway through an attack. This is presentation state only: it sends no packet, changes no damage, and does not survive the client entity.

`summon_divine_dogs` is an action-controller clip. A successful summon already emits `DOGS_SUMMON`; that cue will carry the caster entity id and Megumi's VFX recipe will trigger the clip on the resolved player. Recall and Sic do not reuse the summon clip. No new cue or payload is added.

`combat_idle` remains exported but unrouted. The current slice has no authoritative combat-mode state, and inventing one only to consume a clip would expand gameplay scope.

## Compatibility And Failure Policy

- Existing held-item rendering attaches to `right_hand` and `left_hand`.
- Existing vanilla arm-pose bridging remains active whenever an action clip does not own the arms.
- Existing head-look clamps remain active, damped while an action clip plays.
- Failure to resolve the summon cue's entity skips only the animation; gameplay and VFX continue.
- No changes are made to Divine Dog lifecycle, cooldowns, targeting, placement, packets, or shared renderer dispatch.

## Verification

Automated checks cover compilation, resource locations, required bones and clips, the three-step melee route, the summon cue anchor, vessel boundaries, and documentation counts. The final branch must pass `./gradlew qualityGate --no-daemon`.

In-game smoke remains required for scale and offsets, first-person arms, held items and shield, head look, idle/walk/run transitions, the three-hit animation cycle, summon animation visibility for local and remote players, and regressions to Nobara and Todo rendering.
