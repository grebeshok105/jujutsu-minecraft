# Session Handoff — Todo Stone Rework

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/todo-stone-rework`
- Branch: `feat/todo-stone-rework` (from `main` `3c71ce6`)
- Scope: delete the marker system entirely; add the thrown stone (`V` throw / `V` self-swap / `Shift+V` target swap on the appended `TERTIARY_SNEAK(9)` wire id); split `Shift+B` off the pair swap into the triple cyclic swap (Todo→A→T→Todo) and delete the `canonicalSlot` fold; single transient-state owner (`TodoTransientState`) with one cleanup path (`TodoStateLifecycle`).

## Design contract

- Spec: [docs/TODO_STONE_REWORK.md](docs/TODO_STONE_REWORK.md) (committed before implementation).
- Codex: [Todo — Boogie Woogie and combat slice](Jujutsu%20Kaizen/jujutsumod-codebase-codex/03-systems/Todo-Boogie-Woogie.md).
- Wire: `TERTIARY_SNEAK(9)` appended to `CharacterAbility`; ids 0–8 untouched; no new payloads; `USE_CONTEXT(5)` keeps its id and detection but no vessel answers it.

## Verification

- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` — required green before handoff.
- Nothing in the suite constructs a `ServerLevel` (E1): every teleport, collision, HUD and readability claim below is proven only by the manual smoke.

## Manual smoke checklist (round 1 pending)

Stone flight (`V`):
1. Throw in the open: the stone leaves the eye line slowly (~3.5 blocks/s), flies dead straight with a readable trail, and vanishes with a puff after ~5 s.
2. Throw into water: it keeps flying through the water column, not stopping at the surface; fire/lava do not end it.
3. Throw at a wall and along a narrow corridor: block contact vanishes it immediately (no anchor, nothing placed, no item dropped); a point-blank throw into a wall may vanish instantly — accepted.
4. Throw at a mob: the stone passes through bodies, no damage, no mark, no aggro.
5. While a stone flies, `V` again NEVER throws a second one; after it dies, `V` throws again. The HUD chip shows the remaining seconds only while a stone lives and only as Todo.

Stone self-swap (`V` with a live stone):
6. On the ground: Todo trades places with the flying stone; the stone continues its flight from Todo's old spot with its old direction and remaining clock; Todo keeps his own momentum and look; fall distance reset.
7. In the air: swap mid-flight works (STRICT still allows air); no clipping into blocks; refusal when the stone's point cannot fit Todo (e.g. inside a 1-block slit) moves nobody and says so.
8. Out of range (>32 blocks) or stone in another dimension: plain refusal, stone keeps flying, no cooldown burned.
9. A successful self-swap opens the momentum window (next melee hit is boosted + staggers), same as the aimed `R` swap.

Target swap (`Shift+V`):
10. On a mob and on a second player: the aimed body trades places with the stone; Todo stays put; the stone keeps flying from the target's old center.
11. Moving target and moving stone: the swap uses both live positions at cast time, not stale ones.
12. Ineligible targets refuse cleanly: dead, spectator, mounted/vehicle, leashed, armor stand, different dimension, no line of sight, beyond 20 blocks.
13. No stone / no target / unsafe placement at the stone's point: each refusal has its own message, nothing moves, the stone (if any) keeps flying. No momentum from a target swap.

Pair swap and triple cycle (`B`, `Shift+B`):
14. Plain pair swap regression: B marks — actionbar line, one audible mark beat, then a quiet ring re-drawn on the body about once a second and the pair HUD chip holding (both visible to the casting Todo alone); second B swaps the two marked bodies, Todo stays put; second B on the mark cancels; second B at nothing refuses and keeps the mark.
15. The pair chip shows the marked body's name and remaining TTL while a selection lives (Todo only).
16. B mark, then `Shift+B` on a second body: the triple cycle runs with the fixed direction — Todo to the marked body's spot, the marked body to the aimed body's spot, the aimed body to Todo's spot. The three-edge directional effect reads the cycle order.
17. `Shift+B` with no selection refuses with its own message and does NOT behave as B; the crouch does not lose a lined-up selection (B mark → sneak → Shift+B works).
18. Triple near walls, ledges, water and in the air: any body without a safe STRICT destination cancels the whole cast — nobody moves, selection survives; retry after repositioning works.
19. Triple cooldown (8 s) and pair cooldown (5 s) are separate: after a triple, plain B is still available on its own clock and vice versa.
20. Triple grants NO momentum window.
21. Rollback: no partially-moved outcomes are ever observed (if a mid-commit failure happens, all three bodies are back at their starts and the log carries an error line).

Purge and regressions:
22. `R` with nothing under the crosshair is a plain refusal — never a teleport to anything; the marker item no longer exists (`/give` finds no `todo_swap_marker`; creative tab has none).
23. An ordinary right click (once or twice) behaves fully vanilla for Todo — doors, mounts, trades; no marking of any kind.
24. `Shift+R` feint unchanged: clap performance, no movement, no endpoint bursts. Black Flash and momentum-on-`R`-swap unchanged.
25. Nobara full kit unchanged (R/B/Shift+R/Shift+B, ESP, mega nail); Megumi full kit unchanged (dogs, trap, move, drop; his `V` still Shadow Drop — Todo's V never leaks into other vessels).
26. Multiplayer observation: a second player sees the stone, its trail, both swap presentations, and the triple's three edges — but never the pair mark, its quiet pulse ring, or any Todo-only HUD chip: the selection stays the caster's secret.
27. Cleanup: death, respawn, dimension change, vessel change (menu deselect), disconnect — each kills a live stone (with vanish puff where applicable), clears the pair selection, and leaves no HUD chip; rejoin shows clean state; server stop leaves nothing behind on restart.

## Status

- Implementation integrated on this branch (rule-of-four wave: purge / stone core / pair+triple / client blocks + docs + skeleton).
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` — **green** after integration fixes (hurtServer override on the stone, pure swap-range boundary, VfxCues silent-repeat + displacement factories for the pulse and the cycle edges, contract-pin respellings).
- Independent review wave (4 reviewers, disjoint zones) done: 6 code P2s + doc sweep, all 18 unique findings adjudicated **accept** in `.superpowers/rule-of-four/todo-stone-rework/review-spec.md` and applied — portal refusal + identity-guarded unconditional discard, caster-state gate on all three stone casts, AABB/dead-velocity collision end, seeded `DATA_REMAINING_TICKS`, pair-cache read eviction + newest-entry pick, id-based HUD stone cache, `TodoStoneRef.entityId` dropped, stone respawns at the displaced body's center, caster-only quiet pulse re-draw ring, and the marker/canonicalSlot truth sweep across Codex/skill/spec docs.
- Manual smoke round 1: **pending** (checklist above).
