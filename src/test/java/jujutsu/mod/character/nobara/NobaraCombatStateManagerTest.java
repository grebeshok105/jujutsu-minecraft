package jujutsu.mod.character.nobara;

import java.util.UUID;

public final class NobaraCombatStateManagerTest {
	private NobaraCombatStateManagerTest() {}

	public static void main(String[] args) {
		assertPreparingConsumesAtMostFourNails();
		assertPreparedNailsPersistUntilLaunchOrClear();
		assertCooldownRejectsHammerActivation();
		assertDimensionChangeClearsPreparedNails();
		assertLaunchTransitionsThroughWindupFlightRecoveryCooldown();
		System.out.println("NobaraCombatStateManagerTest passed");
	}

	private static void assertPreparingConsumesAtMostFourNails() {
		NobaraCombatStateManager manager = new NobaraCombatStateManager();
		UUID player = UUID.randomUUID();

		NobaraCombatStateManager.PrepareResult result = manager.prepareNails(player, "minecraft:overworld", 20L, 12, false);

		assert result.preparedCount() == 4 : result;
		assert result.consumedCount() == 4 : result;
		assert manager.state(player).preparedCount(20L) == 4;
	}

	private static void assertPreparedNailsPersistUntilLaunchOrClear() {
		NobaraCombatStateManager manager = new NobaraCombatStateManager();
		UUID player = UUID.randomUUID();
		manager.prepareNails(player, "minecraft:overworld", 10L, 4, false);

		assert manager.state(player).preparedCount(10L) == 4;
		assert manager.state(player).preparedCount(111L) == 4;
		assert manager.state(player).preparedCount(1210L) == 4;

		NobaraCombatStateManager.LaunchResult launch = manager.startHairpin(player, "minecraft:overworld", 1211L);

		assert launch.accepted() : launch;
		assert launch.nailCount() == 4 : launch;
		assert manager.state(player).preparedCount(1211L) == 0;
	}

	private static void assertCooldownRejectsHammerActivation() {
		NobaraCombatStateManager manager = new NobaraCombatStateManager();
		UUID player = UUID.randomUUID();
		manager.prepareNails(player, "minecraft:overworld", 0L, 4, false);

		NobaraCombatStateManager.LaunchResult first = manager.startHairpin(player, "minecraft:overworld", 5L);
		assert first.accepted() : first;
		manager.markImpactResolved(player, 23L);

		manager.prepareNails(player, "minecraft:overworld", 30L, 4, false);
		NobaraCombatStateManager.LaunchResult blocked = manager.startHairpin(player, "minecraft:overworld", 31L);
		assert !blocked.accepted() : blocked;
		assert blocked.reason() == NobaraCombatStateManager.RejectReason.COOLDOWN : blocked;
	}

	private static void assertDimensionChangeClearsPreparedNails() {
		NobaraCombatStateManager manager = new NobaraCombatStateManager();
		UUID player = UUID.randomUUID();
		manager.prepareNails(player, "minecraft:overworld", 0L, 4, false);

		NobaraCombatStateManager.LaunchResult result = manager.startHairpin(player, "minecraft:the_nether", 5L);

		assert !result.accepted() : result;
		assert result.reason() == NobaraCombatStateManager.RejectReason.NO_PREPARED_NAILS : result;
		assert manager.state(player).preparedCount(5L) == 0;
	}

	private static void assertLaunchTransitionsThroughWindupFlightRecoveryCooldown() {
		NobaraCombatStateManager manager = new NobaraCombatStateManager();
		UUID player = UUID.randomUUID();
		manager.prepareNails(player, "minecraft:overworld", 0L, 4, false);
		NobaraCombatStateManager.LaunchResult launch = manager.startHairpin(player, "minecraft:overworld", 5L);

		assert launch.accepted() : launch;
		assert launch.windupEndsAt() == 11L : launch;
		assert launch.impactAt() == 14L : launch;
		assert manager.state(player).phaseAt(10L) == NobaraCombatStateManager.Phase.WINDUP;
		assert manager.state(player).phaseAt(12L) == NobaraCombatStateManager.Phase.FLIGHT;
		assert manager.state(player).phaseAt(14L) == NobaraCombatStateManager.Phase.RECOVERY;
		assert manager.state(player).phaseAt(28L) == NobaraCombatStateManager.Phase.COOLDOWN;
		assert manager.state(player).phaseAt(85L) == NobaraCombatStateManager.Phase.IDLE;
	}
}
