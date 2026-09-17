package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

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

	/**
	 * Called by the zone container scan (Block 3's {@code InfectionSink.tickZone}): a
	 * block-entity container at {@code containerPos} holds {@code stack}. Lets the dwell
	 * tracker pin the object to the container's position (spec §8.1 — a chest is not
	 * protection).
	 */
	default void noteContainer(BlockPos containerPos, ItemStack stack) {
	}
}
