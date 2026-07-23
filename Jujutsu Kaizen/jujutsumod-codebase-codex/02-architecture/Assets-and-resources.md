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

See docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.
