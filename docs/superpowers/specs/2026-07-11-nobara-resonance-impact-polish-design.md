# Design — усиленная Resonance с куклой

Дата: 2026-07-11

## Решение

Усиление остаётся полностью внутри существующего VFX Core. Сервер по-прежнему
решает момент попадания, урон и stagger; `DOLL_STRIKE` и
`RESONANCE_RELEASE` остаются единственными transient-cues сцены.

`RESONANCE_RELEASE` получает более длинную world-фазу и отдельный ударный
профиль камеры: короткий zoom-in, более заметную тряску и плавный возврат.
`DOLL_STRIKE` получает более длинную локальную реакцию Нобары.

Добавляется узкий director-owned `VfxTimeChannel`. Он не меняет серверные
тики, скорость игры или gameplay authority. Клиентский `DeltaTracker.Timer`
только на короткое время масштабирует render partial ticks, создавая лёгкий
hit-stop/slow-motion эффект. Коэффициент возвращается к `1.0` автоматически
и очищается при смене мира или disconnect.

Экранная негативная реакция — это не vanilla potion effect. Для локального
клиента сущности, на которую заякорен `RESONANCE_RELEASE`, VFX Core включает
короткую nausea-подобную зелёно-тёмную виньетку с мягким blur. Наблюдатели
видят world impact и обычную дистанционную камеру, но не получают чужую
тошноту или slow-motion.

## Начальные значения

- `RESONANCE_RELEASE`: 38 тиков вместо 26.
- Slow-motion цели: `0.65x` на 300 мс.
- Nausea overlay цели: 760 мс, ограниченная прозрачность.
- Resonance zoom: около 10–12 градусов FOV с плавным возвратом.
- Shake/impact: расширенный двухфазный профиль, без изменения урона.

Значения являются визуальными стартовыми параметрами и остаются локально в
VFX channels/recipe, а не в серверной боевой логике.

## Границы

- Не добавлять `MobEffectInstance` и не менять `ServerLevel` time.
- Не вводить отдельные packet receivers, effect managers или per-effect
  render callbacks.
- Не требовать новых shader assets: nausea-эффект строится на существующем
  director-owned HUD overlay и blur channel.
