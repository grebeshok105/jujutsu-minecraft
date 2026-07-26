# GUI — Character Select

Status: CURRENT

- Key: N.
- Screen: jujutsu.mod.client.rich.screens.clickgui.ClickGui.
- Live category: Characters.
- Inert rows: Soon placeholders.
- Vessel panel: CharacterRosterPanel.
- Current cards: Nobara, Todo, and None, in that order — drawn from `JujutsuCharacterClients.inRosterOrder()`, so the panel holds no vessel's data and a new vessel appears with no edit here (VERIFIED — `CharacterRosterPanel.CARDS` is initialized from the registry). Each card is the vessel's own `rosterEntry()`: nameKey/roleKey/subtitleKey, portrait, and an input strip listing what its router actually answers.
- Nobara and Todo cards carry a skin portrait read from each definition's `playerSkin()` (`textures/entity/character/nobara.png`, `.../todo.png`); None is the vanilla-play card.
- Confirm sends SelectCharacterPayload and receives server echo through CharacterSelectionSyncPayload.
- Theme: ClickGuiTheme owns only the easing; the accent and warmth it eases toward come from each vessel's `accent()`/`warmth()`. See [Vessel definitions](../02-architecture/Vessel-definitions.md).

The Neon Dashboard, Key V, CharacterSelectScreen, and ModernMenu fallback are retired.
