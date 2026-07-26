# Todo (Aoi Todo) — Boogie Woogie and Combat Slice

Status: CURRENT

Todo's playable slice: the Boogie Woogie swap on the shared PRIMARY slot, a feint clap on SECONDARY, passive attribute modifiers, and a Black Flash bridge on vanilla melee. All claims VERIFIED against `src/main/java/jujutsu/mod/character/todo/**` unless labelled otherwise.

## Tuning constants

Every number lives in `TodoProfile`. Nothing else should hold a Todo magic number.

| Constant | Value | Applied by |
|---|---:|---|
| `MELEE_DAMAGE_MULTIPLIER` | 1.50 | `CharacterCombatModifiers` — `Attributes.ATTACK_DAMAGE` modifier `todo/damage`, added as `value - 1.0` |
| `ATTACK_SPEED_MULTIPLIER` | 0.85 | `CharacterCombatModifiers` — `Attributes.ATTACK_SPEED` modifier `todo/attack_speed` |
| `STAGGER_DURATION_MULTIPLIER` | 0.50 | `CharacterCombatModifiers` — incoming stagger, `max(1, ceil(requested * 0.50))` |
| `BOOGIE_WOOGIE_RANGE` | 20.0 | `TodoBoogieWoogieRuntime` target resolve and a re-check on squared distance |
| `BOOGIE_WOOGIE_COOLDOWN_TICKS` | 60 | three seconds at 20 TPS |
| `SAFE_POSITION_HORIZONTAL_RADIUS` | 1.0 | horizontal nudge ring |
| `SAFE_POSITION_UPWARD_BLOCKS` | 3 | upward nudge ceiling |
| `WORLD_BORDER_MARGIN` | 0.05 | destination box inflation before the border test |
| `BLACK_FLASH_CHANCE` | 0.10 | `TodoBlackFlashRuntime` |
| `BLACK_FLASH_DAMAGE_MULTIPLIER` | 1.75 | bonus is `baseDamageTaken * (multiplier - 1.0)` |
| `BLACK_FLASH_STAGGER_TICKS` | 14 | `CombatStagger.GLOBAL` |

## Entry gate

`CharacterAbilityExecutor.tryCast` handles the not-selected and cooldown rejections, then routes `TODO` to `TodoAbilityRouter.tryCast` — not to a runtime directly. The router's switch over `CharacterAbility` is exhaustive on purpose: a future slot constant fails compilation there instead of silently falling into the swap, which is exactly what happened while the executor called `TodoBoogieWoogieRuntime` for every slot. `PRIMARY` → `TodoBoogieWoogieRuntime`, `SECONDARY` → `TodoFakeClapRuntime`, and each runtime still re-checks that it was handed its own slot.

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

`findSafeDestination` takes a `Strictness`. Under `SOFT`, used by this ability, a last-resort fallback accepts the exact requested point when it passes `isInWorldDestination` — finite coordinates, world bounds, chunk loaded, and now the world border, which moved into that test so both paths enforce it. Block collision is still skipped on that path, so the fallback can place a participant clipping geometry, which vanilla resolves by pushing it out. `STRICT` has no fallback: if no candidate passes, the destination is null and the whole swap cancels. It exists for swaps that move third parties, where relaxing safety for a bystander is not justified by Todo's own mid-air feel.

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

## The feint clap — `Shift+R`, the SECONDARY slot

A complete Boogie Woogie clap that moves nobody, so the next real one is a coin the opponent has to call. VERIFIED against `TodoFakeClapRuntime`, `TodoSwapGates`, `TodoBoogieWoogieRuntime.emitClapPerformance`, `TodoVfxRecipes`, and `TodoFakeClapTest` unless labelled otherwise.

The server knows the cast is hollow from the first tick: `TodoFakeClapRuntime` never starts a swap and then cancels it, so no target is resolved, no destination is planned, and no body can be left half-moved. `TodoFakeClapTest` asserts the file mentions none of `teleportTo`, `TodoSwapPlan`, `findSafeDestination`, `TargetResolver`, or `SWAP_ENDPOINT`, so the teleport machinery cannot creep in later.

Input is the existing technique key with `Shift` held — `JujutsuKeybinds` reads `client.player.isShiftKeyDown()` and picks `SECONDARY` instead of `PRIMARY`. No hold threshold and no double tap: the real swap has to stay instant, and both casts have to be typeable equally fast.

### The indistinguishability contract

Four things make the two casts alike by construction rather than by tuning.

1. **One shared performance.** Both emit the clap through `TodoBoogieWoogieRuntime.emitClapPerformance` — same cue id `todo/boogie_woogie`, same caster anchor with a zero offset, same server-side `JujutsuSounds.PROJECTJJK_CLAP` at the same volume and pitch, on the same tick. One implementation, so the two presentations cannot drift apart in a later edit. `TodoFakeClapTest` asserts the feint does not name `TodoVfxIds.BOOGIE_WOOGIE` itself, only the shared method.
2. **One shared gate truth table.** Both read `TodoSwapGates.evaluate`, so the set of casts that get refused — and the message each refusal produces — is identical. A feint that were allowed with a sword in hand would announce itself.
3. **Independent cooldown slots.** `CharacterAbilityCooldowns` keys on (player, slot). The feint starts and reports `SECONDARY` with `TodoProfile.FAKE_CLAP_COOLDOWN_TICKS` and never names `PRIMARY`; the swap keeps `PRIMARY` with `BOOGIE_WOOGIE_COOLDOWN_TICKS`. A feint therefore neither spends nor postpones the real swap. There is deliberately **no** gate requiring the real swap to be ready — that was offered and declined, because a feint that only works while the swap is off cooldown is itself a tell.
4. **Caster-only tell.** The single unshared packet is `TodoVfxIds.FEINT_TELL`, sent through `JujutsuNetworking.sendVfxCue(todo, …)` to one player and never broadcast (`TodoFakeClapTest` asserts `broadcastVfxCue` does not appear in the file). Its recipe is a six-tick dust ring at chest height and nothing else — no sound, no HUD flash, no camera kick, since every one of those would be perceivable by the observer the feint exists to deceive.

The one field that does differ carries no information: the feint puts `todo.getLookAngle()` where the swap puts the normalized caster-to-target delta, and those point the same way. INFERRED that this keeps a future recipe honest — no recipe reads that field today.

`CharacterAbility` gained `SECONDARY(1)` by appending. Network ids are wire format and are never renumbered; `TodoFakeClapTest` pins `PRIMARY == 0`, `SECONDARY == 1`, and `byNetworkId(2) == null`.

### What is still distinguishable — the open product question

The real swap teleports both bodies at cast time, but the clap's palm contact is at `VfxFirstPersonChannel.CLAP_CONTACT_PROGRESS` = 0.39 of the 0.72 s `ability.boogie_woogie` animation. An observer therefore sees a real swap **before** the palms meet, which means a feint is already distinguishable at t = 0 by the absence of a teleport — and, in the same instant, by the absence of the two `todo/swap_endpoint` bursts and of the movement sounds that follow one tick later at both origins.

Delaying the swap to the contact frame was **not** done. It would change `TodoBoogieWoogieRuntime`'s commit path — the most safety-critical method in the kit, the one that owns the two-sided preflight and the best-effort rollback — and that is not in the approved plan. This is the feint's main open product question. Decide it deliberately, not as a side effect of a VFX pass.

### Coverage — the honest limit

Nothing in the test suite can construct a `ServerLevel`, so no test ever calls `TodoFakeClapRuntime.tryCast`. The feint is covered by pure gate logic (the `TodoSwapGates.evaluate` truth table) plus source-text contract assertions in `TodoFakeClapTest` and `TodoHandsEmptyTest`: that the shared performance method exists and is the one the feint calls, that the tell is a single-player send, that no swap machinery is present, that the cooldown slots are separate, and that the router switch carries no `default`. Whether the two casts actually read as the same event to a second player is UNKNOWN — it has not been verified in game. See E1 in docs/KNOWN_ISSUES.md.

## The pair swap — `B`, the TERTIARY slot

`TodoPairSwapRuntime`. Todo claps and two other bodies trade places; he does not move. Two casts on one key: the first marks a participant, the second resolves the pair and commits.

What each cast costs, and what it does not:

| Cast | Effect | Cooldown |
|---|---|---|
| First, on an eligible body | marks it, caster-only cue + actionbar naming it | none — lining a swap up is free |
| Second, on a different eligible body | commits the swap | `TERTIARY` slot, `PAIR_SWAP_COOLDOWN_TICKS` |
| Second, back at the mark | deliberate cancel, mark dropped | none |
| Second, at nothing | refused, **mark survives** | none — a missed click must not cost a two-cast setup |

Distance is measured from Todo to each participant and **never between the two of them**. A 40-block spread between the pair is the whole value of the technique; the javadoc says so explicitly so nobody "fixes" it into a pair-distance limit. Both participants must also be in reach, visible, and pass the same `isEligibleTarget` policy as a direct swap target — a bystander is never held to a laxer standard than a body Todo aims at.

`TodoPendingSelection` stores the dimension, the network id **and** the UUID. The id is what `TargetResolver` returns and what the level can look up; the UUID is what proves the entity found under that id is the same one and not a recycled slot. It is dropped on expiry, on the marked body dying, and on `DISCONNECT`, `AFTER_RESPAWN`, `AFTER_PLAYER_CHANGE_WORLD`, `SERVER_STOPPING`, and vessel change (`CharacterSelectionManager.select` calls `TodoPairSwapRuntime.forget`). The expiry sweep deliberately does **not** read an unresolvable entity as dead — an unloaded chunk is not a death, and the commit path re-verifies liveness anyway. That asymmetry is intentional; making the sweep stricter would drop marks whenever a target walked out of a loaded chunk.

Placement is `STRICT`, which finally gives that enum a call site. Everything else is the self swap's machinery unchanged: the same `TodoSwapPlan.preflight` atomicity rule, the same sequential placement, the same best-effort rollback with an error-level incomplete-restore log. `TodoSwapPlan`'s components are named `firstDestination` / `secondDestination` precisely because the record now covers both shapes.

The commit reuses `BOOGIE_WOOGIE` plus two `SWAP_ENDPOINT` cues, so at a distance a pair swap and an ordinary one look alike — free deception, and one less presentation to keep in step. The mark itself is a one-shot caster-only `PAIR_MARK` cue plus the actionbar line, **not** a marker that tracks the body: `VfxInstance.start` is called once and never ticked, so a transient cue cannot follow a live entity. VFX Core's rule is that anything which must follow an entity belongs on that entity's renderer. The actionbar naming the target is what the caster actually needs to remember who is marked.

## The thrown mark — no new slot

`TodoSwapMarkerItem`, `TodoSwapMarkerEntity`, `TodoSwapMark`, `TodoSwapMarks`, `TodoMarkerSwapRuntime`.

No new ability slot and no new key. `TodoBoogieWoogieRuntime.tryCast` falls back to a live mark **only after** the crosshair has failed to find an eligible target, so the priority is the one the player means: an enemy under the crosshair wins, and the mark gets what is left. `TodoSwapMarkerTest` pins that ordering by source position rather than trusting a comment.

The marker is single-stack and consumed on throw. That is what keeps the empty-hands rule absolute instead of turning it into a whitelist — the gate is read at swap time, and by then the throwing hand is empty. A stackable marker would leave a remainder in hand and correctly block the swap, so the stack size is load-bearing, and a test pins it.

Vanilla owns the flight: `ThrowableItemProjectile` gives authoritative movement, client interpolation, hit detection and tracking. The entity type is `noSave()`, so a mark cannot outlive the session that threw it.

Two mark forms, genuinely different lifetimes, one record and one release path:

| Form | Trigger | The projectile | Readability | What ending the mark undoes |
|---|---|---|---|---|
| `POSITION` | block hit | **stays alive** — it *is* the mark, resting `MARKER_SURFACE_OFFSET` off the struck face | rendered by vanilla `ThrownItemRenderer` | discards the projectile |
| `ENTITY` | body hit | removed immediately | vanilla glowing on the struck body | clears the glow, but only if the mark applied it |

`glowApplied` is false when the body was already glowing, so ending a mark can never extinguish another system's highlight — Nobara's target marks use the same vanilla glow. Every way a mark can end funnels through one `release` method, and a test asserts there is exactly one, because "each cleanup path must handle both forms" is where this feature's bugs would otherwise live. Released on expiry, marked-body death, disconnect, respawn, dimension change and server stop; the sweep applies the same unloaded-chunk-is-not-a-death rule as the pair selection.

A landed marker's `tick()` returns before `super.tick()`. That is deliberate: a resting mark must take no physics and must not re-enter hit detection, and its lifetime belongs to `TodoSwapMarks` rather than a second clock on the entity.

Both forms swap under `STRICT` placement. The `POSITION` form moves one body, so atomicity is trivial — either Todo's destination is safe or nothing happens; the `ENTITY` form runs the ordinary two-destination plan. Reach is `MARKER_SWAP_RANGE`, longer than the aimed swap, because the mark cost an item, a throw and a public telegraph an opponent can play around. A mark is **consumed** by the swap it enables; it is not a reusable anchor. The swap takes the ordinary `PRIMARY` cooldown, because it is the primary swap.

Coverage is the same honest limit as the feint: pure record logic plus source-text contracts. No test teleports anything. Whether a resting marker reads clearly in world, and whether either mark form leaks a projectile or a glow in real play, is UNKNOWN.

## Seam: Todo does not own a Black Flash cue id

`TodoVfxIds` defines `todo/boogie_woogie`, `todo/swap_endpoint`, and `todo/feint_tell` — and no Black Flash id. `TodoBlackFlashRuntime.afterDamage` broadcasts `NobaraVfxIds.BLACK_FLASH` instead of a Todo-owned id (VERIFIED — `import jujutsu.mod.vfx.NobaraVfxIds`).

This is a real cross-character coupling, not a shared-effects abstraction: the id lives in a Nobara-named class and is registered by `NobaraVfxRecipes`. Retuning Nobara's Black Flash presentation silently retunes Todo's. Either promote Black Flash to a shared id or give Todo its own; until then, treat `NobaraVfxIds` as roster-shared in practice and vessel-named in code. A third vessel should not copy this.

`TodoBlackFlashRuntime` otherwise reuses the existing path cleanly: `ForcedBlackFlash` debug override, a re-entrancy guard set (`APPLYING_BONUS`) cleared on disconnect and server stop, and a deliberate temporary `invulnerableTime = 0` around the bonus hit because `AFTER_DAMAGE` runs after vanilla sets it.

## Not in this slice

No starter items (`Nobara starter tools are claimed once; Todo has none`), and no third-person model work beyond the shared stack — see [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the GeckoLib side and the CLAP first-person style.

Combat feel, swap readability, and clap timing are UNKNOWN without a real client smoke test.
