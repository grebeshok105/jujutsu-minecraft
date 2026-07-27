# Session Handoff — Jujutsu Minecraft

## Current state

- `main` = the vessel definition seam and the add-vessel skill (PR #9, #10, #11), the quality gate, the JUnit foundation, and the Boogie Woogie impact pass. Pushed; local and remote agree
- **Start any new work from `main`.** Everything is merged, and every branch that was fully contained in `main` has been deleted locally and on the remote. Two local branches survive on purpose, and neither is pending work — see "Leftover branches" below
- Barrier stages 1 and 2 are done. **Stage 3 is ArchUnit**: port only structural rules out of `ProjectSanityTest` — side separation, package isolation between vessels, which layer may hold a payload. Asset, JSON, localization and image checks stay as file reads. Claims about what a method does with a value belong in JUnit or later in GameTest, never in ArchUnit
- **Finishing Todo is planned in one place: [docs/TODO_COMPLETION_CHECKLIST.md](docs/TODO_COMPLETION_CHECKLIST.md).** Audited 2026-07-27 against `97dd526`. Its finding in one line: the approved scope is implemented and wired end to end, everything except the mark mechanics has been played but never recorded, and eight defects were found by reading — four behavioural, four documentation claims the code contradicts. None of the four behavioural ones is a shape a play session would have surfaced. Use it as the Todo work list; this file keeps only the handoff history
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)" and, for the seam, under "The Vessel Seam". This file records only what changed recently and what is still unproven. Documentation authority order is owned by AGENTS.md; asset and provenance policy by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

### Leftover branches — neither is pending work

- `feat/todo-boogie-woogie` holds eight commits that are not in `main` by hash. Their content is the earlier version of the Todo kit and the shared vessel render stack, which reached `main` by another route. Merging it would roll `ProjectSanityTest` and `TargetResolverTest` back to older versions. Delete it when you are comfortable; do not merge it.
- `codex/vfx-director-prototype` holds one commit adding a standalone HTML sandbox under a documentation directory the audit forbids. Merging it fails `qualityGate` on the spot. Keep it as a scratch reference or delete it; it cannot land as is.
- `worktree-neon-gui` is checked out by an editor workspace outside this repository's `.worktrees`, so git will not let it go. It is fully contained in `main`.

## On the active branch — ArchUnit boundaries, after an adversarial review

Stage 3. Ten structural rules over compiled bytecode, three source-text tripwires named as the greps they are, and a recorded list of what neither can prove.

The first version of the rules was reviewed by three independent judges instructed to break them, not to bless them. **They confirmed 16 bypasses against a green build.** Every one has been re-run against the fixed rules: 15 are caught, 1 is documented as beyond static analysis.

- **The rules stopped enumerating vessel names.** Identities come from `JujutsuCharacter`, and packages under `character` are checked fail-closed, so a third vessel is covered the day it exists. Five bypasses came from two hardcoded strings — a package spelled `nobaranet`, a vessel called `yuji`, `TodoProfile` moved sideways into `jujutsu.mod.combat`.
- **The wire surface is pinned as an inventory**, not by class location. A payload declared in the shared `jujutsu.mod.network` package was a private input path that every location-based rule waved through.
- **Payload placement is checked in both outputs.** It used to check only the server one, so a payload in the client `todo` package — the case the rule names verbatim — passed.
- **The registry allowlist narrowed from a class to a direction.** Building a vessel's content is registration; calling a vessel method from a registry is shared code running vessel logic, and two judges independently smuggled dispatch in that way.
- **Two things bytecode cannot see, confirmed and covered honestly.** javac folds `static final` constants into the caller, so `TodoProfile.BOOGIE_WOOGIE_RANGE` read from shared code leaves no dependency at all — verified with `javap`. The same is true of `Class.forName`. Both are caught by source-text tripwires that say in their own name and javadoc that they are greps.
- **Rule 5 was deleted, not kept for the count.** Its subject set was a strict subset of rule 3's with the same assertion, so no mutation could redden it alone.
- **What is still open is written down.** The limits of the gate are listed in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md), together with E13 and E14.

## Landed on main — the JUnit foundation

Stage 2 of the barrier plan. Foundation only: **no existing `JavaExec` program was migrated**, so all 34 keep running exactly as before.

- **`fabric-loader-junit` boots the loader for the test JVM.** It carries JUnit Jupiter 5.10.0 transitively, so no other test dependency was added. This is the capability the JavaExec programs never had: a test can now call `Bootstrap.bootStrap()` and touch real registries, codecs and buffers.
- **`failOnNoDiscoveredTests` flipped to `true`.** It was `false`, which was harmless while no JUnit existed and poisonous the moment it did: a misconfigured platform discovers zero tests and the task passes green, exactly like a `JavaExec` task that lost its `-ea`.
- **First JUnit test covers a real gap, not a demo.** `SelectionPayloadCodecTest` round-trips `CharacterSelectionSyncPayload` and `SelectCharacterPayload`, which carry the vessel selection and had **no coverage of any kind**. Both write two strings back to back, so a transposition compiles, keeps the build green, and shows up in game as the wrong skin or the wrong vessel.
- **Known consequence, recorded before it bites:** the 34 `JavaExec` programs each run in their own JVM, so static state cannot leak between them. A shared JUnit JVM removes that. `CharacterAbilityCooldowns`, `CombatStagger`, `EmbeddedNailRegistry` and `ProjectJjkNailMarks` therefore migrate last.
- **`CharacterAbilityCooldowns` is not unit-testable as written** — its key resolves the vessel from a live `ServerPlayer` and the key record is private. The vessel-keyed cooldown, the contract most worth covering, needs a small pure core extracted before a test can reach it. Not done here; that is a deliberate scope call, not an oversight.

## Landed on main — the single merge gate

Stage 1 of an agreed six-stage plan for the build-time barrier: `qualityGate → JUnit 5 → ArchUnit → SpotBugs → PIT → GameTest`. Stages 1 and 2 are both on `main`.

- **`./gradlew qualityGate`** is now the one command. It runs `check`, `auditDocumentation` and `verifyAssertionsEnabled`. AGENTS.md makes a green run of exactly this task the condition for the word "verified", and states in the same place what a green run does **not** prove.
- **The documentation audit stopped being CI-only.** It was a separate workflow step, so a documentation break surfaced after a push rather than before a commit.
- **`verifyAssertionsEnabled` closes a real hole, not a hypothetical one.** `JavaExec.enableAssertions` defaults to `false` — measured, not assumed — and the verification programs are `main()` classes guarded by `assert`. A task registered without `jvmArgs '-ea'` therefore passes unconditionally and silently. The audit reads the Gradle task model rather than the text of `build.gradle`, so a task registered from anywhere is covered, and it fails with the list of offenders.
- **CI no longer keeps its own command list.** It runs the same `qualityGate`, then `assemble` for the artifact. Green CI and green local now mean the same thing.
- **New rule, applied to itself:** every gate rule ships with proof that it can fail. All three components were broken deliberately and the failure messages recorded in the commit body.

## Landed on main — the Boogie Woogie impact pass

Five commits. The through-line: **the swap stopped being a teleport with a sound on it.** Contract and reasoning: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Todo-Boogie-Woogie.md`.

- **A swapped body keeps its momentum on the client.** `place` teleports absolutely with an empty `Relative` set, so the transition carried `Vec3.ZERO` and the client was told its velocity was nothing — the server-side `setDeltaMovement` was a fiction for a player, who owns his own movement. One `hurtMarked` inside the shared restore helper fixes all four routes and the rollback path.
- **One emission point.** The aimed swap, both marker swaps and the pair swap each hand-copied the same five feedback calls; they now share `emitSwapImpact`. The pending-sound scheduler carries its own sound, so a low landing report lands three ticks after the clap at the midpoint of where the bodies ended up — one report, because Minecraft audio has no propagation delay and two would flam.
- **The impact sequence.** Camera snap, per-body afterimage silhouette, per-body arrival gather, velocity streak, and six ticks of the world's audio stepping back. All of it on `SWAP_AFTERIMAGE` / `SWAP_ARRIVAL`, which the feint does not emit — the feint shares the clap cue, so anything added there would announce it.
- **A landed mark is a permanent anchor**, a body mark is not. The record enforces both lifetimes rather than trusting call sites, and `TodoSwapMarks.onUsed` is the one place that decides what a swap costs a mark.
- **Swap momentum.** A completed swap opens a 24-tick window; the next confirmed hit is ×1.25 and staggers 8 ticks. Carried by a `MobEffect` attribute modifier, so there is no second damage instance at all.

### What the exploration found that the naive version would have shipped broken

1. `ServerLivingEntityEvents.AFTER_DAMAGE` **does not fire on a killing blow** — a kill would have silently refunded the momentum.
2. `SoundManager.updateSourceVolume` **ignores its volume argument** for every category except MASTER; it is not a ducking API. `pauseAllExcept` / `resume` is.
3. `TODO_SWAP_MARKER` is `noSave()`, so an unloaded chunk destroys the projectile forever while the mark sweep deliberately treats an unresolvable entity as alive — permanence would have produced an invisible working teleport anchor.
4. Black Flash re-enters the damage event, so an unguarded momentum listener sees one swing twice and spends the window on the wrong pass.

### Verified by mutation, not by a green run

Three: assignment instead of `Math.max` in `VfxSoundDuck.extendedDeadline`, `sin` for `cos` in `VfxWorldChannel.facingScale`, and disabling the re-entrancy guard in `TodoSwapMomentum`. Each fails its own assertion and was reverted.

## Landed on main — the vessel definition seam

Nine commits. The through-line: **shared code stopped asking which character a player is and asks the vessel.** Contract: `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md`. Reasoning and before/after: `docs/VESSEL_DEFINITION_REFACTOR.md`.

- **Slots renamed after input positions** — PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click with a technique weapon). Safe to renumber: the ids are transient, only the character id persists.
- **The input layer became a translator.** `JujutsuKeybinds` maps `(key, sneak)` to a slot and knows no vessel. Keybind ids still read `nobara_hairpin_*` on purpose — vanilla writes that string into options.txt.
- **Nobara moved onto the shared slots**; her private packet and int gate are deleted. This exposed two defects, both fixed: the cooldown key gained the vessel (`(player, vessel, slot)` on both sides), and the `hairpin` debug commands refuse a non-Nobara caster instead of firing Todo's swap under a hairpin's name.
- **Stale-vessel casts refused.** `CharacterAbilityPayload` carries the vessel the client believed in; the server compares and refuses. Closes the menu round-trip window where a key press was executed by the vessel the player had just left.
- **Server definitions** — `CharacterDefinition` + `JujutsuCharacters`. Mod init loops the registry instead of hand-listing twelve per-vessel `register()` calls.
- **Client definitions** — `CharacterClientDefinition` + `JujutsuCharacterClients`. `JujutsuVfxRecipes` deleted; each vessel registers its own. Six shared client files stopped naming a vessel. The roster's input strips were stale and are now honest.
- **E7 and E12 closed.** Seven direct vessel references remain in `src/`, one per file, all deliberate.

## Landed on main — the add-vessel skill

- `.claude/skills/add-vessel/SKILL.md` — repo-local, versioned with the architecture it describes. Six phases, prohibitions, readiness checklist, commit order. References the Codex notes rather than restating them.
- **Both registry tests now derive their expectations** instead of hand-keeping per-vessel lists: vessels from the enum, packages from the vessel id, and each card's expected length from the arms its router does not refuse. Before this, a new vessel would have shipped with three guarantees silently absent — which contradicted the skill's central claim.
- AGENTS.md gained "The Vessel Seam" as a first-class rule and lost the ten-step character workflow the skill now owns.
- The claim was verified, not argued: adding a `JujutsuCharacter` constant produces **exactly two** compile errors, one per registry, and none elsewhere; binding it to the wrong definition compiles and fails `testCharacterDefinitions`.

## Verification status

- 33 JavaExec verification programs wired into `check`, all green. Three added by the seam work (`testCharacterDefinitions`, `testCharacterClients`, `testNobaraAbilitySlots`) and three by the impact pass (`testTodoSwapMomentum`, `testVfxSoundDuck`, `testVfxSilhouette`). All 33 confirmed to enable assertions by `verifyAssertionsEnabled`.
- Plus one JUnit class, 4 tests, run by the standard `test` task inside `check`. `failOnNoDiscoveredTests` is `true`, so a suite that discovers nothing fails instead of passing.
- The documentation audit is **inside `./gradlew qualityGate`** now, and no longer a hand-run step. The impact pass moved all four audited counters at once.
- Two checks proven able to fail by mutation rather than only observed green: transposing two router arms, and binding a constant to the wrong definition.
- Jar built from `main` and installed at `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.

**Nothing in the suite constructs a `ServerLevel`,** so no test casts anything for real. Treat the build as proof of shape, not of behaviour.

### In-game smoke — partial, and here is exactly how partial

Run by the user at commit `d9df2b5`: Nobara's kit confirmed working (abilities activate, nails fly correctly), Todo confirmed on `R` and `B`.

**Not re-run since.** Everything below landed after that test:

- server definitions (`29dd4c4`, `8561cf7`) — attributes, stagger and selection hooks moved into definitions, and every vessel runtime now installs through `registerServerHooks` instead of mod init
- client definitions (`53a4dcd`) — renderers, skins, roster cards, theme accents and VFX packs all moved behind the client registry
- the marker's vessel gate and Todo's Shift+B fold (`20b5b15`)

## Must be checked in game before the impact pass is trusted

Still accurate and still unrun. Which of these gate calling Todo finished is decided by section 1 of [docs/TODO_COMPLETION_CHECKLIST.md](docs/TODO_COMPLETION_CHECKLIST.md); the step-by-step procedure stays owned by [docs/BUILDING_IN_SANDBOX.md](docs/BUILDING_IN_SANDBOX.md). Neither list is superseded — the checklist points at both rather than restating them.

The user ran this pass in game through the sixth commit and it held.

**The seventh commit shipped without an in-game pass, deliberately.** The user was told what was unverified and chose to merge anyway, so this is an accepted risk rather than a forgotten step. What was skipped: the `USE_CONTEXT` slot, reached by two right clicks in quick succession. It is the only slot whose key vanilla already owns, so a defect there does not look like a broken ability — it looks like ordinary right clicks misbehaving. If block, container or item interaction ever starts feeling wrong, check this first: that a single right click still does its ordinary thing, that a pair marks the body under the crosshair, and that normal interaction never trips the pair.

1. **A normal swap** reads as one physical beat: clap, snap, two silhouettes, two arrivals, a fraction of a second of quiet, then the low report. Movement continues instead of stopping dead.
2. **Every rejection is silent** — hands full, no target with no mark, nowhere safe to stand: no sound, no flash, no duck.
3. **The feint still gives nothing away.** `Shift+R` has the clap and the camera snap; no silhouette, no arrival, no report, no quiet.
4. **Open a menu during the quiet.** Audio returns and the vanilla pause is not disturbed.
5. **Five swaps back to back** leave no stuck silhouette, no stuck quiet, no stuck camera offset.
6. **The mark.** Throw it, swap to it — the mark and the projectile are both still there; swap again. Walk out of render distance and back: the mark is honestly lost with a message. Change dimension, die, change vessel: cleared.
7. **A body mark** still lasts ten seconds and is still consumed.
8. **Momentum.** Swap then hit: heavier, staggers, its own effect. The second hit is ordinary. Swap, miss, then hit inside the window: still boosted. Swap, wait two seconds, hit: ordinary. Swap then change vessel: the effect is gone.
9. **Nobara regression.** `restoreMotionAndRotation` and `VfxWorldChannel` are the only shared files this pass touched.

## Must be checked in game before the seam work is trusted

1. **Both vessels still load and render.** The renderer map, skin mixin and roster are now registry-driven; a wiring mistake shows as a vanilla body or a missing card, not as a failed build.
2. **Every runtime still installs.** Mod init no longer names them. Nail traps, straw doll, resonance, hammer combat, Todo's marks — if a `registerServerHooks` call were dropped the ability would simply do nothing, silently. The build-time test covers the call existing, not the listener firing.
3. **Attributes and stagger.** Todo should still hit harder, swing slower and shrug off stagger — those moved from a shared file into `TodoDefinition`.
4. **Nobara kit restore.** Drop the hammer, re-select her: it returns, and held tools are not duplicated.
5. **Shift+B as Todo** reaches the pair swap, including crouched between the two presses. Known un-predicted edge: Shift+B during the B cooldown earns the recharging message rather than being suppressed locally.
6. **The marker gate on both sides.** As Nobara or None, right click the marker: no throw, no sound, no consumed item. As Todo it throws normally.
7. **Roster cards.** Nobara's strip shows five inputs, Todo's three, in both languages; accents ease per vessel; None sits last.
8. **The stale-vessel refusal.** Switch vessels and press R inside the confirmation round trip: nothing fires from the old vessel.

## Next product steps

1. Barrier stage 3 — ArchUnit, scoped as described under "Current state". Then SpotBugs, then PIT, then GameTest.
2. Finish Todo per [docs/TODO_COMPLETION_CHECKLIST.md](docs/TODO_COMPLETION_CHECKLIST.md) — its section 9 is the measurable definition of done. Start with the four behavioural defects (D1–D4), because two of them are only observable in the in-game pass and it is cheaper to fix first and verify once.
3. Run the in-game pass above — items 1–3 are the ones the seam work put at real risk.
4. Decide the fate of `CharacterPlayerState.hasClaimedStarter`: give the persisted claim a job or delete it (E12 residue).
5. Decide E10 (Nobara's fallback erases five translated diagnostics) and E11 (cooldown message precedes her silent stagger check) deliberately, not inside a refactor.
6. Move "is this stack my technique weapon" out of `JujutsuKeybinds` into the client definition — the last vessel-specific line in the input layer, and the one thing blocking a melee vessel from using `ATTACK_CONTEXT`.
7. Add world/GameTest coverage for the swap runtimes and their rollback paths (E1/E8).
8. Replace temporary ProjectJJK placeholders; resolve Rich-Modern provenance before any public distribution.

## Open decisions left for the user

- AGENTS.md restates twenty `TodoProfile` constants in a table while itself saying `TodoProfile` is the source of truth and must not be restated. Removing the table would drop the file under the 300-line hygiene target; keeping it is defensible for at-a-glance reference. Not changed unilaterally.
