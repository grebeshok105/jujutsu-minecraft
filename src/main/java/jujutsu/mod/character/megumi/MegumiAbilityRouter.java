package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;

/** Megumi's two occupied input positions; the runtimes land in later reviewable stages. */
public final class MegumiAbilityRouter {
	private MegumiAbilityRouter() {}

	public static boolean tryCast(ServerPlayer player, CharacterAbility ability, boolean notify) {
		return switch (ability) {
			case PRIMARY -> tryDivineDogs(player, notify);
			case PRIMARY_SNEAK -> trySic(player, notify);
			case SECONDARY, SECONDARY_SNEAK, ATTACK_CONTEXT, USE_CONTEXT -> false;
		};
	}

	private static boolean tryDivineDogs(ServerPlayer player, boolean notify) {
		return MegumiSummonRuntime.tryToggle(player, notify);
	}

	private static boolean trySic(ServerPlayer player, boolean notify) {
		return false;
	}
}
