# production-ready custom particles для hairpin на fabric 1.21.8

## executive summary

для целевого стека `minecraft 1.21.8 + fabric api 0.134.0+1.21.8 + official mojang mappings` базовая линия такая: регистрируешь particle types в common-коде, клиентские factory - отдельно, а для реального hairpin-эффекта не тащишь по сети сами частицы. сервер должен слать один семантический clientbound payload с anchor-контекстом и детерминированным seed, а клиент уже локально раскладывает это в несколько семейств VFX. это лучше попадает в ваш `mark -> warn -> compression -> snap -> burst -> residue`, потому что сетевой контракт остается маленьким и стабильным, а визуальная грамотность живет целиком на клиенте. сама Fabric-референс-сборка для 1.21.8 использует `loom.officialMojangMappings()` и `fabric-api 0.134.0+1.21.8`. citeturn37view0turn31view0

для 1.21.8 production-цели под Hairpin я бы держал почти все runtime particles простыми `SimpleParticleType`, а параметризацию делал не через particle packet, а через отдельный payload `CustomPacketPayload`. complex particle types нужны только если тебе реально надо, чтобы сам particle type нес параметры цвета, scale, lifetime, material или seed через codecs - например для `/particle`-дебага, data-driven пресетов или общего API для других систем. `FabricParticleTypes.simple(...)` создает простой type, а `FabricParticleTypes.complex(...)` - type с собственными codec/packet codec; у самого `ParticleType<T>` контракт именно такой: `codec()` и `streamCodec()` живут на типе, а `ParticleOptions` - это отдельный payload-объект. citeturn6view0turn13view0turn13view1turn13view2

по рендеру ставка должна быть на `TextureSheetParticle` и его родственников. в 1.21.8 именно они дают тебе знакомый quad-based путь, sprite sets, возрастную анимацию и нормальный батчинг по render type. `ParticleRenderType` в этой версии имеет только `TERRAIN_SHEET`, `PARTICLE_SHEET_OPAQUE`, `PARTICLE_SHEET_TRANSLUCENT`, `CUSTOM` и `NO_RENDER`; отдельного “lit sheet” нет. emissive-вид достигается не отдельным render type, а через `getLightColor(float)` - так делают ванильные `GlowParticle` и `SoulParticle`. для Hairpin это значит: residue и metal shards держать world-lit, а грязно-фуксиевые edge sparks и tiny ignition ticks делать full-bright короткой жизнью. citeturn7view3turn19view0turn19view1turn10view1

самая важная продакшн-ошибка, которой стоит избежать - соблазн свалить весь эффект в один translucent generic burst. `ParticleEngine` хранит очереди частиц по `ParticleRenderType` и рендерит их слоями, так что лишние render types режут batching, а тяжелая прозрачность бьет в overdraw и fill rate. Hairpin по факту лучше выглядит и лучше скейлится, когда opaque/quasi-opaque делает “тело” взрыва, а translucent оставлен только для edge flash. это не только быстрее, но и ближе к вашей визуальной грамматике: не TNT и не дымовая сфера. citeturn11view1turn7view3turn49search3turn49search4

## api ground truth для fabric 1.21.8

### версия и naming crosswalk

если цель именно `fabric 1.21.8 / mojang mappings`, не копируй слепо свежие примеры из ветки 26.1+. официальный porting guide прямо говорит, что в 26.1 Fabric API переименовал `ParticleFactoryRegistry -> ParticleProviderRegistry` и `FabricSpriteProvider -> FabricSpriteSet`. значит для вашей 1.21.8-линии ориентир - старые имена `ParticleFactoryRegistry` и `FabricSpriteProvider`, даже если часть новой документации уже показывает renamed API. параллельно official reference build для `reference/1.21.8` сидит на Mojang mappings. citeturn48view0turn48view1turn37view0

по mojang mappings для 1.21.8 полезный минимум такой: `net.minecraft.core.particles.ParticleType`, `net.minecraft.core.particles.SimpleParticleType`, `net.minecraft.core.particles.ParticleOptions`, `net.minecraft.client.particle.Particle`, `TextureSheetParticle`, `SingleQuadParticle`, `SpriteSet`, `ParticleRenderType`, `ParticleProvider`, `ParticleEngine`, `ParticleDescription`, а для идентификаторов - `net.minecraft.resources.ResourceLocation`. старые Yarn/старые туториалы чаще называют это `ParticleEffect`, `SpriteProvider`, `ParticleTextureSheet`, `Identifier`. именно из-за этого в старых гайдах часто кажется, что “имена не сходятся”, хотя классы те же по смыслу. citeturn13view2turn7view2turn7view3turn33view0turn10view1

### регистрация type и client factory

common-регистрация идет через `Registry.register(...)`, а registry-объект для particle types - `BuiltInRegistries.PARTICLE_TYPE` в официальном reference-примере Fabric. для Mojang mappings 1.21.8 id лучше собирать через `ResourceLocation.fromNamespaceAndPath(modId, path)`, потому что page для `ResourceLocation` в mappings.dev показывает именно этот factory method на целевой версии. `FabricParticleTypes.simple()` и `simple(boolean alwaysSpawn)` дают простой type; `alwaysSpawn` - это тот же override limiter / always spawn флаг на `ParticleType`. citeturn52view0turn33view0turn6view0turn13view1

клиентская регистрация для 1.21.8 идет через `net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry`. у него есть `getInstance()`, `register(ParticleType<T>, ParticleFactory<T>)` и delayed-overload `register(ParticleType<T>, PendingParticleFactory<T>)`. delayed-вариант особенно полезен для sprite-driven particle families, потому что factory создается позже и получает `FabricSpriteProvider`, связанный с активным resource pack. именно это и нужно Hairpin-семействам, где ты хочешь age-animation или случайный выбор спрайта без ручного ковыряния в atlas. citeturn53view0turn50view0turn16view0

```java
// common
public final class HairpinParticles {
    public static final SimpleParticleType HAIRPIN_WARN_EDGE =
            FabricParticleTypes.simple(false);
    public static final SimpleParticleType HAIRPIN_RESIDUE =
            FabricParticleTypes.simple(false);

    public static void bootstrap() {
        Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("jujutsumod", "hairpin_warn_edge"),
                HAIRPIN_WARN_EDGE
        );
        Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("jujutsumod", "hairpin_residue"),
                HAIRPIN_RESIDUE
        );
    }
}

// client
public final class HairpinParticlesClient {
    public static void bootstrap() {
        ParticleFactoryRegistry.getInstance().register(
                HairpinParticles.HAIRPIN_WARN_EDGE,
                sprites -> new HairpinWarnEdgeProvider(sprites)
        );
        ParticleFactoryRegistry.getInstance().register(
                HairpinParticles.HAIRPIN_RESIDUE,
                sprites -> new HairpinResidueProvider(sprites)
        );
    }
}
```

этот шаблон совпадает с тем, как Fabric docs разводят common particle registration и client-side factory registration, только с поправкой на Mojang name `ResourceLocation` и на то, что ваша целевая линия 1.21.8 еще живет на `ParticleFactoryRegistry`, а не на `ParticleProviderRegistry`. citeturn52view0turn48view0turn33view0turn53view0

### когда simple достаточно, а когда нужен complex

`SimpleParticleType` в 1.21.8 сам реализует `ParticleOptions`, но по сути это “пустой” payload: в нем нет пользовательских полей для цвета, масштаба, lifetime, seed или material class, только собственные codec/streamCodec и возвращение своего типа. если твой Hairpin pipeline уже шлет отдельный семантический packet, этого более чем хватает: type нужен клиенту только как локальный ключ для factory и atlas json. citeturn13view0turn13view2

`FabricParticleTypes.complex(...)` нужен в трех случаях. первый - когда ты хочешь, чтобы сам particle был параметрическим и сериализуемым как `ParticleOptions`, а не просто спавнился из локальной логики. второй - когда нужен `/particle`-debug с данными вроде `seed`, `scale`, `material`, `anchorNormal`, и ты хочешь командой воспроизводить точный кадр. третий - когда particle type должен быть публичным модульным API для других систем внутри мода. Fabric javadocs прямо описывают complex-вариант как type с custom factory и codec/packet serialization. `BlockParticleOption` в ваниле еще и показывает общий pattern: `codec(type)` и `streamCodec(type)` собираются от конкретного поля `BlockState`. citeturn6view0turn41view0

для Hairpin production slice вывод простой: рабочие боевые families делай `simple`, а если нужен удобный tuning/debug path, добавь один отдельный complex-параметрический debug particle. так ты не размазываешь по сети служебные VFX-поля каждый раз, но не теряешь точную воспроизводимость для отладки. это уже вывод из particle API и Fabric networking model. citeturn6view0turn31view0

### sprite sets, lifecycle и json sprite declarations

у `TextureSheetParticle` на 1.21.8 есть ровно те методы, которые для Hairpin и нужны: `setSprite(TextureAtlasSprite)`, `pickSprite(SpriteSet)` и `setSpriteFromAge(SpriteSet)`. сам `SpriteSet` умеет как `get(age, maxAge)`, так и `get(RandomSource)`. практический смысл такой: для residue motes и metal shards делай случайный выбор кадра один раз через `pickSprite`, а для warn-edge / snap-edge / ignition ticks используй `setSpriteFromAge`, чтобы frame progression шел по age без ручного индекса. citeturn7view0turn7view2

lifecycle живет в базовом `Particle`: там находятся `age`, `lifetime`, `gravity`, `friction`, `alpha`, `hasPhysics`, `roll` и `speedUpWhenYMotionIsBlocked`. размер quad живет уровнем выше в `SingleQuadParticle` как `quadSize`, плюс есть `scale(float)`. это значит, что для большинства Hairpin-семейств тебе не нужен кастомный рендер-путь - хватает нормального subclass с аккуратным constructor setup и небольшим `tick()`, который правит alpha, velocity damping и иногда sprite-by-age. citeturn10view1turn10view0

json-описание спрайтов парсит `net.minecraft.client.particle.ParticleDescription.fromJson(JsonObject)`, а `ParticleEngine` во время reload грузит particle descriptions и texture list. Fabric javadocs для delayed factory отдельно говорят, что спрайты загружаются из `domain:/particles/particle_name.json`. Fabric guide для custom particles показывает ровно два ресурсных каталога: `assets/<modid>/particles/` для json и `assets/<modid>/textures/particle/` для png, а сам json содержит `textures: [...]`. в ванильном atlas-описании `assets/minecraft/atlases/particles.json` источник тоже один - directory `particle`, то есть ваши hairpin png действительно должны жить под `textures/particle`. citeturn11view1turn11view2turn50view0turn52view0turn46view0

### render type, lighting и batching

в 1.21.8 `ParticleRenderType` - это record с пятью константами: `TERRAIN_SHEET`, `PARTICLE_SHEET_OPAQUE`, `PARTICLE_SHEET_TRANSLUCENT`, `CUSTOM`, `NO_RENDER`. `ParticleEngine` держит `Map<ParticleRenderType, Queue<Particle>> particles` и отдельный `RENDER_ORDER`, то есть движок и правда мыслит частицами как слоями. отсюда продакшн-правило простое: чем меньше семейств тебе приходится раскидывать по разным render types, тем лучше batching и предсказуемее поведение под оптимизационными рендерами. citeturn7view3turn11view1

“lit vs no-lit” здесь не отдельная текстурная sheet. это вопрос `getLightColor(float)`. ванильные `GlowParticle`, `SoulParticle` и `SimpleAnimatedParticle` показывают нужный паттерн: render type может оставаться sheet-based, а яркость - переопределяться отдельно. для Hairpin это очень чистая развилка: blood-black residue motes и metal shards - world-lit; dirty-fuchsia edge sparks и tiny ignition ticks - full-bright, но короткоживущие и без lingering afterglow. citeturn19view1turn19view0turn11view0

`CUSTOM` держи как последний резерв. в базовом `Particle` есть отдельный `renderCustom(PoseStack, MultiBufferSource, Camera, float)`; это уже другой путь, и ровно он чаще всего создает проблемы совместимости с альтернативными рендерами, если payload вершин или state setup не совпадает с тем, что ожидает renderer. реальные issue reports у Sodium и Iris показывают и mod-compat bugs на анимации custom particle textures, и краши на нестандартных particle render paths / vertex formats. для Hairpin vertical slice это железное “не надо”, пока sheet-based вариант не уперся в потолок. citeturn10view1turn54view0turn54view1turn54view2

## hairpin particle taxonomy

ниже не “что умеет движок”, а уже рекомендуемая разбивка под ваш конкретный Hairpin. она собрана так, чтобы визуальный синтаксис читался по фазам и при этом не уходил в generic explosion/smoke language. техническая опора тут на `TextureSheetParticle`, `SimpleAnimatedParticle`, `TerrainParticle` и sheet-based render types. citeturn7view0turn11view0turn19view2turn7view3

| particle id | роль | lifetime | textures | базовый класс | render type | lighting | цвет | фаза | lod |
|---|---|---:|---:|---|---|---|---|---|---|
| `hairpin_mark_stain` | anchored mark вокруг armed nail. не glow. почти статичный след | 14-24 t | 2 | `TextureSheetParticle` | `PARTICLE_SHEET_OPAQUE` | world-lit | blood-black / black cherry | mark | >24m: 1 sprite, >40m: off |
| `hairpin_warn_edge` | короткий edge pulse по контуру будущего snap. только край | 4-6 t | 3 | `SimpleAnimatedParticle` | `PARTICLE_SHEET_TRANSLUCENT` | full-bright | dirty fuchsia только edge | warn | >20m: count x0.5, >32m: off |
| `hairpin_compression_mote` | inward-moving motes к anchor point. ощущение сжатия | 6-10 t | 2 | `TextureSheetParticle` | `PARTICLE_SHEET_OPAQUE` | world-lit | dark carmine / blood-black | compression | >24m: count x0.5 |
| `hairpin_snap_crack` | микро-вспышка в момент snap. не lingering | 2-4 t | 2 | `SimpleAnimatedParticle` | `PARTICLE_SHEET_TRANSLUCENT` | full-bright | dirty fuchsia edge + почти черный core | snap | только near/mid |
| `hairpin_burst_residue` | основное “тело” burst. не сфера, а рваный directional burst | 10-18 t | 3 | `TextureSheetParticle` | `PARTICLE_SHEET_OPAQUE` | world-lit | blood-black / black cherry / dark carmine | burst | >24m: count x0.6, >40m: 2-3 шт. |
| `hairpin_burst_metal_shard` | cold-metal shard от nail fracture / ring-off | 8-14 t | 2 | `TextureSheetParticle` | `PARTICLE_SHEET_OPAQUE` | world-lit | cold metal | burst | оставить дальше всего, но count x0.5 |
| `hairpin_block_chip` | dust chips по материалу поверхности | 8-12 t | vanilla/block-derived | `TerrainParticle` или block-derived spawn | `TERRAIN_SHEET` | world-lit | по блоку | burst | >20m: count x0.5, >32m: off |
| `hairpin_ignition_tick` | tiny nail ignition tick на старте и перед snap | 2-3 t | 1 | `SimpleAnimatedParticle` | `PARTICLE_SHEET_TRANSLUCENT` | full-bright | faint dirty fuchsia edge, почти бело-металлический центр | mark / warn / snap | near only |

практическое замечание: `mark_stain` честнее было бы делать вообще не particle, а decal/anchored model. но если vertical slice должен остаться внутри particle pipeline, делай его максимально сдержанным и всегда убивай на `snap`, чтобы он не превратился в независимый afterglow. citeturn10view1turn7view3

## exact implementation checklist для fabric 1.21.8

### что делать в common-коде

собери отдельный `HairpinParticles` и отдельный `HairpinVfxPayload`. particle types зарегистрируй в common initializer, потому что тип существует на обеих физических сторонах. сеть тоже регистрируй в common initializer через `PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)`. сам payload делай `record`, реализующий `CustomPacketPayload`, по тому же паттерну, который Fabric networking guide показывает для clientbound payload. citeturn31view0turn52view0turn53view0

минимальный payload для Hairpin я бы держал таким:

- `long eventSeed`
- `int attackInstanceId`
- `BlockPos` или compact anchor pos
- `byte phaseMask`
- `byte materialClass`
- `byte intensityTier`
- `Vec3i`/packed normal index, если нужен directional burst
- `int serverGameTimeMod` или клиентски-устойчивый phase start tick

это уже рекомендация по дизайну, но она хорошо ложится на Fabric-паттерн `CustomPacketPayload + StreamCodec`, где сервер шлет только смысловые данные, а не сам готовый particle cloud. citeturn31view0

### что делать в client-коде

в client initializer:

- регистрируй все factory через `ParticleFactoryRegistry.getInstance().register(...)`; для sprite families используй delayed overload, чтобы provider пришел из resource packs. citeturn53view0turn50view0
- регистрируй `ClientPlayNetworking.registerGlobalReceiver(HairpinVfxPayload.TYPE, ...)` и внутри receiver вызывай единый `HairpinParticleSpawner.spawn(level, payload)`. citeturn31view0
- не раздавай spawn-логику по самим particle classes. сами частицы должны быть тупыми рендерами своих preset-параметров, а семантическая оркестрация должна жить в одном spawner-е. это сильно упрощает phase tuning.

### какие subclasses реально нужны

по-хорошему хватит четырех ручных subclasses и одного block-derived path:

```text
HairpinMarkParticle extends TextureSheetParticle
HairpinResidueParticle extends TextureSheetParticle
HairpinMetalShardParticle extends TextureSheetParticle
HairpinEdgeSparkParticle extends SimpleAnimatedParticle
```

`SimpleAnimatedParticle` бери там, где реально есть sprite-by-age и короткая fade-логика. mappings page показывает, что у него уже есть `sprites`, `tick()`, `setFadeColor(int)` и `getLightColor(float)`. для residue/shards чаще выгоднее голый `TextureSheetParticle`, где ты сам задаешь damping, alpha curve и случайный спрайт один раз. citeturn11view0turn7view0

для dust chips по материалу поверхности не пытайся рисовать “fake block dust” вручную, если вам нужен честный block-derived look. ванильный `BlockParticleOption` уже несет `BlockState`, а `TerrainParticle` существует как отдельный terrain-sheet particle class. если требуется стилизация сильнее ванили, оставь terrain chips только для hard surface и stone-like материалов, а flesh/cursed surfaces своди к вашей custom-palette residue family. citeturn41view0turn19view2

### как должна выглядеть файловая структура

рекомендую держать Hairpin-частицы в отдельном разделе, а не размазывать по общему particle-каталогу:

```text
src/main/java/com/yourteam/jujutsumod/
  JujutsuMod.java
  particle/
    HairpinParticles.java
  network/
    HairpinVfxPayload.java
  hairpin/
    HairpinVfxSemantics.java
    HairpinAnchorSnapshot.java

src/client/java/com/yourteam/jujutsumod/
  JujutsuModClient.java
  particle/
    HairpinParticlesClient.java
    HairpinParticleSpawner.java
    HairpinRng.java
    client/
      HairpinMarkParticle.java
      HairpinResidueParticle.java
      HairpinMetalShardParticle.java
      HairpinEdgeSparkParticle.java

src/main/resources/assets/jujutsumod/
  particles/
    hairpin_mark_stain.json
    hairpin_warn_edge.json
    hairpin_compression_mote.json
    hairpin_snap_crack.json
    hairpin_burst_residue.json
    hairpin_burst_metal_shard.json
    hairpin_ignition_tick.json
  textures/particle/hairpin/
    mark_stain_0.png
    mark_stain_1.png
    warn_edge_0.png
    warn_edge_1.png
    warn_edge_2.png
    compression_mote_0.png
    compression_mote_1.png
    snap_crack_0.png
    snap_crack_1.png
    residue_0.png
    residue_1.png
    residue_2.png
    metal_shard_0.png
    metal_shard_1.png
    ignition_tick_0.png
```

такой layout совпадает с тем, как Fabric docs разводят `particles/` json и `textures/particle/` png, а отдельная подпапка `particle/hairpin/` не ломает atlas lookup, потому что particle atlas в ванили просто сканирует директорию `particle`. citeturn52view0turn46view0

### asset pipeline, который реально подойдет Hairpin

тут уже не столько “API fact”, сколько production-рекомендация.

для Hairpin я бы держал такие размеры:

- `8x8` для ignition ticks и самых мелких edge sparks
- `16x16` для residue motes, compression motes и warn-edge
- `16x16` или `16x8` для metal shards
- максимум `3-4` кадров на семью, иначе atlas разрастается быстрее, чем выигрыш по читаемости

экспортируй в обычный `png rgba`, без premultiplied alpha. края обязательно подчищай - не мягкая мутная дымка, а резкий shape с узким feather на dirty-fuchsia edge. если используешь внешние редакторы, добавляй аккуратную дилатацию по цвету на полупрозрачной кромке, чтобы на atlas/mipmap не словить грязный ореол. и да, никакого “soft smoke circle with dark tint”. это убьет всю грамматику Hairpin.

## performance budget и multiplayer sync

### бюджет по фазам и lod

если держать большинство burst-семейств на `PARTICLE_SHEET_OPAQUE`, а translucent оставить только там, где реально нужен edge flash, Hairpin спокойно помещается в очень вменяемый бюджет. я бы закладывал near-camera пик на один anchor event примерно так:

- mark: `1`
- warn: `2-4`
- compression: `6-8`
- snap: `3-5`
- burst residue: `8-12`
- metal shards: `3-5`
- block chips: `4-8`
- lingering residue: уже входит в residue budget, а не отдельной волной

итого нормальный пик - примерно `27-43` живых quad-частиц на один `snap/burst`, что для короткой Hairpin сцены выглядит богато, но не упирается мордой в overdraw как большая translucent сфера. прозрачность всегда самая дорогая часть такого эффекта, потому что overlapping transparent geometry добавляет overdraw и тратит fill rate. citeturn11view1turn7view3turn49search3turn49search4

простая lod-схема для вашего slice:

| дистанция | правило |
|---|---|
| `0-12 м` | полный набор семейств |
| `12-24 м` | translucent familias x0.5, opaque x0.75 |
| `24-40 м` | выключить `warn_edge`, `snap_crack`, `ignition_tick`; оставить только сокращенный burst body |
| `40+ м` | либо ничего, либо 1-2 opaque cues если это влияет на gameplay-readability |

ключевой приоритет у LOD не “меньше всего”, а “сначала режь translucent edge-only stuff”. opaque residue и пара metal shards на дистанции читаются лучше, чем дешевая фуксиевая искра без контекста.

### как писать сами particles, чтобы не мусорить cpu

`Particle` уже хранит нужные тебе поля `gravity`, `friction`, `age`, `lifetime`, `alpha`, `hasPhysics`. поэтому:

- sparks, mark и residue по умолчанию делай `hasPhysics = false`
- metal shards и некоторые block chips можно оставить с физикой только если это реально продает удар
- не создавай в `tick()` новые `Vec3`, `Random`, `Color`, `AABB` и прочую мелочь
- seed-branching делай один раз в constructor/provider
- для sprite animation пользуйся `setSpriteFromAge`, а не вычисляй uv вручную на каждом tick
- `quadSize` и `scale(float)` используй один раз на spawn или через легкую age-curve

это все естественно вытекает из polyline базовых классов: у `Particle` и `SingleQuadParticle` уже есть все нужные поля и методы для дешевого tick-а. citeturn10view1turn10view0turn7view0

### что сервер должен слать и что слать нельзя

сервер должен слать только семантику события и данные, которые клиент сам не может восстановить надежно: anchor id/pos, phase, material class, intensity tier, seed и, если надо, нормаль поверхности или локальный burst axis. отправка должна идти только трекаемым клиентам - Fabric networking guide прямо подчеркивает tracking как паттерн для эффективного оповещения только нужных игроков. citeturn31view0

по сети не надо слать:

- per-particle positions
- per-particle velocities
- per-particle colors
- per-frame alpha curves
- живые массивы кадров / texture indices
- вторичные lingering clouds

это все должно детерминированно выводиться на клиенте из `eventSeed + phase + materialClass + distance tier`. если нарушить это правило, Hairpin очень быстро превращается из semantic VFX в сетевой мусор и начинает расходиться по кадрам между клиентами при ревизии эффектов. это уже инженерный вывод из Fabric payload-модели и того факта, что particle factories все равно создают частицы локально на клиенте. citeturn31view0turn53view0

## compatibility, resource reload и verification

### sodium, iris и resource reload

у `ParticleEngine` есть `reload(...)`, он реализует `PreparableReloadListener`, а sprite-aware particle sets лежат в `spriteSets`. отдельный `ParticleEngine.MutableSpriteSet` умеет `rebind(List<TextureAtlasSprite>)`. перевод на человеческий: ресурсный reload реально меняет bind спрайтов, и любой код, который кеширует atlas sprites статически “навсегда”, со временем поймает stale reference или странный визуальный мусор. для Hairpin это значит: можешь кешировать индекс, seed и scalar-параметры, но не `TextureAtlasSprite` в static singleton-ах. citeturn11view1turn12view0

с Sodium/Iris две реальные зоны риска уже подтверждались issue-трекерами. первая - animated/custom particle textures могут ломаться или вести себя не как в ваниле. вторая - нестандартные particle render paths и vertex formats могут приводить к mod-compat крашам. значит для Hairpin безопасный путь очень скучный, но правильный: sheet-based particles, стандартные factories, минимум `CUSTOM`, минимум шейдерного колдовства, максимум ванильного контракта. citeturn54view0turn54view1turn54view2

### smoke test checklist

перед тем как считать Hairpin “production-ready”, я бы гонял вот такой набор проверок:

- каждая зарегистрированная particle id имеет json в `assets/jujutsumod/particles/` и все texture refs внутри реально существуют. формат json для particles - это `textures: [...]`, без лишних полей. citeturn52view0turn11view2
- после resource reload все Hairpin families продолжают спавниться и анимироваться так же. это проверка на stale sprite refs. citeturn11view1turn12view0
- near/mid/far LOD визуально не ломает grammar: на дистанции должен остаться `cause`, а не случайный sparkle-spam.
- night/day pass: dirty-fuchsia edge sparks не должны выглядеть как магический неон-шар. full-bright должен быть мгновенным и edge-only. citeturn19view0turn19view1
- block-material pass: `hairpin_block_chip` на камне, дереве, металле и cursed-поверхностях не должен давать одну и ту же пыль.
- multiplayer pass: два клиента с одинаковым payload обязаны видеть одинаковую фазовую структуру burst-а. расхождение допустимо только в LOD по дистанции.
- compatibility pass: vanilla, Sodium, Iris без shaderpack, Iris с тяжелым shaderpack. минимум smoke test на spawn, despawn, reload и массовый burst. citeturn54view0turn54view1turn54view2

## anti-patterns и failure modes

самые опасные фейлы для этого конкретного Hairpin:

- делать один большой translucent smoke-ball и красить его в бордовый. визуально это сразу обычный explosion language, а не Hairpin.
- тянуть цвет/seed/velocity каждой частицы по сети. по факту это лишнее, и Fabric payload layer тебе такого не требует. citeturn31view0
- ставить `ParticleRenderType.CUSTOM` всем семьям “на будущее”. будущее там обычно одно - совместимость ломается раньше, чем эффект становится лучше. citeturn7view3turn54view1turn54view2
- делать весь burst full-bright. тогда пропадает ощущение blood-black compression и холодного металла, остается клиповая магия.
- хранить `TextureAtlasSprite` в static полях между reload-ами. `MutableSpriteSet.rebind(...)` прямо намекает, что так делать не надо. citeturn12view0
- строить mark как независимый lingering glow. это ломает вашу grammar rule “нельзя независимый afterglow”.
- использовать huge soft 32x32 quads для residue motes. дорого и мутно.
- забыть, что `warn` и `snap` - разные фазы. если их цвета, длительность и язык одинаковые, эффект теряет драматургию.
- делать block chips всегда кастомной бордовой крошкой. на твердой поверхности лучше коротко показать material truth.
- полагаться на новые имена `ParticleProviderRegistry`/`FabricSpriteSet` в коде 1.21.8. это уже следующая линия API. citeturn48view0turn48view1

## source trust и open questions

### source trust table

| класс источника | что я из него брал | доверие для этой задачи | комментарий |
|---|---|---|---|
| official fabric docs | reference build для 1.21.8, custom particles flow, networking payload flow | высокое | лучший источник для workflow, но свежая ветка docs уже отражает post-26.1 rename-ы, их надо сверять с 1.21.8 line. citeturn37view0turn52view0turn31view0turn48view0 |
| fabric api javadocs 1.21.8 line | `FabricParticleTypes`, `ParticleFactoryRegistry`, `PendingParticleFactory`, `FabricSpriteProvider` | высокое | это точный API surface для fabric 1.21.8-поколения. citeturn6view0turn53view0turn50view0turn16view0 |
| mojang mappings via mappings.dev 1.21.8 | package/class/method names, `Particle`, `TextureSheetParticle`, `SpriteSet`, `ParticleRenderType`, `ParticleDescription`, `ResourceLocation` | высокое | это и есть ground truth для mojang-mapped naming и class surface. citeturn10view1turn7view0turn7view2turn7view3turn11view2turn33view0 |
| vanilla asset mirror | particle atlas source path | средне-высокое | не официальный Mojang-хостинг, но полезный mirror для проверки asset layout. citeturn46view0 |
| sodium/iris issue trackers | только compatibility risks | среднее | это не спецификация, а evidence of real-world breakage. я использовал их только как предупреждение, не как API-истину. citeturn54view0turn54view1turn54view2 |
| general graphics docs | overdraw / fill-rate cost of transparency | среднее | это общая GPU-логика, не Fabric-specific. использовано только для cost model прозрачных particles. citeturn49search3turn49search4 |
| inspiration-only tutorials | youtube, reddit, random blogs | низкое | для code-level выводов я на них не опирался. |

### open questions that must be answered before implementation

без ответов на эти вопросы код написать можно, но polished vertical slice будет шататься:

- `mark` должен быть именно particle, или у вас допустим один decal/anchored quad вне particle system?
- anchor может сидеть только в блоке, или еще и в entity/hitbox. если entity тоже да, нужен другой payload identity.
- нужен ли deterministic replay только “между клиентами”, или еще и “между билдами”. если второе, rng-stream нельзя менять без versioning.
- block chips должны всегда зависеть от `BlockState`, или для cursed/organic surface нужен отдельный material class поверх ванили.
- `warn` обязан читаться из mid-range дистанции как gameplay cue, или это purely cinematic слой. от этого зависит, оставлять ли `warn_edge` после `20-24 м`.
- нужен ли debug-командный путь через `/particle`, или весь tuning будет только через custom payload/dev command. от этого зависит, добавлять ли отдельный complex debug particle.
- metal shard может рикошетить по геометрии, или это лишняя физика. если нет - `hasPhysics = false` везде, кроме maybe block chips.
- допускается ли один full-bright кадр в `burst_residue`, или вся emissive-энергия должна остаться только в `snap_crack` и `ignition_tick`.
- сколько одновременных Hairpin anchors реально может взорваться в один тик в vertical slice. это главный вход для окончательного particle budget.
- нужна ли цветовая реакция на biome lighting/shaderpacks, или палитра должна быть полностью art-locked. это влияет на то, насколько aggressively вы будете переопределять `getLightColor(float)`.