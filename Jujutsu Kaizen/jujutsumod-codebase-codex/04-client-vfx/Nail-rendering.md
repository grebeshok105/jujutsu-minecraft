# Nail Rendering

Status: CURRENT

ProjectJjkNailRenderer renders prepared, flying, embedded, and trap nail states. Embedded entity nails follow synchronized local offset/forward data; block anchors remain fixed to validated block state. Depth 1..3 affects gameplay/render state.

Aura ownership is state-dependent and deliberate: non-embedded nails (prepared and flying) draw the blue force-field envelope, embedded nails draw none, and the envelope is local to the entity render pose. It moved here from a removed fake-flight renderer, so the real nail entity renderer is now its only owner (VERIFIED — ProjectJjkNailRenderer, `state.embedded` branches; render scale 0.58 embedded / 0.7 launched / 0.62 prepared).

Embedded nails are render-attached: the renderer resolves the host entity, computes the anchor from the synchronized local offset rotated by body yaw, and translates by the difference from the entity's own position. That is what keeps a nail from visibly chasing a sprinting target one tick behind.

The physical metal model must remain readable without broad aura. Transient particles and camera effects belong to VFX Core, not to a second persistent-effect manager.

Renderer and GeckoLib behavior require in-game smoke testing; compilation alone is insufficient.
