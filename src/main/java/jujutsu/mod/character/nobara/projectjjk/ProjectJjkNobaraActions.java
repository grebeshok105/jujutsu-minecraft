package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.CombatStagger;

public final class ProjectJjkNobaraActions {
	public static final int HAIRPIN_DIRECTED = 0;
	public static final int HAIRPIN_MASS = 1;
	public static final int HAMMER_CONTEXT = 2;
	public static final int NAIL_LAUNCH_EXPLOSIVE = HAMMER_CONTEXT;
	public static final int SELF_RESONANCE = 3;
	public static final int NAIL_TRAP = 4;
	@Deprecated public static final int HAIRPIN_ENLARGE = HAIRPIN_DIRECTED;
	@Deprecated public static final int HAIRPIN_EXPLOSION = HAIRPIN_MASS;

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
			case HAIRPIN_DIRECTED -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.startDirectedHairpin(player);
			case HAIRPIN_MASS -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(player) && ProjectJjkRitualRuntime.startMassHairpin(player);
			case HAMMER_CONTEXT -> NobaraHammerCombatRuntime.handleInput(player);
			case SELF_RESONANCE -> SelfResonanceRuntime.tryCast(player);
			case NAIL_TRAP -> NailTrapRuntime.tryPlace(player);
			default -> false;
		};
		if (!cast && notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return cast;
	}
}
