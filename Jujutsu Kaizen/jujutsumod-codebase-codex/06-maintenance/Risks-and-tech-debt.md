# Risks & Tech Debt

← [[00-MOC]] · [[../04-client-vfx/VFX-core]]

Only code-backed or process-backed risks.

| ID | Risk | Priority | Source | Status |
|---|---|---|---|---|
| R1 | Checkout without cinematic worktree misleads agents (missing current Nobara) | **P0** | MOC SoT note | VERIFIED |
| R2 | In-memory character selection is lost on restart | **P1** | `CharacterSelectionManager` map | VERIFIED |
| R3 | VFX broadcast radius or `canSend` can omit a client’s local composition | **P1** | `JujutsuNetworking.java:38-59` | VERIFIED |
| R4 | Existing camera/skin/renderer mixins increase MC-bump cost | **P2** | client mixins json | VERIFIED |
| R5 | ProjectJJK-named assets/legal comparison materials | **P1** | assets `projectjjk/**`, ARR research | VERIFIED |
| R6 | No automated in-game or two-client smoke in CI | **P2** | Java assertion tests/build only | VERIFIED |
| R7 | Custom particle classes still have boilerplate even though burst/ring spawning is centralized | **P2** | particle package + `VfxParticleChannel` | MITIGATED |
| R8 | A future agent could bypass VFX Core, drop late-cue age from a recipe, or reintroduce legacy static managers | **P1** | `ProjectSanityTest.java:303-393` + [[VFX-core]] | MITIGATED |
| R9 | Vanilla blur call compiles but its availability/feel across live client state and graphics settings is not manually verified | **P2** | `VfxPostProcessChannel.java:23-36` | MITIGATED |

## VFX-specific guardrails

- The server can decide cue timing and seed; the client may only draw.
- Unknown or expired cues are safe to ignore, but a missing ID is still a visual regression and must get a test.
- Every current Nobara HUD/camera/first-person/post-process recipe call must preserve `initialAgeTicks`; the exact 23-call guard intentionally fails when one silently falls back to a fresh overload.
- `ClientLevel` cleanup is identity-based, not dimension-name based. The guard requires `clear()` inside the identity-change branch, and null/disconnect must reset the tracked level.
- World impacts must retain `VfxCue` and resolve the entity anchor on every render; caching the first resolved position would reintroduce stale geometry after movement/despawn.
- `VfxDirector` caps active instances at 64. High-frequency scenes need a measured budget, not an unbounded new manager.
- Vanilla particle settings reduce local density. Verify spectacle at ALL, DECREASED, and MINIMAL before declaring an effect tuned.
- Blur remains internal to the director. If the public vanilla call throws a runtime/linkage error, it disables only blur for that client session; world/HUD/camera/particle fallbacks must remain sufficient.

## Resolved on 2026-07-10

| Former risk | Resolution | Source | Status |
|---|---|---|---|
| Integer impulse switch drifted client/server | one shared `VfxCue` payload and recipe registry replace the switch | `VfxCuePayload.java`, `JujutsuClientNetworking.java:14-15` | VERIFIED |
| Separate Hairpin world/HUD/camera/particle/FP managers duplicated lifecycle | `VfxDirector` owns generic channels, level-identity cleanup, and null/disconnect reset | `VfxDirector.java:25-148` | VERIFIED |
| Late packets replayed one-shot beats and restarted realtime channel clocks | opening beats are `< 2` ticks; non-expired cues pass actual age into offset channel timestamps | `VfxTimeline.java:10-27`, `NobaraVfxRecipes.java:37-189` | VERIFIED |
| World impacts captured a one-time origin instead of following a live entity | each render resolves the retained cue through `VfxAnchorResolver`, with `cue.origin()` fallback | `VfxWorldChannel.java:34-69` | VERIFIED |
| First-person snap phase scale did not traverse its documented range | 0.75-second snap now traverses the complete `0..15` scale | `VfxFirstPersonChannel.java:14-59`, `ProjectSanityTest.java:380-393` | VERIFIED |
| Persistent nail aura could be lost in a generic timeline migration | it remains state-driven and shares `VfxPalette` | `ProjectJjkNailRenderer.java:23,31-42` | VERIFIED |
| Post-process had no proven internal route | director-owned `VfxPostProcessChannel` calls the public vanilla blur method and disables itself safely on runtime/linkage failure | `VfxPostProcessChannel.java:7-46` | MITIGATED |
| Mark-only remote hit omitted the canon-defining physical link/effigy ritual | target-bound remnant, reusable doll, nail, hammer, wind-up, and server revalidation now gate Resonance | [[../03-systems/Straw-Doll-resonance]] | VERIFIED |

## P0 action

Document source of truth as the cinematic branch/worktree until merged to main/release. This vault cites that path.

---
tags: #jujutsumod #risks #vfx #verified
