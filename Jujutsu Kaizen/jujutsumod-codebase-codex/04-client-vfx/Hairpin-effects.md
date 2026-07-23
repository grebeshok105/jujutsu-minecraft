# Hairpin and Combat Client Effects

Status: CURRENT

Server-confirmed actions emit VfxCue objects. JujutsuClientNetworking forwards cues to VfxDirector, which looks up NobaraVfxRecipes and delegates world, HUD, camera, first-person, particle, sound, post-process, and time-channel primitives.

Effects use cue age to reject or seek late playback. Persistent nails are rendered by ProjectJjkNailRenderer; transient compression, snap, burst, residue, camera, and sound beats belong to recipes/channels.

Nobara currently exposes 25 typed VFX ids through NobaraVfxIds.
