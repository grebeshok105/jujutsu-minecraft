# Session Handoff — Megumi Shadow Kit + Shadow Drop

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/megumi-shadow-drop`
- Branch: `feat/megumi-shadow-drop` (from `feat/megumi-shadow-kit` `21c595a`; merges back into the PR #55 head)
- Scope: Megumi's second ability pair (`B` Shadow Trap, `Shift+B` Shadow Move) plus the polish wave — void-black trap-family pools, a smooth visible dive in both persons, and the new `V` Shadow Drop (telegraphed overhead zone dropping one weighted vanilla falling block).

## Design contract

- Specs: [docs/MEGUMI_SHADOW_KIT.md](docs/MEGUMI_SHADOW_KIT.md) and [docs/MEGUMI_SHADOW_DROP.md](docs/MEGUMI_SHADOW_DROP.md) (both committed before implementation).
- Codex: [Megumi shadow kit](Jujutsu%20Kaizen/jujutsumod-codebase-codex/03-systems/Megumi-shadow-kit.md).
- Wire: `SECONDARY_SNEAK_HOLD(6)` / `SECONDARY_SNEAK_RELEASE(7)` / `TERTIARY(8)` appended to `CharacterAbility`; ids 0–5 untouched; no new payloads.

## Verification

- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` — required green before handoff.
- Nothing in the suite constructs a `ServerLevel` (E1): teleports, collision, invisibility sync, trap feel and animations are proven only by the manual smoke below.

## Manual smoke checklist (round 1 pending)

Shadow Trap (`B`):
1. Cast on a mob and on a second player: pool opens under the target's feet, target is heavily slowed, cannot jump, walks out slowly; pool stays put for 5 s and collapses readably.
2. Cast on an airborne target: pool lands on the ground beneath it, not in mid-air.
3. Golem/cow/sheep (non-Enemy mobs) are gripped too; own Divine Dogs and allies are not.
4. Dogs + trap: Sic a gripped target — dogs close in and pounce naturally, no teleporting dogs.
5. Trap + owner death / dimension change / vessel change / disconnect: pool closes immediately.

Shadow Move tap (`Shift+B`, released quickly):
6. Aimed at a standing mob: Megumi sinks (~0.4 s), vanishes briefly, emerges ~1.75 blocks behind its back facing it; camera/rotation snap is clean.
7. Aimed at a moving/turning target: exit tracks the target's live back at emerge time, not its cast-time position; sharp turn mid-cast still lands behind the current back or safely nearby (±25°/±50°/±75° arc).
8. Target dies or runs far while Megumi is hidden: he resurfaces at his start point, no cooldown lost beyond the normal one.
9. Backstep against a wall-hugging target / in a 1-wide corridor / under a low ceiling: no clipping into blocks; falls back across the rear arc or returns to start.
10. Aimed at the ground/a ledge/a wall with no target: free step onto or beside the aimed surface; nudged out of the face, may resolve slightly above.
11. Free step through a window/thick wall: refused (clip cannot see through); unloaded chunks unreachable.
12. No target, aiming at the sky: refused with the "no shadow" line, no cooldown.
13. Damage during the sink window: cast cancels on the spot, no teleport, no cooldown.

Deep submerge (`Shift+B` held):
14. Hold ≥ 0.3 s: after the sink Megumi disappears (model, held item, shadow); a faint dark ripple follows his movement.
15. While under: walking/jumping over small obstacles works, walls still block; attacks do nothing; R / Shift+R / B are swallowed with the "inside the shadow" line.
16. Ordinary attacks (melee, arrows) do not land while fully under; standing in fire/lava while under deals nothing until emerge (accepted mobility payoff — fire ticks land after surfacing); void still kills.
17. Release / repeat tap: emerges early at the current spot. Timeout (~2.5 s): auto-emerge.
18. Emerge inside a suffocating spot (sand poured on the ripple): rescued to the nearest safe point within ~3 blocks, else back at the entry point.
19. Death / dimension change / vessel change / disconnect / server stop in every phase: visibility restored, no stuck invisible player, no leftover state after rejoin.
20. Second player observes: dive pool, ripple while hidden, emerge burst; the hidden body is not targetable by sight; nameplate behaviour noted.

Regression:
21. Dogs summon/recall/Sic/pounce unchanged; Nobara R/B/Shift+R/Shift+B unchanged; Todo swap/feint/pair swap unchanged — note the pair-swap tap now confirms on release (≤0.3 s later than before).
22. Cooldown mirror: after each move the Shift+B slot shows cooldown (tap 6 s, hold 10 s); trap 10 s on B.

Shadow Drop (`V`) + dive polish (this wave):
23. Trap pool and drop disc are void-black holes while alive: pure #000, no translucency breath; the close dissolves smoothly (255→0 sweep) instead of blinking out; the dogs' summon pool still fades.
24. Sink is smooth in third person at any frame rate: the body eases down ~1.9 blocks continuously (per-frame partial ticks, no once-per-tick stepping) and is hidden only when fully under; emerge rises the same way. Another player watching sees the same.
25. Sink is smooth in first person: the camera dips continuously (never into the ground), the screen veils to ~75% black at the bottom, settles to a light veil while under, and clears across the emerge.
26. `V` on an aimed mob: a small black disc opens ~4 blocks over its head, follows it for 1 s (moving target keeps the disc overhead), then a 1–3 block volley falls out — the first dead-centre, the rest scattered inside the disc; near-flat weights (sand 30 / gravel 25 / clay 25 / anvil 20 per block), the anvil hurts hard, soft blocks lightly.
27. The fallen blocks never place into the world and never drop an item, wherever they land (open ground, water, a mob's head, a hole).
28. `V` with no target / sky aim: refused with the drop "no shadow" line, no cooldown started. Cooldown mirror on V is 3 s after a successful cast.
29. Target dies or leaves the dimension during the telegraph: the disc closes, nothing falls, cooldown already ran.
30. Nobara and Todo on `V`: silent no-op (no message, no packet error); their kits unchanged.
31. Owner death / vessel change / disconnect during the telegraph: zone closes cleanly, nothing falls after.

## Smoke round 1 findings → fixes (committed `aef9b5c`)

1. Sink read as stepped → every reader now samples `ShadowBodySink` at the frame's fractional game time; `VfxCameraChannel.diveOffsetBlocks/diveFadeAlpha` take the partial tick.
2. Trap-family pools blinked out on close → the closing sweep dissolves 255→0 (smoothstep) while staying pure black; alive pools remain constant 255.
3. Drop felt weak/slow → `DROP_COOLDOWN_TICKS` 160→60 (3 s), and each cast releases a `DROP_MIN_BLOCKS`–`DROP_MAX_BLOCKS` (1–3) volley scattered inside `DROP_SCATTER_RADIUS` (0.9); weights flattened 40/30/20/10 → 30/25/25/20 so the anvil actually shows up.

## Review round (4 independent reviewers, wave `21c595a..aef9b5c`)

Consolidated into [docs/MEGUMI_SHADOW_DROP_REVIEW.md](docs/MEGUMI_SHADOW_DROP_REVIEW.md) — findings
R1–R7, **all applied** in the follow-up pass on this branch. Highlights: R1 major —
`FallingBlockEntity.fall()` unconditionally replaces the block at its spawn position (bytecode
re-verified with `javap`), fixed by the `spawnPosFor` walk-down guard, so a cast under a ceiling
spawns below it instead of eating the roof; R2 — interrupted dives now hand their depth over in
both directions (backdated `beginEmerge`/`beginSink`); R3 — a corpse mid death-animation still
anchors `drop_zone_close`; R4 — the channel dive tests pin the sink TTL clock through
`ShadowBodySinkTestClock`; R5 — the wave spec's API row matches the shipped three-arg signatures;
R6 — five new `ShadowBodySinkTest` pins (reorder, late join, re-dive, both depth handoffs); R7 —
"Third Technique" capitalization.

## Deploy

- Deployed jar in `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar` is built from `feat/megumi-shadow-drop` at `aef9b5c`, md5 `57311c6950b9f2ddef43008bcc78820f` (previous: `e2974a9`, md5 `f79039f850ea61b98ed5aa9ff0385f26`).

## Next steps

1. Manual smoke round 2 against the checklist above (human-controlled client), focusing items 23–31 plus the R1/R2 scenarios (cast under a ceiling: no block deleted, volley spawns below it; take damage during the sink: the body rises from its current depth).
2. Merge `feat/megumi-shadow-drop` into `feat/megumi-shadow-kit`, then squash-merge PR #55 into `main`.
