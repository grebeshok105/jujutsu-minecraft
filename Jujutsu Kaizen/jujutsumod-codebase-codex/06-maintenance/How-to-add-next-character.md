# How to Add the Next Character

Status: CURRENT RATIONALE

The executable procedure is the `add-vessel` project skill (`.claude/skills/add-vessel/SKILL.md`). This note keeps only the seam rationale, the history worth remembering, and the entry points. For steps, follow the skill.

Three vessels ship (Nobara, Todo, Megumi) plus NONE, so the shared contracts already exist and were extracted from real code rather than guessed. The shape of the whole job: **one enum constant, one server definition, one client definition, one line in each registry, and assets.** `registerServerHooks` and `registerClientHooks` exist precisely so that neither `JujutsuMod.onInitialize` nor `JujutsuModClient` is ever edited for a new vessel — both just loop their registry.

Read [Vessel definitions](../02-architecture/Vessel-definitions.md) first — it is the contract — then [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the drawing half.

## Why the seam looks like this

- A vessel-private payload once forced a full migration to the one shared `CharacterAbilityPayload`, which carries the vessel claim the server checks. Two input paths cannot agree on what a key means — never add a second one.
- A `default` arm in a vessel or slot switch turns a compile error into a silent wrong answer. Routers answer `AbilityResult` (`SUCCESS` / `HANDLED_FAILURE` / `UNHANDLED_FAILURE`) exhaustively; unused slots refuse explicitly. The full cast contract is `docs/ABILITY_RESULT_CONTRACT.md`.
- The aggregate `JujutsuVfxRecipes` was a second hand-kept list of who exists; each vessel now registers its own recipe pack from its client definition, so the list cannot drift.
- The marker-item gate died with the marker: vessel-gated things ride the executor's selection gate, not item checks. Starter kits ensure-don't-grant, because `onSelected` runs on every selection (E12).
- Transient entities get `.noSave()` — a thrown object that saves haunts the world after a crash.
- Megumi proved the third vessel adds no shared summon hierarchy: two transient vanilla bodies in one runtime pack, no universal abstraction. Do not invent a framework on top of the seams; extend them.

## Entry points

- Contract: [Vessel definitions](../02-architecture/Vessel-definitions.md) (hooks, wire seam, deliberate residue).
- Drawing half: [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) (adapter subclass, bone-only rig, one skin texture).
- Presentation: [VFX core](../04-client-vfx/VFX-core.md) (cue → director → recipe → channel, own your ids).
- Verification: [Test and build commands](../05-reference/Test-and-build-commands.md) (gate, GameTest lanes, MCP lane) plus the boundary suites `VesselBoundaryTest`, `SourceBoundaryTripwireTest`, `ProjectSanityTest`.
- Debt that still bites: E1 (no ability/world-behavior automation), E2 (no technique-id catalog), E5 (no lang-parity check), E10/E11 (message ordering), E13/E14 (boundary pins) — all in `docs/KNOWN_ISSUES.md`.
