package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class MegumiSummonStateTest {
	private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
	private static final ResourceKey<Level> NETHER = Level.NETHER;
	private static final UUID WHITE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID BLACK = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final MegumiDivineDogPack PACK = new MegumiDivineDogPack(OVERWORLD, WHITE, BLACK, 41L, 120L);

	@Test
	void duplicateGuardAppliesOnlyOnTheSummonTick() {
		assertTrue(MegumiSummonState.isSameTickDuplicate(PACK, 120L));
		assertFalse(MegumiSummonState.isSameTickDuplicate(PACK, 121L));
	}

	@Test
	void membershipRejectsStaleTokenAndDimension() {
		assertTrue(MegumiSummonState.belongsToPack(PACK, WHITE, 41L, OVERWORLD));
		assertTrue(MegumiSummonState.belongsToPack(PACK, BLACK, 41L, OVERWORLD));
		assertFalse(MegumiSummonState.belongsToPack(PACK, WHITE, 40L, OVERWORLD));
		assertFalse(MegumiSummonState.belongsToPack(PACK, WHITE, 41L, NETHER));
		assertFalse(MegumiSummonState.belongsToPack(PACK, UUID.randomUUID(), 41L, OVERWORLD));
	}

	@Test
	void oneLivingSiblingRetainsThePack() {
		assertTrue(MegumiSummonState.retainsPack(2));
		assertTrue(MegumiSummonState.retainsPack(1));
		assertFalse(MegumiSummonState.retainsPack(0));
	}
}
