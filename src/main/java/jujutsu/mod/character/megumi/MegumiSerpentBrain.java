package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Serpent behaviour: FOLLOW → READY → PREPARE_AMBUSH → SUBMERGED → EMERGE → BIND → RELEASE →
 * RECOVERY. Groundwork stub — the ambush/bind loop is filled by the dedicated worker island.
 */
final class MegumiSerpentBrain {
	private MegumiSerpentBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiSerpentEntity serpent, long gameTime) {
	}
}
