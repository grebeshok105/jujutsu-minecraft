# Megumi shikigami (Nue / Toad / Rabbit Escape / Max Elephant)

Status: CURRENT

## Slice boundary

The Ten Shadows technique key (`PRIMARY`, `R`) no longer means "dogs": it summons, recalls, or
swaps whichever shikigami the per-player selection currently names. `MegumiShikigamiSelection`
(default `DOGS`) is advanced by `TERTIARY_SNEAK` (`Shift+V`); `PRIMARY_SNEAK` (`Shift+R`) sics the
active pack. The `DOGS` selection delegates to the untouched dog runtime (`MegumiSummonRuntime`),
so this note owns only the non-dog layer; the dogs themselves are owned by
[Megumi Divine Dogs](Megumi-Divine-Dogs.md) and the unchanged shadow kit by
[Megumi shadow kit](Megumi-shadow-kit.md).

Server code lives under `jujutsu.mod.character.megumi` (`MegumiShikigami*`, `MegumiNue*`,
`MegumiToad*`, `MegumiRabbit*`/`MegumiRabbits*`, `MegumiElephant*`); client render stacks live
under `jujutsu.mod.client.render.megumi`. Each shikigami is one `MegumiShikigamiEntity` subclass
plus one server brain plus one client animatable/model/renderer triple, mirroring the shipped dog
pattern. Implementation status on this branch: all four bodies ship — Nue in `d781c3b`, Toad,
Rabbit Escape and Max Elephant in `2c51107` — each accepted in game over the MCP dev lane, with
the review-wave fixes landing on top.

## Slot map

| Input | Ability | Answers |
|---|---|---|
| `R` | technique key (`PRIMARY`) | `MegumiShikigamiRuntime.tryPrimary` — summon / recall / swap the selection, or the dogs when `DOGS` is selected |
| `S+R` | sic (`PRIMARY_SNEAK`) | `MegumiShikigamiRuntime.trySic` — send every living ordered body at the aimed target; `SIC_COOLDOWN_TICKS = 30` on `PRIMARY_SNEAK`. Bodies also answer **by themselves**: every tick the per-owner pass in `MegumiShikigamiRuntime.retaliate` / `MegumiSummonRuntime.retaliate` marks the owner's aggressor (`MegumiRetaliationPolicy.pickAggressor` — the owner's last attacker for `RETALIATION_WINDOW_TICKS = 100`, else the nearest mob already targeting the owner inside `RETALIATION_RADIUS = 16`). A sic set by hand outranks it (`hasManualSicTarget`), and `MegumiTargetPolicy.Facts.ownSummonBody` keeps the pack off its own bodies (issue #76) |
| `S+V` | select (`TERTIARY_SNEAK`) | `MegumiShikigamiRuntime.tryCycle` — advance the selection, action-bar feedback, never a cooldown |
| `B` / `S+B` (+hold) / `V` | shadow kit | unchanged: trap / step / deep submerge / drop (`MegumiAbilityRouter`) |

The roster card (`MegumiClientDefinition.rosterEntry`) lists seven rows in input order — Divine
Dogs `R`, Sic `S+R`, Shikigami Select `S+V`, Shadow Trap `B`, Shadow Step `S+B`, Deep Submerge
`S+B+`, Shadow Drop `V`. The `R` row still carries the `divine_dogs` label key: with the default
`DOGS` selection it summons the dogs, and after a cycle it summons whatever is selected. Five HUD
cells mirror the five technique slots (`PRIMARY`, `PRIMARY_SNEAK`, `SECONDARY`, `SECONDARY_SNEAK`,
`TERTIARY`); the `S+V` row owns no cooldown and gets no cell. The `PRIMARY` denominator is
`max(maxRecallCooldownTicks(), maxDeathCooldownTicks())` over `MegumiShikigamiProfile`, currently
600 (Max Elephant death); `TERTIARY_SNEAK` answers 0.

## Selection semantics and swap rules

`MegumiShikigamiSelection` is an in-memory, owner-keyed map: `selected` (default `DOGS`), `cycle`
(`selected().next()`, order dogs → nue → toad → rabbits → elephant → dogs), `set`, `clear`,
`clearAll`. It resets on relog by design (accepted limit, see `docs/KNOWN_ISSUES.md`); only
`DISCONNECT` and `SERVER_STOPPING` clear it in production. The dev/MCP fixture-reset tool step deliberately restores the DOGS default (verified in game: clean-slate semantics for tests, not keeps-selection).

`tryPrimary` decides through the pure `MegumiShikigamiSwapPolicy` (`DELEGATE_DOGS` /
`RECALL_SELF` / `RECALL_OTHER_THEN_SUMMON` / `SUMMON`):

1. Selection `DOGS` → the dog runtime answers (`tryToggle`); a live non-dog pack is swapped out
   first with `TeardownReason.SWAPPED`.
2. A live pack of the selected type → manual recall (`RECALL`), which costs that type's recall
   cooldown.
3. A live pack of another type, or live dogs under a non-dog selection → swap: tear the old side
   down with `SWAPPED` (visual recall, **no cooldown**) and summon the selection, with the
   `shikigami.swap` action-bar message (`%s return — %s takes the field`).
4. Nothing live → summon the selection: air-spot preflight for Nue, ground/ring preflight for the
   rest; failure answers `shikigami.no_room` and returns false; success plays the shadow-open
   sound plus the type's summon cue and starts no cooldown.
5. A second `PRIMARY` in the same game tick is a no-op `true` (key-repeat guard on
   `summonedAtGameTime`).

`trySic` resolves through the unchanged `TargetResolver` within `SIC_RANGE = 20.0` plus owner line
of sight and `MegumiSummonRuntime.isEligibleTarget`, assigns every combat-enabled body
(`acceptsSicCommand`), and plays the snap plus the generic `megumi/shikigami_sic` marker. With no
living bodies it answers `shikigami.none`.
## Per-type mechanics and tuning pointers

Every number lives in `MegumiShikigamiProfile`; brains contain no magic constants. Cooldown table
(all five rows shipped in the profile; the DOGS row mirrors the dog runtime for reference only):

| Type | Recall | Death |
|---|---|---|
| Dogs (reference) | 240 | 600 |
| Nue | 240 | 400 |
| Toad | 240 | 400 |
| Rabbit Escape | 120 (manual recall) / 120 (lifetime expiry) | 200 (anchor loss) |
| Max Elephant | 260 | 600 |

**Nue** (fragile flyer, hitbox 0.9×0.9): 24 health, 4 attack, 0.38 speed; materialize 16 /
recall 12 ticks; hovers 3.0 above the owner's head off-command (`FlyingMoveControl`, vanilla —
the plan's custom-move-control question was settled for reuse). Sic starts a server-driven dive
within 24 blocks and line of sight: straight-line steering at the target's eyes at 0.55
blocks/tick, 40-tick timeout, impact at box-to-point distance ≤ 1.6 → 5.0 owner-attributed damage,
stagger 12 ticks, `SLOWNESS` 40 ticks, 60-tick charge cooldown. The canon combo: a
`MEGUMI_SOAKED` target (Max Elephant's primer) takes ×1.5 damage, 20-tick stagger, 60-tick slow.
Clips `idle`/`fly`/`attack` on the body layer; the imported `flight_feet` (legs and feet, four of
ten bones) and `grab_feet` (talons) ride a second `megumi_nue_feet` controller, because a partial
clip on the body controller freezes every bone it does not name — the wings used to lock for the
whole of fast travel. Policy
`MegumiShikigamiAnimationPolicy.nue`: phase rise/sink outranks the `attack` one-shot, which
outranks travel. Sounds: shared shadow-open swell plus `PHANTOM_FLAP`/`PHANTOM_AMBIENT` on
emergence, shared implosion on recall, `PHANTOM_AMBIENT` sic accent, `PHANTOM_BITE` + quiet `TRIDENT_THUNDER` on impact — vanilla placeholders plus the existing mod-owned shadow sounds, no new entries.

**Toad** (grappler, hitbox 1.3×1.0): 80 health, 4 attack, 0.22 speed; materialize 16 / recall 12;
recall 240 / death 400. Tongue within 12 blocks + owner line of sight: 6-tick windup
(`beginAction`), strike on the final action tick → 3.0 owner-attributed damage, yank toward the
toad at 0.65 horizontal + 0.25 up (`MegumiToadPolicy.pullVelocity`), stagger 8 ticks, 100-tick
tongue cooldown. Pure policy: `pullVelocity` (zero horizontal when degenerate), `canTongue`
(inclusive range), `strikeTickReached` (== 1). Clips `howl`/`walk`/`attack`/`tongue`; tongue is a
VFX-only strike (no tongue geometry upstream — accepted limit). Sounds: `FROG_LONG_JUMP` +
`FROG_AMBIENT` on emergence, `FROG_TONGUE` windup + `FROG_EAT` impact, `FROG_AMBIENT` idle.

**Rabbit Escape** (swarm, hitbox 0.4×0.4): 10 bodies, 4 health each, 0 damage, 0.32 speed, ring
radius 2.2; materialize/recall 10 ticks; recall 120 / death 200, and the 300-tick lifetime expiry is
priced by its own row (`TeardownReason.EXPIRED`) / 120. One hidden anchor
(random body at summon): anchor loss disperses the whole pack (`teardown(DEATH)`, 200-tick
cooldown); manual recall and the 300-tick lifetime expiry both cost 120. Upkeep every 20 ticks
tops the swarm back up by at most 2 (`registerExtraBody`). Per body every 10 ticks: hostiles
within 1.4 that pass eligibility and friendly-fire are bumped (0.35 knockback, slight up),
`SLOWNESS` 20 ticks, `rabbits_pop` cue. Pure policy: `shouldRespawn` / `expired` / `bumpReady` /
`respawnBatch`. Clips `walk`/`run`/`attack`; hop movement is a small own `MoveControl` (jump
every 10 ticks while moving). Bodies spawn inside `FollowOwnerGoal`'s stop radius, so they need their
own locomotion — `RabbitChaseGoal` (4) closes on the mark while `MegumiRabbitSwarmPolicy.shouldChase`
holds, `RabbitDriftGoal` (7) picks a fresh ring point every `RABBIT_DRIFT_INTERVAL_TICKS = 30` while
the body sits inside `RABBIT_DRIFT_LEASH = 9` of the owner and carries no mark (issue #78). Sounds: `RABBIT_AMBIENT` summon/idle, `RABBIT_HURT` bump. The
near-flat upstream texture ships as-is (accepted limit).

**Max Elephant** (heavy, hitbox 2.0×2.2): 120 health, 6 attack, 0.18 speed; materialize 30 /
recall 16; recall 260 / death 600. Trunk jet within 16 blocks + line of sight: 8-tick windup,
40-tick firing window, one pulse every 2 ticks down a 12-long, 1.4-half-width corridor from the
trunk (`MegumiElephantPolicy.inJetCorridor`, excludes behind the trunk): 1.0 owner-attributed
damage, 0.5 knockback with small up, douse (`clearFire`), `MEGUMI_SOAKED` 100 ticks, 220-tick jet
cooldown. Friendly-fire bodies in the corridor take nothing. Clips `idle`/`walk`/`run`/`attack`/
`shoot` (the authored `loop:true` on `shoot` does not govern `RawAnimation` playback — the action
controller holds it one-shot while `actionTicks > 0`, dog precedent). Sounds: quiet
`RAVAGER_ROAR` summon, `RAVAGER_ATTACK` windup + `GENERIC_SPLASH` per pulse, `RAVAGER_AMBIENT`
idle. The off-origin `body` pivot is upstream data; only renderer scale/offset may compensate.

## Lifecycle and cleanup

`MegumiShikigamiRuntime` keeps one owner-keyed `MegumiShikigamiPack` (type, dimension, anchor id,
body ids, summon token, summon game time) plus the `TEARDOWN_IN_PROGRESS` guard. `teardown` is
the single destructive entry point: drop the record, cross-level sweep of owned bodies,
`beginRecall` for the RECALL family and `discard` otherwise, then the reason's cooldown via
`startCooldownIfLonger` (a longer active deadline is never shortened).

| Trigger | Reason | Bodies | PRIMARY cooldown |
|---|---|---|---|
| `R` on the active selected type | `RECALL` | sink-out | recall row |
| `R` with another side live | `SWAPPED` (old side) | sink-out | none (swap is free) |
| Anchor dead, or zero living bodies (tick/death/unload reconcile) | `DEATH` | vanish | death row |
| Owner death | `DEATH` | vanish | death row |
| Vessel deselect (`onDeselected`) | `DESELECTED` | sink-out | recall row; selection kept |
| Owner disconnect | `DISCONNECT` | vanish | none; selection cleared |
| Respawn | `RESPAWN` | vanish | none |
| Dimension change | `DIMENSION_CHANGE` | sink-out | recall row |
| Server stopping | `SERVER_STOPPING` | vanish | none; `clearAll()` |
| Dev/MCP fixture reset | `FIXTURE_RESET` | sink-out | none; the tool then clears the selection back to DOGS |
| Rabbit lifetime expiry | `EXPIRED` | sink-out + `rabbits_pop` | 120 (expiry row) |

Presentation phases (`MegumiShikigamiPresentationPolicy`, parameterized — the dog policy is
untouched): `MATERIALIZING` (no AI/nav, no combat either way, rises from one block below),
`ACTIVE` (only combat-enabled phase), `RECALLING` (sinks, then hard-discards). Bodies whose
record is gone, whose owner is gone, or whose type/token/dimension mismatches hard-discard on
their own tick (`shouldHardDiscard`); a `RECALLING` body in its own dimension may finish sinking
without a pack. Stale sic targets revalidate every tick against eligibility.

Shared helpers: `MegumiShikigamiSpawnPlacement` (ground / air / ring preflights over
`SafeBodyPlacement`, with predicate seams for pure tests), `MegumiShikigamiFriendlyFire`
(owner, same-team players, own shikigami/dogs are protected from every AoE), `PackView`
(`type`, `dimension`, `anchorAlive`, `aliveBodies`, `summonedAtGameTime`, `anchorId` — the dev
and GameTest observation surface, mirrored into the MCP `state_get` `megumi.shikigami` object).

## Asset provenance

All four models are Sorcery Age extracts, used with the author's personal permission (owner
statement — same class as the Mythic Mounts entry). Full record: `docs/PROVENANCE.md` and
`docs/THIRD_PARTY_NOTICES.md` (upstream `wood-m-corp/sorcery-age`, commit
`40a60272b95a6d408a91963ee26ea297ed8fd200`, per-file git blob SHA-1 manifest). Imported per
type: one geo (`geometry.megumi_<x>` under `assets/jujutsumod/geckolib/models/`), one animation
set (clips re-keyed to `animation.megumi_<x>.*`, `geckolib_format_version: 2` stamped where the
archive lacks it), one texture (`textures/entity/megumi_<x>.png`). The FULL upstream clip set is
kept; code contracts only the used subset (clip map: Nue `misc.idle`→`idle`, `move.fly`→`fly`,
`attack.swing`→`attack`; Toad `misc.howl`→`howl`, `move.walk`→`walk`, `attack.swing`→`attack`,
`attack.tongue`→`tongue`; Rabbit `move.walk`→`walk`, `move.run`→`run`, `attack.swing`→`attack`;
Elephant `attack.shoot`→`shoot`, `move.walk`→`walk`, `move.run`→`run`, `misc.idle`→`idle`,
`attack.swing`→`attack`). Deliberately unshipped: `toad_tongue.png` / `toad_wings.png`
(unreferenced by the geo). No new ogg files: every shikigami reuses vanilla sounds as
placeholders. VFX ids (`MegumiVfxIds`, all in `LIVE` with recipes in `MegumiVfxRecipes`):
`megumi/nue_summon`, `megumi/nue_dive`, `megumi/nue_shock`, `megumi/toad_summon`,
`megumi/toad_tongue`, `megumi/rabbits_summon`, `megumi/rabbits_pop`, `megumi/elephant_summon`,
`megumi/elephant_jet`, plus the shared `megumi/shikigami_sic` marker and
`megumi/shikigami_recall` sweep.

## Evidence boundary

JUnit plus architecture checks cover selection order/defaults, the swap-decision table,
teardown-reason → cooldown mapping (`SWAPPED` = 0), presentation transitions and combat gates,
spawn-placement offset math, friendly-fire membership, per-type pure policies (dive velocity,
impact predicate, soaked escalation; tongue pull/range/strike; rabbit respawn/expiry/bump;
elephant corridor), resource contracts (geo identifiers, clip keys, one-shot attack/tongue,
texture paths, `geckolib_format_version`), router-arm and roster/HUD pins, and the lang key sets.

GameTests (`MegumiShikigamiGameTests`, `MegumiToadGameTests`, `MegumiRabbitsGameTests`,
`MegumiElephantGameTests`, `MegumiShikigamiCrossTests`) cover summon → pack shape, recall and
death cooldowns per type, the zero-cooldown swap, sic-driven abilities end to end (dive damage +
slow, tongue pull, bump knockback + slow, jet damage + soak), the swarm upkeep/expiry/anchor
rules, friendly fire inside the jet corridor, and the cross-type guarantees (one-active,
fixture-reset teardown with the selection back to DOGS, deselect teardown with the selection kept).

Oracle trap, measured in game 2026-09-11: a `NoAI:1b` mob is FULLY FROZEN — external velocity is stored but the position never integrates, not even gravity. Displacement-based oracles ("distance decreased", knockback travel) must use an AI mob with zeroed speed (Slowness amplifier 100) or assert velocity/effect state instead (`getDeltaMovement`, effects). The tongue/bump scenarios pin the pull through the velocity vector for exactly this reason.

No automated test boots the client, renders a frame, or plays audio. Summon-geometry feel on
floors, ledges, water, and tight rooms; flight/pull/knockback feel; kill attribution; spatial
mix and duplication of the vanilla placeholder sounds; HUD placement; remote synchronization;
and the look of each model against its hitbox (Nue wingspan ≈ 1.6 blocks, Toad ≈ 1.2 tall,
Elephant pivot compensation) remain the in-game verification pass in
`.superpowers/rule-of-four/megumi-shikigami/progress.md`.
