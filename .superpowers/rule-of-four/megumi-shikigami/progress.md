# Rule of Four — megumi-shikigami (Nue / Toad / Rabbit Escape / Max Elephant)

Task: implement the four remaining Megumi shikigami on the existing Ten Shadows / summon system,
using the Sorcery Age GeckoLib assets (author-permitted, archive verified by SHA). User command:
«все выполняешь по правилу 4». Autonomy: design decisions are delegated to the agent; balance
values easily tunable; strictly one shikigami at a time with in-game verification before the next.

## Recorded deviation (Phase 2 mechanics)

Default Phase 2 batches 4 workers in parallel. The user requires strictly sequential acceptance
(«не начинай следующего, пока предыдущий не работает»), so Phase 2 blocks are ACCEPTED
sequentially — one block at a time, each closed by a green build + in-game MCP verification of that
shikigami.

Update 2026-09-11 (user instruction «запускай по правилу 4 воркеров, сразу несколько, а не одного»):
DEVELOPMENT now runs in parallel (Toad + Rabbit + Elephant + integration/docs workers together),
while ACCEPTANCE stays sequential in the plan order. To keep parallel development safe:

- every shikigami worker owns only its private files (server trio, client render stack, assets,
  unit tests, its GameTest class);
- ALL shared-file changes — `MegumiShikigamiProfile`, `MegumiShikigamiRuntime`, `JujutsuEntities`,
  `MegumiDefinition`, `MegumiVfxIds`, `MegumiVfxRecipes`, `MegumiClientDefinition`, both lang files,
  the three VFX pin tests, `00-MOC.md`, `src/gametest/resources/fabric.mod.json` — are applied by
  MAIN from worker-provided snippets, one type at a time, in the plan order (Toad → Rabbit →
  Elephant), each followed by a build + in-game pass before the next type is accepted;
- gradle is single-threaded by agreement (workers ask main before any build; the live lane and
  builds never overlap).

Everything else of the pipeline (Phase 0 scouts, Phase 1 plan, Phase 1.5 plan review, Phase 3
review wave of 4 reviewers + QA, Phase 4 adjudication + fable-judge, Phase 5 capture) is kept.

## Phase log

- [x] Phase 0 — 4 scouts dispatched in one batch 2026-09-11: SummonCore, VesselWiring, RenderAssets,
      CanonVerify. ALL SETTLED; reports persisted as scout-1..4-report.md (cleaned markdown).
- [x] Phase 1 — implementation plan written (writing-plans loaded first): `implementation-plan.md`,
      617 lines: frozen contracts + block 5 (foundation + Nue, main) + blocks 1-3 (Toad / Rabbit /
      Elephant) + block 4 (integration + docs), verification protocol, self-review.
- [x] Phase 1.5 — 3 plan reviews settled (PlanBuildability 21 findings, PlanVerifiability 17,
      PlanCompleteness 10 = 38 total). All adjudicated in `plan-review.md`; every confirmed P0/P1
      fixed in the plan (cycle arm, zero-cost SWAPPED swap + its owned dog-file edit, frozen
      SwapPolicy, arena-safe GameTest ranges, roster/HUD ownership, oracle fixes, sound table,
      red-proof duty, fixture file, report deliverable). One rejection with reason (C1 cooldown
      pin — conflicts with the frozen zero-cost swap). Phase 1.5 CLOSED; P0 count = 0.

Environment check (2026-09-11): `JAVA_HOME=temurin21` + `compileJava compileClientJava` green (22 s).
MCP lane prerequisites on disk: upstream jar, `run/saves/mcp-spike`, config auth_required=false.
- [ ] Phase 2 — sequential blocks + green barrier per block + in-game verification per shikigami
- [ ] Phase 3 — review wave (4 reviewers + QA) → `review-spec.md`, `qa-report.md`
- [ ] Phase 4 — adjudication, fixes, fable-judge pass, final verification
- [ ] Phase 5 — capture (learn/retain, docs, CHANGELOG if present)

## Green-barrier evidence

### Block B5 (foundation + Nue, main) — 2026-09-11

- `compileJava compileClientJava` green; `mcpdevClasses` (with `-PmcpUpstreamJar`) green.
- Focused JUnit suites green (173 tests across `jujutsu.mod.character.megumi.*`, `jujutsu.mod.client.vfx.*`,
  `jujutsu.mod.vfx.*`): new `MegumiShikigami*Test` (roster, selection, pack, swap policy, presentation
  policy, profile, spawn placement) + `MegumiNuePolicyTest`; pins updated in `MegumiAbilitySlotsTest`
  (3 new arms), `MegumiLifecyclePolicyTest` (SWAPPED free + widened recall predicate),
  `VfxCompletenessTest` (49 → 54 live ids), `VfxCueTest` (5 new wire strings), `VfxRadiusContractTest`
  (new presentation-owner row).
- Independent read-only audit of the whole B5 server/client diff (reviewer subagent) before the first
  build: 2 blockers + 2 wrong-API findings, all fixed (missing `ParticleTypes` import, `defineSynchedData`
  override, `setCanPassDoors` removed, dog recall cue owner fetched for SWAPPED).

## In-game verification evidence

### Toad — MCP dev lane, 2026-09-11 (parallel-dispatch round)

| Check | Result |
|---|---|
| Summon with TOAD selected | state: `type: toad`, `alive_bodies: 1`, `anchor_alive: true`; entity `jujutsumod:megumi_toad` HP 80/80 |
| Model render | imported bipedal frog model renders (bones `body/head/mouth/left_arm/...`, 19.5 model units × MODEL_SCALE 0.85 ≈ 1.0-1.2 blocks — matches the plan target) |
| Tongue damage | zombie 20 → 17.06 (2.94 = 3.0 raw with zombie armour) |
| Tongue pull | zombie 6.0 → 4.70 → 3.16 blocks from the toad within 6 ticks of the strike |
| Sic cooldown | 30 (`SIC_COOLDOWN_TICKS`) |
| Recall | PRIMARY 240 at the tick of the call |
| GameTests | `megumi_toad_game_tests_*` 4/4 green in the full gate |

**Oracle discovery (project-wide, found here):** a `NoAI:1b` mob is FULLY FROZEN — an externally set
velocity is stored but the entity never moves (no travel, not even gravity: a NoAI zombie spawned
4 blocks up stays airborne). Every displacement-based oracle (pull / knockback / shove) must
therefore use an AI zombie whose speed is zeroed by Slowness (`amplifier:100`) — it travels under
applied velocity while its own AI cannot displace it. Velocity-magnitude asserts on NoAI targets
stay valid. Toad S2's secondary "distance decreased" assert was vacuous (the toad walked to the
zombie) and is being reworked to this idiom; Rabbit/Elephant suites audited for the same trap.

### Nue — MCP dev lane, 2026-09-11 (three sessions: initial, post-fix, regression)

| Check | Result |
|---|---|
| Summon (R) with NUE selected | `megumi.shikigami` state: `type: nue`, `alive_bodies: 1`, `anchor_alive: true`; entity `jujutsumod:megumi_nue` at owner +3.0 (hover goal), health 24, noGravity |
| Model + texture render | Client screenshot: bird silhouette with broad wings + hanging talons, reddish-brown imported sheet (vision-model description of `.tmp-nue-view.png`) |
| Sic (⇧R) | cooldown 30 (`SIC_COOLDOWN_TICKS`); dive triggered, entity steered onto the target |
| Dive damage | zombie 20 → 15.08 (4.92 raw, armour-reduced 5.0) |
| Soak escalation | soaked zombie: 20 → 12.62 (**7.38 = 5.0 × 1.5**), SLOWNESS 60 ticks at the hit sample (`NUE_SLOW_TICKS_SOAKED`); plain control 4.92 |
| Manual recall (R) | cooldown 241 at the tick of the call (240), body removed, `type: null` |
| Anchor death | cooldown 395 right after the kill (400 − reconcile ticks), body removed |
| Free swap | dogs active → select NUE → R: dogs pack gone, Nue out, PRIMARY cooldown **0** |
| `fixture_reset` | 22 steps incl. `megumi_shikigami_teardown`, `megumi_shikigami_selection_clear`; selection back to `dogs` |
| Dog regression | dogs summon (white+black alive) then ⇧R sic damaged the target (20 → 17.06) — untouched system still green |

Bug found only in game and fixed in B5: the dive's impact test used feet-to-feet distance while the dive
steers at the target's eyes, so the flyer hovered one body-height above the target and never landed a hit;
now `MegumiNuePolicy.impactReachedSq` measures target hitbox → body position (`AABB.distanceToSqr`).
Verified after the fix by the damage numbers above.

## 2026-09-12 — gate green, Rabbit + Elephant accepted in game, animation-layer bug fixed

Full `qualityGate` (2026-09-12, after the fixes): **BUILD SUCCESSFUL in 1m 2s; 56 GameTest cases, 0 failures** —
all four shikigami suites plus `MegumiShikigamiCrossTests` C1–C4. The C4 failure from the previous gate was a
**test** artifact, not production: the cooldown store keys on `(player, vessel, slot)` and resolves the vessel
from the live selection, so reading PRIMARY under NONE returned 0 while the 240-armed row lives under MEGUMI.
C4 now deselects, re-selects MEGUMI and asserts the cooldown resumes at 240 — the real player-visible contract.

### Bug found by review advisory, confirmed by the animation JSONs, fixed (client only)

| Body | Clip on the body controller | Bones | Was | Fix |
|---|---|---|---|---|
| Nue | `flight_feet` for RUN/RISE | 4 (legs+feet) vs `fly`'s 10 | wings/head/tail/torso froze for the whole of fast travel — which is the entire dive | body layer plays whole-body clips only; new `megumi_nue_feet` layer owns legs+talons (`flight_feet` while travelling, `grab_feet` while the impact is live) |
| Toad | `tongue` for the tongue action | 1 (`mouth`) | the whole toad froze for the length of the strike | new `megumi_toad_action` layer owns tongue/swing; the walk cycle keeps the legs under it |

Both follow the shipped dog precedent (`MegumiDogGeoAnimatable` BASE + BITE). Rabbit (attack on its own ACTION
controller) and Elephant (`shoot`/`walk`/`run` are whole-body) were audited and are clean.

In-game evidence (MCP lane, 2026-09-12): Nue dive frames — wings *different pose* between frames ("angled up and
swept back" → "spread out horizontally/downward", legs tucked → trailing); Toad strike frames — body changes pose
("forelegs-down lunge" → "body stretched low, hind legs trailing" → "upright rearing"). Frames in
`.omp/anim-check/`. Caveat: 854×480, so the reads are approximate; the bone-coverage fact is the primary proof.

### Rabbit Escape — accepted in game (all six oracles)

| Check | Result |
|---|---|
| Summon (R) | `type: rabbits`, `alive_bodies: 10`, `anchor_alive: true`, `anchor_id` present, PRIMARY cooldown **0** |
| Bump (displacement) | slowed-AI probe (AI + Slowness 100) shoved **4.91 blocks** (dx −2.53, dz −4.21) off the swarm |
| Bump (effect) | clean NoAI probe: `minecraft:slowness` amplifier 0, 19 ticks left — applied by the bump, no self-inflicted effects |
| Anchor death | anchor killed → pack torn down (`type: null`), PRIMARY read **176** (200 armed, 24 ticks elapsed) |
| Manual recall | cooldown **120** at the call, pack gone |
| Lifetime expiry | after 310 ticks the swarm expired by itself: pack `null`, recall-family cooldown armed (84 left) |

### Max Elephant — accepted in game

| Check | Result |
|---|---|
| Summon (R) | `type: elephant`, 1 body, health **120/120**, PRIMARY cooldown 0 |
| Sic (⇧R) → jet | `routed: true`, SIC cooldown 30; frozen victim 20 → **16.24** (−3.76 = 4 pulses × 0.94 after armour) and `jujutsumod:megumi_soaked` applied |
| Owner inside the corridor | the player stood in the jet line 3 blocks from the elephant, victim beyond at 9: victim soaked + damaged, **owner effects empty** — the live-corridor premise holds in game |
| Recall (R) | cooldown **250** at the read (260 armed), pack gone |
| Combo Nue × SOAKED | soaked dive **7.38** vs plain dive **4.92** = ×1.5 multiplier honoured (the plain case also ate a follow-up melee swipe, 8.86 total — oracle notes below) |

Oracle notes for the next session: `entity_teleport`'s `facing` computes pitch from the **entity's feet**, so aiming
at a target's eye level tilts the ray 10–20° high and the sic misses (`routed: false` with no message). Aim at
`target.y − 0.6` for a flat shot. A second silent refusal cause: the player's own shikigami body standing in the
ray (`isOwnBody` check) — park the body off the firing line before sic. Both cost ~40 minutes of lane time today;
recorded so the next run does not repeat it.

## 2026-09-12 (cont.) — fix wave, gate round, red-proofs

Fix wave: `FixerE` (server bodies: #2 jet plant, #3 reach gate, #6 Nue yaw, #9 windup sound, #11 upkeep
clock, #12 staggered bump windows, #13 single-sourced expiry arm), `FixerC` (client: #4 elephant action
layer, #8 layering pins + Nue resources test + `animation_length` stamps, #10 policy unit suite, #14 S4
exactness, #15 Toad pins), main (#1 swap staging, #5 swap-out sink, #7 roster label + lang, #16 dog
`isOwnSummonBody` guard, docs #17–#19 in KNOWN_ISSUES).

Main repaired three compile slips from the wave (missing `UUID`/`GameTest` imports in the rabbit suite,
a dropped `cache` field in the elephant animatable) and one behavioural slip the fixer's own new test
caught: the jet-end path lost the sic-target restore (`elephantJetSuspendsAiWhileFiring`, red at tick 85
→ green after re-adding it).

`qualityGate`: **BUILD SUCCESSFUL, 67 GameTest cases / 0 failures, JUnit 345+** — green after every
mutation below was restored.

### Red-proofs (each check observed failing on its mutation, then green on restore)

| Check | Mutation | Observed red |
|---|---|---|
| `refusedSwapKeepsTheBodyAlreadyOut` (C9, new) | teardown hoisted above the staging block | `pack view present: expected <present>, actual <absent>` |
| `theSwapOutBodyFinishesItsSink` (C10, new) | `shouldHardDiscard` type-mismatch arm back to `return true` | `nue bodies still sinking: expected <1>, actual <0> @tick 7` |
| `theOldSixteenBlockTriggerRefusesToFireABlankVolley` | `jetTriggerInReach` body replaced by `return true` | FAILED |
| `eastboundFlightFacesEastNotWest` + 3 more yaw pins | `atan2(-x, z)` → `atan2(x, z)` | 4 FAILED |
| `aFreshPackStartsFromItsOwnSummonTick` | upkeep mark read without the summon token | FAILED |
| `toadActionBeatsWalkAndRestIdles` | action arm moved below the movement arm | FAILED |
| `theTongueRidesTheActionControllerAsAOneShotClip` | tongue re-published from the base controller | FAILED |
| `elephantJetSuspendsAiWhileFiring` (FixerE's, new) | jet-end lost the target restore (live slip, not a planted mutation) | `sic target restored after the jet: expected <zombie>, actual <null> @tick 85` |

C9/C10 were iterated twice before they could fail: the rabbit-ring seal missed the ring's block cells
(radius 2.2 rounds onto ±2 cells and the placer scans ±3 vertical offsets, so a six-block seal over the
whole neighbourhood was still leaky), and the sink read sat before the outgoing body's next tick. C9 now
blocks the *air* column instead — the Toad stands forward of it, so only the Nue arrival is refused —
and C10 reads three ticks in.

### In-game re-verification of the fix wave (lane pass 2, 2026-09-12)

| Check | Result |
|---|---|
| Elephant reach gate — target ~21 blocks from the body | sic answers `routed: true` (the shared aim still accepts it) but **no jet**: no soak after 60 ticks, no jet cooldown armed; the body walks over and melees instead (5.0 damage, the sic target stays a valid combat target). The old build fired a 20-pulse blank volley here. |
| Elephant jet in reach (6 blocks) | `routed: true`, target 20 → 16.24 with `MEGUMI_SOAKED` — the gate did not break the normal case. |
| Elephant plant | six ~10-tick samples of the body's position during the jet: **drift 0.000 every sample** (the earlier 0.6-block reading was the approach momentum before the plant engaged). Target damaged + soaked while the body stood still. |
| Nue facing (the mirrored-yaw fix) | dive frames: the bird reads as seen **from behind, tail toward the viewer, diving away toward the target** — the corrected yaw (`atan2(-x, z)`) has it face its motion. 854×480 caveat: the head is not distinctly resolvable, the reading is silhouette + dive direction. |

Frames: `.omp/anim-check/nue-yaw-{0,1,2}.png`, `.omp/anim-check/nue-yaw-triple.png`.

### fable-judge pass (2026-09-12) — verdict: REFUTED (narrow), fixed

The judge re-ran the four load-bearing red-proofs verbatim (all reproduced), re-ran the gate itself
(**67 GameTest cases / 0 failures, JUnit 371 / 0**), confirmed the Toad floors still fail on a halved
pull, and confirmed scope/docs/debris clean. One refutation:

- `RABBITS_EXPIRY_COOLDOWN_TICKS` had become **write-only** when the fix wave single-sourced the expiry
  path onto the recall-family teardown: mutating that row 120 -> 121 kept every game test green, so the
  S4 javadoc claimed a red-proof that did not exist.

Fix (this commit): the dead row is deleted, `expire()`'s javadoc names `RABBITS_RECALL_COOLDOWN_TICKS`
as the price of expiry, the S4/S6 javadoc now names the row they actually pin, the S4 literal is folded
into `EXPECTED_RECALL_COOLDOWN_TICKS`, and the Codex lifecycle table says "120 (recall row)".

Re-proved after the fix: mutating `RABBITS_RECALL_COOLDOWN_TICKS` 120 -> 121 fails **both** S4
(`PRIMARY expiry cooldown (elapsed-corrected): expected <120>, actual <121> @tick 310`) and S6
(`PRIMARY recall cooldown: expected <120>, actual <121> @tick 4`); restored, gate green again.
