# AGENTS.md — Jujutsu Minecraft

## Project Identity

- Project root: `D:/WorkFlow/Jujutsu Minecraft`
- Mod loader: Fabric
- Minecraft version: `1.21.8`
- Java version: `21`
- Mod id: `jujutsumod`
- Status: **Nobara + Todo + Megumi vertical slices live** (combat + VFX + character menu), not an empty template
- Core fantasy: a Minecraft mod inspired by the *Jujutsu Kaisen / Магическая битва* idea space — polished combat, strong visual identity, repeatable workflow for deeply designed characters

## Current Product Direction

Ship a small number of fully polished characters. Do not rush a huge roster. Nobara is the template character; Todo is the first kit built by copying that template.

Primary priorities:

1. Quality gameplay over raw feature count.
2. Unique, readable, beautiful visual effects.
3. Character kits that feel different mechanically, not just different damage numbers.
4. A clean workflow reusable for the next character.
5. Fabric-native implementation for Minecraft `1.21.8`.

### Current slice (facts)

This block is the single owner of current-slice facts. `README.md` keeps only the user-facing pitch and controls; `SESSION.md` keeps only what changed on the active branch.

- Playable vessels: **Nobara** (nails, hammer, Hairpin, Resonance, traps, Black Flash path), **Todo** (Boogie Woogie swap, thrown stone anchor, triple cyclic swap, heavy vanilla melee, shared Black Flash bridge), **Megumi** (two independently mortal Divine Dogs, Shadow Trap, Shadow Move, Shadow Drop), and **None**
- Nobara controls: `R` directed Hairpin, `B` Mega Nail (merges the aimed target's embedded nails into one delayed piercing strike; an empty B shows the router fallback), `Shift+R` Self Resonance, `Shift+B` Nail Trap, hammer left click contextual melee. Her embedded nails also feed a client-only target ESP visible to the local Nobara alone
- Todo controls: `R` Boogie Woogie (server-authoritative self↔target swap; no target under the crosshair is a plain refusal — the old thrown-mark fallback is deleted), `Shift+R` feint clap (the full clap performance with no swap behind it; the modifier is the sneak key, so the cast is visibly crouched — an accepted tradeoff recorded in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)), `B` pair swap (first cast marks a bystander, second swaps the pair while Todo stays put), `Shift+B` triple cyclic swap (with a live pair selection A and a second aimed target T: Todo → A's position, A → T's position, T → Todo's position; with no selection it refuses instead of degrading into `B`), `V` throws the stone or swaps Todo with it, `Shift+V` swaps the aimed target with the stone; vanilla melee with Todo attribute modifiers
- Megumi controls: `R` atomically summons both Divine Dogs or recalls the surviving pack; `Shift+R` commands every living dog to attack one server-selected eligible target; `B` Shadow Trap (a static slowing pool pinned under the aimed target's feet); `Shift+B` Shadow Move — one travel technique with three contextual modes: tap with an eligible target under the crosshair emerges behind its live back, tap at an aimed surface is a free step, hold past 6 ticks is a deep submerge that lasts until release, a repeat tap or 50 ticks; `V` Shadow Drop (a telegraphed zone that follows the aimed target overhead for 1 s, then drops a 1–3 block weighted volley scattered inside the disc — sand 30% / gravel 25% / clay 25% / anvil 20% per block, `disableDrop()` so nothing ever litters the world, crush damage 1.0/5 soft and vanilla 2.0/40 anvil). Summon has no cooldown; recall is 240 ticks; final pack loss is 600 ticks; Sic is 30 ticks; trap is 200 ticks on `SECONDARY`; the move is 120 (tap) / 200 (hold) ticks on `SECONDARY_SNEAK`; the drop is 60 ticks on `TERTIARY`
- The shadow grip is a vanilla-mirrored `megumi_shadow_grip` attribute effect (`MOVEMENT_SPEED` −75%, `JUMP_STRENGTH` ×0 via `ADD_MULTIPLIED_TOTAL`), applied every server tick inside the pool with an 8-tick duration and expiring by itself — a gripped body walks out slowly; never a stun, never a manual effect scrub. The submerged walk keeps full vanilla collision, refuses attacks and other casts through the router's lock gate, and hides the whole body through the shared `HiddenBodyRenderGate` (TTL set fed by the ripple cues — the first fires when the body actually hides, so the sink stays watchable; fail-open on a lost packet) plus a vanilla invisibility flag re-asserted every tick while under. The dive is smooth in both persons and continuous between ticks (all three readers sample `ShadowBodySink` at the frame's fractional game time): the third-person body eases down 1.9 blocks across the sink, `VfxCameraChannel.diveOffsetBlocks(partialTick)` dips the first-person camera and `MegumiShadowDiveHud` veils the screen. Trap-family shadow pools (trap and drop zone) render void-black — vertex alpha 255 for their whole life, only the radius animates — and dissolve only through the closing sweep's 255→0 fade; the dogs' decorative summon pool keeps its own fade. `SECONDARY_SNEAK_HOLD`/`SECONDARY_SNEAK_RELEASE` are wire ids 6 and 7, and `TERTIARY` (`V`, instant send, no sneak pairing) is wire id 8 — appended, never renumbered; the release slot deliberately carries no cooldown so the end of a held gesture always reaches the router
- The second technique key owns the kit's only hold gesture, and only while sneaking: a plain press casts `SECONDARY` instantly, a sneaking press buffers and resolves on release (tap, ≤6 ticks) or at the threshold (hold). Todo's triple-swap tap and Nobara's nail trap therefore confirm on release — up to 6 ticks later than a plain press, accepted for one shared input grammar
- Each vessel binds one server and one client definition — see "The Vessel Seam" below, which owns that rule
- Each vessel's slots are mapped by its own router — `NobaraAbilityRouter`, `TodoAbilityRouter`, or `MegumiAbilityRouter`, reached through the vessel's definition — over an exhaustive `CharacterAbility` switch, so a new slot fails compilation instead of falling into whichever arm a `default` would have picked. Nobara's router additionally owns her stagger check and her single fallback message, because both are hers alone and neither belongs in the shared executor. `CharacterAbility` network ids are wire format: append, never renumber. `CharacterAbilityPayload` also carries the vessel the client believed in, and the server refuses a cast whose claim disagrees with the stored selection
- Megumi's pack is runtime-only and identified by owner UUID plus summon token, never entity id. One guarded teardown owns every destructive cleanup. A living sibling survives reconcile; a final loss starts the longer cooldown. Leash recovery checks every 10 ticks beyond 32 blocks and teleports only onto a loaded, floor-supported, collision-free, non-fire/non-lava point within radius 3; without one, normal pathing continues unchanged
- Divine Dogs materialize for 16 ticks and manually recall for 12 ticks; AI, combat collision, attacks and incoming damage are disabled outside `ACTIVE`. The cooldown HUD reads only the existing mirrored `PRIMARY` deadline and appears only while Megumi has no pack and that deadline remains positive
- Divine Dogs have 60 HP, 3 damage and 0.34 movement speed. Only an exact Sic target can trigger each dog's independent server pounce: inclusive 3-8 block range, LOS, 80-tick per-dog cooldown, 16-tick flight timeout, steering toward the target until a collision or landing, one owner-attributed 5-damage hit, 6 ticks of stagger and a 2.4-strength knockback after accepted damage
- Todo has no starter loadout and no items in this slice: the stone is a served entity (`todo_stone`), never an inventory item — thrown by `V`, existing only in flight. Baseline tuning lives in `TodoProfile`
- `CharacterAbility.USE_CONTEXT` is the sixth slot and the only one whose key the game already owns. The **first** right click of a pair is vanilla's and is handled before the mod sees it — accepted, not worked around; the input layer sends the slot only once a pair completes, so an ordinary right click costs no packet. Since the stone rework no vessel answers it — every router returns `false`, and the slot stays reserved wire format
- The stone exists only while flying: one per Todo, a straight slow line (no gravity, no damage, no marks, entities are ignored, water and fire are not terminal), and it ends on lifetime expiry, on block collision, on void, and on every cleanup exit — death, respawn, vessel change, dimension change, disconnect, server stop, or the entity going missing from a *loaded* chunk. A collision is a vanish, never an anchor, and portals refuse it outright. Repeat `V` while it flies swaps Todo with the stone at the stone's current position; `Shift+V` swaps the aimed target with it while Todo stays put. A swap exchanges positions only — every body and the stone keep their own motion, and the stone keeps flying with its remaining lifetime from the old center of whoever it displaced
- A completed swap opens a 24-tick window through the `todo_swap_momentum` effect: the next confirmed melee hit lands at ×1.25 and staggers for 8 ticks, then the window closes. A miss or a blocked hit does not spend it. The damage is an `ATTACK_DAMAGE` modifier on the effect itself, never a second damage instance. Granted only by the swaps Todo makes with his own body — the aimed `R` swap and the `V` stone self-swap — never by `Shift+V`, the pair swap, the triple swap or the feint
- Everything a **completed** swap shows rides on `SWAP_AFTERIMAGE` / `SWAP_ARRIVAL` cues that `TodoFakeClapRuntime` never emits. The feint shares the `BOOGIE_WOOGIE` clap cue by design, so nothing that only a real swap earns may be added to that recipe
- Nobara, Todo and Megumi render through GeckoLib replaced-player renderers declared by their client definitions. Megumi cycles `punch_1`, `punch_2` and `kick` on ordinary client-side swings, and his server-confirmed summon cue triggers `summon_divine_dogs`; this presentation state changes no melee gameplay. Vessel renderers are collected by `CharacterGeoRenderers` and dispatched from `CharacterRenderDispatchMixin`
- Vessel render code is shared: `CharacterPlayerGeoRenderer` (render entry + pose-stack guard), `CharacterPlayerGeoModel` (arm pose + clamped head look), `CharacterHeldItemLayer` (hand attachments). A new vessel supplies assets and hooks, not a copied render stack
- Transient combat VFX: **VFX Core** only (`VfxCue` → director → recipes); each vessel registers its own recipe pack from its client definition's `registerClientHooks()` — the aggregate `JujutsuVfxRecipes` is deleted
- Player menu: **Key N → ClickGui**; sidebar **Characters** (live) + **Soon...** placeholders (non-clickable); the panel drags by its header band with left mouse or anywhere on it with middle mouse, its position is session-only, and the vanilla crosshair is declined while the menu owns the screen
- Character apply: `SelectCharacterPayload` C2S, server-authoritative; selection persists via Fabric Data Attachment API. Nobara's starter loadout is deliberately re-applied on **every** selection — it is idempotent, filling only a missing hammer, doll or nails, so a lost kit is restored without duplicating held items. The persisted starter claim is recorded for every vessel and currently read by nothing (see E12 in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md))
- UI theme: `ClickGuiTheme` owns only the easing; the accent/warmth it eases toward come from each vessel's client definition (Nobara orange, None slate)
- Ordinary loaded embedded nails: 1200-tick TTL, maximum 30 per owner, resolved through `EmbeddedNailRegistry`
- Resonance global server hit-stop: see the accepted decision in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md), which owns that rationale
- **No** cursed-energy resource bar in the current kit
- **No** Neon Dashboard menu; that path is retired — the `V` key now carries the shared third-technique slot, not a menu

#### Todo baseline numbers (from `TodoProfile`)

| Parameter | Value |
|---|---:|
| `BOOGIE_WOOGIE_RANGE` | 20.0 blocks |
| `BOOGIE_WOOGIE_COOLDOWN_TICKS` | 60 (3 s) |
| `FAKE_CLAP_COOLDOWN_TICKS` | 20 (1 s) |
| `PAIR_SWAP_COOLDOWN_TICKS` | 100 (5 s) |
| `PAIR_SELECTION_TTL_TICKS` | 100 (5 s) |
| `STONE_SPEED_BLOCKS_PER_TICK` | 0.175 (3.5 blocks/s) |
| `STONE_LIFETIME_TICKS` | 100 (5 s) |
| `STONE_HITBOX_SIZE` | 0.35 |
| `STONE_THROW_COOLDOWN_TICKS` | 10 (0.5 s, anti-double-click only) |
| `STONE_SELF_SWAP_COOLDOWN_TICKS` | 60 (3 s) |
| `STONE_TARGET_SWAP_COOLDOWN_TICKS` | 100 (5 s) |
| `STONE_SWAP_RANGE` | 32.0 blocks (Todo↔stone distance, both swaps) |
| `STONE_TARGET_RANGE` | 20.0 blocks (Shift+V crosshair reach) |
| `TRIPLE_SWAP_COOLDOWN_TICKS` | 160 (8 s) |
| `PAIR_MARK_PULSE_TICKS` | 20 (selection mark re-emit period) |
| `SWAP_MOMENTUM_DAMAGE_MULTIPLIER` | 1.25 |
| `SWAP_MOMENTUM_WINDOW_TICKS` | 24 (1.2 s) |
| `SWAP_MOMENTUM_STAGGER_TICKS` | 8 |
| `MELEE_DAMAGE_MULTIPLIER` | 1.50 |
| `ATTACK_SPEED_MULTIPLIER` | 0.85 |
| `STAGGER_DURATION_MULTIPLIER` | 0.50 |
| `BLACK_FLASH_CHANCE` | 0.10 |
| `BLACK_FLASH_DAMAGE_MULTIPLIER` | 1.75 |
| `BLACK_FLASH_STAGGER_TICKS` | 14 |
| `SAFE_POSITION_HORIZONTAL_RADIUS` | 1.0 block |
| `SAFE_POSITION_UPWARD_BLOCKS` | 3 |
| `WORLD_BORDER_MARGIN` | 0.05 |

`TodoProfile` is the source of truth for these values; do not restate them elsewhere.

#### Boogie Woogie destination policy (deliberate)

`TodoBoogieWoogieRuntime.findSafeDestination` checks only world bounds, chunk load, world border, and solid-block collision. There is **no floor requirement** and **no third-party entity-occupancy gate** — air, water, crawl, and flight destinations are all valid by design. This is intentional for the current 1–2 player target, not an oversight; the residual debt is tracked in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md). Do not add an occupancy gate without a product decision.

Strictness is the line between Todo's own feel and everyone else's safety, and it is drawn per **body**, not per cast. `SOFT` keeps the last-resort fallback to the exact requested point, which skips `noBlockCollision`; exactly one destination in the whole kit uses it — Todo's own arrival in the aimed swap, because that fallback is why mid-air swaps feel good and the risk is his to take.

Every other body gets `STRICT`, including the aimed swap's **target**: the pair swap's two participants, all three bodies of the triple cycle, Todo arriving at the stone (`V`), and the target arriving at the stone (`Shift+V`). `STRICT` is not a floor requirement — air, water and crawl spaces stay valid — it only refuses a point inside geometry, judged by the body's own bounding box and inside the world border. When no such point exists the whole cast cancels through preflight rather than forcing anyone into a wall, and a refused cast moves nothing at all.

There is deliberately **no defaulting overload** of `findSafeDestination`. One existed and silently supplied `SOFT`, which is how the target came to be placed through the unchecked fallback for the whole first slice. Every caller states its strictness at the call site, so the unsafe choice cannot be made by omission. Do not reintroduce a default, and do not relax `STRICT` to make a cast succeed more often.

#### The empty-hands gate is absolute (deliberate)

Any item in either hand refuses every Todo clap, checked in one place — `TodoSwapGates`. Both the real swap and the feint read that same truth table, because a feint that were allowed under conditions the real swap refuses would announce itself. The stone shares only the caster-state half of that table (spectator, dead, unsafe transport, staggered — refused silently): it is an entity thrown by an ability cast, not an inventory item, so hands stay deliberately ungated for it. Do not turn the rule into a list of permitted items.

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
| **Skills** | Match task → skill and follow its checklist. This repository ships its own under `.claude/skills/`; those are versioned with the architecture they describe and are authoritative for it. User-level skills may also be present but belong to other projects — do not assume a named skill exists without checking. |
| **mcp-runner** | Launch a narrowly relevant public MCP server in the sandbox when it adds real capability; Context7 is useful for current library APIs. Do not install arbitrary servers just for quantity. |
| **mcpvault** | Optional external Obsidian vault. Use it when connected, but never treat an unavailable local vault as a blocker or as newer than the versioned repo Codex. |
| **codegraph** | Structural “where is / who calls / architecture” questions when `.codegraph/` exists. Build the index with `codegraph init`; query with `codegraph explore "<question or symbol names>"` for the relevant symbols' source plus the call paths between them, or `codegraph node <symbol-or-file>` for one symbol's source and callers. Prefer it over grep for “who calls this”. The index is local-only and never committed — only `.codegraph/.gitignore` is tracked. Re-run `codegraph init` after a refactor, or the graph answers from stale symbols. |
| **filesystem/search** | Authoritative fallback for current implementation facts. |
| **Repo docs** | `AGENTS.md`, active `SESSION.md`, `docs/README.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`. |

If an optional MCP server, vault, or code graph is unavailable, say so once and continue with the repository. Current code and tests remain authoritative.

## Documentation Authority

Use this order when documents disagree:

1. Current code and passing tests for implemented behavior.
2. `AGENTS.md` for durable product and engineering rules.
3. Active `SESSION.md` for the branch and latest handoff.
4. Versioned Codebase Codex MOC for architecture and maintenance navigation.
5. `docs/KNOWN_ISSUES.md` for unresolved debt.

The repository intentionally keeps only current documentation. If a past decision still matters, summarize its durable conclusion in the relevant current document rather than restoring an archive. The documentation audit runs inside `./gradlew qualityGate`; for a faster loop after documentation-only changes, run `./gradlew auditDocumentation` or `python3 tools/audit_docs.py` directly.

## Brainstorming Gate

Before implementing **new** gameplay systems, characters, VFX architecture, networking contracts, or major UI shells:

1. Clarify the design goal.
2. Compare 2–3 viable approaches.
3. Pick the simplest approach that can still become the long-term template.
4. Write the approved design/spec before implementation.
5. Only then write an implementation plan.

Skip the full gate for trivial fixes, copy, polish, or changes the user already specified precisely.  
No code-first experiments in the main product path unless the user explicitly asks for a throwaway prototype.

## Knowledge Bases

- **Versioned repo Codex:** start at `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; this is the maintained architecture index shipped with the repository.
- **ProjectJJK provenance:** use `docs/PROVENANCE.md` and `docs/THIRD_PARTY_NOTICES.md` for the current permission and replacement policy.
- An external Obsidian vault may contain richer research when mcpvault is connected, but it is optional and must be cross-checked against current code before use.
- Treat `VERIFIED` / `INFERRED` / `UNKNOWN` exactly as defined in `01-meta/Citation-standard.md`. Do not implement `UNKNOWN` as fact.
- Every meaningful gameplay, character, VFX, UI, networking, asset, or architecture change must update the relevant current Codex note and MOC link.
- Mention exact note paths when a knowledge-base claim influences implementation.

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
- Characters tab is the vessel select path; do not reintroduce the Neon Dashboard menu without an explicit product decision (the `V` key is the third technique key now, not a menu key).
- Panels: project SDF (`SdfRenderer`) via `Render2D` adapters; text: MSDF where wired. Do not claim full original Rich GL pipelines as live.
- Vessel selection must go through server payloads — no silent client-only authority.
- Panel geometry lives in **one** GUI-scaled space: mouse coordinates, the screen's `width`/`height`, and the SDF surfaces already agree, so never convert a drag offset or a hit test through `Render2D.getScaleMultiplier()`. Rendering and hit testing must read the panel origin from one accessor, or the two desynchronize. Panel position is session-only — the project has no UI-state persistence.
- The ClickGui rasterizes **immediately** (`SdfRenderer.flush()` during Screen rendering), while vanilla HUD elements are only recorded during `Gui.render` and rasterized last by `GuiRenderer`. A vanilla HUD element therefore composites **over** the finished menu and no scrim alpha can hide it. Suppress one by conditionally declining its draw through `HudElementRegistry.replaceElement` — never by removing the element, hiding the whole HUD, or adding a HUD mixin.

## The Vessel Seam (non-negotiable)

**Shared code never asks which character a player is. It asks the vessel.**

A vessel is one `JujutsuCharacter` constant, one `CharacterDefinition` bound in `JujutsuCharacters`, one `CharacterClientDefinition` bound in `JujutsuCharacterClients`, and assets. Adding one changes **no shared file** beyond the enum and those two registry arms.

Verified, not asserted: adding a constant and compiling produces exactly two errors, one per registry, and none anywhere else. Binding it to the wrong definition compiles and fails `testCharacterDefinitions` instead. Re-run that probe if you suspect the seam has eroded.

Two guarantees, not one. **Compile-time** is the exhaustive `switch` with no `default`, in those two registries only. **Build-time** is the registry tests, covering what a switch cannot express — that a definition claims the constant it was bound to, that no client type reached `src/main`, that every `register()` under a vessel's package is called, and that each card lists the slots its router answers. They derive from the enum and the source tree, so a new vessel needs no test edit provided its runtimes live in `jujutsu/mod/character/<id>/` and its definition is `<Id>Definition.java`.

Therefore, without exception:

- No per-character `if` or `switch` in a **dispatch** file — anything deciding which character this is. Ask the definition. Content registries (`JujutsuItems`, `JujutsuEntities`) are a different category and may of course gain a vessel's items.
- No payload, cooldown store, or input path for one vessel. `CharacterAbilityPayload` and `CharacterAbilityCooldowns` are shared and already key on the vessel.
- No vessel runtime registered from `JujutsuMod` or `JujutsuModClient`. Use `registerServerHooks` / `registerClientHooks`; both init files loop their registry.
- No client type in `src/main` — a dedicated server loads that source set and every implementation of `CharacterDefinition`.
- No roster, skin, renderer or VFX metadata written twice. Declare it once on the definition.

Contract: Codex note `02-architecture/Vessel-definitions.md`. Procedure: the `add-vessel` skill. Three deliberate per-vessel exceptions remain and are listed in that note (the stone rework retired the marker item's gate together with the item); do not add a fourth without recording why.

## Mandatory VFX Core Contract

- Read vault note `jujutsumod-codebase-codex/04-client-vfx/VFX-core.md` before designing, implementing, or reviewing combat visuals.
- Every transient combat effect must use: server-confirmed action → `VfxCue` → `VfxDirector` → `<Character>VfxRecipes` → director-owned channels; cues are visual-only.
- For each character, add `<Character>VfxIds` and `<Character>VfxRecipes`, then register them from that vessel's `CharacterClientDefinition.registerClientHooks()` — installed once by `JujutsuCharacterClients.registerAll()` at client init, after `VfxDirector.initialize()`.
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

**Done for v2 slice:** the second character (Todo) built on the same contracts — proving the template is reusable, not just theoretical.

**Next milestone focus:** in-game smoke and feel polish for the three shipped vessels, not a broad unfinished framework. Megumi is the third-vessel seam proof and deliberately adds no universal summon abstraction.

## Adding a Character

Use the **`add-vessel`** skill in `.claude/skills/`. It owns the procedure end to end — research and design, scaffold, abilities, client presentation, testing, documentation — along with the readiness checklist, the commit order, and the explicit list of things a new vessel must never do. It is versioned with the architecture it describes, so it is authoritative over any workflow restated elsewhere.

What this file still owns and the skill defers to: the seam rule above, the VFX Core contract, the design principles below, and the brainstorming gate. Design approval comes before scaffolding, always.

## Code Organization Direction

Do not invent empty packages. Prefer existing roots; add packages only when a feature needs them.

Typical live areas:

- `jujutsu.mod.character` / `…nobara.projectjjk` / `…character.todo` / `…character.megumi` — server vessel definitions (`CharacterDefinition`, `JujutsuCharacters`), vessels and combat runtimes
- `jujutsu.mod.vfx` + `jujutsu.mod.client.vfx` — cues, director, recipes, channels
- `jujutsu.mod.network` — typed payloads
- `jujutsu.mod.registry` — items, entities, particles, sounds
- `jujutsu.mod.client.character` — client vessel definitions (`CharacterClientDefinition`, `JujutsuCharacterClients`), roster entries, client selection mirror
- `jujutsu.mod.client.rich` — ClickGui / modules / theme
- `jujutsu.mod.client.ui.msdf` + `…ui.neon.render` — MSDF + SDF backends
- `jujutsu.mod.client.input` — keybinds
- `jujutsu.mod.client.render` + `…render.nobara` / `…render.todo` — entity/player/item and GeckoLib character renderers

## Asset Policy

- Keep source/reference assets outside pure runtime packs when possible.
- Runtime sounds must be OGG Vorbis.
- User-visible text must be localizable for real UI.
- VFX must read in motion, not only in screenshots.
- Never copy anime assets into the repo unless licensing is explicit.
- Prefer original/inspired designs over copyrighted rips.
- ProjectJJK placeholder policy, the retained upstream notice, and the Rich-Modern provenance question are owned by [docs/PROVENANCE.md](docs/PROVENANCE.md) and [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md). Read them before touching anything under a `projectjjk` path or under `client/rich`.

## Verification Policy

One command owns the word "verified":

```bash
./gradlew qualityGate
```

It runs `check` (both source sets compiled, the JUnit suite, and every custom `JavaExec` verification program), the documentation audit `tools/audit_docs.py`, and an audit that every verification `JavaExec` task actually enables assertions. Nothing may be called done, fixed, passing or verified without a green run of exactly this command. While working, run the narrowest task that proves the change; run the gate before any handoff. Never claim a verification you did not run.

**Write new tests as JUnit 5.** `fabric-loader-junit` boots the loader for the test JVM, so a JUnit test can bootstrap Minecraft and exercise registries, codecs and buffers for real — which is the only way to stop asserting behaviour by grepping source text. The existing `JavaExec` programs stay and migrate gradually; [docs/BUILDING_IN_SANDBOX.md](docs/BUILDING_IN_SANDBOX.md) owns the migration order and the reason four state-holding classes go last.

**What a green gate proves, and what it does not.** It proves the shape of the code, the contracts between vessels, and the pure logic reachable without a world. It does **not** prove behaviour inside a running Minecraft world: nothing in the suite constructs a `ServerLevel`, so no automated check casts an ability, moves a body, or renders a frame. Until GameTest coverage exists (E1 in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)), in-world behaviour is proven only by the client-smoke checklist. Do not claim in-game behavior from a green gate.

**Every new gate rule ships with proof that it can fail.** A check only ever observed green may be vacuous — a misspelled package in an architecture rule passes against zero classes. Break the thing the rule guards, record the mutation and the resulting failure message in the commit body, then restore. A rule with no recorded red run is not a rule.

[docs/BUILDING_IN_SANDBOX.md](docs/BUILDING_IN_SANDBOX.md) owns the full command recipe — gate composition, focused verification tasks, sandbox setup, and the client-smoke checklist. Use it instead of restating commands here.

## Communication

- User writes Russian; answer in Russian (technical terms may stay English).
- Keep explanations direct and practical.
- Surface tradeoffs before implementing non-trivial work.
- If unknown, verify from repo/vault before asserting.
- At handoff: exact files/branches, commands run, jar path if deployed.

## Resolved Decisions (do not re-open casually)

1. Hybrid fidelity: Minecraft-native feel + ProjectJJK-inspired Nobara contracts where verified.
2. Multiplayer-safe networking from the start (typed payloads, server authority).
3. First template character: **Nobara**; second vessel: **Todo**, built on the same contracts instead of a new framework.
4. No universal cursed-energy bar in the current kit.
5. Transient combat VFX: **VFX Core only**.
6. Product menu: **ClickGui (N)** with Characters select; Neon V dashboard retired.
7. Character selection persists through Fabric Data Attachment API; starter kits are restored idempotently on every selection rather than claimed once (the persisted claim is recorded but unread — see E12).
8. Loaded ordinary embedded nails use a TTL, a per-owner cap, and an owner index — exact numbers in “Current slice (facts)” above.
9. Resonance global server hit-stop stays intentional unless the product target changes — rationale in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).
10. Todo differs from Nobara on positioning and raw melee, not on a damage-projectile kit; his stone is an inert anchor for swaps, deals nothing, and he intentionally ships without a starter loadout.
11. Boogie Woogie destinations have no floor check and no entity-occupancy gate — see the destination policy above.
12. The stone exists only in flight: terminal collision is a vanish, never a landed anchor. One stone per Todo; `V` with a live stone is the self-swap, never a second throw. Rebalancing levers are split out (`STONE_SELF_SWAP_COOLDOWN_TICKS`, `STONE_TARGET_SWAP_COOLDOWN_TICKS`, `STONE_SWAP_RANGE`, `STONE_LIFETIME_TICKS`) so pricing it later is a number, not a rewrite.
13. The swap's momentum bonus is carried by a `MobEffect` attribute modifier, not by a second damage instance. It rewards only swaps Todo makes with his own body (`R`, `V` self-swap). Two known limits are accepted and documented in `TodoSwapMomentumRuntime`: a sweeping attack keeps the boost on later victims after the window is spent, and on bare fists ×1.25 is worth about a third of a heart, so the stagger is the payload.
14. Megumi is the third vessel. Divine Dogs are two transient vanilla `Wolf` bodies in one runtime pack; the slice adds no persistent Ten Shadows state or shared summon hierarchy.
15. Megumi's shadow kit: the second technique key owns the kit's only hold gesture (sneaking press buffers ≤6 ticks); the submerge damage gate deliberately swallows fire and lava for its ≤2.5 s window (mobility payoff priced by the 10 s cooldown); item use under the shadow stays unlocked as a recorded open question.
16. Todo's `Shift+B` is its own slot (`SECONDARY_SNEAK` reaches his router; the `canonicalSlot` fold is deleted) and runs the triple cyclic swap with the fixed direction Todo→A→T→Todo, priced separately from the pair swap. All three bodies preflight STRICT before anything moves; a mid-commit failure rolls back every moved body and logs an error. `Shift+V` is the appended `TERTIARY_SNEAK(9)` wire id; ids 0–8 kept their numbers.

## Open Questions (real remaining)

1. Whether ClickGui grows more live tabs later or stays Characters + Soon placeholders.
2. How far to push Rich visual parity vs keep SDF/MSDF adapters long-term.
3. When temporary ProjectJJK placeholders are replaced and what provenance evidence is needed for a public release.
