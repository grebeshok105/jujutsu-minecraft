package jujutsu.mod.cursedspirit.ability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Pure pool-roll policy (Block 3, #86): three distinct abilities per body, weighted by grade
 * profile weight plus the variant signature bump.
 */
public final class CursedSpiritAbilityPolicy {
	/** Abilities per body. */
	public static final int POOL_SIZE = 3;

	private CursedSpiritAbilityPolicy() {
	}

	/** Finished roll weight of one ability for one body: profile weight + variant bias. */
	public static int weight(CursedSpiritAbilityId id, CursedSpiritGrade grade, CursedSpiritVariant variant) {
		return CursedSpiritAbilityProfile.of(id, grade).weight()
				+ CursedSpiritAbilityProfile.variantBias(id, variant);
	}

	/** Whether the ability may enter the pool at this grade (power-rank gate, C3). */
	public static boolean eligible(CursedSpiritAbilityId id, CursedSpiritGrade grade) {
		return grade.powerRank() >= CursedSpiritAbilityProfile.requiredPowerRank(id);
	}

	/**
	 * Rolls exactly {@link #POOL_SIZE} distinct abilities, without replacement. Deterministic
	 * for a fixed {@link RandomSource} sequence; never returns duplicates or ineligible ids.
	 */
	public static List<CursedSpiritAbilityId> rollThree(RandomSource random, CursedSpiritGrade grade,
			CursedSpiritVariant variant) {
		List<CursedSpiritAbilityId> remaining = new ArrayList<>();
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			if (eligible(id, grade)) {
				remaining.add(id);
			}
		}
		List<CursedSpiritAbilityId> pool = new ArrayList<>(POOL_SIZE);
		for (int pick = 0; pick < POOL_SIZE && !remaining.isEmpty(); pick++) {
			int total = 0;
			for (CursedSpiritAbilityId id : remaining) {
				total += weight(id, grade, variant);
			}
			int roll = random.nextInt(total);
			int cursor = roll;
			int index = remaining.size() - 1;
			for (int i = 0; i < remaining.size(); i++) {
				cursor -= weight(remaining.get(i), grade, variant);
				if (cursor < 0) {
					index = i;
					break;
				}
			}
			pool.add(remaining.remove(index));
		}
		return List.copyOf(pool);
	}

	/**
	 * Weighted pick of one id from explicit candidate weights (used by the in-combat decider).
	 * Pure for unit testing; empty map returns empty.
	 */
	public static java.util.Optional<CursedSpiritAbilityId> pick(RandomSource random,
			Map<CursedSpiritAbilityId, Integer> weights) {
		int total = 0;
		for (int weight : weights.values()) {
			total += Math.max(0, weight);
		}
		if (total <= 0) {
			return java.util.Optional.empty();
		}
		int cursor = random.nextInt(total);
		for (Map.Entry<CursedSpiritAbilityId, Integer> entry : weights.entrySet()) {
			cursor -= Math.max(0, entry.getValue());
			if (cursor < 0) {
				return java.util.Optional.of(entry.getKey());
			}
		}
		return java.util.Optional.empty();
	}

	/** Snapshot weight table for one body, for tests and the decider. */
	public static Map<CursedSpiritAbilityId, Integer> weights(CursedSpiritGrade grade,
			CursedSpiritVariant variant) {
		Map<CursedSpiritAbilityId, Integer> table = new EnumMap<>(CursedSpiritAbilityId.class);
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			if (eligible(id, grade)) {
				table.put(id, weight(id, grade, variant));
			}
		}
		return table;
	}
}
