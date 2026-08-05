# Nail Entity Lifecycle

Status: CURRENT

ProjectJjkNailEntity moves through prepared, launched, and embedded states. Anchors may target an entity, block, or registered runtime object. Ordinary embedded nails carry owner id, anchor, depth 1..3, age, and synchronized render attachment data. The owner UUID is synchronized to clients through `DATA_OWNER_UUID` (`OPTIONAL_LIVING_ENTITY_REFERENCE`); `clientOwnerUuid()` reads it on both sides and feeds the client-only target ESP.

## Bounded lifecycle

- Prepared/launched nail maximum age: 1200 ticks.
- Loaded ordinary embedded nail TTL: 1200 ticks.
- Maximum loaded ordinary embedded nails per owner per level: 30.
- EmbeddedNailRegistry indexes loaded non-trap nails by ServerLevel and owner UUID in insertion order.
- The 31st nail discards the oldest tracked nail.
- onRemoval and state transitions untrack the entity; server stop clears registry maps.
- Hairpin R queries the owner index instead of scanning level.getAllEntities(); the Mega Nail B selects nails by target bounding box + `anchor().stableId()` equality and atomically discards them at cast.

## Depth

Depth 1..3 persists and synchronizes; a hammer hit deepens one nail. Damage multipliers are `NAIL_DEPTH_1_MULTIPLIER = 1.0f`, `NAIL_DEPTH_2_MULTIPLIER = 1.35f`, `NAIL_DEPTH_3_MULTIPLIER = 1.75f`, resolved by depth with depth-1 as the default fallback (VERIFIED — ProjectJjkNobaraProfile). Depth has dedicated transition and level-III VFX.

Trap nails remain owned by NailTrapRuntime and use the shorter trap lifetime. The nail entity type is currently noSave, so unloaded entities are not durable world storage despite having serialization code.
