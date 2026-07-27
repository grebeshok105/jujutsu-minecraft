package jujutsu.mod.character.megumi;

/** Pure friendly-fire and target-liveness truth table shared by every Divine Dog target path. */
final class MegumiTargetPolicy {
	private MegumiTargetPolicy() {}

	static boolean accepts(Facts facts) {
		return !facts.owner()
				&& facts.alive()
				&& facts.loaded()
				&& facts.sameLevel()
				&& !facts.spectator()
				&& !facts.ownDog()
				&& !facts.allied();
	}

	record Facts(
			boolean owner,
			boolean alive,
			boolean loaded,
			boolean sameLevel,
			boolean spectator,
			boolean ownDog,
			boolean allied
	) {}
}
