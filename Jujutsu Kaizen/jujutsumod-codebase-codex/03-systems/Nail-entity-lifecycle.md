# Nail Entity Lifecycle

Status: CURRENT

ProjectJjkNailEntity moves through prepared, launched, and embedded states. Anchors may target an entity, block, or registered runtime object. Ordinary embedded nails carry owner id, anchor, depth 1..3, age, and synchronized render attachment data.

## Bounded lifecycle

- Prepared/launched nail maximum age: 1200 ticks.
- Loaded ordinary embedded nail TTL: 1200 ticks.
- Maximum loaded ordinary embedded nails per owner per level: 30.
- EmbeddedNailRegistry indexes loaded non-trap nails by ServerLevel and owner UUID in insertion order.
- The 31st nail discards the oldest tracked nail.
- onRemoval and state transitions untrack the entity; server stop clears registry maps.
- Hairpin R/B query the owner index instead of scanning level.getAllEntities().

Trap nails remain owned by NailTrapRuntime and use the shorter trap lifetime. The nail entity type is currently noSave, so unloaded entities are not durable world storage despite having serialization code.
