# Networking

Status: CURRENT

Eight typed custom payloads are registered:

| Direction | Payload | Purpose |
|---|---|---|
| S2C | VfxCuePayload | transient visual cue |
| S2C | CharacterSelectionSyncPayload | vessel/model sync |
| C2S | SelectCharacterPayload | request persistent vessel selection |
| C2S | CharacterAbilityPayload | shared active-ability slot id, for every vessel |
| S2C | AbilityCooldownPayload | server-confirmed ability cooldown mirror |
| S2C | CurseLinkOptionsPayload | selectable curse links |
| C2S | SelectCurseLinkPayload | chosen link |
| S2C | BlackFlashFocusPayload | focus state |

C2S receivers execute on the server thread. SelectCharacterPayload limits ids to 32 UTF characters. Unknown character ids map to None. The only remaining id gate is CharacterAbility.byNetworkId, which returns null for an unknown slot id; the handler then does nothing. CharacterAbilityPayload carries only a slot id; the server resolves character, target, and success. CurseLinkOptionsPayload still needs list/string bounds.

VFX cues are sent directly or radius-filtered and capability-gated. Long-lived state must not rely on a one-shot cue.
