# Networking

Status: CURRENT

Eight typed custom payloads are registered:

| Direction | Payload | Purpose |
|---|---|---|
| S2C | VfxCuePayload | transient visual cue |
| S2C | CharacterSelectionSyncPayload | vessel/model sync |
| C2S | SelectCharacterPayload | request persistent vessel selection |
| C2S | CharacterAbilityPayload | shared active-ability slot id plus the vessel the client believed it was casting as |
| S2C | AbilityCooldownPayload | server-confirmed ability cooldown mirror |
| S2C | CurseLinkOptionsPayload | selectable curse links |
| C2S | SelectCurseLinkPayload | chosen link |
| S2C | BlackFlashFocusPayload | focus state |

C2S receivers execute on the server thread. SelectCharacterPayload limits ids to 32 UTF characters. Unknown character ids map to None. CharacterAbility.byNetworkId returns null for an unknown slot id; the handler then does nothing. CharacterAbilityPayload carries a slot id and a claimed vessel id (also 32-char bounded); the server refuses the cast silently when the claim disagrees with the stored selection — the menu applies a switch locally before the server confirms it, and a slot means a different ability per vessel, so a press inside that round trip would otherwise be cast by the vessel the player just left. The claim is only ever compared, never trusted to choose anything; the server still resolves character, target, and success itself. See [Vessel definitions](Vessel-definitions.md). CurseLinkOptionsPayload still needs list/string bounds.

VFX cues are sent directly or radius-filtered and capability-gated. Long-lived state must not rely on a one-shot cue.
