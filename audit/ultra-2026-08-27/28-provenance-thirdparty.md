## Provenance — 28-provenance-thirdparty

### 1) Что проверено (файлы/символы)

**Документация provenance:**
- `docs/PROVENANCE.md:1-16` — policy ProjectJJK placeholder, scope, Megumi assets оговорка
- `docs/THIRD_PARTY_NOTICES.md:1-51` — upstream notice Hadences, Segoe UI Semilight notice, Rich-Modern оговорка, MCP companion MIT notice с commit pin `0caf461`
- `docs/KNOWN_ISSUES.md:61-88` — R1 (Rich-Modern), R2 (Segoe UI), R3 (placeholder redistribution), Accepted decision ProjectJJK placeholder
- `AGENTS.md:311-320,370-373` — Asset Policy, Ownership правило `projectjjk`/`client/rich`
- `AGENTS.md:186-193` — Knowledge Bases, `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` ссылки на provenance
- `README.md:50-52` — лицензия CC0-1.0 + disclaimer "Not everything is covered"
- `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Assets-and-resources.md:1-15` — правила runtime resources, GeckoLib layout
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md:13-16` — риски Rich/Segoe/placeholder
- `Jujutsu Kaizen/jujutsumod-codebase-codex/01-meta/Uncertainties.md:7-11` — UNKNOWN для Rich-Modern и public redistribution
- `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md:33-35` — VERIFIED/UNKNOWN классификация

**Лицензия и метаданные:**
- `LICENSE:1-121` — CC0 1.0 Universal полный текст
- `src/main/resources/fabric.mod.json:10` — `"license": "CC0-1.0"`
- `build.gradle:72-74,91-153,558-700` — `auditDocumentation`, `auditReleaseJarIsolation`, `mcpdev` sourceSet, `-PmcpSpike`/`-PmcpUpstreamJar` knobs
- `gradle.properties:1-15` — `loom_version=1.17.17`, `mod_version=1.0.0`
- `.gitignore:62-64` — `Jujutsu Kaizen/grok-projectjjk-codex/` игнор

**ProjectJJK placeholder поверхность:**
- `git ls-files | grep projectjjk` — 460 tracked путей (проверено `git ls-files | grep -i projectjjk | wc -l`)
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/*.java` — 30+ Java файлов server-логики (проверено `find src -path "*projectjjk*" -type f`)
- `src/main/resources/assets/jujutsumod/{geo,animations,geckolib}/projectjjk/*` — geo/animation JSON
- `src/main/resources/assets/jujutsumod/textures/projectjjk/**` — ~80+ текстур (abilities/entity/particle/item/model)
- `src/main/resources/assets/jujutsumod/sounds/projectjjk/*.ogg` — 42 OGG (проверено `ls sounds/projectjjk`)
- `src/main/resources/assets/jujutsumod/sounds.json:1-141` — 24 SoundEvent, 17 `projectjjk.*`
- `archive/character-player-gecko/manifest.txt:21-25` — заархивированные `projectjjk/nobara_kugisaki.geo.json` + texture
- `src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java` + `src/client/java/jujutsu/mod/client/render/nobara/doll/*` — рендереры

**Rich-Modern provenance:**
- `src/client/java/jujutsu/mod/client/rich/**` — 216 Java файлов (проверено `git ls-files | grep rich | wc -l`)
- `src/client/java/jujutsu/mod/client/rich/util/timer/StopWatch.java:3-6` — `© 2025 Copyright Rich Client 2.0 All Rights Reserved`
- `src/client/java/jujutsu/mod/client/rich/util/timer/TimerUtil.java:3-6` — аналогичный header
- `src/client/java/antidaunleak/api/UserProfile.java:1-22` — orphan namespace, 0 references (`grep -rl antidaunleak src/` → 1 hit)
- `src/client/resources/assets/jujutsumod/fonts/*.png` + `shaders/core/msdf.*` — Rich-derived fonts/shaders
- `build.gradle:72-74` — `compileOnly lombok:1.18.36` для Rich sources

**Segoe UI font:**
- `src/main/resources/assets/jujutsumod/font/neon.ttf:1` — 869_992 bytes, Segoe UI Semilight
- `src/main/resources/assets/jujutsumod/font/neon.json:1-16` — TTF provider `jujutsumod:neon.ttf`
- `tools/generate_neon_font.py:1-134` — генератор bitmap atlas из Segoe TTF, удаляет `neon.ttf` после генерации

**MCP companion:**
- `src/mcpdev/**` — dev-only sourceSet
- `build.gradle:91-153,603-700` — `auditReleaseJarIsolation` forbids `jujutsu/mcpdev/`, `com/chapmanjw/`, `io/modelcontextprotocol/`

**Инструменты проверки:**
- `codegraph_explore` — индекс не покрывает Markdown provenance (fallback на `grep`/`read`)
- `grep` — `projectjjk`, `rich|Rich`, `neon.ttf|Segoe`, `Copyright`, `license|License`, `fabric.mod.json`
- `git ls-files` + `bash ls` — инвентаризация tracked assets
- `python tools/audit_docs.py` — `Documentation audit passed: 88 files, nobara_vfx_ids 24`
- `read` напрямую — PROVENANCE.md, THIRD_PARTY_NOTICES.md, KNOWN_ISSUES.md, AGENTS.md, LICENSE, fabric.mod.json

---

### 2) Находки

| severity | файл:строка | описание | рекомендация |
|---|---|---|---|
| **critical** | `src/main/resources/fabric.mod.json:10` + `LICENSE:1-5` vs `docs/THIRD_PARTY_NOTICES.md:5-13` + `docs/PROVENANCE.md:5-9` | **Fabric metadata заявляет `CC0-1.0` на весь артефакт, но репозиторий содержит All Rights Reserved контент (ProjectJJK placeholders от Hadences, Segoe UI Semilight, Rich-Modern с UNKNOWN лицензией).** `fabric.mod.json:license` публикуется на Modrinth/CurseForge и попадает в release jar. `README.md:52` честно дисклеймит "Not everything is covered", но `fabric.mod.json` дисклеймера не содержит. Для публичного релиза это misrepresentation — потребитель видит CC0, но 460 `projectjjk` путей + `neon.ttf` + 216 Rich файлов не CC0. CC0 §4(a) явно не покрывает trademark/патент, но All Rights Reserved Hadences — именно copyright, и CC0 waiver к нему не применим без relicense. | Разделить лицензию в `fabric.mod.json` или добавить `custom` поле: `"license": "CC0-1.0 for original code; see THIRD_PARTY_NOTICES.md for excluded paths"`. Долгосрочно — заменить placeholders и удалить `neon.ttf` до первой публикации на Modrinth. Добавить проверку в `auditReleaseJarIsolation` или новый `auditLicenseScope` что `projectjjk/**` + `font/neon.ttf` + `client/rich/**` не попадают под CC0 claim в `fabric.mod.json` description. |
| **critical** | `src/client/java/jujutsu/mod/client/rich/util/timer/StopWatch.java:3-6` + `TimerUtil.java:3-6` + весь `src/client/java/jujutsu/mod/client/rich/**` (216 файлов, `build.gradle:72-74` lombok) | **Rich-Modern provenance полностью unresolved, но код уже в `src/client` и компилируется в release jar.** `THIRD_PARTY_NOTICES.md:21-23` честно пишет "require a separate provenance/license decision before public release" — но текст лицензии Rich-Modern отсутствует, permission не записан, commit/источник не указан. Два файла сохранили `© 2025 Copyright Rich Client 2.0 All Rights Reserved ®` header — это Все права защищены, не MIT/Apache. `build.gradle:72` тянет `lombok` именно для этих sources (комментарий "Ported Rich-Modern clickgui sources use lombok"). R1 в `KNOWN_ISSUES.md:67-73` открыт с 2026-07-26 без прогресса. Публичный релиз с этим пакетом = потенциальное нарушение без relicense. | До релиза обязателен один из: (a) получить письменное разрешение автора Rich-Modern с указанием scope redistribution + зафиксировать в `THIRD_PARTY_NOTICES.md` с датой/контактом/commit, или (b) переписать `client/rich` на оригинальный SDF/MSDF-only ClickGui и удалить ported код. Минимальный cheap-win из `KNOWN_ISSUES.md:73` — удалить `src/client/java/antidaunleak/api/UserProfile.java` (orphan, 0 references) чтобы сузить поверхность с 216 до 215 файлов и убрать `antidaunleak` namespace. Добавить `git diff --stat` guard что `src/client/java/jujutsu/mod/client/rich` не растёт без provenance review. |
| **major** | `src/main/resources/assets/jujutsumod/font/neon.ttf:1` (869_992 bytes) + `src/main/resources/assets/jujutsumod/font/neon.json:4-5` | **Segoe UI Semilight bundled без лицензии Microsoft.** `THIRD_PARTY_NOTICES.md:15-17` честно идентифицирует: "identifies as Segoe UI Semilight. It is not Open Sans and is not covered by OFL. Remove or replace before public distribution." `KNOWN_ISSUES.md:75-81` R2 открыт. Файл 870 KB лежит в `src/main/resources` и пакуется в каждый jar (проверено `git ls-files | grep neon.ttf`). Segoe UI — proprietary Microsoft font, redistribution без лицензии = нарушение. `tools/generate_neon_font.py:131-133` уже умеет генерировать bitmap atlas и удалять `neon.ttf`, но в репозитории оба артефакта сосуществуют. | Удалить `neon.ttf` после проверки что ClickGui не грузит TTF напрямую: проверить `MsdfFonts.bootstrap()` vs `neon.json` reference. Если MSDF atlas уже покрывает весь рендер — удалить TTF и оставить только `tools/generate_neon_font.py` как build-time генератор. Иначе заменить на OFL шрифт (Open Sans, Inter, JetBrains Mono) и перегенерить `neon.json` + atlas. Добавить `auditReleaseJarIsolation` check на `assets/jujutsumod/font/neon.ttf` (аналогично MCP check). |
| **major** | `docs/PROVENANCE.md:12-16` | **Megumi player assets provenance — "delivered locally by owner, redistribution evidence not bundled" — means no аудитируемый chain.** Текст: "Authorship and redistribution evidence are not bundled in the repository. Treat both inputs as private-development assets. Record their redistribution permission or replace them before a public release." Это 3 файла минимум: `textures/entity/character/megumi.png` + `geckolib/models/character_skin/megumi.geo.json` + `geckolib/animations/megumi/megumi_fushiguro.animation.json` + дополнительно 64x64 skin для рук/ростера. Источник не верифицирован, лицензия не указана. При публичной публикации без записи — тот же риск что и ProjectJJK placeholder. | Запросить у владельца исходники/автора Blockbench модели Megumi, зафиксировать в `PROVENANCE.md` кто автор, дата передачи, scope redistribution (CC0 / proprietary with permission / commission). Если это commission — приложить договор/письмо. Если source неизвестен — пометить как R3-подобный блокер и запланировать замену. До тех пор `PROVENANCE.md:14` формулировка "Treat as private-development" корректна для private dev, но недостаточна для public. |
| **major** | `src/client/java/antidaunleak/api/UserProfile.java:1-22` | **Orphan namespace `antidaunleak` — единственный файл в imported `antidaunleak` namespace, 0 references в `src/`.** `KNOWN_ISSUES.md:73` прямо называет: "Cheapest available reduction: `UserProfile.java` is the only remaining file in the imported `antidaunleak` namespace, and nothing references it. Removing it would shrink unresolved-provenance surface by one whole namespace at zero functional cost." Файл — stub для Rich avatar panel (`profile("username"/"uid")` читает `Minecraft.getInstance().player`). Сам факт существования `antidaunleak` package в `src/client/java` сигнализирует аудитору что проект импортировал анти-утечку/лицензионный код Rich-Modern без очистки. | Удалить `src/client/java/antidaunleak/api/UserProfile.java` и пустой package `antidaunleak/api`. Это задокументированный в `KNOWN_ISSUES.md` zero-cost win для R1. Выполнить как отдельный commit с message `chore(provenance): remove orphan antidaunleak stub, zero references` и обновить `KNOWN_ISSUES.md:73` (закрыть cheapest reduction). Проверить что `grep -r antidaunleak` после удаления пуст. |
| **major** | `build.gradle:603-700` (`auditReleaseJarIsolation`) + `git ls-files \| grep projectjjk` (460 путей, `src/main/resources/assets/jujutsumod/textures/projectjjk/**`, `geo/projectjjk/**`) | **Нет автоматической защиты от "тихой экспансии" ProjectJJK placeholder set.** `PROVENANCE.md:9` + `THIRD_PARTY_NOTICES.md:13` оба требуют "Do not expand this imported asset set." Но `auditReleaseJarIsolation` проверяет только `jujutsu/mcpdev/`, `com/chapmanjw/`, `io/modelcontextprotocol/` — про `projectjjk` там ни слова. Новый `textures/projectjjk/foo.png` пройдёт gate зелёным. 460 файлов уже в репозитории (geo, animations, textures, sounds), и без guard число будет расти. `AGENTS.md:319` делегирует policy в PROVENANCE/THIRD_PARTY, но gate его не enforces. | Расширить `auditReleaseJarIsolation` или добавить отдельный `auditProvenance` task: (a) лимит на `projectjjk` file count (сейчас 460 — зафиксировать floor и фейлить при росте без явного approve), или (b) проверка что каждый новый `projectjjk` путь присутствует в allowlist. Минимум — добавить `grep -c projectjjk` assertion в `ProjectSanityTest` аналогично существующим scan floors. |
| **major** | `src/main/resources/fabric.mod.json:10` (`"license": "CC0-1.0"`) + `LICENSE:1` (CC0) vs `build.gradle:72-74` (lombok compileOnly) + `src/client/java/jujutsu/mod/client/rich/**` (Rich-Modern) | **Lombok и другие third-party runtime/build зависимости не отражены в `THIRD_PARTY_NOTICES.md`.** `THIRD_PARTY_NOTICES.md` перечисляет только ProjectJJK, Segoe UI, Rich-Modern, `minecraft-java-fabric-mcp-server` (MIT). При этом `build.gradle` тянет `lombok:1.18.36` (MIT-ish, но требует notice), `fabric-loader`, `fabric-api`, `geckolib`. Для публичного релиза Modrinth требует хотя бы `fabric-api` + `geckolib` как dependencies (они уже в `fabric.mod.json:depends`), но `THIRD_PARTY_NOTICES.md` их не упоминает. Lombok как `compileOnly` не попадает в jar, но остаётся part of build provenance. | Пополнить `THIRD_PARTY_NOTICES.md` секциями для `org.projectlombok:lombok` (MIT), `net.fabricmc:fabric-loader` (Apache 2.0), `net.fabricmc.fabric-api` (Apache 2.0), `com.github.jeffreysmith: GeckoLib` (MIT). Альтернатива — явно указать что `compileOnly` deps не требуют bundling notice, но для полноты лучше перечислить. Синхронизировать с `fabric.mod.json:depends` секцией. |
| **minor** | `docs/PROVENANCE.md:5-10` + `docs/THIRD_PARTY_NOTICES.md:5-13` | **Дублирование provenance policy между двумя файлами без канонического owner.** `PROVENANCE.md:5-9` и `THIRD_PARTY_NOTICES.md:5-13` оба описывают "temporary placeholders with permission, not CC0, do not expand, replace before public release" с почти дословным пересечением. `AGENTS.md:188,319` ссылается на оба файла как owner. `KNOWN_ISSUES.md:63` говорит "Owned by PROVENANCE.md and THIRD_PARTY_NOTICES.md. Those files hold the scope" — но не уточняет какой файл canonical для permission scope vs retained notice. При изменении scope нужно править оба файла, риск рассинхрона (как было с E1 branch docs). | Объявить `PROVENANCE.md` canonical для policy/scope/replacement plan, `THIRD_PARTY_NOTICES.md` — только для retained upstream notices (копии лицензий). Убрать дублирующий policy текст из `THIRD_PARTY_NOTICES.md:5-6,13` оставив там только `> Copyright © 2023 Hadences` блок + ссылку на `PROVENANCE.md`. Зафиксировать в `AGENTS.md:188` что "policy lives in PROVENANCE.md, notices in THIRD_PARTY_NOTICES.md". |
| **minor** | `src/main/resources/assets/jujutsumod/textures/projectjjk/**` + `src/main/resources/assets/jujutsumod/geo/projectjjk/**` + `src/main/resources/assets/jujutsumod/animations/projectjjk/**` vs `AGENTS.md:317` ("Never copy anime assets") | **Отсутствие явной проверки "no anime rip" — policy есть, но gate её не проверяет.** `AGENTS.md:317` требует "Never copy anime assets into the repo unless licensing is explicit. Prefer original/inspired designs." Placeholder textures под `projectjjk` — как раз пограничный случай: они не прямые rips из anime, но и не original — это производные от ProjectJJK fan-game. Документация честно маркирует их как temporary, но `auditDocumentation` не проверяет наличие новых `textures/projectjjk/**` без provenance entry, и `build.gradle` не фейлит если кто-то добавит `textures/jujutsu_kaisen/logo.png` с copyrighted artwork. | Добавить в `tools/audit_docs.py` или новый `auditProvenance` проверку: любой файл под `textures/projectjjk/**` должен быть перечислен в `PROVENANCE.md` или иметь комментарий в `Assets-and-resources.md`. Для `textures/entity/character/{megumi,nobara,todo}.png` — убедиться что они не являются upscale/копиями ProjectJJK textures (сейчас `nobara.png` — original? нужен visual diff). Зафиксировать в `PROVENANCE.md` какие именно `projectjjk` sub-paths считаются placeholder (сейчас только общая фраза "assets, data, manifests"). |
| **minor** | `build.gradle:91-153` (`mcpdev` sourceSet) + `docs/THIRD_PARTY_NOTICES.md:25-27,29-51` | **MCP companion provenance — единственная образцово оформленная секция, но pin `0caf461` не зафиксирован в `build.gradle`.** `THIRD_PARTY_NOTICES.md:27` пишет "inspected at commit `0caf461`" и приводит полный MIT текст, `build.gradle:91-153` требует `-PmcpUpstreamJar=<path>` и `auditReleaseJarIsolation` проверяет отсутствие `mcpdev` в release jar — это идеальный образец. Недостаток: `build.gradle` не содержит `0caf461` нигде (`grep 0caf461 build.gradle` — 0 hits), pin живёт только в Markdown. Если кто-то подсунет другой commit, gate не поймает. | Зафиксировать expected MCP commit в `build.gradle` (комментарий рядом с `mcpUpstreamJar` property) или в `gradle.properties` как `mcp_server_commit=0caf461`, и добавить в `auditReleaseJarIsolation` проверку что upstream jar manifest/commit совпадает. Сейчас это minor, потому что `mcpdev` вообще не пакуется в release и риск только для dev. |

---

### 3) Позитивные наблюдения

- **Честность документации выше среднего.** `PROVENANCE.md:5-9` + `THIRD_PARTY_NOTICES.md:5-13` явно пишут "All Rights Reserved", "not relicensed as CC0", "temporary private-development placeholders" — никакой попытки выдать чужие ассеты за свои. Upstream notice `© 2023 Hadences` сохранён дословно с `> blockquote`. Это редкость — многие проекты просто молчат.
- **R1/R2/R3 заведены как release blockers с actionable next steps.** `KNOWN_ISSUES.md:65-88` содержит три R-блокера с `Verified 2026-07-26`, cheapest reduction для R1 (`UserProfile.java` orphan), размером `neon.ttf` (~870 KB) для R2, и ссылкой на policy для R3. Каждый помечен "Still open" — нет ложного `PASS`.
- **MCP companion — образцовый isolation.** `build.gradle:91-153` dev-only sourceSet `mcpdev`, `-PmcpSpike`/`-PmcpUpstreamJar` knobs, `auditReleaseJarIsolation:603-700` forbids `jujutsu/mcpdev/` + `com/chapmanjw/` + `io/modelcontextprotocol/` + `mcp-tools` entrypoint. `THIRD_PARTY_NOTICES.md:25-51` приводит полный MIT текст с commit pin. Лучшая секция provenance в проекте — можно копировать как шаблон для будущих deps.
- **Megumi assets — честный UNKNOWN вместо выдуманного.** `PROVENANCE.md:12-16` не притворяется что provenance есть: "Authorship and redistribution evidence are not bundled. Treat as private-development assets. Record permission or replace before public release." Это корректный `UNKNOWN` по `Citation-standard.md`.
- **CC0 для original code выбран осознанно, не по умолчанию.** `LICENSE:1` полный CC0 1.0, `README.md:52` дисклеймит исключения, `AGENTS.md:317-319` policy "Prefer original/inspired designs over copyrighted rips" — консистентная лицензионная философия, а не случайный MIT.
- **`tools/generate_neon_font.py:1-134` уже решает R2.** Скрипт генерирует bitmap atlas из TTF и удаляет `neon.ttf` (`old.unlink():133`), но артефакт `neon.ttf` ещё лежит в репозитории — решение написано, осталось применить.
- **Scope placeholder ограничен корректно.** `PROVENANCE.md:5` "Imported content is limited to assets, data, manifests; no Java class files or dependency jars are bundled" — `grep projectjjk src/main/java` показывает только `character/nobara/projectjjk` (своя логика, не импорт), `src/mcpdev` изолирован отдельно. Нет vendor jar в `libs/`.
- **Документационный аудит gate существует.** `tools/audit_docs.py` + `build.gradle:558,689` `auditDocumentation` + `qualityGate` — 88 Markdown файлов, метрики `main_java 126, client_java 191`. Gate не фейлит на provenance, но хотя бы проверяет stale references и broken links.
- **`grok-projectjjk-codex` вынесен в `.gitignore`.** `AGENTS.md` прошлый инцидент: research material случайно попал через `git add -A`, теперь `Jujutsu Kaizen/grok-projectjjk-codex/` в `.gitignore:63` — урок усвоен.

---

### 4) Вердикт

**Вердикт: PASS WITH CAVEATS**

**Обоснование:** Для текущего статуса проекта (**private development, 1–2 игрока, не публикуется на Modrinth/CurseForge**) provenance оформлен честно и достаточно. Все три риска (ProjectJJK placeholders, Rich-Modern, Segoe UI) явно задокументированы как `UNKNOWN`/blocker, upstream notices сохранены, CC0 scope дисклеймится в `README.md:52`, MCP companion изолирован образцово. Код не пытается выдать чужие ассеты за CC0, не копирует anime rips напрямую, и не пакует `mcpdev` в release jar — `auditReleaseJarIsolation` это доказывает.

Для **публичного релиза** текущий вердикт — **FAIL** по трём blocker-ам (R1, R2, R3 + Megumi). Но в рамках private slice это `PASS WITH CAVEATS` — caveats задокументированы и не скрыты.

**Приоритет фиксов:**

| приоритет | действие | файл(ы) | блокер |
|---|---|---|---|
| **P0 (до первой публикации)** | Получить письменное разрешение Rich-Modern или переписать `client/rich` | `src/client/java/jujutsu/mod/client/rich/**`, `docs/THIRD_PARTY_NOTICES.md:21-23` | R1 |
| **P0 (до первой публикации)** | Удалить или заменить `neon.ttf` (Segoe UI) | `src/main/resources/assets/jujutsumod/font/neon.ttf`, `neon.json:4-5` | R2 |
| **P0 (до первой публикации)** | Зафиксировать redistribution scope для ProjectJJK placeholders или заменить ассеты | `docs/PROVENANCE.md:5-9`, `textures/projectjjk/**`, `geo/projectjjk/**` | R3 |
| **P0 (до первой публикации)** | Зафиксировать provenance Megumi модели/текстуры/анимаций | `docs/PROVENANCE.md:12-16`, `textures/entity/character/megumi.png` | R3-like |
| **P1 (быстрый win)** | Удалить orphan `antidaunleak/api/UserProfile.java` | `src/client/java/antidaunleak/api/UserProfile.java:1-22` | R1 surface |
| **P1** | Уточнить `fabric.mod.json:10` license field — добавить дисклеймер про excluded paths | `src/main/resources/fabric.mod.json:10` | critical |
| **P1** | Добавить gate против экспансии `projectjjk` asset set | `build.gradle:603-700`, `tools/audit_docs.py` | major |
| **P2** | Пополнить `THIRD_PARTY_NOTICES.md` для lombok/fabric-api/geckolib | `docs/THIRD_PARTY_NOTICES.md:1-51` | minor |
| **P2** | Развести canonical ownership PROVENANCE vs THIRD_PARTY_NOTICES | `docs/PROVENANCE.md`, `docs/THIRD_PARTY_NOTICES.md` | minor |
| **P2** | Зафиксировать MCP commit pin в `build.gradle`/`gradle.properties` | `build.gradle:91-153` | minor |

AUDIT_ID: 28-provenance-thirdparty
