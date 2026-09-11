# Combat Timing and Black Flash

Status: CURRENT

NobaraActionTimeline centralizes windup, impact, recovery, and supported Black Flash windows. Hammer actions are resolved on the server through NobaraHammerCombatRuntime. Black Flash chance, chain multiplier, healing, stagger, and recovery values live in ProjectJjkNobaraProfile.

ForcedBlackFlash is debug-only server state and clears through its registered lifecycle. Production damage remains server-owned; VFX cues describe confirmed results.

Tests: BlackFlashWindowTest, ResonantMomentumTest, ProjectJjkNobaraProfileTest, and ProjectSanityTest.

## Shared combat core

Vessel-agnostic mechanics live in `combat/` and are owned by no vessel note in particular: `BlackFlashStrike`/`BlackFlashFocus`/`BlackFlashWindow`/`BlackFlashImpact` (trigger evaluation, focus join-sync, chain window, impact presentation) plus the `ForcedBlackFlash` debug override, `SafeBodyPlacement` (the box/world-border/world-load placement check every teleport plan funnels through), `TargetResolver` (eligibility + ranking — see E1b in KNOWN_ISSUES before touching ranking), `CombatStagger.GLOBAL` (the only interrupt system), and `JujutsuDamageSources` alongside vessel-scoped sources classes. Resonance hit-stop itself is driven by `ServerTimeDilation` (currently housed under `nobara/projectjjk/` beside its only caller, the straw-doll runtime — a mechanism with one client, not a kit rule).
