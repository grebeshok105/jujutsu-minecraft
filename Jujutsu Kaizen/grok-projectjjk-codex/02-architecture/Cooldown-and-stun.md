# Cooldown & Stun

← [[00-MOC]] · [[Ability-system]]

## CooldownManager

Path: `game/system/cooldown/CooldownManager.java`  
Singleton: `ProjectJJK.cooldownManager`

Storage: `Map<UUID, Map<abilityId, endEpochMs>>`

| Method | Behavior |
|---|---|
| `forceCooldown` | set end = now + sec×1000; S2C SetAbilityCooldownPacket |
| `forceHighestCooldown` | merge with max(end); used by dependency abilities |
| `startCooldown` | no-op if already cooling that ability |
| `setAbilityCooldown` | unconditional |
| `removeCooldown` | clear |
| `isOnCooldown` | end > now (cleans expired) |
| `getRemainingCooldown` | ms left |

**Unit in ability registry: seconds** (UI appends `"s"`).

### Dependencies

On cast, each `dependencyAbilities` entry calls `forceHighestCooldown(player, id, seconds)`.  
Full matrix: [[05-reference/Dependencies]]

### Extra CD from cast return

`Pair<Boolean, Long>` second value often adds seconds to CD (e.g. Six Eyes returns 60, Purple returns 5). Units are not perfectly consistent across abilities — check per-ability notes.

## StunTimeManager

Path: `game/system/cooldown/StunTimeManager.java`  
Singleton: `ProjectJJK.stunTimeManager`

| Method | Behavior |
|---|---|
| `startStunTimer(player, seconds)` | applies `ModEffects.STUN` for seconds×20 ticks; StunData true; actionbar message |
| `updateStunTimers` | expire → StunData false |
| `isOnStunTime` | map check |

Ticked on END_SERVER_TICK when map non-empty.

**⚠ Note:** map key is `ServerPlayer` object identity — fragile across re-login (as written).

## StunData

NBT `"stun"` boolean + `SynchronizeStunPacket`.

## Ability stuns (examples)

| Source | Player | Mob |
|---|---|---|
| Hairpin Enlarge | stunPlayer 2s | STUN 50 ticks amp 1 |
| Nail Bind | stunPlayer 2s | STUN 20 ticks amp 1 |
| Dont Move | stunPlayer 3s | STUN 3×20 |
| Counter | 1s (static) | — |

---

tags: #projectjjk #architecture #cooldown
