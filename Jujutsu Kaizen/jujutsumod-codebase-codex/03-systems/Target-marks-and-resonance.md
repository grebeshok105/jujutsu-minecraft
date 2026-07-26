# Target Marks

Status: CURRENT

This note owns target marks. Resonance behaviour, the ritual, and Resonant Momentum are owned by [Straw Doll Resonance](Straw-Doll-resonance.md) — do not restate them here.

Accepted ordinary nail hits open embed windows, create concrete embedded nail anchors, apply/refresh target marks, and advance remnant progress. Marks are owner-aware and expire after their configured duration.

Mark visuals use vanilla Minecraft glowing (`MobEffects.GLOWING`) plus the project team-colour path — not custom world-shell geometry. An earlier implementation drew lightning ribbons around the target AABB in the world renderer; that was replaced because the engine outline is what "glow" means, it tracks the entity mesh instead of a hand-computed partial-tick AABB, it syncs like any vanilla effect in multiplayer, and it costs one outline pass instead of per-frame vertex work. Status: VERIFIED.

Hairpin resolves concrete nails rather than synthesizing damage from an aggregate mark count. Persistent nail visuals stay on the nail entity renderer; transient release/impact feedback stays in VFX Core.
