package jujutsu.mod.cursedspirit.ability;

/**
 * Ready-made per-grade parameters of one ability (Block 3, #86).
 *
 * <p>Every consumer reads finished numbers from
 * {@link CursedSpiritAbilityProfile#of(CursedSpiritAbilityId, jujutsu.mod.cursedspirit.CursedSpiritGrade)};
 * no {@code * grade} arithmetic exists anywhere in ability code (global constraint). Field
 * semantics are per-id and documented on the profile:
 *
 * <ul>
 *   <li>DASH — damage: collision damage; radius: hit radius; durationTicks: flight window;
 *       speed: launch impulse (blocks/tick); strength: knockback; cooldownTicks; weight.</li>
 *   <li>GROUND_SLAM — damage: shockwave damage; radius: shockwave radius; durationTicks: landing
 *       timeout; speed: jump impulse; strength: victim pop; cooldownTicks; weight.</li>
 *   <li>ACID_SPIT — damage: direct glob hit; radius: zone radius; durationTicks: zone lifetime;
 *       speed: glob velocity; strength: zone pulse damage; cooldownTicks; weight.</li>
 *   <li>GRAB_RUNNER — damage: unused (0, the run deals no damage); radius: unused;
 *       durationTicks: carry window (~80 = ~4 s); speed: navigation speed modifier;
 *       strength: unused; cooldownTicks; weight.</li>
 *   <li>FEAR — damage: unused (0); radius: cast range; durationTicks: debuff duration;
 *       speed: unused; strength: unused; cooldownTicks; weight.</li>
 *   <li>REGEN — damage: unused; radius: unused; durationTicks: HoT window; speed: unused;
 *       strength: heal per 20-tick pulse; cooldownTicks; weight.</li>
 *   <li>ARMOR — passive: damage/radius/duration/speed unused; strength: flat absorbed damage;
 *       cooldownTicks unused (0); weight.</li>
 *   <li>BERSERK — latched state: damage/radius/duration unused; speed: movement-speed bonus
 *       (multiplied total); strength: attack-damage bonus (multiplied total);
 *       cooldownTicks unused (0); weight.</li>
 * </ul>
 */
public record CursedSpiritAbilityParams(double damage, double radius, int durationTicks,
		double speed, double strength, int cooldownTicks, int weight) {
}
