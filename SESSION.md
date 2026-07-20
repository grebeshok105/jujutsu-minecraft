# Session Handoff — Jujutsu Minecraft

> **CURRENT 2026-07-20 — NEON GUI COMPLETE (Stages 2-7 done).**
> Worktree `.qoder/worktrees/neon-gui`, ветка `worktree-neon-gui`.
> Все стейджи закоммичены. `gradlew check` зелёный. Jar скопирован в инстанс.
> **In-game рендер-проверка НЕ выполнена** (пользователь пропустил). Первое действие — запустить клиент и нажать V.

---

## Где мы сейчас

- Проект: `D:\WorkFlow\Jujutsu Minecraft`
- Активный worktree: `D:\WorkFlow\Jujutsu Minecraft\.qoder\worktrees\neon-gui`
- Ветка: `worktree-neon-gui` (main = `72f36a3`)
- Последний коммит: `3faef61 refactor(gui): delete legacy UI, update sanity tests (Stage 7)`
- Инстанс: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar` (обновлён)

## Коммиты (Stages 3-7)

| Commit | Stage | Содержание |
|---|---|---|
| `77e2128` | 3 | Kit core + draggable dashboard window |
| `bf13e8e` | 4 | Sidebar, pages, scroll, shell controls |
| `70ea937` | 5 | Functional CharacterPage (SelectCharacterPayload) |
| `3b14ca9` | 6 | KeybindField, ColorPicker, sounds, lang |
| `3faef61` | 7 | Cleanup: delete legacy, update tests |

## Статус стейджей

| Stage | Статус |
|---|---|
| 0-2 | DONE (ранее) |
| 3 | DONE ✅ |
| 4 | DONE ✅ |
| 5 | DONE ✅ |
| 6 | DONE ✅ |
| 7 | DONE ✅ |

## ПЕРВОЕ ДЕЙСТВИЕ: in-game проверка

1. Запустить инстанс или `gradlew.bat runClient --no-daemon`
2. Зайти в мир, нажать **V**
3. Проверить: окно открывается с анимацией, sidebar переключает страницы, drag за header, X/Esc/V закрывают, Character page шлёт payload, glow/hover работают
4. GUI scale 1/2/4, resize
5. Если шейдер не работает → проверить latest.log на GLSL ошибки

## Архитектура (что построено)

```
src/client/java/jujutsu/mod/client/
  ui/neon/
    UiComponent.java       — базовый виджет (float bounds, hover/press, abs coords)
    UiContainer.java       — дети, reverse z-order input
    UiRoot.java            — окно, drag, theme, scrim+window SDF
    NeonTheme.java         — per-character accent, lerp()
    NeonContext.java       — render context record
    layout/
      VerticalLayout.java
      HorizontalLayout.java
      ScrollContainer.java — scissored text clipping
    widget/
      NeonLabel.java
      NeonButton.java      — primary/secondary, glow, sound
      NeonToggle.java      — shell, local state, sound
      NeonSlider.java      — shell, drag-capture
      NeonDropdown.java    — shell, popup list
      NeonCard.java        — character card, glow selection
      SidebarItem.java     — glyph + label, animated glow
      KeybindField.java    — shell, listen-mode
      NeonColorPicker.java — shell, preset cycling
    render/
      SdfPipelines.java    — RenderPipeline + VertexFormat
      SdfShape.java        — shape record + builder
      SdfRenderer.java     — batch → one draw call
      NeonBlur.java        — independent disable
  gui/
    NeonDashboardScreen.java — Screen host (V toggle, open/close anim, drag)
    neon/
      NeonPage.java        — abstract page
      PageContainer.java   — page switch animation
      pages/
        CharacterPage.java — REAL: SelectCharacterPayload, ability strip, portrait
        CombatPage.java    — shell
        VisualsPage.java   — shell
        MiscPage.java      — shell (KeybindField, ColorPicker)
  input/JujutsuKeybinds.java — V = toggle dashboard
```

## Обязательные правила (напоминание)

- Клиентский код — только `src/client`. VFX контракт не трогать.
- Коммиты conventional, английские.
- Obsidian codex обновлять.
- Собирать jar и копировать в инстанс.

---

## SUPERSEDED (предыдущие handoff)

> **SUPERSEDED 2026-07-20:** Neon GUI Stages 2-7 завершены в worktree-neon-gui.

> **SUPERSEDED 2026-07-12:** Resonant Momentum / hammer / embedded nail — в main.

> **SUPERSEDED 2026-07-11:** Nobara cinematic slice — в main.
