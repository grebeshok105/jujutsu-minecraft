package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedincident.infection.InfectionQueue;

/** R70/R71 queue ordering and bounded-drain seam. */
final class InfectionQueueTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void fifoAcceptsEditsAndExposesCount() {
		IncidentRecord record = new IncidentRecord();
		record.id = UUID.randomUUID();
		InfectionQueue queue = new InfectionQueue(record);
		queue.enqueue(new BlockPos(1, 2, 3), Blocks.COARSE_DIRT.defaultBlockState());
		queue.enqueue(new BlockPos(4, 5, 6), Blocks.PODZOL.defaultBlockState());
		assertEquals(2, queue.size());
		queue.clear();
		assertTrue(queue.isEmpty());
	}

	@Test
	void requestedDrainCannotExceedSharedBudget() {
		assertEquals(0, InfectionQueue.boundedBudget(-4));
		assertEquals(32, InfectionQueue.boundedBudget(32));
		assertEquals(64, InfectionQueue.boundedBudget(1000));
	}
}
