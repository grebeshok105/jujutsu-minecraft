# Block 3 report — Nue arc + shikigami audit

## Done

- Added the directed `MegumiShikigamiRuntime.broadcastCue` overload and shared `directedCue` factory. The overload uses the same radius/audience path as the existing cue helper.
- `MegumiNueBrain` now sends `NUE_DIVE` with a source direction and sends `NUE_SHOCK` through the production `shockCue` path with `origin=target`, `anchorEntityId=Nue`, and direction `Nue -> target`.
- Added `NueArcState`: immutable target endpoint, live Nue entity id, seeded arc, eight-tick expiry.
- Added and registered `NueArcRenderer`: 2–3 seeded jagged lightning ribbons, two-tick jitter reseeding, lightning/additive buffer, six-tick expanding impact shell with four mini-arcs. The same world pass renders the directional elephant water ribbon.
- `MegumiVfxRecipes`: `nueShock` registers the arc and leaves a small spark/ring garnish; `nueDive` samples sparks along cue direction; elephant jet registers its directional stream; removed the deleted B1 `triggerNueWings` caller while retaining unrelated shadow/dog animation hooks.
- Rabbit audit fix: server bump calls the existing synchronized `swing` trigger once per successful bump; client render state promotes the imported one-shot attack clip from the swing state or action timer and lets it finish after the short swing window.
- Elephant audit fix: jet cue carries the elephant-facing direction and the renderer draws a directional stream.
- Dogs audit: existing real-body renderer plus `DOGS_SIC`/`DOGS_POUNCE` cue recipes remain wired; no mechanics change was needed.
- No new live id was added to `MegumiVfxIds`; B5 does not need a completeness-count update for this block.

## Files touched

- `src/main/java/jujutsu/mod/character/megumi/MegumiNueBrain.java`
- `src/main/java/jujutsu/mod/character/megumi/MegumiShikigamiRuntime.java`
- `src/main/java/jujutsu/mod/character/megumi/MegumiElephantBrain.java`
- `src/main/java/jujutsu/mod/character/megumi/MegumiRabbitsBrain.java`
- `src/client/java/jujutsu/mod/client/vfx/megumi/MegumiVfxRecipes.java`
- `src/client/java/jujutsu/mod/client/vfx/megumi/NueArcState.java`
- `src/client/java/jujutsu/mod/client/vfx/megumi/NueArcRenderer.java`
- `src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java`
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiRabbitRenderer.java`
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiRabbitsGeoAnimatable.java`
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiShikigamiAnimationPolicy.java`
- `src/test/java/jujutsu/mod/character/megumi/NueArcCueTest.java`
- `src/test/java/jujutsu/mod/client/vfx/megumi/NueArcStateTest.java`
- `src/test/java/jujutsu/mod/client/render/megumi/MegumiShikigamiAnimationPolicyTest.java`
- `src/test/java/jujutsu/mod/character/megumi/MegumiElephantBrainTest.java`

## Verification

- Structural Java parse checks with `ast_grep` passed for the new renderer/state and all edited production methods.
- `git diff --check` passed with no output for the Block 3 paths.
- LSP diagnostics were attempted for the edited Java files but the local `jdtls` server failed to start; no diagnostic result was available.
- Per worker contract, Gradle, tests, GameTest, qualityGate, and MCP lane were not run.

## Main run

Run: `gradlew.bat compileJava compileClientJava test --tests "*NueArc*" --tests "*Megumi*"`

Expected: green.

Preview: Main must perform the required in-game/rendered screenshot check for the Nue arc/impact and elephant stream before barrier sign-off.

## Deviations / unresolved

- No known deviations from Task 3 requirements.
- In-game preview evidence is unresolved until Main runs the client/MCP or rendered screenshot lane.
