package jujutsu.mod.combat;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class TargetResolverTest {
	private TargetResolverTest() {}

	public static void main(String[] args) {
		assertCloserEntityHitBeatsFartherEntity();
		assertRealHitBeatsAimAssistGraze();
		assertCrosshairAngleDecidesBetweenGrazes();
		assertDistanceDecidesBetweenEquallyAimedGrazes();
		assertTiesBreakOnEntityIdRegardlessOfOrder();
		assertEntityHitBeatsBlockWhenCloser();
		assertBlockWinsWhenEntityIsBehindBlock();
		assertBlockFallbackWhenNoEntityHit();
		assertMissUsesMaxRangePoint();
		assertOwnerCandidateIsIgnored();
		System.out.println("TargetResolverTest passed");
	}

	private static void assertCloserEntityHitBeatsFartherEntity() {
		// hitDistance is the ray–AABB entry distance (not center depth).
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.35), 0.75, 5.5),
						new TargetResolver.EntityCandidate(11, new Vec3(8.0, 1.6, 0.05), 0.75, 7.5)
				),
				99
		);

		assert result.mode() == TargetResolver.Mode.ENTITY : result;
		assert result.entityId().orElseThrow() == 7 : "Closest ray–AABB hit must win: " + result;
	}

	private static void assertRealHitBeatsAimAssistGraze() {
		// The near candidate was only caught by the 0.35 aim-assist pad; the far one is under the crosshair.
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(4.0, 1.6, 1.1), 0.75, 4.0, false),
						new TargetResolver.EntityCandidate(11, new Vec3(9.0, 1.6, 0.0), 0.75, 8.6, true)
				),
				99
		);

		assert result.entityId().orElseThrow() == 11 : "Aim assist must not steal the target you are looking at: " + result;
	}

	private static void assertCrosshairAngleDecidesBetweenGrazes() {
		// Neither body is under the crosshair, so the one nearer the aim line wins even though it is farther.
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.9), 0.75, 5.6, false),
						new TargetResolver.EntityCandidate(11, new Vec3(8.0, 1.6, 0.4), 0.75, 7.6, false)
				),
				99
		);

		assert result.entityId().orElseThrow() == 11 : "The graze closest to the crosshair must win: " + result;
	}

	private static void assertDistanceDecidesBetweenEquallyAimedGrazes() {
		// Mirrored across the aim line, so the angular key is identical and distance has to settle it.
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(11, new Vec3(6.0, 1.6, -0.5), 0.75, 5.8, false),
						new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.5), 0.75, 5.4, false)
				),
				99
		);

		assert result.entityId().orElseThrow() == 7 : "Equally aimed grazes must fall back to distance: " + result;
	}

	private static void assertTiesBreakOnEntityIdRegardlessOfOrder() {
		TargetResolver.EntityCandidate low = new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.0), 0.75, 5.5);
		TargetResolver.EntityCandidate high = new TargetResolver.EntityCandidate(11, new Vec3(6.0, 1.6, 0.0), 0.75, 5.5);

		// Real entity iteration order is not stable as entities move between sections, so a perfectly
		// tied pair must not let it decide the target.
		for (List<TargetResolver.EntityCandidate> order : List.of(List.of(low, high), List.of(high, low))) {
			TargetResolver.Result result = TargetResolver.resolveForTests(
					new Vec3(0.0, 1.6, 0.0),
					new Vec3(1.0, 0.0, 0.0),
					32.0,
					Optional.empty(),
					order,
					99
			);
			assert result.entityId().orElseThrow() == 7 : "A tie must resolve to the lowest entity id: " + result;
		}
	}

	private static void assertEntityHitBeatsBlockWhenCloser() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.of(new TargetResolver.BlockCandidate(new Vec3(12.0, 1.6, 0.0), new Vec3(-1.0, 0.0, 0.0))),
				List.of(new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.0), 0.75, 5.2)),
				99
		);

		assert result.mode() == TargetResolver.Mode.ENTITY : result;
		assert result.entityId().orElseThrow() == 7 : result;
	}

	private static void assertBlockWinsWhenEntityIsBehindBlock() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.of(new TargetResolver.BlockCandidate(new Vec3(5.0, 1.6, 0.0), new Vec3(-1.0, 0.0, 0.0))),
				List.of(new TargetResolver.EntityCandidate(7, new Vec3(8.0, 1.6, 0.0), 0.75, 7.5)),
				99
		);

		assert result.mode() == TargetResolver.Mode.BLOCK : result;
		assert result.entityId().isEmpty() : result;
	}

	private static void assertBlockFallbackWhenNoEntityHit() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.of(new TargetResolver.BlockCandidate(new Vec3(10.0, 1.6, 0.0), new Vec3(-1.0, 0.0, 0.0))),
				List.of(),
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
				List.of(new TargetResolver.EntityCandidate(4, new Vec3(4.0, 1.6, 0.0), 0.75, 3.5)),
				4
		);

		assert result.mode() == TargetResolver.Mode.MISS : result;
	}

	private static boolean close(Vec3 left, Vec3 right, double epsilon) {
		return left.distanceToSqr(right) <= epsilon * epsilon;
	}
}
