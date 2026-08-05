# Nobara Combat Expansion

Status: CURRENT

Controls:

- R — directed Hairpin chain from the aimed owned nail/target.
- B — Mega Nail: merges every embedded nail on the aimed target into one delayed piercing strike (`ProjectJjkMegaNailRuntime`).
- Shift+R — Self Resonance through an explicit curse link.
- Shift+B — triangular Nail Trap.
- Hammer left click — contextual horizontal/overhead/nail interaction.
- Nail use/hold — prepare individual nails; hammer launches prepared nails.

Nail Trap constants (VERIFIED — ProjectJjkNobaraProfile): radius 6.0, 3 nails, prism height 3.0, placement range 8.0, lifetime 600 ticks, damage 15.0, interrupt stagger 12 ticks, collapse 6 ticks. After impact the trap embeds one ordinary depth-I nail.

Mega Nail constants (VERIFIED — ProjectJjkNobaraProfile): per-nail damage = Enlarge per-nail damage (4.0) × depth weight, cap 42.0; knockback base = Hairpin knockback, +Hairpin explosion knockback per nail, cap 3.0; strike delay 6 ticks; retry timeout 40 ticks. The cast atomically discards the selected nails (ENLARGE consume flash per nail) and consumes the target's marks; the strike resolves by entity id, then UUID, and passes through the target along the frozen cast direction. Nail selection requires `isEmbedded()`, caster ownership, and `nail.anchor().stableId()` equal to the target UUID.

Target ESP (client-only): `NobaraEspState` aggregates synced nail data every 2 client ticks into per-target groups (`aggregate(List<NailView>)` — pure, JUnit-covered); only embedded, locally-owned nails on living targets count. The lowest nail entity id per target renders the billboard badge (name + rank + nail pips) via `ProjectJjkNailRenderer`; `NobaraEspRanks.rankKey` classifies players by roster grade key and mobs by max-health thresholds (≥100 special grade, ≥40 rank1, ≥20 rank2, else rank3) and returns `esp.jujutsumod.rank.*` localization keys. ESP needs `ProjectJjkNailEntity.clientOwnerUuid()`, synced through `DATA_OWNER_UUID`.

Bound Remnant stores a FLESH / TOKEN / CURSE visual type (`RemnantVisualType`, classified as CURSE when curse-tagged, else FLESH for animals and TOKEN otherwise), with per-type item textures plus a default `resonance_remnant.png` (VERIFIED).

Server systems own targeting, resources, damage, stagger, Black Flash, trap state, remnant progression, and chain scheduling. Client systems own animation and VFX only.
