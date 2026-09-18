# Megumi shikigami (Nue / Toad / Rabbit Escape / Max Elephant)

Status: CURRENT

## Slice boundary

The Ten Shadows technique key (`PRIMARY`, `R`) summons or recalls whichever shikigami the
per-player selection currently names — and since issue #107 the types are **additive**: every
pack stands beside the others, there is no swap branch left. `MegumiShikigamiSelection`
(default `DOGS`) is advanced by `TERTIARY_SNEAK` (`Shift+V`); `PRIMARY_SNEAK` (`Shift+R`) issues
one **global sic** to every living body across both families, and an empty aim clears all manual
orders. The `DOGS` selection delegates to the dog runtime (`MegumiSummonRuntime`), whose pack
coexists with the shikigami packs; the dogs themselves are owned by
[Megumi Divine Dogs](Megumi-Divine-Dogs.md) and the unchanged shadow kit by
[Megumi shadow kit](Megumi-shadow-kit.md). Issue #108 adds the partial-manifestation key (`X`,
press/release edges): Nue's wings and Toad's tongue, one partial at a time, refused while that
type's full body is materialized.

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
| `R` | technique key (`PRIMARY`) | `MegumiShikigamiRuntime.tryPrimary` — summon the selection, or recall exactly that type when it is already out (`MegumiShikigamiSwapPolicy`: `RECALL_SELF` / `SUMMON`, plus `DELEGATE_DOGS`); additive, never a swap |
| `S+R` | sic (`PRIMARY_SNEAK`) | `MegumiShikigamiRuntime.trySic` — one global order: the aimed target is assigned to every combat-enabled body in BOTH families; an aim that names nothing clears every MANUAL mark (`sic_cleared`, D5). Costs the `PRIMARY_SNEAK` cooldown on success. Bodies also answer **by themselves**: every tick the per-owner pass in `MegumiShikigamiRuntime.retaliate` / `MegumiSummonRuntime.retaliate` marks the owner's aggressor (`MegumiRetaliationPolicy.pickAggressor` — the owner's last attacker for `RETALIATION_WINDOW_TICKS = 100`, else the nearest mob already targeting the owner inside `RETALIATION_RADIUS = 16`). A sic set by hand outranks it (`hasManualSicTarget`), and `MegumiTargetPolicy.Facts.ownSummonBody` keeps the pack off its own bodies (issue #76). The window is measured on the owner's own `tickCount` — vanilla stamps `lastHurtByMobTimestamp` with it |
| `S+V` | select (`TERTIARY_SNEAK`) | `MegumiShikigamiRuntime.tryCycle` — advance the selection, action-bar feedback, never a cooldown; refused while a partial manifestation is out |
| `X` (press/release) | partial (`PARTIAL` / `PARTIAL_RELEASE`) | `MegumiPartialRuntime.tryPartial` / `tryPartialRelease` — Nue selection toggles the wings, Toad selection anchors the tongue at the aimed surface (release detaches); other selections refuse, and so does a partial whose type's full body is out |
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
`RECALL_SELF` / `SUMMON` — the swap branch died with coexistence, issue #107 D1):

1. Selection `DOGS` → the dog runtime answers (`tryToggle`); other packs stay out untouched.
2. A live pack of the selected type → manual recall (`RECALL`), which costs that type's recall
   cooldown.
3. Nothing of that type live → summon it beside whatever else is out: air-spot preflight for
   Nue, ground/ring preflight for the rest; failure answers `shikigami.no_room` and returns
   false; success plays the shadow-open sound plus the type's summon cue and starts no cooldown.
4. A second `PRIMARY` in the same game tick is a no-op `true` (key-repeat guard on
   `summonedAtGameTime`).

`trySic` resolves through the unchanged `TargetResolver` within `SIC_RANGE = 20.0` plus owner line
of sight and `MegumiSummonRuntime.isEligibleTarget`. Since #107 it is one **global** order: every
combat-enabled body in both families (`acceptsSicCommand`) takes the mark, the snap plus the
generic `megumi/shikigami_sic` marker plays, and the `PRIMARY_SNEAK` cooldown applies. An aim that
names nothing is the release-the-pack edge: every MANUAL mark is cleared and the answer is
`sic_cleared`. With no living bodies it answers `shikigami.none`.

## Autonomous coordination (issue #107)

`MegumiPackCoordinator` is the autonomous half of Ten Shadows: every `COORDINATION_SCAN_TICKS = 5`
ticks it rebuilds one `MegumiCombatContext` per owner — a single entity scan shared by the whole
pack — then lets the pure `MegumiCoordinationPolicy` pick each mark-holding body's target. Bodies
keep acting on their `sicTargetUuid` exactly as before; the coordinator only writes the mark. It
registers AFTER the two pack runtimes so its END_SERVER_TICK reads the marks their reconcile and
retaliation passes already settled. A MANUAL sic is never reassigned; a RETALIATION mark is left
to the retaliation pass; Rabbit Escape carries no marks at all (D13 — the swarm's chaos is its
contribution).

Scoring is weights only — no veto path: distance, danger (max health), soaked/held/intent
bonuses, owner/ally threat factors, an occupancy penalty, and jitter. Three rules sit beside the
score: hysteresis (`COORD_HYSTERESIS = 1.25`, a challenger must beat the standing mark by the
factor — skipped for threat picks), the spread rule (a merely-occupied best yields to the best
free candidate unless it is claimed by an ally's intent or threatening the pack), and the
autonomy band (`AUTONOMY_RADIUS = 50` assignable, `RETURN_RADIUS = 60` drops a self-placed mark so
the body walks home; manual marks exempt). `MegumiFailureMemory` makes a failed pounce cost the
body: weight `max(0.3, 1.0 − 0.6·count)` recovering linearly over a 100-tick window, re-stamped on
every wall-abort. Brains read `contextFor` for soft coordination — an elephant holds its jet off
a target an ally is already working.

## Partial manifestations (issue #108)

`MegumiPartialRuntime` is the server-authoritative state machine for Nue's wings and Toad's
tongue — one partial at a time, keyed by owner. The marker effect is the authority on both sides:
`JujutsuEffects.MEGUMI_NUE_WINGS` / `MEGUMI_TOAD_TONGUE` gate the glide grant and the grapple, the
state map re-syncs from the effect every tick, and every teardown path (deselect, respawn,
disconnect, dimension change, server stop, fixture reset, a shadow move) removes marker AND tells
the tongue's client to stop pulling.

**Wings** (`X` press toggles): the fall-flying flag's only setter is `startFallFlying()`, so the
upkeep re-asserts it while the marker lives — the player glides with zero fall damage, and the
partial ends on landing (D2) or when the marker lifts.

**Tongue** (`X` press anchors, release detaches): `TargetResolver` must name a BLOCK within
`TONGUE_RANGE = 10` or the cast refuses (`partial.no_anchor`). While anchored, the client pulls
the player toward the point (`TonguePullPolicy` + `TonguePhysicsMixin`, client-authoritative
movement with the server holding the anchor); the tongue lets go by itself when the anchor block
stops being solid or the line to it blocks (R31/R32). A repeated press while out is a no-op, and
the §19 exclusion refuses the partial while that type's full body is materialized — and refuses
the summon while the partial is out.

The roster card lists the partial row; HUD answers 0 for both partial slots (no cooldowns).

## Quick selector (G)

The strip is the second selection path beside `S+V` cycling: a hold of `key.jujutsumod.quick_selector`
(default G) opens a bottom-center strip of five slots — one per shikigami — without pausing the
world; releasing the key closes it. A tap under `TAP_MAX_TICKS` cycles instead, so the key covers
both gestures. While open, left-click on an available slot selects it immediately and the strip
stays open; a cooling slot rejects with a shake; hover never selects. A hold released without a
click restores nothing — the pre-open selection stands.

Client side lives under `jujutsu.mod.client.character.megumi.selector`: `JujutsuKeybinds` owns the
gesture state machine (tap vs hold, screen-open mid-hold cancels rather than phantom-cycling,
sub-tick taps re-arm via `clickCount`), `MegumiShikigamiSelectorScreen` renders the strip through
`SelectorMotion` (entrance/exit phases, per-slot snapshots so a mid-entrance close doesn't snap
trailing slots), `ShikigamiSelectorLayout` places it above the hotbar **and** the status rows,
and `ShikigamiSlotView` draws each slot's GeckoLib body through a picture-in-picture render state
that injects `PACKED_LIGHT` itself — the dispatcher mixin only covers the entity render path, not
`submitEntityRenderState`, so a naive PiP crashes with an NPE.

State arrives over the wire as a `MegumiShikigamiSync` snapshot (slot states + cooldown deadlines
rebased onto the client clock by `ClientMegumiShikigamiState.apply`); selection goes back as a C2S
`select` request that `MegumiDefinition.selectShikigami` validates (`MegumiShikigami.byId` rejects
unknown ids) and deduplicates — an unchanged selection never echoes a snapshot. `LOCKED`,
`DESTROYED` and `TEMPORARY` render but have no producer yet (accepted narrowing, see
`docs/KNOWN_ISSUES.md`). Dev-lane hooks: `/jujutsu_debug shikigami_selector` toggles the strip
without the key, and the mcpdev `jujutsu_input` tool injects tap/press/release/hold/click so the
gesture is scriptable end to end.

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
recall 240 / death 400. The tongue is the visual — the mechanic is the grab (issue #79): in reach
(12 blocks, inclusive) with owner line of sight, a 6-tick windup commits on the final action tick
(`actionTicks == 1`) and the hold starts. No damage, no yank: the value is control. The victim hangs
1.2 blocks in front of the body at feet level (`MegumiToadPolicy.anchor`), re-pinned every tick through
the shared `HoldSupport` — server teleport plus the invisible `GRIPPED` marker on a 10-tick refresh.
That marker is also the client half: `HoldInputMixin` blanks a held player's movement input (attacks,
items, inventory, hotbar stay), so the client never rubber-bands against the pin. Mobs additionally get
navigation stop plus heavy Slowness, but never `NoAI` — a held mob keeps aiming and attacking. Hold length
scales with victim toughness: base 90 ticks minus 0.20 per max-health point (and, for non-players, 6.0 per
hitbox-volume block), clamped 60–100; then the victim is thrown away from the owner (1.6 + 0.35 up,
an 8-tick stagger applied first so the launch reads as a hit) and released. The bind snaps early if the
body is dragged past 16 blocks from its owner, and while a hold lasts body and victim neither damage each
other. Target priority: the owner's sic mark first, else the body's own nearest eligible pick rescanned
every 20 ticks — a self-pick never overwrites an order. Who may be grabbed is data
(`jujutsumod:ungrabbable` — greater cursed spirit, dragon, wither, warden, golem, elder guardian — read
through `CombatTags`). The plain `MeleeAttackGoal` bite (4 attack) stays for everything the grab is not for.
Pure policy: `canGrab` / `holdTicksFor` / `anchor` / `throwVelocity` / `bindBroken` / `strikeTickReached`.
Clips `howl`/`walk`/`attack`/`tongue`; tongue is a VFX-only strike (no tongue geometry upstream — accepted limit). Sounds: `FROG_LONG_JUMP` +
`FROG_AMBIENT` on emergence, `FROG_TONGUE` windup + `FROG_EAT` on the commit, `FROG_LONG_JUMP` on the throw, `FROG_AMBIENT` idle.

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
cooldown. The jet never fires blank: past corridor reach no jet starts and no cooldown burns
(`jetTriggerInReach`). Friendly-fire bodies in the corridor take nothing.
Presence (issue #79): the walking body is pressure. Every 10 ticks, everything living inside 3.5 blocks
that is not on the owner's side is shoved away (0.7 velocity impulse + 1.1 lift — an impulse, not
`knockback()`, so knockback resistance does not eat it), and whatever `MegumiHostilityPolicy` calls
hostile takes 1.0 owner-attributed damage on top, with `invulnerableTime` zeroed so every pulse lands.
Hostile means any of: `Enemy` archetype, currently targeting the owner, or a fresh aggressor (100-tick
window). Allies are pushed, never hurt; the body never touches itself.
Footprint (issue #79): while walking, the feet crush litter. Every 10 ticks the body samples its real
displacement since the last sample (`sampleFootprintStep` — position delta over elapsed ticks, never the
velocity field) and, above 0.05 blocks/tick horizontal, destroys up to 4 blocks under and just ahead of
its feet without drops. What counts as litter is an allowlist (`jujutsumod:destructible_by_shikigami`:
dirt-family, sand, gravel, glass, torches, leaves, planks, fences, flowers, crops — chests, ores and
anything functional are not members). A standing elephant sweeps nothing.
Clips `idle`/`walk`/`run`/`attack`/`shoot` (the authored `loop:true` on `shoot` does not govern `RawAnimation` playback — the action
controller holds it one-shot while `actionTicks > 0`, dog precedent). Sounds: quiet
`RAVAGER_ROAR` summon, `RAVAGER_ATTACK` windup + `GENERIC_SPLASH` per pulse, `RAVAGER_AMBIENT`
idle. The off-origin `body` pivot is upstream data; only renderer scale/offset may compensate.

## Lifecycle and cleanup

`MegumiShikigamiRuntime` keeps one owner-keyed `MegumiShikigamiPack` **per type** (type,
dimension, anchor id, body ids, summon token, summon game time) plus the `TEARDOWN_IN_PROGRESS`
guard — coexistence means the map is keyed (owner, type), and `teardownType` sweeps exactly one
type's pack so recalling Nue never touches the Toad. `teardown` is the single destructive entry
point: drop the record, cross-level sweep of owned bodies, `beginRecall` for the RECALL family
and `discard` otherwise, then the reason's cooldown via `startCooldownIfLonger` (a longer active
deadline is never shortened). Summon cooldowns are per-(owner, type) deadlines in
`MegumiSummonCooldowns` — a recalled Nue cannot lock the dogs.

| Trigger | Reason | Bodies | PRIMARY cooldown |
|---|---|---|---|
| `R` on the active selected type | `RECALL` | sink-out | recall row |
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

JUnit plus architecture checks cover selection order/defaults, the summon/recall decision table,
teardown-reason → cooldown mapping, presentation transitions and combat gates,
spawn-placement offset math, friendly-fire membership, per-type pure policies (dive velocity,
impact predicate, soaked escalation; grab reach/hold/anchor/throw/bind-break/strike; rabbit respawn/expiry/bump;
elephant corridor/presence/footprint/hostility), the coordination policy (band action, score
weights, hysteresis, spread pick, failure-memory decay), the tongue pull law, resource contracts
(geo identifiers, clip keys, one-shot attack/tongue,
texture paths, `geckolib_format_version`), router-arm and roster/HUD pins, and the lang key sets.

GameTests (`MegumiShikigamiGameTests`, `MegumiToadGameTests`, `MegumiRabbitsGameTests`,
`MegumiElephantGameTests`, `MegumiShikigamiCrossTests`, `MegumiCoexistenceGameTests`,
`MegumiAutonomyGameTests`, `MegumiPartialGameTests`, `MegumiWingsGameTests`,
`MegumiTongueGameTests`) cover summon → pack shape, recall and
death cooldowns per type, additive coexistence (two types out at once, per-type recall,
per-type summon deadlines), the global sic (both families marked, empty aim clears orders),
autonomous marking and the failure-memory retry gate, sic-driven abilities end to end (dive damage +
slow, toad hold without damage + throw, bump knockback + slow, jet damage + soak), the swarm upkeep/expiry/anchor
rules, friendly fire inside the jet corridor, the partial state machine (wings toggle + zero fall
damage + landing end, tongue anchor/pull/release/refusals), and the cross-type guarantees
(fixture-reset teardown with the selection back to DOGS, deselect teardown with the selection kept).

Oracle trap, measured in game 2026-09-11: a `NoAI:1b` mob is FULLY FROZEN — external velocity is stored but the position never integrates, not even gravity. Displacement-based oracles ("distance decreased", knockback travel) must use an AI mob with zeroed speed (Slowness amplifier 100) or assert velocity/effect state instead (`getDeltaMovement`, effects). The hold/throw scenarios pin the grip through the anchor position and the throw through the velocity vector for exactly this reason.
+
Cursed Saga (issue #79) extends both sides: unit pins for hold length, anchor, throw, bind-break, presence, footprint and hostility, plus in-game Toad-hold and Elephant-pressure/footprint scenarios beside the sic-driven ability cases.

No automated test boots the client, renders a frame, or plays audio. Summon-geometry feel on
floors, ledges, water, and tight rooms; flight/pull/knockback feel; kill attribution; spatial
mix and duplication of the vanilla placeholder sounds; HUD placement; remote synchronization;
and the look of each model against its hitbox (Nue wingspan ≈ 1.6 blocks, Toad ≈ 1.2 tall,
Elephant pivot compensation) remain the in-game verification pass in
`.superpowers/rule-of-four/megumi-shikigami/progress.md`.
