package jujutsu.mod.character.nobara.projectjjk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Assertion-main coverage for trap geometry and the corner-anchor lifecycle contract. */
public final class NailTrapTest {
	public static void main(String[] args) {
		testTriggerCircleContainment();
		testExpiryPausesWhileUnavailable();
		testSingleTriggerAndCollapseTiming();
		testDeterministicTargetSelection();
		testReplacementReturnsPreviousTrap();
		testBalanceIsCentralized();
		testTrapCornersAreAnchors();
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

	private static void testTrapCornersAreAnchors() {
		NailTrap trap = trap();
		assert trap.nailIds().size() == ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT;
		assert trap.vertices().size() == ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT;
		try {
			String runtime = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java"));
			String entity = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
			assert runtime.contains("markAsTrapNail") && runtime.contains("attachToBlock")
					: "every corner must be represented by an embedded trap nail";
			assert entity.contains("TRAP_NAIL_TAG") && entity.contains("isTrapNail")
					: "trap corner identity must survive save/load";
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private static void testServerIntegrationContract() {
		try {
			String runtime = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java"));
			String entity = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
			String ids = Files.readString(Path.of("src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java"));
			String recipes = Files.readString(Path.of("src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
			assert runtime.contains("NAIL_TRAP_DAMAGE") && runtime.contains("NobaraDamageSources.hairpin")
					: "trap impact must use the Hairpin damage event";
			assert runtime.contains("CombatStagger.GLOBAL.apply") && runtime.contains("NAIL_TRAP_INTERRUPT_TICKS");
			assert runtime.contains("attachToEntity") && entity.contains("public void attachToEntity");
			assert runtime.contains("NAIL_TRAP_PLACED") && runtime.contains("NAIL_TRAP_ARMED")
					&& runtime.contains("NAIL_TRAP_COLLAPSE") && runtime.contains("NAIL_TRAP_IMPACT");
			assert ids.contains("NAIL_TRAP_PLACED") && ids.contains("NAIL_TRAP_ARMED")
					&& ids.contains("NAIL_TRAP_COLLAPSE") && ids.contains("NAIL_TRAP_IMPACT");
			assert recipes.contains("NobaraVfxIds.NAIL_TRAP_PLACED") && recipes.contains("NobaraVfxIds.NAIL_TRAP_ARMED")
					&& recipes.contains("NobaraVfxIds.NAIL_TRAP_COLLAPSE") && recipes.contains("NobaraVfxIds.NAIL_TRAP_IMPACT");
			assert runtime.contains("onAnchorDestroyed") && runtime.contains("COLLAPSING_DISCARDS")
					: "corner loss must collapse with recursion guard";
			assert runtime.contains("NailAnchorRegistry.NailOrigin.TRAP_IMPACT")
					&& runtime.contains("HairpinRuntime.markTarget")
					: "trap impact must register its origin and owner-scoped mark";
			assert !runtime.contains("ServerPlayConnectionEvents.DISCONNECT")
					: "disconnect must not clear persistent traps";
		} catch (Exception exception) {
			throw new AssertionError("Unable to inspect nail trap integration", exception);
		}
	}

	private static void testTriggerCircleContainment() {
		NailTrap trap = trap();
		double r = ProjectJjkNobaraProfile.NAIL_TRAP_TRIGGER_RADIUS;
		assert trap.contains(0.0, 0.0, 0.0);
		assert trap.contains(0.0, 0.0, r - 0.01);
		assert trap.contains(0.0, 0.0, r);
		assert !trap.contains(0.0, 0.0, r + 0.01);
		assert trap.contains(0.0, 0.0, ProjectJjkNobaraProfile.NAIL_TRAP_RADIUS);
		assert !trap.contains(0.0, 3.1, 0.0);
	}

	private static void testExpiryPausesWhileUnavailable() {
		NailTrap trap = trap();
		for (int i = 0; i < 599; i++) trap.tick(true);
		assert !trap.expired();
		for (int i = 0; i < 200; i++) trap.tick(false);
		assert !trap.expired();
		trap.tick(true);
		assert trap.expired();
	}

	private static void testSingleTriggerAndCollapseTiming() {
		NailTrap trap = trap();
		UUID target = UUID.randomUUID();
		assert trap.trigger(target);
		assert !trap.trigger(UUID.randomUUID());
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
		assert selected.orElseThrow().equals(low);
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
