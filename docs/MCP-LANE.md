# MCP dev-lane — операционный гайд

Dev-only лейн для in-game верификации через MCP-компаньон (`src/mcpdev/`).
В релиз не входит; живёт только в dev-клиенте при запуске с `-PmcpSpike`.

## Топология портов

| Порт | Эндпоинт | Что там живёт |
|---|---|---|
| 8765 | `http://127.0.0.1:8765/mcp` | world/server: все `jujutsu_*`, `entity_*`, `command_execute`, `world_*` |
| 8766 | `http://127.0.0.1:8766/mcp` | client-sense: `client_status`, сенсорика клиента |

Оба — JSON-RPC по HTTP: `tools/list`, `tools/call`.

## Запуск

```bash
./gradlew runClient -PmcpSpike \
  -PmcpUpstreamJar="D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/minecraft-fabric-mcp-1.1.0+1.21.8.jar" \
  --no-daemon
```

`-PmcpSpike` подключает upstream MCP-мод и companion-jar (`jarMcpdev`,
собирается автоматически), quickPlay загружает сейв `run/saves/mcp-spike`.
Без `-PmcpUpstreamJar` все mcpdev-таски скипаются — это норма, не баг.

## Рестарт-протокол (ВАЖНО)

Убить gradle-шелл **не убивает** игру — окно висит, порт 8765 занят.
Правильный порядок:

```bash
# 1. Найти java-процесс ИГРЫ (devlaunch/MinecraftClient), не gradle-демон
jps -lv | grep -i devlaunch     # или FabricLoader/MinecraftClient
# 2. Убить именно его
taskkill //PID <pid> //F        # двойные слеши — git-bash escaping
# 3. Убить shell с runClient (если ещё жив)
# 4. Проверить, что порт свободен — вывод должен быть пустым
netstat -ano | grep 8765
# 5. Только теперь — новый запуск командой выше
```

Один живой клиент на лейн — всегда. Второй клиент = два порта, рассинхрон,
«инструмент работает в одном мире и не работает в другом».

## Прямой HTTP-обход бриджа

Бридж сессии флашит состояние при живом сервере — для скриптов дёргай MCP
напрямую:

```bash
python .agent-runs/ingame/mc_call.py 8765 jujutsu_state_get '{}'
python .agent-runs/ingame/mc_call.py 8765 jujutsu_combat_log '{"since_tick":0}'
```

## Рецепты

- **Спавн духа с фиксированным пулом**: `jujutsu_entity_summon_near`
  `{"entity_type":"jujutsumod:cursed_spirit","nbt":"{Abilities:\"fear,dash,armor\"}"}`
  — плоские NBT-ключи прямо на сущности.
- **Бафф моба хп**: `"nbt":"{attributes:[{id:\"minecraft:max_health\",base:200.0}],Health:200.0f}"`
  — ⚠️ ключ `attributes` строчными; camelCase `Attributes` молча игнорируется.
- **Снапшот и чистка**: `jujutsu_state_get` (полное состояние игрока/кулдаунов/
  сущности) и `jujutsu_fixture_reset` (возврат фикстуры в исходное) — primary
  инструменты вокруг каждого сценария.
- **Sic-aim**: `jujutsu_look_at` → `jujutsu_ability_invoke {"slot":"PRIMARY_SNEAK"}`.
- **Источник-aware урон**: `jujutsu_entity_attack` — проверки гейтов
  («немаг не ранит духа») требуют реального источника, `/damage` не подходит.
- **Ожидание условий**: `jujutsu_wait_until` — `entity_dead`, `effect_on`,
  `health_below`, `cooldown_clear` и т.д.; `jujutsu_ticks_wait` — простой
  тик-слип.

## Известные капризы (наблюдено вживую)

- `command_execute` теряет текстовый output: `data get`/`gamemode` отдают
  `successCount:0` даже при успехе — не используй для чтения состояния.
- `entity_get` бросает «Entity not found» на мёртвых/выгруженных — для
  «умер ли» используй `jujutsu_wait_until` с `entity_dead`/`entity_gone`
  или `jujutsu_combat_log`.
- `distance=..N` в селекторах меряется от origin команды, не от игрока.
- Тег-селекторы `@e[type=#...]` отдают `[]` — только точные id.
- `Abilities`-пул духа и `attributes` NBT — регистр ключа значим.
