package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MegumiRabbitsBrainTest {
	@Test
	void aFreshPackStartsFromItsOwnSummonTick() {
		UUID owner = UUID.randomUUID();
		assertEquals(50L, MegumiRabbitsBrain.lastUpkeepOrDefault(owner, 7L, 50L),
				"no mark yet: the window opens one interval after the summon");
		MegumiRabbitsBrain.noteUpkeep(owner, 7L, 100L);
		assertEquals(100L, MegumiRabbitsBrain.lastUpkeepOrDefault(owner, 7L, 50L),
				"same pack: the stored window rules");
		assertEquals(50L, MegumiRabbitsBrain.lastUpkeepOrDefault(owner, 8L, 50L),
				"a new pack (new token) must not inherit the old swarm's clock — neither an early "
						+ "top-up from a stale stamp nor a late one from a stamp newer than the summon");
	}

	@Test
	void marksWithoutPacksAreSwept() {
		MegumiRabbitsBrain.dropUpkeepWithoutPack();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		MegumiRabbitsBrain.noteUpkeep(first, 1L, 10L);
		MegumiRabbitsBrain.noteUpkeep(second, 1L, 20L);
		assertEquals(2, MegumiRabbitsBrain.dropUpkeepWithoutPack(),
				"the unit JVM holds no packs, so both marks are pack-less");
		assertEquals(0, MegumiRabbitsBrain.dropUpkeepWithoutPack());
		assertEquals(9L, MegumiRabbitsBrain.lastUpkeepOrDefault(first, 1L, 9L),
				"swept marks read as absent");
	}

	@Test
	void spawnOrderStaggersTheFirstBumpWindow() {
		Set<Integer> offsets = new HashSet<>();
		for (int index = 0; index < MegumiShikigamiProfile.RABBITS_SWARM_SIZE; index++) {
			int offset = MegumiRabbitsBrain.staggerOffset(index);
			assertTrue(offset >= 0
					&& offset < MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS,
					"offset " + offset + " escapes the bump period");
			offsets.add(offset);
		}
		assertEquals(MegumiShikigamiProfile.RABBITS_SWARM_SIZE, offsets.size(),
				"all ten bodies sharing one window is the stacking bug: each body needs its own tick");
	}

	@Test
	void theStaggerWrapsAndTreatsUnknownBodiesAsImmediate() {
		assertEquals(MegumiRabbitsBrain.staggerOffset(0),
				MegumiRabbitsBrain.staggerOffset(MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS),
				"replacement bodies past the first ten reuse the ripple instead of piling onto one tick");
		assertEquals(MegumiRabbitsBrain.staggerOffset(3), MegumiRabbitsBrain.staggerOffset(3),
				"the offset is a pure function of the index, never random");
		assertEquals(0, MegumiRabbitsBrain.staggerOffset(-1),
				"a body missing from the pack record fires at once rather than never");
	}
}
