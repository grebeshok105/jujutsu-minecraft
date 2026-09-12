package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiRabbitsPolicyTest {
	@Test
	void upkeepFiresOnlyBelowStrengthAfterTheWindow() {
		int size = MegumiShikigamiProfile.RABBITS_SWARM_SIZE;
		int interval = MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS;
		long summonedAt = 1000L;
		assertTrue(MegumiRabbitsPolicy.shouldRespawn(size - 1, size, summonedAt + interval, summonedAt, interval));
		assertFalse(MegumiRabbitsPolicy.shouldRespawn(size - 1, size, summonedAt + interval - 1, summonedAt, interval),
				"the window has not passed yet");
		assertFalse(MegumiRabbitsPolicy.shouldRespawn(size, size, summonedAt + interval, summonedAt, interval),
				"a full swarm needs no top-up");
		assertFalse(MegumiRabbitsPolicy.shouldRespawn(size + 1, size, summonedAt + interval, summonedAt, interval),
				"an over-strength pack (extra bodies mid-recall) must not respawn");
	}

	@Test
	void upkeepRepeatsEveryWindowAfterTheLastTopUp() {
		int size = MegumiShikigamiProfile.RABBITS_SWARM_SIZE;
		int interval = MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS;
		long lastTopUp = 2000L;
		assertTrue(MegumiRabbitsPolicy.shouldRespawn(0, size, lastTopUp + interval, lastTopUp, interval));
		assertFalse(MegumiRabbitsPolicy.shouldRespawn(0, size, lastTopUp + interval - 1, lastTopUp, interval));
	}

	@Test
	void expiryHitsExactlyAtTheLifetimeBoundary() {
		int lifetime = MegumiShikigamiProfile.RABBITS_LIFETIME_TICKS;
		long summonedAt = 500L;
		assertFalse(MegumiRabbitsPolicy.expired(summonedAt, summonedAt + lifetime - 1, lifetime));
		assertTrue(MegumiRabbitsPolicy.expired(summonedAt, summonedAt + lifetime, lifetime));
		assertTrue(MegumiRabbitsPolicy.expired(summonedAt, summonedAt + lifetime + 1, lifetime));
	}

	@Test
	void respawnBatchCapsAtTheShortfall() {
		int size = MegumiShikigamiProfile.RABBITS_SWARM_SIZE;
		int batch = MegumiShikigamiProfile.RABBITS_RESPAWN_BATCH;
		assertEquals(batch, MegumiRabbitsPolicy.respawnBatch(size - batch - 1, size, batch));
		assertEquals(1, MegumiRabbitsPolicy.respawnBatch(size - 1, size, batch));
		assertEquals(0, MegumiRabbitsPolicy.respawnBatch(size, size, batch));
		assertEquals(0, MegumiRabbitsPolicy.respawnBatch(size + 2, size, batch));
	}

	@Test
	void bumpOpensExactlyAtTheNextWindow() {
		long next = 3000L;
		assertFalse(MegumiRabbitsPolicy.bumpReady(next, next - 1));
		assertTrue(MegumiRabbitsPolicy.bumpReady(next, next));
		assertTrue(MegumiRabbitsPolicy.bumpReady(next, next + 1));
	}

	@Test
	void bumpShovesAwayFromTheRabbitWithALift() {
		Vec3 rabbit = new Vec3(0.0, 64.0, 0.0);
		Vec3 impulse = MegumiRabbitsPolicy.bumpImpulse(rabbit, new Vec3(3.0, 64.0, 4.0));
		double knockback = MegumiShikigamiProfile.RABBITS_BUMP_KNOCKBACK;
		assertEquals(knockback, Math.sqrt(impulse.x * impulse.x + impulse.z * impulse.z), 1.0E-9);
		assertTrue(impulse.dot(new Vec3(3.0, 0.0, 4.0)) > 0.0, "the shove must point away from the rabbit");
		assertTrue(impulse.y > 0.0, "the victim stumbles upward, never into the floor");
	}

	@Test
	void stackedBodiesShoveStraightUp() {
		Vec3 rabbit = new Vec3(1.0, 64.0, 1.0);
		Vec3 impulse = MegumiRabbitsPolicy.bumpImpulse(rabbit, rabbit);
		assertEquals(0.0, impulse.x, 1.0E-9);
		assertEquals(0.0, impulse.z, 1.0E-9);
		assertTrue(impulse.y > 0.0);
	}
}
