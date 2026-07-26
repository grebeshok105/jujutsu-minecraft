# GUI Render Pipelines — MSDF Text and SDF Panels

Status: CURRENT

Two custom GPU paths back the single product menu. They are separate pipelines that share a frame, not one code path. All claims VERIFIED against `src/client/java/jujutsu/mod/client/ui/**` and `src/client/java/jujutsu/mod/client/rich/util/render/**` unless labelled otherwise.

| Path | Class | Draws |
|---|---|---|
| MSDF text/icons | `MsdfFonts` → `MsdfFontPipeline` (multi-atlas) | all ClickGui type and icon glyphs |
| SDF shapes | `SdfRenderer` → `SdfPipelines.SDF_SHAPE` | ClickGui panels, rects, gradients, glow |

`Render2D` is the adapter the ported Rich UI code calls; it routes shapes to `SdfRenderer` and text to `MsdfFonts`.

## MSDF faces

`MsdfFonts.Face` has exactly four entries, each backed by a JSON + PNG pair under `assets/jujutsumod/fonts/` (client resources):

| Face | Asset stem |
|---|---|
| UI | `ui` |
| BOLD | `bold` |
| ICONS | `guiicons` |
| CATEGORY | `categoryicons` |

More atlas pairs exist in that folder (`default`, `regular`, `regularnew`, `hudicons`, `icons`, `iconstypetho`, `mainmenuicons`, `test`) — they are leftovers from the port and are **not** loadable through `Face`. `Fonts` maps the Rich font names onto the four live faces and falls through to `Face.UI` by default, so an unknown Rich name degrades to body text rather than failing.

Lifecycle: `MsdfFonts.bootstrap()` from client init throws if the pipeline failed to register (fail fast, no silent textless GUI); `warm()` force-loads any atlas with zero glyphs and is called both from `Render2D.beginFrame()` and from every `draw`; `endFrame()` drains leftover text. Shader pair: `assets/jujutsumod/shaders/core/msdf.vsh` / `.fsh`.

## Fixed GUI scale

Both `Render2D.FIXED_GUI_SCALE = 2.0f` and `ClickGui.FIXED_GUI_SCALE = 2` pin the menu to its own coordinate space, and `Render2D` derives width/height by dividing the window's scaled size by it. The consequence to remember: ClickGui geometry is independent of the player's Minecraft GUI-scale setting, so a layout bug will not reproduce by changing that setting.

## Per-rect flush — a correctness constraint, not overhead

`Render2D` flushes the SDF surface **per rect** rather than batching the frame. The reason is ordering: `SdfRenderer.flush()` draws in one immediate pass, so a late batch would land on top of MSDF text drawn between rects and bury it. The historical symptom was a fully drawn chrome with no labels at all.

Anyone profiling SDF/MSDF batching must treat interleaving as the requirement. Re-batching is only valid together with a real depth or ordering scheme for the text.

## SDF GPU lessons — still live

`SdfRenderer` backs the ClickGui panels, so these are current, not historical. They were expensive to learn and are easy to regress:

- **Buffer endianness.** The vertex `ByteBuffer` must be `allocateDirect(...).order(ByteOrder.nativeOrder())`. Default JVM big-endian byte order produces garbage vertices.
- **Projection backup/restore.** `RenderSystem.backupProjectionMatrix()` before setting the orthographic slice, and `restoreProjectionMatrix()` in a `finally`. Skipping the restore corrupts the rest of the frame, including the world.
- **Far-plane margin.** The cached ortho buffer spans 1000..12000 and the dynamic transform translates z by `-11000`. The margin is what keeps the shapes inside the frustum; changing one number without the other silently drops every panel.
- **Do not close the returned vertex buffer.** `SDF_SHAPE_FORMAT.uploadImmediateVertexBuffer` returns a buffer cached inside the `VertexFormat`. Closing it breaks later frames. Only `projection` is closed, in `close()`.
- **Quad padding.** Each shape's quad is inflated by `glowRadius + PAD` (PAD = 6) so glow, shadow, and AA have room; a tight quad clips its own glow.
- **Draw order.** The pass renders under the vanilla `GuiGraphics` batch, which flushes last — that is why SDF shapes act as background surfaces.
- **Failure is contained.** `flush()` catches `RuntimeException | LinkageError`, logs `SDF draw failed`, and still restores the projection. A driver or pipeline failure loses the panels for that frame instead of crashing the client.

## ClickGui metrics

| Metric | Value | Source |
|---|---:|---|
| Background size | 400 × 250 | `BackgroundComponent.BG_WIDTH` / `BG_HEIGHT` |
| Fixed scale space | 2 | `ClickGui.FIXED_GUI_SCALE` |
| Open animation | 250 ms default | `ClickGui.openAnimation`, `GuiAnimation.ms` |
| Sidebar width | 92 px before content | `ClickGui` `contentX = bgX + 92f` |
| Drag handle height | 38 px | `ClickGui.DRAG_HANDLE_HEIGHT` |
| Handle kept reachable | 24 px | `ClickGui.MIN_HANDLE_VISIBLE` |

## Open path

Key N (`key.jujutsumod.modern_menu`, default `InputConstants.KEY_N`) → `JujutsuKeybinds.toggleModern` → `Initialization.getInstance().getManager().getClickgui()` → `Minecraft.setScreen`.

`toggleModern` closes the ClickGui if it is already the current screen, does nothing if some other screen is open, and logs `ClickGui failed to initialize` if the manager returns null. There is no fallback screen — the retired `ModernMenuScreen` no longer exists.

The screen is a singleton (`ClickGui.INSTANCE`), which is why the dragged panel position survives close and reopen without any persistence layer.

## Panel drag

Left mouse grabs the 38 px header band; middle mouse keeps the whole 400 × 250 rect as a grab surface, as it always did. The header is the only band of the panel with no click target of its own, and the drag branch in `mouseClicked` sits **after** every interactive handler, so a tab, a roster card, or the confirm button always wins the press.

Two things about the geometry are worth keeping straight, because both were bugs:

- **No scale conversion.** `ClickGui` used to divide the drag offset by `Render2D.getScaleMultiplier()` (`2 / guiScale`) while `DragHandler` accumulated raw mouse deltas. That is only correct at GUI scale 2: the panel lagged the cursor at scale 1 and outran it 2× at scale 4. Mouse coordinates, the screen's `width`/`height`, and the SDF surfaces all live in one GUI-scaled space, so the conversion was never needed and is gone.
- **One origin accessor.** `panelOriginX()` / `panelOriginY()` are read by both `render` and `mouseClicked`, so the offset can never be applied to drawing and not to hit testing.

`DragHandler` is now pure geometry in screen pixels: no `IMinecraft`, no GLFW polling, no live window. The screen owns the input and feeds it in through the vanilla `mouseDragged` event. The offset is recomputed from the grab point rather than accumulated, so a dropped motion event cannot make the panel creep, and `clampTo` confines it so at least 24 px of the handle stays reachable — called on every drag step and again from `init()`, which also runs on window resize. `endDrag` happens on release **before** the closing gate, and again in `onClose` and `init`; previously a release during the close animation left `dragging = true`, so reopening resumed a drag with no button held.

Position is session-only by decision: the project has no UI-state persistence and none was added for this.

`DragHandlerTest` (Gradle task `testClickGuiDrag`) covers the grab region, one-to-one travel, no double-apply on a repeated position, release, regrab without a jump, both clamp axes including an inverted range, and reset. It runs without a Minecraft instance — that is the payoff of dropping the GLFW dependency.

## Vanilla crosshair while the menu is open

The crosshair was not merely under the menu, it was drawn **over** it. This is the ordering fact to remember:

- `Gui.render` calls `renderCrosshair` with no open-screen gate — vanilla relies on the inventory being drawn after it.
- That blit is only *recorded* into the GUI render state, then rasterized by `GuiRenderer` at the very end of `GameRenderer.render`, i.e. after Screen rendering.
- The ClickGui instead rasterizes **immediately**, inside `SdfRenderer.flush()` (see the per-rect flush constraint above). Its pixels are already on the framebuffer when the crosshair composites on top — with `BlendFunction.INVERT` (INFERRED from the vanilla draw), which is why the crosshair reads through the panel instead of being covered by it.

So raising the scrim alpha could never have fixed it. An opaque scrim is still just earlier pixels; the crosshair lands after them regardless. Declining the draw is the only mechanism that works.

`ClickGuiHud.register()`, called once from `JujutsuModClient`, wraps `VanillaHudElements.CROSSHAIR` through `HudElementRegistry.replaceElement` and returns early while `Minecraft.getInstance().screen instanceof ClickGui`. Notes on the choice:

- No mixin and no seventh entry in the client mixin config — Fabric already wraps vanilla's `renderCrosshair` call site.
- **Replace, not remove.** The vanilla element stays one condition away, so nothing has to be restored on close and an abnormal close cannot leak a permanently hidden crosshair.
- The gate is true for the whole close animation as well, since the screen stays set until the panel has faded; popping the crosshair back mid-fade would read as a flicker.
- Every other HUD element, third-person mode, and the F3 debug crosshair are untouched.

## Modules

`JujutsuModules.registerAll` registers one `ModuleStructure` per vessel — three today (`Nobara`, `Todo`, `None`), all in `ModuleCategory.COMBAT` (the sidebar renders that category as "Characters") — built by walking `JujutsuCharacterClients.all()` rather than written out, with each row's label, blurb and starting state coming from that vessel's `moduleName`/`moduleDescription`/`moduleStartsEnabled`. Their in-source purpose is to keep the module repository non-empty; the visible roster is drawn by `CharacterRosterPanel`, which is the only path that sends `SelectCharacterPayload`. Module toggles are UI state and are not server-authoritative.

The old asymmetry — two modules against three roster cards, which read like a bug — is gone, and coverage is now structural rather than counted: `ProjectSanityTest.assertClickGuiModulesCoverEveryVessel` checks that the tab really walks the client registry and names nobody, that every vessel's client definition declares its own module label and accent, and that exactly one vessel starts switched on.

## Shaders present but not live

`src/client/resources/assets/jujutsumod/shaders/core/` holds 26 files. Only two families are wired through the adapters: `msdf.*` (MSDF text) and `sdf_shape.*` (SDF panels). The rest — `rect.*`, `blur.*`, `glass_composite.*`, `kawase_down.*`, `kawase_up.*`, `outline.*`, `glow_outline.*`, `mask_diff.*`, `arc_*`, `texture.*` — are port artifacts kept for research. Status: INFERRED inventory; verify a call site before claiming any of them is a runtime path.

The original Rich `RectPipeline` from 1.21.11 did not compile against 1.21.8 Mojmap, which is why `Render2D` is an adapter over the project's own `SdfRenderer` rather than a port of the upstream GPU path.

## Open

- Glyph coverage for full Cyrillic UI strings: UNKNOWN.
- Performance budget with a full settings panel open: UNKNOWN.
