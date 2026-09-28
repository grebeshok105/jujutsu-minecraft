# Nobara Combat Expansion

Status: CURRENT (re-verified 2026-09-04 against source; line numbers below are that check).

Controls (`NobaraAbilityRouter.java:48-57`, keys in `client/.../input/JujutsuKeybinds.java:69-78`):

- R (PRIMARY) — directed Hairpin chain from the aimed owned nail/target (`HairpinRuntime.startDirectedHairpin`).
- B (SECONDARY) — Mega Nail: merges every embedded nail on the aimed target into one piercing strike (`ProjectJjkMegaNailRuntime.start`).
- Shift+R (PRIMARY_SNEAK) — Self Resonance through an explicit curse link (`SelfResonanceRuntime.tryCast`).
- Shift+B (SECONDARY_SNEAK) — triangular Nail Trap (`NailTrapRuntime.tryPlace`).
- Hammer left click (ATTACK_CONTEXT) — contextual horizontal/overhead/nail interaction (`NobaraHammerCombatRuntime.handleInput`).
- Nail use/hold — prepare individual nails; hammer launches prepared nails.

Nail Trap (`ProjectJjkNobaraProfile.java:40-50`, placement/trigger in `NailTrapRuntime.java:227-265`):

- Corner-nail placement radius NAIL_TRAP_RADIUS = 1.15 (nails land ~2 blocks apart; the old 6-block spread was rejected in smoke round 3 — see the comment at Profile:40).
- Trigger is a separate, wider cylinder around the trap centre: NAIL_TRAP_TRIGGER_RADIUS = 2.0, height NAIL_TRAP_PRISM_HEIGHT = 3.0 (`NailTrapRuntime.bounds`, Profile:43-45).
- 3 nails (NAIL_TRAP_NAIL_COUNT), placement range 8.0 (NAIL_TRAP_PLACEMENT_RANGE), lifetime 600 ticks (NAIL_TRAP_LIFETIME_TICKS), damage 15.0 (NAIL_TRAP_DAMAGE), interrupt stagger 12 ticks (NAIL_TRAP_INTERRUPT_TICKS), collapse 6 ticks (NAIL_TRAP_COLLAPSE_TICKS).
- After impact the trap embeds one ordinary nail at depth 1 (fresh `ProjectJjkNailEntity` via `prepare` + `attachToEntity` in `NailTrapRuntime.java:184-187`; embed-depth storage defaults to 1 in `ProjectJjkNailEntity.java:527`).

Mega Nail (`ProjectJjkNobaraProfile.java:68-88`, `ProjectJjkMegaNailRuntime.java`, lifecycle in `ProjectJjkNailEntity.java:382-429`):

- Timing is entity-driven, not a fixed delay/retry: the cast gathers anchors for `MEGA_GATHER_TICKS = 14`, then the mega-nail entity charges for `MEGA_CHARGE_TICKS = 16` (scale grows 0.6 → 2.6) and launches; flight ends on impact or after `MEGA_NAIL_FLIGHT_TIMEOUT_TICKS = 60`. The legacy `MEGA_NAIL_CHARGE_TICKS = 24` constant is not the live timing owner, and there is no retry.
- Damage is pure per-nail base capped: `megaNailDamage(weight) = min(4.0 × depthWeight, 42.0)` — per-nail base equals Enlarge per-nail damage (`MEGA_NAIL_DAMAGE_PER_NAIL = HAIRPIN_ENLARGE_DAMAGE_PER_NAIL`, Profile:65/70), hard cap MEGA_NAIL_DAMAGE_CAP = 42.0 (Profile:72); ResonantMomentum multiplies separately at the impact site (`ProjectJjkMegaNailRuntime.java:119-122,149`).
- Knockback: `min(1.9 + 0.2 × nailCount, 3.0)` — base is Hairpin base (`MEGA_NAIL_KNOCKBACK_BASE = HAIRPIN_KNOCKBACK = 1.9`, Profile:22/84), per-nail bonus equals Hairpin explosion knockback (0.2, Profile:57/86), cap 3.0 (Profile:88); applied as a physical shove scaled ×0.55 with a small upward lift (`ProjectJjkMegaNailRuntime.java:127-131,153-158`).
- Impact stagger is HEAVY: `HEAVY_STAGGER_TICKS = 14` (Profile:127), applied before the shove (`ProjectJjkMegaNailRuntime.java:152`).
- Flight speed ×1.3 over base launch speed (`MEGA_NAIL_SPEED_MULTIPLIER`, Profile:78).
- The cast atomically discards the consumed nails (ENLARGE directional cue per nail) and consumes the target's marks plus the glowing mark; the strike resolves entity-hit / block-hit / miss-timeout, passing through along the frozen cast direction (`ProjectJjkMegaNailRuntime.java:82-105,140-177`).
- Nail selection requires `NailAnchorRegistry.anchorsOnTarget` for the caster and aimed target; the selected anchors are consumed at cast t0.

Target ESP overlay (client-only): ARCHIVED 2026-09-09 — the whole screen-space target HUD was moved to `archive/combat-hud-v1` and unregistered: the `NobaraEspState` per-2-tick scan (`register()`), the `NobaraTargetHud` VfxDirector contribution (`nobara_target_hud`), pure geometry `NobaraTargetLayout`, pure animation `NobaraTargetAnim`, and `NobaraEspRanks` classification, plus their JUnit contracts (files and restore steps in the archive README). Nail gameplay — embedding, resonance, traps, mega nail, glowing target marks, the `ownedByLocal` accent pulse in `ProjectJjkNailRenderer` — is untouched. World-to-screen projection remains as the shared pure helper `ui.WorldToScreen`.

Bound Remnant stores a FLESH / TOKEN / CURSE visual type (`RemnantVisualType.java:11-31`, classified as CURSE when curse-tagged, else FLESH for animals and TOKEN otherwise).

Server systems own targeting, resources, damage, stagger, Black Flash, trap state, bound-remnant extraction and ritual state, and chain scheduling. Client systems own animation and VFX only.
