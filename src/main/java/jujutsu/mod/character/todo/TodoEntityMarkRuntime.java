package jujutsu.mod.character.todo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Marking a body by hand: two right clicks put the swap mark on whoever is under the crosshair.
 *
 * <p>This is not a second mark system. It produces the same {@code ENTITY}-form mark the thrown marker
 * produces, through the same {@link TodoSwapMarks#markBody} call, so it keeps the same ten seconds, the
 * same glow ownership rule and the same single cleanup path. What it removes is the item: a mark on a
 * body no longer costs a throw.
 *
 * <p>It also inherits the one-mark rule, and that is a real cost rather than an oversight — marking a
 * body replaces a landed anchor, so the two are alternatives and not a stockpile.
 *
 * <p>The mark is deliberately <b>not</b> silent to observers: the glow it applies is public, so hiding
 * the cast would only mislead the caster about how visible he is. That is the opposite of the feint,
 * whose cue is caster-only precisely because it must leave no trace.
 */
public final class TodoEntityMarkRuntime {
	private TodoEntityMarkRuntime() {}

	static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.USE_CONTEXT) {
			return false;
		}
		// The same gate the claps read. Marking is part of the technique, so a Todo who cannot clap -- in a
		// boat, staggered, holding something -- cannot mark either, and no cast of his tells a different
		// story about his state than the others do.
		switch (TodoSwapGates.evaluate(todo)) {
			case UNAVAILABLE -> {
				return false;
			}
			case HANDS_FULL -> {
				return reject(todo, notify, "message.jujutsumod.todo.boogie.hands_full", "item in main or off hand");
			}
			case ALLOWED -> {
			}
		}
		ServerLevel level = todo.level();
		LivingEntity target = resolveAimed(todo, level);
		if (target == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed body to mark");
		}
		TodoSwapMarks.markBody(level, todo.getUUID(), target);
		CharacterAbilityCooldowns.start(todo, CharacterAbility.USE_CONTEXT, TodoProfile.ENTITY_MARK_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.USE_CONTEXT, TodoProfile.ENTITY_MARK_COOLDOWN_TICKS);
		Vec3 origin = target.position();
		JujutsuNetworking.broadcastVfxCue(level, origin, TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS,
				new VfxCue(TodoVfxIds.PAIR_MARK, origin, target.getId(), Vec3.ZERO, 1, level.getGameTime(),
						todo.getRandom().nextLong(), Vec3.ZERO));
		if (notify) {
			todo.displayClientMessage(
					Component.translatable("message.jujutsumod.todo.mark.placed", target.getDisplayName()), true);
		}
		JujutsuMod.LOGGER.debug("Todo entity mark placed player={} target={}",
				todo.getGameProfile().getName(), target.getName().getString());
		return true;
	}

	/**
	 * Same resolver, same range and same eligibility rule as the aimed swap, so a body you can mark is
	 * exactly a body you could have swapped with. Line of sight is required here — unlike the swap onto an
	 * already-thrown mark — because this cast reaches out and touches someone right now.
	 */
	private static LivingEntity resolveAimed(ServerPlayer todo, ServerLevel level) {
		TargetResolver.Result aimed = TargetResolver.resolve(level, todo, TodoProfile.BOOGIE_WOOGIE_RANGE,
				candidate -> TodoBoogieWoogieRuntime.isEligibleTarget(todo, candidate));
		if (aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()) {
			return null;
		}
		Entity entity = level.getEntity(aimed.entityId().get());
		if (!(entity instanceof LivingEntity target) || !TodoBoogieWoogieRuntime.isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)) {
			return null;
		}
		return target;
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo entity mark rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
