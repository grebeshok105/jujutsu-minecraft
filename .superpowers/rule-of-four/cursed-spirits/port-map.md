# Port map — Block 3 (verified against the decompiled pack sources)

Source root: `.superpowers/rule-of-four/cursed-spirits/decompiled/net/mcreator/sonsofsins/client/`
Target root: `src/client/java/jujutsu/mod/client/render/cursedspirit/`

Pack code layout (confirmed): `setupAnim` lives in the **model** class (`Modelprowler.setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)`), the renderer is a thin `MobRenderer` subclass with only `getTextureLocation`. Pack models extend `HierarchicalModel<T>` — gone in 1.21.8; the port target is `EntityModel<CursedSpiritRenderState>` with `super(root)` and no `root` field/`root()` override.

| Variant | Model source | Animation source | Clips in source | Clips to ship |
|---|---|---|---|---|
| PROWLER | `model/Modelprowler.java` | `animation/prowlerAnimation.java` | IDLE, WALK, CLIMB, ATTACK, SCREAMER | IDLE, WALK, ATTACK, SCREAMER (drop CLIMB — unused by `setupAnim`) |
| FLOATING_CURSE | `model/Modelcurse_ghost.java` (verified: its parts are exactly the frozen six; `Modelcurse.java` is **not** the rig used) | `animation/ghost/curse_ghostAnimation.java` | IDLE, ATTACK, SCREAMER | all three; model has no attack line → wire `state.attack → ATTACK`; no WALK clip → glides |
| GULBER | `model/Modelgulber.java` | `animation/gulberAnimation.java` | IDLE, WALK, ATTACK, SIT | IDLE, WALK, ATTACK (drop SIT) |
| KELVIN | `model/Modelkelvin.java` | `animation/kelvinAnimation.java` | IDLE, WALK, ATTACK, SCREAMER | all four; SCREAMER targets `left_arm`/`right_arm` → retarget to `leftarm`/`rightarm` |
| BUTCHER | `model/Modelbutcher.java` | `animation/butcherAnimation.java` | IDLE, WALK, ATTACK, SCREAMER | all four |
| GUZZLER | `model/Modelguzzler.java` | `animation/guzzlerAnimation.java` | IDLE, WALK, ATTACK | all three |
| BLUD | `model/Modelblud.java` | `animation/bludAnimation.java` | IDLE, WALK, ATTACK, SCREAMER, TELEPORT_OUT, TELEPORT_IN | IDLE, WALK, ATTACK, SCREAMER (drop both TELEPORT clips) |
| WALKING_BED | `model/Modelwalking_bed.java` | `animation/walking_bedAnimation.java` | IDLE, WALK, MASK, ATTACK, CRAWL_IDLE, CRAWL_WALK, SCREAMER | IDLE, WALK, ATTACK, SCREAMER (drop MASK + both CRAWL clips) |
| WISTIVER | `model/Modelwistiver.java` | `animation/wistiverAnimation.java` | IDLE, WALK, ATTACK, SCREAMER, GRAVE | all five |

Target class names (PascalCase variant): `CursedSpirit<Pascal>Model` / `CursedSpirit<Pascal>Animations` — e.g. `CursedSpiritFloatingCurseModel`, `CursedSpiritWalkingBedAnimations`.

Reference renderers worth reading before writing (pack behaviour: frame counters, state gating, texture/scale): `renderer/{Prowler,Kelvin,Butcher,Guzzler,Blud,WalkingBed,Wistiver,Curse}Renderer.java` — they are thin in the pack, but `BludRenderer`/`DitchedRenderer`/`EtherGhostRenderer` show the state/texture toggles the tail of the roster used (we do NOT port those systems).

Pack texture paths for reference (what Block 1 already shipped with renamed files):
`textures/entities/{prowler,curse,gubler,kelvin,butcher,guzzler,blud,walking_bed,wistiver}.png` → `assets/jujutsumod/textures/entity/cursed/cursed_<variant>.png`.
