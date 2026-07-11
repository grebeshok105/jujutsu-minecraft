package jujutsu.mod.character.nobara.projectjjk;

import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NailTrapTest {
	public static void main(String[] args) {
		testTriangleContainmentIncludesEdges();
		testExpiryPausesWhileUnavailable();
		testSingleTriggerAndCollapseTiming();
		testDeterministicTargetSelection();
		testReplacementReturnsPreviousTrap();
		testBalanceIsCentralized();
		testServerIntegrationContract();
	}

	private static void testBalanceIsCentralized() {
		try {
			assert ProjectJjkNobaraProfile.class.getField("NAIL_TRAP_NAIL_COUNT").getInt(null) == 3;
			assert ProjectJjkNobaraProfile.class.getField("NAIL_TRAP_PRISM_HEIGHT").getDouble(null) == 3.0;
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("trap balance values must live in ProjectJjkNobaraProfile", exception);
		}
	}

	private static void testServerIntegrationContract() {
		try {
			String runtime = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java"));
			String actions = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"));
			String nail = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
			String ids = Files.readString(Path.of("src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java"));
			String ritual = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
			String recipes = Files.readString(Path.of("src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
			assert actions.contains("case NAIL_TRAP -> NailTrapRuntime.tryPlace(player)") : "Shift+B must route to the trap runtime";
			assert runtime.contains("NAIL_TRAP_DAMAGE") && runtime.contains("NobaraDamageSources.hairpin") : "trap impact must use one Hairpin damage event";
			assert runtime.contains("CombatStagger.GLOBAL.apply") && runtime.contains("NAIL_TRAP_INTERRUPT_TICKS") : "trap must use shared action interrupt";
			assert runtime.contains("attachToEntity") && nail.contains("public void attachToEntity") : "trap must embed through the canonical nail entity path";
			assert runtime.contains("NAIL_TRAP_PLACED") && runtime.contains("NAIL_TRAP_ARMED") && runtime.contains("NAIL_TRAP_COLLAPSE") && runtime.contains("NAIL_TRAP_IMPACT") : "all trap phases need VFX cues";
			assert ids.contains("NAIL_TRAP_PLACED") && ids.contains("NAIL_TRAP_ARMED") && ids.contains("NAIL_TRAP_COLLAPSE") && ids.contains("NAIL_TRAP_IMPACT");
			assert recipes.contains("NobaraVfxIds.NAIL_TRAP_PLACED") && recipes.contains("NobaraVfxIds.NAIL_TRAP_ARMED") && recipes.contains("NobaraVfxIds.NAIL_TRAP_COLLAPSE") && recipes.contains("NobaraVfxIds.NAIL_TRAP_IMPACT");
			assert nail.contains("TRAP_NAIL_TAG") && nail.contains("markAsTrapNail") : "trap nail identity must survive chunk save/load";
			assert nail.contains("!NailTrapRuntime.isTrapNail(getUUID())") : "orphaned trap nails must clean themselves after final runtime removal";
			assert ritual.contains("!nail.isTrapNail()") : "armed trap nails must not enter R/B chains";
		} catch (Exception exception) {
			throw new AssertionError("Unable to inspect nail trap integration", exception);
		}
	}

	private static void testTriangleContainmentIncludesEdges() {
		NailTrap trap = trap();
		assert trap.contains(0.0, 0.0, 0.0);
		assert trap.contains(0.0, 0.0, 5.9);
		assert trap.contains(0.0, 0.0, 6.0) : "triangle vertices must count as inside";
		assert !trap.contains(0.0, 0.0, 6.01);
		assert !trap.contains(0.0, 3.1, 0.0) : "targets above the prism must not trigger it";
	}

	private static void testExpiryPausesWhileUnavailable() {
		NailTrap trap = trap();
		for (int i = 0; i < 599; i++) trap.tick(true);
		assert !trap.expired();
		for (int i = 0; i < 200; i++) trap.tick(false);
		assert !trap.expired() : "unloaded trap chunks must pause lifetime";
		trap.tick(true);
		assert trap.expired();
	}

	private static void testSingleTriggerAndCollapseTiming() {
		NailTrap trap = trap();
		UUID target = UUID.randomUUID();
		assert trap.trigger(target);
		assert !trap.trigger(UUID.randomUUID()) : "a trap may reserve only one target";
		assert trap.collapseBeat(0) == 0;
		assert trap.collapseBeat(1) == -1;
		assert trap.collapseBeat(2) == 1;
		assert trap.collapseBeat(4) == 2;
		assert !trap.impactDue(5);
		assert trap.impactDue(6);
	}

	private static void testDeterministicTargetSelection() {
		UUID low = new UUID(0, 1);
		UUID high = new UUID(0, 2);
		var selected = NailTrap.selectTarget(List.of(
				new NailTrap.TargetCandidate(high, 4.0),
				new NailTrap.TargetCandidate(low, 4.0),
				new NailTrap.TargetCandidate(UUID.randomUUID(), 9.0)));
		assert selected.orElseThrow().equals(low) : "distance then UUID must define stable target selection";
	}

	private static void testReplacementReturnsPreviousTrap() {
		NailTrap.Registry registry = new NailTrap.Registry();
		NailTrap first = trap();
		NailTrap second = trap();
		assert registry.replace(first).isEmpty();
		assert registry.replace(second).orElseThrow() == first;
		assert registry.get(first.ownerId()).orElseThrow() == second;
	}

	private static NailTrap trap() {
		UUID owner = new UUID(3, 4);
		double radius = ProjectJjkNobaraProfile.NAIL_TRAP_RADIUS;
		return new NailTrap(owner, "minecraft:overworld", new NailTrap.Point(0, 0, 0), List.of(
				new NailTrap.Point(0, 0, radius),
				new NailTrap.Point(-radius * Math.sqrt(3.0) / 2.0, 0, -radius / 2.0),
				new NailTrap.Point(radius * Math.sqrt(3.0) / 2.0, 0, -radius / 2.0)),
				List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
				ProjectJjkNobaraProfile.NAIL_TRAP_LIFETIME_TICKS,
				ProjectJjkNobaraProfile.NAIL_TRAP_COLLAPSE_TICKS);
	}
}
