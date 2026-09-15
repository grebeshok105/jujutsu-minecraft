# Brief B — Server-side perception fixes (B1–B4)
- agent: worker | run: fix-perception | status: done

## Summary
All four finding groups fixed in `CursedSpiritEntity`, `perception/*`, a new narrow
`CursedSpiritProjectileMixin`, plus `jujutsumod.mixins.json` (registration — required for
the allowed mixin to load; noted as a minimal scope extension). `compileJava`,
`compileTestJava`, `compileGametestJava` and the full JUnit `test` lane are green
(2 new suites included). GameTests were written but **not executed** — heavy lane left to
the tester per the brief; the two new oracles contain reflection on vanilla internals
(packet queue + `canHitEntity`) that is field/method-name verified via javap but unproven
at runtime. Commits: `b234051` (code), `e504483` (tests).

## Detail

### B1 — sound sink hole (F1)
javap-verified on the mapped 1.21.8 merged jar: `playCombinationStepSounds`,
`playMuffledStepSound`, `playSwimSound`/`waterSwimSound`, `LivingEntity.playBlockFallSound`,
`LivingEntity.causeFallDamage` (via `getFallDamageSound`), `playSecondaryHurtSound`, and
`LivingEntity.makeSound` itself ALL invoke `Entity.playSound(SoundEvent,float,float)`,
whose body is a bare `level.playSound(null, x,y,z, ...)` server broadcast. Single funnel
confirmed — overriding it covers every listed path plus `playSound(SoundEvent)` (delegates).

Fix in `CursedSpiritEntity.java`:
- New `@Override public void playSound(SoundEvent, float, float)` (~:596): server side calls
  `sendSoundToPerceivers`; client side falls through to `super.playSound` (local playback,
  tracking already removed non-perceivers).
- `sendSoundToPerceivers(ServerLevel, SoundEvent, float, float)` (~:617): the extracted
  shared send loop — one `ClientboundSoundPacket` per perceiver in range, using the
  existing pure rule `voiceReaches(perceives, distSqr, rangeSqr)`.
- `makeSound` (~:559) now just delegates to `playSound(sound, getSoundVolume(),
  getVoicePitch())` — identical to vanilla's own body, so voices and mechanical sounds
  share one audience path; the isSilent check lives in `playSound`.

### B2 — projectile owner chain (F3)
javap notes: vanilla already stores the shooter as `DamageSource` *causing* entity for
arrow/trident/thrown (`DamageSources.arrow(AbstractArrow, Entity)`), but
`mobProjectile`-style sources and TNT keep the projectile as causing entity with the real
actor behind `getOwner()`. `Entity` has NO `getOwner()`; owners resolve via
`Projectile.getOwner()` / `OwnableEntity.getOwner()`.

- `CursePerception.responsibleParty(Entity)` (CursePerception.java:~112): walks
  Projectile→getOwner / OwnableEntity→getOwner until a Player or root non-ownable,
  depth-capped at 8. `null` stays `null` (dispenser/world hazard = honest).
- `CursedSpiritInteractionGates.allowDamage` (~:32): resolves
  `responsibleParty(getEntity() != null ? getEntity() : getDirectEntity())`.
  Curse→player arm: `victim instanceof Player && !canInteract(victim) && (isSubject(attacker)
  || isSubject(responsible))` — also catches curse-owned projectiles whose causing entity
  is absent. Player→curse arm: `isSubject(victim) && responsible instanceof Player &&
  !canInteract(responsible)`.
- Anti-deflect/pass-through: javap shows `Entity.canBeHitByProjectile()`/`isPickable()`
  are no-arg (no pair context) and `Entity.deflection` only returns non-NONE for the
  `deflects_projectiles` tag (spirits aren't in it — no vanilla deflect path). The single
  pair-aware funnel is `Projectile.canHitEntity(Entity)` (HEAD-consults
  `target.canBeHitByProjectile`, then owner/passenger rules), so the honest fix needed a
  mixin: `CursedSpiritProjectileMixin` injects HEAD into
  `canHitEntity(Lnet/minecraft/world/entity/Entity;)Z` and returns false when the target
  is a curse subject and the resolved owner is a non-interacting player. Registered in
  `jujutsumod.mixins.json`. Effect: the projectile never selects the spirit — no damage
  (already refused), no arrow stick, no deflect leak.

### B3 — perceives vs canInteract seam (F2)
- `CursePerception.interacts(actor, subject)` — pair rule on `canInteract`;
  `mayTouch` kept (out-of-scope ability files still call it) but re-javadoc'd as the
  SENSORY rule; both reduce to one parametrized `excluded(actor, other, right)`.
- Re-pointed to `interacts`: `CursedSpiritEntity.setTarget` choke (~:542),
  `canCollideWith` (~:557), the tick() live-target drop (~:457), `CursedSpiritPushMixin`,
  and all three gate checks in `CursedSpiritInteractionGates` (damage both directions +
  `AttackEntityCallback` swing).
- Kept on `perceives` (sensory, verified correct): `sendSoundToPerceivers` audience,
  `CursedSpiritTrackingMixin`, `PerceiverLookAtPlayerGoal`, all VFX audience filters in
  ability/effects (out of scope anyway), `CurseRenderGate`.
- `NearestAttackableTargetGoal` already read `canInteract` — untouched.

### B4 — entity misc
- `finalizeSpawn` (:277): `@Override` restored.
- `tick()` (:460): `isAlive()` guard before `abilityBrain.tick` — no regen/VFX on the
  corpse during the death window.
- `checkSpawnRules` (:307): day roll + crowd cap now apply ONLY to `NATURAL` and
  `CHUNK_GENERATION` (vanilla's "natural" pair; `ignoresLightRequirements` is
  TRIAL_SPAWNER-only, unrelated). All other reasons get the bare `super.checkSpawnRules`
  gate — spawner/egg/command/summon no longer luck past daylight via the 0.6 roll.
- `readAdditionalSaveData` (:372): `getLong(ROLL_SEED).orElseGet(level().random::nextLong)`
  — absent key no longer collapses every loaded body onto seed 0.
- `registerGoals`: `PerceiverLookAtPlayerGoal` (private subclass, ~:503) — 1.21.8's
  `LookAtPlayerGoal` has NO `Predicate` ctor (javap: candidate filter is a ctor-built
  `TargetingConditions`), so it rechecks `CursePerception.perceives(lookAt)` after
  `super.canUse()`. Nearest-wins nuance documented in javadoc.

### Tests (all new oracles carry red-proof notes)
- `CursedSpiritSoundSinkTest` (JUnit, source-pin): playSound funnel overridden; server
  path routes to `sendSoundToPerceivers` and never calls `level().playSound`; makeSound
  delegates; exactly one `player.connection.send` loop exists.
- `CursePerceptionGateSplitTest` (JUnit, source-pin): interacts↔canInteract /
  mayTouch↔perceives pinned; gates file contains zero `perceives`; owner-chain +
  entity contact gates read `interacts`; tracking keeps `perceives`. (A
  (true,false) flag subject can't be fabricated headless — vessels are enum-keyed and
  only NONE/PERCEIVER exist — so the seam is pinned at source level; documented.)
- `CursedSpiritPerceptionGameTests.spiritSoundsReachPerceiversOnly`: drains each victim's
  real loopback `EmbeddedChannel` outbound queue (reflective `connection`/`channel`
  fields — names javap-verified), calls `spirit.playSound(...)`, asserts a
  `ClientboundSoundPacket` at the spirit's position for the mage and none for NONE.
- `...nonPerceiverProjectileCannotHitSpirit`: reflective `canHitEntity` on real
  `setOwner`-ed arrows (NONE→false, MEGUMI→true — exercises the mixin HEAD) plus
  `ALLOW_DAMAGE.invoker().allowDamage` on `new DamageSource(arrowType, arrow)` — causing
  entity deliberately absent so the owner chain is the only path to the verdict.
- `CursedSpiritGradeGameTests.corruptNbtFallsBackToValidRoll`: Grade=99, NaN StatHp,
  empty tag → valid fallback re-roll; valid-save-minus-RollSeed → non-zero seed + grade
  kept.

## Evidence
- javap (loom minecraftMaven mapped merged 1.21.8 jar, temurin21):
  `Entity.playSound(SoundEvent,FF)` body = `level.playSound(null,...)` broadcast;
  callers listed above all invokevirtual it. `LivingEntity.makeSound` → `playSound`.
  `LookAtPlayerGoal` ctors: `(Mob,Class,float)`, `(…,float,float)`, `(…,float,float,boolean)`
  — no Predicate overload. `Projectile.canHitEntity(Entity)` calls
  `Entity.canBeHitByProjectile()` then owner/passenger; `hitTargetOrDeflectSelf` consults
  `entity.deflection(projectile)`; `Entity.deflection` = `deflects_projectiles` tag ?
  REVERSE : NONE. `DamageSource(Holder,Entity)` ctor exists (direct-only). `Connection`
  has `private io.netty.channel.Channel channel`; `ServerCommonPacketListenerImpl` has
  `protected final Connection connection`; `ValueInput.getLong(String)` returns
  `Optional<Long>`; `EntitySpawnReason` enum includes NATURAL/CHUNK_GENERATION.
- `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileJava compileTestJava
  compileGametestJava` — green (only pre-existing deprecation warnings).
- `./gradlew test` — all suites green, 0 failures (incl. `CursedSpiritSoundSinkTest` 3/3,
  `CursePerceptionGateSplitTest` 5/5); verified via `build/test-results/test/*.xml`
  (no failures=">0"/errors=">0").
- Commits: `b234051` (6 files, +221/-58), `e504483` (4 test files, +390).

## Rejected / Open
- Rejected: overriding `canBeHitByProjectile`/`isPickable` for the projectile gate —
  no-arg methods can't see the shooter; mixin on `canHitEntity` is the narrow honest fix
  (javadoc-justified, descriptor javap-verified).
- Rejected: deleting `mayTouch` — ability/effects files are out of scope and still call
  it; it is now documented as the sensory premise. Their damage still funnels through
  ALLOW_DAMAGE (interacts), so the seam holds end-to-end regardless.
- Open (tester): new GameTests unexecuted here — reflection on `Connection.channel`
  outbound queue and `Projectile.canHitEntity` is signature-verified but not runtime-
  proven. If `readOutbound` yields encoded/wrapped packets, adjust the drain helper.
- Open: PATROL/EVENT/REINFORCEMENT spawn reasons now take the bare vanilla gate (no day
  roll, no cap) — matching the brief's NATURAL/CHUNK_GENERATION-only rule; flag if design
  wants event-spawns to count as natural.
- Open: spirit no longer spawns from spawners/eggs in daylight at all (previously 60%
  via the roll) — intended per brief ("must NOT get the day roll").
- Scope note: `jujutsumod.mixins.json` edited to register the allowed mixin — minimal
  required extension of the allowed-file list.
- Not done (out of scope): Codex doc `03-systems/Cursed-spirits.md` still describes the
  old single-sink voice line — doc owner should refresh.
