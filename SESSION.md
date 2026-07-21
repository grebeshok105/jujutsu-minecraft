# Session Handoff — Jujutsu Minecraft

> **CURRENT 2026-07-21 — NEON GUI POLISH DONE.**
> Worktree `.worktrees/neon-gui-polish`, branch `feat/neon-gui-polish`.
> `main` fast-forwarded to include qoder Neon GUI (`e31a67e`), polish continues here.
> `gradlew check` green. Jar installed to instance.
> Installed JAR SHA-256: `C2EEACFAA0319D1AEF52A731CE9C902BD6BD6309BDECE6FC74A5B26ADB04CBB5`.

---

## Где мы сейчас

- Проект: `D:\WorkFlow\Jujutsu Minecraft`
- Активный worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\neon-gui-polish`
- Ветка: `feat/neon-gui-polish` (base = `main` @ neon GUI + polish)
- План: `docs/superpowers/plans/2026-07-21-neon-gui-polish.md`
- Инстанс: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`

## Что сделано (polish)

1. Ability strip: `absY() + stripTop()`, dynamic row count
2. Emoji soft SDF glow (sidebar / ability / portrait)
3. Header: убран `JUJUTSU // DASHBOARD`, версия `v1.0.0` осталась
4. CtrlRow dynamic height + dropdown overlay pass (z-order)
5. Crosshair hidden via `NeonDashboardCrosshairMixin` + darker scrim
6. Font: Inter TTF as `jujutsumod:neon`
7. Roster: only Nobara + None
8. Confirm primary brighter (brighten + glow + border)
9. Window scale `UI_SCALE = 0.72` (~half area)
10. **Selection restore:** open menu selects current character (client map keeps NONE; default NONE; optimistic `applyLocal` on Confirm)

## Git

- Merge: `main` FF from `worktree-neon-gui` @ `e31a67e`
- Polish branch: `feat/neon-gui-polish`

## In-game QA checklist

1. V → menu smaller, no title, version visible, no crosshair
2. Only Nobara + None cards; ability strip under them
3. If active kit is None → None selected on open; Nobara → Nobara
4. Confirm brighter than Cancel
5. Visuals dropdown popup not stacking into next row
6. Custom font readable
7. Confirm still sends SelectCharacterPayload

## Обязательные правила

- Клиентский код — только `src/client`. VFX контракт не трогать.
- Коммиты conventional, английские.
- Obsidian codex обновлять.
- Собирать jar и копировать в инстанс.

---

## SUPERSEDED

> **SUPERSEDED 2026-07-20:** Neon GUI Stages 2-7 + rework complete on `worktree-neon-gui`.

> **SUPERSEDED 2026-07-12:** Resonant Momentum / hammer / embedded nail — in main.
