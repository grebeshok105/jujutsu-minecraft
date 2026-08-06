package jujutsu.mod.gametest.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Client load canary (Stage B, issue #42): proves the official client GameTest environment boots a
 * real modded client — production mod and test mod loaded, production registry content present, the
 * {@code fabric.client.gametest} system property active, and the client-thread handoff working
 * before any world exists. This is the client-side mirror of the Stage A
 * {@code ServerGameTests.serverLoadsProductionMod} canary.
 *
 * <p>Deliberately does NOT create a world, spawn entities, or take screenshots — that is the
 * observation canary's job ({@code ClientObservationCanaryTest}). Nothing here touches gameplay:
 * no abilities, no issue #21 scenarios, no pixel comparison.
 */
public final class ClientLoadCanaryTest implements FabricClientGameTest {

	private static final String FIXTURE = "clientLoadCanary";
	private static final String SIDE = "client";

	/** Snapshot of the client boot state, read once on the client thread so no field is touched from the test thread. */
	private record ClientBootState(boolean booted, boolean noLevelYet) {}

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean productionLoaded = FabricLoader.getInstance().isModLoaded(ClientGameTestFixtures.PRODUCTION_MOD_ID);
		ClientGameTestFixtures.assertWithDiagnostic(productionLoaded, FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"production mod loaded", "mod '" + ClientGameTestFixtures.PRODUCTION_MOD_ID + "' present",
				productionLoaded ? "present" : "absent");

		boolean testModLoaded = FabricLoader.getInstance().isModLoaded(ClientGameTestFixtures.TEST_MOD_ID);
		ClientGameTestFixtures.assertWithDiagnostic(testModLoaded, FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"test mod loaded", "mod '" + ClientGameTestFixtures.TEST_MOD_ID + "' present",
				testModLoaded ? "present" : "absent");

		boolean entityRegistered = BuiltInRegistries.ENTITY_TYPE.containsKey(ClientGameTestFixtures.PRODUCTION_ENTITY_ID);
		ClientGameTestFixtures.assertWithDiagnostic(entityRegistered, FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"production entity registered", "id '" + ClientGameTestFixtures.PRODUCTION_ENTITY_ID + "' in BuiltInRegistries.ENTITY_TYPE",
				entityRegistered ? "registered" : "missing");

		String gametestProperty = System.getProperty("fabric.client.gametest");
		ClientGameTestFixtures.assertWithDiagnostic(gametestProperty != null, FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"official client gametest environment", "set", String.valueOf(gametestProperty));

		ClientBootState boot = context.computeOnClient((Minecraft client) -> new ClientBootState(client != null, client != null && client.level == null));
		ClientGameTestFixtures.assertWithDiagnostic(boot.booted(), FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"client boots", "Minecraft instance handed to the client thread", boot.booted() ? "client != null" : "client == null");
		ClientGameTestFixtures.assertWithDiagnostic(boot.noLevelYet(), FIXTURE, SIDE, ClientGameTestFixtures.PRE_WORLD_TICK,
				"no world before worldBuilder", "no world before worldBuilder", boot.noLevelYet() ? "level == null" : "level != null");
	}
}
