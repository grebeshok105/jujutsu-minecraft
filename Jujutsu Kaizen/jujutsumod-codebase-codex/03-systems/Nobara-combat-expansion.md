# Nobara Combat Expansion

Status: CURRENT

Controls:

- R — directed Hairpin chain from the aimed owned nail/target.
- B — mass Hairpin over loaded owned embedded nails.
- Shift+R — Self Resonance through an explicit curse link.
- Shift+B — triangular Nail Trap.
- Hammer left click — contextual horizontal/overhead/nail interaction.
- Nail use/hold — prepare individual nails; hammer launches prepared nails.

Nail Trap constants (VERIFIED — ProjectJjkNobaraProfile): radius 6.0, 3 nails, prism height 3.0, placement range 8.0, lifetime 600 ticks, damage 15.0, interrupt stagger 12 ticks, collapse 6 ticks. After impact the trap embeds one ordinary depth-I nail.

Bound Remnant stores a FLESH / TOKEN / CURSE visual type (`RemnantVisualType`, classified as CURSE when curse-tagged, else FLESH for animals and TOKEN otherwise), with per-type item textures plus a default `resonance_remnant.png` (VERIFIED).

Server systems own targeting, resources, damage, stagger, Black Flash, trap state, remnant progression, and chain scheduling. Client systems own animation and VFX only.
