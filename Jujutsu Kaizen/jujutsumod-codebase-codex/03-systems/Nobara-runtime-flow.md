# Nobara Runtime Flow

<- [[00-MOC]] | [[Nobara-overview]] | [[Nail-entity-lifecycle]] | [[Combat-timing-and-black-flash]] | [[Curse-links]] | [[Straw-Doll-resonance]]

**Source:** `src/main/java/jujutsu/mod/character/nobara/projectjjk/`

## Authority boundary

The client only sends a compact `NobaraActionPayload` or an explicit link selection. `JujutsuNetworking` queues the receiver on the server, and `ProjectJjkNobaraActions` rejects a non-Nobara selection or an active stagger before choosing a runtime. Damage, anchor lifecycle, timing, target validation, link validation, and VFX emission are server-owned.

```mermaid
flowchart LR
  C[Keybind or LMB] --> P[NobaraActionPayload]
  P --> S[ProjectJjkNobaraActions server gate]
  S --> H[Hammer / Hairpin / Self Resonance / Nail Trap]
  H --> G[Damage, anchors, links, stagger]
  H --> V[VfxCue]
  V --> D[Client VfxDirector recipe]
```

**Source:** `JujutsuNetworking.java:28-37`, `ProjectJjkNobaraActions.java:21-41`, `JujutsuClientNetworking.java:17-27`. **Status:** VERIFIED.

## Nail preparation and impact

`ProjectJjkNailItem` starts item use and calls `ProjectJjkNobaraRuntime.tickPreparing` on each server use tick. The profile converts hold duration to one initial nail plus one every 10 ticks, capped at eight. `ProjectJjkNailEntity` owns delayed flight, entity/block embedding, typed anchor persistence, and the ordinary nail-hit path. See [[Nail-entity-lifecycle]].

The ordinary impact path can create a target mark/remnant progression, but explosive and self-directed hits are excluded from that ordinary-hit rule. The distinct Straw Doll ritual is documented in [[Straw-Doll-resonance]].

## Hairpin

`ProjectJjkRitualRuntime.tryEnlargeMarkedTarget` requires a valid marked looked-at target and queues the selected owned embedded nails. Its tick path retries a temporarily unavailable anchor and drops only terminal entries.

`ProjectJjkRitualRuntime.startMassHairpin` gathers concrete owned nails, consumes their marks, and resolves Boom in staggered batches via `HairpinChainScheduler` after the configured delay. Both routes emit semantic VFX cues and apply independent `hairpin` damage per nail.

| Action | Initial damage | Timing | Source |
|---|---:|---|---|
| Enlarge (Directed) | 4 per nail | 20-tick delay | `ProjectJjkNobaraProfile.java:61-65`; `ProjectJjkRitualRuntime.java` (tryEnlargeMarkedTarget) |
| Boom (Mass) | 3 per nail | starts after 10 ticks, staged chain batches | `ProjectJjkNobaraProfile.java:32,53`; `ProjectJjkRitualRuntime.java` (startMassHairpin + HairpinChainScheduler) |

## Nail Trap

`NailTrapRuntime.tryPlace` validates placement range (8 blocks), requires 3 nails, and creates a triangular `NailTrap` at the aimed ground position. The trap arms after a delay, then collapses when a hostile entity enters the prism radius (6 blocks, 3 height). Collapse deals 15 damage and interrupts for 12 ticks. Traps expire after 600 ticks.

**Source:** `NailTrapRuntime.java`, `NailTrap.java`, `ProjectJjkNobaraProfile.java:41-48`. **Status:** VERIFIED.

## Hammer and Black Flash

The LMB request first consumes an active Black Flash window. Otherwise the runtime prefers a nearby prepared nail launch, then an embedded-nail drive on the looked-at target, then alternates horizontal and overhead hammer attacks. Delayed impacts use `NobaraActionTimeline`; a valid impact opens a 0..2 tick Black Flash input window. A successful second input adds only the multiplier bonus, applies heavy stagger, emits `BLACK_FLASH`, and grants a persistent player-tag focus synchronized to the local client.

The Fabric attack callback suppresses vanilla entity damage for the Nobara hammer, preventing a second vanilla hit beside the server runtime. See [[Combat-timing-and-black-flash]].

## Self Resonance

Shift+R routes through `SelfResonanceRuntime`. It reads explicit `CurseLinkRegistry` entries for the player: one link is used directly; zero reports failure; two or more send a menu payload and require a separate selected-link confirmation. At the windup impact, the server first applies the dedicated self-resonance damage to the caster. Only if that succeeds are linked loaded living participants damaged, heavily staggered, and sent target VFX. See [[Curse-links]].

## Runtime registration

`JujutsuMod.onInitialize` registers (in order): entities, data components, items, particles, sounds, effects, networking payloads, ritual runtime, straw doll runtime, nail anchor lifecycle, hammer combat runtime, action guard, self resonance runtime, nail trap runtime, server-stop curse link cleanup, commands, and forced black flash.

**Source:** `JujutsuMod.java:33-51`. **Status:** VERIFIED.

---
tags: #jujutsumod #runtime #nobara #verified
