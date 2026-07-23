# Minecraft 1.21 Custom Font — Final Research

## Hard facts from 1.21.8 bytecode

1. **`FontTexture` always sets `FilterMode.NEAREST`.**  
   Soft bitmap edges only work via **alpha**, not linear filtering. Low-res bitmaps (12–14px) always look 144p.

2. **TTF path:** `TrueTypeGlyphProviderDefinition.load` does  
   `location.withPrefix("font/")` then opens the stream.  
   JSON `"file": "ns:neon.ttf"` → `assets/ns/font/neon.ttf`.  
   JSON `"file": "ns:font/neon.ttf"` → **double** `font/font/` → FileNotFound → tofu.

3. **TTF raster size:** `FT_Set_Pixel_Sizes(round(size * oversample))`.  
   Display size ≈ `size`. Smoothness comes from **high oversample** (8–16+).

4. **Bitmap scale:** `scale = height / cellHeight`.  
   If `height != cellH`, glyphs are resampled → squash/blur.  
   Advance = `round(inkWidth * scale) + 1`. Glyphs must be **left-aligned**.

5. **Vanilla UI never uses TTF** for Mojangles — but TTF **is** the correct path for *smooth* custom UI when path + oversample are right (wiki + tryashtar/minecraft-ttf tips).

## Why our previous attempts failed

| Attempt | Failure mode |
|---|---|
| TTF OpenSans/Segoe, wrong path | `font/font/*.ttf` → reject → tofu |
| TTF mixed with plain String draws | Mojangles + Segoe = uneven |
| Bitmap 32 cell / height 10–11 | scale ≪ 1 → squashed, muddy |
| Bitmap centered glyphs | left pad baked into advance → `N o b a r a` |
| Bitmap 12–14 1:1 + alpha crush | NEAREST + few texels + killed AA = 144p |

## Chosen solution (reset)

- **Provider:** TTF only (Segoe UI Semilight as `neon.ttf`)
- **size:** `9.0` (near vanilla height)
- **oversample:** `16.0` (FreeType face ≈ 144px → smooth AA)
- **shift:** `[0, 0.5]`
- **file:** `jujutsumod:neon.ttf` (no double font/)
- **Draw path:** only `NeonFonts.*` (no plain String)

## Verify after install

In `latest.log` must **NOT** appear:
- `Failed to load builder (jujutsumod:neon)`
- `font/font/neon.ttf`
- mass `Couldn't find glyph for character`

Should appear: normal resource reload, smooth Segoe-like UI text.
