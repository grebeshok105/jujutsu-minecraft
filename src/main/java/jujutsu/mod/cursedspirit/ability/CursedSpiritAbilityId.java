package jujutsu.mod.cursedspirit.ability;

import java.util.Optional;

/**
 * The eight v1 abilities of the shared cursed-spirit pool (Block 3, #86).
 *
 * <p>Every id is explicitly classified by three predicates, each implemented as an exhaustive
 * switch <em>expression</em> ({@code ->}, no {@code default}): adding a ninth id fails
 * compilation in all three at once, which is the structural guard against the
 * takesMovement/occupiesAttackClip confusion the plan (C3) was written to prevent. A switch
 * <em>statement</em> would silently skip the new constant, so statements are banned here.
 *
 * <ul>
 *   <li>{@link #passive()} — ARMOR and BERSERK are states, never "started".</li>
 *   <li>{@link #takesMovement()} — DASH, GROUND_SLAM, GRAB_RUNNER all write velocity/position
 *       (the slam writes it twice: jump impulse and landing), so they are strictly mutually
 *       exclusive and the melee goal yields navigation while any of them is active.</li>
 *   <li>{@link #occupiesAttackClip()} — the five ids that play the {@code attack} clip; while
 *       any of them is active the melee goal does not start a new WINDUP. Long-lived states
 *       (ARMOR, BERSERK, REGEN) and acid zones never occupy the clip: a berserk spirit keeps
 *       swinging, harder.</li>
 * </ul>
 */
public enum CursedSpiritAbilityId {
	DASH("dash"),
	GROUND_SLAM("ground_slam"),
	ACID_SPIT("acid_spit"),
	GRAB_RUNNER("grab_runner"),
	FEAR("fear"),
	REGEN("regen"),
	ARMOR("armor"),
	BERSERK("berserk");

	private final String id;

	CursedSpiritAbilityId(String id) {
		this.id = id;
	}

	/** Lowercase persistence id, e.g. {@code "ground_slam"} — also the NBT pool id. */
	public String id() {
		return id;
	}

	public static Optional<CursedSpiritAbilityId> byId(String id) {
		for (CursedSpiritAbilityId ability : values()) {
			if (ability.id.equals(id)) {
				return Optional.of(ability);
			}
		}
		return Optional.empty();
	}

	/** ARMOR and BERSERK are states, not "starts". */
	public boolean passive() {
		return switch (this) {
			case ARMOR, BERSERK -> true;
			case DASH, GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, FEAR, REGEN -> false;
		};
	}

	/** The movement-owning group: all three write velocity, so they never overlap. */
	public boolean takesMovement() {
		return switch (this) {
			case DASH, GROUND_SLAM, GRAB_RUNNER -> true;
			case ACID_SPIT, FEAR, REGEN, ARMOR, BERSERK -> false;
		};
	}

	/** Only the ids that play the attack clip occupy it; long-lived states never do. */
	public boolean occupiesAttackClip() {
		return switch (this) {
			case DASH, GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, FEAR -> true;
			case REGEN, ARMOR, BERSERK -> false;
		};
	}

	/**
	 * Compile-guard entry point: the three switch expressions above are exhaustive, so any new
	 * id left unclassified in any of them is a compile error here too. Always true at runtime;
	 * its value is the compiler's exhaustiveness check, pinned by
	 * {@code CursedSpiritAbilityMatrixTest#everyAbilityIsClassified}.
	 */
	public static boolean everyAbilityIsClassified() {
		for (CursedSpiritAbilityId ability : values()) {
			boolean classified = ability.passive() || !ability.passive();
			boolean moved = ability.takesMovement() || !ability.takesMovement();
			boolean clipped = ability.occupiesAttackClip() || !ability.occupiesAttackClip();
			if (!classified || !moved || !clipped) {
				return false;
			}
		}
		return true;
	}
}
