package jujutsu.mod.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Stage B client observation canary (issue #42): the neutral logical proof that the real modded
 * client observes state that the authoritative integrated server produced, plus a screenshot
 * evidence artifact with fixed scene inputs. No gameplay, no ability, no VFX.
 *
 * <p>Fixed scene inputs (all from the fabric client-gametest world builder's consistent settings,
 * which stay ON — {@code context.worldBuilder().create()}):
 * <ul>
 *   <li>world preset {@code FLAT}, seed {@code "1"}, structure generation OFF;</li>
 *   <li>gamerules {@code doDaylightCycle}, {@code doWeatherCycle} and {@code doMobSpawning} OFF;</li>
 *   <li>day time fixed at 6000 (noon) after world creation — legal precisely because
 *       {@code doDaylightCycle} is off, so the light stays deterministic;</li>
 *   <li>weather clear and frozen (weather cycle off);</li>
 *   <li>one vanilla {@code Pig} with {@code NoAi} spawned at the heightmap surface next to the
 *       shared spawn point — it cannot move, so the scene is deterministic;</li>
 *   <li>screenshot {@code observation_canary} at a fixed 854x480 resolution — the artifact size
 *       is independent of the window, and the screenshot is evidence only: no pixel/golden
 *       comparison is made anywhere in this canary;</li>
 *   <li>the local player camera is aimed at the pig's mid-body right before the capture, so the
 *       evidence frame always shows the observed entity under the crosshair;</li>
 *   <li>the runner prefixes screenshot file names with a counter, so the artifact on disk is
 *       {@code build/run/clientGameTest/screenshots/0000_observation_canary.png}.</li>
 * </ul>
 *
 * <p>Threading model: the test method runs on the fabric "Test thread"; client-world reads and
 * the player rotation happen on the render thread via {@code runOnClient}/{@code computeOnClient};
 * world mutation (day time, spawn, discard) happens on the integrated-server thread via
 * {@code runOnServer}/{@code computeOnServer}. Entity identity crosses threads only as UUIDs.
 *
 * <p>Cleanup layering: the try-with-resources on {@link TestSingleplayerContext} is the
 * world/session cleanup — the runner's final-state assert (no server, disconnected, TitleScreen)
 * enforces it even when this test unwinds with a failure. Inside it, a best-effort {@code finally}
 * discards the pig if it is still alive after a mid-test failure; that secondary cleanup swallows
 * its own exceptions so it can never mask the primary failure. On the success path the discard in
 * step 7 IS the entity cleanup and the finally is a no-op. This canary is vanilla-only: it touches
 * no repository-owned static state, so nothing needs resetting.
 *
 * <p>All waits are finite (never {@code NO_TIMEOUT}); no sleeps, no busy waits, no
 * {@code Minecraft.getInstance()} from the test thread — only the official fabric primitives
 * ({@code waitForChunksRender}, {@code waitForEntityVisible}/{@code waitForEntityGone} via
 * {@link ClientGameTestFixtures}, {@code waitTicks}, {@code runOnClient}/{@code runOnServer}).
 *
 * <p>Deliberately out of scope: #21 gameplay scenarios, #43 MCP bridge, pixel/SSIM/golden gating.
 */
public final class ClientObservationCanaryTest implements FabricClientGameTest {

	/** Fixture name used in every diagnostic and timeout message. */
	private static final String FIXTURE = "clientObservationCanary";
	/** Finite timeouts for entity sync waits (10 s at 20 tps). */
	private static final int ENTITY_SYNC_TIMEOUT_TICKS = 200;
	/** Noon — fixed because the daylight cycle gamerule is off. */
	private static final long NOON_DAY_TIME = 6000L;
	/** Position tolerance for the client-vs-server spawn comparison (interpolation slack). */
	private static final double POSITION_TOLERANCE = 1.0D;
	/** Fixed screenshot size: deterministic artifact independent of the window. */
	private static final int SCREENSHOT_WIDTH = 854;
	private static final int SCREENSHOT_HEIGHT = 480;

	/**
	 * Fixture-local handle carrying the only cross-thread identity: the spawned pig's UUID and its
	 * exact server-side spawn position. Never a network entity id.
	 */
	private record SpawnHandle(UUID uuid, Vec3 spawnPosition) {
	}

	// No explicit constructor: the fabric loader instantiates entrypoints reflectively, so the
	// implicit public no-arg constructor is required.

	@Override
	public void runTest(ClientGameTestContext context) {
		// Step 1: create the world with the fabric consistent settings (the fixed scene inputs
		// documented in the class javadoc). The try-with-resources is the world/session cleanup.
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			// Step 2: render readiness before any observation.
			singleplayer.getClientWorld().waitForChunksRender();

			SpawnHandle spawned = null;
			try {
				// Step 3: fix the scene on the server thread — noon, permanent because the
				// daylight-cycle gamerule is off; weather is already clear and frozen.
				singleplayer.getServer().runOnServer(server -> {
					ServerLevel serverLevel = server.overworld();
					serverLevel.setDayTime(NOON_DAY_TIME);
				});

				// Step 4: spawn the neutral entity on the server thread, at the heightmap surface
				// next to the shared spawn point. Official 1.21.8 spawn API (javap-verified:
				// EntityType#spawn(ServerLevel, BlockPos, EntitySpawnReason)).
				spawned = singleplayer.getServer().computeOnServer(server -> {
					ServerLevel serverLevel = server.overworld();
					BlockPos spawnBase = serverLevel.getSharedSpawnPos().offset(3, 0, 3);
					// 1.21.8: Level#getHeightmapPos was removed; Level#getHeight(Types, x, z) is
					// the official replacement (javap-verified).
					BlockPos surface = new BlockPos(spawnBase.getX(),
							serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, spawnBase.getX(), spawnBase.getZ()),
							spawnBase.getZ());
					Pig pig = EntityType.PIG.spawn(serverLevel, surface, EntitySpawnReason.TRIGGERED);
					ClientGameTestFixtures.assertWithDiagnostic(pig != null && pig.isAlive(), FIXTURE, "server",
							serverLevel.getGameTime(), "pig spawned and alive at heightmap surface",
							"non-null alive Pig at " + surface, pig);
					pig.setNoAi(true); // deterministic position: the pig cannot move.
					return new SpawnHandle(pig.getUUID(), pig.position());
				});
				// The lambdas below capture this final reference (spawned itself is reassigned
				// above, so it is not effectively final).
				final SpawnHandle handle = spawned;

				// Step 5: client observes the appearance — the UUID-based wait proves the client
				// world received the spawn; the asserts then run on the render thread.
				Entity observed = ClientGameTestFixtures.waitForEntityVisible(context, handle.uuid(),
						ENTITY_SYNC_TIMEOUT_TICKS, FIXTURE);
				// clientTick must run on the Test thread: context methods reject other threads.
				final long observationTick = ClientGameTestFixtures.clientTick(context);
				context.computeOnClient(client -> {
					ClientGameTestFixtures.assertWithDiagnostic(observed.getType() == EntityType.PIG, FIXTURE, "client",
							observationTick, "client-observed entity type", "minecraft:pig",
							BuiltInRegistries.ENTITY_TYPE.getKey(observed.getType()));
					ClientGameTestFixtures.assertWithDiagnostic(observed.getUUID().equals(handle.uuid()), FIXTURE,
							"client", observationTick, "client-observed entity uuid", handle.uuid(), observed.getUUID());
					ClientGameTestFixtures.assertWithDiagnostic(
							observed.position().distanceTo(handle.spawnPosition()) <= POSITION_TOLERANCE,
							FIXTURE, "client", observationTick, "client-observed position",
							"within " + POSITION_TOLERANCE + " of " + handle.spawnPosition(), observed.position());
					return observed.position();
				});

				// Step 6: screenshot evidence, AFTER the observation asserts so the scene is
				// proven. Aim the local player at the pig's mid-body (client-legal: local rotation
				// only; feet position + half the pig's ~0.9-block height keeps the crosshair on the
				// body at any distance).
				final long lookAtTick = ClientGameTestFixtures.clientTick(context);
				context.runOnClient(client -> {
					ClientGameTestFixtures.assertWithDiagnostic(client.player != null, FIXTURE, "client", lookAtTick,
							"local player present for lookAt", "non-null LocalPlayer", client.player);
					client.player.lookAt(EntityAnchorArgument.Anchor.EYES, handle.spawnPosition().add(0.0D, 0.45D, 0.0D));
				});
				context.waitTicks(2); // let the rotation apply before the screenshot.
				Path screenshot = context.takeScreenshot(
						TestScreenshotOptions.of("observation_canary").withSize(SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT));
				ClientGameTestFixtures.assertWithDiagnostic(Files.exists(screenshot), FIXTURE, "client",
						ClientGameTestFixtures.clientTick(context), "screenshot file exists", "PNG at " + screenshot,
						Files.exists(screenshot) ? "exists" : "missing");
				ClientGameTestFixtures.assertWithDiagnostic(screenshotSize(screenshot) > 0L, FIXTURE, "client",
						ClientGameTestFixtures.clientTick(context), "screenshot file non-empty", "size > 0",
						screenshotSize(screenshot));

				// Step 7: removal sync — discard on the server thread, then wait for the client to
				// observe the removal (UUID-based, finite timeout).
				singleplayer.getServer().runOnServer(server -> {
					ServerLevel serverLevel = server.overworld();
					Entity serverEntity = serverLevel.getEntity(handle.uuid());
					ClientGameTestFixtures.assertWithDiagnostic(serverEntity != null, FIXTURE, "server",
							serverLevel.getGameTime(), "pig present on server before discard",
							"non-null Entity for uuid " + handle.uuid(), serverEntity);
					serverEntity.discard();
				});
				ClientGameTestFixtures.waitForEntityGone(context, handle.uuid(), ENTITY_SYNC_TIMEOUT_TICKS, FIXTURE);
			} finally {
				// Best-effort entity cleanup: if the test failed before step 7, remove the pig
				// anyway; swallow secondary exceptions so the primary failure is never masked.
				bestEffortDiscard(singleplayer, spawned);
			}
		}
	}

	/**
	 * Best-effort server-side discard of the canary pig if it is still alive, used only on failure
	 * paths (the success path already discarded it in step 7). Exceptions here are swallowed by
	 * design: this cleanup must never mask the primary failure.
	 */
	private static void bestEffortDiscard(TestSingleplayerContext singleplayer, SpawnHandle spawned) {
		if (spawned == null) {
			return; // never spawned (or the spawn step itself failed): nothing to discard.
		}
		try {
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel serverLevel = server.overworld();
				Entity entity = serverLevel.getEntity(spawned.uuid());
				if (entity != null && entity.isAlive()) {
					entity.discard();
				}
			});
		} catch (Throwable ignored) {
			// Secondary cleanup: never mask the primary failure. The world itself is torn down by
			// the try-with-resources close regardless, so this is belt-and-suspenders.
		}
	}

	/**
	 * {@link Files#size} wrapped so a read failure becomes a diagnostic AssertionError instead of
	 * a checked IOException (runTest cannot declare checked exceptions).
	 */
	private static long screenshotSize(Path screenshot) {
		try {
			return Files.size(screenshot);
		} catch (IOException e) {
			throw new AssertionError("[" + FIXTURE + " @client] screenshot size unreadable: " + e, e);
		}
	}
}
