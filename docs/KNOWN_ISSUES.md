# Known Issues and Technical Debt

Status: CURRENT LIVE REGISTER

Last code verification: 2026-07-26. Entries carrying a "Verified 2026-07-26" line were re-checked against source on that date. E3, E4, and E6 were last checked on 2026-07-23 and were not re-verified in this pass; treat their detail as older than the rest.

Applies to: main and the active branch feat/clickgui-drag-and-todo-fake-clap. The earlier branch fix/persistence-nail-lifecycle-docs-sync no longer exists; its work is in main.

Owner hierarchy: current code/tests → AGENTS.md → SESSION.md → Codebase Codex → this register

## Accepted product decisions

### Global Resonance hit-stop

This register owns the rationale; other documents point here.

Resonance intentionally changes the global server tick rate to create hit-stop. This affects every player and dimension, but the current product target is private play for one or two people. Do not remove it as a generic multiplayer optimization. Reopen only if the target becomes a public or competitive server.

### Boogie Woogie destinations have no entity-occupancy gate

Verified 2026-07-26 against `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java`.

`findSafeDestination` gates a destination only on world bounds, chunk load, world border, and solid-block collision (`isPlaceableDestination`, `isInWorldDestination`). There is no floor requirement and no check that another entity already occupies the destination — no `isPickable` or entity-query call exists anywhere in the file, and its doc comment states the policy explicitly: "No floor, no third-party entity occupancy gates."

This is deliberate for the current 1–2 player target: air, water, crawl, and flight destinations are all intended to be valid. It is recorded here because an earlier revision of SESSION.md wrongly claimed a non-living-collision fix ("A3") had landed, and that false claim also reached a pull-request description. It never landed.

Resolved since: `findSafeDestination` now takes a `Strictness`. The ordinary Todo-and-target swap keeps `SOFT`, which retains the last-resort fallback as shipped game feel; `STRICT` has no fallback and is for swaps that move third parties. The world-border test moved into `isInWorldDestination`, so the fallback path enforces it too. The unused `otherSwapParticipant` parameter is gone — its comment claimed the fallback existed because collision is picky about the partner's volume, but `Level.noBlockCollision(Entity, AABB)` tests block shapes only and never consults entities, so there was nothing to exclude.

Residual, and inherent to the policy: two swapped entities can end up interpenetrating, and either can land inside a non-living collidable entity such as a boat or minecart. Vanilla teleport/collision semantics decide what happens next; no crash has been observed or reproduced.

Reopen only if the product target becomes public/competitive play, or if a live smoke test shows a concrete stuck/suffocation case.

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

Specifically for Todo: no test calls any of the four `tryCast` entry points (`TodoBoogieWoogieRuntime`, `TodoFakeClapRuntime`, `TodoPairSwapRuntime`, `TodoMarkerSwapRuntime`). The Todo tests (`TodoProfileTest`, `TodoSwapPlanTest`, `TodoTargetSafetyTest`, `TodoHandsEmptyTest`, `TodoFakeClapTest`, `TodoPairSwapTest`, `TodoSwapMarkerTest`) cover profile constants, `TodoSwapPlan.preflight` null-handling, boolean truth tables including the shared `TodoSwapGates` clap gate, the pure `TodoPendingSelection` and `TodoSwapMark` predicates, and — for the three newest mechanics — source-text contract assertions rather than behaviour. Nothing constructs a `ServerLevel` or exercises an actual teleport.

The gap widened rather than narrowed with this branch: three whole mechanics landed under it. Untested as a result:

- real player↔mob and player↔player swap, blocked destinations, second-teleport failure and rollback, velocity / yaw / pitch / head-yaw / fall-distance preservation, the packet path end to end
- whether the feint clap and the real clap are actually indistinguishable to a second player
- the pair swap's whole selection lifecycle against a live world — expiry, marked-body death, dimension change, and above all that a STRICT cancellation moves nobody rather than half-applying to a bystander
- both thrown-mark forms: that a resting marker is discarded on every exit path, and that an entity mark never leaves a glow behind on a body that was glowing for another reason

Action: add narrow server/world tests around the real runtimes — valid swap, blocked destination, second-teleport failure and rollback, motion and rotation preservation, cooldown started on success and not on failure, and one mark-leak test per form. Keep the existing pure tests as fast checks and keep real runClient smoke for graphics-dependent behavior.

### E1a — Ability cooldown survives respawn and resets on disconnect

Verified 2026-07-26 against `src/main/java/jujutsu/mod/character/CharacterAbilityCooldowns.java`.

`READY_AT` is keyed by player UUID and is pruned only on `ServerPlayConnectionEvents.DISCONNECT` and `SERVER_STOPPING`. There is no death or respawn hook, so a running cooldown survives respawn but is cleared by a disconnect/rejoin. For a 60-tick cooldown this is harmless.

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

## Medium-priority work

### E4 — VFX delivery is transient and radius-filtered

Clients outside the broadcast radius at cast time do not receive a cue. This is acceptable for most short effects, but critical long-lived visuals need explicit state or catch-up rather than wider blind broadcast.

### E5 — Localization parity is not enforced automatically

Verified 2026-07-26: both `en_us.json` and `ru_ru.json` now hold 92 keys with an empty difference in both directions, no duplicates, and matching format specifiers. The key gap this entry originally tracked is closed. Its earlier figures (88 / 54) were themselves stale by the time they were checked; the real pre-fix counts were 92 / 58.

What remains is the half that keeps it from reopening: nothing in `check` compares the two key sets, so the next English key added will silently drift again. Note also that `ru_ru.json` uses a leading-comma style from line 20 onward, which a naive generator would break.

Action: add a key-set parity check to the verification suite. Two smaller judgement calls are recorded rather than fixed: `message.jujutsumod.nobara.self_resonance.selected` leaves "Self Resonance" in Latin while every other ability label is translated, and `screen.jujutsumod.modern_menu` reads "Characters" in English but «Выбор сосуда» in Russian — the English side is the more likely stale one.

### E6 — ClickGui rendering has avoidable per-shape work

Render2D immediately begins and flushes SDF for each shape to preserve MSDF ordering. SdfRenderer allocates/uploads per flush. Profile in-game before redesigning; if material, batch by render layer and reuse staging buffers.

### E7 — Second-character integration is still Nobara-shaped

Verified 2026-07-26: 17 direct `JujutsuCharacter.NOBARA` references remain across src/. The shared vessel render stack removed the per-character branching in rendering, but not elsewhere.

Selection, UI cards, action payloads, loadout dispatch, and VFX registration contain direct Nobara branches. Do not build a giant abstraction early, but extract CharacterDefinition/handler boundaries when the second real kit is approved.

### E8 — Standard test reporting is weak

Verified 2026-07-26: `build.gradle` registers 27 custom JavaExec verification programs.

They use main methods and Java assertions. They are useful and green, but do not provide normal per-test JUnit reports or GameTest world integration — see E1 for the coverage gap that follows from having no world-level tests.

### E9 — Build reproducibility can improve

Verified 2026-07-26: `gradle.properties` still pins `loom_version=1.17-SNAPSHOT`.

CI now tests Java 21. Pin a stable Loom release when available, add dependency locking if releases become important, and add a second supported-JDK matrix only after it is proven compatible.

## Low-priority product debt

- Crafting recipes and broader datapack content are intentionally absent.
- Publication automation for Modrinth/CurseForge should wait until release provenance is clean.
- Some generic Rich ClickGui modules/components are unused and can be removed after confirming the final UI scope.

## Resolved and now in main

These are closed. They are kept as a short list only so a reader does not reopen them; the live behavior is described in AGENTS.md under "Current slice (facts)".

- Character selection persists through Fabric Data Attachment API and is copied on death.
- Nobara starter tools are claimed once per player instead of being refilled on every selection.
- Loaded ordinary embedded nails have a TTL and a per-owner cap.
- Hairpin R/B resolve nails through EmbeddedNailRegistry instead of level.getAllEntities().
- VFX recipe registration goes through the single `JujutsuVfxRecipes.registerAll()` aggregator.
- `TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS` and `WORLD_BORDER_MARGIN` are wired into `TodoBoogieWoogieRuntime` instead of being dead constants.
- Todo has a GeckoLib model, animations, and a player renderer; the `ability.boogie_woogie` hook is live, not a no-op.
- Todo roster labels are localized.
- Documentation audit tooling rejects stale references, broken local links, and stale code-derived metrics, and scopes itself to git-tracked Markdown.
