# Nobara Runtime Flow

Status: CURRENT

## Nail launch

Client input or item use reaches a server-owned runtime. Nails are prepared, consumed, launched toward a resolved target, then either embed or resolve explosive impact. Confirmed results create typed VFX cues.

## Hairpin

R/B name a slot on the client and arrive over the one shared `CharacterAbilityPayload`, stamped with the vessel the client believed in — the server refuses the cast if that claim disagrees with the stored selection. `CharacterAbilityExecutor.tryCast` requires a selected vessel and a ready cooldown, then asks the vessel's definition: `NobaraDefinition.tryCast` hands the slot to `NobaraAbilityRouter`, which adds the no-active-stagger requirement that is hers alone. The executor is the single gate: the C2S receiver and the `/jujutsu hairpin enlarge|explosion` commands both call it, so an OP command cannot bypass the selected-character rule. The commands also refuse unless Nobara is selected, because a slot is an input position and `PRIMARY` means a swap for Todo (VERIFIED — JujutsuCommands.castAbilitySlot). Hairpin Enlarge and Boom are still explicit keybound actions; there is no hammer-item fallback path.
HairpinRuntime obtains validated owner anchors from `NailAnchorRegistry`, snapshots their depth/origin/targetId data, resolves the directed seed through `HairpinSeedResolver`, builds the deterministic spatial network through `HairpinNetwork`, applies dedicated Hairpin damage, and discards resolved nails. `NailAnchorRegistry` owns the embedded-anchor identity and the derived `isDeeplyAnchored` predicate; `NobaraActionGuard` is the router-level re-entrancy guard and `NobaraActionTimeline` centralizes windups. R seed search prefers anchors ahead of the cast start (forward filter `along >= 0.0`).

B is the Mega Nail (`ProjectJjkMegaNailRuntime.start`): it resolves the aimed target through `TargetResolver` within `HAIRPIN_ENLARGE_RANGE`, selects that target's anchors through `NailAnchorRegistry.anchorsOnTarget`, atomically consumes/discards them and the target's marks at cast t0, then runs a 14-tick gather followed by a 16-tick entity-driven charge and a 60-tick flight whose homing resolves by UUID; damage scales from the consumed depth weight (4.0/nail, cap 42). The aim here is the mechanic, not a regression: the old massless "detonate everything loaded" B was replaced wholesale. An empty B (no aimed target with your nails) returns false and shows the router's fallback message; an empty R stays a consume-free refusal.

The `HAIRPIN_EXPLOSION_DETECT_*` constants survive in `ProjectJjkNobaraProfile` but have no production call site — only `ProjectJjkNobaraProfileTest` reads them, as a regression guard against restoring the old 4-block forward offset (VERIFIED). Treat them as a guard, not as live tuning.

## Resonance

The straw-doll path validates caster tools, target-bound remnant, target life/dimension/range, and pending-cast state, then runs a 40-tick ritual timeline: bind at t0, windup at t10, doll strike at t24, and release at t30. All beats before release are presentation-only; final validation consumes one nail and the bound remnant only at release, then applies damage, momentum, stagger, and world-fixed VFX. Resonance has no server tick dilation.
