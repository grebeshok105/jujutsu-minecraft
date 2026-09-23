# Nobara: Target ESP, R feel, Mega Nail (B)

Status: APPROVED DESIGN (implementation in `feat/nobara-esp-and-mega-nail`)

## Что строим

1. **Target ESP** — персональный оверлей Нобары над целями, в которых сидят её гвозди.
2. **R (directed Hairpin)** — только ощущение удара; механика не меняется.
3. **B — «мега-гвоздь»** вместо mass Hairpin: все гвозди Нобары в одной прицельной цели сливаются в один снаряд, который пробивает цель насквозь; сильный удар + мощное отбрасывание; гвозди расходуются атомарно.

## Найденная архитектура (факты)

- Гвоздь — `ProjectJjkNailEntity` (prepared→launched→embedded), синхронизирован на клиент: `DATA_EMBEDDED`, `DATA_EMBEDDED_TARGET_ID`, `DATA_EMBEDDED_LOCAL_OFFSET/FORWARD`, `DATA_EMBED_DEPTH`, `DATA_OWNER_UUID`. ESP читает уже синхронизированные nail entities; owner UUID доступен клиенту через synced data.
- «Усиление» гвоздя = depth 1..3 (молот углубляет), множители `nailDepthMultiplier` 1.0/1.35/1.75.
- Серверный индекс — `NailAnchorRegistry` (per level+owner, cap 30); записи несут depth 1..3, origin (`LAUNCHED`/`TRAP_CORNER`/`TRAP_IMPACT`) и `targetId`; урон и валидация целей — только сервер (`hurtServer`, `TargetResolver`).
- Старый `tryEnlargeMarkedTarget`/`PendingEnlarge`-каркас удалён из production-проводки; прежняя delayed/retry-семантика заменена материальным Mega Nail v2 с 14-тиковым gather, 16-тиковым charge и 60-тиковым flight, без retry.
- Кулдаунов у способностей Нобары нет (executor проверяет isReady, но никто не start'ует).
- VFX-гейт: каждый live id в `NobaraVfxIds.LIVE` обязан иметь ровно один рецепт и production-эмиттер (byte-code скан); presentation radius ≤ delivery radius.
- HUD-вклады — только через `VfxDirector.registerHudContribution` (прецедент `MegumiCooldownHud`). Отдельные HUD-каллбеки запрещены Codex-контрактом.
- Системы рангов в проекте **нет** — «Grade 3» существует только строкой в локализации/roster.

## Решения

### ESP

Статус 2026-09-09: решения ниже АРХИВИРОВАНЫ вместе с реализацией — весь ESP/HUD-слой перенесён в `archive/combat-hud-v1` (README: состав, отключённые регистрации, шаги восстановления) и из активной сборки выключен. Запись сохранена как проектная справка для будущего восстановления: owner-поле, агрегатор, рендер, ранг.

- **Owner на клиент**: одно новое synched-поле `DATA_OWNER_UUID` (`OPTIONAL_UUID`) в `ProjectJjkNailEntity`, ставится в `prepare()`. Это расширение существующей синхронизации сущности, не параллельный учёт.
- **Агрегатор** `NobaraEspState` (client): каждые 2 клиентских тика пересобирает `Map<targetId, TargetEsp>` из `level.entitiesForRendering()`: embedded && owner == local player && цель жива. Гейт: выбранный вессел == NOBARA. Пересборка с нуля ⇒ stale-состояний нет по построению (смерть цели, discard гвоздя, смена вессела, выход — всё сходится к пустой мапе).
- **Рендер** — ESP переехал в screen-space HUD: `NobaraTargetHud` (регистрация через `VfxDirector.registerHudContribution`, паттерн `AbilityHud`) рисует name badge над целью + стек карточек health/grade/nails справа от цели на существующем SDF/MSDF-стеке; геометрия и анимации — `NobaraTargetLayout`/`NobaraTargetAnim`. Проекция мира на экран изолирована в `ui.WorldToScreen`. Лидер-гвоздь и world-space billboard удалены из `ProjectJjkNailRenderer`; рендерер сохранил только акцентное пульс-кольцо своих гвоздей (0xE48A36). Никаких новых render-хуков и миксинов; world-to-screen изолирован в ui.WorldToScreen (JUnit).
- **Ранг**: системы нет ⇒ детерминированная клиентская классификация `NobaraEspRanks` (продуктовое допущение, вынесено в отчёт): игрок → grade его вессела из roster-строки; моб → по maxHealth: ≥100 Special Grade, ≥40 Grade 1, ≥20 Grade 2, иначе Grade 3. Пороги — именованные константы, локализуемые ключи.

### R feel (механика заморожена)

Сервер уже играет звуки и шлёт `EXPLOSION` cue в тик реального урона (`explodeChainNail`) — синхронность есть. Полировка только в клиентском рецепте `EXPLOSION` (`NobaraVfxRecipes`): камера-импакт по близости, чуть плотнее burst на depth 3, отчётливее finale. Ни новых пакетов, ни серверных изменений, ни повторного урона.

### Mega Nail (B)

- **Роутер**: `SECONDARY -> canCastMarkedHairpin(nobara) && ProjectJjkMegaNailRuntime.start(nobara)` (гейт остаётся — как требует `NobaraAbilitySlotsTest`).
- **Каст** (`start`): `TargetResolver.resolve(HAIRPIN_ENLARGE_RANGE=20)` → живая цель; гвозди цели выбираются через `NailAnchorRegistry.anchorsOnTarget` (depth/origin/targetId). Нет цели или гвоздей → `false` ⇒ единый fallback-тост роутера; кулдаун не жжётся, повторный крик дёшев.
- **Атомарный расход в t0 каста**: снапшот `NailAnchorRegistry` + `weight = Σ nailDepthMultiplier(depth)`; выбранные гвозди немедленно `discard()`, затем `ProjectJjkNailMarks.consume(target)`. Повторная активация в тот же тик не находит гвоздей ⇒ двойной расход исключён на источнике. ENLARGE cue на каждом гвозде + `CASTER_ACTION(CASTER_MEGA_NAIL=5)`.
- **Удар** entity-driven: после 14-тикового gather Mega Nail материализуется в точке сборки, заряжается `MEGA_CHARGE_TICKS = 16`, затем летит; `MEGA_NAIL_FLIGHT_TIMEOUT_TICKS = 60` завершает промах. PENDING/retry-цикла нет: сущность сама хранит `weight/count/targetId`, а цель переоценивается при запуске.
- **Формулы** (всё из существующего баланса, именованные константы в Profile):
  - `damage = min(HAIRPIN_ENLARGE_DAMAGE_PER_NAIL(4.0) × weight, MEGA_NAIL_DAMAGE_CAP) × ResonantMomentum` — per-nail база унаследована у enlarge (та же семантика «направленный удар по одной цели её гвоздями»);
  - `MEGA_NAIL_DAMAGE_CAP = 42.0` = 1.5 × `RESONANCE_DAMAGE(28)` — сильнейший разовый удар кита с полной подготовкой; продуктовое допущение, отмечено в отчёте;
  - `knockback = min(HAIRPIN_KNOCKBACK(1.9) + 0.2 × count, 3.0)`; 0.2 = `HAIRPIN_EXPLOSION_KNOCKBACK`; кап 3.0 чуть выше сильнейшего существующего (pounce 2.4) — это ультимативный расход; направление — зафиксированный в pending вектор «кастер→цель» («пройти насквозь»);
  - stagger = `HEAVY_STAGGER_TICKS(14)`.
  - Урон — ровно один `hurtServer`; никакого клиентского урона.
- **Кулдаун не добавляем**: в ките Нобары их нет; стоимость — все гвозди цели. Продуктовая неоднозначность в отчёте.
- **Чистый cutover mass Hairpin**: `startMassHairpin`, `HairpinChain.Mode.MASS`-ветки, `HAIRPIN_MASS_CHAIN_DELAY_TICKS`, `HAIRPIN_BOOM_DAMAGE_PER_NAIL`, `CASTER_HAIRPIN_MASS`, mass-строки roster/lang — удалены. `tryEnlargeMarkedTarget`+`PendingEnlarge` также удалены; их delayed/retry-каркас заменён материальным Mega Nail v2.
- **VFX**: live id `MEGA_NAIL_STRIKE` uses `worldFixedDisplacement` (origin = точка удара, `anchorOffset` = полный вектор прохода), intensity = clamp(count,1..7)|finale-бит не нужен. Delivery 64.0 (существующий `VFX_DELIVERY_RADIUS`), presentation ≤ 64; направленный трассер-«копьё» + импакт-burst + камера, а звук уже серверный (`PROJECTJJK_DEEP_EXPLOSION`, `PROJECTJJK_AEC_BOOM`).

### Mega Nail v2 — материальный гвоздь (смок-фидбек 2026-08-05, УТВЕРЖДЕНО)

Смок отверг «эффект взрыва без снаряда». Требование продукта: гвозди цели **медленно стягиваются воедино** в **большой прокачанный гвоздь, материализующийся ПЕРЕД Нобарой**, который затем пробивает цель. Богатая подача: заряд, стяжка, свечение, запуск, трассер, импакт.

- **Снаряд — реальная сущность**: тот же `ProjectJjkNailEntity` с новым synced-флагом `DATA_MEGA` и `DATA_MEGA_PROGRESS` (float 0→1). Persistent-визуал живёт на рендерере сущности (контракт VFX Core), transient-слои — на директоре.
- **Таймлайн**: `start()` (селекция/расход/метки — без изменений) → GATHER `MEGA_GATHER_TICKS = 14` (гвозди стягиваются к точке сборки) → спавн mega-гвоздя (глаза −0.2, +1.6 блока по взгляду, заморожена) → CHARGE `MEGA_CHARGE_TICKS = 16` (рост scale 0.6→2.6, прогресс синкается) → LAUNCH: перенацеливание на актуальную позицию цели, если она жива в ≤48 блоках, иначе замороженный вектор; скорость `NAIL_SPEED×1.3` → IMPACT: та же формула урона/kb/stagger (stagger LivingEntity-overload ДО knockback), `MEGA_NAIL_STRIKE` cue; блок/таймаут 60 тиков → терминальный VFX, без эмбеда.
- **PENDING-цикл рантайма удаляется**: сущность самодостаточна (weight/count/targetId на ней), retry-механика не нужна, две Нобары = две сущности.
- **Стяжка**: consume-ENLARGE cue становится направленным (direction = гвоздь→точка сборки) — клиент рисует поток частиц вдоль вектора.
- **Новый live id `MEGA_NAIL_CHARGE`** (24-тиковый world-fixed VFX-рецепт в точке сборки: кольца, спираль частиц, glow, звук зарядки; механическая charge-фаза сущности — 16 тиков; presentation WIDE ≤ delivery 64).
- **Константы**: `MEGA_GATHER_TICKS = 14`, `MEGA_CHARGE_TICKS = 16`; `MEGA_NAIL_FLIGHT_TIMEOUT_TICKS = 60`, `MEGA_NAIL_SPEED_MULTIPLIER = 1.3`, `MEGA_NAIL_SCALE_*` — в Profile. Legacy `MEGA_NAIL_CHARGE_TICKS` не является live timing owner.

## Тесты

- Правки: `NobaraAbilitySlotsTest` (SECONDARY → `ProjectJjkMegaNailRuntime.start`), `HairpinChainTest` (MASS-кейсы долой), `ProjectSanityTest` (mass-упоминания).
- Новые (JUnit, без мира): `NobaraEspRanksTest` (классификация), `ProjectJjkMegaNailMathTest` (damage/knockback формулы: 1 гвоздь, N гвоздей, кап, depth-веса), ESP-агрегация как чистая функция над снапшотом (gating: чужой владелец, не-Нобара, мёртвая цель).
- Авто-гейты сами проверят: recipe/emitter completeness нового id, radius contract, roster-slot соответствие.
- In-world поведение — ручной смок (E1: GameTest в проекте нет).

## Не делаем

Кулдаун/ресурс-бар, новые зависимости, изменение других весселов, синхронизацию server-side `NailAnchorRegistry` на клиент (ESP читает уже синхронизированные nail entities).
