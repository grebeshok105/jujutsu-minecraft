package jujutsu.mod.character.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Injectable commit backend lifecycle and pinned SwapKind mapping. */
class SwapCommitTeleportTest {
	@Test
	void defaultWiringIsTheProductionTeleport() {
		assertSame(SwapCommit.PRODUCTION_COMMIT_TELEPORT, SwapCommit.commitTeleport());
	}

	@Test
	void overrideReplacesAndRestoreReturnsProduction() {
		SwapCommitTeleport failing = (body, level, destination, yaw, pitch) -> false;
		SwapCommit.overrideCommitTeleport(failing);
		try {
			assertSame(failing, SwapCommit.commitTeleport());
			assertNotSame(SwapCommit.PRODUCTION_COMMIT_TELEPORT, SwapCommit.commitTeleport());
		} finally {
			SwapCommit.restoreProductionCommitTeleport();
		}
		assertSame(SwapCommit.PRODUCTION_COMMIT_TELEPORT, SwapCommit.commitTeleport());
	}

	@Test
	void overrideRejectsNull() {
		assertThrows(NullPointerException.class, () -> SwapCommit.overrideCommitTeleport(null));
	}

	@Test
	void everySwapKindHasThePinnedSlotAndCooldown() {
		assertEquals(60, SwapKind.AIMED.baseCooldownTicks());
		assertEquals(100, SwapKind.PAIR.baseCooldownTicks());
		assertEquals(160, SwapKind.TRIPLE.baseCooldownTicks());
		assertEquals(60, SwapKind.STONE_SELF.baseCooldownTicks());
		assertEquals(100, SwapKind.STONE_TARGET.baseCooldownTicks());
	}

	@Test
	void runtimesDoNotOwnASecondTeleportBackend() throws Exception {
		for (String runtime : new String[] {"TodoBoogieWoogieRuntime.java", "TodoPairSwapRuntime.java", "TodoStoneRuntime.java"}) {
			String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo", runtime));
			assertEquals(-1, source.indexOf("teleportTo("), runtime + " must route body movement through SwapCommit");
		}
	}
}

// Red: before this change, the seam lived on TodoBoogieWoogieRuntime and pair/stone bypassed it;
// `./gradlew.bat test --tests '*SwapCommitTeleportTest*'` failed the unified-backend contract.
// Run: ./gradlew.bat test --tests "*SwapCommitTeleportTest*"
// Expected: SwapCommit backend and all-kind contract assertions pass.
