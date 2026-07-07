package jujutsu.mod.character.nobara;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class HairpinGameplayServiceTest {
	private HairpinGameplayServiceTest() {}

	public static void main(String[] args) {
		assertDamageScalesWithNailCountAndCaps();
		assertKnockbackScalesWithNailCount();
		assertCinematicNailsAreDeterministicAroundCaster();
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
}
