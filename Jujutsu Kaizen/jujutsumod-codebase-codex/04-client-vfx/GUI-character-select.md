# GUI — Character Select

← [[00-MOC]] · [[../03-systems/Character-selection]] · [[VFX-core]]

## Entry

| Piece | Source | Status |
|---|---|---|
| Key V | `JujutsuKeybinds.java:19-24` | VERIFIED |
| Screen | `CharacterSelectScreen.java:19-148` | VERIFIED |
| Confirm payload | `CharacterSelectScreen.java:101-105` | VERIFIED |
| UI kit | `client/ui/UiScreen`, `UiButton`, `CharacterCard`, `UiTheme`, `UiRender`, `UiEase` | VERIFIED paths |

## Current layout facts

- Panel is dark gray, not pure black: `UiTheme.SCRIM/PANEL/PANEL_RAISED`.
- Character cards: Nobara + Default.
- Nobara uses orange accent; Default uses dark neutral accent.
- Nobara portrait head uses the skin texture, with a neutral backing panel to avoid the old muddy/brown look.
- A compact kit preview is rendered under the cards when Nobara is selected.

## Nobara kit preview

ProjectJJK ability icons are copied into the jujutsumod namespace:

| Cell | Icon source path in runtime assets | UI source |
|---|---|---|
| Piercing | `textures/gui/abilities/piercing_nail.png` | `CharacterSelectScreen.java:119` |
| Enlarge | `textures/gui/abilities/hairpin_enlargement.png` | `CharacterSelectScreen.java:120` |
| Boom | `textures/gui/abilities/hairpin_explosion.png` | `CharacterSelectScreen.java:121` |
| Resonance | `textures/gui/abilities/resonance.png` | `CharacterSelectScreen.java:122` |

## Performance-sensitive

The UI remains simple: two `CharacterCard` elements, two `UiButton` elements, and one non-interactive ability strip. No animated particle backdrop was added.

## Regression

`ProjectSanityTest.assertExplicitNobaraActionsAreVisible` checks that Enlarge/Boom are visible in UI and control paths.

---
tags: #jujutsumod #gui #verified
