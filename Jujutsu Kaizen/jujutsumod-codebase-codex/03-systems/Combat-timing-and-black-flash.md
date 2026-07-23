# Combat Timing and Black Flash

Status: CURRENT

NobaraActionTimeline centralizes windup, impact, recovery, and supported Black Flash windows. Hammer actions are resolved on the server through NobaraHammerCombatRuntime. Black Flash chance, chain multiplier, healing, stagger, and recovery values live in ProjectJjkNobaraProfile.

ForcedBlackFlash is debug-only server state and clears through its registered lifecycle. Production damage remains server-owned; VFX cues describe confirmed results.

Tests: BlackFlashWindowTest, ResonantMomentumTest, ProjectJjkNobaraProfileTest, and ProjectSanityTest.
