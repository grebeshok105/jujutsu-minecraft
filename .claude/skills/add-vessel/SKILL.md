---
name: add-vessel
description: Use when adding a new playable character (vessel) to the Jujutsu Minecraft mod from a ready DESIGN SPEC — a new sorcerer, technique kit, roster entry, player presentation, or ability set. Carries the spec end to end: scaffold, abilities, presentation, tests, qualityGate, in-game MCP verification, reviews, docs, PR. Also use when reviewing or planning such work.
---

# Add Vessel

An automation skill, not an architecture book. Input is a ready **DESIGN SPEC** from the user; output is a playable, tested, reviewed, in-game-verified vessel with docs and a PR. Work it autonomously for hours without pinging the user — stop only for a true blocker or a missing design decision.

Contract owner is the root `AGENTS.md`. Nothing here overrides it; where this skill and `AGENTS.md` disagree, `AGENTS.md` wins.

## When not this skill

No DESIGN SPEC yet and the user asks to invent a vessel from scratch → the brainstorming gate from `AGENTS.md` runs first (research, approaches, trade-offs, final spec). This skill starts when the spec exists.

## Input contract: is the spec executable?

Check fast, before any plan. A spec is executable when it answers every question **this** vessel raises — never demand irrelevant sections:

- identity and combat fantasy (one sentence each: fantasy + counterplay);
- abilities: what each does, success and failure behavior, what the player is told on refusal;
- base slots used (see §3) and which stay deliberately empty;
- controls and input behavior (tap / hold / contextual, if any);
- targeting: rules, range, line-of-sight, invalid targets;
- cooldown or resource behavior per ability;
- interactions between the vessel's own abilities;
- needed content: items, entities, models, animations, VFX;
- cleanup and lifecycle, if the vessel holds state;
- edge cases that matter for this kit;
- acceptance criteria — each one checkable, ideally with how.

Missing a **mandatory** design decision that context cannot supply → stop and ask the user, naming exactly what is missing. Anything technical (code placement, abstractions, existing mechanisms) is yours to resolve — never ask.

Do not re-run brainstorming over a complete spec and do not re-open settled decisions without cause.

## Happy path

```
DESIGN SPEC → spec check → repo research → /writing-plans → implementation plan
→ scaffold → abilities → items/entities → presentation/VFX → tests
→ mutation checks → subagent reviews → qualityGate → launch → MCP in-game verification
→ fixes → focused re-check → full qualityGate on the final state → docs → git/PR → final acceptance report
```

`/writing-plans` is the planning skill: call it after reading the spec and researching the repo. If a current implementation plan already exists, use it — never write a second one for ritual. The plan must cover the whole spec: implementation, tests, VFX/assets, runtime verification, docs, reviews. After the plan, keep executing to DONE without waiting for user approval between stages.
SPEC and plan stay separated: the spec owns behavior and fantasy, the plan owns technical realization. Never quietly change design because it is easier to build.

## 1. Research the current repo

Read before planning. Paths first, then the three reference vessels:

| Read | Why |
|---|---|
| `src/main/java/jujutsu/mod/character/CharacterAbility.java` | all 10 slots + wire ids (append-only) |
| `src/main/java/jujutsu/mod/character/JujutsuCharacter.java`, `JujutsuCharacters.java` | enum + server registry |
| `src/client/java/jujutsu/mod/client/character/JujutsuCharacterClients.java` | client registry |
| `src/main/java/jujutsu/mod/character/CharacterDefinition.java`, `src/client/java/jujutsu/mod/client/character/CharacterClientDefinition.java` | full hook lists — read both, they are short |
| `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md` | the seam contract + deliberate exceptions |
| `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/Vessel-render-stack.md` | current render contract (vanilla + bridge) |
| `docs/KNOWN_ISSUES.md` | accepted trade-offs — never "fix" a recorded decision |
| `.omp/mcp.json` + `.claude/skills/mcp-lane-launch/SKILL.md` | how the live game is launched (`runClient -PmcpSpike`) and driven over `mc-world`/`mc-client` |

Reference vessels by problem, not by admiration:

- entities, packs, lifecycles, hold gestures → `character/megumi/` (`MegumiDefinition`, `MegumiAbilityRouter`, `MegumiProfile`, `MegumiDivineDogEntity`, `MegumiShadowMoveRuntime`);
- teleportation, placement safety, rollback, thrown transient entity → `character/todo/` (`TodoSwapPlan`, `TodoStoneEntity`, `TodoStoneRuntime`, `TodoProfile`, `TodoSwapGates`);
- items, starter kit, technique weapon, client HUD contributions → `character/nobara/` (`ProjectJjkNobaraLoadout`, `ProjectJjkNobaraProfile` in `projectjjk/`, `client/character/nobara/`).

Load `skill://writing-plans` before writing the plan. Use codegraph/LSP for structure and references, knowledge-rag (`.omp/RULES.md`) for past decisions.

## 2. The seam (practical version)

Shared code asks the vessel, never which character the player is. Concretely: adding a vessel edits the enum, one arm in each of the two registries, content registries for its items/entities, lang, docs — and nothing else shared. If your change needs a per-character `if`/`switch` in a dispatch file (executor, cooldowns, selection, keybinds, init files, render dispatch, theme) just so shared code learns the new name, stop: the seam is wrong, not the vessel. Content registries (`JujutsuItems`, `JujutsuEntities`) are not dispatch — using them is expected.

`ProjectSanityTest`, `VesselBoundaryTest`, `SourceBoundaryTripwireTest` encode the negative side of this contract (retired subsystems must stay gone, vessel classes stay in vessel packages). Read their pinned lists before placing a class — they tell you where things may not live.

## 3. Slot model

Base model per vessel: **three action slots, each with a Shift variant — six standard combinations** (today `R` → `PRIMARY`, `B` → `SECONDARY`, `V` → `TERTIARY`, plus `_SNEAK`). This is the base, not a ceiling: the enum holds 10 slots and appends without renumbering (`CharacterAbilityWireFormatTest` pins ordinal == network id).

Contextual slots a design may claim:

- `ATTACK_CONTEXT` — left click holding a technique weapon. Only slot not fully behind the seam: `JujutsuKeybinds.isTechniqueWeapon` still names Nobara's hammers, so a melee-tool fantasy needs a design-stage decision, not a mid-implementation surprise.
- `USE_CONTEXT` — two right clicks inside `USE_PAIR_WINDOW_TICKS`. Vanilla owns the first click; the input layer sends only completed pairs. No vessel answers it today; every router refuses it explicitly and the wire id stays reserved.
- `SECONDARY_SNEAK_HOLD` / `SECONDARY_SNEAK_RELEASE` — sneak-`B` buffered in `JujutsuKeybinds` (`SECONDARY_HOLD_THRESHOLD_TICKS = 6`): tap resolves on release as `SECONDARY_SNEAK`, hold sends `HOLD` once at the threshold and `RELEASE` at key-up. The release carries no cooldown by convention (nothing ever starts one) so a gesture can always end; a release with no live server state is refused by the router.
- `TERTIARY` / `TERTIARY_SNEAK` — instant send, no hold pairing.

Key mapping lives in `src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java` and is a translator `(key, sneak) → slot` with no vessel knowledge. A vessel never edits it.

## 4. Scaffold

Create, in this order — the compiler is the checklist (adding the enum constant breaks exactly the two exhaustive registry switches; re-run that probe if you ever doubt the seam):

| # | Create / edit | Notes |
|---|---|---|
| 1 | enum constant in `src/main/java/jujutsu/mod/character/JujutsuCharacter.java` | lowercase id; build fails in the two registries until both arms exist |
| 2 | `src/main/java/jujutsu/mod/character/<id>/<Id>Definition.java` + arm in `JujutsuCharacters` | one field, one switch arm; `id()` must return the bound constant (registry test checks) |
| 3 | `src/main/java/jujutsu/mod/character/<id>/<Id>AbilityRouter.java`, `tryCast` delegates to it | `switch` over `CharacterAbility`, exhaustive, no `default`; unused slots answer `AbilityResult.UNHANDLED_FAILURE` explicitly (`SUCCESS` / `HANDLED_FAILURE` / `UNHANDLED_FAILURE` — see `AbilityResult.java`) |
| 4 | `src/main/java/jujutsu/mod/character/<id>/<Id>Profile.java` | every tuning number lives here, no magic constants in runtimes (legacy exception: Nobara's is `projectjjk/ProjectJjkNobaraProfile.java`) |
| 5 | `src/client/java/jujutsu/mod/client/character/<id>/<Id>ClientDefinition.java` + arm in `JujutsuCharacterClients` | required: `id()`, `rosterEntry()`, `accent()`, `moduleName()`, `moduleDescription`; defaults cover the rest; `moduleStartsEnabled` stays `false` (only NONE says yes) |
| 6 | card strings in `src/main/resources/assets/jujutsumod/lang/en_us.json` + `ru_ru.json` | both files, every key the card and every refusal message names |

Naming is load-bearing: server runtimes under `character/<id>/`, definition named `<Id>Definition.java` — the registry and boundary tests derive identity from the enum, so the convention is what keeps them passing with no edits. Client runtimes mirror under `client/character/<id>/` and `client/render/<id>/`.

First green milestone: scaffold compiles, `qualityGate` passes, kit still empty.

## 5. Abilities — which shared system for what

Reuse is the contract, not a preference. "If you need X, use Y first":

| Need | Use | Notes |
|---|---|---|
| input path | `CharacterAbilityPayload` only | carries the vessel claim; the server refuses stale-selection casts |
| gating (selection, cooldown) | `CharacterAbilityExecutor` | vessel-private rules go in your router before the switch |
| cooldowns | `CharacterAbilityCooldowns.start` + `JujutsuNetworking.sendAbilityCooldown` (key is player+vessel+slot) | start only after success; mirror to the client; never a second store |
| targeting | `jujutsu.mod.combat.TargetResolver` + your eligibility predicate | check E1b in `KNOWN_ISSUES.md` before touching ranking |
| teleport / placement | `TodoSwapPlan`: resolve all destinations, preflight, commit atomically, roll back on partial failure | never set coordinates directly |
| interrupts | `CombatStagger.GLOBAL` | no second stagger system |
| damage | `JujutsuDamageSources`, or the vessel-scoped sources class closest to your kit (e.g. `NobaraDamageSources`) | no third damage path |
| selection state | `CharacterSelectionManager` (server store); `CharacterSelectionView.of(player)` for gates | the view answers on both sides — vanilla calls item `use` on the client too, so a server-only item check desyncs prediction |
| attributes | `applyAttributes` / `removeAttributes` | remove exactly what you add; remove runs for every vessel on switch, unconditionally safe |
| stagger resistance | `adjustIncomingStaggerTicks` | argument is always > 0 |
| starter kit | follow `ProjectJjkNobaraLoadout` | ensure-don't-grant (`onSelected` runs every selection), drop at feet when full, count substitutes, never read `hasClaimedStarter` (E12 — deliberately reversed) |
| cleanup | one entry point per vessel, called from every trigger | player-keyed state: `SERVER_STOPPING`, `DISCONNECT`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, `onDeselected`; entity-keyed state adds `AFTER_DEATH` + `ENTITY_UNLOAD` |

Server authority is absolute: targets, validation, cooldown consumption, movement, cue emission — server. The client sends a request and draws what it is told. A cooldown is never consumed by a refused cast unless the spec says otherwise.

## 6. Items and entities

- Item: class → `src/main/java/jujutsu/mod/registry/JujutsuItems.java` (field, factory, `register()` line) → creative-tab entry when the item has no other acquisition path. Decide `stacksTo` against the gates that read the item at use time.
- Entity: class → `src/main/java/jujutsu/mod/registry/JujutsuEntities.java` (type, register, builder with `.sized` + tracking range). Transient things get **`.noSave()`** — a thrown object that saves outlives the session and haunts the world after a crash (`TodoStoneEntity` is the worked example). Give it its own flight/idle TTL; registry lifetime and gameplay lifetime differ.
- Old burns that still apply: double starter grant on re-select, full-inventory loss (drop at feet), client-predicted item use the server then refuses.

## 7. Presentation and VFX

- Transient combat effects only through cue → `VfxDirector` → recipe → director-owned channel. Cues are visual-only, emitted by the server after a confirmed action.
- Ids: `src/main/java/jujutsu/mod/vfx/<Vessel>VfxIds.java` (main side, both sides import). Recipes: `src/client/java/jujutsu/mod/client/vfx/<id>/<Vessel>VfxRecipes.java`, registered from your `registerClientHooks()`. No aggregate recipe file (pinned absent), no per-effect managers or mixins.
- Body: `skinAnimation()` adapter in `src/client/java/jujutsu/mod/client/render/<id>/` (`<Vessel>SkinAnimationAdapter` + model + animatable, mirroring the shipped three); `null` keeps the vanilla pose. Geo under `assets/<mod-id>/geckolib/models/` + `animations/`, mirroring an existing vessel's files. One skin texture via `playerSkin()` — it drives first-person hands and vanilla skin paths.
- Item assets come in threes (`items/<id>.json`, `models/item/<id>.json`, texture) — a missing one is a purple cube in game, not an error. Mirror a shipped item.
- Roster card lists what the router actually answers, in input order, with real key labels; `hudSlots()` + `maxCooldownTicks()` drive the in-world HUD from the same strip so the two cannot drift. Strings in both lang files.
- A file existing and compiling proves nothing visual: every VFX and every body change is checked by eye in game (§9). The retired replaced-player Geo stack under `archive/` is not a reference — do not copy from it.

## 8. Verification matrix

Derive the rows from the spec; every relevant aspect of the spec needs verification evidence in the matching row:
Automated tests are mandatory where the behavior is reasonably and stably checkable that way. Where automated verification is objectively inapplicable or adds no signal, say so explicitly in the row instead of writing a theater test. Gameplay-visible behavior always keeps its in-game column: no convenient unit test never excuses skipping runtime verification. Strictness stays: new behavioral or significant logic ships with tests.

| If the vessel has… | Tests | In game (MCP lane) |
|---|---|---|
| targeting | target rules, range/LOS/invalid-target units + regression | valid hit, refusal cases, refusal messages, no cooldown consumed on refuse |
| movement / teleport | destination validity, collision/safety, rollback on partial failure, server authority | all destinations incl. air/water/edge, blocked case moves nobody, relog consistency |
| entity | spawn, lifetime/TTL, death, unload, cleanup on all triggers, save/no-save | watch it live, expire, kill, unload chunk, restart without ghosts |
| item | acquisition, use, invalid use, full-inventory drop, client+server behavior, assets present | do each by hand, both sides agree, no purple cubes |
| VFX | cue→recipe path wired, no runtime errors | see it with eyes: timing, readability, no leaks after the effect ends |
| UI (card, HUD, overlays) | slot map bound arm by arm | render, input, GUI scales, states (cooldown/dead/spectator) |
| dedicated-server-sensitive scope (networking/payloads, client/main boundary, lifecycle, entity sync, server-only behavior, registration/loading) — only when the spec scope touches it | boundary suites (incl. the no-client-in-`src/main` pins) + headless-server lane | dedicated-server startup/runtime check when project tooling allows (`runGameTest` boots a headless server and exercises real loading) |
| selection / persistence | — (runtime-only by nature; row records why, not a fake unit) | select, relog, selection persists; starter restores missing pieces without duplicating held ones |

New behavioral or architecturally significant logic gets tests; bugfixes get regression tests where reasonable. Mutation where meaningful, per protected invariant — not per test method: several cases (or parameterized cases) guarding one invariant need a single red-proof showing that invariant actually fails when broken. Break the guarded thing, record the mutation and the failure in the commit body, restore. Red-proof proves the tests have value; it is not paperwork per method. No theater tests.

Reuse the boundary suites, never a parallel framework: `VesselBoundaryTest`, `SourceBoundaryTripwireTest`, `ProjectSanityTest`, `CharacterAbilityWireFormatTest`, the registry tests. Assert inside the arm, not anywhere in the file.

## 9. Run it

- Full gate (mandatory for a vessel): `./gradlew qualityGate` (`gradlew.bat` on Windows). Focused loop while working: `./gradlew check`, plus `runGameTest` / `runClientGameTest` / `auditDocumentation` as relevant. Green gate is necessary, never sufficient. Gate hygiene: any production or test code change after the last full gate invalidates its result. Fixes from runtime verification or reviews go through the focused re-check first, then a full `qualityGate` on the final state — the commit going into the PR must itself be green.
- In-game verification is mandatory and self-driven: launch via the `mcp-lane-launch` skill, drive `mc-world`/`mc-client` (select vessel, invoke abilities, read state, screenshots), execute every acceptance criterion plus the failure/edge rows from §8. The old "hand the user a smoke checklist" rule is deleted — the agent runs the smoke itself (`vessel-smoke-runner` documents the run shape).
- Recovery loops, no user round-trips:
  - check fails → diagnose → fix → rerun that check → run the regression around it → continue;
  - in-game bug → fix → rebuild/relaunch → re-verify the same scenario → continue.
- Stop only for a true blocker (unfixable from repo/docs/tools) or a missing design decision. A technical error is never a reason to return to the user.

## 10. Reviews

Independent subagent review before DONE — combine roles sensibly, but cover: architecture/seam compliance, implementation quality, spec compliance (every acceptance criterion traced), tests and verification honesty. Reviewers hunt real problems — spec violations, structural damage, regressions, missing tests — never `LGTM`. Findings are fixed with re-verification, or explicitly rejected with reasons when the reviewer is wrong. Substantial implementation changes after a review re-open review on the affected part only — trivia does not restart the whole pipeline.

## 11. Docs (same task, not a follow-up)

- New Codex note under `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/`, linked from `00-MOC.md` with metrics bumped (`tools/audit_docs.py` enforces the counts inside the gate).
- `SESSION.md`: what changed, what is verified, what remains for the user.
- Accepted trade-offs go to `docs/KNOWN_ISSUES.md` with reasons, never into code comments.
- No new markdown file when an existing place fits. Knowledge-rag / VaultMCP when available for past context; code and tests beat prose — fix stale docs instead of building on them.

## 12. Git

One vessel is one task and one PR. Small logical commits, each green on its own; no unrelated changes in the series. Verify at milestones (scaffold green, each ability green, presentation green), not only at the end. Suggested commit shape — a guide, not a ritual:

1. roster constant + both definitions + registry arms + profile + card strings (empty kit, green);
2. one ability per commit (runtime, router arm, cooldown, cue, test + red-proof);
3. items/entities if any (class, registries, assets, lang);
4. body (adapters, assets, skin);
5. VFX recipe pack;
6. docs (Codex note, MOC, SESSION, KNOWN_ISSUES).

PR title and body in Russian, written for the user per `AGENTS.md`: what, why, key decisions, verification (tests run, in-game checks, reviews), known limits.

## 13. Forbidden

- Per-character `if`/`switch` in any dispatch file — ask the definition.
- A vessel-private payload or input path — one shared path carries the vessel claim.
- A second cooldown store — the shared one already keys on player+vessel+slot.
- Runtime registration from `JujutsuMod`/`JujutsuModClient` — use `registerServerHooks` / `registerClientHooks`.
- Any client type in `src/main` — a dedicated server loads it; failure lands far from the cause.
- Roster, skin, VFX or renderer metadata written twice — declare once on the definition.
- Bypassing server validation or the shared ability path.
- Touching another vessel without its full in-game regression.
- A `default` arm in vessel or slot switches — it turns a compile error into a silent wrong answer.
- Transient entities without `.noSave()`; starter logic reading `hasClaimedStarter`; reaching into `archive/` for "reference" renderers.

## 14. Final acceptance

Walk every acceptance criterion from the DESIGN SPEC and record it as a trace — criterion → verification method → concrete proof → result. Proof names a real test, check, MCP scenario, or runtime observation; never a bare "verified manually".

| criterion | how verified | proof | result |
|---|---|---|---|
| e.g. `Shift+V` on invalid target keeps cooldown | JUnit + MCP in-game | `<Vessel>TargetRefusalTest` + lane run | PASS |

Example logic, not a template to fill blindly — the rows come from the spec.

Vessel DONE only when: spec fully implemented; plan executed; seams intact (boundary suites green); new tests exist and pass; mutation checks pass where applicable; full `qualityGate` green on the final PR state; Minecraft launches; MCP in-game acceptance done incl. edge cases; reviews done and findings resolved (post-review changes re-reviewed where substantial); docs and context updated; git/PR complete per project workflow; no known open spec items.
