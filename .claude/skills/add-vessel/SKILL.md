---
name: add-vessel
description: Use when adding a new playable character (vessel) to the Jujutsu Minecraft mod — a new sorcerer, technique kit, roster entry, player model, or ability set. Also use when reviewing or planning such work.
---

# Add Vessel

## The one rule

**Shared code never asks which character a player is. It asks the vessel.**

A new vessel is one enum constant, one server definition, one client definition, one line in each of two registries, and assets.

Two categories of shared file, and only one of them is closed:

- **Dispatch files** — anything that decides *which character this is*: the executor, combat modifiers, selection manager, cooldowns, keybinds, both mod-init files, the renderer map, the theme, the module tab, the roster, the skin mixin. **Never edit these.** If a vessel forces you to, stop: the seam is wrong, not the vessel.
- **Content and housekeeping** — `JujutsuItems`, `JujutsuEntities`, `build.gradle`, the two lang files, `00-MOC.md`, `AGENTS.md`, `SESSION.md`. A vessel with an item, an entity, a test or a string **must** edit these, and the phases below say when. That is not a seam violation; a shared registry of items is not a per-character branch.

The test is not "is this file shared" but "does this file ask who the player is". Nothing you add may make an existing file ask that.

## Source of truth, in order

Read these before designing. Do not restate them here; this skill is the procedure, they are the contract.

1. `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md` — the seam. Every hook, what guarantees what, the wire protocol, the deliberate exceptions.
2. `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md` — the step-by-step this skill drives.
3. `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/Vessel-render-stack.md` — the drawing half.
4. `AGENTS.md` → "Current slice (facts)" — durable product facts.
5. `docs/KNOWN_ISSUES.md` — accepted tradeoffs and open debt. Read before "fixing" something that is recorded as deliberate.

**If a document disagrees with the code, the code wins.** Establish the real contract by reading source, then fix the stale document in the same change. Do not build on a claim you have not verified.

---

## Phase 1 — Research and design

Do not write code yet. Produce a short written design and get it approved.

Ask the user, unless already answered:

- Who is the vessel, and which arc/form?
- What is the combat fantasy in one sentence, and the counterplay in one sentence?
- Which of the six input slots does the kit use, and what does each do?
- Stronger, weaker, or sideways compared to Nobara and Todo?
- Mandatory or not: custom model, animations, starter items, a technique weapon, a thrown entity, passive combat behaviour.
- If it has items: how does a player get them — a starter kit on selection, a creative-tab entry, both? Todo's marker needed an explicit `CreativeModeTabs.COMBAT` entry precisely because he has no starter kit.
- One PR, or scaffold first and abilities after?

**Two design constraints to check early.** `ATTACK_CONTEXT` — left click holding a technique weapon — is the only slot not yet fully behind the seam: `JujutsuKeybinds.isTechniqueWeapon` still spells out Nobara's two hammers. A melee vessel whose fantasy is "left click with my cursed tool" therefore needs either a shared-input edit or a different slot. Decide this in design, not halfway through implementation.

The sixth slot, `USE_CONTEXT`, is two right clicks in quick succession, and it is the only one whose key vanilla already owns: the first click of the pair is handled before the mod sees it. The input layer sends the slot only once a pair completes, so an ordinary right click costs no packet. Todo marks a body with it. A defect here does not look like a broken ability — it looks like ordinary right clicks misbehaving, so smoke-test block, container and item interaction if you use it.

Then research the character honestly. Canon gives fantasy, not numbers — range, cooldown, and safety rules are design choices and must be written down as such, with the reason.

Design block to produce:

- Vessel id (lowercase, matches the enum constant), display name, role, subtitle.
- Slot map: what each used input position does, and which are deliberately empty.
- Per ability: target rules, range, cooldown, failure modes and the message each produces, what is server-authoritative.
- Attribute modifiers, stagger resistance, starter kit — or explicitly none.
- Cleanup surface: what state the vessel holds and what must drop on death, disconnect, respawn, dimension change and vessel switch.
- Presentation: accent colour, warmth, skin, portrait, cue ids, animations.

## Phase 2 — Scaffold

Order matters: the compiler is the checklist. This was verified rather than assumed — adding a constant to `JujutsuCharacter` and compiling produces **exactly two** errors, one per registry, and none anywhere else. Binding it to the wrong definition compiles fine and fails `testCharacterDefinitions` instead. Re-run that probe if you ever doubt the seam still holds.

1. **Enum constant** in `jujutsu.mod.character.JujutsuCharacter`. The build now fails in exactly two places until both halves exist. That is the design.
2. **Server definition** `<New>Definition implements CharacterDefinition`, in `jujutsu/mod/character/<id>/` beside its runtimes. The package name must be the enum's `id()` and the file must be `<Id>Definition.java` — the registry test derives both from the enum, so following the convention is what keeps it working with no edit. Bind it in `JujutsuCharacters` — one field, one switch arm.
3. **Ability router** `<New>AbilityRouter`: a switch over `CharacterAbility`, **exhaustive, no `default`**, answering `false` explicitly on unused input positions. `tryCast` delegates to it.
4. **Client definition** `<New>ClientDefinition implements CharacterClientDefinition`. Bind it in `JujutsuCharacterClients`.
5. **Profile class** `<New>Profile` for every tuning constant. No magic numbers in runtimes.

Server hooks (`CharacterDefinition`), required first two:

| Hook | Use when |
|---|---|
| `id()` | always — must return the constant it is bound to |
| `tryCast(player, slot, notify)` | always — delegate to the router |
| `registerServerHooks()` | the vessel has event-driven runtimes |
| `canonicalSlot(slot)` | two input positions are the same input to this vessel |
| `applyAttributes` / `removeAttributes` | vanilla attribute modifiers; remove exactly what you add |
| `adjustIncomingStaggerTicks(int)` | stagger resistance; the argument is always > 0 |
| `onSelected` / `onDeselected` | starter kit, or state that must drop when the vessel is left |

Client hooks (`CharacterClientDefinition`), required first two plus `accent`, `moduleName`, `moduleDescription`:

| Hook | Use when |
|---|---|
| `id()` / `rosterEntry()` | always |
| `accent()` / `warmth()` | always / when the shell should warm |
| `createRenderer(context)` | custom GeckoLib body; omit for the vanilla model |
| `playerSkin()` | replacement skin — declare the path **once**, here |
| `rosterOrder()` | placing the card; vessels first, NONE last |
| `registerClientHooks()` | entity renderers and the VFX recipe pack |
| `moduleStartsEnabled()` | never for a vessel — only NONE says yes |

## Phase 3 — Abilities

Every ability goes through the existing shared systems. Reuse is not a preference here, it is the contract.

- **Input**: `CharacterAbilityPayload` only. The input layer already translates `(key, sneak)` into a slot and knows nothing about vessels — it needs no edit.
- **Gate**: `CharacterAbilityExecutor` already owns not-selected and cooldown. Rules that are yours alone go in your router, before the switch, like Nobara's stagger check.
- **Cooldowns**: `CharacterAbilityCooldowns.start(player, slot, ticks)` plus `JujutsuNetworking.sendAbilityCooldown(player, slot, ticks)`. The key already includes the vessel. Never build a second cooldown store.
- **Targeting**: `jujutsu.mod.combat.TargetResolver` with your own eligibility predicate. Read E1b in `docs/KNOWN_ISSUES.md` before touching its ranking.
- **Safe placement**: if you teleport or place anything, follow `TodoSwapPlan` — resolve all destinations first, commit atomically, roll back on partial failure. Never mutate player coordinates directly; use the mapped teleport API.
- **Stagger**: `CombatStagger.GLOBAL`, not a second interrupt system.
- **Damage**: `JujutsuDamageSources`.
- **Vessel-gated items**: `CharacterSelectionView.of(player)` — it answers on **both** sides, because vanilla calls an item's `use` on the client too. A server-only check lets the client predict an action the server refuses and then take back the item and the sound. `TodoSwapMarkerItem` is the worked example.

### Items and entities

A vessel with content edits the shared content registries. That is expected; see the two categories at the top.

- **Item**: class, then `JujutsuItems` — field, factory, `register()` line, and a creative-tab entry if it has no other acquisition path. Decide `stacksTo` deliberately: Todo's marker is single-stack **because** the empty-hands gate is read at swap time.
- **Entity**: class, then `JujutsuEntities` — type field, register, builder. Set `.sized`, `.clientTrackingRange`, and **`.noSave()` for anything transient**. A thrown mark that saves outlives the session and leaves ghosts in the world after a crash — invisible to every check in this skill.
- Give the entity its own flight/idle TTL. Registry lifetime and gameplay lifetime are different things.

### Starter kits

If the vessel gets tools on selection, follow `ProjectJjkNobaraLoadout`:

- **Ensure, do not grant.** Fill only what is *missing*. `onSelected` runs on **every** selection, deliberately, so a kit lost to death is restored — a naive "give on select" duplicates items on every switch.
- Drop to the player's feet when the inventory is full.
- Count substitutes if the item has variants, the way Nobara counts both hammer forms.
- **Do not wire `hasClaimedStarter` into it.** The persisted claim exists and is read by nothing; the one-time-claim rule was deliberately reversed. Using it un-reverses a recorded decision — see E12 in `docs/KNOWN_ISSUES.md`.

Server authority is absolute: the server selects targets, validates, starts cooldowns **only after success**, performs movement, and emits cues. The client sends a request and draws what it is told.

Cleanup is not optional, and the trigger list depends on **what the state is keyed to**:

| State keyed to | Must drop on |
|---|---|
| a player | `SERVER_STOPPING`, `DISCONNECT`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, and vessel switch |
| a world entity — an embedded nail, a marked body, a thrown mark | the four above **plus** `ServerLivingEntityEvents.AFTER_DEATH` and `ServerEntityEvents.ENTITY_UNLOAD` |

The vessel-switch trigger is `onDeselected`; the rest are your runtime's own listeners. Give the vessel **one** cleanup entry point that every trigger calls, rather than one partial handler per event — that is how a path gets missed. Nobara's entity-keyed state (`NailAnchorLifecycle`, `ProjectJjkStrawDollRuntime`) is the worked example of the second row; Todo's player-keyed state is the first.

## Phase 4 — Client presentation

- **VFX**: `<New>VfxIds` + `<New>VfxRecipes`, registered from `registerClientHooks()`. Cue → director → recipe → channel. Cues are visual-only and are emitted by the server after a confirmed action. Own your cue ids.
- **Render**: subclass the shared stack — `CharacterPlayerGeoRenderer`, `CharacterPlayerGeoModel`, `CharacterHeldItemLayer`. Both shipped renderers are under 20 lines. If yours needs more, the base class is wrong, not your vessel — but changing a base class changes how **every** shipped vessel draws, so it is a shared-render change and carries the same regression duty as touching a vessel: re-check both existing bodies in game. `renderCharacter` is `final` on purpose.
- **Two body textures, not one.** A custom-geo vessel ships both: a vanilla-layout skin driving first-person hands and the roster portrait (`playerSkin()`, used by `CharacterSkinMixin`), and the geo texture the model names. Todo ships `todo.png` and `todo_aoi.png`. Pointing both at one file breaks one of the two paths.
- **Assets**: `.geo.json` and `.animation.json` go under `assets/<ns>/geckolib/models/**` and `assets/<ns>/geckolib/animations/**`, where every shipped asset lives. The legacy `geo/` tree still exists and is not indexed — see `02-architecture/Assets-and-resources.md` for that trap.
- **Item assets** are three files each, and missing one shows up as a purple-black cube in game rather than as an error: `assets/<ns>/items/<id>.json`, `assets/<ns>/models/item/<id>.json`, and the texture.
- **Roster card**: list what your router **actually answers**, in input order, with the real key labels. A card that lies is worse than a card that is short — both shipped cards had drifted and it took a refactor to notice.
- **Strings**: `en_us.json` and `ru_ru.json`, both, for every key the card and every refusal message names.

## Phase 5 — Testing and in-game smoke

**Write new tests as JUnit 5.** They join `check` automatically — no task to register, nothing to forget. The older `main()` + `assert` programs wired as JavaExec tasks in `build.gradle` still run and are not being converted wholesale, but do not add another one. `fabric-loader-junit` boots the loader for the test JVM, so a JUnit test may call `SharedConstants.tryDetectVersion()` and `Bootstrap.bootStrap()` in `@BeforeAll` and then exercise registries, codecs and buffers for real. `SelectionPayloadCodecTest` is the worked example.

Three suites already cover your vessel with **no edit**, provided you follow the convention — runtimes in `jujutsu/mod/character/<id>/`, definition named `<Id>Definition.java`:

- The **registry tests** check the binding: that your definitions claim the right constant, that every `register()` under your package is called, and that no client type reached `src/main`.
- **`VesselBoundaryTest`** reads compiled bytecode and derives vessel identities from the enum. Your package under `character` must be spelled exactly as your `id()`, or it fails as an unregistered vessel. It also pins the full packet inventory, so a payload of yours anywhere fails until it is argued for.
- **`SourceBoundaryTripwireTest`** is a source-text grep, named as one, for the two breaches bytecode cannot show: a compile-time constant, which javac folds into the caller, and `Class.forName`. It will fail if shared code names one of your types, and if your vessel names another vessel's.

Do not duplicate any of them. Test what is yours: the slot map bound arm by arm, the profile's invariants, any pure predicate.

**Assert inside the arm, not anywhere in the file.** A whole-file substring search passes with two arms transposed.

**Every new check ships with proof that it can fail.** Break the thing it guards, record the mutation and the resulting failure message in the commit body, restore. A check only ever seen green may be vacuous — this is a repository rule, in AGENTS.md, not a suggestion.

Then run the one command that owns the word "verified":

```bash
./gradlew qualityGate
```

It runs `check`, the documentation audit and the assertion-flag audit together. Nothing may be called done without a green run of exactly this.

**In-game smoke is mandatory and cannot be skipped.** Compilation proves nothing about feel, rendering, animation or timing. Hand the user a short checklist ordered by what is most likely broken — the newest path first, then the shared paths the vessel touches, then what should have stayed untouched. Do not run the game yourself unless asked.

## Phase 6 — Documentation and final audit

- New Codex note under `03-systems/` for the vessel, following `Nobara-overview.md` / `Todo-Boogie-Woogie.md`.
- Link it from `00-MOC.md` and bump the metrics table. `tools/audit_docs.py` enforces those counts and now runs **inside `./gradlew qualityGate`**, so a stale table fails before the commit rather than after the push. Adding any `.java` file moves a counter — expect that, it is not a mistake.
- `AGENTS.md` "Current slice (facts)": controls line and anything durable.
- `SESSION.md`: what changed, what is verified, what the user still has to check.
- Any accepted tradeoff goes in `docs/KNOWN_ISSUES.md` with its reason, not in a comment.

Final audit — grep and confirm:

```bash
grep -rn "JujutsuCharacter\.<NEW>" src/main/java src/client/java
```

Every hit must be inside your own vessel's files. One in each `id()`, and nothing in a dispatch file.

Run it for **your** constant only. Running it for `NOBARA` or `TODO` returns the recorded exceptions — the `hairpin` command guard, Todo's damage-listener filter, the marker's thrower check — and will look like the seam is already broken. It is not; those are listed in `02-architecture/Vessel-definitions.md`.

---

## Forbidden

| Never | Why |
|---|---|
| A per-character `if` or `switch` in a dispatch file | The seam exists to delete these. Ask the definition. Content registries are a different category — see the top. |
| A payload for one vessel | Two input paths cannot agree on what a key means. This exact mistake cost a full migration. |
| A second cooldown store | The shared one already keys on `(player, vessel, slot)`. |
| Registering runtimes in `JujutsuMod` or `JujutsuModClient` | Use `registerServerHooks` / `registerClientHooks`. Both init files loop their registry. |
| A client type in `src/main` | A dedicated server loads that source set and every implementation of `CharacterDefinition`. It fails at class load, far from the cause. |
| Roster, skin, renderer or VFX metadata written twice | Declare each once on the definition and read it from there. |
| Bypassing `CharacterAbilityPayload` or the shared ability path | It carries the vessel claim that makes stale-selection casts refuse. |
| A JSON framework or plugin system | Not without a separate, explicit request. Two interfaces and two registries is the right size. |
| Touching an existing vessel | Only with a reason and a full in-game regression of that vessel's kit. |
| A `default` arm in any vessel or slot switch | It converts a compile error into a silent wrong answer. |

## Readiness checklist

- [ ] Design block written and approved; numbers justified, not copied from canon
- [ ] Enum constant added
- [ ] Server definition written and bound in `JujutsuCharacters`
- [ ] Client definition written and bound in `JujutsuCharacterClients`
- [ ] Ability router exhaustive, no `default`, unused slots answer `false` explicitly
- [ ] Runtimes registered from `registerServerHooks` / `registerClientHooks`, not from mod init
- [ ] Every tuning constant in `<New>Profile`
- [ ] Cooldowns through the shared store; started only after success; mirrored to the client
- [ ] Targeting through `TargetResolver`; placement atomic with rollback
- [ ] Cleanup on all five triggers, through one entry point
- [ ] Roster card lists what the router answers, in input order, with real key labels
- [ ] Renderer, skin, portrait, accent, warmth, module row all declared on the client definition
- [ ] Items registered in `JujutsuItems` with an acquisition path; entities in `JujutsuEntities` with `.noSave()` if transient
- [ ] Item assets complete: `items/<id>.json`, `models/item/<id>.json`, texture
- [ ] Both body textures present — vanilla-layout skin and geo texture
- [ ] Any vessel-gated item checks selection on both sides through `CharacterSelectionView`
- [ ] Starter kit fills only what is missing, and does not read `hasClaimedStarter`
- [ ] `en_us` and `ru_ru` complete for every key named
- [ ] Tests added as JUnit 5, each proven able to fail by a recorded mutation
- [ ] `./gradlew qualityGate` green
- [ ] Codex note written, MOC linked and metrics bumped, `AGENTS.md` and `SESSION.md` updated
- [ ] `grep -rn "JujutsuCharacter.<NEW>"` shows hits only in the vessel's own files
- [ ] In-game smoke checklist handed to the user

## Commit order

Small commits, each green on its own. This shape came from the migration that built the seam:

| | Commit | Contents |
|---:|---|---|
| 1 | `feat(<vessel>): add the roster constant and both definitions` | enum, both definitions, both registry arms, profile, lang keys for the card — kit still empty, build green |
| 2 | `feat(<vessel>): implement <first ability>` | runtime, router arm, cooldown, cue id, verification program |
| 3 | `feat(<vessel>): implement <next ability>` | one ability per commit |
| 4 | `feat(<vessel>): give <vessel> a body` | model, animations, renderer, both textures |
| 4a | `feat(<vessel>): add <item>` | item class, `JujutsuItems`, entity + `JujutsuEntities` if thrown, item assets, lang |
| 5 | `feat(<vessel>): register the VFX recipe pack` | recipes and channels |
| 6 | `docs(project): record <vessel>` | Codex note, MOC, AGENTS.md, SESSION.md, metrics |

If a commit turns out to need a **dispatch** file, that is the signal to stop and re-read the seam — not to edit the file. Needing a content registry, a lang file or the MOC is normal and expected. Needing `build.gradle` no longer is: JUnit tests join `check` on their own.

## Final output

Report: the vessel and its id; the slot map; files added, and confirmation that no shared file changed beyond the enum and the two registries; abilities implemented; assets added; test names and the mutation used to prove each; the `qualityGate` result; what remains for the user in game.
