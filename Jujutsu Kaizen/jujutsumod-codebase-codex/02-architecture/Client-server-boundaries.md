# Client-Server Boundaries

Status: CURRENT

## Server/common

src/main owns character state, combat resolution, damage, target selection, item consumption, nail/trap lifecycle, typed payload registration, and cue creation. C2S handlers execute on the logical server.

## Client

src/client owns keybinds, ClickGui, particles, camera/HUD feedback, MSDF/SDF rendering, GeckoLib player replacement rendering, and VFX recipes/channels.

## Boundary contracts

- Character selection persists in a server-owned Fabric attachment and syncs through CharacterSelectionSyncPayload.
- Every vessel's abilities are validated server-side through CharacterAbilityExecutor, which owns the selection and cooldown checks, then routes the slot to that vessel's own router (NobaraAbilityRouter, TodoAbilityRouter).
- VfxCue is presentation-only; clients never decide damage or resources.
- Client-only imports must not appear in src/main.
- Six client mixins are declared; no VfxDeltaTrackerMixin is present.
