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
| R8 | A future agent could bypass VFX Core or reintroduce legacy static managers | **P1** | `ProjectSanityTest.java:305-362` + [[VFX-core]] | MITIGATED |
| R9 | No compatible Fabric 1.21.8 shader/post-process backend is proven | **P2** | VFX Core V1 boundary | OPEN |

## VFX-specific guardrails

- The server can decide cue timing and seed; the client may only draw.
- Unknown or expired cues are safe to ignore, but a missing ID is still a visual regression and must get a test.
- `VfxDirector` caps active instances at 64. High-frequency scenes need a measured budget, not an unbounded new manager.
- Vanilla particle settings reduce local density. Verify spectacle at ALL, DECREASED, and MINIMAL before declaring an effect tuned.
- A post-process spike may happen later behind the director; do not add a fake abstraction or dependency until a 1.21.8-compatible route is tested.

## Resolved on 2026-07-10

| Former risk | Resolution | Source | Status |
|---|---|---|---|
| Integer impulse switch drifted client/server | one shared `VfxCue` payload and recipe registry replace the switch | `VfxCuePayload.java`, `JujutsuClientNetworking.java:14-15` | VERIFIED |
| Separate Hairpin world/HUD/camera/particle/FP managers duplicated lifecycle | `VfxDirector` owns generic channels and cleanup | `VfxDirector.java:24-125` | VERIFIED |
| Persistent nail aura could be lost in a generic timeline migration | it remains state-driven and shares `VfxPalette` | `ProjectJjkNailRenderer.java:23,31-42` | VERIFIED |

## P0 action

Document source of truth as the cinematic branch/worktree until merged to main/release. This vault cites that path.

---
tags: #jujutsumod #risks #vfx #verified
