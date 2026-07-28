# Session Handoff - Test Architecture Tier 1-2

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/test-architecture-tier1-tier2`
- Branch: `fix/test-architecture-tier1-tier2`
- Base: `2a37974250d8acaf8bdb735dc54b55680ea17bd5` (`main`, after PR #17)

## Current scope

- Implement only T1.1, T1.2, T2.1, verification-count documentation correction, T2.2 and T2.3 from `docs/TEST_ARCHITECTURE_PLAN.md`.
- Tier 3 and Tier 4 remain deferred. Megumi gameplay and polish are out of scope.
- T1.1 is complete: Black Flash chance and centralized runtime usage have independent assertions. The required `0.99f` mutation failed with `Black Flash chance must stay at 10%`; restored source passed `testBlackFlash`.
- T1.2 is complete: ordering checks first require both runtime fragments. Replacing the accepted-hit gate with `damageAccepted` failed with `missing accepted-hit gate before the embed Black Flash window`; restored source passed `testBlackFlash`.
- T2.1 is complete: `check` dynamically depends on every verification `JavaExec`. `check`, `verifyAssertionsEnabled`, and `check --dry-run` passed; a temporary group member appeared in the dry-run without manual wiring.
- The stale verification-program counts were removed from current documentation; `verifyAssertionsEnabled` is the single live inventory source and `auditDocumentation` passed.
- T2.2 is complete: the classifier derives a source-relative package path and accepts only registered vessel roots. Direct JUnit coverage passed after a RED synthetic shared-path mutation; the strict and prior shared-file inventories both contain 195 files.
- T2.3 is complete: floors are 194 main classes, 214 client classes and 195 shared source files. Each `floor + 1` mutation failed; direct boundary tests prove `floor - 1` fails and `floor` passes.
