# Uncertainties

← [[00-MOC]] · [[Citation-standard]]

## Structural

| Topic | Status | Notes |
|---|---|---|
| Checkout lag vs full kit | VERIFIED | cite cinematic worktree as source of truth |
| Which branch is release | UNKNOWN product | document cinematic worktree until merged/released |

## Startup smoke completed

`gradlew.bat runClient --no-daemon` reached `JujutsuMod initialized`, LWJGL/OpenAL initialization, resource reload, and atlas creation on 2026-07-10. `run/logs/latest.log` contained no fatal/error before the intentional terminal stop. This proves startup only.

## Gameplay scenarios not performed in this pass

| Topic | Status | Verify via |
|---|---|---|
| Full combat loop feel after Straw Doll expansion | UNKNOWN | manual nails/hammer/R/B/shift loop |
| Eight-nail preparation, entity/block/runtime anchor unload/rebind, and terminal removal feel | UNKNOWN | manual world pass with chunk unload, target death, block replacement, ItemEntity/vehicle/runtime resolver |
| Black Flash early/on-time/late feel and focus persistence after relog | UNKNOWN | manual timing pass and rejoin |
| Hammer context routing and vanilla double-hit suppression | UNKNOWN | prepared launch, embedded drive, horizontal/overhead swing pass |
| CurseLink one/multiple selection, stale selection rejection, and self-resonance interruption | UNKNOWN | dev links plus multiplayer/manual interaction pass |
| Multiplayer mark/glow sync | UNKNOWN | 2 clients, Glowing/team cleanup |
| Hammer/launch, Straw Doll acquisition/ritual, and Enlarge/Boom VFX composition | UNKNOWN | manual in-game observation |
| Vanilla blur availability/comfort in all heavy scenes | UNKNOWN | manual scene pass across graphics settings; confirm shaderless fallback remains readable |
| Death/despawn anchor fallback in a live scene | UNKNOWN | remove/kill anchor during an active cue |
| Reduced/minimal particle setting readability | UNKNOWN | repeat scenes with DECREASED/MINIMAL particles |
| Character select perf | INFERRED risk | open V screen, watch FPS |
| No-falloff SFX comfort | UNKNOWN | ear test |
| Real nail aura visual after migration to entity renderer | UNKNOWN | in-game view of prepared/flying/embedded nails |

Manual and two-client scenarios were not performed because the user explicitly prohibited Computer Use/UI automation for this task.

## Resolved / no longer uncertain

| Topic | Status | Source |
|---|---|---|
| ProjectJJK nail/hammer behavior remains default; original Straw Doll/remnant are registered separately | VERIFIED | `JujutsuItems.java:14-19` |
| Mark-only remote Resonance is still active | RESOLVED false | removed; [[../03-systems/Straw-Doll-resonance]] |
| Legacy Hairpin runtime still in tree | RESOLVED false | removed; guard `ProjectSanityTest.java:159-188` |
| Old post-shader bind active | RESOLVED false | old Hairpin post-shaders removed; guard `ProjectSanityTest.java:173-174` |
| No cursed energy in profile | VERIFIED | no CE constants in `ProjectJjkNobaraProfile` |
| Combat-expansion structural contracts | VERIFIED | `NailAnchorTest`, `BlackFlashWindowTest`, `CurseLinkRegistryTest`, `ProjectJjkNobaraProfileTest`, and `ProjectSanityTest` |

---
tags: #jujutsumod #uncertainties #verified
