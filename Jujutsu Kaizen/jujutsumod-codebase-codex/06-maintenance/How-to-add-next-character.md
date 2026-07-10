# How to Add the Next Character

← [[00-MOC]] · [[../04-client-vfx/VFX-core]] · AGENTS character workflow

## Recommended path

1. Write the character brief, combat loop, counterplay, VFX language, and approved design before code.
2. Add `JujutsuCharacter` enum value, select-screen card/lang, and loadout branch.
3. Create `character/<name>/` with profile constants and server-authoritative runtime methods.
4. Add items/entities/sounds/particles only where the design needs them.
5. Use explicit C2S action payloads only for client input that requires server validation.
6. For each transient visual event:
   - add `<Character>VfxIds`,
   - emit a server `VfxCue`,
   - register a Java `VfxRecipe`,
   - test/document it.
7. Keep persistent, stateful visual objects (for example an entity aura) in their real entity renderer; share `VfxPalette`/helpers only where useful.
8. Add pure tests, `ProjectSanityTest` guards, build, then run real-client smoke.

## VFX recipe checklist

| Step | Required result |
|---|---|
| ID | stable `ResourceLocation` under the character namespace |
| Server cue | origin fallback, optional entity anchor, bounded intensity, `level.getGameTime()`, `level.random.nextLong()` |
| Recipe | one registration through `VfxDirector`; channel-only composition |
| Quality | readable result at reduced/minimal particles; cull local spectacle by proximity as needed |
| Boundaries | no client gameplay mutation and no per-effect receiver/render callback/mixin |
| Documentation | VFX note, MOC, affected networking/boundary notes |
| QA | check/build + real ability smoke; two-client observation when available |

## Do expand

| Place | Why |
|---|---|
| Character enum + selection | single gate |
| Per-character package | isolation |
| Profile constants class | balance without magic numbers |
| `*VfxIds` + server cue + client recipe | one reusable VFX contract |
| Real entity renderer for persistent world objects | avoids fake client-only followers |

## Do not inflate early

| Place | Why |
|---|---|
| Giant universal Ability framework | AGENTS: prove kit first |
| Shared CE economy | not in current code; design decision |
| One semantic impulse payload or client switch per character | VFX Core replaces this pattern |
| Per-character render events/HUD singletons/mixins | director owns transient effects |
| JSON/DSL editor, preview scene, generic GeckoLib attachments | explicitly outside VFX Core V1 |
| Parallel old/new implementation stacks | keep one canonical runtime path per character |

## Pain points to expect

- Selection map is in-memory only (no disk persistence).
- Visual assets and particles grow fast; assign ownership and delete dead assets when replacing a pipeline.
- A server ID without a client recipe fails safely but loses spectacle; cover it with a guard.
- In-game feel and two-client behavior remain manual checks; compile/tests do not prove them.

**Status:** INFERRED process from current Nobara reference implementation + AGENTS; no second character exists yet.

---
tags: #jujutsumod #maintenance #vfx #verified
