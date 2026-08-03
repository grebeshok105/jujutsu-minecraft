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

GeckoLib 5.2.2 indexes models **only** under `assets/<ns>/geckolib/models/**` and animations **only** under `assets/<ns>/geckolib/animations/**`. The GeckoLib-4 / ProjectJJK-1.21.1 layout (`assets/<ns>/geo/**`, `assets/<ns>/animations/**`) is never loaded — a file can be present in the jar and still be absent from GeckoLib's bake map, which surfaces as `IllegalArgumentException: Unable to find model file` at first third-person render, not at startup. Status: VERIFIED (live player animation rigs are `assets/jujutsumod/geckolib/models/character_skin/{nobara,todo,megumi}.geo.json`; the existing vessel animation JSON remains under `geckolib/animations/**`; `ProjectSanityTest` asserts the rigs contain no visible geometry).

Editing the id string without moving the file does nothing. Move the asset.

### Archived legacy player Geo assets

The former visible player Geo models and their dedicated textures were moved to
`archive/character-player-gecko/`, together with the obsolete visible renderer classes and dispatch
mixin. `manifest.txt` preserves every original path. The archive is outside both Gradle source sets,
so it is not packaged into the jar. Other unrelated `geo/projectjjk/` entity assets remain governed by
their own provenance and runtime contracts.

Do not delete or edit the archived player files casually. Provenance for the ProjectJJK placeholder set
is a separate decision. If you are debugging a player animation, confirm both the `character_skin`
rig path and the live `geckolib/animations` path before anything else.

See docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.
