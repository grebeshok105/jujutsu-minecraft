# ULTRA AUDIT — индекс отчётов (2026-08-27)

> Всего срезов: 50
> Успешно отчитались: 26/50
> Упало: 24
> Папка: `audit/ultra-2026-08-27/`
> Каждый файл — итоговый отчёт одного агента, без суммаризации (как просил хозяин).

## Срезы

- [01-vessel-seam-contract.md](./01-vessel-seam-contract.md) — Vessel Seam: Проверь Vessel Seam контракт: JujutsuCharacter enum, JujutsuCharacters registry, JujutsuCharacterClients, отсутствие if/
- [02-registry-bindings.md](./02-registry-bindings.md) — Registries: Аудит всех registry: JujutsuCharacters, JujutsuCharacterClients, JujutsuItems, JujutsuEntities, JujutsuParticles, Jujuts
- [03-character-definitions-architecture.md](./03-character-definitions-architecture.md) — Char Definitions: Аудит архитектуры CharacterDefinition и CharacterClientDefinition: интерфейсы, serverHooks/clientHooks, как vessel бинди
- [04-nobara-kit.md](./04-nobara-kit.md) — Nobara Kit: Глубокий аудит Nobara: nail система (EmbeddedNailRegistry, 30 cap, 1200 TTL), Hairpin (R), Mega Nail (B), Self Resonance
- [05-todo-kit.md](./05-todo-kit.md) — Todo Kit: Глубокий аудит Todo: Boogie Woogie (R 20 блоков 60т), Fake Clap (Shift+R 20т), Pair Swap (B 100т), Triple Cyclic Swap (S
- [06-megumi-kit.md](./06-megumi-kit.md) — Megumi Kit: Глубокий аудит Megumi: Divine Dogs (2 волка 60 HP, pounce 3-8 блоков, 80т кд), summon/recall (240т/600т), Sic (Shift+R 3
- [07-none-vessel.md](./07-none-vessel.md) — None Vessel: Аудит vessel None: как реализован пустой сосуд, выбор через SelectCharacterPayload, персистентность через Fabric Data At
- [08-vfx-core-director.md](./08-vfx-core-director.md) — VFX Core: Аудит VFX Core: VfxCue → VfxDirector → recipes → channels. Проверь соблюдение контракта (server-confirmed → cue → direct
- [09-vfx-recipes-nobara.md](./09-vfx-recipes-nobara.md) — VFX Nobara: Аудит Nobara VFX: NobaraVfxIds, NobaraVfxRecipes, регистрация из ClientDefinition, визуальная читаемость, цвета/тайминги
- [10-vfx-recipes-todo-megumi.md](./10-vfx-recipes-todo-megumi.md) — VFX Todo+Megumi: Аудит Todo и Megumi VFX: их VfxIds/Recipes, stone visuals, swap afterimages, shadow pools (void-black), Divine Dogs summ
- [11-networking-payloads.md](./11-networking-payloads.md) — Networking: Аудит сети: CharacterAbilityPayload, SelectCharacterPayload, CharacterAbility (wire ids 0-9, append-only), CharacterAbil
- [12-server-authority.md](./12-server-authority.md) — Server Authority: Аудит сервер-авторитета: вся геймплей-логика на logical server, клиент только косметика, валидация кастов (кулдауны, sta
- [13-todo-stone-entity.md](./13-todo-stone-entity.md) — Stone Entity: Аудит todo_stone entity: один камень на Todo, полёт 0.23 без гравитации, игнор энтити, терминации (lifetime 100, collisi
- [14-todo-swap-momentum.md](./14-todo-swap-momentum.md) — Swap Momentum: Аудит TodoSwapMomentumRuntime и BoogieWoogie runtimes: префлайт, findSafeDestination STRICT/SOFT, rollback при mid-commi
- [15-divine-dogs-lifecycle.md](./15-divine-dogs-lifecycle.md) — Divine Dogs: Аудит Divine Dogs lifecycle: owner UUID + token идентификация, one guarded teardown, reconcile живого сиблинга, ликовани
- [16-megumi-shadow-abilities.md](./16-megumi-shadow-abilities.md) — Megumi Shadow: Аудит Megumi shadow способностей: trap pool (8т эффект 75% slow), Shadow Move 3 режима (target-behind, surface step, dee
- [17-embedded-nail-registry.md](./17-embedded-nail-registry.md) — Nail Registry: Аудит EmbeddedNailRegistry: 1200т TTL, 30 cap, owner index, dedup агрегация NobaraEspState, leader nail удаление, утечки
- [18-input-keybinds-routers.md](./18-input-keybinds-routers.md) — Input Routers: Аудит ввода: keybinds R/B/V/N + sneak модификаторы, hold gesture второй техники (6т threshold, SECONDARY_SNEAK_HOLD/RELE
- [19-clickgui-menu.md](./19-clickgui-menu.md) — ClickGui: Аудит ClickGui (Key N): Characters sidebar, Soon placeholders, drag header/middle-mouse, session-only позиция, crosshair
- [20-client-renderers.md](./20-client-renderers.md) — Renderers: Аудит рендеров: GeckoLib replaced-player renderers, CharacterGeoRenderers dispatch via mixin, CharacterPlayerGeoRenderer
- [21-hud-target-esp.md](./21-hud-target-esp.md) — HUD ESP: Аудит HUD: NobaraTargetHud (bracket, divider, segmented HP 10, nail_icon, rank token, CHEST_FRACTION 0.60), MegumiShadow
- [22-sounds-particles.md](./22-sounds-particles.md) — Sounds Particles: Аудит звуков/частиц: OGG Vorbis, регистрация, VFX vs entity renderer persistent visuals, custome particle vs vanilla, от
- [23-registry-items-entities.md](./23-registry-items-entities.md) — Items Entities: Аудит JujutsuItems/JujutsuEntities: vessel items биндятся корректно vs seam, Todo без starter loadout, stone entity regi
- [24-mixins-safety.md](./24-mixins-safety.md) — Mixins: Аудит миксинов: только когда Fabric API недостаточно, предпочитай WrapOperation, @Unique, узкий scope, документированнос
- [25-build-gradle-qualitygate.md](./25-build-gradle-qualitygate.md) — Build Gate: Аудит сборки: Gradle 1.21.8 Java 21, ./gradlew qualityGate = check + auditDocumentation + assertions audit, auditRelease
- [26-tests-gametest.md](./26-tests-gametest.md) — Tests: Аудит тестов: JUnit 5 с fabric-loader-junit, JavaExec верификации, GameTest (runGameTest neutral canaries, client-gamete
- [27-docs-codex-known-issues.md](./27-docs-codex-known-issues.md) — Docs Codex: Аудит документации: AGENTS.md vs SESSION.md vs README vs Codex MOC vs KNOWN_ISSUES.md авторитет, audit_docs.py, current 
- [28-provenance-thirdparty.md](./28-provenance-thirdparty.md) — Provenance: Аудит PROVENANCE.md и THIRD_PARTY_NOTICES.md: ProjectJJK placeholder policy, Rich-Modern provenance вопрос, лицензия, за
- [29-dependencies.md](./29-dependencies.md) — Dependencies: Аудит зависимостей: GeckoLib, Fabric API justification (visual quality, stable animation, speed), avoid small utils, req
- [30-world-border-safety.md](./30-world-border-safety.md) — World Safety: Аудит безопасности позиций: findSafeDestination (world bounds, chunk load, border margin 0.05, solid collision, нет floo
- [31-combat-damage.md](./31-combat-damage.md) — Combat: Аудит боя: урон, stagger (Todo 0.5x, Black Flash 14т), Black Flash 10% 1.75x, attribute modifiers, hit-stop Resonance, к
- [32-shadow-sink-camera.md](./32-shadow-sink-camera.md) — Shadow Sink: Аудит ShadowBodySink и камеры: sink 1.9 блока ease, VfxCameraChannel.diveOffsetBlocks(partialTick), continuous между тик
- [33-attributes-effects.md](./33-attributes-effects.md) — Attributes: Аудит атрибутов: MOVEMENT_SPEED -75% ADD_MULTIPLIED_TOTAL, JUMP_STRENGTH 0, todo_swap_momentum ATTACK_DAMAGE, Todo melee
- [34-persistence.md](./34-persistence.md) — Persistence: Аудит персистентности: Fabric Data Attachment API для vessel selection, стартер клейм записывается но не читается (E12),
- [35-observability.md](./35-observability.md) — Observability: Аудит наблюдаемости: логи ошибок (triple swap rollback), метрики, rate limits, database is locked handling, логирование 
- [36-performance.md](./36-performance.md) — Performance: Аудит перфоманса: never allocate/copy/compute avoidably, compiled code, tick handlers (trap 8т apply, leash 10т check), 
- [37-security-anticheat.md](./37-security-anticheat.md) — Security: Аудит безопасности: bearer token MC_MCP_TOKEN, MCP dev-lane (8765/8766), валидация дистанций (Boogie 20, stone swap 32),
- [38-i18n.md](./38-i18n.md) — i18n: Аудит локализации: user-visible текст через Component.translatable, отсутствие хардкода строк в UI, языковые файлы, fall
- [39-assets-models.md](./39-assets-models.md) — Assets: Аудит ассетов: текстуры, модели, анимации GeckoLib, nail_icon.png 16x16, SDF/MSDF, asset policy (source outside runtime 
- [40-ui-rendering.md](./40-ui-rendering.md) — UI Rendering: Аудит UI рендеринга: SdfRenderer, Render2D adapters, MSDF, ClickGuiTheme easing + vessel accent, vanilla HUD composite-o
- [41-package-structure.md](./41-package-structure.md) — Package Structure: Аудит структуры пакетов: jujutsu.mod.character/*, vfx, network, registry, client/*, отсутствие пустых пакетов, модульнос
- [42-git-workflow.md](./42-git-workflow.md) — Git: Аудит git workflow: изолированные worktrees, commit every meaningful change, conventional commits (feat/fix/chore), smal
- [43-constants-profiles.md](./43-constants-profiles.md) — Constants: Аудит констант: TodoProfile source of truth, Megumi cooldowns (summon 0/recall240/final600, Sic30, trap200, move120/200,
- [44-cross-vessel-isolation.md](./44-cross-vessel-isolation.md) — Isolation: Аудит изоляции сосудов: отсутствие кросс-влияния (Todo stone не мешает Megumi dogs, Nobara nails не тратятся другими), s
- [45-cleanup-lifecycle.md](./45-cleanup-lifecycle.md) — Cleanup: Аудит cleanup: death/respawn/vessel change/dimension/disconnect/server stop/chunk unload — stone vanish, dogs teardown, 
- [46-multiplayer-races.md](./46-multiplayer-races.md) — Multiplayer: Аудит мультиплеера: concurrency, race conditions, server-authoritative ticks, одновременные касты, pounce LOS, target se
- [47-file-limits-modularity.md](./47-file-limits-modularity.md) — File Limits: Аудит лимитов файлов: 500 soft / 1000 hard, god-file detection, check_file_sizes.py, механическая резка per-concern моду
- [48-magic-numbers.md](./48-magic-numbers.md) — Magic Numbers: Аудит magic numbers: поиск хардкода вместо констант профилей, дублирование чисел, необъяснённые 6т/8т/24т/1200т, баланс 
- [49-dead-code.md](./49-dead-code.md) — Dead Code: Аудит мёртвого кода: неиспользуемые символы, недостижимые ветки, забытые TODO, удалённые fallback (thrown-mark), canonic
- [50-product-roadmap.md](./50-product-roadmap.md) — Product: Аудит продукта: quality over quantity, distinct VFX language, defensive/mobility per character, readable counterplay, no

## Статус волн
- WAVE1: 25/25 ok
- WAVE2: 1/25 ok
- RETRY: 0/24 ok, still failed: 26-tests-gametest, 27-docs-codex-known-issues, 28-provenance-thirdparty, 29-dependencies, 30-world-border-safety, 31-combat-damage, 32-shadow-sink-camera, 33-attributes-effects, 34-persistence, 35-observability, 36-performance, 37-security-anticheat, 38-i18n, 39-assets-models, 40-ui-rendering, 41-package-structure, 43-constants-profiles, 44-cross-vessel-isolation, 45-cleanup-lifecycle, 46-multiplayer-races, 47-file-limits-modularity, 48-magic-numbers, 49-dead-code, 50-product-roadmap

## Как читать
Открой любой `.md` в папке — это полный отчёт агента по своему срезу. Индекс — только навигация, не суммаризация.

AUDIT_RUN: 2026-08-27 ultra 50-slice
