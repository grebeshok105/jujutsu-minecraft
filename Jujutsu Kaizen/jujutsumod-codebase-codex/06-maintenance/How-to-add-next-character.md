# How to Add the Next Character

Status: CURRENT

Two vessels ship (Nobara, Todo) plus NONE, so the shared contracts already exist and were extracted from real code rather than guessed. This note is the procedure for the **third** vessel. Do not invent a further layer of abstraction on top of these seams; extend them.

Read [Vessel definitions](../02-architecture/Vessel-definitions.md) first — it is the contract this procedure fills in — then [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) for the drawing half.

The shape of the whole job: **one enum constant, one server definition, one client definition, one line in each registry, and assets.** `registerServerHooks` and `registerClientHooks` exist precisely so that neither `JujutsuMod.onInitialize` nor `JujutsuModClient` is ever edited for a new vessel — both just loop their registry.

## 1. Design, before code

Write and approve the fantasy, combat loop, counterplay, controls, and VFX language. Nothing below is worth doing for an unapproved kit.

## 2. Roster constant

Add the constant to `JujutsuCharacter`. This is the step that makes the compiler do the work for you: `JujutsuCharacters.definition` and `JujutsuCharacterClients.definition` both switch on `JujutsuCharacter` **exhaustively with no `default`** (VERIFIED), so the build fails in exactly those two places until the new constant is bound to a server definition and a client definition. Nothing else on either side switches on a vessel.

## 3. Server definition

Write `<New>Definition implements CharacterDefinition` beside the vessel's runtimes and bind it in `JujutsuCharacters` (one field, one switch arm). Only `id()` and `tryCast` are required; everything else defaults to doing nothing.

- `tryCast` delegates to your own `<New>AbilityRouter`: a slot map over `CharacterAbility`, **exhaustive with no `default`**, answering `false` explicitly on the input positions the vessel does not use — the pattern both shipped routers follow (`NobaraAbilityRouter`, `TodoAbilityRouter`). The shared executor already owns the not-selected and cooldown gates; anything that is yours alone (Nobara's stagger check, her single fallback message) belongs in your router. Do not add a parallel payload per ability; every vessel's abilities arrive over `CharacterAbilityPayload`.
- `registerServerHooks()` installs your event-driven runtimes once at mod init. `CharacterDefinitionRegistryTest` reads the expected list off the source tree: a class under your vessel's package exposing `register()` that your definition never calls fails the build.
- Override `applyAttributes`/`removeAttributes`, `adjustIncomingStaggerTicks`, `onSelected`/`onDeselected`, or `canonicalSlot` (fold two inputs the vessel treats as one, like Todo's Shift+B → B) only if the vessel actually does those things. Clear static state on `SERVER_STOPPING` inside your own runtimes.
- Put tuning constants in one `<New>Profile` class, following `TodoProfile` / `ProjectJjkNobaraProfile`.

## 4. Client definition

Write `<New>ClientDefinition implements CharacterClientDefinition` and bind it in `JujutsuCharacterClients`. Required: `id()` and `rosterEntry()` — the card's name/role/subtitle keys, portrait, and input strip listing what your router actually answers. Then:

- `skinAnimation()` returns your GeckoLib-to-vanilla `PlayerModel` adapter, or `null` to keep the ordinary vanilla pose.
- `playerSkin()` declares the replacement skin path once; the skin mixin and the roster portrait both read it.
- `accent()` / `warmth()` are what the ClickGui shell eases toward; `rosterOrder()` places the card (vessels first, NONE last).
- `registerClientHooks()` registers your entity renderers and `<New>VfxRecipes` — see step 7.
- `moduleName` / `moduleDescription` fill your row in the Characters tab.

The card, the theme, the module row, and skin-animation dispatch are all derived from the registry — `CharacterRosterPanel`, `ClickGuiTheme`, `JujutsuModules`, `CharacterSkinAnimationRenderer` and `CharacterSkinMixin` need no edits.

## 5. Render stack — adapt the vanilla player, do not fork

| Piece | Base to extend | What the subclass supplies |
|---|---|---|
| Adapter | `CharacterSkinAnimationAdapter<A>` | animatable instance, skin animation model, and any vessel-specific render-state data |
| Model | `CharacterSkinAnimationModel<A>` | animation `ResourceLocation`, `headLookWeight`, `actionKeyframedIsPlaying` |
| Rig | `geckolib/models/character_skin/<id>.geo.json` | shared humanoid bone names and no visible cubes/UVs |
| Animatable | a `GeoReplacedEntity` singleton | animation controllers and the movement/action predicates |

The adapter evaluates the existing clips and maps `root`, `body`, `head`, arms and legs to the live
vanilla `PlayerModel`. Keep unsupported Blockbench-only bones evaluation-only; do not add visible Geo
geometry back to the player path. Megumi's per-player swing sequence belongs in his adapter.

The bridge reaches the game through your client definition's `skinAnimation`, not through a switch arm anywhere.

## 6. Assets

GeckoLib 5 indexes only `assets/<ns>/geckolib/models/**` and `assets/<ns>/geckolib/animations/**`. Place the `.geo.json` and `.animation.json` there and nowhere else. See [Assets and resources](../02-architecture/Assets-and-resources.md) for the trap that the legacy `geo/` tree still sets.

## 7. Presentation

Add `<New>VfxIds` and `<New>VfxRecipes` and register the pack from your client definition's `registerClientHooks()` — the aggregate `JujutsuVfxRecipes` no longer exists, so the list of who has recipes cannot drift from the list of who exists. Own your cue ids; Todo's reuse of `NobaraVfxIds.BLACK_FLASH` is a known seam, not a pattern to copy (see [Todo Boogie Woogie](../03-systems/Todo-Boogie-Woogie.md)).

If the vessel needs a first-person hand treatment, add a `VfxFirstPersonChannel.Style` and handle it in `FirstPersonHandFxMixin` — do not add a new mixin for one pose.

## 8. Strings

Localized strings in both `en_us` and `ru_ru` for every key your `rosterEntry()` and your refusal messages name. The card itself needs no panel edit — see step 4.

## 9. Verification

Deterministic unit tests as JavaExec verification programs (add yours to the group and it joins `check` dynamically), plus real `runClient` smoke — rendering, animation, and combat feel are not provable by compilation. `./gradlew verifyAssertionsEnabled` reports the live program inventory. `CharacterDefinitionRegistryTest` and `CharacterClientRegistryTest` already cover the binding itself: that your definitions claim the right constant, that every `register()` under your package is called, and that nothing client-only leaks into `src/main`. Then update SESSION.md for the handoff, AGENTS.md only for durable decisions, and the Codex notes that the change makes stale.
