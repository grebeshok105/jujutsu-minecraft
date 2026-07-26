package jujutsu.mod.character;

import net.minecraft.server.level.ServerPlayer;

/**
 * The absence of a vessel, as an object rather than as a null.
 *
 * <p>Shared code asks the registry for a definition and gets one for every player, so no caller has to
 * remember that "no vessel selected" is a case. It casts nothing and owns nothing; every other hook is
 * inherited as the do-nothing default, which is exactly right — including {@link #removeAttributes},
 * since it never added any.
 */
final class NoneDefinition implements CharacterDefinition {
	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.NONE;
	}

	@Override
	public boolean tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		// Unreachable through the executor, which refuses NONE with a message before dispatching. Stated
		// here anyway so the answer does not depend on that check staying where it is.
		return false;
	}
}
