package jujutsu.mod.character.nobara.projectjjk;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ResonantMomentumTest {
	private ResonantMomentumTest() {}

	public static void main(String[] args) {
		assertScaleOracles();
		integrationContract();
	}

	private static void assertScaleOracles() {
		assert ResonantMomentum.scaleTicks(10, 1.0f) == 10;
		assert ResonantMomentum.scaleTicks(10, 1.10f) == 9 : "10 ticks at 1.10x should round to 9";
		assert ResonantMomentum.scaleTicks(100, 1.10f) == 91 : "100 ticks at 1.10x should round to 91";
		assert ResonantMomentum.scaleTicks(4, 1.10f) == 4 : "short actions stay bounded at one-tick granularity";
		assert ResonantMomentum.scaleTicks(10, 0.5f) == 10 : "scaling must never slow an action";
		assert ResonantMomentum.scaleTicks(0, 1.10f) == 0;
	}

	private static void integrationContract() {
		String root = System.getProperty("user.dir").replace('\\', '/');
		String momentum = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ResonantMomentum.java");
		String straw = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java");
		String nails = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java");
		String hammer = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java");
		String ritual = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/HairpinRuntime.java");
		String networking = read(root + "/src/main/java/jujutsu/mod/network/JujutsuNetworking.java");
		String effects = read(root + "/src/main/java/jujutsu/mod/registry/JujutsuEffects.java");
		String initializer = read(root + "/src/main/java/jujutsu/mod/JujutsuMod.java");
		assert momentum.contains("grantExecutionMoment") : "Momentum must expose the bounded execution grant";
		assert momentum.contains("isActive(LivingEntity") : "Momentum must expose active-state inspection";
		assert momentum.contains("MOMENTUM_DAMAGE_MULT") : "the multiplier must have one named constant";
		assert straw.contains("ResonantMomentum.grantExecutionMoment") : "successful resonance must grant Momentum";
		assert nails.contains("ResonantMomentum.accelerateElapsedTicks(player") : "nail preparation must be accelerated";
		assert hammer.contains("* ResonantMomentum.damageMultiplier(player)") : "hammer damage must be amplified explicitly";
		assert ritual.contains("* ResonantMomentum.damageMultiplier(caster)") : "Hairpin damage must be amplified explicitly";
		assert !straw.contains("tickRateManager") : "resonance must not mutate the server tick rate";
		assert !networking.contains("ResonantMomentumPayload") : "native effect synchronization replaces custom payload";
		assert effects.contains("resonant_momentum") : "Momentum must be a registered native effect";
		assert effects.contains("MobEffectCategory.BENEFICIAL") : "Momentum must be beneficial";
		assert initializer.contains("JujutsuEffects.register()") : "custom effects must be registered";
	}

	private static String read(String path) {
		try { return Files.readString(Path.of(path)); }
		catch (Exception exception) { throw new AssertionError(path, exception); }
	}
}
