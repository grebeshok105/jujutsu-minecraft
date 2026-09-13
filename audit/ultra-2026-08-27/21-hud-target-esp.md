## HUD ESP — 21-hud-target-esp

### 1) Что проверено (файлы/символы)

Срез `21-hud-target-esp`: `NobaraTargetHud` (bracket/divider/segmented HP 10/nail_icon/rank token/CHEST_FRACTION 0.60), `MegumiShadowDiveHud`, cooldown HUD mirror PRIMARY, `GuiGraphics` без `mulPose`/`Matrix3x2f`, корректность позиционирования. Собрано `codegraph_explore` + `read`/`grep`/`glob`/`bash`.

- `src/client/java/jujutsu/mod/client/character/nobara/NobaraTargetHud.java:39-291` — `CHEST_FRACTION 0.60`, `SILHOUETTE_GAP_PX 3f`, `EDGE_MARGIN 4f`, `BLOCKS_TO_PX 16.0`, `STATES`, `render`/`drawBlock`/`place`/`track`/`prune`/`rankKeyFor`/`withAlpha`/`Camera.of`
- `src/client/java/jujutsu/mod/client/character/nobara/NobaraTargetLayout.java:14-152` — `attachScale`, `block`, `filledSegments`, `gradeDisplay`, `hpRatioText`, константы `BRACKET_GAP 4/CAP 3`, `HP_SEGMENTS 10/W 3/GAP 1/H 5`, `NAIL_H 16`, `RANK_CELL 7`
- `src/client/java/jujutsu/mod/client/character/nobara/NobaraTargetAnim.java:11-44` — `appearAlpha`/`slideOffsetPx`/`popScale`/`approachValue`
- `src/client/java/jujutsu/mod/client/character/nobara/NobaraEspState.java:25-149` — `aggregate`/`snapshot`/`refresh` каждые 2 тика
- `src/client/java/jujutsu/mod/client/character/nobara/NobaraEspRanks.java:9-48` — `rankKey`, пороги 100/40/20
- `src/client/java/jujutsu/mod/client/character/nobara/NobaraClientDefinition.java:108-118` — `registerHudContribution(nobara_target_hud)`
- `src/client/java/jujutsu/mod/client/ui/WorldToScreen.java:22-130` — `project`/`clampToScreen`/`MIN_DEPTH 0.5`
- `src/client/java/jujutsu/mod/client/ui/UiEase.java:1-39` — easing/approach
- `src/client/java/jujutsu/mod/client/character/megumi/MegumiShadowDiveHud.java:15-31` — veil `diveFadeAlpha`
- `src/client/java/jujutsu/mod/client/character/megumi/MegumiCooldownHud.java:14-46` — `visible`/`render` mirror PRIMARY
- `src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java:64-133` — `maxCooldownTicks`, 2× `registerHudContribution`
- `src/client/java/jujutsu/mod/client/vfx/VfxCameraChannel.java:150-179` — `diveFadeAlpha`
- `src/client/java/jujutsu/mod/client/vfx/VfxDirector.java:43,68-70,115-117,151-153` — HUD channel/dive/registration
- `src/client/java/jujutsu/mod/client/vfx/VfxHudChannel.java:23-102` — contribution order
- `src/client/java/jujutsu/mod/client/character/ClientAbilityCooldowns.java:11-57` — `READY_AT` mirror
- `src/client/java/jujutsu/mod/client/hud/AbilityHud.java:66-137` — overlay эталон SDF
- `src/main/resources/assets/jujutsumod/textures/gui/hud/nail_icon.png` — verified exists
- `src/main/java/jujutsu/mod/character/CharacterAbility.java:16-73` + `CharacterAbilityCooldowns.java:21-70` — wire ids append-only, server authoritative

Проверено `grep mulPose|Matrix3x2f src/client/**/character/** src/client/**/hud/** src/client/**/ui/**` → 0 хитов (в HUD нет, только `FirstPersonHandFxMixin`/`ProjectJjkNailRenderer` вне скоупа). `grep popScale` → только `Anim.java`+тест, вызова в проде нет.

### 2) Находки

| severity | файл:строка | описание | рекомендация |
|---|---|---|---|
| major | `NobaraTargetHud.java:143-146` | **nail_icon не фейдится.** `drawBlock` красит bracket/divider/HP/тексты через `withAlpha(..., alpha)` (0→1 за 3т), а `blit(NAIL_ICON, ..., 16,16,16,16)` без альфы — первые 3т иконка 100% на полупрозрачном фоне. `GUI_TEXTURED blit` в 1.21.8 не принимает tint. | Прокинуть alpha через `setColor`/pose tint, либо гейтить `if(alpha>0.02)`, либо задокументировать limitation. |
| major | `NobaraTargetHud.java:283-289` — `Camera.of()` | **Camera rotation mismatch.** Позиция из `MainCamera.getPosition()`, pitch/yaw из `player.getXRot/YRot`, fov из `options.fov`. В third-person/detached/bob/spyglass — проекция улетает на десятки px. | Брать pitch/yaw/fov из `MainCamera` (+ `VfxDirector.fovOffset()`), не из player. |
| major | `NobaraTargetHud.java:183-185` | **Depth для `attachScale` расходится с проекцией.** `depthBlocks = sqrt(distanceToSqr(cam, feet))` vs `screen = project(chest - cam)` где `chest = pos+0.60*BbHeight`. Разница до 1 блока → scale прыгает. `distance` без `partialTick`, chest — с ним → дрожь. | `depthBlocks = max(1.0, screen.depth())` — одна строка, согласованно. |
| major | `MegumiCooldownHud.java:19-33` + `AGENTS.md:38` | **Cooldown HUD без pack-гейта.** AGENTS: «only while Megumi has no pack and deadline>0», код проверяет только `megumiSelected && remaining>0`. При `remaining` после recall/pack death, пока entity пака живёт 12-16т — чип виден поверх пака. Держится на implicit «summon не ставит cooldown». | Добавить pack-absence гейт или зафиксировать инвариант тестом `summonDoesNotStartCooldown`. |
| minor | `NobaraTargetAnim.java:32-38` | **Dead code `popScale`/`POP_TICKS`.** Определён и покрыт тестом, но ни одного вызова в `src/client` (grep только Anim+тест). `STATES` не хранит `lastNailCount`. | Удалить или подключить: `lastNailCount`+`nailChangeGameTime` в `STATES`, скейлить nail row. |
| minor | `NobaraTargetHud.java:71-108` | **Ghost 0-2т после смены vessel.** `render` гейтит только `snapshot.isEmpty()`, не `characterOrNone==NOBARA`. `refresh` чистит snapshot каждые 2т → 1т HUD рисуется уже как Todo/Megumi. | Добавить в `render:72` `if(characterOrNone!=NOBARA){ STATES.clear(); return; }`. |
| minor | `NobaraTargetHud.java:283-289` + `VfxCameraChannel.java:99-117` | **FOV без `fovOffset`.** `Camera.of` берёт `options.fov`, игнорируя `VfxCameraChannel.fovOffset` (удары/diving). Ошибка на краю 15-25px. | `effectiveFov = options.fov + VfxDirector.fovOffset()` или `MainCamera.getFov()`. |
| minor | `MegumiShadowDiveHud.java:20-30` + `VfxHudChannel.java:95-102` | **Z-order veil не закреплён.** `renderHud` → `cinematic→nausea→flash→contributions` (LinkedHashMap). Порядок veil vs flash/nausea зависит от порядка регистрации; сейчас veil поверх flash, коммент «under crosshair like nausea» — несоответствие. | Задокументировать порядок или вынести veil перед flash/nausea, покрыть тестом на `contributions` keys. |
| minor | `NobaraTargetLayout.java:104-110` | **HP `round` щедрый.** `filled=round(clamp*10)` → 5% уже 1 сегмент, 95% уже 10. `max<=0||!isFinite→0` закрыто тестом. | Оставить как product (мягче) или `floor` для честности. |
| minor | `NobaraTargetLayout.java:119-151` | **Rank `gradeDisplay` хрупкий.** Строковый мап `contains("special_grade")` + 3 ветки `character_select.*` → новый vessel без обновления вернёт `?`; рефактор локализации сломает молча. | Держать `?` fallback, добавить в `add-vessel` чеклист; альтернатива — `switch(JujutsuCharacter)` без строк. |
| minor | `NobaraTargetHud.java:245-254` + `docs/KNOWN_ISSUES.md:E7` | **Docs drift E7.** `rankKeyFor` — vessel self-check как в E7, но в списке `TodoBlackFlashRuntime/NobaraEspState/...` `NobaraTargetHud` отсутствует. | Добавить `NobaraTargetHud.rankKeyFor` в E7 как accepted display-only. |
| minor | `NobaraTargetHud.java:130-140` | **1px drift на scaled константах.** `Math.round(raw*scale)` для `segW/gap/cap/icon/cell` vs `dividerW=round(hpW)` → при 1.25 `segSpan 49` vs `hpW 48.75`. | Считать `dividerW` из `segW*10+gap*9` или хранить точный `hpW`. Низкий приоритет. |

### 3) Позитивные наблюдения

- **Screen-space cutover верный.** Лидер-гвоздь/`EspTargetData` удалены из `ProjectJjkNailRenderer`, ESP через `VfxDirector.registerHudContribution`+`WorldToScreen` — seam чище, tripwire E14 5→3.
- **Pure-math геометрия.** `NobaraTargetLayout`/`Anim`/`WorldToScreen` без Minecraft imports, покрыты `NobaraTargetLayoutTest`/`WorldToScreenTest`/`NobaraEspRanksTest`/`MegumiCooldownHudTest`; `block()` — единый источник якорей.
- **GuiGraphics без mulPose выполнен.** Весь срез `fill`/`blit(GUI_TEXTURED)`/`drawString` — `grep` 0 хитов в HUD; `SdfRenderer` только в `AbilityHud`, Nobara сознательно «dissolve into Minecraft»; defer `::render` в `NobaraClientDefinition:113-117` избегает `<clinit>` краша.
- **Константы как в спеке.** `BRACKET_GAP 4/CAP 3`, `DIVIDER_H 1` шириной `hpW`, `HP 10×(3+1) H5`, `NAIL_H 16+COUNT_GAP 3`, `RANK_CELL 7`, `CHEST 0.60`, `GAP 3`, `EDGE 4`, `attachScale clamp(9/depth,0.75,1.25)` — честные константы.
- **Позиционирование probe→clamp→block корректно.** `bbWidth*scale*16 clamp 12-40 + SLIDE 6→0` до `clampToScreen(margin 4)` — pipeline верный, `WorldToScreen` точно `Entity.calculateViewVector`+EAST fallback+`MIN_DEPTH 0.5`.
- **Mirror PRIMARY без второго таймера.** `MegumiCooldownHud` читает тот же `READY_AT` что `AbilityHud` overlay; `maxCooldownTicks = max(RECALL, PACK_DEATH)` — знаменатель верный; `SECONDARY_SNEAK_HOLD` помечен «never cooldown».
- **Veil минималистичен.** `MegumiShadowDiveHud` 10 строк: `isFirstPerson`+`diveFadeAlpha(partialTick)` SINK 0→0.75/UNDER 0.75→0.25/EMERGING 0.45→0 + `fill` — data-gated, без второго механизма.
- **Без утечек, O(n) bounded.** `STATES` 3 точки `clear` + `prune`, `snapshot=Map.of()` при null/не-Nobara, `aggregate` unmodifiable, `approach` clamp 0-4т, `refresh` каждые 2т по `entitiesForRendering` ≤30.
- **VFX Core соблюдён.** 3 HUD `registerHudContribution` с `putIfAbsent` guard, `renderHud` после `cinematic/nausea/flash`, `PURE_BLACK`+alpha корректно, `wire ids` append-only.

### 4) Вердикт

**PASS WITH CAVEATS** — инварианты Vessel Seam / VFX Core / server-authoritative соблюдены, позиционирование (bracket/caps/divider/10 сегментов/nail 16*scale/rank 7+gold/CHEST 0.60/clamp) и `GuiGraphics` без `mulPose`/`Matrix3x2f` — как в спеке (проверено grep). Критических (краш/эксплойт/десинк урона) нет.

4 major — визуальная/позиционная полировка (иконка без альфы, camera mismatch, depth/scale, pack-гейт) — закрыть до релизного смока. Остальные minor — hygiene.

**Приоритет фиксов:**
1. P1 `NobaraTargetHud.java:143-146` — alpha иконки
2. P1 `NobaraTargetHud.java:283-289` — Camera из MainCamera
3. P1 `NobaraTargetHud.java:183-185` — depth из `screen.depth()`
4. P1 `MegumiCooldownHud.java:19-33` — pack-гейт/тест инварианта
5. P2 `NobaraTargetAnim.java:32-38` — dead `popScale`
6. P2 `NobaraTargetHud.java:71-108` — vessel гейт NOBARA
7. P2 FOV+veil z-order
8. P3 HP round/`gradeDisplay`/E7/1px drift — polish

AUDIT_ID: 21-hud-target-esp
