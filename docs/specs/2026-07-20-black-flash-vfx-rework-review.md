# Spec / Deep Review: Black Flash VFX Rework

Дата: 2026-07-20  
Статус: REVIEW ONLY (исправления **не** вносились)  
Ревьюер: peer review (не автор реализации)  
Диапазон: `c208eb7` … `5821c74` (6 коммитов на `main` / `origin/main`)  
Сборка при ревью: `gradlew.bat test` → **BUILD SUCCESSFUL** (тесты UP-TO-DATE / green)

---

## 1. Scope и коммиты

| Коммит | Сообщение | Содержание (факт) |
|--------|-----------|-------------------|
| `c208eb7` | feat(vfx): add direction field to VfxCue | 8-е поле `Vec3 direction`, normalize в compact ctor, codec, все emitters + NailTrap migration, тесты cue/timeline/anchor |
| `5e439e6` | feat(vfx): add Black Flash palette and camera preset | 5 BF colors в `VfxPalette`, `VfxCameraChannel.triggerBlackFlash` |
| `0dec3f1` | feat(vfx): add BLACK_FLASH directional world impact style | `ImpactStyle.BLACK_FLASH` + 4 фазы geometry + basis/fallback + seeded PRNG |
| `ac6bfa1` | feat(vfx): register Black Flash particle types | `BF_LIGHTNING/SPARK/IMPACT` registry + client providers + JSON + textures under `textures/particle/black_flash/` |
| `8ff8d35` | feat(nobara): rework Black Flash VFX recipe | recipe rewrite, `emitDirected`, emitters direction, sanity 39→40 |
| `5821c74` | docs(codex): update Black Flash VFX documentation | VFX-core, Hairpin-effects, Combat-timing-and-black-flash |

Коммит-сплит **здоровый**: infra → palette/camera → world → particles → recipe/emit → docs. Совпадает с изначальным планом и с peer-feedback (direction blast radius + NailTrap migration в commit 1).

---

## 2. Executive verdict

| Ось | Оценка | Комментарий |
|-----|--------|-------------|
| Полнота vs заявленный feature | **Высокая** | Directional world BF, custom particles, camera, HUD stack, FP snap, 4-layer sound, server direction — всё есть в коде |
| Архитектурная вписываемость | **Хорошая** | Идёт через VfxCue → Director → Recipe → Channels; world impact one-shot на opening beat |
| Качество реализации | **Средне-высокое** | Читаемо, fallback basis, seeded lightning, density-scaled bursts |
| Тестовая страховка | **Слабая** | Codec/direction покрыты; geometry/camera/BF recipe — почти нет behavioral/guard depth |
| Docs sync | **Хорошая для 3 vault-файлов** | Claim-Source / parity map не обновлены |
| Legal / assets | **Риск без изменений** | BF textures — копии ProjectJJK ARR |
| Production readiness (feel) | **UNKNOWN без in-game** | Unit green ≠ cinematic quality |

**Итог:** rework **реально закрывает заявленный scope** и выглядит как добросовестная, структурированная реализация. Это не «пустой PR». При этом есть **средние** пробелы (test guards, ZERO-direction fallback, spark directionality, tiny textures, GeckoLib bypass, HUD soup risk) и **непроверенный** in-game feel.

Оценка: **7.8 / 10** как delivery feature; **6.5 / 10** как long-term VFX engineering (tests + edge cases).

---

## 3. Что сделано хорошо

### 3.1 Protocol / infrastructure (`c208eb7`)

- `VfxCue` получил `direction` с normalize / `Vec3.ZERO` fallback в compact constructor.
- `VfxCuePayload` читает/пишет direction после `seed` — порядок стабильный.
- **NailTrap** перестал пихать direction в `anchorOffset` — правильная миграция:
  - было: `anchorOffset = direction`
  - стало: `anchorOffset = ZERO`, `direction = direction`
- Все call sites обновлены (не оставили half-migrated compile breaks).
- Тесты cue/timeline/anchor обновлены под 8-arg ctor.

### 3.2 Recipe lifecycle (`8ff8d35`) — важно

Автор **исправил** ошибочную модель «every tick» из раннего плана:

- **Opening beat:** world impact, particles, sounds, GeckoLib
- **On start (once, age-aware):** camera / HUD / blur / FP snap

`VfxInstance.start()` one-shot — структура соответствует реальному director.

World impact **не** re-trigger'ится каждый кадр → нет stack 28× ImpactFlash.

### 3.3 World geometry (`0dec3f1`)

- 4 фазы (compression / blades / lightning / shockwave) с progress windows.
- `directionalBasis`: fallback при `|forward · UP| > 0.98` (NORTH as up ref) — закрывает look up/down NaN.
- Lightning **seeded** от `cue.seed()` (`pseudoRandom`) — детерминизм / late join consistency.
- 5/7 bolts biased along forward, 2 full random — разумный visual bias.
- Палитра void/crimson/white отделена от cursed blue — BF читается другим language.

### 3.4 Camera (`5e439e6`)

- Отдельный preset, не reuse heavyImpact.
- Freq **120 / 135** (не 28 из плохого черновика плана) — high-frequency vibe.
- Короче resonance (impulses ~130ms, FOV envelope ~300ms) — intent «злее и короче» соблюдён по числам.
- Peak yaw ~6.5+2.2 в пределах clamp ±9 (при strength≈1).

### 3.5 Particles (`ac6bfa1` + recipe)

- Бюджет opening **~62** raw counts (3+5+8+12+20+14), не 140+ из раздутого плана — лучше.
- `context.burst` → `VfxQuality.scaledCount` — density scale **реально** работает для particles.
- World geometry **не** gated quality → на MINIMAL blades/lightning остаются (как заявлено в handoff).
- 3 provider-класса с внятной физикой: flicker, gravity spark, expand impact.

### 3.6 Emitters / first-person

- Nail BF: `nail.deltaMovement` с fallback `lookAngle` если velocity≈0.
- Hammer BF: `player.getLookAngle()`.
- FP snap: `localPlayer.getId() == cue.anchorEntityId()` — корректный caster-only gate при текущем `emitDirected` (anchor = player).

### 3.7 Docs (`5821c74`)

- Combat-timing получил dedicated VFX composition section.
- VFX-core: direction field + BF scene row.
- Hairpin-effects: BF row обновлён.

### 3.8 Process

- 6 атомарных коммитов, осмысленные messages.
- `gradlew test` green на HEAD.

---

## 4. Findings (по severity)

### HIGH

#### H1. Age-aware sanity guard **не видит** `triggerBlackFlash` / `triggerFlash`

**Файл:** `ProjectSanityTest.java` (~478)

Regex:

```text
trigger(?:Launch|HeavyImpact|Explosion|Ritual|Swing|Impact|Snap|Blur|ResonanceImpact|SlowMotion|Nausea)
```

Факт на HEAD:

| Call | В regex? |
|------|----------|
| `triggerBlackFlash(..., initialAgeTicks)` | **Нет** |
| `triggerFlash(..., initialAgeTicks)` | **Нет** |
| `triggerImpact` / `Snap` / `Nausea` / `Blur` | Да |

Счётчик 40 **случайно** сходится (HeavyImpact→новые Nausea/Snap и т.д.), но:

- можно **удалить** age-aware `triggerBlackFlash` — assert всё равно green;
- guard **не защищает** главный camera path BF.

**Риск:** silent regression camera/flash late-join offsets.

#### H2. ZERO-direction + BLACK_FLASH = broken omnidirectional fallback

`directionalBasis(ZERO)` возвращает `{EAST, UP}` **без** подмены forward на default axis.

В blades:

```java
bladeDir = forward.scale(cos).add(right.scale(sin)).normalize();
// forward == ZERO → blades вдоль right, не omni/forward
```

BF emitters сейчас всегда шлют non-zero direction → **latent**. Если кто-то вызовет `BLACK_FLASH` с `Vec3.ZERO` (generic emit path), slash читается как lateral веер, не omni.

#### H3. GeckoLib `triggerAction` всё ещё вне channel contract

Opening beat:

```java
NobaraPlayerGeoAnimatable.INSTANCE.triggerAction(entity, "black_flash");
```

Это pre-existing VFX Core contract violation (audit H12). Rework **оставил** bypass. Не регресс, но BF по-прежнему не pure channel pipeline.

---

### MEDIUM

#### M1. Sparks **не** directional

План / handoff: «искры вдоль direction».  
Рецепт: isotropic `burst(BF_SPARK, origin, 12, 0.8, 1.2, random)` — без bias look/velocity.

World blades directional; particle cloud — нет. Visual language partially inconsistent.

#### M2. Tiny / suspect textures (109 bytes)

| File | Size |
|------|------|
| `bf_impact4.png` | 109 B |
| `bf_lightning1.png` | 109 B |
| `bf_lightning11.png` | 109 B |

Скорее empty/near-empty frames. Flicker sequence может «моргать» пустыми спрайтами. Нужен visual check; unit tests это не ловят.

#### M3. ARR asset surface expanded

Textures **скопированы** из `projectjjk/particle/black_flash/` в runtime path `textures/particle/black_flash/`.

- Дублирование байтов в jar.
- Legal: всё ещё ProjectJJK ARR (Hadences).
- Публикация risk не уменьшен.

#### M4. Camera «270ms» — marketing number

Факт:

- impulse windows: 0–90, 30–140, 60–130 → ~140ms shake body
- FOV: 0–160 and 100–300 → envelope **~300ms**

Docs/commit claim «270ms total» — приблизительно, не derived constant. Не баг, но drift.

#### M5. HUD stack density (feel risk)

Одновременно:

- `triggerImpact` vignette/speed lines  
- white `triggerFlash` 180ms  
- `triggerNausea(0.6)`  
- blur 260ms  
- aggressive camera  

При proximity≈1 на close range — риск «soup» / PvP nausea. Проximity scale есть, но soft-cap по «читаемости удара» нет. **In-game only.**

#### M6. Anchor = caster → VFX follows player

`emitDirected` ставит `anchorEntityId = player.getId()`, `anchorOffset = origin - player.pos`.

Pre-existing pattern (старый `emit` тоже так делал). На 28 ticks BF: если caster двигается, resolved origin **уезжает** с ним. Для impact-at-target это может выглядеть как «вспышка прилипла к игроку». Не введено rework'ом, но BF теперь **дольше и заметнее** → симптомы сильнее.

#### M7. Docs partial

Обновлены:

- `VFX-core.md`
- `Hairpin-effects.md`
- `Combat-timing-and-black-flash.md`

**Не** обновлены (stale relative to feature):

- `Claim-Source-Index.md` (direction field / 8-arg cue / new particles)
- `ProjectJJK-parity-map.md` (Black Flash всё ещё «MISSING» в parity table)
- line-ref drift elsewhere

#### M8. Нет unit tests на core BF math

Нет тестов на:

- `directionalBasis` up/down degeneracy  
- `pseudoRandom` stability  
- recipe contains `ImpactStyle.BLACK_FLASH` / `triggerBlackFlash` (кроме raw count 40)  
- particle registration presence  

Regression surface = manual only.

---

### LOW

#### L1. `direction == null` → NPE in compact ctor

`direction.lengthSqr()` без null-check. Call sites non-null; defensive coding absent.

#### L2. Comment noise in `VfxWorldChannel.renderBlackFlash`

Phase comments в production code — стиль репо обычно minimal; cosmetic.

#### L3. Dust colors hard-coded hex, not `VfxPalette`

`0xB40014` / `0xFF1E3C` vs palette constants — drift risk if palette retuned.

#### L4. Particle path duplication

Оригиналы остаются под `textures/projectjjk/particle/black_flash/` + копии в `textures/particle/black_flash/`. Orphan risk.

#### L5. Sanity «40» magic number

Любое добавление age-aware call без bump assert → red build. ОК: include BlackFlash/Flash in regex, or keep inventory table.

#### L6. Network protocol version

Добавление 3 doubles в payload без protocol id — нормально для single-mod version lock, ломает mixed client/server old/new. Dev-stage OK.

---

## 5. Claim check vs handoff summary

| Заявление (handoff) | Код | Вердикт |
|---------------------|-----|---------|
| Directional slash blades (4) | `for (index < 4)` fan along forward | **TRUE** |
| 7 seeded lightning zigzags | 7 bolts, seed PRNG, 4–5 segments | **TRUE** |
| Compression → shockwave | phases A and D | **TRUE** |
| 3 custom particle types | registry + providers + JSON | **TRUE** |
| 4-layer sound | impact, impact2, snap, deep_explosion | **TRUE** |
| Aggressive camera 270ms, freq 120–135, FOV −12/+8 | freq true; FOV true; 270ms ≈ 300ms envelope | **MOSTLY** |
| White HUD flash + nausea + blur | present | **TRUE** |
| FP snap caster-only | id match | **TRUE** |
| Opening world impact one-shot | opening beat only | **TRUE** |
| Up/down no NaN (basis fallback) | `|dot| > 0.98` → NORTH | **LIKELY** (unit untested) |
| MINIMAL particles keep world geometry | quality only scales bursts; world channel independent | **TRUE** |
| Sparks along direction | isotropic burst | **FALSE vs plan** |
| Fully pushed | `origin/main == 5821c74` | **TRUE** (at review time) |

---

## 6. Comparison with peer plan feedback (pre-implementation)

| Feedback item | Addressed? |
|---------------|------------|
| Fix «every tick» model | **Yes** |
| Direction blast radius + all ctors | **Yes** |
| NailTrap migrate off anchorOffset | **Yes** |
| High-freq camera (not 28) | **Yes** (120–135) |
| Basis degeneracy fallback | **Yes** |
| Particle budget cut | **Yes** (~62 vs 140) |
| Seeded lightning RNG | **Yes** |
| Acceptance criteria / in-game checklist | **Partial** (docs; no automated) |
| Phase particles optional | Shipped full (ok) |
| Guard `BLACK_FLASH` ImpactStyle in sanity | **No** (only count 39→40) |

---

## 7. Residual risks (play / ship)

1. **Feel / readability** — only human QA (`/jujutsu forcedblackflash true`).  
2. **Close-range HUD soup** — nausea+flash+blur+camera.  
3. **Moving caster** — anchored-to-player flash drift.  
4. **Empty particle frames** — 109 B textures.  
5. **Legal** — ARR textures still ship.  
6. **Regression** — weak automated net on BF-specific paths.

---

## 8. Recommended follow-ups (не сделано в этом ревью)

P1:

1. Расширить age-aware regex: `BlackFlash|Flash|...` + assert recipe contains `ImpactStyle.BLACK_FLASH` / `triggerBlackFlash`.  
2. Unit test `directionalBasis` for ZERO / up / down / horizontal.  
3. ZERO-direction: treat as default forward (e.g. NORTH) inside `renderBlackFlash` or basis.  
4. Visual QA: empty frames `bf_lightning1/11`, `bf_impact4`.

P2:

5. Directional spark burst (bias velocity along `cue.direction()`).  
6. Optional: pin BF origin with `NO_ANCHOR` + fixed world origin for non-moving impact.  
7. Update Claim-Source-Index + parity map.  
8. Replace ARR BF particle textures with original art before public release.

P3:

9. Palette-driven dust colors.  
10. Drop duplicate `projectjjk/particle/black_flash` if unused by other paths.

---

## 9. Files touched (implementation surface)

**Core protocol:**  
`VfxCue.java`, `VfxCuePayload.java`

**Client VFX:**  
`VfxPalette.java`, `VfxCameraChannel.java`, `VfxWorldChannel.java`, `NobaraVfxRecipes.java`

**Particles:**  
`JujutsuParticles.java`, `JujutsuClientParticles.java`,  
`BfLightningParticle.java`, `BfSparkParticle.java`, `BfImpactParticle.java`,  
`particles/bf_*.json`, `textures/particle/black_flash/*`

**Server emitters:**  
`NobaraHammerCombatRuntime.java` (`emitDirected`),  
plus all `new VfxCue(...)` sites + `NailTrapRuntime.emit`

**Tests:**  
`VfxCueTest`, `VfxTimelineTest`, `VfxAnchorResolverTest`, `ProjectSanityTest` (count only)

**Docs (vault):**  
`VFX-core.md`, `Hairpin-effects.md`, `Combat-timing-and-black-flash.md`

---

## 10. In-game verification checklist (for human)

Не прогонялось в этом ревью; оставить для ручного QA:

- [ ] `/jujutsu forcedblackflash true` → hammer hit living → blades along look  
- [ ] prepared-nail BF → direction follows nail velocity (or look fallback)  
- [ ] look straight up / down → no NaN / missing geometry  
- [ ] particles MINIMAL → world blades/lightning still visible  
- [ ] 1P caster → hand snap; other player watching → no their hand snap  
- [ ] walk during BF → does flash stick to caster uncomfortably?  
- [ ] 2 BF within 1s → camera clamp / flash not stuck  
- [ ] inspect particle frames for empty sprites  

---

## 11. Final statement

Black Flash VFX Rework **реализован по существу**, запушен (`5821c74`), компилируется и проходит существующие unit-тесты. Архитектурно rework **сильнее** исходного плана (lifecycle, particle budget, camera freq, NailTrap migration).

Главные замечания ревью — **не «сломано / не работает»**, а:

1. слабая automated защита BF-specific paths (H1),  
2. latent ZERO-direction behavior (H2),  
3. legal/tiny textures/feel risks (M2–M5),  
4. docs/index gaps (M7).

**Никаких code changes не вносилось** в рамках этого документа.
