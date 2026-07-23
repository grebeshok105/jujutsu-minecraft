# VFX Core — Nobara Reference Implementation

Status: CURRENT

Canonical path:

server-confirmed action → VfxCue/VfxCuePayload → JujutsuClientNetworking → VfxDirector → NobaraVfxRecipes → director-owned channels

VfxDirector owns recipe registration, active-instance cap 64, cue age/expiry, world identity, disconnect cleanup, render callbacks, and shared channels. Unknown ids are logged once and ignored.

NobaraVfxIds defines 25 ids. ProjectSanityTest currently requires 44 age-aware real-time channel calls and rejects removed legacy managers/mixins. Six client mixins are configured; VfxDeltaTrackerMixin is intentionally absent.

VfxTimeChannel is a bounded client VFX primitive, but production code must not scale global Minecraft DeltaTracker time. Resonance gameplay hit-stop is separately and intentionally server-global through ServerTimeDilation.

When a second character arrives, add its ids/recipes and introduce one explicit aggregate recipe-registration entrypoint rather than per-effect receivers.
