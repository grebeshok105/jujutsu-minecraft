# AGENTS.md — Jujutsu Minecraft

## Project Identity

- Project root: `D:/WorkFlow/Jujutsu Minecraft`
- Mod loader: Fabric
- Minecraft version: `1.21.8`
- Java version: `21`
- Mod id: `jujutsumod`
- Status: **Nobara vertical slice live** (combat + VFX + character menu), not an empty template
- Core fantasy: a Minecraft mod inspired by the *Jujutsu Kaisen / Магическая битва* idea space — polished combat, strong visual identity, repeatable workflow for deeply designed characters

## Current Product Direction

Ship a small number of fully polished characters. Do not rush a huge roster. Nobara is the first template character.

Primary priorities:

1. Quality gameplay over raw feature count.
2. Unique, readable, beautiful visual effects.
3. Character kits that feel different mechanically, not just different damage numbers.
4. A clean workflow reusable for the next character.
5. Fabric-native implementation for Minecraft `1.21.8`.

### Current slice (facts)

- Playable vessel: **Nobara** (nails, hammer, Hairpin, Resonance, traps, Black Flash path) + **None**
- Transient combat VFX: **VFX Core** only (`VfxCue` → director → recipes)
- Player menu: **Key N → ClickGui**; sidebar **Characters** (live) + **Soon...** placeholders (non-clickable)
- Character apply: `SelectCharacterPayload` C2S, server-authoritative; UI theme orange/slate via `ClickGuiTheme`
- **No** cursed-energy resource bar in the current kit
- **No** Neon Dashboard / Key V menu (removed)
- Optional GUI work may live on worktree/branch `feat/neon-gui-polish` while `main` lags — **check worktree before editing**

## Non-Negotiable Workflow

- Work in git only. If the repository is missing, initialize it before changing project files.
- Use an isolated worktree for feature work instead of editing the main checkout directly.
- Commit every meaningful change immediately after it is made and verified.
- Commit messages are in English and conventional-style, for example:
  - `chore(project): add agent instructions`
  - `feat(character): add yuji core kit`
  - `fix(gui): correct character roster hitboxes`
- Keep changes small enough that a single commit can be reviewed honestly.
- Do not batch unrelated work into one commit.
- Do not leave uncommitted project changes at a handoff point unless explicitly blocked.
- For multi-session GUI/combat work, keep `SESSION.md` on the active worktree current.

## Agent Tools (use when they fit)

Do not load everything every turn. Prefer the lightest tool that answers the question.

| Tool | When |
|------|------|
| **Skills** | Load `using-superpowers` first each session; then match task → skill (e.g. `minecraft-mod-dev`, `using-git-worktrees`, `systematic-debugging`, `verification-before-completion`). Follow skill checklists when they apply. |
| **mcpvault** | Obsidian vault: read/write codex notes before design/implement/review of documented systems. Paths under vault root (e.g. `jujutsumod-codebase-codex/…`). |
| **codegraph** | Structural “where is / who calls / architecture” questions on indexed code — use before long grep/read loops when `.codegraph/` exists. |
| **codebase-memory / search** | Fallback symbol search when codegraph is unavailable or insufficient. |
| **Repo docs** | `SESSION.md` (active worktree), `AGENTS.md`, vault MOCs |

If mcpvault or codegraph is unavailable, say so once and continue with filesystem/search.

## Brainstorming Gate

Before implementing **new** gameplay systems, characters, VFX architecture, networking contracts, or major UI shells:

1. Clarify the design goal.
2. Compare 2–3 viable approaches.
3. Pick the simplest approach that can still become the long-term template.
4. Write the approved design/spec before implementation.
5. Only then write an implementation plan.

Skip the full gate for trivial fixes, copy, polish, or changes the user already specified precisely.  
No code-first experiments in the main product path unless the user explicitly asks for a throwaway prototype.

## Obsidian Knowledge Base

- Before designing, implementing, or reviewing ProjectJJK/Nobara/ported systems, consult the vault via **mcpvault** first.
- **Our mod codex:** `jujutsumod-codebase-codex/` — start `00-MOC.md`, then `01-meta/Citation-standard.md`, `04-client-vfx/VFX-core.md`, GUI notes (`GUI-dual-overview`, `GUI-character-select`, `GUI-rich-clickgui`).
- **ProjectJJK research:** `grok-projectjjk-codex/` — start `00-MOC.md`; for 1:1 parity use `05-reference/Claim-Source-Index.md` + `One-to-one-checklist.md`.
- Treat `VERIFIED` / `INFERRED` / `UNKNOWN` exactly as in the citation standard. Do not implement `UNKNOWN` as fact.
- Cross-check vault claims against the real codebase (codegraph → search) before changing behavior.
- Every meaningful gameplay, character, VFX, UI, networking, asset, or architecture change must create or update codex notes (new system → note + MOC link; change → claims/citations/uncertainties).
- When using vault findings, mention note path(s). If mcpvault is down, say so and use repo docs/code.

## Technical Rules

- Use public Fabric APIs only. Never import `net.fabricmc.fabric.impl.*`.
- Keep server-authoritative gameplay on the logical server.
- Keep rendering, HUD, particles, camera work, keybinds, menus, and client-only animation under `src/client`.
- Use Mojang mappings conventions for Minecraft `1.21+`:
  - `ResourceLocation.fromNamespaceAndPath(namespace, path)` or `ResourceLocation.parse(...)`
  - `net.minecraft.network.chat.Component`
- Avoid deprecated loader, Fabric, and Minecraft APIs.
- Prefer typed/custom payload networking patterns.
- Do not add Mixins unless a normal Fabric API or event cannot solve the problem.
- If Mixins are required:
  - prefer MixinExtras `@WrapOperation` over `@Redirect` when possible
  - mark private helper fields/methods with `@Unique`
  - keep each mixin narrowly scoped and documented in the design/spec, not in long code comments

### UI / menu rules (current)

- Single product menu: **ClickGui on N** (`jujutsu.mod.client.rich…`).
- Characters tab is the vessel select path; do not reintroduce Neon Dashboard / Key V without an explicit product decision.
- Panels: project SDF (`SdfRenderer`) via `Render2D` adapters; text: MSDF where wired. Do not claim full original Rich GL pipelines as live.
- Vessel selection must go through server payloads — no silent client-only authority.

## Mandatory VFX Core Contract

- Read vault note `jujutsumod-codebase-codex/04-client-vfx/VFX-core.md` before designing, implementing, or reviewing combat visuals.
- Every transient combat effect must use: server-confirmed action → `VfxCue` → `VfxDirector` → `<Character>VfxRecipes` → director-owned channels; cues are visual-only.
- For each character, add `<Character>VfxIds` and `<Character>VfxRecipes`; when the second character arrives, register recipes through one explicit `JujutsuVfxRecipes.registerAll()`.
- Persistent visuals that follow a real entity/state stay on that entity/state renderer, not a transient timeline.
- Do not create per-effect receivers, render/HUD callbacks, camera/HUD managers, lifecycle managers, or effect-specific mixins; add a shared director channel only after an approved design shows existing channels are insufficient.

## Dependency Policy

Third-party libraries and companion mods are allowed, but every dependency must justify itself.

Allowed reasons:

- Visual quality that would be expensive or fragile to rebuild.
- Stable animation, rendering, or capability system (e.g. GeckoLib for doll/player geo).
- Noticeable speed without locking into a dead ecosystem.

Before adding a dependency, record: what it does, required vs optional, MC/Fabric support, runtime impact, missing-dependency behavior.

Avoid libraries for small utilities, simple math, or abstractions Java/Fabric already handle cleanly.

## Gameplay Design Principles

Every character should have:

- A clear fantasy in one sentence.
- A unique resource or pressure model **if needed** (not required — current kit has no CE bar).
- A small number of high-impact abilities instead of many filler buttons.
- At least one defensive or mobility decision, not only attacks.
- Readable counterplay for multiplayer/server use.
- Distinct VFX language: color, shape, timing, sound, screen feel.
- Progression only if it improves play; no grind by default.

Avoid:

- Same ability with different particles.
- Unbounded damage scaling.
- Invisible passives.
- Long cooldowns as the only balance lever.
- Giant registries before the next kit proves itself.

## First Milestone Philosophy

**Done for v1 slice:** one playable character (Nobara), server-authoritative combat path, VFX Core, ability inputs, character select menu, repeatable pattern to copy.

**Next milestone focus:** polish feel/visuals of the current kit, then the **second** character using the same contracts (VFX ids/recipes, selection, networking) — not a broad unfinished framework.

## Suggested Character Workflow

For each character:

1. Write a character brief.
2. Define the combat loop and any resource/pressure model.
3. Define abilities (costs, targets, counterplay).
4. Define VFX/SFX against VFX Core.
5. Define networking and client/server boundaries.
6. Implement the smallest playable version.
7. Verify in-game (not compile-only).
8. Polish timing, feel, visuals.
9. Capture reusable patterns only after it works once.
10. Update vault codex + `SESSION.md` if the handoff matters.

## Code Organization Direction

Do not invent empty packages. Prefer existing roots; add packages only when a feature needs them.

Typical live areas:

- `jujutsu.mod.character` / `…nobara.projectjjk` — vessels and combat runtimes
- `jujutsu.mod.vfx` + `jujutsu.mod.client.vfx` — cues, director, recipes, channels
- `jujutsu.mod.network` — typed payloads
- `jujutsu.mod.registry` — items, entities, particles, sounds
- `jujutsu.mod.client.rich` — ClickGui / modules / theme
- `jujutsu.mod.client.ui.msdf` + `…ui.neon.render` — MSDF + SDF backends
- `jujutsu.mod.client.input` — keybinds
- `jujutsu.mod.client.render` — entity/player/item renderers

## Asset Policy

- Keep source/reference assets outside pure runtime packs when possible.
- Runtime sounds must be OGG Vorbis.
- User-visible text must be localizable for real UI.
- VFX must read in motion, not only in screenshots.
- Never copy anime assets into the repo unless licensing is explicit.
- Prefer original/inspired designs over copyrighted rips.
- ProjectJJK-named comparison assets stay research/legal-sensitive — do not expand casually.

## Verification Policy

Before claiming work is done, run the narrowest command that proves the changed behavior.

Baseline:

- Windows: `gradlew.bat build --no-daemon -x test` (or full `build` when sanity matters)
- Unix: `./gradlew build --no-daemon -x test`
- Client smoke when UI/gameplay changed: `gradlew.bat runClient --no-daemon`

Do not claim in-game behavior from compilation alone.

## Communication

- User writes Russian; answer in Russian (technical terms may stay English).
- Keep explanations direct and practical.
- Surface tradeoffs before implementing non-trivial work.
- If unknown, verify from repo/vault before asserting.
- At handoff: exact files/branches, commands run, jar path if deployed.

## Resolved Decisions (do not re-open casually)

1. Hybrid fidelity: Minecraft-native feel + ProjectJJK-inspired Nobara contracts where verified.
2. Multiplayer-safe networking from the start (typed payloads, server authority).
3. First template character: **Nobara**.
4. No universal cursed-energy bar in the current kit.
5. Transient combat VFX: **VFX Core only**.
6. Product menu: **ClickGui (N)** with Characters select; Neon V dashboard retired.

## Open Questions (real remaining)

1. When to merge `feat/neon-gui-polish` (or successor) fully into `main` / release.
2. Who is the second character, and which kit axes must differ from Nobara.
3. Whether ClickGui grows more live tabs later or stays Characters + Soon placeholders.
4. How far to push Rich visual parity vs keep SDF/MSDF adapters long-term.
