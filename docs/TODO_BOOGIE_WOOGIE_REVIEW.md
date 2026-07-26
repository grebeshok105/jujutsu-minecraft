# Глубокое ревью Aoi Todo / Boogie Woogie

**Целевая ветка:** `feat/todo-boogie-woogie`

**Ревьюируемый коммит:** `c8e48dd` — `feat(todo): add playable Aoi Todo`

**Корректная база сравнения:** `710b24e` (`main`)

**Диапазон:** `710b24e...c8e48dd`

**Статус ревью:** исходный код, ресурсы, конфигурация и тесты намеренно не изменялись.

## Краткое резюме

Ветка добавляет второго выбираемого персонажа, Todo, с серверной способностью Boogie Woogie, общими слотами способностей и cooldown, базовыми атрибутами, мостом vanilla-melee к Black Flash, клиентским VFX-рецептом и интеграцией в ClickGui. Общая граница client/server в целом соблюдена: клиент лишь отправляет типизированный запрос, а сервер выбирает цель, проверяет позиции, телепортирует сущности, запускает cooldown и публикует presentation-only cue.

`./gradlew build --no-daemon --rerun-tasks` прошёл успешно на Java 21 и выполнил все 22 зарегистрированные JavaExec-проверки. Отдельно подтверждён запуск dedicated server до `Done` и инициализация `JujutsuMod`.

Ветка пока **не готова к merge** по двум причинам:

1. Обязательный documentation audit падает, а GitHub Actions запускает его до Gradle build.
2. Реализованная VFX-регистрация не выполняет явно зафиксированное требование проекта для второго персонажа: единый агрегатор `JujutsuVfxRecipes.registerAll()` отсутствует.

Дополнительно есть подтверждённый разрыв между заявленной безопасностью swap и фактической проверкой коллизий с посторонними non-living entities, а тесты не покрывают реальную world/teleport интеграцию.

## Метод и уровни подтверждения

| Уровень | Значение |
|---|---|
| Подтверждено кодом | Симптом следует из конкретного текущего кода или отсутствия требуемого кода. |
| Подтверждено diff | Изменение и его область влияния видны в сравнении с базой. |
| Подтверждено проверкой | Получено фактическим запуском build, audit или smoke. |
| Риск / нужна ручная проверка | Код оставляет реалистичный риск, но конкретный игровой симптом не воспроизводился в доступной среде. |

## Ветка, база и релевантные аналоги

### Реальные refs и история

`git ls-remote --heads` вернул точный ref `refs/heads/feat/todo-boogie-woogie`; вариант с иным написанием не использовался. Локальная чистая копия была переключена именно на эту ветку.

- `feat/todo-boogie-woogie` указывает на `c8e48dd`.
- Ветка содержит ровно один коммит поверх `main`.
- `git merge-base main feat/todo-boogie-woogie` и `git merge-base --fork-point main feat/todo-boogie-woogie` оба вернули `710b24e`.
- Базовый diff содержит 42 пути и `+1316/-244` строк.

### Предыдущие ветки и персонажи

Единственный реально реализованный и ближайший персонаж-аналог — **Nobara** в текущем `main`: persistent character selection, `ProjectJjkNobaraActions`, Nobara runtime, `TargetResolver`, typed payloads и VFX Core.

Также проверены доступные исторические refs:

| Ref | Итог сравнения | Значение для ревью |
|---|---|---|
| `feat/nobara-resonance-and-ui` | Его tip `2cf011c` является предком `main`. | Исторический источник паттернов Nobara, но не отдельная параллельная база. |
| `codex/nobara-cinematic-slice` | Его tip `fbf1de2` является предком `main`. | Исторический источник VFX-паттернов; актуальный код уже присутствует в базе. |
| `docs/sandbox-build-recipe` | Его tip `1cb56df` является предком `main`. | Подтверждает актуальные команды проверки в документации. |

Поэтому корректное архитектурное сравнение сделано с Nobara и VFX Core в `710b24e`, а не с произвольно выбранной старой веткой.

## Карта реализации Boogie Woogie

### Выбор персонажа, состояние и атрибуты

1. `JujutsuCharacter.TODO` добавлен в `src/main/java/jujutsu/mod/character/JujutsuCharacter.java`.
2. `CharacterSelectionManager.select` сохраняет Todo через существующий attachment `JujutsuAttachments.CHARACTER_STATE`, синхронизирует выбор через `CharacterSelectionSyncPayload` и вызывает `CharacterCombatModifiers.applyForSelection`.
3. `CharacterCombatModifiers` добавляет transient modifiers для `ATTACK_DAMAGE` и `ATTACK_SPEED`, переустанавливает их на join, respawn и смене мира; `CombatStagger` использует существующий путь `adjustedStaggerTicks`.
4. `CharacterPlayerStateTest` подтверждает codec round-trip Todo и сохранение истории starter claim Nobara.

### Ввод, сеть и cooldown

1. Клиентский `JujutsuKeybinds` использует уже существующую primary key `R` для Todo.
2. `CharacterAbilityPayload` несёт только numeric slot `PRIMARY`; клиент не передаёт цель, позицию или результат способности.
3. `JujutsuNetworking` ставит C2S обработчик на server thread и вызывает `CharacterAbilityExecutor`.
4. `CharacterAbilityExecutor` проверяет selected character и серверный `CharacterAbilityCooldowns`, затем маршрутизирует только Todo в `TodoBoogieWoogieRuntime.tryCast`.
5. Cooldown создаётся только после полного успешного swap и подтверждается клиенту `AbilityCooldownPayload`; `ClientAbilityCooldowns` служит лишь подавлением лишнего штатного ввода.

### Цель, swap и cleanup

`TodoBoogieWoogieRuntime.tryCast`:

1. Отклоняет не-primary slot, spectator, мёртвого Todo, passenger/vehicle и staggered Todo.
2. Использует общий `TargetResolver` с Todo-specific predicate.
3. Повторно получает entity по id, проверяет тип, линию видимости, дистанцию, состояние транспорта, world и конечность координат.
4. Снимает snapshots позиции, поворота, head yaw и velocity обоих участников.
5. Ищет две safe destinations, создаёт `TodoSwapPlan` только при наличии обеих.
6. Повторно проверяет состояние сущностей до commit.
7. Телепортирует Todo и target через mapped server teleport API; при ложном результате одной из операций пытается восстановить оба snapshot.
8. Восстанавливает rotation, velocity и fall distance, затем запускает cooldown, звуки и VFX.

### Black Flash

`TodoBlackFlashRuntime` использует `ServerLivingEntityEvents.AFTER_DAMAGE` для успешных, неблокированных direct vanilla melee hits Todo. Он повторно использует `ForcedBlackFlash`, `BlackFlashFocus`, `CombatStagger`, `JujutsuDamageSources.blackFlash` и существующий `NobaraVfxIds.BLACK_FLASH`. Защита `APPLYING_BONUS` предотвращает рекурсию после bonus damage.

## Использование существующих core-систем

| Core-механизм | Использование Todo | Оценка |
|---|---|---|
| Persistent character state | `CharacterPlayerState`, attachment, selection sync | Корректно переиспользован. |
| Server authority | Клиент передаёт только ability id; цель и swap принадлежат серверу | Корректно. |
| Targeting | Общий `TargetResolver` с predicate | Корректно, но глобально меняет tie-break для всех его callers; требуется Nobara regression smoke. |
| Cooldown | Новый общий `CharacterAbilityCooldowns` вместо Todo-local map | Разумное обобщение второго персонажа. |
| Combat modifiers | Vanilla attributes и `CombatStagger.adjustedStaggerTicks` | Корректно интегрировано. |
| Damage source | Новый neutral `JujutsuDamageSources`, Nobara facade сохранён | Корректное переиспользование без дублирования registry lookup. |
| VFX Core | `VfxCue` → payload → `VfxDirector` → `TodoVfxRecipes` → shared channels | Основной контракт соблюдён. |
| VFX registration convention | Две прямые регистрации в `JujutsuModClient` | Нарушено требование агрегатора для второго персонажа, см. A2. |

### Подтверждённо корректные границы

- В `src/main` нет client imports, что подтверждено успешным `ProjectSanityTest` в полном build.
- Нет Todo-specific client packet receiver, render callback или lifecycle manager: `TodoVfxRecipes` регистрирует один recipe в существующем `VfxDirector`.
- VFX cue не используется как источник игровой истины.
- Нет новых моделей, GeckoLib animation JSON, texture, particle JSON или sound resource для Todo. Это соответствует явно отложенному scope: Todo пока использует vanilla player model, no-op animation hook и vanilla sounds.

## Найденные проблемы, ранжированные по серьёзности

### A1 — High: documentation audit и CI гарантированно падают

**Подтверждение:** подтверждено проверкой, кодом и CI workflow.

**Симптом:** `python3 tools/audit_docs.py` завершился с ошибкой:

- ожидается `Main Java files | 81`, но в MOC оставлено `68`;
- ожидается `Client Java files | 147`, но оставлено `144`;
- ожидается `Test Java files | 22`, но оставлено `19`;
- ожидается `Verification programs | 22`, но оставлено `19`.

`.github/workflows/build.yml:22-23` запускает этот audit до Gradle build, поэтому push или PR не пройдут текущий workflow несмотря на зелёный локальный `build`.

**Файлы и символы:**

- `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md:33-41`;
- `.github/workflows/build.yml:22-23`;
- `tools/audit_docs.py:63-123`;
- `build.gradle:57-85`, `build.gradle:267-289`;
- `README.md:7`, `SESSION.md:20`, `docs/BUILDING_IN_SANDBOX.md:14`, `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Networking.md:5-18`, `03-systems/Character-selection.md:5-16`, `02-architecture/Entrypoints-and-lifecycle.md:5-18`, `04-client-vfx/VFX-core.md:5-15`.

**Сравнение с проектом:** AGENTS.md требует обновлять актуальные Codex notes при каждом значимом character/VFX/networking изменении. Ветка добавляет второго персонажа, две payload types, три custom verification tasks и Todo VFX, но не синхронизирует current source-of-truth документы.

**Направление исправления:** привести существующие current docs и code-derived metrics к фактическому состоянию ветки, обновить карту networking/selection/entrypoints/VFX и список текущих персонажей. Не создавать отдельную архивную инфраструктуру.

### A2 — Medium: отсутствует обязательный агрегатор регистрации VFX-рецептов второго персонажа

**Подтверждение:** подтверждено кодом и diff.

**Симптом/риск:** AGENTS.md и `06-maintenance/How-to-add-next-character.md` однозначно требуют, когда появляется второй персонаж, зарегистрировать recipes через явный `JujutsuVfxRecipes.registerAll()`. Ветка оставляет два прямых вызова:

```java
NobaraVfxRecipes.register();
TodoVfxRecipes.register();
```

Ни класса `JujutsuVfxRecipes`, ни `registerAll()` в репозитории нет. Это не ломает текущий запуск, но оставляет ровно ту рассредоточенную точку регистрации, которую проектная конвенция должна была устранить на втором персонаже.

**Файлы и символы:**

- `AGENTS.md:123-129`;
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md:12-17`;
- `src/client/java/jujutsu/mod/client/JujutsuModClient.java:25-28`;
- `src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java:26-52`;
- `src/client/java/jujutsu/mod/client/vfx/todo/TodoVfxRecipes.java:21-23`.

**Сравнение с существующим механизмом:** `VfxDirector` уже остаётся единственным owner lifecycle и registry. Нужна лишь его предусмотренная агрегирующая точка, а не новый receiver или отдельный director.

**Направление исправления:** добавить предусмотренный агрегирующий registration entrypoint и вызвать его из client initializer; сами recipes и VFX Core каналы не менять.

### A3 — Medium: safe-swap проверяет collision только с LivingEntity, хотя design требует non-participant entity collision

**Подтверждение:** подтверждено кодом и сравнением с approved design; конкретный игровой сбой не воспроизведён.

**Симптом/риск:** `TodoBoogieWoogieRuntime.isSafeDestination` запрашивает entities в candidate AABB, но predicate пропускает любой `Entity`, который не является `LivingEntity`. Поэтому boat, minecart и другие non-living collidable entities не отклоняют destination. В design явно заявлена проверка collision с любым non-participant entity.

Это не доказывает crash: итоговое поведение конкретного транспорта зависит от vanilla teleport/collision semantics. Но контракт безопасной позиции реализован не полностью, и swap может завершиться в позиции, которую design объявляет недопустимой.

**Файлы и символы:**

- `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java:123-138`, особенно predicate на строках 135-137;
- `docs/TODO_BOOGIE_WOOGIE.md:69-79`, пункт о complete destination bounding box и non-participant entity collision;
- `src/main/java/jujutsu/mod/character/todo/TodoTargetSafety.java:7-9`.

**Сравнение с существующим механизмом:** ветка уже централизует preflight в `isSafeDestination`; пропуск находится в этом существующем guard, а не требует отдельной safety subsystem.

**Направление исправления:** расширить текущую preflight-проверку в `isSafeDestination` на релевантные collidable non-participant entities и добавить world-level тесты для boat/minecart и fallback positions. Отдельную систему телепорта не создавать.

### A4 — Medium: заявленная world-integrated валидация отсутствует; Todo tests проверяют форму кода, а не способность

**Подтверждение:** подтверждено кодом, тестами и отсутствием GameTest.

**Симптом/риск:** approved design требует покрыть target rejection, cooldown replay prevention, safe positions, atomic rollback, velocity/rotation preservation, persistence/selection и VFX registration. Реальные Todo tests покрывают только:

- литеральные profile constants (`TodoProfileTest`);
- null/non-null аргументы `TodoSwapPlan.preflight` (`TodoSwapPlanTest`);
- boolean truth table `TodoTargetSafety` (`TodoTargetSafetyTest`);
- source-text presence в `ProjectSanityTest`.

Ни один test не вызывает `TodoBoogieWoogieRuntime.tryCast`, не создаёт `ServerLevel`, не проверяет actual teleport/rollback, velocity, rotation, occupied destination, death/reconnect cooldown semantics или packet path. `glob src/test/java/**/GameTest` не нашёл GameTest-классов.

**Файлы и символы:**

- `src/test/java/jujutsu/mod/character/todo/TodoProfileTest.java`;
- `src/test/java/jujutsu/mod/character/todo/TodoSwapPlanTest.java`;
- `src/test/java/jujutsu/mod/character/todo/TodoTargetSafetyTest.java`;
- `src/test/java/jujutsu/mod/ProjectSanityTest.java:367-395`;
- `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java:43-187`;
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md:16-17`.

**Сравнение с существующим механизмом:** проект уже использует dedicated-server lifecycle and deterministic verification programs, а maintenance guidance прямо требует GameTest/dedicated-server coverage для world integration.

**Направление исправления:** добавить узкие server/world tests вокруг фактического runtime и оставить существующие pure tests как быстрые checks. Минимальный набор: valid player↔mob swap, player↔player swap, blocked target position, transport/non-living occupancy, second teleport failure/rollback, velocity/rotation/fall reset, cooldown on success and no cooldown on failure.

### A5 — Low: configuration contract TodoProfile частично обходится, а VFX palette дублируется

**Подтверждение:** подтверждено кодом.

**Симптом/риск:** design утверждает, что все базовые значения Todo находятся в `TodoProfile`, но часть safety tuning фактически зашита в runtime:

- `TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS` объявлен, проверяется в `TodoProfileTest`, но нигде не используется production code.
- `TodoProfile.WORLD_BORDER_MARGIN` объявлен, но нигде не используется.
- `TodoBoogieWoogieRuntime.HORIZONTAL_OFFSETS` содержит непосредственно `0.5`, `0.7` и `1.0`; эти значения определяют фактический fallback geometry.
- Цвета `0xB26CFF` и `0x71D7FF` в `TodoVfxRecipes` соответственно повторяют RGB triples `TODO_VIOLET_*` и `TODO_EDGE_*` из `VfxPalette`.

Это не меняет текущую механику и не приводит к compile failure, но делает будущую настройку расходящейся: тест защищает одну константу, runtime использует другую literal table.

**Файлы и символы:**

- `src/main/java/jujutsu/mod/character/todo/TodoProfile.java:12-24`;
- `src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java:31-39`, `111-119`;
- `src/test/java/jujutsu/mod/character/todo/TodoProfileTest.java:11-14`;
- `src/client/java/jujutsu/mod/client/vfx/VfxPalette.java:36-44`;
- `src/client/java/jujutsu/mod/client/vfx/todo/TodoVfxRecipes.java:16-17`.

**Сравнение с существующим механизмом:** central profile и `VfxPalette` уже существуют именно как owners tuning and color language. Новая инфраструктура не нужна.

**Направление исправления:** сделать один owner фактических fallback parameters и один owner Todo VFX colors; либо удалить неиспользуемые profile constants, либо использовать их в runtime после анализа желаемой fallback shape.

### A6 — Low: новые player-visible строки Todo в CharacterRosterPanel остаются hard-coded

**Подтверждение:** подтверждено кодом и ресурсами.

**Симптом/риск:** `CharacterRosterPanel` добавляет `Aoi Todo`, `Boogie Woogie` и `Heavy Melee` как Java strings, а `en_us.json` и `ru_ru.json` не содержат ключей для них. AGENTS.md требует локализовать user-visible text. Проблема уже есть у части старых ClickGui labels, но ветка расширяет её новым персонажем.

**Файлы и символы:**

- `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/character/CharacterRosterPanel.java:28-35`, `180-196`;
- `src/main/resources/assets/jujutsumod/lang/en_us.json`;
- `src/main/resources/assets/jujutsumod/lang/ru_ru.json`;
- `AGENTS.md:201-206`.

**Сравнение с существующим механизмом:** обе locale files уже используются для message/key text. Это не требует новой UI layer.

**Направление исправления:** вынести новые Todo-specific display labels в существующие language resources вместе с остальными текущими panel labels, не меняя selection или render architecture.

### A7 — Low: `git diff --check` не проходит из-за trailing whitespace в design doc

**Подтверждение:** подтверждено проверкой.

**Симптом:** `git diff --check 710b24e...c8e48dd` сообщает trailing whitespace в:

- `docs/TODO_BOOGIE_WOOGIE.md:3`;
- `docs/TODO_BOOGIE_WOOGIE.md:4`;
- `docs/TODO_BOOGIE_WOOGIE.md:64`.

**Сравнение с существующим механизмом:** `05-reference/Test-and-build-commands.md` включает `git diff --check` в full verification.

**Направление исправления:** убрать только обнаруженные trailing spaces и повторно запустить diff check.

## Изменения, требующие regression smoke, но не объявленные дефектами

### Общий TargetResolver теперь выбирает по crosshair proximity, а затем по depth

В `TargetResolver.resolveForTests` comparator изменён с nearest depth на closest perpendicular distance, затем depth. Это оправдано Todo design, который требует crosshair priority, и покрыто `TargetResolverTest.assertCrosshairPriorityBeatsNearerOffAxisCandidate`.

Однако `TargetResolver` также вызывают `NobaraHammerCombatRuntime`, `ProjectJjkNobaraRuntime` и `ProjectJjkRitualRuntime`. Изменение является cross-character semantic change, а не Todo-local policy. Отсутствует доказательство регрессии, поэтому это не включено в список дефектов; перед merge нужен ручной Nobara smoke для hammer targeting, nail launch и directed Hairpin.

### Cooldown при death, reconnect и смене selection

`CharacterAbilityCooldowns` чистит state при disconnect и server stop, но не чистит его на death; ключом является UUID, поэтому cooldown переживает respawn, но сбрасывается disconnect. Это фактическое поведение кода, а не доказанный дефект: design не определяет policy для 60-tick cooldown в этих переходах. Его следует подтвердить как gameplay decision в manual test, чтобы нельзя было случайно изменить policy позднее.

### UI density

`ClickGui` передаёт roster panel ширину `300` (`BackgroundComponent.BG_WIDTH=400`, content width `300`). С тремя карточками `CharacterRosterPanel.cardBounds` оставляет примерно `89` logical pixels на card, а new labels не имеют clipping/wrapping. Статически это риск overlap/readability, но без графического smoke нельзя достоверно утверждать, что конкретный шрифт визуально выходит за границы.

## Hardcode и configuration audit

| Значение/область | Где находится | Оценка |
|---|---|---|
| Множители damage, attack speed, stagger; range и cooldown | `TodoProfile`, используются `CharacterCombatModifiers`, `TodoBoogieWoogieRuntime` | Корректная локальная configuration. |
| SAFE_POSITION_UPWARD_BLOCKS | `TodoProfile`, используется в search loop | Корректно централизован. |
| SAFE_POSITION_HORIZONTAL_RADIUS и WORLD_BORDER_MARGIN | `TodoProfile`, не используются | A5: stale configuration. |
| Fallback offsets `0.5/0.7/1.0` | `TodoBoogieWoogieRuntime.HORIZONTAL_OFFSETS` | A5: фактическая configuration скрыта в runtime. |
| VFX particle counts, radii, screen/camera duration | `TodoVfxRecipes`, VFX channels | Допустимые recipe-local visual tuning values; lifecycle принадлежит director channels. |
| Violet/cyan Todo RGB | И `VfxPalette`, и `TodoVfxRecipes` | A5: подтверждённое дублирование. |
| `64.0` VFX broadcast radius | `emitSwapFeedback` | Соответствует существующему radius-filtered VFX approach; не выделен как отдельный defect. |

## Полный разбор VFX и SFX

### Boogie Woogie после успешного swap

| Эффект | Запуск и условие | Сторона и sync | Cleanup / ресурсы / риск |
|---|---|---|---|
| Server clap-like sound | `TodoBoogieWoogieRuntime.emitSwapFeedback`, только после двух успешных teleports | Server `level.playSound` в `todoOrigin`; vanilla `NOTE_BLOCK_HAT`, `SoundSource.PLAYERS` | Одноразовый vanilla sound, cleanup не нужен. Нет нового sound asset. |
| Server teleport sound | Тот же method, только after success | Server `level.playSound` в `targetOrigin`; vanilla `ENDERMAN_TELEPORT` | Одноразовый vanilla sound, cleanup не нужен. Нет нового sound asset. |
| VFX cue | `emitSwapFeedback`, только after success | Server broadcasts `VfxCue(TodoVfxIds.BOOGIE_WOOGIE, todoOrigin, NO_ANCHOR, targetOrigin - todoOrigin, ...)` в радиусе 64 | Presentation-only; cue receiver and active instance owned by VFX Core. При обычном cooldown spam отсутствует; cue может быть не получен клиентом вне radius, что является существующим transient VFX limitation. |
| Violet flash | `TodoVfxRecipes.boogieWoogie` opening beat, у обоих pre-swap origins | Client after `VfxCuePayload` → `VfxDirector` | 12 Dust particles `#B26CFF` в каждой точке. Частицы не держат отдельного state; director instance живёт 8 ticks. |
| Cyan flash и ring | Тот же recipe и условие | Client | 8 Dust particles и ring из 10 `#71D7FF` particles в каждой точке. Нет новых particle textures/JSON. |
| World ribbon и pulses | `VfxWorldChannel.renderBoogieWoogie`, style `BOOGIE_WOOGIE` | Client shared world channel | Три ribbon layers между immutable origins и два pulse. `ImpactFlash` длится 8 ticks, channel cap 48, `VfxDirector` очищает его при disconnect/level change. |
| Camera/FOV feedback | `context.camera().triggerLaunch(1, proximity, initialAgeTicks)` | Local client only | Existing camera channel: bounded lists по 64 impulses/FOV impulses, life 110-330 ms. Intensity зависит от distance до `cue.origin` (Todo pre-swap origin). |
| Screen flash | `context.hud().triggerFlash(80, ...)` | Local client only | Existing HUD channel, 80 ms, max alpha bounded to 180; clear на disconnect/level reset. |
| Animation hook | `TodoAnimationHooks.triggerBoogieWoogie(cue)` | Client | **Фактически no-op**. `ability.boogie_woogie` не связан с model/animation asset; это соответствует документированному отложенному scope, не missing resource. |

### Todo melee Black Flash

| Эффект | Запуск и условие | Сторона и sync | Cleanup / ресурсы / риск |
|---|---|---|---|
| Bonus damage, stagger, knockback, focus | `TodoBlackFlashRuntime.afterDamage` после неблокированного successful direct vanilla melee Todo и successful roll/forced debug | Server-only gameplay. `APPLYING_BONUS` защищает от рекурсивного AFTER_DAMAGE callback | Set чистится вокруг bonus call в `finally`, на disconnect и server stop. |
| Existing Black Flash VFX cue | После подтверждённого proc | Server broadcasts existing `NobaraVfxIds.BLACK_FLASH` cue | Reuses current VFX Core and particle registrations rather than Todo-specific effects. |
| Black Flash world/particle/camera/HUD/post-process/SFX | `NobaraVfxRecipes.blackFlash` как существующий recipe | Client VFX Core | Используются existing BF particles, existing ProjectJJK sounds, 48-tick VFX instance и bounded shared channels. Cue Todo не имеет anchor, поэтому Nobara Geo animation hook не запускается. |

### Отсутствующие эффекты, подтверждённо не реализованные

- Нет Todo model, Geo JSON, animation JSON или player renderer.
- Нет отдельного Todo sound entry в `sounds.json` и нет нового OGG.
- Нет VFX/SFX при отказе цели, cooldown refusal, отмене или unsafe preflight.
- Нет external-target swap, custom HUD, resource bar, rhythm/vibraslap mechanics или persistent Todo visual state.

Это соответствует `docs/TODO_BOOGIE_WOOGIE.md` и не считается missing implementation в рамках указанного slice.

## Проверки и результаты

| Проверка | Команда/метод | Результат |
|---|---|---|
| Проверка refs и базы | `git ls-remote --heads`, `git merge-base`, `git log`, `git diff main...feat/todo-boogie-woogie` | Успешно: target `feat/todo-boogie-woogie`, base `710b24e`, один commit поверх main. |
| Full build и все custom checks | `bash ./gradlew build --no-daemon --rerun-tasks` с Temurin 21.0.11 | Успешно: 33 actionable tasks; compiled main/client; `check` выполнил 22 JavaExec verification programs. |
| Lang JSON syntax | Python JSON parse для `en_us.json` и `ru_ru.json` | Успешно. Нестандартное расположение запятых в `ru_ru.json` остаётся syntactically valid. |
| Documentation audit | `python3 tools/audit_docs.py` | Неуспешно: четыре stale MOC metrics, см. A1. |
| Diff hygiene исходной ветки | `git diff --check 710b24e...c8e48dd` | Неуспешно: три trailing-whitespace diagnostics, см. A7. |
| Dedicated server smoke | Time-bounded `./gradlew runServer --no-daemon --args='nogui'` с временным `run/eula.txt` | Сервер достиг `Done`; `JujutsuMod initialized`; timeout намеренно остановил живой сервер после запуска. Временный `run/` и `logs/` удалены. Это не тестировало player interaction. |
| Client smoke | Проверены `DISPLAY`, `WAYLAND_DISPLAY`, наличие `xvfb-run` | Не выполнен: display отсутствует, `xvfb-run` недоступен. |

## Непроверенные сценарии и внешние ограничения

1. Реальный player↔mob и player↔player swap в клиенте/сервере.
2. Blocked destination, non-living occupancy, large mobs, fluid/partial-block placement и rollback при второй teleport failure.
3. Сохранение velocity, yaw/pitch/head yaw и fall handling в живом игровом мире.
4. Поведение cooldown после death, reconnect, смены dimension и смены character selection.
5. Nobara targeting regression после global `TargetResolver` comparator change.
6. ClickGui card readability/hitboxes в реальном renderer при трёх cards.
7. VFX/SFX timing, camera/HUD response и radius-filtered reception на нескольких clients.
8. Полный client runtime, GeckoLib client mixins и render path: headless environment не имеет display.

## Итоговая оценка готовности

**Compile/build readiness:** хорошая. Common и client код собираются, зарегистрированные custom checks зелёные, dedicated server запускает мод без fatal startup error.

**Merge/CI readiness:** недостаточная. Documentation audit, обязательный в текущем CI workflow, уже падает. До исправления A1 ветка не должна считаться готовой к merge.

**Architecture readiness:** частично хорошая. Server authority, typed payloads, shared cooldown abstraction, character state, damage source reuse и VFX Core в основном следуют существующим механизмам. A2 нужно устранить, чтобы выполнить зафиксированный second-character VFX convention.

**Gameplay safety readiness:** требует доработки и world-level validation. A3 и A4 не доказывают crash, но оставляют несоответствие declared safe-swap contract и непроверенные критические teleport edge cases.

**Рекомендуемый порядок после этого ревью:** A1 → A2 → A3/A4 → docs/diff hygiene и ручной client/multiplayer smoke. Исходный код в рамках данного ревью намеренно не менялся.
