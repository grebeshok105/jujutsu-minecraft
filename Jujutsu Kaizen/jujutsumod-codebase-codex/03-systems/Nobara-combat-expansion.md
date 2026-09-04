# Nobara Combat Expansion

Status: CURRENT (re-verified 2026-09-04 against source; line numbers below are that check).

Controls (`NobaraAbilityRouter.java:48-57`, keys in `client/.../input/JujutsuKeybinds.java:69-78`):

- R (PRIMARY) — directed Hairpin chain from the aimed owned nail/target (`ProjectJjkRitualRuntime.startDirectedHairpin`).
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

- Timing is entity-driven, not a fixed delay/retry: the mega-nail entity hovers at the gather point for MEGA_NAIL_CHARGE_TICKS = 24 (scale grows 0.6 → 2.6, `MEGA_NAIL_SCALE_START/END` at Profile:79-82), then launches; flight ends on impact or after MEGA_NAIL_FLIGHT_TIMEOUT_TICKS = 60 (`ProjectJjkNailEntity.java:392-421`, Profile:73-76). There is no 6-tick strike delay and no 40-tick retry.
- Damage is pure per-nail base capped: `megaNailDamage(weight) = min(4.0 × depthWeight, 42.0)` — per-nail base equals Enlarge per-nail damage (`MEGA_NAIL_DAMAGE_PER_NAIL = HAIRPIN_ENLARGE_DAMAGE_PER_NAIL`, Profile:65/70), hard cap MEGA_NAIL_DAMAGE_CAP = 42.0 (Profile:72); ResonantMomentum multiplies separately at the impact site (`ProjectJjkMegaNailRuntime.java:119-122,149`).
- Knockback: `min(1.9 + 0.2 × nailCount, 3.0)` — base is Hairpin base (`MEGA_NAIL_KNOCKBACK_BASE = HAIRPIN_KNOCKBACK = 1.9`, Profile:22/84), per-nail bonus equals Hairpin explosion knockback (0.2, Profile:57/86), cap 3.0 (Profile:88); applied as a physical shove scaled ×0.55 with a small upward lift (`ProjectJjkMegaNailRuntime.java:127-131,153-158`).
- Impact stagger is HEAVY: `HEAVY_STAGGER_TICKS = 14` (Profile:127), applied before the shove (`ProjectJjkMegaNailRuntime.java:152`).
- Flight speed ×1.3 over base launch speed (`MEGA_NAIL_SPEED_MULTIPLIER`, Profile:78).
- The cast atomically discards the consumed nails (ENLARGE directional cue per nail) and consumes the target's marks plus the glowing mark; the strike resolves entity-hit / block-hit / miss-timeout, passing through along the frozen cast direction (`ProjectJjkMegaNailRuntime.java:82-105,140-177`).
- Nail selection requires `isEmbedded()`, caster ownership, and `nail.anchor().stableId()` equal to the target UUID, scanned in the target's inflated bounding box (`ProjectJjkMegaNailRuntime.java:65-68`).

Target ESP (client-only): `NobaraEspState` scans every 2 client ticks (`REFRESH_INTERVAL_TICKS`, `NobaraEspState.java:26`) for embedded, locally-owned nails on living targets and folds them into per-target groups via pure `aggregate(List<NailView>)` (JUnit-covered in `NobaraEspStateTest`). The overlay is a screen-space HUD contribution — `NobaraTargetHud.render`, registered via `VfxDirector.registerHudContribution` in `NobaraClientDefinition.java:117`: a thin light bracket line just right of the target's silhouette with rows to its right (name, hairline divider, segmented HP bar, integer current/max, 16×16 nail icon with the local player's embedded-nail count, rank token from `NobaraEspRanks`). Rendering is vanilla `GuiGraphics` (`fill` / font / `blit`) — no SDF glass, no MSDF glyphs (`NobaraTargetHud.java:36-37,110-146`). Geometry comes from pure `NobaraTargetLayout` (no Minecraft imports; distance scale `clamp(9/depth, 0.75, 1.25)`), animation from pure `NobaraTargetAnim` (3-tick appear, 6 px slide-in, nail-count pop; HP chaser), and world-to-screen projection from the import-free pure helper `ui.WorldToScreen` (JUnit-covered). Anchors derive from the target entity's bounding box (chest fraction 0.60), never from a nail position. `NobaraEspRanks.rankKey` classifies players by roster grade key and mobs by max-health thresholds (≥100 special grade, ≥40 rank1, ≥20 rank2, else rank3; JUnit-covered in `NobaraEspRanksTest`).

Bound Remnant stores a FLESH / TOKEN / CURSE visual type (`RemnantVisualType.java:11-31`, classified as CURSE when curse-tagged, else FLESH for animals and TOKEN otherwise).

Server systems own targeting, resources, damage, stagger, Black Flash, trap state, remnant progression, and chain scheduling. Client systems own animation and VFX only.
