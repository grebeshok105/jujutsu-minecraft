# Nobara Overview

Status: CURRENT

Fantasy: battlefield setup through physical nails, hammer timing, remote Hairpin detonation, and a remnant/doll Resonance ritual.

Core systems:

- NobaraDefinition / NobaraClientDefinition — her two halves of the vessel seam: the server definition routes casts, installs her runtimes' hooks, and restores the starter kit idempotently on every selection; the client definition owns her renderer, skin, roster card, accent, and VFX recipe registration. See [Vessel definitions](../02-architecture/Vessel-definitions.md).
- NobaraAbilityRouter — her slot map: what each shared input position casts, plus her own stagger check and single fallback message.
- ProjectJjkNobaraRuntime — nail preparation, launch, impact, and common feedback.
- ProjectJjkRitualRuntime — marks, directed/mass Hairpin, chains, and detonation.
- NobaraHammerCombatRuntime — contextual melee and embedded-nail drive.
- ProjectJjkStrawDollRuntime — remnant-bound Resonance ritual.
- SelfResonanceRuntime — explicit curse-link strike.
- NailTrapRuntime — placement, arming, collapse, and impact.
- EmbeddedNailRegistry — bounded loaded-nail owner index.
- NobaraVfxIds/NobaraVfxRecipes — typed presentation contract.

The current kit has no universal cursed-energy bar. Balance constants are centralized in ProjectJjkNobaraProfile.
