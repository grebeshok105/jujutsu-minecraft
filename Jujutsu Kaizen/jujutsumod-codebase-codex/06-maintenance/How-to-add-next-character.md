# How to Add the Next Character

← [[00-MOC]] · AGENTS character workflow

## Recommended path (based on current code)

1. **Brief + numbers** (design doc first — AGENTS brainstorming gate)
2. Add `JujutsuCharacter` enum value (`JujutsuCharacter.java`)
3. Extend `CharacterSelectScreen` card + lang keys
4. In `CharacterSelectionManager.select`, branch loadout like Nobara’s `ensureStarterTools`
5. New package `character/<name>/` with:
   - Profile constants (like `ProjectJjkNobaraProfile`)
   - Runtime server methods
   - Items/entities only if needed
6. Register items/entities/sounds/particles in existing registry classes
7. Networking: reuse impulse pattern or add typed payload + client handler
8. Client VFX under `client/fx` / particles — no server render
9. Tests for pure math/state; build; runClient smoke

## Do expand

| Place | Why |
|---|---|
| Character enum + selection | single gate |
| Per-character package | isolation |
| Profile constants class | balance without magic numbers |
| JujutsuNetworking broadcast helpers | proven S2C pattern |

## Do not inflate early

| Place | Why |
|---|---|
| Giant universal Ability framework | AGENTS: prove kit first |
| Shared CE economy | not in current code; design decision |
| Mixins per character | avoid unless API blocked |
| Copying ProjectJJK grade system wholesale | different progression model |

## Pain points to expect

- Selection map is in-memory only (no disk persistence) — `CharacterSelectionManager` ConcurrentHashMap  
- Dual Nobara stacks (legacy Hairpin FX vs ProjectJJK items) — don’t add a third without cleanup  
- Asset volume for VFX grows fast  

**Status:** INFERRED process from code structure + AGENTS; no second character exists yet.

---
tags: #jujutsumod #maintenance
