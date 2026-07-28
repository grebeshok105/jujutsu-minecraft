# Known Issues and Technical Debt

Status: CURRENT LIVE REGISTER

Last code verification: 2026-07-26. Entries carrying a "Verified 2026-07-26" line were re-checked against source on that date. E3, E4, and E6 were last checked on 2026-07-23 and were not re-verified in this pass; treat their detail as older than the rest.

Applies to: main and the active branch feat/todo-input-slots. The earlier branch fix/persistence-nail-lifecycle-docs-sync no longer exists; its work is in main.

Owner hierarchy: current code/tests → AGENTS.md → SESSION.md → Codebase Codex → this register

## Accepted product decisions

### Global Resonance hit-stop

This register owns the rationale; other documents point here.

Resonance intentionally changes the global server tick rate to create hit-stop. This affects every player and dimension, but the current product target is private play for one or two people. Do not remove it as a generic multiplayer optimization. Reopen only if the target becomes a public or competitive server.

### Boogie Woogie destinations have no entity-occupancy gate

Verified 2026-07-26 against `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java`.

`findSafeDestination` gates a destination only on world bounds, chunk load, world border, and solid-block collision (`isPlaceableDestination`, `isInWorldDestination`). There is no floor requirement and no check that another entity already occupies the destination — no `isPickable` or entity-query call exists anywhere in the file, and its doc comment states the policy explicitly: "No floor, no third-party entity occupancy gates."

This is deliberate for the current 1–2 player target: air, water, crawl, and flight destinations are all intended to be valid. It is recorded here because an earlier revision of SESSION.md wrongly claimed a non-living-collision fix ("A3") had landed, and that false claim also reached a pull-request description. It never landed.

Resolved since: `findSafeDestination` takes a `Strictness`. The world-border test moved into `isInWorldDestination`, so the fallback path enforces it too. The unused `otherSwapParticipant` parameter is gone — its comment claimed the fallback existed because collision is picky about the partner's volume, but `Level.noBlockCollision(Entity, AABB)` tests block shapes only and never consults entities, so there was nothing to exclude.

Corrected 2026-07-27, and it is worth being exact about what changed. The line was never drawn per cast; it is drawn per **body**. An earlier revision of this entry and of AGENTS.md said the aimed swap "keeps `SOFT`", which was true of the *cast* and therefore true of its **target** as well — a body that did not ask to be moved could be placed at the exact requested point with `noBlockCollision` skipped, and vanilla would push it back out of the wall. The defaulting overload that supplied `SOFT` without any caller choosing it is deleted, so strictness is now stated at every call site. Todo's own arrival keeps the fallback; the target, both pair-swap participants and both mark forms all take `STRICT`, and a cast that cannot place them cancels whole. Decided in favour of the safety principle rather than the shipped behaviour.

Residual, and inherent to the policy: two swapped entities can end up interpenetrating, and either can land inside a non-living collidable entity such as a boat or minecart. Vanilla teleport/collision semantics decide what happens next; no crash has been observed or reproduced.

Reopen only if the product target becomes public/competitive play, or if a live smoke test shows a concrete stuck/suffocation case.

### The feint clap's input scheme leaks the caster's pose

Decided 2026-07-26. This register owns the rationale; the Codex note points here.

`Shift+R` casts the feint and plain `R` casts the real swap, which means the modifier is the **sneak key**. `LocalPlayer.isShiftKeyDown()` reads that key, and it is also what drives `crouching` → `Pose.CROUCHING`, which is synchronized to every tracking client. Two consequences follow, and both are accepted rather than fixed:

1. An observer can tell a feint from a real swap by watching Todo's pose alone — standing means real, crouched means feint — without reading a single cue, sound or timing. That defeats the presentation-level indistinguishability the feint was built for, which is genuinely airtight everywhere else: one shared `emitClapPerformance`, one shared gate table, field-identical cues.
2. A sneaking Todo cannot cast the real swap at all. Every press while crouched feints.

Why it stands: the input scheme is a product decision — one key, no hold threshold, no double tap, because the real swap has to stay instant. Every alternative costs something the decision rejected. A dedicated `KeyMapping` fixes both consequences completely and is the cheapest technical fix, but adds a fourth key to a two-key kit. Inverting the pair moves the tell onto the real cast instead of removing it. A non-sneak modifier still needs a new `KeyMapping` or a raw GLFW poll.

Reopen if live PvP shows the pose tell makes the feint worthless, or if a fourth keybind becomes acceptable. Do not "fix" it by adding a hold threshold or a double tap — those were rejected because they would delay the real swap.

### Swap momentum survives a sweeping attack, and is nearly worthless on a fist

Decided 2026-07-26 with the impact pass. Both are recorded in `TodoSwapMomentumRuntime`'s javadoc as well, because both look like bugs to anyone reading the class cold.

1. **Sweep keeps the boost after the window is spent.** `Player.attack` reads `ATTACK_DAMAGE` into a local before the sweeping block runs, and computes sweep damage as `1.0 + SWEEPING_DAMAGE_RATIO × that local`. Removing the effect during the primary victim's `AFTER_DAMAGE` cannot shrink a float already on the stack, so later victims of the same swing take boosted damage from a spent window. The stagger and the cue do **not** duplicate — the effect is already gone when later victims arrive. It also costs a deliberate hotbar swap, because sweeping needs a sword and both hands must be empty to clap. The only fixes are a mixin into `Player.attack` or abandoning the attribute for a re-entrant bonus hit; the second would reintroduce exactly the double-application the attribute exists to prevent.
2. **On bare fists ×1.25 is worth under a fifth of a heart.** A fist is 1.0 attack damage, Todo's is 1.5, boosted 1.875 — a gain of 0.375 against a two-point heart. An earlier revision of this line called that "about a third of a heart", which overstated it by roughly 2×; the arithmetic beside it was always right and the conclusion is unchanged. The eight-tick stagger is the real payload, and the damage only matters if the player draws a weapon inside the 24-tick window. That is the intended loop — displace, arm, hit — not an oversight. Reopen if play shows the window is too short to arm in.

### A permanent mark shadows the primary key, and is the kit's strongest tool

Accepted 2026-07-26. Two consequences of making a landed marker reusable, neither of them a defect:

1. While a mark exists, every `R` press that finds nothing under the crosshair becomes a 32-block teleport instead of a `no_target` message. That is the fallback behaving as specified, but it changes how the key feels; the arrival now has its own visuals so the two casts are at least distinguishable.
2. One thrown item buys an unlimited return on a 60-tick cooldown — in practice a personal evacuation point behind a wall. Nothing is limited today. The levers are already separated so that limiting it later is a number rather than a rewrite: `MARKER_SWAP_COOLDOWN_TICKS` (split from the aimed swap's and equal to it), `MARKER_SWAP_RANGE`, `TodoSwapMarks.onUsed` for a charge count, and making the projectile damageable for counterplay — the mark already ends when its projectile leaves a loaded chunk, so destroying it needs only an entity change.

### ProjectJJK placeholder assets

Owned by [PROVENANCE.md](PROVENANCE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Those files hold the permission scope, the retained upstream notice, and the replacement policy. Only the release-blocking consequences are tracked here, as R3.

## Public-release blockers

### R1 — Rich-Modern provenance is unresolved

Verified 2026-07-26. Still open.

The client/rich package and associated font/shader assets were derived from a user-provided Rich-Modern reference. Dated research explicitly said study-only, while current source describes a port. Determine the upstream license/permission and replace code/assets that cannot be redistributed.

Cheapest available reduction: `src/client/java/antidaunleak/api/UserProfile.java` is git-tracked, is the only remaining file in the imported `antidaunleak` namespace, and nothing in the tree references it (`grep -rl antidaunleak src/` returns that file alone). Removing it would shrink the unresolved-provenance surface by one whole namespace at zero functional cost. Do not delete it as an unapproved cleanup — it is recorded here so the decision is made deliberately along with the rest of R1.

### R2 — Bundled Segoe UI font

Verified 2026-07-26: `src/main/resources/assets/jujutsumod/font/neon.ttf` is still present (~870 KB) alongside `neon.json`.

The notice itself is owned by [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) under "Segoe UI Semilight".

Action: confirm whether ClickGui still loads the TTF at all given the MSDF atlases, then remove it if unused or replace it with a verified redistributable font before public distribution.

### R3 — Placeholder release permission must be recorded

Verified 2026-07-26. Still open.

Private author permission is sufficient for current development. A public release still needs a recorded scope covering redistribution, or replacement with original assets. Policy detail lives in [PROVENANCE.md](PROVENANCE.md).

## High-priority engineering work

### E1 — No automated in-game smoke test, and no world/teleport coverage for the swap

Verified 2026-07-26. Still open.

CI compiles and runs assertion programs but does not boot a client or dedicated server. Renderer, mixin, packet, UI, and gameplay integration regressions can survive a green build. `grep -rln GameTest src/` returns nothing — there are no GameTest classes at all.

Widened again 2026-07-26 by the Boogie Woogie impact pass. Nothing in it can be tested by the current harness beyond pure helpers: the sound duck's actual effect on `SoundManager`, the afterimage's readability against a real body, whether `hurtMarked` genuinely restores visible momentum for a moved player, and whether the momentum window is spent by the hit the player thinks it was. The client-smoke checklist for all of it is in `SESSION.md`.

Specifically for Todo: no test calls any of the four `tryCast` entry points (`TodoBoogieWoogieRuntime`, `TodoFakeClapRuntime`, `TodoPairSwapRuntime`, `TodoMarkerSwapRuntime`). The Todo tests (`TodoProfileTest`, `TodoSwapPlanTest`, `TodoTargetSafetyTest`, `TodoHandsEmptyTest`, `TodoFakeClapTest`, `TodoPairSwapTest`, `TodoSwapMarkerTest`) cover profile constants, `TodoSwapPlan.preflight` null-handling, boolean truth tables including the shared `TodoSwapGates` clap gate, the pure `TodoPendingSelection` and `TodoSwapMark` predicates, and — for the three newest mechanics — source-text contract assertions rather than behaviour. Nothing constructs a `ServerLevel` or exercises an actual teleport.

The gap widened rather than narrowed with this branch: three whole mechanics landed under it. Untested as a result:

- real player↔mob and player↔player swap, blocked destinations, second-teleport failure and rollback, velocity / yaw / pitch / head-yaw / fall-distance preservation, the packet path end to end
- whether the feint clap and the real clap are actually indistinguishable to a second player
- the pair swap's whole selection lifecycle against a live world — expiry, marked-body death, dimension change, and above all that a STRICT cancellation moves nobody rather than half-applying to a bystander
- both thrown-mark forms: that a resting marker is discarded on every exit path, and that an entity mark never leaves a glow behind on a body that was glowing for another reason

Action: add narrow server/world tests around the real runtimes — valid swap, blocked destination, second-teleport failure and rollback, motion and rotation preservation, cooldown started on success and not on failure, and one mark-leak test per form. Keep the existing pure tests as fast checks and keep real runClient smoke for graphics-dependent behavior.

### E1a — Ability cooldown survives respawn and resets on disconnect

Verified 2026-07-26 against `src/main/java/jujutsu/mod/character/CharacterAbilityCooldowns.java`.

`READY_AT` is keyed by `(player UUID, vessel, slot)` and is pruned only on `ServerPlayConnectionEvents.DISCONNECT` and `SERVER_STOPPING`. There is no death or respawn hook, so a running cooldown survives respawn but is cleared by a disconnect/rejoin. For a 60-tick cooldown this is harmless.

The vessel is part of the key because a slot names an input position, so the same slot is a different ability for each vessel; without it one vessel's cooldown refused another's ability after a switch. `CHARACTER_STATE` is `copyOnDeath()`, so the vessel resolves identically after respawn and the policy above is unchanged. Switching away and back does not reset a cooldown: the stored value is an absolute game time, so it resumes where it left off.

This is recorded as the intended policy rather than a defect, so that a future refactor cannot change it silently. Confirm it during the next manual smoke; if the desired policy is different, change it deliberately and update this entry.

### E1b — TargetResolver ordering is a cross-character contract

Verified 2026-07-26 against `src/main/java/jujutsu/mod/combat/TargetResolver.java`. The dead-tie-break defect below is fixed; the cross-character exposure and the missing smoke are still open.

What was wrong: the comparator claimed to fall back to crosshair proximity, but that key sat behind an exact-equality test on `hitDistance` — a double from `AABB.clip` plus `distanceTo` — which two real entities essentially never satisfy. The second key was therefore dead, and "closest to the crosshair" was never implemented: a mob dead-centre in the crosshair lost to a nearer mob clipped by the very edge of its 0.35 aim-assist pad.

Ranking now has three live keys, in order:

1. **Pierced before grazed.** `EntityCandidate.pierced` comes from a second `clip` against the un-inflated bounding box, so a body the ray truly entered always beats one only the aim-assist pad caught. Aim assist can no longer steal the target from what the player is looking straight at.
2. **Depth for real hits, crosshair angle for grazes.** Pierced candidates rank by `hitDistance` along the ray. Assist-only grazes rank by `angularOffset` — the perpendicular offset divided by the distance to the candidate, so a far target is not punished for being far.
3. **Entity id, always last.** This closes a second hole: a perfectly tied pair used to be decided by entity-section iteration order, so the chosen target could flip between ticks as entities moved.

Detection is untouched — still ray–AABB, not centre-near-ray. The extra record component defaults to a real hit in both existing `EntityCandidate` constructors, so no call site changed. `TargetResolverTest` now covers real-hit-beats-graze, angle between grazes, distance between equally aimed grazes, and id-decided ties independent of list order.

Still open, and the reason this entry survives the fix: the resolver is shared by four callers — `TodoBoogieWoogieRuntime`, `NobaraHammerCombatRuntime`, `ProjectJjkNobaraRuntime`, and `ProjectJjkRitualRuntime` — so this was a roster-wide gameplay change, not a per-ability tweak, and it has only pure-comparator coverage. **Needs a Nobara targeting regression smoke: hammer targeting, nail launch, and directed Hairpin.** Until that runs, "assist no longer steals the target" is verified as comparator logic and UNVERIFIED as feel. Any future comparator edit needs the same smoke.

### E2 — Curse-link options payload is not bounded

Verified 2026-07-26: `CurseLinkOptionsPayload.read` still does `readVarInt()` and loops that many times with an unbounded `readUtf()` per entry.

The payload trusts an incoming list size and unbounded technique string, while the client creates one button per entry.

Action: cap entries and string length, reject malformed ids, and add scrolling/pagination if the list can grow.

### E3 — Some server runtime state is still static and unevenly cleaned

CombatStagger, preparation state, anchor-removal tracking, and related maps use different cleanup rules. Most are server-thread safe, but long-running worlds need explicit ownership and pruning.

Action: centralize per-server state or add lifecycle/TTL cleanup with tests.

## Limits of the build-time gate

An adversarial review of the architecture rules ran 16 attacks against a green build; 15 are now caught, and the boundary below is where a structural rule stops being able to help. **This list is the honest edge of what a green `qualityGate` proves.** It is not a to-do list — each entry needs a different kind of test, not a better rule.

- **A vessel registering a callback into shared mutable state.** Every reference points vessel → shared, the permitted direction, yet shared dispatch becomes vessel-specific after init. Confirmed green against every rule. Catching it needs a unit test over the dispatcher, not a dependency rule.
- **A class name assembled from fragments at runtime.** `Class.forName` on a literal is caught by `SourceBoundaryTripwireTest`; a name built by concatenation at runtime is not, and never will be.
- **A constant copied by hand.** Reading `TodoProfile.BOOGIE_WOOGIE_RANGE` is caught. Typing `20.0` with a comment saying where it came from is indistinguishable from any other number.
- **A shared extension point with exactly one implementer.** A method on `CharacterDefinition` that only one vessel overrides is structurally identical to a genuine shared hook. Catching it needs a test that counts implementers per method.
- **In-world behaviour of any kind.** Nothing in the suite constructs a `ServerLevel`. See the Verification Policy in AGENTS.md.

One more limit was found on 2026-07-28 and is tracked separately as E15: a rule can only help if the gate actually runs it, and a check can be green while proving nothing. Neither failure is visible from inside the rule set.

### E14 — four vessel-named classes live in shared packages

`NobaraVfxIds` and `TodoVfxIds` sit in `jujutsu.mod.vfx`, which is the `<Character>VfxIds` shape AGENTS.md prescribes, so those two are deliberate. `NobaraHudState` in `jujutsu.mod.client.fx` and `ProjectJjkNailRenderer` in `jujutsu.mod.client.render` are not: both are vessel code in a shared package while `client.render.nobara` already exists. `VesselBoundaryTest#vesselNamedClassesStayInTheirVesselPackage` pins the set of four, so moving either one fails the test and forces this entry to shrink with it.

### E15 — Test-suite defects and cleanup are tracked in PR #17

Opened 2026-07-28. Documentation only so far; nothing has been fixed yet.

A full read of `src/test/java/jujutsu/mod/**` and the verification half of `build.gradle` produced an ordered remediation plan, which lives in [TEST_ARCHITECTURE_PLAN.md](TEST_ARCHITECTURE_PLAN.md) and is proposed in **[PR #17](https://github.com/grebeshok105/jujutsu-minecraft/pull/17)**. That file is the detail; this entry exists so the register does not have to be read alongside a pull request to know the suite has open defects.

Two of the findings are recorded here in full, because they are not "work to schedule" — they are checks that are green today while proving nothing, and a reader of this register should not have to open a pull request to learn that:

1. **`BlackFlashWindowTest` does not check the Black Flash chance.** The assertion is `hammer.contains("BLACK_FLASH_CHANCE") || profile.contains("BLACK_FLASH_CHANCE = 0.10f")`. The left operand is true whenever the runtime names the constant, which it always does, so the operand that pins the value never runs. Setting the chance to `0.99f` leaves the suite green. The fix is two independent assertions — read the constant directly for the number, keep the grep for "the runtime uses the shared constant rather than an inline literal" — because neither implies the other.
2. **The call-order checks in the same file pass when their subject is deleted.** They compare `runtime.indexOf(A) < runtime.indexOf(B)` without checking that either fragment was found. A missing `A` gives `-1 < n`, which is true. The failure mode is asymmetric: losing `B` fails correctly, losing `A` is a false green.

The rest of the plan, in the order it should be done: wire `check` to the `verification` task group instead of re-listing the tasks by hand; fix `isInsideVesselPackage`, which matches `"/" + vesselId + "/"` anywhere in a path and so silently excludes any unrelated directory named after a vessel from the shared-code scan (fail-open, against the fail-closed design of everything around it); raise the stale floors, starting with `>= 2` vessel types where three are registered; remove greps that duplicate the compiler or another check; and then the long-running items — splitting `ProjectSanityTest`, migrating the `main()`+`assert` programs to JUnit one class at a time, covering `CharacterAbilityExecutor`, and evaluating mutation testing on the pure policy classes.

Two of those connect directly to entries already in this register. The `CharacterAbilityExecutor` gap is the same one "Limits of the build-time gate" describes as needing "a unit test over the dispatcher, not a dependency rule". The hand-maintained `check` list is the reason a new verification program can exist, carry `-ea`, satisfy `verifyAssertionsEnabled`, and still never run — that task audits assertion flags, not invocation.

The plan marks as UNVERIFIED anything that needs a local build or a checkout-wide count, including the new floor values and whether `CharacterAbilityExecutor` already has indirect coverage. Do not lift numbers out of it into this register without counting them first.

## Medium-priority work

### E4 — VFX delivery is transient and radius-filtered

Clients outside the broadcast radius at cast time do not receive a cue. This is acceptable for most short effects, but critical long-lived visuals need explicit state or catch-up rather than wider blind broadcast.

### E5 — Localization parity is not enforced automatically

Verified 2026-07-26: both `en_us.json` and `ru_ru.json` now hold 95 keys with an empty difference in both directions, no duplicates, and matching format specifiers. The key gap this entry originally tracked is closed. Its earlier figures (88 / 54) were themselves stale by the time they were checked; the real pre-fix counts were 92 / 58.

What remains is the half that keeps it from reopening: nothing in `check` compares the two key sets, so the next English key added will silently drift again. Note also that `ru_ru.json` uses a leading-comma style from line 20 onward, which a naive generator would break.

Action: add a key-set parity check to the verification suite. Two smaller judgement calls are recorded rather than fixed: `message.jujutsumod.nobara.self_resonance.selected` leaves "Self Resonance" in Latin while every other ability label is translated, and `screen.jujutsumod.modern_menu` reads "Characters" in English but «Выбор сосуда» in Russian — the English side is the more likely stale one.

### E6 — ClickGui rendering has avoidable per-shape work

Render2D immediately begins and flushes SDF for each shape to preserve MSDF ordering. SdfRenderer allocates/uploads per flush. Profile in-game before redesigning; if material, batch by render layer and reuse staging buffers.

### E7 — Closed: shared code no longer branches on a vessel

Closed 2026-07-26 on feat/todo-input-slots by the vessel definition seam: every `JujutsuCharacter` constant binds one server definition (`CharacterDefinition` in `JujutsuCharacters`) and one client definition (`CharacterClientDefinition` in `JujutsuCharacterClients`), and the shared files that used to name vessels — mod init, client init, `CharacterAbilityExecutor`, `CharacterCombatModifiers`, `CharacterGeoRenderers`, `ClickGuiTheme`, `JujutsuModules`, `CharacterRosterPanel`, `CharacterSkinMixin` — now ask the registries. The contract is owned by the Codex note `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md`.

Recounted 2026-07-26: exactly seven direct `JujutsuCharacter.NOBARA`/`.TODO` references remain across `src/main` and `src/client`, one per file, all deliberate:

- Four are the `id()` declarations in the vessel definitions themselves (`NobaraDefinition`, `TodoDefinition`, `NobaraClientDefinition`, `TodoClientDefinition`) — a definition naming the constant it speaks for is the seam working, not a leak.
- `JujutsuCommands` refuses the `hairpin` debug commands unless Nobara is selected, because a slot is an input position and `PRIMARY` cast as Todo would fire his swap while reporting a hairpin.
- `TodoBlackFlashRuntime` filters its own damage listener for Todo — a vessel's own hook checking for itself.
- `TodoSwapMarkerItem.use` refuses a non-Todo thrower on both sides through `CharacterSelectionView` — the E12 fix.

One vessel-specific line survives in shared code without naming an enum constant: `JujutsuKeybinds.isTechniqueWeapon` still spells out Nobara's two hammers to decide whether left click counts as `ATTACK_CONTEXT`. It leaves when the client definition can answer "is this stack my technique weapon".

### E8 — Standard test reporting is weak

Verified 2026-07-26: `build.gradle` registers 30 custom JavaExec verification programs.

They use main methods and Java assertions. They are useful and green, but do not provide normal per-test JUnit reports or GameTest world integration — see E1 for the coverage gap that follows from having no world-level tests.

Stale figure, flagged 2026-07-28: the count of 30 above predates the current tree and is no longer correct. It is deliberately left as written rather than replaced with a second guess — recount it locally with `./gradlew tasks --group verification` and correct it in the same change that wires `check` to the task group (see E15), after which the number stops being maintained by hand in two places. The rest of this entry still holds: the migration path off `main()`+`assert` is E15's Tier 4.

### E9 — Build reproducibility can improve

Verified 2026-07-26: `gradle.properties` still pins `loom_version=1.17-SNAPSHOT`.

CI now tests Java 21. Pin a stable Loom release when available, add dependency locking if releases become important, and add a second supported-JDK matrix only after it is proven compatible.

### E10 — Nobara's generic fallback erases five specific diagnostics

Verified 2026-07-26 against `NobaraAbilityRouter`, `NailTrapRuntime` and `SelfResonanceRuntime`.

`NailTrapRuntime.tryPlace` and `SelfResonanceRuntime.tryCast` display a specific reason and then return `false`. The router's fallback immediately writes `message.jujutsumod.nobara.action.no_target` into the same action-bar slot, which replaces the text and resets its timer. So `trap.no_ground`, `trap.unsupported`, `trap.no_nails`, `trap.failed` and `self_resonance.no_link` are authored and translated in both languages but never actually read by a player.

Pre-existing: inherited verbatim from the int-keyed gate the router replaced, so this is not migration damage. It is recorded rather than fixed because the fix changes what players see, and the router cannot currently tell "the runtime already explained itself" from "nothing was found". The honest shapes are either a tri-state return from each runtime, or dropping the fallback for the slots whose runtimes speak.

Worth being exact about the cost of waiting, because it is smaller than it looks: the blocker is not `CharacterDefinition.tryCast`'s `boolean`. Widening that alone would change nothing, since the router still could not tell the two failures apart. The information has to come from `NailTrapRuntime` and `SelfResonanceRuntime` themselves, so the fix reaches into the runtimes whenever it is done, and the shared interface can be widened at that point at no extra cost.

`NobaraAbilitySlotsTest` deliberately scopes its message count to the router and says so, rather than claiming a property it cannot establish across files.

### E11 — The shared cooldown message now precedes Nobara's silent stagger check

Verified 2026-07-26 against `CharacterAbilityExecutor` and `NobaraAbilityRouter`.

The gate this router replaced ran selection, then stagger as a silent early return, then the ability. The shared executor now checks the cooldown between selection and dispatch, and that check is not silent — it displays `message.jujutsumod.character.action.cooldown`. A player who is both staggered and recharging would therefore be told about the cooldown where the old order said nothing.

Inert today: no Nobara ability writes to `CharacterAbilityCooldowns`, so she never has one to be told about. It becomes reachable the first time one of her abilities takes a cooldown, which makes this a decision to take deliberately at that moment rather than a bug to fix now. The clean resolution is to let a vessel own the ordering of its own gates.

### E12 — Closed: the swap marker item now has a vessel gate

Closed 2026-07-26 on feat/todo-input-slots, verified against `TodoSwapMarkerItem` and `CharacterSelectionView`.

`TodoSwapMarkerItem.use` now refuses any thrower who is not Todo, checked on **both** sides through `CharacterSelectionView` — the server reads its own selection, the client reads the mirror handed in at client init. Both sides matter because vanilla calls an item's `use` on the client too; a server-only gate would let the client predict a throw the server then refuses, taking back a consumed item and a played sound.

For the history: the gap existed because `TodoDefinition.onDeselected` correctly cleans up only for the vessel being left, where the old every-selection clear had hidden the missing gate by destroying stray markers as a side effect. A player who was Nobara, or nobody, could leave a mark in the world that only Todo could ever use.

Still open, related: `CharacterPlayerState.hasClaimedStarter` has no production callers at all. The starter claim is recorded and persisted for every vessel, but nothing reads it, because the loadout is deliberately re-applied on every selection so a lost kit can be restored. Either give the claim a job or delete it; leaving persisted state that nothing consumes invites someone to trust it later.

### E13 — Closed: the network layer no longer names a vessel

Closed 2026-07-27 on `fix/e13-network-vessel-seam`. `JujutsuNetworking.registerServerReceivers` used to register the `SelectCurseLinkPayload` receiver by calling `jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime.select` through an inline fully qualified name — a vessel runtime reached directly from shared code, which the vessel seam forbids.

The receiver now hands a neutral intent to the seam that already existed: `JujutsuCharacters.of(player).selectCurseLink(player, linkId)`. A curse link is a shared concept — `jujutsu.mod.curse` is a shared package that `JujutsuCommands` reads too — so what travels is *the player picked link X*, and only the player's own vessel decides what that means. `CharacterDefinition.selectCurseLink` defaults to refusing, `NobaraDefinition` overrides it, and the Self Resonance logic never left her package.

**It fixed a second defect that was not the one being tracked.** The old receiver honoured the packet from any sender, because it named one vessel's runtime instead of asking who the sender is. Routing it through the definition means a player who is not the vessel that opened the picker is refused by the same seam that already refuses a stale-vessel ability cast.

Why it survived every source-text check until ArchUnit found it: an inline fully qualified name leaves no `import` line to grep, and `NobaraAbilitySlotsTest` asserted the call was present — protecting the packet from deletion, but pinning its registration site as a side effect. That half of the assertion is gone; the packet itself is still required to exist.

Both allowlist entries went with it. `VesselBoundaryTest#theOneKnownNetworkLeakDoesNotGrow` is now `#theNetworkLayerTouchesNoVesselCode` and asserts the empty set rather than one permitted class; the `JujutsuNetworking` entry in `SourceBoundaryTripwireTest#TRACKED_DEBT` is deleted. The rule was tightened rather than deleted, against the instruction this entry used to carry: a receiver wired straight to a vessel runtime is the easiest seam breach in the codebase to write, and this is the only check that can see it. Proven by mutation rather than by a green run — the mutation and its failure message are in the commit body.

**One residue, recorded rather than hidden.** `selectCurseLink` is a shared extension point with exactly one implementer, which "Limits of the build-time gate" above lists as a thing no structural rule can tell from a genuine shared hook. `canonicalSlot` sits in the same position and is accepted for the same reason: the alternative is shared code that knows which vessel asked.

## Low-priority product debt

- Crafting recipes and broader datapack content are intentionally absent.
- Publication automation for Modrinth/CurseForge should wait until release provenance is clean.
- Some generic Rich ClickGui modules/components are unused and can be removed after confirming the final UI scope.

## Resolved and now in main

These are closed. They are kept as a short list only so a reader does not reopen them; the live behavior is described in AGENTS.md under "Current slice (facts)".

- Character selection persists through Fabric Data Attachment API and is copied on death.
- Nobara's starter kit is restored idempotently on every selection — it fills only a missing hammer, doll or nails, so re-selection cannot duplicate held tools. (This deliberately reversed the earlier one-time-claim rule; the persisted claim is now recorded for every vessel and read by nothing — see E12.)
- Loaded ordinary embedded nails have a TTL and a per-owner cap.
- Hairpin R/B resolve nails through EmbeddedNailRegistry instead of level.getAllEntities().
- VFX recipe registration goes through each vessel's `CharacterClientDefinition.registerClientHooks()`; the `JujutsuVfxRecipes` aggregator is deleted so the list of who has recipes cannot drift from the list of who exists.
- `TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS` and `WORLD_BORDER_MARGIN` are wired into `TodoBoogieWoogieRuntime` instead of being dead constants.
- Todo has a GeckoLib model, animations, and a player renderer; the `ability.boogie_woogie` hook is live, not a no-op.
- Todo roster labels are localized.
- Documentation audit tooling rejects stale references, broken local links, and stale code-derived metrics, and scopes itself to git-tracked Markdown.
