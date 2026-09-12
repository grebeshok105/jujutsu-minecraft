# Current State — память проекта

Status: CURRENT
Обновлено: 2026-09-09

## 2026-09-09 — dev-lane переехал в основной репозиторий (ветка feat/dev-lane-home)

- Код lane (src/mcpdev, gradle-обвязка -PmcpSpike, prepareMcpSpikeRun) уже был в основной линии — переносить не пришлось; старый клон `Jujutsu Minecraft/.worktrees/mcp-port-spike` (Aug 7) устарел. Сейв `run/saves/mcp-spike` скопирован сюда, lane поднят и проверен из этого репозитория.
- Починено два бага mcpdev (коммит 90979a8): (1) три тула #66-эпохи (fixture_list, player_set_rotation, ticks_wait) лежали файлами, но не были в `JujutsuModStatusToolProvider.toolClasses()` — в живом реестре их не было; (2) `JujutsuTicksWaitTool`: `player == null ||` в условии готовности завершал чистый tick-wait мгновенно (0 тиков) — теперь ждёт ровно N.
- Проверено вживую: 11/11 jujutsu-тулов (всего 115 на 8765), client 6 на 8766; mod_status/vessel_list/fixture_list/state_get/cooldowns/vessel_select/ability_invoke (routed swap todo↔zombie, swap_momentum true)/player_set_rotation/ticks_wait (40 тиков ≈ 2.2 с)/fixture_reset (20 шагов) — зелёные; скриншоты через view_capture.
- Бонус: in-world проверка архива HUD — Nobara выбрана, хаммер в руке, полосы способностей над хотбаром НЕТ, панелей/имён у мобов НЕТ (см. feat/archive-combat-hud).
- Грабли: player_uuid меняется каждый запуск (offline-профиль) → сначала entity_query @a; run/config/minecraft_fabric_mcp/config.json локально = auth_required:false + log_level:debug.

## 2026-09-09 — боевой HUD убран в архив (ветка feat/archive-combat-hud)

- По запросу «убрать в дальний ящик»: нижняя полоса способностей (AbilityHud) и Nobara target-HUD (имя цели/HP/ранг/гвозди) полностью отключены. 6 клиентских + 3 тестовых файла перенесены в `archive/combat-hud-v1/` (git mv — история сохранена); срезаны 2 регистрации: JujutsuModClient `ability_hud` и NobaraClientDefinition `nobara_target_hud` + `NobaraEspState.register()`.
- Восстановление без git-археологии: README архива (состав, точки регистрации, шаги). Старый glass-вид (как в jar 2026-08-21 = commit 5d95a0b) — снапшотом в `archive/combat-hud-v1/snapshot-glass-5d95a0b-2026-08-21/`.
- Важно: репозиторий уже содержал thin-bracket версию HUD (4268c4a, 2026-08-25), а игровой jar в инстансе был от 2026-08-21 (glass). Деплой пересобранного jar выполнен 2026-09-09 17:10, md5 источника и копии совпали.
- Проверки: qualityGate зелёный (~52 с), в jar архивных классов нет, runClient доходит до главного меню без ошибок инициализации.

## Деплой в игровой инстанс

- Игровой инстанс Minecraft: `D:/Games/instances/Jujutsu`; мод кладётся в `D:/Games/instances/Jujutsu/mods/`.
- Jar называется `jujutsumod-<version>.jar` (версия — `mod_version` в `gradle.properties`, сейчас 1.1.0). При деплое новый jar кладём рядом, а старый уводим в бэкап (`*.jar.bak-<дата>`): Fabric грузит любой `.jar`, поэтому двух версий мода в папке остаться не должно.
- Сборка: `./gradlew.bat assemble --no-daemon --max-workers=1 --no-watch-fs` → `build/libs/jujutsumod-<version>.jar` (в корне того worktree, где собираешь).
- После копирования сверять md5 источника и копии.
- Зависимости уже лежат в инстансе: `geckolib-fabric-1.21.8-5.2.2.jar`, `fabric-api-0.136.1+1.21.8.jar`. Новые зависимости мода требуют отдельного деплоя в ту же папку.
- `assemble` достаточно только для упаковки jar; словом «verified» владеет исключительно `./gradlew qualityGate`.

## Issue #21 slice 1 — Todo aimed swap GameTests (PR #61, ждёт мержа)

- Ветка `test/todo-aimed-swap-gametest` (worktree `.worktrees/todo-aimed-swap-gametest`), база `main` `7f9f5de`. PR #61 открыт, CI зелёный на финальном хеде `d4e5ec3` (runs 31107699908/31107702436), MERGEABLE — мерж по команде пользователя (squash).
- 5 коммитов: seam (SwapCommitTeleport, 229f8f3) → сценарии 1-2 (222f9d7) → сценарии 3-4 (3c32a6c) → docs (a77c843) → review fixes (d4e5ec3).
- Rule-of-four прошёл полностью: 4 скаута → 5-блочная план-спека → 4 воркера → 4 ревьюера (zero P0/P1, 3 P2 применены). Red proof: инверсия rollback-ассерта → runGameTest exit 1 + qualityGate BUILD FAILED, восстановлено.
- **Главная находка ревью**: vanilla `hasLineOfSight` меряет луч до ГЛАЗ цели (голем 1.4×2.7, глаза ~y+2.3) — первая арена сценария 3 рефузила на LOS-гейте резолвера (он идёт ДО префлайта), а не на blocked destination; плита поднята на y=4..6 (выше любой eye-line), pre-cast LOS-ассерт пинит точку отказа навсегда.
- Уроки: ServerPlayer.forceSetRotation в 1.21.8 пакетный (authoritative поля не пишет — aim в фикстурах через setYRot/setXRot/setYHeadRot); GameTest teardown-колбэки (tick-6/16) не бегут после fail — failure-path discard через asserted-флаг в finally; сид velocity/fallDistance перед капчей делает restore-ассерты дискриминирующими.

## Issue #43 — MCP-спайк ЗАВЕРШЁН (PR #62, ждёт CI после outage и мержа)

- Вердикт: **PASS, архитектура A (port/fork upstream)**, fallback B. Decision record: `docs/MCP_1_21_8_PORT_SPIKE.md` в ветке `spike/mcp-1.21.8-upstream-port` (worktree `.worktrees/mcp-port-spike`); `docs/research/` в этом репо запрещён аудитом — record живёт в `docs/`.
- PR #62 (не мержить); upstream fork: `grebeshok105/minecraft-java-fabric-mcp-server`, ветка `mc-1.21.8-target` (5 коммитов на `0caf461`: target → SSE hold-open → seam → jackson fields → review-hardening `dd8cfdd`). Наши коммиты: e170ee8 + bf3df86 + 6fbd081.
- Live-доказано (Windows, OMP 17.2.5, print-режим `omp -p` из probe-cwd с `.omp/mcp.json` type http): 105 тулов, `jujutsu_mod_status` → `{mod_version, selected_vessel: todo}` через production `CharacterSelectionManager`, bounded-мутация summon→despawn по UUID с откатом мира, view_capture 854×480 (реальный кадр), shutdown освобождает 8765/8766, auth 401 fail-closed.
- Ключевые ловушки 1.21.8 (проверены живьём): Mojang переименовал `ResourceLocation`→`Identifier` МЕЖДУ 1.21.8 и 1.21.11 (порт = stonecutter-условки `mc_gte_21_11`); **MC 1.21.8 несёт parent-classloader Jackson 2.13.4.2**, который затеняет nested 2.22 → `ObjectNode.properties()` (2.15+) падает, лечится `fields()`; закрытая DOMAIN_TO_CATEGORY-карта upstream реджектит незнакомые домены — seam требует `ToolProvider.domainCategories()`.
- Запуск спайк-сессии: `runClient -PmcpSpike -PmcpUpstreamJar=<jar>`; конфиги эндпоинтов в `run/config/minecraft_fabric_mcp/{config,client}.json`; mcpdev дремлет без пропертей, релизный jar чист (аудит расширен com/chapmanjw + io/modelcontextprotocol + jujutsu/mcpdev + mcp-tools).
- CI на ветке ждёт восстановления GitHub Actions (major outage 2026-08-06, оба рана убиты инфраструктурой): после восстановления `gh run rerun 31121633549` или дождаться авто-рана на 6fbd081.
- Next slice (в record): v0 L3-поверхность (~12–18 тулов) через seam + интерактивное воспроизведение aimed-swap сценария PR #61.

## Волна 2026-08-07: 6 PR замержены, всё в main

- #62 спайк MCP-моста на 1.21.8 (upstream port chapmanjw, 105+ тулов, SSE hold-open, Jackson fix, ToolProvider seam)
- #63 dev-control тулы + автономный вход (7 jujutsu_* тулов, quickPlay, prepareMcpSpikeRun)
- #64 ability-result contract (AbilityResult SUCCESS/HANDLED_FAILURE/UNHANDLED_FAILURE)
- #65 stone lifecycle GameTests (21 сценарий, 29/29 gametest'ов зелёные)
- #66 L3 completion (ticks_wait, rotation_set, fixture_list — 11 jujutsu тулов всего)
- #67 ability HUD (draggable, SDF/MSDF, все сосуды, hudSlots()/maxCooldownTicks() seam'ы)
- Ночной автономный смок всех 3 китов: 43 кейса, 0 реальных поломок (отчёт `.omp/smoke-run-1/report.md`)
- Ревью-волны: 4×4 ревьюера на #62/#63/#64/#67, все находки применены
- Урок: mcpdev не компилируется qualityGate — кросс-PR коллизии ловятся только jarMcpdev или live-прогоном
- Всё запушено, SESSION.md актуален; новые ветки резать от origin/main

## Чекауты и worktrees

- Рабочих чекаутов репо два: `D:/WorkFlow/Jujutsu Minecraft` (с пробелом; основной, с linked worktrees) и `D:/WorkFlow/jujutsu-minecraft` (с дефисом; его индексирует RAG). Не путать.
- В основном чекауте ветки фич/PR живут в linked worktrees под `.worktrees/<имя>`; имя папки может НЕ совпадать с именем ветки.
- Пример: ветка `codex/character-animation-overhaul` (head PR 53) живёт в `.worktrees/character-skin-animation`.
- Перед обновлением ветки — `git worktree list`: checked-out ветку нельзя двигать из главного чекаута, только изнутри её worktree (`git -C <worktree> merge --ff-only origin/<branch>`).

## Состояние PR-веток (2026-08-05)

- PR 52 и PR 53 (skin-backed third-person анимации) ВЛИТЫ в main squash-мержами: `9008905` (#52), `2e16933` (#53). Контент main идентичен проверенному `8bc292f` (diff пуст) — тому же, из которого собран задеплоенный в инстанс jar.
- Урок stacked-PR: squash-merge базового PR даёт add/add-конфликты у верхнего PR после ретаргета (merge-base остаётся до базовой ветки). Решение — rebase только собственных коммитов верхнего PR: `git rebase --onto main <вершина базовой ветки>` + force-with-lease; эквивалентность проверять пустым `git diff <старый head> <новый head>`.
- Ветки `codex/character-skin-animation` и `codex/character-animation-overhaul` на origin сохранены (не удалялись при мерже).
- Ручной смок в игре ПРОВЕДЁН пользователем 2026-08-05 (jar из `2e16933`/`8bc292f`): анимации приняты как приемлемые («пойдут»), не идеальные. Вердикт: заметный апгрейд качества third-person клипов требует авторинга в Blockbench (ручная правка animation.json исчерпала себя), направление отложено как потенциальное, задача не поставлена.

## Фича T3: Nobara target ESP + Mega Nail (2026-08-05)

- **PR #54 ВЛИТ в main** squash-мержем `3dccd56` (2026-08-05, смок раунд 4 принят пользователем: «нормально. доволен»); хендофф закрыт `fe2040b`. Ветка `feat/nobara-esp-and-mega-nail` и worktree `.worktrees/nobara-esp-meganail` удалены (worktree сносился вручную `rm -rf` + `git worktree prune` — `git worktree remove` падает на длинных путях loom-кэша). Jar в инстансе собран из main `3dccd56`, md5 `37d8ae47b945d6aef86129200b3c3234`. История фичи: дизайн `6881029`, код `bc6bddd`, ревью-фиксы `f73748f`, смок-1 `4374da4`, real-driver `2532828`, смок-3 `71ef31c`.
- Смок-хроника: раунд 1 отверг бейдж/взрыв-вместо-гвоздя/ловушку; раунд 2 вскрыл фиктивную «по-тиковую» периодику в рецептах (VFX-рецепт = one-shot per cue!) — вихрь переехал на клиентский тик entity, звук — на сервер, кольцо ловушки — на серверный пульс каждые 40 тиков; раунд 3: бейдж не рисовался из-за зеркальной матрицы (ваниль скейлит +X), ловушка сжата (гвозди 1.15 от центра, триггер-цилиндр 2.0, ловит любой Mob+игроков — голем не Enemy!), мега-кнокбек через setDeltaMovement+hurtMarked (knockback() зануляется KNOCKBACK_RESISTANCE голема), ESP-бейдж справа на уровне груди, синтезированные звуки (tools/synth_mega_sounds.py: riser 1.3s + jet blast; vorbis даёт транзиент-овершут ~×1.8 — рендерить с peak 0.5) + эскалирующая тряска пульс-cue intensity 2..5.
- Новый B Нобары — «мега-гвоздь» v2 (`ProjectJjkMegaNailRuntime` + `ProjectJjkNailEntity`): B спавнит НАСТОЯЩИЙ гвоздь-entity перед Нобарой (gather point), гвозди цели стягиваются направленными ENLARGE-потоками, 24 тика зарядки (`DATA_MEGA`/`DATA_MEGA_PROGRESS` synced, scale 0.6→2.6 через `megaRenderScale()` на entity — рендерер без новой vessel-ссылки, tripwire = 5), запуск ×1.3 по свежей позиции цели (≤48 блоков), flight timeout 60 тиков, импакт через колбеки (`onMegaNailImpact`/`onMegaNailTimeout`), PENDING-планировщик удалён. Null-tolerant к офлайн-кастеру. Ловушка: `DATA_TRAP` synced, постоянное зонное кольцо + усиленные placed/armed/collapse. ESP: `NobaraEspState`/`NobaraEspRanks` + бейджи в `ProjectJjkNailRenderer`, виден только локальной Нобаре; owner U…
- **2026-08-21 (feat/nobara-target-hud)**: ESP-накладка переехала из world-space billboard (ProjectJjkNailRenderer) в screen-space HUD (NobaraTargetHud contribution в VfxDirector + WorldToScreen-проекция); лидер-гвоздь и EspTargetData удалены, tripwire-пин рендерера сужен 5→3 (см. KNOWN_ISSUES E14); актуальная запись — в E14, не здесь.
- Урок контрактных тестов: delivery-радиусы VFX объявлять боксированным `Double` (примитив инлайнится javac и невидим байткод-сканеру `VfxCompletenessTest.productionDeliveryRadii`); live ids 36 (24 Нобары, +`mega_nail_charge`), wire-строки в `VfxCueTest`, счётчик age-aware вызовов в `ProjectSanityTest` = 50.

## Фича: Megumi shadow kit + Shadow Drop (2026-08-05, PR #55)

- **PR #55 ВЛИТ в main** squash-мержем `5719390` (title: «feat(megumi): shadow kit — Trap, Move, Drop (B / Shift+B / V)»); хендофф закрыт `3c71ce6` по конвенции «docs: close out SESSION.md after PR #NN merge». Ветки `feat/megumi-shadow-kit` / `feat/megumi-shadow-drop` на origin и их worktrees (`.worktrees/megumi-shadow-kit`, `.worktrees/megumi-shadow-drop`) НЕ удалены. Jar в инстансе md5 `a96ce9580f04f76ecfaaa05aecfc65cf` — сорсы идентичны main `5719390`.
- Кит Мегуми: `B` Shadow Trap (пул r2.6, грип `megumi_shadow_grip` SPEED −75% / JUMP ×0 с 8-тиковым рефрешем, кд 200); `Shift+B` Shadow Move — одна техника, три режима (tap с целью = backstep за спину, tap в поверхность = свободный шаг, hold >6 тиков = submerge ≤50 тиков; кд 120 tap / 200 hold); `V` Shadow Drop (зона r1.2 в 4 блоках над головой, следует за целью, телеграф 20 тиков, кд 60, залп 1–3 блоков внутри скаттера 0.9, веса near-flat 30/25/25/20 sand/gravel/clay/anvil). Wire: `SECONDARY_SNEAK_HOLD(6)`/`SECONDARY_SNEAK_RELEASE(7)`/`TERTIARY(8)` appended; release-слот без кд.
- Дайв-стек: `ShadowBodySink` (клиентский кэш sink/emerge прогресса; 3-арг API — рецепты передают длительности, кэш vessel-нейтрален; TTL fail-open; читатели семплируют по `frameTime = gameTime + partialTick`) + `HiddenBodyRenderGate` + `VfxCameraChannel.diveOffsetBlocks/diveFadeAlpha(partialTick)` + `MegumiShadowDiveHud`. Трап-семейство пулов = void-black (alpha 255 всю жизнь, радиус анимируется), закрытие растворяется 255→0 smoothstep; декоративный пул собак сохраняет свой fade.
- Смок-раунд 1 (фиксы `aef9b5c`): «ступенчатое» погружение → partial-tick семплинг всюду; резкое исчезание пула → close-fade; «дроп слаб/редок» → кд 160→60 + залп 1–3.
- Ревью-процесс волны: 4 независимых ревьюера (server core / vfx client / dive presentation / tests+docs+seam) → консолидированная спека `docs/MEGUMI_SHADOW_DROP_REVIEW.md` (R1–R7, каждый с готовым фиксом; сначала review-first без правок по требованию пользователя, затем отдельный проход применения). Все R1–R7 применены в `6487694`, статус спеки CURRENT/applied.
- **Урок R1 (главный)**: `FallingBlockEntity.fall(level, pos, state)` в 1.21.8 БЕЗУСЛОВНО делает `level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3)` = AIR для сухого пейлоада (проверено `javap -c` по маппированному jar из loom-кэша, offsets 60–73). Синтетический спавн падающего блока обязан гардить позицию: `spawnPosFor` walk-down от hover-точки к голове цели, первая позиция с `canBeReplaced() && getFluidState().isEmpty()`; запечатанная колонна = скип блока (не удаление потолка). Бонус: в пещере залп спавнится ниже потолка и работает.
- **Урок R2**: клиентский кэш анимационного прогресса при прерывании обязан передавать текущую глубину (backdate start: `start = t − round((1−depth)·ticks)`), иначе тело скачет на полную глубину/поверхность. Сделано в обе стороны (`beginEmerge` из SINKING, `beginSink` из EMERGING) + 5 JUnit-пинов (реордер-риппл, late-join, re-dive, оба хендоффа).
- Тест-инфра: `ShadowBodySinkTestClock` — public фикстура в тестовом сорс-сете пакета `jujutsu.mod.client.render`, пробрасывает package-private `setClockForTests` для тестов из других пакетов (dive-блок `VfxCameraChannelTest` больше не гоняется с wall-clock TTL).
- **Урок сборки**: `loom_version` запинен на релизный `1.17.17`. Маркер `1.17-SNAPSHOT` (публикация 2026-08-05 19:51Z) указал на несуществующий `fabric-loom:1.17.18` → CI падал на конфигурации, локально спасал кэш старого снапшота. Снапшоты билд-плагинов в CI = флейк по чужому расписанию публикации; пинить релизы.
- Конвенция мержа репо: squash-merge (`gh pr merge N --squash`), title PR становится заголовком сквоша; перед мержем обновить title/body PR под финальный контент.
- Next: ручной смок-раунд 2 из main — чеклист 23–31 в SESSION.md + два ревью-сценария (каст `V` под потолком: ничего не удаляется, блоки из-под потолка; урон во время синка: всплытие с текущей глубины).

## Инфраструктура: GameTest (issue #42, 2026-08-06)

- **Stage A (PR #59) ВЛИТ** squash-мержем `9926f7d`: изолированный тест-мод `jujutsumod-gametest` (source set `src/gametest`), серверные канарейки, задача `runGameTest` (Loom цепляет её к `check` → внутри qualityGate), JUnit XML `build/test-results/gametest/junit.xml`, `auditReleaseJarIsolation` в гейте.
- **Stage B — PR #60 ВЛИТ в main** squash-мержем `fa86c4c` (2026-08-06); хендофф закрыт `7f9f5de` по конвенции «docs: close out SESSION.md after PR #NN merge». Внутри сквоша полная волна правила четырёх (4 скаута → план 5 блоков → 4 воркера → 4 ревьюера: 0 P0/P1, 1 P2 — точность длительностей в evidence, 12 P3; фиксы применены). После мержа штатный workflow_dispatch с main прошёл: run 31098479281 SUCCESS (~2m18s) — Xvfb-lane 2/2 зелёных на ubuntu-24.04. Ветка `test/client-gametest-foundation` и её worktree сохранены (не удалялись): официальный `fabric-client-gametest` entrypoint в том же тест-моде; задача **`runClientGameTest`** (группа fabric, к check НЕ цепляется — клиентская lane вне qualityGate by construction, доказано dry-run); канарейки `ClientLoadCanaryTest` + `ClientObservationCanaryTest` (мир через `worldBuilder().create()`, NoAi-свинья по UUID, скриншот 854x480 `0000_observation_canary.png`); фильтр `fabric.client.gametest.modid`; audit расширен на client-gametest API префиксы.
- Факты API (source-verified, fabric-api 0.136.1): machine-readable отчёта для client gametests НЕТ (только exit code + логи); `ClientLevel#getEntities()` protected в 1.21.8 named — публичный путь `Level#getEntity(UUID)`; `Level#getHeightmapPos` удалён — `Level#getHeight(Types,x,z)`; скриншоты в `<runDir>/screenshots/%04d_<name>.png`, API сам ждёт рендер.
- Стабильность: 5/5 зелёных локальных прогонов (26-31 с, RX 6700 XT), 0 утечек KnotClient. Red proofs: инверсия клиентской ассерции → exit 1 с диагностикой; маркер в jar → audit FAILED.
- Xvfb: workflow_dispatch регистрируется ТОЛЬКО с default branch (нельзя дёрнуть до мержа); реальный Linux-прогон доказан с временной ветки: run 31094422326, ubuntu-24.04+Xvfb, SUCCESS 2m31s (Loom сам оборачивает в xvfb-run при наличии xvfb). Lane manual + non-required.

## Фича: Megumi shikigami — Nue (B5 правила 4, 2026-09-11, ветка feat/megumi-shikigami)

- Задача: оставшиеся шикигами Мегуми (Nue → Toad → Rabbit Escape → Max Elephant), строго последовательно, на существующей системе Ten Shadows; ассеты — Sorcery Age ten-shadows (разрешение автора), план `.superpowers/rule-of-four/megumi-shikigami/implementation-plan.md` (сначала 3 ревью плана, 48 находок разобраны).
- B5 (фундамент + Nue) сделан main-ом: `MegumiShikigami`/`Selection`/`Pack`/`PresentationPolicy`/`Profile`/`FriendlyFire`/`SwapPolicy`/`SpawnPlacement` + `MegumiShikigamiEntity` (общая база) + `MegumiShikigamiRuntime` (tryPrimary/trySic/tryCycle/teardown/packView) + Nue `Policy/Entity/Brain` + клиентский стек (`MegumiShikigamiRenderState` с собственным бэк-бэгом, `MegumiShikigamiAnimationPolicy`, `MegumiNueGeoAnimatable/Model/Renderer`) + VFX-ids/рецепты + lang + mcpdev-расширение (`megumi.shikigami` в state_get, шаги `megumi_shikigami_teardown`/`selection_clear` в fixture_reset).
- Единственная правка dog-файла: `TeardownReason.SWAPPED` (нулевой кулдаун) + расширение условия recall-перехода в `MegumiSummonRuntime`.
- **Урок (найден только в игре)**: проверка попадания дайва считалась по «нога-к-ногам» (`Entity.distanceTo`), а сам дайв ведёт к глазам цели → летун зависал в одном корпусе над целью и НИКОГДА не бил. Фикс: `MegumiNuePolicy.impactReachedSq(target.getBoundingBox().distanceToSqr(nue.position()))`. Мораль: для летающих атакующих дистанцию попадания считать от ХИТБОКСА цели, а не от её позиции.
- **Урок (lорк лейна)**: перекомпиляция мода при живом `runClient` (в т.ч. параллельным gradle-таском воркера) рушит классы под клиентом — `NoClassDefFoundError` в mcpdev-тулах. Держать один gradle-поток: либо лейн, либо сборка; воркеров просить не запускать gradle во время игровой проверки.
- **Урок (SIC и фазы)**: `trySic` отдаёт команду только телам в фазе ACTIVE → суммон и SIC нельзя звать в одном окне материализации (16 тиков), иначе команда «проглатывается» (в инструменте это выглядит как routed:true без эффекта).
- Замеры в игре: обычный дайв 4.92 урона (5.0 × броневые ~2% снятия), промокший зомби 7.38 (=×1.5) + SLOWNESS 60 тиков; recall 240, смерть якоря 400, своп из собак — PRIMARY 0 и пак собак снят; регрессия собак (суммон + SIC-урон) зелёная.
