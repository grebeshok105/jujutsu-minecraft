package jujutsu.mod.character.megumi;

/**
 * Nue's partial wings (issue #108): the {@code EntityElytraEvents.ALLOW}/{@code CUSTOM} handlers
 * that grant fall-flying while the owner carries {@code MEGUMI_NUE_WINGS}, plus the fall-damage
 * veto. Contract skeleton — the event wiring lands with the wings block.
 */
public final class MegumiNueWings {
	private MegumiNueWings() {}
	public static void register() {}

	/** One-shot unfold cue at the owner; the wings block fills in the emit. */
	public static void playUnfoldCue(net.minecraft.server.level.ServerPlayer player) {}
}
