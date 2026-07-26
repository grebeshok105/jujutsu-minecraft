# VFX Core — Nobara Reference Implementation

Status: CURRENT

Canonical path:

server-confirmed action → VfxCue/VfxCuePayload → JujutsuClientNetworking → VfxDirector → character recipes → director-owned channels

Client bootstrap calls `JujutsuVfxRecipes.registerAll()`, which registers `NobaraVfxRecipes` and `TodoVfxRecipes` into the shared `VfxDirector`.

VfxDirector owns recipe registration, active-instance cap 64, cue age/expiry, world identity, disconnect cleanup, render callbacks, and shared channels. Unknown ids are logged once and ignored.

NobaraVfxIds defines 25 ids. TodoVfxIds currently defines Boogie Woogie. ProjectSanityTest requires age-aware real-time channel calls and rejects removed legacy managers/mixins. Six client mixins are configured; VfxDeltaTrackerMixin is intentionally absent.

VfxTimeChannel is a bounded client VFX primitive, but production code must not scale global Minecraft DeltaTracker time. Resonance gameplay hit-stop is separately and intentionally server-global through ServerTimeDilation.

Additional characters add `<Character>VfxIds` / `<Character>VfxRecipes` and wire them through the same aggregate entrypoint.
