# Client-Server Boundaries

Status: CURRENT

## Server/common

src/main owns character state, combat resolution, damage, target selection, item consumption, nail/trap lifecycle, typed payload registration, and cue creation. C2S handlers execute on the logical server.

## Client

src/client owns keybinds, ClickGui, particles, camera/HUD feedback, MSDF/SDF rendering, GeckoLib player replacement rendering, and VFX recipes/channels.

## Boundary contracts

- Character selection persists in a server-owned Fabric attachment and syncs through CharacterSelectionSyncPayload.
- Nobara actions are validated server-side through ProjectJjkNobaraActions.
- VfxCue is presentation-only; clients never decide damage or resources.
- Client-only imports must not appear in src/main.
- Six client mixins are declared; no VfxDeltaTrackerMixin is present.
