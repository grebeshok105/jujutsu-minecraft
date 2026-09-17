package jujutsu.mod.cursedincident.policy;

import net.minecraft.util.RandomSource;

/**
 * Pure seal rules (issue #110 spec §12): required tier per object grade, integrity
 * floors, and the rare catastrophic-failure roll (a fresh quality seal is never an
 * absolute guarantee). Owned by Block 1 so {@code IncidentControl.damageSeal} compiles
 * without importing the object package; Block 2's {@code SealPolicyTest} pins the
 * probability bounds.
 */
public final class SealPolicy {

	private SealPolicy() {
	}

	/** Minimum talisman tier (1..3) that can seal an object of {@code grade} (1..5). */
	public static int requiredTier(int grade) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/**
	 * Rare catastrophic seal failure (spec §12.3). Returns true when the seal snaps
	 * despite remaining integrity; probability bounded by {@code integrity} — lower
	 * integrity, higher chance. Seeded for determinism (R41).
	 */
	public static boolean maybeCatastrophicFail(RandomSource random, int integrity) {
		throw new UnsupportedOperationException("block-1 pending");
	}
}
