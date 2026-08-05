# Session Handoff — Megumi Shadow Kit

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/megumi-shadow-kit`
- Branch: `feat/megumi-shadow-kit` (from `main` `fe2040b`)
- Scope: Megumi's second ability pair — `B` Shadow Trap (static slowing pool under the aimed target) and `Shift+B` Shadow Move (one technique, three contextual modes: emerge behind the target, free step to an aimed surface, held deep submerge).

## Design contract

- Spec: [docs/MEGUMI_SHADOW_KIT.md](docs/MEGUMI_SHADOW_KIT.md) (committed before implementation).
- Codex: [Megumi shadow kit](Jujutsu%20Kaizen/jujutsumod-codebase-codex/03-systems/Megumi-shadow-kit.md).
- Wire: `SECONDARY_SNEAK_HOLD(6)` / `SECONDARY_SNEAK_RELEASE(7)` appended to `CharacterAbility`; ids 0–5 untouched; no new payloads.

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
16. Ordinary attacks (melee, arrows) do not land while fully under; void still kills.
17. Release / repeat tap: emerges early at the current spot. Timeout (~2.5 s): auto-emerge.
18. Emerge inside a suffocating spot (sand poured on the ripple): rescued to the nearest safe point within ~3 blocks, else back at the entry point.
19. Death / dimension change / vessel change / disconnect / server stop in every phase: visibility restored, no stuck invisible player, no leftover state after rejoin.
20. Second player observes: dive pool, ripple while hidden, emerge burst; the hidden body is not targetable by sight; nameplate behaviour noted.

Regression:
21. Dogs summon/recall/Sic/pounce unchanged; Nobara R/B/Shift+R/Shift+B unchanged; Todo swap/feint/pair swap unchanged — note the pair-swap tap now confirms on release (≤0.3 s later than before).
22. Cooldown mirror: after each move the Shift+B slot shows cooldown (tap 6 s, hold 10 s); trap 10 s on B.

## Next steps

1. Integrate client VFX/animation/test wave, run `qualityGate`, independent review, fixes.
2. Build the jar, deploy to `D:/Games/instances/Jujutsu/mods/`, run manual smoke round 1.
3. Open the PR.
