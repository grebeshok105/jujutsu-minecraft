# Todo (Aoi Todo) — Boogie Woogie and Combat Slice

Status: CURRENT

Todo's playable slice: the Boogie Woogie swap on the shared PRIMARY slot, a feint clap on PRIMARY_SNEAK, a pair swap on SECONDARY, passive attribute modifiers, and a Black Flash bridge on vanilla melee. All claims VERIFIED against `src/main/java/jujutsu/mod/character/todo/**` unless labelled otherwise.

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

## Entry gate

`CharacterAbilityExecutor.tryCast` handles the not-selected and cooldown rejections, then asks the selected vessel's definition — `TodoDefinition.tryCast` delegates to `TodoAbilityRouter.tryCast`, not to a runtime directly. The router's switch over `CharacterAbility` is exhaustive on purpose: a future slot constant fails compilation there instead of silently falling into the swap, which is exactly what happened while the executor called `TodoBoogieWoogieRuntime` for every slot. `PRIMARY` → `TodoBoogieWoogieRuntime`, `PRIMARY_SNEAK` → `TodoFakeClapRuntime`, `SECONDARY` → `TodoPairSwapRuntime`. `SECONDARY_SNEAK` never arrives: `TodoDefinition.canonicalSlot` folds it onto `SECONDARY`, because Shift+B is B for him — the pair swap is two presses on one key and cares about neither stance nor hands, so crouching to line up the second participant must not lose the press. The folding happens in the executor **before** the cooldown check, or the sneak variant would be a slot with no cooldown of its own and pressing it would bypass the real one; the router's arm stays (answering `false`) only so the switch remains exhaustive without a `default`. `ATTACK_CONTEXT` is genuinely empty — his melee is plain vanilla. Each runtime still re-checks that it was handed its own slot.

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

`TodoPairSwapRuntime`. Todo claps and two other bodies trade places; he does not move. Two casts on one key: the first marks a participant, the second resolves the pair and commits. Shift+B reaches it too — `TodoDefinition.canonicalSlot` folds `SECONDARY_SNEAK` onto `SECONDARY` — so crouching between the two presses does not silently lose the second one. One asymmetry worth knowing: the client-side cooldown mirror does not fold, so pressing Shift+B during the `SECONDARY` cooldown sends a packet that earns the ordinary recharging message instead of being suppressed locally. Correct, just not predicted.

What each cast costs, and what it does not:

| Cast | Effect | Cooldown |
|---|---|---|
| First, on an eligible body | marks it, caster-only cue + actionbar naming it | none — lining a swap up is free |
| Second, on a different eligible body | commits the swap | `SECONDARY` slot, `PAIR_SWAP_COOLDOWN_TICKS` |
| Second, back at the mark | deliberate cancel, mark dropped | none |
| Second, at nothing | refused, **mark survives** | none — a missed click must not cost a two-cast setup |

Distance is measured from Todo to each participant and **never between the two of them**. A 40-block spread between the pair is the whole value of the technique; the javadoc says so explicitly so nobody "fixes" it into a pair-distance limit. Both participants must also be in reach, visible, and pass the same `isEligibleTarget` policy as a direct swap target — a bystander is never held to a laxer standard than a body Todo aims at.

`TodoPendingSelection` stores the dimension, the network id **and** the UUID. The id is what `TargetResolver` returns and what the level can look up; the UUID is what proves the entity found under that id is the same one and not a recycled slot. It is dropped on expiry, on the marked body dying, and on `DISCONNECT`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, `SERVER_STOPPING`, and vessel change (`TodoDefinition.onDeselected` calls `TodoPairSwapRuntime.forget` — it runs only when Todo is the vessel being left, not on every selection change as the old unconditional clear did; see E12 in docs/KNOWN_ISSUES.md for the marker gap that clear used to hide, now closed). The expiry sweep deliberately does **not** read an unresolvable entity as dead — an unloaded chunk is not a death, and the commit path re-verifies liveness anyway. That asymmetry is intentional; making the sweep stricter would drop marks whenever a target walked out of a loaded chunk.

Placement is `STRICT`, which finally gives that enum a call site. The scan behind both strictness values now lives in shared `jujutsu.mod.combat.SafeBodyPlacement` (extracted when Megumi's shadow move became its second consumer); the wrapper, the `Strictness` vocabulary and every call site stay in `TodoBoogieWoogieRuntime`, and the candidate order is unchanged. Everything else is the self swap's machinery unchanged: the same `TodoSwapPlan.preflight` atomicity rule, the same sequential placement, the same best-effort rollback with an error-level incomplete-restore log. `TodoSwapPlan`'s components are named `firstDestination` / `secondDestination` precisely because the record now covers both shapes.

The commit reuses `BOOGIE_WOOGIE` plus two `SWAP_ENDPOINT` cues, so at a distance a pair swap and an ordinary one look alike — free deception, and one less presentation to keep in step. The mark itself is a one-shot caster-only `PAIR_MARK` cue plus the actionbar line, **not** a marker that tracks the body: `VfxInstance.start` is called once and never ticked, so a transient cue cannot follow a live entity. VFX Core's rule is that anything which must follow an entity belongs on that entity's renderer. The actionbar naming the target is what the caster actually needs to remember who is marked.

## Marking a body by hand — the sixth slot

`CharacterAbility.USE_CONTEXT`, `TodoEntityMarkRuntime`, `TodoSwapMarks.markBody`.

Two right clicks put the swap mark on whoever is under the crosshair, at the aimed swap's own range and through its own eligibility rule. It is **not** a second mark system: it produces the same `ENTITY`-form mark the thrown marker produces, with the same ten seconds, the same glow-ownership rule and the same single cleanup path. What it removes is the item.

Adding the slot is the seam's own claim being exercised: appending `USE_CONTEXT(5)` produced exactly two compile errors, one per exhaustive router, and none anywhere else. Nobara refuses it explicitly rather than by a `default`; Todo answers it. Nothing else in shared code moved, and the roster-card test did its own bookkeeping — expected card length is derived as slots minus refused arms, so Nobara stayed at five and Todo's card had to gain a fourth entry.

**The first click is vanilla's, deliberately.** `USE_CONTEXT` is the only slot whose key the game already owns, and a tick-level edge detector runs after vanilla has already handled the press. At the ranges this cast is for — a body across the arena, hands empty — vanilla's right click does nothing; up close the first press will still mount the horse or open the trade before the second one marks. Cancelling it would need the real interaction events or a mixin, and it was accepted instead. The input layer sends the slot **only once a pair completes**, so an ordinary right click costs no packet.

The pair window is six ticks and lives in `JujutsuKeybinds`, vessel-neutral like the rest of that file. This is the kit's only multi-press input on the *use* key, and it does not contradict the standing "the swap stays instant" rule — that rule is about the technique key, and nothing here delays anything. The second technique key does carry a hold gesture since the Megumi shadow kit; its cost is documented there.

Two costs, both real and both deliberate:

- **It inherits the one-mark rule.** Marking a body replaces a landed anchor, so the two are alternatives rather than a stockpile.
- **The cast is public.** The glow it applies is visible to everyone, so a caster-only cue would only mislead the caster about how visible he is. That is the exact inverse of the feint, whose cue is caster-only precisely because it must leave no trace.

The glow sequence — release the old mark, *then* read the glow, then apply and store — now lives once in `TodoSwapMarks.markBody`, called by both the throw and the ability. It was a comment-guarded ordering in one file; with two callers it had to become one method, and `TodoEntityMarkTest` pins the order there while `TodoSwapMarkerTest` pins the delegation.

## The thrown mark — no new slot

`TodoSwapMarkerItem`, `TodoSwapMarkerEntity`, `TodoSwapMark`, `TodoSwapMarks`, `TodoMarkerSwapRuntime`.

No new ability slot and no new key. `TodoBoogieWoogieRuntime.tryCast` falls back to a live mark **only after** the crosshair has failed to find an eligible target, so the priority is the one the player means: an enemy under the crosshair wins, and the mark gets what is left. `TodoSwapMarkerTest` pins that ordering by source position rather than trusting a comment.

Only Todo can throw it: `TodoSwapMarkerItem.use` refuses for any other vessel, checked on **both** sides through `CharacterSelectionView` because vanilla calls an item's `use` on the client too — a server-only gate would let the client predict a throw the server then refuses, taking back a consumed item and a played sound. That closed E12: before the gate, anyone could leave a mark in the world that only Todo could ever use.

The marker is single-stack and consumed on throw. That is what keeps the empty-hands rule absolute instead of turning it into a whitelist — the gate is read at swap time, and by then the throwing hand is empty. A stackable marker would leave a remainder in hand and correctly block the swap, so the stack size is load-bearing, and a test pins it.

Vanilla owns the flight: `ThrowableItemProjectile` gives authoritative movement, client interpolation, hit detection and tracking. The entity type is `noSave()`, so a mark cannot outlive the session that threw it.

Two mark forms, genuinely different lifetimes, one record and one release path:

| Form | Trigger | Lifetime | Spent by a swap? | The projectile | What ending the mark undoes |
|---|---|---|---|---|---|
| `POSITION` | block hit | **none** (`TodoSwapMark.NEVER`) | no — reusable anchor | **stays alive** — it *is* the mark, resting `MARKER_SURFACE_OFFSET` off the struck face | discards the projectile |
| `ENTITY` | body hit | `MARKER_BODY_MARK_TTL_TICKS` | yes | removed immediately | clears the glow, but only if the mark applied it |

The two lifetimes are enforced by the record, not by remembering: `atPosition` takes no expiry parameter at all, so giving a landed mark a clock does not compile, and the canonical constructor rejects either form holding the other's lifetime. `TodoSwapMarks.onUsed` is the single place that decides what a swap costs its mark — separate from `clear`, which is the unconditional teardown every other path uses. A charge limit or any other price lands in that one method.

**What "permanent" means, exactly:** permanent until explicitly cleared or until the marker is lost, and **never persistent between server sessions**. It ends on death, on changing vessel, on changing dimension, on disconnect, on server stop, and when the projectile goes missing from a *loaded* chunk. `NEVER` is the absence of a timer, not eternity.

That last clause is load-bearing and only became reachable with permanence. The entity type is `noSave()`, so an unloaded chunk removes the projectile and never returns it, while the mark sweep deliberately refuses to read an unresolvable entity as dead ("an unloaded chunk is not a death"). Under a ten-second TTL that window was unreachable; with a permanent anchor, walking out of render distance and back would have left a working teleport anchor with no marker anywhere in the world. `landedMarkerIsGone` checks the chunk **before** the entity, which ends the mark exactly when its projectile is really gone and covers an explosion, a `/kill` and third-party cleanup with the same rule. Losing an anchor is announced; expiry stays silent.

`glowApplied` is false when the body was already glowing, so ending a mark can never extinguish another system's highlight — Nobara's target marks use the same vanilla glow. Every way a mark can end funnels through one `release` method, and a test asserts there is exactly one, because "each cleanup path must handle both forms" is where this feature's bugs would otherwise live. Released on expiry, marked-body death, disconnect, respawn, dimension change and server stop; the sweep applies the same unloaded-chunk-is-not-a-death rule as the pair selection.

A landed marker's `tick()` returns before `super.tick()`. That is deliberate: a resting mark must take no physics and must not re-enter hit detection, and its lifetime belongs to `TodoSwapMarks` rather than a second clock on the entity.

Both forms swap under `STRICT` placement. The `POSITION` form moves one body, so atomicity is trivial — either Todo's destination is safe or nothing happens; the `ENTITY` form runs the ordinary two-destination plan. Reach is `MARKER_SWAP_RANGE`, longer than the aimed swap, because the mark cost an item, a throw and a public telegraph an opponent can play around. The cooldown is `MARKER_SWAP_COOLDOWN_TICKS` on the `PRIMARY` slot — equal to the aimed swap's today, and split from it deliberately: a reusable thirty-two block return is the strongest thing in the kit, and pricing it differently should be one number rather than a rewrite.

Consequence worth stating: while a landed mark exists, every `PRIMARY` press that finds nothing under the crosshair becomes a teleport instead of a `no_target` line. That is the fallback working as designed, but it changes how the key feels, which is why the arrival now has visuals of its own.

Coverage is the same honest limit as the feint: pure record logic plus source-text contracts. No test teleports anything. Whether a resting marker reads clearly in world, and whether either mark form leaks a projectile or a glow in real play, is UNKNOWN.

## The impact sequence — and why it lives on its own cues

`TodoBoogieWoogieRuntime.emitSwapImpact` is the single emission point for all four routes: the aimed swap, both marker swaps and the pair swap. They used to hand-copy the same five calls, which is a shape that drifts, and the copy that drifts is the one nobody plays often enough to notice.

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

**Momentum.** `place` teleports absolutely with an empty `Relative` set, so the transition carries `Vec3.ZERO` and the client is told its velocity is nothing — the server-side `setDeltaMovement` was a fiction for a player, who owns his own movement. `restoreMotionAndRotation` now sets `hurtMarked`, which makes `ServerEntity#sendChanges` emit `ClientboundSetEntityMotionPacket` through `broadcastAndSend`, reaching the trackers and the moved player's own connection. One line inside the shared helper covers all four routes and the rollback path.

## Swap momentum — one heavier hit

`TodoSwapMomentum` (pure policy), `TodoSwapMomentumRuntime`, `JujutsuEffects.TODO_SWAP_MOMENTUM`.

A completed swap opens a 24-tick window; the next confirmed melee hit lands harder, staggers for `SWAP_MOMENTUM_STAGGER_TICKS`, and gets its own `MOMENTUM_STRIKE` cue. A miss, a shielded hit or a hit that dealt nothing leave it untouched; it lapses on its own.

The damage rides on the effect's own `ATTACK_DAMAGE` modifier, not on a second `hurtServer` call. That is what makes it impossible to double-apply — the vanilla swing is simply bigger, so there is no extra damage instance to fight with Black Flash's bonus, pierce invulnerability, or fire the damage event twice. `ADD_MULTIPLIED_TOTAL` composes with Todo's standing `+0.50` as `base × 1.50 × 1.25`; `ADD_MULTIPLIED_BASE` would silently have meant `×1.75`.

Three non-obvious constraints the runtime exists to satisfy:

- **`AFTER_DAMAGE` does not fire on a killing blow.** A kill would have silently refunded the window and shown nothing, so `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` is a second entry into one spend path (no stagger — there is nobody left to interrupt).
- **Black Flash re-enters the damage event** to apply its bonus, so the listener sees one swing twice. Without the `BlackFlashStrike.isApplyingBonus` guard the window is spent on the nested pass, tying the stagger and the cue to a hidden ten-percent roll. The stagger is additionally guarded on `isAlive()`, because that bonus hit can kill the target inside the same swing.
- **The spend path checks the attacker is still Todo**, so leaving the vessel mid-window would strand a live `+25%` modifier nothing could remove. `onDeselected` takes it off; the attribute sweeps do not reach it, because it belongs to the effect rather than to the definition.

Grant sites are exactly two: past the last `return false` in `TodoBoogieWoogieRuntime.tryCast`, and in `TodoMarkerSwapRuntime.finish` (one site for both mark routes). **Not** the pair swap — Todo does not move and takes no positional risk, which is why its cooldown is already 100 against 60 — and **not** the feint, whose 20-tick cooldown would make it a threefold-cheaper way to buy the window.

Two limits recorded rather than hidden, both in the runtime's javadoc:

1. A **sweeping attack** keeps the boosted damage on its later victims after the window is spent on the first: `Player.attack` captures the damage into a local before the sweep block, so removing the modifier mid-swing cannot shrink a float already on the stack. Stagger and cue do not duplicate. It costs a deliberate hotbar swap, since both hands must be empty to clap.
2. On **bare fists** `×1.25` is worth about a third of a heart. The stagger is the payload; the damage matters only if the player draws a weapon inside the window, which is the intended loop — displace, arm, hit.

`SWAP_MOMENTUM_WINDOW_TICKS` (24) is deliberately below both swap cooldowns, which makes "refreshes rather than stacks" structurally unreachable instead of merely unlikely. That inequality is what the test asserts, rather than trying to exercise a refresh that cannot happen.

## Seam: Todo does not own a Black Flash cue id

`TodoVfxIds` defines `todo/boogie_woogie`, `todo/swap_endpoint`, `todo/swap_afterimage`, `todo/swap_arrival`, `todo/momentum_strike`, `todo/feint_tell` and `todo/pair_mark` — and no Black Flash id. `TodoBlackFlashRuntime.afterDamage` broadcasts `NobaraVfxIds.BLACK_FLASH` instead of a Todo-owned id (VERIFIED — `import jujutsu.mod.vfx.NobaraVfxIds`).

This is a real cross-character coupling, not a shared-effects abstraction: the id lives in a Nobara-named class and is registered by `NobaraVfxRecipes`. Retuning Nobara's Black Flash presentation silently retunes Todo's. Either promote Black Flash to a shared id or give Todo its own; until then, treat `NobaraVfxIds` as roster-shared in practice and vessel-named in code. A third vessel should not copy this.

`TodoBlackFlashRuntime` otherwise reuses the existing path cleanly: `ForcedBlackFlash` debug override, a re-entrancy guard set (`APPLYING_BONUS`) cleared on disconnect and server stop, and a deliberate temporary `invulnerableTime = 0` around the bonus hit because `AFTER_DAMAGE` runs after vanilla sets it.

## Not in this slice

No starter items (Nobara's kit is restored idempotently on every selection; Todo has none), and no third-person model work beyond the shared stack — see [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the GeckoLib side and the CLAP first-person style.

Combat feel, swap readability, and clap timing are UNKNOWN without a real client smoke test.
