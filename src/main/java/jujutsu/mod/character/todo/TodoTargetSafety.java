package jujutsu.mod.character.todo;

/** Pure transport-state policy used before a living entity can participate in a position swap. */
public final class TodoTargetSafety {
	private TodoTargetSafety() {}

	public static boolean hasUnsafeTransportState(boolean passenger, boolean vehicle, boolean leashed) {
		return passenger || vehicle || leashed;
	}
}
