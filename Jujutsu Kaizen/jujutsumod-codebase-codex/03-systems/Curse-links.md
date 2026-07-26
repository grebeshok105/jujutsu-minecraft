# Curse Links

Status: CURRENT

CurseLinkRegistry stores explicit server-owned links with stable ids, participants, source owner, and technique id. Self Resonance requests available links, lets the client select an id, then revalidates server-side before damage.

The registry is shared, not Nobara's — `JujutsuCommands` reads it too — so the choice travelling back from the client is a neutral intent. `SelectCurseLinkPayload` reaches `CharacterDefinition.selectCurseLink`, which refuses by default; Nobara overrides it and forwards to `SelfResonanceRuntime.select`, where ownership and participation are checked. Before E13 was closed the receiver named that runtime directly, which meant the packet was honoured no matter which vessel sent it. See [Networking](../02-architecture/Networking.md) and [Vessel definitions](../02-architecture/Vessel-definitions.md).

Current debt: CurseLinkOptionsPayload does not cap entry count or technique-id string length, and CurseLinkSelectionScreen creates one button per entry. Add decode bounds and scrolling before the system can grow.
