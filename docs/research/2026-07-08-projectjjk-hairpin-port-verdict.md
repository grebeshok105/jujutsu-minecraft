# ProjectJJK Hairpin Port Verdict

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-08

Workspace: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`

Scope: research only. No mod source or runtime assets were changed.

## Primary Sources

- `C:\Users\KOMP1\Downloads\projectjjk-1.2.0-1.21.1-fabric-beta.jar`
- `C:\Users\KOMP1\Downloads\specter-v0.1.6.jar`
- `C:\Users\KOMP1\Downloads\jujutsumod_projectjjk_integration_spec.md`
- `C:\Users\KOMP1\Downloads\projectjjk_asset_spec.md`

Temporary inspection copy:

- `%TEMP%\projectjjk_hairpin_research\projectjjk`
- `%TEMP%\projectjjk_hairpin_research\specter`

Tools used: zip extraction, class/resource listing, class constant-pool string extraction, and CFR decompilation for selected classes. The decompiled Java is treated as a readable view of the local jar bytecode, not as source to copy into the mod.

## Short Verdict

| Requested thing | Port verdict to Fabric 1.21.8 | 1:1 feasibility |
|---|---|---|
| Blue outline/mark after nail hit | Portable as a new implementation. | Not 1:1 by asset copy alone; ProjectJJK uses per-player glowing UUID data, custom glow color sync, and client mixins. |
| Hairpin enlarge, including FX/damage/sounds/logic | Portable as a behavior reimplementation. | Functionally 1:1 is feasible from bytecode/spec, but direct code/dependency port is not recommended. |
| First-person snap animation | Portable without Specter. | 1:1 motion curve is feasible; it depends on ProjectJJK's own `fpanim` system and held-item renderer mixin, not Specter. |
| Hairpin explosion, including FX/damage/sounds/logic | Portable as a behavior reimplementation. | Functionally 1:1 is feasible from bytecode/spec, but it relies on NailEntity state, scheduled tasks, VFX entities, particles, sounds, and Satin screen shake. |

Important legal/maintenance note: ProjectJJK metadata says `license: All Rights Reserved`; Specter metadata says `license: ARR`. Treat both as reference unless permission explicitly covers reuse. Do not copy decompiled code into this mod.

## Dependencies And Version Risk

ProjectJJK is a Fabric `1.21.1` mod:

- Source: `projectjjk-1.2.0-1.21.1-fabric-beta.jar!/fabric.mod.json`
- Mod id/version: `projectjjk`, `1.2.0-1.21.1-fabric-beta`
- Depends on Minecraft `~1.21.1`, Java `>=21`, Fabric API.
- Includes: `specter-v0.1.0.jar`, `BossBarLib-v2.0.2.jar`, `geckolib-fabric-1.21.1-4.6.6.jar`, `midnightlib-1.6.3-fabric.jar`, `satin-2.0.0.jar`, `SmartBrainLib-fabric-1.21.1-1.16.1.jar`.
- Uses `projectjjk.accesswidener` and `projectjjk.mixins.json`.

Specter standalone jar is also for Minecraft `~1.21.1`:

- Source: `specter-v0.1.6.jar!/fabric.mod.json`
- Mod id/version: `specter`, `v0.1.6`
- Includes `renderer-fabric-1.2.1.jar` and `satin-2.0.0.jar`.
- Uses `specter.accesswidener` and `specter.mixins.json`.

Conclusion: direct runtime port is high risk for Fabric/Minecraft `1.21.8`. The safer route is to reimplement behavior using public Fabric 1.21.8 APIs and use ProjectJJK as a behavioral/asset reference only.

## Blue Outline / Mark On Nail Hit

ProjectJJK has a general per-player glowing/outline system:

- `projectjjk-...jar!/net/hadences/data/GlowingData.class`
  - Stores `glowing_entities` as UUID list in player persistent data.
  - Stores `glowing_color` as an integer.
  - Syncs to client with `SynchronizeGlowingDataPacket` and `SynchronizeGlowingColorPacket`.
- `projectjjk-...jar!/net/hadences/mixin/client/EnableGlowingMobMixin.class`
  - Modifies `MinecraftClient.hasOutline(entity)` return value.
  - Returns true when the entity UUID is in the local player's glowing UUID list.
- `projectjjk-...jar!/net/hadences/mixin/GlowingColorMixin.class`
  - Injects into `Entity.getTeamColorValue`.
  - Replaces outline color with the local player's `glowing_color`.

Related nail source:

- `projectjjk-...jar!/net/hadences/entity/custom/projectile/NailEntity.class`
  - On server tick, while flying and not in ground, spawns dust with color `(0.17254902, 0.9098039, 0.9607843)` and scale `0.5`.
  - On entity hit, applies `ModDamageTypes.NAIL_DAMAGE`.
- `projectjjk-...jar!/data/projectjjk/damage_type/nail_damage.json`
  - `message_id: "nail_damage"`.

Finding:

- The blue visual language is real and consistent: ProjectJJK uses cyan/blue particle color `0.17254902, 0.9098039, 0.9607843` in Nail/Hairpin effects.
- The actual blue outline/mark is not a standalone asset. It is a runtime outline system based on UUID sync + mixins.
- I did not find evidence that NailEntity itself directly registers the glowing UUID on hit. The mark likely lives in ability/runtime support code outside the selected NailEntity hit method, or is driven by the integration/spec concept rather than the projectile class alone. The existence of `GlowingData` and outline mixins proves ProjectJJK has the mechanism, but not that a nail hit automatically marks without additional ability code.

Port recommendation:

- Reimplement as a `jujutsumod` client/server semantic marker:
  - server records nail-tagged target UUID + expiry;
  - server syncs marker list/color to the relevant player;
  - client renders outline/marker using our own 1.21.8-safe path.
- Avoid copying ProjectJJK's mixins directly. If outline must use vanilla outline, use a narrow client mixin or a custom render layer after a design decision.

## Hairpin Enlarge

Primary classes/resources:

- `projectjjk-...jar!/net/hadences/game/system/ability/technique/innate/straw_doll_technique/HairpinEnlargement.class`
- `projectjjk-...jar!/net/hadences/entity/movesets/cursed_techniques/straw_doll/HairpinEnlargementMoveset.class`
- `projectjjk-...jar!/net/hadences/util/damage_type/ModDamageTypes.class`
- `projectjjk-...jar!/data/projectjjk/damage_type/hairpin_enlargement.json`
- `projectjjk-...jar!/assets/projectjjk/sounds/snap.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/black_flash_impact.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/goo_foley.ogg`
- `projectjjk-...jar!/net/hadences/entity/custom/vfx/flash_strike_effect/FlashStrike64VFX.class`
- `projectjjk-...jar!/net/hadences/entity/custom/vfx/flash_strike_effect/FlashStrike64VFX2.class`
- `projectjjk-...jar!/net/hadences/entity/custom/vfx/spark_effect/SparkVFX.class`
- `projectjjk-...jar!/net/hadences/particle/ModParticles.class`

Observed player ability logic:

- Raycasts from player eye position along look direction.
- Range is constructor-driven (`rangeInBlocks`), and the moveset variant uses a fixed `10.0` block cast range.
- Requires the hit living target to be tagged in `ITEVisualizer` before casting.
- Starts first-person animation `"snap"` via `PlayerManager.serverPlayFPAnimation(player, "snap")`.
- Plays `ModSounds.SNAP` immediately.
- Schedules the actual enlarge hit after `1000 ms`.

Observed enlarge hit:

- Applies stun:
  - players: `PlayerManager.stunPlayer(p, 2)`;
  - non-player living entities: `ModEffects.STUN` for `50` ticks, amplifier `1`.
- Applies damage:
  - damage type key: `projectjjk:hairpin_enlargement`;
  - data file: `data/projectjjk/damage_type/hairpin_enlargement.json`;
  - amount comes from ability damage, through `getHPDamage(player)` for player ability or `JJKEntity.getScaledDamage(...)` for NPC moveset.
- Sounds at target:
  - vanilla-ish impact sound (`class_3417.field_14896.comp_349()`) volume `0.25`, pitch `2.0`;
  - `ModSounds.BLACK_FLASH_IMPACT` volume `2.0`, pitch `2.0`;
  - `ModSounds.GOO_FOLEY` volume `0.25`, pitch `1.5`.
- Client/player screen effects in player ability:
  - `SatinUtil.ShaderEffect.SCREEN_SHAKE` for `8`;
  - `SatinUtil.ShaderEffect.BLACK_AND_WHITE` for `4`.
- World FX:
  - `BloodParticleEffect(10, zero velocity, target eye position)`;
  - cyan flash at target + `(0, 1, 0)` with color `(0.17254902, 0.9098039, 0.9607843)`, size/amount `3`;
  - `FlashStrike64VFX` scale `(6.5, 0.5, 6.5)`, color/int `60411`, offset from target, rotation `(player body yaw, -90)`;
  - `FlashStrike64VFX2` scale `(5.0, 0.5, 5.0)`, color/int `60411`, rotation `(player body yaw, 90)`;
  - two `SparkVFX`, scale `(2,2,2)`, color/int `1578021`, rotated differently;
  - `ModParticles.CE_SPARK`;
  - vanilla particle `class_2398.field_38002`.

Verdict:

- 1:1 behavior is reproducible as a reimplementation.
- Asset-only port is insufficient: the effect depends on scheduled server logic, custom damage type, stun effect, first-person animation, VFX entity classes, particles, sounds, and Satin shader effects.
- Direct code port is not recommended because the class is built against 1.21.1 named/intermediary symbols, SmartBrainLib for NPC movesets, Satin, ProjectJJK's ability framework, and ProjectJJK utility classes.

## First-Person Snap Animation

Primary classes/resources:

- `projectjjk-...jar!/net/hadences/util/fpanim/FPAnimator.class`
- `projectjjk-...jar!/net/hadences/util/fpanim/animations/SnapAnimation.class`
- `projectjjk-...jar!/net/hadences/util/fpanim/animations/DefaultFPAnimation.class`
- `projectjjk-...jar!/net/hadences/mixin/client/HeldItemRendererMixin.class`
- `projectjjk-...jar!/projectjjk.mixins.json`
- `projectjjk-...jar!/assets/projectjjk/sounds/snap.ogg`

Observed animation implementation:

- `FPAnimator` registers animation name `"snap"` to `new SnapAnimation("snap")`.
- `SnapAnimation` duration is `0.5f`.
- It renders only the main hand arm (`class_1268.field_5808`).
- Motion curve:
  - `scaledProgress = progress * 10`;
  - phase `< 1`: translate `(1.25, -0.8, 0)`, rotate `(50,70,-20)`;
  - phase `< 4`: ease-in quartic from y `-0.8` to `-0.3`, x rotation `50` to `20`;
  - phase `< 8`: hold translate `(1.25, -0.3, 0)`, rotate `(20,70,-20)`;
  - phase `< 15`: cubic return y `-0.3` to `-1.2`;
  - else stop.
- `HeldItemRendererMixin` cancels `renderFirstPersonItem` while `ProjectJJKClient.fpAnimator.isPlayingAnimation()` and renders player arm manually through `IHeldItemRenderer.renderArm(...)`.

Specter dependency check:

- `specter-v0.1.6.jar!/fabric.mod.json` exposes a rendering/shader library with `SpecterGameRendererMixin` and `SpecterShaderProgramMixin`.
- `projectjjk-...jar!/net/hadences/util/fpanim/*` references ProjectJJK's own `fpanim` interfaces/classes, not `net/hadences/Specter` or `net/lib/Specter`.
- `SnapAnimation.class` references Minecraft matrix/math/hand classes and `DefaultFPAnimation`; no Specter class appeared in its constant-pool references.

Verdict:

- Snap animation does not depend on Specter.
- 1:1 snap motion is portable by reimplementing the curve and a 1.21.8-safe first-person hand render hook.
- The sound layer uses `assets/projectjjk/sounds/snap.ogg` and `ModSounds.SNAP`.

## Hairpin Explosion

Primary classes/resources:

- `projectjjk-...jar!/net/hadences/game/system/ability/technique/innate/straw_doll_technique/HairpinExplosion.class`
- `projectjjk-...jar!/net/hadences/entity/movesets/cursed_techniques/straw_doll/HairpinExplosionMoveset.class`
- `projectjjk-...jar!/net/hadences/entity/custom/projectile/NailEntity.class`
- `projectjjk-...jar!/net/hadences/util/damage_type/ModDamageTypes.class`
- `projectjjk-...jar!/data/projectjjk/damage_type/hairpin_explosion.json`
- `projectjjk-...jar!/assets/projectjjk/sounds/snap.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/explode.ogg`
- `projectjjk-...jar!/net/hadences/entity/custom/vfx/flash_effect/Flash32VFX.class`
- `projectjjk-...jar!/net/hadences/particle/ModParticles.class`

Observed logic:

- Finds owned nails by hitscan:
  - origin: player eye position plus look direction normalized and scaled by `4.0`;
  - direction: player look direction;
  - range: ability constructor value (`rangeDetectRange`) or fixed `10.0` in NPC moveset;
  - radius/width argument: `5.0`;
  - only includes `NailEntity` whose owner is the caster.
- Shuffles nails.
- Starts first-person `"snap"` animation for player ability.
- Plays `ModSounds.SNAP`.
- For every found nail:
  - `nail.setEnergyActive(true)`;
  - spawns vanilla particles `field_38002` and `field_11208`.
- Starts scheduled repeating task:
  - initial delay `500 ms`;
  - interval `20 ms`;
  - each tick explodes `random.nextInt(1, 3)` nails, so 1 or 2 nails per tick;
  - when index reaches nail count, task cancels.

Observed per-nail explosion:

- `nail.setEnergyActive(false)`.
- Cyan flash at nail position with color `(0.17254902, 0.9098039, 0.9607843)`, amount/size `3`.
- Spawns `ModParticles.CE_SPARK` and vanilla particle `field_38002`.
- Looks for entities in an AABB expanded by `1.5` around nail position.
- Excludes nail and player in the player ability. The NPC moveset decompilation shows `entity -> entity != nail && entity != entity`, which is likely a decompiler/name-shadow artifact or original bug; player ability clearly excludes `player`.
- For each living entity:
  - launch vector is from nail to entity, normalized;
  - player ability knockback scale `0.2`;
  - NPC moveset knockback scale `0.5`;
  - if victim is player, plays `SatinUtil.ShaderEffect.SCREEN_SHAKE` for `10`;
  - applies `projectjjk:hairpin_explosion` damage.
- Plays `ModSounds.EXPLODE` at the nail, volume `0.2`, pitch `2.0`.
- Spawns `Flash32VFX` scale `(3.0, 1.0, 3.0)`, color/int `2943221`, y offset `+0.2`.
- Player ability also plays caster screen shake `SatinUtil.ShaderEffect.SCREEN_SHAKE` for `5` each scheduled run.

Verdict:

- 1:1 behavior is reproducible as a reimplementation.
- Asset-only port is insufficient: this depends on persistent NailEntity ownership/state, hitscan utility, scheduled task timing, damage type, VFX entity, particle registration, sound events, and optional Satin screen shake.
- Direct code port is not recommended for the same reasons as Hairpin Enlarge.

## Nail Entity And Assets

Primary sources:

- `projectjjk-...jar!/net/hadences/entity/custom/projectile/NailEntity.class`
- `projectjjk-...jar!/net/hadences/entity/client/projectile/NailModel.class`
- `projectjjk-...jar!/assets/projectjjk/geo/nail.geo.json`
- `projectjjk-...jar!/assets/projectjjk/textures/entity/nail.png`

NailEntity behavior:

- Extends Minecraft persistent projectile class (`class_1665` in decompiled intermediary view).
- Implements GeckoLib `GeoEntity`.
- Tracked data:
  - `maxAge`, default `1200`;
  - `scale`, default `(1,1,1)`;
  - tracked yaw/pitch;
  - `energyActive`, default `false`.
- Server tick:
  - despawns when age reaches `maxAge`;
  - while flying and not in ground, spawns cyan dust trail with color `(0.17254902, 0.9098039, 0.9607843)`, scale `0.5`, count `10`.
- Hit:
  - applies `projectjjk:nail_damage` from owner with projectile damage value.

Nail model:

- `NailModel.class` uses namespace `projectjjk`.
- Model: `geo/nail.geo.json`.
- Texture: `textures/entity/nail.png`.
- No animation resource path is returned for nail.

Port implication:

- Hairpin explosion 1:1 needs a nail runtime object or equivalent tracked server state. A pure particle nail cannot support ProjectJJK's ownership scan and delayed explosion behavior without adding a separate logical nail registry.

## Sounds

Relevant sound sources:

- `projectjjk-...jar!/assets/projectjjk/sounds.json`
- `projectjjk-...jar!/assets/projectjjk/sounds/snap.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/explode.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/black_flash_impact.ogg`
- `projectjjk-...jar!/assets/projectjjk/sounds/goo_foley.ogg`
- `projectjjk-...jar!/net/hadences/sound/ModSounds.class`

`ModSounds.class` constant-pool/decompiled references include:

- `BLACK_FLASH_IMPACT`
- `BLACK_FLASH_IMPACT2`
- `EXPLODE`
- `SNAP`
- `SPIRIT_SOUND1..9`
- project namespace `projectjjk`

The local asset spec also lists additional sounds useful for Hairpin/Nobara:

- Source: `C:\Users\KOMP1\Downloads\projectjjk_asset_spec.md`
- Relevant entries: `snap`, `spell_shot`, `explode`, `implode`, `deep_explosion`, `chime`, `magic`.

## Particles, VFX, Shaders, Specter

ProjectJJK particle/VFX sources:

- `projectjjk-...jar!/net/hadences/particle/ModParticles.class`
- `projectjjk-...jar!/assets/projectjjk/particles/*.json`
- `projectjjk-...jar!/assets/projectjjk/textures/particle/**`
- `projectjjk-...jar!/net/hadences/entity/custom/vfx/**`

`ModParticles.class` references both local particle classes and Specter-like particle effects:

- Simple/project particles: `SPARK`, `BF_SPARK`, `BLUE_SPARK`, `RED_SPARK`, `PURPLE_SPARK`, `CE_SPARK`.
- Plane/custom effects: `IMPACT_MINI`, `IMPACT_SMALL`, `IMPACT_LARGE`, `IMPACT_GIANT`, `BF_IMPACT`, `GLOW*`, `SPARK*`, etc.
- Specter package references: `net/lib/Specter/effects/*`, `net/lib/Specter/particles/*`, `net/hadences/particles/types/SpecterParticleTypes`.

Specter standalone jar:

- `specter-v0.1.6.jar!/net/hadences/Specter.class`
- `specter-v0.1.6.jar!/net/hadences/SpecterClient.class`
- `specter-v0.1.6.jar!/net/hadences/mixin/client/SpecterGameRendererMixin.class`
- `specter-v0.1.6.jar!/net/hadences/mixin/client/SpecterShaderProgramMixin.class`
- `specter-v0.1.6.jar!/assets/specter/shaders/core/default.*`
- `specter-v0.1.6.jar!/assets/specter/particles/specter_test.json`

Verdict:

- Specter is relevant to ProjectJJK's custom particle/shader stack.
- Specter is not required for first-person snap.
- Hairpin Enlarge/Explosion use ProjectJJK VFX entities and particles; these can be recreated without Specter if our mod implements its own transient VFX/render layer, but exact ProjectJJK particle behavior may require porting the Specter-like particle concepts.
- Satin is relevant for screen shake/black-and-white shader calls in Hairpin Enlarge/Explosion.

## Damage Types

Primary sources:

- `projectjjk-...jar!/net/hadences/util/damage_type/ModDamageTypes.class`
- `projectjjk-...jar!/data/projectjjk/damage_type/nail_damage.json`
- `projectjjk-...jar!/data/projectjjk/damage_type/curse_bind_nail.json`
- `projectjjk-...jar!/data/projectjjk/damage_type/hairpin_enlargement.json`
- `projectjjk-...jar!/data/projectjjk/damage_type/hairpin_explosion.json`

Relevant keys in `ModDamageTypes.class`:

- `projectjjk:nail_damage`
- `projectjjk:hairpin_enlargement`
- `projectjjk:hairpin_explosion`
- `projectjjk:curse_bind_nail`

All inspected JSON files use:

```json
{
  "exhaustion": 0.1,
  "message_id": "...",
  "scaling": "when_caused_by_living_non_player"
}
```

Port recommendation:

- Recreate as `jujutsumod:*` damage types unless a vendor namespace decision is explicitly approved.
- Keep ProjectJJK JSON values as reference.

## Integration Spec Cross-Check

`C:\Users\KOMP1\Downloads\jujutsumod_projectjjk_integration_spec.md` already recommends:

- asset/runtime migration rather than code copy;
- keep `jujutsumod` as the runtime mod;
- do not copy `assets/minecraft`, `data/minecraft`, access widener, bundled jars, or Fabric metadata blindly;
- use ProjectJJK assets as vendor/reference material;
- rewrite GeckoLib renderers, VFX entities, particle factories, sound aliases, networking, and gameplay code under our architecture.

`C:\Users\KOMP1\Downloads\projectjjk_asset_spec.md` confirms:

- license risk: ProjectJJK jar metadata is `All Rights Reserved`;
- ProjectJJK has 42 sound files, 50 particle JSON files, 25 animation files, 38 geo models;
- Nobara/Hairpin-relevant assets include `geo/nail.geo.json`, `textures/entity/nail.png`, `geo/doll.geo.json`, `animations/doll.animation.json`, `textures/entity/doll.png`, Hairpin ability icons, `snap`, `explode`, `deep_explosion`, and generic VFX particles.

## Final Recommendation

Port all four requested features as clean-room behavior implementations guided by the local ProjectJJK jar/specs:

1. Implement a `jujutsumod` nail marker/outline system with server-owned target UUIDs and client rendering.
2. Implement Hairpin Enlarge timing and FX profile from ProjectJJK, but map damage/sounds/VFX into our own registries.
3. Recreate first-person snap from `SnapAnimation`'s transform curve; Specter is not needed.
4. Implement Hairpin Explosion around owned nail runtime objects, preserving the 500 ms start delay, 20 ms cadence, 1-2 nails per tick, 1.5 block blast radius, snap/explode sounds, cyan flash, CE sparks, and knockback/damage.

Do not port ProjectJJK classes directly. The exact 1:1 target should be documented as a behavior spec and implemented with Fabric 1.21.8 APIs, our side separation, and our networking model.
