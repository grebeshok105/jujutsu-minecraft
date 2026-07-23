# Rich-Modern GUI font findings (read-only study)

**Source:** user-provided `Rich-Modern.rar` (Fabric client, MC 1.21.11).  
**Scope:** only `rich.util.render.font.*`, MSDF shaders, font atlases, ClickGui text entry points.  
**Not executed:** no gradle, no jar run, no native code. Extracted for reading under `docs/research/rich-modern-gui-ref/` (do not ship).

## Verdict for our Neon Dashboard

Vanilla MC `Font` / TTF / low-res bitmap **cannot** match their UI quality because:

1. MC `FontTexture` forces **`FilterMode.NEAREST`** → any small bitmap looks 144p.
2. TTF FreeType path is limited and still bakes into that nearest atlas.
3. Rich-Modern **does not use** `net.minecraft.client.gui.Font` for GUI text.

They use **MSDF** (Multi-channel Signed Distance Field) + **custom RenderPipeline** + **LINEAR** sampling.

## Architecture (GUI font only)

```
assets/rich/fonts/<name>.json   ← msdf-atlas-gen style metrics + glyph UVs
assets/rich/fonts/<name>.png    ← MSDF atlas (often 2048×2048, size=64)
assets/rich/shaders/core/msdf.{vsh,fsh}

Fonts.REGULAR.draw(text, x, y, size, color)
  → FontRenderer.drawText
    → FontPipeline.drawText (batch quads)
      → MSDF fragment shader (median RGB → smooth edge)
```

### Key classes

| Class | Role |
|---|---|
| `Fonts` | Registry of named fonts (`regular`, `regularnew`, `bold`, icons…) |
| `Font` | Thin API: `draw`, `drawCentered`, `getWidth`, `getHeight(size)` |
| `FontRenderer` | Loads atlases, delegates draw |
| `FontAtlas` | Parses JSON (`atlas`, `metrics`, `glyphs[]`) |
| `Glyph` | UV + advance + offsets |
| `FontPipeline` | Immediate GPU batch, `RenderPipeline`, **LINEAR** sampler |
| `FontInitializer` | Lazy init on client tick when ResourceManager ready |

### MSDF atlas JSON (example: regularnew / test)

```json
{
  "atlas": {
    "type": "msdf",
    "distanceRange": 6,
    "size": 64,
    "width": 2048,
    "height": 2048,
    "yOrigin": "bottom"
  },
  "metrics": {
    "emSize": 1,
    "lineHeight": 1.21,
    "ascender": 0.97,
    "descender": -0.24
  },
  "glyphs": [
    {
      "unicode": 65,
      "advance": 0.68,
      "planeBounds": { "left": ..., "bottom": ..., "right": ..., "top": ... },
      "atlasBounds": { "left": ..., "bottom": ..., "right": ..., "top": ... }
    }
  ]
}
```

This is the **msdf-atlas-gen** format (Chlumsky), not Minecraft `font/*.json` providers.

### Scaling (why it stays sharp)

```java
float scale = size / atlas.getFontSize(); // e.g. size=8, fontSize=64 → scale=0.125
// each glyph drawn as quad; edges reconstructed by SDF in shader
```

Arbitrary `size` in screen space without re-baking. NEAREST MC fonts cannot do this.

### Shader essence (`msdf.fsh`)

```glsl
vec3 msd = texture(Sampler0, texCoord).rgb;
float sd = median(msd.r, msd.g, msd.b);
float screenPxDist = screenPxRange() * (sd - 0.5);
float opacity = clamp(screenPxDist + 0.5, 0.0, 1.0);
fragColor = vec4(charColor.rgb, charColor.a * opacity);
```

Sampler: **`FilterMode.LINEAR`** in `FontPipeline.flush()` (not NEAREST).

### Click GUI

`ClickGui` is a full custom `Screen` with panels/components. Text uses `Fonts.*.draw*(..., size, color)` — not `GuiGraphics.drawString`.  
(Most call sites go through component renderers; size is explicit float, often ~6–12.)

## What we should build for jujutsumod

**Do not** keep fighting vanilla font providers for neon UI.

Port a **minimal MSDF text layer** for dashboard only:

1. Generate atlas offline with [msdf-atlas-gen](https://github.com/Chlumsky/msdf-atlas-gen) from Inter/Segoe (or reuse a clean free face).
2. Assets: `assets/jujutsumod/fonts/neon.{json,png}` + `shaders/core/msdf.{vsh,fsh}`.
3. Client-only: `MsdfFont`, `MsdfAtlas`, `MsdfPipeline` (mirror Rich structure, our package, Mojmap, our SDF-era RenderPipeline style).
4. `NeonFonts.draw*` → MSDF pipeline at size ~9–11 GUI px.
5. Leave vanilla `Font` for non-dashboard (chat, etc.).

## Legal / hygiene

- Study only; **do not copy** Rich-Modern sources into product code.
- Do not ship the extracted ref tree in releases.
- Prefer generating our own atlas from an OFL font (Inter) if redistributing.

## Local extract path (read-only)

`docs/research/rich-modern-gui-ref/Rich-Modern/...`  
Safe to delete after implementation.
