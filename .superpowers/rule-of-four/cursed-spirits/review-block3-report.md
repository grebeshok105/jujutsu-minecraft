# Review Block 3 — client render stack

Reviewer: ReviewBlock3. Scope: `src/client/java/jujutsu/mod/client/render/cursedspirit/**`
(9 anim classes, 9 model classes, `CursedSpiritRenderState`, `CursedSpiritModels`,
`CursedSpiritRenderer`, `CursedSpiritClient`), 1-line edit in
`src/client/java/jujutsu/mod/client/JujutsuModClient.java`, tests in
`src/test/java/jujutsu/mod/client/render/cursedspirit/**`.
Requirements in scope: R2 (render half), R21, R24 (visual fit).

Commits under review: `4b9d29d` (first rig), `dc4ab63` (full port) — plus
`ddf5001 chore(cursed-spirits): drop the undriven wistiver GRAVE clip` (landed after
`block-3-report.md`; tree now matches the frozen plan, report § "GRAVE ships" is stale).

## 1. Port fidelity (verified by byte-diff, not eyeball)

Method: python-extracted `createBodyLayer()` bodies and per-clip
`AnimationDefinition` statements from ported vs decompiled sources, compared as strings.

- **All 9 `createBodyLayer()` bodies byte-identical** to their decompiled counterparts
  (lengths equal AND content equal), incl. `FLOATING_CURSE` vs `Modelcurse_ghost`
  (not `Modelcurse`): PROWLER 4574, KELVIN 6165, FLOATING_CURSE 1416, BLUD 3900,
  GULBER 2515, GUZZLER 2769, BUTCHER 4732, WALKING_BED 10119, WISTIVER 8587 chars.
  Port: `.../model/CursedSpirit<Pascal>Model.java` → `createBodyLayer()`;
  decompiled: `.superpowers/rule-of-four/cursed-spirits/decompiled/net/mcreator/sonsofsins/client/model/Model<name>.java`.
- **All shipped clips byte-identical except the one documented retarget**: 32/33
  shipped clip statements identical (PROWLER 4, FLOATING_CURSE 3, GULBER 3, KELVIN
  IDLE/WALK/ATTACK, BUTCHER 4, GUZZLER 3, BLUD 4, WALKING_BED 4, WISTIVER 4).
- **KELVIN SCREAMER retarget confirmed minimal**: ported
  `anim/CursedSpiritKelvinAnimations.java:14` vs decompiled `animation/kelvinAnimation.java:25`.
  After mapping port `leftarm`→`left_arm` (1st channel) and the 2nd-channel `rightarm`→`right_arm`,
  the statements are **identical** — i.e. exactly the frozen rename, nothing else.
  Decompiled SCREAMER bones were `{body,bone4,flater,head,left_arm,left_leg,right_arm,right_leg,rightarm}`;
  ported are the frozen `{body,bone4,flater,head,leftarm,left_leg,rightarm,right_leg}`.
  Observation (per-spec, NOT a defect): the pack had TWO right-side channels (`right_arm` dead +
  `rightarm` live); both now target `rightarm` (2× ROTATION channels, verified via channel-target
  dump). Vanilla applies both (last wins per timestamp). This is what the frozen plan mandates;
  Block 5 should eyeball Kelvin's screamer (manual check M4 below).
- **Dropped clips absent, as frozen**: no `CLIMB` in Prowler anims, no `SIT` in Gulber anims,
  no `TELEPORT_*` in Blud anims, no `MASK`/`CRAWL_*` in WalkingBed anims, no `GRAVE` in Wistiver
  anims (removed by `ddf5001` from both the anim class and `CursedSpiritRigFixtures`).
- **`state.attack → ATTACK` wiring (FLOATING_CURSE)**: ported
  `model/CursedSpiritFloatingCurseModel.java:56-63` applies scream, idle AND attack; the pack's
  `Modelcurse_ghost.setupAnim` applied only screamer+idle. Mandated append, present. No walk
  field/clip — glides, accepted limit.
- **Scream-gated walk**: Prowler (`CursedSpiritProwlerModel.java:89-91`), Kelvin (`:104-106`),
  Butcher, Blud (`CursedSpiritBludModel.java:88-90`), WalkingBed (`:139-141`),
  Wistiver (`:127-129`) all use `if (!state.scream.isStarted())` + `applyWalk(...)` with pack
  args preserved (2.5/4.5 everywhere except Gulber 2.5/4.0, Guzzler 2.0/4.0 — both match their
  decompiled `animateWalk` args). Pack's `DATA_scream_animtime == 0` gate correctly translated.
  Gulber drops the `SIT` apply line and converts the sit-gated walk to unconditional
  (`CursedSpiritGulberModel.java:68-70`); Guzzler keeps pack order with no head lines
  (`CursedSpiritGuzzlerModel.java:62-65`); WalkingBed collapses grave branches to the IDLE branch
  (`CursedSpiritWalkingBedModel.java:135-141`); Wistiver drops the unconditional `GRAVE` apply.
  All per port rules.
- **Head formula**: `head.yRot = state.yRot * 0.017453292f; head.xRot = state.xRot * 0.017453292f`
  in every model that had head lines (all except Guzzler, which has none as authored) —
  identical math to pack's `/ 57.295776f`.
- **Structure rules**: no `root` field, no `root()` override, no `renderToBuffer`, no
  `LAYER_LOCATION`, no `HierarchicalModel` in any of the 9 models (grep-verified; only
  legitimate `root()`/`bake(root())` calls remain). `super(root)` ctor, `EntityModel<CursedSpiritRenderState>`.
  Anim utility private ctors present exactly where the pack had them (gulber/guzzler/wistiver),
  absent elsewhere — matches decompiled.

## 2. Contract-test strength

- Re-ran `gradlew.bat test --tests "jujutsu.mod.client.render.cursedspirit.*"` (Java 21):
  **BUILD SUCCESSFUL** — `CursedSpiritRigContractTest` 5/5, `CursedSpiritAnimationContractTest` 3/3,
  0 failures/errors (matches `block-3-report.md` counts).
- Red-proof records checked against the asserts I read: the five quoted failure messages
  (`bone set for KELVIN/SCREAMER`, `bone set for KELVIN/IDLE`, `looping for KELVIN/IDLE`,
  `clip bone leftarm_renamed ... missing from baked model`, `missing texture ...cursed_kelvin.png`)
  match the assert message formats in `CursedSpiritRigContractTest.java` verbatim.
  I did not re-mutate the tree (read-only scope); the mechanism is sound for structure-level breakage:
  dropped channel → `clipBoneSetsAndCountsMatchFrozen` fires; swapped keys → same; flipped looping →
  `loopingFlagsMatchFrozen`; renamed bone → bone-set + baked-model asserts; deleted texture → `everyTextureExists`.
- Gaps (P3 findings F2/F3 below, plan acceptance still met): `clipKeysUniquePerVariant`'s uniqueness
  half cannot fail by construction (`clipsOf` returns a `Map`); neither suite pins keyframe VALUES,
  exact lengths (`> 0` only), walk args, `setupAnim` order/gates, or box/UV data — a values-level
  regression passes green. Fixtures themselves are hand-copied literals (I cross-checked every
  `put(...)` bone set against the plan's frozen clip map — all match — but no test guards the
  fixtures independently). Value fidelity for this review was established by the byte-diffs in §1.

## 3. Renderer correctness

- Dispatch (`CursedSpiritRenderer.java:26-32`): `rigs.get(state.variant)` then `super.render(...)` —
  TropicalFish shape per plan. Map is an `EnumMap` filled with all 9 variants in
  `CursedSpiritModels.bakeAll` (all 9 `put` lines read); `get` cannot miss. `getTextureLocation`
  (`:50-52`) returns `JujutsuMod.id(state.variant.texture())`, so model and texture always derive
  from the same `state.variant` — no mismatch path.
- `extractRenderState` (`:40-47`): `super` first (walk/schema), then variant + `idle/attack/scream`
  `copyFrom`. Correct Warden pattern; matches the frozen client contract.
- Scale/offset/shadow: `scale()` (`:55-59`) applies `renderScale()` + `translate(0, renderOffsetY(), 0)`;
  shadow radii 0.4/0.4/0.7 per tier instance in `CursedSpiritClient.java`. Variant data
  (`CursedSpiritVariant.java`) matches plan: scales 0.85/0.8/0.9/0.95/0.9/1.0/0.9/0.9/0.85,
  offsetY 0.35 only FLOATING_CURSE, scream null only GULBER/GUZZLER. Note for the manual pass:
  `translate` runs AFTER `scale`, so the 0.35 offset is scaled (×0.8 → 0.28 net) — if the floater
  hovers wrong, check this order first (observation, not a defect).
- `JujutsuModClient.java` diff `main...HEAD`: exactly +2/-0 (import + `CursedSpiritClient.register();`
  after `registerAll()`), as claimed.
- Leak check: `git grep -i sonsofsins -- src/` → zero hits (re-run). `grep -ri gecko` over the new
  client tree → zero. No `client.*`/`EntityModel`/`MobRenderer`/`KeyframeAnimation` imports in
  `src/main` (grep-verified); server→client direction clean. Client→server imports are the sanctioned
  boundary types only (`CursedSpiritEntity`, `CursedSpiritVariant`, `JujutsuEntities`, `JujutsuMod::id`).

## 4. Dead weight

- No shipped clip without a driver: every clip in every anim class is baked + applied in its model
  (FLOATING_CURSE has no WALK clip and no walk driver — consistent; GULBER/GUZZLER have no SCREAMER
  clip and no scream field — consistent; Wistiver GRAVE fully removed by `ddf5001`).
- No leftover debug helpers, TODOs, `System.out`, GeckoLib imports, or `sonsofsins` references.
- Model `ModelPart` fields mirror the pack verbatim per the frozen port rule (they validate hierarchy
  at bake — missing child throws — so they are load-bearing, not dead).
- One nit: F1 (double `bakeAll`), plus test-hygiene notes F2/F3. No dead constants found
  (`NON_LOOPING` matches the frozen 5; `BONES` matches the frozen map).

## 5. Findings

- **F1 (P3): `CursedSpiritRenderer` ctor bakes all rigs twice.**
  `CursedSpiritRenderer.java:19-22` calls `CursedSpiritModels.bakeAll(context)` once for the
  `super(...)` argument and again for `this.rigs` — 18 models baked, 9 discarded at every client init
  (3 renderer instances → 27 wasted bakes). What breaks: nothing functionally; one-time init waste.
  Counter-proposal: bake once and share via a private ctor:
```suggestion
	public CursedSpiritRenderer(EntityRendererProvider.Context context, float shadowRadius) {
		this(context, shadowRadius, CursedSpiritModels.bakeAll(context));
	}

	private CursedSpiritRenderer(EntityRendererProvider.Context context, float shadowRadius,
			Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> rigs) {
		super(context, rigs.get(CursedSpiritVariant.PROWLER), shadowRadius);
		this.rigs = rigs;
	}
```
  Confidence 0.9.
- **F2 (P3): `clipKeysUniquePerVariant` uniqueness assert is tautological + duplicates rig test.**
  `CursedSpiritAnimationContractTest.java:13-24`: `clipsOf` builds a `LinkedHashMap` keyed by field
  name, so "duplicate keys" are impossible; its count half repeats
  `CursedSpiritRigContractTest.clipKeysMatchFrozen`. What breaks: false confidence in one of 3 tests;
  a fixtures typo (wrong bone set literal) passes both suites since both read the same `BONES` literal.
  Counter-proposal: replace with per-variant expected clip-count literals independent of `BONES`
  (4,3,3,4,4,3,4,4,4 in enum order). Confidence 0.8.
- **F3 (P3): value-level fidelity not machine-pinned.**
  Both suites pin structure (keys, bone sets, looping, part existence, texture files) but not keyframe
  values, exact lengths (`clipLengthsPositive` passes any `> 0`), walk args (2.5/4.5 vs 2.0/4.0),
  `setupAnim` order/gates, or box/UV data. What breaks: a values regression (halved keyframe times,
  swapped walk args) stays green. Counter-proposal: assert exact `lengthInSeconds()` per clip against
  authored literals (2.12/0.92/1.36/0.56 prowler, 2.12/0.72/0.56 curse-ghost, 5.04/1.12/0.6 gulber,
  5.04/1.08/1.2/0.56 kelvin, 2.08/1.08/1.36/0.56 butcher, 5.04/1.32/0.56 guzzler, 2.08/1.16/0.76/0.56
  blud, 2.96/1.08/1.3/0.56 walking-bed, 2.08/1.24/0.76/0.56 wistiver). Confidence 0.85.

## 6. Per-requirement verdicts (Block 3 half; live halves owned by Block 5)

- **R2 (render half) → PASS.** 9 distinct rigs × 9 distinct textures × 9 distinct anim sets, all
  present and variant-dispatched from one state field. Evidence: §1 byte-diffs,
  `everyTextureExists` green, `CursedSpiritModels` 9/9 puts, `getTextureLocation` from
  `state.variant`. Remaining (Block 5): noon screenshots per variant + numeric bbox/region diff (M1).
- **R21 → PASS (structure/wiring).** Every shipped clip is baked and applied to its driven state;
  call order, scream gate, walk args, head formula all verified against decompiled in §1.
  Hurt/death have no dedicated clips by design (vanilla flash + `deathTime`; accepted "no VFX" limit).
  Remaining (Block 5): timing/feel — frozen-vs-moving pairs, attack burst capture, scream-gate
  walk-resume observation (M2).
- **R24 (visual fit) → PASS (static sanity).** Scales/offsets/shadows sane and per plan; hitbox rows
  are Block 2's (frozen 0.85×1.0 / 0.75×1.9 / 1.35×2.4) vs render scales that plausibly fit.
  Remaining (Block 5): screenshot-bbox vs AABB comparison per tier + floater hover check (M3).

Manual checks (explicit): M1 variant-distinctness frames + numeric diff, owner eyeball; M2
idle/walk frozen-vs-moving diff, attack burst + motion mask, scream walk-gate resume at ~12 ticks,
hurt flash + death removal; M3 aimed-damage edge test + bbox-vs-AABB per tier, floater hover height
(if off, inspect `CursedSpiritRenderer.scale` translate-after-scale order first); M4 Kelvin screamer
arm motion sanity (double `rightarm` ROTATION channels per frozen retarget).

## 7. Scope verdict

**GO.** `compileClientJava` BUILD SUCCESSFUL (re-run 2026-09-12, Java 21); contract tests 5/5 + 3/3
green; port fidelity proven by exhaustive byte-diffs (9/9 body layers, 32/33 clips identical +
Kelvin retarget-only); dispatch/texture/state/scale wiring correct; zero banned references;
no dead clips or leaks. Findings are P3 hygiene only. Block-3-report red-proofs and `Факт:` lines
spot-checked and consistent (one staleness: GRAVE paragraph superseded by `ddf5001`, tree is now
per-plan).
