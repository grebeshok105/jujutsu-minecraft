# Todo (Aoi Todo) — Boogie Woogie and Combat Slice

Status: CURRENT

Todo's playable slice: the Boogie Woogie swap on the shared PRIMARY slot, a feint clap on PRIMARY_SNEAK, a pair swap on SECONDARY, the triple cycle on SECONDARY_SNEAK, the thrown stone on TERTIARY with its target swap on TERTIARY_SNEAK, passive attribute modifiers, and a Black Flash bridge on vanilla melee. All claims VERIFIED against `src/main/java/jujutsu/mod/character/todo/**` unless labelled otherwise.

## Tuning constants

Every number lives in `TodoProfile`. Nothing else should hold a Todo magic number.

| Constant | Value | Applied by |
|---|---:|---|
| `MELEE_DAMAGE_MULTIPLIER` | 1.50 | `TodoDefinition.applyAttributes` — `Attributes.ATTACK_DAMAGE` modifier `todo/melee_damage`, added as `value - 1.0` |
| `ATTACK_SPEED_MULTIPLIER` | 0.85 | `TodoDefinition.applyAttributes` — `Attributes.ATTACK_SPEED` modifier `todo/attack_speed` |
| `STAGGER_DURATION_MULTIPLIER` | 0.50 | `TodoDefinition.adjustIncomingStaggerTicks` — incoming stagger, `max(1, ceil(requested * 0.50))`, never inventing a stagger from a request of zero |
| `BOOGIE_WOOGIE_RANGE` | 20.0 | `TodoBoogieWoogieRuntime` target resolve and a re-check on squared distance |
| `BOOGIE_WOOGIE_COOLDOWN_TICKS` | 60 | three seconds at 20 TPS |
| `SAFE_POSITION_HORIZONTAL_RADIUS` | 1.0 | horizontal nudge ring |
| `SAFE_POSITION_UPWARD_BLOCKS` | 3 | upward nudge ceiling |
| `WORLD_BORDER_MARGIN` | 0.05 | destination box inflation before the border test |
| `BLACK_FLASH_CHANCE` | 0.10 | `TodoBlackFlashRuntime` |
| `BLACK_FLASH_DAMAGE_MULTIPLIER` | 1.75 | bonus is `baseDamageTaken * (multiplier - 1.0)` |
| `BLACK_FLASH_STAGGER_TICKS` | 14 | `CombatStagger.GLOBAL` |
| `PAIR_SWAP_COOLDOWN_TICKS` | 100 | second pair cast, `SECONDARY` slot |
| `PAIR_SELECTION_TTL_TICKS` | 100 | pending selection lifetime |
| `PAIR_MARK_PULSE_TICKS` | 20 | server re-emit period of the selection cue |
| `TRIPLE_SWAP_COOLDOWN_TICKS` | 160 | triple cycle, `SECONDARY_SNEAK` slot |
| `STONE_SPEED_BLOCKS_PER_TICK` | 0.175 | straight-line stone velocity (3.5 blocks/s) |
| `STONE_LIFETIME_TICKS` | 100 | stone flight clock |
| `STONE_HITBOX_SIZE` | 0.35 | stone entity bbox |
| `STONE_THROW_COOLDOWN_TICKS` | 10 | anti-double-click on the throw |
| `STONE_SELF_SWAP_COOLDOWN_TICKS` | 60 | V with a live stone |
| `STONE_TARGET_SWAP_COOLDOWN_TICKS` | 100 | Shift+V |
| `STONE_SWAP_RANGE` | 32.0 | max Todo↔stone distance for either stone swap |
| `STONE_TARGET_RANGE` | 20.0 | Shift+V crosshair reach |

## Entry gate

`CharacterAbilityExecutor.tryCast` handles the not-selected and cooldown rejections, then asks the selected vessel's definition — `TodoDefinition.tryCast` delegates to `TodoAbilityRouter.tryCast`, not to a runtime directly. The router's switch over `CharacterAbility` is exhaustive on purpose: a future slot constant fails compilation there instead of silently falling into the swap, which is exactly what happened while the executor called `TodoBoogieWoogieRuntime` for every slot. `PRIMARY` → `TodoBoogieWoogieRuntime`, `PRIMARY_SNEAK` → `TodoFakeClapRuntime`, `SECONDARY` → `TodoPairSwapRuntime` (pair), `SECONDARY_SNEAK` → the triple cycle in the same runtime, `TERTIARY` and `TERTIARY_SNEAK` → `TodoStoneRuntime`. The `canonicalSlot` fold that used to collapse `SECONDARY_SNEAK` onto `SECONDARY` is deleted with the whole hook — Shift+B is its own technique now, and no vessel folds slots anymore. `USE_CONTEXT` is answered by nobody and returns `false`.

The gate checks themselves no longer live in either runtime; they live in `TodoSwapGates`. `evaluate(spectator, alive, unsafeTransport, staggered, handsEmpty)` is pure policy and returns one of three answers:

| Answer | Condition | Player feedback |
|---|---|---|
| `UNAVAILABLE` | spectator, not alive, `TodoTargetSafety.hasUnsafeTransportState(passenger, vehicle, false)`, or `CombatStagger.GLOBAL` | none — caster state is not worth an actionbar line |
| `HANDS_FULL` | all of the above clear, but a stack is in main or off hand | `todo.boogie.hands_full` |
| `ALLOWED` | everything clear | — |

State outranks hands: a spectator holding a sword gets `UNAVAILABLE`, so a refusal never leaks what the hands held for a cast that could not have happened anyway. The empty-hands gate is still authoritative and early — nothing is touched before it, so a rejected cast has no partial effects.

Coverage of the gate is pure logic: `TodoFakeClapTest` walks the truth table directly, `TodoTargetSafetyTest` covers the transport half, and `TodoHandsEmptyTest` now asserts that *neither* runtime re-checks hands locally (`getMainHandItem` / `getOffhandItem` must not appear in either file) and that both call `TodoSwapGates.evaluate(todo)`.

Target eligibility (`isEligibleTarget`): not self, alive, not spectator, not removed, no unsafe transport state (including `Leashable.isLeashed`), not an `ArmorStand`, same level, finite position. The aimed target from `TargetResolver` is then re-fetched by id and re-validated, plus `hasLineOfSight` and a range re-check — the resolve result is never trusted on its own.

## Atomic two-sided preflight

`TodoSwapPlan` is an immutable record of both destinations. `TodoSwapPlan.preflight(todoDestination, targetDestination)` returns `Optional.empty()` if **either** destination is null. That is the whole atomicity rule: `findSafeDestination` returns null on failure, so one unusable side aborts the swap before anything moves. `TodoSwapPlanTest` covers it.

Both snapshots are captured before preflight, and a cross-level pair is rejected. Between preflight and commit there is a final liveness re-check (removed / alive / level identity), because preflight is not free of intervening ticks in principle.

## Destination-safety policy — what the code actually does

Read this from `TodoBoogieWoogieRuntime.findSafeDestination` and `isPlaceableDestination`, not from any older description.

The policy is deliberately **free-form**: air, water, crawl space, and mid-flight are all valid destinations. Exactly four things are checked:

1. finite coordinates
2. `level.isInWorldBounds(BlockPos)`
3. the destination chunk is loaded (`getChunkSource().hasChunk`)
4. `level.getWorldBorder().isWithinBounds(box.inflate(0.05))` and `level.noBlockCollision(entity, box)`

There is **no floor check** and **no third-party entity-occupancy gate**. A swap can legally drop either participant into open air, and a destination occupied by an unrelated entity is not rejected. This is intentional for the technique's fantasy; do not "fix" it without a product decision, and do not document a floor requirement that does not exist.

The search order is `up` 0..3 outer, then a 13-entry horizontal offset ring (origin, ±0.5 axis, ±1.0 axis, ±0.7 diagonals) inner — so the exact requested point wins when it is usable.

`findSafeDestination` takes a `Strictness`, and every caller states it — the defaulting overload that used to supply `SOFT` is deleted, because it applied the fallback to bodies nobody had chosen to expose to it.

Under `SOFT` a last-resort fallback accepts the exact requested point when it passes `isInWorldDestination` — finite coordinates, world bounds, chunk loaded, and the world border, which moved into that test so both paths enforce it. Block collision is skipped on that path, so the fallback can place a body clipping geometry, which vanilla resolves by pushing it out. **Exactly one destination in the kit uses it: Todo's own arrival in the aimed swap.**

`STRICT` has no fallback: if no candidate passes, the destination is null and the whole cast cancels through the preflight. It covers every body that is not Todo — the aimed swap's target, both pair-swap participants, and both forms of the thrown-mark swap. Note what it is not: `STRICT` imposes no floor and no occupancy gate, so air, water and crawl destinations stay legal for a third party. It refuses one thing, a point inside geometry, measured with the body's own posed bounding box.

The aimed swap's target used to take `SOFT` along with Todo, through the defaulting overload. That was the one place where a body which did not ask to be moved could be forced into a wall, and it was decided in favour of the safety principle rather than the shipped behaviour: Todo keeps his fallback, everyone else is placed only where collision passed, and a cast that cannot do that fails instead.

An earlier in-source comment justified the fallback by claiming `noBlockCollision` is picky about the swap partner's old volume. That is false — `Level.noBlockCollision(Entity, AABB)` tests block shapes only and never consults entities. The dead `otherSwapParticipant` parameter that comment referred to has been deleted.

## Commit, and the rollback that can fail

Placement is sequential and short-circuited: Todo first, target only if Todo succeeded (`teleportTo` with an empty `Relative` set and the snapshot's yaw/pitch).

If either placement fails, both entities are restored from their snapshots. `restore` returns false when the entity has changed level, or when its own `teleportTo` fails. When either restore returns false the runtime logs at **error** level with both flags:

```
Todo Boogie Woogie rollback incomplete player=… target=… todoRestored=… targetRestored=…
```

This is the honest part of the design and worth understanding: the rollback is best-effort, not transactional. A logged incomplete restore means a participant is somewhere neither the plan nor the snapshot describes. The cast still reports failure to the player (`todo.boogie.unsafe`) either way. If that line ever appears in a real log it is a bug report, not noise.

On success both participants get `restoreMotionAndRotation` (forced rotation, head yaw, delta movement, `resetFallDistance`) — momentum and facing survive the swap.

## Cooldown and feedback

`CharacterAbilityCooldowns.start` plus `JujutsuNetworking.sendAbilityCooldown` for the `PRIMARY` slot cooldown. Then two kinds of cue, because one cue cannot carry two absolute world points: `TodoVfxIds.BOOGIE_WOOGIE` is the performance, anchored to the caster with a zero offset, and one `TodoVfxIds.SWAP_ENDPOINT` per moved body carries an absolute endpoint with no anchor, each broadcast around its own point so far-side observers receive it.

The clap half of that is now `TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, origin, aim)` — the sound plus the caster-anchored cue, with nothing about the swap in it. The endpoint cues and the movement sounds stay in `emitSwapFeedback`, because only a real swap has endpoints. The feint calls `emitClapPerformance` and nothing else.

That split fixed a real defect. `VfxAnchorResolver` already adds the cue's anchor offset, the recipe added it again, and the cue is broadcast after the teleport — so the two flashes landed at `todoPos + delta` and `todoPos + 2*delta`, drifting with packet order. The ribbon was never affected because `VfxWorldChannel` treats this style as world-fixed and reads `cue.origin()` directly.

The first-person clap is gated on the local anchor. A recipe runs on every client that receives the cue, so before the gate every nearby player's own arms clapped.

Sound is server-authoritative: `JujutsuSounds.PROJECTJJK_CLAP` plays with the swap, and a movement sound follows one tick later at both original positions through the static pending-sound queue drained by `TodoBoogieWoogieRuntime.register()`'s END_WORLD_TICK listener, which now clears on `SERVER_STOPPING`. An earlier revision of this note claimed clients timed the clap from `TodoVfxRecipes`; that was never true — no sound call existed there.

## The feint clap — `Shift+R`, the PRIMARY_SNEAK slot

A complete Boogie Woogie clap that moves nobody, so the next real one is a coin the opponent has to call. VERIFIED against `TodoFakeClapRuntime`, `TodoSwapGates`, `TodoBoogieWoogieRuntime.emitClapPerformance`, `TodoVfxRecipes`, and `TodoFakeClapTest` unless labelled otherwise.

The server knows the cast is hollow from the first tick: `TodoFakeClapRuntime` never starts a swap and then cancels it, so no target is resolved, no destination is planned, and no body can be left half-moved. `TodoFakeClapTest` asserts the file mentions none of `teleportTo`, `TodoSwapPlan`, `findSafeDestination`, `TargetResolver`, or `SWAP_ENDPOINT`, so the teleport machinery cannot creep in later.

Input is the existing technique key with `Shift` held — `JujutsuKeybinds` reads `client.player.isShiftKeyDown()` in its one `slot(...)` helper and picks `PRIMARY_SNEAK` instead of `PRIMARY`. No hold threshold and no double tap on the technique key: the real swap has to stay instant, and both casts have to be typeable equally fast. (The *second* technique key is different since the Megumi shadow kit: its sneaking press buffers for a six-tick hold window, so Todo's Shift+B pair-swap tap now confirms on release — see [Megumi shadow kit](Megumi-shadow-kit.md).)

### The indistinguishability contract

Four things make the two casts alike by construction rather than by tuning.

1. **One shared performance.** Both emit the clap through `TodoBoogieWoogieRuntime.emitClapPerformance` — same cue id `todo/boogie_woogie`, same caster anchor with a zero offset, same server-side `JujutsuSounds.PROJECTJJK_CLAP` at the same volume and pitch, on the same tick. One implementation, so the two presentations cannot drift apart in a later edit. `TodoFakeClapTest` asserts the feint does not name `TodoVfxIds.BOOGIE_WOOGIE` itself, only the shared method.
2. **One shared gate truth table.** Both read `TodoSwapGates.evaluate`, so the set of casts that get refused — and the message each refusal produces — is identical. A feint that were allowed with a sword in hand would announce itself.
3. **Independent cooldown slots.** `CharacterAbilityCooldowns` keys on (player, vessel, slot). The feint starts and reports `PRIMARY_SNEAK` with `TodoProfile.FAKE_CLAP_COOLDOWN_TICKS` and never names `PRIMARY`; the swap keeps `PRIMARY` with `BOOGIE_WOOGIE_COOLDOWN_TICKS`. A feint therefore neither spends nor postpones the real swap. There is deliberately **no** gate requiring the real swap to be ready — that was offered and declined, because a feint that only works while the swap is off cooldown is itself a tell.
4. **Caster-only tell.** The single unshared packet is `TodoVfxIds.FEINT_TELL`, sent through `JujutsuNetworking.sendVfxCue(todo, …)` to one player and never broadcast (`TodoFakeClapTest` asserts `broadcastVfxCue` does not appear in the file). Its recipe is a six-tick dust ring at chest height and nothing else — no sound, no HUD flash, no camera kick, since every one of those would be perceivable by the observer the feint exists to deceive.

The one field that does differ carries no information: the feint puts `todo.getLookAngle()` where the swap puts the normalized caster-to-target delta, and those point the same way. INFERRED that this keeps a future recipe honest — no recipe reads that field today.

The feint's slot is `PRIMARY_SNEAK(1)`. Network ids are wire format and are never renumbered; new slots append. `TodoFakeClapTest` pins all five — `PRIMARY == 0`, `PRIMARY_SNEAK == 1`, `SECONDARY == 2`, `SECONDARY_SNEAK == 3`, `ATTACK_CONTEXT == 4` — round-trips every slot through `byNetworkId`, and asserts `byNetworkId(99) == null`.

### What is still distinguishable — the open product question

The real swap teleports both bodies at cast time, but the clap's palm contact is at `VfxFirstPersonChannel.CLAP_CONTACT_PROGRESS` = 0.39 of the 0.72 s `ability.boogie_woogie` animation. An observer therefore sees a real swap **before** the palms meet, which means a feint is already distinguishable at t = 0 by the absence of a teleport — and, in the same instant, by the absence of the two `todo/swap_endpoint` bursts and of the movement sounds that follow one tick later at both origins.

Delaying the swap to the contact frame was **not** done. It would change `TodoBoogieWoogieRuntime`'s commit path — the most safety-critical method in the kit, the one that owns the two-sided preflight and the best-effort rollback — and that is not in the approved plan. Decide it deliberately, not as a side effect of a VFX pass.

### The bigger tell is the input, not the timing

The modifier is the sneak key, so `Shift+R` casts while crouched and the pose is synchronized to every observer: standing means real, crouched means feint, readable without any of the cue work above. A sneaking Todo also cannot cast the real swap at all.

This is an accepted product decision, not an oversight — docs/KNOWN_ISSUES.md owns the rationale and the reopen conditions under "The feint clap's input scheme leaks the caster's pose". Note what it means for anyone tuning this: the presentation is already indistinguishable, so tightening cues, sounds or timings further buys nothing while the pose tell stands. Work on the input scheme or on the teleport timing instead.

### Coverage — the honest limit

Nothing in the test suite can construct a `ServerLevel`, so no test ever calls `TodoFakeClapRuntime.tryCast`. The feint is covered by pure gate logic (the `TodoSwapGates.evaluate` truth table) plus source-text contract assertions in `TodoFakeClapTest` and `TodoHandsEmptyTest`: that the shared performance method exists and is the one the feint calls, that the tell is a single-player send, that no swap machinery is present, that the cooldown slots are separate, and that the router switch carries no `default`. Whether the two casts actually read as the same event to a second player is UNKNOWN — it has not been verified in game. See E1 in docs/KNOWN_ISSUES.md.

## The pair swap — `B`, the SECONDARY slot

`TodoPairSwapRuntime`. Todo claps and two other bodies trade places; he does not move. Two casts on one key: the first marks a participant, the second resolves the pair and commits. `Shift+B` no longer reaches it: since the stone rework `SECONDARY_SNEAK` is the triple cycle's own slot (below), and a sneaking press during a lined-up pair is exactly how the cycle is entered — the selection survives the modifier.

What each cast costs, and what it does not:

| Cast | Effect | Cooldown |
|---|---|---|
| First, on an eligible body | marks it, caster-only cue + actionbar naming it | none — lining a swap up is free |
| Second, on a different eligible body | commits the swap | `SECONDARY` slot, `PAIR_SWAP_COOLDOWN_TICKS` |
| Second, back at the mark | deliberate cancel, mark dropped | none |
| Second, at nothing | refused, **mark survives** | none — a missed click must not cost a two-cast setup |

Distance is measured from Todo to each participant and **never between the two of them**. A 40-block spread between the pair is the whole value of the technique; the javadoc says so explicitly so nobody "fixes" it into a pair-distance limit. Both participants must also be in reach, visible, and pass the same `isEligibleTarget` policy as a direct swap target — a bystander is never held to a laxer standard than a body Todo aims at.

`TodoPendingSelection` stores the dimension, the network id **and** the UUID. The id is what `TargetResolver` returns and what the level can look up; the UUID is what proves the entity found under that id is the same one and not a recycled slot. The record now lives in `TodoTransientState` — the single owner of Todo's transient server state — and is dropped on expiry, on the marked body dying, and on every lifecycle exit that `TodoStateLifecycle` registers: `DISCONNECT`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, `SERVER_STOPPING`, death, and vessel change (`TodoDefinition.onDeselected` → `dropEverything`). The expiry sweep deliberately does **not** read an unresolvable entity as dead — an unloaded chunk is not a death.

Placement is `STRICT`, which finally gives that enum a call site. The scan behind both strictness values now lives in shared `jujutsu.mod.combat.SafeBodyPlacement` (extracted when Megumi's shadow move became its second consumer); the wrapper, the `Strictness` vocabulary and every call site stay in `TodoBoogieWoogieRuntime`, and the candidate order is unchanged. Everything else is the self swap's machinery unchanged: the same `TodoSwapPlan.preflight` atomicity rule, the same sequential placement, the same best-effort rollback with an error-level incomplete-restore log. `TodoSwapPlan`'s components are named `firstDestination` / `secondDestination` precisely because the record now covers both shapes.

The commit reuses `BOOGIE_WOOGIE` plus two `SWAP_ENDPOINT` cues, so at a distance a pair swap and an ordinary one look alike — free deception, and one less presentation to keep in step. The mark itself is a caster-only `PAIR_MARK` cue plus the actionbar line — and since the stone rework the server re-emits that cue every `PAIR_MARK_PULSE_TICKS` while the selection lives (a silent intensity-0 pulse, the Megumi trap-boundary pattern), so the chosen body stays readable without violating VFX Core's one-shot recipe rule. The pulse also feeds the client-side pair chip through the recipe's fail-open cache — the HUD learns of the selection from the cue itself, never from a payload.

## The triple cycle — `Shift+B`, the SECONDARY_SNEAK slot

With a live selection A and a second eligible body T under the crosshair, `Shift+B` runs the
three-body cyclic swap. The direction is fixed and pinned by `TodoTripleSwapTest`:

| Body | Goes to |
|---|---|
| Todo | A's position |
| A | T's position |
| T | Todo's position |

`Shift+B` with no selection refuses (`triple.no_first`) rather than degrading into `B` — the old
fold is exactly the bug this slot exists to not have. T must differ from A and from Todo, and both
bodies pass the same eligibility policy as every other swap participant.

All three bodies preflight `STRICT` at their destinations through `TodoTripleSwapPlan`
(the three-destination sibling of `TodoSwapPlan`, same null-aborts rule) before anything moves;
one unusable destination cancels the cast, moves nobody, and keeps the selection. The commit is
snapshot ×3 → place ×3 → restore ×3 under the accepted body policy; a mid-commit teleport failure
rolls back every already-moved body to its snapshot in reverse order and logs at error level —
a partial cycle without a log line is the one outcome the design forbids. Success consumes the
selection and prices the `SECONDARY_SNEAK` slot at `TRIPLE_SWAP_COOLDOWN_TICKS`, split from the
pair's price on purpose. The cycle grants **no** swap momentum: that window rewards swaps Todo
makes with his own body, and here his movement is bought by moving two other people.

Presentation: one `TRIPLE_SWAP` cue per cycle edge (three per cast), each carrying the edge's
direction and length so the A→B→C→A flow reads in-world, plus the ordinary per-body
afterimage/arrival pair. There is deliberately **no** clap in this cast: the three edges are the
triple's own language, and `emitTripleFeedback` emits nothing else — a clap would make the cycle
read as one more pair swap at a distance.

## The sixth slot is empty

`CharacterAbility.USE_CONTEXT(5)` keeps its wire id and its client-side pair detector (two right
clicks within six ticks, the first click deliberately vanilla's), but since the stone rework no
vessel answers it — every router returns `false`. The slot stays reserved wire format: ids are
append-only, and the input grammar survives so a future technique can claim it without touching
shared code. The entity-mark runtime that used to live here is deleted with the marker system.

## The stone — `V` and `Shift+V`, the TERTIARY slots

`TodoStoneEntity`, `TodoStoneRuntime`, `TodoTransientState` (the ref), `TodoStoneRef`.

One small inert stone that exists only in flight. `V` with no live stone throws it from the eye
position along the look vector: a straight, slow, readable line — `STONE_SPEED_BLOCKS_PER_TICK`,
no gravity, no arc, `noSave()`, one per Todo. It deals no damage, marks nothing, ignores entities
entirely, passes through water and fire, and ends on `STONE_LIFETIME_TICKS`, on block collision,
on the void, and on every lifecycle exit — a collision is a vanish (`STONE_VANISH`), never an
anchor. The old marker's "landed anchor" concept is deliberately dead: what the stone buys is a
five-second moving window, not a camp spot.

`V` with a live stone is the **self-swap**: Todo trades places with the stone at the stone's
*current* position, `STRICT` preflight for his body, nothing moves on refusal. The stone appears
at Todo's old center, keeps its own velocity and its remaining clock, and keeps flying. This is a
completed swap Todo made with his own body, so it grants the momentum window and wears the real
swap's cues — `BOOGIE_WOOGIE` clap plus afterimage/arrival at both ends.

Both stone casts read the caster-state half of the clap gate (`TodoSwapGates.casterStateBlocked`):
a spectator, dead, mounted/riding or staggered Todo is refused silently, exactly like every other
`UNAVAILABLE`. Hands deliberately stay ungated — the stone is an ability cast, not an item use, so
the empty-hands rule never applies to it.

`Shift+V` (`TERTIARY_SNEAK(9)`, the rework's one appended wire id) is the **target swap**: the
aimed body within `STONE_TARGET_RANGE` trades places with the stone while Todo stays put. The
target passes the aimed swap's full eligibility family and lands under `STRICT` — no safe point
at the stone means nobody moves and the stone keeps flying. No momentum: Todo did not move.
Both stone swaps also require the stone within `STONE_SWAP_RANGE` of Todo and in his dimension.

The ref (`TodoStoneRef`) resolves the entity by UUID inside its recorded dimension only — the
entity id travels in cues, never resolves anything. A stone missing from a *loaded* chunk clears
the ref through the sweep; an unloaded chunk is not a death, the same rule the pair selection
uses. The throw's cooldown is deliberately tiny (anti-double-click): the price of the kit sits on
the two swaps, not on the throw, so throwing never locks the follow-up.

## The impact sequence — and why it lives on its own cues

`TodoBoogieWoogieRuntime.emitSwapImpact` is the single emission point for every completed swap route: the aimed swap, the pair swap, the stone self-swap, the stone target swap, and each moved body of the triple cycle. They used to hand-copy the same five calls, which is a shape that drifts, and the copy that drifts is the one nobody plays often enough to notice.

**The feint sends the same `BOOGIE_WOOGIE` cue as a real swap** (`TodoFakeClapRuntime` → `emitClapPerformance`). That single fact decides the whole layout: anything added to the clap recipe is something a feint does too. So the clap keeps only what a feint must also have — the camera snap, the HUD flash, the animation — and everything a completed swap earns rides on cues the feint never emits.

| Beat | Carrier | Notes |
|---|---|---|
| clap sound, camera snap for every observer, HUD flash | `BOOGIE_WOOGIE` | shared with the feint on purpose |
| ribbon between the two ends of the geometry | `SWAP_ENDPOINT` (leading only) | the trailing cue no longer claims a flash slot to draw nothing |
| burst and body silhouette where each body stood | `SWAP_AFTERIMAGE`, one per moved body | 4 ticks |
| inward gather, landing column, velocity streak; camera snap for the participant; sound duck | `SWAP_ARRIVAL`, one per moved body | 6 ticks |
| displacement whoosh at both ends (+1 tick), low landing report at the arrival midpoint (+3) | server sound scheduler | one report, not two — Minecraft audio has no propagation delay |

The two new cues carry data the renderer cannot get any other way. `SWAP_AFTERIMAGE` takes `(bbWidth, bbHeight, yaw)` in its `anchorOffset` because by render time the live entity is standing somewhere else in a different pose; `SWAP_ARRIVAL` takes `(speed, bbWidth, bbHeight)` because `VfxCue` normalizes `direction`, so magnitude cannot survive there — one vector, two useful forms. Both keep `NO_ANCHOR`, which is what lets `VfxContext.resolveOrigin` stay honest about what it returns.

The silhouette is an outline, not a fill, and that is physics rather than taste: `RenderType.lightning()` blends additively, so a filled body at any readable alpha blows out toward white. The only solid piece is a torso wash at a sixth of the outline's alpha. It holds four ticks because it stands exactly where the *other* body is arriving — longer and it stops reading as "you see who left" and starts reading as a rendering bug.

`ImpactStyle` now carries `worldFixed` on the enum constructor rather than in a boolean expression at the call site. An afterimage that followed its anchor entity would chase the body that left, which is the one thing it must never do, and the compiler now refuses to let a new style skip the question.

**Sound duck.** `VfxSoundChannel` pauses every category except `PLAYERS` and `UI` for six ticks through the vanilla `SoundManager.pauseAllExcept` / `resume` pair — no mixin, and nothing written to the player's own volume settings. Because that switch is shared with vanilla's pause menu, the channel tracks who owns the pause (`VfxSoundDuck.State`) and lifts only its own; opening a screen ends the duck on the same tick, a second duck extends rather than restarts, and every existing teardown path already reaches `clear()`. VFX Core has no client-global slow-motion channel, so no slow-motion is involved.

**Momentum.** `place` teleports absolutely with an empty `Relative` set, so the transition carries `Vec3.ZERO` and the client is told its velocity is nothing — the server-side `setDeltaMovement` was a fiction for a player, who owns his own movement. `restoreMotionAndRotation` now sets `hurtMarked`, which makes `ServerEntity#sendChanges` emit `ClientboundSetEntityMotionPacket` through `broadcastAndSend`, reaching the trackers and the moved player's own connection. One line inside the shared helper covers every route and the rollback path.

## Swap momentum — one heavier hit

`TodoSwapMomentum` (pure policy), `TodoSwapMomentumRuntime`, `JujutsuEffects.TODO_SWAP_MOMENTUM`.

A completed swap opens a 24-tick window; the next confirmed melee hit lands harder, staggers for `SWAP_MOMENTUM_STAGGER_TICKS`, and gets its own `MOMENTUM_STRIKE` cue. A miss, a shielded hit or a hit that dealt nothing leave it untouched; it lapses on its own.

The damage rides on the effect's own `ATTACK_DAMAGE` modifier, not on a second `hurtServer` call. That is what makes it impossible to double-apply — the vanilla swing is simply bigger, so there is no extra damage instance to fight with Black Flash's bonus, pierce invulnerability, or fire the damage event twice. `ADD_MULTIPLIED_TOTAL` composes with Todo's standing `+0.50` as `base × 1.50 × 1.25`; `ADD_MULTIPLIED_BASE` would silently have meant `×1.75`.

Three non-obvious constraints the runtime exists to satisfy:

- **`AFTER_DAMAGE` does not fire on a killing blow.** A kill would have silently refunded the window and shown nothing, so `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` is a second entry into one spend path (no stagger — there is nobody left to interrupt).
- **Black Flash re-enters the damage event** to apply its bonus, so the listener sees one swing twice. Without the `BlackFlashStrike.isApplyingBonus` guard the window is spent on the nested pass, tying the stagger and the cue to a hidden ten-percent roll. The stagger is additionally guarded on `isAlive()`, because that bonus hit can kill the target inside the same swing.
- **The spend path checks the attacker is still Todo**, so leaving the vessel mid-window would strand a live `+25%` modifier nothing could remove. `onDeselected` takes it off; the attribute sweeps do not reach it, because it belongs to the effect rather than to the definition.

Grant sites are exactly two: past the last `return false` in `TodoBoogieWoogieRuntime.tryCast`, and in `TodoStoneRuntime.selfSwap` — the two swaps Todo makes with his own body. **Not** the pair swap or the triple cycle — Todo takes no positional risk in either — **not** the stone's target swap (`Shift+V`), where he also stays put, and **not** the feint, whose 20-tick cooldown would make it a threefold-cheaper way to buy the window.

Two limits recorded rather than hidden, both in the runtime's javadoc:

1. A **sweeping attack** keeps the boosted damage on its later victims after the window is spent on the first: `Player.attack` captures the damage into a local before the sweep block, so removing the modifier mid-swing cannot shrink a float already on the stack. Stagger and cue do not duplicate. It costs a deliberate hotbar swap, since both hands must be empty to clap.
2. On **bare fists** `×1.25` is worth about a third of a heart. The stagger is the payload; the damage matters only if the player draws a weapon inside the window, which is the intended loop — displace, arm, hit.

`SWAP_MOMENTUM_WINDOW_TICKS` (24) is deliberately below both swap cooldowns, which makes "refreshes rather than stacks" structurally unreachable instead of merely unlikely. That inequality is what the test asserts, rather than trying to exercise a refresh that cannot happen.

## Seam: Todo does not own a Black Flash cue id

`TodoVfxIds` defines `todo/boogie_woogie`, `todo/swap_endpoint`, `todo/swap_afterimage`, `todo/swap_arrival`, `todo/momentum_strike`, `todo/feint_tell`, `todo/pair_mark`, `todo/stone_throw`, `todo/stone_vanish` and `todo/triple_swap` — and no Black Flash id. `TodoBlackFlashRuntime.afterDamage` broadcasts `NobaraVfxIds.BLACK_FLASH` instead of a Todo-owned id (VERIFIED — `import jujutsu.mod.vfx.NobaraVfxIds`).

This is a real cross-character coupling, not a shared-effects abstraction: the id lives in a Nobara-named class and is registered by `NobaraVfxRecipes`. Retuning Nobara's Black Flash presentation silently retunes Todo's. Either promote Black Flash to a shared id or give Todo its own; until then, treat `NobaraVfxIds` as roster-shared in practice and vessel-named in code. A third vessel should not copy this.

`TodoBlackFlashRuntime` otherwise reuses the existing path cleanly: `ForcedBlackFlash` debug override, a re-entrancy guard set (`APPLYING_BONUS`) cleared on disconnect and server stop, and a deliberate temporary `invulnerableTime = 0` around the bonus hit because `AFTER_DAMAGE` runs after vanilla sets it.

## Not in this slice

No starter items and no items at all — the stone is an entity, never an inventory stack (Nobara's kit is restored idempotently on every selection; Todo has none) — and no third-person model work beyond the shared stack; see [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the GeckoLib side and the CLAP first-person style.

Combat feel, swap readability, and clap timing are UNKNOWN without a real client smoke test.
