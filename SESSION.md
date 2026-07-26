# Session Handoff — Jujutsu Minecraft

## Current state

- `main` = `c9e4904` — the vessel definition seam, merged as PR #9 and PR #10
- Active branch: `docs/add-vessel-skill` at `805c8a3`, **not merged**
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)" and, for the seam, under "The Vessel Seam". This file records only what changed recently and what is still unproven. Documentation authority order is owned by AGENTS.md; asset and provenance policy by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

## Landed on main — the vessel definition seam

Nine commits. The through-line: **shared code stopped asking which character a player is and asks the vessel.** Contract: `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md`. Reasoning and before/after: `docs/VESSEL_DEFINITION_REFACTOR.md`.

- **Slots renamed after input positions** — PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click with a technique weapon). Safe to renumber: the ids are transient, only the character id persists.
- **The input layer became a translator.** `JujutsuKeybinds` maps `(key, sneak)` to a slot and knows no vessel. Keybind ids still read `nobara_hairpin_*` on purpose — vanilla writes that string into options.txt.
- **Nobara moved onto the shared slots**; her private packet and int gate are deleted. This exposed two defects, both fixed: the cooldown key gained the vessel (`(player, vessel, slot)` on both sides), and the `hairpin` debug commands refuse a non-Nobara caster instead of firing Todo's swap under a hairpin's name.
- **Stale-vessel casts refused.** `CharacterAbilityPayload` carries the vessel the client believed in; the server compares and refuses. Closes the menu round-trip window where a key press was executed by the vessel the player had just left.
- **Server definitions** — `CharacterDefinition` + `JujutsuCharacters`. Mod init loops the registry instead of hand-listing twelve per-vessel `register()` calls.
- **Client definitions** — `CharacterClientDefinition` + `JujutsuCharacterClients`. `JujutsuVfxRecipes` deleted; each vessel registers its own. Six shared client files stopped naming a vessel. The roster's input strips were stale and are now honest.
- **E7 and E12 closed.** Seven direct vessel references remain in `src/`, one per file, all deliberate.

## On the active branch — the add-vessel skill

- `.claude/skills/add-vessel/SKILL.md` — repo-local, versioned with the architecture it describes. Six phases, prohibitions, readiness checklist, commit order. References the Codex notes rather than restating them.
- **Both registry tests now derive their expectations** instead of hand-keeping per-vessel lists: vessels from the enum, packages from the vessel id, and each card's expected length from the arms its router does not refuse. Before this, a new vessel would have shipped with three guarantees silently absent — which contradicted the skill's central claim.
- AGENTS.md gained "The Vessel Seam" as a first-class rule and lost the ten-step character workflow the skill now owns.
- The claim was verified, not argued: adding a `JujutsuCharacter` constant produces **exactly two** compile errors, one per registry, and none elsewhere; binding it to the wrong definition compiles and fails `testCharacterDefinitions`.

## Verification status

- 30 JavaExec verification programs wired into `check`, all green. Three added by the seam work: `testCharacterDefinitions`, `testCharacterClients`, `testNobaraAbilitySlots`.
- `python tools/audit_docs.py` passing. It is a **CI step, not part of `gradlew build`** — run it by hand after documentation changes.
- Two checks proven able to fail by mutation rather than only observed green: transposing two router arms, and binding a constant to the wrong definition.
- Jar built from `main` and installed at `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.

**Nothing in the suite constructs a `ServerLevel`,** so no test casts anything for real. Treat the build as proof of shape, not of behaviour.

### In-game smoke — partial, and here is exactly how partial

Run by the user at commit `d9df2b5`: Nobara's kit confirmed working (abilities activate, nails fly correctly), Todo confirmed on `R` and `B`.

**Not re-run since.** Everything below landed after that test:

- server definitions (`29dd4c4`, `8561cf7`) — attributes, stagger and selection hooks moved into definitions, and every vessel runtime now installs through `registerServerHooks` instead of mod init
- client definitions (`53a4dcd`) — renderers, skins, roster cards, theme accents and VFX packs all moved behind the client registry
- the marker's vessel gate and Todo's Shift+B fold (`20b5b15`)

## Must be checked in game before this is trusted

1. **Both vessels still load and render.** The renderer map, skin mixin and roster are now registry-driven; a wiring mistake shows as a vanilla body or a missing card, not as a failed build.
2. **Every runtime still installs.** Mod init no longer names them. Nail traps, straw doll, resonance, hammer combat, Todo's marks — if a `registerServerHooks` call were dropped the ability would simply do nothing, silently. The build-time test covers the call existing, not the listener firing.
3. **Attributes and stagger.** Todo should still hit harder, swing slower and shrug off stagger — those moved from a shared file into `TodoDefinition`.
4. **Nobara kit restore.** Drop the hammer, re-select her: it returns, and held tools are not duplicated.
5. **Shift+B as Todo** reaches the pair swap, including crouched between the two presses. Known un-predicted edge: Shift+B during the B cooldown earns the recharging message rather than being suppressed locally.
6. **The marker gate on both sides.** As Nobara or None, right click the marker: no throw, no sound, no consumed item. As Todo it throws normally.
7. **Roster cards.** Nobara's strip shows five inputs, Todo's three, in both languages; accents ease per vessel; None sits last.
8. **The stale-vessel refusal.** Switch vessels and press R inside the confirmation round trip: nothing fires from the old vessel.

## Next product steps

1. Merge `docs/add-vessel-skill`, or say what should change in the skill first.
2. Run the in-game pass above — items 1–3 are the ones the seam work put at real risk.
3. Decide the fate of `CharacterPlayerState.hasClaimedStarter`: give the persisted claim a job or delete it (E12 residue).
4. Decide E10 (Nobara's fallback erases five translated diagnostics) and E11 (cooldown message precedes her silent stagger check) deliberately, not inside a refactor.
5. Move "is this stack my technique weapon" out of `JujutsuKeybinds` into the client definition — the last vessel-specific line in the input layer, and the one thing blocking a melee vessel from using `ATTACK_CONTEXT`.
6. Add world/GameTest coverage for the swap runtimes and their rollback paths (E1/E8).
7. Replace temporary ProjectJJK placeholders; resolve Rich-Modern provenance before any public distribution.

## Open decisions left for the user

- AGENTS.md restates twenty `TodoProfile` constants in a table while itself saying `TodoProfile` is the source of truth and must not be restated. Removing the table would drop the file under the 300-line hygiene target; keeping it is defensible for at-a-glance reference. Not changed unilaterally.
