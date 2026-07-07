# **Архитектура боевой системы для Minecraft Fabric 1.21.8: Проектирование сервер-авторитарного кинематографичного PvP/PvE-движка**

## **Аннотация**

Настоящий технический отчет посвящен проектированию архитектуры сетевой боевой системы для платформы Minecraft Fabric (версия 1.21.8, среда исполнения Java 21). Основной технологический вызов проекта заключается в совмещении динамики файтинга, вдохновленного вселенной *Jujutsu Kaisen*, со строгим контролем серверного состояния и сетевой стабильностью в условиях стандартного игрового цикла Minecraft с частотой 20 тиков в секунду (TPS).  
В качестве базовой парадигмы выбрана полностью авторитарная серверная модель (server-authoritative) с немедленным клиентским визуальным предсказанием (client prediction) для минимизации субъективного времени отклика (input lag). Разрабатываемый фреймворк нацелен на запуск первого изолированного вертикального среза (способность «Шпилька» персонажа Нобара Кугисаки / Nobara Hairpin). Предлагаемая архитектура исключает тупиковые проектные решения при последующем масштабировании на десятки уникальных персонажей, систему Проклятой Энергии (Cursed Energy) и комплексные пространственные хитбоксы.

## **1\. Стейт-машина способностей и валидация**

Основой предсказуемого и безопасного выполнения способностей является детерминированная стейт-машина, функционирующая параллельно на стороне сервера (авторитарно) и клиента (визуально). Жизненный цикл любой боевой способности подразделяется на четыре последовательные фазы, жестко контролируемые по времени.

### **Фазовая структура жизненного цикла способности**

* **Фаза подготовки (Startup):** Временной интервал от момента инициации способности до генерации ее логического эффекта. В этой фазе проигрываются предварительные визуальные эффекты и анимации замаха, предупреждающие оппонентов о готовящейся атаке. Способность в этой фазе может быть прервана получением сильного урона (ошеломление/Stagger) или осознанным действием игрока (отмена/Cancel).  
* **Активная фаза (Active):** Окно времени, в течение которого генерируются боевые сущности (снаряды), создаются зоны поражения (зонные хитбоксы) и производится валидация попаданий на сервере.  
* **Фаза восстановления (Recovery):** Окно анимационного выхода из способности. Персонаж не может инициировать новые атаки, однако в определенные под-окна (Cancel Windows) допускается прерывание фазы защитными действиями, такими как уклонение или блок.  
* **Состояние покоя (Idle):** Готовность к обработке следующего кадра ввода.

          `[span_33](start_span)[span_33](end_span)      +----------------------------------------+`  
                `|                                        |`  
                `v                                        |`  
       `+------------------+     Запрос ввода     +---------------+`  
       `|       IDLE       | -------------------> |    STARTUP    |`  
       `+------------------+                      +---------------+`  
                `^                                        |`  
                `|                                        | Переход по тику`  
                `|                                        v`  
       `+------------------+                      +---------------+`  
       `|     RECOVERY     | <------------------- |    ACTIVE     |`  
       `+------------------+     Переход по тику  +---------------+`

### **Буферизация ввода (Buffered Input)**

Для компенсации сетевого джиттера и обеспечения отзывчивого интерфейса применяется окно буферизации ввода размером в 3 игровых тика (\\approx 150 мс). Если запрос на активацию следующего действия поступает в завершающей стадии фазы *Recovery*, он помещается в очередь и автоматически исполняется на первом доступном тике фазы *Idle*, исключая задержки из\-за неидеального тайминга игрока.

### **Окна отмены (Cancel Windows)**

Каждая фаза жестко размечена временными метками отмены. Отмена анимации (например, переход из *Active* в *Recovery* при успешном уклонении) регулируется сервером. Клиент отправляет запрос на отмену, сервер сверяет текущую фазу с разрешенным интервалом и при успешной валидации обновляет стейт-машину, прерывая воспроизведение исходной анимации на всех клиентах в зоне видимости.

### **Валидация целей на стороне сервера**

Клиент выполняет локальную трассировку лучей (raycasting) для отрисовки прицела и мгновенной визуальной реакции. Однако логическая проверка цели (Target Validation) целиком лежит на сервере. Сервер проверяет:

1. Принадлежность цели к списку живых сущностей (LivingEntity) в допустимом радиусе.  
2. Наличие препятствий (линии видимости через блоки) между атакующим и целью.  
3. Наличие активных кадров неуязвимости (i-frames) у жертвы.  
4. Соответствие энергетических ресурсов (наличие необходимого количества Cursed Energy).

## **2\. Тайминг в сетке 20 TPS**

Стандартный игровой цикл сервера Minecraft функционирует с частотой 20 тиков в секунду (длина тика \\tau \= 50 мс). Визуальное отображение на стороне клиента интерполируется с максимально возможной кадровой частотой (60+ FPS). Перенос точных данных файтинг-анимаций (Frame Data), рассчитанных на 60\\text{ Hz}, на серверную сетку 20\\text{ Hz} требует введения понятия *виртуальных кадров*.

### **Математическое преобразование кадров в тики**

Пусть способность определена в классических кадрах файтинга (F, при базовой частоте 60\\text{ Hz}). Перевод в серверные тики (T) выполняется с округлением в большую сторону:  
T \= \\left\\lceil \\frac{F \\cdot \\text{TPS}}{60} \\right\\rceil \= \\left\\lceil \\frac{F}{3} \\right\\rceil  
Таким образом, один логический тик Minecraft эквивалентен временному интервалу в три виртуальных кадра файтинга. Внутренний таймер анимаций на клиенте использует суб-тиковую интерполяцию через время рендеринга (render partial ticks), обеспечивая плавность отображения кинематографических эффектов независимо от фиксированного серверного такта.  
Для минимизации ощущения задержки ввода (input lag) и предотвращения читерства применяется строгое разделение ответственности за симуляцию различных аспектов боевого взаимодействия.

### **Распределение авторитета симуляции**

| Категория симуляции | Метод синхронизации | Авторитет |
| :---- | :---- | :---- |
| **Координаты движения** | Эвристическое предсказание, жесткая коррекция при расхождении \> 0.5 блока. | Сервер |
| **Вычисление урона** | Применяется только на сервере; клиент узнает урон из сетевых пакетов обратной связи. | Сервер |
| **Анимация персонажа** | Локальный запуск на клиенте в момент ввода; синхронизация через пакеты вещания для наблюдателей. | Клиент (визуально) / Сервер (логически) |
| **Эффекты частиц (VFX)** | Мгновенная генерация локально; сервер транслирует семантическое событие для окружения. | Клиент |

## **3\. Читаемость боя и геймдизайн**

В многопользовательской хаотичной среде визуальная чистота и понятность угроз критически важны для обеспечения честного противодействия (counterplay).

### **Телеграфирование угроз (Telegraphing)**

Каждая способность должна явно сообщать о своей подготовке до момента нанесения урона. Для «Шпильки» Нобары это реализуется двухэтапно:

* **Направленный VFX:** В момент начала фазы *Startup* вокруг игрока формируется сходящаяся спираль из частиц проклятой энергии, а в точке прицеливания проецируется тусклый геометрический маркер (красный круг из плоских частиц на поверхности блоков).  
* **Аудио-сигнал (SFX):** Воспроизведение уникального, высокочастотного позиционируемого звука замаха, который затухает по мере приближения к фазе *Active*.

### **Области угрозы (Threat Shapes) и хитбоксы**

Вместо стандартных параллелепипедов коллизий Minecraft (AABB) для сложных атак применяются направленные геометрические примитивы (сферы, конусы, секторы), рассчитываемые математически на сервере в момент перехода в фазу *Active*.  
Коническая область поражения перед игроком рассчитывается через скалярное произведение векторов направления взгляда атакующего \\vec{D} и направления на цель \\vec{V}:  
\\cos(\\theta) \= \\frac{\\vec{D} \\cdot \\vec{V}}{\\|\\vec{D}\\| \\|\\vec{V}\\|} \\ge \\cos\\left(\\frac{\\alpha}{2}\\right)  
где \\alpha — угол раскрытия конуса поражения, а длина вектора \\|\\vec{V}\\| не превышает максимальный радиус атаки R. При несоблюдении неравенства урон сервером отклоняется, даже если клиент визуально отобразил попадание частицы в цель.

### **Специфика PvE и PvP балансировки**

Архитектура закладывает раздельные коэффициенты для взаимодействия «Игрок-Моб» и «Игрок-Игрок» на уровне хранения данных способностей.  
В **PvE** акцент смещен на кинематографичность: враги имеют увеличенные хитбоксы, а способности игрока наносят повышенный урон по площади (AoE) с сильным отбрасыванием (knockback).  
В **PvP** радиус поражения конусов сужается на 25\\%, время активного окна сокращается, а реакция на попадание заменяется на фиксированное прерывание анимаций (Stagger/Posture damage) без хаотичного отбрасывания, ломающего позиционирование соперников.

## **4\. Хитстоп, камера и визуальный импакт**

Визуальный вес («сочность») боевых действий в файтингах достигается за счет мгновенной обратной связи. Однако в сервер-авторитарных играх нельзя останавливать глобальный игровой цикл для симуляции кинематографического замирания.

### **Хитстоп (Hitstop) без остановки сервера**

Эффект хитстопа (кратковременная остановка времени на кадрах удара) реализуется исключительно на стороне клиентов участников боя. На сервере симуляция продолжается без изменений, в то время как клиенты воспроизводят локальное замирание, детали которого зависят от роли игрока в сцене.

### **Профили эффектов хитстопа по ролям**

| Параметр эффекта | Профиль Атакующего | Профиль Жертвы | Профиль Наблюдателя |
| :---- | :---- | :---- | :---- |
| **Пауза анимации (playerAnimator)** | 4 тика на паузе (200 мс). | 6 тиков на паузе (300 мс). | Без паузы (плавное затухание скорости). |
| **Локальное дрожание камеры** | Высокая частота, малая амплитуда (фокус на оружии). | Низкая частота, высокая амплитуда (фокус на импакте). | Отсутствует (или минимально при расстоянии \<3 блоков). |
| **Изменение FOV (FOV Pulse)** | Кратковременное сжатие (зум) на \-10\\%. | Резкое расширение на \+15\\% на старте. | Не применяется. |
| **Вспышка экрана (Screen Flash)** | Тонкий сине-черный ореол (проклятая энергия). | Интенсивный красный/темно-красный градиент по краям. | Не применяется. |

### **Математика динамического дрожания камеры (Camera Shake)**

Дрожание камеры рассчитывается локально на клиенте с помощью затухающего гармонического колебания. Смещение камеры по осям Pitch и Yaw на шаге времени t (в секундах с момента удара) задается формулой:  
\\Delta\\theta(t) \= A \\cdot e^{-\\lambda t} \\sin(\\omega t \+ \\phi)  
где A — амплитуда (зависит от силы атаки), \\lambda — коэффициент демпфирования (затухания), \\omega — частота колебаний.  
Для предотвращения укачивания (motion sickness) и обеспечения комфорта в PvP:

1. Амплитуда A масштабируется глобальной настройкой доступности в конфигурационном файле клиента (значение от 0.0 до 1.0).  
2. В PvP-режимах угловое дрожание программно ограничивается значением \\Delta\\theta\_{\\max} \= 0.15^\\circ, чтобы не препятствовать удержанию прицела на цели.  
3. Ось Roll полностью исключается из расчетов дрожания для снижения вестибулярного дискомфорта.

## **5\. Сетевое взаимодействие**

Сетевой протокол Fabric 1.21.8 базируется на строгих контрактах сериализации через CustomPacketPayload и StreamCodec.

### **Схема сетевого обмена при активации способности**

1. Клиент отправляет на сервер пакет ServerboundAbilityRequestPayload с уникальным идентификатором способности и текущим вектором взгляда.  
2. Сервер производит валидацию ресурсов и кулдаунов.  
3. При успешной проверке сервер рассылает в радиусе видимости (tracking broadcast) пакет ClientboundSemanticEventPayload для запуска визуальных и звуковых эффектов на всех подключенных клиентах.  
4. В случае десинхронизации ресурсов или кулдаунов сервер отправляет инициирующему клиенту пакет принудительного отката состояния ClientboundAbilityRejectPayload.

### **Синхронизация визуальных эффектов через детерминированные сиды**

Для предотвращения сетевой перегрузки индивидуальными координатами частиц (per-particle networking) используется механизм детерминированных сидов (Deterministic Seeds). Сервер генерирует случайное число типа long (сид визуализации) и отправляет его внутри семантического события.  
Клиенты, получая пакет, инициализируют локальный генератор случайных чисел Random(seed). Это гарантирует, что случайный разброс гвоздей Нобары, направления осколков и углы расхождения частиц будут абсолютно идентичны на экранах всех наблюдателей без передачи координат каждого отдельного элемента по сети.

### **Компенсация задержки (Lag Compensation / Rewind)**

Для PvP-составляющей критически важно компенсировать пинг игроков, иначе атакующему придется стрелять «на опережение» мимо видимой модели врага. Сервер хранит кольцевой буфер пространственных положений (Transform History) всех сущностей за последние 20 тиков (1000 мс).  
При получении пакета атаки сервер вычисляет расчетный исторический тик T\_{\\text{rewind}}:  
T\_{\\text{rewind}} \= T\_{\\text{current}} \- \\left\\lceil \\frac{\\text{RTT}}{2 \\cdot 50} \\right\\rceil \- T\_{\\text{client\\\_render\\\_delay}}  
где \\text{RTT} — измеренное время кругового обхода пакета атакующего игрока (в миллисекундах), а T\_{\\text{client\\\_render\\\_delay}} — поправка на клиентскую интерполяцию сущностей (обычно составляет 1-2 тика).  
Сервер временно перемещает хитбоксы цели в координаты тика T\_{\\text{rewind}}, производит проверку пересечения луча или площади атаки, наносит урон и возвращает хитбоксы в текущий тик симуляции.

### **Данные, которые запрещено доверять клиенту**

* Количество нанесенного урона и тип накладываемых статусных эффектов.  
* Факт успешного пробития блока или уклонения.  
* Текущие показатели прочности брони, здоровья и Проклятой Энергии игрока.  
* Временные интервалы перезарядки (cooldowns) способностей.

## **6\. Дата-ориентированная архитектура способностей**

Для упрощения геймдизайнерского балансирования и возможности добавления новых персонажей без перекомпиляции Java-кода, все параметры способностей выносятся в JSON-файлы данных (Data Assets), считываемые через стандартную систему рецептов и реестров Minecraft посредством Codec.  
`{`  
  `"ability_id": "jjk:nobara_hairpin",`  
  `"resource_cost": {`  
    `"cursed_energy": 25.0`  
  `},`  
  `"timing_windows": {`  
    `"startup_ticks": 6,`  
    `"active_ticks": 2,`  
    `"recovery_ticks": 10,`  
    `"buffer_input_ticks": 3`  
  `},`  
  `"damage": {`  
    `"base_pve": 15.0,`  
    `"base_pvp": 8.0,`  
    `"posture_damage": 30.0`  
  `},`  
  `"physics": {`  
    `"nail_velocity": 1.8,`  
    `"detonation_radius": 3.5,`  
    `"knockback_coefficient": 1.2`  
  `},`  
  `"visuals": {`  
    `"startup_particle": "jjk:resonance_charge",`  
    `"active_particle": "jjk:cursed_nail_trail",`  
    `"cast_sound": "jjk:item.nobara.cast_resonance"`  
  `}`  
`}`

Для десериализации этой структуры на стороне сервера в Java 21 используется механизм RecordCodecBuilder.  
`public record AbilityConfig(`  
    `String abilityId,`  
    `ResourceCost resourceCost,`  
    `TimingWindows timingWindows,`  
    `DamageConfig damage,`  
    `PhysicsConfig physics,`  
    `VisualsConfig visuals`  
`) {`  
    `public static final Codec<AbilityConfig> CODEC = RecordCodecBuilder.create(instance ->`   
        `instance.group(`  
            `Codec.STRING.fieldOf("ability_id").forGetter(AbilityConfig::abilityId),`  
            `ResourceCost.CODEC.fieldOf("resource_cost").forGetter(AbilityConfig::resourceCost),`  
            `TimingWindows.CODEC.fieldOf("timing_windows").forGetter(AbilityConfig::timingWindows),`  
            `DamageConfig.CODEC.fieldOf("damage").forGetter(AbilityConfig::damage),`  
            `PhysicsConfig.CODEC.fieldOf("physics").forGetter(AbilityConfig::physics),`  
            `VisualsConfig.CODEC.fieldOf("visuals").forGetter(AbilityConfig::visuals)`  
        `).apply(instance, AbilityConfig::new)`  
    `);`  
`}`

`public record ResourceCost(double cursedEnergy) {`  
    `public static final Codec<ResourceCost> CODEC = RecordCodecBuilder.create(instance ->`  
        `instance.group(`  
            `Codec.DOUBLE.fieldOf("cursed_energy").forGetter(ResourceCost::cursedEnergy)`  
        `).apply(instance, ResourceCost::new)`  
    `);`  
`}`

`public record TimingWindows(int startupTicks, int activeTicks, int recoveryTicks, int bufferInputTicks) {`  
    `public static final Codec<TimingWindows> CODEC = RecordCodecBuilder.create(instance ->`  
        `instance.group(`  
            `Codec.INT.fieldOf("startup_ticks").forGetter(TimingWindows::startupTicks),`  
            `Codec.INT.fieldOf("active_ticks").forGetter(TimingWindows::activeTicks),`  
            `Codec.INT.fieldOf("recovery_ticks").forGetter(TimingWindows::recoveryTicks),`  
            `Codec.INT.fieldOf("buffer_input_ticks").forGetter(TimingWindows::bufferInputTicks)`  
        `).apply(instance, TimingWindows::new)`  
    `);`  
`}`

`public record DamageConfig(double basePve, double basePvp, double postureDamage) {`  
    `public static final Codec<DamageConfig> CODEC = RecordCodecBuilder.create(instance ->`  
        `instance.group(`  
            `Codec.DOUBLE.fieldOf("base_pve").forGetter(DamageConfig::basePve),`  
            `Codec.DOUBLE.fieldOf("base_pvp").forGetter(DamageConfig::basePvp),`  
            `Codec.DOUBLE.fieldOf("posture_damage").forGetter(DamageConfig::postureDamage)`  
        `).apply(instance, DamageConfig::new)`  
    `);`  
`}`

`public record PhysicsConfig(double nailVelocity, double detonationRadius, double knockbackCoefficient) {`  
    `public static final Codec<PhysicsConfig> CODEC = RecordCodecBuilder.create(instance ->`  
        `instance.group(`  
            `Codec.DOUBLE.fieldOf("nail_velocity").forGetter(PhysicsConfig::nailVelocity),`  
            `Codec.DOUBLE.fieldOf("detonation_radius").forGetter(PhysicsConfig::detonationRadius),`  
            `Codec.DOUBLE.fieldOf("knockback_coefficient").forGetter(PhysicsConfig::knockbackCoefficient)`  
        `).apply(instance, PhysicsConfig::new)`  
    `);`  
`}`

`public record VisualsConfig(String startupParticle, String activeParticle, String castSound) {`  
    `public static final Codec<VisualsConfig> CODEC = RecordCodecBuilder.create(instance ->`  
        `instance.group(`  
            `Codec.STRING.fieldOf("startup_particle").forGetter(VisualsConfig::startupParticle),`  
            `Codec.STRING.fieldOf("active_particle").forGetter(VisualsConfig::activeParticle),`  
            `Codec.STRING.fieldOf("cast_sound").forGetter(VisualsConfig::castSound)`  
        `).apply(instance, VisualsConfig::new)`  
    `);`  
`}`

### **Архитектурный компромисс против избыточного проектирования**

Для первого вертикального среза (Nobara Hairpin) создание обобщенного интерпретатора JSON-скриптов является избыточным. Архитектурный компромисс заключается в разделении данных и логики:

* **Данные (JSON-ресурс):** Описывают только численные константы, идентификаторы частиц и звуков.  
* **Логика (Java-код):** Реализует конкретное поведение способности (механику полета гвоздя и детонации). Java-класс считывает десериализованный JSON через Codec при старте сервера. Это обеспечивает гибкость настройки коэффициентов без усложнения кодовой базы интерпретаторами.

## **7\. Рекомендуемая минимальная архитектура для первого среза**

Для демонстрации первого polished slice (способность Nobara Hairpin) необходимо реализовать минимальный, но расширяемый набор компонентов.

### **Nobara Hairpin: Механика первого среза**

Способность разделена на два связанных действия:

1. **Бросок гвоздя (Nail Throw):** Обычная атака, спавнящая физический снаряд NailEntity. Гвоздь летит по прямолинейной траектории и втыкается в блоки или живые сущности (накладывая статус-эффект PinAttachment).  
2. **Резонанс / Шпилька (Hairpin Detonation):** При повторном прожатии способности все активные гвозди детонируют, нанося взрывной урон проклятой энергией и накладывая сильный хитстоп на пораженные цели.

### **Архитектурные компоненты первого среза**

* AbilityRegistry: Простой статический реестр способностей, ассоциирующий уникальный Identifier с инстансом класса Ability.  
* AbilityStateManager: Компонент, прикрепленный к игроку (через Fabric Data Attachment API), хранящий текущую фазу, оставшиеся тики до перехода в следующее состояние и буферизованный ввод.  
* NailEntity: Серверный снаряд, наследуемый от ProjectileEntity. Осуществляет проверку столкновений на сервере и синхронизирует позицию с клиентами через стандартный трекинг Minecraft.  
* VisualPredictionEngine: Клиентский синглтон, перехватывающий нажатие клавиши атаки, мгновенно запускающий локальную анимацию замаха рук игрока и создающий псевдо-снаряд на стороне клиента для плавного рендеринга полета до получения первого пакета от сервера.

## **8\. Будущая масштабируемая архитектура**

После успешной верификации первого среза, архитектура эволюционирует в полноценный событийно-ориентированный фреймворк способностей (Event-Driven Ability Framework).

* **Система Проклятой Энергии (Cursed Energy System):** Интегрируется как глобальный компонент сущности. Изменение пула энергии происходит через транзакции на сервере с последующей репликацией дельты на клиент. При падении уровня энергии ниже нуля стейт-машина прерывает выполнение способностей и переводит игрока в ослабленное состояние (Exhausted).  
* **Менеджер перезарядок (Cooldown Manager):** Обобщенная служба, отслеживающая кулдауны по тегам способностей (например, способность «Шпилька» вешает кулдаун на категорию jjk:resonance\_abilities), что позволяет реализовывать механики совместной перезарядки умений.  
* **Шина Боевых Событий (Combat Event Bus):** Позволяет модульно навешивать пассивные эффекты персонажей (например, пассивная способность увеличивает урон от взрывов проклятой энергии). Все этапы стейт-машины генерируют события на этой шине, изолируя логику конкретных эффектов от ядра движка способностей.

## **9\. Сетевой протокол и границы системы**

Для реализации сетевого взаимодействия в соответствии со стандартами современных версий Fabric API, ниже представлены спецификации сетевых пакетов, использующие Java Records и типы StreamCodec.

### **Спецификация сетевых пакетов (Fabric 1.21.8 / Java 21\)**

`public record ServerboundAbilityRequestPayload(`  
    `String abilityId,`  
    `double cameraX,`  
    `double cameraY,`  
    `double cameraZ,`  
    `float yaw,`  
    `float pitch,`  
    `long clientTimestamp`  
`) impleme[span_95](start_span)[span_95](end_span)[span_98](start_span)[span_98](end_span)nts CustomPacketPayload {`  
    `public static final CustomPacketPayload.Type<ServerboundAbilityRequestPayload> TYPE =`   
        `new CustomPacketPayload.Type<>(Identifier.of("jjk", "ability_request"));`

    `public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAbilityRequestPayload> CODEC =`   
        `StreamCodec.composite(`  
            `ByteBufCodecs.STRING_UTF8, ServerboundAbilityRequestPayload::abilityId,`  
            `ByteBufCodecs.DOUBLE, ServerboundAbilityRequestPayload::cameraX,`  
            `ByteBufCodecs.DOUBLE, ServerboundAbilityRequestPayload::cameraY,`  
            `ByteBufCodecs.DOUBLE, ServerboundAbilityRequestPayload::cameraZ,`  
            `ByteBufCodecs.FLOAT, ServerboundAbilityRequestPayload::yaw,`  
            `ByteBufCodecs.FLOAT, ServerboundAbilityRequestPayload::pitch,`  
            `ByteBufCodecs.VAR_LONG, ServerboundAbilityRequestPayload::clientTimestamp,`  
            `ServerboundAbilityRequestPayload::new`  
        `);`

    `@Override`  
    `public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {`  
        `return TYPE;`  
    `}`  
`}`

`public record ClientboundSemanticEventPayload(`  
    `double posX,`  
    `double posY,`  
    `double posZ,`  
    `int sourceEntityId,`  
    `int eventTypeCode,`  
    `long visualSeed`  
`) implements CustomPacketPayload {`  
    `public static final CustomPacketPayload.Type<ClientboundSemanticEventPayload> TYPE =`   
        `new CustomPacketPayload.Type<>(Identifier.of("jjk", "semantic_event"));`

    `public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSemanticEventPayload> CODEC =`   
        `StreamCodec.composite(`  
            `ByteBufCodecs.DOUBLE, ClientboundSemanticEventPayload::posX,`  
            `ByteBufCodecs.DOUBLE, ClientboundSemanticEventPayload::posY,`  
            `ByteBufCodecs.DOUBLE, ClientboundSemanticEventPayload::posZ,`  
            `ByteBufCodecs.VAR_INT, ClientboundSemanticEventPayload::sourceEntityId,`  
            `ByteBufCodecs.VAR_INT, ClientboundSemanticEventPayload::eventTypeCode,`  
            `ByteBufCodecs.VAR_LONG, ClientboundSemanticEventPayload::visualSeed,`  
            `ClientboundSemanticEventPayload::new`  
        `);`

    `@Override`  
    `public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {`  
        `return TYPE;`  
    `}`  
`}`

`public record ClientboundHitConfirmPayload(`  
    `int targetEntityId,`  
    `float damageDealt,`  
    `int hitstopTicks,`  
    `boolean isFatal`  
`) implements CustomPacketPayload {`  
    `public static final CustomPacketPayload.Type<ClientboundHitConfirmPayload> TYPE =`   
        `new CustomPacketPayload.Type<>(Identifier.of("jjk", "hit_confirm"));`

    `public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHitConfirmPayload> CODEC =`   
        `StreamCodec.composite(`  
            `ByteBufCodecs.VAR_INT, ClientboundHitConfirmPayload::targetEntityId,`  
            `ByteBufCodecs.FLOAT, ClientboundHitConfirmPayload::damageDealt,`  
            `ByteBufCodecs.VAR_INT, ClientboundHitConfirmPayload::hitstopTicks,`  
            `ByteBufCodecs.BOOL, ClientboundHitConfirmPayload::isFatal,`  
            `ClientboundHitConfirmPayload::new`  
        `);`

    `@Override`  
    `public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {`  
        `return TYPE;`  
    `}`  
`}`

### **Таблица распределения ответственности между клиентом и сервером**

| Игровой домен | Обработка на Клиенте (Client-Side) | Обработка на Сервере (Server-Side) |
| :---- | :---- | :---- |
| **Жизненный цикл снаряда (Гвоздь)** | Рендерит локальную меш-модель снаряда, спавнит частицы следа на каждом кадре рендеринга, воспроизводит свист полета. | Проверяет коллизии гвоздя с миром каждые 50 мс, рассчитывает падение скорости, регистрирует застревание в блоках/сущностях. |
| **Детонация (Шпилька)** | Генерирует локальный взрыв частиц, воспроизводит раскатистый грохот детонации, запускает дрожание камеры и вспышку. | Проверяет радиус поражения, наносит урон проклятой энергией, рассчитывает пробитие осанки (Posture Damage). |
| **Анимация (Атакующий)** | Мгновенно запускает локальное воспроизведение анимации замаха через playerAnimator при нажатии клавиши. | Переводит состояние игрока в STARTUP, транслирует сетевой ID анимации всем наблюдателям в зоне стриминга пакетов. |
| **Регистрация урона** | Отображает урон через всплывающие индикаторы и окрашивает модель цели в красный цвет при получении подтверждения. | Проверяет соответствие таймингов тика, рассчитывает итоговый урон с учетом брони и i-frames, обновляет здоровье жертвы. |

\#\# 10\. Процедура верификации и анти-паттерны  
Процесс контроля качества боевой системы разделен на пять специализированных этапов (проходов) тестирования, автоматические тесты и многопользовательские испытания.

### **Пять этапов геймдизайн-тестирования**

1. **Проход ясности (Clarity Pass):** Визуальный и аудио-анализ в контролируемых условиях. Тестировщик оценивает, насколько отчетливо читается фаза *Startup* у соперника на расстоянии 15 блоков в условиях плохой видимости (ночь, дождь). Световой маркер угрозы должен контрастировать с окружением.  
2. **Проход честности (Fairness Pass):** Анализ соотношения времени подготовки атаки и времени реакции. Время фазы *Startup* проверяется на соответствие времени уклонения. Способность не должна наносить моментальный урон без возможности уклониться или поставить блок.  
3. **Проход выразительности (Expression Pass):** Проверка сочности импакта (Juicy Impact). Анализируется плавность перехода в хитстоп, корректность амплитуды дрожания камеры в зависимости от расстояния до взрыва, а также выразительность FOV-пульсации.  
4. **Проход противодействия (Counterplay Pass):** Оценка доступных игровых опций во время боя. Проверяется, прерывается ли каст способности при получении критического удара, и успевает ли защищающийся игрок провести контратаку во время фазы *Recovery* атакующего.  
5. **Проход читаемости в хаосе (Readability-under-Chaos Pass):** Стресс-тест визуальных эффектов в групповом сражении (формат 5х5 игроков). При одновременном использовании способностей визуальные частицы и маркеры угроз не должны превращать экран в сплошное цветовое облако, скрывающее силуэты персонажей и критические фазы замахов.

### **Чек-лист многопользовательского смоук-тестирования (Multiplayer Smoke Tests)**

* \[ \] **Тест симуляции задержки:** Запустить тестовый сервер с искусственной задержкой пакетов в 150 мс и джиттером в 15 мс (через утилиту tc в Linux или локальный прокси-сервер).  
  * *Ожидаемый результат:* Атакующий игрок при стрельбе точно по модели движущегося соперника регистрирует попадания стабильно. Модели жертв не возвращаются назад скачками при срабатывании системы лаг-компенсации.  
* \[ \] **Тест на спам пакетами активации:** Имитировать отправку вредоносным клиентом пакетов ServerboundAbilityRequestPayload со скоростью 100 пакетов в секунду.  
  * *Ожидаемый результат:* Сервер отклоняет все некорректные запросы, не создавая утечек памяти и не перегружая планировщик задач. Стейт-машина игрока корректно выдерживает кулдауны.  
* \[ \] **Тест на схождение траекторий снарядов:** Выпустить 50 гвоздей под углом к камере при пинге в 200 мс.  
  * *Ожидаемый результат:* Локально предсказанный на клиенте полет гвоздя плавно синхронизируется с авторитарной серверной траекторией. Дергания, дублирование моделей снарядов или их застревание в воздухе отсутствуют.  
* \[ \] **Тест на устойчивость хитстопа при дисконнекте:** Разорвать соединение с атакующим игроком в момент его нахождения в фазе хитстопа.  
  * *Ожидаемый результат:* Сервер и другие клиенты корректно выводят модель отключившегося игрока из зависшего анимационного состояния без падения серверного потока.

### **Анти-паттерны проектирования, исключенные из архитектуры**

* **Использование исключительно длинных кулдаунов в качестве баланса:** Длинные таймеры перезарядки делают игровой процесс медленным и вязким. Вместо этого баланс строится вокруг расхода Проклятой Энергии, риска прерывания атаки на фазе *Startup* и уязвимости игрока в фазе *Recovery*.  
* **Визуальный шум, скрывающий угрозы:** Чрезмерное количество частиц проклятой энергии затрудняет чтение действий оппонента. Архитектура вводит жесткие лимиты на плотность спавна частиц и обязывает использовать четко очерченные геометрические маркеры зон поражения.  
* **Клиент-авторитарный расчет урона:** Принятие сервером готовых значений урона от клиента открывает возможность для читерства. Все вычисления урона, проверка брони и применение статус-эффектов производятся исключительно на сервере.  
* **Передача координат каждой отдельной частицы по сети (Per-particle Networking):** Попытка синхронизировать координаты всех визуальных эффектов перегружает пропускную способность сетевого канала. Сеть передает только семантические события с детерминированными сидами, а физика частиц рассчитывается клиентами локально.  
* **Использование одинаковых эффектов частиц для разных по свойствам атак:** Способности, имеющие разные хитбоксы и тайминги, не должны использовать идентичные визуальные ресурсы. Это лишает игроков возможности мгновенно идентифицировать тип угрозы.  
* **Проектирование глобального фреймворка до проверки первого вертикального среза:** Попытка создать универсальный абстрактный движок для десятков персонажей до полировки механики «Шпильки» Нобары ведет к архитектурному тупику. Сначала разрабатывается и полируется один срез, после чего выявляются общие паттерны для абстрагирования.  
* **Балансировка PvE и PvP при помощи общих неизменяемых констант:** Попытка использовать один и тот же урон для монстров и живых игроков рушит баланс в одном из режимов. Архитектура изначально закладывает раздельные PvE/PvP коэффициенты в конфигурационные файлы способностей.

## **11\. Источники и уровни достоверности**

Разработка архитектуры опирается на официальные технические спецификации сетевого взаимодействия современных версий Minecraft и проверенные временем паттерны сетевой синхронизации соревновательных игр.

### **Источники информации и оценка достоверности**

| Категория источника | Специфика используемых данных | Уровень достоверности | Влияние на архитектуру |
| :---- | :---- | :---- | :---- |
| **Официальная документация FabricMC** | Спецификации сетевого взаимодействия, пакетов и сериализаторов для Minecraft 1.21+. | **Максимальный** | На основе этих данных спроектирована структура CustomPacketPayload с использованием Java Records и StreamCodec\[span\_139\](start\_span)\[span\_139\](end\_span). |
| **Архивы и спецификации сетевых движков (Source SDK, Unity Netcode)** | Алгоритмы лаг-компенсации, назад-направленного анализа времени (rewind) и предсказания ввода на клиенте. | **Высокий** | На основе этих данных спроектирован алгоритм расчета исторического тика T\_{\\text{rewind}} и буфер пространственных состояний целей. |
| **Технические отчеты инди-модификаций (playerAnimator, Better Combat)** | Практика управления анимациями игроков в Minecraft, реализация клиентского хитстопа и кастомных коллизий. | **Высокий** | Разработано разделение ответственности при остановке анимации на клиентах без заморозки серверного тика. |

#### **Источники**

1\. Networking \- Fabric Documentation, https://docs.fabricmc.net/develop/networking 2\. MSWHendys/Eroded\_World: Hardcore survival overhaul mod for Minecraft Fabric 1.21.8 featuring energy-based movement, dynamic mining instability, environmental collapse events, and crafting quality tiers · GitHub, https://github.com/MSWHendys/Eroded\_World 3\. DiolezHitRegistry \- Minecraft Mod \- Modrinth, https://modrinth.com/mod/diolezhitregistry 4\. Lag Compensation \- Server-Side Timeline Shifting \- Fatshark Forums, https://forums.fatsharkgames.com/t/lag-compensation-server-side-timeline-shifting/117518 5\. Definitely not making a massively multiplayer game : r/gamedev \- Reddit, https://www.reddit.com/r/gamedev/comments/1u12ufh/definitely\_not\_making\_a\_massively\_multiplayer\_game/ 6\. UE5 shooter framework with lag compensation, kill cams and client-predicted inventory., https://www.reddit.com/r/UnrealEngine5/comments/1u1yrwm/ue5\_shooter\_framework\_with\_lag\_compensation\_kill/ 7\. Fast-Paced Multiplayer (Part IV): Lag Compensation \- Gabriel Gambetta, https://www.gabrielgambetta.com/lag-compensation.html 8\. Client prediction/rollback for things it can't predict (other players' actions) \- Reddit, https://www.reddit.com/r/gamedev/comments/1o8oktx/client\_predictionrollback\_for\_things\_it\_cant/ 9\. Top 10 Minecraft Animation Mods by Community Downloads \- CurseForge Blog, https://blog.curseforge.com/top-minecraft-animation-mods/ 10\. ZsoltMolnarrr/BetterCombat: ⚔️ Easy, spectacular and fun melee combat system from Minecraft Dungeons. \- GitHub, https://github.com/ZsoltMolnarrr/BetterCombat 11\. Tidy Up Time \- News \- The Indie Stone Forums, https://theindiestone.com/forums/index.php?/topic/72103-tidy-up-time/ 12\. Networking \- Fabric Wiki, https://wiki.fabricmc.net/tutorial:networking 13\. Camera Overhaul \- Minecraft Mod \- Modrinth, https://modrinth.com/mod/cameraoverhaul 14\. Fabric for Minecraft 1.21.6, 1.21.7 & 1.21.8, https://fabricmc.net/2025/06/15/1216.html 15\. Better Combat Particle \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/better-combat-particle 16\. \[Archived\] Rewind \- Server-Authoritative Hit Validation & Custom Character Replication, https://devforum.roblox.com/t/archived-rewind-server-authoritative-hit-validation-custom-character-replication/4160622 17\. Riposte \- Minecraft Mod \- Modrinth, https://modrinth.com/mod/riposte 18\. Combat Expansion V4 REWRITTEN \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/combat-expansion 19\. Better Combat \- Heavier Weapons \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/better-combat-heavier-weapons 20\. Lag Compensation \- Server-Side Timeline Shifting \- Page 2 \- Fatshark Forums, https://forums.fatsharkgames.com/t/lag-compensation-server-side-timeline-shifting/117518?page=2 21\. camera shake \- Minecraft Mods \- CurseForge, https://www.curseforge.com/minecraft/mc-mods/camera-shake 22\. LoganDark/fabric-camera-shake \- GitHub, https://github.com/LoganDark/fabric-camera-shake 23\. How to create a "View Bobbing" effect \[simple tutorial\] : r/gamedev \- Reddit, https://www.reddit.com/r/gamedev/comments/1lweud/how\_to\_create\_a\_view\_bobbing\_effect\_simple/ 24\. Commands/camerashake \- Minecraft Wiki \- Fandom, https://minecraft.fandom.com/wiki/Commands/camerashake 25\. camerashake Command | Microsoft Learn, https://learn.microsoft.com/en-us/minecraft/creator/commands/commands/camerashake?view=minecraft-bedrock-stable 26\. NeoForge 20.5 for Minecraft 1.20.5, https://neoforged.net/news/20.5release/ 27\. Uses of Interface net.minecraft.network.protocol.common.custom.CustomPacketPayload, https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/network/protocol/common/custom/class-use/CustomPacketPayload.html 28\. Lag Compensation \- Valve Developer Community, https://developer.valvesoftware.com/wiki/Lag\_Compensation 29\. feat(network): server-side lag compensation and hit-detection rewind · Issue \#425 \- GitHub, https://github.com/fighters-legacy/fighters-legacy/issues/425 30\. Server-side rewind | Netcode for Entities | 1.7.0 \- Unity \- Manual, https://docs.unity3d.com/Packages/com.unity.netcode@1.7/manual/server-rewind.html 31\. Server Rewind (Lag Compensation) \- SnapNet, https://www.snapnet.dev/docs/unreal-engine-sdk/manual/server-rewind/ 32\. Codecs \- Minecraft Forge Documentation, https://docs.minecraftforge.net/en/latest/datastorage/codecs/ 33\. Data Assets \- Hytale Modding, https://hytalemodding.dev/en/docs/established-information/server/content-categories/data-assets 34\. Codec purpose? \- Modder Support \- Minecraft Forge Forums, https://forums.minecraftforge.net/topic/93050-codec-purpose/ 35\. Is this the optimal way to load custom JSON using Codec in NeoForge 1.21.5? Looking for advice \#2433 \- GitHub, https://github.com/neoforged/NeoForge/discussions/2433 36\. How would you implement an easy-expanded, data-driven ability system? : r/Unity2D, https://www.reddit.com/r/Unity2D/comments/1q49z1n/how\_would\_you\_implement\_an\_easyexpanded/ 37\. Registering Payloads | NeoForged docs, https://docs.neoforged.net/docs/networking/payload/ 38\. Best Hytale Mods: Modding with Cursor & More \- Hone Blog, https://hone.gg/blog/hytale-mods/