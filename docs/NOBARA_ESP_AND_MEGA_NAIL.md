# Nobara: Target ESP, R feel, Mega Nail (B)

Status: APPROVED DESIGN (implementation in `feat/nobara-esp-and-mega-nail`)

## Что строим

1. **Target ESP** — персональный оверлей Нобары над целями, в которых сидят её гвозди.
2. **R (directed Hairpin)** — только ощущение удара; механика не меняется.
3. **B — «мега-гвоздь»** вместо mass Hairpin: все гвозди Нобары в одной прицельной цели сливаются в один снаряд, который пробивает цель насквозь; сильный удар + мощное отбрасывание; гвозди расходуются атомарно.

## Найденная архитектура (факты)

- Гвоздь — `ProjectJjkNailEntity` (prepared→launched→embedded), synched на клиент: `DATA_EMBEDDED`, `DATA_EMBEDDED_TARGET_ID`, `DATA_EMBEDDED_LOCAL_OFFSET/FORWARD`, `DATA_EMBED_DEPTH`. **Owner не синхронизирован** (только server-side `ownerUuid`).
- «Усиление» гвоздя = depth 1..3 (молот углубляет), множители `nailDepthMultiplier` 1.0/1.35/1.75.
- Серверный индекс — `EmbeddedNailRegistry` (per level+owner, cap 30); урон и валидация целей — только сервер (`hurtServer`, `TargetResolver`).
- `tryEnlargeMarkedTarget`/`PendingEnlarge` — **мёртвый в проводке** ProjectJJK-порт: телеграф → задержка 20 тиков → per-nail урон 4.0 → discard, с RETRY/TERMINAL-семантикой. Роутер его не зовёт; тесты только запрещают прятать его в молот.
- Кулдаунов у способностей Нобары нет (executor проверяет isReady, но никто не start'ует).
- VFX-гейт: каждый live id в `NobaraVfxIds.LIVE` обязан иметь ровно один рецепт и production-эмиттер (byte-code скан); presentation radius ≤ delivery radius.
- HUD-вклады — только через `VfxDirector.registerHudContribution` (прецедент `MegumiCooldownHud`). Отдельные HUD-каллбеки запрещены Codex-контрактом.
- Системы рангов в проекте **нет** — «Grade 3» существует только строкой в локализации/roster.

## Решения

### ESP

- **Owner на клиент**: одно новое synched-поле `DATA_OWNER_UUID` (`OPTIONAL_UUID`) в `ProjectJjkNailEntity`, ставится в `prepare()`. Это расширение существующей синхронизации сущности, не параллельный учёт.
- **Агрегатор** `NobaraEspState` (client): каждые 2 клиентских тика пересобирает `Map<targetId, TargetEsp>` из `level.entitiesForRendering()`: embedded && owner == local player && цель жива. Гейт: выбранный вессел == NOBARA. Пересборка с нуля ⇒ stale-состояний нет по построению (смерть цели, discard гвоздя, смена вессела, выход — всё сходится к пустой мапе).
- **Рендер** — по Codex-правилу «persistent visuals живут на entity/state renderer»: `ProjectJjkNailRenderer` получает два дополнения:
  - пульс-кольцо гвоздя владельца подкрашивается акцентом Нобары (0xE48A36) — «положение гвоздей на теле» уже рендерится, остаётся пометить «свои»;
  - **лидер-гвоздь** цели (min entity id из агрегатора) рисует один сдержанный биллборд над целью: строка HP (`♥ 12.5/20`), строка ранга, строка `⚲ ×N` + депт-пипсы (`•/••/•••` на гвоздь). Тёмный полупрозрачный фон, без свечения, масштаб nameplate.
  - Никаких новых render-хуков, миксинов и HUD-каллбеков; ноль world-to-screen математики.
- **Ранг**: системы нет ⇒ детерминированная клиентская классификация `NobaraEspRanks` (продуктовое допущение, вынесено в отчёт): игрок → grade его вессела из roster-строки; моб → по maxHealth: ≥100 Special Grade, ≥40 Grade 1, ≥20 Grade 2, иначе Grade 3. Пороги — именованные константы, локализуемые ключи.

### R feel (механика заморожена)

Сервер уже играет звуки и шлёт `EXPLOSION` cue в тик реального урона (`explodeChainNail`) — синхронность есть. Полировка только в клиентском рецепте `EXPLOSION` (`NobaraVfxRecipes`): камера-импакт по близости, чуть плотнее burst на depth 3, отчётливее finale. Ни новых пакетов, ни серверных изменений, ни повторного урона.

### Mega Nail (B)

- **Роутер**: `SECONDARY -> canCastMarkedHairpin(nobara) && ProjectJjkMegaNailRuntime.start(nobara)` (гейт остаётся — как требует `NobaraAbilitySlotsTest`).
- **Каст** (`start`): `TargetResolver.resolve(HAIRPIN_ENLARGE_RANGE=20)` → живая цель; гвозди цели по образцу enlarge (AABB inflate 2.0, isEmbedded && isOwnedBy && anchor.stableId==target). Нет цели или гвоздей → `false` ⇒ единый fallback-тост роутера (прецедент — `canCastMarkedHairpin` false-до-рантайма); кулдаун не жжётся, повторный крик дёшев.
- **Атомарный расход в тике каста**: снапшот `List<UUID>` + `weight = Σ nailDepthMultiplier(depth)`; каждый гвоздь `discard()` немедленно (гварды `isRemoved`/`isOwnedBy`/anchor как в enlarge); `ProjectJjkNailMarks.consume(target)`. Повторная активация в тот же тик не находит гвоздей ⇒ двойной расход исключён на источнике. ENLARGE cue на каждом гвозде (стягивание) + `CASTER_ACTION(CASTER_MEGA_NAIL=5)`.
- **Удар** через `MEGA_NAIL_STRIKE_DELAY_TICKS = 6` (прецедент `NAIL_TRAP_COLLAPSE_TICKS`): pending-запись `{casterId, targetUuid/entityId, dueTime, weight, count, direction}` в собственном tick-цикле рантайма (паттерн PendingEnlarge). Цель жива → урон+knockback+stagger+VFX; цель умерла/удалена → TERMINAL, только пролётный VFX (гвозди потрачены — согласовано с Hairpin, где гвозди взрываются независимо); временно недоступна (чанк) → RETRY до `MEGA_NAIL_RETRY_TIMEOUT_TICKS = 40` (= 2×ENLARGE_DELAY; enlarge ретраил вечно — таймаут закрывает утечку).
- **Формулы** (всё из существующего баланса, именованные константы в Profile):
  - `damage = min(HAIRPIN_ENLARGE_DAMAGE_PER_NAIL(4.0) × weight, MEGA_NAIL_DAMAGE_CAP) × ResonantMomentum` — per-nail база унаследована у enlarge (та же семантика «направленный удар по одной цели её гвоздями»);
  - `MEGA_NAIL_DAMAGE_CAP = 42.0` = 1.5 × `RESONANCE_DAMAGE(28)` — сильнейший разовый удар кита с полной подготовкой; продуктовое допущение, отмечено в отчёте;
  - `knockback = min(HAIRPIN_KNOCKBACK(1.9) + 0.2 × count, 3.0)`; 0.2 = `HAIRPIN_EXPLOSION_KNOCKBACK`; кап 3.0 чуть выше сильнейшего существующего (pounce 2.4) — это ультимативный расход; направление — зафиксированный в pending вектор «кастер→цель» («пройти насквозь»);
  - stagger = `HEAVY_STAGGER_TICKS(14)`.
  - Урон — ровно один `hurtServer`; никакого клиентского урона.
- **Кулдаун не добавляем**: в ките Нобары их нет; стоимость — все гвозди цели. Продуктовая неоднозначность в отчёте.
- **Чистый cutover mass Hairpin**: `startMassHairpin`, `HairpinChain.Mode.MASS`-ветки, `HAIRPIN_MASS_CHAIN_DELAY_TICKS`, `HAIRPIN_BOOM_DAMAGE_PER_NAIL`, `CASTER_HAIRPIN_MASS`, mass-строки roster/lang — удаляются. `tryEnlargeMarkedTarget`+`PendingEnlarge` удаляются (мёртвые; их каркас и семантика переезжают в мега-рантайм; эмиттер ENLARGE переезжает в converge-фазу).
- **VFX**: +1 live id `MEGA_NAIL_STRIKE` (LIVE 22→22: −0 +1, mass не имел собственного id; итог 23). Транспорт: `worldFixedDisplacement` (origin = точка слияния, anchorOffset = полный вектор прохода), intensity = clamp(count,1..7)|finale-бит не нужен. Delivery 64.0 (существующий `VFX_DELIVERY_RADIUS`), presentation ≤ 64. Рецепт: направленный трассер-«копьё» + импакт-burst + камера + HUD flash低 + звук уже серверный (`PROJECTJJK_DEEP_EXPLOSION`, `LONG_WHOOSH` в тик удара).

## Тесты

- Правки: `NobaraAbilitySlotsTest` (SECONDARY → `ProjectJjkMegaNailRuntime.start`), `HairpinChainTest` (MASS-кейсы долой), `ProjectSanityTest` (mass-упоминания).
- Новые (JUnit, без мира): `NobaraEspRanksTest` (классификация), `ProjectJjkMegaNailMathTest` (damage/knockback формулы: 1 гвоздь, N гвоздей, кап, depth-веса), ESP-агрегация как чистая функция над снапшотом (gating: чужой владелец, не-Нобара, мёртвая цель).
- Авто-гейты сами проверят: recipe/emitter completeness нового id, radius contract, roster-slot соответствие.
- In-world поведение — ручной смок (E1: GameTest в проекте нет).

## Не делаем

Кулдаун/ресурс-бар, новые зависимости, изменение других весселов, синхронизацию EmbeddedNailRegistry на клиент (ESP читает уже синхронизированные nail entities), world-to-screen HUD-проекцию.
