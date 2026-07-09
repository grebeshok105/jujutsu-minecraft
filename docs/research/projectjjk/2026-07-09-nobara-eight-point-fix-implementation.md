# Nobara Eight-Point Fix Implementation

Date: 2026-07-09

## Scope

This pass implements the approved eight-point Nobara repair against the cinematic worktree, not the older main checkout. The goal was to remove the most visible regressions: transparent/chasing embedded nails, floating blue target marks, V-screen fill cost, soul-fire nail aura, and the missing GeckoLib-backed player body.

## Changes

- Embedded nails now use `ProjectJjkNailEmbedding.bodyEmbedPoint` and no longer reset old position every embedded tick, so interpolation is not killed while the target moves.
- Embedded nail rendering no longer draws translucent lightning ribbons over the mesh. The nail uses the fixed 3D item transform and a small embed offset.
- Target marks now use `entity.getPosition(partialTick)` and render as a tight cyan body glow shell instead of the old orbiting blue cage.
- Prepared/flying nails now render a cyan force-field envelope in the client world renderer. `ProjectJjkNobaraRuntime` no longer emits vanilla soul-fire particles for nail prepare/flight/impact.
- The V character select UI no longer uses per-pixel-row rounded rectangle fills for large panels/cards.
- GeckoLib `5.2.2` is a required dependency, matching the installed `geckolib-fabric-1.21.8-5.2.2.jar`.
- Nobara's ProjectJJK geo model and `npc.animation.json` are rendered on the selected player through client-only renderer mixins. `NobaraPlayerRendererMixin` records player entity ids; `NobaraLivingEntityRendererMixin` hooks the declared living render method and swaps only selected Nobara players. This does not create an NPC entity or AI.
- The V-screen portrait still uses `textures/entity/character/nobara.png`, not the GeckoLib NPC texture.

## Hairpin Enlarge / Boom

They remain explicit Nobara actions:

- `R` sends `NobaraActionPayload.HAIRPIN_ENLARGE`.
- `B` sends `NobaraActionPayload.HAIRPIN_EXPLOSION`.
- Both keybinds and commands route through `ProjectJjkNobaraActions.tryCast`.

## Verification

- `gradlew.bat testProjectSanity --no-daemon`
- `gradlew.bat check --no-daemon`
- `gradlew.bat build --no-daemon -x test`

Compilation verifies the wiring and regression checks. It does not prove the GeckoLib player render is visually correct in-game; that still needs an in-client smoke test after the jar is installed.
