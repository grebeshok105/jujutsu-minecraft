# Session Handoff — Jujutsu Minecraft

## Current state

- `main` = `a0d7f79` — the vessel definition seam and the add-vessel skill, merged as PR #9, #10 and #11
- Active branch: `feat/todo-swap-impact`, **not merged** — the Boogie Woogie gameplay pass
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)" and, for the seam, under "The Vessel Seam". This file records only what changed recently and what is still unproven. Documentation authority order is owned by AGENTS.md; asset and provenance policy by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

## On the active branch — the Boogie Woogie impact pass

Five commits. The through-line: **the swap stopped being a teleport with a sound on it.** Contract and reasoning: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Todo-Boogie-Woogie.md`.

- **A swapped body keeps its momentum on the client.** `place` teleports absolutely with an empty `Relative` set, so the transition carried `Vec3.ZERO` and the client was told its velocity was nothing — the server-side `setDeltaMovement` was a fiction for a player, who owns his own movement. One `hurtMarked` inside the shared restore helper fixes all four routes and the rollback path.
- **One emission point.** The aimed swap, both marker swaps and the pair swap each hand-copied the same five feedback calls; they now share `emitSwapImpact`. The pending-sound scheduler carries its own sound, so a low landing report lands three ticks after the clap at the midpoint of where the bodies ended up — one report, because Minecraft audio has no propagation delay and two would flam.
- **The impact sequence.** Camera snap, per-body afterimage silhouette, per-body arrival gather, velocity streak, and six ticks of the world's audio stepping back. All of it on `SWAP_AFTERIMAGE` / `SWAP_ARRIVAL`, which the feint does not emit — the feint shares the clap cue, so anything added there would announce it.
- **A landed mark is a permanent anchor**, a body mark is not. The record enforces both lifetimes rather than trusting call sites, and `TodoSwapMarks.onUsed` is the one place that decides what a swap costs a mark.
- **Swap momentum.** A completed swap opens a 24-tick window; the next confirmed hit is ×1.25 and staggers 8 ticks. Carried by a `MobEffect` attribute modifier, so there is no second damage instance at all.

### What the exploration found that the naive version would have shipped broken

1. `ServerLivingEntityEvents.AFTER_DAMAGE` **does not fire on a killing blow** — a kill would have silently refunded the momentum.
2. `SoundManager.updateSourceVolume` **ignores its volume argument** for every category except MASTER; it is not a ducking API. `pauseAllExcept` / `resume` is.
3. `TODO_SWAP_MARKER` is `noSave()`, so an unloaded chunk destroys the projectile forever while the mark sweep deliberately treats an unresolvable entity as alive — permanence would have produced an invisible working teleport anchor.
4. Black Flash re-enters the damage event, so an unguarded momentum listener sees one swing twice and spends the window on the wrong pass.

### Verified by mutation, not by a green run

Three: assignment instead of `Math.max` in `VfxSoundDuck.extendedDeadline`, `sin` for `cos` in `VfxWorldChannel.facingScale`, and disabling the re-entrancy guard in `TodoSwapMomentum`. Each fails its own assertion and was reverted.

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

- 33 JavaExec verification programs wired into `check`, all green. Three added by the seam work (`testCharacterDefinitions`, `testCharacterClients`, `testNobaraAbilitySlots`) and three by the impact pass (`testTodoSwapMomentum`, `testVfxSoundDuck`, `testVfxSilhouette`).
- `python tools/audit_docs.py` passing. It is a **CI step, not part of `gradlew build`** — run it by hand after documentation changes. This pass moved all four audited counters at once.
- Two checks proven able to fail by mutation rather than only observed green: transposing two router arms, and binding a constant to the wrong definition.
- Jar built from `main` and installed at `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.

**Nothing in the suite constructs a `ServerLevel`,** so no test casts anything for real. Treat the build as proof of shape, not of behaviour.

### In-game smoke — partial, and here is exactly how partial

Run by the user at commit `d9df2b5`: Nobara's kit confirmed working (abilities activate, nails fly correctly), Todo confirmed on `R` and `B`.

**Not re-run since.** Everything below landed after that test:

- server definitions (`29dd4c4`, `8561cf7`) — attributes, stagger and selection hooks moved into definitions, and every vessel runtime now installs through `registerServerHooks` instead of mod init
- client definitions (`53a4dcd`) — renderers, skins, roster cards, theme accents and VFX packs all moved behind the client registry
- the marker's vessel gate and Todo's Shift+B fold (`20b5b15`)

## Must be checked in game before the impact pass is trusted

Nothing below has been run in game. The build proves shape, not behaviour.

1. **A normal swap** reads as one physical beat: clap, snap, two silhouettes, two arrivals, a fraction of a second of quiet, then the low report. Movement continues instead of stopping dead.
2. **Every rejection is silent** — hands full, no target with no mark, nowhere safe to stand: no sound, no flash, no duck.
3. **The feint still gives nothing away.** `Shift+R` has the clap and the camera snap; no silhouette, no arrival, no report, no quiet.
4. **Open a menu during the quiet.** Audio returns and the vanilla pause is not disturbed.
5. **Five swaps back to back** leave no stuck silhouette, no stuck quiet, no stuck camera offset.
6. **The mark.** Throw it, swap to it — the mark and the projectile are both still there; swap again. Walk out of render distance and back: the mark is honestly lost with a message. Change dimension, die, change vessel: cleared.
7. **A body mark** still lasts ten seconds and is still consumed.
8. **Momentum.** Swap then hit: heavier, staggers, its own effect. The second hit is ordinary. Swap, miss, then hit inside the window: still boosted. Swap, wait two seconds, hit: ordinary. Swap then change vessel: the effect is gone.
9. **Nobara regression.** `restoreMotionAndRotation` and `VfxWorldChannel` are the only shared files this pass touched.

## Must be checked in game before the seam work is trusted

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
