# VFX Core: verified refactoring and completion plan

Status: **implementation-ready specification**  
Repository baseline: `main` at `e3ee14480cff557b9c409a01465924521fecd4ad`  
Scope: architecture, correctness, cleanup, tests, documentation and implementation sequencing for the existing VFX Core  
Out of scope: implementing the changes described here, redesigning existing effects, adding abilities, changing gameplay balance, replacing assets, or introducing a second VFX framework

## 1. Purpose

This document replaces two independent audit reports with one code-verified plan.

The reports were useful, but neither was fully accurate. Several findings were correct and actionable; several counts were wrong; some proposed tests already exist; one report misunderstood `ACTIVE_INSTANCES`; the other understood it but miscounted the channels and proposed unnecessary machinery. This specification keeps only claims that are supported by the repository at the baseline commit above.

The goal is narrow:

1. fix one confirmed VFX payload bug;
2. remove confirmed dead API and dead registrations;
3. make the existing transport and registration contracts testable;
4. standardize ownership and package layout without changing wire ids;
5. split the only serious client-side monolith without inventing a plugin framework;
6. define a measurable point at which VFX Core is considered complete and enters architectural freeze.

The current architecture is already fundamentally sound. The work below is a completion and hardening pass, not a replacement.

---

## 2. Verified baseline

### 2.1 Canonical pipeline

The live pipeline is:

```text
server-confirmed action
    -> VfxCue
    -> VfxCuePayload
    -> JujutsuClientNetworking
    -> VfxDirector
    -> <Character>VfxRecipes
    -> director-owned channels
```

Registration order is deliberate and correct:

```text
VfxDirector.initialize()
    -> JujutsuCharacterClients.registerAll()
    -> each CharacterClientDefinition.registerClientHooks()
    -> each vessel registers its own recipe pack
```

The architecture therefore has one aggregate client bootstrap without an aggregate recipe list that can drift from the roster.

### 2.2 Wire contract

`VfxCue` has eight transported fields:

1. `effectId`
2. `origin`
3. `anchorEntityId`
4. `anchorOffset`
5. `intensity`
6. `startGameTime`
7. `seed`
8. `direction`

`VfxCuePayload.STREAM_CODEC` writes and reads those fields in a fixed order. The string values of the effect ids and the field order are wire compatibility concerns.

`VfxCue` normalizes `direction`. A magnitude that must survive transport cannot be stored only in `direction`; Todo already follows this rule by carrying speed or dimensions in `anchorOffset` while using `direction` only for orientation.

The broadcast radius is not part of the cue. It is chosen by each server emitter.

### 2.3 Effect ids

At the baseline commit:

| Owner | Declared ids | Registered recipes | Confirmed live emitters |
|---|---:|---:|---:|
| Nobara | 25 | 25 | 21 |
| Todo | 7 | 7 | 7 |
| Megumi | 5 | 5 | 5 |
| **Total** | **37** | **37** | **33** |

The four registered Nobara ids with no production emitter are:

- `RESONANCE_CHANNEL`
- `RESONANCE_STRIKE`
- `LINK_BIND`
- `EMBEDDED_NAIL_DRIVE`

Their recipes are aliases or animation wrappers, but no server runtime emits them. They are dead contract surface, not dormant behavior documented as planned.

### 2.4 Director-owned channels

`VfxDirector` owns eight channel objects:

1. `VfxWorldChannel`
2. `VfxHudChannel`
3. `VfxCameraChannel`
4. `VfxFirstPersonChannel`
5. `VfxParticleChannel`
6. `VfxSoundChannel`
7. `VfxPostProcessChannel`
8. `VfxTimeChannel`

`VfxContext` exposes the same eight channels.

The count is eight. `VfxQuality` and `VfxPalette` are supporting types, not channels. `VfxSoundDuck` is a helper owned by the sound path, not a ninth channel.

### 2.5 World styles and caps

`VfxWorldChannel.ImpactStyle` contains twelve styles:

- `HAMMER_SEND`
- `ENLARGE`
- `EXPLOSION`
- `RITUAL_BIND`
- `DOLL_STRIKE`
- `RESONANCE_RELEASE`
- `BLACK_FLASH`
- `BOOGIE_WOOGIE`
- `SWAP_AFTERIMAGE`
- `SWAP_ARRIVAL`
- `MEGUMI_SHADOW_OPEN`
- `MEGUMI_SHADOW_CLOSE`

`worldFixed` is correctly stored as a property of the style. It must remain explicit and local to the style declaration.

There are two numeric caps in the current code:

- `VfxDirector.MAX_ACTIVE_INSTANCES = 64`
- `VfxWorldChannel.MAX_IMPACT_FLASHES = 48`

These caps do not have the same meaning. The world cap bounds actual retained render state. The director cap bounds bookkeeping objects whose `start` callback has already run.

### 2.6 Existing tests that must not be duplicated

The repository already has coverage for:

- cue field preservation;
- payload codec round-trip;
- stable selected Nobara ids;
- timeline age, expiry and opening-beat behavior;
- late real-time windows;
- anchor resolution and fallback;
- sound duck arithmetic and ownership;
- first-person timing behavior;
- several pure world-style calculations.

Any implementation plan that says “add the first codec round-trip test” is stale. The actual task is to migrate or expand the existing test under the repository's JUnit 5 policy, not to create a duplicate assertion program.

---

## 3. Confirmed defects and debt

### 3.1 P0 correctness defect: Nail Trap collapse payload loses travel distance

#### Current emitter

`NailTrapRuntime` emits `NAIL_TRAP_COLLAPSE` with:

- `origin = from`
- `anchorOffset = Vec3.ZERO`
- `direction = to - from`

#### Current recipe

`NobaraVfxRecipes.nailTrapCollapse` reads:

```java
Vec3 travel = cue.anchorOffset();
```

It then samples points from `origin` to `origin + travel`.

#### Why this is broken

The emitter puts the displacement in `direction`, while the recipe reads `anchorOffset`. Even changing the recipe to read `direction` would remain incorrect because `VfxCue` normalizes `direction`, destroying the original distance.

The collapse recipe therefore cannot reconstruct the real nail-to-target path from the cue it receives. The server separately spawns its own collapse trail, so the bug can be partially hidden by duplicated feedback, but the client recipe contract is still wrong.

#### Required correction

Transport the full `to - from` delta in `anchorOffset`. `direction` may carry the normalized orientation as an optional convenience, but the displacement magnitude must live in `anchorOffset`.

#### Regression contract

A test must construct a collapse cue for a non-unit displacement and prove:

- `anchorOffset == to - from`;
- the endpoint reconstructed as `origin + anchorOffset` equals `to`;
- `direction` remains normalized when non-zero;
- zero-distance input is safe.

The test must fail against the current emitter before the fix. A source-text assertion alone is weaker than a pure cue-construction test and should not be the primary proof.

### 3.2 P1 dead channel: `VfxTimeChannel`

`VfxTimeChannel` stores a client-side scale and exposes it through `VfxDirector.timeScale()`. Production recipes call `triggerSlowMotion` for the two large Resonance effects.

No live render or game-time consumer reads `VfxDirector.timeScale()`. The configured client mixins do not include a delta-tracker time-scaling mixin. Consequently, the calls mutate private channel state but do not affect presentation.

This is misleading dead behavior. It suggests client slow motion exists when it does not.

#### Required correction

Remove:

- `VfxTimeChannel`;
- the `TIME` field in `VfxDirector`;
- the `time` field, constructor parameter and accessor in `VfxContext`;
- `VfxDirector.timeScale()`;
- both production `triggerSlowMotion` calls;
- tests that exist only for the removed unused channel.

Do not replace it with a new mixin in this refactor. Resonance already has an explicitly accepted server-global time-dilation path. Introducing client time scaling would be a product and gameplay presentation decision, not dead-code cleanup.

### 3.3 P1 dead ids and recipes

The following ids have no production emitter:

- `RESONANCE_CHANNEL`
- `RESONANCE_STRIKE`
- `LINK_BIND`
- `EMBEDDED_NAIL_DRIVE`

Their registrations make the recipe table look complete while preserving code paths no action can reach.

#### Required correction

Delete the four constants and their recipe registrations/method aliases. Preserve all live ids and their string paths unchanged.

A repository-wide contract test must prevent a new registered id from remaining emitter-less indefinitely.

### 3.4 P1 misleading director lifecycle: `ACTIVE_INSTANCES`

`VfxRecipe.create(cue)` returns a `VfxInstance`. `VfxDirector.receive` calls `instance.start(context, initialAgeTicks)` exactly once. After that call, continuing visuals live in the channels:

- world effects are retained by `VfxWorldChannel`;
- camera and FOV impulses are retained by `VfxCameraChannel`;
- HUD windows are retained by `VfxHudChannel`;
- first-person state is retained by `VfxFirstPersonChannel`;
- sound duck state is retained by `VfxSoundChannel`;
- blur state is retained by `VfxPostProcessChannel`;
- particles and sounds are emitted immediately.

`ACTIVE_INSTANCES` is only walked to discard expired records. Removing an entry does not stop a channel effect. Evicting the oldest record at 64 does not evict the visible world flash, HUD window, camera impulse, particle, sound or blur that was already started.

Therefore the current name and cap imply a global active-effect limiter that does not exist.

#### Required correction

Delete `ACTIVE_INSTANCES`, `ActiveInstance` and `MAX_ACTIVE_INSTANCES` unless implementation work first discovers an actual lifecycle consumer not present at the verified baseline.

`receive` should:

1. resolve the recipe;
2. create the instance;
3. reject it if already expired;
4. compute initial age;
5. invoke `start` once.

The director tick should retain only work it genuinely owns, currently level binding and sound-channel ticking.

Do not replace the list with `ArrayDeque`, smarter eviction or priority rules. That would optimize bookkeeping that should not exist and would still not control real channel state.

The actual retained world-state cap of 48 remains a separate concern and should not be changed without a reproduced visual overload case.

### 3.5 P2 package asymmetry

The intended repository convention is:

```text
src/main/java/jujutsu/mod/vfx/<Character>VfxIds.java
src/client/java/jujutsu/mod/client/vfx/<character>/<Character>VfxRecipes.java
```

Nobara and Todo mostly follow it. Megumi currently places:

- `MegumiVfxIds` under `character/megumi/vfx`;
- `MegumiVfxRecipes` under `client/character/megumi/vfx`.

The behavior is correct, but the ownership layout conflicts with the documented VFX Core extension seam.

#### Required correction

Move the Megumi id and recipe classes to the canonical VFX packages. Keep all five `megumi/*` resource paths unchanged. Update imports and the client definition hook only.

This is a package refactor, not a wire migration.

### 3.6 P2 duplicated cue construction conventions

Multiple runtimes hand-construct `VfxCue` using small local factory methods. Most repeat the same four shapes:

- world-fixed cue;
- entity-anchored cue;
- entity-anchored directed cue;
- exact-origin directed cue.

The duplication has already contributed to the collapse bug because payload meaning is scattered across emitters and recipes.

#### Required correction

Add one small shared `VfxCues` factory in `jujutsu.mod.vfx` for canonical transport shapes. It should centralize mechanics, not hide effect semantics.

Recommended surface:

```java
VfxCues.worldFixed(...)
VfxCues.anchored(...)
VfxCues.anchoredDirected(...)
VfxCues.worldFixedDirected(...)
```

Each method should make `NO_ANCHOR`, anchor offsets, clamped intensity, game time and seed handling explicit.

Do not create one wrapper record per effect. Todo's overloaded `anchorOffset` payloads and Hairpin's packed intensity remain effect-specific contracts documented beside their ids and recipes. The shared factory should remove boilerplate, not produce a parallel type system.

### 3.7 P2 nondeterministic camera tests

`VfxCameraChannel` reads `System.currentTimeMillis()` directly in trigger and sample paths. Its arithmetic is bounded, but tests cannot step exact timestamps without sleeping or relying on timing luck.

#### Required correction

Inject a package-private `LongSupplier` clock, matching the proven approach already used by `VfxFirstPersonChannel`.

Add deterministic JUnit tests for:

- expiry removal;
- overlapping impulse addition;
- yaw and pitch clamps;
- FOV clamps;
- future-start handling;
- late cue offsets;
- the relative strength of swap snap versus heavy impact and Black Flash, expressed as bounded invariants rather than exact fragile wave samples.

Do not change the production curves in the same PR as clock injection unless a failing test demonstrates an actual defect.

### 3.8 P2 world-channel monolith

`VfxWorldChannel` owns valid shared responsibilities:

- retained world-effect lifecycle;
- a real cap of 48;
- age and expiry;
- anchor resolution;
- render buffer acquisition;
- style dispatch.

It also contains all geometry and every style implementation in one large file. Adding a style expands a central switch and a large pile of helper methods.

The problem is file-level coupling and reviewability, not the existence of the switch or the style enum.

#### Required correction

Keep `VfxWorldChannel` as the lifecycle owner and dispatcher. Extract render implementations by visual family:

```text
client/vfx/world/
    HairpinWorldEffects.java
    BlackFlashWorldEffects.java
    SwapWorldEffects.java
    ShadowWorldEffects.java
    VfxWorldGeometry.java
```

Suggested ownership:

- `HairpinWorldEffects`: `HAMMER_SEND`, `ENLARGE`, `EXPLOSION`, `RITUAL_BIND`, `DOLL_STRIKE`, `RESONANCE_RELEASE`
- `BlackFlashWorldEffects`: `BLACK_FLASH`
- `SwapWorldEffects`: `BOOGIE_WOOGIE`, `SWAP_AFTERIMAGE`, `SWAP_ARRIVAL`
- `ShadowWorldEffects`: `MEGUMI_SHADOW_OPEN`, `MEGUMI_SHADOW_CLOSE`
- `VfxWorldGeometry`: ribbon, basis, side-vector and shared low-level vertex helpers

Keep `ImpactStyle` exhaustive and keep `worldFixed` on the style. Do not introduce service loading, reflection, a renderer registry, dependency injection or one class per enum constant. Twelve styles do not justify a plugin framework.

The extraction must be behavior-preserving. The only intended visual change in the entire plan is the collapse-path bug fix.

---

## 4. Corrected conclusions from the two audits

### 4.1 Findings accepted

The combined plan accepts these findings:

- the collapse cue payload is wrong;
- four Nobara ids and registrations are dead;
- `VfxTimeChannel` has no production consumer;
- `ACTIVE_INSTANCES` is bookkeeping, not a real global visual lifecycle;
- `VfxWorldChannel` is the primary maintainability hotspot;
- Megumi's VFX package layout is inconsistent with the documented extension seam;
- cue-construction conventions should be centralized carefully;
- camera timing needs an injectable clock;
- the architecture should be frozen after a finite completion pass.

### 4.2 Findings corrected or rejected

The combined plan rejects or corrects these claims:

- **“There are nine channels.”** There are eight.
- **“Nobara has 24 ids.”** Nobara has 25.
- **“Todo has four ids.”** Todo has seven at the verified baseline.
- **“Megumi has four ids.”** Megumi has five.
- **“There are 32 cue ids.”** There are 37 declared ids and 33 with live emitters.
- **“A codec round-trip test is missing.”** It already exists in `VfxCueTest`; the work is migration and expansion.
- **“The 64-instance cap drops visible effects.”** It drops only director bookkeeping records after their start callbacks have run.
- **“Replace the active list with a better queue.”** Rejected. Remove the misleading bookkeeping instead.
- **“Add an address-only `SWAP_ARRIVAL_SELF` cue now.”** Deferred. The local-arrival heuristic has edge cases, but changing cue count and participant feedback is a behavior change. It requires an observed in-game failure or a separate design decision.
- **“Introduce typed wrapper records for every cue.”** Rejected as excessive for the current scale. Use a small canonical factory plus effect-owned payload documentation.
- **“Freeze the current channel count as seven.”** Incorrect baseline and unnecessary wording. After removing the dead time channel, seven live channels remain; that result follows from cleanup rather than being assumed in advance.
- **“The 48-world-effect cap should be redesigned now.”** Deferred until a reproducible overload case or profiling data exists.

---

## 5. Target architecture

After this plan is implemented, the architecture should be:

```text
shared transport
    VfxCue
    VfxCuePayload
    VfxTimeline
    VfxAnchorResolver
    VfxCues

shared id ownership
    NobaraVfxIds
    TodoVfxIds
    MegumiVfxIds

client director
    VfxDirector
        - recipe registry
        - unknown-id logging
        - level/disconnect lifecycle
        - one world callback
        - one HUD element
        - one client tick listener

live channels
    VfxWorldChannel
    VfxHudChannel
    VfxCameraChannel
    VfxFirstPersonChannel
    VfxParticleChannel
    VfxSoundChannel + VfxSoundDuck
    VfxPostProcessChannel

world rendering
    VfxWorldChannel: lifecycle + cap + dispatch
    world/HairpinWorldEffects
    world/BlackFlashWorldEffects
    world/SwapWorldEffects
    world/ShadowWorldEffects
    world/VfxWorldGeometry

recipe ownership
    client/vfx/nobara/NobaraVfxRecipes
    client/vfx/todo/TodoVfxRecipes
    client/vfx/megumi/MegumiVfxRecipes
```

Properties that remain unchanged:

- server authority;
- one shared S2C payload;
- eight-field wire format;
- all existing resource-location strings;
- visual-only cue semantics;
- character-owned recipe registration;
- one HUD element;
- no effect-specific packet receiver;
- no effect-specific mixin;
- persistent visuals remain on entity/state renderers;
- late-cue age behavior;
- style-owned `worldFixed` policy;
- existing gameplay time dilation for Resonance.

---

## 6. Implementation sequence

The work should be delivered as small reviewable PRs. The order below separates correctness, deletion, contract hardening and mechanical extraction.

### PR 1: fix Nail Trap collapse payload

**Scope**

- introduce a testable collapse-cue construction seam;
- put full displacement in `anchorOffset`;
- preserve normalized orientation in `direction` if useful;
- add JUnit regression tests;
- run a focused in-game smoke of a trap collapse at short and long distances.

**Allowed behavior change**

- the client collapse line correctly spans from each nail to the target.

**Forbidden collateral changes**

- particle counts;
- sounds;
- trap timings;
- damage;
- collapse cadence;
- generic cue format.

**Acceptance**

- non-unit displacement round-trips exactly through the cue fields;
- reconstructed endpoint equals the target point;
- in-game trail converges on the target;
- existing quality gate passes.

### PR 2: remove dead VFX surface

**Scope**

- remove the four un-emitted Nobara ids and aliases;
- remove `VfxTimeChannel` and all unused plumbing/calls/tests;
- update VFX documentation counts.

**Acceptance**

- no references remain to the removed ids;
- no references remain to `VfxTimeChannel`, `VfxDirector.timeScale` or `context.time()`;
- all live id strings remain unchanged;
- Resonance server time dilation remains untouched;
- quality gate passes.

### PR 3: remove fake active-instance lifecycle

**Scope**

- remove `ACTIVE_INSTANCES`, `ActiveInstance` and `MAX_ACTIVE_INSTANCES`;
- simplify `VfxDirector.receive` and `tick`;
- add tests around receive-time expiry and one-shot start behavior through a package-private pure seam if needed.

**Acceptance**

- each accepted cue starts exactly once;
- already-expired cues do not start;
- unknown ids still warn once and do not start;
- disconnect and level changes still clear every actual channel;
- the real `VfxWorldChannel` cap remains unchanged.

### PR 4: harden id and codec contracts

**Scope**

- migrate existing VFX assertion programs in scope to JUnit 5 where still required by the repository's test plan;
- retain and expand the existing codec round-trip test rather than duplicating it;
- add `ALL` sets or an equivalent explicit enumeration to each `*VfxIds` class;
- test that recipe registration matches the declared id sets exactly;
- add a build-time emitter-coverage check restricted to production sources and excluding id/recipe declarations;
- record red runs for deliberately removed registration or codec-field mutations.

**Acceptance**

- every declared id has exactly one recipe;
- every registered recipe id belongs to a declared vessel set;
- every declared id has at least one production emitter reference;
- duplicate registration still fails;
- payload round-trip covers all eight fields, non-zero direction, non-zero anchor offset and `NO_ANCHOR`;
- test names describe contract behavior, not implementation trivia.

**Caution**

An emitter-coverage test is an architectural source check. It proves that an id is referenced by production emitter code, not that the gameplay route is reachable in a live world. Keep that limitation explicit.

### PR 5: canonical cue factories and package normalization

**Scope**

- add the minimal `VfxCues` factory;
- migrate repeated generic constructor helpers runtime by runtime;
- keep effect-specific packed payloads explicit;
- move Megumi ids and recipes to canonical VFX packages;
- update imports and documentation.

**Suggested migration order**

1. Megumi summon runtime;
2. Nobara ritual runtime;
3. Nail Trap runtime;
4. Nobara hammer runtime;
5. Todo emitters.

Todo goes last because its `anchorOffset` fields intentionally carry several different payload shapes and deserve the most careful review.

**Acceptance**

- no wire id string changes;
- no field-order changes;
- generic cue construction no longer hand-repeats `NO_ANCHOR`, anchor delta and intensity clamping in several runtimes;
- specialized Todo and Hairpin payload conventions remain documented beside their ids;
- package-boundary tests accept the new canonical layout.

### PR 6: deterministic camera-channel tests

**Scope**

- inject a package-private millisecond clock;
- add JUnit tests for lifetime, overlap and clamps;
- preserve production constants and curves.

**Acceptance**

- no sleeps;
- no wall-clock flakes;
- test clock can cross exact expiry boundaries;
- production behavior is unchanged;
- camera clamps remain `yaw [-9, 9]`, `pitch [-7, 7]`, final FOV `[-18, 20]` unless a separate bug is demonstrated.

### PR 7: split world rendering by visual family

**Scope**

- extract style rendering and shared geometry from `VfxWorldChannel`;
- keep lifecycle, cap, age, anchoring and dispatch in `VfxWorldChannel`;
- preserve `ImpactStyle` exhaustiveness and `worldFixed` ownership;
- move existing pure tests with the functions they cover.

**Acceptance**

- no intended visual changes;
- no new registration system;
- no reflection;
- no additional render callback;
- no new mixin;
- `MAX_IMPACT_FLASHES = 48` remains unchanged;
- before/after smoke captures show the same Hairpin, Black Flash, swap and shadow presentation except for the earlier collapse fix.

### PR 8: completion docs and architectural freeze

**Scope**

- update `VFX-core.md` with final counts and extension rules;
- update stale references in `AGENTS.md`, Codex and known issues;
- mark completed debt as resolved;
- record remaining accepted limitations.

**Acceptance**

- documentation matches code and tests;
- no document claims Todo has four ids, Megumi has four ids, nine channels exist, or codec coverage is absent;
- the freeze rule below is copied into the canonical VFX Core documentation.

---

## 7. Test strategy

### 7.1 Pure and unit tests

Required fast tests:

| Area | Contract |
|---|---|
| `VfxCue` | direction normalization, zero direction, all field preservation |
| Codec | encode/decode all eight fields with non-default values |
| Timeline | age, expiry boundary, opening beat, late real-time windows |
| Anchor resolver | static origin, live anchor, offset, missing-anchor fallback |
| Cue factories | correct anchor id/offset, intensity clamp, seed/time preservation |
| Collapse cue | full displacement survives in `anchorOffset` |
| Id sets | declared ids equal registered ids |
| Emitter coverage | every declared id is referenced by production emitter code |
| Camera | deterministic overlap, expiry and clamps |
| First person | existing clock-driven SNAP/CLAP/SIGN behavior remains |
| Sound duck | ownership, extension and restore rules remain |
| World math | silhouette dimensions, shadow pool envelopes and extracted helper math |

### 7.2 Mutation/red-run expectations

For architecture-contract tests, the implementation PR should record at least these deliberate failures:

1. delete one codec field from encode or decode;
2. remove one recipe registration;
3. add a declared id without an emitter;
4. change collapse cue `anchorOffset` back to zero;
5. make a duplicate recipe registration;
6. change a world-fixed style flag for an afterimage or shadow pool.

The purpose is to prove that the tests defend the contract they claim to defend. Human beings have an impressive ability to write green tests for code paths the test never touches.

### 7.3 In-game smoke matrix

Automated tests cannot validate readability, mixin integration, sound layering or actual rendering. A focused smoke remains mandatory.

#### Nobara

- hammer horizontal and overhead;
- prepared nail launch;
- ordinary impact and local impact sound;
- directed and mass Hairpin;
- enlargement;
- trap placement, armed state, collapse and impact;
- Resonance bind, doll strike and release;
- Black Flash;
- Self Resonance.

#### Todo

- real clap and feint share the same clap presentation;
- aimed swap;
- pair swap;
- landed marker swap;
- body marker swap;
- afterimage dimensions differ for player and wide mob;
- arrival streak respects preserved velocity;
- momentum strike can coincide with Black Flash without visual corruption;
- sound duck restores on deadline, menu open, disconnect and level change.

#### Megumi

- player summon sign;
- both dog shadow-open pools;
- manual recall shadow-close pools;
- Sic marker;
- accepted pounce impact;
- cooldown HUD contribution remains under the single VFX HUD element.

#### Cross-system

- unknown cue id logs once and does not crash;
- reconnect and dimension change clear transient state;
- reduced/minimal particle settings scale counts but never turn a positive requested burst into zero;
- no VFX path applies damage, marks, cooldowns or gameplay state client-side.

### 7.4 Performance checks

No optimization work is approved by this plan. Still, implementation should collect a lightweight baseline before and after the world-channel split:

- active world-effect count under repeated Black Flash and Hairpin spam;
- frame time with 1, 16, 32 and 48 retained world flashes;
- allocation profile for repeated ring/ribbon rendering;
- HUD fill-call count during Resonance overlay.

These measurements are evidence for future work. They are not permission to add custom batching or RenderTypes in the same refactor.

---

## 8. Preserved invariants

Every implementation PR must preserve these rules.

### 8.1 Authority

A cue is visual-only. Client VFX code must never:

- deal damage;
- apply marks;
- start cooldowns;
- select targets;
- move entities;
- open gameplay UI;
- decide whether an ability succeeded.

### 8.2 Todo feint contract

The real swap and feint share the `BOOGIE_WOOGIE` clap cue. Anything that only a completed swap earns belongs in `SWAP_ENDPOINT`, `SWAP_AFTERIMAGE`, `SWAP_ARRIVAL` or another completion-only cue.

Do not add displacement-only camera, sound, HUD or world feedback to the shared clap recipe.

### 8.3 Direction magnitude

`direction` is normalized. Any meaningful magnitude must travel elsewhere, normally `anchorOffset` or `intensity`, with the payload shape documented.

### 8.4 World-fixed ownership

Whether a retained world style follows an anchor is a property of the style. Do not rebuild a second list or switch that can disagree with `ImpactStyle`.

### 8.5 Persistent visuals

Persistent state belongs to the entity or state renderer:

- nails on the nail renderer;
- dogs on the dog entity/renderer;
- permanent or long-lived marks on the owning gameplay state.

VFX Core owns transient presentation timelines.

### 8.6 Shared hooks

The core owns:

- one world render callback;
- one HUD element;
- one client tick listener;
- the existing shared first-person mixin;
- the existing camera/game-renderer integration.

A new ability does not get its own callback, singleton lifecycle manager, packet receiver or mixin.

### 8.7 Wire stability

The refactor must not:

- reorder `VfxCuePayload` fields;
- rename live `nobara/*`, `todo/*` or `megumi/*` ids;
- add a protocol version field casually;
- change payload meaning without updating the id contract and tests.

---

## 9. Explicit non-goals

The following proposals are not part of completing VFX Core:

- JSON or script-driven recipes;
- an event bus for cues;
- dependency injection for the director;
- service-loaded style renderers;
- reflection-based id discovery;
- a custom rendering engine;
- custom RenderType or batching work without profiling evidence;
- a second payload type for every effect;
- per-effect wrapper records across the whole codebase;
- client-side global time scaling;
- changing the accepted Resonance server hit-stop;
- changing Todo swap locality behavior without a reproduced failure;
- redesigning particle density values;
- changing effect timings, colors, sounds or counts for aesthetic reasons;
- adding new character abilities while this plan is being implemented.

Those may become valid future projects. They require their own product decision and evidence. Smuggling them into a cleanup pass would turn a finite refactor into the traditional software-engineering ritual of rebuilding a working system until nobody remembers why it existed.

---

## 10. Risk register

| Risk | Where | Mitigation |
|---|---|---|
| Collapse fix accidentally double-renders a stronger trail | PR 1 | compare server particles and client recipe separately; change only payload mapping |
| Dead id is actually emitted through an overlooked indirection | PR 2 | repository-wide production-source search and compile before deletion |
| Removing `VfxTimeChannel` changes an undocumented mixin path | PR 2 | verify configured client mixins and all `timeScale` references before deletion |
| Removing `ACTIVE_INSTANCES` exposes hidden lifetime dependence | PR 3 | prove all continuing state lives in channels; test start-once and expiry rejection |
| Emitter coverage source test produces false positives | PR 4 | restrict to `src/main`, exclude definitions/recipes/tests/docs, document limitation |
| Shared factory obscures custom payload semantics | PR 5 | keep specialized Todo/Hairpin methods local and documented |
| Package move changes resource ids | PR 5 | assert exact id strings before and after |
| Clock injection changes camera math | PR 6 | constructor-only seam; production uses `System::currentTimeMillis`; constants untouched |
| World extraction changes geometry accidentally | PR 7 | mechanical moves, focused pure tests, before/after smoke captures |
| Documentation drifts immediately | PR 8 | derive counts from explicit `ALL` sets and registration tests |

---

## 11. Definition of done

VFX Core is considered complete when all conditions below are true.

### Correctness

- Nail Trap collapse carries full displacement and renders toward the target.
- No registered effect id lacks a production emitter.
- No dead channel claims to provide presentation that has no consumer.

### Transport

- `VfxCue` remains an eight-field wire contract.
- Codec round-trip is a JUnit test and covers non-default values.
- All live id strings are pinned.
- Common cue construction goes through the canonical factory where appropriate.

### Registration

- each declared id has exactly one recipe;
- duplicate registration fails;
- unknown ids log once and are ignored;
- each vessel registers recipes only through its client definition hook.

### Lifecycle

- the director retains no fake active-instance list;
- each channel owns its real retained state and cleanup;
- disconnect and level changes clear all transient state;
- the world channel's real cap remains bounded and tested at its boundary.

### Structure

- all `<Character>VfxIds` classes use the canonical shared VFX package;
- all `<Character>VfxRecipes` classes use `client/vfx/<character>`;
- world lifecycle and style geometry are separated;
- adding a new style does not require adding another render callback, mixin or packet receiver.

### Verification

- relevant unit tests run under JUnit 5;
- required red mutations were demonstrated;
- the full quality gate passes;
- the smoke matrix passes on a real client/world;
- no visual change is observed outside the intentional collapse fix.

### Documentation

- canonical docs contain the final live counts;
- stale counts and stale claims are removed;
- accepted limitations remain explicit;
- the architectural freeze rule below is recorded.

---

## 12. Architectural freeze after completion

After the definition of done is satisfied, VFX Core enters freeze.

Normal future character work may add:

1. a new `<Character>VfxIds` set;
2. a new `<Character>VfxRecipes` pack;
3. a new world style implemented through the existing world-style boundary;
4. new particle or sound assets registered through existing shared registries;
5. bug fixes with regression tests.

The following require a separate design review because they change the system rather than extend it:

- a new director channel;
- a new VFX packet or wire-format field;
- a new render/HUD callback;
- a new VFX-specific mixin;
- data-driven recipes;
- a new lifecycle manager;
- client-side gameplay authority;
- global client time scaling;
- custom batching or rendering infrastructure.

Reopen frozen internals only with evidence:

- profiling demonstrates a measurable frame-time or allocation problem;
- a reproducible cue burst shows the 48-world-effect cap drops important presentation;
- a live multiplayer case proves the local-arrival heuristic is wrong often enough to justify a protocol or cue change;
- a fourth or later vessel cannot express a required effect through the existing seven live channels after cleanup.

The burden is on the new requirement, not on the existing architecture to become infinitely abstract in anticipation of hypothetical sorcerers.

---

## 13. Final implementation checklist

- [ ] PR 1: collapse payload fix and regression test
- [ ] PR 2: remove four dead Nobara ids and `VfxTimeChannel`
- [ ] PR 3: remove `ACTIVE_INSTANCES` bookkeeping
- [ ] PR 4: JUnit contract hardening for codec, ids, recipes and emitters
- [ ] PR 5: add `VfxCues` and normalize Megumi package ownership
- [ ] PR 6: inject camera clock and add deterministic tests
- [ ] PR 7: split world rendering by visual family
- [ ] PR 8: update canonical documentation and freeze the core
- [ ] full quality gate green after every PR
- [ ] recorded red runs for contract tests
- [ ] in-game smoke matrix complete
- [ ] no out-of-scope gameplay or aesthetic changes

When every item is complete, VFX Core should stop being an open-ended architecture project. New effort should return to character presentation and gameplay content, which is allegedly why the mod exists.
