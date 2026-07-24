# Aoi Todo — Boogie Woogie vertical slice

Status: APPROVED IMPLEMENTATION DESIGN  
Target branch: `feat/todo-boogie-woogie`  
Scope: second playable character, minimal stable multiplayer-safe kit.

## Product goal

Add Aoi Todo as the second selectable vessel and use the work to prove the mod can support more than one character without introducing a parallel character, cooldown, networking, targeting, or VFX architecture.

This slice contains one active technique: **Boogie Woogie**. It swaps Todo with one valid living target. It does not include a model, a finished animation, external anime sounds, a cursed-energy resource, external-target swaps, rhythm gameplay, or unique combo attacks.

## Research basis

Canonical Boogie Woogie is an instant position exchange associated with Todo's clap activation. Canon does not provide a fixed game radius, cooldown, universal line-of-sight rule, or Minecraft-safe placement rules. Those values are mod design choices.

- https://jujutsu-kaisen.fandom.com/wiki/Boogie_Woogie
- https://gamerant.com/jujutsu-kaisen-todos-boogie-woogie-technique-explained/
- https://mappings.dev/1.21.8/net/minecraft/server/level/ServerPlayer.html
- https://maven.fabricmc.net/docs/yarn-1.21.8%2Bbuild.1/net/minecraft/world/TeleportTarget.html
- https://maven.fabricmc.net/docs/yarn-1.21.8+build.1/net/minecraft/world/CollisionView.html
- https://maven.fabricmc.net/docs/yarn-1.21.8+build.1/net/minecraft/world/border/WorldBorder.html

## Agreed first implementation

### Identity and selection

- Character id: `todo`.
- Display name: Aoi Todo.
- Todo appears in the existing ClickGui character roster.
- Todo uses the existing persistent `CharacterPlayerState` attachment. Selection survives death, reconnect, world restart, and dimension changes.
- Todo has no starter items in this slice. Vanilla melee remains the base weapon path.
- Todo intentionally has no custom GeckoLib model or animation asset yet. The existing vanilla player model remains active.

### Base profile

All values live in `TodoProfile`.

| Parameter | Value | Reason |
|---|---:|---|
| Vanilla melee damage multiplier | 1.50 | Heavy close-range identity |
| Attack speed multiplier | 0.85 | Slightly slower attacks |
| Stagger duration multiplier | 0.50 | Higher resistance, not immunity |
| Movement speed modifier | 0.00 | Normal movement |
| Boogie Woogie range | 24 blocks | User requirement |
| Boogie Woogie cooldown | 60 ticks | 3 seconds |
| Safe horizontal search | 1 block | Small local fallback only |
| Safe upward search | 3 blocks | Escape low obstruction without long relocation |

Damage, attack speed, and knockback-resistance-compatible behavior use vanilla attributes. Stagger resistance is applied through the existing `CombatStagger` duration calculation rather than a second stagger system.

### Input and networking

- Reuse the existing primary-technique key: **R**.
- The client sends a generic `CharacterAbilityPayload(PRIMARY)` only when the confirmed server cooldown is ready.
- Server-side `CharacterAbilityExecutor` dispatches by selected character. The current generic path has one real consumer: Todo.
- Nobara keeps its proven legacy action payload path in this slice; migration is deferred until a third character proves it worthwhile.
- The server alone selects and validates the target, starts cooldown only after success, performs the teleport, and emits sound/VFX cues.

### Target selection

Boogie Woogie calls the shared `TargetResolver` with Todo-specific validation.

Allowed: other players and ordinary living mobs.  
Rejected: Todo, dead entities, spectators, armor stands, removed/technical entities, passengers, vehicles, leashed entities, different-world entities, out-of-range entities, blocked line-of-sight candidates, and invalid positions.

Selection is server-side from Todo's eye ray. Candidates must be before the first solid block hit, inside the 24-block reach, inside the ray sweep, and are ranked by closest perpendicular distance to the crosshair, then depth along the ray.

### Atomic safe swap

1. Snapshot both entities: world, position, yaw, pitch, head yaw, velocity, and fall state.
2. Require the same `ServerLevel`.
3. Resolve Todo-at-target and target-at-Todo independently.
4. Test the complete destination bounding box against world bounds, world border, loaded chunk availability, block collision, non-participant entity collision, a safe floor, and a small deterministic offset/upward fallback search.
5. Abort entirely if either destination is invalid.
6. Teleport through the mapped server teleport API; never mutate player coordinates directly.
7. Restore each entity's own yaw, pitch, head yaw, velocity, and reset fall distance.
8. Roll back Todo if the second authoritative teleport unexpectedly fails.
9. Emit cooldown, sounds, and VFX only after the complete swap succeeds.

The ability deals no direct damage and does not exchange velocity, rotation, world, inventory, or entity identity.

### VFX, sound, and animation hook

All transient visuals use the existing `VfxCue → VfxDirector → TodoVfxRecipes` pipeline.

- Cue id: `todo/boogie_woogie`.
- At both pre-swap origins: a compact purple/cyan cursed-energy flash using existing VFX channels and vanilla dust particles.
- Between origins: a short world-channel ribbon/trail through the same cue data, not tick polling.
- Sounds: vanilla short clap-like and teleport-like sounds, played by the server at the two origins. No protected anime audio is added.
- `TodoAnimationHooks.BOOGIE_WOOGIE` exposes the future hook id `ability.boogie_woogie`. It is a safe no-op until a Todo GeckoLib model/animation is added.

### Todo melee and Black Flash

Todo retains vanilla melee mechanics with server-applied attribute modifiers. A small shared bridge reuses the existing Black Flash values, focus state, forced-debug state, stagger behavior, and VFX for accepted direct Todo melee hits. Todo gets no new combo system or Todo-specific Black Flash mechanics.

## Explicitly deferred

- Swap between two external targets.
- Cursed anchors, thrown-object targets, repeated high-rate swaps, vibraslap/rhythm mechanics, tempo meter, Todo items, custom HUD, model, animation JSON, domain expansion, and boss content.

## Verification

Automated coverage must include target rejection, cooldown replay prevention, safe-position behavior, atomic rollback, velocity/rotation preservation, persistence/selection, and VFX/cue registration.

Manual sandbox smoke must prove Todo selection, R activation against a valid target, blocked-position cancellation, cooldown behavior, player↔mob swap, player↔player swap when available, selection persistence, and no Nobara regression.
