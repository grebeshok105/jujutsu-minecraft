# How to Add the Next Character

Status: CURRENT

Two vessels ship (Nobara, Todo) plus NONE, so the shared contracts already exist and were extracted from real code rather than guessed. This note is the procedure for the **third** vessel. Do not invent a further layer of abstraction on top of these seams; extend them.

Read [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) first — it is the contract this procedure fills in.

## 1. Design, before code

Write and approve the fantasy, combat loop, counterplay, controls, and VFX language. Nothing below is worth doing for an unapproved kit.

## 2. Roster constant

Add the constant to `JujutsuCharacter`. This is the step that makes the compiler do the work for you: `CharacterGeoRenderers.create` switches on `JujutsuCharacter` **exhaustively with no `default`**, so the build fails until the new constant either declares a renderer or explicitly opts into vanilla with `null` (VERIFIED — CharacterGeoRenderers.create). `CharacterAbilityExecutor.tryCast` switches the same way (VERIFIED).

## 3. Render stack — subclass, do not fork

| Piece | Base to extend | What the subclass supplies |
|---|---|---|
| Renderer | `CharacterPlayerGeoRenderer<A, R>` | model instance, animatable instance, `addRenderLayer` calls, `withScale` |
| Model | `CharacterPlayerGeoModel<A>` | model/texture/animation `ResourceLocation`s, `headLookWeight`, `actionKeyframedIsPlaying` |
| Held items | `CharacterHeldItemLayer<A, R>` | the two hand bone names, nothing else |
| Animatable | a `GeoReplacedEntity` singleton | animation controllers and the movement/action predicates |

Compare `NobaraPlayerGeoRenderer` and `TodoPlayerGeoRenderer`: each is under 20 lines. If a third vessel needs more than that, the seam is wrong — fix the base class rather than overriding `renderCharacter`, which is `final` on purpose.

Then add the `case <NEW> -> new <New>PlayerGeoRenderer<>(context);` arm in `CharacterGeoRenderers.create`.

## 4. Assets

GeckoLib 5 indexes only `assets/<ns>/geckolib/models/**` and `assets/<ns>/geckolib/animations/**`. Place the `.geo.json` and `.animation.json` there and nowhere else. See [Assets and resources](../02-architecture/Assets-and-resources.md) for the trap that the legacy `geo/` tree still sets.

## 5. Server behaviour

- Route active techniques through the shared slot: `CharacterAbility` + `CharacterAbilityExecutor.tryCast`, which already owns the not-selected and cooldown gates. Add a `case <NEW> -> <New>AbilityRouter.tryCast(...)` arm and write that router as your own slot map, the pattern both shipped vessels now follow (`NobaraAbilityRouter`, `TodoAbilityRouter`): switch on `CharacterAbility` **exhaustively with no `default`** so a new slot fails compilation instead of falling into whichever arm a `default` would have picked, and answer `false` explicitly on the input positions your vessel does not use. Anything that is yours alone belongs in your router rather than the shared executor — Nobara's stagger check and her single fallback message are the shipped example. Do not add a parallel payload per ability; every vessel's abilities arrive over `CharacterAbilityPayload`.
- Put tuning constants in one `<New>Profile` class, following `TodoProfile` / `ProjectJjkNobaraProfile`.
- Register any tick/disconnect/stop lifecycle from `JujutsuMod.onInitialize`, and clear static state on `SERVER_STOPPING` — this is existing debt, do not add to it.

## 6. Presentation

Add `<New>VfxIds` and `<New>VfxRecipes` and wire them through `JujutsuVfxRecipes.registerAll()`. Own your cue ids; Todo's reuse of `NobaraVfxIds.BLACK_FLASH` is a known seam, not a pattern to copy (see [Todo Boogie Woogie](../03-systems/Todo-Boogie-Woogie.md)).

If the vessel needs a first-person hand treatment, add a `VfxFirstPersonChannel.Style` and handle it in `FirstPersonHandFxMixin` — do not add a new mixin for one pose.

## 7. UI and strings

Add a `CharacterCard` entry to `CharacterRosterPanel.CARDS` and localized strings in both `en_us` and `ru_ru`.

## 8. Verification

Deterministic unit tests as JavaExec verification programs (there are 28; add yours and it joins `check`), plus real `runClient` smoke — rendering, animation, and combat feel are not provable by compilation. Then update SESSION.md for the handoff, AGENTS.md only for durable decisions, and the Codex notes that the change makes stale.
