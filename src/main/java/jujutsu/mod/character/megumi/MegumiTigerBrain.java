package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tiger behaviour: STALK/APPROACH → COMBO_WINDUP → STRIKE_1 → STRIKE_2 → FINISHER → RECOVERY.
 * The combo is committed and never retargets; a miss is a miss. Groundwork stub — the combo loop
 * is filled by the dedicated worker island.
 */
final class MegumiTigerBrain {
	private MegumiTigerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiTigerEntity tiger, long gameTime) {
	}
}
