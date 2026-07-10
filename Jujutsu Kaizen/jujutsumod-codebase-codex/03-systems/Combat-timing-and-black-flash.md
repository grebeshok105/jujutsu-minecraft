# Combat Timing and Black Flash

← [[00-MOC]] · [[Nobara-overview]] · [[Nobara-runtime-flow]] · [[Nobara-combat-expansion]]

## Server timing contract

`NobaraActionTimeline` centralizes an action's impact tick, recovery end, and Black Flash input bounds. The current horizontal timeline impacts at tick 3 and recovers at 8; overhead impacts at tick 8 and recovers at 16. Prepared launch has immediate impact semantics with 10-tick recovery. Doll strike and self resonance use a 14-tick windup and 20-tick recovery.

`BlackFlashWindow` is single-use and accepts only server game time within its inclusive `[opensAt, closesAt]` range. The profile currently opens at impact and closes two ticks later. It stores the target UUID, eligible impact kind, and base damage; it never allows a client to supply damage or a target.

**Source:** `NobaraActionTimeline.java:3-34`, `BlackFlashWindow.java:5-31`, `ProjectJjkNobaraProfile.java:53-62`. **Status:** VERIFIED.

## Hammer routing

| Priority | Result | Damage / effect |
|---:|---|---|
| 1 | active Black Flash window | multiplier bonus or flight amplification |
| 2 | nearby owned prepared nail | launch nail and open prepared-nail window |
| 3 | owned embedded nail on looked-at target | 4 damage, drive deeper, heavy stagger |
| 4 | alternating swing | horizontal 5 or overhead 8; light/heavy stagger |

The runtime preserves a per-player alternation state, tracks one pending delayed attack, and clears pending/window/alternation maps on server stop. A staggered player cannot start another Nobara action.

**Source:** `NobaraHammerCombatRuntime.java:25-180`, `CombatStagger.java`, `ProjectJjkNobaraActions.java:20-36`. **Status:** VERIFIED.

## Focus

Successful Black Flash grants `jujutsumod.black_flash_focus` as a server player tag. It is persistent with the player entity, sent on grant and join through `BlackFlashFocusPayload`, mirrored by `ClientBlackFlashFocus`, and cleared from the client on disconnect. This slice exposes `BlackFlashFocus.hasFocus` as an extension read API but defines no additional focus passive yet.

**Source:** `BlackFlashFocus.java:7-14`, `JujutsuNetworking.java:27-29`, `JujutsuClientNetworking.java:25-30`. **Status:** VERIFIED.

## Verification

`BlackFlashWindowTest` covers early/on-time/late input and one-shot consumption. Manual QA remains required for perceptible timing, multiplayer observation, and animation alignment.

---
tags: #jujutsumod #combat #black-flash #timing #verified
