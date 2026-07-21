# Minecraft 1.21 Custom Font Research

Date: 2026-07-21  
Goal: readable, homogeneous custom UI font for Neon Dashboard (not default Mojangles).

## Sources

- Minecraft Wiki — Font providers (bitmap / ttf / unihex / space / reference)
- MC 1.21.8 FreeType path: `TrueTypeGlyphProviderDefinition.load` applies `ResourceLocation.withPrefix("font/")`
- Instance log failures: `jujutsumod:font/font/neon.ttf` when JSON used `file: jujutsumod:font/neon.ttf`
- Vanilla default font: **bitmap + unifont**, never TTF

## Approaches compared

| Approach | Quality in MC GUI | Complexity | Verdict |
|---|---|---|---|
| TTF provider (runtime FreeType) | Muddy AA, no kerning, uneven | Low | **Reject for UI** |
| Bitmap provider (pre-baked PNG) | Stable, recolorable, pack-standard | Medium | **Use this** |
| Unihex | Pixel-grid Unicode | High for Latin UI | Overkill |
| Default Mojangles | Homogeneous, pixel | Zero | Fallback only |

## Correct paths

| Asset | Path |
|---|---|
| Font definition | `assets/<ns>/font/<name>.json` → font id `<ns>:<name>` |
| TTF file (if used) | `assets/<ns>/font/<file>.ttf` with JSON `"file": "<ns>:<file>.ttf"` (MC adds `font/`) |
| Bitmap texture | `assets/<ns>/textures/font/<file>.png` with JSON `"file": "<ns>:font/<file>.png"` |

## Implementation chosen

1. Bake **Segoe UI Semilight** offline via `tools/generate_neon_font.py` (Pillow).
2. Output `textures/font/neon.png` (32px cells, height=16, ascent=13).
3. `font/neon.json` = space + bitmap + unifont reference (no TTF, no default pixel mix).
4. All dashboard text via `NeonFonts` helpers (`Style.withFont(jujutsumod:neon)`).

## Rebuild font

```bat
python tools/generate_neon_font.py
gradlew.bat build --no-daemon -x test
```

## What not to do

- Do not ship runtime TTF for primary UI text.
- Do not mix plain `drawString(String)` (default font) with styled Components.
- Do not put `font/` twice in the TTF file id.
- Do not leave uppercase non-glyph files in `assets/.../font/` (LICENSE.txt → invalid path warning).
