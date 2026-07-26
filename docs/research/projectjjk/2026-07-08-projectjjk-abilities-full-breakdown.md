# ProjectJJK 1.2.0-1.21.1-fabric-beta — полный разбор способностей

**Источник:** `C:\Users\KOMP1\Downloads\projectjjk-1.2.0-1.21.1-fabric-beta.jar`  
**Метод:** CFR 0.152 decompile (`AbilityRegistry`, ability classes, class kits, movesets, lang `en_us.json`)  
**Дата:** 2026-07-08  
**Лицензия мода:** All Rights Reserved — это research notes для reimplementation, **не** копипаст кода в `jujutsumod`.

---

## 0. Как устроена система способностей

### 0.1 Core classes

| Класс | Роль |
|---|---|
| `net.hadences.game.system.ability.Ability` | Базовый контракт ability |
| `AbilityRegistry` | Статический map всех ability instances |
| `ServerAbilitySlots` / `ClientAbilitySlots` | Hotbar слоты |
| `AbilityInventoryData` | Какие ability «выучены» игроком |
| `CooldownManager` | CD в секундах (float) |
| `CursedEnergyData` + spend path | Стоимость CE |
| `InnateClass` + `InnateClassRegistry` | Выбор техники + unlock по grade |
| `*Moveset` entities | Хореография атаки на сервере |
| Custom packets S2C/C2S | Синхронизация cast/release/VFX |

### 0.2 Контракт `Ability`

```text
Ability(id, image, damage, cooldownSeconds, costCE, type)
type ∈ { LEARNED, INNATE, NULL }
```

- **DAMAGE** — base; runtime: `damage * DamageMultiplierData` и ×0.65 если на игроке Weakness.
- **COOLDOWN** — секунды.
- **COST** — cursed energy.
- **Hold system:** список `IntervalThreshold(startMs)` + function. `onCast(player, heldDurationMs)` берёт **наибольший** threshold ≤ hold time.
- **dependencyAbilities:** при касте форсит cooldown на связанные ability ids.
- **isPassive:** UI/поведение пассива (`setPassive(true)`).
- Cast return: `Pair<Boolean success, Long extraCooldownTicksOrSeconds>` (в decompile long часто в секундах/тиках — смотреть per-ability).

### 0.3 Registry order (канон чисел)

В `AbilityRegistry` static block:

```text
new X(id, damage, cooldown, cost, [extras...], type, icon)
```

`extras` — ability-specific: charges, range, duration, multipliers, hold params.

### 0.4 Innate classes (playable)

Реально зарегистрированы в `InnateClassRegistry`:

1. `limitless`
2. `cursed_speech`
3. `boogie_woogie`
4. `straw_doll`
5. `ratio`
6. `blood_manipulation`

**Есть string ids, но НЕ put в map (не выбираются):**  
`ten_shadows`, `black_bird_manipulation`, `construction` (+ пустые class stubs: `TenShadowsClass`, `BlackBirdManipulationClass`, `ConstructionClass`, `InverseClass`).

### 0.5 Grade unlock ladder

Каждый `InnateClass` имеет:

`learnAbilitiesGrade4 / Grade3 / SemiGrade2 / Grade2 / SemiGrade1 / Grade1 / GradeSpecial`

Обычно **ранний grade = базовые skills**, высокий grade = ультимейты (резонанс, purple, overtime…).

---

## 1. Bundled libraries — нужна ли Specter?

Jar-in-jar (`fabric.mod.json` → `jars`):

| Jar | mod id | Версия MC | Назначение | Нужна ли для reimpl abilities? |
|---|---|---|---|---|
| **specter-v0.1.0.jar** | specter | ~1.21.1 | Внутренняя lib автора (рендер/утилиты, jij) | **Нет** для gameplay-логики. Не тянуть 1:1 |
| BossBarLib-v2.0.2 | bossbarlib | ~1.21.1 | Кастомные boss bars | Только если нужны такие UI-бары |
| geckolib-fabric-1.21.1-4.6.6 | geckolib | ≥1.21.1 | 3D geo/animation (nail, doll, spirits, VFX planes) | **Опционально** (presentation). На 1.21.8 — другая версия GeckoLib |
| midnightlib-1.6.3 | midnightlib | ≥1.21 | Конфиг UI | Нет (есть Cloth/own config) |
| satin-2.0.0 | satin | ≥1.20.3 client | Post-shaders, screen shake, color FX | **Опционально**. ProjectJJK зовёт через `SatinUtil` |
| SmartBrainLib-1.16.1 | smartbrainlib | ≥1.21.1 | AI brains для мобов/духов | Только PvE AI |

### Вердикт

```text
Ability cast path (server):
  keybind → slots → Ability.onCast → CE + CD
    → spawn Moveset / Projectile / VFX entity / scheduled tasks
    → damage types / effects / player data

Presentation path (client):
  packets + particles + sounds
    → GeckoLib models
    → Satin shaders / shake
    → Specter helpers (если используются)
```

**Specter не является dependency ability-формул.**  
Damage, marks, resonance, CE, cooldowns, hold thresholds — чистый server Java + Fabric networking.  
Specter/Satin/GeckoLib — слой «как это выглядит».

Для **jujutsumod (Fabric 1.21.8):** reimplement behavior на public API; libs только если сознательно хотите тот же presentation stack. Direct jar port ProjectJJK на 1.21.8 — high risk (1.21.1 + ARR + jij).

---

## 2. Полная таблица AbilityRegistry

Колонки: **dmg / cd(s) / cost(CE) / extras / type / passive(code)**

### 2.1 Learnable (общие)

| id | dmg | cd | cost | extras | type | passive |
|---|---:|---:|---:|---|---|---|
| `power_punch` | 3 | 1 | 5 | — | LEARNED | no |
| `black_flash` | 0 | 0 | 0 | chance **5%**, dmg amp **2.0**, CE restore **8%**, CD reduce **15%** | LEARNED | **yes** |
| `pummel_barrage` | 1 | 4 | 15 | punches **15** | LEARNED | no |
| `uppercut` | 1 | 3 | 10 | — | LEARNED | no |
| `counter` | 1 | 8 | 20 | stunDuration static **1**s | LEARNED | no |
| `heavy_blow` | 4 | 10 | 30 | — | LEARNED | no |
| `zenith_focus` | 0 | 0 | 0 | reduce chance **30%** | LEARNED | **yes** |
| `reverse_cursed_technique` | 0 | 0 | 0 | toggle effect | LEARNED | no |
| `flash_step` | 0 | 0 | 0 | blink / step | LEARNED | no |
| `dash` | 0 | 10 | 0 | speed buff IV, 60 ticks | LEARNED | no |
| `finalitys_edge` | 1 | 20 | 60 | finisher | LEARNED | no |
| `guard` | 0 | 3 | 0 | dmg reduction **60%** | LEARNED | **yes** |

### 2.2 Cursed Speech

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `get_twisted` | 8 | 15 | 20 | — | twist CC + dmg; shared speech CD deps |
| `blast_away` | 3 | 15 | 50 | ray 15, radius 5 | knockback cone; deps other speech 1s |
| `explode` | 5 | 20 | 80 | — | explosion command |
| `dont_move` | 0 | 30 | 100 | stun **3**s | stun; ignores Infinity; Satin shake |
| `crumble_away` | 15 | 25 | 100 | ray 15, radius 5 | heavy damage speech |

Все speech abilities связывают друг друга через `addDependentAbility` (короткий shared lock).

### 2.3 Limitless

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `infinity` | 0 | 5 | 0 | toggle | `ModEffects.INFINITY` ~infinite duration toggle |
| `six_eyes` | 0 | 35 | 35 | duration **1 min** | CE efficiency effect 1×60×20 ticks |
| `blue` | 4 | 8 | 100 | radius **10** | attract + dmg; locks purple CD |
| `red` | 15 | 16 | 200 | radius **8** | explosion repulse; locks purple CD |
| `purple` | 80 | 40 | 600 | — | merge blue+red timeline; stun 60 ticks; locks red/blue 5s |
| `blue_mastery` | 10 | 8 | 100 | maxOut **5**s, dist **5.0** | hold: none/default/projectile/max (0/500/1500/2500 ms) |
| `red_mastery` | 15 | 16 | 200 | hold modes | 0/500/1500/2500 ms variants |
| `purple_spark` | 40 | 40 | 600 | hold purple @2500ms | cheaper purple variant; deps red/blue |

### 2.4 Boogie Woogie

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `spatial_swap` | 0 | 1 | 25 | range **30** | swap with target/entity |
| `displacement_burst` | 0 | 1 | 80 | entityR **40**, maxDist **10** | AoE swap/displace |
| `sixth_sense` | 0 | 35 | 35 | **1** min, **25%** | buff: proc chance sense |
| `showmaker` | 12 | 12 | 35 | range **10**, height **10** | aerial setup + dmg |
| `phantom_applause` | 0 | 20 | 35 | timer **15**s, range **10** | temporary effect aura |

### 2.5 Straw Doll (Nobara) — critical for jujutsumod

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `piercing_nail` | 0.5 | 1 | 5 | speed **1.0** | hold: single / triple@300ms / barrage@800ms (10 nails) |
| `nail_bind_curse` | 0.5 | 10 | 40 | detect **10** | «Shadow Impale»: nails return, AoE stun+dmg |
| `hairpin_explosion` | 1 | 12 | 30 | detect **10** | detonate owned nails (scheduled chain) |
| `hairpin_enlargement` | 12 | 15 | 15 | range **20** | enlarge tagged nail/target, delayed snap |
| `resonance` | 20 | 20 | 100 | suppress **6**s | doll ritual remote hit, radius scan **30**, link dist **3** setup |
| `resonant_remains` | 0 | 10 | 35 | drop chance **5%** | **PASSIVE**: body part drop for resonance fuel |

### 2.6 Ratio (Nanami)

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `ratio` | 0 | 0 | 0 | crit **10%**, mult **1.75** | **PASSIVE** critical weak-point |
| `swift_strike` | 5 | 5 | 15 | — | fast hit; overtime variant |
| `collapse` | 12 | 5 | 20 | ray 6, radius 1 | ratio-powered strike |
| `overtime` | 0 | 60 | 30 | survive **20**s, OT **30**s, CD reduce **75%** | binding vow self-buff |

### 2.7 Blood Manipulation (Choso)

| id | dmg | cd | cost | extras | notes |
|---|---:|---:|---:|---|---|
| `blood_manipulation` | 0 | 0 | 0 | holder | meta-slot: routes hold to PiercingBlood / SlicingExorcism |
| `blood_utility` | 0 | 0 | 0 | holder | meta-slot: Supernova / CrimsonBinding |
| `blood_control` | 0 | 0 | 8 | long-hold toggle | modes: hardening / flowing / convergence (hold 0 vs 1000000) |
| `piercing_blood` | 4 | 12 | 50 | hold 0/1000/2500 | none / single (35% scale) / max output 60t range 25 |
| `slicing_exorcism` | 12 | 8 | 20 | hold 0/500 | chakram blood disks |
| `crimson_binding` | 0 | 12 | 30 | debuff 100t str 2 | bind/slow style |
| `supernova` | 10 | 0 | 20 | orb chance 30% | blood orb nova |
| `wing_king` | 0 | 40 | 60 | dur 30s, max orbs 8, poison 3s | transformation / homing orbs |
| `flowing_red_scale` | 0 | 0 | 0 | (+20% tech dmg via control) | mode flag |
| `convergence` | 0 | 0 | 0 | — | mode flag (prep for piercing) |
| `blood_hardening` | 0 | 0 | 0 | (−30% dmg / −50% tech dmg taken) | mode flag |

**BloodControl constants (in code):**  
`BLOOD_HARDENING_DAMAGE_REDUCTION=30%`, `BLOOD_HARDENING_TECHNIQUE_DAMAGE_REDUCTION=50%`, `FLOWING_RED_SCALE_DAMAGE_MULTIPLIER=20%`.

---

## 3. Unlock по grade (что выдаёт class)

### Straw Doll
| Grade | Teaches |
|---|---|
| Grade4 | `resonant_remains`, `piercing_nail` |
| Grade3 | `nail_bind_curse` |
| SemiGrade2 | — |
| Grade2 | `hairpin_explosion` |
| SemiGrade1 | `hairpin_enlargement` |
| Grade1 | `resonance` |
| GradeSpecial | — |

### Limitless
| Grade | Teaches |
|---|---|
| Grade4 | `six_eyes`, `infinity`, `blue`, `red`, `purple` |
| Grade3 | `infinity`, `blue`, `red`, `purple` |
| SemiGrade2 | `blue`, `red`, `purple` |
| Grade2 | `red`, `purple` |
| SemiGrade1 / Grade1 / Special | `purple` (progressive) |

*(Mastery variants `blue_mastery` / `red_mastery` / `purple_spark` регистрируются в map, unlock path может быть отдельным progression/teach command.)*

### Boogie Woogie
| Grade | Teaches |
|---|---|
| Grade4 | all: swap, phantom, displacement, showmaker, sixth_sense |
| → higher | снимает ранние, оставляет поздние ультимейты |

### Cursed Speech
| Grade | Teaches |
|---|---|
| Grade4 | get_twisted, blast_away, explode, dont_move, crumble_away |
| Grade3 | explode, dont_move, crumble_away |
| SemiGrade2 | dont_move, crumble_away |
| Grade2 | crumble_away |

### Ratio
| Grade | Teaches |
|---|---|
| Grade4 | ratio, swift_strike, collapse, overtime |
| Grade3+ | без ratio passive на низких — см. class file |
| Grade1 | overtime |

### Blood Manipulation
| Grade | Teaches |
|---|---|
| Grade4 | blood_manipulation, blood_utility, blood_control, wing_king |
| lower grades | progressively only control/wing_king |

---

## 4. Детальный разбор по ability (implementation notes)

Ниже — поведение **из decompiled code**, не маркетинг-описаний.

### 4.1 Learnable

#### `power_punch` — PowerPunch
- Raycast range **6**, radius **1**.
- Damage type `POWER_PUNCH`.
- Быстрый front punch.

#### `black_flash` — BlackFlash (**PASSIVE**)
- Params: **5%** chance, **×2** damage amp, **8%** CE restore, **15%** ability CD reduction on proc.
- Triggers only on **PHYSICAL** damage category.
- On proc: raycast 5 blocks, deals `BLACK_FLASH` damage = hitDamage × amp.
- VFX: `SatinUtil` BLACK_AND_WHITE (4) + SCREEN_SHAKE (10).
- **Needs Satin for original flash feel; logic does not need Specter.**

#### `pummel_barrage` — PummelBarrage
- **15** punches via scheduled ticks.
- Per-hit `PUMMEL_BARRAGE` damage = base 1 × multipliers.
- Returns extra CD long `2`.

#### `uppercut` — Uppercut
- Melee launch hit, damage type uppercut.

#### `counter` — Counter
- Windowed counter; stunDuration **1**s static.
- Returns duration as long.

#### `heavy_blow` — HeavyBlow
- Raycast 5, SCREEN_SHAKE 5, `HEAVY_BLOW` damage 4.

#### `zenith_focus` — ZenithFocus (**PASSIVE**)
- **30%** chance damage reduction style focus (effect `ZenithFocusEffect`).
- Passive, cast is no-op success.

#### `reverse_cursed_technique` — RCT
- Toggle `ModEffects.REVERSE_CURSED_TECHNIQUE` duration 1000000 or remove.
- Off-path returns extra long `5`.

#### `flash_step` — FlashStep
- Short teleport/step along look; fail if blocked.

#### `dash` — Dash
- CD 10s, cost 0.
- Speed effect amplifier **4**, duration **60** ticks.

#### `finalitys_edge` — FinalitysEdge
- High cost finisher (60 CE, 20s CD), damage type `FINALITYS_EDGE`.

#### `guard` — Guard (**PASSIVE**)
- **60%** damage reduction number stored; cast returns **false** (not an active cast ability).
- CD 3 / cost 0 in registry (passive data carrier).

---

### 4.2 Cursed Speech

Pattern: forward hit-scan ~4 block offset, ray **15**, radius **5**, Satin shake, custom damage types, mutual short CD locks.

| Ability | Special |
|---|---|
| GetTwisted | twist/CC + 8 dmg |
| BlastAway | knockback blast 3 dmg |
| Explode | force explode 5 dmg |
| DontMove | stun **3**s (players via PlayerManager; mobs STUN effect); **skips targets with Infinity** |
| CrumbleAway | heavy 15 dmg |

---

### 4.3 Limitless

#### Infinity
- Toggle effect `INFINITY` duration 1_000_000.
- Cost 0, CD 5s.
- Blocks some CC (e.g. DontMove skips infinity players).

#### Six Eyes
- Applies `SIX_EYES` for `minutes * 60 * 20` ticks (registry: **1 minute**).
- Cost 35, CD 35.
- Returns long = minutes*60 (cooldown extension semantics).

#### Blue (default)
- Radius **10**.
- Attract living entities in `radius+4`, damage `BLUE`.
- Locks `purple` / `purple_spark` CD to blue's cooldown.

#### Red (default)
- Radius **8**.
- Spawns explosion VFX timeline (~15 ticks), `explode(radius)`, damage `RED`.
- Locks purple CD.

#### Purple
- Damage **80**, CD 40, cost **600** (most expensive).
- Multi-stage merge animation (scaleFactor, mergeDuration 16×scale, idle 8).
- Satin shake + color flash; applies STUN 60 ticks on hit.
- Locks red/blue/masteries for 5s.

#### BlueMastery / RedMastery
- Hold thresholds ms: **0 / 500 / 1500 / 2500** → none / default / projectile / maximum output.
- Max output duration from ctor (Blue: 5s, distance 5).
- Heavy Satin usage (shake, circle ray).

#### PurpleSpark
- Hold 0 = none, 2500 = purple path.
- Damage 40 (half of full purple), same 600 CE / 40 CD.
- scaleFactor 1.4, shorter offsets.

---

### 4.4 Boogie Woogie

#### SpatialSwap
- Range **30** blocks: swap positions with looked entity/point.

#### DisplacementBurst
- entityRadius **40**, maxDistance **10**: AoE displacement of entities.

#### SixthSense
- Buff for **1 minute**, **25%** proc chance field.
- Effect `SixthSenseEffect`.

#### Showmaker
- Range 10, teleport height 10, damage 12.
- Spark VFX + multi-tick (maxTicks 15).

#### PhantomApplause
- Timer 15s, block range 10.
- Effect `PhantomApplauseEffect`.

---

### 4.5 Straw Doll (deep)

#### PiercingNail
- Base dmg 0.5, CD 1s, CE 5, nail speed 1.0.
- Hold thresholds (**ms**):  
  - **0** → single nail  
  - **300** → triple nails  
  - **800** → barrage (`totalNails=10`)
- Spawns `NailEntity` projectiles; owned by player.
- Cyan dust while flying (entity tick); on hit applies nail damage type.

#### NailBindCurse («Hairpin: Shadow Impale» in lang)
- Detect range **10**.
- Hit-scan for **own** `NailEntity` in front cone.
- Scheduled timer every 250ms (20 ticks chain): each nail damages nearby living, stuns (players 2s / mobs STUN 20t), plays snap, spawns FlashStrike VFX.
- Damage type `CURSE_BIND_NAIL`.

#### HairpinExplosion
- Detect range **10**.
- Finds owned nails → sequential explosions (index loop, scheduled).
- Damage type `HAIRPIN_EXPLOSION`.
- Low base dmg field (1) — real punch often from explosion radius logic in moveset/entity.

#### HairpinEnlargement
- Range **20**.
- Requires tagged entity (error lang: `No Tagged Entity found!`).
- Delayed enlargement/snap against marked target (moveset `HairpinEnlargementMoveset`).
- Damage type `HAIRPIN_ENLARGEMENT`.
- Base dmg **12**, CD 15, CE 15.

#### Resonance
- Dmg **20**, CD 20, CE **100**, suppress duration **6s** (`suppressedDuration * 20` ticks).
- Setup distance **3**, resonance radius **30**.
- Uses straw doll item/link data; remote strike; applies `SuppressedEffect`.
- Moveset `ResonanceMoveset` multi-stage.

#### ResonantRemains (**PASSIVE**)
- **5%** body part drop chance on kill/hit path.
- Dropped remains enable/enhance Resonance targeting.
- Passive flag true; cast no-op.

**Straw doll flow (code-level):**  
`PiercingNail` embed → (optional remains) → `HairpinEnlargement` / `HairpinExplosion` / `NailBindCurse` → `Resonance` as remote finisher.

---

### 4.6 Ratio

#### Ratio (**PASSIVE**)
- **10%** crit chance, **×1.75** multiplier on ratio crit.
- Passive; cast no-op.
- Actual crit application via damage listeners elsewhere.

#### SwiftStrike
- Fast 5 dmg strike; has `overtimeCast` branch when Overtime active.

#### Collapse
- Ray 6, radius 1, dmg 12.
- `overtimeCast` stronger path.

#### Overtime
- Survive phase **20s**, then overtime **30s**.
- During OT: cooldown reduction **75%**.
- Self binding-vow style buff (`OvertimeEffect`).
- CD 60, cost 30.

---

### 4.7 Blood Manipulation

Architecture uses **holder abilities**:

- `BloodManipulation` hold-routes to PiercingBlood / SlicingExorcism (client+server hold function lists dynamic).
- `BloodUtility` hold-routes to Supernova / CrimsonBinding.
- `BloodControl` long-hold toggles modes: hardening / flowing red scale / convergence.

| Ability | Implementation notes |
|---|---|
| PiercingBlood | hold 0 none / 1000 single (35% dmg scale, range 25) / 2500 max output 60 ticks; block break threshold 3.0 |
| SlicingExorcism | hold 500+ launches blood chakram entity |
| CrimsonBinding | debuff duration 100, strength 2 |
| Supernova | 30% blood orb generation, radius loop |
| WingKing | 30s form, max 8 homing orbs, poison 3s |
| FlowingRedScale / Convergence / Hardening | mode flags consumed by control/other casts |

---

## 5. Movesets (choreography layer)

Package: `net.hadences.entity.movesets.cursed_techniques.*`

Examples (straw doll):

- `PiercingNailMoveset`
- `HairpinEnlargementMoveset`
- `HairpinExplosionMoveset`
- `NailBindCurseMoveset`
- `ResonanceMoveset`

Also: blood, boogie, speech, ratio movesets.  
Ability class often **starts** moveset/entity; numbers for multi-hit frames live in moveset.

---

## 6. Effects (status)

| Effect class | Related |
|---|---|
| `InfinityEffect` | Limitless infinity |
| `SixEyesEffect` | Six eyes |
| `OvertimeEffect` | Ratio overtime |
| `PhantomApplauseEffect` | Boogie |
| `SixthSenseEffect` | Boogie |
| `ReverseCursedTechniqueEffect` | RCT heal/regen style |
| `WingKingEffect` | Blood form |
| `ZenithFocusEffect` | Passive focus |
| `StunEffect` / `SuppressedEffect` | CC |
| `ModEffects` registry | central |

---

## 7. Cursed Energy

`CursedEnergySystem` (decompiled) tracks last CE use timestamp for «combat sleep» regen gating:

- `onUseCurseEnergy(player)` stores millis
- `isCurseEnergySleep(player, combat_durationSeconds)` true if still in combat window

Max CE / current CE live in `CursedEnergyData` + commands `SetMaxCursedEnergyCommand`.  
Regen handler: `CursedEnergyRegenerationHandler`.

---

## 8. Damage types (data/)

Under jar `data/projectjjk/damage_type/` (examples seen in research):

- `nail_damage`
- `hairpin_enlargement`
- `hairpin_explosion`
- `curse_bind_nail`
- plus learned: power_punch, black_flash, heavy_blow, pummel_barrage, finalitys_edge, speech types, blue/red/purple, etc.

`ModDamageTypes` + `DamageTypeCategories` (PHYSICAL vs technique) gate Black Flash.

---

## 9. Networking / client

- Ability trigger / release packets (`TriggerAbilityOnReleasePacket`, etc.)
- Glowing/outline system for marks (`GlowingData` + mixins) — **not Specter-specific**, ProjectJJK own mixins
- FP animations (`util.fpanim`, e.g. `SnapAnimation`) for first-person hammer/nail feel
- Held item renderer mixins

---

## 10. Что НЕ ability, но рядом

- Cursed spirits grades 4→special + movesets (LaserBeam, PoisonShoot, Finger Bearer kit…)
- Dungeon / ChallengeArena / Quests
- Class selection GUI
- Ability hotbar HUD (`AbilityHudPointerData`)
- Teach commands: `TeachAbilityCommand`, `ClearAbilityHotbarCommand`

---

## 11. Dependency matrix (коротко)

| Нужно сделать | Specter | GeckoLib | Satin | SBL | BossBar | Midnight |
|---|---|---|---|---|---|---|
| CE / CD / hotbar / grades | нет | нет | нет | нет | нет | нет |
| Nail / hairpin / resonance **logic** | нет | нет | нет | нет | нет | нет |
| Nail/doll **3D models** | нет | да* | нет | нет | нет | нет |
| Black Flash screen flash | нет | нет | да* | нет | нет | нет |
| Spirit AI | нет | model | нет | да | опц | нет |
| Config screen | нет | нет | нет | нет | нет | да* |

\*оригинальный ProjectJJK путь; jujutsumod может заменить.

---

## 12. Полный список ability id (50)

```
black_flash, blast_away, blood_control, blood_hardening, blood_manipulation,
blood_utility, blue, blue_mastery, collapse, convergence, counter,
crimson_binding, crumble_away, dash, displacement_burst, dont_move, explode,
finalitys_edge, flash_step, flowing_red_scale, get_twisted, guard,
hairpin_enlargement, hairpin_explosion, heavy_blow, infinity, nail_bind_curse,
overtime, phantom_applause, piercing_blood, piercing_nail, power_punch,
pummel_barrage, purple, purple_spark, ratio, red, red_mastery, resonance,
resonant_remains, reverse_cursed_technique, showmaker, six_eyes, sixth_sense,
slicing_exorcism, spatial_swap, supernova, swift_strike, uppercut, wing_king,
zenith_focus
```

**Passives (setPassive true):**  
`black_flash`, `guard`, `zenith_focus`, `ratio`, `resonant_remains`

---

## 13. Caveats

1. CFR ≠ original source; local names reconstructed.
2. Некоторые числа баланса в moveset/entity, не в Ability ctor.
3. Hold-function damage может отличаться от base DAMAGE.
4. Mastery abilities могут требовать teach path вне grade table.
5. Ten Shadows / Construction / Black Bird — **stubs**.
6. **Не копировать** decompiled Java (ARR).
7. ProjectJJK = **1.21.1**; jujutsumod = **1.21.8** — API drift real.

---

## 14. Decompile artifacts (local)

- Extract: `C:\Users\KOMP1\Downloads\projectjjk_extract`
- Decompiled abilities: `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled`
- Analyzer script: `C:\Users\KOMP1\Downloads\analyze_projectjjk_abilities.py`

---

*End of breakdown.*
