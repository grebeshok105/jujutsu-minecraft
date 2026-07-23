# Character Selection

Status: CURRENT

JujutsuCharacter currently contains None and Nobara. N opens ClickGui; CharacterRosterPanel previews a vessel and sends SelectCharacterPayload on Confirm.

Server flow:

1. JujutsuNetworking receives the bounded character id on the server thread.
2. CharacterSelectionManager loads CharacterPlayerState from JujutsuAttachments.CHARACTER_STATE.
3. The selected id is persisted and copied on death.
4. The Nobara starter loadout is granted only when Nobara has not previously been claimed by that player.
5. CharacterSelectionSyncPayload is broadcast for rendering/UI.
6. On reconnect, syncOnJoin restores the persisted state to the joining client and observers.

Selecting None does not erase starter-claim history. Disconnect removes remote rendering state from observers but does not delete the persisted selection.
