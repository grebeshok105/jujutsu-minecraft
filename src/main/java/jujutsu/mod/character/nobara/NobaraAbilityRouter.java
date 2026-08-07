package jujutsu.mod.character.nobara;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkMegaNailRuntime;
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

	public static AbilityResult tryCast(ServerPlayer nobara, CharacterAbility ability, boolean notify) {
		if (CombatStagger.GLOBAL.isStaggered(nobara.getUUID(), nobara.level().getGameTime())) {
			// Silent: being staggered is already legible on screen, and the fallback line below would
			// read as "no target", which is the wrong explanation. This early return bypasses the
			// message gate below, so returning UNHANDLED_FAILURE here stays silent by construction.
			return AbilityResult.UNHANDLED_FAILURE;
		}
		// The hold gesture, its release and the empty third technique key are not casts; refusing them
		// through the switch would show the no-target line to anyone who holds Shift+B out of habit or
		// taps V on a vessel with nothing there — and "no target" would be the wrong explanation for an
		// unclaimed key. Refused silently, before the message gate. The switch below still answers all
		// three arms so it stays exhaustive.
		if (ability == CharacterAbility.SECONDARY_SNEAK_HOLD || ability == CharacterAbility.SECONDARY_SNEAK_RELEASE
				|| ability == CharacterAbility.TERTIARY || ability == CharacterAbility.TERTIARY_SNEAK) {
			return AbilityResult.UNHANDLED_FAILURE;
		}
		AbilityResult result = switch (ability) {
			// Short-circuit: when the explosive lock refuses, nothing has been said and the fallback
			// speaks; otherwise the runtime's own result is returned verbatim.
			case PRIMARY -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)
					? ProjectJjkRitualRuntime.startDirectedHairpin(nobara)
					: AbilityResult.UNHANDLED_FAILURE;
			case PRIMARY_SNEAK -> SelfResonanceRuntime.tryCast(nobara);
			// B — Mega Nail: converges every embedded nail on the aimed target into one piercing strike.
			case SECONDARY -> ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)
					? ProjectJjkMegaNailRuntime.start(nobara)
					: AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY_SNEAK -> NailTrapRuntime.tryPlace(nobara);
			case ATTACK_CONTEXT -> NobaraHammerCombatRuntime.handleInput(nobara);
			// Her right click is vanilla's, and she holds no technique key. Answering UNHANDLED_FAILURE
			// explicitly rather than adding a default keeps the next new slot a compile error here
			// instead of a silent no-op.
			case USE_CONTEXT -> AbilityResult.UNHANDLED_FAILURE;
			// Unreachable through the early return above; kept so the switch stays exhaustive.
			case SECONDARY_SNEAK_HOLD -> AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY_SNEAK_RELEASE -> AbilityResult.UNHANDLED_FAILURE;
			// Unreachable through the early return above; kept so the switch stays exhaustive.
			case TERTIARY -> AbilityResult.UNHANDLED_FAILURE;
			case TERTIARY_SNEAK -> AbilityResult.UNHANDLED_FAILURE;
		};
		if (result == AbilityResult.UNHANDLED_FAILURE && notify) {
			nobara.displayClientMessage(Component.translatable("message.jujutsumod.nobara.action.no_target"), true);
		}
		return result;
	}
}
