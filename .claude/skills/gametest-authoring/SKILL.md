---
name: gametest-authoring
description: Use when writing or debugging Fabric GameTest scenarios for the Jujutsu Minecraft mod — server GameTests (runGameTest lane), fixtures, world setup, timing, and the known 1.21.8 arena traps (barrier walls, random world offset, mock-player limits, water drag). Proven on PRs #61 and #65 (29/29 green).
---

# GameTest Authoring

Write deterministic server GameTests for the mod's combat behaviour. Proven on #61 (Todo aimed swap, 8 scenarios) and #65 (Todo stone lifecycle, 21 scenarios) — 29/29 green, 3 consecutive runs.

## Where things live

- Scenarios: `src/gametest/java/jujutsu/mod/gametest/` (e.g. `TodoStoneGameTests.java`)
- Fixtures: `src/gametest/java/jujutsu/mod/gametest/TodoSwapTestFixtures.java` — `setupTodoCaster` (mock player, select vessel, clear cooldowns), `castPrimary`/`castTertiary` (production `CharacterAbilityExecutor.tryCast`), `cleanupCaster`, `safe(Runnable)` best-effort wrapper
- Registration: `src/gametest/resources/fabric.mod.json` — append the class to the `fabric-gametest` entrypoints array or it never runs (the #61/#65 pattern)
- Run: `./gradlew.bat runGameTest --no-daemon --max-workers=1 --no-watch-fs`; results in `build/test-results/gametest/junit.xml` (parse it for failures, NOT the gradle log)

## The arena (1.21.8, bytecode-verified)

- Test world: flat, seed 0, structures on an 8-block grid, origin y=-59, skyAccess=true
- **Barrier walls flush at the 8x8 structure edge (rel 8), full height + barrier floor at rel y=-1** — any horizontal flight dies on a wall at rel ~7.9-8.0 (block collision → entity discarded). Vertical flights are free (skyAccess). Long-distance asserts MUST throw straight up or stay ≤ 6 blocks horizontal
- **World offset is random (±7-12 million) and its sign flips per run** — absolute position asserts fail. Always assert RELATIVE positions (e.g. `x - cornerX > 2.5`), never absolute
- `setBlock` has no structure-bounds check — geometry can go anywhere

## Mock player limits (the #61/#65 lessons)

- `makeMockServerPlayerInLevel()` = real ServerPlayer with loopback channel; rotation IS controllable (write server yRot/xRot/headYRot)
- **`kill()` no-ops on mock players** — `hurtServer` damage path refuses. Use `die(damageSources().genericKill())` to fire AFTER_DEATH synchronously
- Action-bar messages unobservable on the mock (no client) — assert state, not messages, unless you subclass ServerPlayer with a recording `displayClientMessage` (the #64 `MessageRecordingPlayer` pattern)
- Player-list registration not required for the executor path (select broadcasts only to list members, server-null-safe)

## Timing & physics

- Stone: 0.23 blocks/tick, 100-tick lifetime. Expiry tests need `@GameTest(maxTicks = 150)` (throw at tick 2, vanish ~102)
- Water drag is REAL: vanilla fluid push applies 0.8/tick → 0.23 becomes 0.118 after 3 drags. Assert direction-preserved + speed band, never exact velocity through water
- Cooldown gates: `CharacterAbilityCooldowns.start(player, ability, ticks)` seeds; `clear` for cleanup. Fresh mock UUID per test = no cross-test leak
- Assert in the same tick callback where possible (phase-immune); flight = delta asserts between same-phase callbacks
- `AbilityResult` (post-#64): success check is `tryCast(...) == AbilityResult.SUCCESS`; boolean consumers in fixtures map it

## Red-proof duty

Per repo policy, each scenario must be provable red: invert one assert (e.g. remove `endFlight`'s clearStone) → run → record the failure → restore. A test only ever observed green may be vacuous.

## Pragmatic skips (recorded, with reasons)

- Rollback branches unreachable through direct `place()` (not the injectable seam) — skip with comment
- True chunk unload, organic cross-dimension, disconnect event, server stop — skip; cover via representative exits + state-injection proxies
- Respawn (mock can't click respawn) — same dropEverything path as death
