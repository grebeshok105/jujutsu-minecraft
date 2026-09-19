# Cursed Incidents

Status: CURRENT (issue #110, `feat/cursed-incidents`; core loop, objects, infection, seal, mcpdev surface landed)

A world-scale subsystem: a **cursed incident** is a persistent, escalating zone anchored on a source — a free-standing curse well or a physical **cursed object** — that infects blocks, spawns tagged spirits, culls animals, and can be sealed, damaged, relocated, or cleaned up. Records live in a `SavedData` store (`jujutsumod_incidents`) and survive save/load; the world half runs through a bounded tick driver, never inside chunk-load callbacks.

## Shape

- `IncidentControl` is the single facade: spawn/spawnObject, advance/setStage/escalate, seal/unseal/damageSeal, relocate, forceSecondary, cleanup, inspect/list, reseed, catchUp. Everything else — commands, the 15 `jujutsu_incident_*` mcpdev tools, GameTests — goes through it. `IncidentRecord` is the mutable store row; `InspectView` is the read model.
- Stages: INITIAL → GROWING → INFESTED → CRITICAL → CATASTROPHIC, strictly forward. `StagePolicy` thresholds are base ticks × `escalationSpeedMul` (grade 1→0.7 … 5→1.4) × template mul. `advance`/`advanceTo` fire every owed transition; `transitionsBetween` clamps target ≤ current and negative ticks.
- Sources: `SourceKind.FREE` (no physical anchor) or `SourceKind.OBJECT` — a minted `CursedObjectItem` stack whose `CURSED_OBJECT_STATE` component is the **seal authority**; the record mirrors it via `IncidentControl.syncSealFromComponent` (called from `CursedObjectItem.trySeal/unseal/damageSeal` and `ObjectDwellTracker.applySealDecay`, both directions).
- Persistence: `IncidentSavedData` (codec `IncidentCodec`) tolerates corrupt entries — a bad record drops with a log line, siblings survive (R60). SCAR is a `boolean scarred` flag plus a `scars` list of abandoned centers; `cleanup` scars the record and removes the world half.
- Pressure: `PressureRuntime` accumulates `elapsed / TICKS_PER_DAY` with the remainder preserved (`lastTickGameTime += accrued * TICKS_PER_DAY`) and rolls natural spawns; `cursedPressure`/`addPressure`/`resetPressure` are the only pressure verbs.

## World half

- `IncidentRuntime` ticks every 20 server ticks: per-record `server.getLevel(record.dimension)` (non-overworld incidents age in their own level), `advanceTo` for owed stages, then `sink.tickZone` for loaded, unsealed, unscarred records. `SERVER_STOPPING` clears every static map; `SERVER_STARTED` re-binds via `IncidentWiring.rebind()` — a second world entry in one JVM is fully functional.
- `InfectionSink` (the production `IncidentWorldSink`) owns: stage deltas (seeded sample `RandomSource.create(seed ^ stage.ordinal())`, `min(400, r³/8)` positions, queued as `(pos, stage)` pairs and mapped at drain time so unloaded edits survive), the 64/tick shared block budget, cadences (curse top-up 200t, container scan 300t, animal cull 400t), VFX cues (ZONE_AMBIENT/STAGE_PULSE/SEAL_APPLIED/SEAL_DEGRADE/SEAL_BREAK/SECONDARY_BIRTH — the last announced once per node id), and `onCleanup` (discards `jujutsumod:incident/<uuid>`-tagged spirits via `getAllEntities`, unregisters the source object).
- `InfectionQueue.drain` maps `InfectionPolicy.mapBlock(current, stage, seeded)` at apply time; unloaded positions requeue without counting (deferred is counted once at enqueue). Sealed records are refused at every gate: applyStageDelta, tickZone, drain, flushPendingDrains.
- `IncidentSpawnRuntime` spawns waves of `CursedSpiritEntity` tagged `jujutsumod:incident/<uuid>` — the tag is what cleanup, fixture-reset and the cull sweep read. `trySpawnWave` respects the local cap.

## Objects and dwell

- `CursedObjectRegistry` holds the type table (`sukuna_finger` indestructible + 7 destructible + `qa_probe` dev-only, excluded from natural rolls); `ObjectSpawnerImpl` mints `ItemEntity` + `CURSED_OBJECT_STATE`. `spawn()` mints through the bound spawner for OBJECT sources — a refused mint drops the record before the INITIAL delta.
- `ObjectDwellTracker` (the `DwellProvider`) tracks world items, carried stacks and container contents. Dwell accumulates while the object stays inside `type.dwellRadius()` of its anchor; the threshold is `params.dwellTicksRequired()` per incident (default 24000). A completed dwell relocates the incident center (`syncDwellCenter` writes `record.center`/`sourcePos`/`sourceContainer`).
- Destruction semantics: `RemovalReason.DISCARDED` = pickup (carried, not destroyed — no incident cease, no indestructible respawn); `KILLED`/other = destruction → `IncidentControl.onSourceDestroyed` → `cleanup` (C10). Indestructible types respawn only on genuine loss.
- Seal decay: sealed objects lose integrity per in-game day; at zero the component unseals and `syncSealFromComponent` breaks the record's seal (`onSealBroken` fires).

## Surfaces

- Commands: `/jujutsu incident spawn|inspect|list|advance|set_stage|escalate|seal|unseal|damage_seal|relocate|secondary|cleanup|reseed|identify` under the existing permission-2 root.
- mcpdev: 15 `jujutsu_incident_*` tools registered in `JujutsuModStatusToolProvider` (integer schemas for long args; structured errors, no stack traces).
- Client: `src/client/.../cursedincident/` — zone atmosphere, stage pulse, seal cues (`seal_crack` on degrade), object GeckoLib rendering with a doll fallback for unknown/`qa_probe` types.

## Known limits

- `PressureRuntime.tick` has no fake-clock seam; accumulation is covered indirectly, not by a dedicated unit test.
- `cadenceProbeForTest` is a live but currently unused test seam.
