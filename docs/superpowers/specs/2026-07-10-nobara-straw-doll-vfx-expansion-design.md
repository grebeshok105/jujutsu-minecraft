# Nobara Straw Doll and VFX Expansion

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Status: approved for implementation on 2026-07-10.

## Goal

Replace the nail flame treatment, give Nobara's existing actions substantially more cinematic weight, and add a canon-forward Straw Doll Resonance loop with an original model and effects. Keep gameplay server-authoritative and route every transient visual through the existing VFX Core.

## Confirmed Baseline

- `R` is working as designed. Hairpin Enlarge requires a living target with an active ordinary nail mark under the crosshair.
- `B` searches a wider area for embedded nails and therefore appears more permissive.
- Nobara already emits typed `VfxCue` events that are received by `VfxDirector` and composed in `NobaraVfxRecipes`.
- The existing ProjectJJK doll model, animation, and texture are research material only and must not be used in the runtime result.

## Nail Visual Language

Prepared and flying nails must read as forged metal carrying compressed cursed energy, not as burning blue torches.

- Keep the real nail item model clearly visible.
- Replace the broad lightning/flame envelope with a narrow cyan-white rim, intermittent orbiting slivers, and short pressure bands.
- Prepared nails use a restrained pulse and small inward-moving motes.
- Launched nails gain a directional tail, brighter tip compression, and sparse streaks aligned with travel.
- Embedded nails lose the flight aura and retain only a faint readable mark pulse.
- Remove `SOUL_FIRE_FLAME` and flame-like ignition composition from Nobara recipes where it causes the same visual misread.

## Cinematic VFX Upgrade

The existing cue/director/recipe contract stays unchanged. New reusable capability is added behind the director only where current channels are insufficient.

### Camera and screen feel

- Split camera response into named launch, heavy impact, explosion, and ritual profiles instead of scaling the same impulse everywhere.
- Hammer launch briefly pulls the FOV inward, then releases it.
- Nail impact kicks outward and settles quickly.
- Enlarge and Resonance use a sharp compression beat followed by a heavier rebound.
- Explosion uses several short high-frequency impulses rather than one long sway.
- Clamp cumulative camera and FOV motion so stacked nails remain readable and do not permanently disorient the player.

### Blur and overlays

- Add an internal `VfxPostProcessChannel` owned by `VfxDirector`.
- Use Minecraft 1.21.8's public `GameRenderer.processBlurEffect()` for short blur pulses; do not add Satin, Veil, Photon, or another dependency.
- Invoke blur only during world rendering and only for the local viewer within the recipe's proximity budget.
- If blur is unavailable or the call cannot be made safely, all recipes retain a shaderless fallback using HUD focus, vignette, camera, FOV, particles, and world geometry.
- Add no generic public shader-authoring API and no post-effect resource copied from ProjectJJK.

### World composition

- Enlarge: inward compression rings, a momentary held frame, then radial cyan-white blades and metal fragments.
- Explosion: compact implosion core, staggered shell breaks, sparks, dust, and short aftershock rings.
- Hammer send: a readable contact arc and four aligned launch streaks.
- Resonance: ritual binding lines at the doll, a nail-strike flash, then a remote target bloom with a dark center and cyan-white fracture lines.

## Canon-Forward Straw Doll Loop

Research source: `docs/research/2026-07-10-nobara-straw-doll-canon.md`.

Canon requirements preserved:

- The doll is a hand-held ritual proxy, not a summon, pet, turret, or permanent block.
- A meaningful physical remnant establishes the target link.
- Hammer and cursed-energy nail enact Resonance through the proxy.
- Once linked, the strike can reach a loaded living target without line of sight.
- Hairpin remains a separate nail-mark detonation technique.

Minecraft adaptations, not claims of canon:

- Two successful ordinary nail hits by the same Nobara produce one target remnant.
- A successful ritual consumes the remnant and one nail, while the doll and hammer remain reusable.
- The target must be alive, loaded, in the same dimension, and within a finite server range.
- The physical hammer wind-up is an interruptible/readable telegraph.

### Player flow

1. Select Nobara and receive the existing hammer/nails plus a reusable straw doll.
2. Hit a living target twice with ordinary nails. The second hit drops one bound target remnant at the wound and resets remnant progress for that target.
3. Pick up the remnant. Its bound target identity is stored server-side in a typed item data component.
4. Hold the straw doll in the off hand and the hammer in the main hand. Keep at least one nail in inventory.
5. Shift-right-click with the hammer to begin Resonance. The server selects one valid bound remnant from inventory, validates the target, and starts the ritual wind-up.
6. During the wind-up the doll is rendered in the off hand, binding VFX converge on it, and the hammer prepares the strike.
7. At impact the server consumes one nail and the selected remnant, applies remote damage and weakness, clears the ritual link, and emits local doll plus remote target cues.
8. Invalid, dead, unloaded, cross-dimension, or out-of-range targets do not consume resources and return a localized action-bar failure.

The old two-step mark-only Shift+hammer binding path is removed. Nail marks continue to power Hairpin Enlarge and Explosion only.

## Original Doll Asset

Create the runtime model and texture from scratch in Blockbench.

- Compact hand-sized humanoid silhouette built from uneven bundled straw.
- Cylindrical head, split legs, asymmetric arm angles, rough ends, and a clear torso strike area.
- Original proportions, binding layout, UVs, texture marks, and animation timing.
- Muted olive-yellow straw, dark charcoal-green bindings, subtle cursed cyan accents only during effects.
- Animations: restrained idle sway, ritual raise/bind, hammer impact recoil, and short energy-release shudder.
- Source project is stored with the mod's source assets; exported runtime geometry, animation, and texture are placed under the mod namespace.
- Do not copy or trace ProjectJJK geometry, animation values, texture, manga panels, or anime frames.

## Architecture

### Server gameplay

- A focused remnant component carries the bound target UUID and dimension identity.
- A small server runtime owns per-caster/per-target two-hit progress and pending ritual casts.
- Cast resolution revalidates selection, items, target identity, dimension, range, and life state before consuming resources or applying damage.
- Runtime state clears on logout, death, and level lifecycle consistently with existing Nobara transient state.

### Client presentation

- Add Straw Doll cue IDs to `NobaraVfxIds` and recipes to `NobaraVfxRecipes`.
- Use the existing world, particle, sound, HUD, camera/FOV, and first-person channels.
- Add only the internal post-process channel and the doll entity/item renderer needed by the approved presentation.
- Persistent doll geometry is rendered by its actual item/entity renderer; transient ritual effects remain recipes.

### Networking

- The existing Shift+hammer server item-use path starts the ritual; no new C2S ability packet is required.
- Server-confirmed stages emit typed cues through `JujutsuNetworking`.
- No client recipe may mutate targets, consume items, or decide ritual success.

## Error Handling

- Never consume a remnant or nail before final server validation.
- Prevent duplicate remnants from the same hit threshold and duplicate pending rituals from input spam.
- Cancel pending casts cleanly when the caster dies, logs out, changes dimension, stops holding the required tools, or the target becomes invalid.
- Missing client recipes, model resources, or blur availability degrade visuals without changing gameplay.

## Verification

- Pure tests cover remnant threshold/reset, ritual validation, range/dimension rules, resource consumption decisions, and VFX registration/timing.
- Project sanity checks cover item/component/entity/model/texture registration and forbid use of ProjectJJK doll runtime assets.
- Run focused tests after each slice, then `gradlew.bat check --no-daemon` and `gradlew.bat build --no-daemon -x test`.
- Inspect exported Blockbench asset renders before packaging.
- Build and copy the final runtime JAR to `D:\Games\instances\Jujutsu\mods`, then compare SHA-256.
- Do not claim gameplay feel or multiplayer behavior from compilation. The user performs manual in-game QA; no Computer Use or UI automation is allowed.

## Acceptance Criteria

- Prepared and flying nails no longer read as blue fire.
- Hammer sends, Enlarge, Explosion, and Resonance have distinct camera/FOV/overlay/world compositions with stronger impact.
- A short proximity-gated blur pulse exists with a reliable shaderless fallback.
- Straw Doll requires a physical target remnant, reusable original doll, hammer, and one nail.
- Ritual damage is server-authoritative, remote without line of sight, and bounded to valid loaded same-dimension targets.
- The original doll model, texture, animations, and dedicated ritual effects render from `jujutsumod` resources.
- All transient effects use VFX Core and no parallel effect manager is introduced.
- Verification passes and the exact built JAR is installed for manual QA.
