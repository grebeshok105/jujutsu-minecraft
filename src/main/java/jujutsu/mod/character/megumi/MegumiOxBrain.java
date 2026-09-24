package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ox behaviour: FOLLOW → ACQUIRE → ALIGN → WINDUP → CHARGE → IMPACT/PASS_THROUGH → RECOVERY.
 * The committed line is frozen at windup — the charge never homes. Groundwork stub — the charge
 * loop is filled by the dedicated worker island.
 */
final class MegumiOxBrain {
	private MegumiOxBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiOxEntity ox, long gameTime) {
	}
}
