# Rich ClickGUI visual target (locked reference)

Source screenshot: `C:\Users\KOMP1\Pictures\Снимок экрана 2026-07-21 144110.png`  
Layout source: `rich.screens.clickgui.ClickGui` (FIXED_GUI_SCALE=2)

## Layout (logical px @ scale 2)

| Region | x | y | w | h |
|--------|---|---|---|---|
| Shell | centered | centered | **400** | **270** |
| Sidebar | 0 | 0 | **92** | 270 |
| Header band | 92 | 0 | 308 | **38** |
| Module list | **92** | **38** | **120** | H-46 |
| Settings | **218** | **38** | **172** | H-46 |

## Palette (sampled from screenshot)

| Token | Hex | Notes |
|-------|-----|-------|
| Panel | `#161614` | main shell fill |
| Sidebar | `#171815` | almost same as panel |
| Module row | `#1B1B19` | elevated card |
| Selected row | `#212121` | slightly lighter |
| Search / chips | `#1B1B1B` | |
| Avatar chip | `#212121` | |
| Text primary | `#E4E4E4` | soft white |
| Text dim | `#9A9A9A` | |
| Text muted | `#6E6E6E` | section labels |
| Online | `#4ADE80` | only green in UI |
| Scrim | black ~125 alpha | ClickGui `dimAlpha` |

**No orange/amber chrome.** Accent is light gray; confirm may use warm desaturated beige.

## Visual rules

1. Large outer radius (~18), continuous rounded shell (no hard sidebar corners).
2. Module rows: compact ~28px height, name left, bind letter right, optional + / star / gear.
3. Selected module = fill lift only (+ subtle border), not thick glow.
4. Selected category = soft pill + thin left bar (white/gray, not neon).
5. Settings rows: icon + title + subtitle; value right-aligned (dropdown / slider / toggle).
6. Top: avatar chip left, category title center-left, search pill right.
7. Section labels uppercase muted (“Основные”, “Другие”).
8. Typography small (7–11 MSDF), high density, low contrast hierarchy.
9. Minimal glow; soft single-pixel borders `~0x18FFFFFF`.
10. Content for jujutsu maps into this shell (Character list + settings detail + Confirm).

## Mapping our data

| Rich | Ours |
|------|------|
| Category sidebar | Character / Combat / Visuals / Misc |
| Module list | Nobara / None (Character); abilities (Combat) |
| Settings panel | vessel detail + ability key rows + Confirm/Cancel |
| Search | visual pill (filter later) |
| Avatar | player name + online + active vessel |

## Status

- Baseline implementation: modern menu on N, commits through `7befbeb`.
- This file is the **visual contract** for follow-up polish commits.
