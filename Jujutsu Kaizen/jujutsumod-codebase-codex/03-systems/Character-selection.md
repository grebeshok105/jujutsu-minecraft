# Character Selection

Status: CURRENT

JujutsuCharacter currently contains NONE, NOBARA, TODO, and MEGUMI (`src/main/java/jujutsu/mod/character/JujutsuCharacter.java:5-9`). N opens ClickGui; CharacterRosterPanel previews a vessel and sends SelectCharacterPayload on Confirm. Roster labels use language keys.

Server flow:

1. JujutsuNetworking receives the bounded character id on the server thread.
2. CharacterSelectionManager loads CharacterPlayerState from JujutsuAttachments.CHARACTER_STATE.
3. The departing vessel's `onDeselected` hook runs before the new selection is stored, so it still sees itself selected — Todo's mark and pending-swap cleanup lives there.
4. The selected id is persisted and copied on death. The starter claim is recorded for every vessel — "has been this vessel at least once" is a fact about the player — and is currently read by nothing (see E12 in docs/KNOWN_ISSUES.md).
5. CharacterCombatModifiers asks every definition to remove its own attribute modifiers, then the selected one to add its own; the ids and numbers live with the vessel that owns them.
6. The arriving vessel's `onSelected` hook runs. Nobara's restores her starter loadout on **every** selection, deliberately: it is idempotent — it fills only a missing hammer, doll or nails (`src/main/java/jujutsu/mod/character/nobara/NobaraDefinition.java:56-59`) — so re-selecting her replaces a kit lost to death or a switch without duplicating held ones. Todo and Megumi define no `onSelected` and grant no starter items.
7. CharacterSelectionSyncPayload is broadcast for rendering/UI.
8. On reconnect, syncOnJoin restores the persisted state to the joining client and observers.

Per-vessel select/deselect hooks (defaults are no-ops in `CharacterDefinition`):

- NONE: no hooks — casts nothing, owns nothing.
- NOBARA: `onSelected` restores the starter kit idempotently (`src/main/java/jujutsu/mod/character/nobara/NobaraDefinition.java:56-59`); no `onDeselected`.
- TODO: no `onSelected` (no starter items); `onDeselected` drops pair selection, thrown stone, and momentum state via `TodoStateLifecycle.dropEverything` (`src/main/java/jujutsu/mod/character/todo/TodoDefinition.java:74-76`).
- MEGUMI: no `onSelected`; `onDeselected` tears down summons, shadow trap, shadow drop, and shadow move state (`src/main/java/jujutsu/mod/character/megumi/MegumiDefinition.java:44-50`).

Hook contracts are owned by [Vessel definitions](../02-architecture/Vessel-definitions.md). Selecting None does not erase starter-claim history. Disconnect removes remote rendering state from observers but does not delete the persisted selection.
