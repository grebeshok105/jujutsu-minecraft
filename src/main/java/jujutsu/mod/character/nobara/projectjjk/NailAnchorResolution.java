package jujutsu.mod.character.nobara.projectjjk;

public enum NailAnchorResolution {
	RESOLVED,
	TEMPORARILY_UNAVAILABLE,
	CONFIRMED_REMOVED,
	INVALID;

	public static NailAnchorResolution forEntity(boolean loaded, boolean confirmedRemoved, boolean identityMatches) {
		if (confirmedRemoved) {
			return CONFIRMED_REMOVED;
		}
		if (!loaded) {
			return TEMPORARILY_UNAVAILABLE;
		}
		return identityMatches ? RESOLVED : INVALID;
	}

	public static NailAnchorResolution forBlock(boolean chunkLoaded, boolean stateMatches) {
		if (!chunkLoaded) {
			return TEMPORARILY_UNAVAILABLE;
		}
		return stateMatches ? RESOLVED : CONFIRMED_REMOVED;
	}
}
