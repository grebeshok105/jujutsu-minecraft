# nobara hairpin production vfx art bible and implementation breakdown

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

## production art direction summary

hairpin в каноне - это не “магический взрыв вообще” и не прокси-удар через куклу. это локальная детонация уже заряженных гвоздей, причем именно застрявший гвоздь становится отсроченной угрозой. в бою с momo hairpin превращает промахи по цели в контроль пространства через взрыв застрявших гвоздей и разрушение окружения. resonance работает иначе: ей нужен связанный фрагмент цели и соломенная кукла как прокси, после чего урон проходит по связи, а не из места застрявшего гвоздя. у hairpin семантика простая и сильная: “здесь уже поставлена мина, и она принадлежит nobara”. citeturn4view0turn4view1turn4view2turn5view0turn5view1

для production-ready версии под minecraft это надо переводить не в аниме-вспышку, а в физичный якорь угрозы. главный визуальный субъект эффекта - не шар взрыва, а сам nail-anchor. burst должен читаться как высвобождение накопленной энергии из штифта и трещины вокруг него, а не как отдельное заклинание, прилетевшее сверху. если этот принцип сломать, эффект мгновенно уедет в tnt, fireball или generic cursed magic. по факту это будет слабый дизайн.

kanzashi как культурный источник полезен не буквальной “цветочностью”, а материалом и жестом формы: длинный жесткий стержень, металлическая холодная отделка, деликатный декоративный узел, иногда шелк и подвесы, ювелирная точность вместо мясистой пиротехники. музейные примеры подтверждают связку silver + silk и общую деликатную, вытянутую типологию формы. значит, для minecraft-native адаптации надо брать не “аниме asset”, а язык forged pin + cold silver + restrained petal/ribbon echoes. citeturn4view8turn4view9turn2search1

внутри minecraft есть жесткие технические рамки. стандартные частицы в основном живут как camera-facing sprite quads, а не как настоящая геометрия. display entities удобны для показа custom model, но у них нет коллизии и они все же считаются как entities на клиенте. значит, nail-anchor и крупные spike-блоки нельзя держать чисто на particles. наоборот, их надо делать world geometry или item display/model, а particles оставить для edge energy, dust, grit и decay. для hud и optional overlay 1.21.8 уже использует Hud API и новый рендер-пайплайн, а raw opengl Fabric прямо не рекомендует. citeturn1search6turn9view4turn4view7turn9view1turn4view6turn9view0

важный вывод по стилю: это должен быть **anchored cursed shrapnel effect**. не beam, не orb, не smoke sphere. nail - first. burst - second. afterglow - только распад тех же векторов burst, без второй независимой ауры.

## canon and minecraft translation

hairpin в сюжете решает две задачи. первая - капитализировать уже размещенные гвозди, включая промахи, через удаленную детонацию. вторая - продлить reach техники за пределы обычного прямого попадания. resonance решает другую задачу: находить и пробивать цель по связи через effigy logic. поэтому визуально их надо разводить жестко: resonance читает “connection / proxy / curse transmission”, hairpin читает “embedded threat / delayed detonation / local rupture”. если у hairpin появится луч, нитка, круг-ритуал или кукольный glyph, вы случайно скрестите две разные техники. это ошибка уровня концепта. citeturn4view0turn4view1turn5view0turn5view1

для оригинально вдохновленного, но не копирующего assets решения kanzashi нужен как подслой taste, не как предмет в лоб. я бы не делал декоративный “женский hair ornament” как сам снаряд. это будет либо кринжово, либо слишком cosplay. сильнее работает такой перевод: боевой forged nail получает сплющенную головку с очень легким kanzashi-эхом, а в burst-фазе появляются короткие broken-petal arcs и резаные ribbon-гранки, которые отсылают к metal/silk craftsmanship. тогда источник чувствуется, но без кражи аниме-кадров и без прямой ювелирки. материалы kanzashi исторически действительно тяготеют к металлу, шелку и тонкой ручной детализации. citeturn4view8turn4view9turn2search1

сама minecraft-подача должна быть кубичной и экономной. частицы читаются как billboards, так что они хороши для edge flicker, grit, ash и коротких streaks, но не для основного “гвоздя” и не для чистого blast silhouette. world reading в темных сценах и в pvp-хаосе выигрывает не от плотности, а от трех вещей: видавшийся штифт, короткий контрастный pulse на кромке и один сухой звуковой сигнал. лишняя particle-супа убьет распознавание. display/item models хороши для самого marker, но ими нельзя спамить как residue, потому что display entities рендерятся как сущности. citeturn9view4turn4view7turn4view5turn6search1

трактовка фаз из вашего target удачная. особенно хорошо, что “embedded nails are threat markers” и что afterglow должен быть decay тех же burst-векторов. это сильнее любого отдельного halo. слабое место было бы одно: если mark и warn окажутся слишком декоративными, игрок не поймет, что это именно **armed remote detonation**. поэтому marker обязан быть физичным и почти утилитарным. красивость - на кромке и в распаде, не на основном объеме.

iturn10image2turn10image4turn10image6turn10image10

эти музейные и архивные примеры полезны не для прямого копирования, а для понимания пропорций: длинный металлический носитель, маленький акцентный узел, редкие подвесы, почти хирургическая тонкость. именно такую логику стоит брать в shape language вашего burst и marker head. citeturn4view8turn4view9turn2search1

## phase-by-phase storyboard

для таблицы ниже: minecraft тикает 20 раз в секунду, то есть 1 tick = 50 ms. это важно для телеграфа и fairness. citeturn1search14turn9view1

| фаза | цель | длительность | визуал | камера | звук | сильное правило |
|---|---|---:|---|---|---|---|
| mark | закрепить угрозу | persistent, settle 2-3 ticks / 100-150 ms после втыкания | видимый застрявший nail-anchor. тонкая холодная стальная головка, черно-вишневый “пропитанный” ствол, под ним 2-4 px crack/decal по нормали поверхности | без shake | короткий metallic pin с сухим хвостом | mark должен читаться даже без дальнейших фаз |
| warning | честный телеграф активации | 6-8 ticks / 300-400 ms | 2 импульса по краю головки и crack seam. dirty fuchsia только по кромке и только как 1-2 pixel rim. легкая внутренняя пульсация в crack, не сфера | максимум микроподергивание только у владельца в first-person | повторный ping + низкий cursed hum | если warning короче 6 ticks, в pvp это уже спорно |
| compression | показать втягивание энергии внутрь точки | 3-4 ticks / 150-200 ms | частицы и малые shard-линии втягиваются к гвоздю. crack слегка “подсасывает” пыль из окружения. объем схлопывается, а не растет | у очень близких игроков можно дать едва заметное lens pinch, но без aggressive shake | hum уплотняется, появляется metal strain | нельзя делать вихрь-воронку. это будет чужая магия |
| snap | разрыв перед выбросом | 1 tick / 50 ms | один сверхкороткий черно-красный line-collapse кадр по оси nail + 3-5 прерывистых micro-arcs | 0 или микро-jolt только в радиусе очень близко | hammer-crack | самый короткий кадр. не растягивать |
| burst | основной уроновый beat | 2-3 ticks / 100-150 ms | направленный bouquet из blocky spikes + broken arcs + grit. не шар. сильнейший выброс идет из nail normal и tangent plane трещины | очень легкая local shake только у ближайших игроков | dry detonation + grit transients | burst обязан быть асимметричным и локальным |
| residue | затухание тех же векторов | 12-20 ticks / 600-1000 ms | те же arcs ломаются на угольные следы, черно-красные крошки и редкий fuchsia edge fade. 1-2 lingering ribbon scraps по вектору burst | без камеры | gritty tail + hum dissolve | residue не отдельная “аура” |
| clear | уход сцены | еще 4-6 ticks / 200-300 ms если нужно | crack темнеет и остается лишь краткий stain/decal | нет | тишина | не держать эффект слишком долго |

### режиссура по камере и темпу

лучший темп для hairpin - это **короткая двуступенчатая тревога и очень сухой snap**. то есть игрок сначала видит “armed”, потом “oh shit now”, и только потом мгновенный выброс. многие модовые эффекты заваливаются в бесконечный заряд. тут так нельзя. nobara не про медленный spellcast. она про резкий злой switch.

для third-person и наблюдателей камера вообще не должна быть главным носителем мощности. мощность должны нести silhouette и audio. pvp-комфорт важнее “вау shaky cam”. если хочется “продать” snap, делайте это через 1-frame line collapse, а не через экранолом.

## asset, color, shape, and audio bible

### asset manifest

| ассет | приоритет | тип | spec | владелец | примечание |
|---|---|---|---|---|---|
| world nail marker | p0 | item model / display model | 48-96 tris или 8-12 cuboid эквивалентов | tech art | длина силуэта важнее детализации головки |
| embedded flesh nail variant | p0 | item model / attached display | тот же base mesh + 1 alt UV | tech art | должен выглядеть “вбитым”, не приклеенным |
| armed nail material | p0 | texture set | 32x32 base, 32x32 emissive mask optional | tech art | emissive только по groove/rim |
| surface crack decal atlas | p0 | sprite atlas | 128x128, 4-8 вариаций | vfx art | stone/wood/flesh branches |
| burst grit sprites | p0 | particle spritesheet | 128x128, 8-12 frames total | vfx art | уголь, chip, ash, thin arc |
| broken arc sprites | p0 | particle spritesheet | 128x128, 6-8 variants | vfx art | это не lasers. края рваные |
| spike burst meshes | p0 | model or submitted world geometry | 24-64 tris each, 3-5 variants | tech art | короткие blocky wedges |
| residue atlas | p0 | particle/decal atlas | 128x128, 6-10 stains | vfx art | coagulated black-red, matte |
| owner-only activation pip | p1 | hud icon | 16x16 or 32x32 | ui | минимум визуального шума |
| victim warning edge icon | p2 | hud overlay | fullscreen mask + tiny icon | ui | только если world readability провалится |
| compression mask | p1 | shader/noise texture | 64x64 or 128x128 | tech art | optional, soft radial shear only |
| snap flash mask | p1 | shader mask | 64x64 | tech art | optional, one-frame use |
| sound bank hairpin_ping | p0 | .ogg | mono, <300 ms | audio | anchor confirmation |
| sound bank hairpin_hum | p0 | .ogg | mono loop or stitched one-shot | audio | local tension bed |
| sound bank hairpin_snap | p0 | .ogg | mono, <180 ms | audio | metal + dry fracture |
| sound bank hairpin_burst | p0 | .ogg | mono body + stereo sweetener optional | audio | основной hit |
| sound bank hairpin_tail | p0 | .ogg | mono/stereo hybrid, <900 ms | audio | grit decay |
| subtitles/lang entries | p1 | json/lang | subtitle keys | engineering/ui | accessibility, debug value |

fabric для particles ожидает registered `ParticleType`, клиентскую регистрацию factory и json-файл в `particles`, который указывает текстуры. для кастомных звуков нужны `.ogg`, `sounds.json` и `SoundEvent`; subtitles подключаются через `sounds.json` и lang. citeturn4view5turn9view2turn9view3

### color and material bible

палитра должна быть грязной и тяжелой. вот рабочий production набор:

| роль | hex | доля |
|---|---|---:|
| blood-black base | `#12090c` | 34% |
| void black core | `#080607` | 18% |
| black cherry body | `#250913` | 18% |
| dark carmine wound energy | `#5b101b` | 12% |
| coagulated residue | `#311016` | 8% |
| dirty fuchsia edge | `#8a2f58` | 5% |
| cold steel dark | `#5f666d` | 3% |
| cold steel specular | `#98a1aa` | 2% |

мое жесткое мнение: dirty fuchsia нельзя пускать выше 5-8% видимой площади в любой кадр. если она займет больше, эффект станет “неоновым cursed sci-fi” и потеряет телесность. то же с carmine - он должен жить внутри трещины, в подкладке burst и в коротком edge stain, а не заливать всю сцену.

материалы тоже простые. nail shaft - холодный металл с 1-pixel spec hit. nail head - темнее, с маленькой внутренней прорезью или насечкой под armed state. crack - матовый, будто прожженная и треснувшая поверхность. residue - почти бархатный, сверкает только на кромке. если есть emissive, использовать его только на activation rim и в snap-frame. если shaderless fallback, тот же эффект достигается value contrast + alpha falloff + низкая насыщенность основы.

### shape bible

основная форма marker - длинный боевой штифт с чуть сплющенной, несимметричной головкой. не круглая шляпка гвоздя и не украшение из цветка. лучший компромисс - “forged nail with kanzashi memory”: головка напоминает лопатку или маленький лепестковый клин, но остается оружием.

surface crack не должна рисовать perfect ring. нужен короткий надлом по нормали удара и 1-2 ответвления по плоскости материала. wood может дать более длинные splinter cracks, stone - короче и толще, flesh - не crack, а разорванный bruise seam с тягучим black-red contour.

burst spikes - это не beam cluster. это короткие wedge-блоки, будто энергия выламывает клинья наружу. broken arcs - тоже не laser trails. у них должен быть рваный ритм и ломанная длина, как если бы burst vector уже начал осыпаться. residue - не smoke ball, а грязные мазки и сажистые flakes, продолжающие те же направления, что и burst.

### audio bible

| событие | состав слоя | правило панорамы | дистанция / lod | комментарий |
|---|---|---|---|---|
| mark_place | metal tick + tiny grit | mono | слышно близко, быстро режется | подтверждает “гвоздь сел” |
| arm_warning | higher metallic ping + muted hum onset | mono | 12-18 блоков | главный честный телеграф |
| compression_loop | low cursed hum + strained metal | mono | только near-mid | не должен гудеть как reactor |
| snap | hammer crack + dry clip transient | mono | широкий, но короткий | это пик напряжения |
| burst | dry detonation + grit spray + sub thump | mono body, stereo sweetener optional | 20-28 блоков | нельзя делать tnt-boom |
| residue_tail | ash hiss + decaying strain | near mono, stereo только very subtle | быстро умирает на дистанции | связывает burst и fade |
| owner_confirm | soft internal click | stereo ui optional | только владелец | если нужен gameplay confirm |

мирные и позиционные sounds должны быть в основном mono, чтобы world-space локализация в minecraft оставалась читаемой. stereo можно оставить только для owner-only ui cue или для совсем легкого decorative sweetener на burst tail. fabric-документация для custom sounds опирается на `.ogg`, `sounds.json`, subtitle keys и `SoundEvent.createVariableRangeEvent`, так что логику lod и разные range-ивенты делать нормально. citeturn9view2turn9view3

## implementation mapping and readability

### particle, shader, world-geometry mapping table

| элемент | лучший носитель | почему | fallback |
|---|---|---|---|
| embedded nail marker | item display / custom model / attached marker entity | нужен жесткий силуэт, particles тут слабые | billboard sprite только на low mode |
| armed rim pulse | particle + material emissive | короткий локальный edge beat | texture swap без emissive |
| surface crack | decal-like particle cluster или tiny submitted geometry | привязка к плоскости и нормали | sprite atlas aligned to face |
| compression in-draw | particles, направленные к anchor | дешево и читаемо | material pulse only |
| snap line-collapse | optional tiny shader pass или one-frame world ribbon sprites | это микробит, не нужен тяжелый full-screen pass | 1-frame sprite cross-collapse |
| burst spikes | submitted world geometry или 2-3 custom spike displays | основной объем должен быть пространственным | усиленный particle bouquet |
| grit / ash / chips | particles | это их работа | урезать count на low preset |
| residue stains | particles / short-lived decals | нельзя плодить entities | texture fade only |
| owner readiness pip | hud element | ясный internal confirm | none |
| victim discomfort vignette | hud/shader optional | только если нужна fairness-помощь | none |

particles в minecraft хороши именно как спрайтовые квады. для big read их надо поддерживать геометрией. display entities умеют показывать custom model и трансформы, но за каждый такой объект вы платите entity cost, поэтому их стоит тратить на nail-anchor и максимум на 2-3 burst-spikes в ключевой фазе, а не на шлейф. для hud-слоя Fabric дает `HudElementRegistry`, что удобно для owner-only feedback и очень мягких warning overlays. citeturn9view4turn4view7turn9view1

### readability and counterplay

для атакующего картина должна быть такой: он сразу видит, какие гвозди реально armed. это можно дать через более чистый steel/fuchsia rim и, если совсем надо, через очень скромный owner-only pip на hud. не надо давать через wallhack outline - это уже другая механика и визуально токсично.

для жертвы сигнал еще важнее. она должна видеть три вещи: сам застрявший marker, краткий armed pulse и фазу compression перед snap. если один из этих слоев отсутствует, hairpin станет ощущаться как netcode death. по-честному минимальный читаемый warning - 300-400 ms. меньше - уже больно, особенно в мультиплеере с темными сценами и hit-flash шумом.

для наблюдателя лучше всего работает простой и честный язык: nail, pulse, inward draw, sharp burst, dirty residue. наблюдатель не должен разбираться в hidden internal states. если spectator с первого взгляда не понимает “воткнутый гвоздь сейчас рванет”, значит дизайн пока не production-ready.

моя прямая рекомендация: **не делать глобальный full-screen shader обязательной частью эффекта**. Fabric 1.21.8 и дальше движется в сторону более формализованного render-state / extraction-drawing пайплайна, а raw opengl path прямо не приветствуется. hairpin должен хорошо жить shaderless. шейдер тут только luxury polish на compression/snap. citeturn4view6turn9view0

## quality gates, anti-patterns, acceptance checklist, and open questions

### visual anti-patterns and correction strategies

| анти-паттерн | почему плохо | чем заменить |
|---|---|---|
| круглый smoky explosion ball | выглядит как tnt/fireball | asymmetrical spike bouquet + crack-driven debris |
| длинный beam до цели | путает hairpin с resonance | все движение только вокруг anchor |
| неоновая магента в половине кадра | уводит в sci-fi spell | fuchsia только по edge и decay fringe |
| отдельный halo после burst | создает второй чужой эффект | decay тех же burst-векторов |
| perfect magic circle на поверхности | это уже ритуал, не nail detonation | короткий offset crack seam |
| слишком ornate jewelry nail | превращает оружие в аксессуар | forged combat nail с тонким kanzashi-эхом |
| screen shake как главный sell | pvp-дискомфорт и дешево | snap-frame + audio crack + silhouette |
| particle flood | нечитабельно в темноте и в хаосе | 3 слоя: marker, pulse, directed burst |

### acceptance checklist for approving the visual target before implementation

| чек | pass condition |
|---|---|
| marker read | на стоп-кадре в cave, forest night и basalt/nether сцене зритель за 1 секунду находит armed nail без подсказки |
| canon separation | эффект не читается как resonance, beam curse, tnt или fireball |
| anchor logic | burst явно рождается из nail-anchor и crack, а не из отдельного шара |
| pvp fairness | жертва получает минимум 6 ticks читаемого warning до snap |
| silhouette economy | в burst есть 1 dominant shape, 1 support shape, 1 residue layer. не больше |
| palette discipline | dirty fuchsia не доминирует ни в одном кадре, metal остается холодным |
| residue continuity | afterglow - это распад burst, а не новая аура |
| low-spec fallback | без шейдера и при reduced particles эффект все еще узнаваем |
| distance lod | на 16-24 блоках остается понятен факт активации, но шум не забивает сцену |
| audio language | ping, hum, snap, burst, tail различимы и не звучат как vanilla tnt |
| ownership clarity | атакующий понимает armed state без wallhack-читовщины |
| implementation sanity | display/model используется на marker, не на всей residue-сцене |

### clip and screenshot gate pack

до порта в код я бы требовал вот такой минимальный пакет:

- 3 orthographic concept sheets: marker, compression, burst
- 6 keyframes по фазам на одной сцене
- 3 dark-scene mockups
- 1 multiplayer-chaos mockup с минимум 3 боевыми источниками шума
- 2 clips по 2-3 секунды: near camera и observer mid-distance
- 1 low-spec fallback clip без shader pass
- 1 side-by-side “bad vs corrected” board по anti-patterns

если команда не может показать этот пакет, значит target еще сырой. тут без обид. просто рано лезть в кастомные particles и shaders.

### remaining open questions

- есть ли лимит на одновременные armed nails на одной цели и в мире. от этого зависит, сколько marker/display-объектов можно позволить без мусора.
- nail на живой цели должен сидеть в approximate body offset, в hitbox center или в фиксированной косте/точке модели. это сильно влияет на attached-display реализацию.
- нужен ли owner-only hud pip вообще. если world readability уже сильная, hud можно выкинуть.
- допускается ли optional screen-space shader в baseline-сборке, или нужен полностью shaderless-first target.
- нужна ли разная surface language для stone, wood, flesh уже в v1, или достаточно одного общего crack atlas.
- путь `docs/visual-targets/nobara-hairpin/index.html` был упомянут как существующий visual target, но сам html не был доступен мне через инструменты этого чата. поэтому этот bible опирается на ваш текстовый target и канон hairpin, а не на покадровый аудит текущего html-макета.