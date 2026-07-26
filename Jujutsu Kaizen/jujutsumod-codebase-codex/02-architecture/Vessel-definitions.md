---
status: CURRENT
---

# Vessel definitions

The invariant: **shared code never asks which character a player is. It asks the vessel.**

A slot is an input position, not an ability. `PRIMARY` is the R key, and what R does is a different thing for each vessel. So the meaning of an input can only live in one place — the vessel's own definition — and never in the input layer, the ability executor, the theme or the roster.

## Two halves, and why they are two

| | Server | Client |
|---|---|---|
| Interface | `jujutsu.mod.character.CharacterDefinition` | `jujutsu.mod.client.character.CharacterClientDefinition` |
| Registry | `JujutsuCharacters` | `JujutsuCharacterClients` |
| Source set | `src/main` | `src/client` |

They are separate interfaces rather than one interface with a client half, and that is not a style preference. A dedicated server loads `CharacterDefinition` and **every implementation of it**. A renderer, a GUI theme or a VFX recipe reachable from that interface would drag client classes onto a machine that has none, and the failure would land at class load, far from the line that caused it. The two halves are joined only by the `JujutsuCharacter` constant they both answer for.

`CharacterClientRegistryTest` walks the entire `src/main` source tree and fails the build on any reference to `net.minecraft.client` or `jujutsu.mod.client`. See [Client-server boundaries](Client-server-boundaries.md).

## Server contract

Required: `id()` and `tryCast(player, slot, notify)`. Everything else has a safe default, so a vessel with no attribute modifiers does not have to say so.

**`id()`** — the constant this definition speaks for. The registry's switch cannot catch an arm wired to the wrong definition, because `case TODO -> NOBARA_DEFINITION` compiles and type-checks. Asking each definition who it thinks it is closes that gap, and `CharacterDefinitionRegistryTest` is what asks. The registry's fields are suffixed `*_DEFINITION` so a transposed arm at least reads wrong at a glance.

**`tryCast`** — `CharacterAbilityExecutor` has already checked that the player has a vessel and that the slot's cooldown is up. Rules belonging to one vessel alone stay in that vessel's router: Nobara's stagger gate and her single fallback message are hers, and Todo has neither. See [Nobara runtime flow](../03-systems/Nobara-runtime-flow.md) and [Todo Boogie Woogie](../03-systems/Todo-Boogie-Woogie.md).

**`registerServerHooks()`** — installs the vessel's event listeners, once, at mod init. Before this hook existed, `JujutsuMod.onInitialize` hand-listed twelve per-vessel `register()` calls, which meant any vessel with event-driven behaviour had to edit mod init — the one shared file this seam was supposed to free. `CharacterDefinitionRegistryTest` reads the expected list **off the source tree**: any class under a vessel's package exposing `register()` that no definition calls fails the build, because a listener that never installs produces no error anywhere and simply does not work.

**`applyAttributes` / `removeAttributes`** — every definition is asked to drop its own modifiers on a selection change, then the selected one adds its own. Sweeping all of them rather than clearing a known set is what let `CharacterCombatModifiers` stop naming Todo's attribute ids. The contract is "remove what you add", and it is held by the definition keeping both in one file.

**`adjustIncomingStaggerTicks(int)`** — scales a stagger the vessel is about to **receive**. Named for the direction because two stagger rules coexist and they are one word apart: this resistance, and Nobara's gate that refuses a cast while she is staggered. The parameter is always greater than zero; an implementation must not turn a request for no stagger into one tick of it.

**`onSelected` / `onDeselected`** — the departing vessel packs up **before** the new selection is stored, so its hook still sees itself selected; the arriving vessel's hook runs after. Both run on every selection change, including re-selecting the same vessel. Todo's mark and pending-swap cleanup lives in `onDeselected`; Nobara's kit restore lives in `onSelected` and is idempotent, so re-selecting her replaces a hammer lost to death without duplicating one still held. See [Character selection](../03-systems/Character-selection.md).

Every vessel runtime already registers its own disconnect, respawn, dimension-change and shutdown cleanup. `onDeselected` covers the one trigger they cannot see — the switch itself.

## Client contract

Required: `id()` and `rosterEntry()`. Every vessel has a name and a card.

**`rosterEntry()`** — the card: name, role and subtitle keys, a portrait, and the input strip in input order. The record's fields are named for what they hold. They used to be name/technique/grade, into which Nobara passed a full name, a role and a grade while Todo passed a name, a technique and a role, so the field names were wrong for one of them either way. See [GUI character select](../04-client-vfx/GUI-character-select.md).

**`createRenderer(context)`** — the GeckoLib renderer that replaces the vanilla player model, or `null` for vanilla. It takes the context rather than returning a finished renderer because `CharacterGeoRenderers.create` is called from a mixin on every renderer rebuild, not once at startup — it must stay a pure factory, safe to call repeatedly. See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

**`playerSkin()`** — the texture that replaces the vanilla skin, driving first-person hands and every vanilla skin path. Declared once here; the skin mixin and the roster portrait both read it. Both used to spell the path out themselves.

**`accent()` / `warmth()`** — what colour the shell eases toward. `ClickGuiTheme` owns the easing and nothing else.

**`rosterOrder()`** — where the vessel sits in the menu. Separate from enum order, which starts with `NONE` so it reads as the absent value, while the menu has always shown it last.

**`registerClientHooks()`** — entity renderers and VFX recipe packs, once at client init, after `VfxDirector.initialize()` because the recipes register into the director it builds. The aggregate `JujutsuVfxRecipes` this replaced was a second hand-kept list of who exists. See [VFX core](../04-client-vfx/VFX-core.md).

**`moduleName` / `moduleDescription` / `moduleStartsEnabled`** — the vessel's row in the Characters tab.

## What guarantees what

Two different mechanisms, and conflating them is how "fail-closed" gets promised and not delivered.

- **Compile-time** — the exhaustive `switch` with no `default`, in exactly two places: `JujutsuCharacters.definition` and `JujutsuCharacterClients.definition`. A new `JujutsuCharacter` constant stops compilation in both until it is bound. Nothing else on either side switches on a vessel.
- **Build-time** — the registry tests. They cover what a switch cannot express: that a definition claims the constant it was bound to, that no client type reached the shared source set, that every vessel runtime exposing `register()` is actually called, and that the sweep used for the attribute clear covers every vessel exactly once.

Both registry tests derive their expectations from `JujutsuCharacter.values()` or from the source tree, never from a list written beside the switch. A second hand-kept list is the one thing that can disagree with the switch without failing compilation.

## The wire seam

`CharacterAbilityPayload` carries the slot **and the vessel the client believed it was casting as**. The server resolves the real vessel itself and uses the claim only for the comparison — it is a claim to be checked, never an instruction.

It exists because the selection menu applies a switch locally and closes before the server has confirmed it. Inside that round trip a key press names the vessel the player has already left, and since a slot means a different ability for each vessel, casting it would fire the wrong one. See [Networking](Networking.md).

Slot ids travel only in `CharacterAbilityPayload` and `AbilityCooldownPayload` and are never written to disk — the persisted value is the character id. New slots append rather than renumber.

Cooldowns key on `(player, vessel, slot)` on both sides. Without the vessel, one vessel's cooldown refused another's ability on the same slot after a switch, and the client believed the ability was up while the server refused it.

## Deliberate residue

Not everything per-vessel is a defect, and these stay on purpose:

- `JujutsuCommands` gates the `hairpin` debug commands on Nobara. A slot is an input position, so `PRIMARY` run as Todo is a teleport; a command named "Hairpin" firing his swap and reporting it as a hairpin is worse than a per-vessel check in a debug command.
- `TodoBlackFlashRuntime` checks that the damaging player is Todo. That is a vessel's own listener filtering for itself, not shared code branching on a vessel.
- `JujutsuKeybinds` still spells out Nobara's two hammers to decide whether left click counts as `ATTACK_CONTEXT`. It is the last vessel-specific line in the input layer and it leaves when the client definition answers "is this stack my technique weapon".
- `TodoSwapMarkerItem` refuses a thrower who is not Todo, on both sides through `CharacterSelectionView`. The item is his technique made physical, and only the server knows the selection authoritatively — the client half of that view exists so the client does not predict a throw the server refuses.

The seam closed E7 and E12 in [KNOWN_ISSUES](../../../docs/KNOWN_ISSUES.md). E10 and E11 stay open there: both are inherited message-ordering edges the migration made visible, and both change what a player sees, so neither belongs in a refactor.

## Adding a vessel

See [How to add the next character](../06-maintenance/How-to-add-next-character.md). In short: one enum constant, one server definition, one client definition, one line in each registry, and assets. No shared file changes.
