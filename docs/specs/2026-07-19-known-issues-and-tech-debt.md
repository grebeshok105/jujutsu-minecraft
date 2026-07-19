# Spec: Known Issues & Tech Debt

Дата: 2026-07-19
Статус: OPEN
Контекст: main синхронизирован с codex/nobara-cinematic-slice (5073b24).

---

## P0 — Критические

### 1. Worktree SoT устранён, но worktree не очищены

main теперь содержит весь код из `codex/nobara-cinematic-slice`. Worktree `.worktrees/nobara-cinematic-slice` и ветка `codex/nobara-cinematic-slice` больше не нужны как источник правды.

**Действие:** удалить worktree + ветку после подтверждения, что незакоммиченный `.bbmodel` не нужен. Остальные worktree (`brainstorming`, `vfx-director-prototype`) — ревизовать и удалить.

---

## P1 — Высокие

### 2. Выбор персонажа теряется при рестарте

`CharacterSelectionManager` хранит выбор в памяти. После перезахода игрок теряет выбранного персонажа.

**Действие:** персистить в player data (NBT/Component) или в world savedata.

### 3. VFX broadcast может пропустить клиента

`VfxCuePayload` рассылается по радиусу. Клиент вне радиуса в момент каста не получит cue → визуальный провал.

**Действие:** late-join catch-up или broadcast всем игрокам в измерении для критичных cue.

### 4. ProjectJJK ARR-ассеты в репозитории

Текстуры, модели, анимации, звуки из ProjectJJK (All Rights Reserved) находятся в `assets/jujutsumod/textures/projectjjk/`, `geo/projectjjk/`, `sounds/projectjjk/`, `geckolib/`. Юридический риск при публикации.

**Действие:** заменить на оригинальные ассеты или получить лицензию. До замены — не публиковать мод публично.

### 5. Нет автоматического in-game smoke-теста в CI

Сборка проверяет компиляцию и unit-тесты, но не запускает клиент. Регрессия в рендере/краше не ловится.

**Действие:** headless `runClient` smoke или GameTest Framework.

---

## P2 — Средние

### 6. Shader/post-process бэкенд не доказан на 1.21.8

`VfxPostProcessChannel` существует, но ни один post-process шейдер не подтверждён работающим на 1.21.8 (vanilla post-process pipeline изменился). Старые hairpin-шейдеры удалены.

**Действие:** прототип одного post-process pass на 1.21.8; если невозможно — fallback на world-space квады.

### 7. Mixin-зависимость от vanilla renderer internals

`NobaraPlayerRendererMixin`, `NobaraLivingEntityRendererMixin`, `CharacterSkinMixin`, `HairpinCameraMixin`, `HairpinGameRendererMixin`, `VfxDeltaTrackerMixin`, `NobaraFirstPersonSnapMixin` — 7 mixin. Каждый MC-апдейт может сломать.

**Действие:** при апгрейде MC — приоритетная проверка mixin. По возможности заменять на Fabric API hooks.

### 8. Нет localization для ru_ru (частично)

`ru_ru.json` добавлен, но покрывает не все ключи из `en_us.json`.

**Действие:** синхронизировать ключи.

### 9. Нет публикации (CurseForge/Modrinth)

`build.gradle` не содержит `cursegradle` / `modrinth` плагинов. Релизный процесс ручной.

**Действие:** добавить после замены ARR-ассетов.

---

## P3 — Низкие / Наблюдение

### 10. Кастомные частицы — boilerplate

8 particle-классов с повторяющейся логикой. Частично митигировано `JujutsuClientParticles`, но каждый класс всё ещё ~50 строк однотипного кода.

### 11. Нет datapack-контента

Рецепты, loot tables, tags для предметов отсутствуют (кроме damage_type tags). Крафт гвоздей/молота не определён.

### 12. `nobara-cinematic-slice-review-33ef9c2.zip` в корне репо

4.5 MB бинарник. Не нужен в git.

**Действие:** удалить из tracking (git rm --cached) + добавить в .gitignore.

### 13. `lightrag.log` и `rag_storage/` в корне

Артефакты RAG-тулинга. Не должны быть в репо.

**Действие:** .gitignore.

---

## Открытые дизайн-вопросы (из AGENTS.md)

| # | Вопрос | Статус |
|---|--------|--------|
| 1 | Anime-accurate vs Minecraft-native vs hybrid? | Фактически hybrid (ProjectJJK-механики + MC-рендер) |
| 2 | Singleplayer-first или multiplayer-safe? | Server-authoritative, но без dedicated-server теста |
| 3 | Первый персонаж? | Нобара — реализована |
| 4 | Сколько активных способностей? | 6+ (nail, hammer, enlarge, boom, resonance, trap, chain) |
| 5 | Cursed energy универсальная или per-character? | Per-character (`ProjectJjkNobaraProfile`) |
| 6 | Визуальная библиотека? | GeckoLib для geo-рендера; постпроцесс не решён |
| 7 | Прогрессия в первом майлстоуне? | Remnant progress есть, но без unlock-гейта |

---

## Следующие шаги (приоритет)

1. Удалить stale worktree и ветки
2. Заменить/изолировать ARR-ассеты
3. Персистентность выбора персонажа
4. VFX late-join / broadcast fix
5. Доказать shader-бэкенд или закрыть канал
6. In-game smoke в CI
