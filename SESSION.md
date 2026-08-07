# Session Handoff — Ability HUD

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/ability-hud`
- Branch: `feat/ability-hud` (base origin/main a394643)
- Scope: minimalist draggable in-world ability cooldown HUD for all vessels, ClickGui SDF style

## What shipped

- `HudSlot` record + `CharacterClientDefinition.hudSlots()` seam (default empty for NONE)
- `CharacterClientDefinition.maxCooldownTicks(CharacterAbility)` seam (per-vessel denominators, no vessel imports in shared code)
- `AbilityHud` — SDF/MSDF render, single flush per frame, bottom-center anchor
- Drag via ClickGui's `DragHandler` fed by `END_CLIENT_TICK` GLFW polling
- Registered via `VfxDirector.registerHudContribution`
- In-game verified: Todo 6 slots with cooldown overlay, Nobara 5 slots, NONE hidden

## Review fixes applied

- clampToScreen unconditionally (off-screen recovery)
- ⇧ glyph → S+ prefix (MSDF atlas lacks U+21E7)
- Cooldown overlay border(0,0) (Nobara accent leak)
- Strip width excludes trailing GAP
- Press-edge latch (no cursor-enter grab)
- client.screen==null guard (no drag while UI open)
- Duplicate imports removed

## Verification

- qualityGate green (client_java 187)
- compileClientJava green
- 270 JUnit + gametests green
- In-game screenshots: `.omp/rule-of-four/ability-hud/hud_*.png`
