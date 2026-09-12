# Cursed Spirits Implementation Plan (rev 2 — post-review)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship three hostile cursed-spirit gameplay tiers (`lesser_cursed_spirit`, `cursed_spirit`, `greater_cursed_spirit`) whose AI/stats/spawn rules are tier-owned and whose look (model, texture, animations, sounds) is chosen per presentation variant from the Sons of Sins asset pack — no per-variant gameplay, one shared system.

**Architecture:** One concrete `CursedSpiritEntity` (extends `Monster`) instantiated three times with a tier constructor argument; one enum `CursedSpiritVariant` owns presentation + spawn-weight data; one `CursedSpiritProfile` owns every balance number per tier. Client rendering uses the **vanilla** model/animation stack (ported near-verbatim from the pack's vanilla-API model and `AnimationDefinition` classes) — one renderer class (three instances) with per-variant model dispatch, render states per vanilla 1.21.8 conventions, animation states synced via entity events. Natural spawning via `SpawnPlacements` + Fabric `BiomeModifications.addSpawn`, plus a local crowd cap.

**Tech Stack:** Fabric 1.21.8, Java 21, Minecraft vanilla model/animation APIs (`EntityModel<EntityRenderState>`, `LayerDefinition`, `AnimationDefinition`/`KeyframeAnimation`/`AnimationState`), Fabric biome API, existing project combat core. GeckoLib stays for its existing users (vessels, shikigami); cursed spirits deliberately do not use it.

## Global Constraints

- Server-authoritative gameplay; nothing client-only in `src/main`; rendering/anim data live in `src/client`.
- Public Minecraft/Fabric APIs only; no new mixins.
- All balance numbers live in `CursedSpiritProfile` (tiers + shared constants) and `CursedSpiritVariant` (weights/scale-only presentation). No magic constants in brains/goals.
- New behavior ships with tests; every new check must be observed failing on a mutation before it is trusted.
- `qualityGate` decides the automated barrier (JUnit + GameTests + docs audit + jar isolation); Windows: `gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` with `JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current`.
- MOC metrics in `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` must be bumped or `auditDocumentation` fails.
- Asset provenance: Sons of Sins 2.2.1d (NeoForge 1.21.1) jar; author permitted use and adaptation via owner statement 2026-09-12; record in `docs/PROVENANCE.md` + `docs/THIRD_PARTY_NOTICES.md` with the `MANIFEST.csv` sha256 manifest of shipped files.
- Manual-only checks (cannot be automated): sound firing/coherence; animation timing feel; variant distinctness eyeball; world spawn feel; hitbox visual fit — each gets a stated manual method in the Codex verification boundary and `progress.md`.
- Report shape for workers: `block-N-report.md`, every closed acceptance item carries `Факт: <command/observation> → <result>`.

## Frozen cross-block contracts

Entity ids / types:

| id | class | tier | hitbox (w×h) | MobCategory |
|---|---|---|---|---|
| `lesser_cursed_spirit` | `CursedSpiritEntity` (tier `LESSER`) | T1 | 0.85 × 1.0 | MONSTER |
| `cursed_spirit` | `CursedSpiritEntity` (tier `COMMON`) | T2 | 0.75 × 1.9 | MONSTER |
| `greater_cursed_spirit` | `CursedSpiritEntity` (tier `GREATER`) | T3 | 1.35 × 2.4 | MONSTER |

Server packages: `src/main/java/jujutsu/mod/cursedspirit/`. Client packages: `src/client/java/jujutsu/mod/client/render/cursedspirit/`.

Variant roster (frozen; `CursedSpiritVariant` enum constant → tier):
- LESSER: `PROWLER`, `FLOATING_CURSE`, `GULBER`
- COMMON: `KELVIN`, `BUTCHER`, `GUZZLER`, `BLUD`
- GREATER: `WALKING_BED`, `WISTIVER`

### Frozen per-variant asset table (source files → shipped names)

| Variant | source texture | source sounds (channel → file) | clips to ship (from the decompiled animation class) |
|---|---|---|---|
| PROWLER | `prowler.png` | ambient `prowler_ambient`; hurt `prowler_hurt`; death `prowler_death`; scream `prowler_scream` | IDLE, WALK, ATTACK, SCREAMER (drop CLIMB: == WALK) |
| FLOATING_CURSE | `curse.png` | ambient `curse_ambient`; hurt `curse_hurt`; death `curse_death`; scream `curse_scream` | IDLE, ATTACK, SCREAMER (from `curse_ghostAnimation`) |
| GULBER | `gubler.png` (upstream typo) | ambient `wight_ambient_1`; hurt `wight_hurt_1`; death `wight_death_1`; no scream | IDLE, WALK, ATTACK (drop SIT) |
| KELVIN | `kelvin.png` | ambient `kelvin_ambient`; hurt `kelvin_hurt`; death `kelvin_death`; scream `kelvin_scream` | IDLE, WALK, ATTACK, SCREAMER (SCREAMER: retarget `left_arm`/`right_arm` → `leftarm`/`rightarm`) |
| BUTCHER | `butcher.png` | ambient `butcher_ambient`; hurt `butcher_hurt`; death `butcher_death`; scream `butcher_scream` | IDLE, WALK, ATTACK, SCREAMER |
| GUZZLER | `guzzler.png` | ambient `guzzler_ambient`; hurt `guzzler_hurt_1`; death `guzzler_death`; no scream | IDLE, WALK, ATTACK |
| BLUD | `blud.png` | ambient `blud_ambient`; hurt `blud_hurt`; death `blud_death`; scream `blud_scream` | IDLE, WALK, ATTACK, SCREAMER (drop TELEPORT_IN/OUT) |
| WALKING_BED | `walking_bed.png` | ambient `walking_bed_ambient`; hurt `walking_bed_hurt`; death `walking_bed_death`; scream `walking_bed_scream` | IDLE, WALK, ATTACK, SCREAMER (drop MASK, CRAWL_IDLE, CRAWL_WALK) |
| WISTIVER | `wistiver.png` | ambient `wistiver_ambient`; hurt `wistiver_hurt`; death `wistiver_death`; scream `wistiver_scream` (`wistiver_screamer` unused) | IDLE, WALK, ATTACK, SCREAMER (drop GRAVE: no driver state — same rule as CLIMB/SIT/MASK/CRAWL_*) |

34 sound channels total (4×7 + 3×2). Names: `assets/jujutsumod/textures/entity/cursed/cursed_<variantLower>.png`; `assets/jujutsumod/sounds/cursed/cursed_<variantLower>_<channel>.ogg`; `sounds.json` key `cursed.<variantLower>_<channel>`; `JujutsuSounds` constant `CURSED_<VARIANT_UPPER>_<CHANNEL_UPPER>`; lang `entity.jujutsumod.<entity_id>` + `subtitles.jujutsumod.cursed.<variantLower>_<channel>`.

### Frozen clip bone map (from `analysis/rig_audit.py` — the port contract test pins these)

- PROWLER (parts 17): IDLE {body,head,in_left_leg,in_right_leg,left_arm,right_arm}, WALK same, ATTACK same, SCREAMER {body,flatter,head,left_arm,right_arm}.
- FLOATING_CURSE (parts 6): IDLE {body,bone,head}, ATTACK {body,head,spine}, SCREAMER {body,flater,head}.
- GULBER (parts 9): IDLE {body2,head}, WALK {body2,head,left_foot,left_foot2,right_foot,right_foot2}, ATTACK {body,body2,bone,head,left_foot,left_foot2,right_foot,right_foot2}.
- KELVIN (parts 20): IDLE {body,head,leftarm,rightarm}, WALK {body,head,left_leg,leftarm,right_leg,rightarm}, ATTACK {body,bone6,bone8,head,left_leg,leftarm,rightarm}, SCREAMER {body,bone4,flater,head,leftarm,left_leg,rightarm,right_leg} (after retarget fix).
- BUTCHER (parts 17): IDLE {body,head,left_arm,right_arm}, WALK {body,bone,head,left_arm,left_leg,right_arm,right_leg}, ATTACK {body,bone,flater,head,left_arm,left_leg,right_arm}, SCREAMER {body,flater,head,left_arm,left_leg,right_arm,right_leg}.
- GUZZLER (parts 8): IDLE {body2,jaw,left_arm}, WALK {body2,jaw,left_arm,left_leg,right_arm,right_leg}, ATTACK {body2,jaw,left_arm,right_arm}.
- BLUD (parts 16): IDLE {bone2,bone4,chest,head,right_arm,stain}, WALK {bone,chest,head,left_arm,stain}, ATTACK {bone,bone2,chest,head}, SCREAMER {bone,bone2,bone6,chest,flater,head,left_arm,right_arm,stain}.
- WALKING_BED (parts 35): IDLE {body,bone7,bone9,head,head2,left_arm,right_arm}, WALK {body,left_arm,left_leg,right_arm,right_leg}, ATTACK {arm,arm4,body,bone,bone6,flatter,head,head2,left_arm,left_leg,right_arm}, SCREAMER {body,flatter,head,left_arm,left_leg,right_arm,right_leg}.
- WISTIVER (parts 31): IDLE {bod,bone2,head,left_arm,right_arm}, WALK {body}, ATTACK {bod,bone,bone2,head,jaw,jaw2,left_arm,left_arm2,left_front_arm,right_arm,right_arm2,right_front_arm,top_jaw,top_jaw2}, SCREAMER {bod,flater,head,left_arm,right_arm} (GRAVE dropped — no driver state).

Looping flags (as authored, preserved): all clips `looping` EXCEPT PROWLER.ATTACK, FLOATING_CURSE.ATTACK, KELVIN.ATTACK, GUZZLER.ATTACK, BLUD.ATTACK (non-looping). Playback is state-driven either way.

### Public API of the server module (other blocks compile against these)

```java
public enum CursedSpiritTier { LESSER, COMMON, GREATER }

public enum CursedSpiritVariant {
    PROWLER, FLOATING_CURSE, GULBER, KELVIN, BUTCHER, GUZZLER, BLUD, WALKING_BED, WISTIVER;
    public CursedSpiritTier tier();
    public String id();                 // lowercase, e.g. "prowler"
    public int weight();                // weighted roll inside the tier
    public float renderScale();         // client presentation
    public float renderOffsetY();       // client presentation
    public String texture();            // "textures/entity/cursed/cursed_prowler.png"
    public SoundEvent ambientSound(); public SoundEvent hurtSound(); public SoundEvent deathSound();
    public SoundEvent screamSound();    // nullable
}

public record CursedSpiritTierStats(
    double maxHealth, double attackDamage, double movementSpeed, double followRange,
    double knockbackResistance, double staggerMultiplier,
    int attackWindupTicks, int attackCooldownTicks, double attackReach,
    double attackKnockback, double strikeStep, double aoeRadius, double aoeDamageScale,
    double aoeKnockback, int xpReward, int spawnWeight, int spawnMinGroup, int spawnMaxGroup) {}

public final class CursedSpiritProfile {
    public static final int MAX_SPIRITS_NEARBY = 10;      // local crowd cap (radius 48)
    public static final double CROWD_RADIUS = 48.0;
    public static CursedSpiritTierStats of(CursedSpiritTier tier);
}

public class CursedSpiritEntity extends Monster implements StaggerResistant {
    public CursedSpiritEntity(EntityType<? extends CursedSpiritEntity> type, Level level, CursedSpiritTier tier);
    public CursedSpiritTier tier(); public CursedSpiritVariant variant();
    public void setVariant(CursedSpiritVariant variant);      // server + tests
    public static AttributeSupplier.Builder createAttributes(CursedSpiritTier tier);
    public int adjustIncomingStaggerTicks(int ticks);
    public static CursedSpiritVariant rollVariant(CursedSpiritTier tier, RandomSource random);
}

public interface jujutsu.mod.combat.StaggerResistant { int adjustIncomingStaggerTicks(int ticks); }
public final class jujutsu.mod.combat.CombatTags {
    public static final TagKey<EntityType<?>> BOOGIE_WOOGIE_IMMUNE;
    public static boolean isBoogieWoogieImmune(Entity target);
}
```

Persistence contract: the variant is saved by **string id under NBT key `Variant`** (`addAdditionalSaveData`), validated against its tier on load (mismatch → re-roll); unknown id → re-roll. Animation sync contract (frozen): `ATTACK_START=61`, `SCREAM_START=62`, `ATTACK_END=63`, `SCREAM_END=64`; the server logic broadcasts them, `handleEntityEvent` on the client (and the server's own copy) stops+starts/ stops the corresponding `AnimationState`; `idleAnimationState` starts in `tick()` on both sides. No death animation event — vanilla `deathTime` covers death.

Client contract: `CursedSpiritClient.register()` (renderers + layers) called from `JujutsuModClient`; per-variant rigs keyed by `CursedSpiritVariant`; `CursedSpiritRenderState` carries `variant` + `AnimationState idle/attack/scream` copied from the entity in `extractRenderState` (vanilla `WardenRenderer`/`WardenModel` pattern).

---

## Task 1 (Block 1) — Asset import: textures, sounds, lang, provenance

**Requirements:** R2 (asset half), R22 (paths), R23 (data half), R28 (provenance half)

**Files:**
- Create: `src/main/resources/assets/jujutsumod/textures/entity/cursed/cursed_<variantLower>.png` (9 files)
- Create: `src/main/resources/assets/jujutsumod/sounds/cursed/cursed_<variantLower>_<channel>.ogg` (34 files)
- Modify: `src/main/resources/assets/jujutsumod/sounds.json` (34 entries)
- Modify: `src/main/resources/assets/jujutsumod/lang/en_us.json`, `ru_ru.json`
- Modify: `docs/PROVENANCE.md`, `docs/THIRD_PARTY_NOTICES.md`
- Create: `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritResourceContractTest.java`

**Interfaces:** produces the `.ogg`/`.png`/lang/sounds.json half of the frozen table; `JujutsuSounds` constants are **Block 2's** (this block only adds the data files they point at).

**Out-of-scope:** model/animation Java (Block 3), entity code + `JujutsuSounds.java` (Block 2), spawn (Block 4).

- [ ] **Step 1:** Copy the frozen table's sources: sounds from `.superpowers/rule-of-four/cursed-spirits/assets/raw/assets/sons_of_sins/sounds/`, textures from `.../textures/entities/`; rename to the frozen shipped names (texture map: WISTIVER→`wistiver.png`, FLOATING_CURSE→`curse.png`, GULBER→`gubler.png`, KELVIN→`kelvin.png`, BUTCHER→`butcher.png`, GUZZLER→`guzzler.png`, BLUD→`blud.png`, WALKING_BED→`walking_bed.png`, PROWLER→`prowler.png`). Verify each PNG decodes and each ogg starts with the `OggS` magic (the mono/44.1 kHz property was verified once during reconnaissance — `scout-3-report.md` addenda — and is not re-checked in Java).
- [ ] **Step 2:** Append the 34 `sounds.json` entries in the repo's existing shape; each gets `subtitle: "subtitles.jujutsumod.cursed.<key>"`.
- [ ] **Step 3:** Lang: entity names in both files (`Lesser Cursed Spirit` / `Мелкое проклятие`, `Cursed Spirit` / `Проклятие`, `Greater Cursed Spirit` / `Великое проклятие`), plus one subtitle per sounds.json key in BOTH files.
- [ ] **Step 4:** `CursedSpiritResourceContractTest` (file-level only; the frozen tables are pasted into the test as literals): every texture path exists and decodes as PNG; every variant/channel in the table has its `.ogg`; every sounds.json `cursed.*` key has subtitle + one existing sound file; both lang files contain the three entity keys and every subtitle key; the nine texture files are pairwise NON-identical (md5) — distinctness oracle. Run: `gradlew.bat test --tests "jujutsu.mod.cursedspirit.CursedSpiritResourceContractTest" --no-daemon` → PASS. Red-proofs: delete one sounds.json entry → fail; copy prowler.png over gulber's file → distinctness fails → restore.
- [ ] **Step 5:** Provenance docs: new section per repo format (source jar `sons_of_sins-2.2.1d-neoforge-1.21.1.jar`, owner permission statement 2026-09-12, evidence status, exact shipped paths, deliberately excluded files (all other creatures/props/organs, `wistiver_screamer.ogg`), MANIFEST.csv sha256 list for shipped files, "do not expand").
- [ ] **Step 6:** Commit: `feat(cursed-spirits): import Sons of Sins textures, sounds, lang`.

**Acceptance:** resource contract test green + both red-proofs recorded; 34 ogg + 9 textures + lang present. `Факт:` lines in `block-1-report.md`.

---

## Task 2 (Block 2) — Server core: entity, tiers, variants, AI, seams, tags

**Requirements:** R1, R3, R4, R5, R11, R12, R13, R14, R15, R16, R17 (tag half), R18 (weights data), R19 (cap data), R20, R24, R25

**Files:**
- Create: `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritTier.java`, `CursedSpiritVariant.java`, `CursedSpiritProfile.java`, `CursedSpiritEntity.java`, `CursedSpiritAttackGoal.java`, `CursedSpiritAttackPolicy.java`, `CursedSpirits.java`
- Create: `src/main/java/jujutsu/mod/combat/StaggerResistant.java`, `src/main/java/jujutsu/mod/combat/CombatTags.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuEntities.java` (3 types), `src/main/java/jujutsu/mod/JujutsuMod.java` (1 line), `src/main/java/jujutsu/mod/character/CharacterCombatModifiers.java` (stagger hook), `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java` (immune clause), `src/main/java/jujutsu/mod/registry/JujutsuSounds.java` (cursed rows)
- Create: `src/main/resources/data/jujutsumod/tags/entity_type/boogie_woogie_immune.json`; modify `src/main/resources/data/jujutsumod/tags/entity_type/resonance_remnant_curse.json`
- Create: `src/gametest/java/jujutsu/mod/gametest/CursedSpiritGameTests.java`, `CursedSpiritTestFixtures.java`; modify `src/gametest/resources/fabric.mod.json`
- Create tests: `src/test/java/jujutsu/mod/cursedspirit/{CursedSpiritProfileTest,CursedSpiritVariantTest,CursedSpiritAttackPolicyTest,CursedSpiritTagContractTest}.java`

**Depends on:** none (disjoint files; the server side compiles without asset files). **Parallel-safe with:** [Block 1] EXCEPT nothing shared.

**Dispatch gate (IMPORTANT):** commit the frozen public API FIRST as commit 1 — `CursedSpiritTier`, `CursedSpiritVariant`, `CursedSpiritProfile`, `CursedSpiritEntity` skeleton (ctor/tier/variant/attributes/sounds/goals registration), `JujutsuSounds` cursed rows, `JujutsuEntities` rows, `StaggerResistant`, `CombatTags`, seam edits, tag JSONs — then send Main `API landed` and continue. Blocks 3/4 are dispatched on that message. **The final Block-2 tree does NOT call `CursedSpiritSpawnIntegration`** (that one line is Block 4's serialized edit).
When the block is fully green, send Main `block-2 report filed` (this gates Block 4's `fabric.mod.json` + `CursedSpirits.java` edits and Block 5's registration edits).

**Interfaces:** consumes nothing from other blocks at compile time; produces the frozen public API above; `CursedSpirits.registerServerHooks()` = attributes only (spawn call added by Block 4).

**Out-of-scope:** rendering (Block 3), spawn rules/biome wiring (Block 4), docs (Block 5).

- [ ] **Step 1:** `StaggerResistant` interface + `CombatTags.BOOGIE_WOOGIE_IMMUNE` + `isBoogieWoogieImmune(Entity)` (javadoc: "argument is always > 0; return may be 0 for full resistance").
- [ ] **Step 2:** Edit `CharacterCombatModifiers.adjustedStaggerTicks`:

```java
public static int adjustedStaggerTicks(LivingEntity entity, int requestedTicks) {
    if (requestedTicks <= 0) return requestedTicks;
    if (entity instanceof ServerPlayer player) return JujutsuCharacters.of(player).adjustIncomingStaggerTicks(requestedTicks);
    if (entity instanceof StaggerResistant resistant) return resistant.adjustIncomingStaggerTicks(requestedTicks);
    return requestedTicks;
}
```
Run `gradlew.bat test --tests "jujutsu.mod.combat.*" --no-daemon` → PASS (existing stagger tests unchanged).
- [ ] **Step 3:** Edit `TodoBoogieWoogieRuntime.isEligibleTarget`: add `&& !CombatTags.isBoogieWoogieImmune(target)`. Tag JSON `boogie_woogie_immune.json` contains `greater_cursed_spirit`. Existing Todo swap tests stay green.
- [ ] **Step 4:** `CursedSpiritProfile` rows (initial balance, all tunable here):

| tier | HP | dmg | speed | follow | KB-res | stagger× | windup | cooldown | reach | KB | step | AoE r | AoE dmg× | AoE KB | xp | weight | group |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| LESSER | 14 | 3.0 | 0.30 | 16 | 0.0 | 1.0 | 5 | 20 | 2.0 | 0.4 | 0.0 | 0 | 0 | 0 | 5 | 65 | 2–4 |
| COMMON | 45 | 5.0 | 0.24 | 24 | 0.0 | 1.0 | 9 | 30 | 2.5 | 0.9 | 0.35 | 0 | 0 | 0 | 15 | 20 | 1–2 |
| GREATER | 150 | 8.0 | 0.17 | 32 | 0.85 | 0.4 | 14 | 60 | 3.0 | 1.0 | 0.0 | 3.5 | 0.6 | 1.2 | 40 | 5 | 1–1 |

- [ ] **Step 5:** `CursedSpiritVariant` enum (frozen roster/fields) + `JujutsuSounds` rows `CURSED_<VARIANT>_<CHANNEL>` for exactly the frozen channels (create + register). Weights: PROWLER 3, FLOATING_CURSE 2, GULBER 2, KELVIN 3, BUTCHER 2, GUZZLER 2, BLUD 1, WALKING_BED 1, WISTIVER 1. Render scales (initial, tuned by eye in Block 3): prowler 0.85, floating_curse 0.8 + offsetY 0.35, gulber 0.9, kelvin 0.95, butcher 0.9, guzzler 1.0, blud 0.9, walking_bed 0.9, wistiver 0.85.
- [ ] **Step 6:** `CursedSpiritEntity`: tier in ctor; `SynchedEntityData` variant ordinal (int, `defineSynchedData`); `rollVariant(tier, random)` weighted; `finalizeSpawn` rolls when unset; persistence by **string id under NBT key `Variant`** (`TodoStoneEntity` tag precedent) with tier validation on load (mismatch/unknown → re-roll); `createAttributes(tier)` (+`KNOCKBACK_RESISTANCE`, `FOLLOW_RANGE`); `adjustIncomingStaggerTicks` = `max(1, round(ticks × staggerMultiplier))` (floor 1 tick documented); sounds from variant; day-light: plain `Monster` (no burning, no undead mixin); `getBaseExperienceReward(ServerLevel)` override → profile xp (`getExperienceReward` is final — javap-verified); `checkSpawnRules(LevelAccessor, EntitySpawnReason)` override (Block 4).
- **Animation state machine (frozen byte ids; NO death event — vanilla `deathTime` covers death):** `ATTACK_START=61`, `SCREAM_START=62`, `ATTACK_END=63`, `SCREAM_END=64` (Warden precedent 61/62; avoid `LivingEntity`'s own 3, 46–52, 54, 55, 60, 65, 67, 68 — javap-verified). `handleEntityEvent(byte id)`: 61 → `attackAnimationState.stop(); start(tickCount)`; 63 → `stop()`; 62 → `screamAnimationState.stop(); start(tickCount)` + `screamTicks = SCREAM_DURATION_TICKS`; 64 → `stop()`. `SCREAM_DURATION_TICKS = 12` (all SCREAMER clips are 0.56 s in the pack = 11.2 ticks, rounded up) — pinned here, do not re-derive. `tick()` (both sides) starts `idleAnimationState` when stopped; server tick decrements `screamTicks` and broadcasts 64 at zero.
- Why stops are mandatory: `startIfStopped` never restarts a finished clip (2nd attack would be invisible) and the looping SCREAMER clip would hold 6 variants' walk gate forever (the ported `setupAnim`s gate walk on the scream state).
- Goals: `FloatGoal`, `CursedSpiritAttackGoal` (reads `getTarget()`), `RandomStrollGoal(0.8)`, `LookAtPlayerGoal(Player, 8)`, `RandomLookAroundGoal`; targetSelector: `NearestAttackableTargetGoal<Player>` + `HurtByTargetGoal` (without these the mob never acquires a target; mock-player targeting additionally needs difficulty != PEACEFUL + a non-spectator, non-invulnerable victim — asserted in Step 10).
- [ ] **Step 7:** `CursedSpiritAttackPolicy` (pure math only): `APPROACH→WINDUP→STRIKE→RECOVER`, `windupDone`, `strikeTargets` (single + AoE set), `cooldownTicks`. `CursedSpiritAttackGoal extends Goal`: requires `getTarget()` alive + LOS; at WINDUP start broadcasts `ATTACK_START` and calls `mob.swing(MAIN_HAND)`; at STRIKE applies `hurtServer(mobAttack(this))` to the target (+AoE bodies within `aoeRadius`, `aoeDamageScale`, `aoeKnockback`), `strikeStep` forward impulse, knockback; at RECOVER end broadcasts `ATTACK_END`; pauses while `CombatStagger.GLOBAL.isStaggered`. The entity's hurt path (`hurtServer` override) broadcasts `SCREAM_START` only when `variant.screamSound() != null` (GULBER/GUZZLER have none — frozen table).
- [ ] **Step 8:** Registration: `JujutsuEntities` 3 types (frozen ids, `MobCategory.MONSTER`, frozen hitboxes, `clientTrackingRange(96).updateInterval(3)`, no `.noSave()`); `CursedSpirits.registerServerHooks()` = `FabricDefaultAttributeRegistry.register ×3`; `JujutsuMod` one line `CursedSpirits.registerServerHooks();`. Tag JSON: add the three ids to `resonance_remnant_curse.json`.
- [ ] **Step 9:** JUnit (`CursedSpiritProfileTest`, `CursedSpiritVariantTest`, `CursedSpiritAttackPolicyTest`, `CursedSpiritTagContractTest`): **R1 registry contract** (three ids exist in `BuiltInRegistries.ENTITY_TYPE`, category == MONSTER, hitbox width/height == the frozen table, no `.noSave()`); profile invariants + weights ordering + group sanity; variant table completeness (every variant: texture non-null, sounds per frozen channel table, tier matches roster); `rollVariant` seeded distribution + empty/boundary; policy transitions + AoE membership + cooldown; **variant NBT round-trip** (string id save/load incl. unknown-id re-roll); tag JSON resources contain the three ids (curse tag) / one id (immune tag);
- **R16: TargetResolver comparator** — cursed-spirit entry ranks through the existing `TargetResolver` test seam like any mob (reuse `TargetResolverTest` patterns); **R20 linkage**: mutate a profile row (test-side copy) → assert the derived damage/knockback numbers change (anti-duplication proof that logic reads the profile, not literals). Red-proofs: weights-order flip; AoE membership flip.
- [ ] **Step 10:** GameTests (`CursedSpiritGameTests` + fixtures; mobile attacker must NEVER be `spawnWithNoFreeWill`; victim = mock player in survival, non-invulnerable, non-spectator, inside followRange with LOS; premise asserts BEFORE HP asserts): R3 lessers acquire (`getTarget()==victim` poll) then damage; R4 COMMON strike carries the `strikeStep` velocity burst at the strike tick; R5 GREATER AoE damages a second body inside radius and not one outside; R11 two damage sources both reduce HP, no invulnerability; R12 stagger present then cleared after the profile window (poll);
- R13 knockback velocity ordering LESSER>COMMON>GREATER under one impulse; R14 swap allow (LESSER AND COMMON positions exchange); R15 swap deny GREATER with positions unchanged + zero cooldown; R24 hitbox: melee reach test at the hitbox edge (damage lands inside/refused outside); R25 lethal `hurtServer` removal (`removeAndVerifyGone`); tags in-world (`type.is(...)` both tags). Register classes in `fabric.mod.json`.
- Per-scenario red-proof (R27): for every GameTest scenario, break the production path it covers (e.g. zero the profile field it asserts, skip the AoE loop) → the scenario must FAIL → restore; record each failure message in the report.
- [ ] **Step 11:** Commit: `feat(cursed-spirits): server tiers, variants, AI and combat seams`.

**Acceptance:** frozen API compiles; JUnit + GameTests green with red-proofs recorded; existing Todo/Nobara/Megumi tests untouched and green. `Факт:` lines in `block-2-report.md`.

---

## Task 3 (Block 3) — Client render stack: ported vanilla models, animations, renderers

**Requirements:** R2 (render half), R21, R24 (visual fit)

**Files:**
- Create: `src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritClient.java`, `CursedSpiritRenderer.java`, `CursedSpiritRenderState.java`, `model/CursedSpirit<Pascal>Model.java` ×9, `anim/CursedSpirit<Pascal>Animations.java` ×9, `CursedSpiritModels.java` (rig table)
- Modify: `src/client/java/jujutsu/mod/client/JujutsuModClient.java` (1 line: `CursedSpiritClient.register();`)
- Create tests: `src/test/java/jujutsu/mod/client/render/cursedspirit/{CursedSpiritRigContractTest,CursedSpiritAnimationContractTest}.java`

**Depends on:** [Block 2] (compile-time: `CursedSpiritEntity`, `CursedSpiritVariant`, `CursedSpiritTier`); Block 1's texture files for resource checks. Dispatch gate: Block 2's `API landed`. **Parallel-safe with:** [Block 1, Block 4]. Port sources: `.superpowers/rule-of-four/cursed-spirits/decompiled/net/mcreator/sonsofsins/client/{model,animation}/`.

**Fail-fast order:** port PROWLER first (model + animations + render state + renderer + registration with one variant), commit 1 = `feat(cursed-spirits): first rig (prowler)`, send Main `first rig landed`. Main runs a live probe (summon 3 LESSER spirits, screenshot) while the remaining 8 ports continue. Fix approach errors before mass porting.

**Out-of-scope:** glow/eyes layers, mount tack, procedural renderer extras (uniform scale procedure, ItemInHandLayer, HumanoidArmorLayer) — dropped by design.

**Port rules (mechanical, apply to every creature):**
1. Packages: `net.mcreator.sonsofsins.client.model` → `jujutsu.mod.client.render.cursedspirit.model`; `.animation` → `...cursedspirit.anim`.
2. Model class: drop `LAYER_LOCATION`, `ResourceLocation`, `PoseStack/VertexConsumer` imports; extend `EntityModel<CursedSpiritRenderState>`; ctor calls `super(root)` (`EntityModel` has no no-arg ctor; `Model` ctor takes `(ModelPart, Function)`) and must NOT declare a `root` field or override `root()` (both are final; `HierarchicalModel` no longer exists in 1.21.8 — javap-verified; the field-hiding variant NPEs at render); keep the ctor's `getChild(...)` lookups verbatim; keep `createBodyLayer()` verbatim; do NOT port `renderToBuffer`.
3. `setupAnim`: header → `public void setupAnim(CursedSpiritRenderState state)`; keep `root().getAllParts().forEach(ModelPart::resetPose);`; head look uses the state's yaw/pitch — read the exact formula from `HumanoidModel.setupAnim(S)` via `javap -c` on the 1.21.8 jar before writing (signature-level javap is not enough); each `this.animate(entity.XAnimationState, CLIP, ageInTicks)` → pre-baked `KeyframeAnimation` field (`AnimationDefinition.bake(root())`) with `CLIP.apply(state.X, state.ageInTicks)`; `this.animateWalk(CLIP, limbSwing, limbSwingAmount, a, b)` → `WALK.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, a, b)`; preserve per-creature call ORDER; scream-gated walk → `!state.scream.isStarted()`; entity-data conditions become render-state fields.
4. Animation classes: package rename only; keyframe values byte-identical; apply the frozen fixes (KELVIN SCREAMER retarget `left_arm`/`right_arm` → `leftarm`/`rightarm`; drop the non-shipped clips; PROWLER's `CLIMB` is unused by its `setupAnim` — drop it for that reason, not because it equals WALK, which it does not (javap-verified different lengths)). FLOATING_CURSE has no WALK clip in the pack and its model file has no attack line: wire `state.attack → curse_ghostAnimation.ATTACK` explicitly (else its attack animation is dead) and accept that it glides rather than walks.
5. Render state: `CursedSpiritRenderState extends LivingEntityRenderState` with `variant` + `AnimationState idle/attack/scream`; `extractRenderState` copies entity states (`state.x.copyFrom(entity.x)`), variant, and `walkAnimation*` (inherited).
6. Renderer: `CursedSpiritRenderer extends MobRenderer<CursedSpiritEntity, CursedSpiritRenderState, EntityModel<CursedSpiritRenderState>>` (three instances). Per-variant dispatch: **the vanilla `TropicalFishRenderer` shape (verified in 1.21.8: it holds `modelA/modelB` and overrides `render(S, PoseStack, MultiBufferSource, int)`, selecting the model then `super.render(...)`)** — here: `this.model = rigs.get(state.variant); super.render(...)`. `getTextureLocation(state)` = `state.variant.texture()`; `scale(S, PoseStack)` applies `variant.renderScale()` + `translate(0, variant.renderOffsetY(), 0)`; `getShadowRadius` = 0.4 (LESSER/COMMON) / 0.7 (GREATER) via ctor.
7. Layers: per variant `EntityModelLayerRegistry.registerModelLayer(new ModelLayerLocation(JujutsuMod.id("cursed_spirit/" + variant.id()), "main"), ModelX::createBodyLayer)`; `CursedSpiritClient.register()` registers the three renderers + all layers; one line in `JujutsuModClient`.
8. `CursedSpiritRigContractTest`: for every variant — every frozen clip key exists in its animations class; each clip's **exact bone-name set and count** equals the frozen clip map (both directions: no extra, none missing); looping flag matches the frozen table; every clip bone exists among the baked part names (`LayerDefinition.create(...).bakeRoot().getAllParts()`); every texture path exists. `CursedSpiritAnimationContractTest`: clip keys are unique per variant, lengths > 0, no channel targets a part absent from the model. Red-proofs (each must fail then be restored): rename one clip bone;
drop one channel (count assert must fire); swap two clip keys; flip a looping flag; delete a texture file.
9. Commit: `feat(cursed-spirits): vanilla model/animation port and tier renderers`.

**Acceptance:** `compileClientJava` green; both contract tests green + all five red-proofs recorded; no GeckoLib imports in new files; `git grep -i sonsofsins src/` → zero hits. `Факт:` lines in `block-3-report.md`.

---

## Task 4 (Block 4) — Natural spawn integration + local cap

**Requirements:** R18, R19, R30

**Files:**
- Create: `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritSpawnRules.java`, `CursedSpiritSpawnIntegration.java`
- Create tests: `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritSpawnRulesTest.java`, `src/gametest/java/jujutsu/mod/gametest/CursedSpiritSpawnGameTests.java`
- Modify (serialized, after Block 2's `block-2 report filed`): one line in `CursedSpirits.registerServerHooks()` — `CursedSpiritSpawnIntegration.register();` — and `src/gametest/resources/fabric.mod.json` (register the spawn GameTest class; second serialized edit after Block 2's first)

**Depends on:** [Block 2]. Dispatch gate: Block 2's `API landed` (write the files); the serialized edits wait for `block-2 report filed`. **Parallel-safe with:** [Block 1, Block 3] EXCEPT `CursedSpirits.java`/`fabric.mod.json` (serialized).

**Out-of-scope:** spawn eggs, commands, nether/end spawns, structure spawns.

- [ ] **Step 1:** **Public-API spawn gate (NO access widener).** Facts (javap-verified on the 1.21.8 merged jar): `SpawnPlacements.register` is **private** and has no Fabric wrapper; `Mob.checkSpawnRules(LevelAccessor, EntitySpawnReason)` is **public** and IS called by `NaturalSpawner` on the natural path (`javap -c` shows `Mob.checkSpawnRules` ×2 next to `SpawnPlacements.isSpawnPositionOk`/`checkSpawnRules`); an unregistered type gets `getPlacementType → NO_RESTRICTIONS` and `getHeightmapType → MOTION_BLOCKING_NO_LEAVES` (bytecode-verified defaults), so vanilla still picks a sane surface position. Therefore gate in the entity:
  `public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) { return super.checkSpawnRules(level, reason) && CursedSpiritSpawnRules.belowLocalCap(level, this.blockPosition()); }`
  `super` = `Monster.checkMonsterSpawnRules` → difficulty != PEACEFUL + vanilla darkness roll + `checkMobSpawnRules`. Do NOT re-implement darkness: it is vanilla-owned and proven in-world (Step 5).
- [ ] **Step 2:** `CursedSpiritSpawnRules`: pure `static boolean belowCap(int nearby, int cap)` + adapter `belowLocalCap(LevelAccessor level, BlockPos pos)` → `level instanceof ServerLevel sl ? sl.getEntitiesOfClass(CursedSpiritEntity.class, new AABB(pos).inflate(CROWD_RADIUS)).size() < MAX_SPIRITS_NEARBY : true` (client-local worlds never spawn naturally).
- [ ] **Step 3:** `CursedSpiritSpawnIntegration.register()`: three `BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type, stats.spawnWeight(), stats.spawnMinGroup(), stats.spawnMaxGroup())` rows with numbers straight from `CursedSpiritProfile`; a comment states why `SpawnPlacements` is not touched (private in 1.21.8; the entity-side gate covers it).
- [ ] **Step 4:** JUnit `CursedSpiritSpawnRulesTest` on the pure core: cap boundaries (cap-1 pass / cap fail / cap+1 fail); weights ordering (T1 > T2 > T3) and group sanity (min ≥ 1, min ≤ max) read from the profile; a test asserting the three registered spawn rows come from the profile numbers (no literals). Red-proof: invert the cap comparison → fail → restore.
- [ ] **Step 5:** GameTest real oracle (replaces the vacuous placement-type asserts): build two cells in the arena — one lit (glowstone), one dark; assert `spirit.checkSpawnRules(level, EntitySpawnReason.NATURAL)` is **false** in the lit cell and **true** in the dark cell (creature placed under a solid roof, floor solid, enough space); then spawn `MAX_SPIRITS_NEARBY` spirits near the dark cell and assert **false** (cap). Red-proof: invert the cap comparison → the cap assert must fail → restore.
- [ ] **Step 6:** Report the expected night-share estimate (weights 65/20/5 share of MONSTER spawn attempts in dark overworld areas) and the explicit decisions: no daylight burning (they persist once spawned but only spawn in darkness), peaceful difficulty = no spawns, difficulty gating mirrors vanilla monsters.
- [ ] **Step 7:** Commit: `feat(cursed-spirits): natural overworld spawning`.

**Acceptance:** pure-core JUnit + the in-world spawn GameTest green with red-proofs; registration called from server init; soak falsification numbers recorded in the block report (census: cursed-spirit counts per 60 s at night stay ≤ `MAX_SPIRITS_NEARBY` per player area — sample via `command_execute` selectors; TPS ≥ 18 read from `command_execute /tick query`; zero `jujutsumod` ERROR lines in the lane log). `Факт:` lines in `block-4-report.md`.

---

## Task 5 (main, Block 5) — Integration GameTests, docs, live MCP verification, report

**Requirements:** R6, R7, R8, R9, R10, R16 (live half), R17 (progression half), R23 (manual half), R26, R27, R28 (docs), R29, plus the final human report

**Files:**
- Create: `src/gametest/java/jujutsu/mod/gametest/CursedSpiritIntegrationGameTests.java`
- Create: `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritSoundSymmetryTest.java` (Java-constant direction: every `CursedSpiritVariant` sound constant location `cursed/<id>_<channel>` ↔ sounds.json key ↔ `.ogg` ↔ both lang subtitles)
- Modify: `src/gametest/resources/fabric.mod.json` (append, serialized after Block 2/4)
- Create: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Cursed-spirits.md`; modify `00-MOC.md` (link + metric counts), `docs/KNOWN_ISSUES.md`, `SESSION.md`, `.superpowers/rule-of-four/cursed-spirits/progress.md`

**Depends on:** all blocks. **Parallel-safe with:** none (integrates).

- [ ] **Step 0 (fail-fast probe):** when Block 3 sends `first rig landed` and Block 2 is in — run the lane, summon 3 LESSER spirits, screenshot; record in `progress.md`. If the rig is wrong (silhouette/scale/dispatch), send findings to Block 3 immediately.
- [ ] **Step 1:** Integration GameTests (production paths; mock player in survival; premise asserts first): R6/R7 Nobara — nail hit via the real embed/mark path marks the spirit, aimed Hairpin through `NobaraAbilityRouter.tryCast(player, ability, false)` returns routed:true + HP delta, mis-aim routed:false + zero cooldown; R8 Resonance — marked spirit takes the ritual damage, unmarked takes none; R9 Todo — `TodoAbilityRouter.tryCast` lane: melee + Black Flash damage (delta > base); R10 Megumi — `MegumiAbilityRouter.tryCast` sic assigns the spirit, pounce damages it (snapshot from Block 2's build); R16 live half — aimed resolver picks the centred far spirit over a near edge-graze (scout-4's E1b smoke).
- R17 progression — `RemnantVisualType.classify` is **package-private** and `ProjectJjkTags` is package-private: verify CURSE classification by extending the existing same-package JUnit `RemnantVisualTypeTest` (do NOT call classify from the GameTest); in the GameTest assert via `TagKey.create(Registries.ENTITY_TYPE, JujutsuMod.id("resonance_remnant_curse"))` + the production mint path (`REMNANT_HIT_THRESHOLD = 2` + `recordHit`) for each tier's type, plus no orphan nails after death. Red-proof per scenario.
- [ ] **Step 2:** `CursedSpiritSoundSymmetryTest` both directions + red-proof (remove one row).
- [ ] **Step 3:** Docs: Codex note (status CURRENT; slice boundary; per-tier table; variant roster; spawn table + cap; manual-only verification boundary list with methods; tuning pointer), MOC link + metrics bump (`gradlew.bat auditDocumentation --no-daemon`), `KNOWN_ISSUES.md` accepted limits (draft balance; no VFX; floater hover offset; FLOATING_CURSE glides — no walk clip; pack sounds adapted; T3 stagger floor; swing-synced attack clip; vanilla stack rationale), `SESSION.md` handoff.
- [ ] **Step 4:** Green barrier: `gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` → record counts in `progress.md`.
- [ ] **Step 5:** Live MCP lane (per `mcp-lane-launch`), **confirm tool schemas via `tools/list` BEFORE writing the soak protocol**, then use these verified facts: `entity_summon` takes any `entity_type` id + optional SNBT (`nbt:{Variant:"prowler"}` — the NBT key is frozen) and REQUIRES a `dimension`; `entity_query` is `@a`-oriented and selector support is limited — mob census = `@e` with `limit` + client-side filtering on `EntityInfo.type`, or `command_execute`; `entity_set_nbt`/`entity_get_nbt` exist (use them to force a variant and to read back state); `entity_apply_effect` is uuid-only AND requires `duration_ticks` (+`amplifier`) — freeze a MOB by the uuid returned from the summon; there is NO `server_get_status` — TPS via `command_execute /tick query`; `jujutsu_state_get` is player-vessel state, NOT mob stagger — mob oracles are HP/velocity deltas; crosshair/raycast MISS entities — no raycast oracles; no audio oracle (sounds are manual-only).
- Then per tier: summon 2–3 variants at known spots, `time set noon`, freeze stills with slowness-8, `view_capture` frames judged NUMERICALLY (bbox/mean luminance/region diff), frozen-vs-moving pair for idle/walk, burst protocol for the attack (invoke + short `ticks_wait` + capture, motion mask), fight the player for hurt/death, kill each tier (HP→0, removal), verify hitbox sanity via aimed damage + render-bbox comparison, T3 knockback resistance vs T1 under one source, night soak with the falsification numbers from Block 4.
- Multi-variant simultaneity: ≥3 variants of one tier in one frame with a mechanic oracle true for all.
- [ ] **Step 6:** Report to the owner (Russian, outcome-first): tiers→models table; per-tier behavior; balance table + tuning pointer; sounds/animations used; what was verified via MCP with numbers; remaining limits.
- [ ] **Step 7:** Commit + PR per repo convention (Russian title/body «Для игрока» + technical part). Push branch.

**Acceptance:** integration tests green + red-proven; `qualityGate` green on the final commit; live evidence recorded (frames + numeric deltas + query outputs + soak numbers); docs audit green; PR open.

---

## File ownership matrix

| File(s) | Owner |
|---|---|
| `assets/jujutsumod/{textures/entity/cursed/**,sounds/cursed/**,sounds.json,lang/*.json}`, `docs/PROVENANCE.md`, `docs/THIRD_PARTY_NOTICES.md`, `CursedSpiritResourceContractTest` | Block 1 |
| `cursedspirit/**` (main), `combat/StaggerResistant.java`, `combat/CombatTags.java`, `CharacterCombatModifiers.java`, `TodoBoogieWoogieRuntime.java`, `JujutsuEntities.java`, `JujutsuMod.java`, `JujutsuSounds.java`, tag JSONs, `CursedSpiritGameTests`, `CursedSpiritTestFixtures`, `fabric.mod.json` (first edit) | Block 2 |
| `client/render/cursedspirit/**`, `JujutsuModClient.java`, client-side render tests | Block 3 |
| `cursedspirit/CursedSpiritSpawnRules.java`, `cursedspirit/CursedSpiritSpawnIntegration.java`, `CursedSpirits.java` (one-line edit), spawn tests + `CursedSpiritSpawnGameTests`, `fabric.mod.json` (second edit) | Block 4 |
| `gametest/CursedSpiritIntegrationGameTests.java`, `CursedSpiritSoundSymmetryTest.java`, `00-MOC.md`, `03-systems/Cursed-spirits.md`, `KNOWN_ISSUES.md`, `SESSION.md`, `fabric.mod.json` (third edit), PR | Block 5 (main) |

Serialization gates (named hub messages): `API landed` (Block 2 → dispatch Blocks 3/4; Block 3's compile depends on it); `first rig landed` (Block 3 → Block 5 step 0 probe); `block-2 report filed` (Block 2 → Block 4's `CursedSpirits.java` + `fabric.mod.json` edits, and Block 5's `fabric.mod.json` edit); `block-4 report filed` (Block 4 → Block 5's `fabric.mod.json` edit).

## Decision Records

**D1 — Render stack: direct vanilla port, not GeckoLib conversion.** Context: the pack ships 1.21.1-vanilla-API Java models + `AnimationDefinition` classes (zero MCreator/NeoForge imports); the repo's mobs all use GeckoLib 5.2.2. Options: (a) convert Java→`.geo.json`/`.animation.json` via Blockbench-derived math and add per-variant GeckoLib rigs; (b) port the Java near-verbatim and render with the vanilla stack that 1.21.8 fully exposes. Decision: (b). Why: (a) requires an unverifiable-offline coordinate/sign mapping for 10+ creatures plus a GeckoLib-specific per-variant dispatch;
(b) eliminates the whole conversion risk class, keeps the authored animations bit-exact, and uses the platform runtime the assets were authored for. Consequences/risks: the repo gains a vanilla-stack mob render path next to GeckoLib; recorded as a deliberate, documented split. The user's brief explicitly allowed choosing the safer/simpler embedding.

**D2 — One entity class, tier as constructor data.** `EntityType.Builder.of((type, level) -> new CursedSpiritEntity(type, level, CursedSpiritTier.X), MobCategory.MONSTER)` — no subclass-per-tier switch; attributes via `FabricDefaultAttributeRegistry` rows keyed by type; tier is never serialized (derived from the type).

**D3 — Spawn: natural pipeline + local cap, no access widener.** `BiomeModifications.addSpawn` (verified present in fabric-biome-api-v1 16.1.0) + the entity-side spawn gate. `SpawnPlacements.register` is **private** in 1.21.8 (javap-verified) and has no Fabric wrapper, but `Mob.checkSpawnRules(LevelAccessor, EntitySpawnReason)` is public and IS called by `NaturalSpawner` (`javap -c`), and an unregistered type keeps sane vanilla defaults (`NO_RESTRICTIONS` + `MOTION_BLOCKING_NO_LEAVES`), so the gate lives in a `checkSpawnRules` override: vanilla darkness/difficulty via `super` + the local crowd cap `MAX_SPIRITS_NEARBY` (radius 48). No access widener, no mixin, no custom spawner runtime; mobcap/despawn free; all tuning in the profile + one cap constant.

**D4 — Stagger resistance via the `StaggerResistant` seam; floor 1 tick.** Data-driven per entity; `CombatStagger` stays the single stagger system.

**D5 — Boogie Woogie immunity for GREATER only** via the `jujutsumod:boogie_woogie_immune` tag + one clause in `TodoBoogieWoogieRuntime.isEligibleTarget` (aimed/pair/stone inherit).

**D6 — Roster exclusions (recorded).** Not used: organs/attachments/pets/props; `DITCHED` (invisible base + anon layers); `DEVOURER`/`NIBBLER`/`ETHER_GHOST` (flyer/swimmer/ghost-morph under ground tiers would read wrong); `BLUD_AGGRESSIVE`/`BLUD_TRADE`; `BETRAYED_GUZZLER` (geo-identical); `GRUB` (bulky but 1.2 blocks — future T2/T3 variant). Textures are final in Block 1: FLOATING_CURSE ships the pack's base `curse.png` (no Block-3 re-copy); ghost/glow/eye sheets are NOT imported in this slice.

**D7 — No VFX changes.** Model animation + sounds + knockback carry the read; `VfxCore` untouched (revisit after the live pass if the T3 slam feels flat).

**D8 — Brief deviation, recorded.** The owner's brief sketched a per-tier sequential build with live checks between tiers. This plan builds the shared foundation once (as the brief's first requirement mandates) and does the per-tier live verification as a structured sequence inside the final pass (T1 → T2 → T3 → combined), plus the early single-rig probe in Block 3. The deviation is intentional (three sequential builds of one system would violate "не три независимые системы"); it is recorded here and in `progress.md`.

## Rollback

- Each block is a commit series on `feat/cursed-spirits`; reverting a block is `git revert` of its commits (no migrations, no data formats).
- Spawn is the only world-touching change: disabling = removing the three `BiomeModifications.addSpawn` rows in `CursedSpiritSpawnIntegration` (one-line revert), mobs stay summonable for tests.

## Verification commands (used by every block)

```
JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current
gradlew.bat compileJava compileClientJava compileTestJava --no-daemon --max-workers=1 --no-watch-fs
gradlew.bat test --tests "jujutsu.mod.cursedspirit.*" --no-daemon
gradlew.bat runGameTest --no-daemon --max-workers=1 --no-watch-fs     # JUnit XML: build/test-results/gametest/junit.xml
gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs
```
