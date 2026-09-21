package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedincident.infection.InfectionPolicy;

/** R16/R17/R28/R31/R36 mapping and determinism oracles. */
final class InfectionPolicyTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void grassProgressesAtPinnedBoundaries() {
		assertEquals(Blocks.COARSE_DIRT.defaultBlockState(), InfectionPolicy
				.mapBlock(Blocks.GRASS_BLOCK.defaultBlockState(), IncidentStage.GROWING, RandomSource.create(1L)).orElseThrow());
		assertEquals(Blocks.PODZOL.defaultBlockState(), InfectionPolicy
				.mapBlock(Blocks.GRASS_BLOCK.defaultBlockState(), IncidentStage.INFESTED, RandomSource.create(1L)).orElseThrow());
		assertEquals(Blocks.SOUL_SOIL.defaultBlockState(), InfectionPolicy
				.mapBlock(Blocks.GRASS_BLOCK.defaultBlockState(), IncidentStage.CRITICAL, RandomSource.create(1L)).orElseThrow());
	}

	@Test
	void vegetationAndLightingBoundariesAreIrreversible() {
		assertEquals(Blocks.DEAD_BUSH.defaultBlockState(), InfectionPolicy
				.mapBlock(Blocks.TALL_GRASS.defaultBlockState(), IncidentStage.GROWING, RandomSource.create(2L)).orElseThrow());
		assertTrue(InfectionPolicy.mapBlock(Blocks.GLASS.defaultBlockState(), IncidentStage.INFESTED,
				RandomSource.create(2L)).orElseThrow().isAir());
		assertTrue(InfectionPolicy.mapBlock(Blocks.TORCH.defaultBlockState(), IncidentStage.GROWING,
				RandomSource.create(2L)).orElseThrow().isAir());
	}

	@Test
	void containersUseDropPreservingPath() {
		assertTrue(InfectionPolicy.isContainer(Blocks.CHEST.defaultBlockState()));
		assertTrue(InfectionPolicy.isContainer(Blocks.BEACON.defaultBlockState()));
		assertFalse(InfectionPolicy.isContainer(Blocks.STONE.defaultBlockState()));
	}

	@Test
	void sameSeedMappingIsDeterministic() {
		var state = Blocks.OAK_LOG.defaultBlockState();
		assertEquals(InfectionPolicy.mapBlock(state, IncidentStage.INFESTED, RandomSource.create(77L)),
				InfectionPolicy.mapBlock(state, IncidentStage.INFESTED, RandomSource.create(77L)));
	}
}
