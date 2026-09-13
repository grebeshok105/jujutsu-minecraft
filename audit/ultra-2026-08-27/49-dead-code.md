## Dead Code — 49-dead-code

### 1) Что проверено (файлы/символы)

Срез 49 — аудит мёртвого кода, забытых TODO и реликтов трёх удалённых поверхностей: `thrown-mark` fallback (Todo aimed swap), `canonicalSlot` fold, агрегат `JujutsuVfxRecipes`. Проверка велась через `grep`/`read` по `src/main`, `src/client`, `src/test`, `src/gametest`, `docs/` и сверкой с `AGENTS.md` / `docs/KNOWN_ISSUES.md` / `ProjectSanityTest` / `SourceBoundaryTripwireTest`.

**Удалённые символы (проверка отсутствия реликтов):**
- `JujutsuVfxRecipes` — grep по `src/` — 0 попаданий в production; единственное упоминание в `src/test/java/jujutsu/mod/ProjectSanityTest.java:457` как `assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/JujutsuVfxRecipes.java"))`. Файл физически отсутствует в `src/client/java/jujutsu/mod/client/vfx/` (там `VfxDirector`, `megumi/`, `nobara/`, `todo/`, `world/`). AGENTS.md:48 `Transient combat VFX: VFX Core only (...) the aggregate JujutsuVfxRecipes is deleted` — подтверждено.
- `canonicalSlot` — grep по `src/main` + `src/client` — 0 попаданий. Единственные упоминания в тестах-стражах: `src/test/java/jujutsu/mod/character/todo/TodoPairSwapTest.java:121-133` и `src/test/java/jujutsu/mod/ProjectSanityTest.java:522-523` (`assert !contains("canonicalSlot")`). `CharacterDefinition.java`, `TodoDefinition.java`, `CharacterAbilityExecutor.java` — чисто.
- `thrown-mark` / `TodoSwapMarkerItem` / `TodoSwapMarkerEntity` / `TodoMarkerSwapRuntime` — grep `TodoSwapMarker|TodoMarker|thrownMark` по `src/main` + `src/client` — 0 попаданий. В `src/test/java/jujutsu/mod/ProjectSanityTest.java:495-497` три пути перечислены как удалённые и проверяются на отсутствие. Todo stone (`TodoStoneEntity`, `TodoStoneRuntime`, `JujutsuEntities.TODO_STONE`) полностью заменил маркеры, реликтов нет.
- `VfxTimeChannel` / `timeScale` — grep `VfxTimeChannel` по `src/` — 0 попаданий. В `docs/KNOWN_ISSUES.md:21-23` зафиксировано удаление как dead API. Проверено: `src/client/java/jujutsu/mod/client/vfx/` не содержит файла, `VfxDirector.java` не экспортирует `timeScale()`.
- `otherSwapParticipant` параметр `findSafeDestination` — удалён, в коде `TodoBoogieWoogieRuntime.java` метод принимает только `Strictness` без дефолтной перегрузки — проверено через `TodoPairSwapTest.java:92-104`.

**Мёртвый код / TODO / unreachable:**
- `grep -rnE "//\s*TODO|/\*\s*TODO|FIXME|XXX|HACK"` по `src/main`, `src/client`, `src/gametest` — 0 production TODO. Грубый `grep "TODO"` даёт только `JujutsuCharacter.TODO` enum (loader). Корректная проверка с regex подтверждает: забытых TODO нет.
- `grep -rn "@Deprecated|@SuppressWarnings.*unused|isInsideVesselPackage"` — `SuppressWarnings("unchecked")` только в `src/test/java/jujutsu/mod/character/CharacterAbilityCooldownsClearAllTest.java:55` (тест), в production — 0.
- `hasClaimedStarter` / `claimedStarterCharacters` / `claimStarter` — `src/main/java/jujutsu/mod/character/CharacterPlayerState.java:29,37` + `CharacterSelectionManager.java:24`.
- `CharacterDefinition.selectCurseLink` — `src/main/java/jujutsu/mod/character/CharacterDefinition.java:83` (default), override только `NobaraDefinition.java:51`.
- `NobaraAbilityRouter` исчерпывающие `switch` — `src/main/java/jujutsu/mod/character/nobara/NobaraAbilityRouter.java:41-67`.
- `antidaunleak.api.UserProfile` — `src/client/java/antidaunleak/api/UserProfile.java:1-24`.
- Wildcard imports `import java.awt.*` — 18 файлов в `src/client/java/jujutsu/mod/client/rich/**`.
- Неиспользуемые wire id `CharacterAbility.USE_CONTEXT` — `src/main/java/jujutsu/mod/character/CharacterAbility.java:34` + все роутеры (`NobaraAbilityRouter:61`, `TodoAbilityRouter:32-34`, `MegumiAbilityRouter:41`).
- Stale reference check `grep -rn "in.*production"` — охвачены файлы из `docs/KNOWN_ISSUES.md` E12, E14, R1.

**Инструменты/методы:**
- `codegraph` недоступен на машине — анализ через `grep` + `read` + `glob` + чтение тестов-стражей (`ProjectSanityTest`, `SourceBoundaryTripwireTest`, `TodoPairSwapTest`, `VesselBoundaryTest`).
- Проверена трансляция ключей `message.jujutsumod.nobara.trap.*` и `self_resonance.no_link` в `src/main/resources/assets/jujutsumod/lang/en_us.json:18-22,89` / `ru_ru.json`.

---

### 2) Находки

| Severity | Файл:строка | Описание | Рекомендация |
|---|---|---|---|
| **major** | `src/client/java/antidaunleak/api/UserProfile.java:1-24` | Полностью мёртвый файл. `grep -rn UserProfile src/` возвращает только сам файл. Stub Rich-панели аватара (`username`/`uid` → `Minecraft.getInstance().player`), никем не вызывается после удаления Rich Modern UI. Является единственным файлом в пакете `antidaunleak`, расширяет поверхность нерешённого provenance (KNOWN_ISSUES R1). Держит зависимость от `net.minecraft.client.Minecraft` в `src/client` без причины. | Удалить файл и пакет `antidaunleak/` целиком после product-решения по R1. До удаления — зафиксировать в `docs/PROVENANCE.md` как намеренный stub, либо добавить `assert !exists` в `ProjectSanityTest` аналогично `JujutsuVfxRecipes`. Стоимость удаления — ноль функциональности, минус один namespace из scope лицензионного аудита. Не удалять без одобрения как silent cleanup (требование KNOWN_ISSUES R1). |
| **major** | `src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java:65,70,74,89` + `src/main/java/jujutsu/mod/character/nobara/projectjjk/SelfResonanceRuntime.java:49` → `src/main/java/jujutsu/mod/character/nobara/NobaraAbilityRouter.java:69-71` | 5 переводов — `trap.no_ground`, `trap.unsupported`, `trap.no_nails`, `trap.failed`, `self_resonance.no_link` — пишутся в action bar и сразу перезаписываются универсальным fallback `message.jujutsumod.nobara.action.no_target` из роутера. `NailTrapRuntime.tryPlace()` и `SelfResonanceRuntime.tryCast()` возвращают `false`, роутер не различает `HANDLED_FAILURE` vs `UNHANDLED_FAILURE` и всегда пишет `no_target` при `notify`. В итоге 5 ключей из `en_us.json:18-22,89` никогда не видны игроку — мёртвые сообщения при живом коде. Зафиксировано как KNOWN_ISSUES E10, но остаётся мёртвым UX. | Починить в рамках E10 одним из двух честных путей: (a) три-state из рантаймов (`SUCCESS`/`HANDLED_FAILURE`/`UNHANDLED_FAILURE`) и не писать fallback если уже `HANDLED_FAILURE`, либо (b) убрать fallback для слотов `SECONDARY_SNEAK`/`PRIMARY_SNEAK`. Любой путь требует правки `NailTrapRuntime` + `SelfResonanceRuntime` + `NobaraAbilityRouter`. До фикса — не добавлять новые `fail()`-ключи без теста видимости. Приоритет — средний, но единственный major влияющий на игрока. |
| **minor** | `src/main/java/jujutsu/mod/character/CharacterPlayerState.java:29-45` (`hasClaimedStarter`, `claimStarter`, поле `claimedStarterCharacters`, `CODEC` с `claimed_starter_characters`) | Персистентное состояние пишется на каждом `CharacterSelectionManager.select()` (`CharacterSelectionManager.java:24` `withSelectedCharacter().claimStarter()`), но никогда не читается в production — `grep -rn hasClaimedStarter src/main` даёт только `CharacterPlayerState.java` (определение + `internal if`). Поле сериализуется в `CODEC`, растёт с каждым новым сосудом, копируется на смерть (`copyOnDeath` via attachment). Чистый мёртвый код / мёртвое поле. Отмечено в KNOWN_ISSUES E12 как `has no production callers at all`. | Либо удалить поле+методы+codec-key целиком (миграция — старый `claimed_starter_characters` игнорируется при чтении, `CODEC.optionalFieldOf` уже дефолтит в `Set.of()`), либо дать ему задачу (например, one-time грант стартового набора вместо идиомпотентного `fill only missing`). Решение — product, не техдолг. До решения — добавить `ProjectSanityTest`-tripwire что поле не читается, чтобы никто не начал на него полагаться. |
| **minor** | `src/main/java/jujutsu/mod/character/nobara/NobaraAbilityRouter.java:41-43` (early return) + `62-67` (switch arms `SECONDARY_SNEAK_HOLD`, `SECONDARY_SNEAK_RELEASE`, `TERTIARY`, `TERTIARY_SNEAK`) | Документированные недостижимые ветки. Early return `if (ability == SECONDARY_SNEAK_HOLD \|\| SECONDARY_SNEAK_RELEASE \|\| TERTIARY \|\| TERTIARY_SNEAK) return UNHANDLED_FAILURE` делает 4 arms в `switch` (строки 62-67) недостижимыми в рантайме. Комментарий `Unreachable through the early return above; kept so the switch stays exhaustive` честен. Это намеренный dead branch ради compile-time гарантии exhaustive switch без `default`. Аналогично `NoneDefinition.java:21` — недостижим через executor, но оставлен. | Оставить как есть — паттерн корректен и задокументирован. Альтернатива (убрать early return и пустить ветки через switch) ухудшит UX — показывала бы `no_target` на hold-релизе. Для аудита dead-code помечать такие ветки аннотацией `// exhaustive-only` и не считать как долг. Рекомендация — не фиксить, только не копировать паттерн без комментария. |
| **minor** | `src/main/java/jujutsu/mod/character/CharacterDefinition.java:83-85` (`default boolean selectCurseLink(...) { return false; }`) | Shared extension point с единственным реализатором — только `NobaraDefinition.java:51` override. Для `TodoDefinition`, `MegumiDefinition`, `NoneDefinition` метод мёртвый (всегда `false`). Это тот же класс проблем что и удалённый `canonicalSlot` — единственный пользователь в shared интерфейсе, невидимый для structural правил (KNOWN_ISSUES "Limits of the build-time gate" — `A shared extension point with exactly one implementer`). Риск — кто-то добавит второй сосуд и скопирует `canonicalSlot`-паттерн. | Зафиксировать как известный лимит build-time gate (уже в KNOWN_ISSUES), не удалять. Если `curseLink` останется Nobara-only навсегда — перенести метод из интерфейса в `NobaraDefinition` и вызывать через `instanceof`/cast в `JujutsuNetworking.java:40` с явным кастом (честнее чем shared API с одним юзером). Если планируется второй пользователь — оставить, но добавить тест `assert implementers >=2 \|\| documented single-implementer`. Сейчас — без действий, только наблюдение. |
| **minor** | `src/client/java/jujutsu/mod/client/rich/**` — 18 файлов с `import java.awt.*` / `import java.util.*` (напр. `modules/module/setting/implement/ColorSetting.java:7`, `screens/clickgui/impl/background/render/CategoryRenderer.java:8`, `screens/clickgui/impl/module/handler/ModuleAnimationHandler.java:8`, `screens/clickgui/impl/settingsrender/ColorComponent.java:11`) | Wildcard импорты — не мёртвый код в строгом смысле, но мёртвый вес: `java.awt.*` тянет `Color`, `Graphics` и т.д. в namespace без явности, скрывает реальную зависимость от AWT (используется только `Color.HSBtoRGB` и `new Color()`). В `ModuleAnimationHandler` `java.util.*` скрывает `List`, `Map`. Не ломает сборку, но снижает читаемость и маскирует неиспользуемые импорты. Часть Rich Modern наследия (KNOWN_ISSUES E6/E14 контекст). | При следующем касании Rich-модулей заменить `import java.awt.*` на `import java.awt.Color` (фактически используется только `Color`), `import java.util.*` на точечные. Не делать отдельным PR только ради импортов — правило `api-lint` не настроен на `*` import в этом проекте. Low prio. |
| **minor** | `src/main/java/jujutsu/mod/character/CharacterAbility.java:34` (`USE_CONTEXT(5)`) + все роутеры (`TodoAbilityRouter.java:32-34`, `NobaraAbilityRouter.java:61`, `MegumiAbilityRouter.java:41`) | Wire id `USE_CONTEXT` зарезервирован (append-only per AGENTS.md:40-41) но никем не обслуживается — все роутеры `case USE_CONTEXT -> UNHANDLED_FAILURE`, `TodoAbilityRouter` комментирует `keeps its wire id and its client detection (...) but ... UNHANDLED_FAILURE`. Клиент `JujutsuKeybinds.java:142` всё ещё шлёт payload на paired right click, сервер всегда отказывает. Это намеренный мёртвый слот, а не случайный. Если переиспользовать id — wire-совместимость сломается. | Оставить. Это не долг, а контракт: wire ids append-only. Пометить в `CharacterAbility.java` javadoc что `USE_CONTEXT` зарезервирован и не удаляется. Не занимать id 5 под новый слот. Тест `ProjectSanityTest.java:508-509` уже страхует. |

---

### 3) Позитивные наблюдения

- **Три удаления зачищены образцово.** `JujutsuVfxRecipes`, `canonicalSlot`, `thrown-mark`/`TodoSwapMarker*` — ни одного реликта в `src/main`/`src/client`, ни одного `import`, ни одного комментария-реликта. Стражи в тестах (`ProjectSanityTest:457,495-497,522-523`, `TodoPairSwapTest:121-133`) инвертированы — ассертят отсутствие, а не присутствие, что предотвращает регресс `git revert`-ом.
- **Нет забытых TODO.** `// TODO` / `FIXME` / `XXX` / `HACK` в production — 0. Единственный `TODO` в кодовой базе — имя константы `JujutsuCharacter.TODO` — корректно исключён regex-проверкой. Команда не оставляет долг в комментариях.
- **Exhaustive switch без `default` — жив и единообразен.** `NobaraAbilityRouter`, `TodoAbilityRouter`, `MegumiAbilityRouter`, `CharacterAbilityExecutor`, `JujutsuCharacters`/`JujutsuCharacterClients` — все без `default`, новый `CharacterAbility` даёт точечную ошибку компиляции. Это именно та защита, которая сделала удаление `canonicalSlot` безопасным.
- **VFX Core — единственная поверхность.** `src/client/java/jujutsu/mod/client/vfx/` содержит только `VfxDirector` + каналы (`VfxSoundChannel`, `VfxWorldChannel` и т.д.) + per-vessel паки (`NobaraVfxRecipes`, `TodoVfxRecipes`, `MegumiVfxRecipes`). Ни одного per-effect receiver/mixin — контракт `VfxCue → director → recipes` соблюдён.
- **Реликт-поле изолировано.** `claimedStarterCharacters` хотя и мёртвое, уже помечено в KNOWN_ISSUES E12 и не расползается — единственный writer — `CharacterSelectionManager:24`, единственный reader — тесты. Нет скрытых `getClaimedStarterCharacters()` вызовов.
- **Просроченные поверхности не вернулись через тесты.** `TodoPairSwapTest.canonicalSlotSeamIsGoneEntirely` и `TodoPairSwapTest.characterDefinitionAndExecutorContainNoCanonicalSlot` явно проверяют три файла на отсутствие `canonicalSlot` — даже ручной `String.contains` не даст вернуть fold молча.
- **Provenance-долг локализован.** Весь `antidaunleak` — один файл, один пакет, zero fan-out. Удаление — атомарно и не требует миграции.

---

### 4) Вердикт — **PASS WITH CAVEATS** + приоритет фиксов

**Вердикт: PASS WITH CAVEATS.**

Срез `thrown-mark` / `canonicalSlot` / `JujutsuVfxRecipes` зачищен без реликтов — ни одной забытый импорт, ни одного мёртвого `if (false)`, ни одного `TODO` в коде. Exhaustive switch и append-only wire ids держатся. Это PASS-часть.

CAVEATS — два живых мёртвых куска, унаследованных до среза и не созданных им:

1. **Мёртвое персистентное поле** `claimedStarterCharacters` (E12) — безвредно сейчас, но каждый новый сосуд увеличивает сериализуемый state, который никто не читает. Риск — будущий автор начнёт на него полагаться.
2. **Мёртвый файл-остров** `antidaunleak.api.UserProfile` (R1) — единственный реликт Rich Modern provenance за пределами лицензионного scope. Риск — не тех, а релизный: расширяет поверхность аудита без пользы.

Оба — не регрессы этого среза, но именно dead-code аудит их и должен подсвечивать. Третий caveat — 5 мёртвых переводов `trap.*` / `self_resonance.no_link` — формально относится к Nobara UX (E10), но попадает под определение dead code (строки, которые компилируются и переводятся, но никогда не достигают экрана). Это единственный caveat влияющий на игрока.

FAIL был бы при: реликтовых `import JujutsuVfxRecipes`, живом `canonicalSlot`, или fallback `thrown-mark` в `TodoBoogieWoogieRuntime` — ничего из этого не найдено.

**Приоритет фиксов:**

| Приоритет | Что | Где | Усилия |
|---|---|---|---|
| **P1** | Удалить `antidaunleak/api/UserProfile.java` (или зафиксировать как намеренный stub с tripwire) | `src/client/java/antidaunleak/**` | 5 мин + product решение по R1 |
| **P1** | Починить E10 — различать `HANDLED_FAILURE` vs `UNHANDLED_FAILURE` чтобы `trap.*` / `self_resonance.no_link` стали видимы | `NailTrapRuntime.java`, `SelfResonanceRuntime.java`, `NobaraAbilityRouter.java:69` | 0.5 дня, меняет UX — требует smoke |
| **P2** | Удалить или дать задачу `claimedStarterCharacters` / `hasClaimedStarter` / `claimStarter` (E12) | `CharacterPlayerState.java:29-45`, `CharacterSelectionManager.java:24` | 0.5 дня + миграция `CODEC` |
| **P3** | Заменить `import java.awt.*` / `java.util.*` на точечные в Rich-модулях | `src/client/java/jujutsu/mod/client/rich/**` | 10 мин, делать при касании |
| **P3** | Задокументировать `selectCurseLink` как single-implementer (или вынести из shared интерфейса) | `CharacterDefinition.java:83` | 10 мин, решение архитектурное |

Без P1+P2 срез — чистый PASS по реликтам, но с висящими мёртвыми поверхностями. С их выполнением — полный PASS.

AUDIT_ID: 49-dead-code
