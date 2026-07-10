# Risks & Tech Debt

← [[00-MOC]]

Only code-backed or process-backed risks.

| ID | Risk | Priority | Source | Status |
|---|---|---|---|---|
| R1 | Checkout without cinematic worktree misleads agents (missing Nobara) | **P0** | 28 vs 77 Java files | VERIFIED |
| R2 | Dual combat stacks (legacy Hairpin runtime + ProjectJJK kit) | **P1** | both packages under `character/nobara` | VERIFIED |
| R3 | In-memory character selection lost on restart | **P1** | `CharacterSelectionManager` map | VERIFIED |
| R4 | Broadcast radius / canSend silent drops | **P1** | `JujutsuNetworking` canSend false path | VERIFIED |
| R5 | Client mixins for camera/skin increase update cost on MC bump | **P2** | client mixins json | VERIFIED |
| R6 | ProjectJJK-named assets/legal comparison materials | **P1** | assets `projectjjk/**`, ARR research | VERIFIED |
| R7 | No automated in-game smoke in CI | **P2** | only unit-style JavaExec tests | VERIFIED |
| R8 | Post-shader pipeline may be incomplete | **P2** | shaders on disk; bind UNKNOWN | UNKNOWN bind |
| R9 | Particle class boilerplate duplication | **P2** | multiple `Hairpin*Particle` classes | VERIFIED structure |

## P0 action

Document SoT = cinematic branch/worktree until merged to main/release branch.  
This vault always cites that path.

---
tags: #jujutsumod #risks
