# Known Issues and Technical Debt

Status: CURRENT LIVE REGISTER
Last code verification: 2026-09-13 (Cursed Saga branch `feat/cursed-saga` before commit:
qualityGate green — 142/142 GameTests, 541 JUnit; live MCP smoke 16/17 oracle checks green,
the 17th a driver-timing artifact documented in the Cursed Saga continuation notes).
Earlier verifications: 2026-07-29. Entries carrying a "Verified 2026-07-29" line were re-checked
against source on that date; E3, E4, and E6 were last checked on 2026-07-23 — treat their detail
as older than the rest.

Applies to: main and the active branch `feat/cursed-saga` (Cursed Saga). The earlier branch refactor/vfx-remove-dead-surface and fix/persistence-nail-lifecycle-docs-sync no longer exist; their work is in main.

Owner hierarchy: current code/tests → AGENTS.md → SESSION.md → Codebase Codex → this register

## Accepted product decisions

### Global Resonance hit-stop

This register owns the rationale; other documents point here.

Resonance intentionally changes the global server tick rate to create hit-stop. This affects every player and dimension, but the current product target is private play for one or two people. Do not remove it as a generic multiplayer optimization. Reopen only if the target becomes a public or competitive server.

### VFX Core does not provide client-global slow motion

Verified 2026-07-29 against the removed `VfxTimeChannel` path, `NobaraVfxRecipes`, and `VfxDirector`.

`VfxTimeChannel` stored a scale and deadline. `dollStrike` and `resonanceRelease` wrote values into it, but no production consumer applied `VfxDirector.timeScale()`, so real client-global slow motion never existed. The channel and its calls are removed as dead API, not replaced with another system. Server-global Resonance hit-stop remains the separate accepted decision above. Client-global slow motion may return only through an independently approved design with an explicitly named consumer and lifecycle.

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

### ProjectJJK placeholder assets

Owned by [PROVENANCE.md](PROVENANCE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Those files hold the permission scope, the retained upstream notice, and the replacement policy. Only the release-blocking consequences are tracked here, as R3.

### Shikigami selection is in-memory, and the slice ships with accepted limits

Decided with the `feat/megumi-shikigami` branch. Accepted limits of the Ten Shadows slice
(Nue / Toad / Rabbit Escape / Max Elephant); reopen any of them only with an explicit owner call,
not as drive-by "fixes".

1. **The shikigami selection resets on relog.** `MegumiShikigamiSelection` is a static
   owner-keyed map by design (see its javadoc): only `DISCONNECT` and `SERVER_STOPPING` clear it,
   vessel deselect deliberately keeps it, and the dev/MCP fixture-reset tool clears it as an extra
   step. There is no persistence, so a rejoining player is back on `DOGS`. Persist it only through
   an approved design (the dog pack itself is equally transient).
2. **Shikigami sounds are vanilla placeholders.** No per-shikigami sounds were found upstream, so
   Nue speaks phantom, the toad speaks frog, the rabbits speak rabbit, and the elephant speaks
   ravager, as literals at the call sites (no NEW `JujutsuSounds`/`sounds.json` entries — the
   bodies still play the existing shared shadow-open swell and recall implosion). Swapping in
   real voice lines later touches call sites only.
3. **The Rabbit Escape texture is near-flat upstream.** `megumi_rabbit.png` ships byte-identical
   to the Sorcery Age source (321 bytes); no cleanup pass is planned. The swarm reads through
   motion and count, not fur detail.
4. **The toad tongue is VFX-only.** `toad_tongue.png`/`toad_wings.png` are deliberately unshipped
   (unreferenced by the imported geo — see PROVENANCE), so the tongue strike has a cue and a yank
   but no tongue geometry. Adding a tongue model is new art, not a bug fix.
5. **GameTest displacement oracles must not use `NoAI` mobs.** Measured in game 2026-09-11: a
   `NoAI:1b` mob is fully frozen — external velocity is stored but the position never integrates,
   not even gravity. Assert displacement only on AI mobs with zeroed speed (Slowness amplifier
   100), otherwise assert velocity/effect state. Recorded so a future scenario author cannot
   re-learn it the red way.
6. **The sic can out-range what the body can actually do.** `SIC_RANGE` (the aim) is 20 blocks for
   every type, while the toad's tongue reaches 12 and the elephant's jet corridor about 13.2 from
   the body. A sic past those marks still routes, plays the snap and the cue, and arms the 30-tick
   `PRIMARY_SNEAK` cooldown; the elephant now refuses to *fire* beyond its reach (review fix), and
   in both cases the body walks in and melees the mark instead — so the command is not wasted, it
   just does not telegraph the shorter reach. A per-type sic-range contract would fix the tell;
   until then it is a UX wart, not a broken strike.
7. **The elephant's jet is level.** `faceTarget` sets yaw only, so the corridor leaves the trunk
   at ~1.9 blocks with no pitch: bodies shorter than about 1.4 blocks (Rabbit Escape sits at 0.2)
   pass under it at any range. Aiming pitch at the target's chest would change which targets are
   hittable, so it is an owner call rather than a silent fix.
7b. **The Divine Dogs' pounce yaw looks mirrored.** `MegumiSummonRuntime` sets the pounce facing
   with `atan2(x, z)` where Minecraft's yaw convention needs `atan2(-x, z)` — the same defect the
   Nue and the Elephant shipped with until this branch. The dogs render correctly under vanilla AI
   facing and their PR (#72) verified their kit, so this is recorded rather than touched: it is a
   cosmetic pounce-facing question in a frozen system, and it deserves its own pass with the same
   frame evidence the shikigami got.
8. **The rabbit `run` clip is asymmetric upstream.** `megumi_rabbit.animation.json`'s `run` bends
   the left knee without the left foot and the right foot without the right knee (`walk` is
   symmetric). Shipped byte-identical to the Sorcery Age source; the swarm reads through motion
   and count, and editing a third-party clip is a new asset revision, not a bug fix.

9. **The MCP dev-lane save can kill the lane player before it loads.** Verified
   2026-09-13: persisted cursed spirits (they are the only summon-like entities that
   save — shikigami are `noSave()`) gathered near the spawn over several live passes,
   and a freshly booted lane player died inside the same crowd before the world finished
   loading. A dead singleplayer player sits on the death screen, which pauses the
   integrated server: entities freeze (`NoAI:1b` forever, TNT fuse never burns), MCP
   tools still answer (onMainThread tasks run between paused ticks), and every entity
   oracle silently voids. Win32 foreground cannot always be re-taken for the Respawn
   click (AnyDesk holds it; PostMessage does not reach LWJGL input). Recovery is
   offline: stop the lane, delete `run/saves/mcp-spike` (the retired spike worktree at
   `D:/WorkFlow/Jujutsu Minecraft/.worktrees/mcp-port-spike/run/saves/mcp-spike` holds
   the seeded copy), relaunch. The live driver (`live_block5b.py`) now refuses to run
   its oracles on a dead player for exactly this reason.

### Cursed spirits ship with accepted limits (slice 1)

1. **Balance is a first pass.** Every number lives in `CursedSpiritProfile` (tier rows + the shared
   crowd cap) and `CursedSpiritVariant` (look/weights/sounds); treat the table as the tuning
   surface, not a contract. The plan's numbers were chosen for feel, not measured against live
   play yet.
2. **No VFX for the tier bodies.** Model animation, sounds and knockback carry the read; the VFX
   Core is untouched. If the greater tier's slam reads flat in play, that is the time to revisit —
   not before.
3. **The spawn light gate is `PathfinderMob`'s walk-value, not vanilla's rolled darkness.** It
   refuses at local brightness ≳12 (glowstone) and allows dim 8–12; vanilla's `isDarkEnoughToSpawn`
   roll is deliberately not re-implemented (it would duplicate engine logic). The local crowd cap
   (10 within 48 blocks) plus the spawn weights keep the world from flooding; a stricter
   vanilla-dark feel needs one extra conjunct in `CursedSpiritEntity.checkSpawnRules`.
4. **Attack playback follows the profile, not the clip.** Non-looping clips are stop-then-started
   on every swing (`startIfStopped` would freeze the second attack) and the scream state is
   stopped by a server timer (`SCREAM_DURATION_TICKS = 12`, the authored SCREAMER length). If a
   clip's length is edited later, the walk gate and the scream window still key off the state, so
   the read stays consistent — but the timing contract is the profile, not the asset.
5. **Several authored clips are deliberately not shipped**: `WISTIVER.GRAVE`, `PROWLER.CLIMB`,
   `GULBER.SIT`, `BLUD.TELEPORT_IN/OUT`, `WALKING_BED.MASK/CRAWL_*` (no driver state or unused by
   the pack's own `setupAnim`). A clip without a driver is not shipped — the same rule that
   removed them from the contracts.
6. **Cursed spirits render through the vanilla model stack, not GeckoLib.** The pack ships
   vanilla-API Java models and `AnimationDefinition` classes, so the port is near-verbatim (see
   the Codex note). This is a deliberate second render path next to the GeckoLib house style; the
   vessel-boundary guard carries a scoped, documented `render → cursedspirit` exception.
7. **Strike reach is re-checked at the strike moment; line of sight is not.** A momentary LOS blip
   mid-swing must not abort an attack (recorded melee forgiveness), but a target that leaves reach
   no longer takes the hit.
8. **Greater spirits are immune to Boogie Woogie by tag** (`jujutsumod:boogie_woogie_immune`), a
   design choice: a greater-tier elite that a swap can teleport is a different fight. (The
   "150-HP elite" phrasing retired with the Cursed Saga grade axis — HP now belongs to the
   grade, the tier keeps morphology and the combat pattern.)
9. **FLOATING_CURSE is rigged from `Modelcurse_ghost` and glides** — the pack authored no walk
   clip for it, and its attack clip comes from the ghost animation set (wired explicitly).

### Cursed Saga ships with accepted limits (slice 2: #79–#82, #86)

1. **Balance is a first pass again, and the power axis moved.** Health/damage/speed now belong to
   the rolled **grade** (5→3 spawnable, absolute disjoint bands), while the tier keeps morphology
   and the combat pattern. Two slice-1 reads are deliberately retired: "greater = 150 HP elite"
   (a greater body is now whatever grade it rolled, position-nudged inside the band) and
   "lesser = the fastest" (speed is ordered by grade: 0.22–0.25 / 0.26–0.29 / 0.30–0.33). The
   archetype ranks inside a band are pinned by a unit test on purpose: a rebalance edits that test.
2. **Curse VFX go to an audience, not a radius.** Every curse cue (dash, slam, acid zone, spit,
   fear, regen, armor, berserk, runner) is broadcast through the `Predicate<ServerPlayer>`
   overload with `CursePerception::perceives`, on top of the tracking filter and the address-only
   voice sink. A non-perceiver standing inside the radius sees and hears nothing of the curse —
   including its world effects such as the acid pool.
3. **Held-by-the-toad mobs carry no marker after the throw.** Both kinds of victim are pinned by
   the same `HoldSupport` marker and both drop it at throw/recall/death. (A mob's `GRIPPED` was
   previously left to expire on its own — a foreign HARMFUL icon for up to 10 ticks.)
4. **Client suppression zeroes both recomputed input fields.** `HoldInputMixin` writes
   `keyPresses` **and** `moveVector` after `ClientInput.tick()`: zeroing only the first left WASD
   locomotion alive and rubber-banded the victim against the server pin. Jump/sneak/sprint and
   locomotion are suppressed; attack, item use, inventory and hotbar stay available (by design).
   The client half is not GameTest-able — its acceptance lives in the live lane.
5. **Servers keep the curse in one runtime state.** `CARRIED` (runner) and the hold markers are
   cleared on every exit path, expiry included — a stuck entry would deny attack/break/place with
   no visible cause, so the expiry branch routes through the effect's own `end()`.
6. **Fear inverts input, not text.** The fear debuffs invert movement axes, mouse buttons and the
   hotbar mirror through the canonical per-input-type handlers (`MouseHandler`/`KeyboardHandler`),
   so GUIs inherit the mapping. Text entry (chat, anvil, signs) is not covered by the input matrix
   and is a live-check item, not a promise.
7. **A greater-tier body is `ungrabbable` by tag, and that is a morphology call, not a level
   gate** (`ungrabbable.json`, precedent recorded in the plan): "don't forbid grabbing by power"
   is honoured inside the grabbable tiers — hold length still falls with the victim's health, so a
   grade-3 curse is held shorter than a grade-5 one.
8. **Fear/runner windows can outlive a vessel switch.** The window is not cancelled when the victim
   stops being a perceiver mid-effect (the runner's hold ends on the next `mayTouch` loss; the fear
   debuff is a timed effect that expires on its own). Accepted: the window is seconds long, and
   cancelling would need per-victim perception tracking for no player-visible gain.
9. **`fabric-gametest` entrypoints are a hand-maintained list — a GameTest class that is not in it
   silently never runs.** This slice shipped two such classes (grade + ability, 8 scenarios) and
   the lane was green without them; both reviews found it independently. Treat "class on disk ==
   class in entrypoints" as part of any lane claim.
10. **A spirit's melee strike reads the grade row, not the `ATTACK_DAMAGE` attribute.**
   `CursedSpiritAttackGoal.strike` prices the hit through
   `CursedSpiritAttackPolicy.primaryDamage(mob.gradeStats())`, so any transient modifier on the
   body is silently ignored by the direct hit — most visibly the berserk latch, whose
   `ATTACK_DAMAGE` modifier (+30→50%) raises the attribute but not the swing (the same applies to
   `/attribute` edits and future damage systems; the AoE shockwave reads its own
   `aoeDamage(gradeStats, row)` row). The attribute stays registered and base-synced
   (`createAttributes`/`applyStats`) because vanilla combat plumbing reads it, so this is a
   standing seam decision, not a missing registration: switching the strike to
   `mob.getAttributeValue(Attributes.ATTACK_DAMAGE)` is cheap when an owner call wants berserk to
   boost melee — flagged for awareness, accepted for now.

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

The 2026-09-11 Mythic Mounts Dire Wolf import (Divine Dogs visual) is in the same class: the author's permission is recorded as an owner statement only.

## High-priority engineering work

### E1 — No automated in-game smoke test, and no world/teleport coverage for the swap

Verified 2026-08-06. Still open.

CI compiles and runs assertion programs but the canonical gate does not boot a client. Renderer, mixin, packet, UI, and gameplay integration regressions can survive a green build. Issue #42 Stage A changed part of this: a server GameTest foundation now exists — the `gametest` source set with two neutral canaries (`jujutsu.mod.gametest.ServerGameTests`), run as `runGameTest` inside the canonical gate. Issue #42 Stage B adds the client side as a separate, non-required lane: `runClientGameTest` boots the real modded client with an integrated world and runs two client canaries (`jujutsu.mod.gametest.client.ClientLoadCanaryTest` — client boots with the production mod loaded — and `ClientObservationCanaryTest` — client/server observation with screenshot evidence), but the lane is manual-only (`workflow_dispatch` in CI) and stays deliberately outside `qualityGate`; the gate itself still boots no client. The canaries prove the harnesses; they exercise no ability, no vessel runtime, and no gameplay. Every world/ability scenario below remains uncovered.

Widened again 2026-07-26 by the Boogie Woogie impact pass. Nothing in it can be tested by the current harness beyond pure helpers: the sound duck's actual effect on `SoundManager`, the afterimage's readability against a real body, whether `hurtMarked` genuinely restores visible momentum for a moved player, and whether the momentum window is spent by the hit the player thinks it was. The client-smoke checklist for all of it is in `SESSION.md`.

Specifically for Todo: no test calls any of the five cast entry points (`TodoBoogieWoogieRuntime`, `TodoFakeClapRuntime`, `TodoPairSwapRuntime`'s pair and triple paths, `TodoStoneRuntime`). The Todo tests (`TodoProfileTest`, `TodoSwapPlanTest`, `TodoTargetSafetyTest`, `TodoHandsEmptyTest`, `TodoFakeClapTest`, `TodoPairSwapTest`, `TodoStoneTest`, `TodoTripleSwapTest`) cover profile constants, preflight null-handling, boolean truth tables including the shared `TodoSwapGates` clap gate, the pure cycle-direction and rollback-order mappings, and source-text contract assertions rather than behaviour. Nothing constructs a `ServerLevel` or exercises an actual teleport.

The gap has only widened with each wave — the input-slots branch put three whole mechanics under it, and the stone rework replaced one of them with two more. Untested as a result:

- real player↔mob and player↔player swap, blocked destinations, second-teleport failure and rollback, velocity / yaw / pitch / head-yaw / fall-distance preservation, the packet path end to end
- whether the feint clap and the real clap are actually indistinguishable to a second player
- the pair swap's whole selection lifecycle against a live world — expiry, marked-body death, dimension change, and above all that a STRICT cancellation moves nobody rather than half-applying to a bystander
- the stone's whole life against a live world — flight, collision vanish, lifetime expiry, the V self-swap and Shift+V target swap, and that a STRICT refusal on either moves nobody
- the triple cycle against a live world — three-body preflight refusal moving nobody, and the rollback path actually restoring every moved body when a mid-commit teleport fails

Action: add narrow server/world tests around the real runtimes — valid swap, blocked destination, second-teleport failure and rollback, motion and rotation preservation, cooldown started on success and not on failure, and one stone-leak test per exit path. Keep the existing pure tests as fast checks and keep real runClient smoke for graphics-dependent behavior.

### E1a — Ability cooldown survives respawn and resets on disconnect or a vessel switch

Verified 2026-07-26 against `src/main/java/jujutsu/mod/character/CharacterAbilityCooldowns.java`;
switching policy revised 2026-09-12 (issue #84).

`READY_AT` is keyed by `(player UUID, vessel, slot)` and is pruned only on `ServerPlayConnectionEvents.DISCONNECT` and `SERVER_STOPPING`. There is no death or respawn hook, so a running cooldown survives respawn but is cleared by a disconnect/rejoin. For a 60-tick cooldown this is harmless.

The vessel is part of the key because a slot names an input position, so the same slot is a different ability for each vessel; without it one vessel's cooldown refused another's ability after a switch. `CHARACTER_STATE` is `copyOnDeath()`, so the vessel resolves identically after respawn and the policy above is unchanged.

**A vessel switch is a clean slate (revised 2026-09-12, issue #84).** This entry used to record the opposite — "switching away and back does not reset a cooldown" — which shipped as a defect: a player who left a vessel mid-cooldown returned to a half-spent deadline with no visible cause. `CharacterSelectionManager.select` now calls `clearForCharacter(player, previousVessel)` for the vessel that is leaving (after `onDeselected`, so a teardown-armed deadline goes too) and mirrors a zero for every slot to that player's client. Re-confirming the **same** vessel clears nothing on purpose: the menu must not be a cooldown-reset button.

Accepted consequence: because the recall and death prices are cooldowns, deselecting and re-selecting a vessel now also clears those prices. The owner asked for the clean slate on 2026-09-12, and the alternative (keeping the price across a switch) is what the issue reported as broken. Respawn and disconnect behaviour is unchanged. Confirm the switch case during the next manual smoke; if the desired policy changes again, change it deliberately and update this entry.

### E1b — TargetResolver ordering is a cross-character contract

Verified 2026-07-26 against `src/main/java/jujutsu/mod/combat/TargetResolver.java`. The dead-tie-break defect below is fixed; the cross-character exposure and the missing smoke are still open.

What was wrong: the comparator claimed to fall back to crosshair proximity, but that key sat behind an exact-equality test on `hitDistance` — a double from `AABB.clip` plus `distanceTo` — which two real entities essentially never satisfy. The second key was therefore dead, and "closest to the crosshair" was never implemented: a mob dead-centre in the crosshair lost to a nearer mob clipped by the very edge of its 0.35 aim-assist pad.

Ranking now has three live keys, in order:

1. **Pierced before grazed.** `EntityCandidate.pierced` comes from a second `clip` against the un-inflated bounding box, so a body the ray truly entered always beats one only the aim-assist pad caught. Aim assist can no longer steal the target from what the player is looking straight at.
2. **Depth for real hits, crosshair angle for grazes.** Pierced candidates rank by `hitDistance` along the ray. Assist-only grazes rank by `angularOffset` — the perpendicular offset divided by the distance to the candidate, so a far target is not punished for being far.
3. **Entity id, always last.** This closes a second hole: a perfectly tied pair used to be decided by entity-section iteration order, so the chosen target could flip between ticks as entities moved.

Detection is untouched — still ray–AABB, not centre-near-ray. The extra record component defaults to a real hit in both existing `EntityCandidate` constructors, so no call site changed. `TargetResolverTest` now covers real-hit-beats-graze, angle between grazes, distance between equally aimed grazes, and id-decided ties independent of list order.

Still open, and the reason this entry survives the fix: the resolver is shared by four callers — `TodoBoogieWoogieRuntime`, `NobaraHammerCombatRuntime`, `ProjectJjkNobaraRuntime`, and `ProjectJjkRitualRuntime` — so this was a roster-wide gameplay change, not a per-ability tweak, and it has only pure-comparator coverage. **Needs a Nobara targeting regression smoke: hammer targeting, nail launch, and directed Hairpin.** Until that runs, "assist no longer steals the target" is verified as comparator logic and UNVERIFIED as feel. Any future comparator edit needs the same smoke.

### E2 — Curse-link technique ids lack canonical semantic validation

Option A was accepted in [issue #20](https://github.com/grebeshok105/jujutsu-minecraft/issues/20#issuecomment-5143288156) and implemented on `fix/curselink-payload-bounds`.

Implemented at the codec boundary:

- `MAX_ENTRIES = 64` rejects negative and over-cap counts before list allocation.
- `MAX_TECHNIQUE_ID_LENGTH = 256` bounds decoded and encoded technique-id strings. A length failure rejects the whole payload because the stream cannot be assumed aligned.
- Syntactically malformed `ResourceLocation` strings are dropped after being fully read; later entries remain aligned, and the decoder logs once per payload.
- The writer refuses over-cap lists and over-length technique ids before writing the payload; it never truncates.
- Valid payloads retain the existing UUID/UUID/string wire layout and round-trip byte-identically.

The current curse-link registry still has no natural maximum. `SelfResonanceRuntime` is a real producer of this S2C payload, and the client passes the already bounded list to `CurseLinkSelectionScreen`. There is still no canonical catalog of supported technique ids, so a well-formed unknown id cannot be rejected honestly. That semantic acceptance criterion remains BLOCKED; E2 stays open for this gap only. No supported-id registry or UI pagination was added.

### E3 — Some server runtime state is still static and unevenly cleaned

CombatStagger, preparation state, anchor-removal tracking, and related maps use different cleanup rules. Most are server-thread safe, but long-running worlds need explicit ownership and pruning.

Action: centralize per-server state or add lifecycle/TTL cleanup with tests.

## Limits of the build-time gate

An adversarial review of the architecture rules ran 16 attacks against a green build; 15 are now caught, and the boundary below is where a structural rule stops being able to help. **This list is the honest edge of what a green `qualityGate` proves.** It is not a to-do list — each entry needs a different kind of test, not a better rule.

- **A vessel registering a callback into shared mutable state.** Every reference points vessel → shared, the permitted direction, yet shared dispatch becomes vessel-specific after init. Confirmed green against every rule. Catching it needs a unit test over the dispatcher, not a dependency rule.
- **A class name assembled from fragments at runtime.** `Class.forName` on a literal is caught by `SourceBoundaryTripwireTest`; a name built by concatenation at runtime is not, and never will be.
- **A constant copied by hand.** Reading `TodoProfile.BOOGIE_WOOGIE_RANGE` is caught. Typing `20.0` with a comment saying where it came from is indistinguishable from any other number.
- **A shared extension point with exactly one implementer.** A method on `CharacterDefinition` that only one vessel overrides is structurally identical to a genuine shared hook. Catching it needs a test that counts implementers per method.
- **In-world behaviour beyond the two neutral canaries.** The suite now boots one headless `ServerLevel` for the GameTest canaries, and the optional manual client lane (`runClientGameTest`) renders frames and takes screenshots — but no automated check casts an ability or moves a vessel's body, and the client lane is not part of the gate. See the Verification Policy in AGENTS.md.

One more limit was found on 2026-07-28 and is tracked separately as E15: a rule can only help if the gate actually runs it, and a check can be green while proving nothing. Neither failure is visible from inside the rule set.

### E14 — four vessel-named classes live in shared packages

`NobaraVfxIds` and `TodoVfxIds` sit in `jujutsu.mod.vfx`, which is the `<Character>VfxIds` shape AGENTS.md prescribes, so those two are deliberate. `NobaraHudState` in `jujutsu.mod.client.fx` and `ProjectJjkNailRenderer` in `jujutsu.mod.client.render` are not: both are vessel code in a shared package while `client.render.nobara` already exists. `VesselBoundaryTest#vesselNamedClassesStayInTheirVesselPackage` pins the set of four, so moving either one fails the test and forces this entry to shrink with it.

Widened 2026-08-05 by the target ESP: `ProjectJjkNailRenderer` now also references `NobaraEspState` and `NobaraEspRanks` (both correctly under `client.character.nobara`), so the `SourceBoundaryTripwireTest#TRACKED_DEBT` entry for that file grew from three named references to five. The class count above is unchanged — the ESP classes live in the right package; the renderer they feed is still the one mis-homed file. Moving the renderer into `client.render.nobara` shrinks both this entry and the tripwire map together.

Narrowed 2026-08-21 by the target-HUD rework: the world-space billboard and leader nail were deleted from `ProjectJjkNailRenderer` (the ESP overlay moved to the screen-space HUD contribution `NobaraTargetHud`, projected with `ui.WorldToScreen`), so the `TRACKED_DEBT` entry shrank from five named references to three — `NobaraEspRanks` and `NobaraEspState` no longer appear in the file. The class remains mis-homed until it moves into `client.render.nobara`.

### E15 — Test-suite defects and cleanup are tracked in PR #17

Opened 2026-07-28. Documentation only so far; nothing has been fixed yet.

A full read of `src/test/java/jujutsu/mod/**` and the verification half of `build.gradle` produced an ordered remediation plan, which lives in [TEST_ARCHITECTURE_PLAN.md](TEST_ARCHITECTURE_PLAN.md) and is proposed in **[PR #17](https://github.com/grebeshok105/jujutsu-minecraft/pull/17)**. That file is the detail; this entry exists so the register does not have to be read alongside a pull request to know the suite has open defects.

Two of the findings are recorded here in full, because they are not "work to schedule" — they are checks that are green today while proving nothing, and a reader of this register should not have to open a pull request to learn that:

1. **`BlackFlashWindowTest` does not check the Black Flash chance.** The assertion is `hammer.contains("BLACK_FLASH_CHANCE") || profile.contains("BLACK_FLASH_CHANCE = 0.10f")`. The left operand is true whenever the runtime names the constant, which it always does, so the operand that pins the value never runs. Setting the chance to `0.99f` leaves the suite green. The fix is two independent assertions — read the constant directly for the number, keep the grep for "the runtime uses the shared constant rather than an inline literal" — because neither implies the other.
2. **The call-order checks in the same file pass when their subject is deleted.** They compare `runtime.indexOf(A) < runtime.indexOf(B)` without checking that either fragment was found. A missing `A` gives `-1 < n`, which is true. The failure mode is asymmetric: losing `B` fails correctly, losing `A` is a false green.

The rest of the plan, in the order it should be done: wire `check` to the `verification` task group instead of re-listing the tasks by hand; fix the fail-open `isInsideVesselPackage` path classifier; then remeasure and raise only the three real scan floors. The vessel-map `>= 2` check is not part of that work: its apparent exact replacement is tautological, while missing vessel trees already fail independently. Tier 3 keeps the no-`default` switch assertion because it protects the compiler guarantee's precondition; only genuinely redundant greps are deferred for later review. The long-running items remain splitting `ProjectSanityTest`, migrating the `main()`+`assert` programs to JUnit one class at a time, covering `CharacterAbilityExecutor`, and evaluating mutation testing on pure policies.

Two of those connect directly to entries already in this register. The `CharacterAbilityExecutor` gap is the same one "Limits of the build-time gate" describes as needing "a unit test over the dispatcher, not a dependency rule". The hand-maintained `check` list is the reason a new verification program can exist, carry `-ea`, satisfy `verifyAssertionsEnabled`, and still never run — that task audits assertion flags, not invocation.

One ordering constraint is load-bearing and is stated in the plan rather than implied: nothing may be deleted from `ProjectSanityTest` on duplication grounds until that file has been inventoried. Which of its checks ArchUnit already proves is not known, and cutting on a guess is how coverage disappears quietly.

The plan marks as UNVERIFIED anything that needs a local build or a checkout-wide count, including the new floor values and whether `CharacterAbilityExecutor` already has indirect coverage. Its documentation inventory identifies five stale verification-program claims, including a `VERIFIED` entry in the claim-source index; all must be sourced from `verifyAssertionsEnabled` when the implementation branch updates prose. Dynamic Gradle wiring does not update prose by itself. Do not lift numbers out of the plan into this register without counting them first.

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

Recounted 2026-08-05 with the stone rework, and the shape of the count changed: eight files carry a direct `JujutsuCharacter.NOBARA`/`.TODO` reference across `src/main` and `src/client`, all deliberate, in three categories (recounted 2026-09-09: `NobaraEspState` moved out with the archived combat HUD):

- Four are the `id()` declarations in the vessel definitions themselves (`NobaraDefinition`, `TodoDefinition`, `NobaraClientDefinition`, `TodoClientDefinition`) — a definition naming the constant it speaks for is the seam working, not a leak.
- `JujutsuCommands` refuses the `hairpin` debug commands unless Nobara is selected, because a slot is an input position and `PRIMARY` cast as Todo would fire his swap while reporting a hairpin.
- Three are a vessel's own hook or presentation state filtering for itself, which is the seam's accepted self-check form, not shared code branching: `TodoBlackFlashRuntime` and `TodoSwapMomentumRuntime` on the server, `TodoStatusHud` on the client. (`NobaraEspState` was here too until 2026-09-09, when it moved to `archive/combat-hud-v1` with the combat HUD; `TodoSwapMarkerItem`, formerly in this list, is deleted with the marker system.)

One vessel-specific line survives in shared code without naming an enum constant: `JujutsuKeybinds.isTechniqueWeapon` still spells out Nobara's two hammers to decide whether left click counts as `ATTACK_CONTEXT`. It leaves when the client definition can answer "is this stack my technique weapon".

### E8 — Standard test reporting is weak

Verified 2026-07-28: `./gradlew verifyAssertionsEnabled` is the live inventory of custom JavaExec verification programs.

They use main methods and Java assertions. They are useful and green, but do not provide normal per-test JUnit reports or GameTest world integration — see E1 for the coverage gap that follows from having no world-level tests.

`check` dynamically depends on every verification `JavaExec`; add a program to that group once. Run `./gradlew verifyAssertionsEnabled` whenever the current inventory matters instead of maintaining a count in prose.

Do not recount it from `./gradlew tasks --group verification`: that group also contains `verifyAssertionsEnabled`, `auditDocumentation` and `qualityGate`, so its listing is not a count of verification programs. `verifyAssertionsEnabled` filters with the same expression the wiring change will use, so its number and the gate's set cannot drift apart.

The rest of this entry still holds: the migration path off `main()`+`assert` is E15's Tier 4.

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

### E12 — Closed, then superseded: the marker item is deleted

Closed 2026-07-26 by giving `TodoSwapMarkerItem.use` a two-sided vessel gate through
`CharacterSelectionView`; superseded 2026-08-05 when the stone rework deleted the marker system
entirely — item, projectile, marks and their runtimes. The durable lesson stands: anything a
vessel can leave in the world must be gated on **both** sides, because vanilla calls an item's
`use` on the client too, and Todo's stone inherits that rule by never being an item at all.

Still open, related: `CharacterPlayerState.hasClaimedStarter` has no production callers at all. The starter claim is recorded and persisted for every vessel, but nothing reads it, because the loadout is deliberately re-applied on every selection so a lost kit can be restored. Either give the claim a job or delete it; leaving persisted state that nothing consumes invites someone to trust it later.

### E13 — Closed: the network layer no longer names a vessel

Closed 2026-07-27 on `fix/e13-network-vessel-seam`. `JujutsuNetworking.registerServerReceivers` used to register the `SelectCurseLinkPayload` receiver by calling `jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime.select` through an inline fully qualified name — a vessel runtime reached directly from shared code, which the vessel seam forbids.

The receiver now hands a neutral intent to the seam that already existed: `JujutsuCharacters.of(player).selectCurseLink(player, linkId)`. A curse link is a shared concept — `jujutsu.mod.curse` is a shared package that `JujutsuCommands` reads too — so what travels is *the player picked link X*, and only the player's own vessel decides what that means. `CharacterDefinition.selectCurseLink` defaults to refusing, `NobaraDefinition` overrides it, and the Self Resonance logic never left her package.

**It fixed a second defect that was not the one being tracked.** The old receiver honoured the packet from any sender, because it named one vessel's runtime instead of asking who the sender is. Routing it through the definition means a player who is not the vessel that opened the picker is refused by the same seam that already refuses a stale-vessel ability cast.

Why it survived every source-text check until ArchUnit found it: an inline fully qualified name leaves no `import` line to grep, and `NobaraAbilitySlotsTest` asserted the call was present — protecting the packet from deletion, but pinning its registration site as a side effect. That half of the assertion is gone; the packet itself is still required to exist.

Both allowlist entries went with it. `VesselBoundaryTest#theOneKnownNetworkLeakDoesNotGrow` is now `#theNetworkLayerTouchesNoVesselCode` and asserts the empty set rather than one permitted class; the `JujutsuNetworking` entry in `SourceBoundaryTripwireTest#TRACKED_DEBT` is deleted. The rule was tightened rather than deleted, against the instruction this entry used to carry: a receiver wired straight to a vessel runtime is the easiest seam breach in the codebase to write, and this is the only check that can see it. Proven by mutation rather than by a green run — the mutation and its failure message are in the commit body.

**One residue, recorded rather than hidden.** `selectCurseLink` is a shared extension point with exactly one implementer, which "Limits of the build-time gate" above lists as a thing no structural rule can tell from a genuine shared hook. `canonicalSlot` sat in the same position until the stone rework deleted it together with its only implementer — the fold that used to collapse Todo's `Shift+B` into `B`.

## Low-priority product debt

- Nobara's nail-cast sound is noticeably too loud during manual smoke. Expected behavior is a comfortable volume consistent with the rest of Nobara's kit. Reported 2026-07-31; tracked in [GitHub issue #48](https://github.com/grebeshok105/jujutsu-minecraft/issues/48). No audio change is included in the current pass.
- Crafting recipes and broader datapack content are intentionally absent.
- Publication automation for Modrinth/CurseForge should wait until release provenance is clean.
- Some generic Rich ClickGui modules/components are unused and can be removed after confirming the final UI scope.

## Archived and recoverable

### E16 — Combat HUD (ability strip + Nobara target panel) archived 2026-09-09

The whole in-world combat HUD the player saw in the 2026-08-21 build was put in a box on request: the bottom-center ability strip (`AbilityHud`, five per-vessel cells, drag) and the Nobara target overlay (`NobaraTargetHud` + `NobaraEspState` 2-tick scan + `NobaraTargetLayout`/`NobaraTargetAnim` geometry/animation + `NobaraEspRanks` classification) no longer exist in `src/` and are not registered — `archive/combat-hud-v1/README.md` is the single source for what moved, which two registration lines were cut (JujutsuModClient `ability_hud`, NobaraClientDefinition `nobara_target_hud`), and how to restore (git mv back, re-add registrations, bump MOC metrics). A verbatim snapshot of the older glass-card look (the jar from 2026-08-21, commit 5d95a0b) sits in `archive/combat-hud-v1/snapshot-glass-5d95a0b-2026-08-21/`.

Untouched: ability input (R / S+R / B / S+B / LMB …), cooldown suppression, VfxDirector + the four remaining contributions (Megumi ×2, Todo ×2), the `hudSlots()`/`maxCooldownTicks()` seam (kept for restore), shared render helpers, assets and the `esp.jujutsumod.rank.*` lang keys. The game-instance jar was rebuilt from `feat/archive-combat-hud` and redeployed on 2026-09-09 17:10.

## Resolved and now in main

- These are closed. They are kept as a short list only so a reader does not reopen them; the live behavior is described in the Codex MOC product snapshot and the source it points to.

- Character selection persists through Fabric Data Attachment API and is copied on death.
- Nobara's starter kit is restored idempotently on every selection — it fills only a missing hammer, doll or nails, so re-selection cannot duplicate held tools. (This deliberately reversed the earlier one-time-claim rule; the persisted claim is now recorded for every vessel and read by nothing — see E12.)
- Loaded ordinary embedded nails have a TTL and a per-owner cap.
- Hairpin R/B resolve nails through EmbeddedNailRegistry instead of level.getAllEntities().
- VFX recipe registration goes through each vessel's `CharacterClientDefinition.registerClientHooks()`; the `JujutsuVfxRecipes` aggregator is deleted so the list of who has recipes cannot drift from the list of who exists.
- `TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS` and `WORLD_BORDER_MARGIN` are wired into `TodoBoogieWoogieRuntime` instead of being dead constants.
- Todo has a GeckoLib model, animations, and a player renderer; the `ability.boogie_woogie` hook is live, not a no-op.
- Todo roster labels are localized.
- Documentation audit tooling rejects stale references, broken local links, and stale code-derived metrics, and scopes itself to git-tracked Markdown.
