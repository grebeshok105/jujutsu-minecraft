Sons of Sins -> curse asset pack
==================================

Источник:
- sons_of_sins-2.2.1d-neoforge-1.21.1.jar
- пользователь сообщил, что автор Sons of Sins лично разрешил использовать эти ассеты в его JJK Fabric-моде.

Зачем этот архив:
- собрать максимум полезного для будущих cursed spirits / проклятий;
- не переносить чужую игровую механику как систему, а использовать модели, текстуры, анимационные данные,
  звуки и renderer/entity-код только как референс при адаптации.

ВАЖНО ПРО ФОРМАТ МОДЕЛЕЙ
------------------------
Эта версия Sons of Sins НЕ кладёт в JAR .geo.json или .bbmodel.
MCreator-модели скомпилированы как Java model classes (LayerDefinition / ModelPart), а анимации тоже
скомпилированы в Java classes.

Поэтому внутри есть:
1) raw/ — оригинальные .class и ресурсные файлы побайтно из JAR;
2) references/javap/ — javap-дизассемблирование top-level model/animation классов;
3) references/main_sins_javap/ — более целевые model/animation/renderer/entity дампы для семи основных sins.

Если автор может прислать оригинальные Blockbench .bbmodel-файлы — это будет удобнее для ручного редактирования.
Но даже без них агент может портировать/восстановить Java LayerDefinition-модели по классам/дизассемблированию.

Семь основных существ из скрина / основной линейки:
- Wistiver
- Walking Bed
- Prowler
- Kelvin
- Curse
- Blüd
- Butcher

В pack также намеренно оставлены дополнительные creature-модели из JAR (Grub, Devourer, Gulber, Guzzler,
Nibbler, Ditched, Betrayed Guzzler, ghost/organ variants и т.д.). Они могут пригодиться как дополнительные
варианты моделей для одного и того же тира проклятия.

Что извлечено
-------------
- model classes: 67
- animation classes: 20
- renderer classes: 74
- entity classes: 84
- entity textures: 38
- organ/variant textures: 69
- sounds: 80
- sound maps / lang / particles: включены
- references/tier_reference.png — присланный пользователем визуальный референс группировки по тирам
- MANIFEST.csv — путь, размер и SHA-256 каждого извлечённого файла

Рекомендуемый подход в jujutsu-minecraft
----------------------------------------
Не делать 7 отдельных классов проклятий только ради внешности.

Смысл пользовательской идеи:
- 3 gameplay presets / tiers;
- внутри каждого тира несколько визуальных variants;
- model/texture/sound variant выбирается отдельно от боевой логики тира.

То есть AI/stats/abilities принадлежат tier preset, а конкретная туша — presentation variant.
Это позволит использовать 7+ моделей без 7 почти одинаковых сущностей.

Не привязывай баланс к оригинальным Sons of Sins entity-классам — их код лежит здесь только как reference.
