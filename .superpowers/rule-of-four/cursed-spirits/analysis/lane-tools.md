# Lane tool schemas (captured live, 2026-09-12)

## entity_summon [mod] (no extra props)
Summons an entity at a position with optional SNBT.
  - dimension (REQ) string: Dimension identifier
  - entity_type (REQ) string: Entity type id (e.g. minecraft:zombie)
  - position (REQ) object: Spawn position
  - nbt (opt) string: SNBT applied at spawn time

## entity_query [mod] (no extra props)
Returns entities matching a vanilla selector. Simple selectors (@e, @a) are enumerated directly; complex selectors are limited in v0.1.0.
  - dimension (REQ) string: Dimension identifier
  - selector (REQ) string: Vanilla selector, e.g. @e, @a
  - limit (opt) integer: Max results

## entity_get [mod] (no extra props)
Looks up an entity by UUID and returns its current state.
  - uuid (REQ) string: Entity UUID

## entity_get_nbt [mod] (no extra props)
Returns the entity's full NBT as SNBT.
  - uuid (REQ) string: Entity UUID

## entity_set_nbt [mod] (no extra props)
Merges SNBT into the entity (vanilla /data merge entity).
  - uuid (REQ) string: Entity UUID
  - nbt (REQ) string: SNBT to merge

## entity_teleport [mod] (no extra props)
Teleports an entity, optionally facing a point.
  - uuid (REQ) string: Entity UUID
  - dimension (REQ) string: Destination dimension
  - position (REQ) object: Destination position
  - facing (opt) object: World position to face (optional)

## entity_apply_effect [mod] (no extra props)
Applies a status effect to an entity.
  - uuid (REQ) string: Entity UUID
  - effect (REQ) string: Effect id (e.g. minecraft:speed)
  - duration_ticks (REQ) integer: Duration in ticks
  - amplifier (opt) integer: Amplifier (0..255, default 0)
  - ambient (opt) boolean: Ambient (default false)
  - show_particles (opt) boolean: Show particles (default true)
  - show_icon (opt) boolean: Show icon (default true)

## entity_apply_damage [mod] (no extra props)
Applies damage to an entity from a named source.
  - uuid (REQ) string: Entity UUID
  - amount (REQ) number: Damage amount
  - damage_type (opt) string: Damage type id (default minecraft:generic)

## entity_kill [mod] (no extra props)
Kills an entity via the standard damage pipeline.
  - uuid (REQ) string: Entity UUID

## command_execute [mod] (no extra props)
Runs a slash command as the console source. Captures /tellraw and feedback messages into the output array.
  - command (REQ) string: Command (with or without leading slash)

## jujutsu_ticks_wait [mod] (no extra props)
Waits N server ticks asynchronously without blocking the HTTP thread
  - ticks (REQ) integer: Number of server ticks to wait (1-1200; with player_uuid, the cap for the cooldown wait)
  - player_uuid (opt) string: Wait until this player's PRIMARY cooldown reaches 0 (capped at ticks)

## jujutsu_state_get [mod] (no extra props)
Reads the current combat and transient state of one online player: vessel, position, stagger, cooldowns, effect flags, Todo pair selection and stone, Megumi pack/trap/move/drop, and Nobara embedded nails (current dimension only) and marks.
  - player_uuid (REQ) string: Player UUID

## jujutsu_ability_invoke [mod] (no extra props)
Invokes one ability slot for the target player.
  - player_uuid (REQ) string: Player UUID
  - slot (REQ) string: Ability slot name (case-insensitive): PRIMARY, PRIMARY_SNEAK, SECONDARY, SECONDARY_SNEAK, ATTACK_CONTEXT, USE_CONTEXT, S
  - expect_vessel (opt) string: Required vessel id; a mismatch refuses without invoking
  - notify (opt) boolean: Show the in-game action message; default true

## jujutsu_vessel_select [mod] (no extra props)
Selects a vessel (character) for the target player.
  - player_uuid (REQ) string: Player UUID
  - vessel_id (REQ) string: Canonical vessel id, e.g. megumi (case-insensitive)

## jujutsu_fixture_reset [mod] (no extra props)
Clears runtime combat state for one online player: cooldowns, stagger, Todo transient state, Megumi summons/traps/moves/drops, Nobara nails/traps/marks/resonance, and shared black-flash tags and effects. Does not touch vessel selection, starter claims, or inventory.
  - player_uuid (REQ) string: Player UUID

## jujutsu_fixture_list [mod] (no extra props)
Lists available fixture scenarios and their cleanup guarantees

## server_get_status [mod] (no extra props)
Returns Minecraft server status — version, uptime, TPS/MSPT, online player count, loaded dimensions, mod version, registered tool count.

## level_get_time [mod] (no extra props)
Returns the current time of day (in ticks) for a dimension.
  - dimension (REQ) string: Dimension identifier

## level_set_time [mod] (no extra props)
Sets the time of day (in ticks) for a dimension. 0..23999 covers one Minecraft day.
  - dimension (REQ) string: Dimension identifier
  - time (REQ) integer: Time of day in ticks

## level_get_difficulty [mod] (no extra props)
Returns the world-wide difficulty.

