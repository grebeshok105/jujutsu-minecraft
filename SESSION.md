# Session Handoff — Jujutsu Minecraft

> **CURRENT 2026-07-20 — NEON GUI (Stage 2 done, paused for context compaction).**
> Активная работа: новое neon dashboard GUI. Worktree `.qoder/worktrees/neon-gui`, ветка `worktree-neon-gui`.
> Stage 2 (SDF shader pipeline + probe) ЗАКОММИЧЕН в `e4404f2`. Компиляция подтверждена (`BUILD SUCCESSFUL`).
> **In-game рендер-проверка шейдера НЕ выполнена** (GLSL компилируется в рантайме) — это первое, что нужно сделать.
> План: `C:\Users\KOMP1\.qoder\plans\northern-narrows-chub.md`. Vault: `jujutsumod-codebase-codex/04-client-vfx/GUI-neon-dashboard.md`.

---

## Где мы сейчас

- Проект: `D:\WorkFlow\Jujutsu Minecraft`
- Активный worktree: `D:\WorkFlow\Jujutsu Minecraft\.qoder\worktrees\neon-gui`
- Ветка: `worktree-neon-gui` (НЕ main; main = `72f36a3` с Black Flash VFX + старой GUI)
- Последний коммит worktree: `e4404f2 feat(gui): SDF shader pipeline + probe screen (Stage 2)`
- Инстанс: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar` (туда копировать собранный jar)

## Цель (goal)

Реализовать все стейджи Neon GUI (Stage 2-7). После КАЖДОГО стейджа — супер-ревью субагентами на баги. Финал: готовый мод с новым GUI, собранный jar. Пользователь явно попросил: дальше работать параллельно с фоновыми субагентами.

## Статус стейджей

| Stage | Содержание | Статус |
|---|---|---|
| 0 | Baseline (worktree + green build) | DONE |
| 1 | HTML-макет (scalable roster, per-char theme, Apple emoji, голова Nobara) | DONE, approved |
| 2 | SDF shader pipeline + probe screen | DONE (compile ✅, **in-game render ⏳**) |
| 3 | Kit core + пустое draggable окно | НЕ НАЧАТ |
| 4 | Sidebar, pages, scroll, shell controls | НЕ НАЧАТ |
| 5 | Character page (порт SelectCharacterPayload) | НЕ НАЧАТ |
| 6 | Polish (KeybindField, ColorPicker, звуки, lang) | НЕ НАЧАТ |
| 7 | Cleanup (удаление legacy, тесты, доки) | НЕ НАЧАТ |

## ПЕРВОЕ ДЕЙСТВИЕ после компакта: in-game проверка probe

1. `cd .qoder/worktrees/neon-gui && gradlew.bat build --no-daemon -x test`
2. Скопировать `build/libs/jujutsumod-1.0.0.jar` в `D:\Games\instances\Jujutsu\mods\` (перезапустить инстанс — Java держит jar открытым).
3. `gradlew.bat runClient --no-daemon` (или запустить инстанс), зайти в мир, нажать **V**.
4. Должен открыться `SdfProbeScreen`: тёмное окно с оранжевой рамкой/glow, sidebar, 2 карточки, кнопка CONFIRM, текст поверх фигур.
5. Проверить: скругления без лесенки (GUI scale 1/2/4), нет shader-ошибок в `latest.log`, нет blaze3d leak-варнингов.
6. Если шейдер НЕ работает → go/no-go гейт: чинить pipeline или откат на fill-based (`UiRender`).

## Ключевые тех. факты (SDF pipeline, MC 1.21.6+ API)

- В 1.21.6+ НЕТ `CoreShaderRegistrationCallback` (Fabric API для шейдеров удалён). Регистрация через `RenderPipelines.register(...)` в Java.
- Core-шейдеры — `.vsh`/`.fsh` в `assets/<ns>/shaders/core/`, авто-дискавери, БЕЗ .json.
- Файлы: `ui/neon/render/SdfPipelines.java` (pipeline + VertexFormat 27 float), `SdfShape.java`, `SdfRenderer.java`; шейдеры `assets/jujutsumod/shaders/core/sdf_shape.{vsh,fsh}`; probe `gui/SdfProbeScreen.java`.
- **Правило слоёв (критично):** immediate draw в `Screen.render` попадает ПОД GuiGraphics (retained-mode, flush в конце). SDF = фоновые поверхности; текст/blit = GuiGraphics (всегда сверху). Поверх виджетов — только через Mixin в GuiRenderer (избегать).
- `uploadImmediateVertexBuffer` → GpuBuffer кэшируется в VertexFormat, НЕ close(). `getSequentialBuffer(QUADS).getBuffer(N*6)`, НЕ close(). `drawIndexed(0,0,indexCount,1)`. RenderPass — try-with-resources.
- Проекция: `CachedOrthoProjectionMatrixBuffer("jujutsumod_sdf",1000,11000,true)` + `setProjectionMatrix(slice,ORTHOGRAPHIC)`. DynamicTransforms: modelView=translation(0,0,-11000), colorModulator=(1,1,1,1).
- V сейчас временно открывает `SdfProbeScreen` (в `JujutsuKeybinds`). В Stage 3 — на реальный dashboard.

## Дизайн (источник правды — HTML-макет)

- Макет: `docs/gui/2026-07-20-neon-dashboard-mockup.html` + рендеры `mockup-v3-nobara.png` / `mockup-v3-none.png`.
- Тема следует за выбранным персонажем (НЕ user-picked). Nobara = orange `#E48A36` / deep `#8B3F1C`.
- В игре только Nobara + NONE; остальные (Gojo/Sukuna/Megumi/Yuji) — "SOON" плейсхолдеры.
- Иконки — Apple Color Emoji (`docs/gui/AppleColorEmoji-Windows.ttf`, 256MB, gitignored). В игре → пре-рендер в PNG pixel-perfect (без масштабирования, без мыла — явное требование пользователя).
- Голова Nobara — скин `textures/entity/character/nobara.png` (голова UV 8,8 + шляпа 40,8).
- Window: draggable за header, r=10, tight outer glow (12px -5, НЕ толстый), top highlight.
- Пользовательские правки макета (v3): NONE в ростере, корректная голова, воздух внизу (footer/abilities/buttons), затянутый glow.

## Обязательные правила (AGENTS.md + пользователь)

- Клиентский код — только `src/client`. Не трогать gameplay/VFX (VfxCue→VfxDirector контракт).
- Коммиты маленькие, conventional, английские. После каждого стейджа — супер-ревью субагентами.
- Обновлять Obsidian codex после значимых изменений (`GUI-neon-dashboard.md` уже создана + ссылка в MOC).
- Собирать jar и копировать в инстанс после работы над модом.
- Пользователь хочет параллельную работу с фоновыми субагентами.
- Skills: `verification-before-completion` перед любым "готово"; `using-superpowers` уже загружен.

## Что читать следующей сессии

- `AGENTS.md`
- План: `C:\Users\KOMP1\.qoder\plans\northern-narrows-chub.md`
- Vault: `jujutsumod-codebase-codex/04-client-vfx/GUI-neon-dashboard.md` и `00-MOC.md`
- Макет: `docs/gui/2026-07-20-neon-dashboard-mockup.html`

---

## SUPERSEDED (предыдущие handoff, работа уже в main)

> **SUPERSEDED 2026-07-12:** Resonant Momentum / hammer / embedded nail — всё в main. Не возобновлять.

> **SUPERSEDED 2026-07-11:** Nobara cinematic slice (R/B chains, depth, trap, Black Flash, remnant, Resonant Momentum) — в main.

Дата: 2026-07-09 (исходный)

## Где мы были (архив)

- Проект: `D:\WorkFlow\Jujutsu Minecraft`
- Старый worktree: `.worktrees\nobara-cinematic-slice` (ветка `codex/nobara-cinematic-slice`) — уже в main.

## Главное состояние (архив)

ProjectJJK Nobara — единственная каноничная Нобара. Старый jujutsumod Nobara/Hairpin стек удалён. Id `hairpin_nail` и `straw_doll_hammer` — теперь ProjectJJK items/models.

## Важные ловушки (актуально)

- Не возвращать старый `NobaraHairpinRuntime`, `HairpinFxPayload`, `HairpinPlaybackManager`, `NobaraNailFlightManager`.
- Старые имена в `ProjectSanityTest` — negative assertions, это нормально.
- Часть `hairpin_*` используется текущей ProjectJJK Nobara (не legacy).
- После работы над модом — собирать jar и копировать в `D:\Games\instances\Jujutsu\mods`.
