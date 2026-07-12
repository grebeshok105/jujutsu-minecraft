package jujutsu.mod.character.nobara.projectjjk;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ResonantMomentumTest {
	private ResonantMomentumTest() {}

	public static void main(String[] args) {
		assert ResonantMomentum.scaleTicks(10, 1.0f) == 10;
		assert ResonantMomentum.scaleTicks(10, 1.15f) == 9 : "15% faster rounds 10 / 1.15 up";
		assert ResonantMomentum.scaleTicks(4, 1.15f) == 3 : "four-tick launch cadence must become observably faster";
		integrationContract();
	}

	private static void integrationContract() {
		String root = System.getProperty("user.dir").replace('\\', '/');
		String straw = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java");
		String nails = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java");
		String hammer = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java");
		String ritual = read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java");
		String networking = read(root + "/src/main/java/jujutsu/mod/network/JujutsuNetworking.java");
		String effects = read(root + "/src/main/java/jujutsu/mod/registry/JujutsuEffects.java");
		String initializer = read(root + "/src/main/java/jujutsu/mod/JujutsuMod.java");
		assert effects.contains("resonant_momentum") : "Momentum must be a registered native effect";
		assert effects.contains("MobEffectCategory.BENEFICIAL") : "Momentum must appear as a beneficial effect";
		assert initializer.contains("JujutsuEffects.register()") : "custom effects must be registered during mod initialization";
		assert straw.contains("ResonantMomentum.grant(caster)") : "successful doll impact must grant Momentum";
		assert straw.contains("if (!target.hurtServer") : "failed damage must not grant Momentum";
		assert read(root + "/src/main/java/jujutsu/mod/character/nobara/projectjjk/ResonantMomentum.java").contains("hasEffect(JujutsuEffects.RESONANT_MOMENTUM)") : "server multipliers must read native effect presence";
		assert nails.contains("ResonantMomentum.scaleTicks(player") : "preparation and launch cadence must be accelerated";
		assert hammer.contains("* ResonantMomentum.damageMultiplier(player)") : "hammer damage must be amplified explicitly";
		assert ritual.contains("* ResonantMomentum.damageMultiplier(caster)") : "R/B Hairpin damage must be amplified explicitly";
		assert !networking.contains("ResonantMomentumPayload") : "native effect synchronization replaces custom payload";
		assert !Files.exists(Path.of(root, "src/client/java/jujutsu/mod/client/character/ClientResonantMomentum.java"));
		assert !Files.exists(Path.of(root, "src/client/java/jujutsu/mod/client/hud/ResonantMomentumHud.java"));
		assert !Files.exists(Path.of(root, "src/main/java/jujutsu/mod/network/ResonantMomentumPayload.java"));
		assert Files.exists(Path.of(root, "src/main/resources/assets/jujutsumod/textures/mob_effect/resonant_momentum.png")) : "native effect needs its own icon";
	}

	private static String read(String path) {
		try { return Files.readString(Path.of(path)); }
		catch (Exception exception) { throw new AssertionError(path, exception); }
	}
}
