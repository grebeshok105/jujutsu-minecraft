# Todo (Aoi Todo) — Boogie Woogie and Combat Slice

Status: CURRENT

Todo's first playable slice: one active technique on the shared PRIMARY slot, passive attribute modifiers, and a Black Flash bridge on vanilla melee. All claims VERIFIED against `src/main/java/jujutsu/mod/character/todo/**` unless labelled otherwise.

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

`CharacterAbilityExecutor.tryCast` handles the not-selected and cooldown rejections, then routes `TODO` to `TodoBoogieWoogieRuntime.tryCast`. That method re-gates on ability == PRIMARY, spectator, alive, `TodoTargetSafety.hasUnsafeTransportState(passenger, vehicle, false)`, and `CombatStagger.GLOBAL`.

The empty-hands gate is authoritative and early: any stack in main or off hand rejects with `todo.boogie.hands_full` before any state is touched, so a rejected cast has no partial effects. `TodoHandsEmptyTest` and `TodoTargetSafetyTest` cover these as pure logic.

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

`CharacterAbilityCooldowns.start` plus `JujutsuNetworking.sendAbilityCooldown` for the 60-tick cooldown. Then two kinds of cue, because one cue cannot carry two absolute world points: `TodoVfxIds.BOOGIE_WOOGIE` is the performance, anchored to the caster with a zero offset, and one `TodoVfxIds.SWAP_ENDPOINT` per moved body carries an absolute endpoint with no anchor, each broadcast around its own point so far-side observers receive it.

That split fixed a real defect. `VfxAnchorResolver` already adds the cue's anchor offset, the recipe added it again, and the cue is broadcast after the teleport — so the two flashes landed at `todoPos + delta` and `todoPos + 2*delta`, drifting with packet order. The ribbon was never affected because `VfxWorldChannel` treats this style as world-fixed and reads `cue.origin()` directly.

The first-person clap is gated on the local anchor. A recipe runs on every client that receives the cue, so before the gate every nearby player's own arms clapped.

Sound is server-authoritative: `JujutsuSounds.PROJECTJJK_CLAP` plays with the swap, and a movement sound follows one tick later at both original positions through the static pending-sound queue drained by `TodoBoogieWoogieRuntime.register()`'s END_WORLD_TICK listener, which now clears on `SERVER_STOPPING`. An earlier revision of this note claimed clients timed the clap from `TodoVfxRecipes`; that was never true — no sound call existed there.

## Seam: Todo does not own a Black Flash cue id

`TodoVfxIds` defines exactly one id, `todo/boogie_woogie`. `TodoBlackFlashRuntime.afterDamage` broadcasts `NobaraVfxIds.BLACK_FLASH` instead of a Todo-owned id (VERIFIED — `import jujutsu.mod.vfx.NobaraVfxIds`).

This is a real cross-character coupling, not a shared-effects abstraction: the id lives in a Nobara-named class and is registered by `NobaraVfxRecipes`. Retuning Nobara's Black Flash presentation silently retunes Todo's. Either promote Black Flash to a shared id or give Todo its own; until then, treat `NobaraVfxIds` as roster-shared in practice and vessel-named in code. A third vessel should not copy this.

`TodoBlackFlashRuntime` otherwise reuses the existing path cleanly: `ForcedBlackFlash` debug override, a re-entrancy guard set (`APPLYING_BONUS`) cleared on disconnect and server stop, and a deliberate temporary `invulnerableTime = 0` around the bonus hit because `AFTER_DAMAGE` runs after vanilla sets it.

## Not in this slice

No starter items (`Nobara starter tools are claimed once; Todo has none`), and no third-person model work beyond the shared stack — see [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the GeckoLib side and the CLAP first-person style.

Combat feel, swap readability, and clap timing are UNKNOWN without a real client smoke test.
