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

C2S receivers execute on the server thread. SelectCharacterPayload limits ids to 32 UTF characters. Unknown character ids map to None. CharacterAbility.byNetworkId returns null for an unknown slot id; the handler then does nothing. CharacterAbilityPayload carries a slot id and a claimed vessel id (also 32-char bounded); the server refuses the cast silently when the claim disagrees with the stored selection — the menu applies a switch locally before the server confirms it, and a slot means a different ability per vessel, so a press inside that round trip would otherwise be cast by the vessel the player just left. The claim is only ever compared, never trusted to choose anything; the server still resolves character, target, and success itself. See [Vessel definitions](Vessel-definitions.md).

CurseLinkOptionsPayload is an S2C payload produced by the Self Resonance path and decoded through its existing `CustomPacketPayload.codec`. Its decoder owns the defensive bounds: `MAX_ENTRIES = 64` and `MAX_TECHNIQUE_ID_LENGTH = 256`. Invalid counts are rejected before allocation, over-length strings reject the whole payload, and syntactically malformed `ResourceLocation` values are dropped after the complete string has been consumed so later entries stay aligned. Its writer refuses both invalid bounds before emitting the payload. Valid entries keep the existing UUID/UUID/string wire format. A well-formed unknown technique id remains accepted under Option A because the project has no canonical supported-id catalog.

No receiver names a vessel. `SelectCurseLinkPayload` was the exception until E13 was closed: it called `SelfResonanceRuntime.select` through an inline fully qualified name, so the packet was honoured whoever sent it. It now goes to `JujutsuCharacters.of(player).selectCurseLink(...)`, which refuses by default, and `VesselBoundaryTest#theNetworkLayerTouchesNoVesselCode` holds `JujutsuNetworking` at zero dependencies on any vessel package.

VFX cues are sent directly or radius-filtered and capability-gated. Long-lived state must not rely on a one-shot cue.
