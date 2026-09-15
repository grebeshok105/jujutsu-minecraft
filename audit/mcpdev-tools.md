# mcpdev-tools — отчёт по блоку

Ветка `feat/mcpdev-tools` (worktree `D:/WorkFlow/jm-wt-mcpdev`), HEAD base `7acbfc5`.
Цель: закрыть дыры наблюдаемости/управления, выявленные живым тест-сеансом.

## Что добавлено (src/mcpdev/java/jujutsu/mcpdev/)

| Тул | Назначение |
|---|---|
| `jujutsu_entity_set_health` | set живому entity, clamp 0..maxHealth; 0 → `kill()` через death path |
| `jujutsu_player_respawn` | vanilla `PlayerList.respawn`; fail fast если жив |
| `jujutsu_player_set_gamemode` | survival/creative/adventure/spectator |
| `jujutsu_entity_attack` | source-aware `hurtServer` (playerAttack/mobAttack), amount=attack_damage |
| `jujutsu_look_at` | eye-to-eye `lookAt` для sic-aim |
| `jujutsu_entity_summon_near` | `/summon` через dispatcher + box-diff UUID, оффсет + SNBT |
| `jujutsu_combat_log` | drain ring-буфера 512 записей (tick/kind/types/uuids/amount/blocked) |
| `jujutsu_wait_until` | TickTask-ожидание условий any/all: entity_dead/gone/present, effect_on/off, health_below/above, cooldown_clear |

Инфраструктура: `JujutsuCombatLog` (AFTER_DAMAGE + AFTER_DEATH хуки, чистка на
SERVER_STOPPED, атрибуция снарядов по owner), `JujutsuMcpdevEntities`
(cross-dimension find/require), регистрация в `JujutsuMcpdevBridge`,
провайдер дополнен 8 классами. Док: `docs/MCP-LANE.md`.

## Верификация

- `./gradlew mcpdevClasses -PmcpUpstreamJar=...` — SUCCESS.
- `./gradlew qualityGate` — SUCCESS: 155 GameTests passed,
  `build/test-results/gametest/junit.xml` 0 failures/errors, JUnit-таски и
  аудиты (auditDocumentation, auditReleaseJarIsolation) зелёные.
- Живой лейн: клиент поднят из worktree (порты 8765/8766, pid 26616, мир
  mcp-spike скопирован). Смоук каждого тула — см. Evidence.

## Смоук-лог (все команды реально исполнены через mc_call.py :8765)

- `jujutsu_combat_log {"limit":10}` — damage/death события с реальной
  атрибуцией (spirit→player, spirit↔spirit), `last_tick` растёт.
- `jujutsu_player_set_gamemode` creative→survival — ок.
- `jujutsu_entity_set_health` на игроке health:0 → `killed:true`; зомби 200hp
  → `health:50, max_health:200`.
- `jujutsu_player_respawn` — отказ на живом («Player is alive»), после
  `gamerule doImmediateRespawn false` + kill → `alive:true, health:20`.
- `jujutsu_entity_summon_near` — зомби с NBT-буфом хп и дух с
  `{Abilities:"fear,dash,armor"}` → `resolved:true` + uuid.
- `jujutsu_look_at` — yaw/pitch применены и возвращены.
- `jujutsu_entity_attack` — player→spirit `hurt:true` (30.03 hp остаток);
  zombie→spirit `hurt:true` → death-запись в логе; spirit→player на мёртвой
  цели корректно `hurt:false`.
- `jujutsu_wait_until` — `[entity_dead, effect_on]` mode=any →
  `satisfied:true, waited_ticks:115`, per-condition matched флаги верные.

## Открытое / замечания

- `gamerule doImmediateRespawn` оставлен `false` в mcp-spike мире (надо для
  сценариев смерти; вернуть при желании через command_execute).
- Спириты в мире фармят игрока — для чистых прогонов используй
  `jujutsu_fixture_reset` / summon с оффсетом.
- Upstream issue: https://github.com/grebeshok105/minecraft-java-fabric-mcp-server/issues/1
