# Nail Rendering

Status: CURRENT

ProjectJjkNailRenderer renders prepared, flying, embedded, and trap nail states. Embedded entity nails follow synchronized local offset/forward data; block anchors remain fixed to validated block state. Depth 1..3 affects gameplay/render state.

The physical metal model must remain readable without broad aura. Transient particles and camera effects belong to VFX Core, not to a second persistent-effect manager.

Renderer and GeckoLib behavior require in-game smoke testing; compilation alone is insufficient.
