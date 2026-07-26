# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: refactor/shared-vessel-render-stack
- Base: main at 21ebefe (merge of PR #4)
- PR #4 merged only the first Todo slice (c8e48dd) plus its review note (b3b4605)
- This branch rebases the remaining eight commits onto that merge: the GeckoLib model pass and review fixes (85792d8) and the shared vessel render stack (5be077b, 17d4a65, 69b9358, f4ccdea, 350aa86)
- Product target: private play for one or two people

## Current product state

- Fabric 1.21.8, Java 21, mod id jujutsumod.
- Playable vessels: Nobara, Todo (Aoi Todo), and None.
- N opens the single ClickGui product menu. The Neon Dashboard and Key V path are retired.
- Character selection is sent through SelectCharacterPayload and remains server-authoritative.
- Selection persists across reconnects/restarts through the Fabric Data Attachment API.
- The Nobara starter hammer, doll, and nails are granted once per player; re-selecting Nobara does not refill them.
- Todo has no starter items; vanilla melee + Boogie Woogie (R) + shared Black Flash bridge.
- Nobara controls: R directed Hairpin, B mass Hairpin, Shift+R Self Resonance, Shift+B Nail Trap, hammer left click contextual melee.
- Transient combat presentation uses VfxCue → VfxDirector → JujutsuVfxRecipes.registerAll() → character recipes and shared director channels.
- Resonance intentionally changes the global server tick rate for hit-stop. This is accepted for the current 1–2 player target.
- Ordinary loaded embedded nails expire after 1200 ticks, are capped at 30 per owner, and are resolved through EmbeddedNailRegistry rather than level.getAllEntities().
- Vessel rendering is shared. CharacterGeoRenderers resolves one renderer per vessel through an exhaustive switch, so a new JujutsuCharacter constant fails compilation until it declares a renderer or opts into vanilla. CharacterPlayerGeoRenderer owns the render entry and pose-stack guard, CharacterPlayerGeoModel owns the arm pose and clamped head look, CharacterHeldItemLayer owns hand attachments.
- The three shared render mixins are named for their real scope: CharacterRenderDispatchMixin, PlayerRenderContextMixin, FirstPersonHandFxMixin.

## Asset and provenance decisions

- ProjectJJK placeholder models/assets are used with permission from the author and are intended to be replaced later.
- They are not automatically covered by the repository CC0 declaration.
- Rich-Modern-derived code/assets still need a provenance review before a public release.
- Do not remove the ProjectJJK placeholders as an unapproved cleanup; do not expand the imported set casually.

## Documentation authority

1. Current code and passing tests.
2. AGENTS.md for durable product direction.
3. This SESSION.md for the active handoff.
4. Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md for current architecture.
5. docs/KNOWN_ISSUES.md for live debt.

Use docs/README.md for the current-document map. Historical documentation has been intentionally removed. Run python3 tools/audit_docs.py after documentation changes.

## Verification status

Branch implementation verified earlier on c8e48dd (build + live smoke for Todo swap).

Review-fix pass (this session):

- A1 docs metrics / Todo source-of-truth updates
- A2 JujutsuVfxRecipes.registerAll()
- A3 non-living pickable collision in safe destinations
- B1 Black Flash bonus clears invulnerableTime for the bonus hit only
- B2 rollback logs incomplete restore
- A5 TodoProfile horizontal radius + world-border margin wired
- A6 roster labels localized
- A7 trailing whitespace removed from design doc

Todo GeckoLib model pass:

- Assets from `TODO_AOI_READY.zip` → `geckolib/models/todo/todo_aoi`, `geckolib/animations/todo/todo_aoi`, texture `textures/entity/character/todo_aoi.png`
- Client: `TodoPlayerGeoAnimatable` / `Model` / `Renderer` / `HeldItemLayer`; player render mixin branches for Todo
- Animations: idle, walk, attack, `ability.boogie_woogie` (triggered via VFX cue anchor on cast)
- `gradlew.bat build --no-daemon` — BUILD SUCCESSFUL
- Installed jar: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`

Render debt pass (this session):

- Renamed the three shared render mixins; no behavior change
- CharacterGeoRenderer / CharacterGeoRenderers replace the per-character `if` chain; verified fail-closed by temporarily adding a fourth JujutsuCharacter constant and confirming the build fails at CharacterGeoRenderers
- CharacterHeldItemLayer, CharacterPlayerGeoRenderer, CharacterPlayerGeoModel absorb the duplicated vessel render stack
- Sanity-test guards repointed to the shared files and extended to assert no vessel redefines the head-look clamps or hand-rolls the pose-stack guard
- `gradlew.bat build --no-daemon` — BUILD SUCCESSFUL

Rebase onto 21ebefe (this session):

- Eight commits replayed onto the PR #4 merge with no conflicts
- `gradlew.bat build --no-daemon` — BUILD SUCCESSFUL
- `python tools/audit_docs.py` — passed; the audit now scopes to git-tracked Markdown so untracked local workspaces cannot fail it

In-game smoke still required, and now covers both the original Todo slice and the render refactor: model/animations, clap timing, swap occupancy, BF bonus, Nobara targeting, plus third-person Nobara and Todo held items and head look.

## Next product steps

1. In-game smoke: Todo select, R swap, boat/minecart blocked dest, BF damage numbers, Nobara Hairpin targeting.
2. Optional world/GameTest coverage for tryCast/rollback.
3. Replace temporary ProjectJJK placeholders when original assets are available.
4. Resolve Rich-Modern provenance before any public distribution.
