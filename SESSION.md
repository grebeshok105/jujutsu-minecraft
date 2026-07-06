# Сессия 2026-07-06 — Cutoff

## Проект

- `D:/WorkFlow/Jujutsu Minecraft`
- Fabric `1.21.8`, Java `21`, Mojang mappings
- Mod id: `jujutsumod`
- Рабочее дерево: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/brainstorming`
- Ветка: `chore/jujutsu-brainstorming`

## О чём сессия

Обсуждение и старт реализации первого vertical slice мода по *Магической битве / Jujutsu Kaisen*.
Первый персонаж — **Нобара**. Первый эффект — **Hairpin**.
Упор: **кинематографичный визуал**, кастомные частицы/звуки, “stylish shonen impact”.

## Главные решения

- Стартовая позиция по канону: почти канон, но Minecraft-native реализация.
- Первый slice: **Hairpin cinematic VFX**, не весь бой и не вся система.
- Путь: `VFX bible → standalone visual target → минимальный in-game Fabric prototype`.
- LibsFX / Universal FX (`D:/WorkFlow old/WorkFLow/TestimCodex/LibsFX`) — **не зависимость**.
  - Она под MC `1.21.1` / Satin `2.0.0`, наш мод под `1.21.8`.
  - Сборка LibsFX упала на скачивании Satin.
  - Статус: только референс идей/кода.
- Без Veil/Satin/Lodestone/ParticleAnimationLib как обязательных зависимостей.
- GeckoLib и Player Animation Library — опционально позже, не для первого slice.
- GitHub MCP сейчас сломан (`Bad credentials`), не использовать. Ресёрч через Tavily/прямые URL/local jars.

## Research

Изучены два файла:
- `C:/Users/KOMP1/Downloads/Research1.md`
- `C:/Users/KOMP1/Downloads/deep-research-report (4).md`

Общий вывод: для первого Hairpin строим свой маленький VFX-пайплайн на Fabric particles + тонкий renderer + HUD flash + custom sounds.

## Что сделано в коде

### Project setup
- Создан `AGENTS.md` с правилами проекта и workflow.
- Инициализирован git.
- Коммиты:
  - `f469ebc chore(project): add initial Fabric template and agent guide`
  - `d962cc2 chore(git): ignore local worktrees`
- Создан worktree `.worktrees/brainstorming`, ветка `chore/jujutsu-brainstorming`.
- Базовый билд проверен: `BUILD SUCCESSFUL` с Java 21.

### Дизайн/план
- Спека: `docs/superpowers/specs/2026-07-06-nobara-hairpin-cinematic-design.md`
  - 5 фаз Hairpin: Prep Freeze, Hammer Snap, Nail Ignition, Hairpin Bloom, Afterglow.
  - Палитра, звуковые слои, ассеты, client/server boundary.
- План: `docs/superpowers/plans/2026-07-06-nobara-hairpin-prototype.md`
- Коммиты:
  - `2745684 docs(design): define Nobara Hairpin cinematic slice`
  - `23b272e docs(design): document Universal FX decision`
  - `39aa731 docs(plan): add Nobara Hairpin prototype plan`

### Реализация
- `HairpinTimeline` — теструемая модель таймингов фаз.
  - `src/main/java/jujutsu/mod/fx/HairpinTimeline.java`
  - `src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java`
  - Тест проходит: `HairpinTimelineTest passed`.
  - Коммит: `eb789ed feat(fx): add Hairpin timeline model`
- Серверный триггер и payload:
  - `src/main/java/jujutsu/mod/network/HairpinFxPayload.java`
  - `src/main/java/jujutsu/mod/network/JujutsuNetworking.java`
  - `src/main/java/jujutsu/mod/command/JujutsuCommands.java`
  - Команда: `/jujutsu hairpin`
  - Отправляет один S2C payload только если клиент зарегистрировал приёмник.
  - Компилируется: `compileJava` OK.
  - Коммит: `21af244 feat(fx): add Hairpin trigger payload`

## Проверенные Fabric 1.21.8 API (локально через javap)

- `net.minecraft.network.protocol.common.custom.CustomPacketPayload`
- `net.minecraft.network.codec.StreamCodec`
- `net.minecraft.network.RegistryFriendlyByteBuf`
- `net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()`
- `net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload)`
- `ServerPlayNetworking.canSend(player, type)` — используем перед отправкой.
- `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver`
- `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT`
- HUD/рендер: `WorldRenderEvents`, `HudRenderCallback`, `HudElementRegistry` найдены в `fabric-rendering-v1`.

## Что не сделано

- Клиентский приёмник `HairpinFxPayload` пока не зарегистрирован.
- Нет `HairpinPlayback` / `HairpinPlaybackManager` на клиенте.
- Нет кастомных частиц, звуков, рендера, HUD flash.
- In-game smoke-тест не проводился.
- Standalone visual target не сделан.

## Ассеты, которые нужны от пользователя

Звуки (OGG Vorbis, желательно mono):
- `hairpin_prep_muffle.ogg`
- `hairpin_hammer_snap.ogg`
- `hairpin_nail_ignite.ogg`
- `hairpin_tracer_whip.ogg`
- `hairpin_bloom_core.ogg`
- `hairpin_shard_scatter.ogg`
- `hairpin_afterglow_crackle.ogg`
- `hairpin_screen_hit.ogg`

Частицы (PNG):
- `hairpin_spark.png`
- `hairpin_tracer_streak.png`
- `hairpin_shard_red.png`
- `hairpin_shock_ring.png`
- `hairpin_impact_flash_white.png`
- `hairpin_soot_wisp_black.png`
- `hairpin_residue_mist_redblack.png`

## Открытые вопросы

- Какой standalone visual target делаем: HTML/Three.js, Blender, или короткий референс-клип?
- Делаем ли сразу HUD flash + маленький world renderer, или сначала только частицы?

## Состояние

- Компиляция: OK.
- Тесты тайминга: OK.
- В игре не проверено.
- Worktree чистый, изменения закоммичены.