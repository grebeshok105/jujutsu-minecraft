# Nobara Runtime Flow

Status: CURRENT

## Nail launch

Client input or item use reaches a server-owned runtime. Nails are prepared, consumed, launched toward a resolved target, then either embed or resolve explosive impact. Confirmed results create typed VFX cues.

## Hairpin

R/B pass through ProjectJjkNobaraActions, which requires selected Nobara and no active stagger. `ProjectJjkNobaraActions.tryCast` is the single gate: the C2S receiver and the `/jujutsu hairpin enlarge|explosion` commands both call it, so an OP command cannot bypass the selected-character rule (VERIFIED — JujutsuCommands.castNobaraAction). Hairpin Enlarge and Boom are explicit keybound actions with their own payload; there is no hammer-item fallback path.
 ProjectJjkRitualRuntime obtains loaded nails from EmbeddedNailRegistry, orders a HairpinChain, schedules cadence, resolves concrete nail entities, applies dedicated Hairpin damage, and discards resolved nails.

B no longer does a look-direction search at all. `collectAllLoadedOwnedNails` takes every loaded owned nail from `EmbeddedNailRegistry` and `HairpinChainOrder.nearestNeighbor` orders it (VERIFIED — ProjectJjkRitualRuntime). This replaced an aim-capsule search that rejected anchors behind its own start point, so nails at the caster's feet or at point-blank range silently produced an empty cast — the "Boom works sometimes" bug. Do not reintroduce an aim gate on B; it is a detonation of *your* nails, not of what you are looking at.

The `HAIRPIN_EXPLOSION_DETECT_*` constants survive in `ProjectJjkNobaraProfile` but have no production call site — only `ProjectJjkNobaraProfileTest` reads them, as a regression guard against restoring the old 4-block forward offset (VERIFIED). Treat them as a guard, not as live tuning.

## Resonance

The straw-doll path validates caster tools, target-bound remnant, target life/dimension/range, and pending-cast state. Resources are consumed only at final validation. Successful damage grants momentum, staggers the target, applies accepted global hit-stop, and broadcasts world-fixed VFX.
