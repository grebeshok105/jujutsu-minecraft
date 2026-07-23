# Nobara GeckoLib Head Spin Root Cause

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-09

## Sources inspected

- `src/client/java/jujutsu/mod/client/render/nobara/NobaraPlayerGeoModel.java`
- `src/client/java/jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java`
- `src/main/resources/assets/jujutsumod/geo/projectjjk/nobara_kugisaki.geo.json`
- `src/main/resources/assets/jujutsumod/animations/projectjjk/npc.animation.json`
- GeckoLib source jar: `C:\Users\KOMP1\.gradle\caches\modules-2\files-2.1\software.bernie.geckolib\geckolib-fabric-1.21.8\5.2.2\c34a8f37f801b17e4fc27f0df688dad7b5fa1b27\geckolib-fabric-1.21.8-5.2.2-sources.jar`
- Obsidian notes: `grok-projectjjk-codex/00-MOC.md`, `grok-projectjjk-codex/01-meta/Citation-standard.md`, `grok-projectjjk-codex/02-architecture/Libraries.md`, `grok-projectjjk-codex/06-for-jujutsumod/Porting-notes.md`

## Findings

The head spin is state accumulation, not a bad degree/radian conversion.

GeckoLib calls `setCustomAnimations` after its normal animation pass: `GeoModel.handleAnimations` ticks the animation processor, then calls custom animations (`GeoModel.java:245-251` in the source jar). `GeoBone.setRotX/Y` writes absolute radians and marks the bone as rotation-changed (`GeoBone.java:122-131`). GeckoLib clears those change markers at the end of `AnimationProcessor.tickAnimation` (`AnimationProcessor.java:245`), before `setCustomAnimations` runs.

Current Nobara code adds look rotation to the existing bone pose:

```java
head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
```

Source: `NobaraPlayerGeoModel.java:47-51`.

That is only safe when the head pose is overwritten by keyframes every render pass. It is unsafe on animations that do not keyframe `head`, because the custom pass leaves `rotationChanged = true` for the next frame. On the next `tickAnimation`, GeckoLib skips the reset-to-initial branch for bones already marked changed (`AnimationProcessor.java:167-208`), so the old custom look remains and the new custom look is added again.

The base controller uses `idle`, `walk`, and `run` as the normal movement loop (`NobaraPlayerGeoAnimatable.java:91-105`). In `npc.animation.json`, `animation.player_model.idle` has no `bones` at all (`npc.animation.json:4-7`), and `animation.player_model.walk` has body/limb bones but no `head` keyframe near its start (`npc.animation.json:19-35`). `run` and action animations do keyframe `head` (`npc.animation.json:528-540`, `576-585`), which explains why the bug is most visible in idle/walk and less deterministic during attacks.

Yaw/pitch units and signs are broadly correct. GeckoLib's own `DefaultedEntityGeoModel` head-turn code reads pitch/yaw data and applies `-pitch * DEG_TO_RAD`, `-yaw * DEG_TO_RAD` (`DefaultedEntityGeoModel.java:50-63`). GeckoLib's replaced-entity renderer captures entity pitch, yaw, and body yaw into data tickets (`GeoReplacedEntityRenderer.java:281-283`), and the data tickets are `Float`s (`DataTickets.java:47-49`). Current Nobara code also clamps degree values and multiplies by `Mth.DEG_TO_RAD` with the same negative sign (`NobaraPlayerGeoModel.java:48-51`). The chosen yaw basis, `playerState.yRot - playerState.bodyRot`, is the right intent for a head-relative turn on a body-rotated player replacement.

The model head bone itself is normal: `head` is parented to `body` with pivot `[0, 23.75, 0]` (`nobara_kugisaki.geo.json:38-40`). Child/cube rotations exist, but they are mesh details, not evidence of a different head-look axis (`nobara_kugisaki.geo.json:42-45`).

## Safest patch shape

Use a code-side fix that prevents custom head-look from leaking into GeckoLib's next-frame reset bookkeeping:

```java
private static void applyHeadLook(GeoBone head, PlayerRenderState playerState, float weight) {
    float yawDegrees = Mth.clamp(Mth.wrapDegrees(playerState.yRot - playerState.bodyRot), -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
    float pitchDegrees = Mth.clamp(playerState.xRot, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);

    head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
    head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
    head.resetStateChanges();
}
```

This keeps additive rotation after keyframes for action/run animations, but clears only the GeckoLib change marker after the manual render-only adjustment. The current rendered pose still uses the head-look values; the next frame starts from GeckoLib's animation/reset result instead of from the previous custom look.

Optional extra hardening: add neutral `head` rotation keyframes to `animation.player_model.idle` and `animation.player_model.walk`:

```json
"head": {
  "rotation": {
    "vector": [0, 0, 0]
  }
}
```

That makes the asset overwrite the head every frame for the base loops too. It is a useful belt-and-suspenders asset fix, but the minimal and safest root-cause fix is clearing the custom change marker after applying the render-only head-look.

