package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.CombatStagger;

public final class ProjectJjkNobaraActions {
	public static final int HAIRPIN_ENLARGE = 0;
	public static final int HAIRPIN_EXPLOSION = 1;
	public static final int HAMMER_CONTEXT = 2;
	public static final int NAIL_LAUNCH_EXPLOSIVE = HAMMER_CONTEXT;
	public static final int SELF_RESONANCE = 3;
	public static final int NAIL_TRAP = 4;
	public static final int HAIRPIN_DIRECTED = HAIRPIN_ENLARGE;
	public static final int HAIRPIN_MASS = HAIRPIN_EXPLOSION;

	private ProjectJjkNobaraActions() {}

	public static boolean tryCast(ServerPlayer player, int action, boolean notify) {
		if (CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.not_selected"), true);
			}
			return false;
		}
		if (CombatStagger.GLOBAL.isStaggered(player.getUUID(), player.level().getGameTime())) return false;
		boolean cast = switch (action) {
			case HAIRPIN_ENLARGE -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.tryEnlargeMarkedTarget(player);
			case HAIRPIN_EXPLOSION -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.detonateMarks(player);
			case HAMMER_CONTEXT -> NobaraHammerCombatRuntime.handleInput(player);
			case SELF_RESONANCE -> SelfResonanceRuntime.tryCast(player);
			default -> false;
		};
		if (!cast && notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return cast;
	}
}
