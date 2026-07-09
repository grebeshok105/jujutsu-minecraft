# Session Handoff — VFX Core Implementation

Дата: 2026-07-10

## Цель следующей сессии

Реализовать собственный Fabric-native VFX Core для Nobara в соответствии с подробным планом:

- `docs/superpowers/plans/2026-07-10-vfx-core-nobara.md`

Не дублируй план: этот handoff хранит только состояние и точки входа.

## Source of Truth

- Worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`
- Branch: `codex/nobara-cinematic-slice`
- Starting commit: `080927a docs(session): add legacy nobara handoff`
- Main checkout `D:\WorkFlow\Jujutsu Minecraft` грязный и пользовательский; его не трогать.
- До первого VFX-изменения worktree был чистым.

## Approved Decisions

- Нобара — первый реальный consumer VFX Core.
- Приоритет: maximum spectacle, но с distance culling и vanilla particle quality.
- Мультиплеер безопасен с первого дня: server-confirmed cue, client-only visuals.
- Java recipes; без JSON/DSL, preview/demo режима, новых shader dependencies или generic GeckoLib bone layer.
- Satin, Veil, Photon не брать: их ветки не поддерживают нужную Fabric 1.21.8 конфигурацию.
- Сохранять server particles и real nail renderer; не переносить persistent nail aura в transient timeline насильно.

## Current Code Shape

- Server emits `ProjectJjkNobaraImpulsePayload`; client `JujutsuClientNetworking.handleProjectJjkImpulse` branches on integer kinds.
- `HairpinWorldRenderer`, `HairpinCinematicCamera`, `HairpinScreenOverlay`, `ResonanceEffects`, and `FpSnapAnimator` are current static VFX paths to replace.
- `ProjectJjkNailRenderer` remains the state-driven entity renderer.
- Build uses JavaExec assertion tests. Existing verification tasks: `testProjectSanity`, `testTargetResolver`, `testProjectJjkNobaraProfile`.

## Required Checks

- Read the plan above, `AGENTS.md`, and Obsidian VFX notes before code.
- Use codebase-memory graph first for code discovery.
- TDD: create/run failing assertion test before each production slice.
- Commit each verified task with the exact conventional messages in the plan.
- Final required commands: `gradlew.bat check --no-daemon`, `gradlew.bat build --no-daemon -x test`, `gradlew.bat runClient --no-daemon`, then copy the runtime jar to `D:\Games\instances\Jujutsu\mods`.

## Suggested Skills

- `using-git-worktrees`
- `test-driven-development`
- `systematic-debugging` if a build/test fails
- `verification-before-completion`
- `requesting-code-review`
- `finishing-a-development-branch`
