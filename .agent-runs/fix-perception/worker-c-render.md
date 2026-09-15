# Brief C — Render leak: shadow + F3+B hitbox for non-perceivers
- agent: worker | run: fix-perception | status: done

## Summary
Fixed without a mixin. javap on the mapped 1.21.8 client jar showed both leak
paths are gated on `EntityRenderState.isInvisible`: the shadow additionally
needs `getShadowRadius(state) > 0`, and the F3+B hitbox is only extracted when
`!state.isInvisible`. The on-fire flame (`displayFireAnimation`) ignores
`isInvisible`, so it is cleared too. `CursedSpiritRenderer.extractRenderState`
now flags the state via the new `CurseRenderGate.hiddenFromView` (the gate's
negation — single decision point). 8/8 CurseRenderGate tests green; red-mutant
proven. Commit `fbc3a65`.

## Detail
javap evidence (clientonly jar, loom mappings 1.21.8):

`EntityRenderDispatcher` private `render(S, double,double,double, PoseStack,
MultiBufferSource, int, EntityRenderer)`:
- shadow block requires, in order: `options.entityShadows()`,
  `shouldRenderShadow`, `!state.isInvisible`,
  `renderer.getShadowRadius(state) > 0`, `strength > 0`
  (bytecode offsets 128–229; `getfield EntityRenderState.isInvisible` at 155,
  `invokevirtual EntityRenderer.getShadowRadius` at 164).
- hitbox block at offset 259: `if (state.hitboxesRenderState != null)
  renderHitboxes(...)`.

`EntityRenderer.createRenderState(T, float)` tail (offsets 838–889):
`hitboxesRenderState` is populated only when
`dispatcher.shouldRenderHitBoxes() && !state.isInvisible &&
!minecraft.showOnlyReducedInfo()` — i.e. setting `isInvisible` during
`extractRenderState` (which runs just before this check) suppresses the F3+B
hitbox entirely. No renderer-side hook for hitboxes exists, but none is needed.

`EntityRenderer` per-state hooks found: `protected float getShadowRadius(S)`
(default returns `shadowRadius` field), `getShadowStrength(S)`. Not needed —
`isInvisible` covers shadow + hitbox in one flag.

`displayFireAnimation` → `renderFlame` (dispatcher offsets 65–98) is NOT gated
on `isInvisible`; cleared explicitly so a burning hidden spirit leaves no
flame sprite.

`EntityRenderState.isInvisible` and `.displayFireAnimation` are public fields
(javap -p confirmed).

### Changes
- `CurseRenderGate.java`: added `hiddenFromView(Entity)` and
  `hiddenFromView(boolean)` — pure negation of `shouldRender`, javadoc explains
  the dispatcher-side leak needs a positive "hidden" decision.
- `CursedSpiritRenderer.java:58-67`: in `extractRenderState`, after
  `curseSubject` is set, `if (CurseRenderGate.hiddenFromView(state.curseSubject))
  { state.isInvisible = true; state.displayFireAnimation = false; }`.
- `CurseRenderGateTest.java`: +3 tests — `nonSubjectIsNeverHiddenFromView`
  (real call), `hiddenFromViewIsShouldRenderNegated` (source-pin),
  `extractRenderStateSuppressesLeakPaths` (source-pin on all three statements).

## Evidence
- `javap -c -p net.minecraft.client.renderer.entity.EntityRenderDispatcher`
  and `EntityRenderer` on
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly/1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2/...jar`.
- `./gradlew compileClientJava compileTestJava test --tests '*CurseRenderGate*'`
  → BUILD SUCCESSFUL; `TEST-...CurseRenderGateTest.xml`: tests="8" failures="0".
- Red-mutant: `state.isInvisible = true` → `false` ⇒
  `extractRenderStateSuppressesLeakPaths() FAILED`, BUILD FAILED in 5s; reverted,
  green again.
- Commit `fbc3a65` on `fix/review-perception` — only the 3 allowed files staged.

## Rejected/Open
- Rejected: `getShadowRadius(S)` override returning 0 — works for the shadow but
  does nothing for the hitbox; `isInvisible` covers both.
- Rejected: client mixin on `EntityRenderDispatcher.renderHitboxes` — proven
  unnecessary; the extract-time `isInvisible` gate is a cleaner seam.
- Scope note: `CursedSpiritAcidSpitRenderer` was outside allowed files; if it
  has a shadow/hitbox it may need the same flag pattern (likely shadowless
  projectile-style renderer — not verified).
- No in-game verification run (headless env); behavior rests on javap-verified
  dispatcher gates.
