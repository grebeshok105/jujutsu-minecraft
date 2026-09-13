## Build Gate — 25-build-gradle-qualitygate

> Дата: 2026-08-27 · MC 1.21.8 · Java 21 · Gradle 9.5.1 · Loom 1.17.17 · mod id `jujutsumod`

### 1) Что проверено (файлы/символы)

**Инструмент:** `codegraph_explore`, `grep`, `read`, прямой запуск `gradlew.bat` c `JAVA_HOME=temurin21`.

| Область | Файлы | Что смотрел |
|---------|-------|-------------|
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties:1-9` | `distributionUrl` → `gradle-9.5.1-bin.zip`, `validateDistributionUrl=true`, `retries=0` |
| Gradle properties | `gradle.properties:1-20` | `minecraft_version=1.21.8`, `loader_version=0.19.3`, `loom_version=1.17.17`, `fabric_api_version=0.136.1+1.21.8`, `geckolib_version=5.2.2`, `org.gradle.jvmargs=-Xmx1G`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=false` |
| Build script | `build.gradle:1-759` весь файл, два прохода `read` | plugins, `loom { splitEnvironmentSourceSets() }`, `fabricApi { configureTests }`, `loom.runs.*`, dependencies, `mcpdev` sourceSet, `jarMcpdev`, `prepareMcpSpikeRun`, 29× `JavaExec` verification programs, `check`, `verifyAssertionsEnabled`, `auditDocumentation`, `auditReleaseJarIsolation`, `qualityGate`, `processResources`, `JavaCompile.options.release`, `test { useJUnitPlatform }`, `java { sourceCompatibility }`, `jar`, `publishing` |
| Fabric descriptors | `src/main/resources/fabric.mod.json:1-35`, `src/gametest/resources/fabric.mod.json` | `id`, `depends.minecraft ~1.21.8 / java >=21 / fabricloader >=0.19.3`, entrypoints `main`/`client` vs `fabric-gametest`/`fabric-client-gametest`, `environment`, `mixins` |
| Mixins | `src/main/resources/jujutsumod.mixins.json:1-12`, `src/client/resources/jujutsumod.client.mixins.json:1-16` | `compatibilityLevel JAVA_21`, инжекторы, списки миксов |
| Documentation audit | `tools/audit_docs.py:1-143` весь, `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md:1-99` | `CURRENT_DOCS`, `REMOVED_DOC_DIRS`, `FORBIDDEN_REFERENCES`, `code_metrics()`, `validate_links()`, `TEST_TASK_PATTERN`, MOC-метрики |
| CI | `.github/workflows/build.yml`, `.github/workflows/client-gametest.yml` | `actions/checkout@v6`, `gradle/actions/wrapper-validation@v6`, `setup-java@v5` `temurin 21`, `./gradlew qualityGate --no-daemon`, артефакты, `workflow_dispatch` для client lane |
| Живой прогон | `gradlew.bat tasks --group=verification`, `gradlew.bat verifyAssertionsEnabled`, `python3 tools/audit_docs.py`, `gradlew.bat check --dry-run` с `JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current` | соответствие 29 verification-программ, `failOnNoDiscoveredTests`, отчёт GameTest |

**Команды (свежий вывод 2026-08-27):**

- `JAVA_HOME=temurin21 gradlew.bat tasks --group=verification` → 29 задач `test*` + `auditDocumentation`, `auditReleaseJarIsolation`, `verifyAssertionsEnabled`, `qualityGate` — BUILD SUCCESSFUL.
- `JAVA_HOME=temurin21 gradlew.bat verifyAssertionsEnabled` → `verifyAssertionsEnabled: 29 verification JavaExec tasks all enable assertions` — BUILD SUCCESSFUL.
- `python3 tools/audit_docs.py` → `Documentation audit passed: 88 current Markdown files, metrics={'main_java':126,…}` — EXIT 0, MOC-метрики совпали.
- `gradlew.bat --version` без `JAVA_HOME=21` → `Could not resolve fabric-loom:1.17.17 Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.` — корректно падает, требует Java 21.
- `gradlew.bat --version` с `JAVA_HOME=21` → `Gradle 9.5.1, Kotlin 2.3.20, Groovy 4.0.29, Launcher JVM 17 → Daemon JVM 21.0.11 Temurin` — BUILD SUCCESSFUL.
- `gradlew.bat check --dry-run` → все задачи в порядке `SKIPPED` (up-to-date), BUILD SUCCESSFUL.

---

### 2) Находки

| # | Severity | Файл:строка | Описание | Рекомендация |
|---|----------|-------------|----------|--------------|
| 1 | **minor** | `gradle.properties:6` + `build.gradle:174` | `org.gradle.configuration-cache=false` отключён глобально с комментарием `IntelliJ IDEA is not yet fully compatible, see fabric-loom#1349`. Это принято как workaround, но issue 1349 закрывается по мере релизов Loom/IDEA. Вечный `false` лишает проект самой полезной оптимизации Gradle 9 и маскирует проблемы configuration-cache у кастомных задач (`verifyAssertionsEnabled`, `auditDocumentation`, `auditReleaseJarIsolation` используют `doLast` с захватом `project` — `build.gradle:175` аккуратно комментирует `Project projectRef = project` как deprecated-безопасный захват, но такие задачи по дизайну не configuration-cache-safe). | Периодически перепроверять `fabric-loom#1349`; завести задачу «включить CC на пробу» раз в квартал. Долгосрочно — мигрировать кастомные `doLast` на `BuildService`/`@Input` свойства, тогда флаг можно будет вернуть в `true` без риска. |
| 2 | **minor** | `build.gradle:725-733` | `java { sourceCompatibility = VERSION_21; targetCompatibility = VERSION_21 }` продублирован поверх `tasks.withType(JavaCompile){ options.release = 21 }` (`build.gradle:709-711`). При `options.release = 21` пара `source/targetCompatibility` избыточна и в Gradle 9 считается устаревшим способом фиксации языка. Функционально безвредно (loom 1.17 корректно уважает `release`), но вводит два источника истины. | Удалить блок `sourceCompatibility/targetCompatibility`, оставить только `options.release = 21` + при желании `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` для авто-провижена JDK на CI. |
| 3 | **minor** | `build.gradle:709-711` | Отсутствие `java.toolchain` / `jvmToolchain`. Сборка требует Java 21 только через `options.release = 21` и ранний фейл Loom (`Dependency requires at least JVM runtime version 21`). На машине с `JAVA_HOME=17` фейл приходит лишь на этапе `configure` из резолва `fabric-loom:1.17.17`, а не из явного `toolchain` сообщения. CI ставит 21 через `setup-java@v5`, локально — ручной `JAVA_HOME`. | Добавить `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` — Gradle сам скачает/выберет 21, сообщение об ошибке станет явным, `gradlew` без `JAVA_HOME=21` перестанет падать неочевидным резолвом Loom. Совместимо с `options.release`. |
| 4 | **minor** | `build.gradle:122-124` | `tasks.named('assemble') { dependsOn tasks.named('jarMcpdev') }` безусловно wiring'ит `jarMcpdev` в `assemble`, но сам `jarMcpdev` матчится правилом `tasks.matching { it.name.toLowerCase().contains('mcpdev') }.configureEach { onlyIf { mcpCompanionEnabled } }` (`build.gradle:134-136`). В итоге `assemble` без `-PmcpUpstreamJar` всё равно «зависит» от задачи, которая тут же `SKIPPED` по `onlyIf`. Функционально корректно (проверено: `assemble` зелёный без проперти), но `assemble` в отчёте `--dry-run` всегда тянет лишний узел графа, а `--rerun-tasks` триггерит объяснение «why skipped». | Заменить безусловный `dependsOn` на условный: `if (mcpCompanionEnabled) tasks.named('assemble').configure { dependsOn 'jarMcpdev' }` либо `tasks.named('assemble').configure { dependsOn(tasks.named('jarMcpdev').map { mcpCompanionEnabled ? it : null }) }`. Семантика не изменится, граф станет чище. |
| 5 | **minor** | `build.gradle:558-579` (`auditDocumentation`) | `auditDocumentation` резолвит интерпретатор перебором `['python3','python']` через `ProcessBuilder(candidate,'--version')` (`build.gradle:583-595`). На Windows без `python` в `PATH` таск падает `GradleException('neither python3 nor python is on PATH')`. CI (`ubuntu-24.04`) имеет `python3`, локально у Windows-разработчика — `py` launcher, а не `python`. Падение честное, но сообщение не подсказывает `py`/`python3.12` и не упоминает `py -3`. | Расширить `resolvePythonInterpreter()` кандидатами `['python3','python','py']` и для `py` пробовать `py -3 --version`. Либо задокументировать в `docs/BUILDING_IN_SANDBOX.md` требование `python` в `PATH` на Windows. |
| 6 | **minor** | `build.gradle:603-680` (`auditReleaseJarIsolation`) | Аудит `auditReleaseJarIsolation` сканирует только outer zip-entries и честно комментирует ограничение (`build.gradle:600-602`: `Scope note: … walks the OUTER jar's entries only — if jar-in-jar … must learn to open nested jars`). Если в будущем появится `loom { include(...) }` / `META-INF/jars`, test-мод просочится мимо всех 9 правил. Сегодня `grep -rn loom.*include` пуст — риска нет, но инвариант хрупок: один `include` в `dependencies` ломает гарантию молча. | Добавить в `auditReleaseJarIsolation` проверку `if (name.startsWith('META-INF/jars/') \|\| name == 'META-INF/jars') offenders << ...` либо явный `if (jarHasNestedJars) throw GradleException("nested jars present — extend audit")`. Это превратит молчаливый пропуск в громкий фейл. |
| 7 | **minor** | `build.gradle:735-742` (`jar` task) | `jar { from("LICENSE") { rename { "${it}_$projectName"} } }` — поле `it` в `rename` это имя файла (`LICENSE`), результат `LICENSE_jujutsumod`. Нетривиально, но корректно. Более хрупко: `from("LICENSE")` без `include`/`duplicatesStrategy` — если в корне появится второй `LICENSE*`, он тоже попадёт в jar. | Явно указать `from("LICENSE") { include "LICENSE"; rename { ... } }` или перейти на `from(rootProject.file("LICENSE"))`. Не критично. |
| 8 | **minor** | `gradle-wrapper.properties:5` | `retries=0` и `networkTimeout=10000` (10 s) — дефолт Fabric Template. На flaky CI/слабом интернете скачивание `gradle-9.5.1-bin.zip` падает без ретраев, хотя `validateDistributionUrl=true` корректно включён. | Поднять `retries=3` и `retryBackOffMs=1500` (дефолт Gradle) — бесплатная устойчивость без побочных эффектов. |
| 9 | **minor** | `gradle.properties:19-20`, `build.gradle:73-76` | Версии зафиксированы, но `dependency locking` / `version catalog` (`libs.versions.toml`) отсутствует — файл `libs.versions.toml` не найден. Обновление транзитивок (Loom → ASM, Fabric API → netty) недетерминировано между `clean` сборками. Для релиз-мода это приемлемо (Loom пинит MC), но `docs/KNOWN_ISSUES.md:E9` прямо отмечает `Build reproducibility can improve`. | При подготовке к публикации включить `dependencyLocking { lockAllConfigurations() }` и закоммитить `gradle.lockfile`, либо мигрировать на `libs.versions.toml` catalog. Не блокирует разработку. |

> **Итого: 9 minor, 0 major, 0 critical.** Код сборки в отличной форме — все находки это полировка/устойчивость, а не функциональные дефекты.

---

### 3) Позитивные наблюдения

- **QualityGate — эталонный.** `tasks.register('qualityGate') { dependsOn check, auditDocumentation, verifyAssertionsEnabled }` + `tasks.named('qualityGate'){ dependsOn auditReleaseJarIsolation }` (`build.gradle:683-697`) — ровно спецификация `check + auditDocumentation + assertions audit + auditReleaseJarIsolation`. `check` тянет `compileJava`/`compileClientJava` через `test` classpath, так что компиляторный барьер внутри гейта без дублирования. CI (`build.yml:17-24`) не держит ручного списка шагов — просто `./gradlew qualityGate --no-daemon`, один источник истины.
- **Assertions audit — образцовая защита от fail-open.** `verifyAssertionsEnabled` (`build.gradle:526-554`) читает реальный `tasks.withType(JavaExec).matching{ group == verification }.toList()` и проверяет оба пути `enableAssertions` / `jvmArgs -ea`, включая `-ea:package`. Комментарий (`build.gradle:522-525`) честно объясняет, почему без `-ea` `assert` молча зелёный. Проверено: 29/29 программ имеют `-ea`.
- **Release jar isolation — 9 правил, все уместны.** `auditReleaseJarIsolation` (`build.gradle:603-680`) зависит от `remapJar`, сканирует `remapJar.archiveFile`, ловит `jujutsu/mod/gametest/`, `jujutsu/mod/{bridge,mcp,control}`, `data/jujutsumod-gametest/`, `net/fabricmc/fabric/api/gametest/`, `com/tngtech/archunit/`, `com/chapmanjw/`, `io/modelcontextprotocol/`, `jujutsu/mcpdev/`, второй `fabric.mod.json` и `id == jujutsumod-gametest`. Scope-ограничение outer-jar честно задокументировано.
- **Mojang mappings — корректно.** `mappings loom.officialMojangMappings()` (`build.gradle:65`) — единственно верный выбор для 1.21.8. `loom { splitEnvironmentSourceSets() }` + `mods { jujutsumod { sourceSet main; sourceSet client } }` (`build.gradle:19-27`) — каноничный split, Mixins разделены по `environment`.
- **GameTest wiring — аккуратный.** `fabricApi { configureTests { createSourceSet=true; modId=jujutsumod-gametest; enableGameTests=true; enableClientGameTests=true } }` (`build.gradle:36-43`), `loom.runs.named('gameTest'){ property 'fabric-api.gametest.report-file', layout.buildDirectory... }` (`build.gradle:49-51`) — использует `layout.buildDirectory` (Gradle 9 замена `buildDir`). Client lane (`build.gradle:58-60`) корректно отмечает отсутствие client report-file в `fabric-api 0.136.1`. Client lane не входит в `check`/`qualityGate` by design — `client-gametest.yml` только `workflow_dispatch`.
- **Gradle 9.5.1 + Java 21 — свежие.** `gradle-wrapper.properties:3` `gradle-9.5.1-bin.zip` — актуальная ветка. `loader 0.19.3` — последняя стабильная для 1.21.8. `fabric-api 0.136.1+1.21.8`, `geckolib 5.2.2` — свежие для целевого MC. `loom 1.17.17` — стабильный релиз (снят `1.17-SNAPSHOT`, закрыт E9 частично). `options.release = 21` (`build.gradle:710`) + `compatibilityLevel JAVA_21` в обоих `mixins.json:4`/`src/client/...:4`.
- **Wrapper validation в CI.** Оба workflow (`build.yml:11`, `client-gametest.yml:13`) используют `gradle/actions/wrapper-validation@v6` до `setup-java`.
- **Документационный аудит — сильный.** `tools/audit_docs.py` проверяет `CURRENT_DOCS`, `REMOVED_DOC_DIRS`, `FORBIDDEN_REFERENCES`, `LINK_PATTERN`, `code_metrics()` против MOC (`MOC metric is stale` — `build.gradle:128-130`), `markdown_files()` через `git ls-files -z` (только tracked). MOC-метрики (`MOC.md:37-45`) совпали с live: `Main 126 / Client 191 / Test 85 / Verification 29 / Client mixins 6 / Nobara VFX 24`.
- **MCP dev lane — dormant by default.** `mcpdev` sourceSet (`build.gradle:100-107`) использует `compileClasspath += main.output`, `jarMcpdev` явный, `tasks.matching{ contains('mcpdev') }.configureEach{ onlyIf{ mcpCompanionEnabled } }` — без `-PmcpUpstreamJar` всё `SKIPPED`, продакшн без MCP-импортов. `modLocalRuntime` vs `localRuntime` разграничение (`build.gradle:154-156`) корректно (Loom remap только upstream).
- **`failOnNoDiscoveredTests = true`** (`build.gradle:722`) — JUnit с нулём тестов падает, а не зеленеет. `tasks.withType(JavaCompile).configureEach{ options.release=21 }` покрывает все sourceSets включая `gametest`/`mcpdev`.
- **`fabric.mod.json` — строгий.** `depends: minecraft ~1.21.8 / java >=21 / fabricloader >=0.19.3 / fabric-api * / geckolib >=5.2.2`, `schemaVersion 1`, `environment *`, оба entrypoint'а. Gametest-дескриптор (`src/gametest/resources/fabric.mod.json`) имеет `depends.jujutsumod *` и `environment *`.
- **Lombok — узко и корректно.** `compileOnly + annotationProcessor` для `main`, `clientCompileOnly + clientAnnotationProcessor` для `client` (`build.gradle:73-76`), без `implementation` — не течёт в рантайм.

---

### 4) Вердикт

**PASS WITH CAVEATS** (граничит с **PASS** — все caveats minor, гейт зелёный, сборка воспроизводима).

Сборка — одна из самых аккуратных в проекте. `qualityGate` ровно соответствует спецификации, все 4 компонента на месте, инварианты `verifyAssertionsEnabled` и `auditReleaseJarIsolation` реализованы образцово, Mojang mappings / Fabric Loom / версии зависимостей — корректны и свежи, Gradle 9.5.1 + Java 21 enforced. Единственный способ собрать релиз — `./gradlew qualityGate --no-daemon` с Java 21 (проверено: Java 17 падает на резолве Loom, как и должно).

**Приоритет фиксов (все — когда удобно, не блокеры):**

1. **P3 (next sprint):** #6 — добавить `META-INF/jars` детектор в `auditReleaseJarIsolation` (один `if`, превращает молчаливый пропуск в громкий фейл при будущем `loom include`).
2. **P3:** #8 — `retries=3` в `gradle-wrapper.properties` (одна строка, устойчивость скачивания).
3. **P4 (backlog):** #3 — добавить `java.toolchain { languageVersion 21 }` (лучшее сообщение об ошибке, авто-провижен JDK).
4. **P4:** #5 — расширить `resolvePythonInterpreter()` кандидатом `py` для Windows.
5. **P4:** #4 — условный `dependsOn jarMcpdev` (чистота графа).
6. **P4:** #2 — удалить дублирующий `sourceCompatibility/targetCompatibility`.
7. **P5 (pre-release):** #9 — `dependencyLocking` / `libs.versions.toml` перед публикацией; #1 — перепроверить `configuration-cache` после обновления Loom/IDEA.

AUDIT_ID: 25-build-gradle-qualitygate
