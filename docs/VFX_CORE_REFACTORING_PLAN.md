# VFX Core: verified refactoring and completion plan

Status: **implementation-ready specification, revision 2**  
Repository baseline: `main` at `e3ee14480cff557b9c409a01465924521fecd4ad`  
Scope: correctness, transport contracts, ownership, cleanup, tests, package layout, implementation order, smoke verification, documentation, and architectural freeze for the existing VFX Core  
Out of scope: implementing the plan in this PR, changing gameplay balance, redesigning existing effects, adding abilities, replacing assets, or creating a second VFX framework

## 1. Purpose

This document replaces two independent audits and their follow-up reviews with one code-verified implementation plan.

The audits were useful, but neither was fully accurate. The follow-up reviews corrected additional weaknesses in the first revision of this plan. This revision keeps only conclusions supported by the repository at the baseline commit and records the process constraints needed to implement them safely.

The plan has nine goals:

1. fix the confirmed Nail Trap collapse payload defect;
2. remove dead ids, dead recipes, dead time-channel plumbing, and fake director lifecycle;
3. centralize common cue construction before the correctness fix uses it;
4. make payload reading safer where Todo currently packs three values into one vector;
5. define ownership for delivery radius, presentation radius, and visual duration;
6. harden codec, id, recipe, emitter, and packed-intensity contracts without duplicating existing tests;
7. normalize package ownership and split the world-rendering monolith without changing visuals;
8. define a finite, reviewable implementation sequence and smoke matrix;
9. freeze VFX Core after the completion criteria are met.

The current architecture is fundamentally sound. This is a completion and hardening pass, not a rewrite.

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

Registration order is deliberate:

```text
VfxDirector.initialize()
    -> JujutsuCharacterClients.registerAll()
    -> each CharacterClientDefinition.registerClientHooks()
    -> each vessel registers its own recipe pack
```

The architecture therefore has one aggregate client bootstrap without an aggregate recipe list that can drift from the roster.

### 2.2 Wire contract

`VfxCue` transports eight fields:

1. `effectId`
2. `origin`
3. `anchorEntityId`
4. `anchorOffset`
5. `intensity`
6. `startGameTime`
7. `seed`
8. `direction`

`VfxCuePayload.STREAM_CODEC` writes and reads those fields in a fixed order. Field order and live effect-id strings are wire-compatibility concerns.

`VfxCue` normalizes `direction`. A magnitude that must survive transport cannot be stored only in `direction`.

The broadcast radius is not part of the cue. It is selected by each server emitter. Client recipes separately choose a proximity attenuation radius.

### 2.3 Effect ids

At the baseline commit:

| Owner | Declared ids | Registered recipes | Confirmed production-emitted ids |
|---|---:|---:|---:|
| Nobara | 25 | 25 | 21 |
| Todo | 7 | 7 | 7 |
| Megumi | 5 | 5 | 5 |
| **Total** | **37** | **37** | **33** |

The four registered Nobara ids without a production emitter are:

- `RESONANCE_CHANNEL`
- `RESONANCE_STRIKE`
- `LINK_BIND`
- `EMBEDDED_NAIL_DRIVE`

Their recipes are aliases or animation wrappers. They are dead contract surface, not planned behavior.

### 2.4 Director-owned channels

`VfxDirector` owns eight channels:

1. `VfxWorldChannel`
2. `VfxHudChannel`
3. `VfxCameraChannel`
4. `VfxFirstPersonChannel`
5. `VfxParticleChannel`
6. `VfxSoundChannel`
7. `VfxPostProcessChannel`
8. `VfxTimeChannel`

`VfxContext` exposes the same eight.

`VfxQuality` and `VfxPalette` are supporting types. `VfxSoundDuck` is a helper inside the sound path. None is a ninth channel.

After removal of the dead time channel, seven live channels remain. Seven is a cleanup result, not an assumed target.

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

`worldFixed` is correctly owned by each style.

Two current caps have different meanings:

- `VfxDirector.MAX_ACTIVE_INSTANCES = 64` bounds bookkeeping records whose start callback has already run;
- `VfxWorldChannel.MAX_IMPACT_FLASHES = 48` bounds retained world-render state.

The first is misleading lifecycle. The second is a real visual-state cap.

### 2.6 Existing tests that must not be duplicated

The repository already covers:

- cue field preservation;
- real payload codec round-trip;
- selected stable Nobara id paths;
- timeline age, expiry, opening-beat, future-cue, and late-window behavior;
- anchor resolution and fallback;
- sound-duck arithmetic and ownership;
- first-person timing;
- several pure world-style calculations.

`VfxCueTest.assertPayloadRoundTripsCue()` already encodes and decodes through the real `VfxCuePayload.STREAM_CODEC`.

The test is still incomplete:

- it is a legacy `main()` plus Java `assert` program;
- its direction is zero;
- it does not exercise `NO_ANCHOR`;
- it does not prove a non-unit direction is normalized before transport;
- it pins `NobaraVfxIds.RESONANCE_STRIKE`, which this plan removes.

The correct task is to migrate and expand the existing test, not create a duplicate golden test.

---

## 3. Confirmed correctness defects and dead behavior

### 3.1 P0: Nail Trap collapse loses travel distance

#### Current emitter contract

`NailTrapRuntime` emits `NAIL_TRAP_COLLAPSE` with:

- `origin = from`;
- `anchorOffset = Vec3.ZERO`;
- `direction = to - from`.

#### Current recipe contract

`NobaraVfxRecipes.nailTrapCollapse` reads:

```java
Vec3 travel = cue.anchorOffset();
```

It samples the line from `origin` to `origin + travel`.

#### Failure

The emitter writes displacement to `direction`, while the recipe reads `anchorOffset`. Reading `direction` instead would still be wrong because `VfxCue` normalizes it and destroys distance.

The client recipe therefore reconstructs a zero-length path. A separate server particle trail partially masks the defect in-game, but the VFX Core cue contract is broken.

#### Required correction

The full `to - from` displacement must travel in `anchorOffset`. `direction` may carry normalized orientation, but not the only copy of magnitude.

The corrected cue must be built through the canonical `VfxCues` factory introduced before this fix. Do not create a temporary local seam that the next PR deletes.

#### Regression contract

A pure test must prove:

- non-unit displacement survives in `anchorOffset`;
- `origin + anchorOffset == target`;
- non-zero `direction` is normalized;
- zero-distance input is safe;
- codec round-trip preserves the corrected fields.

### 3.2 P1: `VfxTimeChannel` has writers but no consumer

Production recipes call:

- `triggerSlowMotion(0.55f, 2000, ...)` for `DOLL_STRIKE`;
- `triggerSlowMotion(0.5f, 2000, ...)` for `RESONANCE_RELEASE`.

`VfxTimeChannel` stores a scale and `VfxDirector.timeScale()` exposes it, but no renderer, delta tracker, game loop, or configured mixin reads the value.

This is not an unused call site. It is a live write path with no read path, which makes the advertised effect inert.

#### Required correction

Remove:

- `VfxTimeChannel`;
- `VfxDirector.TIME`;
- the `VfxContext.time` field, constructor parameter, and accessor;
- `VfxDirector.timeScale()`;
- both `triggerSlowMotion` calls;
- tests that exist only for the dead channel.

Do not replace it with a time-scaling mixin. Resonance already has accepted server-global hit-stop. Client-only slow motion is a separate product decision.

The cleanup PR must add an accepted-decision entry to `docs/KNOWN_ISSUES.md` stating that the attempted client slow-motion path was never implemented, was deliberately removed, and must not be reintroduced casually through a mixin.

### 3.3 P1: four dead ids and recipes

Delete:

- `RESONANCE_CHANNEL`;
- `RESONANCE_STRIKE`;
- `LINK_BIND`;
- `EMBEDDED_NAIL_DRIVE`.

Delete their aliases or wrapper registrations. Preserve every live id string unchanged.

`VfxCueTest.assertNobaraEffectIdsStayStable()` currently pins `nobara/resonance_strike`. The cleanup PR must replace that assertion with a live id before deleting the constant. `VfxTimelineTest` pins only live Straw Doll ids and should remain valid.

### 3.4 P1: `ACTIVE_INSTANCES` is fake lifecycle

`VfxDirector.receive` creates an instance and calls `instance.start(...)` once. Continuing state then lives in channels:

- world flashes in `VfxWorldChannel`;
- camera impulses in `VfxCameraChannel`;
- HUD windows in `VfxHudChannel`;
- first-person state in `VfxFirstPersonChannel`;
- sound duck in `VfxSoundChannel`;
- blur in `VfxPostProcessChannel`;
- particles and sounds are emitted immediately.

Removing an `ActiveInstance` record does not stop any of those effects. Evicting the oldest record at 64 does not evict presentation.

#### Required correction

Delete:

- `ACTIVE_INSTANCES`;
- `ActiveInstance`;
- `MAX_ACTIVE_INSTANCES`.

`receive` should resolve, create, reject if expired, compute initial age, and start once. Director tick should retain only work the director genuinely owns.

Do not replace the list with `ArrayDeque`, priorities, or smarter eviction. That would optimize misleading bookkeeping.

---

## 4. Structural debt and contract ownership

### 4.1 Package asymmetry

Canonical ownership is:

```text
src/main/java/jujutsu/mod/vfx/<Character>VfxIds.java
src/client/java/jujutsu/mod/client/vfx/<character>/<Character>VfxRecipes.java
```

Nobara and Todo follow it. Megumi currently places ids and recipes under character-specific package trees.

Move Megumi ids and recipes to canonical VFX packages. Preserve all five `megumi/*` strings.

### 4.2 Common cue construction

Several runtimes repeat the same transport shapes:

- world-fixed;
- world-fixed directed;
- entity-anchored;
- entity-anchored directed;
- world-fixed with full displacement.

Introduce one small shared `VfxCues` factory in `jujutsu.mod.vfx`.

Recommended surface:

```java
VfxCues.worldFixed(...)
VfxCues.worldFixedDirected(...)
VfxCues.worldFixedDisplacement(...)
VfxCues.anchored(...)
VfxCues.anchoredDirected(...)
```

The factory owns transport mechanics:

- `NO_ANCHOR`;
- anchor delta calculation;
- intensity clamping;
- game time;
- seed;
- normalized orientation;
- preserving full displacement outside `direction`.

It must not hide effect semantics behind dozens of effect-specific factory methods.

### 4.3 Narrow Todo read model

Reject one wrapper per effect. Accept one narrow typed reader where it pays for itself.

`SWAP_ARRIVAL` overloads `anchorOffset` as three unrelated values:

- speed;
- body width;
- body height.

The recipe reads this convention directly. That is the same class of producer-consumer disagreement that caused the collapse bug.

Introduce a small client-side value object such as:

```java
TodoSwapArrivalPayload.from(VfxCue cue)
```

It should expose named accessors for speed, body width, body height, and direction. It does not change the wire format and does not create a general typed-cue hierarchy.

No wrapper is required for simple cues whose fields already have their ordinary meaning.

### 4.4 Delivery-radius and presentation-radius ownership

Server delivery and client attenuation currently use independent constants and literals.

Verified examples include:

- Todo broadcasts swap cues with `TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS = 64.0`, while the clap recipe attenuates with `56.0`;
- Megumi owns `MegumiProfile.VFX_CUE_RADIUS = 48.0` on the server side;
- Nobara recipes contain proximity literals including `40.0`, `48.0`, `56.0`, and `64.0`;
- at least one Nobara runtime broadcasts through a separate `56.0` constant.

These values do not always need to be equal. Delivery can intentionally be wider than full-strength presentation. They must, however, obey one explicit policy.

#### Required policy

Each live effect family must identify:

- **delivery radius**: the maximum distance at which a client receives the cue;
- **presentation radius**: the distance used for sound, HUD, camera, or post-process attenuation.

Required invariant:

```text
presentation radius <= delivery radius
```

If the values are intended to match, they must share one named constant. If they intentionally differ, both must be named and the reason documented beside them.

A client radius larger than delivery radius is misleading because its outer range is unreachable. A delivery radius much larger than every presentation radius wastes packet fan-out.

Do not move gameplay targeting ranges into VFX constants. This policy applies only to cue delivery and presentation attenuation.

### 4.5 Duration ownership

Recipes frequently declare both:

```java
VfxInstance.of(durationTicks, ...)
context.world().triggerImpact(..., durationTicks)
```

When both values describe the same visual lifetime, one named local or constant must own the duration and feed both calls.

Do not enforce blind equality globally. Some recipes intentionally keep a recipe alive longer than one world-style component, such as a long Black Flash recipe with a shorter retained world impact.

Required rule:

- equal semantic lifetime: one value, reused;
- intentionally different lifetimes: two named values plus a comment or test describing the relationship;
- no repeated anonymous numeric pair that can drift silently.

### 4.6 Packed Hairpin intensity

Hairpin uses an integer payload that carries both depth and a finale flag through `hairpinExplosionIntensity(depth, finale)` and its reader.

This is a packed transport contract and needs focused tests:

- pack then unpack preserves clamped depth;
- depth is clamped to `1..3`;
- finale survives independently of depth;
- non-finale and finale values cannot collide for the supported depth range.

The test belongs in the contract-hardening PR, not in a new framework.

### 4.7 Camera determinism

`VfxCameraChannel` reads wall-clock time directly. Inject a package-private `LongSupplier` clock, matching the established first-person approach.

Tests must cover:

- expiry;
- overlapping impulses;
- yaw and pitch clamps;
- FOV clamps;
- future starts;
- late offsets;
- bounded relative strength between swap snap, heavy impact, explosion, and Black Flash.

Do not change curves in the same PR unless a failing test demonstrates a defect.

### 4.8 World-channel monolith

`VfxWorldChannel` correctly owns lifecycle, the real cap of 48, age, expiry, anchor resolution, render-buffer acquisition, and style dispatch.

It also owns every geometry implementation in one large file.

Keep it as lifecycle owner and dispatcher. Extract visual families:

```text
client/vfx/world/
    HairpinWorldEffects.java
    BlackFlashWorldEffects.java
    SwapWorldEffects.java
    ShadowWorldEffects.java
    VfxWorldGeometry.java
```

Suggested ownership:

- Hairpin: `HAMMER_SEND`, `ENLARGE`, `EXPLOSION`, `RITUAL_BIND`, `DOLL_STRIKE`, `RESONANCE_RELEASE`;
- Black Flash: `BLACK_FLASH`;
- Swap: `BOOGIE_WOOGIE`, `SWAP_AFTERIMAGE`, `SWAP_ARRIVAL`;
- Shadow: `MEGUMI_SHADOW_OPEN`, `MEGUMI_SHADOW_CLOSE`;
- Geometry: ribbon, basis, side-vector, and shared vertex helpers.

Do not add reflection, service loading, dependency injection, a renderer registry, or one class per enum constant.

### 4.9 P3 decisions

#### Todo legacy `NO_ANCHOR` fallback

`TodoAnimationHooks` falls back to the local nearby player for legacy `NO_ANCHOR` clap broadcasts.

During the factory migration:

1. prove every live `BOOGIE_WOOGIE` emitter supplies the caster anchor;
2. if true, remove the fallback and add a focused test or source contract;
3. if any live route still needs it, keep it and document that route explicitly.

Do not preserve a branch merely because its comment says “legacy”. Do not remove it on assumption either.

#### Tiny recipe helpers

`random`, `isLocalAnchor`, and similar helpers are duplicated between recipe packs.

This duplication is accepted. They are small, vessel-local, and often differ subtly in semantics. Do not create a shared utility class solely to remove a few lines.

---

## 5. Corrected conclusions from the audits

### 5.1 Accepted

- collapse payload is wrong;
- four Nobara ids and registrations are dead;
- `VfxTimeChannel` has no consumer;
- `ACTIVE_INSTANCES` is bookkeeping, not global visual lifecycle;
- existing codec and timeline tests must be expanded, not duplicated;
- cue construction should be centralized before the collapse fix;
- one narrow Todo arrival reader is justified;
- radius and duration ownership need explicit invariants;
- Hairpin packed intensity needs a contract test;
- camera timing needs an injectable clock;
- Megumi package layout should be normalized;
- `VfxWorldChannel` should be split by visual family;
- VFX Core should freeze after a finite completion pass.

### 5.2 Corrected or rejected

- “There are nine channels.” Incorrect. There are eight.
- “Nobara has 24 ids.” Incorrect. There are 25.
- “Todo has four ids.” Stale. There are seven.
- “Megumi has four ids.” Stale. There are five.
- “There are 32 ids.” Incorrect. There are 37 declared and 33 production-emitted.
- “Codec round-trip is missing.” Incorrect. It exists but needs migration and stronger cases.
- “The 64 cap drops visible effects.” Incorrect. It drops bookkeeping records only.
- “Replace the active list with a better queue.” Rejected. Remove it.
- “Add `SWAP_ARRIVAL_SELF` during cleanup.” Deferred. It changes behavior and cue count.
- “Create typed wrappers for every cue.” Rejected. Use one narrow Todo reader only where fields are triply overloaded.
- “Never use wrappers.” Also rejected. A small read model is justified when it prevents silent field reinterpretation.
- “All duration values must be equal.” Rejected. Equal semantics share one value; intentional sub-lifetimes remain separate and named.
- “Every declared id must immediately have an emitter in all branches.” Too rigid. Completeness applies to live ids, with an explicit staged-development rule.
- “Redesign the 48-world-effect cap now.” Deferred until profiling or a reproduced presentation loss exists.

---

## 6. Target architecture

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
        recipe registry
        unknown-id logging
        level/disconnect lifecycle
        one world callback
        one HUD element
        one client tick listener

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

effect-specific read model
    client/vfx/todo/TodoSwapArrivalPayload
```

Preserved properties:

- server authority;
- one shared S2C payload;
- eight-field wire format;
- all live id strings;
- visual-only cue semantics;
- character-owned recipe registration;
- one HUD element;
- no effect-specific receiver;
- no effect-specific mixin;
- persistent visuals remain on entity or state renderers;
- late-cue behavior;
- style-owned `worldFixed` policy;
- accepted server-global Resonance hit-stop.

---

## 7. Contract hardening rules

### 7.1 Id lifecycle

Each id must have one explicit state:

- **live**: may be registered and must have at least one production emitter;
- **planned**: temporarily allowed during staged development, must not be presented as complete, and must carry a follow-up issue or PR reference;
- **removed**: constant, recipe, test pin, documentation, and emitter references are deleted together.

A recipe-first then emitter-second workflow is allowed. The first PR must place the id in an explicit `PLANNED` set or equivalent structured allowlist. The second PR promotes it to live and removes the exception.

No permanent comment-only exception is allowed.

### 7.2 Recipe completeness

For live ids:

- each declared id has exactly one recipe;
- each registered recipe id belongs to exactly one vessel id set;
- duplicate registration remains a hard failure;
- planned ids are either unregistered or explicitly excluded by the structured planned set.

### 7.3 Emitter coverage

The coverage check proves a production reference, not world reachability.

Preferred implementation: scan compiled production bytecode for field references to `*VfxIds` constants from emitter classes. A bytecode field-reference scan cannot be satisfied by comments, documentation, test code, or an unrelated string literal.

Acceptable fallback: an AST-aware source scan restricted to production Java call arguments.

Do not use a raw repository-wide string search. It can turn a comment into a false green, which this repository has already demonstrated is an alarmingly easy human achievement.

Recognized emission paths must include direct and factory-based construction, including:

- `new VfxCue(...)`;
- `VfxCues.*(...)`;
- local helpers that receive a concrete id at a production call site;
- `JujutsuNetworking.broadcastVfxCue(...)`;
- `JujutsuNetworking.sendVfxCue(...)`.

Recorded red runs must include removing the only production emitter reference for one live id.

### 7.4 Codec contract

The migrated JUnit test must cover:

- all eight fields;
- non-default origin and offset;
- `NO_ANCHOR`;
- a real anchor id;
- non-zero normalized direction;
- zero direction;
- non-default intensity, game time, and seed;
- equality after real `STREAM_CODEC` encode/decode.

### 7.5 Radius contract

For every live effect family:

- delivery radius is named;
- presentation radius is named or intentionally shares the delivery constant;
- presentation radius does not exceed delivery radius;
- any intentional gap has a comment or test;
- gameplay target range is not reused as a visual radius by accident.

### 7.6 Duration contract

For each recipe with retained channel state:

- shared semantic lifetime uses one value;
- intentional sub-lifetimes use separately named values;
- tests cover at least one equal-lifetime recipe and one intentional split-lifetime recipe;
- no anonymous duplicated pair is allowed to drift.

---

## 8. Implementation sequence

The work should ship as nine small PRs. The order prevents temporary seams and separates behavior changes from mechanical extraction.

### PR 1: canonical cue foundations

**Scope**

- add minimal `VfxCues`;
- include the world-fixed displacement shape needed by collapse;
- add pure JUnit tests for factory field ownership;
- add named radius and duration contract conventions to canonical VFX documentation;
- do not migrate every runtime yet.

**Acceptance**

- factory preserves exact origin, anchor, offset, intensity, game time, seed, and orientation semantics;
- full displacement never relies only on normalized `direction`;
- no id string or codec field changes;
- no visual behavior changes;
- quality gate passes.

### PR 2: fix Nail Trap collapse payload

**Scope**

- construct collapse cues through `VfxCues.worldFixedDisplacement` or the equivalent canonical method;
- place full `to - from` in `anchorOffset`;
- retain normalized direction only as orientation;
- extend codec and collapse regression tests;
- run short-distance, long-distance, and zero-distance smoke.

**Allowed behavior change**

- client collapse trails span from each nail to the target.

**Forbidden collateral change**

- particle counts;
- sounds;
- trap timing;
- damage;
- collapse cadence;
- generic wire shape.

**Acceptance**

- endpoint reconstruction is exact;
- the test fails against the old emitter mapping;
- server and client trails do not accidentally double in strength;
- quality gate passes.

### PR 3: remove dead VFX surface

**Scope**

- replace the dead `RESONANCE_STRIKE` pin in `VfxCueTest` with a live id;
- remove the four dead ids and recipe registrations;
- remove `VfxTimeChannel` and all plumbing/calls/tests;
- update counts;
- add the accepted slow-motion decision to `docs/KNOWN_ISSUES.md`.

**Acceptance**

- no references remain to removed ids;
- no references remain to `VfxTimeChannel`, `VfxDirector.timeScale`, or `context.time()`;
- every live id string remains unchanged;
- Resonance server hit-stop remains untouched;
- all stable-id tests pin only live ids;
- quality gate passes.

### PR 4: remove fake active-instance lifecycle

**Scope**

- remove `ACTIVE_INSTANCES`, `ActiveInstance`, and `MAX_ACTIVE_INSTANCES`;
- simplify receive and tick;
- add tests for expiry rejection and one-shot start behavior.

**Acceptance**

- accepted cues start exactly once;
- expired cues never start;
- unknown ids warn once and do not start;
- disconnect and level changes clear every real channel;
- `MAX_IMPACT_FLASHES = 48` remains unchanged;
- quality gate passes.

### PR 5: harden transport and completeness contracts

**Scope**

- migrate existing VFX assertion programs in scope to JUnit 5;
- expand the existing codec test;
- add explicit live and planned id enumeration;
- test declared-live ids against recipe registration;
- add precise emitter coverage using bytecode field references or AST-aware matching;
- add Hairpin packed-intensity tests;
- add radius ownership tests;
- add duration ownership tests;
- record red mutations.

**Required red mutations**

1. remove one codec field from encode or decode;
2. remove one recipe registration;
3. remove the only emitter reference for one live id;
4. add a live id without a recipe;
5. move an incomplete id to planned and prove the live check remains green while the planned set remains visible;
6. make a presentation radius exceed delivery radius;
7. change a shared duration at only one of its two consumers;
8. break finale or depth unpacking;
9. restore collapse offset to zero.

**Acceptance**

- comments and string literals cannot satisfy emitter coverage;
- staged recipe-first development remains possible through an explicit planned set;
- every live id has one recipe and at least one production reference;
- codec covers all eight fields and both anchor modes;
- Hairpin depth/finale packing is pinned;
- radius and duration policies are executable rather than prose-only;
- quality gate passes.

### PR 6: package normalization and controlled factory migration

**Scope**

- move Megumi ids and recipes to canonical packages;
- migrate generic cue construction runtime by runtime;
- introduce `TodoSwapArrivalPayload` or equivalent narrow reader;
- verify and remove or retain the Todo legacy `NO_ANCHOR` fallback deliberately;
- leave tiny recipe helpers local by policy.

**Suggested migration order**

1. Megumi summon and dog runtimes;
2. Nobara ritual runtime;
3. Nail Trap runtime;
4. Nobara hammer and Hairpin runtimes;
5. Todo emitters.

Todo goes last because its payloads are the most overloaded.

**Acceptance**

- no wire string changes;
- no field-order changes;
- generic construction no longer repeats transport boilerplate unnecessarily;
- Todo arrival reads named values rather than raw vector components;
- no global wrapper hierarchy appears;
- legacy fallback has an explicit proven disposition;
- quality gate passes.

### PR 7: deterministic camera tests

**Scope**

- inject package-private millisecond clock;
- add JUnit tests for lifetime, overlap, starts, and clamps;
- preserve constants and curves.

**Acceptance**

- no sleeps;
- exact expiry boundaries are testable;
- production uses `System::currentTimeMillis`;
- production behavior is unchanged;
- quality gate passes.

### PR 8: split world rendering by visual family

**Scope**

- extract style rendering and geometry;
- keep lifecycle, cap, age, anchoring, and dispatch in `VfxWorldChannel`;
- preserve enum exhaustiveness and `worldFixed` ownership;
- move pure tests with their helpers.

**Acceptance**

- no intended visual changes;
- no new callback, mixin, packet, registry, reflection, or plugin mechanism;
- cap remains 48;
- before/after captures match for Hairpin, Black Flash, swap, and shadows except the earlier collapse fix;
- quality gate passes.

### PR 9: completion docs and architectural freeze

**Scope**

- update canonical VFX documentation, Codex, `AGENTS.md`, session references, and known issues;
- remove stale counts and resolved debt;
- record accepted limitations;
- copy the freeze rule below into canonical docs.

**Acceptance**

- documentation matches code and tests;
- no stale claims remain about id counts, channel counts, codec coverage, or active-instance behavior;
- planned-id set is empty or every entry has an explicit follow-up reference;
- full smoke matrix passes;
- core is marked frozen.

---

## 9. Test strategy

### 9.1 Fast tests

| Area | Contract |
|---|---|
| `VfxCue` | direction normalization, zero direction, all field preservation |
| Codec | real encode/decode of all eight fields, `NO_ANCHOR`, live anchor |
| Timeline | age, expiry boundary, future cue, opening beat, late windows |
| Anchor resolver | static origin, live anchor, offset, missing-anchor fallback |
| Cue factories | anchor ownership, displacement, clamp, seed/time preservation |
| Collapse | full travel survives and endpoint reconstructs exactly |
| Id sets | live, planned, registered, and vessel ownership relationships |
| Emitter coverage | production bytecode or AST reference, not comments or strings |
| Hairpin pack | depth clamp, finale bit, collision-free supported values |
| Radius | presentation never exceeds delivery |
| Duration | shared lifetime stays shared; intentional split stays named |
| Todo arrival | speed, width, height, and direction read through named accessors |
| Camera | deterministic overlap, expiry, starts, and clamps |
| First person | existing SNAP, CLAP, and SIGN timing remains |
| Sound duck | ownership, extension, and restore remain |
| World math | extracted silhouette, pool, ribbon, and basis calculations |

### 9.2 In-game smoke matrix

#### Nobara

- hammer horizontal and overhead;
- prepared nail launch;
- ordinary impact and local impact sound;
- directed and mass Hairpin;
- enlargement;
- trap placement, armed state, short collapse, long collapse, impact;
- Resonance bind, doll strike, release, and server hit-stop;
- Black Flash;
- Self Resonance.

#### Todo

- real clap and feint share clap presentation;
- aimed swap;
- pair swap;
- landed marker swap;
- body marker swap;
- afterimage dimensions differ for player and wide mob;
- arrival streak preserves velocity;
- local arrival camera applies only to the displaced participant;
- momentum strike can coincide with Black Flash;
- sound duck restores on deadline, menu, disconnect, and level change;
- clap animation still resolves after legacy fallback disposition.

#### Megumi

- player summon sign;
- both dog shadow-open pools;
- recall shadow-close pools;
- Sic marker;
- accepted pounce impact;
- cooldown HUD remains under one VFX HUD element.

#### Cross-system

- unknown id logs once and does not crash;
- reconnect and dimension change clear transient state;
- reduced and minimal particle settings scale client recipe particles;
- direct server particles are recognized as a separate budgeting path;
- presentation attenuation never expects a radius beyond delivery;
- no VFX path applies gameplay state client-side.

### 9.3 Performance evidence

No optimization is approved by this plan. Collect only a baseline around the world split:

- frame time with 1, 16, 32, and 48 retained world effects;
- allocation profile for repeated rings and ribbons;
- HUD fill-call count during heavy Resonance overlays;
- packet fan-out for representative delivery radii;
- client particle counts at each quality level.

Evidence may justify future work. It does not authorize custom batching or RenderTypes in this refactor.

---

## 10. Preserved invariants

### 10.1 Authority

VFX code must never:

- deal damage;
- apply marks;
- start cooldowns;
- select targets;
- move entities;
- decide ability success;
- create gameplay state client-side.

### 10.2 Todo feint

Real swap and feint share the clap cue. Completion-only feedback belongs in completion-only cues.

### 10.3 Direction magnitude

`direction` is normalized. Meaningful magnitude belongs in offset, intensity, or an explicitly documented packed field.

### 10.4 Radius ownership

Delivery and presentation radii are separate concepts. Their relationship must be named and testable.

### 10.5 Duration ownership

One semantic lifetime has one owner. Intentional sub-lifetimes are separately named.

### 10.6 World-fixed ownership

Anchor-follow behavior remains a property of `ImpactStyle`.

### 10.7 Persistent visuals

Long-lived state belongs to the entity or gameplay-state renderer. VFX Core owns transient presentation.

### 10.8 Shared hooks

A new ability does not get its own render callback, HUD element, singleton lifecycle manager, packet receiver, or mixin.

### 10.9 Wire stability

The refactor must not:

- reorder payload fields;
- rename live ids;
- casually add protocol versioning;
- reinterpret a field without tests and nearby documentation.

---

## 11. Explicit non-goals

- JSON or script-driven recipes;
- event bus for cues;
- dependency injection for the director;
- service-loaded renderers;
- reflection-based id discovery;
- custom rendering engine;
- custom batching before profiling;
- one payload type per effect;
- general typed-cue hierarchy;
- client-global time scaling;
- changing Resonance server hit-stop;
- changing Todo locality behavior without a reproduced failure;
- redesigning particle density, colors, sounds, or timings;
- adding abilities during this pass;
- centralizing every three-line vessel-local helper.

---

## 12. Risk register

| Risk | PR | Mitigation |
|---|---:|---|
| Factory becomes a hidden effect API | 1 | keep only transport shapes |
| Collapse fix double-renders stronger trail | 2 | compare server and client trail separately |
| Dead stable-id test blocks cleanup | 3 | replace `RESONANCE_STRIKE` pin first |
| Slow motion is reintroduced casually later | 3 | accepted decision in `KNOWN_ISSUES.md` |
| Overlooked emitter keeps a supposedly dead id alive | 3 | production reference scan before deletion |
| Hidden dependency on active-instance list | 4 | prove channel ownership and start-once behavior |
| Raw source matcher produces false green | 5 | bytecode or AST-aware scan, red mutation |
| Emitter gate blocks staged work | 5 | structured planned-id state |
| Radius constants are equalized when intentionally different | 5 | enforce relation, not universal equality |
| Duration test forces intentional sub-lifetimes equal | 5 | named split-lifetime exception |
| Hairpin packed payload drifts | 5 | pack/unpack and clamp tests |
| Shared factory obscures Todo payloads | 6 | narrow named Todo reader |
| Legacy fallback is removed without proof | 6 | enumerate live clap emitters first |
| Package move changes ids | 6 | pin strings before and after |
| Clock injection changes camera math | 7 | constructor-only seam, constants untouched |
| World extraction changes geometry | 8 | mechanical move, pure tests, captures |
| Documentation drifts immediately | 9 | derive counts from tested live sets |

---

## 13. Definition of done

### Correctness

- collapse carries full displacement and reaches target;
- no live id lacks a recipe or production emitter reference;
- no dead channel claims to provide presentation;
- packed Hairpin intensity is tested;
- Todo arrival payload is read through named accessors.

### Transport

- cue remains eight fields;
- codec test is JUnit and covers non-default values and both anchor modes;
- live id strings are pinned;
- common construction uses `VfxCues` where appropriate;
- normalized direction is never the sole owner of magnitude.

### Radius and duration

- every live effect family has explicit delivery and presentation ownership;
- presentation never exceeds delivery;
- same-lifetime values share one owner;
- intentional sub-lifetimes are named and documented.

### Registration

- every live id has exactly one recipe;
- duplicate registration fails;
- unknown ids log once and are ignored;
- planned ids are explicit and temporary;
- comments and strings cannot satisfy emitter coverage.

### Lifecycle

- director retains no fake active-instance list;
- each channel owns real state and cleanup;
- disconnect and level change clear transient state;
- real world cap remains bounded at 48.

### Structure

- all id and recipe classes use canonical packages;
- world lifecycle and geometry are separated;
- no extra callback, receiver, mixin, registry, or lifecycle manager appears;
- tiny vessel-local helpers remain local unless sharing has semantic value.

### Verification

- relevant tests run under JUnit 5;
- required red mutations were demonstrated;
- full quality gate passes after every PR;
- smoke matrix passes in a real client and world;
- no visual change occurs outside the collapse correction.

### Documentation

- canonical docs contain final counts and extension rules;
- dead slow-motion attempt is recorded as deliberately removed;
- accepted limitations remain explicit;
- freeze rule is recorded.

---

## 14. Architectural freeze

After the definition of done is satisfied, normal character work may add:

1. a new vessel id set;
2. a new recipe pack;
3. a new style through the existing world-style boundary;
4. new assets through existing registries;
5. bug fixes with regression tests.

The following require separate design review:

- new director channel;
- new packet or wire field;
- new render or HUD callback;
- new VFX-specific mixin;
- data-driven recipes;
- new lifecycle manager;
- client gameplay authority;
- global client time scaling;
- custom batching or rendering infrastructure;
- general typed-cue hierarchy.

Reopen frozen internals only with evidence:

- profiling shows a measurable problem;
- a reproduced burst proves the world cap drops important presentation;
- multiplayer proves Todo locality is wrong often enough to change cues;
- a future vessel cannot express required presentation through the seven live channels;
- staged-id workflow becomes a recurring bottleneck despite the planned-id seam.

---

## 15. Final checklist

- [x] PR 1: minimal `VfxCues` foundation and contract tests
- [x] PR 2: collapse payload correction and smoke
- [x] PR 3: dead ids, dead time channel, stable-id test update, known-issue decision
- [x] PR 4: remove fake active-instance lifecycle
- [x] PR 5: codec, ids, recipes, precise emitters, Hairpin pack, radius, duration
- [x] PR 6: package normalization, factory migration, Todo arrival reader, fallback decision
- [x] PR 7: deterministic camera tests
- [x] PR 8: world rendering split
- [ ] PR 9: final docs and freeze
- [x] full quality gate green after every PR
- [x] required red mutations recorded
- [ ] smoke matrix complete
- [x] no out-of-scope gameplay or aesthetic changes

When every item is complete, VFX Core stops being an open-ended architecture project. Future effort returns to character presentation and gameplay content, which is the entire reason this machinery exists.
