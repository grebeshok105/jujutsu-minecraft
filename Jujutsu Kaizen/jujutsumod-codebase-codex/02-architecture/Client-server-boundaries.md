# Client-Server Boundaries

Status: CURRENT

## Server/common

src/main owns character state, combat resolution, damage, target selection, item consumption, nail/trap lifecycle, typed payload registration, and cue creation. C2S handlers execute on the logical server.

## Client

src/client owns keybinds, ClickGui, particles, camera/HUD feedback, MSDF/SDF rendering, GeckoLib player replacement rendering, and VFX recipes/channels.

## Boundary contracts

- Character selection persists in a server-owned Fabric attachment and syncs through CharacterSelectionSyncPayload.
- Every vessel's abilities are validated server-side through CharacterAbilityExecutor, which owns the selection and cooldown checks, then asks the selected vessel's CharacterDefinition — whose tryCast delegates to that vessel's own router (NobaraAbilityRouter, TodoAbilityRouter). The receiver also refuses a cast whose claimed vessel disagrees with the stored selection — see [Networking](Networking.md) and [Vessel definitions](Vessel-definitions.md).
- VfxCue is presentation-only; clients never decide damage or resources.
- Client-only imports must not appear in src/main. CharacterClientRegistryTest walks the entire src/main source tree and fails the build on any reference to net.minecraft.client or jujutsu.mod.client.
- Six client mixins are declared; no VfxDeltaTrackerMixin is present.
