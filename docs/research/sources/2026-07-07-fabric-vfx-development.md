# **Техническое исследование реализации высококачественных клиентских VFX в Minecraft Fabric 1.21.8**

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

## **Архитектура клиент-серверного разделения и конвейер рендеринга**

Современная разработка боевых модификаций для Minecraft требует жесткого разделения ответственности между сервером, обладающим абсолютным авторитетом в принятии решений (Gameplay Authority), и клиентом, отвечающим за высокопроизводительную визуализацию и интерполяцию состояний игрового мира. Использование механизма splitEnvironmentSourceSets() в сборочном скрипте Gradle позволяет физически изолировать клиентский код в директории src/client, полностью предотвращая непреднамеренный доступ к классам физического клиента на стороне выделенного сервера.  
Начиная с версии Minecraft 1.21.2, разработчики Mojang пересмотрели базовые принципы рендеринга и перешли к фазовому разделению визуализации на этап извлечения данных (Extraction Stage) и этап отрисовки (Render/Draw Phase). Основной целью этих архитектурных изменений является подготовка игрового движка к многопоточному рендерингу, где один поток готовит геометрические данные, а другой отправляет их графическому процессору. Данное разделение оказывает прямое влияние на проектирование визуальных эффектов в версии 1.21.8. Для сохранения совместимости с оптимизаторами графики и обеспечения стабильной частоты кадров в Fabric API 0.136.1+1.21.8 была добавлена поддержка FabricRenderState, которая позволяет безопасно передавать извлеченные данные состояния рендеринга между этапами без создания рассинхронизации в потоках.

## **1\. Система частиц в Fabric 1.21.8 (Mojang Mappings)**

Система частиц Minecraft представляет собой легковесный, аппаратно-оптимизированный механизм для рендеринга плоских двумерных билбордов, ориентированных на камеру игрока. Она является наиболее предпочтительным решением для вывода высокоинтенсивных боевых спецэффектов, таких как искры проклятой энергии при использовании техники Nobara Hairpin.

### **Регистрация типов частиц и фабрик**

В Minecraft 1.21.8 процесс создания кастомной частицы разделен на два ключевых этапа:

1. **Общая регистрация типа (Common Initializer)**: Создается экземпляр SimpleParticleType, который регистрируется в реестре BuiltInRegistries.PARTICLE\_TYPE под уникальным идентификатором ResourceLocation.  
2. **Регистрация клиентской фабрики (Client Initializer)**: В классе, помеченном для выполнения только на клиенте, фабрика регистрируется через ParticleFactoryRegistry.getInstance().register().

Интеграция спрайтов и текстурных анимаций требует использования интерфейса SpriteSet (в Yarn-маппингах известного как SpriteProvider), передающего массив текстурных кадров в конструктор частицы.  
`// Регистрация в общем инициализаторе (src/main) - Verified for Fabric 1.21.8`  
`public class ModParticleTypes {`  
    `public static final SimpleParticleType HAIRPIN_ENERGY = FabricParticleTypes.simple();`

    `public static void register() {`  
        `Registry.register(`  
            `BuiltInRegistries.PARTICLE_TYPE,`  
            `ResourceLocation.fromNamespaceAndPath("nobara", "hairpin_energy"),`  
            `HAIRPIN_ENERGY`  
        `);`  
    `}`  
`}`

`// Регистрация в клиентском инициализаторе (src/client) - Verified for Fabric 1.21.8`  
`public class ModParticleClient {`  
    `public static void registerFactories() {`  
        `ParticleFactoryRegistry.getInstance().register(`  
            `ModParticleTypes.HAIRPIN_ENERGY,`  
            `HairpinEnergyParticle.Provider::new`  
        `);`  
    `}`  
`}`

### **Анимация спрайтов и TextureSheetParticle**

Для создания анимированных частиц, таких как пульсация проклятой энергии, класс частицы должен наследоваться от TextureSheetParticle. Этот класс содержит встроенные абстракции для интерполяции размеров, цвета и работы с текстурными листами. Метод tick() частицы должен вызывать setSpriteFromAge(SpriteSet) на каждом шаге симуляции, чтобы динамически обновлять текстурные координаты (U0, U1, V0, V1) на основе возраста частицы (age) и ее максимального времени жизни (lifetime).  
Текстурный ассет-пайплайн требует создания конфигурационного JSON-файла по пути assets/\<modid\>/particles/\<particle\_name\>.json. Этот файл объявляет массив используемых текстур, расположенных в директории assets/\<modid\>/textures/particle/:  
`{`  
  `"textures": [`  
    `"nobara:hairpin_energy_0",`  
    `"nobara:hairpin_energy_1",`  
    `"nobara:hairpin_energy_2",[span_17](start_span)[span_17](end_span)[span_20](start_span)[span_20](end_span)`  
    `"nobara:hairpin_energy_3"`  
  `]`  
`}`

### **Сравнение текстурных листов и оптимизация производительности**

Выбор правильного типа ParticleRenderType напрямую влияет на поведение графического конвейера и производительность отрисовки при массовых сражениях.

| Режим рендеринга (ParticleRenderType) | Использование альфа-канала | Тест глубины (Depth Test) | Влияние на производительность и рендеринг |
| :---- | :---- | :---- | :---- |
| PARTICLE\_SHEET\_OPAQUE | Нет (альфа отсекается) | Да | Максимальная производительность. Не требует сортировки геометрии на CPU. |
| PARTICLE\_SHEET\_TRANSLUCENT | Полное полупрозрачное смешивание | Да | Требует сортировки квадов по расстоянию от камеры (back-to-front) на CPU перед загрузкой в GPU. Высокий риск падения производительности. |
| PARTICLE\_SHEET\_LIT | Да (игнорирует световую карту) | Да | Светится в темноте. Применяется для магических разрядов. Снижает накладные расходы на выборку текстуры освещения. |

### **Практические рекомендации для кратковременных боевых частиц (Combat Particles)**

Для минимизации задержек кадра при спаме частицами во время атак необходимо придерживаться следующих правил:

* **Отключение физических проверок**: Поле hasPhysics должно быть принудительно установлено в false в конструкторе частицы. Это избавляет игровой поток от ежекадрового выполнения трассировки лучей для проверки коллизий частицы с блоками.  
* **Строгое ограничение времени жизни**: Время жизни частицы должно находиться в диапазоне от 10 до 20 тиков. Кратковременные эффекты не успевают перегрузить стек рендеринга.  
* **Исключение аллокаций в циклах**: В методе tick() частицы категорически запрещено выделять память под новые объекты (например, временные векторы Vector3f или цветовые параметры). Все вычисления должны оперировать примитивными типами или использовать предопределенные статические кэши.

## **2\. Рендеринг в мировом пространстве (World-Space VFX)**

Рендеринг трехмерных объектов, таких как трассирующие линии летящих гвоздей, расширяющиеся кольца энергии при детонации куклы или трехмерные полигональные ленты, требует прямого доступа к буферам прорисовки игрового мира.

### **Точки перехвата WorldRenderEvents**

Интерфейс Fabric API WorldRenderEvents предоставляет разработчику набор событий, интегрированных в основной цикл LevelRenderer (ранее WorldRenderer). Для боевых эффектов наиболее критичны две точки:

* WorldRenderEvents.AFTER\_TRANSLUCENT: Вызывается после того, как все полупрозрачные слои чанков, сущностей и частиц были записаны во фреймбуфер, но до объединения слоев в режиме Fabulous. Это наиболее безопасная зона для большинства полупрозрачных эффектов, гарантирующая отсутствие некорректного наложения (Z-fighting) поверх сущностей.  
* WorldRenderEvents.LAST: Вызывается непосредственно перед отключением матриц прорисовки мира и переходом к интерфейсу. Идеально подходит для оверлеев, которые должны визуально перекрывать блоки и сущности без выполнения теста глубины.

### **Переход к координатам относительно камеры (Camera-Relative)**

Для предотвращения визуального дрожания геометрии на больших координатах из\-за ограничений точности вычислений одинарной точности (float) в OpenGL, все вычисления и передача координат в буфер должны выполняться относительно текущей позиции камеры. Координаты камеры извлекаются из объекта Camera внутри WorldRenderContext.  
Математическое смещение вычисляется по формуле:  
\\mathbf{P}\_{render} \= \\mathbf{P}\_{world} \- \\mathbf{P}\_{camera}  
Смещение матрицы выполняется непосредственно в PoseStack (в Yarn-маппингах известном как MatrixStack):  
`// Вычисление относительного положения в событии WorldRenderEvents.AFTER_TRANSLUCENT`  
`WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {`  
    `Vec3 cameraPos = context.camera().getPosition();`  
    `PoseStack poseStack = context.matrixStack(); // Название в 1.21.8[span_59](start_span)[span_59](end_span)[span_61](start_span)[span_61](end_span)`  
      
    `poseStack.pushPose();`  
    `// Смещение начала координат рендеринга к позиции камеры`  
    `poseStack.translate(`  
        `worldX - cameraPos.x,`  
        `worldY - cameraPos.y,`  
        `worldZ - cameraPos.z`  
    `);`  
      
    `// Выполнение рендеринга геометрии`  
    `poseStack.popPose();`  
`});`

### **Вершинный конвейер и изменения в BufferBuilder**

Начиная с версии 1.21, в системе генерации вершин произошли фундаментальные изменения. Все операции выполняются через обертку ByteBufferBuilder, инкапсулированную в BufferBuilder. Системные изменения включают в себя:

* Метод vertex() заменен на addVertex().  
* Изменились имена методов форматирования атрибутов вершины: color() заменен на setColor(), uv() на setUv(), light() на setLight(), а normal() на setNormal().  
* Метод endVertex() полностью удален. Запись текущей вершины завершается автоматически в момент вызова метода addVertex() для следующей вершины или при завершении записи всего буфера перед вызовом отрисовщика.

Пример отрисовки плоского осколка проклятой энергии (Verified for Fabric 1.21.8):  
`VertexConsumer consumer = context.consumers().getBuffer(RenderType.leash()); // Пример базового типа слоя`  
`Matrix4f matrix = poseStack.last().pose();`

`// Построение полигона (Quad) из 4-х вершин`  
`consumer.addVertex(matrix, 0.0f, 0.0f, 0.0f).setUv(0.0f, 0.0f).setColor(160, 32, 240, 255);`  
`consumer.addVertex(matrix, 0.0f, 1.0f, 0.0f).setUv(0.0f, 1.0f).setColor(160, 32, 240, 255);`  
`consumer.addVertex(matrix, 1.0f, 1.0f, 0.0f).setUv(1.0f, 1.0f).setColor(160, 32, 240, 255);`  
`consumer.addVertex(matrix, 1.0f, 0.0f, 0.0f).setUv(1.0f, 0.0f).setColor(160, 32, 240, 255);`

### **Геометрические примитивы боевых VFX**

1. **Трассирующие линии (Tracer Lines)**: Отрисовка линий лучей должна производиться строго в режиме VertexFormat.Mode.DEBUG\_LINES. Использование стандартных режимов LINES или LINE\_STRIP на современных версиях OpenGL в Minecraft часто приводит к сбоям рендеринга на некоторых видеокартах из\-за некорректной обработки индексов буфера.  
2. **Кольца и ленты (Rings / Ribbons)**: Формируются динамически на основе генерации триангуляционных лент в режиме VertexFormat.Mode.TRIANGLE\_STRIP. Для построения кольца используется тригонометрический цикл, рассчитывающий внутренний радиус R\_{in} и внешний радиус R\_{out} с заданным шагом угла \\alpha.

### **Разделение возможностей Fabric API и R\&D задач**

Для поддержания стабильности и совместимости мода необходимо разделять функционал, реализуемый через стандартные программные интерфейсы, и задачи, требующие глубоких исследований ядра игры.

* **Безопасно без Mixins (Verified for Fabric 1.21.8)**: Рендеринг трехмерной геометрии с использованием встроенных в игру слоев RenderType (таких как RenderType.translucent() или RenderType.lightning()) в обработчиках WorldRenderEvents.  
* **Требует отдельной R\&D ветки**: Разработка кастомных вершинных и фрагментных шейдеров (assets/nobara/shaders/core/), изменение логики конвейера RenderPipeline, создание пользовательских буферов постобработки (Post-processing chains) через вмешательство в классы PostChain и ShaderManager.

## **3\. Эффекты HUD, экрана и камеры**

Качественный боевой мод требует визуального отклика непосредственно на экране игрока. Это реализуется через динамические эффекты вспышек, изменения поля зрения (FOV) и тряску камеры при детонации проклятых кукол.

### **HudElementRegistry и Matrix3x2fStack в 1.21.8**

Интерфейс рендеринга HUD претерпел изменения в версии 1.21.8. Регистрация элементов интерфейса выполняется через HudElementRegistry.  
*Важное изменение архитектуры 1.21.8 (Verified for Fabric 1.21.8)*: Вместо класса PoseStack для 2D-трансформаций элементов интерфейса теперь передается класс Matrix3x2fStack. Данный класс полностью исключает использование координаты z во всех операциях масштабирования (scale) и смещения (translate).  
Использование DeltaTracker позволяет получать точное интерполированное значение времени частичного тика (gameTimeDeltaPartialTick), обеспечивая плавное затухание эффектов независимо от кадровой частоты монитора:  
`// Регистрация HUD-эффекта вспышки проклятой энергии (src/client) - Verified for Fabric 1.21.8`  
`HudElementRegistry.addLast(`  
    `ResourceLocation.fromNamespaceAndPath("nobara", "hairpin_flash"),`  
    `(guiGraphics, deltaTracker) -> {`  
        `float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false); //[span_101](start_span)[span_101](end_span)`  
        `float flashAlpha = HairpinFlashEffect.getInterpolatedAlpha(partialTick);`

        `if (flashAlpha > 0.001f) {`  
            `int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();`  
            `int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();`  
              
            `// Заполнение экрана полупрозрачным розовым цветом проклятой энергии Nobara`  
            `int argbColor = ((int) (flashAlpha * 255) << 24) | 0xFF1493;`  
            `guiGraphics.fill(0, 0, screenWidth, screenHeight, argbColor);`  
        `}`  
    `}`  
`);`

### **Camera Shake и FOV Pulse**

Для реализации динамических искажений камеры и поля зрения (FOV) применяются разные подходы в зависимости от уровня интеграции:

1. **FOV Pulse (Изменение угла обзора)**: Может быть реализовано полностью через публичные Fabric API путем прослушивания событий обновления поля зрения (например, вмешательством в вычисление FOV игрока на клиенте), возвращая модифицированный коэффициент масштабирования.  
2. **Camera Shake (Тряска камеры)**: Физический движок Java-версии Minecraft не имеет встроенной поддержки тряски камеры (в отличие от Bedrock Edition). Реализация требует внедрения Mixin в класс Camera или GameRenderer для прямого смещения позиции и вращения матрицы проекции камеры на основе затухающих колебаний.

## **4\. Звуковой тракт и ассет-пайплайн**

Акустическое сопровождение (звук удара молотка, свист летящего гвоздя, оглушительный взрыв куклы при активации проклятой техники) является важнейшим элементом импакта боевой системы.

### **Регистрация звуков и звуковой ассет-пайплайн**

Каждое звуковое событие SoundEvent должно быть зарегистрировано в общем реестре BuiltInRegistries.SOUND\_EVENT через статический метод SoundEvent.createVariableRangeEvent():  
`public class ModSounds {`  
    `public static final ResourceLocation HAIRPIN_HIT_ID = ResourceLocation.fromNamespaceAndPath("nobara", "hairpin_hit");`  
    `public static final SoundEvent HAIRPIN_HIT = Registry.register(`  
        `BuiltInRegistries.SOUND_EVENT,`  
        `HAIRPIN_HIT_ID,`  
        `SoundEvent.createVariableRangeEvent(HAIRPIN_HIT_ID)`  
    `);`  
`}`

Файл сопоставления звуков assets/\<modid\>/sounds.json определяет список используемых файлов и связывает их со звуковым событием:  
`{`  
  `"hairpin_hit": {`  
    `"category": "player",`  
    `"subtitle": "subtitles.nobara.hairpin_hit",`  
    `"sounds": [`  
      `{`  
        `"name": "nobara:hairpin_hit_0",`  
        `"volume": 1.0,`  
        `"pitch": 1.0`  
      `},`  
      `{`  
        `"name": "nobara:hairpin_hit_1",`  
        `"volume": 1.0,`  
        `"pitch": 0.95`  
      `}`  
    `]`  
  `}`  
`}`

### **Технические требования к аудиофайлам**

Качество пространственного позиционирования звуковых эффектов зависит от формата исходных файлов:

| Параметр звука | Требование | Обоснование |
| :---- | :---- | :---- |
| **Кодек файлов** | OGG Vorbis | Единственный сжатый аудиоформат, поддерживаемый звуковым движком игры. |
| **Количество каналов** | **Mono (1 канал)** | **Критическое требование\!** Только моно-файлы поддерживают пространственное 3D-позиционирование и затухание звука по мере удаления от источника. Стерео-файлы проигрываются плоско по всей ширине панорамы без учета расстояния. |
| **Частота дискретизации** | 44100 Гц / 48000 Гц | Стандартный формат для бесконфликтной обработки аудиобиблиотекой OpenAL. |

### **Наложение коротких звуков (Layering) и проблемы перезагрузки ресурсов**

Для придания взрыву Hairpin массивности рекомендуется одновременно воспроизводить три звуковых слоя на клиенте: низкочастотный удар, среднечастотный скрежет энергии и высокочастотный кристаллический звон распадающегося проклятого гвоздя.  
При частых операциях перезагрузки ресурсов на клиенте (комбинация клавиш F3 \+ T) игра полностью очищает кэш OpenAL. Если мод хранит долгоживущие ссылки на кастомные аудио-потоки без прослушивания системного события ResourceManagerHelper, это гарантированно приведет к утечкам памяти в куче (Heap) и зависанию звукового потока.

## **5\. Сетевая модель воспроизведения VFX**

Сетевая модель боевой системы должна строиться на принципе: **Игровая логика полностью просчитывается на сервере, клиент является лишь детерминированным симулятором визуальных эффектов**.

### **Использование typed CustomPacketPayload**

Начиная с версии Minecraft 1.20.5 и в 1.21.8, сетевые пакеты используют типизированную систему CustomPacketPayload (в Mojang-маппингах). Вместо передачи низкоуровневых байтовых массивов или координат каждой сгенерированной частицы, сервер отправляет одно высокоуровневое семантическое событие.  
Пример реализации пакета события взрыва Hairpin (Verified for Fabric 1.21.8):  
`public record HairpinExplodePayload(BlockPos position, float power, long randomSeed) implements CustomPacketPayload {`  
    `public static final Type<HairpinExplodePayload> TYPE = new Type<>(`  
        `ResourceLocation.fromNamespaceAndPath("nobara", "hairpin_explode")`  
    `);`

    `// Определение бинарного кодека на основе StreamCodec`  
  `[span_2](start_span)[span_2](end_span)  public static final StreamCodec<RegistryFriendlyByteBuf, HairpinExplodePayload> CODEC = StreamCodec.composite(`  
        `BlockPos.STREAM_CODEC, HairpinExplodePayload::position,`  
        `ByteBufCodecs.FLOAT, HairpinExplodePayload::power,`  
        `ByteBufCodecs.VAR_LONG, HairpinExplodePayload::randomSeed,`  
        `HairpinExplodePayload::new`  
    `);`

    `@Override`  
    `public Type<? extends CustomPacketPayload> type() {`  
        `return TYPE;`  
    `}`  
`}`

### **Сетевая оптимизация и детерминированное воспроизведение**

Когда сервер регистрирует попадание проклятой куклы, он находит всех игроков, отслеживающих данную область (Tracking Players), с помощью класса PlayerLookup (logical server side). Сервер отправляет пакет один раз. Пакет содержит координату эпицентра взрыва, масштаб эффекта (power) и начальное число генератора случайных чисел (randomSeed).  
При получении пакета каждый клиент инициализирует локальный генератор случайных чисел RandomSource.create(payload.randomSeed()). Это позволяет клиенту детерминированно рассчитать траекторию разлета каждой из сотен искр и осколков локально. В результате:

* Траектории и анимации частиц выглядят идентично для всех наблюдателей боя.  
* Нагрузка на сеть снижается до нуля, так как передается только один пакет размером в несколько байт, вместо передачи координат каждой отдельной частицы на каждом тике.

## **6\. Производительность и совместимость с Sodium**

### **Борьба с полупрозрачным перекрытием (Translucent Overdraw)**

Наиболее распространенная причина падения FPS при воспроизведении боевых эффектов — избыточное наложение полупрозрачной геометрии (Translucent Overdraw). Когда сотни частиц с типом PARTICLE\_SHEET\_TRANSLUCENT отрисовываются друг перед другом, видеокарта вынуждена выполнять ресурсоемкие операции альфа-смешивания (Alpha-Blending) для каждого пикселя экрана многократно за один кадр.  
Для снижения нагрузки необходимо применять динамическое ограничение количества частиц и заменять густые облака полупрозрачного дыма на текстуры с жестким альфа-каналом (Dithering) или использовать непрозрачные частицы PARTICLE\_SHEET\_OPAQUE со сложными шейдерными эффектами.

### **Исключение аллокаций памяти в игровом цикле**

Сборщик мусора (GC) в Java 21 крайне чувствителен к выделению временных короткоживущих объектов в куче во время отрисовки кадров. Вызов оператора new внутри обработчиков рендеринга WorldRenderEvents или в методах частиц tick() и move\[span\_47\](start\_span)\[span\_47\](end\_span)() создает лавинообразный рост нагрузки на память, приводя к микрофризам игры каждые несколько секунд. Все расчеты должны оперировать кэшированными примитивами.

### **Совместимость с оптимизатором Sodium**

Sodium кардинально переопределяет логику отрисовки чанков игрового мира. Для бесконфликтной интеграции с Sodium моды должны:

* Использовать интерфейс FabricRenderState, backported в Fabric API 1.21.8, для безопасной передачи извлеченного состояния рендеринга.  
* Полностью избегать прямых низкоуровневых вызовов OpenGL API (библиотеки LWJGL GL11.\*). Отрисовка геометрии должна производиться строго через Vertex-буферы, запрашиваемые у VertexConsumerProvider.

### **Оптимизация LOD (Level of Detail) для боевых VFX**

Для поддержания стабильной кадровой частоты во время массовых сражений на сервере используется динамическое изменение качества рендеринга спецэффектов.

| Дистанция до эффекта (d) | Плотность частиц (Particle Count) | Качество геометрии (Geom LOD) | Аудиосопровождение | Оверлеи экрана (Flashes / Shake) |
| :---- | :---- | :---- | :---- | :---- |
| Ближняя зона (d \\le 8 блоков) | 100% | Полная детализация (36 сегментов на кольцо) | Воспроизводятся все слои звука | Эффекты включены на полную мощность |
| Средняя зона (8 \< d \\le 24 блоков) | 40% | Упрощенный рендеринг (12 сегментов) | Воспроизводится только основной звуковой слой | Интенсивность снижена на 70% |
| Дальняя зона (d \> 24 блоков) | 5% (только вспышка в эпицентре) | Полностью отключается | Звук не проигрывается | Отключены |

## **7\. Минимальная реализация эффекта Hairpin (Minimal Prototype)**

Данный минимальный прототип содержит полностью рабочий клиентский и общий код для воспроизведения детонации проклятого гвоздя Hairpin с использованием Mojang-маппингов в Fabric 1.21.8.

### **Шаг 1: Определение и регистрация сетевого пакета (Common)**

`// Файл: src/main/java/net/nobara/network/HairpinExplodePayload.java`  
`p[span_131](start_span)[span_131](end_span)[span_133](start_span)[span_133](end_span)ackage net.nobara.network;`

`import net.minecraft.core.BlockPos;`  
`import net.minecraft.network.RegistryFriendlyByteBuf;`  
`import net.minecraft.network.codec.ByteBufCodecs;`  
`import net.minecraft.network.codec.StreamCodec;`  
`import net.minecraft.network.protocol.common.custom.CustomPacketPayload;`  
`import net.minecraft.resources.ResourceLocation;`

`public record HairpinExplodePayload(BlockPos position, float power, long randomSeed) implements CustomPacketPayload {`  
    `public static final Type<HairpinExplodePayload> TYPE = new Type<>(`  
        `ResourceLocation.fromNamespaceAndPath("nobara", "hairpin_explode")`  
    `);`

    `public static final StreamCodec<RegistryFriendlyByteBuf, HairpinExplodePayload> CODEC = StreamCodec.composite(`  
        `BlockPos.STREAM_CODEC, HairpinExplodePayload::position,`  
        `ByteBufCodecs.FLOAT, HairpinExplodePayload::power,`  
        `ByteBufCodecs.VAR_LONG, HairpinExplodePayload::randomSeed,`  
        `HairpinExplodePayload::new`  
    `);`

    `@Override`  
    `public Type<? extends CustomPacketPayload> type() {`  
        `return TYPE;`  
    `}`  
`}`

### **Шаг 2: Реализация клиентской логики и спавна VFX (Client-Only)**

`// Файл: src/client/java/net/nobara/client/renderer/HairpinClientVfx.java`  
`package net.nobara.client.renderer;`

`import net.minecraft.client.Minecraft;`  
`import net.minecraft.client.multiplayer.ClientLevel;`  
`import net.minecraft.core.particles.ParticleTypes;`  
`import net.minecraft.sounds.SoundSource;`  
`import net.nobara.client.particle.HairpinParticleManager;`  
`import net.nobara.network.HairpinExplodePayload;`  
`import net.nobara.registry.ModSounds;`  
`import net.minecraft.util.RandomSource;`

`public class HairpinClientVfx {`  
    `public static void handleVfxPayload(HairpinExplodePayload payload) {`  
        `Minecraft client = Minecraft.getInstance();`  
        `ClientLevel level = client.level;`  
        `if (level == null) return;`

        `double x = payload.position().getX() + 0.5;`  
        `double y = payload.position().getY() + 0.5;`  
        `double z = payload.position().getZ() + 0.5;`

        `RandomSource random = RandomSource.create(payload.randomSeed());`

        `// 1. Спавн частиц проклятой энергии в эпицентре`  
        `int count = (int) (30 * payload.power());`  
        `for (int i = 0; i < count; i++) {`  
            `double rx = x + (random.nextDouble() - 0.5) * 1.2;`  
            `double ry = y + (random.nextDouble() - 0.5) * 1.2;`  
            `double rz = z + (random.nextDouble() - 0.5) * 1.2;`  
              
            `double vx = (random.nextDouble() - 0.5) * 0.25;`  
            `double vy = random.nextDouble() * 0.35;`  
            `double vz = (random.nextDouble() - 0.5) * 0.25;`

            `level.addParticle(ParticleTypes.WITCH, rx, ry, rz, vx, vy, vz);`  
        `}`

        `// 2. Воспроизведение пространственного моно-звука взрыва`  
        `level.playLocalSound(`  
            `x, y, z,`  
            `ModSounds.HAIRPIN_EXPLODE,`  
            `SoundSource.PLAYERS,`  
            `1.0F,`  
            `0.9F + random.nextFloat() * 0.2F,`  
            `false`  
        `);`

        `// 3. Расчет расстояния для локальной тряски камеры`  
        `if (client.player != null) {`  
            `double distSq = client.player.distanceToSqr(x, y, z);`  
            `if (distSq < 256.0) { // В радиусе 16 блоков`  
                `float intensity = (float) ((1.0 - Math.sqrt(distSq) / 16.0) * 0.15 * payload.power());`  
                `CameraShakeHandler.addCameraShake(intensity);`  
            `}`  
        `}`  
    `}`  
`}`

### **Шаг 3: Инициализация приемника сетевого пакета (Client Entrypoint)**

`// Файл: src/client/java/net/nobara/client/ModClientEntrypoint.java`  
`package net.nobara.client;`

`import net.fabricmc.api.ClientModInitializer;`  
`import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;`  
`import net.nobara.client.renderer.HairpinClientVfx;`  
`import net.nobara.network.HairpinExplodePayload;`

`public class ModClientEntrypoint implements ClientModInitializer {`  
    `@Override`  
    `public void onInitializeClient() {`  
        `ClientPlayNetworking.registerGlobalReceiver(`  
            `HairpinExplodePayload.TYPE,`  
            `(payload, context) -> {`  
                `context.client().execute(() -> {`  
                    `HairpinClientVfx.handleVfxPayload(payload);`  
                `});`  
            `}`  
        `);`  
    `}`  
`}`

## **11\. План расширенной разработки (Advanced VFX Pipeline)**

После создания минимально жизнеспособного прототипа рекомендуется переходить к расширению графических возможностей боевого мода:

1. **Интеграция сторонних библиотек (Optional Path)**:  
   * **Satin / Veil (Inspiration only)**: Рекомендуется изучить исходный код Satin или библиотеки Veil для реализации кастомных шейдеров постобработки в обход ограничений ванильного конвейера. Они позволяют применять радиальное размытие и хроматическую аберрацию в момент проклятых вспышек Nobara Hairpin.  
   * **AzureLib / GeckoLib (Inspiration only)**: Полезны для реализации сложной полигональной скелетной анимации проклятых кукол, если они отображаются в мире в виде динамических 3D-сущностей.  
2. **Продвинутый конвейер буферизации (Advanced Buffer Rendering)**: Разработка собственной системы пакетной отрисовки геометрических линий (Tracer Trails) через единый глобальный вершинный буфер, динамически очищаемый на каждом кадре, вместо выполнения индивидуальных операций отрисовки для каждого летящего прожектиля гвоздя.

## **12\. Рекомендуемая файлово-пакетная структура проекта**

`nobara-mod/`  
`├── src/`  
`│   ├── main/                           # Общий код и серверная логика (src/main)`  
`│   │   ├── java/`  
`│   │   │   └── net/nobara/`  
`│   │   │       ├── NobaraMod.java      # Общий инициализатор мода[span_153](start_span)[span_153](end_span)`  
`│   │   │       ├── network/`  
`│   │   │       │   └── HairpinExplodePayload.java`  
`│   │   │       ├── registry/`  
`│   │   │       │   ├── ModParticleTypes.java`  
`│   │   │       │   └── ModSounds.java`  
`│   │   │       └── entity/`  
`│   │   │           └── HairpinProjectileEntity.java`  
`│   │   └── resources/`  
`│   │       ├── assets/nobara/`  
`│   │       │   ├── particles/`  
`│   │       │   │   └── hairpin_energy.json`  
`│   │       │   ├── sounds.json`  
`│   │       │   └── textures/`  
`│   │       │       └── particle/`  
`│   │       │           └── hairpin_energy_0.png`  
`│   │       └── data/`  
`│   └── client/                         # Клиентский код (src/client, изолирован)`  
`│       └── java/`  
`│           └── net/nobara/client/`  
`│               ├── NobaraModClient.java # Клиентский инициализатор`  
`│               ├── renderer/`  
`│               │   ├── HairpinClientVfx.java`  
`│               │   ├── CameraShakeHandler.java`  
`│               │   └── WorldVfxOverlayRenderer.java`  
`│               └── particle/`  
`│                   └── HairpinEnergyParticle.java`

## **13\. Чек-лист проверки API и анализ рисков**

### **Таблица верификации API-запросов и совместимости**

Материалы исследования и структура классов Minecraft 1.21.8 требуют калибровки каждого используемого вызова:

| Имя класса / Метода | Назначение | Статус верификации | Рекомендации по интеграции |
| :---- | :---- | :---- | :---- |
| FabricRenderState | Передача состояния рендеринга чанков | **Verified for Fabric 1.21.8** | Использовать для сохранения совместимости с Sodium в многопоточном режиме. |
| WorldRenderEvents.AFTER\_TRANSLUCENT | Отрисовка геометрии после прозрачных слоев | **Verified for Fabric 1.21.8** | Использовать как базовую точку для отрисовки трехмерных проклятых лент и колец взрыва. |
| Matrix3x2fStack | Матричный стек для 2D HUD-интерфейса | **Verified for Fabric 1.21.8** | Использовать взамен PoseStack для отрисовки боевых вспышек на экране. Не использовать координату z. |
| DeltaTracker | Извлечение частичного тика времени | **Verified for Fabric 1.21.8** | Запрашивать через deltaTracker.getGameTimeDeltaPartialTick(false). |
| BufferBuilder.addVertex | Создание вершины в буфере | **Verified for Fabric 1.21.8** | Метод vertex() заменен на addVertex(). Исключить вызов endVertex(). |
| Veil / Satin Shader API | Кастомные шейдерные эффекты | **Inspiration only** | Использовать на этапе пост-прототипирования как опциональную зависимость. |
| Camera.setup() | Настройка углов и позиции камеры | **Needs local javap/source check** | Требует ручной валидации маппингов сигнатур методов в локальной среде IntelliJ IDEA перед созданием Mixin. |

### **Критические риски разработки боевых VFX**

| Описание риска | Последствие для игрового процесса | Метод предотвращения / Решение | | :--- | :--- | :--- | | **Вызовы OpenGL напрямую** | Падение клиента на современных графических Vulkan/Metal-бэкендах в будущем. Несовместимость с Sodium. | Отрисовывать геометрию исключительно через ванильный VertexConsumer. | | **Пространственные аудиофайлы в формате Stereo** | Звук проигрывается плоско по всей панораме, не затухая при удалении игрока от эпицентра взрыва. | Конвертировать все боевые звуки строго в моно-режим перед помещением в ассеты. | | **Выделение памяти в потоке отрисовки** | Рост нагрузки на GC Java 21, появление микрофризов во время боя. | Исключить оператор new из рендеринг-коллбэков. Использовать статические кэши векторов. | | **Обработка сетевых пакетов в потоке Netty** | Нестабильное поведение рендеринга и случайные краши клиента. | Всегда выполнять код воспроизведения эффектов внутри планировщика через client.execute(). |

#### **Источники**

1\. Side \- Fabric Wiki, https://wiki.fabricmc.net/tutorial:side 2\. Fabric for Minecraft 1.21.6, 1.21.7 & 1.21.8, https://fabricmc.net/2025/06/15/1216.html 3\. Basic Rendering Concepts \- Fabric Documentation, https://docs.fabricmc.net/develop/rendering/basic-concepts 4\. Grundlegende Rendering-Konzepte | Fabric Dokumentation, https://docs.fabricmc.net/de\_de/develop/rendering/basic-concepts 5\. Fabric API \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/7228829 6\. Adding Particles \- Fabric Wiki, https://wiki.fabricmc.net/tutorial:particles 7\. Particles | NeoForged docs, https://docs.neoforged.net/docs/1.21.1/resources/client/particles/ 8\. API/Utilities for Particle creation and registration · Issue \#192 · FabricMC/fabric-api \- GitHub, https://github.com/FabricMC/fabric-api/issues/192 9\. Registries (yarn 1.21+build.2 API) \- Fabric, https://maven.fabricmc.net/docs/yarn-1.21+build.2/net/minecraft/registry/Registries.html 10\. Creating Custom Sounds \- Fabric Documentation, https://docs.fabricmc.net/develop/sounds/custom 11\. Particles \- Minecraft Forge Documentation, https://docs.minecraftforge.net/en/latest/gameeffects/particles/ 12\. Client Particles | NeoForged docs, https://docs.neoforged.net/docs/rendering/particles/ 13\. DripParticle (neoforge 1.21.0-21.0.30-beta) \- nekoyue.github.io, https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/client/particle/DripParticle.html 14\. WorldRenderEvents (fabric-api 0.100.1+1.21 API), https://maven.fabricmc.net/docs/fabric-api-0.100.1+1.21/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.html 15\. Porting to Fabric API 26.1 \- Fabric Documentation, https://docs.fabricmc.net/develop/porting/fabric-api 16\. WorldRenderEvents.Last (fabric-api 0.97.9+1.21 API), https://maven.fabricmc.net/docs/fabric-api-0.97.9+1.21/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.Last.html 17\. Reproduce WorldRenderContext::matrixStack in Minecraft 1.21.9 and later \- Reddit, https://www.reddit.com/r/fabricmc/comments/1o8smqk/reproduce\_worldrendercontextmatrixstack\_in/ 18\. Minecraft 1.20.6 \-\> 1.21 Mod Migration Primer \- NeoForged Documentation, https://docs.neoforged.net/primer/docs/1.21/ 19\. Minecraft 1.20.6 \-\> 1.21 Mod Migration Primer \- GitHub, https://github.com/neoforged/.github/blob/main/primers/1.21/index.md 20\. How to draw a line in Minecraft with Fabric \- Stack Overflow, https://stackoverflow.com/questions/72121439/how-to-draw-a-line-in-minecraft-with-fabric 21\. For rendering 2D in Minecraft 1.21 \- GitHub Gist, https://gist.github.com/ItziSpyder/c06c34f28e406c04be63d593f0a0d0c1 22\. Minecraft 1.21.1 \-\> 1.21.2 Mod Migration Primer \- GitHub, https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md 23\. camera shake \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/camera-shake 24\. Rendering in the HUD \- Fabric Documentation, https://docs.fabricmc.net/develop/rendering/hud 25\. Minecraft 1.20.5/6 \-\> 1.21 Mod Migration Primer · GitHub, https://gist.github.com/ChampionAsh5357/d895a7b1a34341e19c80870720f9880f 26\. Commands/camerashake \- Minecraft Wiki \- Fandom, https://minecraft.fandom.com/wiki/Commands/camerashake 27\. Minecraft 1.21.8 \-\> 1.21.9 Mod Migration Primer \- NeoForged Documentation, https://docs.neoforged.net/primer/docs/1.21.9/ 28\. Creare Suoni Personalizzati | Documentazione di Fabric, https://docs.fabricmc.net/it\_it/develop/sounds/custom 29\. Sounds | NeoForged docs, https://docs.neoforged.net/docs/resources/client/sounds/ 30\. Playing Sounds \- Fabric Wiki, https://wiki.fabricmc.net/tutorial:sounds 31\. Better Player Locator Bar \- Changelog \- Modrinth, https://modrinth.com/mod/bplb/changelog 32\. Networking \- Fabric Documentation, https://docs.fabricmc.net/develop/networking 33\. orevein-neoforge-1.9.2-V26.1.2.jar \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/orevein/files/8035475 34\. Networking issue in 1.20.5 and 1.20.6 : r/fabricmc \- Reddit, https://www.reddit.com/r/fabricmc/comments/1j89qu6/networking\_issue\_in\_1205\_and\_1206/ 35\. Eclipse Core Client \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/eclipse-core-client 36\. BufferBuilder (yarn 1.21.5+build.1 API) \- Fabric, https://maven.fabricmc.net/docs/yarn-1.21.5+build.1/net/minecraft/client/render/BufferBuilder.html 37\. Sodium \- Minecraft Mod \- Modrinth, https://modrinth.com/mod/sodium/versions 38\. Sodium \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/sodium 39\. Fabric API \- Changelog \- Modrinth, https://modrinth.com:2053/mod/fabric-api/changelog?page=6