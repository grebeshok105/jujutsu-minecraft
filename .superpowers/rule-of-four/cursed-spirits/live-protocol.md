# Live verification protocol (Block 5, MCP dev lane)

**Tool schemas are confirmed via `tools/list` at the start of the live session** (upstream jar 1.1.0); the called out specifics below come from the reviewer-verified pinned sources and must be re-checked in that call. Everything is evidence-bearing: every line ends with a number, a frame, or a query result.

## Session setup

1. Lane: `mcp-lane-launch` skill sequence — `gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/minecraft-fabric-mcp-1.1.0+1.21.8.jar --no-daemon --max-workers=1 --no-watch-fs --console=plain`; ports 8765 (mod tools) / 8766 (upstream); bearer token from `run/config/minecraft_fabric_mcp/config.json` (auth off on this machine).
2. Confirm `tools/list`; record the exact tool names available this run.
3. Player uuid is new each launch — capture it from the first query and reuse.
4. `time set noon`, `weather clear`, freeze the player where needed (Slowness 100 = freeze without NoAI).
5. Every mob oracle: HP delta (`entity_get_nbt` / damage numbers), position delta, or numeric frame analysis — **no** stagger-via-`state_get`, **no** raycast/crosshair oracles, **no** audio oracles (sounds are manual-only).

## Per-tier sequence (T1 → T2 → T3 → combined)

| Step | Call | Oracle (number) |
|---|---|---|
| Summon | `entity_summon(type, dimension, pos, nbt:{Variant:"<id>"})` (per the frozen NBT key) | entity query returns 1 with the expected `type` + `Variant` |
| Render | `jujutsu_view_capture` (still, frozen mob) | frame exists; bbox height ratio vs hitbox within ±15%; mean luminance > background floor; variant texture present (region color histogram differs from a same-tier sibling) |
| Idle | two captures 40 ticks apart, mob frozen | motion mask (diff pixels) < 1% |
| Walk | send the mob toward a target (player at 8 blocks, AI walking) | motion mask > 5% between frames; position delta > 0.5 blocks over 40 ticks |
| Attack | burst protocol: provoke (approach), then capture ×3 with `ticks_wait` 4/8/12 | attack frame motion mask > walk frame; player HP drops by the tier damage (single-target) |
| Hurt | player hits the mob via the character ability lane | mob HP delta; scream state observable only as extra motion on the next burst frame (variant with scream) |
| Death | push HP to 0 | `entity_query` no longer lists the mob; no orphan entity |
| T2 lunge | capture at STRIKE tick vs pre-strike | position delta toward the target ≥ `strikeStep` |
| T3 AoE | second mob 3 blocks away, outside radius | inside-radius mob HP drops, outside-radius mob HP unchanged |
| T3 knockback | same impulse source on T1 and T3 (e.g. an explosion or a known knockback ability) | T3 displacement < 40% of T1 displacement (KB-res 0.85) |
| Swap | Todo Boogie Woogie aimed at LESSER/COMMON then GREATER | positions exchange (T1/T2); GREATER: positions unchanged + cooldown 0 |
| Megumi sic | sic a shikigami onto the spirit | spirit HP delta; shikigami takes damage back |
| Nobara | nail hit then Hairpin (aimed) | mark present; HP delta on aimed, none on mis-aim; `cooldown == 0` on mis-aim |
| Resonance | marked kill | ritual damage on the marked spirit; unmarked takes none |
| Multi-variant | ≥3 variants of one tier in one frame | one frame containing ≥3 distinct `Variant` values (from `entity_get_nbt`), each with its own mechanic oracle true |
| Soak | 10 min at night, census every 60 s | counts ≤ `MAX_SPIRITS_NEARBY` per sampled area; TPS ≥ 18 (`/tick query`); zero `jujutsumod` ERROR lines in the lane log |

## Manual-only checks (recorded in the Codex verification boundary)

- Sound firing/coherence: play one session per tier listening to ambient/hurt/death (+scream where the variant has it); note silence/mismatch.
- Animation timing/weight: watch attack windups and walk cycles; note floaty/cut-off motion (burst frames are only supporting numeric evidence).
- Variant distinctness and silhouette readability at distance: side-by-side noon screenshots per tier.
- World-feel/cap comfort: is the night population noticeable but not oppressive; is a T3 encounter readable at first sight.
- Hitbox visual fit: does the model match the collision box when fighting (aimed damage at the box edge + eyeball).

## Evidence layout

- Frames + numeric deltas + `entity_query`/`entity_get_nbt` outputs + soak numbers are appended to `block-5-evidence.md` and referenced from the final report; the PR body carries the summary (numbers, not adjectives).
