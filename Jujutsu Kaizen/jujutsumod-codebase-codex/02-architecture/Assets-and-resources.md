# Assets and Resources

Status: CURRENT

Runtime common resources live under src/main/resources; client-only shaders, MSDF atlases, and Rich UI assets live under src/client/resources. processResources excludes source-assets/** but packages normal runtime assets.

Key rules:

- User-visible strings belong in lang files.
- Sounds are OGG Vorbis.
- Transient combat visuals use VFX Core; persistent entity visuals stay with entity/state renderers.
- ProjectJJK-named assets are temporary placeholders used with author permission and are not CC0.
- neon.ttf is Segoe UI Semilight and must be removed/replaced before public redistribution unless separately licensed.
- Rich-Modern-derived content needs a provenance decision before public release.

## GeckoLib asset layout — load-bearing

GeckoLib 5.2.2 indexes models **only** under `assets/<ns>/geckolib/models/**` and animations **only** under `assets/<ns>/geckolib/animations/**`. The GeckoLib-4 / ProjectJJK-1.21.1 layout (`assets/<ns>/geo/**`, `assets/<ns>/animations/**`) is never loaded — a file can be present in the jar and still be absent from GeckoLib's bake map, which surfaces as `IllegalArgumentException: Unable to find model file` at first third-person render, not at startup. Status: VERIFIED (live layout: `assets/jujutsumod/geckolib/models/{projectjjk/nobara_kugisaki,straw_doll,todo/todo_aoi}.geo.json` and three matching `geckolib/animations/**` files; `ProjectSanityTest` asserts no live model id contains `geo/projectjjk`).

Editing the id string without moving the file does nothing. Move the asset.

### Trap: the legacy `geo/` tree is still in the repo

18 unused GeckoLib-4 files still sit under `src/main/resources/assets/jujutsumod/geo/projectjjk/`, including `nobara_kugisaki.geo.json`, which is a **duplicate name** of the live model under `geckolib/models/projectjjk/` (VERIFIED — no Java source references the `geo/` path; the only mentions are negative assertions in `ProjectSanityTest`). They are packaged into the jar and loaded by nothing.

Do not delete them and do not edit them. Provenance for the ProjectJJK placeholder set is a separate decision, and the duplicate is only a hazard if someone edits the wrong `nobara_kugisaki.geo.json` and concludes GeckoLib is broken. If you are debugging a model, confirm the path prefix before anything else.

See docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.
