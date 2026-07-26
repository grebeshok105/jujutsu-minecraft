package jujutsu.mod.combat;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class TargetResolverTest {
	private TargetResolverTest() {}

	public static void main(String[] args) {
		assertEntitySweepBeatsFartherBlock();
		assertCrosshairPriorityBeatsNearerOffAxisCandidate();
		assertBlockFallbackWhenNoEntityIsNearRay();
		assertMissUsesMaxRangePoint();
		assertOwnerCandidateIsIgnored();
		System.out.println("TargetResolverTest passed");
	}

	private static void assertEntitySweepBeatsFartherBlock() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.of(new TargetResolver.BlockCandidate(new Vec3(12.0, 1.6, 0.0), new Vec3(-1.0, 0.0, 0.0))),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.35), 0.75),
						new TargetResolver.EntityCandidate(11, new Vec3(5.0, 1.6, 2.4), 0.75)
				),
				99
		);

		assert result.mode() == TargetResolver.Mode.ENTITY : result;
		assert result.entityId().orElseThrow() == 7 : result;
		assert close(result.point(), new Vec3(6.0, 1.6, 0.35), 0.001) : result.point();
	}

	private static void assertCrosshairPriorityBeatsNearerOffAxisCandidate() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
					new Vec3(0.0, 1.6, 0.0),
					new Vec3(1.0, 0.0, 0.0),
					24.0,
					Optional.empty(),
					List.of(
							new TargetResolver.EntityCandidate(7, new Vec3(4.0, 1.6, 1.2), 0.25),
							new TargetResolver.EntityCandidate(11, new Vec3(8.0, 1.6, 0.05), 0.25)
					),
					99
			);

			assert result.mode() == TargetResolver.Mode.ENTITY : result;
			assert result.entityId().orElseThrow() == 11 : "The target closest to the crosshair center must win: " + result;
	}

	private static void assertBlockFallbackWhenNoEntityIsNearRay() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.of(new TargetResolver.BlockCandidate(new Vec3(10.0, 1.6, 0.0), new Vec3(-1.0, 0.0, 0.0))),
				List.of(new TargetResolver.EntityCandidate(3, new Vec3(5.0, 1.6, 4.0), 0.75)),
				99
		);

		assert result.mode() == TargetResolver.Mode.BLOCK : result;
		assert result.entityId().isEmpty() : result;
		assert close(result.point(), new Vec3(10.0, 1.6, 0.0), 0.001) : result.point();
	}

	private static void assertMissUsesMaxRangePoint() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(1.0, 2.0, 3.0),
				new Vec3(0.0, 0.0, 1.0),
				32.0,
				Optional.empty(),
				List.of(),
				99
		);

		assert result.mode() == TargetResolver.Mode.MISS : result;
		assert result.entityId().isEmpty() : result;
		assert close(result.point(), new Vec3(1.0, 2.0, 35.0), 0.001) : result.point();
	}

	private static void assertOwnerCandidateIsIgnored() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(new TargetResolver.EntityCandidate(4, new Vec3(4.0, 1.6, 0.0), 0.75)),
				4
		);

		assert result.mode() == TargetResolver.Mode.MISS : result;
	}

	private static boolean close(Vec3 left, Vec3 right, double epsilon) {
		return left.distanceToSqr(right) <= epsilon * epsilon;
	}
}
