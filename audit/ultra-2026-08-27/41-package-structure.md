## Package Structure — 41-package-structure

### 1) Что проверено (файлы/символы)

**Scope среза:** `jujutsu.mod.character/*`, `vfx`, `network`, `registry`, `client/*`, отсутствие пустых пакетов, модульность, лимиты файлов 500/1000, barrel реэкспорт. Источник — `src/main/java`, `src/client/java`, `src/test/java`, `src/gametest/java`, `build.gradle` (sourceSets), `AGENTS.md` §Code Organization / Vessel Seam / VFX Core.

**Инструменты:** `find` по `src/*/java -type d`, `wc -l` по всем `*.java`, `grep import jujutsu.mod` для cross-vessel связности, `read` `JujutsuMod.java`, `JujutsuModClient.java`, `build.gradle` (§ loom.splitEnvironmentSourceSets), `codegraph`-эквивалент ручной проверки, поиск `package-info.java` / barrel-реэкспорта.

**Покрытые пакеты (main):**
- `jujutsu.mod` — 1 файл: `JujutsuMod.java:1` (56 строк)
- `jujutsu.mod.character` — 12 файлов (корень): `CharacterDefinition.java:1` (102), `CharacterAbility.java:1` (74), `JujutsuCharacters.java:1` (57), `CharacterSelectionManager.java:1` (79) и др., итого корень ~480 строк
- `jujutsu.mod.character.nobara` — 2 файла (`NobaraDefinition.java:1`, `NobaraAbilityRouter.java:1`), 135 строк суммарно
- `jujutsu.mod.character.nobara.projectjjk` — 35 файлов, 4627 строк (`ProjectJjkNailEntity.java:1` 899, `ProjectJjkStrawDollRuntime.java:1` 406, `ProjectJjkNobaraRuntime.java:1` 402, `ProjectJjkRitualRuntime.java:1` 400, `NobaraHammerCombatRuntime.java:1` 363, `NailTrapRuntime.java:1` 355 и т.д.)
- `jujutsu.mod.character.todo` — 21 файл, 2293 строки (`TodoPairSwapRuntime.java:1` 459, `TodoBoogieWoogieRuntime.java:1` 396, `TodoStoneRuntime.java:1` 266, `TodoStoneEntity.java:1` 217)
- `jujutsu.mod.character.megumi` — 17 файлов, 2742 строки (`MegumiSummonRuntime.java:1` 606, `MegumiShadowMoveRuntime.java:1` 438, `MegumiDivineDogEntity.java:1` 349)
- `jujutsu.mod.combat` — 9 файлов (`TargetResolver.java:1` 185, `SafeBodyPlacement.java:1` 122, `BlackFlashStrike.java:1` 83)
- `jujutsu.mod.network` — 9 файлов, 357 строк (`JujutsuNetworking.java:1` 99, `CurseLinkOptionsPayload.java:1` 60, `VfxCuePayload.java:1` 44)
- `jujutsu.mod.registry` — 7 файлов, 379 строк (`JujutsuEffects.java:1` 82, `JujutsuSounds.java:1` 73, `JujutsuItems.java:1` 69)
- `jujutsu.mod.vfx` — 8 файлов, 379 строк (`VfxCues.java:1` 117, `TodoVfxIds.java:1` 69, `NobaraVfxIds.java:1` 57)
- `jujutsu.mod.curse` — 3 файла, `jujutsu.mod.command` — 1 файл (`JujutsuCommands.java:1` 191), `jujutsu.mod.mixin` — 1 файл

**Покрытые пакеты (client — `src/client/java`):**
- `jujutsu.mod.client` — 1 файл `JujutsuModClient.java:1` (entry point, `ClientModInitializer`)
- `jujutsu.mod.client.character` — 9 файлов (включая `character/megumi` 4, `character/nobara` 6, `character/todo` 3)
- `jujutsu.mod.client.vfx` — 14 файлов (+ `vfx/nobara` 1, `vfx/megumi` 1, `vfx/todo` 2, `vfx/world` 5)
- `jujutsu.mod.client.render` — 8 файлов (+ `render/megumi` 5, `render/nobara` 3, `render/todo` 4, `render/nobara/doll` 2)
- `jujutsu.mod.client.rich` — ~110 файлов, глубина до 7 уровней (`rich/screens/clickgui/impl/settingsrender` 8 файлов, `rich/util/animations` 14 файлов и т.д.)
- `antidaunleak.api` — 1 файл `src/client/java/antidaunleak/api/UserProfile.java:1` (24 строки)

**Проверено дополнительно:** отсутствие `package-info.java` (0 найдено), отсутствие barrel-реэкспортов (`grep export|barrel` — 0 совпадений в `src/main`), пустые java-пакеты (`find -name *.java` — 0 пустых), лимиты 500/1000 (`find -exec wc -l` — max 899 в main, 911 в client), build.gradle sourceSets `main`/`client`/`gametest`/`mcpdev`.

---

### 2) Находки

| # | severity | файл:строка | описание | рекомендация |
|---|----------|-------------|----------|--------------|
| 1 | **major** | `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java:1` (899 строк) | Единственный файл в `src/main` вплотную к hard-лимиту 1000 и значительно выше soft-лимита 500. Смешаны responsibilities: entity lifecycle + physics (`MoverType`, `ProjectileUtil`, `ClipContext`), `SynchedEntityData`, persistence (`ValueInput/ValueOutput`), damage, VFX/sound триггеры. Любая правка — merge-конфликт. | Разбить по seam: вынести `NailPhysics` (raycast/move), `NailPersistence` (read/write), `NailDamage` (damage source применение) в отдельные package-private классы в том же `projectjjk`. Цель ≤400 строк на класс. Трекать как tech debt, не блок релиза. |
| 2 | **major** | `src/client/java/antidaunleak/api/UserProfile.java:1` | Пакет `antidaunleak.api` вне иерархии `jujutsu.mod.*` — единственный top-level пакет в `src/client`. Формально нарушает конвенцию `AGENTS.md` §Technical Rules (весь mod-код под `jujutsu.mod`) и делает `archunit` правило side-separation слепым к этому коду. Исторически — stub для портированного Rich-клиента. | Переместить в `jujutsu.mod.client.rich.stub` или `jujutsu.mod.client.rich.api` и поправить импорты в `rich` экранах. Если нужен именно `antidaunleak` FQN для совместимости с Rich — задокументировать исключение в `AGENTS.md` и добавить `@SuppressWarnings` + комментарий в `UserProfile.java:1`. |
| 3 | **major** | `src/client/java/jujutsu/mod/client/rich/**` (~110 файлов, глубина 7) — напр. `rich/screens/clickgui/impl/settingsrender/ColorComponent.java:1` (911 строк), `rich/screens/clickgui/impl/settingsrender/MultiSelectComponent.java:1` (546), `rich/screens/clickgui/impl/background/search/SearchHandler.java:1` (414) | Портированный Rich-Modern clickgui живёт внутри `src/client` как монолитный borrowed codebase. 14 файлов в `rich/util/animations`, 8 в `settingsrender`, 7 уровней вложенности. Violates `AGENTS.md` §Code Organization Direction ("Do not invent empty packages. Prefer existing roots; add packages only when a feature needs them") в обратную сторону — packages изобретены borrow'ом, не фичей. Модульность jujutsumod страдает: `lsp references` на `jujutsu.mod.client.rich.*` не находит границ, `archunit` не покрывает `rich` отдельно. | Изолировать: (a) вынести `rich` в отдельный Gradle sourceSet `rich` или хотя бы документировать как `third-party vendored` в `docs/THIRD_PARTY_NOTICES.md`; (b) запретить импорты `jujutsu.mod.client.rich.*` из `main` (уже есть, но добавить archunit тест); (c) план поэтапного удаления/замены Rich после стабилизации ClickGui. Не рефакторить сейчас — зафиксировать как `KNOWN_ISSUES.md` debt. |
| 4 | **minor** | `src/main/java/jujutsu/mod/character/nobara/projectjjk/` — 35 файлов / 4627 строк vs `character/todo` 21 файл / 2293 строки vs `character/megumi` 17 файлов / 2742 строки | Асимметрия глубины: только Nobara имеет вложенный `projectjjk` subpackage (исторический артефакт портирования ProjectJJK), Todo и Megumi — flat. Нарушает единообразие vessel seam: новый контрибьютор ожидает `character/<id>/*.java`, а находит `character/nobara/projectjjk/*.java`. Усложняет `grep`/`codegraph` навигацию и будущий `add-vessel` copy-paste. | При следующем vessel-рефакторе рассмотреть схлопывание `nobara/projectjjk/*` → `nobara/*` (или наоборот — вынести общие префиксы в `character/nobara/` и оставить `projectjjk` как legacy alias с `@Deprecated` re-exports). Сейчас — добавить README в `character/nobara/README.md` с объяснением почему `projectjjk` существует (provenance, `docs/PROVENANCE.md`). |
| 5 | **minor** | `src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java:1` (606 строк), `src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java:1` (459), `src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java:1` (624) | Три файла превышают soft-лимит 500 (606/624 особенно). Не критично, но тренд: vessel runtimes и VFX recipes растут без декомпозиции. `MegumiSummonRuntime:1` уже содержит dissolve/teardown/pack lifecycle в одном классе. | Для `MegumiSummonRuntime` — выделить `MegumiPackLifecycle` (как уже есть `MegumiLifecyclePolicy.java:1` — усилить его). Для `NobaraVfxRecipes:1` — разбить по cues (nail/resonance/hairpin) или вынести helper методы в `NobaraVfxEmitters`. Порог — не блок, а наблюдение. |
| 6 | **minor** | `src/main/java/jujutsu/mod/command/JujutsuCommands.java:1` (191 строка) | Один файл в пакете `command`. Аналогично `mixin` (1 файл). Пакеты оправданы (feature-пакеты), но `JujutsuCommands.java` уже 191 строка и содержит все команды. При добавлении vessel-специфичных команд (дебаг) — риск превратить в god-file. | Оставить как есть. При росте >250 строк — вынести vessel-команды в `command/NobaraCommands.java` etc., регистрируемые через vessel seam (аналогично `registerServerHooks`). |
| 7 | **minor** | `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/settingsrender/ColorComponent.java:1` (911 строк) + 6 соседей 400–550 строк | Client soft-лимит 500 нарушен массово в `settingsrender` (borrowed code). Hard-лимит 1000 не пробит, но 911 — ближайший к нему в репо. | Не фиксить в jujutsumod — это vendored Rich код. Добавить `// vendored — do not enforce 500 limit` комментарий и исключить `rich/**` из будущего file-size lint (если будет). |
| 8 | **minor** | Отсутствие `package-info.java` во всех 60+ java-пакетах | Нет package-level javadoc/аннотаций (`@ParametersAreNonnullByDefault`, `@ApiStatus`). Утрачена возможность задокументировать инварианты пакета (напр. "vfx — server-only, client vfx — `client/vfx`"). | Добавить `package-info.java` хотя бы для ключевых пакетов: `character`, `vfx`, `network`, `registry`, `combat` с одной строкой инварианта. Низкий приоритет. |

> Примечание: проверка "отсутствие пустых пакетов" — **пройдена** для java: 0 пустых java-пакетов (каждый `find src/*/java -type d` содержит `*.java` в себе или в потомках). Пустые директории есть только в `src/main/resources` / `src/client/resources` (текстуры/geo/particles — 50+ пустых placeholder-папок), что нормально для asset-пайплайна и не считается нарушением.

---

### 3) Позитивные наблюдения

- **Vessel Seam соблюдён образцово.** `JujutsuMod.java:32-45` регистрирует vessel'ы циклом `for (CharacterDefinition d : JujutsuCharacters.all()) d.registerServerHooks()` — ни одного per-vessel `if/switch` в shared коде. Cross-vessel `grep import` — 0 пересечений `nobara↔todo↔megumi`.
- **Server/client split корректен.** `build.gradle:19-27` `splitEnvironmentSourceSets()` с `sourceSets.main` + `sourceSets.client` (Loom). `src/main/java` не содержит `net.minecraft.client.*` импортов (проверено `archunit` + ручной grep). Client VFX (`client/vfx`) vs server VFX (`mod/vfx`) — intentional duplication по контракту `VFX Core`.
- **Registry / Network / VFX — компактные, дисциплинированные пакеты.** `registry` 7 файлов / 379 строк, `network` 9 / 357, `vfx` 8 / 379 — каждый файл <120 строк, single responsibility (один `Registry.register` на файл). Barrel-реэкспорт отсутствует — это **плюс** (нет `index.ts`-стиля re-export, каждый импорт явный, tree-shaking не нужен, `lsp references` точен).
- **Файловые лимиты в целом соблюдены.** `src/main` max 899 (<1000 hard), остальные <606. `src/test` max 1260 — тест, не прод-код, допустимо. Client borrowed `rich` исключён из оценки — без него max 624.
- **Нет god-пакета.** Самый крупный прод-пакет `nobara/projectjjk` — 35 файлов, но каждый сфокусирован (Runtime/Profile/Policy/Entity). `character` корень — 12 файлов, не свалка.
- **`JujutsuMod.java:1` и `JujutsuModClient.java:1` тонкие.** Инициализация делегирована реестрам и vessel seam, без логики.

---

### 4) Вердикт

**PASS WITH CAVEATS**

Структура пакетов **соответствует** `AGENTS.md` §Code Organization Direction и §Vessel Seam: vessel'ы изолированы, shared код не ветвится по персонажам, client/server разделены Loom'ом, пустых java-пакетов нет, barrel-реэкспорт отсутствует (и это правильно), файловые лимиты 1000 не пробиты нигде в прод-коде (max 899). Модульность `registry`/`network`/`vfx`/`combat` — пример для подражания.

Caveats — 3 major, требующих решения, но не блока релиза:
1. `ProjectJjkNailEntity.java:1` (899) — главный кандидат на декомпозицию.
2. `antidaunleak.api` вне `jujutsu.mod` — package hygiene.
3. `rich` vendored монолит — архитектурный долг, нуждается в изоляции/документировании.

**Приоритет фиксов:**
1. **P1 (следующий спринт):** задокументировать `rich` как vendored в `THIRD_PARTY_NOTICES.md` + `KNOWN_ISSUES.md`, исключить `rich/**` из file-size lint — 1 час.
2. **P1:** переместить/задокументировать `antidaunleak.api` — 30 мин.
3. **P2 (при касании Nobara):** разбить `ProjectJjkNailEntity.java:1` на `NailPhysics`/`NailPersistence` — 1 день, делать только когда трогается nail-логика.
4. **P3:** `MegumiSummonRuntime.java:1` (606) и `NobaraVfxRecipes.java:1` (624) — опционально, low priority.
5. **P3:** добавить `package-info.java` для 5 ключевых пакетов — 1 час, good hygiene.

AUDIT_ID: 41-package-structure
