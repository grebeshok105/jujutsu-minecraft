# План — усиленная Resonance с куклой

1. Расширить VFX Core узким `VfxTimeChannel`, передать его через
   `VfxContext`/`VfxDirector` и добавить client-only mixin для масштабирования
   render partial ticks.
2. Усилить `VfxCameraChannel` профилем Resonance, добавить target-local
   nausea-подобный overlay в `VfxHudChannel`, увеличить blur и длительность
   world/recipe-фаз кукольного удара.
3. В `NobaraVfxRecipes` включить slow-motion и nausea только когда cue
   заякорен на локальном игроке; дистанционные наблюдатели сохраняют обычную
   камеру и world-VFX.
4. Добавить узкие sanity/assertion проверки wiring и обновить VFX Core,
   Straw Doll и maintenance codex.
5. Запустить focused assertions, `check`, runtime build, `git diff --check`,
   скопировать итоговый runtime JAR в игровой instance и сравнить SHA-256.
