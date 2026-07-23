# Nobara Hairpin Cast Polish

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Status: accepted for implementation.

## Goal

Make Nobara Hairpin feel like a heavy, cinematic hammer strike that sends four prepared nails into a target, instead of a generic blue projectile volley.

## Scope

- Keep the existing item-first flow.
- Keep server-authoritative target resolution, damage, knockback, cooldown, and impact timing.
- Do not add temporary nail entities or per-nail world hitboxes in this pass.
- Do not add a new visual dependency.
- Do not run automated in-game smoke tests; the user will verify in-game manually.

## Gameplay Flow

1. Right-click `jujutsumod:hairpin_nail` to prepare up to four nails.
2. Prepared nails appear in a readable row in front of the player.
3. Right-click `jujutsumod:straw_doll_hammer` while the row is active.
4. The server resolves the current aim:
   - if a living target is under the aim sweep, nails attach visually to that target point;
   - if no entity is targeted, nails fly to the aimed block or miss point.
5. One hammer strike launches every prepared nail at once.
6. Each nail travels with a blue cursed-flame aura, not only a blue line.
7. Arrival produces nail impact beats before the existing blood-black Hairpin bloom.

## Visual Direction

- Prepared state: four steel nails in a horizontal row in front of the caster, with restrained blue flame licking around each nail.
- Launch: a short camera/FOV punch and screen focus effect sell the hammer hit.
- Flight: each nail has a bright blue-white core, darker blue outer flame, and flickering flame tongues around the path.
- Impact: rapid per-nail sparks plus the existing blood-black cinematic bloom.
- Screen treatment: use existing overlay/camera hooks for vignette, focus lines, FOV pull, and flash. A true post-processing blur shader is deferred unless the current hooks are not enough.

## Sound Direction

- Prep uses a small metallic ready sound.
- Hammer launch uses a heavier impact sound at the caster.
- Nail flight/ignition uses a sharper cursed flame sound near the target.
- Nail arrival uses short impact ticks before bloom.
- Existing OGG files may be reused and layered in this pass; new generated/recorded sound assets are deferred unless necessary.

## Implementation Notes

- Extend payloads only if the client needs stable prepared nail positions without recomputing them from the player every frame.
- Prefer deterministic helper methods in `HairpinGameplayService` for nail row placement and target-facing vectors.
- Keep state non-persistent and compatible with the existing debug commands.
- Keep the implementation small enough to remain the template for the next character slice.

## Acceptance Criteria

- In game, the prepared nails read as four objects in a row in front of Nobara.
- Hammer use sends all prepared nails together.
- Aimed entity targets receive visually attached/centered impacts; no-target casts still fly to the aim point.
- Nail flight reads as blue cursed flame.
- Launch and impact have noticeably more weight from camera, overlay, and layered sounds.
- Project build and relevant non-gameplay tests pass, without running automated client smoke.
