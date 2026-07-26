package jujutsu.mod.character.todo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * The feint: a complete Boogie Woogie clap that moves nobody.
 *
 * <p>The server knows the cast is hollow from the first tick. It never starts a swap and cancels it,
 * so no target is resolved, no destination is planned and no body is ever half-moved — which is why
 * this file contains none of the teleport machinery. What everyone in range gets is the clap
 * performance the real swap emits, from the same method, on the same tick.
 *
 * <p>The one packet the feint does not share goes to the caster alone, so the player who pressed the
 * key knows the cast registered without anyone else learning that nothing followed it.
 */
public final class TodoFakeClapRuntime {
	private TodoFakeClapRuntime() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.PRIMARY_SNEAK) {
			return false;
		}
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
		Vec3 origin = todo.position();
		// Its own cooldown slot, so a feint neither spends nor postpones the real swap.
		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY_SNEAK, TodoProfile.FAKE_CLAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.PRIMARY_SNEAK,
				TodoProfile.FAKE_CLAP_COOLDOWN_TICKS);
		// An aim vector rather than zero. A real swap passes the raw caster-to-target delta, which VfxCue's
		// compact constructor normalizes, so what arrives on the wire is a unit vector pointing roughly
		// where the caster looks. Matching that keeps the cues alike even to a future recipe that reads the
		// field -- and note the likeness depends on VfxCue keeping that normalization.
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, origin, todo.getLookAngle());
		JujutsuNetworking.sendVfxCue(todo, new VfxCue(TodoVfxIds.FEINT_TELL, origin, todo.getId(), Vec3.ZERO, 1,
				level.getGameTime(), todo.getRandom().nextLong(), Vec3.ZERO));
		JujutsuMod.LOGGER.debug("Todo feint clap player={} at={}", todo.getGameProfile().getName(), origin);
		return true;
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo feint clap rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
