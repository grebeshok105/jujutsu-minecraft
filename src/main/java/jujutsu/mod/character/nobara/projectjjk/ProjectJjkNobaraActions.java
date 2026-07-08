package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;

public final class ProjectJjkNobaraActions {
	public static final int HAIRPIN_ENLARGE = 0;
	public static final int HAIRPIN_EXPLOSION = 1;

	private ProjectJjkNobaraActions() {}

	public static boolean tryCast(ServerPlayer player, int action, boolean notify) {
		if (CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.not_selected"), true);
			}
			return false;
		}
		boolean cast = switch (action) {
			case HAIRPIN_ENLARGE -> ProjectJjkRitualRuntime.tryEnlargeMarkedTarget(player);
			case HAIRPIN_EXPLOSION -> ProjectJjkRitualRuntime.detonateMarks(player);
			default -> false;
		};
		if (!cast && notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return cast;
	}
}
