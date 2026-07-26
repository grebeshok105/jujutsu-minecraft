# Neon GUI Polish Implementation Plan

> **For agentic workers:** Execute inline in one pass. User explicitly forbids review↔fix loops: do **one global review after all fixes**, then fix findings once. Do not dispatch multi-stage review subagents.

**Goal:** Merge qoder `worktree-neon-gui` into `main`, open a new polish branch, and fix 9 in-game Neon Dashboard UX defects from screenshots (layout, chrome, roster, fonts, crosshair, accents, size).

**Architecture:** Client-only polish on the existing SDF Neon UI kit (`src/client/.../ui/neon/*` + `gui/NeonDashboardScreen` + pages). No VFX contract changes. No server payloads. One layout pass + one overlay pass for dropdowns.

**Tech Stack:** Fabric 1.21.8, Java 21, Mojang mappings, existing SDF pipeline, Minecraft font providers (TTF), optional client mixin for crosshair.

**Baseline (from SESSION / git):**

| Item | Value |
|---|---|
| Qoder worktree | `D:\WorkFlow\Jujutsu Minecraft\.qoder\worktrees\neon-gui` |
| Branch to merge | `worktree-neon-gui` @ `e31a67e` |
| Main HEAD | `a79c639` |
| Relation | `worktree-neon-gui` is **25 commits ahead, 0 behind** `main` → clean **fast-forward** |
| Session truth | `.qoder/worktrees/neon-gui/SESSION.md` (CURRENT 2026-07-20 NEON GUI COMPLETE) |
| Obsidian | `jujutsumod-codebase-codex/04-client-vfx/GUI-neon-dashboard.md`, `GUI-character-select.md` |
| Instance jar | `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar` |

**Evidence (screenshots 2026-07-21):**

1. Ability strip (Piercing / Enlarge / Boom / Resonance) drawn **on top of** Megumi/Yuji cards; white `+` crosshair visible through dashboard.
2. Visuals page: dropdown popup + row labels stack; popup text bleeds into next `CtrlRow` (Glow intensity / Neon halo).

**Root causes already verified in code (do not re-discover):**

| # | Symptom | Root cause | Primary file(s) |
|---|---|---|---|
| 1 | Ability strip wrong place | `drawAbilityStrip*` uses `stripTop()` **without** `absY()`; SDF/text Y is page-local while cards use absolute | `CharacterPage.java` |
| 2 | No emoji glow | Emoji blits only; no soft halo under icons | `SidebarItem`, `CharacterPage`, `NeonCard` |
| 3 | Title noise | Header draws full `JUJUTSU // DASHBOARD` | `NeonDashboardScreen.renderShellText` |
| 4 | Text / popup overlap | (a) `CtrlRow` desc @ y+28 vs control body height 24–30; (b) open `NeonDropdown` popup rendered in tree order → later rows paint text on top; (c) dual labels (control label + CtrlRow desc) | `CtrlRow`, `NeonDropdown`, shell pages |
| 5 | Crosshair visible | Semi-transparent `g.fill(0x8A…)` insufficient; HUD crosshair still shows | `NeonDashboardScreen` + mixin if needed |
| 6 | Default MC font | All drawString use `Screen.font` / default | font resource + style helper |
| 7 | Unusable roster | `ROSTER` hardcodes 6 entries incl. SOON | `CharacterPage.ROSTER` |
| 8 | Confirm dull | Primary fill = theme accent but low perceived contrast on dark panel | `NeonButton` + theme accent boost for primary |
| 9 | GUI too large | Window `min(660, w-40) × min(440, h-40)` + large paddings | `NeonDashboardScreen.layoutWindow` + constants |

---

## Process constraints (user)

1. **Merge qoder → main first**, then continue on a **new own branch**.
2. Work from **actual commits** + neon-gui `SESSION.md`, not stale root `SESSION.md` (root is 2026-07-12 Nobara handoff).
3. **No review mill**: implement all fixes → `gradlew check` → **one** global review → apply review fixes once → rebuild/install jar.
4. Use **context7** for MC font provider details; **mcpvault** for codex notes; update Obsidian after change.
5. Isolated worktree preferred (`AGENTS.md`); commits conventional English; Russian user communication.
6. Do **not** push GitHub unless asked.

---

## Task 0 — Git: merge qoder into main + new branch

**Goal:** `main` contains Neon GUI (`e31a67e`), feature branch ready for polish.

### Step 0.1 — Pre-flight on main checkout

```powershell
cd "D:\WorkFlow\Jujutsu Minecraft"
git status -sb
git worktree list
git log --oneline -3 main
git log --oneline -3 worktree-neon-gui
git merge-base main worktree-neon-gui   # expect a79c639
```

**Dirty main note (current):** modified/untracked under `Jujutsu Kaizen/` (vault). Do **not** lose vault notes.

- Option A (recommended): leave vault dirty outside merge path; FF merge only code commits.
- If merge refuses due to dirty tracked files: `git stash push -m "vault-wip" -- "Jujutsu Kaizen"` then pop after merge.

### Step 0.2 — Fast-forward main

```powershell
git checkout main
git merge --ff-only worktree-neon-gui
git log --oneline -5   # tip should be e31a67e (or later if qoder advanced)
```

If FF fails (unexpected divergence): **stop** and report; do not force.

### Step 0.3 — New branch + worktree

```powershell
git branch feat/neon-gui-polish
git worktree add ".worktrees/neon-gui-polish" feat/neon-gui-polish
```

All code edits happen in:

`D:\WorkFlow\Jujutsu Minecraft\.worktrees\neon-gui-polish`

### Step 0.4 — Seed plan + SESSION into feature tree

Copy this plan if missing:

`docs/superpowers/plans/2026-07-21-neon-gui-polish.md`

Update feature `SESSION.md` header to:

> CURRENT 2026-07-21 — polish branch `feat/neon-gui-polish` after FF merge of `worktree-neon-gui` @ `<sha>`.

### Step 0.5 — Commit docs only if needed

```powershell
git add docs/superpowers/plans/2026-07-21-neon-gui-polish.md
git commit -m "docs(gui): add neon dashboard polish plan"
```

---

## Task 1 — Fix ability strip layout (screenshot 1)

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/gui/neon/pages/CharacterPage.java`

### Step 1.1 — Use absolute Y + dynamic row count

`stripTop()` today assumes **3** card rows always:

```java
return top + 3 * (cardH + 10) + 8;
```

After roster shrink (Task 7) this must follow **actual rows**. Also every draw path must add `absY()`.

**Target logic:**

```java
private float stripTop() {
    float top = contentTop();
    float cardH = cards.isEmpty() ? 62f : cards.get(0).height();
    int rows = Math.max(1, (int) Math.ceil(cards.size() / 2.0));
    return top + rows * (cardH + 10) + 8;
}

private void drawAbilityStripSurface(NeonContext ctx) {
    float ax = absX();
    float ay = absY();
    float labelY = ay + stripTop();
    float stripY = labelY + 13;
    // all SdfShape rect Y use labelY/stripY (absolute)
    ...
}

private void drawAbilityStripText(NeonContext ctx) {
    float ax = absX();
    float ay = absY();
    float labelY = ay + stripTop();
    ...
}
```

### Step 1.2 — Keep strip above Cancel/Confirm

Ensure `stripY + stripH + margin < btnY`. If not, reduce `stripH` or card gap rather than overlapping buttons.

With only 2 cards (1 row) after Task 7, space is ample even at reduced window size.

### Step 1.3 — Commit

```text
fix(gui): place ability strip with absY and dynamic roster rows
```

---

## Task 2 — Soft glow under emoji icons

**Files:**
- Modify: `SidebarItem.java`, `CharacterPage.drawAbilityStripSurface`, `NeonCard` emoji path

### Approach

Before each emoji `blit`, add a small SDF disc/rounded rect glow using theme accent (sidebar/ability) or card accent (portrait):

```java
// example under 16×16 sidebar icon
ctx.sdf().add(SdfShape.builder()
    .rect(ax + 7, ay + 7, 20, 20)
    .radius(10)
    .border(0, 0)
    .glow(10, applyAlpha(t.glow(), 0.35f + 0.25f * selectAnim))
    .fill(applyAlpha(t.accentArgb(), 0.12f), applyAlpha(t.accentArgb(), 0.04f))
    .highlight(0f)
    .build());
```

Keep glow subtle (user: «небольшой»). Ability strip icons: glow radius ~8–10, alpha ~0.25–0.4. Portrait emoji (None): same under well.

Do **not** replace emoji PNGs; glow is SDF underlayer only.

### Commit

```text
feat(gui): add soft SDF glow under dashboard emoji icons
```

---

## Task 3 — Header: drop “JUJUTSU // DASHBOARD”, keep version

**Files:**
- Modify: `NeonDashboardScreen.java` (`renderHeaderChrome`, `renderShellText`)

### Changes

1. Remove title `Component` drawing of `JUJUTSU // DASHBOARD`.
2. Keep sigil (rings) optional — **keep** for brand, or leave if it looks empty; prefer keep sigil + version only.
3. Keep version badge background + `v1.0.0` text.
4. Reposition version badge next to sigil (left cluster), not at hardcoded `wx+178` which assumed long title width.

```java
// version badge after sigil
float badgeX = wx + 42; // was 178
ctx.sdf()...rect(badgeX, wy + 12, 44, 16)...
g.drawString(..., "v1.0.0", (int)(badgeX + 6), (int)(wy + 16), ...);
```

### Commit

```text
fix(gui): remove dashboard title, keep version badge
```

---

## Task 4 — Fix overlapping text + dropdown popups (screenshot 2)

**Files:**
- Modify: `CtrlRow.java`, `NeonDropdown.java`, optionally `NeonSlider`/`NeonToggle`/`KeybindField` heights
- Modify: `VisualsPage` / `CombatPage` / `MiscPage` if row spacing constants change
- Modify: `UiRoot` or `NeonDashboardScreen` for **overlay pass** if needed

### 4A — CtrlRow layout contract

Current bug:

- Control at `(12, 5, w-24, control.height())` with heights 24–30.
- Description at `absY + 28` → collides with control body (especially slider rail at `ay+18`).

**New layout (single column row):**

```
[ control label ........ value/widget ]  y+6..y+22
[ description muted                   ]  y+26..y+36
```

Rules:

1. Controls keep **one** primary label (their own).
2. CtrlRow description stays below, `y = 28` only if control visual height ≤ 24; otherwise set `descY = control.height() + 8`.
3. CtrlRow height = `descY + 14` (dynamic), not fixed 46 — pages must use returned height when stacking `y += row.height() + gap`.

Prefer updating shell pages:

```java
CtrlRow particle = new CtrlRow(...);
particle.setBounds(0, y, pageW, particle.preferredHeight());
add(particle);
y += particle.height() + 8;
```

Add `preferredHeight()` on `CtrlRow`.

### 4B — Dropdown dual-label cleanup

`NeonDropdown` currently draws label on the left of the full-width field. When nested in `CtrlRow`, the field is already full row width — OK.

Avoid drawing description that repeats the same words. Keep distinct: label = control name, desc = help text (as now).

When open, **do not** draw the field value line in a way that doubles with popup header.

### 4C — Popup z-order (critical)

Popup is painted in `renderText` mid-tree → next `CtrlRow` text draws on top (matches screenshot).

**Fix:** deferred overlay list on `UiRoot` or `NeonContext`:

```java
// NeonContext or UiRoot
void deferOverlay(Runnable r);
void flushOverlays();
```

`NeonDropdown.renderText`: if open, `ctx.deferOverlay(() -> drawPopup(...))` instead of drawing immediately.

`NeonDashboardScreen.render` after `root.renderText(ctx)`:

```java
root.renderText(ctx);
ctx.flushOverlays(); // or root.flushOverlays()
renderShellText(ctx);
```

Popup surface:

- Fully opaque panel `0xF21A1410` + 1px accent border (SDF or fill).
- Clip/options only inside panel.
- Hit-testing already uses `isInPopup`; ensure reverse z-order input still prefers open dropdown (already first-match if parent walks reverse — verify `UiContainer`).

### 4D — Close other dropdowns when opening one (nice-to-have, same commit if cheap)

### Commit

```text
fix(gui): resolve ctrl-row text stacking and dropdown overlay order
```

---

## Task 5 — Hide crosshair while menu open

**Files:**
- Prefer: client mixin on crosshair render when `Minecraft.screen instanceof NeonDashboardScreen`
- Keep existing scrim fill as backup

### Research note (context7 / yarn)

In 1.21.x crosshair is rendered from GUI/HUD path; semi-transparent scrim is not enough (screenshot shows `+`).

**Preferred implementation:**

```java
@Mixin(/* Gui or Gui$... crosshair method for 1.21.8 */)
public class NeonDashboardCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void jujutsumod$hideCrosshair(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof NeonDashboardScreen) {
            ci.cancel();
        }
    }
}
```

Exact method/class: confirm against named mappings in this project (check existing mixins + `yarn`/`mojmap` in `build.gradle`). Use MixinExtras only if wrap needed. Register in `jujutsumod.client.mixins.json`.

If method renamed in 1.21.8, use `rg`/codegraph on yarn names before writing.

Also set scrim fill alpha higher under full screen if mixin proves fragile: `0xE0060403` as belt-and-suspenders (not sole fix).

### Commit

```text
fix(gui): hide crosshair while neon dashboard is open
```

---

## Task 6 — Custom font (non-default Minecraft)

**Files:**
- Create: `src/main/resources/assets/jujutsumod/font/neon.json`
- Create: `src/main/resources/assets/jujutsumod/font/neon.ttf` (or `*.otf`)
- Create: `src/client/java/jujutsu/mod/client/ui/neon/NeonFonts.java` (ResourceLocation + style helper)
- Modify: draw paths to use styled components **or** Font instance

### Font choice

Use a small **OFL** UI font bundled in repo (pick one, ship file):

| Candidate | Why |
|---|---|
| **Inter** (recommended) | Clean UI, excellent Latin, small subset possible |
| Outfit / Manrope | More “display”, still readable |

Do **not** ship Apple Color Emoji TTF (256MB, already gitignored).

### font.json (Minecraft provider)

Per Minecraft Wiki font providers (`type: ttf`):

```json
{
  "providers": [
    {
      "type": "ttf",
      "file": "jujutsumod:neon.ttf",
      "shift": [0, 0],
      "size": 11.0,
      "oversample": 2.0,
      "skip": ""
    },
    {
      "type": "reference",
      "id": "minecraft:include/space"
    },
    {
      "type": "reference",
      "id": "minecraft:include/default"
    }
  ]
}
```

Tune `size`/`shift` in-game (TTF baselines differ). Fallback reference providers keep missing glyphs from default.

### Usage in code

```java
public final class NeonFonts {
    public static final ResourceLocation ID = JujutsuMod.id("neon");
    public static Style style() { return Style.EMPTY.withFont(ID); }
    public static Component t(String s) { return Component.literal(s).withStyle(style()); }
    public static Component wrap(Component c) { return c.copy().withStyle(style()); }
}
```

Apply `NeonFonts.wrap(...)` (or style) for all dashboard strings: header, sidebar, pages, buttons, cards, ctrl rows.

**Verify with context7/wiki during implementation** if `file` path form is `namespace:name.ttf` under `assets/namespace/font/`.

### Commit

```text
feat(gui): add Inter-based neon dashboard font
```

---

## Task 7 — Roster: only Nobara + None

**Files:**
- Modify: `CharacterPage.java` (`ROSTER` array)
- Update: Obsidian `GUI-character-select.md` (6-card → 2-card)
- Optional: remove unused SOON emoji textures later (not required this pass)

```java
private static final Roster[] ROSTER = {
    new Roster("Nobara Kugisaki", "Straw Doll Technique", "Grade 3",
            0xFFE48A36, 0xFF8B3F1C, true, true, null, JujutsuCharacter.NOBARA),
    new Roster("None", "No Technique", "Default",
            0xFF505760, 0xFF2E333A, true, false, dash("bust"), JujutsuCharacter.NONE),
};
```

Grid remains 2-column; one row. Ability strip under that row (Task 1). Cancel/Confirm stay bottom.

### Commit

```text
fix(gui): show only Nobara and None in character roster
```

---

## Task 8 — Brighter Confirm (primary) accents

**Files:**
- Modify: `NeonButton.java` primary branch
- Optionally `NeonTheme` helpers for primary button fill

### Changes

Primary button currently:

```java
fillTop = t.accentArgb();
fillBottom = t.deepArgb();
glowR = 12f; glowAlpha = 0.55f * ...
```

On dark panel this reads as “brown pill”, same family as chrome. Boost:

1. **Lighter fill top** (lerp accent toward warm white ~30%): e.g. `0xFFFFB45A` for Nobara primary top while deep stays accent.
2. **Stronger glow** radius 14–16, alpha 0.7–0.85.
3. **Border** 1px lighter accent edge even for primary (not 0).
4. **Hover**: increase highlight + scale glow; optional slight brightness up.
5. Secondary Cancel stays quiet (current raised style OK).

Helper:

```java
private static int brighten(int argb, float amount) { /* lerp RGB toward 255 */ }
```

### Commit

```text
fix(gui): brighten primary confirm button accent
```

---

## Task 9 — Shrink GUI ~50%

**Files:**
- Modify: `NeonDashboardScreen` constants + `layoutWindow`
- Cascade: sidebar width, header height, card portrait, button heights, page paddings

### Interpretation

User: «примерно на 50% поменьше».  

**Plan:** linear scale factor **`UI_SCALE = 0.72`** as first target (≈ half the area: 0.72² ≈ 0.52), not 0.5 linear (too cramped for 8px MC glyph metrics + TTF). If still large in-game, second tweak to **0.65**.

Base today: `660 × 440`.

| Scale | Window |
|---|---|
| 1.00 | 660 × 440 |
| **0.72** | **~475 × 317** |
| 0.65 | ~429 × 286 |
| 0.50 | 330 × 220 (last resort) |

### Concrete constants (at 0.72)

```java
private static final float UI_SCALE = 0.72f;
private static final float SIDEBAR_W = 132 * UI_SCALE;   // ~95
private static final float HEADER_H = 40 * UI_SCALE;     // ~29
// layoutWindow:
float ww = Math.min(660 * UI_SCALE, width - 24);
float wh = Math.min(440 * UI_SCALE, height - 24);
```

Also scale:

- Sidebar item height/spacing (`38` → `~27`)
- Card `PORTRAIT` 46 → ~34, padding
- Ability strip cell height 40 → ~30
- Button height 36 → ~28
- Font stays readable via TTF size; avoid drawing text smaller than 7px effective

With only 2 roster cards, reduced height still fits strip + buttons.

### Commit

```text
fix(gui): scale neon dashboard window and chrome to ~72%
```

---

## Task 10 — Docs + SESSION + Obsidian

**Files:**
- `SESSION.md` (feature worktree + optionally root pointer)
- Obsidian via mcpvault:
  - `jujutsumod-codebase-codex/04-client-vfx/GUI-neon-dashboard.md`
  - `jujutsumod-codebase-codex/04-client-vfx/GUI-character-select.md`
  - touch `00-MOC.md` recent updates if needed

Document:

- Branch `feat/neon-gui-polish`
- Roster = Nobara + None only
- Font id `jujutsumod:neon`
- Crosshair hidden via mixin
- Window scale factor
- Known remaining shell-page limits (Combat/Visuals still non-persistent)

### Commit

```text
docs(gui): record neon dashboard polish in session and codex
```

---

## Task 11 — Build, install, one global review, one fix pass

### 11.1 — Build

```powershell
cd "D:\WorkFlow\Jujutsu Minecraft\.worktrees\neon-gui-polish"
.\gradlew.bat check --no-daemon
.\gradlew.bat build --no-daemon -x test
```

### 11.2 — Install jar

```powershell
Copy-Item -Force "build\libs\jujutsumod-1.0.0.jar" "D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar"
Get-FileHash "...\mods\jujutsumod-1.0.0.jar" -Algorithm SHA256
```

### 11.3 — In-game checklist (manual)

1. V opens dashboard; title text gone; version visible.
2. Only Nobara + None cards; ability strip under cards, no overlap.
3. Emoji icons have soft glow.
4. Confirm clearly brighter/clickable vs Cancel.
5. Window ~half area; still usable at GUI scale 2/3.
6. Visuals: open Particle quality dropdown — options readable, no stack with Glow intensity.
7. Crosshair fully hidden while open; returns after close.
8. Custom font visible (not pixel default).
9. Select Nobara → Confirm → payload still works; theme accent orange.

### 11.4 — ONE global review

Single read-only pass across the polish diff (`main...HEAD` or merge-base). Categories:

- Layout abs coords consistency
- Mixin safety / mixin json
- Font fallback if TTF missing
- No server/VFX regressions
- No leftover SOON roster references in UI tests

Apply **all** review fixes in **one** commit:

```text
fix(gui): address neon polish global review findings
```

**Do not** open a second review cycle unless build is red.

### 11.5 — Final verification

Re-run `gradlew.bat check --no-daemon` after review fixes; reinstall jar; update SESSION with SHA-256.

---

## Suggested commit sequence (max ~10, atomic)

1. `docs(gui): add neon dashboard polish plan` (if not already)
2. `fix(gui): place ability strip with absY and dynamic roster rows`
3. `fix(gui): show only Nobara and None in character roster`
4. `fix(gui): remove dashboard title, keep version badge`
5. `fix(gui): resolve ctrl-row text stacking and dropdown overlay order`
6. `fix(gui): hide crosshair while neon dashboard is open`
7. `feat(gui): add Inter-based neon dashboard font`
8. `feat(gui): add soft SDF glow under dashboard emoji icons`
9. `fix(gui): brighten primary confirm button accent`
10. `fix(gui): scale neon dashboard window and chrome to ~72%`
11. `docs(gui): record neon dashboard polish in session and codex`
12. `fix(gui): address neon polish global review findings` (only if needed)

Tasks 2–9 can be batched into fewer commits if preferred, but keep **layout/roster**, **dropdown**, **font**, **crosshair**, **scale** separable for bisect.

---

## File map (expected touch set)

| Path | Role |
|---|---|
| `client/gui/NeonDashboardScreen.java` | size, header, crosshair scrim, overlay flush, font |
| `client/gui/neon/pages/CharacterPage.java` | roster, ability strip absY |
| `client/gui/neon/pages/{Combat,Visuals,Misc}Page.java` | CtrlRow spacing |
| `client/ui/neon/widget/CtrlRow.java` | dynamic height / desc Y |
| `client/ui/neon/widget/NeonDropdown.java` | overlay popup |
| `client/ui/neon/widget/NeonButton.java` | primary accent |
| `client/ui/neon/widget/SidebarItem.java` | emoji glow |
| `client/ui/neon/widget/NeonCard.java` | emoji glow |
| `client/ui/neon/NeonContext.java` or `UiRoot.java` | deferred overlays |
| `client/ui/neon/NeonFonts.java` | **new** |
| `client/mixin/*Crosshair*.java` | **new** |
| `client/**/jujutsumod.client.mixins.json` | register mixin |
| `assets/jujutsumod/font/neon.json` + `neon.ttf` | **new** |
| `SESSION.md`, Obsidian GUI notes | docs |

**Out of scope:** VFX director, Nobara combat, networking beyond existing `SelectCharacterPayload`, SOON character implementation, HTML mockup redesign, dependency additions (no new mods).

---

## Risks / traps

1. **Main dirty vault** — don't commit unrelated `Jujutsu Kaizen` noise into polish commits unless intentionally updating codex notes.
2. **Qoder worktree still checked out on `worktree-neon-gui`** — after FF, that worktree stays valid; polish worktree is separate. Don't edit qoder tree for this task.
3. **SDF layer order** — emoji glow must be queued in `renderSurface` **before** icon blit in `renderText` (already the case if glow in surface).
4. **Dropdown overlay** must still receive clicks — input path uses geometry, not draw order; verify `mouseClicked` when popup extends outside parent bounds (parent `contains` may block). If blocked, handle open-popup hit test in `PageContainer`/`UiRoot` before children clip.
5. **TTF licensing** — only OFL/Apache fonts; record license path under `docs/` or `assets/.../font/LICENSE`.
6. **Scale 0.72 + font** — re-check badge/sigil positions after scale; avoid hardcoded `wx+178`.
7. **Tests** — `ProjectSanityTest` may mention character select UI strings; run `check`, update assertions if roster text assumptions break.

---

## Success criteria

- [ ] `main` fast-forwarded to include neon GUI; active work on `feat/neon-gui-polish`
- [ ] All 9 user items visually fixed in-game
- [ ] `gradlew check` green
- [ ] Jar installed to instance with recorded SHA-256
- [ ] Obsidian GUI notes updated
- [ ] Exactly **one** post-implementation review pass (no review thrash)

---

## Execution mode (per user)

**Inline execution** after plan approval — not subagent-per-task with multi-review.  
When starting implementation: Task 0 (merge) first, then Tasks 1–11 in order, single review at 11.4.
