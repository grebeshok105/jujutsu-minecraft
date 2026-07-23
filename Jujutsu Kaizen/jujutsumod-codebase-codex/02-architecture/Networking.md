# Networking

Status: CURRENT

Seven typed custom payloads are registered:

| Direction | Payload | Purpose |
|---|---|---|
| S2C | VfxCuePayload | transient visual cue |
| S2C | CharacterSelectionSyncPayload | vessel/model sync |
| C2S | SelectCharacterPayload | request persistent vessel selection |
| C2S | NobaraActionPayload | Nobara action id |
| S2C | CurseLinkOptionsPayload | selectable curse links |
| C2S | SelectCurseLinkPayload | chosen link |
| S2C | BlackFlashFocusPayload | focus state |

C2S receivers execute on the server thread. SelectCharacterPayload limits ids to 32 UTF characters. Unknown character ids map to None; unknown action ids fail closed. CurseLinkOptionsPayload still needs list/string bounds.

VFX cues are sent directly or radius-filtered and capability-gated. Long-lived state must not rely on a one-shot cue.
