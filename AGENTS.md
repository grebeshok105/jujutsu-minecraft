# AGENTS.md — Jujutsu Minecraft

## Project Identity

- Project root: `D:/WorkFlow/Jujutsu Minecraft`
- Mod loader: Fabric
- Minecraft version: `1.21.8`
- Java version: `21`
- Mod id: `jujutsumod`
- Current starting point: Fabric template
- Core fantasy: a Minecraft mod inspired by the *Jujutsu Kaisen / Магическая битва* idea space, focused on polished combat, strong visual identity, and a repeatable workflow for building deeply designed characters.

## Current Product Direction

Build a high-quality jujutsu combat mod through a small number of fully polished characters first. Do not rush a huge roster. The first characters define the template for every future character.

Primary priorities:

1. Quality gameplay over raw feature count.
2. Unique, readable, beautiful visual effects.
3. Character kits that feel different mechanically, not just different damage numbers.
4. A clean workflow that can be reused after the first ideal implementation.
5. Fabric-native implementation for Minecraft `1.21.8`.

## Non-Negotiable Workflow

- Work in git only. If the repository is missing, initialize it before changing project files.
- Use an isolated worktree for feature work instead of editing the main checkout directly.
- Commit every meaningful change immediately after it is made and verified.
- Commit messages are in English and conventional-style, for example:
  - `chore(project): add agent instructions`
  - `feat(character): add yuji core kit`
  - `fix(combat): correct cursed energy drain`
- Keep changes small enough that a single commit can be reviewed honestly.
- Do not batch unrelated work into one commit.
- Do not leave uncommitted project changes at a handoff point unless explicitly blocked.

## Brainstorming Gate

Before implementing gameplay systems, characters, effects, networking, assets, or UI:

1. Clarify the design goal.
2. Compare 2-3 viable approaches.
3. Pick the simplest approach that can still become the long-term template.
4. Write the approved design/spec before implementation.
5. Only then write an implementation plan.

No code-first experiments in the main mod unless the user explicitly asks for a throwaway prototype.

## Obsidian Knowledge Base

- Before designing, implementing, or reviewing ProjectJJK/Nobara/ported systems, consult the local Obsidian knowledge base through the available MCP (`mcpvault`) first.
- Treat `Jujutsu Kaizen/grok-projectjjk-codex/` as the current ProjectJJK research index: start from `00-MOC.md`, then read `01-meta/Citation-standard.md` and the relevant architecture, ability, reference, and porting notes.
- For 1:1 ProjectJJK behavior, use `05-reference/Claim-Source-Index.md` first, open the cited `file:line`, then follow `05-reference/One-to-one-checklist.md` before coding.
- Treat `VERIFIED`, `INFERRED`, and `UNKNOWN` exactly as defined by the citation standard. Do not implement `UNKNOWN` claims as facts; turn them into research tasks.
- Cross-check Obsidian notes against the actual jujutsumod codebase before changing behavior. Use the project code graph/MCP tools for source discovery, then fall back to `rg` only when MCP results are insufficient or the target is non-code.
- Every meaningful gameplay, character, VFX, UI, networking, asset, or architecture change must create or update documentation in the Obsidian/codebase codex. New systems need a dedicated note plus links from the relevant MOC/index; changed systems need updated claims, source citations, and uncertainties.
- When using Obsidian findings in an implementation or review, mention the note path(s) used. If the Obsidian MCP is unavailable, say so explicitly and continue with repository docs/code search.

## Technical Rules

- Use public Fabric APIs only. Never import `net.fabricmc.fabric.impl.*`.
- Keep server-authoritative gameplay on the logical server.
- Keep rendering, HUD, particles, camera work, keybinds, and client-only animation code under `src/client`.
- Use Mojang mappings conventions for Minecraft `1.21+`:
  - `ResourceLocation.fromNamespaceAndPath(namespace, path)` or `ResourceLocation.parse(...)`
  - `net.minecraft.network.chat.Component`
- Avoid deprecated loader, Fabric, and Minecraft APIs.
- Prefer typed/custom payload networking patterns when networking is added.
- Do not add Mixins unless a normal Fabric API or event cannot solve the problem.
- If Mixins are required:
  - prefer MixinExtras `@WrapOperation` over `@Redirect` when possible
  - mark private helper fields/methods with `@Unique`
  - keep each mixin narrowly scoped and documented in the design/spec, not in code comments

## Mandatory VFX Core Contract

- Read `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md` before designing, implementing, or reviewing combat visuals.
- Every transient combat effect must use: server-confirmed action -> `VfxCue` -> typed S2C transport -> `VfxDirector` -> `<Character>VfxRecipes` -> director-owned channels.
- Keep gameplay authority on the server. A cue and its recipe may render only; they must never apply damage, marks, cooldowns, targeting, entity spawning, or other gameplay state.
- For each character, keep stable IDs in `<Character>VfxIds` and client scene composition in `<Character>VfxRecipes`. Use `VfxContext` and the director-owned world, particle, sound, HUD, camera/FOV, and first-person channels.
- Persistent, stateful visuals whose lifetime follows a real entity or gameplay state stay in the real entity/state renderer. They may share stable helpers such as `VfxPalette`; do not force them into a transient timeline.
- Do not add per-effect packet receivers, global render/HUD callbacks, HUD or camera singletons, parallel lifecycle managers, or effect-specific mixins as a shortcut around VFX Core.
- If an effect cannot be expressed through existing channels, stop and update the approved design before adding one narrow director-owned channel. Add shared primitives only when two characters need them or when they enforce a global policy such as lifecycle, timing, quality, culling, or budgets.
- When the second character with transient VFX is added, introduce one explicit `JujutsuVfxRecipes.registerAll()` bootstrap that calls each character recipe class. Do not use reflection, classpath scanning, JSON/DSL registration, or a giant effect switch.
- Every new effect requires ID/recipe registration coverage, quality and distance policy, codex updates, `ProjectSanityTest`, `check`, build, and manual visual QA. Compilation alone does not prove effect quality or multiplayer observation.

## Dependency Policy

Third-party libraries and companion mods are allowed, but every dependency must justify itself.

Allowed reasons:

- It enables visual quality that would be expensive or fragile to rebuild.
- It provides a stable animation, rendering, or capability system.
- It noticeably improves development speed without locking the project into a dead ecosystem.

Before adding a dependency, record:

- What the dependency does.
- Whether it is required or optional.
- Its Minecraft/Fabric version support.
- Its runtime impact.
- What happens if the dependency is missing.

Avoid adding libraries for small utilities, simple math, or abstractions that Java/Fabric already handle cleanly.

## Gameplay Design Principles

Every character should have:

- A clear fantasy in one sentence.
- A unique resource or pressure model if the character needs one.
- A small number of high-impact abilities instead of many filler buttons.
- At least one defensive or mobility decision, not only attacks.
- Readable counterplay for multiplayer/server use.
- Distinct VFX language: color, shape, timing, sound, screen feel.
- A progression path only if it improves play; do not add grind by default.

Avoid:

- Same ability with different particles.
- Unbounded damage scaling.
- Passive effects that are invisible to the player.
- Long cooldowns as the only balancing lever.
- Giant registries before the first kit proves the workflow.

## First Milestone Philosophy

The first milestone should produce one excellent vertical slice, not a broad unfinished framework.

A good first slice likely includes:

- One playable character/technique set.
- One resource model, probably cursed energy.
- One input path for abilities.
- One HUD/readout if the resource needs it.
- One polished visual-effect pipeline.
- One server-authoritative combat path.
- One repeatable pattern for adding the next character.

## Suggested Character Workflow

For each character:

1. Write a character brief.
2. Define the resource and combat loop.
3. Define abilities with cooldowns, costs, targets, and counterplay.
4. Define VFX/SFX requirements.
5. Define networking and client/server boundaries.
6. Implement the smallest playable version.
7. Verify in-game behavior.
8. Polish timing, feel, and visuals.
9. Capture the pattern in reusable code only after it works once.

## Code Organization Direction

The template is still empty, so keep early structure boring and explicit.

Likely package direction once implementation begins:

- `jujutsu.mod.registry` — central registration entrypoints
- `jujutsu.mod.character` — character definitions and runtime state
- `jujutsu.mod.ability` — ability interfaces and shared execution code
- `jujutsu.mod.energy` — cursed energy resource logic
- `jujutsu.mod.combat` — damage, targeting, hit detection helpers
- `jujutsu.mod.network` — custom payload definitions and handlers
- `jujutsu.mod.client` — client entrypoint and client-only setup
- `jujutsu.mod.client.fx` — particles, beams, screen/camera effects
- `jujutsu.mod.client.hud` — HUD rendering

Do not create all packages up front. Add them only when the first feature needs them.

## Asset Policy

- Keep source/reference assets outside generated runtime resources when possible.
- Runtime sounds must be OGG Vorbis.
- User-visible text must be localizable when it becomes real gameplay UI.
- Visual effects should be readable in motion, not just pretty in screenshots.
- Never copy anime assets directly into the repo unless licensing is explicitly resolved.
- Prefer original, inspired designs over direct copyrighted rips.

## Verification Policy

Before claiming work is done, run the narrowest command that proves the changed behavior.

Baseline/project commands:

- Windows: `gradlew.bat build --no-daemon -x test`
- Unix shell: `./gradlew build --no-daemon -x test`
- Datagen, when added: `gradlew.bat runDatagen --no-daemon`
- Client smoke test, when gameplay is added: `gradlew.bat runClient --no-daemon`

Do not claim in-game behavior works from compilation alone. Compilation proves only compilation.

## Communication

- User writes Russian; answer in Russian.
- Keep explanations direct and practical.
- Surface tradeoffs before implementing.
- If something is unknown, verify it from the repo or current docs before asserting it.
- Mention exact files changed and exact commands run at handoff.

## Current Open Design Questions

These must be answered before gameplay implementation starts:

1. Should the mod be mostly anime-accurate, Minecraft-native inspired, or a hybrid?
2. Is the first vertical slice singleplayer-first or multiplayer-safe from day one?
3. Which first character/technique set defines the template?
4. How many active abilities should the first character have?
5. Should cursed energy be universal across all characters or character-specific?
6. What visual library, if any, should carry high-end effects?
7. Should progression/unlocks exist in the first milestone or wait until combat feels good?
