package jujutsu.mod.character.megumi;

/**
 * Pure friendly-fire and target-liveness truth table shared by every Ten Shadows target path.
 *
 * <p>{@code ownSummonBody} covers both layers — the Divine Dogs and the whole non-dog shikigami
 * roster — because a sibling body is never a legal mark, however it crosses an aim or an
 * auto-acquired target (the retaliation pass of issue #76 reads the same filter).
 */
final class MegumiTargetPolicy {
	private MegumiTargetPolicy() {}

	static boolean accepts(Facts facts) {
		return !facts.owner()
				&& facts.alive()
				&& facts.loaded()
				&& facts.sameLevel()
				&& !facts.spectator()
				&& !facts.ownSummonBody()
				&& !facts.allied();
	}

	record Facts(
			boolean owner,
			boolean alive,
			boolean loaded,
			boolean sameLevel,
			boolean spectator,
			boolean ownSummonBody,
			boolean allied
	) {}
}
