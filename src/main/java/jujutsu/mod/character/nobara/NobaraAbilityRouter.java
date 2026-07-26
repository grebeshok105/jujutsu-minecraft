package jujutsu.mod.character.nobara;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;
import jujutsu.mod.combat.CombatStagger;

/**
 * Nobara's slot map: what each input position means for her.
 *
 * <p>The switch is exhaustive on purpose, so a new {@link CharacterAbility} constant fails compilation
 * here rather than falling into whichever arm a {@code default} would have chosen. She fills the five
 * slots her kit was built around and refuses the sixth explicitly.
 *
 * <p>Two things live here and not in the shared executor because they are hers alone: the stagger check,
 * which she has and Todo does not, and the single fallback message every failed cast of hers produces.
 * Both were inherited verbatim from the bespoke int-keyed gate this class replaced, in that order.
 */
public final class NobaraAbilityRouter {
	private NobaraAbilityRouter() {}

	public static boolean tryCast(ServerPlayer nobara, CharacterAbility ability, boolean notify) {
		if (CombatStagger.GLOBAL.isStaggered(nobara.getUUID(), nobara.level().getGameTime())) {
			// Silent: being staggered is already legible on screen, and the fallback line below would
			// read as "no target", which is the wrong explanation.
			return false;
		}
		boolean cast = switch (ability) {
			case PRIMARY -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)
					&& ProjectJjkRitualRuntime.startDirectedHairpin(nobara);
			case PRIMARY_SNEAK -> SelfResonanceRuntime.tryCast(nobara);
			case SECONDARY -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)
					&& ProjectJjkRitualRuntime.startMassHairpin(nobara);
			case SECONDARY_SNEAK -> NailTrapRuntime.tryPlace(nobara);
			case ATTACK_CONTEXT -> NobaraHammerCombatRuntime.handleInput(nobara);
			// Her right click is vanilla's. Answering false explicitly rather than adding a default keeps
			// the next new slot a compile error here instead of a silent no-op.
			case USE_CONTEXT -> false;
		};
		if (!cast && notify) {
			nobara.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return cast;
	}
}
