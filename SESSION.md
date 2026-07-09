# Session Handoff — Jujutsu Minecraft

Дата: 2026-07-09

## Где мы сейчас

- Проект: `D:\WorkFlow\Jujutsu Minecraft`
- Активный worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`
- Ветка: `codex/nobara-cinematic-slice`
- Последний коммит: `af1a225 refactor(nobara): remove legacy hairpin runtime`
- GitHub не пушить, пока пользователь явно не попросит.

## Главное состояние

ProjectJJK Nobara теперь единственная каноничная Нобара.

Старая jujutsumod Nobara/Hairpin реализация удалена:

- `NobaraHairpinRuntime`
- `NobaraCombatStateManager`
- `HairpinGameplayService`
- старые `HairpinNailItem` / `StrawDollHammerItem`
- `HairpinFxPayload`, `HairpinNailFlightPayload`, `PreparedNailsPayload`
- `HairpinPlayback`, `HairpinPlaybackManager`, `NobaraNailFlightManager`
- `HairpinTimeline`, `HairpinVisualProfile`, `HairpinDebugLog`
- старые item-модели, item-текстуры и неиспользуемые Hairpin post-shaders
- старые тесты и Gradle tasks под удаленный стек

Id `hairpin_nail` и `straw_doll_hammer` оставлены, но теперь это ProjectJJK items/models.

## Что обязательно читать следующей сессии

- `AGENTS.md`
- Obsidian: `jujutsumod-codebase-codex/00-MOC.md`
- Obsidian: `jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- Obsidian: `jujutsumod-codebase-codex/06-maintenance/2026-07-09-legacy-nobara-removal.md`
- Git commit: `af1a225`

Codebase graph обновлен и готов:

`D-WorkFlow-Jujutsu-Minecraft-.worktrees-nobara-cinematic-slice`

## Проверки последней сессии

Прошло:

- `gradlew.bat check --no-daemon`
- `gradlew.bat build --no-daemon -x test`

Jar скопирован в инстанс:

`D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`

SHA256 jar в `build/libs` и в инстансе совпал:

`29FC260FDF71F5ACB2921E29A9FC6EE684D414D01FDF8B714B96F7248F32134D`

## Ревью

Read-only reviewer проверил cleanup:

- critical issues не найдено;
- live-ссылок на удаленный legacy Nobara stack нет;
- ProjectJJK Nobara не удалена и остается подключенной;
- замечание по временному global Gradle memory tweak исправлено до коммита;
- guard/doc gaps исправлены до коммита.

## Важные ловушки

- Не возвращать старый `NobaraHairpinRuntime`, `HairpinFxPayload`, `HairpinPlaybackManager`, `NobaraNailFlightManager`.
- Старые имена внутри `ProjectSanityTest` нормальны: это negative assertions.
- Оставшиеся `hairpin_*` не всегда legacy: часть используется текущей ProjectJJK Nobara для эффектов, звуков, UI и action names.
- Старые repo docs по cinematic slice и item 3D model technique помечены как superseded archival notes.
- После любой работы по моду нужно собирать runtime jar и копировать его в `D:\Games\instances\Jujutsu\mods`.

## Suggested skills

- `verification-before-completion` перед любым "готово".
- `requesting-code-review` после крупных gameplay/rendering изменений.
- `diagnosing-bugs` или `systematic-debugging` для крашей и визуальных багов.
- `obsidian-vault` перед изменениями ProjectJJK/Nobara/ported systems.
- `handoff` перед завершением длинной сессии.

## Что логично делать дальше

- In-game smoke test текущей ProjectJJK Nobara.
- Собирать новый feedback по модели, голове, гвоздям, VFX и UI.
- Полировать Nobara маленькими коммитами.
- Обновлять Obsidian/codebase codex после каждого значимого gameplay/VFX/UI/networking/asset изменения.
