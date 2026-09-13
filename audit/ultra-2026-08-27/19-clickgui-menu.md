## ClickGui — 19-clickgui-menu

### 1) Что проверено (файлы/символы)

**Инструменты проверки:** `glob src/client/java/jujutsu/mod/client/rich/**/*.java`, `grep ClickGui|clickgui`, `grep getScaleMultiplier|FIXED_GUI_SCALE|scaleFactor`, `read` по каждому ключевому файлу, `codegraph_explore` (fallback grep когда индекс пуст), ручная сверка с AGENTS.md §212-217, Codex `04-client-vfx/GUI-render-pipelines.md`, `04-client-vfx/GUI-character-select.md`, `docs/KNOWN_ISSUES.md`, `ProjectSanityTest.assertClickGuiPanelIsDraggableAndOwnsTheCrosshair`.

**Проверенные файлы и символы (точные пути):**

| файл | символы / зона |
|---|---|
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/ClickGui.java:25-260` | `ClickGui extends Screen`, `INSTANCE` singleton, `FIXED_GUI_SCALE=2`, `DRAG_HANDLE_HEIGHT=38f`, `MIN_HANDLE_VISIBLE=24f`, `panelOriginX()/panelOriginY()`, `clampDragToScreen()`, `scaleFactor()`, `render()`, `mouseClicked()`, `mouseDragged()`, `mouseReleased()`, `onClose()`/`close()`, `isPauseScreen()` |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/DragHandler.java:1-104` | `DragHandler` pure geometry, `startDrag()`, `drag()`, `clampTo()`, `endDrag()`, `reset()`, `update()`, `isResetNeeded()`, `ANIMATION_SPEED=10f` |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/ClickGuiHud.java:1-39` | `ClickGuiHud.register()` → `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, ...)`, `isClickGuiOpen()` |
| `src/client/java/jujutsu/mod/client/rich/util/render/Render2D.java:14-166` | `FIXED_GUI_SCALE=2.0f`, `SDF: SdfRenderer`, `beginFrame()/endFrame()`, `getFixedScaledWidth/Height()`, `getFixedGuiScale()`, `getScaleMultiplier()`, `shape()` → `SDF.begin/add/flush` per rect |
| `src/client/java/jujutsu/mod/client/ui/neon/render/SdfRenderer.java:38-232` | `flush()`, `drawBatch()`, `ensureSceneCopy()`, `putVertex()`, `ByteBuffer.allocateDirect(...).order(nativeOrder())`, `backupProjectionMatrix/restoreProjectionMatrix`, `PAD=6f` |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/character/CharacterRosterPanel.java:24-289` | `CARDS = JujutsuCharacterClients.inRosterOrder()`, `syncFromClient()`, `render()`, `renderCard()`, `renderAbilityStrip()`, `renderConfirm()`, `mouseClicked()`, `selectPreview()`, `applySelection()` (`ClientPlayNetworking.send SelectCharacterPayload` + `applyLocal` + `screen.onClose()`), `cardBounds()/confirmBounds()/hit()` |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/background/BackgroundComponent.java:16-139` | `BG_WIDTH=400 BG_HEIGHT=250`, `render()`, `renderCategoryPanel()`, `renderHeader()`, `renderCategoryNames()`, `getCategoryAtPosition()` |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/background/render/CategoryRenderer.java:1-158` | `LIVE_CATEGORIES={COMBAT}`, `SOON_COUNT=2`, `render()`, `renderCategoryItem(interactive)`, `getCategoryAtPosition()` (soon rows ignored) |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/background/render/BackgroundRenderer.java:1-45` | SDF chrome |
| `src/client/java/jujutsu/mod/client/rich/screens/clickgui/impl/background/render/HeaderRenderer.java:1-67` | header chrome, `isSearchBoxHovered()=false` |
| `src/client/java/jujutsu/mod/client/rich/theme/ClickGuiTheme.java:1-92` | `setCharacter()`, `snapTo()`, `tick()`, `accent()/warm()`, делегирование accent/warmth из `CharacterClientDefinition` (Vessel Seam) |
| `src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java:58-228` | `register()`, `modernMenu=N`, `toggleModern()`, `slot()/sendCharacterAbility()` |
| `src/client/java/jujutsu/mod/client/JujutsuModClient.java:18-41` | `CharacterSelectionView.setClientLookup`, `JujutsuCharacterClients.registerAll()`, `SdfPipelines.SDF_SHAPE` check, `MsdfFonts.bootstrap()`, `Initialization.getInstance()`, `ClickGuiHud.register()` |
| `src/client/java/jujutsu/mod/client/rich/Initialization.java:1-24` | `Manager` init + `new ClickGui()` singleton |
| `src/client/java/jujutsu/mod/client/rich/manager/Manager.java:14-58` | `clickgui = new ClickGui()` |
| `src/client/java/jujutsu/mod/client/rich/modules/jujutsu/JujutsuModules.java:1-27` | `registerAll()` итерирует `JujutsuCharacterClients.all()` |
| `src/client/java/jujutsu/mod/client/rich/modules/module/category/ModuleCategory.java:1-20` | `COMBAT("Characters")` + 5 placeholder категорий |
| `src/client/resources/jujutsumod.client.mixins.json:1-16` | 6 миксинов, отсутствие Crosshair/GuiMixin |
| `src/test/java/jujutsu/mod/ProjectSanityTest.java:989-1021` | `assertClickGuiPanelIsDraggableAndOwnsTheCrosshair` gate |
| `src/test/java/jujutsu/mod/client/rich/screens/clickgui/impl/DragHandlerTest.java:1-98` | геометрические инварианты drag |
| `src/client/java/jujutsu/mod/client/character/ClientCharacterSelectionManager.java:16-117` | `applyLocal/apply` + `cancelFirstPersonOnVesselChange` |

Сверено с контрактами: AGENTS.md §212-217 (GUI-scaled space, flush, replaceElement), KNOWN_ISSUES E6/E7, Vessel Seam §219-224 (accent/warmth, moduleName из definition).

---

### 2) Находки

| severity | файл:строка | описание | рекомендация |
|---|---|---|---|
| **major** | `ClickGui.java:80-83` | Мёртвый метод `scaleFactor()` — вычисляет `FIXED_GUI_SCALE / guiScale` и нигде не используется. Прямое нарушение инварианта AGENTS.md:216 "never convert a drag offset or hit test through `Render2D.getScaleMultiplier()`". Сам по себе не ломает, но провоцирует регрессию: следующий автор увидит готовый helper и применит его к drag, сломав 1:1 travel на scale 1/4 (именно этот баг уже фиксили по Codex GUI-render-pipelines). `ProjectSanityTest` ловит только `getScaleMultiplier()`, но не `scaleFactor()`. | Удалить метод. Добавить в `assertClickGuiPanelIsDraggableAndOwnsTheCrosshair` проверку `!clickGui.contains("scaleFactor")` или оставить комментарий `// deleted intentionally — see AGENTS.md panel geometry`. |
| **major** | `Render2D.java:50-53` | `getScaleMultiplier()` всё ещё публичен, хотя по AGENTS.md:216 и Codex должен быть неиспользуемым. Любой новый экран/оверлей может его вызвать и получить рассинхрон рендера и hit-test. Сейчас не вызывается (подтверждено `grep`), но API остаётся ловушкой. | Пометить `@Deprecated(forRemoval=true)` с javadoc "Do not use — ClickGui lives in one GUI-scaled space; see AGENTS.md", либо удалить если нет внешних потребителей. Альтернатива — сделать private и удалить вовсе. |
| **major** | `ClickGui.java:75,117` | Двойной `ClickGuiTheme.tick()`: в `tick()` (1f) и в `render()` (deltaTicks 0.05..3f). При 60 fps тема ускоряется в ~2× (tick + render каждый кадр). При просадке до 20 fps — ещё сильнее, т.к. `render.delta` >1. Визуально — accent/warmth "дёргается" быстрее чем задумано, openProgress/hover уже используют один проход. | Оставить tick только в одном месте. Канонично — в `tick()` (вызывается 20 tps) или в `render()` с нормализованным delta; но не в обоих. Если нужен smooth — убрать из `tick()` и оставить только `render()`. |
| **major** | `DragHandler.java:1,101-103` | `import org.lwjgl.glfw.GLFW` + метод `isResetNeeded()` нигде не вызывается. `grep isResetNeeded` → только определение. GLFW остался после отделения DragHandler от GLFW polling (Codex: "no IMinecraft, no GLFW polling"). Мёртвый импорт держит зависимость, которую специально удаляли для теста без Minecraft. | Удалить импорт и метод. Если Ctrl-reset планировался — реализовать через `ClickGui.keyPressed` и `DragHandler.reset()`, иначе это мёртвый код. |
| **minor** | `DragHandler.java:36-44` | `update()` использует `System.currentTimeMillis()` для easing `offset→target`. Wall-clock скачет при NTP/дебаге/сне машины; `deltaTime` ограничен 0.1f, но всё равно зависимость от wall clock вместо `tick`/`deltaTicks`. При длительной паузе — резкий рывок. Сейчас drag пишет `offset` напрямую, easing касается только programmatic `target`, поэтому баг редкий. | Заменить на `Util.getMillis()` / передавать `deltaTicks` из `ClickGui.tick/render` как в `BackgroundComponent.updateAnimations`. Или удалить easing вовсе — `target==offset` во всех текущих path, `update()` фактически no-op во время drag. |
| **minor** | `ClickGui.java:163-165` | Закрытие через `mc.setScreen(null)` внутри `render()`. В современных версиях `Screen.render` не ожидает смены экрана mid-frame; может пропустить `removed()`/`onClose()` следующего кадра. Сейчас работает т.к. `closing` флаг, но хрупко. | Вынести в `tick()`: `if (closing && animValue<=0.01f) mc.setScreen(null);` — как в vanilla `Screen` lifecycle. |
| **minor** | `ClickGui.java:26,42-43` | `public static ClickGui INSTANCE` мутабельный singleton, перезаписывается в конструкторе. `Manager` уже владеет экземпляром (`manager.getClickgui()`), а `JujutsuKeybinds.toggleModern` берёт его оттуда; `INSTANCE` используется только в комментариях/Codex для объяснения session-only позиции. Двойной источник истины, риск рассинхрона если кто-то вызовет `new ClickGui()` повторно. | Сделать `INSTANCE` private + getter, или удалить и везде использовать `Initialization.getInstance().getManager().getClickgui()`. Как минимум сделать `final` и проверять `if (INSTANCE!=null) throw`. |
| **minor** | `ClickGui.java:51-56` | `init()` вызывает `dragHandler.endDrag()` и `clampDragToScreen()`, но не сбрасывает `closing`. При ресайзе окна во время close-анимации `init()` переводит `closing=false`, обрывая fade-out. | В `init()` проверять `if (closing) return;` или сохранять closing и не ресетать анимацию. |
| **minor** | `ClickGui.java:128-129` | `Render2D.rect(0,0,5000,5000, ...)` — scrim рисуется гигантским rect вместо `width/height`. Работает т.к. SdfRenderer + PAD, но при `guiW/guiH` <5000 оставляет хвосты за пределами clip, а при больших `guiScale` — лишние пиксели за пределами фреймбуфера. | Заменить на `Render2D.rect(0,0, this.width, this.height, ...)` или `guiW/guiH` из `Render2D.getFixedScaledWidth()`. |
| **minor** | `CharacterRosterPanel.java:42-61,172-232` | `applySelection()` = optimistic `applyLocal` + `send(SelectCharacterPayload)` + `screen.onClose()` без проверки `canSend` failure. Если в момент клика игрок вне мира / канал не готов, локальный preview остаётся Nobara, сервер остаётся на старом vessel — раскол до следующего `CharacterSelectionSyncPayload`. `JujutsuKeybinds.sendCharacterAbility` уже штампует `character.id()` для защиты от этого окна, но сам факт раскола UI ↔ server на 1-2 тика остаётся. | После `canSend==false` откатывать `preview` (`syncFromClient()`) или показывать toast. Либо не вызывать `onClose()` до получения эха (хуже UX). Минимум — логировать `LOG.warn` при `!canSend`. |
| **minor** | `CharacterRosterPanel.java:238-244` | `lastPanelW/H/Gap/CardH` — mutable layout cache, инициализируется лениво в `syncFromClient()` дефолтами 298×204. Если первый `render()` вызван до `syncFromClient()` (теоретически `Manager` создаёт ClickGui до `Minecraft.player!=null`), `cardBounds()` делит на `CARDS.length` с дефолтом — ок, но `confirmBounds()` и `hit()` работают на тех же дефолтах один кадр, потом прыжок. | Инициализировать поля в объявлении (`private float lastPanelW=298f` и т.д.), убрать ленивый `if (lastPanelW<=1f)` блок. |
| **minor** | `ClickGui.java:96,104` + `DragHandler.java:82-86` | `clampTo` с инвертированным range (`min>max`) возвращает `min`. При очень маленьком окне (`width < MIN_HANDLE_VISIBLE` или `height < MIN_HANDLE_VISIBLE`) clamp range инвертируется и панель "прилипает" к `min`. Тест `DragHandlerTest.assertClampConfinesBothAxes` проверяет этот случай и ожидает collapse to min — поведение задокументировано, но UX странный: панель не центрируется. | При `min>max` возвращать `(min+max)/2` или центр экрана. Сейчас оставляем как есть — согласовано с тестом; фиксировать только если появится репорт. |
| **minor** | `SdfRenderer.java:127-128,141` | `ByteBuffer.allocateDirect(batch.size()*4*stride)` + `uploadImmediateVertexBuffer` на каждый `flush()`. `Render2D.shape()` флашит per-rect (корректность ради MSDF ordering, см. AGENTS.md:214 и Codex), значит на один ClickGui кадр ~20-30 аллокаций direct buffer. E6 в KNOWN_ISSUES уже помечен как "avoidable per-shape work" — профилировать перед редизайном. | Не фиксить сейчас (корректность важнее), но при профилировании: батчить opaque/glass раздельно за кадр и переиспользовать `ByteBuffer` (ThreadLocal) — только вместе с depth/ordering для MSDF. |
| **minor** | `SdfRenderer.java:98-118,115` | `flush()` ловит `RuntimeException|LinkageError`, логирует и всё равно `restoreProjectionMatrix()` в finally — хорошо. Но `LinkageError` ловится только вокруг `setProjectionMatrix/drawBatch`, а `ensureSceneCopy` вне try — при ошибке создания текстуры `sceneCopy` остаётся null и glass fallback молча рисует только tint (логируется). | Вынести `ensureSceneCopy` внутрь try или добавить отдельный catch вокруг него — сейчас уже есть try/catch внутри `ensureSceneCopy` (стр.196), так что фактически покрыто. Оставить как есть. |
| **minor** | `CategoryRenderer.java:135-146` | Hit-test границы `bgX+10..+95` × `catY..catY+13` жёстко зашиты, а рендер `ROW_H=15f` + `FIRST_ROW_Y=65f`. Между live row и первым Soon row зазор 2px не кликабелен — ок. Но если добавится второй live таб, `SOON` rows окажутся кликабельными по gap? Сейчас Soon игнорируются явно — корректно. | При добавлении live категории заменить `SOON_COUNT` на динамический расчёт или вынести hit-test в таблицу. Сейчас PASS. |

Итого: 3 major (мёртвый `scaleFactor`, публичный `getScaleMultiplier`, двойной `tick`) + 1 major-мёртвый GLFW + 9 minor. Критических (потеря данных/краш/десинк) нет.

---

### 3) Позитивные наблюдения

- **Characters sidebar — образцовый Vessel Seam.** `CharacterRosterPanel.CARDS = JujutsuCharacterClients.inRosterOrder()`, `JujutsuModules.registerAll()` итерирует `JujutsuCharacterClients.all()`. Добавление vessel не требует правок в ClickGui/ростер/тему (проверено `ProjectSanityTest.assertClickGuiModulesCoverEveryVessel`). Accent/warmth берутся из `CharacterClientDefinition.accent()/warmth()`, `ClickGuiTheme` только easing — точное соблюдение AGENTS.md:50 и §219.
- **Soon placeholders — корректно non-clickable.** `CategoryRenderer.LIVE_CATEGORIES={COMBAT}` единственный hit-testable; `SOON_COUNT=2` рендерится с `interactive=false` (dim 70 alpha, серый 90,90,95) и `getCategoryAtPosition()` явно игнорирует Soon rows (`return null`). `BackgroundComponent.getCategoryAtPosition` делегирует туда же — консистентно.
- **Drag — чистая геометрия без GLFW.** `DragHandler` не импортирует `IMinecraft`/`Minecraft`, offset считается от grab point (`dragStartOffset + mouse-grabStart`), а не аккумулируется — dropped motion не вызывает creep. `clampTo` вызывается на каждый `mouseDragged` и из `init()` (ресайз). Header 38px (left) vs whole panel 400×250 (middle) — `mouseClicked` проверяет все интерактивные элементы до `startDrag`, header не крадёт клики по картам/Confirm.
- **Session-only позиция — верно.** `ClickGui` singleton (`INSTANCE` / `Manager.clickgui`) хранит `DragHandler.offset`; `width/height` + `panelOriginX/Y` переиспользуются в render и hit-test — один accessor. Нет персиста в `options.txt`/`Config` — соответствует AGENTS.md:216 "Panel position is session-only — the project has no UI-state persistence."
- **GUI-scaled space без getScaleMultiplier — соблюдено.** `panelOriginX/Y`, `BackgroundComponent.BG_WIDTH/HEIGHT`, `Render2D` и `SdfRenderer` живут в `FIXED_GUI_SCALE=2` пространстве; `width/height` уже делённые. `DragHandler.drag` прибавляет `mouseX-dragStartX` 1:1 без деления. `grep getScaleMultiplier` в `src/client/java/jujutsu/mod/client/rich/screens/clickgui` — 0 вхождений (единственные — определение в `Render2D` и мёртвый `ClickGui.scaleFactor`).
- **Crosshair decline — каноничный `replaceElement`.** `ClickGuiHud.register()` → `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla->{ if (screen instanceof ClickGui) return; vanilla.render(...) })`. Не `removeElement`, не `hideGui`, не mixin (проверено `jujutsumod.client.mixins.json` — 6 миксинов, без Crosshair/Gui). Условие `instanceof ClickGui` покрывает и close-fade (screen остаётся установленным до `animValue<=0.01`), убирая flicker.
- **SdfRenderer flush — корректность прежде перфоманса.** `Render2D.shape()` делает `begin/add/flush` per-rect чтобы MSDF текст между rects не закапывался под late SDF batch (исторический баг "chrome без labels"). `flush()` бэкапит проекцию, ставит орто `CachedOrthoProjectionMatrixBuffer("jujutsumod_sdf",1000,12000)`, `nativeOrder()` direct buffer, `PAD=6` для glow, `try/finally restoreProjectionMatrix`, catch+log без краша. Разделение opaque/glass с `SceneSampler` copy — ленивое, только если `highlight<0`.
- **Тестовое покрытие drag — без Minecraft.** `DragHandlerTest` (task `testClickGuiDrag`) покрывает grab region, 1:1 travel, no double-apply, release freeze, regrab without jump, clamp both axes + inverted range, reset — всё без GLFW/Minecraft.
- **SDF/MSDF lifecycle — fail-fast.** `JujutsuModClient` проверяет `SdfPipelines.SDF_SHAPE != null`, `MsdfFonts.bootstrap()` из init — без silent textless GUI.

---

### 4) Вердикт

**PASS WITH CAVEATS**

ClickGui среза `19-clickgui-menu` реализован аккуратно и соответствует всем заявленным инвариантам ТЗ:

- Characters sidebar live + Soon non-clickable ✓
- drag header (LMB 38px) / middle-mouse whole panel + clamp 24px ✓
- session-only позиция (singleton, один accessor) ✓
- crosshair decline через `HudElementRegistry.replaceElement` (replace, not remove, без mixin) ✓
- GUI-scaled space без `getScaleMultiplier()` (0 использований в clickgui path) ✓
- `SdfRenderer.flush()` immediate с backup/restore, nativeOrder, per-rect для MSDF ordering ✓
- Vessel Seam соблюдён (accent/warmth/moduleName из definition, roster из registry) ✓

Критических багов, десинков выбора vessel, утечек GPU-ресурсов или нарушений server-authoritative нет. Найденные major — это мёртвый код-ловушка (`scaleFactor`, `getScaleMultiplier`, `isResetNeeded`) и двойной tick темы; minor — wall-clock, singleton двойственность, scrim 5000×5000, optimistic UI окно. Ни один не требует отката среза.

**Приоритет фиксов:**

1. **P1 (до релиза):** удалить `ClickGui.scaleFactor()`, пометить/удалить `Render2D.getScaleMultiplier()`, убрать `GLFW` импорт + `isResetNeeded()` из `DragHandler`, убрать двойной `ClickGuiTheme.tick()` (оставить один).
2. **P2 (следующий проход):** `scrim 5000×5000 → width/height`, `setScreen(null)` из `render` → `tick`, `INSTANCE` сделать private/final, `lastPanelW/H` инициализировать в поле, `applySelection` логировать `!canSend`.
3. **P3 (техдолг, не блокирует):** заменить `System.currentTimeMillis` на `Util.getMillis`/deltaTicks, централизовать clamp при инвертированном range, профилировать SdfRenderer per-rect аллокации (E6) только если in-game профайлер покажет cost.

AUDIT_ID: 19-clickgui-menu
