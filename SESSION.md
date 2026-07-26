# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: feat/clickgui-drag-and-todo-fake-clap
- Base: main at 61dd084 (merge of PR #6)
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)". This file records only what changed on this branch. Documentation authority order is owned by AGENTS.md; asset and provenance policy is owned by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

## What changed on this branch

Two ClickGui fixes, the rest of the approved Todo plan, and two model defects.

- **ClickGui panel drag.** A `DragHandler` already existed, bound to middle mouse over the whole panel, and only tracked the cursor correctly at GUI scale 2 — the offset was divided by `Render2D.getScaleMultiplier()` while the handler accumulated raw mouse deltas. Left mouse now grabs the 38 px header band, the only part of the panel with no click target of its own; middle mouse keeps the whole panel. Motion comes from `mouseDragged` instead of polling GLFW, the offset is recomputed from the grab point rather than accumulated, and it is clamped so a drag cannot leave the handle unreachable. Release, close and reopen no longer leave a stuck grab. Position is session-only: the project has no UI-state persistence and none was added.
- **Vanilla crosshair while the menu is open.** It was drawn *over* the menu, not under it: `Gui.render` has no open-screen gate and the blit is only recorded into the GUI render state, rasterized last, while the ClickGui rasterizes immediately in `SdfRenderer.flush()`. Suppressed with a conditional `HudElementRegistry.replaceElement` on `VanillaHudElements.CROSSHAIR`. No mixin, nothing to restore on close.
- **Todo feint clap on `Shift+R`.** The server knows the cast is hollow from the first tick; it never starts a swap and cancels one. Both casts emit the clap through one extracted `emitClapPerformance` and read one shared `TodoSwapGates` truth table, so neither the presentation nor the refusals can drift apart. Independent cooldown slot; no gate requiring the real swap to be ready (offered, declined).
- **`TargetResolver` ranking.** The crosshair-proximity key was dead behind an exact-equality test on `hitDistance`. Ranking is now pierced-before-grazed, then depth for real hits or crosshair angle for aim-assist grazes, then always entity id — which also closes a tie being decided by entity iteration order.
- **Todo pair swap on `B`.** Two casts: mark a bystander, then swap the pair while Todo stays put. Distance is measured from Todo to each participant, never between them. STRICT placement, which finally gives that enum a call site.
- **Todo thrown marker.** A single-stack item consumed on throw, so the empty-hands rule stays absolute rather than becoming a whitelist. `R` falls back to a live mark only after the crosshair finds nothing. Two mark forms — a resting projectile, or a glow on a struck body — share one record and one release path.
- **Model.** Shoulder pads were parented to the opposite arms and swung out through the back at the clap contact frame; both are mirrored to their own side, and the contact keyframe no longer pushes the arms backward. `sleeve_R` sampled two texels outside the 128-wide atlas and shared six with `sleeve_L`; it moved to a clear block.
- **Localization.** Russian keys brought level with English (issue E5).

## Verification status

- `gradlew.bat build --no-daemon` — BUILD SUCCESSFUL, 27 verification programs green
- `python tools/audit_docs.py` — passing
- New coverage: `DragHandlerTest`, `TodoFakeClapTest`, `TodoPairSwapTest`, `TodoSwapMarkerTest`, plus three new `TargetResolverTest` cases for angle, distance and a stable tie-break
- In-game client smoke — **NOT run on this branch.**

Nothing in the test suite can construct a `ServerLevel`, so no test on this branch teleports anything or calls any `tryCast`. Three whole mechanics — the feint, the pair swap, the thrown marker — are covered only by pure logic and source-text contracts. Treat the build as proof of shape, not of behaviour.

## Must be checked in game before this is trusted

The full checklist is in docs/BUILDING_IN_SANDBOX.md. The four that matter most:

1. **Nobara targeting regression.** `TargetResolver` is shared by her hammer, nail launch and directed Hairpin. Its ranking changed; nothing automated covers her.
2. **The feint's honest tell.** A real swap teleports at cast time while palm contact is at 0.39 of the 0.72 s animation, so a feint is already distinguishable at t = 0 by the missing teleport. Delaying the swap to the contact frame was deliberately not done — it would change the kit's most safety-critical method and is outside the approved plan. This is the feint's open product question.
3. **Mark and marker leaks.** Both mark forms and the pair selection have several exit paths; a stuck glow or an orphan resting marker would only show in play.
4. **STRICT cancellation.** A pair or marker swap into an unsafe destination must cancel with nothing moved, never half-apply.

## Next product steps

1. Run the client-smoke checklist in docs/BUILDING_IN_SANDBOX.md, starting with the Nobara regression.
2. Decide the feint's contact-frame question above.
3. Add world/GameTest coverage for the swap runtimes and their rollback paths; nothing exercises them (E1/E8 in docs/KNOWN_ISSUES.md).
4. Replace temporary ProjectJJK placeholders when original assets are available.
5. Resolve Rich-Modern provenance before any public distribution.
