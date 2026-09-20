package jujutsu.mod.client.tongue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class MegumiTonguePhaseTest {
	private static final double EPSILON = 1.0E-6;
	private static final Vec3 MOUTH = new Vec3(1.0, 65.0, 2.0);
	private static final Vec3 ANCHOR = new Vec3(9.0, 62.0, -4.0);

	@Test
	void wirePhasesAndDurationsMatchPhysicalLifecycle() {
		assertEquals(TongueClientState.Phase.SHOOTING,
				TongueClientState.Phase.fromWire(0));
		assertEquals(TongueClientState.Phase.ANCHORED,
				TongueClientState.Phase.fromWire(1));
		assertEquals(TongueClientState.Phase.RETRACTING,
				TongueClientState.Phase.fromWire(2));
		assertEquals(4, TongueClientState.Phase.SHOOTING.durationTicks());
		assertEquals(5, TongueClientState.Phase.RETRACTING.durationTicks());
		assertNull(TongueClientState.Phase.fromWire(99));
	}

	@Test
	void shootingAndRetractingMoveTheTipWithoutAnInstantLine() {
		Vec3 shootStart = TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.SHOOTING, 0.0f);
		Vec3 shootMid = TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.SHOOTING, 0.5f);
		Vec3 anchored = TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.ANCHORED, 1.0f);
		Vec3 retractStart = TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.RETRACTING, 0.0f);
		Vec3 retractEnd = TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.RETRACTING, 1.0f);

		assertEquals(MOUTH, shootStart);
		assertNotNull(shootMid);
		assertEquals(MOUTH.lerp(ANCHOR, 0.5), shootMid);
		assertEquals(ANCHOR, TongueClientState.tipPosition(
				MOUTH, ANCHOR, TongueClientState.Phase.ANCHORED, 0.0f));
		assertEquals(0.0, retractEnd.distanceTo(MOUTH), EPSILON);
	}
}
