package jujutsu.mod.character.nobara;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.PreparedNailsPayload;

public final class HairpinGameplayServiceTest {
	private HairpinGameplayServiceTest() {}

	public static void main(String[] args) {
		assertDamageScalesWithNailCountAndCaps();
		assertKnockbackScalesWithNailCount();
		assertCinematicNailsAreDeterministicAroundCaster();
		assertPreparedNailsFormRowInFrontOfCaster();
		assertPreparedNailRowClampsToFour();
		assertPreparedPayloadCarriesStableRowPositions();
		assertFlightAndFxPayloadsCarryAttachmentIds();
		System.out.println("HairpinGameplayServiceTest passed");
	}

	private static void assertDamageScalesWithNailCountAndCaps() {
		assert HairpinGameplayService.damageForNailCount(1) == 40.0f;
		assert HairpinGameplayService.damageForNailCount(4) == 64.0f;
		assert HairpinGameplayService.damageForNailCount(8) == 64.0f;
	}

	private static void assertKnockbackScalesWithNailCount() {
		assert HairpinGameplayService.knockbackForNailCount(1) == 1.65f;
		assert HairpinGameplayService.knockbackForNailCount(4) == 2.4f;
	}

	private static void assertCinematicNailsAreDeterministicAroundCaster() {
		Vec3 origin = new Vec3(0.0, 1.6, 0.0);
		Vec3 look = new Vec3(1.0, 0.0, 0.0);
		Vec3 target = new Vec3(8.0, 1.6, 0.0);
		List<Vec3> nails = HairpinGameplayService.cinematicNailStarts(origin, look, target, 4);

		assert nails.size() == 4 : nails;
		assert nails.get(0).distanceToSqr(origin) > 0.2 : nails;
		assert nails.get(0).distanceToSqr(nails.get(1)) > 0.1 : nails;
		assert nails.stream().allMatch(nail -> nail.distanceToSqr(target) > 16.0) : nails;
	}

	private static void assertPreparedNailsFormRowInFrontOfCaster() {
		Vec3 origin = new Vec3(0.0, 1.6, 0.0);
		Vec3 look = new Vec3(0.0, 0.0, 1.0);
		List<Vec3> nails = HairpinGameplayService.preparedNailRow(origin, look, 4);

		assert nails.size() == 4 : nails;
		assert close(nails.get(0).z, 1.15) : nails;
		assert close(nails.get(3).z, 1.15) : nails;
		assert nails.get(0).x < nails.get(1).x : nails;
		assert nails.get(1).x < nails.get(2).x : nails;
		assert nails.get(2).x < nails.get(3).x : nails;
		assert close(nails.get(0).y, 1.36) : nails;
		assert close(nails.get(3).y, 1.36) : nails;
	}

	private static void assertPreparedNailRowClampsToFour() {
		Vec3 origin = new Vec3(0.0, 1.6, 0.0);
		Vec3 look = new Vec3(1.0, 0.0, 0.0);

		assert HairpinGameplayService.preparedNailRow(origin, look, -2).isEmpty();
		assert HairpinGameplayService.preparedNailRow(origin, look, 8).size() == 4;
	}

	private static void assertPreparedPayloadCarriesStableRowPositions() {
		List<Vec3> row = HairpinGameplayService.preparedNailRow(new Vec3(0.0, 1.6, 0.0), new Vec3(0.0, 0.0, 1.0), 4);
		PreparedNailsPayload payload = PreparedNailsPayload.create(7, 42, 3, 100L, row);
		List<Vec3> nails = payload.nails();

		assert nails.size() == 3 : nails;
		assert close(nails.get(0).x, row.get(0).x) : nails;
		assert close(nails.get(2).z, row.get(2).z) : nails;
		assert close(payload.direction().z, 1.0) : payload.direction();
	}

	private static void assertFlightAndFxPayloadsCarryAttachmentIds() {
		HairpinNailFlightPayload flight = new HairpinNailFlightPayload(
				7,
				42,
				99,
				3,
				1.0,
				2.0,
				3.0,
				100L,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);
		HairpinFxPayload fx = new HairpinFxPayload(
				7,
				99,
				1.0,
				2.0,
				3.0,
				100L,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);

		assert flight.ownerEntityId() == 42 : flight;
		assert flight.targetEntityId() == 99 : flight;
		assert fx.targetEntityId() == 99 : fx;
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0E-5;
	}
}
