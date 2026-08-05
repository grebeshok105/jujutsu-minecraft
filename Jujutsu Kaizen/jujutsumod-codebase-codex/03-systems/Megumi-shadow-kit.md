# Megumi shadow kit — Shadow Trap and Shadow Move

Status: CURRENT

## Slice boundary

Megumi's second ability pair. `SECONDARY` (`B`) opens one static shadow pool under the aimed target; `SECONDARY_SNEAK` (`Shift+B`) is one travel technique with three contextual modes — emerge behind the target, free step to an aimed surface, and a held deep submerge on the appended `SECONDARY_SNEAK_HOLD`/`SECONDARY_SNEAK_RELEASE` slots. The slice adds no payloads, items, persistence, dimensions or mixins; design contract in [docs/MEGUMI_SHADOW_KIT.md](../../../docs/MEGUMI_SHADOW_KIT.md), tuning owned by `MegumiProfile`.

The two appended slots are wire ids 6 and 7 in `CharacterAbility`; ids 0–5 are untouched. They travel in the existing `CharacterAbilityPayload`. The input layer buffers a *sneaking* second-technique press and resolves it on release (tap) or at the six-tick hold threshold; a plain `B` press still sends instantly. `SECONDARY_SNEAK_RELEASE` deliberately never carries a cooldown so the end of a held gesture always reaches the router; a release with no matching server state is refused there.

## Shadow Trap — `MegumiShadowTrapRuntime`

One pool per owner, keyed by owner UUID. The cast resolves through the shared `TargetResolver` with the same `MegumiTargetPolicy` eligibility Sic uses, plus owner line of sight; the centre snaps down up to `SHADOW_TRAP_GROUND_SNAP_BLOCKS` onto the ground under an airborne target and then never moves. Radius `SHADOW_TRAP_RADIUS` (2.6), vertical reach −1.0..+1.6, lifetime `SHADOW_TRAP_DURATION_TICKS` (100), cooldown `SHADOW_TRAP_COOLDOWN_TICKS` (200) on `SECONDARY`.

Every server tick the trap applies `megumi_shadow_grip` (`JujutsuEffects.MEGUMI_SHADOW_GRIP`, HARMFUL) for `SHADOW_TRAP_GRIP_REFRESH_TICKS` (8) to every eligible living body standing inside the cylinder. The grip is pure vanilla attribute mathematics — `MOVEMENT_SPEED` ×(1−0.75), `JUMP_STRENGTH` ×0 through `ADD_MULTIPLIED_TOTAL` — so it mirrors to clients through the effect system and expires by itself once a body leaves; nothing ever scrubs effects manually. Not a stun: a gripped body walks out slowly. The dogs get no teleport and no forced pounce from the pool.

Cleanup is one `clear` path fed by owner death, respawn, dimension change, disconnect, deselection (`MegumiDefinition.onDeselected`) and server stop; expiry and a vanished level/chunk/owner close the pool from the tick loop.

## Shadow Move — `MegumiShadowMoveRuntime`

One state per player: `SINK(8)` → `HIDDEN(4)` → `EMERGE(6)` for taps, `SINK(8)` → `SUBMERGED(≤50)` → `EMERGE(6)` for the hold. Mode selection is `MegumiShadowMovePolicy.resolveTapMode`: an eligible living target under the crosshair (range `SHADOW_STEP_TARGET_RANGE`, LOS) wins backstep; otherwise an aimed block hit within `SHADOW_STEP_RANGE` is a free step; otherwise the cast is refused before any state exists.

- **Backstep.** The exit is computed at the end of `HIDDEN` against the target's live position and `yBodyRot` — the back of the body, not the head. Candidates walk `MegumiShadowMovePolicy.REAR_ARC_DEGREES` (0, ±25, ±50, ±75) at `BACKSTEP_DISTANCE`, each validated by `SafeBodyPlacement`; the first safe one wins and Megumi faces the target on arrival. A dead/removed/cross-level target, one that drifted past twice the cast range, or an empty arc all resurface him at his start point.
- **Free step.** The aimed point is nudged half a block out of the hit face and re-validated at emerge time against the world as it is then. The block clip is the line-of-sight test by construction: thick walls, closed rooms and unloaded chunks are unreachable.
- **Deep submerge.** Entered only through the hold slot, checked by the router against the shared `SECONDARY_SNEAK` cooldown. While under, the walk keeps full vanilla collision (no wall clipping, ever), attacks are refused by `AttackEntityCallback`, every other slot is swallowed by the router's lock gate, and a ripple cue re-emits every `SHADOW_RIPPLE_PERIOD_TICKS` (5) as both the readability tell and the client hide signal. Release, a repeat tap, the `SUBMERGE_MAX_TICKS` (50) timeout or any lifecycle event ends it.

Damage during `SINK` cancels the cast on the spot — no teleport, no cooldown. In `HIDDEN`/`SUBMERGED`, `ServerLivingEntityEvents.ALLOW_DAMAGE` refuses everything that does not bypass invulnerability; fire and lava are inside that shield for the ≤2.5 s a submerge lasts, an accepted mobility payoff priced by the hold cooldown (item use stays unlocked — a known open question, not an oversight). The invisibility flag is re-asserted every tick while under, because vanilla's `updateInvisibilityStatus` clears it whenever the effect list changes — an enemy trap re-gripping the body does so every tick. The emerge teleports through the same snapshot primitive the swap uses (`teleportTo` with no relative flags, zeroed motion, `hurtMarked`), starts the mode's cooldown on `SECONDARY_SNEAK` (`SHADOW_STEP_COOLDOWN_TICKS` 120 for taps, `SUBMERGE_COOLDOWN_TICKS` 200 for the hold), and restores visibility potion-aware. Every teardown restores visibility and leaves no pending teleport: the body simply resurfaces where it already is.

## Shared placement — `SafeBodyPlacement`

`TodoBoogieWoogieRuntime.findSafeDestination` moved to `jujutsu.mod.combat.SafeBodyPlacement` once the shadow move became its second production consumer. The scan is unchanged — requested point, a precomputed 13-offset horizontal ring, up to three one-block upward steps, in-world + chunk + border + `noBlockCollision` — and the exact-point fallback that Todo's SOFT keeps is a policy flag. Todo's wrapper and `Strictness` vocabulary stay in his file; Megumi declares `EXIT`, `RESCUE` (ring radius `EMERGE_SEARCH_RADIUS`) and `RETURN` policies. Neither vessel names the other.

## Presentation

Seven appended live ids in `MegumiVfxIds` (`shadow_trap_open/zone/grip/close`, `shadow_dive/ripple/emerge`), recipes in `MegumiVfxRecipes`, all one-shot per cue behind `isOpeningBeat`. Continuous visuals ride server re-emission — the trap zone every `SHADOW_TRAP_ZONE_PULSE_TICKS` (40) with the radius in the cue intensity (tenths of a block), the ripple every 5 ticks — never client re-ticking. Trap pools draw as true circles so the telegraph matches the authoritative grip cylinder; only the dogs' decorative summon pool keeps its stylized depth squash. The dive/emerge cues trigger the `shadow_dive`/`shadow_emerge` GeckoLib clips through `MegumiAnimationHooks` and, for the local caster only, blur plus a light nausea beat. The sink stays watchable — the dive cue hides nothing; a client-side TTL set fed by the ripple cues (the first one fires the moment the body actually hides, at the end of the sink) blanks a submerged player's whole render, and a lost packet fails open to visible. Sounds are server-played (`goo_foley`, `implode`, `whoosh_hit`); recipes stay silent.

## Verified boundaries

Adding the two slots produced exactly the expected compile errors in the three exhaustive routers and nothing else. Divine Dogs, Sic and pounce are untouched. The roster card derives its expected length from the router minus refused arms minus `*_RELEASE` slots (the end of a gesture is not a technique), so Megumi's card grew to five abilities.

## What no automated test covers

No test constructs a `ServerLevel`, so actual teleports, collision during the submerged walk, invisibility sync, trap slow feel, animation playback and multiplayer visibility remain in-game smoke — the checklist lives in the branch `SESSION.md` (E1).
