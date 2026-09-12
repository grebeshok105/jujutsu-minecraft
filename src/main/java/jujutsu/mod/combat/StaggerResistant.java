package jujutsu.mod.combat;

/**
 * Entities that scale incoming stagger instead of taking the full requested ticks.
 *
 * <p>Implemented by mobs with per-tier stagger resistance (cursed spirits). The player path keeps
 * going through the vessel definition; see
 * {@link jujutsu.mod.character.CharacterCombatModifiers#adjustedStaggerTicks}.
 */
public interface StaggerResistant {
	/**
	 * Maps requested stagger ticks to applied ticks.
	 *
	 * @param ticks requested ticks, always &gt; 0
	 * @return ticks to apply; may be 0 for full resistance
	 */
	int adjustIncomingStaggerTicks(int ticks);
}
