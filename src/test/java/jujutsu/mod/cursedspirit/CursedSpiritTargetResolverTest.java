package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import jujutsu.mod.combat.TargetResolver;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * R16 comparator half (Step 9): a cursed-spirit candidate ranks through the existing
 * {@link TargetResolver#resolveForTests} seam like any mob — a centred far spirit beats a near
 * edge-graze, and the nearer of two pierced spirits wins.
 */
final class CursedSpiritTargetResolverTest {
	@Test
	void centredFarSpiritBeatsNearEdgeGraze() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(4.0, 1.6, 1.1), 0.75, 4.0, false),
						new TargetResolver.EntityCandidate(11, new Vec3(9.0, 1.6, 0.0), 0.75, 8.6, true)),
				99);
		assertEquals(TargetResolver.Mode.ENTITY, result.mode());
		assertEquals(11, result.entityId().orElseThrow());
	}

	@Test
	void nearerPiercedSpiritBeatsFartherPiercedSpirit() {
		TargetResolver.Result result = TargetResolver.resolveForTests(
				new Vec3(0.0, 1.6, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				32.0,
				Optional.empty(),
				List.of(
						new TargetResolver.EntityCandidate(7, new Vec3(6.0, 1.6, 0.35), 0.75, 5.5),
						new TargetResolver.EntityCandidate(11, new Vec3(8.0, 1.6, 0.05), 0.75, 7.5)),
				99);
		assertTrue(result.entityId().orElseThrow() == 7, "closer pierced spirit wins: " + result);
	}
}
