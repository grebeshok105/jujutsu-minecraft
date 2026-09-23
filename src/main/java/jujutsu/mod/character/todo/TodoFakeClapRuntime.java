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
import jujutsu.mod.vfx.VfxCues;

/** Shift+R deception clap: no node resolution, plan, commit, movement, or momentum. */
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
		int intensity = 1 + TodoSwapHooks.beatOf(todo);
		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY_SNEAK, TodoProfile.FAKE_CLAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.PRIMARY_SNEAK, TodoProfile.FAKE_CLAP_COOLDOWN_TICKS);
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, origin, todo.getLookAngle(),
				TodoVfxIds.FEINT_CLAP, intensity);
		TodoBoogieWoogieRuntime.scheduleDisplacementWhoosh(level, origin);
		// Caster-only feint confirmation: the observer must see nothing a real swap would not show, so
		// this cue is a direct send, never a broadcast.
		JujutsuNetworking.sendVfxCue(todo,
				VfxCues.anchored(TodoVfxIds.FEINT_TELL, origin, todo.getId(), todo.position(), 1,
						level.getGameTime(), todo.getRandom().nextLong()));
		JujutsuMod.LOGGER.debug("Todo fake clap player={} at={}", todo.getGameProfile().getName(), origin);
		return true;
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo fake clap rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
