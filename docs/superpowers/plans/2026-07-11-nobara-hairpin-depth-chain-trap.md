# Nobara Hairpin Depth, Chain, Trap, Remnants, and Momentum Implementation Plan

> **Status: COMPLETE 2026-07-11.** Implemented through `d1461cc`; two final reviews completed and fixed in one pass. Runtime JAR installed with SHA-256 `472D7CECD157C89CF66A39D113B8837BEFA72CAB7F1C6A4C6A5F242FDCF257C1`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build distinct directed/mass Hairpin chains, persistent nail depth, a Shift+B territory trap, forced Black Flash debugging, three polished Bound Remnant variants, and Resonant Momentum.

**Architecture:** Keep `ProjectJjkNailEntity` as the canonical nail carrier. Introduce small server-owned state modules for chain scheduling, traps, forced Black Flash, and Momentum; route their visuals through the existing VFX Core. Extend existing payload/keybind/action routing rather than adding parallel networking.

**Tech Stack:** Java 21, Fabric 1.21.8, Mojang mappings, typed payloads, vanilla item model definitions, existing VFX Core and GeckoLib render path.

## Global Constraints

- Work only in `D:/WorkFlow/Jujutsu Minecraft/.worktrees/nobara-cinematic-slice`.
- Keep gameplay server-authoritative and client code under `src/client`.
- Put every new gameplay number in `ProjectJjkNobaraProfile`.
- Use one concrete nail entity per damage, sound, and VFX event.
- Temporary unload never proves nail removal.
- Inventory items are not nail anchors.
- Commit each independently verified task with an English conventional commit.
- Conduct one global review after implementation, fix confirmed findings once, then rerun verification without a second review cycle.

---

### Task 1: Central balance and typed action contracts

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java`
- Modify: `src/main/java/jujutsu/mod/network/NobaraActionPayload.java`
- Modify: `src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java`
- Test: `src/test/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfileTest.java`
- Test: `src/test/java/jujutsu/mod/ProjectSanityTest.java`

**Interfaces:**
- Produce action ids `HAIRPIN_DIRECTED`, `HAIRPIN_MASS`, `NAIL_TRAP`, preserving `SELF_RESONANCE`.
- Produce constants for R `5`, B `3`, selection `10`, cadences `2/3`, block explosion `1.5`, depth multipliers, trap `6/8/600/15/12`, and Momentum `1200/1.15`.

- [ ] Add reflection/source assertions for every exact value and for `Shift+B -> NAIL_TRAP`; run `gradlew.bat testProjectJjkNobaraProfile testProjectSanity --no-daemon` and observe failure.
- [ ] Add constants and route normal R, Shift+R, normal B, Shift+B explicitly. Suggested routing:

```java
while (nobaraEnlarge.consumeClick()) sendNobaraAction(player.isShiftKeyDown() ? SELF_RESONANCE : HAIRPIN_DIRECTED);
while (nobaraExplosion.consumeClick()) sendNobaraAction(player.isShiftKeyDown() ? NAIL_TRAP : HAIRPIN_MASS);
```

- [ ] Run focused tests, commit `feat(nobara): split directed and mass hairpin inputs`.

### Task 2: Persistent three-level nail depth

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java`
- Modify: `src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java`
- Test: create `src/test/java/jujutsu/mod/character/nobara/projectjjk/NailDepthTest.java`
- Modify: `build.gradle`

**Interfaces:**
- `int embedDepthLevel()`, `boolean deepen()`, `float depthDamageMultiplier()`.
- Clamp NBT and synchronized entity data to `1..3`; new embedded nails start at `1`.

- [ ] Write a pure `NailDepth` policy test covering clamp, transitions, and `1.0/1.35/1.75`; register a JavaExec test task and verify RED.
- [ ] Implement the policy and save/sync it in the nail entity.
- [ ] Replace the old explicit nail-head drive route: after any successful hammer damage, select one owned nail in the victim ordered by depth descending, embedded age descending (oldest), UUID ascending, then call `deepen()`.
- [ ] Emit `NobaraVfxIds.NAIL_DEEPEN` only on a successful transition. Encode level in cue intensity.
- [ ] Offset rendering along `embeddedLocalForward` using centralized per-level offsets. Do not modify the stored anchor offset merely to render depth.
- [ ] Verify focused tests and commit `feat(nobara): add persistent embedded nail depth`.

### Task 3: Deterministic Hairpin chain scheduler

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/HairpinChain.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/HairpinChainScheduler.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/HairpinChainOrder.java`
- Test: create `src/test/java/jujutsu/mod/character/nobara/projectjjk/HairpinChainTest.java`
- Modify: `build.gradle`

**Interfaces:**
- `HairpinChain.Mode { DIRECTED, MASS }`.
- Immutable UUID order, `nextDueGameTime`, cadence, cursor, and `isFinalResolvableStep`.
- `HairpinChainOrder.nearestNeighbor(Vec3 start, List<Candidate>)` uses UUID lexical comparison as final tie-break.

- [ ] Test stable order, exact 2/3 tick cadence, pause on temporary unavailable, skip confirmed removed, and finale reassignment when trailing entries vanish; verify RED.
- [ ] Implement pure ordering/state without Minecraft dependencies where possible. Core step shape:

```java
Resolution resolution = resolver.resolve(chain.currentNailId());
if (resolution.temporary()) return Step.WAIT;
if (resolution.removed()) { chain.skip(); return Step.SKIPPED; }
return Step.EXPLODE;
```

- [ ] Register scheduler tick/stop cleanup and verify GREEN.
- [ ] Commit `feat(nobara): add deterministic hairpin chain scheduler`.

### Task 4: R directed selection and block destruction

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraDamageSources.java`
- Test: `src/test/java/jujutsu/mod/ProjectSanityTest.java`

**Interfaces:**
- `boolean startDirectedHairpin(ServerPlayer caster)`.
- Seed is looked-at owned nail or all owned nails in looked-at entity; include eligible nails within 10 blocks of seed.
- Damage `5 * depthMultiplier * momentumMultiplier` via Hairpin damage source.

- [ ] Add guards asserting directed seed selection, independent entity damage, and block-only explosion interaction; verify RED.
- [ ] Implement selection and enqueue `DIRECTED` at 2 ticks.
- [ ] For block anchors only, create a vanilla explosion with entity damage disabled/neutralized and block interaction enabled at power `1.5`; keep ProjectJJK AoE damage separate. Use the public 1.21.8 API verified from mapped sources, and respect explosion hooks/claims.
- [ ] Consume only successfully resolved nails. Emit one cue and sound per step; pass depth and finale presentation flags.
- [ ] Verify and commit `feat(nobara): add directed hairpin chain`.

### Task 5: B mass nearest-neighbor chain

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java`
- Test: `src/test/java/jujutsu/mod/character/nobara/projectjjk/HairpinChainTest.java`

**Interfaces:**
- `boolean startMassHairpin(ServerPlayer caster)` collects all loaded/available owned embedded block/entity/runtime nails.
- Base damage `3`, cadence `3`, no block explosion.

- [ ] Add a test proving caster-nearest seed, nearest-neighbor continuation, UUID tie-break, and no shuffle; verify RED against current `Collections.shuffle`.
- [ ] Replace aggregated/random `PendingExplosion` behavior with one scheduler step per nail.
- [ ] Apply depth and Momentum multipliers at explosion time; issue independent cooldown-bypassing damage events.
- [ ] Verify and commit `feat(nobara): add mass hairpin chain`.

### Task 6: Chain/depth/finale VFX and sound

**Files:**
- Modify: `src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/VfxWorldChannel.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuSounds.java` and resources only if a genuinely missing sound beat is required
- Test: `src/test/java/jujutsu/mod/ProjectSanityTest.java`
- Test: `src/test/java/jujutsu/mod/client/vfx/VfxQualityTest.java`

- [ ] Add structural assertions for `NAIL_DEEPEN`, level-III burst, finale sound layer, and separate cues per chain entry; verify RED.
- [ ] Author recipes: depth II compact inward ring; depth III blood-black crack plus cyan compression; ordinary explosion; level-III heavy core; finale double shockwave and low tail sound. Finale must not alter gameplay damage.
- [ ] Keep persistent nails in `ProjectJjkNailRenderer`; transient particles/camera/sound remain in recipes.
- [ ] Verify and commit `feat(vfx): visualize nail depth and chain finales`.

### Task 7: Shift+B triangular nail trap

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrap.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java`
- Modify: `src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java`
- Test: create `src/test/java/jujutsu/mod/character/nobara/projectjjk/NailTrapTest.java`
- Modify: `build.gradle`

**Interfaces:**
- One trap per owner, radius `6`, placement range `8`, life `600` ticks, trigger damage `15`, interrupt `12`, inward animation `6` ticks.

- [ ] Test triangle containment, expiry, single trigger, owner replacement, and deterministic one-target selection; verify RED.
- [ ] Raycast ground, validate three supported positions, consume exactly three nails only after all placements validate, and spawn three block-anchored nail entities.
- [ ] Tick valid hostile living entities inside the triangular prism. On trigger, reserve target UUID and schedule three inward beats over 6 ticks.
- [ ] At impact apply one 15-damage Hairpin event, `CombatStagger` for 12 ticks, and embed one new depth-I owned nail using the normal anchor path. Consume trap nails.
- [ ] Add placement/arming/inward-collapse/impact cues and localized failure messages.
- [ ] Verify and commit `feat(nobara): add triangular nail trap`.

### Task 8: Persistent forced Black Flash debug toggle

**Files:**
- Create: `src/main/java/jujutsu/mod/combat/ForcedBlackFlash.java`
- Modify: existing `/jujutsu debug` command registration class discovered via code graph/search
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java`
- Modify: initial nail-impact path in `ProjectJjkNailEntity.java` or its combat delegate
- Test: create `src/test/java/jujutsu/mod/combat/ForcedBlackFlashTest.java`
- Modify: `build.gradle`

- [ ] Test enable/disable/query and disconnect/server-stop cleanup; verify RED.
- [ ] Add `/jujutsu debug black_flash_force true|false` with per-player feedback and permission behavior consistent with sibling debug commands.
- [ ] At every supported `BlackFlashImpact`, short-circuit timing validation when forced, but reuse the normal success path so damage, focus, VFX, stagger, and launch/depth amplification remain identical.
- [ ] Explicitly assert R/B/trap Hairpin damage does not consult the forced flag.
- [ ] Verify and commit `feat(debug): add forced black flash toggle`.

### Task 9: Bound Remnant visual type and three 64x64 textures

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/RemnantVisualType.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkResonanceRemnant.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuDataComponents.java`
- Create: `src/main/resources/data/jujutsumod/tags/entity_type/resonance_remnant_curse.json`
- Create/replace: `src/main/resources/assets/jujutsumod/textures/item/resonance_remnant_flesh.png`
- Create: `src/main/resources/assets/jujutsumod/textures/item/resonance_remnant_token.png`
- Create: `src/main/resources/assets/jujutsumod/textures/item/resonance_remnant_curse.png`
- Modify: item model definition files for `resonance_remnant`
- Test: `src/test/java/jujutsu/mod/ProjectSanityTest.java`

- [ ] Add codec/fallback/category tests: tagged curse -> CURSE, animal -> FLESH, player/villager/golem/fallback -> TOKEN; old data -> TOKEN. Verify RED.
- [ ] Extend the immutable remnant record/codec and set type at remnant creation.
- [ ] Use the image-generation skill for initial original pixel-art concepts, then manually normalize to exact 64x64 pixel art. Each icon must share straw binding but keep distinct silhouette/palette and transparent background.
- [ ] Wire model selection from stored component. Render and inspect all three at native scale and enlarged nearest-neighbor preview; reject muddy silhouettes or semi-transparent fringe.
- [ ] Verify PNG dimensions/alpha and commit `feat(items): add bound remnant visual variants`.

### Task 10: Resonant Momentum server state and HUD

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ResonantMomentum.java`
- Create: `src/main/java/jujutsu/mod/network/ResonantMomentumPayload.java`
- Modify: `src/main/java/jujutsu/mod/network/JujutsuNetworking.java`
- Modify: `src/client/java/jujutsu/mod/client/network/JujutsuClientNetworking.java`
- Create: `src/client/java/jujutsu/mod/client/character/ClientResonantMomentum.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/NobaraHudState.java` and HUD renderer
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java`
- Modify: nail preparation/launch, hammer, and R/B damage call sites
- Test: create `src/test/java/jujutsu/mod/character/nobara/projectjjk/ResonantMomentumTest.java`

- [ ] Test grant/refresh/no-stack/expiry and exact `1.15` damage/timing multipliers; verify RED.
- [ ] Grant 1200 ticks only after resources are consumed and Straw Doll damage resolves. Sync remaining duration on grant/expiry/join as needed.
- [ ] Apply multiplier explicitly:

```java
float damage = baseDamage * depth.multiplier() * ResonantMomentum.damageMultiplier(caster, gameTime);
int interval = ResonantMomentum.scalePreparationTicks(caster, baseInterval, gameTime);
```

- [ ] Add localized HUD name and countdown. Do not use Strength, Speed, or attribute modifiers.
- [ ] Verify and commit `feat(nobara): add resonant momentum buff`.

### Task 11: Documentation, one global review, final verification, and install

**Files:**
- Update: `docs/superpowers/specs/2026-07-11-nobara-hairpin-depth-chain-trap-design.md`
- Update: `docs/session-handoffs/2026-07-11-nobara-hairpin-depth-chain-trap-handoff.md`
- Update: `SESSION.md`
- Update Obsidian/codebase codex Nobara overview/system notes through `mcpvault`.

- [ ] Run all focused JavaExec assertion tasks introduced above.
- [ ] Run `gradlew.bat check --no-daemon`, `gradlew.bat build --no-daemon -x test`, and `git diff --check`.
- [ ] Perform one global review across the full implementation range for spec and repository standards. Fix every confirmed finding in one pass.
- [ ] Rerun focused/full verification without requesting a second review.
- [ ] Commit final docs/fixes with a conventional message.
- [ ] Replace the old non-sources/non-dev JAR in `D:/Games/instances/Jujutsu/mods`, then compare SHA-256 of built and installed files.
- [ ] Record commit range, commands, hash, known manual QA, and exact continuation point in the handoff and `SESSION.md`.

## Manual Gameplay/VFX QA Owned by User

- Confirm R selects the aimed seed and destroys only nearby blocks at block anchors.
- Confirm B reaches all loaded owned nails in visibly stable nearest-neighbor order.
- Confirm 2/3 tick chains read as separate beats and finale is stronger without extra damage.
- Confirm depth I/II/III model placement and VFX are readable on moving entities.
- Confirm the 6-block trap is useful but not unavoidable, deals 15 total, interrupts briefly, and embeds one nail.
- Confirm forced Black Flash covers hammer and nail physical impacts but not Hairpin technique explosions.
- Inspect all three remnant icons in inventory, hand, ground, and GUI scaling.
- Confirm Resonant Momentum HUD and 60-second feel.
