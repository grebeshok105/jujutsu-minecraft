# Full Audit Report — Jujutsu Minecraft

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Дата: 2026-07-19
Коммит: 054a9a5 (main)
Аудиторы: 6 параллельных субагентов (server code, client code, VFX core, docs compliance, tests/build, assets)

---

## Executive Summary

| Область | CRITICAL | HIGH | MEDIUM | LOW | Всего |
|---------|----------|------|--------|-----|-------|
| Server-side код | 3 | 6 | 11 | 8 | 28 |
| Client-side код | 2 | 5 | 8 | 5 | 20 |
| VFX-ядро | 0 | 1 | 3 | 5 | 9 |
| Документация vs код | 4 | 14 | 9 | 1 | 28 |
| Тесты и сборка | 0 | 2 | 3 | 4 | 9 |
| Ассеты и ресурсы | 1 | 1 | 3 | 3 | 8 |
| **ИТОГО** | **10** | **29** | **37** | **26** | **102** |

Сборка: **BUILD SUCCESSFUL** (39s, 18 тестов прошли).

---

## CRITICAL (10) — Чинить немедленно

### C1. VfxDeltaTrackerMixin глобально ломает тайминг клиента
- `src/client/java/jujutsu/mod/client/mixin/VfxDeltaTrackerMixin.java:12-20`
- Умножает `getGameTimeDeltaTicks` и `getGameTimeDeltaPartialTick` на `timeScale()` для ВСЕХ потребителей: vanilla рендер, другие моды, интерполяция сущностей. При активации slow-mo замедлится весь клиент, а не только VFX.
- Дополнительно: `timeScale()` мутирует состояние внутри getter (non-idempotent).
- **Фикс:** удалить mixin. Применять dilation только внутри VFX-owned render paths.

### C2. VfxWorldChannel рендерит без matrix stack transform
- `src/client/java/jujutsu/mod/client/vfx/VfxWorldChannel.java:41-48, 228-237`
- Камера вычитается вручную, но PoseStack уже содержит camera transform → двойное смещение. Impact flash рендерятся в неверных мировых координатах.
- **Фикс:** использовать `context.matrixStack().last()` для вершин, убрать ручное вычитание камеры.

### C3. NailTrapRuntime.tryPlace возвращает true при failure
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java:63-74`
- Все failure-пути возвращают `true`. Будущие вызывающие стороны (cooldown, resource consumption) будут обмануты.
- **Фикс:** возвращать `false` на failure-путях.

### C4. ProjectJjkNailEntity: noSave() + 90 строк NBT save/load
- `src/main/java/jujutsu/mod/registry/JujutsuEntities.java:28` + `ProjectJjkNailEntity.java:335-424`
- `.noSave()` запрещает сериализацию. NBT-код мёртв. Либо баг (гвозди должны персиститься), либо dead code.
- **Фикс:** решить: убрать `.noSave()` или удалить NBT-методы.

### C5. ServerTimeDilation меняет глобальный tick rate сервера
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ServerTimeDilation.java:10-23`
- `server.tickRateManager().setTickRate(...)` замедляет ВЕСЬ сервер для всех игроков. Грифинг-вектор в мультиплеере. Restore-логика ломается при внешнем изменении rate.
- **Фикс:** заменить на per-player/per-area dilation (attribute modifiers, custom tick scheduler).

### C6. Документация: Entrypoints-and-lifecycle описывает 8 init-шагов, в коде 18
- `JujutsuMod.java:33-51` vs `jujutsumod-codebase-codex/02-architecture/Entrypoints-and-lifecycle.md`
- 10 регистраций не задокументированы (DataComponents, Effects, StrawDollRuntime, NailAnchorLifecycle, HammerCombat, ActionGuard, SelfResonance, NailTrap, ForcedBlackFlash, CurseLink cleanup).
- **Фикс:** переписать секцию.

### C7. Документация: Nobara-runtime-flow ссылается на несуществующий метод `detonateMarks`
- `jujutsumod-codebase-codex/03-systems/Nobara-runtime-flow.md`
- Реальный метод: `startMassHairpin` через `HairpinChainScheduler`.
- **Фикс:** переписать описание Boom-потока.

### C8. Документация: Nobara-overview не содержит NAIL_TRAP (Shift+B)
- `jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- Целая способность (NailTrapRuntime, 336 строк) отсутствует в input map.
- **Фикс:** добавить в overview + input map.

### C9. Документация: Claim-Source-Index говорит "20 IDs", в коде 25
- `jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md` vs `NobaraVfxIds.java:7-31`
- 5 undocumented IDs: NAIL_DEEPEN, NAIL_TRAP_PLACED, NAIL_TRAP_ARMED, NAIL_TRAP_COLLAPSE, NAIL_TRAP_IMPACT.
- **Фикс:** обновить count + line refs.

### C10. 409 ARR-ассетов ProjectJJK в runtime jar
- `textures/projectjjk/` (326), `sounds/projectjjk/` (42), `geo/projectjjk/` (18), `animations/projectjjk/` (11), `geckolib/*/projectjjk/` (2), `items/projectjjk_*` (2), `models/item/projectjjk_*` (2)
- All Rights Reserved. Публикация мода = юридический риск.
- **Фикс:** заменить на оригинальные ассеты или получить лицензию. До замены — не публиковать.

---

## HIGH (29) — Чинить в ближайшем спринте

### Server-side

| # | Файл | Проблема |
|---|------|----------|
| H1 | `ForcedBlackFlash.java:11` | Non-thread-safe `HashSet` (остальные используют ConcurrentHashMap) |
| H2 | `CharacterSelectionManager`, `CombatStagger`, `ProjectJjkNailMarks`, `NailAnchorLifecycle` | Static maps не очищаются на SERVER_STOPPING → memory leak |
| H3 | `NobaraHammerCombatRuntime`, `SelfResonanceRuntime`, `ProjectJjkStrawDollRuntime` | Per-player state не очищается на DISCONNECT |
| H4 | `ProjectJjkNailEntity.java:382,390,416` | Unguarded `UUID.fromString` в NBT deserialization → crash на corrupt data |
| H5 | `ProjectJjkRitualRuntime.java:486,492` | `ServerLevel` в long-lived tick-records → stale reference при unload dimension |
| H6 | `ProjectJjkHammerItem.java:26` | `use()` возвращает SUCCESS даже без действия → блокирует vanilla interactions |

### Client-side

| # | Файл | Проблема |
|---|------|----------|
| H7 | `CharacterSkinMixin.java:23-31` | Новый `PlayerSkin` каждый вызов (несколько раз в кадр на игрока) → GC pressure |
| H8 | `ClientCharacterSelectionManager.java:14-15` | Maps растут без очистки при join/leave игроков |
| H9 | `NobaraLivingEntityRendererMixin.java:22-50` | Target = LivingEntityRenderer (ВСЕ сущности). Per-frame overhead для каждого моба |
| H10 | `VfxHudChannel.java:121-138` | Wall-clock time вместо game time → эффекты не паузятся, не slow-mo |
| H11 | `NobaraPlayerGeoRenderer.java:45-52` | Identity comparison для PoseStack restore → fragile при GC/update |

### VFX-ядро

| # | Файл | Проблема |
|---|------|----------|
| H12 | `NobaraVfxRecipes.java:136-138, 157-159, 337-339` | 3 рецепта вызывают `NobaraPlayerGeoAnimatable.triggerAction()` напрямую — нарушение VFX Core Contract (bypass channels) |

### Документация (14 HIGH)

| # | Проблема |
|---|----------|
| H13 | Entrypoints: client init step 1 перепутан (Straw Doll renderer, не nail) |
| H14 | Networking: все line refs off by +3 |
| H15 | Registries: 4 mixin не задокументированы (из 7 описаны 3) |
| H16 | Registries: `JujutsuEffects` и `RESONANCE_REMNANT_VISUAL` component отсутствуют |
| H17 | Nobara-overview: `HAIRPIN_ENLARGE`/`HAIRPIN_EXPLOSION` deprecated, но представлены как canonical |
| H18 | Nobara-overview: balance constants range `:27-58` не покрывает реальные `:32-76` |
| H19 | Nobara-overview: 5 balance constants не задокументированы (trap damage, depth multipliers, chain radius, momentum, directed damage) |
| H20 | Nobara-runtime-flow: source refs для Enlarge/Boom off by 5-50 lines |
| H21 | Nobara-runtime-flow: missing NailTrapRuntime, ForcedBlackFlash, JujutsuEffects в registration |
| H22 | Claim-Source-Index: mod_id line ref off (18 vs 29) |
| H23 | Claim-Source-Index: init order line refs off |
| H24 | Claim-Source-Index: test tasks count 9 vs actual 18 |
| H25 | Hairpin-effects.md: "14 central IDs" vs actual 25 |
| H26 | VFX-core.md: "23 age-aware calls" vs actual 39 (ProjectSanityTest:479) |

### Тесты/сборка

| # | Проблема |
|---|----------|
| H27 | `loom_version=1.17-SNAPSHOT` — snapshot dependency, может сломаться без предупреждения |
| H28 | Нет behavioral тестов для: networking codecs (кроме VfxCue), combat damage, character selection, nail physics |

### Ассеты

| # | Проблема |
|---|----------|
| H29 | 29 dead файлов в `geo/projectjjk/` + `animations/projectjjk/` — GeckoLib 5 НЕ читает эти пути |

---

## MEDIUM (37) — Планировать

### Server-side (11)

- M1: Unused import `UUID` in `BlackFlashFocus`
- M2: Dead method `preparedRow` in `ProjectJjkNobaraRuntime:287-303`
- M3: Deprecated constants `HAIRPIN_ENLARGE`/`HAIRPIN_EXPLOSION` используются в `JujutsuCommands`
- M4: `safeDirection(Vec3)` скопирован в 6 классов; `isHairpinNail` в 4; `countNails` в 3
- M5: `NailTrapRuntime.COLLAPSES` и `NailTrap.Registry` — plain HashMap без thread-safety docs
- M6: `DETONATE_DAMAGE_PER_MARK = 0.0f` → `detonateDamage(marks)` игнорирует параметр
- M7: Static `RandomSource` в `ProjectJjkRitualRuntime` — использовать `level.random`
- M8: `JujutsuEffects` регистрация в static initializer, не в `register()`
- M9: `ProjectJjkStrawDollItem` — static mutable `rendererFactory` в server-visible коде
- M10: `CurseLinkOptionsPayload.read` — no upper bound на list size
- M11: `NobaraActionGuard` блокирует ВСЕ entity attacks с hammer в руке

### Client-side (8)

- M12: VfxWorldChannel: ~200+ Vec3 allocs per flash per frame (5000+ при 48 flashes)
- M13: `ArrayList.remove(0)` — O(n) в VfxDirector, VfxCameraChannel, VfxWorldChannel
- M14: `NobaraVfxRecipes` создаёт DustParticleOptions per cue (не static constants)
- M15: `VfxQuality` импортирует `net.minecraft.server.level.ParticleStatus` в client code
- M16: `HairpinIgnitionTickParticle` мутирует `this.random` в tick()
- M17: `CharacterSelectScreen` создаёт `Component.translatable()` каждый кадр
- M18: `UiScreen` close animation может быть bypassed (pause/force-close)
- M19: `ProjectJjkNailRenderer` static mutable `ItemStack`

### VFX-ядро (3)

- M20: `VfxTimeChannel` — dead code, ни один рецепт не вызывает `triggerSlowMotion()`
- M21: 3 alias-рецепта (resonanceChannel→ritualBind, resonanceStrike→resonanceRelease, linkBind→remnantDrop) нарушают "distinct VFX language"
- M22: 64-instance cap не ограничивает реальную визуальную сложность (evicted instances' channel effects живут независимо)

### Документация (9)

- M23-M31: Line drift +3-10 по всем файлам; missing disconnect handler в Networking.md; reversed keybind/receiver order; stale guard counts; NailTrapRuntime partial VFX bypass не задокументирован

### Тесты/сборка (3)

- M32: `fabric-api: "*"` в fabric.mod.json — принимать любую версию опасно
- M33: 6/18 тестов используют source-string matching (ломаются при rename)
- M34: .gitignore не покрывает `lightrag.log`, `rag_storage/`, `.obsidian/`, `*.zip`

### Ассеты (3)

- M35: 44 ключа отсутствуют в `ru_ru.json`
- M36: 26 orphaned OGG в `sounds/projectjjk/` (не в sounds.json)
- M37: ~320+ orphaned текстур в `textures/projectjjk/` (не referenced)

---

## LOW (26) — Наблюдать / Чистить при случае

### Server-side (8)
- L1: `JujutsuCharacter.modelId` всегда "wide" — dead field
- L2: Redundant `ProjectJjk` prefix в package `projectjjk`
- L3: Magic numbers в particle/sound параметрах (сотни inline float)
- L4: `NobaraVfxIds` bit-packing без документации
- L5: `VfxTimeline` millis/nanos constants в server code (client-only)
- L6: `EMBEDDED_NAIL_AGE_TICKS = 0` → гвозди не expire (accumulation risk)
- L7: `isPickable() = true` без pickup logic
- L8: Debug particle commands ship в jar без config gate

### Client-side (5)
- L16: VfxWorldChannel дублирует VfxPalette constants locally
- L17: `NobaraHudState` — dead code
- L18: `UiRender.horizontalGradient` — O(width) draw calls
- L19: `JujutsuKeybinds` не consume attack click (hammer + vanilla melee одновременно)
- L20: `CurseLinkSelectionScreen` показывает raw ResourceLocation + truncated UUID

### VFX-ядро (5)
- VfxInstance без eviction callback
- Dual expiry paths (world channel vs director tick)
- `UNKNOWN_EFFECT_IDS` unbounded (malicious server)
- Wall-clock dependency в realtime channels (NTP/sleep risk)
- VFX-core.md scene table: 4 scenes vs actual 9+

### Тесты/сборка (4)
- No explicit `mavenCentral()` в top-level repos
- Gradle 10 deprecation warning
- Thin tests: VfxQualityTest (3 assertions), VfxTimeChannelTest (2 assertions)
- No test XML reports for CI

### Ассеты (3)
- 6 sounds без subtitles (accessibility)
- 1 orphaned model (`resonance_remnant.json` base)
- Source-assets scripts Windows-specific (harmless)

### Документация (1)
- VfxCue line ref off by 1

---

## VFX Core — Вердикт

**Архитектурно sound.** Cue→Director→Recipe→Channel pipeline реализован корректно:
- 64-instance cap enforced
- Unknown-ID warn-once
- Expired cue rejection
- Late-cue age offset
- Opening-beat suppression
- Quality degradation (ALL/DECREASED/MINIMAL)
- Disconnect cleanup
- Level-change cleanup
- Anchor resolution с fallback
- Post-process graceful degradation
- Recipe registration ordering correct
- Duplicate recipe rejection
- Capability-gated sends
- Thread safety (client main thread only)

**Нарушения:**
1. 3 рецепта bypass channels → GeckoLib animation trigger напрямую
2. VfxTimeChannel — dead infrastructure + опасный global mixin
3. 3 alias-рецепта вместо distinct VFX language
4. Документация отстаёт на 11 IDs и 5+ scenes

---

## Тесты — Вердикт

18 тестов, все проходят. Покрытие:
- **Хорошее:** VfxTimeline, VfxAnchorResolver, VfxCue, HairpinChain, NailAnchor, NailTrap, RitualPolicy, RemnantProgress, ServerTimeDilation, NobaraProfile (balance snapshot)
- **Слабое:** VfxQuality (3 assertions), VfxTimeChannel (2 assertions)
- **Отсутствует:** networking codecs (кроме VfxCue), combat damage pipeline, character selection, nail entity physics, GeckoLib integration
- **Антипаттерн:** 6 тестов читают .java файлы и assert-ят строки (architecture guardrails, не behavioral tests)

---

## Приоритетный план действий

### Немедленно (P0)
1. Удалить `VfxDeltaTrackerMixin` или scope-ить dilation только в VFX render paths
2. Починить matrix stack в `VfxWorldChannel`
3. Решить `noSave()` vs NBT для nail entity
4. Заменить `ServerTimeDilation` на безопасную альтернативу
5. Обновить документацию: init order, NailTrap, detonateMarks→startMassHairpin, ID count

### Ближайший спринт (P1)
6. Static map cleanup на SERVER_STOPPING + DISCONNECT
7. Guard UUID.fromString в NBT deserialization
8. Store ResourceKey<Level> вместо ServerLevel в tick-records
9. Cache PlayerSkin в CharacterSkinMixin
10. Retarget NobaraLivingEntityRendererMixin → PlayerRenderer
11. Pin loom_version=1.17.16
12. fabric-api >= 0.136.1 в fabric.mod.json
13. Удалить dead geo/animations/projectjjk (29 файлов)
14. Обновить .gitignore

### Среднесрочно (P2)
15. Заменить/изолировать 409 ARR-ассетов
16. Behavioral тесты для networking + combat
17. Персистентность выбора персонажа
18. VFX late-join / broadcast fix
19. ru_ru localization (44 ключа)
20. Удалить orphaned OGG/текстуры

---

## Файлы, использованные при аудите

**Obsidian vault (mcpvault):**
- `jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- `jujutsumod-codebase-codex/04-client-vfx/Hairpin-effects.md`
- `jujutsumod-codebase-codex/04-client-vfx/Nail-rendering.md`
- `jujutsumod-codebase-codex/02-architecture/Entrypoints-and-lifecycle.md`
- `jujutsumod-codebase-codex/02-architecture/Networking.md`
- `jujutsumod-codebase-codex/02-architecture/Registries.md`
- `jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- `jujutsumod-codebase-codex/03-systems/Nobara-runtime-flow.md`
- `jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md`
- `jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md`

**Код:** 65 server-side .java, 52 client-side .java, 18 test .java, build.gradle, gradle.properties, fabric.mod.json, jujutsumod.client.mixins.json, все resource JSON.

**Команда верификации:** `gradlew.bat build --no-daemon -x test` → BUILD SUCCESSFUL (39s).

---

## Review Corrections (peer review 2026-07-19)

Ревьюер проверил аудит по коду (коммит аудита: 054a9a5). Вердикт: аудит по фактам в основном честный, но severity раздут, часть CRITICAL — док-дрифт или latent-риск, C2 ошибочен.

### Скорректированные severity

| # | Было | Стало | Причина |
|---|------|-------|---------|
| C1 VfxDeltaTrackerMixin | CRITICAL | **HIGH (latent)** | `triggerSlowMotion()` нигде не вызывается, `timeScale()` всегда 1.0. Landmine, не живой баг. Чинить если/когда slow-mo включат. |
| C2 VfxWorldChannel matrix | CRITICAL | **UNVERIFIED / снять** | `addVertex(x,y,z)` без PoseStack — стандартный camera-relative паттерн. «Двойное смещение через PoseStack» технически неверно. Нужен визуальный чек, не слепой фикс. |
| C3 tryPlace → true | CRITICAL | **HIGH** | API-баг реальный, но сейчас cooldown/resource по return value не жрёт, `fail()` пишет сообщения. Не «горит прод». |
| C4 noSave + NBT | CRITICAL | **MEDIUM** | Inconsistency / dead code, не crash. Решить: убрать `.noSave()` или удалить NBT-методы. |
| C5 ServerTimeDilation | CRITICAL | **CRITICAL (подтверждён)** | Глобальный tick rate сервера — реальный гриф/лаг-вектор в мультиплеере. |
| C6-C9 docs | CRITICAL | **MEDIUM (docs)** | Док-долг, не runtime. Уже закрыты коммитом 0c5cc0f. |
| C10 ARR | CRITICAL | **CRITICAL (подтверждён)** | Юрриск при публикации. Не публиковать без замены/лицензии. |
| H1 HashSet | HIGH | **LOW** | Minecraft server thread, cleanup на DISCONNECT/STOPPING есть. ConcurrentHashMap «для красоты». |
| H2 static maps | HIGH | **MEDIUM** | NailAnchorLifecycle чистит на STOPPING. CharacterSelectionManager чистится на DISCONNECT. CombatStagger/NailMarks — слабее, но не «без cleanup». |
| H9 LivingEntityRenderer mixin | HIGH | **MEDIUM** | Heavy path только для PlayerRenderState + Nobara. Per-frame cost — cheap early-return. |

### Итоговый практический приоритет

**P0 (реально чинить в коде):**
1. C5: ServerTimeDilation — не глобальный tick rate
2. C3: tryPlace → false на failure
3. C1: не трогать DeltaTracker глобально (даже если сейчас idle) — удалить или scope-ить
4. C10: не публиковать jar с ARR ProjectJJK без замены/лицензии

**Не срочно / не слепо:**
- C2: сначала визуально посмотреть impact flash в игре
- C6-C9: закрыты (docs sync 0c5cc0f)
- H1: можно игнорить

### Вывод

Аудит — честное review с завышенным CRITICAL и парой ошибочных/раздутых claims (C2, C1 как «активная поломка»). Не «102 критических дыры». Живой риск: global server tick dilation, tryPlace boolean, ARR assets, latent global client time mixin.
