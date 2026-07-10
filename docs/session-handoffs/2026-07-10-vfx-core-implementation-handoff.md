# Session Handoff — VFX Core Implementation

Дата: 2026-07-10

## Состояние на handoff

VFX Core и миграция Nobara реализованы, документация обновлена, проверки прошли, а runtime jar установлен. Manual gameplay/two-client QA не выполнялся из-за прямого запрета пользователя на Computer Use/UI automation и остаётся явно непроверенным:

- `docs/superpowers/plans/2026-07-10-vfx-core-nobara.md`

Не дублируй план: этот handoff хранит только состояние и точки входа.

## Source of Truth

- Worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`
- Branch: `codex/nobara-cinematic-slice`
- Starting commit: `080927a docs(session): add legacy nobara handoff`
- Completed commits:
  - `c375d12 docs(vfx): add core implementation plan`
  - `01f94dd feat(vfx): add synchronized effect cues`
  - `6ef5585 feat(vfx): add client effect director`
  - `3626618 refactor(nobara): route combat effects through vfx core`
  - `fefbd7d merge(docs): integrate codebase codex base`
  - `b6ac0e7 docs(vfx): document nobara core migration`
  - `4aa7274 fix(vfx): honor cue timeline and world lifecycle`
  - `c79c4cb test(vfx): tighten timeline regression guards`
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

- Shared main source: `VfxCue`, `NobaraVfxIds`, `VfxCuePayload`, `VfxTimeline`, and `VfxAnchorResolver`.
- Server emits only typed cues through `JujutsuNetworking.broadcastVfxCue/sendVfxCue`; old integer impulse payload is deleted.
- Client `VfxDirector` owns recipes, lifecycle/expiry, vanilla particle-quality scaling, unknown-ID safety, world/HUD callbacks, and `ClientLevel` identity cleanup. It clears before rebinding and clears plus resets the tracked level on null/disconnect (`VfxDirector.java:25-148`).
- A non-expired late cue receives its actual `initialAgeTicks`; one-shot sound/particle openings run only below two ticks, while HUD, camera/FOV, and first-person realtime timestamps are offset into the remaining phase (`VfxTimeline.java:10-27`; `NobaraVfxRecipes.java:37-189`).
- World impacts retain the cue and resolve a live entity anchor every render, falling back to `cue.origin()` after despawn (`VfxWorldChannel.java:34-69`).
- `NobaraVfxRecipes` registers all ten Nobara IDs. Camera, HUD, particles, world rings/ribbons/blades, local sound, and first-person movement are director channels. The first-person recipe is at `NobaraVfxRecipes.java:188-189`; its 0.75-second snap traverses the complete `0..15` scale (`VfxFirstPersonChannel.java:14-59`).
- Existing narrow mixins only read director state. `ProjectJjkNailRenderer` remains state-driven for persistent aura and shares `VfxPalette`.
- Removed static paths: `HairpinWorldRenderer`, `HairpinCinematicCamera`, `HairpinScreenOverlay`, `ResonanceEffects`, and `FpSnapAnimator`.
- Build uses JavaExec assertion tests. Coverage includes cue codec, timeline age/expiry/opening-window/realtime offsets, anchor fallback, quality, recipe registration, identity-branch cleanup, the exact 15 age-aware recipe calls, and legacy-path absence (`ProjectSanityTest.java:303-393`; absence guards `:372-377`).

## Important Sequencing Decision

Task 2 registered generic transport but intentionally left legacy emitters live. The server/client migration was switched atomically in Task 4 after recipes were registered. This prevents an intermediate commit from producing payloads the client cannot render.

## Documentation State

- Obsidian MOC and `04-client-vfx/VFX-core.md` define `ID → cue → recipe → verification`, forbid direct ability-to-renderer coupling, and keep gameplay mutation on the server.
- `mcpvault` sources and the refreshed worktree code graph were consulted; Hairpin effects, networking, boundaries, API surface, lifecycle, next-character guidance, nail rendering, risks, claims, parity, and build commands were cross-checked against current code.
- Removed timeline/profile/playback/static-manager/payload names remain only in explicitly historical or forbidden-path wording. MOC links now resolve to files committed on this branch.

## Final Verification Evidence

- Original final-review correction (`4aa7274`) focused RED: `testVfxTimeline` failed while timing helpers were missing, and `testProjectSanity` failed while `ClientLevel` tracking was missing. Focused GREEN: both passed; `check` was `BUILD SUCCESSFUL in 11s`; `build --no-daemon -x test` was `BUILD SUCCESSFUL in 14s`.
- Guard tightening (`c79c4cb`) proved the old assertions incorrectly passed after one recipe regressed to a fresh overload and after `clear()` was removed from the level-change branch. The new assertions failed with `found 14` and the expected lifecycle message, then passed after production restoration.
- Fresh final verification: `gradlew.bat testProjectSanity --no-daemon`, `gradlew.bat check --no-daemon`, and `gradlew.bat build --no-daemon -x test` all passed; `check` and `build` were each `BUILD SUCCESSFUL in 10s`.
- Standard `gradlew.bat runClient --no-daemon` was first blocked by Windows system commit limit `errno=1455`. A terminal-only direct launch of the same generated Loom client config, without a Gradle daemon and with `-Xms128m -Xmx1024m`, reached `JujutsuMod initialized`, LWJGL, OpenAL, resource reload, and atlas creation. `logs/2026-07-10-1.log.gz` contained no `ERROR`/`FATAL`; the process was intentionally stopped with Ctrl+C. This was startup smoke, not gameplay QA.
- Manual hammer/launch, resonance/link, Enlarge/Boom, death/despawn anchor fallback, reduced-particle, and two-client scenarios: **NOT PERFORMED / UNVERIFIED** due the explicit no-UI-automation instruction.
- Built jar: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice\build\libs\jujutsumod-1.0.0.jar` (2,102,209 bytes).
- Installed jar: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar` (2,102,209 bytes).
- Source/destination SHA-256: `19A943FFEAED46D55EBBD7F775828499E5DDFA44485339B2ED8802B33F87EE15` (equal).

## Remaining Manual QA

- With explicit permission for human/UI interaction, test the five gameplay/settings scenarios above.
- When a second local client is available, verify one server-confirmed scene from caster and observer viewpoints and confirm no client gameplay authority.

## Suggested Skills

- `using-git-worktrees`
- `test-driven-development`
- `systematic-debugging` if a build/test fails
- `verification-before-completion`
- `requesting-code-review`
- `finishing-a-development-branch`
