package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Deer behaviour: follow the owner, scan wounded friendlies on a fixed cadence, pulse a heal,
 * cleanse on a slower cadence, shove attackers that step inside antler range. Groundwork stub —
 * the heal/cleanse loop is filled by the dedicated worker island.
 */
final class MegumiDeerBrain {
	private MegumiDeerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiDeerEntity deer, long gameTime) {
	}
}
