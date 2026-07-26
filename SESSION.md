# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: feat/todo-input-slots
- Base: main at 00e6846 (merge of PR #8)
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)". This file records only what changed on this branch. Documentation authority order is owned by AGENTS.md; asset and provenance policy is owned by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

## What changed on this branch

One asset commit, then the vessel definition seam in seven steps. The through-line: shared code stops asking which character a player is and asks the vessel. The contract is written up in the Codex note `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md`.

- **Todo's animations re-exported from the live Blockbench project.** The clap is substantially reworked (leftArm to [-80, 40, -50], head and hair_bun motion added, the contact-frame position nudge dropped); idle and attack gained head channels the repo never received.
- **Slots renamed after input positions.** `CharacterAbility` is now PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click with a technique weapon). Safe to renumber because the ids are transient — only the character id persists. Todo's feint moved to PRIMARY_SNEAK and his pair swap to SECONDARY.
- **The input layer became a translator.** `JujutsuKeybinds` maps (key, sneak) to a slot in one `slot(...)` helper and no longer knows who is selected. The registered keybind ids keep reading `nobara_hairpin_*` on purpose — vanilla writes that string into options.txt, and renaming it would silently reset every player's binding.
- **Nobara moved onto the shared slots.** Her private int-keyed gate and private C2S packet are deleted; `NobaraAbilityRouter` maps the five slots to her runtimes and keeps the two rules that are hers alone (the silent stagger check, the single fallback line). This exposed and fixed two defects: the server cooldown key gained the vessel — `(player, vessel, slot)` on both sides, where before Todo's pair-swap cooldown could refuse her mass Hairpin after a switch — and the `hairpin` debug commands now refuse a non-Nobara caster instead of firing Todo's swap and calling it a hairpin.
- **Stale-vessel casts are refused.** `CharacterAbilityPayload` is now `(abilityId, characterId)`: the client stamps the vessel it believed in, and the server refuses when the claim disagrees with the stored selection. Closes the round-trip window where switching Todo → Nobara and pressing R fired a real Boogie Woogie and took its cooldown. The claim is only compared, never trusted.
- **Server definitions.** `CharacterDefinition` + `JujutsuCharacters` (exhaustive switch, no `default`). Hooks: `id`, `tryCast`, and defaults `registerServerHooks`, `canonicalSlot`, `applyAttributes`/`removeAttributes`, `adjustIncomingStaggerTicks`, `onSelected`/`onDeselected`. Mod init loops the registry instead of hand-listing twelve per-vessel `register()` calls. Todo's attribute modifiers and stagger resistance moved into `TodoDefinition`; his mark cleanup moved to `onDeselected` (runs only when he is left). Nobara's starter kit is deliberately re-applied on **every** selection — idempotent, so a lost kit is restored without duplicating held tools; the persisted starter claim is now recorded for every vessel and read by nothing (E12's residue note).
- **Client definitions.** `CharacterClientDefinition` + `JujutsuCharacterClients` (same exhaustive switch). Each vessel owns its renderer, skin path (declared once — the skin mixin and roster both read it), roster card, accent/warmth, module row, and client hooks. `JujutsuVfxRecipes` is deleted; each vessel registers its own recipes. Five shared client files stopped naming a vessel: `CharacterGeoRenderers`, `ClickGuiTheme`, `JujutsuModules`, `CharacterRosterPanel`, `JujutsuModClient` (plus `CharacterSkinMixin`). The roster's input strips were stale and are now honest: Nobara lists all five filled slots (the nail trap was missing and left click was mislabeled "Boom"), Todo lists his three.
- **Two gaps the seam exposed, closed in the last commit.** `TodoSwapMarkerItem.use` now refuses a non-Todo thrower on both sides through the new `CharacterSelectionView` (server reads its own selection, client reads the mirror handed in at init) — closes E12. And Shift+B reaches Todo's pair swap again: `canonicalSlot` lets a vessel fold two inputs into one, applied in the executor before the cooldown check so the sneak variant cannot bypass the real cooldown.

Documentation: E7 (shared code is Nobara-shaped) and E12 (marker vessel gate) are closed in docs/KNOWN_ISSUES.md; E10/E11 record the two inherited message-ordering edges the migration made visible, deliberately not fixed in a refactor.

## Verification status

- Verification suite: 30 JavaExec programs wired into `check`, three new on this branch — `testCharacterDefinitions`, `testCharacterClients`, `testNobaraAbilitySlots`. The registry tests derive expectations from the enum and the source tree (a vessel runtime exposing `register()` that nothing calls fails the build; a definition bound to the wrong constant fails; client types reaching `src/main` fail).
- `python tools/audit_docs.py` — passing after the documentation sync.
- The remapped jar in build/libs was rebuilt at the final code commit (20b5b15) and installed into the play instance.
- In-game client smoke — **NOT run on this branch.**

Nothing in the test suite constructs a `ServerLevel`, so no test casts anything for real. The whole input path was rewired; treat the build as proof of shape, not of behaviour.

## Must be checked in game before this is trusted

The full checklist is in docs/BUILDING_IN_SANDBOX.md. What this branch specifically put at risk:

1. **Every Nobara slot through the shared gate.** R directed Hairpin, Shift+R Self Resonance, B mass Boom, Shift+B nail trap, hammer left click — her entire input path was replaced; a wiring mistake here is invisible to the suite.
2. **The stale-vessel refusal.** Switch vessels in the menu and press R inside the confirmation round trip: nothing should fire from the old vessel, and the new vessel's cast should work a moment later.
3. **Cooldowns keyed by vessel.** Cast Todo's pair swap, switch to Nobara, and confirm B works immediately — and that the client's greyed-out state agrees with the server everywhere.
4. **Shift+B as Todo.** It must reach the pair swap (both presses, including crouched). Known un-predicted edge: Shift+B during the B cooldown earns the recharging message instead of being suppressed locally.
5. **The marker gate on both sides.** As Nobara or None, right click the marker: no throw, no sound, no consumed item, on client and server alike. As Todo it throws normally.
6. **Nobara kit restore.** Drop the hammer, re-select her: it comes back, and held tools are not duplicated.
7. **Roster cards.** Nobara's strip shows five inputs, Todo's three, localized in both languages; accents ease per vessel.
8. **Todo's reworked clap.** The re-exported animation (bigger arm travel, no position nudge) has to read well in real play — that was the point of taking it from the live project.

## Next product steps

1. Run the client-smoke checklist above and in docs/BUILDING_IN_SANDBOX.md.
2. Decide the fate of `CharacterPlayerState.hasClaimedStarter` — give the persisted claim a job or delete it (E12 residue).
3. Decide E10 (Nobara's fallback message erases five translated diagnostics) and E11 (cooldown message vs. her silent stagger check) deliberately, not inside a refactor.
4. Move the "is this stack my technique weapon" question from `JujutsuKeybinds` into the client definition — the last vessel-specific line in the input layer.
5. Add world/GameTest coverage for the swap runtimes and their rollback paths; nothing exercises them (E1/E8 in docs/KNOWN_ISSUES.md).
6. Replace temporary ProjectJJK placeholders when original assets are available; resolve Rich-Modern provenance before any public distribution.
