# Nobara Gameplay Research Synthesis

Status: working synthesis for the next gameplay spec.

Sources:

- `docs/research/sources/2026-07-07-nobara-gameplay-design.md`
- `docs/research/sources/2026-07-07-minecraft-fabric-combat-system.md`
- `docs/research/sources/2026-07-07-fabric-ability-system-architecture.md`
- Existing VFX slice and networking code in `src/main/java/jujutsu/mod/network` and `src/client/java/jujutsu/mod/client/fx`

## Core Direction

Nobara must not play like a sword with particles. Her gameplay should be a staged ritual-combat loop:

1. Set up nails and pressure space.
2. Force a risky contact or good angle.
3. Harvest or apply a link/mark.
4. Disengage and punish pursuit.
5. Execute through Hairpin or Resonance.

The fantasy is "aggressive tactical execution", not raw DPS. The opponent should feel escalating pressure: every nail, fragment, mark, or placed doll narrows their safe choices.

## Design Pillars

- **Server truth first.** Client input asks; server validates state, cost, range, cooldown, and target.
- **Action phases.** Abilities need startup, active, recovery, and cooldown. No instant high-impact button.
- **Readable threat.** Strong actions must telegraph through VFX/SFX/state, especially in PvP.
- **Anchored combat.** Nails and dolls are physical/positional commitments, not invisible global variables.
- **Unique Nobara logic stays local.** Generic ability lifecycle is reusable; Resonance's soul-link rules should live in Nobara code until a second character proves the abstraction.
- **No huge framework first.** Build the smallest real gameplay slice that proves input, server state, hit detection, damage, sync, and VFX.

## Canonical Translation

### Straw Doll Technique

Research frames this as a four-phase loop:

- Harvest: risky close-range hammer contact or special condition produces a target link.
- Binding: link + straw doll becomes a bound proxy.
- Placement: bound doll can be carried or placed, with placed doll being stronger but vulnerable.
- Execution: hammer strike on proxy triggers Resonance.

For the first playable slice, do not implement the full doll/proxy economy yet. Start with a simpler "Nail Mark" path so Hairpin becomes real gameplay. Then add bound doll/Resonance once the combat foundation is stable.

### Hairpin

Hairpin should be the first gameplay bridge because the VFX already exists.

Mechanically:

- Nails become server-authoritative anchors.
- Hairpin detonates nearby anchors.
- Server computes area damage/knockback once.
- Server broadcasts the existing `HairpinFxPayload` so the cinematic layer stays client-only.

Hairpin differs from Resonance:

- Hairpin: area/space control, visible anchors, avoidable by movement and anchor destruction/timeout.
- Resonance: single-target execution through a harvested link, stronger setup cost, can bypass line of sight, heavier counterplay requirements.

## Combat Foundation Implications

The combat system should avoid vanilla flatness by adding:

- Ability lifecycle: `WINDUP -> ACTIVE -> RECOVERY -> COOLDOWN`.
- Server-side player combat state.
- Cooldown/resource checks on server.
- Hit patterns beyond vanilla raycast:
  - ray/swept ray for hammer and thrown nails;
  - sphere/AABB for Hairpin detonation;
  - later, proxy lookup for Resonance.
- Lightweight hit feedback:
  - damage result;
  - small stagger/knockback where safe;
  - existing cinematic camera/screen effects for Hairpin.

Defer full poise, combo chains, rollback/lag compensation, block destruction, and broad player animation integration until the first Nobara loop actually works.

## First Gameplay Slice

Recommended first slice:

1. **Ability request path**
   - Client keybind for Hairpin or item use path if keybind is not ready.
   - C2S `AbilityRequestPayload`.
   - Server validation and cooldown.

2. **Server combat state**
   - Minimal per-player state: cooldowns, active phase, active nail anchors.
   - Keep state in plain server manager first unless Fabric Attachment API is verified and simple in this project version.

3. **Nail anchor gameplay**
   - Use existing `hairpin_nail` item.
   - First implementation can place anchors with right-click/raycast rather than a full projectile entity.
   - Anchor data: owner UUID, position, created game time, TTL, maybe attached target UUID later.
   - Hard limit per player, e.g. 8-12 initially, not 30.

4. **Hairpin execution**
   - Server gathers anchors in radius.
   - Computes one combined damage/knockback pass using entity queries.
   - Removes consumed anchors.
   - Sends existing `HairpinFxPayload` using anchor-derived positions.
   - Existing `/jujutsu hairpin` remains a debug preview, but real gameplay should no longer depend on it.

5. **Diagnostics**
   - `/jujutsu debug hairpin true|false` remains.
   - Add a gameplay debug command to print active anchors/cooldowns.
   - Tests should cover cooldown validation, anchor limits, and Hairpin target selection math.

## Near-Term Deferrals

Defer until after Hairpin is playable:

- Full Straw Doll item/block.
- Bound Doll data component.
- PvP blood/fragment drops.
- Resonance through walls.
- Soul damage bypassing armor.
- Black Flash timing window.
- Player animation dependency.
- Complex persistence/copy-on-death attachment policies.

## Technical Cautions

The research docs mention Data Components, Attachment API, and several Fabric networking details. Before implementation, verify exact Fabric 1.21.8 API names against local dependencies and official docs because these APIs changed across 1.20.5-1.21.x. The existing project already successfully uses:

- `CustomPacketPayload`
- `StreamCodec`
- `PayloadTypeRegistry.playS2C()`
- `ServerPlayNetworking`
- `ClientPlayNetworking`

Prefer extending this proven pattern before introducing new API surface.

Also keep package names consistent with the repo: use `jujutsu.mod...`, not the `net.jujutsumod...` placeholder package used in the research.

## Proposed Next Spec

Write a focused spec named something like:

`docs/superpowers/specs/2026-07-07-nobara-hairpin-gameplay-slice.md`

That spec should lock:

- whether the first trigger is keybind, item use, or both;
- anchor placement rules;
- Hairpin damage/knockback formula;
- cooldown/resource model for the first jar;
- debug commands;
- exact tests and in-game verification.

This is enough research for the next step. More deep research is not needed before the first gameplay slice; implementation will need targeted API verification while coding.
