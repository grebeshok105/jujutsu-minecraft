# GUI — Character Select

← [[00-MOC]] · [[03-systems/Character-selection]]

## Entry

| Piece | Source | Status |
|---|---|---|
| Key V | `JujutsuKeybinds` · lang `key.jujutsumod.character_select` | VERIFIED |
| Screen | `CharacterSelectScreen` | VERIFIED |
| UI kit | `client/ui/UiScreen`, `UiButton`, `CharacterCard`, `UiTheme`, `UiRender`, `UiEase` | VERIFIED paths |

## Layout facts (from earlier read of screen)

- Custom hand-drawn kit, no vanilla widgets primary
- Cards: Nobara + Default
- Confirm sends `SelectCharacterPayload`

**Status:** VERIFIED structure (prior source read); re-open file before UI refactors.

## Performance-sensitive

| Area | Risk | Status |
|---|---|---|
| Animated backdrop / particle motif | CPU/GPU on open | INFERRED |
| Ease animations every frame | cost scales with elements | INFERRED |

## Known risks

- Double blur history fixed on feature branch (git history) — don't reintroduce
- Selection must stay C2S authoritative

---
tags: #jujutsumod #gui
