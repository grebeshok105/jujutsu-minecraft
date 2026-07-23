# Curse Links

Status: CURRENT

CurseLinkRegistry stores explicit server-owned links with stable ids, participants, source owner, and technique id. Self Resonance requests available links, lets the client select an id, then revalidates server-side before damage.

Current debt: CurseLinkOptionsPayload does not cap entry count or technique-id string length, and CurseLinkSelectionScreen creates one button per entry. Add decode bounds and scrolling before the system can grow.
