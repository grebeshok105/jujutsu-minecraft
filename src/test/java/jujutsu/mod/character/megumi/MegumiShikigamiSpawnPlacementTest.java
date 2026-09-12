package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiShikigamiSpawnPlacementTest {
	private static final Vec3 PLAYER = new Vec3(10.0, 64.0, 10.0);

	@Test
	void groundCandidatesFanOutAroundThePlayerAndStayClose() {
		List<Vec3> candidates = MegumiShikigamiSpawnPlacement.groundCandidates(PLAYER, 0.0f);
		assertEquals(4, candidates.size());
		for (Vec3 candidate : candidates) {
			assertTrue(candidate.distanceTo(PLAYER) <= 2.0, "candidate drifted too far: " + candidate);
			assertEquals(PLAYER.y, candidate.y, "ground candidates keep the player's height for the scan");
		}
		assertTrue(candidates.get(0).distanceTo(candidates.get(1)) > 2.0,
				"the two side spots sit on opposite flanks");
	}

	@Test
	void theYawTurnsTheFan() {
		List<Vec3> facingNorth = MegumiShikigamiSpawnPlacement.groundCandidates(PLAYER, 0.0f);
		List<Vec3> facingEast = MegumiShikigamiSpawnPlacement.groundCandidates(PLAYER, 90.0f);
		Vec3 northForward = facingNorth.get(2).subtract(PLAYER);
		Vec3 eastForward = facingEast.get(2).subtract(PLAYER);
		assertTrue(northForward.z > 0.0, "yaw 0 faces +Z");
		assertTrue(eastForward.x < 0.0, "yaw 90 turns the forward spot onto -X");
	}

	@Test
	void hoverCandidatesSitAboveTheHeadWithTheClosestFallbackLast() {
		List<Vec3> candidates = MegumiShikigamiSpawnPlacement.verticalCandidates(PLAYER);
		assertEquals(List.of(2.5, 3.5, 1.5),
				candidates.stream().map(candidate -> candidate.y - PLAYER.y).toList());
	}

	@Test
	void ringCandidatesAreEvenlySpacedAtTheRadius() {
		List<Vec3> candidates = MegumiShikigamiSpawnPlacement.ringCandidates(PLAYER, 4, 2.0);
		assertEquals(4, candidates.size());
		for (Vec3 candidate : candidates) {
			assertEquals(2.0, candidate.distanceTo(PLAYER), 1.0E-9);
			assertEquals(PLAYER.y, candidate.y);
		}
		assertEquals(2.0, candidates.get(0).x - PLAYER.x, 1.0E-9, "angle 0 points at +X");
		assertEquals(2.0, candidates.get(1).z - PLAYER.z, 1.0E-9);
	}

	@Test
	void theFirstSafeCandidateWins() {
		List<Vec3> candidates = MegumiShikigamiSpawnPlacement.verticalCandidates(PLAYER);
		Optional<Vec3> chosen = MegumiShikigamiSpawnPlacement.firstSafe(candidates, candidate -> candidate.y > 64.0);
		assertEquals(candidates.get(0), chosen.orElseThrow());
		assertTrue(MegumiShikigamiSpawnPlacement.firstSafe(candidates, candidate -> false).isEmpty(),
				"no safe candidate must stay empty instead of falling back to a blocked spot");
	}
}
