# Session Handoff — Jujutsu Minecraft

> **CURRENT 2026-07-21 — RICH CLICKGUI PORT ON N + NEON DASHBOARD ON V.**  
> Active worktree: `.worktrees/neon-gui-polish`  
> Active branch: **`feat/neon-gui-polish`** @ **`95d81a1`**  
> `main` still at **`e31a67e`** (neon GUI base only — **does not** include Rich port yet).  
> Jar: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`  
> SHA-256: `5AE099A20B941A666F44BD777A693FE4F8638F35E164A9F00B95C74A7FB68AF7`  
> Text fix confirmed in-game (MSDF labels visible after `95d81a1`).

---

## Worktrees / branches map

| Path | Branch | HEAD | Role |
|------|--------|------|------|
| `D:\WorkFlow\Jujutsu Minecraft` | **`main`** | `e31a67e` | Stable base: neon dashboard GUI merged from qoder |
| `.worktrees/neon-gui-polish` | **`feat/neon-gui-polish`** | **`95d81a1`** | **ACTIVE** — polish + MSDF + Rich ClickGui port (N) |
| `.qoder/worktrees/neon-gui` | `worktree-neon-gui` | `e31a67e` (ahead 25 vs old tip naming) | Original neon GUI stages (Phases 0–4); merged into main |
| `.worktrees/nobara-cinematic-slice` | `codex/nobara-cinematic-slice` | `5073b24` | Nobara cinematic / hammer / momentum (ahead 16) |
| `.worktrees/vfx-director-prototype` | `codex/vfx-director-prototype` | `c9ed0df` | VFX director sandbox docs |
| `.worktrees/brainstorming` | `chore/jujutsu-brainstorming` | `d349422` | Older brainstorming / VFX alignment |

**Merge base for polish branch:** `main` @ `e31a67e`  
**Commits only on `feat/neon-gui-polish` (not in main):** `e31a67e..95d81a1` (25 commits).

---

## Keybinds (current product)

| Key | Screen | Branch / status |
|-----|--------|-----------------|
| **V** | `NeonDashboardScreen` — neon SDF dashboard (Character/Combat/Visuals/Misc) | On `main` + polish branch |
| **N** | **`ClickGui`** — ported Rich-Modern clickgui (`jujutsu.mod.client.rich…`) | **Only on `feat/neon-gui-polish`** |
| R / B / LMB | Nobara kit actions | Shared gameplay |

Fallback if ClickGui fails to init: old `ModernMenuScreen` (legacy experimental shell).

---

## What’s on `feat/neon-gui-polish` (ordered commits)

### A. Neon dashboard polish (earlier on same branch)

| Commit | Summary |
|--------|---------|
| `25d4a0d` | feat(gui): polish neon dashboard layout, selection, chrome |
| `fd9c860` | docs(session): handoff for neon gui polish |
| `35e6fc5` | fix(gui): restore default font, fit layout, emoji glow, center nobara head |
| `091bffc` | docs(session): note gui hotfixes and new jar hash |
| `2355813` … `ed12211` | font experiments (Open Sans / Segoe / bitmap / reset) — mostly superseded for **N** path |

### B. Separate modern menu (N) + MSDF foundation

| Commit | Summary |
|--------|---------|
| **`ef6cf13`** | **feat(gui): add separate modern vessel menu on N with MSDF fonts** |
| `dd2a2ad` | fix(gui): harden modern menu keybind N open path |
| `1b2d723` | feat(gui): restyle modern menu as Rich-like clickgui shell |
| `d7da0d3` | fix(gui): review fixes — hitboxes, scrim, MSDF batch, lifecycle |
| `7befbeb` | chore: untrack Rich-Modern research extract from VCS |
| `c5c8d31` | feat(gui): lock Rich clickgui visual contract (400×250, palette) |
| `8ef6fd3` | feat(gui): port layout/metrics/fonts from full Rich sources |

### C. Full Rich ClickGui structure port (current N)

| Commit | Summary |
|--------|---------|
| **`6b76943`** | **feat(gui): port full Rich clickgui structure with SDF-backed Render2D** |
| **`f3bbf69`** | **fix(gui): open ported Rich ClickGui on N** |
| **`95d81a1`** | **fix(gui): stop SDF batch burying MSDF text; retry font atlas load** ← **HEAD** |

---

## Architecture (N path — HEAD)

```
N key
  → JujutsuKeybinds.toggleModern()
  → Initialization.getInstance().getManager().getClickgui()
  → jujutsu.mod.client.rich.screens.clickgui.ClickGui

ClickGui (ported structure from Rich-Modern)
  ├── BackgroundComponent / BackgroundRenderer / CategoryRenderer / HeaderRenderer / AvatarRenderer
  ├── ModuleComponent / ModuleListRenderer / SettingsPanelRenderer
  ├── settingsrender/* (Checkbox, Slider, Bind, Select, Color, Text, …)
  ├── modules: JujutsuModules (Nobara / None / kit rows under COMBAT etc.)
  └── Render2D  →  SdfRenderer (panels)
      Fonts.*   →  MsdfFonts (bold / ui / guiicons / categoryicons)
```

**Important honesty note (for next agent):**  
UI **call graph and layout** = Rich sources.  
GPU backends **RectPipeline/blur/glass from 1.21.11 did not compile on 1.21.8 Mojmap** → `Render2D` is an adapter over project SDF + MSDF.  
Do not claim original Rich GL pipelines are running as-is.

Assets: `src/client/resources/assets/jujutsumod/fonts/*` (msdf atlases), shaders under `shaders/core/` (msdf + many Rich shader files present; adapters may not use all).

Research (local only, gitignored):  
`docs/research/rich-modern-full/` (full rar extract + codegraph index)  
`docs/research/rich-modern-gui-ref/` (older partial extract)

Spec: `docs/research/2026-07-21-rich-clickgui-visual-spec.md`

---

## What’s on `main` (`e31a67e`)

- Neon dashboard (V) from qoder rework + review fixes  
- Tip commit: `e31a67e docs: record review-fix commits in SESSION.md`  
- **No** Rich ClickGui, **no** N-menu MSDF port  
- Related history on main line (examples):  
  - `aa87078` SDF projection / chrome  
  - `77d94c7` V-while-listening, dropdown, toggle/slider styling  
  - `f0d55ff` Apple emoji icons  
  - `e6b52bf` / `a21db5a` / `5d085af` / `e1922c0` neon phases 0–3  

Other branches (not merged into polish work):  
- `codex/nobara-cinematic-slice` @ `5073b24`  
- `codex/vfx-director-prototype` @ `c9ed0df`  
- `chore/jujutsu-brainstorming` @ `d349422`  

---

## In-game QA (current)

1. **V** → neon dashboard (old polished UI).  
2. **N** → Rich ClickGui shell: sidebar (Основные / Combat…), module list, settings, toggles.  
3. **Text visible** (after `95d81a1`).  
4. Combat category shows Nobara / None + ability modules; settings checkboxes work as UI state.  
5. Esc / N closes ClickGui.  
6. Confirm character select still primarily via **V** neon Character page unless wired later into ClickGui modules.

---

## Next steps (suggested)

1. Merge `feat/neon-gui-polish` → `main` when ready (large client-only GUI commit set).  
2. Wire Nobara Confirm / vessel apply into ClickGui module actions (not only V dashboard).  
3. Optional: port real Rich `RectPipeline` if targeting a loader that matches 1.21.11 APIs.  
4. Update Obsidian codex notes for dual GUI (V neon / N rich).  
5. Clean unused Rich shaders if adapters stay.

---

## Rules (unchanged)

- Client UI only under `src/client`.  
- VFX core contract untouched.  
- Conventional English commits.  
- Build jar → copy to `D:\Games\instances\Jujutsu\mods\`.  
- Obsidian / codebase codex for meaningful system changes.

---

## SUPERSEDED

> **SUPERSEDED 2026-07-21 morning:** “Neon GUI polish done” as sole story — still true for **V**, but **N** is now the Rich port track.  
> **SUPERSEDED 2026-07-20:** Neon stages on `worktree-neon-gui` only.  
> **SUPERSEDED 2026-07-12:** Resonant Momentum / hammer notes — in main gameplay history.
