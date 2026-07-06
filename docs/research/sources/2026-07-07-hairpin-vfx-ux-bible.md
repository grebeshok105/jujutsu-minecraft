# vfx/ux библия для hairpin нобары кугисаки в minecraft fabric

## executive summary

hairpin стоит делать не как “еще один взрыв”, а как технику удаленного пробоя и расширения проклятой энергии из уже поставленных гвоздей. в каноне hairpin всегда читабелен через точку привязки: сначала есть гвоздь, потом короткое подтверждение активации, потом энергия не столько разлетается во все стороны, сколько резко вырастает из места фиксации и ломает цель, окружение или траекторию боя. это видно в трех ключевых применениях: против момо hairpin использует промахнувшиеся гвозди как ловушку в окружении, против кечизу - как добивающий детонатор гвоздя в голове, против махито - как двухшаговую постановку, где первый взрыв выставляет гвозди в нужное положение, а второй уже пробивает ноги и фиксирует цель. citeturn5view0turn14view2turn14view0turn14view1turn13view1turn13view0turn13view2

для polished vertical slice это дает очень чистую игровую формулу: **mark -> warn -> snap -> burst -> residue**. основной читаемый объект - не облако, а именно “заряженный гвоздь” как threat marker. основной цветовой код должен уходить в темный кармин, черно-вишневый, грязную фуксию и холодный металл, а не в оранжево-желтый огонь. официальная product-репрезентация hairpin тоже подчеркивает не огненный шар, а огромный гвоздь и агрессивный, шипастый, почти жидко-кровавый эффект вокруг него. citeturn15view0turn18image4turn18image6

в minecraft это лучше всего переводится в гибрид: **детерминированные маркеры и шипы - геометрия/рендер**, **пыль, мелкие осколки и residue - particles**, **подтверждение активации - короткий hud/camera accent на локальном клиенте**. fabric прямо разделяет такие сценарии: частицы подходят для ambience и спецэффектов, а устойчивые world-space объекты лучше рисовать через отдельный render path или block entity renderer; для сетевой синхронизации придется держать server-authoritative состояние и рассылать его пакетами, потому что даже в singleplayer логический сервер все равно существует. citeturn25search1turn26search1turn28search0turn26search0

## канон hairpin и таблица фаз

hairpin - это extension technique техники straw doll. канонически он работает только через уже заряженные гвозди нобары: она либо раньше вбивает их в цель, либо рассаживает по окружению, после чего удаленно заставляет проклятую энергию в них расшириться и детонировать. это ключевое отличие от resonance: resonance требует “связь” с целью через часть тела и соломенную куклу-эффиджи, а hairpin работает от физически присутствующих гвоздей и не нуждается в кукле как прокси. у resonance логика - проклятый резонанс по связи, у hairpin - локальная детонация и расширение энергии из гвоздя. citeturn6view0turn5view0

в бою нобара обычно сначала создает условия для hairpin, а не жмет его сходу. против момо она намеренно допускает промахи, чтобы нашпиговать деревья вокруг траектории полета. против кечизу у нее уже есть гвоздь в голове цели после black flash-связки, и hairpin становится быстрым добиванием. против махито она использует hairpin как тактическую двухтактную постановку: первый акт - сбить внимание и выставить гвозди вертикально, второй - пробить нижнюю часть тела и зафиксировать цель. citeturn14view2turn14view0turn14view1turn13view1turn13view0turn13view2

что видит цель и что видит наблюдатель, зависит от сцены, но паттерн один и тот же. цель видит “точку опасности” очень близко к себе: гвоздь в теле, у ног или в объекте рядом. сторонний наблюдатель видит больше геометрии события: падающие деревья, огромный шип проклятой энергии из головы кечизу, либо выставление и последующий рост гвоздей под махито. значит, для mod-версии нужно одновременно уважить два слоя читаемости: **локальный страх точки** и **дальний силуэт события**. citeturn14view2turn14view0turn14view1turn13view1turn13view0turn13view2

| фаза | канон-условие | что делает нобара | что видит цель | что видит сторонний наблюдатель | вывод для vertical slice |
|---|---|---|---|---|---|
| постановка | гвозди уже заряжены и размещены в цели или окружении | стреляет, промахивается намеренно, забивает, разводит угол атаки | отдельные гвозди или “безопасный” мусор в окружении | подготовку поля боя | обязательный persistent nail marker |
| warning | перед детонацией есть краткий жест или тактическая пауза; в эпизоде с кечизу она быстро щелкает перед активацией | подтверждает активацию, переключает бой из melee/ranged в remote detonation | поздно понимает, что гвоздь уже “живой” | считывает смену ритма боя | нужен микро-wind-up, 1 читаемый audio cue |
| compression | энергия концентрируется в гвозде | удерживает темп, не растягивает позу | чувствует, что опасность идет из точки привязки | видит, что центр эффекта - гвоздь, не общий blast sphere | bloom должен сидеть на nail head/shaft |
| detonation | проклятая энергия “расширяется с взрывной силой” | добивает, ломает окружение или фиксирует цель | получает резкий локальный пробой | видит шип, разлом, пробивной burst | шипастый burst, не tnt-ball |
| residue | краткий след после удара | сразу продолжает бой или переходит в следующую технику | ошеломление, immobilize или смерть | остаточные осколки, мусор, след энергии | короткий afterglow, чтобы не рвать событие на куски |

источники: описание hairpin и straw doll technique, а также разборы эпизодов и глав с момо, кечизу и махито. citeturn5view0turn6view0turn14view2turn13view1turn14view0turn13view0turn14view1turn13view2

## визуальный и звуковой язык hairpin

канонический костяк визуала - **гвоздь + расширение проклятой энергии**. fandom-сводка, ссылающаяся на конкретные главы и эпизоды, формулирует hairpin как технику, где энергия вокруг гвоздей расширяется со взрывной силой; в главе 61 это вообще описано как “giant spike of cursed energy”, пробивающий голову кечизу. официальный аниме-мерч по hairpin отдельно подчеркивает “eye-catching giant nail” и “intense effect”, то есть визуальный центр сцены - не плазменное облако, а огромный гвоздь и агрессивный выброс вокруг него. citeturn5view0turn14view0turn15view0turn18image4turn18image6

отсюда хорошая художественная интерпретация для мода такая: **60-70% темный кроваво-карминовый и черно-вишневый**, **15-25% грязная фуксия/магентовый акцент для cursed-energy edge**, **10-15% холодный металл гвоздя**, **солома - только вторичный мотив в иконографии, текстуре следа или ui-символе, а не в основном мейн-бласте**. соломенный мотив уместен как тонкая отсылка к ritual lineage техники и ее связи с straw doll/ushi no toki mairi, но если покрасить весь эффект в “бежевую магию”, hairpin потеряет жесткость и threat readability. ритуальная база с гвоздями, молотком и соломенным эффиджи у straw-doll техники реально существует как фольклорная отсылка, но для production-дизайна это лучше держать как inspiration-only, а не как буквальную реконструкцию. citeturn6view0turn23view0turn21search0

звуково hairpin должен читаться как **ударный металл -> заряд/напряжение -> сухая детонация -> короткий хвост**, а не как один универсальный “бум”. это следует из самой хореографии техники: у нее есть гвоздь как физический носитель, есть отдельный акт активации, и только потом локальный burst из точки фиксации. в сценах с кечизу и махито канон подчеркивает именно эту дискретность: сначала placement или уже существующий nail state, потом trigger, потом burst. citeturn14view0turn13view0turn14view1turn13view2

| слой | функция | канон-опора | рекомендация для мода |
|---|---|---|---|
| nail marker | показать, где именно сидит угроза | hairpin всегда исходит из заряженных гвоздей в цели или окружении | жесткий металлический силуэт, едва пульсирующий curse rim |
| pre-trigger bloom | предупредить о скорой активации | нобара сначала подтверждает активацию, затем происходит burst | короткий magenta-crimson на 1-3 тика без полной засветки |
| main burst | сам hairpin | “expands with explosive force”, у кечизу - giant cursed-energy spike | не сфера, а шипастый направленный выброс из nail anchor |
| shock/fracture | слом окружения или фиксация тела | у момо рушит деревья, у махито выставляет и пробивает ноги | разлет острых фрагментов, щепы, каменной пыли, черных брызг |
| residue | сохранить ощущение cursed event | после burst остается визуальная память удара | короткий afterglow 6-12 тиков, без долгого дыма |
| hammer hit | физическая “земля” техники | toolset straw doll technique - hammer + nails | сухой короткий удар, почти без реверба |
| metallic ping | идентификация гвоздя | nail-centered техника | высокий звон на nail placement или armed-state |
| charge/vibration | ощущение cursed compression | detonation наступает после концентрации в гвозде | очень короткий гул или тремор перед snap |
| detonation | кульминация | локальный burst из точки | сухой sharp burst, меньше баса чем у tnt |
| decay tail | не обрубать эффект | residue после cursed burst | 120-250 мс шершавого хвоста, будто пыль и металл оседают |

источники по канону и визуальному центру сцены. цветовые пропорции и звукоряд - это уже production interpretation, а не прямой канон. citeturn5view0turn14view0turn15view0turn18image4turn18image6

## перевод hairpin в minecraft fabric

для блока лучший путь - хранить “застрявшие гвозди” как серверное состояние в block entity или похожем persistent container и рисовать их через block entity renderer, потому что fabric отдельно рекомендует block entities для дополнительных данных блока, а динамическую отрисовку поверх блока - через block entity renderer. это дает стабильные координаты, predictable interpolation и контроль над дальностью/детализацией. citeturn28search5turn28search0

для сущностей лучше не пытаться решить все particles-ами. fabric-доки прямо разводят задачи: particles хороши для ambience и fx, а устойчивые world-space объекты требуют либо врезки в существующий пайплайн, либо собственного world rendering path. значит, вонзенные в моба гвозди лучше делать как детерминированную геометрию или управляемый world-space overlay, привязанный к uuid цели и индексам точек попадания; пыль, крошка, кроваво-черные дребезги и afterglow можно уже добивать particles-ами. citeturn25search1turn26search1

warning phase должна быть читаемой даже в мультиплеере, потому что у hairpin есть честная каноническая логика задержки и постановки. лучший паттерн тут такой: как только сервер подтверждает armed nails, клиент рисует на них тонкий пульсирующий outline и очень короткий pre-bloom. если nail embedded в земле, outline растет вверх на полблока. если в сущности - вокруг точки сидит маленький шип-ореол. в шибуе канон вообще показывает, что первый hairpin у махито использовался как подготовка позиции, а не как финальный урон, и именно поэтому “warning by placement” для этой техники не просто геймдизайн-фокус, а попадание в исходную хореографию. citeturn14view1turn13view2

чтобы собрать bloom -> afterglow в одно непрерывное событие, не надо спавнить пять несвязанных систем. minecraft уже поддерживает интерполяцию display entities, а fabric hud/world rendering статьи отдельно подчеркивают важность плавной анимации во времени. practically это означает один “timeline object” на nail cluster: он проходит стадии armed, bloom, burst, residue по общей кривой, а отдельные эмиттеры только подписаны на фазу. тогда после взрыва не будет ощущения, что bloom исчез и внезапно появился другой чужой effect. citeturn26search20turn26search22turn26search6

для multiplayer читаемости и сетевой устойчивости состояние hairpin должно быть server-authoritative: сервер хранит список nail anchors, owner, fuse state и detonation tick; клиент получает не поток мелких апдейтов каждый кадр, а 2-3 пакетных события - nail_embedded, hairpin_armed, hairpin_detonate. fabric networking-docs отдельно напоминают, что пакеты используются всегда, даже в singleplayer, так что эту архитектуру лучше закладывать сразу. локальные camera/hud accents при этом можно оставить purely client-side, чтобы не плодить сетевой шум и не ломать spectator readability. citeturn26search0turn26search12

## референсные тайминги

minecraft живет в ритме 20 тиков в секунду, то есть 1 тик = 50 мс. из этого удобно собрать две версии hairpin: **cinematic prototype**, где читаемость и стиль чуть важнее скорости, и **gameplay version**, где техника должна быстро замыкать контур “метка -> угроза -> детон”. citeturn27search1

для cinematic prototype я бы держал такой ритм: anticipation 8-12 тиков, compression 2-3 тика, detonation 1-2 тика, decay 10-16 тиков. это дает 1-1.6 секунды на все событие, что уже хорошо воспринимается как законченное аниме-действие со сценическим дыханием. для gameplay version лучше сжать цикл до 12-18 тиков total: anticipation 4-6 тиков, compression 1 тик, detonation 1 тик, decay 6-10 тиков. так warning еще успевает считаться, но способность не разваливает темп pvp/pve. интерполяция и timeline-объект как раз нужны, чтобы даже в короткой версии событие смотрелось цельно, а не дергано. citeturn26search20turn26search22turn27search1

важный нюанс: у hairpin не должно быть “длинного прогрева” как у луча, домена или кастуемой зоны. канон показывает очень короткую компрессию перед snap. особенно на кечизу hairpin работает как nasty remote finisher, почти насмешливое мгновенное добивание; на махито - как тактический двойной щелчок. если анимацию растянуть слишком сильно, техника перестанет быть колкой и станет похожа на generic trap spell. citeturn13view0turn14view0turn14view1turn13view2

## hairpin implementation spec for minecraft

**способность:** hairpin  
**роль в билде:** first polished vertical slice для нобары  
**ядро фантазии:** нобара заранее “засеивает” пространство или тело цели гвоздями, затем удаленно детонирует их проклятую энергию изнутри наружу. citeturn5view0turn6view0

**правила активации**
- hairpin доступен только если у цели или в радиусе вокруг нее есть armed nails.
- nails могут сидеть в блоке, в entitу hitbox, либо в заранее созданном prop/object marker.
- resonance и hairpin надо явно разводить по UX: resonance требует effigy/link state, hairpin требует embedded-nail state. citeturn6view0turn5view0

**состояния способности**
- idle: нет armed nails.
- armed: минимум один nail marker существует.
- warn: короткий pre-bloom и audio ping.
- detonate: burst по всем валидным nails в выбранном кластере.
- residue: краткий след, после которого anchors очищаются или помечаются как spent. citeturn14view1turn13view2

**визуальный стек**
- persistent nails: low-poly model или billboarded geometry, всегда читаемая даже без shader bloom.
- armed aura: тонкий rim и 1-2 кадра микро-пульса.
- main burst: spiked mesh/ribbon burst из каждой точки.
- fracture debris: particles по материалу блока или сущности.
- residue: 6-10 тиков темного свечения и медленно падающих микрочастиц. citeturn25search1turn26search1turn28search0

**аудио стек**
- nail set: короткий metallic ping.
- arm: low-volume cursed vibration.
- trigger: short snap или fingertip click.
- burst: dry sharp detonation, без жирного огненного саб-баса.
- tail: пыльный рассыпающийся cutoff.  
это нужно, чтобы техника сперва читалась как nail-technique, и уже потом как explosion-technique. citeturn14view0turn13view0turn14view1turn13view2

**hud/camera**
- owner-view: очень легкий screen-space pulse на этапе warn.
- victim-view: короткий vignette-jolt только если nail embedded в теле.
- observers: никаких тяжелых fullscreen-эффектов, только world-space читаемость.
- все camera accents - локальные, не сетевые. citeturn26search22turn26search0

**render split**
- particles: dust, chips, blood-black mistlets, afterglow motes.
- geometry/ribbons: nail outlines, cursed spikes, crack-lances, directional burst.
- hud accents: warn pulse, tiny reticle distortion.
- block entity renderer/custom world rendering: persistent embedded nails и cluster-shapes. citeturn25search1turn26search1turn28search0

**мультиплеер**
- сервер хранит список nail anchors.
- сервер присылает detonation tick и seed для воспроизводимого client fx.
- клиент locally expands same curve for bloom/residue.
- дальняя версия эффекта упрощается: меньше микрочастиц, но тот же шипастый silhouette. citeturn26search0turn26search1turn27search1

**рекомендуемый первый vertical slice**
- один target dummy mob
- nails can embed в земле и в dummy
- одна cluster detonation на кнопку
- две версии эффекта: “head finisher” и “ground cluster”
- переключатель cinematic/gameplay timings для быстрой итерации  
это даст сразу обе канонические логики - добивание кечизу и ловушку под ноги по махито. citeturn14view0turn14view1turn13view0turn13view2

## анти-паттерны, ассеты и открытые вопросы

анти-паттерны, которых лучше избегать:

- **обычный tnt-шар**. hairpin в каноне завязан на гвоздь как точку детонации, а не на сферический огненный blast. оранжевый fireball сразу ломает идентичность техники. citeturn5view0turn14view0
- **слишком много дыма**. hairpin читабелен через spike/fracture, не через долгую дымовую шапку. канон подчеркивает резкий burst и короткий aftermath. citeturn14view0turn14view1
- **безwarning-детон**. для махито именно постановка и двухшаговость делают сцену умной. если в моде нет читаемой armed-фазы, техника станет “рандомным проком”. citeturn14view1turn13view2
- **полный уход в солому**. straw motif важен как lineage, но hairpin визуально продается гвоздем, металлом и проклятым burst, а не сеном. citeturn6view0turn15view0turn23view0
- **anime-asset tracing**. пользовательское ограничение тут правильное: надо не копировать кадры, а делать original inspired direction на основе логики техники.
- **переспектакль в масштабе лесного ядерного взрыва**. в каноне hairpin опасен, но он все еще nail-anchored и тактический; даже когда ломает деревья, это бой через окружение, а не carpet bombing. citeturn14view2turn13view1

конкретные ассеты, которые стоит создать:

- 3d-модель обычного гвоздя
- 3d-модель “armed nail” с emissive trim
- 3d-модель крупного cursed spike для finisher shot
- 2 ribbon/mesh варианта burst: узкий и веерный
- 4 particle textures: sharp shard, dust chip, blood-black droplet, magenta spark
- 1 residue texture-atlas с 3 стадиями затухания
- 1 ui-иконка состояния hairpin ready
- 1 ui-иконка embedded nails count
- 5 sfx: nail set, metallic ping, cursed hum, snap trigger, dry burst
- 2 tail layers: grit falloff и cursed hiss
- 1 material-response set для блока: stone / wood / dirt
- 1 lightweight victim overlay для локального клиента

открытые вопросы перед продом:

- nails в мобах считать по hitbox-сегментам или по bone-like attachment points
- cluster detonation должна быть all-at-once или nearest-first chain
- hairpin должен тратить все armed nails или только выбранный кластер
- нужны ли friendly-fire правила для pvp
- нужно ли различать “nail in block” и “nail in flesh” по цвету residue
- как hairpin сочетается с будущим resonance в одном input-схеме
- будет ли vertical slice поддерживать shader-less режим как эталон читаемости
- нужна ли отдельная “finisher camera” для singleplayer и полностью отключаемый режим для multiplayer

## источники и ссылки

основные канон-опоры по работе техники:

- straw doll technique - описание базового набора нобары, различие hairpin и resonance, наследование техники. citeturn6view0
- hairpin - описание механики как расширения проклятой энергии в гвоздях и перечень ключевых применений. citeturn5view0
- chapter 41 / episode 17 - fight vs momo, где hairpin ломает деревья через ранее воткнутые гвозди. citeturn14view2turn13view1
- chapter 61 / episode 24 - добивание кечизу через детонацию гвоздя в голове и giant cursed-energy spike. citeturn14view0turn13view0
- chapter 122 / 123 / episode 43 - shibuya-версия с умной постановкой ног и двойным hairpin против махито. citeturn30view0turn14view1turn13view2

официальные и production-опоры по визуальному центру сцены:

- официальный сайт аниме, merchandise page по фигуре нобары на момент hairpin - подчеркивает giant nail и intense effect как центр сцены. citeturn15view0
- лицензированные фото фигуры s-fire как secondary visual reference для формы burst и соотношения nail/effect. использовать как inspiration-only, не как прямой asset-copy target. citeturn18image4turn18image6

engine-опоры для реализации в fabric/minecraft:

- fabric docs overview. citeturn25search0
- fabric custom particles. citeturn25search1
- fabric world rendering. citeturn26search1
- fabric networking. citeturn26search0
- fabric block entities и block entity renderers. citeturn28search5turn28search0
- minecraft display entity interpolation в snapshot notes и wiki summary - полезно как reference для плавных staged effects. citeturn26search20turn26search6
- minecraft tick reference для перевода таймингов в секунды и тики. citeturn27search1

дополнительная культурная опора:

- academic summary по ushi no koku mairi и обзор ritual kit - useful only as inspiration for straw/nail symbolism, не как прямой визуальный шаблон hairpin. citeturn23view0
- обзор традиции ushi no toki mairi как народной основы образа straw doll + nails. тоже inspiration-only. citeturn21search0