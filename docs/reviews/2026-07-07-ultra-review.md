# Jujutsu Minecraft - Ultra Review

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

## Executive Summary

- Направление проекта правильное: один vertical slice, Hairpin first, server semantic event + client VFX playback. Это реально годная база.
- Главный P0 риск: `main` не содержит текущий мод. Если кто-то билдит корень, он билдит почти пустой Fabric template. Нужно либо merge branch в main, либо явно зафиксировать рабочую ветку как источник истины.
- Текущий Hairpin визуально ещё не production. Он стал ближе к правильной форме, но частицы всё ещё могут читаться как тёмный дым из-за двойного затемнения: тёмные PNG * тёмный `rCol/gCol/bCol` * translucent blending.
- GLSL сейчас почти false progress. Файлы лежат в jar, но runtime-пути нет. Они полезны как source assets на потом, но не считать это активным shader pipeline.
- Архитектурно client/server разделение в целом соблюдено, но есть опасные мелочи: no-op mixins из template, wall-clock timing через `System.currentTimeMillis()`, много аллокаций в render/tick path, шумный info-logging.
- Payload идея хорошая, но payload пока прототипный. Для геймплея нужны source entity id, dimension, ability/profile id, server scheduled tick, anchor normals/entity attachment, version.
- Доки мощные, но расползаются. Есть research corpus, spec, implementation brief, handoff, prompts. Нужен один canonical Hairpin spec, иначе агент будет реализовывать разные версии одной и той же магии.
- До боёвки нельзя идти, пока Hairpin не доказан в игре видео/скриншотами: vanilla, Sodium, Iris no shaderpack, bad visibility, third-person observer, 5-10 repeated triggers.
- Следующий шаг: не новый framework. Сначала маленький VFX stabilization pass: merge/branch hygiene, удалить template mixins, поправить texture/tint, сделать deterministic visual test scene, снять видео.

---

## Stop / Keep / Change

### Stop

- Stop считать текущий VFX “готовым” без in-game видео. Compile и jar packaging не доказывают, что эффект читается.
- Stop плодить research без synthesis. Уже достаточно deep research. Теперь нужен canonical spec.
- Stop держать template mixins:
  - `src/main/java/jujutsu/mod/mixin/ExampleMixin.java`
  - `src/client/java/jujutsu/mod/client/mixin/ExampleClientMixin.java`
  - `jujutsumod.mixins.json`
  - `jujutsumod.client.mixins.json`

  Они ничего не делают, но лезут в `MinecraftServer.loadLevel` и `Minecraft.run`. Это плохой smell. Rule “не добавлять mixins без необходимости” уже нарушен мусором.

- Stop считать GLSL активной частью результата. `assets/jujutsumod/shaders/post/*.fsh/vsh` лежат в jar, но нет `post_effect` json, нет loader path, нет runtime toggle. Пока это эскизы.
- Stop тестировать частицы только server `sendParticles(... count > 0 ...)` командами. Этот путь рандомизирует позиции/скорости и легко превращает shaped VFX в “дымовую шашку”.

### Keep

- Keep один vertical slice. Nobara/Hairpin - правильный выбор.
- Keep semantic event + seed. `HairpinFxPayload` как идея правильная.
- Keep split source sets: `src/main` для common, `src/client` для rendering/hud/particles/client networking.
- Keep particle family breakdown:
  - mark stain
  - warn edge
  - compression mote
  - snap crack
  - burst residue
  - metal shard
  - ignition tick
- Keep world-space geometry direction. Hairpin без nail anchors и fracture ribbons будет просто частицами.
- Keep shader policy: эффект обязан работать без post shader. Shader позже как усилитель.

### Change

- Срочно объединить источник истины: либо merge `chore/jujutsu-brainstorming` в `main`, либо создать `develop` и работать только там.
- Заменить wall-clock timeline на game-time based model:
  - server sends `startGameTime`
  - client computes visual elapsed from `client.level.getGameTime()` + partial tick
  - для локальной плавности можно держать render interpolation, но authority time должен быть tick-based
- Разделить debug commands:
  - `/jujutsu hairpin` - semantic event playback
  - `/jujutsu hairpin particle <family>` - isolated particle preview
  - `/jujutsu hairpin stage <stage>` - visual staging
  - `/jujutsu hairpin perf <count>` - стресс
- Переделать tint/texture balance. Сейчас некоторые итоговые цвета после tint примерно такие:
  - `burst_residue` около `rgb(4, 0.6, 1.2)`
  - `mark_stain` около `rgb(9, 0.7, 1.5)`
  - `compression_mote` около `rgb(17, 1, 3)`

  Это почти выключенный пиксель.

- Сделать VFX profile data-driven хотя бы внутри Java. Не json registry сейчас, но один `HairpinVfxProfile` должен задавать colors, sizes, lifetimes, counts, phase timings, render layers.

---

## Critical Risks

### P0 - Main branch пустой относительно реального прогресса

**Почему риск:** корень проекта содержит template-like `JujutsuMod`, `JujutsuModClient`, README и template metadata. Реальный Hairpin живёт в `.worktrees/brainstorming`.

**Как проявится позже:** агент/CI/ты сам запустите build из корня и будете думать “а где Hairpin”. Потом начнётся шаманство с папками.

**Что делать сейчас:** merge `chore/jujutsu-brainstorming` в main или явно сделать `brainstorming` основной веткой. Затем удалить вложенные build/.gradle artifacts из zip flow.

### P0 - Hairpin может читаться как black smoke

**Почему риск:** текстуры тёмные, tint тёмный, render type translucent, формы residue мягкие. Серверные smoke-test команды ещё и спавнят пачки с spread/speed, что ломает shape language.

**Как проявится позже:** ты будешь накидывать больше particles и shaders, а эффект будет становиться не лучше, а грязнее.

**Что делать сейчас:** поднять value contrast у sprites. Оставить тёмный body, но края и cores должны быть читаемыми. `residue` должен быть flakes/streaks, не blob. Тестировать на stone, grass, night, fog.

### P1 - Wall-clock timeline ломает deterministic playback

**Почему риск:** `HairpinPlayback` стартует через `System.currentTimeMillis()` на клиенте. `startGameTime` в payload есть, но почти не используется. Seed не спасает, если клиенты получают событие в разное время и phase progression идёт по wall clock.

**Как проявится позже:** у разных игроков snap/residue будут слегка не совпадать. При лаге, паузе, low fps, alt-tab визуал поплывёт. Для PvP это прям яд.

**Что делать сейчас:** перейти на server tick schedule. Payload должен нести `startGameTime` / `detonationGameTime`. Клиент строит elapsed по world game time. Wall clock оставить только для HUD flash easing если прям надо.

### P1 - Template mixins без причины

**Почему риск:** required mixins подключены и инжектятся в server/client lifecycle. Сейчас no-op, но это всё равно surface area.

**Как проявится позже:** конфликт с другими модами, странные crash reports, лишняя сложность при Sodium/Iris/debug.

**Что делать сейчас:** удалить оба mixin class и оба mixin json из `fabric.mod.json`, пока реально не понадобится hook.

### P1 - VFX data размазана по коду

**Почему риск:** colors в particle classes, colors в renderer, colors в docs, colors в shader constants, colors в visual target. Уже есть несколько палитр.

**Как проявится позже:** один агент затемнит `warn_edge`, другой поднимет fuchsia в shader, третий поменяет `HairpinVisualProfile`, и Hairpin станет визуальной кашей.

**Что делать сейчас:** один canonical `HairpinVfxProfile` в Java + один canonical `docs/superpowers/specs/hairpin-vfx-canonical.md`.

### P1 - `HairpinWorldRenderer` пока debug geometry

**Почему риск:** `RenderType.lightning()` даёт быстрый прототип, но это не production material. Нет UV, нет texture breakup, нет explicit render phase policy, нет sorting strategy.

**Как проявится позже:** ribbons будут плоскими, слишком одинаковыми, странно светиться или плохо сортироваться с translucent terrain.

**Что делать сейчас:** оставить как временный layer, но назвать его debug/simple renderer. Production pass: wedge meshes/crack lances with profile data.

### P2 - Логирование слишком громкое

**Почему риск:** `HairpinDebugLog.info` пишет phase transitions, particle commands, broadcast per player. Для разработки ок. Для обычного runClient уже мусор.

**Как проявится позже:** логи будут забиты VFX noise, реальные ошибки потеряются.

**Что делать сейчас:** сделать debug flag:
- system property
- gamerule
- `/jujutsu debug hairpin true`
- logger `.debug()` вместо `.info()` для spam

### P2 - Unit tests создают ложную уверенность

**Почему риск:** `HairpinTimelineTest` и `HairpinVisualProfileTest` полезны как защита от тупого регресса. Но они не доказывают readable VFX.

**Как проявится позже:** build зелёный, а в игре чёрный дым.

**Что делать сейчас:** добавить resource sanity tests и обязательный visual smoke checklist. Unit tests оставить, но не поклоняться им как тотему.

---

## Hairpin VFX Audit

### Current likely failure modes

1. **Двойное затемнение sprites**
   - PNG уже тёмные.
   - Particle class ещё затемняет через `rCol/gCol/bCol`.
   - Итог у residue почти чёрный.
   - Minecraft particle tint обычно умножает цвет, а не “красиво красит как Photoshop”.

2. **Residue sprites слишком blob-like**
   - `residue_0/1/2` выглядят как комок дыма/сажи.
   - Для Hairpin нужно больше shard flakes, scratch residue, broken streaks.

3. **Server particle smoke-test портит форму**
   - `sendParticles(... count, spread, speed)` хорош для проверки загрузки particle type.
   - Плох для проверки art direction.
   - Он делает “облако”, а Hairpin должен быть anchored shrapnel.

4. **Billboard particles не знают surface/anchor**
   - Mark stain как billboard в воздухе не будет выглядеть “вбитым”.
   - Нужен либо decal-ish quad на поверхности, либо world-space marker/ribbon, либо nail model.

5. **Dirty fuchsia всё ещё может стать главным read**
   - Docs говорят 5-8%.
   - В игре fullbright fuchsia edge может визуально перебить тёмный body.
   - Надо проверять в motion, не по PNG.

### Correct target look

Production Hairpin должен читаться так:

- Сначала игрок видит nail anchors. Опасность приходит от гвоздя.
- Потом короткий warning edge вокруг anchors.
- Compression не выглядит как beam. Это tension threads, будто энергия тянется через вбитые точки.
- Snap - резкий, угловой, грязный. Не круг. Не TNT. Не firework.
- Burst - короткие клинья, metal shards, broken carmine streaks.
- Afterglow - те же векторы, только распадаются. Не отдельная аура.

Цветовая иерархия:
- 60-70% blood-black / black cherry / dark carmine
- 10-15% cold metal
- 5-8% dirty fuchsia edge
- white только impact tick, 1-2 frames

Палитра правильная, но текущая реализация слишком сильно убивает яркость body. Blood-black не обязан быть невидимым. Тёмный эффект должен иметь readable silhouette.

### Required particle families

Оставить текущие 7, но роли уточнить:

- `hairpin_mark_stain`
  - не дым
  - dark crack decal/stain
  - лучше сделать через world quad/decal позже

- `hairpin_warn_edge`
  - короткий fullbright edge
  - lifetime 4-8 ticks, сейчас 16-20 ticks может быть длинновато

- `hairpin_compression_mote`
  - мелкие inward motes
  - меньше quad size, больше directional motion

- `hairpin_snap_crack`
  - 2-4 tick high-contrast crack
  - сейчас lifetime 11-14 ticks слишком долго для snap accent

- `hairpin_burst_residue`
  - flakes/streaks, не blob
  - gravity можно оставить лёгкую

- `hairpin_burst_metal_shard`
  - физический cold metal read
  - хороший family, но нужен rotation/elongated shape read

- `hairpin_ignition_tick`
  - tiny hot node pulse
  - единственный family который сейчас по контрасту выглядит нормально

### Required texture/sprite direction

Сделать новый mini atlas:

- `mark_crack_thin_0/1`
  - 32x32 или 64x64
  - тонкие angular cracks
  - alpha sharp

- `warn_edge_slash_0/1/2`
  - narrow line, not thick magic streak
  - 1px fuchsia edge + dark core

- `compression_core_0/1`
  - small hot carmine dot with hard center
  - меньше soft feather

- `snap_fracture_0/1`
  - broken jagged slash
  - high contrast

- `residue_flake_0/1/2`
  - ash flakes, dirt scratches, shard trails
  - убрать smoke blob

- `metal_shard_0/1/2`
  - вытянутые холодные металлические клинья

Самое важное: делать sprites светлее, а затемнение контролировать кодом. Сейчас наоборот: sprites уже мрак, код добивает их лопатой.

### World-space geometry direction

Нужно обязательно. Particles alone не вывезут Hairpin.

Минимум production geometry:

- nail marker mesh или хотя бы 2 crossed ribbons как сейчас, но с profile data.
- 4 anchor-to-target compression threads.
- snap wedges from target/anchors.
- broken arcs around impact, не полный круг.
- optional surface crack quad на блоке.

Текущий `HairpinWorldRenderer` уже делает правильную идею: camera-relative ribbons через `WorldRenderEvents.AFTER_ENTITIES`.

Но production:
- убрать `List.copyOf`/new vec spam в render
- вынести phase properties в profile
- не полагаться вечно на `RenderType.lightning`
- добавить angle variation и broken lengths через seed

### Shader recommendation

Сейчас: не включать shader runtime.

Позже:
1. first: shaderless VFX must pass.
2. second: optional screen/local overlay для owner/victim.
3. third: optional post/geometry shader style if Fabric 1.21.8 path реально smoke-tested.
4. never: mandatory fullscreen shader as main readability carrier.

GLSL assets оставить как `art-source` / planned assets. Но сейчас они лежат в `assets/jujutsumod/shaders/post`, без активного post pipeline. Это не плохо. Плохо будет считать это готовой системой.

### In-game acceptance tests

Минимальный тест который доказывает Hairpin:

- `/jujutsu hairpin` 10 раз подряд в одном мире.
- Снять видео 60 fps.
- Проверить 3 дистанции:
  - 2-3 блока
  - 8-12 блоков
  - observer third-person 20 блоков
- Проверить 4 окружения:
  - day stone wall
  - night grass
  - cave/darkness
  - rain/fog если не больно
- Проверить:
  - nails visible before snap
  - snap reads in 1-2 ticks
  - burst originates from anchors/cluster
  - residue follows burst vectors
  - no black smoke ball
  - no missing texture after F3+T
  - no log spam
  - no fps stutter on 5 overlapping Hairpins

---

## Architecture Audit

### Current architecture verdict

Вердикт: хорошая прототипная архитектура, но ещё не production architecture.

Хорошее:
- `src/main` и `src/client` разделены.
- Common регистрирует particles/sounds/network/commands.
- Client регистрирует particle factories, HUD overlay, world renderer, client receiver.
- Payload semantic, не per-particle.
- No `net.fabricmc.fabric.impl.*`.
- No raw OpenGL.

Плохое:
- пустые mixins.
- runtime timing через wall clock.
- VFX profile неполный и отделён от renderer constants.
- debug commands смешаны с production command.
- root/main branch не отражает feature branch.
- metadata всё ещё template.

### Networking

Текущий `HairpinFxPayload`.

Плюсы:
- seed есть.
- target есть.
- 4 nail positions есть.
- `startGameTime` есть.
- ручной codec простой и понятный.

Минусы:
- `startGameTime` почти не используется.
- нет `sourceEntityId`.
- нет `dimensionId`.
- нет `effectProfileId`.
- нет `payloadVersion`.
- нет anchor normal/surface/material.
- нет entity-attached anchors.
- 4 nails захардкожены полями. Для прототипа ок. Для production нужно list/array with max cap.

### Timeline

`HairpinTimeline` норм как первая модель. Длительность 1800 ms совпадает с cinematic target.

Но для gameplay:
- 1800 ms может быть слишком долго.
- Надо два профиля:
  - `cinematic_debug`
  - `gameplay_pvp`
- Gameplay timing лучше в ticks:
  - mark/warn 4-6 ticks
  - compression 1-2 ticks
  - active snap 1 tick
  - residue 6-10 ticks

### Client rendering

Правильно:
- particles/hud/world renderer в client-only.
- `HudElementRegistry` используется в актуальном стиле для новых версий.
- `WorldRenderEvents.AFTER_ENTITIES` в локальном Fabric API 0.136.1+1.21.8 есть.

Плохо:
- allocations:
  - `activePlaybacks()` делает `List.copyOf` каждый render frame.
  - `target()` создаёт новый `Vec3`.
  - `nails()` создаёт новый List + 4 Vec3.
  - renderer вызывает это много раз.
- `System.currentTimeMillis()` в render.
- `RenderType.lightning` без UV/texture может стать тупиком.
- screen flash глобальный static. Если несколько Hairpin, последний перезапишет предыдущий. Для прототипа норм, дальше нужен overlay event stack.

### Debugging/logging

Сейчас `HairpinDebugLog.info` полезен на этапе “почему ничего не видно”.

Но дальше:
- сделать `debug` level.
- добавить `/jujutsu debug hairpin`.
- добавить one-line summary после playback:
  - seed
  - particles spawned per family
  - duration
  - active playbacks peak
- добавить optional client overlay:
  - phase
  - elapsed
  - particle budget
  - active playbacks

### Test strategy

Оставить:
- `HairpinTimelineTest`
- `HairpinVisualProfileTest`

Добавить:
- packet roundtrip codec test.
- resource existence test:
  - all registered particles have json
  - all particle json textures exist
  - sounds json contains all registered sound events
- side separation test:
  - no `net.minecraft.client` imports in `src/main/java`
- forbidden imports test:
  - no `fabric.impl`
  - no raw opengl/lwjgl
- jar content check.

Не тратить время сейчас:
- huge combat unit framework.
- abstract ability engine tests.
- fake rendering tests that only check methods exist.

### Refactor recommendations

Сейчас нужен малый refactor, не “переписать всё красиво”.

Срочно:
- remove template mixins.
- branch hygiene.
- time model.
- debug gating.
- VFX profile constants.

Не срочно:
- base particle class.
- data-driven json profile.
- generic ability framework.
- shader manager.
- massive registry abstraction.

---

## Documentation Audit

### Полезные документы

- `AGENTS.md`
  - сильный. правила правильные.
  - но сам проект уже нарушает mixin rule из-за template mixins.

- `docs/superpowers/specs/2026-07-06-nobara-hairpin-cinematic-design.md`
  - хороший canonical seed.
  - даёт visual grammar и границы.

- `docs/research/2026-07-07-hairpin-vfx-production-implementation-brief.md`
  - самый полезный для реализации.
  - короткий и конкретный.

- `docs/session-handoffs/2026-07-07-hairpin-production-vfx-handoff.md`
  - честно фиксирует, что in-game smoke test не был снят.

- `docs/visual-targets/nobara-hairpin/index.html`
  - полезный target.
  - но его нельзя считать финалом без side-by-side video с Minecraft.

### Слишком общие / опасные

- большие `docs/research/sources/*`
  - полезны как сырьё.
  - опасны как прямые инструкции.
  - там есть citation artifacts и куски с “likely but needs verification”.

- `2026-07-07-jujutsu-kaisen-minecraft-bible.md`
  - масштабно, вдохновляюще, но может увести в “строим весь мир” до одного хорошего эффекта.

- prompts docs
  - оставить как архив.
  - не использовать как source of truth.

### Что нужно синтезировать

Создать один файл:

`docs/superpowers/specs/hairpin-vfx-canonical.md`

Структура:
- fantasy sentence
- phase grammar
- timing table
- color palette with usage %
- particle families
- geometry layers
- sound layers
- forbidden reads
- in-game acceptance tests
- current implementation status
- open questions

### Где нужен canonical spec

- timing сейчас в `HairpinTimeline`
- palette в docs/html/shader/particles/renderer
- particle budget в `HairpinVisualProfile`
- asset needs в spec/research/handoff

Это надо свести. Иначе будет document drift.

---

## Recommended Next 10 Tasks

### 1. Merge/branch hygiene

**Objective:** сделать `chore/jujutsu-brainstorming` реальным рабочим источником.

**Why now:** иначе ревью/CI/build будут смотреть пустой main.

**Files/modules:** git only.

**Acceptance criteria:**
- `main` содержит Hairpin code/docs.
- `git status` clean.
- build из root собирает jar с Hairpin classes/assets.

### 2. Удалить template mixins

**Objective:** убрать лишний bytecode intrusion.

**Why now:** нарушает non-negotiable rule и не даёт пользы.

**Files/modules:**
- `src/main/java/jujutsu/mod/mixin/ExampleMixin.java`
- `src/client/java/jujutsu/mod/client/mixin/ExampleClientMixin.java`
- `src/main/resources/jujutsumod.mixins.json`
- `src/client/resources/jujutsumod.client.mixins.json`
- `src/main/resources/fabric.mod.json`

**Acceptance criteria:**
- no mixin configs in `fabric.mod.json`.
- jar не содержит `ExampleMixin`.
- build passes.

### 3. Исправить particle brightness/tint

**Objective:** убрать black smoke read.

**Why now:** это главный visual symptom.

**Files/modules:**
- `src/client/java/jujutsu/mod/client/particle/*`
- `src/main/resources/assets/jujutsumod/textures/particle/hairpin/*.png`

**Acceptance criteria:**
- residue tinted mean visually not pure black.
- burst читается на stone/night.
- no soft smoke ball in video.

### 4. Split smoke-test commands by purpose

**Objective:** отделить loading tests от art tests.

**Why now:** текущий `sendSmokeParticles` создаёт ложный visual result.

**Files/modules:**
- `JujutsuCommands.java`

**Acceptance criteria:**
- isolated particle command spawns `count=1` exact controlled particle option.
- stage command uses semantic client playback when possible.
- command names stop saying `smoke` for non-smoke VFX.

### 5. Перейти на tick-based playback

**Objective:** deterministic client playback.

**Why now:** payload уже несёт `startGameTime`, но код его игнорит.

**Files/modules:**
- `HairpinFxPayload.java`
- `HairpinPlayback.java`
- `HairpinPlaybackManager.java`
- `HairpinWorldRenderer.java`

**Acceptance criteria:**
- visual elapsed computed from game time.
- two clients receive same phase for same server tick.
- wall clock not used for core VFX timeline.

### 6. Создать `HairpinVfxProfile`

**Objective:** один источник для colors/counts/sizes/lifetimes.

**Why now:** сейчас constants расползлись.

**Files/modules:**
- `HairpinVisualProfile.java`
- particle classes
- `HairpinWorldRenderer.java`

**Acceptance criteria:**
- palette constants are centralized.
- renderer and particles reference profile or mapped preset.
- docs palette matches code.

### 7. Сделать first in-game visual capture pass

**Objective:** доказать или убить текущий VFX честно.

**Why now:** без видео дальше всё гадание на компиляторе.

**Files/modules:** run config, no major code.

**Acceptance criteria:**
- 3 short clips:
  - close
  - observer
  - bad visibility
- notes per phase:
  - mark visible?
  - compression readable?
  - snap satisfying?
  - residue connected?
  - black smoke?

### 8. Добавить resource sanity tests

**Objective:** ловить missing textures/sounds/json до runClient.

**Why now:** дешёвые проверки.

**Files/modules:**
- `src/test/java/jujutsu/mod/resources/...`
- `JujutsuParticles.java`
- `JujutsuSounds.java`

**Acceptance criteria:**
- every registered particle has json.
- every json texture exists.
- every registered sound id exists in `sounds.json`.
- no test claims visual quality.

### 9. Заменить vanilla sound placeholders на rough custom SFX pack

**Objective:** Hairpin без звука будет ощущаться как половина эффекта.

**Why now:** snap нуждается в audio transient.

**Files/modules:**
- `assets/jujutsumod/sounds.json`
- `assets/jujutsumod/sounds/hairpin/*.ogg`

**Acceptance criteria:**
- mono positional sounds.
- 5 layers exist:
  - prep inhale
  - hammer crack
  - ignition tick
  - burst
  - tail
- no vanilla firework/anvil as final read.

### 10. Canonical Hairpin spec

**Objective:** остановить document drift.

**Why now:** docs уже больше чем кода.

**Files/modules:**
- `docs/superpowers/specs/hairpin-vfx-canonical.md`

**Acceptance criteria:**
- one page answers what Hairpin is.
- all future tasks reference it.
- older research marked as raw reference.

---

## Non-Negotiable Acceptance Criteria Before Moving To Combat

До боёвки должно быть доказано в игре:

- `main` or active branch builds correct jar from root.
- no template mixins.
- no `net.fabricmc.fabric.impl.*`.
- no raw OpenGL/LWJGL path.
- no missing particle textures after F3+T.
- `/jujutsu hairpin` plays deterministic scene.
- video proves:
  - nail anchors visible before detonation.
  - warning readable at distance.
  - compression reads inward/anchored.
  - snap is the strongest beat.
  - burst is angular/shrapnel-like.
  - afterglow follows same burst vectors.
  - no generic black smoke sphere.
- vanilla runClient pass.
- Sodium pass.
- Iris without shaderpack pass.
- 5 overlapping Hairpins do not tank FPS or spam logs.
- custom sounds or at least non-vanilla rough placeholders exist.
- debug logging gated.
- payload has enough fields for future gameplay source tracking.
- docs have one canonical Hairpin spec.

---

## Final Verdict

**Продолжать текущий путь?**

Да. Направление сильное. Vertical slice first - правильно. Semantic payload + client VFX playback - правильно. Fabric-native without heavy VFX deps - правильно.

**Откатить часть решений?**

Частично. Удалить template mixins. Не считать GLSL runtime feature. Не доверять server particle smoke tests как visual proof. Пересобрать brightness/tint у particles.

**Сфокусироваться на VFX до боёвки?**

Да. Железно. Если Hairpin сейчас уйдёт в combat code с плохим visual read, потом будет дорогая переделка. Сначала один красивый и читаемый snap. Потом damage/cooldowns/resource.

**Самый важный следующий шаг:**

Сделать честный in-game VFX stabilization pass:

1. Merge рабочую ветку.
2. Убрать мусорные mixins.
3. Поправить black-smoke причину.
4. Перевести timeline на ticks.
5. Снять видео `/jujutsu hairpin` в 3 условиях.

После этого уже можно решать, нужен ли shader/mesh pass или particles+ribbons вытягивают первый slice.
