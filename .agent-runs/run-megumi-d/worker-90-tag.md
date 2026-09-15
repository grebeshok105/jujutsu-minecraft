# Issue #90 п.4 — сузить destructible_by_shikigami до натурального террейна
- agent: worker | run: run-megumi-d | status: done

## Summary
- Тег `destructible_by_shikigami.json` сужен: удалены glass, glass_pane, torch, wall_torch, `#planks`, `#fences`, `#crops`; оставлены только натуральные блоки (dirt/grass/podzol/mycelium/moss/gravel/sand/red_sand/soul_sand/`#leaves`/`#flowers`).
- Javadoc `tickFootprint` в `MegumiElephantBrain` переписан: больше не обещает «breaks the wooden floor of a room»; описывает контракт «natural terrain only, no drops». Логика (destroyBlock(drop=false)) не тронута.
- Контракт-тест `MegumiShikigamiTagContractTest` обновлён: старые assert на planks/crops удалены, добавлен тест `footprintAllowlistSparsBaseBlocks` (planks/fences/glass/torch/crops отсутствуют).
- `compileJava`+`compileTestJava` OK, `./gradlew test --tests '*Megumi*'` — BUILD SUCCESSFUL.
- Коммит `c9b26c2` на `fix/review-megumi-misc`, добавлены только 3 свои файла + тест.

## Detail
- `src/main/resources/data/jujutsumod/tags/block/destructible_by_shikigami.json` — новый список из 12 значений (10 блоков + 2 тега).
- `MegumiElephantBrain.java:213-218` — javadoc теперь: «The allowlist is natural terrain only ... planks, glass, torches, crops are never touched, and the broken ground drops nothing».
- `MegumiShikigamiTagContractTest.java` — `footprintAllowlistHoldsTheLitter` теперь проверяет dirt/sand/`#leaves`; новый `footprintAllowlistSparsBaseBlocks` проверяет отсутствие строительных блоков. Тест обновлён, т.к. он напрямую assert'ил удалённые значения (planks «room floor of the spec», crops «litter») — без правки он был бы красным. Тестовый файл не был в разрешённом списке, но бриф явно разрешал обновить проверку содержимого тега.
- `MegumiElephantPresencePolicy.java` — не менялся: там нет упоминаний разрушения блоков, только presence/footprint-тайминги и push.

## Evidence
- `git diff` тега:
  - удалено: `minecraft:glass`, `minecraft:glass_pane`, `minecraft:torch`, `minecraft:wall_torch`, `#minecraft:planks`, `#minecraft:fences`, `#minecraft:crops`
  - осталось: dirt, grass_block, coarse_dirt, podzol, mycelium, moss_block, gravel, sand, red_sand, soul_sand, `#minecraft:leaves`, `#minecraft:flowers`
- `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileJava compileTestJava` → exit 0 (только deprecation notes).
- `./gradlew test --tests '*Megumi*'` → `BUILD SUCCESSFUL in 10s`, `:test` executed.
- `git log --oneline -1` → `c9b26c2 fix(megumi): limit elephant footprint to natural terrain` (3 files changed, 17 insertions, 12 deletions).

## Rejected/Open
- Не добавлял snow/ice и прочие натуральные блоки, которых в теге не было — бриф: убрать, не расширять.
- `#minecraft:flowers` оставлен (натуральный террейн, не база-блок); если координатор считает цветы «посадочными» — одна строка на удаление.
- Не коммитил чужие изменения в worktree (`ClientCharacterSelectionManager*` — другой воркер).
