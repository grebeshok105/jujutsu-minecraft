# GUI — Character Select

Status: CURRENT

- Key: N.
- Screen: jujutsu.mod.client.rich.screens.clickgui.ClickGui.
- Live category: Characters.
- Inert rows: Soon placeholders.
- Vessel panel: CharacterRosterPanel.
- Current cards: Nobara, Todo, and None — three entries in `CharacterRosterPanel.CARDS`, in that order (VERIFIED).
- Nobara and Todo cards carry a skin portrait (`textures/entity/character/nobara.png`, `.../todo.png`); None is the vanilla-play card.
- Confirm sends SelectCharacterPayload and receives server echo through CharacterSelectionSyncPayload.
- Theme: ClickGuiTheme, orange/slate character accent.

The Neon Dashboard, Key V, CharacterSelectScreen, and ModernMenu fallback are retired.
