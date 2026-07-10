# Nobara Combat Expansion

← [[Nobara-overview]] · [[Nobara-runtime-flow]] · [[Nail-entity-lifecycle]] · [[Target-marks-and-resonance]]

## Current contract

- `ProjectJjkNailEntity` is the persistent carrier for prepared, flying, entity-anchored, block-anchored, and registered runtime-object nails.
- Anchors use stable UUID/object identity. Block anchors also persist dimension and hit face. Missing entities, unloaded chunks, and temporarily unregistered runtime resolvers are unavailable rather than removed; confirmed death/final removal, explicit runtime removal, incompatible block replacement, or Hairpin consumption removes a nail.
- Holding the nail item creates one real server nail every 10 use ticks, capped at eight.
- Enlarge and Boom enumerate concrete owned nails. `jujutsumod:hairpin` is tagged `minecraft:bypasses_cooldown`, so each nail records damage independently.
- Hammer LMB is server-routed between prepared-nail launch, embedded-nail drive, horizontal sweep, and overhead attack. Repeated LMB inside the exact server window attempts Black Flash.
- The Fabric attack callback suppresses vanilla entity damage while a Nobara hammer is used, preventing a second hit beside the authoritative hammer runtime.
- Explicit `CurseLink` objects are the only source of self resonance. One link is automatic; multiple links require selection and a second Shift+R confirmation.

## Shared systems

| Contract | Responsibility |
|---|---|
| `NailAnchor` / `NailRuntimeAnchorRegistry` | Stable attachments and runtime-object extension point |
| `NobaraActionTimeline` | Shared impact/recovery/Black Flash timing |
| `BlackFlashWindow` / `BlackFlashFocus` | Exact timing and reusable focus state |
| `CombatStagger` | Action interruption without potion effects |
| `CurseLinkRegistry` | Explicit source-owned cursed-technique relationships |

## Initial balance

All gameplay values live in `ProjectJjkNobaraProfile`: Enlarge `4` per nail, Boom `3` per nail, doll Resonance `28`, self resonance `6/18`, and Black Flash `1.75x`. They are initial manual-QA tuning values.

## Presentation

Transient presentation remains `VfxCue -> VfxDirector -> NobaraVfxRecipes`. Hammer actions reuse full-body GeckoLib action animations and the director-owned first-person pose channel. Persistent nails remain in `ProjectJjkNailRenderer`.

Dedicated GeckoLib clips now cover horizontal, overhead, nail launch, embedded drive, doll strike, self resonance, and Black Flash. Focus persists as a player tag and is synchronized to the local client. Self resonance uses its own armor/effect/enchantment/resistance-bypassing damage type and only propagates after the self hit succeeds.

---
tags: #jujutsumod #nobara #combat #black-flash #curse-link
