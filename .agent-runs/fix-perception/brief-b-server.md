# Brief B — Server-side perception: sound sinks, projectiles, seam, entity misc

RUN_ID: `fix-perception` · Worktree: `D:/WorkFlow/jm-wt-perception` (branch `fix/review-perception`) · Artifact: `.agent-runs/fix-perception/worker-b-server.md`

You own the server half of the post-merge review fixes for the cursed-spirit perception domain. Spec sources (read the cited regions yourself): `D:/WorkFlow/jujutsu-minecraft/audit/review-3pr/reviewer-pr89-perception.md` (F1–F4, F6) and `reviewer-pr89-grade-spawn.md` (F3, F4).

## Findings to fix (all in your owned files)

### B1. Sound sink hole (#90 п.10 + N4, reviewer F1 — Important)
`CursedSpiritEntity.makeSound` (~:553-592) is NOT the only voice sink. javap-verified on 1.21.8: `Entity.playCombinationStepSounds`, `playMuffledStepSound`, `playSwimSound`/`waterSwimSound`, `LivingEntity.playBlockFallSound`, `LivingEntity.causeFallDamage` (getFallDamageSound), `playSecondaryHurtSound` (thorns) call `Entity.playSound(SoundEvent,float,float)` → `level.playSound(null, x,y,z,...)` = broadcast to ALL players in radius, bypassing tracking and makeSound. A non-perceiver hears footsteps/falls/swims of an invisible spirit.

Fix: intercept the sink(s) in `CursedSpiritEntity` — override `playSound(SoundEvent,float,float)` (verify it is the single funnel via javap of the listed paths on the mapped 1.21.8 jar; if a path bypasses it, override that path too) and route through the same per-perceiver `ClientboundSoundPacket` loop `makeSound` uses. Extract a shared "send to perceivers only" helper — don't duplicate the packet loop.

### B2. Projectile owner chain (#90 п.11, reviewer F3)
`perception/CursedSpiritInteractionGates.java:40` — gate only checks `source.getEntity() instanceof Player`. Arrows/tridents/TNT from a non-perceiver still hurt the spirit; and the spirit's deflect path would visually bounce a projectile "off thin air", leaking its position.

Fix:
- In `allowDamage` (both directions where applicable), walk the owner chain: `source.getEntity()` → `Projectile.getOwner()` / `Entity.getOwner()` until a Player or null; evaluate perception on the resolved player. A projectile with a non-perceiver owner must not damage the spirit.
- Anti-deflect: the spirit must not deflect/block projectiles whose owner can't perceive it — check `Entity.canBeHitByProjectile`/`isPickable` override options on `CursedSpiritEntity` (javap-check how 1.21.8 projectiles select deflectable targets; `Projectile#mayHit`/`canHitEntity` consult `canBeHitByProjectile`). If the honest fix needs a main-side mixin, it must be `src/main/java/jujutsu/mod/mixin/CursedSpirit*Mixin.java` (that glob is yours) — narrow, javap-verified, javadoc-justified.

### B3. perceives vs canInteract seam (#90 п.9, reviewer F2 — latent contract break)
`PerceptionFlags` declares two rights: perceiving (see/hear) vs interacting (target/damage/be-damaged/collide). Today only the target selector reads `canInteract`; everything else reads `perceives`:
- `perception/CursePerception.java:76-82` — `mayTouch` → perceives
- `CursedSpiritInteractionGates.java:32-44` — ALLOW_DAMAGE both sides → perceives
- `CursedSpiritEntity.java:533` setTarget choke → mayTouch; `:548` canCollideWith → mayTouch; push mixin path
- `CursedSpiritEntity.java:491` NearestAttackableTargetGoal → canInteract (correct already)

Fix: interaction gates (damage both directions, melee swing callback, push, collision, setTarget/HurtBy retaliation) must read `canInteract`; sensory gates (tracking, render, sound, VFX audience) keep `perceives`. Audit every `perceives`/`mayTouch` call site in your files and classify. Rename/parametrize `mayTouch` so the checked right is evident (e.g. `canTouch`→`interacts`, or add a `CursePerception.interacts(entity)` beside `perceives`). Numbers stay in profile classes; no literal-flag soup.

### B4. Entity misc (#90 items, reviewer grade-spawn F3 + review summary)
In `CursedSpiritEntity.java`:
- ~:306-321 — the daylight roll (0.6) must apply ONLY to natural spawns: gate on `MobSpawnType.NATURAL` (and CHUNK_GENERATION per spawn semantics — check how vanilla counts "natural"; spawner/MOB_SUMMONED must NOT get the day roll). Read `checkSpawnRules` first; the day-roll currently likely sits in `finalizeSpawn`/`checkSpawnRules` — put it on the correct NATURAL branch only.
- ~:370-377 — NBT without `RollSeed` → seed=0 → identical ability pools. Add a fallback re-roll seed (e.g. `level().random.nextLong()`) when the key is absent, consistent with the existing grade-fallback path.
- ~:458 — `brain.tick` runs with no alive gate → regen/VFX on the corpse in the ~20-tick death window. Add `isAlive()` guard before `brain.tick` (do NOT touch `CursedSpiritAbilityBrain.java` — out of scope).
- ~:276 — restore the lost `@Override` on `finalizeSpawn`.
- `registerGoals` `LookAtPlayerGoal(this, Player.class, 8.0f)` — add the perception predicate (`CursePerception::perceives` — spirits must not stare at non-perceivers; LookAtPlayerGoal accepts a `Predicate<LivingEntity>` in 1.21.8 — verify signature).

## Env / rules

- Windows bash. Gradle: `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...` (PATH java is 1.8, do not use).
- javap (temurin21 `bin/javap`) on the loom-cache mapped 1.21.8 jar to verify every signature you touch.
- Small conventional English commits; `git add` only your files.
- If a gradle run fails on lock/port collision with parallel work, retry once; report persistent failures honestly.

## Allowed files

- `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritEntity.java`
- `src/main/java/jujutsu/mod/cursedspirit/perception/*`
- `src/main/java/jujutsu/mod/mixin/CursedSpirit*Mixin.java` (only if strictly needed; javadoc justification required)
- `src/test/java/jujutsu/mod/cursedspirit/**` and `src/gametest/java/jujutsu/mod/gametest/CursedSpirit*GameTests.java` — write/adjust tests for your fixes (see below)

**Do NOT edit:** `CursedSpiritAttackGoal.java`, `cursedspirit/ability/**`, `cursedspirit/CursedSpiritShelter*.java`, `CursedSpiritSpawn*.java`, client files, Megumi*. Another worker owns client/render.

## Tests (write for your fixes)

- Sound sink: oracle that a non-perceiver does NOT receive step/fall sounds. Reviewer suggestion: route all voice through one testable seam (e.g. a `playForPerceivers(SoundEvent,...)` method / packet-recipient resolver) and test that seam directly — packet-level interception is fine as a GameTest on sent packets or a JUnit on the seam.
- Projectile: non-perceiver-owned projectile does not hurt / does not deflect off the spirit (GameTest or JUnit on the gate logic — pick what honestly exercises the new code).
- Seam: a (perceives=true, interacts=false) flag subject is not damaged / not targeted (pin both directions).
- NBT junk: grade=99, NaN stats, missing keys, missing RollSeed → valid re-roll (fallback path proven).
Each new oracle must be proven non-vacuous (red-mutant note in the report: what you broke to see it fail).

## Verify

`JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileJava compileTestJava test` (targeted `--tests` ok mid-work; leave the heavy GameTest lane to the tester).

## Report

Full report → Artifact path (Summary / Detail per finding B1–B4 / Evidence: commands+javap outputs / Rejected-Open). Return ≤10 lines: status, artifact path, commits, concerns.
