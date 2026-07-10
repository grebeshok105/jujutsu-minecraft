# VFX Core Authoring Contract and Scaling Design

Date: 2026-07-10
Status: Approved design
Scope: Agent guidance, architectural guardrails, and the extension path for future character VFX

## Context

The current branch contains a working VFX Core and a complete Nobara migration. The codebase codex already describes the intended authoring path, but the repository-level `AGENTS.md` does not make that path mandatory for future agents. Documentation alone also cannot stop an accidental return to per-effect packet handlers, render callbacks, HUD managers, camera managers, or other parallel client-effect stacks.

The project is expected to grow toward roughly 10-15 polished characters. The VFX module therefore needs one small author-facing interface and strong internal locality: character authors describe effect scenes while the core continues to own transport, timing, lifecycle, quality scaling, culling policy, and shared render channels.

## Goals

- Make VFX Core the mandatory path for transient combat effects.
- Preserve real renderers for persistent visuals whose lifetime follows gameplay state.
- Give future agents an explicit, repeatable workflow for adding effects and characters.
- Add narrow automated guards for recognizable architectural bypasses.
- Scale to additional characters without building a generic DSL, reflection registry, or universal effect framework prematurely.

## Non-goals

- Changing existing Nobara gameplay or visual timing.
- Moving persistent nail aura rendering into a transient cue timeline.
- Adding shaders, post-processing, a JSON/DSL authoring format, preview tooling, or a new rendering dependency.
- Automatically proving whether every visual idea is transient or persistent.
- Replacing manual visual-quality and multiplayer QA.

## Binding Decision

Every transient combat effect must use this flow:

```text
server-confirmed action
    -> VfxCue
    -> typed S2C VfxCue transport
    -> VfxDirector
    -> <Character>VfxRecipes
    -> director-owned channels
```

The cue remains visual-only. Damage, cooldowns, marks, entity spawning, targeting, and all other gameplay mutation remain server-authoritative.

Persistent visuals are the deliberate exception. A visual whose lifetime is derived from a real entity or durable gameplay state remains in that entity or state renderer. It may reuse stable visual helpers such as `VfxPalette`, but it must not be forced into a short-lived cue timeline merely for architectural uniformity.

## Effect Classification

### Transient combat effect

A finite scene caused by a confirmed gameplay event. Examples include impact bursts, rings, ribbons, blades, local sound beats, HUD flashes, camera/FOV impulses, and first-person pose accents. These effects use VFX Core even if they combine several channels.

### Persistent state visual

A visual continuously derived from an entity or gameplay state. Examples include an aura attached to a living entity, a rendered nail that remains in the world, or a model layer representing an active state. These effects stay in real renderers and do not create a parallel transient manager.

If classification is unclear, the design/spec for that character must decide it before implementation.

## Character Authoring Interface

Each new character with transient effects owns two explicit types:

- `<Character>VfxIds` in shared/main source for stable `ResourceLocation` identifiers used by server cue emission.
- `<Character>VfxRecipes` in client source for Java recipe registration and scene composition.

The authoring workflow is:

1. Define the effect ID.
2. Emit a `VfxCue` only after the server confirms the gameplay action.
3. Register one Java recipe for that ID.
4. Compose the recipe using only `VfxContext` and director-owned world, particle, sound, HUD, camera/FOV, and first-person channels.
5. Define distance culling and vanilla particle-quality behavior where the scene needs them.
6. Add registration/contract coverage, update the codex, and perform the required manual visual QA.

Character code must not create a receiver, global render callback, HUD singleton, camera manager, or effect-specific mixin as an alternative path. A new mixin or channel requires its own design justification because it expands the VFX Core interface for every future consumer.

## Scaling to Additional Characters

Per-character recipe classes keep character visual language local. Gojo, Yuji, Megumi, and later characters can each have distinct recipes without placing their scene details into `VfxDirector` or a giant switch.

When the second character is implemented, add one small client recipe bootstrap, for example `JujutsuVfxRecipes.registerAll()`. It will call each character recipe class explicitly:

```text
VfxDirector.initialize()
JujutsuVfxRecipes.registerAll()
JujutsuClientNetworking.registerReceivers()
```

The bootstrap is an explicit list, not reflection or classpath scanning. It provides one visible registration seam while each character retains local recipe ownership. It is deferred until the second consumer exists; adding it now would provide no additional leverage.

The shared module grows only under one of these conditions:

- At least two characters need the same primitive or policy.
- A concern must be globally consistent, such as lifecycle cleanup, active-instance limits, late-cue timing, quality scaling, or culling.
- A new rendering capability cannot be expressed through the existing channels and has a reviewed Fabric 1.21.8 implementation path.

When one condition is met, extend the implementation behind the existing VFX seam or add one narrow director-owned channel. Do not expose payload handling, callback registration, lifecycle management, or backend details to character recipes.

## Enforcement Layers

### Repository contract

Add a mandatory `VFX Core Contract` section to `AGENTS.md`. It will define the transient/persistent split, required flow, per-character file pattern, forbidden bypasses, documentation duties, and verification requirements. Once this is versioned, the user does not need to repeat "use the VFX library" in every prompt; agents are already required to read and follow `AGENTS.md`.

### Authoring documentation

Keep the following versioned codex notes aligned with the repository contract:

- `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md`

The VFX Core note remains the detailed interface reference. `AGENTS.md` contains the short binding rules and links agents to that note instead of duplicating its full implementation guide.

### Automated architectural guards

Extend `ProjectSanityTest` with narrow checks that can be proven structurally:

- VFX packet reception remains centralized in `JujutsuClientNetworking`.
- Transient world/HUD callback ownership remains centralized in `VfxDirector`.
- Client initialization keeps director setup and recipe registration before VFX cue reception.
- Each character added to the VFX path receives explicit recipe-registration coverage.
- Known removed legacy managers and payload paths remain absent.

The guards must use a small allowlist and actionable failure messages. They must not attempt to infer artistic intent or ban legitimate C2S gameplay payloads and real entity renderers.

## Failure Behaviour

- Duplicate recipe IDs fail fast during registration.
- Unknown cue IDs remain safe at runtime but are treated as a test/documentation failure.
- Expired cues are ignored according to the current timeline policy.
- Missing live anchors fall back to the immutable cue origin.
- Client recipes never compensate for missing server gameplay state.
- A proposed bypass that cannot fit the existing channels triggers a design update before code, not an ad hoc parallel manager.

## Verification

The contract implementation is complete when:

1. `AGENTS.md` contains the mandatory VFX Core rules and links to the detailed authoring note.
2. The three codex notes agree with the same transient/persistent classification and scaling path.
3. `ProjectSanityTest` contains generalized, narrow bypass guards without blocking valid gameplay networking or entity rendering.
4. Existing assertion tasks, `check`, and the runtime build pass.
5. The worktree is clean and each meaningful change is committed separately.

This governance change does not claim new in-game visual behaviour. Manual gameplay and two-client QA remain required when an actual character effect is added or changed.

## Considered Alternatives

### `AGENTS.md` only

Rejected as insufficient. It gives future agents clear instructions but provides no machine-visible warning when an implementation accidentally creates a parallel effect path.

### Fully closed or generic registration framework now

Rejected as premature. With only one character consumer, reflection, generated registries, a DSL, or a large universal effect schema would increase interface complexity without demonstrated leverage.

### Route persistent visuals through VFX Core

Rejected. It would duplicate real gameplay lifetime and anchor state on the client, making persistent entity visuals less reliable and the module shallower rather than deeper.

## References

- `AGENTS.md`
- `docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md`
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md`
