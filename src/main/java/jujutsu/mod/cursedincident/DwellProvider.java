package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;

/**
 * Where an unsealed cursed object currently dwells (issue #110 spec §8). Implemented by
 * the object system (Block 2's {@code ObjectDwellTracker}); the core only reads through
 * this interface so the dependency points inward.
 */
public interface DwellProvider {

	DwellProvider NONE = new DwellProvider() {
	};

	/** Current dwell position of the object instance, or null if untracked. */
	default BlockPos dwellCenterOf(UUID objectInstanceId) {
		return null;
	}

	/** Whether the object instance is currently sealed. */
	default boolean isSealed(UUID objectInstanceId) {
		return false;
	}
}
