# Curse Links

Status: CURRENT

CurseLinkRegistry stores explicit server-owned links with stable ids, participants, source owner, and technique id. Self Resonance requests available links, lets the client select an id, then revalidates server-side before damage.

The registry is shared, not Nobara's — `JujutsuCommands` reads it too — so the choice travelling back from the client is a neutral intent. `SelectCurseLinkPayload` reaches `CharacterDefinition.selectCurseLink`, which refuses by default; Nobara overrides it and forwards to `SelfResonanceRuntime.select`, where ownership and participation are checked. Before E13 was closed the receiver named that runtime directly, which meant the packet was honoured no matter which vessel sent it. See [Networking](../02-architecture/Networking.md) and [Vessel definitions](../02-architecture/Vessel-definitions.md).

Decode bounds are done: `CurseLinkOptionsPayload` caps entries at MAX_ENTRIES=64 and technique-id length at MAX_TECHNIQUE_ID_LENGTH=256 on read (`src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java:15,22,28`) and on write (`src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java:50-53`), covered by `CurseLinkOptionsPayloadTest`. Still open: `CurseLinkSelectionScreen` creates one button per entry with no scroll (`src/client/java/jujutsu/mod/client/gui/CurseLinkSelectionScreen.java:20-27`), so a large option set overflows the screen.
