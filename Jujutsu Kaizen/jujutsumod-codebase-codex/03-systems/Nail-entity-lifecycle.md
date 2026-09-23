# Nail Entity Lifecycle

Status: CURRENT

ProjectJjkNailEntity moves through prepared, launched, and embedded states. Anchors may target an entity, block, or registered runtime object. Ordinary embedded nails carry owner id, anchor, depth 1..3, age, and synchronized render attachment data. The owner UUID is synchronized to clients through `DATA_OWNER_UUID` (`OPTIONAL_LIVING_ENTITY_REFERENCE`); `clientOwnerUuid()` reads it on both sides and feeds the client-only target ESP.

## Bounded lifecycle

- Prepared/launched nail maximum age: 1200 ticks.
- Loaded ordinary embedded nail TTL: 1200 ticks.
- `NailAnchorRegistry` indexes loaded embedded anchors per ServerLevel and owner UUID in insertion order; each entry carries depth 1..3, origin (`LAUNCHED`, `TRAP_CORNER`, or `TRAP_IMPACT`), and `targetId`.
- Maximum loaded embedded anchors per owner per level: 30; the 31st tracked anchor discards the oldest tracked nail.
- `isDeeplyAnchored` is derived, not stored: it is true when at least one live anchor for the owner is attached to the target at depth 3.
- onRemoval and state transitions untrack the entity; server stop clears registry maps.
- Hairpin R queries the owner index instead of scanning level.getAllEntities(); Mega Nail B selects target anchors through `NailAnchorRegistry.anchorsOnTarget`, consumes their marks, and atomically discards the selected anchors at cast t0.

## Depth

Depth 1..3 persists and synchronizes; a hammer hit deepens one nail. Damage multipliers are `NAIL_DEPTH_1_MULTIPLIER = 1.0f`, `NAIL_DEPTH_2_MULTIPLIER = 1.35f`, `NAIL_DEPTH_3_MULTIPLIER = 1.75f`, resolved by depth with depth-1 as the default fallback (VERIFIED — ProjectJjkNobaraProfile). Depth has dedicated transition and level-III VFX.

Trap corner nails remain owned by NailTrapRuntime and use the shorter trap lifetime; a trap-impact anchor is indexed by NailAnchorRegistry as an embedded nail. The nail entity type is currently noSave, so unloaded entities are not durable world storage despite having serialization code.
