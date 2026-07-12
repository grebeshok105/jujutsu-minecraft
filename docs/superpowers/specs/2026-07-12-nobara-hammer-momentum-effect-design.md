# Nobara Hammer and Momentum Effect Design

## Goal

Replace the oversized sledgehammer silhouette with a compact silver nail-driving claw hammer, and remove the custom Resonant Momentum HUD in favor of Minecraft's native beneficial-effect list.

## Hammer

- Keep the existing item ids and gameplay behavior.
- Rebuild the Java item model as a compact claw hammer: narrow dark-brown handle, small silver head, round striking face, short split claw, restrained cold highlights and darker steel undersides.
- The silhouette must read as a one-handed nail hammer, not a mallet or sledgehammer.
- Reduce first-person and third-person display scale while keeping the grip aligned with existing held-item animation attachments.
- Preserve a Blockbench source project and export the runtime Java model JSON and texture assets.

## Resonant Momentum

- Delete `ResonantMomentumHud` and its client registration/state/payload path if no longer needed.
- Register `jujutsumod:resonant_momentum` as a beneficial custom `MobEffect` with its own 18x18 effect icon.
- Successful Straw Doll Resonance applies the effect for 1200 ticks. Reapplication refreshes instead of stacking.
- The effect supplies no vanilla attributes. Existing explicit Nobara preparation, launch, hammer, and R/B multipliers check for the effect on the server.
- Minecraft owns the right-side status icon, inventory entry, and countdown.

## Acceptance

- No custom teal rectangle is rendered.
- Momentum appears in the native effect list and expires/refreshes correctly.
- Hammer reads as compact silver claw hammer in GUI, first person, third person, and ground transforms.
- Automated checks/build pass and the installed runtime JAR hash matches the built JAR.

