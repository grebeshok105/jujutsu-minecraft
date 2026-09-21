# Cursed incidents v1 archive

Frozen on 2026-09-21 from main commit `be3d45937d906cca211a225870dcb8d45f94a6b5`.

This snapshot preserves the cursed-incidents / cursed-zones prototype while removing it from the active mod source sets.

Archived here:
- server incident model, persistence, infection/zone runtime and cursed-object system
- client incident atmosphere, zone rendering, cursed-object renderer and seal FX
- incident network payloads
- incident GameTests, unit tests and MCP dev tools
- cursed-object/talisman assets, cursed-mote particle, incident sounds and cursed-zone damage type
- the cursed-object asset generator

`integration-snapshots/` contains the pre-archive versions of shared files that had incident wiring mixed into otherwise active systems. Use these together with the archived source if the feature is revived later.

The active source tree intentionally keeps cursed spirits and their perception system. Only the incident override was removed, restoring vessel-only curse perception.

This archive is not compiled or loaded by the mod.
