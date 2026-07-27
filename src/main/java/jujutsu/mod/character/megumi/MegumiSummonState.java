package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Pure identity and sibling-survival decisions used by the Divine Dog runtime. */
final class MegumiSummonState {
	private MegumiSummonState() {}

	static boolean isSameTickDuplicate(MegumiDivineDogPack pack, long gameTime) {
		return pack != null && pack.wasSummonedAt(gameTime);
	}

	static boolean belongsToPack(
			MegumiDivineDogPack pack, UUID dogId, long token, ResourceKey<Level> dimension) {
		return pack != null && pack.contains(dogId, token, dimension);
	}

	static boolean retainsPack(int livingDogCount) {
		return livingDogCount > 0;
	}
}
