# Todo (Aoi Todo) — completion checklist

Status: CURRENT

Audited: 2026-07-27 against `main` at `97dd526`, which equalled `origin/main` with a clean tree.

This is the single finite plan for taking Todo from where he is to "done". It supersedes scattered Todo to-dos in [../SESSION.md](../SESSION.md) as the *completion plan*; it does not replace any owner document. Durable product facts stay in [../AGENTS.md](../AGENTS.md), accepted tradeoffs in [KNOWN_ISSUES.md](KNOWN_ISSUES.md), the manual smoke procedure in [BUILDING_IN_SANDBOX.md](BUILDING_IN_SANDBOX.md), and the behaviour contract in the Codex note [Todo Boogie Woogie](../Jujutsu%20Kaizen/jujutsumod-codebase-codex/03-systems/Todo-Boogie-Woogie.md). Where this file and the code disagree, the code wins.

## What the audit actually found

The working estimate going in was 60–70% complete. That number is wrong in both directions, and the split matters more than the figure.

**Implementation of the approved scope is substantially complete.** Every mechanic in the approved design exists, is wired, and is reachable: four cast paths, seven VFX cue ids all emitted and all consumed, four animations all defined and all triggered, five server runtimes all registered, three custom sounds all present on disk and declared, 15 localization keys at full parity in both languages. No mechanic from the approved scope is missing. There is no `TODO`/`FIXME` marker anywhere in Todo's packages.

**In-world verification is partial, and the split is the marks.** Automated coverage of in-world behaviour is nil — nothing in the suite constructs a `ServerLevel`, so no test has ever moved a body. Manual coverage is much better than the repository claims: the author reports having played the in-game smoke for everything except the mark mechanics. The aimed swap, the feint clap, the pair swap, melee and the Black Flash bridge were all exercised. The mark work — the thrown position marker, the entity mark on `USE_CONTEXT`, and the swap onto either form — was built afterwards and has never been played.

The repository records none of that. `SESSION.md` still names commit `d9df2b5` as the last pass and lists two whole sections as unrun. So the *coverage* is better than the documents say and the *record* is worse; the passes exist only as author testimony, with no commit attached. Item 1.7 exists to close that gap.

**Eight defects were confirmed by reading code, not by running it.** Four are behavioural (D1–D4 below), four are documentation claims the code contradicts (D5–D8). None were previously recorded in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

**A passed smoke does not retire any of them, and that is not a hedge.** Each of the four behavioural defects has a shape a normal play session does not surface. D1 only shows in the window between a killing blow and clicking Respawn, while a mark is live — a state a player has little reason to sit in. D2 needs a bow kill inside a 24-tick window opened by a swap. D3 needs a vessel switch inside a 60-tick projectile flight. D4 is invisible by construction: it is a missing log line on a failure path. Three of the four also touch the mark system, which is the untested part.

So: closer to "built, largely played, and carrying four defects that play would not have found" than to "60–70% built". The remaining work is defect closure plus one narrow smoke pass, not construction.

### Confirmed defects, referenced by id throughout this document

| Id | Defect | Confirmed at |
|---|---|---|
| D1 | Player death runs no Todo cleanup at all — no `AFTER_DEATH` listener exists in the package | `character/todo/` has none; `nobara/projectjjk/NailAnchorLifecycle.java:17` and `ProjectJjkStrawDollRuntime.java:56` show the pattern exists |
| D2 | `afterKill` bypasses the momentum policy table entirely | `TodoSwapMomentumRuntime.java:80-88` vs `:67-74` |
| D3 | An in-flight marker outlives its vessel gate | `TodoSwapMarkerEntity.java:77-101`, `TodoDefinition.java:80-87` |
| D4 | The marker swap's single-body rollback discards its failure result | `TodoMarkerSwapRuntime.java:63-66` vs `TodoBoogieWoogieRuntime.java:96-103` |
| D5 | The aimed swap plans the *target's* destination under `SOFT` | `TodoBoogieWoogieRuntime.java:81` → `:142-144` → `:162-164` |
| D6 | `PAIR_MARK`'s javadoc asserts caster-only secrecy that one of its two emitters breaks | `TodoVfxIds.java:17-19` vs `TodoEntityMarkRuntime.java:61-63` |
| D7 | `TodoSwapMark`'s javadoc says a mark "ends on death"; no death listener backs that clause | `TodoSwapMark.java:24-25` |
| D8 | "About a third of a heart" overstates the fist-damage gain by roughly 2× | `TodoProfile.java:88-90`, `KNOWN_ISSUES.md:51` |

### Effort scale used below

Relative implementation effort only. **No calendar estimates** — the repository holds no velocity data, so any duration would be invented.

- **XS** — one constant, one line, or one sentence of prose.
- **S** — one file, contained, introduces no new contract.
- **M** — several files, or one new test that needs real setup.
- **L** — new infrastructure, or a change crossing the vessel seam or a shared file.
- **1 session** — one seated play session at a real client. Used only for smoke items, because their cost is playtime rather than code.

### External limits on this audit

- **No client was launched during this audit.** Nothing below marked UNRUN is inferred from a green build, and nothing marked REPORTED PASSED was observed by the auditor — that status rests entirely on the author's account, given 2026-07-27.
- **PR #12 is closed, not merged.** All six of its commits fail `git merge-base --is-ancestor … main`. Its *content* is nonetheless on `main` — `emitSwapImpact`, `SWAP_AFTERIMAGE`, `VfxSoundDuck` are all present — so the impact pass landed by another route under different hashes. Treat PR #12 as an unreliable history source for Todo; use the source tree.
- **Minecraft and GeckoLib sources are not vendored**, so claims about vanilla internals (`Player.attack` sweep ordering, `AttributeInstance.calculateValue`, `SoundManager.pauseAllExcept`, GeckoLib's `triggerAnim` threading) are reasoned from this repo's own comments and cannot be confirmed here.

---

## 1. Mandatory in-game smoke

**[BUILDING_IN_SANDBOX.md](BUILDING_IN_SANDBOX.md) owns the full step-by-step procedure and stays the owner** — this section lists only the Todo subset that gates completion, so the two do not become rival sources. Run `./gradlew runClient` and work the referenced sections there.

Three overlapping in-game checklists already exist (one in `BUILDING_IN_SANDBOX.md`, two in `SESSION.md`). This is not a fourth: it is the gate list, pointing at them.

Two statuses are used below and they are not the same thing. **REPORTED PASSED** means the author played it and it worked, with no commit recorded — good evidence, weak provenance. **UNRUN** means nobody has played it. Every item must be re-run once D1–D4 land, because all four change behaviour these scenarios exercise; a prior pass is evidence about the old code.

### 1.1 — Core loop: aimed swap against a mob and against a player

- **Current state:** REPORTED PASSED. Implementation confirmed end to end: packet at `JujutsuNetworking.java:33-34` → `CharacterAbilityExecutor.java:26-33` → `TodoAbilityRouter.java:19` → `TodoBoogieWoogieRuntime.tryCast`. The author reports the swap plays correctly; no commit is recorded against that.
- **What remains:** one *recorded* pass covering standing, running and jumping casts, mob swap, player↔player swap, and confirmation that yaw, pitch, head yaw and velocity survive on both bodies. Running and jumping matter specifically because `hurtMarked` is what tells a moved player its own velocity, and a standing swap would not show its absence.
- **How to verify:** in game, `BUILDING_IN_SANDBOX.md` → "Todo — Boogie Woogie (R)". Momentum preservation is the specific thing to watch: `restoreMotionAndRotation` sets `hurtMarked` so the client is told its own velocity, and whether that reads correctly for a moved *player* is exactly what no test can show.
- **Blocks release:** yes. This is the character's primary technique.
- **Size:** 1 session.
- **Risks:** `hurtMarked` is what makes a moved player keep its momentum, and a standing swap cannot show whether it works. If the running and jumping cases were not part of the reported pass, the defect the impact pass existed to fix may still be present.
- **Basis:** `TodoBoogieWoogieRuntime.java:91-107`, `SESSION.md` "In-game smoke — partial".

### 1.2 — Every rejection is silent or correctly worded, and none charges a cooldown

- **Current state:** CONFIRMED by code; REPORTED PASSED for the non-mark paths. `CharacterAbilityCooldowns.start` appears once per path and always after the last `return false` (`TodoBoogieWoogieRuntime.java:109`, `TodoMarkerSwapRuntime.java:110`, `TodoPairSwapRuntime.java:170`). `ClapGate.UNAVAILABLE` returns silently; everything else routes through `reject`.
- **What remains:** confirm in play that hands-full, no-target, out-of-range and no-safe-destination each produce the intended message or intended silence, and that a refusal leaves the key immediately usable.
- **How to verify:** in game. One exception has no message at all and is worth watching for: `TodoMarkerSwapRuntime.java:38-40` returns false with neither log nor actionbar.
- **Blocks release:** yes. A cooldown charged on failure would be felt immediately as an unresponsive key.
- **Size:** 1 session, shares a sitting with 1.1.
- **Risks:** none identified beyond the silent path above.
- **Basis:** `TodoBoogieWoogieRuntime.java:338-344`, `TodoMarkerSwapRuntime.java:140-146`.

### 1.3 — `USE_CONTEXT`: two right clicks mark, one right click stays vanilla

- **Current state:** UNRUN. This is the entity mark, so it falls inside the untested mark work, and `SESSION.md` independently records that the seventh commit shipped it without an in-game pass with the risk accepted. Two sources agreeing makes this the most certainly-unplayed item in the kit.
- **What remains:** confirm a single right click still opens containers, mounts, trades and places blocks normally; that a fast pair marks the body under the crosshair; and that ordinary interaction never trips the pair.
- **How to verify:** in game. The six-tick pair window lives in `JujutsuKeybinds.java:109-121`.
- **Blocks release:** yes. This is the only slot whose key vanilla already owns, so a defect here does not look like a broken ability — it looks like right-click misbehaving generally.
- **Size:** 1 session.
- **Risks:** the first click of the pair is vanilla's by accepted design, so up close the first press will mount or trade before the second marks. Confirm how bad that feels rather than treating it as a bug.
- **Basis:** `SESSION.md` "Must be checked in game before the impact pass is trusted", item on `USE_CONTEXT`.

### 1.4 — State transitions: vessel change, death, respawn, dimension change, relog

- **Current state:** UNRUN in the only configuration that matters. Transitions without a live mark are covered by the reported passes; **every case this item exists for involves a mark, which is the untested half**. D1 predicts a visible failure: no death listener exists, so a mark, its glow and a resting projectile survive from the killing blow until the player clicks Respawn — a window the player controls.
- **What remains:** run each transition with a live mark, a live pair selection and a live momentum window, and record what survives. Specifically: death before respawn, vessel change while a marker is in flight, and disconnect with an active mark.
- **How to verify:** in game. Expected-correct: vessel change, dimension change, disconnect, respawn all clear. Expected-broken pending D1: death-before-respawn.
- **Blocks release:** yes — but as the *verification* of D1's fix, so schedule it after 6.1.
- **Size:** 1 session.
- **Risks:** D3 makes a second case reachable: throw a marker, switch vessel inside the 60-tick flight, and the mark is created after cleanup already ran.
- **Basis:** `TodoSwapMarks.java:31-43`, `TodoDefinition.java:80-87`, D1, D3.

### 1.5 — Repeated use: five swaps back to back, and the feint alternated with the real cast

- **Current state:** REPORTED PASSED for swaps and feints; the menu-during-duck case is UNRUN. No stuck-state defect is predicted by the code — the duck extends rather than restarts (`VfxSoundDuck.java:36-38`) and afterimages are 4-tick one-shots.
- **What remains:** confirm no stuck silhouette, stuck quiet, or stuck camera offset after rapid repeats, and confirm the audio duck lifts when a menu opens mid-duck.
- **How to verify:** in game. Also alternate real and feint casts with a second player watching — this is the only way to test the indistinguishability claim at all.
- **Blocks release:** yes for the stuck-state half; the indistinguishability half is `depends` — the pose tell (accepted in `KNOWN_ISSUES.md`) already makes the two distinguishable, so a presentation difference changes nothing until the input scheme is revisited.
- **Size:** 1 session.
- **Risks:** D6's broadcast `PAIR_MARK` and 4b's ungated duck both make a swap more visible to bystanders than the docs imply.
- **Basis:** `VfxSoundDuck.java:44-46`, `TodoVfxRecipes.java:105`.

### 1.6 — Multiplayer: two clients, one swap

- **Current state:** UNVERIFIED. The architecture is server-authoritative throughout, which is the precondition, not the proof.
- **What remains:** one two-client session confirming both observers see the same swap, the arriving body is where the server put it, and the caster-only cues (`FEINT_TELL`, pair `PAIR_MARK`) really do not reach the second client.
- **How to verify:** in game, two clients. The caster-only claim is checkable directly: `TodoFakeClapRuntime.java:53-54` uses `sendVfxCue`, `TodoPairSwapRuntime.java:92-93` likewise.
- **Blocks release:** depends. The stated product target is private play for one or two people, so this is required for the two-player case and moot for solo.
- **Size:** 1 session.
- **Risks:** none identified in code; this is the scenario with the least static coverage.
- **Basis:** `AGENTS.md` product target, `TodoFakeClapRuntime.java:53-54`.

### 1.7 — Momentum behaviour under each spend shape

- **Current state:** REPORTED PASSED for an ordinary boosted hit. The two shapes that distinguish a correct policy from D2's bypass are UNRUN: a *killing* blow by hand, which should spend, and a bow kill inside the window, which should not.
- **What remains:** three scenarios after 5.1 lands — ordinary hit spends and staggers; killing blow by hand spends without stagger; bow kill inside the window leaves the effect intact. Plus one interaction case: a Black Flash proc inside the window must spend it exactly once, not twice.
- **How to verify:** in game, watching the `Swap Momentum` effect icon disappear or persist. The Black Flash case is what the re-entrancy guard exists for.
- **Blocks release:** yes. This is the verification half of 5.1 and cannot be replaced by the unit test, because `AFTER_KILLED_OTHER_ENTITY`'s firing for projectile kills is not confirmable without a world.
- **Size:** 1 session.
- **Risks:** the bow case requires drawing a bow, which needs a hand — and both hands must be empty to have clapped. The sequence is clap, swap, then draw, so the window is 24 ticks to arm.
- **Basis:** D2, `TodoSwapMomentumRuntime.java:62-88`.

### 1.8 — Record the passes that already happened

- **Current state:** MISSING RECORD, not missing work. The author reports the non-mark smoke was played; the repository disagrees, still naming `d9df2b5` and listing two sections as unrun.
- **What remains:** once the post-fix pass in this section is run, write into `SESSION.md` which items passed and at which commit, replacing the stale claim rather than adding a third one.
- **How to verify:** read `SESSION.md` afterwards; the two "Must be checked in game" sections should name a commit or say plainly that they were superseded.
- **Blocks release:** no on its own — but leaving it means the next audit reaches the same wrong conclusion this one did.
- **Size:** XS.
- **Risks:** none. This is the cheapest item here and it is the one that prevents repeating the mistake.
- **Basis:** `SESSION.md` "In-game smoke — partial", author report 2026-07-27.

---

## 2. Model and animations

### 2.1 — Animation wiring is complete; no work outstanding

- **Current state:** COMPLETE and CONFIRMED. Four animations defined in `todo_aoi.animation.json` — `animation.todo_aoi.idle` (:4), `.walk` (:122), `.attack` (:309), `ability.boogie_woogie` (:443) — and all four reach a play path from Java (`TodoPlayerGeoAnimatable.java:28-31, :81, :87, :89`; `TodoAnimationHooks.java:33`). No orphan clip, no phantom reference. Clap timing is numerically consistent: `animation_length 0.72` matches `VfxFirstPersonChannel.CLAP_DURATION_SECONDS`, and `CLAP_CONTACT_PROGRESS 0.39` resolves to 0.2808 s against arm keyframes at 0.28.
- **What remains:** nothing structural. Only in-game confirmation that the poses read (covered by 1.1).
- **How to verify:** already covered by the smoke pass; no separate check needed.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** as cited.

### 2.2 — Remove three pieces of dead animation code

- **Current state:** PARTIAL — the code works, but carries three unreachable fragments that will mislead the next reader. `TodoPlayerGeoAnimatable.java:50` registers `.triggerableAnim("attack", ATTACK)` on the base controller while `triggerAction()` (`:38-40`) hardcodes the action controller, so the key is unreachable. `TodoAnimationHooks.java:12` declares a `ResourceLocation BOOGIE_WOOGIE` that nothing reads — it exists only to satisfy `ProjectSanityTest.java:466`, which accepts either token. `Movement.running` is computed at `:97-99` and never consumed, taking `RUN_VELOCITY_THRESHOLD_SQR` with it.
- **What remains:** delete the three, or wire them. Note the second cannot simply be deleted without checking `ProjectSanityTest.java:466` still passes on the remaining token.
- **How to verify:** `./gradlew qualityGate`. A green run after deletion proves nothing depended on them.
- **Blocks release:** no. Dead code, not wrong behaviour.
- **Size:** S.
- **Risks:** the `ProjectSanityTest` coupling means the "obvious" deletion can redden the gate; check the assertion first.
- **Basis:** as cited.

### 2.3 — Fix a comment that contradicts its own asset

- **Current state:** DEFECT (documentation-in-code). `TodoPlayerGeoAnimatable.java:61` says "clap keys no longer drive head", but `ability.boogie_woogie` keys the `head` bone with five rotation keyframes.
- **What remains:** correct the comment, or correct the head-damping logic it justifies if the intent was that the clap should not drive the head.
- **How to verify:** read the JSON's `ability.boogie_woogie` bone list; in game, watch whether head look fights the clap.
- **Blocks release:** no.
- **Size:** XS if the comment is wrong; S if the logic is.
- **Risks:** deciding which of the two is wrong needs an in-game look, so pair it with 1.1.
- **Basis:** `TodoPlayerGeoAnimatable.java:61`, `todo_aoi.animation.json:443`.

### 2.4 — Sprint has no clip; decide whether that is final

- **Current state:** ABSENT and acknowledged in source: `TodoPlayerGeoAnimatable.java:88` — "Model pack has no dedicated run clip; heavy walk covers sprint." The detection code for sprint exists and is discarded (see 2.2).
- **What remains:** a product decision — accept walk-covers-sprint permanently and record it as accepted, or author a run clip.
- **How to verify:** in game, sprint as Todo and judge.
- **Blocks release:** no. It is a content gap, not a fault.
- **Size:** XS to record the decision; L to author a clip.
- **Risks:** none. Recording the decision is the cheap half and unblocks 2.2's deletion.
- **Basis:** as cited.

### 2.5 — Two textures are both live; no action

- **Current state:** COMPLETE and CONFIRMED, recorded here because it looks like duplication and is not. `todo.png` (64×64) drives first-person hands, vanilla skin paths and the roster portrait via `TodoClientDefinition.java:56-58` and `CharacterSkinMixin.java:26`. `todo_aoi.png` (128×64) is the geo atlas via `TodoPlayerGeoModel.java:12`, matching the geo's declared dimensions.
- **What remains:** nothing. Confirm visual consistency between the two during 1.1.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** as cited.

---

## 3. Boogie Woogie VFX and sound

### 3.1 — Cue graph is complete; no work outstanding

- **Current state:** COMPLETE and CONFIRMED. Seven ids in `TodoVfxIds`, all seven emitted, all seven consumed by a registered recipe. No orphan in either direction. Recipes are one-shot authoring through `VfxInstance.start`, not per-tick polling.
- **What remains:** nothing structural. Visual quality is a smoke question.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** `TodoVfxIds.java:8-42`, `TodoVfxRecipes.java:33-39`.

### 3.2 — Correct the `PAIR_MARK` contract (D6)

- **Current state:** DEFECT. `TodoVfxIds.java:17-19` documents `PAIR_MARK` as "Sent to one player: only the caster may know who is marked." `TodoPairSwapRuntime.java:92-93` honours that with `sendVfxCue`; `TodoEntityMarkRuntime.java:61-63` broadcasts the same id to 64 blocks. The broadcast is deliberate and reasoned in its own file (the glow it applies is public anyway) — the id's contract was simply never updated.
- **What remains:** rewrite the javadoc to state both emitters and why they differ. Do not change the emission — the reasoning at `TodoEntityMarkRuntime.java:28-30` is sound.
- **How to verify:** read both call sites; confirm the doc names both.
- **Blocks release:** no. Wrong prose, correct behaviour.
- **Size:** XS.
- **Risks:** a reader trusting the current contract could "fix" the broadcast into a send and silently remove intended feedback.
- **Basis:** as cited.

### 3.3 — Decide whether the audio duck should reach non-participants

- **Current state:** DEFECT-or-decision, CONFIRMED as behaviour. `TodoVfxRecipes.java:105` calls `duck()` ungated, while the camera kick beside it at `:106-109` is gated to a 1.5-block arrival radius. `SWAP_ARRIVAL` broadcasts to 64 blocks, so a bystander 60 blocks away loses all non-`PLAYERS`/`UI` audio for 300 ms from a swap they may not have seen.
- **What remains:** either gate `duck()` the way the camera kick is gated, or record the wide duck as intended.
- **How to verify:** in game with two clients, the second one standing far away. This is precisely a two-player observation.
- **Blocks release:** depends. Harmless solo; intrusive for the second player in the stated two-player target.
- **Size:** XS to gate, XS to record.
- **Risks:** gating changes the feel for the participant too if the radius is chosen carelessly — reuse `isLocalArrival` rather than inventing a second radius.
- **Basis:** `TodoVfxRecipes.java:105-109`, `:30`.

### 3.4 — Sound-duck ownership cannot represent a nested vanilla pause

- **Current state:** PARTIAL. `VfxSoundDuck`'s stated invariant is to lift only its own pause, and `restoreDuck` correctly no-ops from `IDLE`. But the state machine has two states and `VfxSoundChannel.java:62` calls the global `SoundManager.resume()`. If vanilla pauses while the duck is active, the next-tick restore lifts vanilla's silence too. The screen-open early restore at `VfxSoundDuck.java:44-46` is clearly written to avoid this; whether a window remains depends on tick ordering.
- **What remains:** confirm in game whether the window is reachable, and only then decide whether the state machine needs a third state.
- **How to verify:** in game — swap, then open the pause menu on the same tick and on the following tick; confirm audio returns and vanilla's own pause is undisturbed.
- **Blocks release:** depends. If reachable, the symptom is audio returning during a vanilla pause, which is user-visible.
- **Size:** XS to verify; M to add a third state if needed.
- **Risks:** this is shared VFX-core code, not Todo-owned — a change here affects any future ducking caller.
- **Basis:** `VfxSoundDuck.java:8-10, :44-46`, `VfxSoundChannel.java:52-62`.

### 3.5 — The pending-sound queue is a per-tick global hook in the audio path

- **Current state:** PARTIAL. `TodoBoogieWoogieRuntime.java:333` holds a static `CopyOnWriteArrayList` drained by an `END_WORLD_TICK` listener (`:314-331`) whose only job is to delay two sounds by 1 and 3 ticks. It runs every world tick with no empty early-out, unlike `TodoPairSwapRuntime.tickSelections` (`:226-228`). Entries are dimension-keyed, so a pending sound in a dimension that stops ticking is never drained until `SERVER_STOPPING` (`:315`).
- **What remains:** add the empty early-out, and decide whether the delay belongs on the client timeline mechanism that already expresses exactly this.
- **How to verify:** `./gradlew qualityGate` for the early-out; the architectural question needs a design call, not a test.
- **Blocks release:** no. The leak is bounded by a few records per swap, all due within 3 ticks.
- **Size:** XS for the early-out; M to move the delay client-side.
- **Risks:** moving it client-side would make the sound client-authored, which is the opposite of the current server-authoritative choice — see 3.6 before doing it.
- **Basis:** `TodoBoogieWoogieRuntime.java:313-331`.

### 3.6 — Record the audio-authoring asymmetry between vessels

- **Current state:** CONFIRMED architectural drift, not a defect. `TodoVfxRecipes` contains zero `playNoFalloff` calls; Todo's audio is entirely server-authored through `level.playSound` with normal distance falloff. `NobaraVfxRecipes` calls `playNoFalloff` at 30+ sites, client-authored with attenuation disabled.
- **What remains:** a one-paragraph decision on which is the pattern for a third vessel. Not a code change.
- **How to verify:** n/a — this is a recorded decision.
- **Blocks release:** no.
- **Size:** XS.
- **Risks:** leaving it unrecorded means the third vessel copies whichever file its author opened first.
- **Basis:** `TodoVfxRecipes.java`, `NobaraVfxRecipes.java`.

### 3.7 — The clap has no subtitle, and its variant samples are unused

- **Current state:** PARTIAL. `sounds.json:107-111` declares `projectjjk.clap` with no `subtitle`, while the two sounds it is paired with do have them. Separately, 24 OGG files ship unreferenced by any `sounds.json` entry, including `clap2.ogg` and five `aec_whoosh_air_cloth` variants — exactly the variant shape `projectjjk.aec_boom` already uses. Todo's signature clap is therefore single-sample and will read as a loop under repeated casts.
- **What remains:** add the subtitle; decide whether to wire the variant samples or drop the unreferenced files from the jar.
- **How to verify:** enable subtitles in game and cast; listen to five casts in a row for sample repetition.
- **Blocks release:** no for the subtitle; depends for repetition, which is a polish judgement only playtime can settle.
- **Size:** XS for the subtitle; S to wire variants.
- **Risks:** the unreferenced files are repo-wide, not Todo-only — dropping them is a separate decision with provenance implications under `PROVENANCE.md`.
- **Basis:** `sounds.json:107-128`, `en_us.json:66-75`.

---

## 4. Fake clap, pair swap and marker swap

### 4.1 — Resolve the `SOFT` placement contradiction (D5)

- **Current state:** DEFECT (documentation), CONFIRMED by reading. `AGENTS.md` states the principle "Every cast that moves a body which did not ask to be moved uses `STRICT` and cancels instead", then enumerates the pair swap and both mark forms. The enumeration matches the code. The principle does not: `TodoBoogieWoogieRuntime.java:81` plans the *aimed target's* destination through the three-argument overload at `:142-144`, which hardcodes `SOFT`, whose last resort at `:162-164` returns the exact requested point while skipping `noBlockCollision` at `:169-170`. The aimed target did not ask to be moved either.
- **What remains:** a product decision, then one edit. Either narrow the stated principle to match the code — "SOFT applies to both bodies of the aimed swap, because that fallback is what makes mid-air swaps feel good" — or pass `STRICT` for the target at `:81` and accept that some aimed swaps will start cancelling.
- **How to verify:** whichever way it is decided, `./gradlew qualityGate`, plus an in-game check that aiming at a target standing against a wall behaves as chosen.
- **Blocks release:** depends. If the principle is what is wanted, this is a live gameplay defect — a target can be forced into geometry. If the code is what is wanted, it is a prose fix.
- **Size:** XS either way; the decision is the work, not the edit.
- **Risks:** switching `:81` to `STRICT` changes shipped feel on the character's primary technique. Do not do it as a tidy-up; it needs the same deliberation as any balance change.
- **Basis:** `AGENTS.md` "Boogie Woogie destination policy", `TodoBoogieWoogieRuntime.java:81, :142-144, :162-170`.

### 4.2 — Log the marker swap's failed rollback (D4)

- **Current state:** DEFECT, CONFIRMED. The single-body marker swap at `TodoMarkerSwapRuntime.java:63-66` calls `TodoBoogieWoogieRuntime.restore(todo, snapshot)` and discards the boolean. Every other rollback in the kit logs at error level on failure — `TodoBoogieWoogieRuntime.java:96-103`, `TodoPairSwapRuntime.java:155-163`, and this same file's two-body form at `:89-93`. A failed restore on this one path is completely invisible.
- **What remains:** capture the result and log it in the same shape as the other three.
- **How to verify:** `./gradlew qualityGate`. Behavioural proof needs a forced teleport failure, which is not reachable without a world.
- **Blocks release:** yes. Silence here means a body stranded somewhere neither the plan nor the snapshot describes, with no evidence it happened — and the project treats that log line as a bug report rather than noise.
- **Size:** XS.
- **Risks:** none. This is strictly additive.
- **Basis:** as cited.

### 4.3 — Close the feint's test gap

- **Current state:** PARTIAL. The indistinguishability contract holds today: the feint emits only `BOOGIE_WOOGIE` (shared, via `emitClapPerformance`) plus the caster-only `FEINT_TELL`, and neither `scheduleDisplacementWhoosh` nor `scheduleLandingReport` is reachable from it. But the test guarding this is a string match with a hole. `TodoFakeClapTest.java:43-53` forbids `SWAP_ENDPOINT` in the feint's own file and omits `SWAP_ARRIVAL` and `SWAP_AFTERIMAGE`; nothing asserts that `emitClapPerformance` itself stays free of swap cues. Adding `SWAP_ARRIVAL` to the shared method would give the feint a camera kick and an audio duck — destroying the deception — with every test still green.
- **What remains:** extend the forbidden-token list, and add an assertion against `emitClapPerformance`'s own body rather than only the feint's.
- **How to verify:** `./gradlew qualityGate`, plus the mutation the project's own rule requires — add `SWAP_ARRIVAL` to `emitClapPerformance`, confirm the new assertion reddens, revert.
- **Blocks release:** yes. The feint's whole value is the deception, and the guard on it currently has a documented hole.
- **Size:** S.
- **Risks:** these are source-text greps by nature; the new assertion should say so in its own message, matching the convention in `SourceBoundaryTripwireTest`.
- **Basis:** `TodoFakeClapTest.java:43-70`, `TodoBoogieWoogieRuntime.java:274-287`.

### 4.4 — Decide whether throwing a marker should read the clap gate

- **Current state:** OPEN QUESTION, not a confirmed defect. `TodoSwapMarkerItem.use` (`:27-47`) checks only that the thrower is Todo, via `CharacterSelectionView`. It never calls `TodoSwapGates`, so a staggered or mounted Todo can throw and land a mark. This is arguably correct — the gate is a *clap* gate and a throw is not a clap, and the swap onto the mark does pass the gate by inheriting `PRIMARY`'s evaluation. What is undocumented is that mark *placement* skips the stagger and transport arms.
- **What remains:** record the decision either way. A second, smaller asymmetry belongs with it: `TodoSwapGates.java:36` passes `leashed = false` for Todo himself while targets are leash-checked at `TodoBoogieWoogieRuntime.java:125` — probably because players cannot be leashed, but it is unstated.
- **How to verify:** in game, get staggered and try to throw.
- **Blocks release:** no.
- **Size:** XS to record; S if placement should be gated.
- **Risks:** gating the throw would make the marker unusable while staggered, which may be the intent or may remove an escape tool. That is a balance call.
- **Basis:** `TodoSwapMarkerItem.java:27-47`, `TodoSwapGates.java:36`.

### 4.5 — Document `active()` as a mutating read

- **Current state:** CONFIRMED behaviour, misleading name. `TodoSwapMarks.active` (`:71-81`) calls `clear` — which discards the resting projectile via `release` (`:118`) — when the mark is in a different dimension or expired. Since `hasMark` (`TodoMarkerSwapRuntime.java:32-34`) is just `active(...) != null`, pressing `R` with no target while in the wrong dimension destroys the anchor. `AFTER_PLAYER_CHANGE_WORLD` already covers the normal case, so this is defensive rather than load-bearing — but the name hides a side effect.
- **What remains:** rename or document. No behaviour change needed.
- **How to verify:** read the method; confirm callers tolerate the side effect.
- **Blocks release:** no.
- **Size:** XS.
- **Risks:** renaming touches several call sites; documenting is cheaper and sufficient.
- **Basis:** `TodoSwapMarks.java:71-81, :118`.

### 4.6 — Pair-swap repeat presses are unrate-limited until one commits

- **Current state:** CONFIRMED by code, UNVERIFIED as feel. Marking takes no cooldown (`TodoPairSwapRuntime.java:99-100`), a deliberate cancel takes none (`:113-120`), and a failed commit takes none. The only bound is the 100-tick selection TTL. That is the documented intent — a missed click must not cost a two-cast setup — but it means `B` can be spammed freely while pending.
- **What remains:** confirm in play that this does not feel exploitable or noisy, then record it as accepted.
- **How to verify:** in game, hold `B` while a pair is pending.
- **Blocks release:** no.
- **Size:** XS to record.
- **Risks:** none identified; the cast moves nothing until it commits.
- **Basis:** `TodoPairSwapRuntime.java:99-120`.

---

## 5. Momentum attack

### 5.1 — Route `afterKill` through the policy table (D2)

- **Current state:** DEFECT, CONFIRMED. `TodoSwapMomentumRuntime.afterDamage` (`:62-78`) builds a seven-argument call to `TodoSwapMomentum.decide` — blocked, damage taken, re-entrancy, direct-entity, vessel, liveness, effect. `afterKill` (`:80-88`) checks three things — killer is a `ServerPlayer`, is Todo, has the effect — and calls `consume` directly, bypassing `decide` entirely. So a ranged or indirect kill inside the window (bow, snowball, trident, an attributed fire or potion tick) spends the melee window and fires the `MOMENTUM_STRIKE` cue. `TodoSwapMomentum` exists precisely so this truth table can be tested rather than argued about, and this path does not consult it.
- **What remains:** give `afterKill` the same direct-entity and re-entrancy checks, routed through `decide` with a flag for the kill case (which legitimately skips the stagger, since there is nobody left to interrupt).
- **How to verify:** extend `TodoSwapMomentumTest` to cover the kill path, then the project's mutation rule — make a ranged kill spend, confirm the new assertion reddens, revert. In game: open a window, kill a mob with a bow, confirm the effect survives.
- **Blocks release:** yes. The window is the payoff of the character's primary technique, and it can currently be consumed by an action the player did not intend to spend it on.
- **Size:** S.
- **Risks:** `AFTER_KILLED_OTHER_ENTITY`'s exact firing for projectile kills cannot be confirmed without a world, so the in-game half of the check is not optional.
- **Basis:** `TodoSwapMomentumRuntime.java:62-88`, `TodoSwapMomentum.java:20-44`.

### 5.2 — Momentum mechanics are otherwise correct; no work outstanding

- **Current state:** COMPLETE and CONFIRMED for everything except 5.1. The ×1.25 is an `ATTACK_DAMAGE` modifier on the effect (`JujutsuEffects.java:27-34`) with no second damage instance anywhere in the runtime. Grant sites are exactly two — `TodoBoogieWoogieRuntime.java:113` and `TodoMarkerSwapRuntime.java:115` — and neither the pair swap nor the feint nor the entity mark grants. The spend table refuses blocked and zero-damage hits (`TodoSwapMomentum.java:33-35`). The Black Flash re-entrancy guard is real and correctly ordered: `TodoDefinition.java:53-56` registers Black Flash before momentum for exactly that reason.
- **What remains:** nothing beyond 5.1 and the in-game confirmation in 1.1.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** as cited.

### 5.3 — Fix the "third of a heart" figure (D8)

- **Current state:** DEFECT (documentation). `TodoProfile.java:88-90` and `KNOWN_ISSUES.md:51` both say the fist gain is "about a third of a heart", giving the arithmetic as 1.0 → 1.5 → 1.875. The arithmetic is right; the gloss is not. The delta is 0.375 damage points and a heart is 2 points, so the gain is ≈0.19 hearts — about a third of a *half*-heart. The conclusion ("nearly worthless, the stagger is the payload") is unaffected and stands.
- **What remains:** correct the phrasing in both places.
- **How to verify:** arithmetic, then `./gradlew qualityGate` for the doc audit.
- **Blocks release:** no.
- **Size:** XS.
- **Risks:** none. The design conclusion does not change, so this is not a balance reopening.
- **Basis:** as cited.

### 5.4 — Accepted limits stay accepted; verify the loop is playable

- **Current state:** Both documented limits still hold in code. Nothing mitigates the sweep leak — no `Player.attack` mixin exists — and the stagger and cue correctly do not duplicate on later sweep victims, because the effect is already gone. The intended loop is displace → arm → hit inside 24 ticks.
- **What remains:** confirm in play that 24 ticks is actually long enough to draw a weapon. `KNOWN_ISSUES.md:51` already names this as the reopen condition.
- **How to verify:** in game, swap then hotbar-swap then hit, repeatedly.
- **Blocks release:** depends. If the window is unusable, the momentum feature delivers only its stagger and the damage half is dead weight.
- **Size:** 1 session, shares a sitting with 1.1.
- **Risks:** both hands must be empty to clap, so the hotbar swap is mandatory, not optional — this is the whole question.
- **Basis:** `TodoSwapMomentumRuntime.java:29-35`, `KNOWN_ISSUES.md:50-51`.

---

## 6. Cleanup and network lifecycle

### 6.1 — Add death cleanup (D1)

- **Current state:** ABSENT, CONFIRMED. There is no `ServerLivingEntityEvents.AFTER_DEATH` or `ALLOW_DEATH` listener anywhere in `character/todo/`. Nobara's package has two (`NailAnchorLifecycle.java:17`, `ProjectJjkStrawDollRuntime.java:56`), so the pattern exists and Todo simply does not use it. All of Todo's death-adjacent cleanup is keyed on *respawn*. Between the killing blow and the player clicking Respawn — a window the player holds open at will — the mark stays live, the resting projectile stays in the world, and a marked body keeps glowing for everyone. A landed mark never expires, and neither sweep predicate looks at the owner's state.
- **What remains:** register a death listener clearing the same state the respawn listener clears.
- **How to verify:** `./gradlew qualityGate` for wiring; the real proof is in game — die with a live mark and a marked body, do not respawn, and confirm the glow and projectile end at death rather than at respawn. That is smoke item 1.4.
- **Blocks release:** yes. It is a visible-world artefact with an unbounded window, and a documentation claim (D7) already asserts the behaviour exists.
- **Size:** S.
- **Risks:** moderate, not critical — a dead player cannot cast, so this is a visual leak rather than an exploit. Do not over-scope it into a general lifecycle refactor; E3 in `KNOWN_ISSUES.md` already owns that.
- **Basis:** as cited.

### 6.2 — Discard in-flight markers on vessel change (D3)

- **Current state:** DEFECT, CONFIRMED. `TodoSwapMarkerItem.use` gates the throw on being Todo (the E12 fix), but `TodoSwapMarkerEntity.onHitBlock` (`:77-84`) and `onHitEntity` (`:90-101`) re-check only that the owner is a live entity — never that the owner is *still* Todo — and `TodoDefinition.onDeselected` (`:80-87`) does not discard projectiles in flight. Throw a marker, switch vessel inside the 60-tick flight window, and the projectile lands and creates a mark after the deselect cleanup has already run. The same shape applies to a disconnect-and-reconnect inside the flight window.
- **What remains:** either re-check the vessel at landing, or discard owned projectiles in `onDeselected`. The first is narrower.
- **How to verify:** in game — throw, switch vessel mid-flight, confirm no mark is created. Part of 1.4.
- **Blocks release:** yes. It reopens exactly the class of hole E12 was closed to fix: a mark existing in the world for a player who is not Todo.
- **Size:** S.
- **Risks:** re-checking at landing needs care around `getOwner()` returning null for a disconnected owner, which already forces `discard()` at `:80-83`.
- **Basis:** as cited.

### 6.3 — Correct the "ends on death" claim (D7)

- **Current state:** DEFECT (documentation), and a direct consequence of 6.1. `TodoSwapMark.java:24-25` states a landed mark "ends on death, on changing vessel, on changing dimension, on disconnect, on server stop, and when the projectile goes missing from a loaded chunk." Five of those six clauses map to a real listener. The death clause maps to the *respawn* listener, which is a different moment.
- **What remains:** if 6.1 is done, this becomes true and needs no edit. If 6.1 is deferred, correct the sentence so it does not assert cleanup that does not happen.
- **How to verify:** re-read after 6.1 lands.
- **Blocks release:** no on its own; it is 6.1's paperwork.
- **Size:** XS.
- **Risks:** leaving it means a future reader trusts a cleanup guarantee the code does not provide.
- **Basis:** as cited.

### 6.4 — Decide whether the momentum effect needs a connection-lifecycle hook

- **Current state:** PARTIAL. `removeEffect(TODO_SWAP_MOMENTUM)` appears exactly twice in `src/main`: the spend at `TodoSwapMomentumRuntime.java:98` and the deselect at `TodoDefinition.java:86`. Nothing removes it on disconnect, dimension change or death. In practice the window is 24 ticks and vanilla expires effects on its own, so this is benign — but a ≤24-tick `+25%` `ATTACK_DAMAGE` modifier is written into playerdata on a mid-window disconnect and resumes on reconnect. The `onDeselected` javadoc at `:83-85` reasons explicitly about stranding this modifier; that reasoning was not extended to the connection lifecycle.
- **What remains:** decide — accept vanilla expiry and record it, or add the hook.
- **How to verify:** in game, disconnect inside the window and reconnect; observe whether the effect is still on.
- **Blocks release:** no. The exposure is at most 1.2 seconds of play time.
- **Size:** XS either way.
- **Risks:** none material.
- **Basis:** as cited.

### 6.5 — `PENDING_SOUNDS` survives disconnect and can strand entries

- **Current state:** PARTIAL, CONFIRMED. `TodoBoogieWoogieRuntime.java:333` is cleared only on `SERVER_STOPPING` (`:315`) and otherwise drained only when a level with the matching dimension key ticks past `dueAt` (`:318-331`). An entry queued for a dimension that stops ticking is never removed. Practically tiny — a handful of records per swap, all due within 3 ticks — but it is per-server-lifetime rather than per-session and unbounded in principle.
- **What remains:** clear the caster's entries on disconnect, or accept and record. Pairs naturally with 3.5's early-out.
- **How to verify:** `./gradlew qualityGate`; the leak itself is not reachable without an unloadable dimension.
- **Blocks release:** no.
- **Size:** XS.
- **Risks:** none.
- **Basis:** as cited.

### 6.6 — Two shared-code lifecycle findings Todo's hot path feeds

- **Current state:** CONFIRMED, and **outside Todo's package** — recorded here because Todo writes into both and would be blamed for the symptoms. First, `CombatStagger.GLOBAL.until` (`CombatStagger.java:10-11`) has no `register()` and no lifecycle hooks at all; entries are evicted only lazily when `isStaggered` is next called for the same UUID. It survives disconnect *and* server stop, so in a single-player client process it carries across world loads. Todo writes into it on every momentum spend and every Black Flash. Second, `BlackFlashStrike`'s global cleanup is owned entirely by `TodoBlackFlashRuntime.java:25-26` even though `BlackFlashStrike` is character-neutral by its own javadoc — if Todo's hooks were ever skipped, the shared set would lose its lifecycle.
- **What remains:** neither belongs to this checklist to fix. Both should be folded into E3 in `KNOWN_ISSUES.md`, which already owns uneven static-state cleanup.
- **How to verify:** n/a here.
- **Blocks release:** no. Neither is a live Todo defect.
- **Size:** XS to record in E3.
- **Risks:** the `BlackFlashStrike` ownership is not a live fault — the `try/finally` at `:80` is the real guarantee — but it is misplaced and a third vessel would inherit the confusion.
- **Basis:** as cited.

### 6.7 — Everything else in the lifecycle is correct

- **Current state:** COMPLETE and CONFIRMED. Vessel change, dimension change, disconnect, respawn and server stop each clear marks and pair selections through registered listeners. The marker entity is `noSave()` (`JujutsuEntities.java:32`), so a mark cannot outlive its session. The unloaded-chunk rule is deliberate and correctly ordered — `landedMarkerIsGone` checks the chunk before the entity, so absence in an unloaded chunk is not read as death. All five runtimes that declare `register()` are registered by `TodoDefinition.java:51-60`; the three cast-path-only runtimes correctly declare none.
- **What remains:** nothing.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** as cited.

---

## 7. Balance

No new values are proposed here. The project has no measurements and no stated balance requirements for Todo, so every number below is recorded as it stands.

### 7.1 — All tuning lives in `TodoProfile`; the constraint holds

- **Current state:** COMPLETE and CONFIRMED. Every Todo number is a constant in `TodoProfile.java`, including sound volumes, pitches and delays. No magic number was found at a Todo call site.
- **What remains:** nothing.
- **Blocks release:** no.
- **Size:** —
- **Risks:** none.
- **Basis:** `TodoProfile.java:6-100`.

### 7.2 — `AGENTS.md`'s constant table is incomplete

- **Current state:** PARTIAL. The "Todo baseline numbers" table in `AGENTS.md` omits `ENTITY_MARK_COOLDOWN_TICKS` (20), which is a gameplay cooldown, not a presentation value. It also omits the sound constants, which matters less. The same file states `TodoProfile` is the source of truth and must not be restated elsewhere, so the table is a partial restatement of a source it declares authoritative — an open decision already recorded at the end of `SESSION.md`.
- **What remains:** either add the missing cooldown or resolve the table's existence per the open decision. Do not do both halves independently.
- **How to verify:** compare the table against `TodoProfile.java` field by field.
- **Blocks release:** no.
- **Size:** XS.
- **Risks:** the table's fate is a decision already parked for the user; do not settle it inside a balance pass.
- **Basis:** `AGENTS.md` "Todo baseline numbers", `TodoProfile.java:70`.

### 7.3 — No Todo number has ever been validated in play

- **Current state:** UNVERIFIED. Every value is a design-time choice. The two with explicit reopen conditions already recorded are the momentum window (is 24 ticks long enough to arm) and the permanent mark (one thrown item buys an unlimited 32-block return on a 60-tick cooldown, with nothing limiting it today).
- **What remains:** play, then record observations against the existing reopen conditions. Do not change numbers before there are observations.
- **How to verify:** in game, across the sessions in section 1.
- **Blocks release:** depends. Unbalanced-but-playable is acceptable for the stated private target; unplayable is not.
- **Size:** covered by the section 1 sessions.
- **Risks:** the levers for pricing the mark are already separated — `MARKER_SWAP_COOLDOWN_TICKS`, `MARKER_SWAP_RANGE`, `TodoSwapMarks.onUsed` — so a later change is a number, not a rewrite. That is a reason to wait for data, not to pre-empt it.
- **Basis:** `KNOWN_ISSUES.md` "A permanent mark shadows the primary key", `TodoProfile.java:72-76`.

### 7.4 — Todo borrows Nobara's Black Flash cue id

- **Current state:** CONFIRMED cross-character coupling, already documented in the Codex note. `TodoBlackFlashRuntime` broadcasts `NobaraVfxIds.BLACK_FLASH` rather than a Todo-owned id, so retuning Nobara's Black Flash presentation silently retunes Todo's. It passes every architectural check because `NobaraVfxIds` lives in the shared `jujutsu.mod.vfx` package, which is outside the vessel packages the boundary rules examine.
- **What remains:** a decision — promote Black Flash to a shared id, or give Todo his own. Not required for Todo to be complete, but a third vessel must not copy it.
- **How to verify:** read `TodoBlackFlashRuntime`'s imports.
- **Blocks release:** no.
- **Size:** S.
- **Risks:** the coupling is invisible to the build-time gate, so it will not be caught if it spreads.
- **Basis:** Codex note "Seam: Todo does not own a Black Flash cue id".

---

## 8. Automated tests

The infrastructure exists and needs no invention: JUnit 5 via `fabric-loader-junit` for new tests, plus 34 legacy `JavaExec` assertion programs. Everything runs inside `./gradlew qualityGate`. Nine Todo test classes are wired into `check`.

Only tests that lock down existing behaviour or a defect found above are listed. No new framework is proposed.

### 8.1 — What Todo coverage actually is today

- **Current state:** CONFIRMED, and thinner than the class count suggests. Four classes are real logic with no source-text reads: `TodoProfileTest` (7 assertions), `TodoSwapMomentumTest` (18), `TodoSwapPlanTest` (4), `TodoTargetSafetyTest` (5). Five are source-text contract tests making 41 `Files.readString` calls between them: `TodoEntityMarkTest`, `TodoFakeClapTest`, `TodoHandsEmptyTest`, `TodoPairSwapTest`, `TodoSwapMarkerTest`. So the three newest mechanics — pair swap, entity mark, thrown mark — are pinned by grep, not by behaviour. No test calls any of the four `tryCast` entry points.
- **What remains:** nothing to fix here; this is the baseline the items below measure against.
- **Blocks release:** no.
- **Size:** —
- **Risks:** source-text tests pass over renames and refactors that preserve the searched string while changing behaviour.
- **Basis:** the nine files under `src/test/java/jujutsu/mod/character/todo/`, `KNOWN_ISSUES.md:98`.

### 8.2 — Test for the `afterKill` policy bypass (D2)

- **Current state:** ABSENT. `TodoSwapMomentumTest` covers `decide` thoroughly but nothing covers `afterKill`, which never calls `decide`.
- **What remains:** extend the pure policy test to cover the kill case once 5.1 routes it through `decide`, so the fix is locked down rather than merely applied.
- **How to verify:** `./gradlew qualityGate`, plus the mutation the project requires — restore the bypass, confirm red, revert.
- **Blocks release:** yes, as part of 5.1.
- **Size:** S.
- **Risks:** the test can only cover the policy, not the Fabric event's firing conditions. Say so in the test.
- **Basis:** `TodoSwapMomentumRuntime.java:80-88`.

### 8.3 — Close the feint guard's hole (D-adjacent, see 4.3)

- **Current state:** PARTIAL, described in 4.3.
- **What remains:** extend `TodoFakeClapTest`'s forbidden-token list and add an assertion over `emitClapPerformance`'s own body.
- **How to verify:** mutation — add `SWAP_ARRIVAL` to `emitClapPerformance`, confirm red, revert.
- **Blocks release:** yes.
- **Size:** S.
- **Risks:** as in 4.3.
- **Basis:** `TodoFakeClapTest.java:43-70`.

### 8.4 — World-level coverage for the four cast paths

- **Current state:** ABSENT, and this is E1 in `KNOWN_ISSUES.md` rather than a new finding. Nothing constructs a `ServerLevel`; `grep -rln GameTest src/` returns nothing.
- **What remains:** narrow world tests for the highest-value cases: a valid swap, a blocked destination, a second-teleport failure with its rollback, motion and rotation preservation, cooldown started on success and not on failure, and one mark-leak test per form.
- **How to verify:** the tests themselves, run inside `qualityGate`.
- **Blocks release:** depends. Todo can be called complete on a passed manual smoke, but every mechanic then rests on a checklist someone has to re-run by hand after each change. This is the item that converts section 1 from a recurring cost into a one-time one.
- **Size:** L. It is new infrastructure, and the project's own migration note warns that four state-holding classes must move last because a shared JUnit JVM removes the per-process isolation the `JavaExec` programs have.
- **Risks:** the largest single item in this document. Do not start it as part of a Todo polish pass; it is barrier stage 6 in the project's own plan.
- **Basis:** `KNOWN_ISSUES.md` E1, `BUILDING_IN_SANDBOX.md` "Focused verification".

### 8.5 — Regression test for death cleanup (D1)

- **Current state:** ABSENT.
- **What remains:** once 6.1 lands, a test asserting the death listener is registered. Behaviour needs 8.4; registration does not.
- **How to verify:** `./gradlew qualityGate`, with a mutation removing the listener.
- **Blocks release:** no on its own; it is 6.1's guard.
- **Size:** S.
- **Risks:** a registration-only test proves the hook exists, not that it clears the right state. Say so in the message.
- **Basis:** 6.1.

---

## 9. Definition of "Todo complete"

Todo is complete when **all** of the following are true. Each is checkable; none is a judgement call by itself.

**Release-blocking, must all be satisfied:**

1. Items 1.1 through 1.7 have each been run at a real client **after D1–D4 land**, and the result recorded in `SESSION.md` with the commit they were run at (1.8). Earlier passes are evidence about earlier code and do not carry forward across a lifecycle change. A green build is not a substitute and never becomes one.
2. D1 is fixed (6.1) and confirmed in game (1.4) — death clears marks, glow and resting projectiles.
3. D2 is fixed (5.1) and locked down by a test (8.2) — no ranged or indirect kill spends the melee window.
4. D3 is fixed (6.2) and confirmed in game (1.4) — no marker landing after its owner stopped being Todo.
5. D4 is fixed (4.2) — the marker swap's failed rollback logs like every other rollback in the kit.
6. The feint guard's hole is closed (4.3 / 8.3) with a recorded red mutation.
7. D5 is decided (4.1) — either the stated principle is narrowed to match the code, or the code is changed to match the principle. Not left contradictory.
8. `./gradlew qualityGate` is green at the commit where the above is claimed.

**Documentation-blocking, must all be satisfied:**

9. D6, D7 and D8 are corrected (3.2, 6.3, 5.3).
10. Every UNRUN line in this document has become either a recorded pass or a recorded accepted risk with a named reason, and no REPORTED PASSED line is still resting on testimony alone. No line stays silent.
11. `KNOWN_ISSUES.md` carries any residual Todo item that is being accepted rather than fixed, and this checklist's corresponding entry points at it instead of restating it.

**Explicitly not required for "complete":**

- 8.4, world-level coverage. It is the project's barrier stage 6 and is scoped beyond Todo. Its absence means every future Todo change re-incurs the manual smoke cost — that is a known, accepted, recorded consequence, not a gap in Todo.
- 2.4 (sprint clip), 3.7 (sound variants), 7.4 (Black Flash id ownership). Each is a decision that must be *recorded*; only recording is required, not acting.
- Any balance change. Numbers may only move after section 1 produces observations, and the two existing reopen conditions in `KNOWN_ISSUES.md` are the trigger.

**How to tell this document is finished with:** every item above is either struck or has moved to `KNOWN_ISSUES.md` as accepted debt, and section 1 has no unrun line. At that point this file should be deleted rather than kept — the project keeps no documentation archive, and a completion plan for a completed character is exactly the kind of point-in-time material `docs/README.md` says does not stay here.
