package jujutsu.mod.combat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class BlackFlashWindowTest {
	private BlackFlashWindowTest() {}

	public static void main(String[] args) {
		UUID target = UUID.randomUUID();
		BlackFlashWindow window = new BlackFlashWindow(target, BlackFlashImpact.HAMMER, 100L, 102L, 8.0f);
		assert !window.accepts(99L);
		assert window.accepts(100L);
		assert window.accepts(102L);
		assert !window.accepts(103L);
		assert window.bonusDamage(1.75f) == 6.0f;
		assert window.consume(101L);
		assert !window.consume(101L) : "one impact can produce at most one Black Flash";
		assertRuntimeOnlyOpensWindowsForAcceptedHits();
		CombatStagger stagger = new CombatStagger();
		stagger.apply(target, 50L, 8);
		assert stagger.isStaggered(target, 57L);
		assert !stagger.isStaggered(target, 58L);
		System.out.println("BlackFlashWindowTest passed");
	}

	private static void assertRuntimeOnlyOpensWindowsForAcceptedHits() {
		try {
			String hammer = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java"));
			String runtime = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
			String nail = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
			String profile = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java"));
			assert hammer.contains("LivingEntity firstSuccessful = null") : "horizontal Black Flash must bind to first accepted hit";
			assert hammer.contains("tryProcLivingBlackFlash") : "Black Flash must roll on accepted living impacts";
			assert hammer.contains("BLACK_FLASH_CHANCE") || profile.contains("BLACK_FLASH_CHANCE = 0.10f")
					: "Black Flash chance must be a flat 10% roll";
			assert !hammer.contains("WINDOWS.put") : "second-click Black Flash timing windows must be removed";
			assert runtime.indexOf("isSuccessfulOrdinaryHit(damageAccepted") < runtime.indexOf("openNailEmbedWindow(owner")
					: "embed Black Flash requires an accepted ordinary hit";
			assert nail.contains("flightMultiplier *= multiplier")
					&& nail.contains("launchVelocity(direction).scale(flightMultiplier)")
					: "prepared-nail Black Flash amplify must survive launch delay";
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}
}
