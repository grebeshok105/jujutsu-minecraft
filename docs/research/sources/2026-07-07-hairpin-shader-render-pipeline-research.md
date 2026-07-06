# fabric 1.21.8 hairpin vfx research for glsl, renderpipeline, post-processing, and sodium/iris-safe rendering

## executive summary

для **первого production-среза hairpin** самый безопасный стек такой:  
**основной эффект делать не через core shader resource pack и не через raw opengl**, а через **world-space геометрию + стандартные world render hooks fabric + стандартные render layers / vertex consumers**, с очень умеренным optional screen-pass только там, где он не ломает совместимость. причина простая: fabric прямо рекомендует держаться за встроенную рендер-систему, а не за raw opengl, а `WorldRenderEvents` специально существуют для совместимости с рендерами, которые сильно меняют пайплайн. отдельно iris официально предупреждает, что resource packs с **core shaders** не работают, если у игрока включен shader pack. citeturn34view0turn35view0turn30search19turn30search3

если перевести это в hairpin-грамматику `mark -> warn -> compression -> snap -> burst -> residue`, то **mvp** должен выглядеть так:  
маркер и предупреждение - через world-space decal/quad/ribbon-геометрию около гвоздя и цели; компрессия - через стягивающиеся ленты и fracture arcs; snap/burst - через короткий additive shell, ударные slash-plane’ы и резкий outline marker; residue - через тот же таймлайн, что и burst, но с затухающими ribbons/embers/noise-fade, без отдельного “настоящего bloom”. так ты получаешь читаемый high-end вайб без опоры на fragile fullscreen-химию. citeturn34view0turn35view0turn41view0turn43view0

**консервативный mvp-план**:
- world-space geometry в `WorldRenderEvents.AFTER_ENTITIES`, используя `WorldRenderContext.consumers()` и координаты **relative to camera**.
- прозрачные/полупрозрачные слои - через стандартные entity-like render layers и quads/ribbons, а не через прямые framebuffer writes.
- минимальный screen overlay только для очень короткого hit accent в `LAST`, и лучше отключаемый по настройке.
- никакой обязательной core-shader resource-pack логики и никакой обязательной зависимости на satin. citeturn35view0turn34view0turn30search3turn32search0

**advanced optional-план**:
- собственный `RenderPipeline` для отдельных world-space материалов, если стандартных слоёв не хватает.
- optional vanilla post effect через современный `PostEffectProcessor` / `PostEffectPipeline`, но только как доп. слой после локальной проверки путей и поведения на 1.21.8.
- satin держать только как реально optional/r&d зависимость, потому что публичные compatibility-метаданные у него не подтверждают 1.21.8 поддержку, а в репозитории есть отдельный запрос на update под 1.21.8. citeturn10view0turn19view0turn20view0turn32search0turn32search1

## verified rendering architecture in fabric 1.21.8

в **1.21.8** у fabric есть верифицируемые `WorldRenderEvents` и `WorldRenderContext`. javadoc прямо говорит, что модам стоит использовать эти события во время `WorldRenderer.render(...)`, чтобы не лезть mixin’ами в конфликтное место, и что это помогает совместимости с third-party renderers, которые сильно меняют пайплайн. порядок событий на кадр задан явно: `START -> AFTER_SETUP -> BEFORE_ENTITIES -> AFTER_ENTITIES -> BEFORE_BLOCK_OUTLINE -> BLOCK_OUTLINE -> BEFORE_DEBUG_RENDER -> ... -> AFTER_TRANSLUCENT -> LAST -> END`. citeturn34view0turn51view0

ключевая мысль для архитектуры hairpin такая: **мир и гейм-состояние собираются отдельно от собственно рисования**. текущая fabric-документация по рендеру мира объясняет разделение на extraction и drawing phases: во время extraction надо собирать все данные, которые нужны для кадра, а в drawing уже работать с подготовленным render state; render state должен быть immutable / thread-safe / cheap to create. это еще важнее для эффекта, который сервер шлёт как `semantic event + seed`, а клиент уже разворачивает в локальные transient layers. citeturn9view0turn10view0

есть важный нюанс по документации: **актуальная страница fabric docs уже показывает более новые имена вроде `LevelRenderEvents`, `LevelExtractionContext`, `LevelRenderContext` и примеры под mojang mappings**, но **верифицируемый fabric api 1.21.8 javadoc** показывает именно `WorldRenderEvents` и `WorldRenderContext`. то есть документацию используй как концептуальную опору по новой архитектуре и `RenderPipeline`, но для 1.21.8 код пиши по фактически существующим типам из javadoc твоей версии. citeturn9view0turn10view0turn34view0turn35view0

для конкретных фаз:
- `AFTER_SETUP` - когда уже есть frustum и известны render chunks. это хороший момент для coarse visibility и activation decisions. citeturn34view0turn51view0
- `AFTER_ENTITIES` - лучший общий хук для **hairpin world-space geometry**, потому что тут доступны `matrixStack()` и `consumers()`, а javadoc прямо говорит, что через `VertexConsumerProvider` обычно получаются лучшие результаты для non-terrain translucency, чем при более поздней прямой отрисовке. citeturn35view0turn51view0
- `AFTER_TRANSLUCENT` - уже после entity/terrain/particle translucent, но до fabulous-combine. тут **vertex consumers уже недоступны**, рендерить надо напрямую во framebuffer. это подходит только для очень локальных overlays / distortion layers. citeturn34view0turn51view0
- `LAST` - фабричный аналог “самого последнего world pass”: все framebuffer writes мира уже закончены, мир ещё не torn down, а матрица уже приведена к camera view. это лучший хук для краткого final accent, который должен быть поверх мира, но до hand/gui. citeturn51view0

по `WorldRenderContext` важно помнить три вещи. `matrixStack()` гарантированно не `null` только с `AFTER_ENTITIES` и позже. `consumers()` лучше использовать для большинства incremental quad renders, но все координаты, отправленные в consumer, должны быть **relative to the camera**. а `advancedTranslucency()` сообщает, включен ли fabulous mode, что прямо влияет на то, лучше ли твоему прозрачному слою жить в `AFTER_TRANSLUCENT` или в `LAST`. citeturn35view0

## first production shader stack for hairpin

### conservative mvp plan

mvp я бы собрал так:

**мир**
- `AFTER_SETUP`: выбрать активные hairpin events по дистанции, frustum, viewer profile и стадии таймлайна.
- `AFTER_ENTITIES`: собрать и отрисовать почти всё - nail markers, anchor ribbons, spike meshes, fracture arcs, slash planes, burst shell, residue ribbons.
- приоритетно использовать **existing render-layer-like pipeline**, а не прямые framebuffer writes. `RenderLayer` в yarn определён как объект, который задаёт vertex format, draw mode, shader, texture, uniform-like state и gl-like state до/после draw. именно это делает его лучшей базой для sodium-friendly рендера. citeturn35view0turn41view0

**экран**
- если очень надо, один короткий optional overlay в `LAST`: слабый radial pulse или chromatic nick на 2-4 тика.
- overlay должен выключаться настройкой отдельно от world effects.
- full-screen distortion не должен быть обязательной частью core readability. citeturn51view0

**почему это лучший первый срез**
- не зависит от core shader resource packs, которые конфликтуют с iris shader packs. citeturn30search3
- держится внутри fabric hooks, созданных под compatibility с third-party renderers. citeturn34view0turn51view0
- почти весь “вау” эффект можно сделать геометрией, прозрачностью, uv-скроллом и таймингом, вообще не заходя в fragile post stack. citeturn41view0turn43view0turn10view0

### advanced optional plan

advanced-режим я бы включал только вторым этапом:

**вариант с custom `RenderPipeline`**
- когда стандартных слоёв не хватает по blend/depth/cull/state.
- fabric docs показывают регистрацию через `RenderPipelines.register(RenderPipeline.builder(...).withLocation(...).withDepthStencilState(...).build())` и дальше ручной draw через `MeshData`, `MappableRingBuffer`, `RenderPass`, `CommandEncoder`, `setPipeline`, `setUniform`, `setVertexBuffer`, `setIndexBuffer`, `drawIndexed`. это уже серьёзный уровень и он хорош для точечных материалов, а не для всего эффекта целиком. citeturn10view0turn36view0turn11search2turn11search3turn11search4

**вариант с vanilla post-processing**
- в 1.21.8 есть `ShaderManager.getPostChain(...)`, `PostEffectProcessor`, `PostEffectPipeline`, `PostEffectPass`; `PostEffectProcessor` работает с internal/external targets и списком passes, а рендерит через `FrameGraphBuilder`. это означает, что ванильный пост-пайплайн существует и в принципе пригоден для кастомного эффекта без внешней библиотеки. citeturn21view0turn19view0turn20view0turn20view1
- практический вывод: **если понадобится distortion/refraction/bloom-ish fullscreen pass, сначала пробовать vanilla post path, а не core shader override**. citeturn19view0turn21view0turn30search3

**вариант с satin**
- satin упрощает managed shader effects, lazy init/reload и внешнюю установку uniforms. его README прямо говорит, что главная фича - managed `ShaderEffect`, и что библиотека даёт callbacks именно под post-processing. citeturn32search2turn31search0
- но публичная совместимость на modrinth сейчас указана только до **1.21.4**, а в issue tracker есть отдельный запрос “update 1.21.8”. как обязательную базу проекта я бы satin на 1.21.8 не брал. только optional dependency или r&d ветка. citeturn32search0turn32search1

## data flow and asset layout

рекомендуемый поток данных для hairpin выглядит так:

```text
server
  -> sends HairpinSemanticEvent
     {
       event_id,
       seed,
       anchors[],
       victim/entity id,
       stage_start_tick,
       stage_durations,
       intensity,
       viewer_role_policy
     }

client event system
  -> stores active event timeline
  -> derives deterministic sub-seeds for each visual sub-layer

per-frame extraction
  -> compute immutable HairpinRenderState
     {
       visible,
       camera-relative anchors,
       stage,
       normalized progress,
       burst/residue shared timeline,
       lod tier,
       accessibility-scaled intensity
     }

per-frame drawing
  -> WorldRenderEvents.AFTER_ENTITIES
       world-space marker / ribbons / spikes / fracture arcs / burst shell
  -> optional WorldRenderEvents.AFTER_TRANSLUCENT
       direct framebuffer overlay only if really needed
  -> optional WorldRenderEvents.LAST
       final pulse / slash accent / subtle screen nick
```

эта схема хорошо совпадает с тем, как fabric и minecraft сейчас разделяют extraction и drawing, и с тем, что `WorldRenderEvents` развешаны по конкретным draw boundaries. citeturn10view0turn34view0turn35view0turn51view0

по файловой раскладке под `assets/jujutsumod` я бы делал так:

```text
assets/jujutsumod/
  shaders/
    core/
      hairpin_marker.json            # только если сознательно делаешь core shader path
      hairpin_marker.vsh
      hairpin_marker.fsh

    post/
      hairpin_distort.vsh
      hairpin_distort.fsh
      hairpin_outline.vsh
      hairpin_outline.fsh
      hairpin_residue_blur.vsh
      hairpin_residue_blur.fsh

    include/
      hairpin_noise.glsl
      hairpin_fracture.glsl
      hairpin_chromatic.glsl
      hairpin_timeline.glsl

  post_effect/
    hairpin_distort.json
    hairpin_outline.json
    hairpin_burst_residue.json

  textures/
    vfx/
      hairpin_noise.png
      hairpin_ribbon.png
      hairpin_marker.png
      hairpin_residue.png
```

что тут основано на источниках:
- vanilla **core shaders** точно живут в `assets/.../shaders/core`, и там же лежат `.json`, `.vsh`, `.fsh`; в 1.21.8 можно увидеть реальные файлы вроде `position_tex.fsh`, который использует `#moj_import` и `Sampler0`. citeturn22search1turn22search3
- современный post-processing stack использует **pipeline/effect json отдельно от shader source**. это видно по современному `PostEffectProcessor`/`PostEffectPipeline`, а также по публичным путям ассетов типа `assets/minecraft/post_effect/transparency.json` и по changelog satin для 1.21+, где post-effect JSON переезжают в `post_effect`, а код шейдеров живёт под `shaders/post`. citeturn19view0turn20view0turn49search3turn31search7

главный практический вывод такой:  
**для hairpin production path лучше считать canonical layout’ом `post_effect/*.json` + `shaders/post/*` + `shaders/include/*` + `shaders/core/*`**, но путь `post_effect` я бы всё равно включил в локальный smoke-test checklist, потому что прямой javadoc 1.21.8 показывает классы загрузчика, а не буквальные значения констант путей. citeturn21view0turn19view0turn31search7

## api verification checklist for fabric 1.21.8

ниже то, что уже можно считать **высокоуверенно подтверждённым** для 1.21.8, и то, что надо добить локальной сборкой.

| статус | что проверено | примечание |
|---|---|---|
| verified | `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents` | есть `START`, `AFTER_SETUP`, `BEFORE_ENTITIES`, `AFTER_ENTITIES`, `BEFORE_BLOCK_OUTLINE`, `BLOCK_OUTLINE`, `BEFORE_DEBUG_RENDER`, `AFTER_TRANSLUCENT`, `LAST`, `END`. citeturn34view0turn51view0 |
| verified | `WorldRenderContext` | есть `camera()`, `consumers()`, `matrixStack()`, `frustum()`, `positionMatrix()`, `projectionMatrix()`, `advancedTranslucency()`, `world()`, `worldRenderer()`. citeturn35view0 |
| verified | `com.mojang.blaze3d.pipeline.RenderPipeline` | есть `builder(...)`, `getVertexFormat()`, `getVertexFormatMode()`, `getSamplers()`, `getUniforms()`, `wantsDepthTexture()`. citeturn36view0turn38search0 |
| verified | `com.mojang.blaze3d.systems.RenderPass` | есть `setPipeline`, binding samplers, uniform uploads, vertex/index buffers, draw calls. citeturn11search2turn11search4turn10view0 |
| verified | vanilla post-processing classes | `ShaderManager.getPostChain(...)`, `PostEffectProcessor`, `PostEffectPipeline`, `PostEffectPass`. citeturn21view0turn19view0turn20view0turn20view1 |
| verified | yarn-side render abstractions | `RenderLayer`, `BufferBuilder`, `VertexConsumer` живы и документированы. citeturn41view0turn13view0turn43view0 |
| verify locally | mojang-name equivalents | для mojang mappings проверь соответствия: `RenderType` ~= yarn `RenderLayer`; `MeshData` ~= yarn `BuiltBuffer`; `ByteBufferBuilder` ~= yarn `BufferAllocator`; `VertexFormat.Mode` ~= yarn `VertexFormat.DrawMode`. сравнение подтверждается примерами fabric docs и primer’ом по official mappings, но финально лучше щёлкнуть IDE в твоём workspace. citeturn10view0turn39view0turn39view1turn13view0 |
| verify locally | exact post-effect asset paths in your dev env | high-confidence путь - `post_effect/*.json` плюс `shaders/post/*`, но это стоит проверить F3+T и тестовым effect id. citeturn49search3turn31search7 |

отдельно: fabric docs по basic rendering сейчас прямо предупреждают, что raw opengl будет ломаться ещё сильнее по мере дальнейшей абстракции backend’а, так что если где-то в hairpin коде остаётся мысль “ну тут один gl call не страшно” - нет, страшно. citeturn30search19

## hairpin-specific effect recipes

### world-space geometry recipes

**nail marker**
- форма: плоский decal ring или слегка выпуклая шестиугольная пластина, screen-facing только при далёком lod.
- хук: `AFTER_ENTITIES`.
- рендер: стандартный translucent/entity-like layer.
- входы: `anchor_pos_ws`, `camera_pos_ws`, `timeline_t`, `seed`, `marker_strength`, `victim_role_factor`.
- поведение: мягкий outline + pulse + короткий uv-scroll на стадии warn. при `snap` marker резко схлопывается в точку.  
это хорошо ложится на `consumers()` и camera-relative vertices, которые именно для такого incremental world render и предназначены. citeturn35view0turn41view0turn43view0

**anchor ribbons**
- форма: не line primitive, а ribbon quad strip из 2-4 сегментов на отрезок.
- почему не обычные линии: `RenderLayer` действительно имеет debug/line layers, но hairpin требует не debug-look, а управляемую ширину, uv-scroll, alpha falloff и fracture jitter. у quad/ribbon геометрии на это просто больше контроля. citeturn41view0turn43view0
- входы: `anchor_a`, `anchor_b`, `width`, `flow_speed`, `noise_phase`, `compression`, `seed`.
- поведение: от “тонкой предупреждающей жилы” к “стянутой энергетической ленте”, после snap остаётся рваный residue tail.

**fracture arcs**
- форма: ломаные ribbon-segments с branch offshoots.
- входы: `seed`, `branch_count`, `jitter_amp`, `age`, `shock_phase`.
- поведение: до snap они собираются внутрь, на snap выбрасывают 1-2 быстрые secondary branch hits, затем гаснут растворением по noise mask.
- лучше всего рисовать тем же material family, что и ribbons, чтобы не плодить pipeline state changes.

**spike meshes**
- форма: короткие 3d клинья/осколки, привязанные к nail anchors и направлению detonation.
- lod: рядом можно дать настоящий 3d wedge, далеко - screen-facing impostor quad.
- поведение: в стадии compression они чуть втягиваются к центру, на burst выстреливают на 1-3 кадра.

**shock slashes**
- форма: плоские thin planes с anisotropic uv или curved billboard strips.
- хук: `AFTER_ENTITIES` для настоящего world-space удара, `LAST` только для короткого финального accent поверх мира.
- поведение: не “огненный шар”, а один-два злых рваных разреза по траектории nail-anchored expansion.

### shader recipes

**radial distortion**
- где: optional fullscreen или local proxy target.
- samplers: `InSampler` / scene color, опционально depth sampler если локально подтверждён путь.
- uniforms: `center_uv`, `radius`, `strength`, `falloff`, `chroma_split`, `time`, `seed`.
- output: искажает только область burst. strength должен иметь separate accessibility scalar.
- замечание: раз это fullscreen-подход, в первом релизе держать слабым и легко отключаемым. post pipeline это поддерживает концептуально, но реальная интеграция требует локальной проверки на 1.21.8. citeturn19view0turn20view0turn20view1

**sobel/marker outline**
- vanilla already ships an edge-style post fragment `entity_sobel.fsh`, который берёт `InSampler`, соседние texel samples и строит edge alpha. это хороший референс для “nail marker / cursed outline” логики. citeturn27search0
- uniforms: `in_size`, `outline_color`, `edge_threshold`, `pulse`.
- output: не полноценный entity-outline на весь экран, а локальный accent вокруг marker mask или burst residue mask.

**blood-black bloom simulation**
- честный bloom с несколькими downsample/blur/upcombine pass’ами для первого среза не нужен. лучше симулировать его как:
  1. emissive-like additive shell в мире,
  2. тонкий softened duplicate layer с более широким alpha falloff,
  3. общая шкала `burst_residue_phase`, чтобы “блик” и “остаток” были одним таймлайном.  
  это даёт читаемое ощущение bloom без тяжёлого post stack. идея “burst/residue one timeline” отлично сочетается с извлечённым immutable render state. citeturn10view0turn35view0

**heat haze / refraction for hairpin**
- визуально это самый опасный эффект в плане совместимости и readability.
- безопасный вариант: world-space “fake refraction” материал через scrolling normal/noise distortion на самих ribbons/quads.
- advanced вариант: реальный scene refraction через post pass. только optional и only-if-supported.
- uniforms: `distort_strength`, `noise_scale`, `scroll_speed`, `heat_radius`, `phase`.

**dissolve / residue fade**
- textures: 1 noise atlas + 1 residue mask.
- uniforms: `fade_t`, `noise_bias`, `softness`, `residue_tint`, `seed`.
- output: не просто alpha down, а edge-biased decay с остающейся “грязной энергией”.

**scrolling uv ribbons**
- uniforms: `uv_scroll`, `tiling`, `width_fade`, `tip_boost`, `age`.
- output: ощущение стянутой cursed-energy compression без необходимости в сложном post.

**noise-driven fracture**
- uniforms: `segment_seed`, `branch_seed`, `compress_t`, `burst_t`.
- output: ломаная дуга, которая перед snap не хаотично шумит, а именно “сжимается” и становится плотнее визуально.

## sodium, iris, accessibility, and performance risks

### sodium/iris risk table

| паттерн | sodium | iris без shader pack | iris с shader pack | вывод |
|---|---|---|---|---|
| `WorldRenderEvents` + `WorldRenderContext` + стандартные consumers/layers | низкий риск | низкий риск | низкий-средний риск | это базовый путь. fabric прямо говорит использовать эти hooks для совместимости с third-party renderers. citeturn34view0turn35view0 |
| прямые framebuffer writes в `AFTER_TRANSLUCENT` / `LAST` | средний риск | средний риск | средний-высокий риск | допустимо только для коротких overlays. fabric javadoc предупреждает, что third-party renderers могут менять/объединять passes. citeturn51view0turn34view0 |
| raw opengl calls и state poking | высокий риск | высокий риск | очень высокий риск | fabric docs буквально советуют не использовать raw opengl. citeturn30search19 |
| mandatory core shader resource pack | средний риск | средний риск | очень высокий риск | iris официально пишет, что resource packs с core shaders не работают при включённом shader pack. citeturn30search3 |
| optional vanilla post-processing | средний риск | средний риск | средний-высокий риск | возможен, но не должен быть единственным носителем readability. нужен runtime fallback. citeturn19view0turn21view0turn30search3 |
| satin как обязательная зависимость | средний риск | средний риск | средний-высокий риск | как библиотека он удобен, но публично не подтверждён под 1.21.8. лучше optional/r&d only. citeturn32search0turn32search1turn32search2 |

### accessibility and multiplayer readability

для multiplayer readability hairpin надо проектировать не как “красиво любой ценой”, а как **профиль видимости**:

- **attacker profile**: можно чуть сильнее compression pulse и residue.
- **victim profile**: меньше screen-space, больше world-space предупреждение около anchor/impact.
- **observer profile**: почти без fullscreen, зато с понятной world silhouette.
- отдельные слайдеры: `world effect intensity`, `screen distortion`, `chromatic split`, `flash duration`.
- hard cap на fullscreen силы и площадь искажения.
- если `advancedTranslucency()`/fabulous или shader pack активны, можно автоматически резать некоторые пост-слои и оставлять только геометрию. `WorldRenderContext` прямо даёт сигнал о fabulous, а iris отдельно ограничивает core shader path. citeturn35view0turn30search3

### performance and profiling

по факту самые дорогие места тут не “glsl как идея”, а:
- **fill-rate** от fullscreen pass’ов,
- **translucent sorting**,
- **gpu upload** динамической геометрии,
- **state churn** между кучей мелких материалов.  
`BufferBuilder` прямо документирован как объект, который строит примитивы и может сортировать quads по расстоянию до камеры для translucency. fabric docs для custom pipeline затем показывают build/upload cycle через `MeshData` и `MappableRingBuffer`, после чего ring buffer ротируется, чтобы не трогать буфер, который ещё ест gpu. это и есть твоя базовая цена на “каждый кадр новая кривая магия”. citeturn13view0turn10view0turn39view2

потому для первого среза:
- держать **0-1 fullscreen pass**, не больше.
- residue делать world-space, а не через blur pyramid.
- делить hairpin на **lod tiers**: near = 3d spikes + full ribbons, mid = fewer segments, far = billboards only.
- объединять материалы: один ribbon материал, один burst shell материал, один residue материал.
- ограничить максимальное число активных anchor-to-anchor ribbons на событие.
- тестировать отдельно сцены: одиночный каст, 4 параллельных каста, дождь/туман/прозрачные блоки, fabulous on/off, iris shader pack on/off.  
последние два режима важны, потому что `AFTER_TRANSLUCENT` ведёт себя особенно чувствительно к translucent/fabulous sequencing. citeturn35view0turn51view0

## anti-patterns and open questions

**no-go zones** для первого production slice:

- делать hairpin критически зависимым от **core shader resource pack**. с iris shader pack это официально ломкий путь. citeturn30search3
- лезть raw opengl’ем в глобальный стейт “потому что так быстрее”. fabric уже прямо ведёт экосистему в сторону backend abstraction. citeturn30search19
- рисовать большую часть эффекта прямыми framebuffer writes, если тот же результат можно получить через consumers/render layers. `WorldRenderEvents` сами предупреждают, что сторонние рендереры могут менять pass boundaries. citeturn51view0turn34view0
- использовать `BEFORE_DEBUG_RENDER` как основной production hook. он больше про debug-like immediate rendering, а не про твой постоянный боевой vfx. citeturn51view0
- пытаться получить “блум” в первой версии через тяжёлую многопроходную пост-цепочку. для hairpin выгоднее **сымитировать bloom геометрией и таймингом**.
- хранить world lookups внутри draw stage. extraction/drawing split уже намекает, что нужно заранее собирать immutable render state. citeturn10view0

**open questions / r&d tasks**:

1. локально подтвердить в mojang mappings workspace все имена, где сейчас есть только cross-check через yarn и loader-agnostic official-mappings primer: `RenderType`, `MeshData`, `ByteBufferBuilder`, `VertexFormat.Mode`. citeturn10view0turn39view0turn39view1turn13view0  
2. сделать минимальный smoke test для `post_effect` пути на **реальном 1.21.8 клиенте**: один trivial pass, один uniform, один sampler. по источникам это high-confidence, но не 100% доказано одной только javadoc страницей. citeturn19view0turn49search3turn31search7  
3. проверить поведение optional screen distortion при:
   - sodium only,
   - iris without shader pack,
   - iris with complementary/bsl-like shader pack.  
   core shaders там уже не опора. citeturn30search3  
4. решить, нужен ли вообще custom `RenderPipeline` в v1. если стандартные layer/material patterns дают нужный результат, не надо усложнять. fabric docs сами подают custom pipeline как решение на случай, когда vanilla pipelines реально не подходят. citeturn9view0turn10view0  
5. продумать единый **hairpin timeline uniform contract**:
   - `t_total`
   - `t_warn`
   - `t_compress`
   - `t_snap`
   - `t_burst_residue`
   - `seed_hi`, `seed_lo`
   - `intensity`
   - `viewer_profile`
   - `accessibility_scale`  

если резать всё до сути, то вывод такой:  
**первую version hairpin делай как geometry-first, pipeline-respecting, iris-safe-by-default систему.** шейдеры нужны, кнш, но в первой боевой версии они должны быть не “ядром совместимости”, а “усилителем уже читаемого world-space эффекта”. по факту это и даст тот самый jujutsu kaisen-style high-end vfx без цирка с sodium/iris.