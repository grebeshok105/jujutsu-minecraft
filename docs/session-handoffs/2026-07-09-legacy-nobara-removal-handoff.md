# Session Handoff — Legacy Nobara Removal

Дата: 2026-07-09

Кратко: ProjectJJK Nobara стала единственной каноничной Нобарой. Старый jujutsumod Nobara/Hairpin stack удален и закрыт regression guard'ом.

## Состояние

- Worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`
- Ветка: `codex/nobara-cinematic-slice`
- Коммит: `af1a225 refactor(nobara): remove legacy hairpin runtime`
- GitHub не пушить без явного запроса пользователя.

## Удалено

- Legacy runtime/items/state/service: `NobaraHairpinRuntime`, `NobaraCombatStateManager`, `HairpinGameplayService`, `HairpinNailItem`, `StrawDollHammerItem`
- Legacy networking: `HairpinFxPayload`, `HairpinNailFlightPayload`, `PreparedNailsPayload`
- Legacy client playback: `HairpinPlayback`, `HairpinPlaybackManager`, `NobaraNailFlightManager`
- Legacy helpers: `HairpinTimeline`, `HairpinVisualProfile`, `HairpinDebugLog`
- Старые item-модели/текстуры, старые Hairpin shaders, старые tests/tasks

## Оставлено

- ProjectJJK runtime package: `src/main/java/jujutsu/mod/character/nobara/projectjjk/`
- Default ids `hairpin_nail` / `straw_doll_hammer`, но теперь они ProjectJJK-backed.
- Current Hairpin particles/sounds/UI/action names, если они используются ProjectJJK Nobara.

## Документация

Читать:

- `SESSION.md`
- Obsidian: `jujutsumod-codebase-codex/00-MOC.md`
- Obsidian: `jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- Obsidian: `jujutsumod-codebase-codex/06-maintenance/2026-07-09-legacy-nobara-removal.md`

Старые repo docs по cinematic Hairpin отмечены как superseded archival notes.

## Проверки

Прошло:

- `gradlew.bat check --no-daemon`
- `gradlew.bat build --no-daemon -x test`

Jar обновлен:

`D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`

SHA256:

`29FC260FDF71F5ACB2921E29A9FC6EE684D414D01FDF8B714B96F7248F32134D`

## Важно следующему агенту

- Не воскресить старый Hairpin payload/playback/runtime.
- Перед изменениями Nobara сначала смотреть Obsidian и codebase graph.
- После работы обязательно собрать jar и скопировать в инстанс.
- Ответы пользователю писать по-русски.
