package jujutsu.mod.character.todo;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pins the aimed swap's commit-teleport seam to its default wiring.
 *
 * <p>The seam exists so a test can substitute a failing backend at the second placement and exercise
 * the partial-commit rollback, which no deterministic world state reaches (Entity#teleportTo only fails
 * for removed entities). These tests only assert the seam's own lifecycle — default, override, restore,
 * null rejection — which is pure static state: {@link TodoBoogieWoogieRuntime}'s class initializer
 * touches no registries (only profile constants and placement policies), and the override's warn line
 * initializes only {@code JujutsuMod}'s slf4j logger, so no Minecraft bootstrap is needed. The override
 * is process-global, so every test restores the production instance in a finally.
 *
 * <p>The default-wiring pin is identity-based ({@code assertSame} on the constant), deliberately
 * bootstrap-free: an edit that keeps the constant but changes the lambda's behaviour passes here and
 * is caught instead by gametest scenarios 1 and 4, which run the default backend against a live world.
 */
class SwapCommitTeleportTest {
	@Test
	void defaultWiringIsTheProductionTeleport() {
		assertSame(TodoBoogieWoogieRuntime.PRODUCTION_COMMIT_TELEPORT,
				TodoBoogieWoogieRuntime.commitTeleport(),
				"the unmodified seam must be the production authoritative teleport");
	}

	@Test
	void overrideReplacesAndRestoreReturnsTheProductionInstance() {
		SwapCommitTeleport failing = (body, level, destination, yaw, pitch) -> false;
		TodoBoogieWoogieRuntime.overrideCommitTeleport(failing);
		try {
			assertSame(failing, TodoBoogieWoogieRuntime.commitTeleport(),
					"the override must be visible through the getter");
			assertNotSame(TodoBoogieWoogieRuntime.PRODUCTION_COMMIT_TELEPORT,
					TodoBoogieWoogieRuntime.commitTeleport(),
					"the override must actually replace the production instance");
		} finally {
			TodoBoogieWoogieRuntime.restoreProductionCommitTeleport();
		}
		assertSame(TodoBoogieWoogieRuntime.PRODUCTION_COMMIT_TELEPORT,
				TodoBoogieWoogieRuntime.commitTeleport(),
				"the restore must put the production instance back");
	}

	@Test
	void overrideRejectsNull() {
		assertThrows(NullPointerException.class,
				() -> TodoBoogieWoogieRuntime.overrideCommitTeleport(null),
				"a null backend must be refused rather than silently disable the commit");
	}
}
