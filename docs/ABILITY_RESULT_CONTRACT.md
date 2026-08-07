# Ability result contract — design (issue #19)

Status: CURRENT
Date: 2026-08-07
Issue: [#19](https://github.com/grebeshok105/jujutsu-minecraft/issues/19) (E10 + E11 from KNOWN_ISSUES)

## Problem

`CharacterDefinition.tryCast` returns `boolean`. Nobara's runtimes write specific failure messages (trap.no_ground, trap.unsupported, trap.no_nails, trap.failed, self_resonance.no_link) then return `false`; the router's generic `no_target` fallback immediately overwrites them in the shared action-bar slot. Five authored, translated messages are invisible to players.

The cooldown gate in the shared executor (with a message) now precedes Nobara's silent stagger check — a deliberate ordering change that was never recorded.

## Decisions

1. **Tri-state `AbilityResult` enum** replaces `boolean` on `CharacterDefinition.tryCast` and all routers: `SUCCESS`, `HANDLED_FAILURE` (runtime already told the player), `UNHANDLED_FAILURE` (nobody said anything — fallback speaks). The information originates in the runtimes, per the issue's requirement.

2. **Fallback fires only on `UNHANDLED_FAILURE`.** The five messages survive.

3. **Gate ordering is recorded as-is**: `not_selected → cooldown (message) → vessel gates (stagger silent, etc.) → dispatch`. Moving cooldown into vessels would create a one-implementer shared hook (E1b/E13 smell) and contradict the documented rule that the shared executor owns the cooldown gate. A test pins the order so it cannot drift. E11 closes as "recorded, not a defect".

4. **Silent-false paths are explicitly classified** (see table below). `SelfResonanceRuntime`'s NEEDS_SELECTION path (opens the link picker) stays `SUCCESS` — the input was consumed and the player got a UI.

5. **Todo and Megumi routers map trivially**: `true → SUCCESS`, `false → UNHANDLED_FAILURE`. They have no router-level fallback, so nothing changes for their players. Megumi's `handleWhileActive` (returns true) → `SUCCESS`.

6. **`trap.failed`** (addFreshEntity failure) has no deterministic world condition — it is source-pinned only; the other four messages get in-world GameTest verification.

## Silent-false classification (Nobara)

| Path | Condition | Result | Rationale |
|---|---|---|---|
| `SelfResonanceRuntime` PENDING | mid-cast | `UNHANDLED_FAILURE` | nothing to tell; generic no_target is honest |
| `SelfResonanceRuntime` non-participant | stale link | `UNHANDLED_FAILURE` | same |
| `SelfResonanceRuntime` NEEDS_SELECTION | opens picker | `SUCCESS` | input consumed, UI shown |
| `NobaraHammerCombatRuntime.handleInput` | no hit | `UNHANDLED_FAILURE` | fallback correct |
| `canCastMarkedHairpin` false | explosive lock | `UNHANDLED_FAILURE` | fallback correct |
| `NailTrapRuntime` fail() paths | 4 conditions | `HANDLED_FAILURE` | specific message survives |
| `SelfResonanceRuntime` no_link | no links | `HANDLED_FAILURE` | specific message survives |

## Cross-PR collision (PR #63)

PR #63's `JujutsuAbilityInvokeTool` calls `CharacterAbilityExecutor.tryCast(...)` as `boolean`. Widening the return to `AbilityResult` breaks mcpdev compilation on the #63 branch — and `qualityGate` does NOT compile mcpdev, so no gate catches it. **Whichever PR merges second needs a one-line fix** (`result == AbilityResult.SUCCESS` in the tool). This branch compiles clean today (mcpdev tools don't exist on it); the collision surfaces only after both merge into main.

## Files touched

- New: `AbilityResult.java`, `NobaraAbilityResultGameTests.java`
- Signature: `CharacterDefinition`, `CharacterAbilityExecutor`, all 4 definitions, all 3 routers
- Runtimes: `NailTrapRuntime`, `SelfResonanceRuntime`, `ProjectJjkRitualRuntime` (startDirectedHairpin), `ProjectJjkMegaNailRuntime` (start), `NobaraHammerCombatRuntime` (handleInput)
- Consumers: `JujutsuCommands`, `TodoSwapTestFixtures`
- Tests: `NobaraAbilitySlotsTest`, `TodoPairSwapTest`, `ProjectSanityTest`, `NailTrapTest` (source-pin updates)
- Docs: KNOWN_ISSUES E10/E11 close, Codex notes, AGENTS.md if needed

## Non-goals

Executor-internal test coverage (T4.3), message rewording/retranslation, widening `NobaraAbilitySlotsTest` beyond router scope, any wire-format change.
