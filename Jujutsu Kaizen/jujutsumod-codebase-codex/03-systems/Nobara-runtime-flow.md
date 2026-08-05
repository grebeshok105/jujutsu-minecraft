# Nobara Runtime Flow

Status: CURRENT

## Nail launch

Client input or item use reaches a server-owned runtime. Nails are prepared, consumed, launched toward a resolved target, then either embed or resolve explosive impact. Confirmed results create typed VFX cues.

## Hairpin

R/B name a slot on the client and arrive over the one shared `CharacterAbilityPayload`, stamped with the vessel the client believed in — the server refuses the cast if that claim disagrees with the stored selection. `CharacterAbilityExecutor.tryCast` requires a selected vessel and a ready cooldown, then asks the vessel's definition: `NobaraDefinition.tryCast` hands the slot to `NobaraAbilityRouter`, which adds the no-active-stagger requirement that is hers alone. The executor is the single gate: the C2S receiver and the `/jujutsu hairpin enlarge|explosion` commands both call it, so an OP command cannot bypass the selected-character rule. The commands also refuse unless Nobara is selected, because a slot is an input position and `PRIMARY` means a swap for Todo (VERIFIED — JujutsuCommands.castAbilitySlot). Hairpin Enlarge and Boom are still explicit keybound actions; there is no hammer-item fallback path.
 ProjectJjkRitualRuntime obtains loaded nails from EmbeddedNailRegistry, orders a HairpinChain, schedules cadence, resolves concrete nail entities, applies dedicated Hairpin damage, and discards resolved nails.

B is the Mega Nail (`ProjectJjkMegaNailRuntime.start`): it resolves the aimed target through `TargetResolver` within `HAIRPIN_ENLARGE_RANGE`, selects that target's embedded owned nails by `anchor().stableId()` equality, atomically discards them (per-nail consume flash) and consumes the target's marks, then schedules one delayed piercing strike (6 ticks) whose damage scales from the consumed depth weight. The aim here is the mechanic, not a regression: the old massless "detonate everything loaded" B was replaced wholesale, and its no-aim lesson lives on only in R's seed search, which still never rejects anchors behind the start point. An empty B (no aimed target with your nails) returns false and shows the router's fallback message; an empty R stays a consumed snap-only cast.

The `HAIRPIN_EXPLOSION_DETECT_*` constants survive in `ProjectJjkNobaraProfile` but have no production call site — only `ProjectJjkNobaraProfileTest` reads them, as a regression guard against restoring the old 4-block forward offset (VERIFIED). Treat them as a guard, not as live tuning.

## Resonance

The straw-doll path validates caster tools, target-bound remnant, target life/dimension/range, and pending-cast state. Resources are consumed only at final validation. Successful damage grants momentum, staggers the target, applies accepted global hit-stop, and broadcasts world-fixed VFX.
