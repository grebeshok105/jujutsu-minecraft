package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Where an unsealed cursed object currently dwells (issue #110 spec §8). Implemented by
 * the object system (Block 2's {@code ObjectDwellTracker}); the core only reads through
 * this interface so the dependency points inward.
 */
public interface DwellProvider {

	/** Complete physical seal/tier/integrity/knowledge state. */
	record SealSnapshot(boolean sealed, int tier, int integrity, KnowledgeLevel knowledge) {
		public SealSnapshot {
			knowledge = knowledge == null ? KnowledgeLevel.UNKNOWN : knowledge;
			tier = Math.max(0, tier);
			integrity = Math.max(0, integrity);
		}
	}

	SealSnapshot EMPTY_SEAL = new SealSnapshot(false, 0, 0, KnowledgeLevel.UNKNOWN);

	DwellProvider NONE = new DwellProvider() {
	};

	/** Current dwell position of the object instance, or null if untracked. */
	default BlockPos dwellCenterOf(UUID objectInstanceId) {
		return null;
	}

	/** Current container position observed for the object, or null when not container-bound. */
	default BlockPos containerOf(UUID objectInstanceId) {
		return null;
	}

	/** Complete physical seal state for the object, or {@link #EMPTY_SEAL}. */
	default SealSnapshot sealSnapshot(UUID objectInstanceId) {
		return EMPTY_SEAL;
	}

	/** Whether the object instance is currently sealed. */
	default boolean isSealed(UUID objectInstanceId) {
		return sealSnapshot(objectInstanceId).sealed();
	}

	/**
	 * Write-through for seal/knowledge state onto the physical stack's
	 * {@code CURSED_OBJECT_STATE} component (the authoritative store — plan review F9).
	 * {@code IncidentControl}'s seal/unseal/damageSeal/identify call this after mutating
	 * the incident record when the source is an object.
	 */
	default void applySealState(UUID objectInstanceId, boolean sealed, int sealTier,
			int sealIntegrity, KnowledgeLevel knowledge) {
	}

	/**
	 * Called by the zone/container scanners for a physical block-entity container.
	 * {@code level} is the authoritative world used for write-through and decay.
	 */
	default void noteContainer(ServerLevel level, BlockPos containerPos, ItemStack stack) {
	}

	/** Compatibility seam for old callers; production callers must provide the level. */
	@Deprecated
	default void noteContainer(BlockPos containerPos, ItemStack stack) {
		noteContainer(null, containerPos, stack);
	}

	/**
	 * Last-known container positions of tracked objects, independent of any incident zone.
	 * The infection sink re-scans these blocks so an object carried far away and stored in
	 * a chest keeps being observed (issue #110 C6).
	 */
	default java.util.Set<BlockPos> knownContainerPositions() {
		return java.util.Set.of();
	}
}
