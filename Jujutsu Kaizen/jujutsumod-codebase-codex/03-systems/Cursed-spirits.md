# Cursed Spirits

Status: CURRENT (slice landed on `feat/cursed-spirits`; balance is first-pass)

Three hostile cursed-spirit tiers — `lesser_cursed_spirit`, `cursed_spirit`, `greater_cursed_spirit` — whose **gameplay belongs to the tier** and whose **look belongs to a presentation variant** imported from the Sons of Sins asset pack (provenance in `docs/PROVENANCE.md`). One entity class, one variant enum, one profile: no per-variant gameplay.

## Shape

- `CursedSpiritEntity extends Monster` with the tier as a constructor argument (`CursedSpiritTier`), registered three times (`src/main/java/jujutsu/mod/registry/JujutsuEntities.java`); attributes via `FabricDefaultAttributeRegistry` in `CursedSpirits.registerServerHooks()`.
- `CursedSpiritProfile` owns **every** balance number (per-tier row + the shared crowd cap); logic reads it at runtime, never caches it at construction.
- `CursedSpiritVariant` owns presentation *and* the in-tier spawn weight: PROWLER/FLOATING_CURSE/GULBER (lesser), KELVIN/BUTCHER/GUZZLER/BLUD (common), WALKING_BED/WISTIVER (greater). The variant is synced (ordinal) and persisted by **string id** under the NBT key `Variant`, validated against the tier on load.
- Tier stats (first pass, all in `CursedSpiritProfile`): lesser 14 HP / 3 dmg / 0.30 speed / weight 65 / group 2–4; common 45 / 5 / 0.24 / 20 / 1–2; greater 150 / 8 / 0.17 / kb-res 0.85 / stagger ×0.4 / AoE r 3.5 / 5 / 1–1. Windup, cooldown, reach, knockback, lunge step, xp and the AoE row live beside them.
- Brain: `CursedSpiritAttackGoal` (APPROACH → WINDUP → STRIKE → RECOVER) reads live stats through `CursedSpiritAttackPolicy` (pure); the goal acquires targets via `NearestAttackableTargetGoal<Player>` + `HurtByTargetGoal` and pauses while `CombatStagger.GLOBAL.isStaggered`.

## Combat-core ties (shared systems, no new ones)

- Stagger: `jujutsu.mod.combat.StaggerResistant` — `CharacterCombatModifiers.adjustedStaggerTicks` asks the body; the tier multiplier floors at 1 tick.
- Boogie Woogie: greater spirits carry `jujutsumod:boogie_woogie_immune` and `TodoBoogieWoogieRuntime.isEligibleTarget` refuses them; lesser/common swap like any monster.
- Nails/resonance: ordinary nail impacts mark and (threshold 2) mint a remnant; a cursed spirit's type is in `jujutsumod:resonance_remnant_curse`, so the minted visual is CURSE (`RemnantVisualType.classify`).
- Megumi: sic resolves cursed spirits as ordinary eligible targets.

## Rendering (deliberate deviation from the GeckoLib house style)

The pack ships vanilla-API Java models and `AnimationDefinition` classes, so the client stack is a **near-verbatim vanilla port** (`src/client/java/jujutsu/mod/client/render/cursedspirit/**`): 9 `EntityModel<CursedSpiritRenderState>` models with one bake per rig, 9 animation classes, one `CursedSpiritRenderer` (three instances) that dispatches the per-variant rig the way `TropicalFishRenderer` does (`this.model = rigs.get(state.variant); super.render(...)`), and `CursedSpiritClient.register()` for layers + renderers. State sync uses the vanilla Warden pattern with frozen byte ids: `ATTACK_START=61`, `SCREAM_START=62`, `ATTACK_END=63`, `SCREAM_END=64` (stop-then-start on starts, `stop()` on ends, `SCREAM_DURATION_TICKS=12`); FLOATING_CURSE glides (the pack authored no walk clip) and `WISTIVER.GRAVE` is not shipped (no driver state).

## Spawning

- Natural pipeline only: `BiomeModifications.addSpawn(foundInOverworld(), MONSTER, type, weight, min, max)` from `CursedSpiritSpawnIntegration` — **no** `SpawnPlacements` (its `register` is private in 1.21.8 and there is no Fabric wrapper) and no access widener.
- The gate lives in `CursedSpiritEntity.checkSpawnRules`: explicit `Difficulty.PEACEFUL → false`, then `super` (`PathfinderMob`'s walk-value light gate — refuses at local brightness ≳12, dim 8–12 passes; deliberately not re-implementing vanilla's rolled darkness), then the local crowd cap `MAX_SPIRITS_NEARBY = 10` within `CROWD_RADIUS = 48` **for `EntitySpawnReason.NATURAL` only** — a spawner, spawn egg or command places its spirit regardless of the crowd, vanilla-consistent because population pressure governs natural spawning (and it keeps the cap conjunct out of a shared-level GameTest oracle).
- No daylight burning (plain `Monster`; they spawn dark and persist); peaceful spawns nothing.

## Verification boundary

Automated: profile/variant/policy/registry/tag JUnit, resource + sound-symmetry contracts, rig/animation contracts (clip keys, bone sets, looping flags, clip counts, exact lengths), and GameTests for AI/strike/AoE/knockback/swap/stagger/cap/spawn-light plus the cross-character integration scenarios (nail mark → Hairpin, remnant mint, Black Flash, sic).

Manual only (cannot be automated headless): sound firing/coherence per tier, animation timing feel, variant distinctness and silhouette read at distance, world spawn feel + soak comfort (census ≤ cap, TPS ≥ 18, zero `jujutsumod` ERROR), hitbox visual fit, and the per-tier menace of a greater spirit. Run them with the MCP dev lane; the protocol and the tool quirks live in `.superpowers/rule-of-four/cursed-spirits/live-protocol.md`.

## Tuning pointers

Balance: `CursedSpiritProfile` (rows + caps). Look/scale/weights/sounds: `CursedSpiritVariant`. Attack feel: the policy/goal read the profile, so a number change there is global. Spawn pressure: the three `addSpawn` rows + `MAX_SPIRITS_NEARBY`. Balance is a first pass — treat the table as the tuning surface, not as a contract.
