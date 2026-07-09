package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;

public final class ProjectJjkNobaraActions {
	public static final int HAIRPIN_ENLARGE = 0;
	public static final int HAIRPIN_EXPLOSION = 1;
	public static final int NAIL_LAUNCH_EXPLOSIVE = 2;

	private ProjectJjkNobaraActions() {}

	public static boolean tryCast(ServerPlayer player, int action, boolean notify) {
		if (CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.not_selected"), true);
			}
			return false;
		}
		boolean cast = switch (action) {
			case HAIRPIN_ENLARGE -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.tryEnlargeMarkedTarget(player);
			case HAIRPIN_EXPLOSION -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.detonateMarks(player);
			case NAIL_LAUNCH_EXPLOSIVE -> ProjectJjkNobaraRuntime.launchHairpin(player, true);
			default -> false;
		};
		if (!cast && notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return cast;
	}
}
