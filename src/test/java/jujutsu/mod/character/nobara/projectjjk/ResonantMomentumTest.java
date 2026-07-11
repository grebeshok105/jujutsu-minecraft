package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResonantMomentumTest {
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");

	private ResonantMomentumTest() {}

	public static void main(String[] args) {
		ResonantMomentum state = new ResonantMomentum(1200, 1.15f);
		assert !state.isActive(PLAYER, 99L);
		assert state.grant(PLAYER, 100L) == 1300L;
		assert state.remainingTicks(PLAYER, 100L) == 1200;
		assert state.damageMultiplier(PLAYER, 1299L) == 1.15f;
		assert state.damageMultiplier(PLAYER, 1300L) == 1.0f;
		assert state.remainingTicks(PLAYER, 1300L) == 0;

		state.grant(PLAYER, 2000L);
		assert state.grant(PLAYER, 2100L) == 3300L : "grant refreshes from now instead of stacking";
		assert state.remainingTicks(PLAYER, 2100L) == 1200;
		state.clear(PLAYER);
		assert !state.isActive(PLAYER, 2100L);

		state.grant(PLAYER, 3000L);
		state.clearAll();
		assert !state.isActive(PLAYER, 3000L);

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
		assert straw.contains("ResonantMomentum.grant(caster)") : "successful doll impact must grant and sync Momentum";
		assert straw.contains("if (!target.hurtServer") : "failed damage must not grant Momentum";
		assert nails.contains("ResonantMomentum.scaleTicks(player") : "preparation and launch cadence must be accelerated";
		assert hammer.contains("* ResonantMomentum.damageMultiplier(player)") : "hammer damage must be amplified explicitly";
		assert ritual.contains("* ResonantMomentum.damageMultiplier(caster)") : "R/B Hairpin damage must be amplified explicitly";
		assert networking.contains("ResonantMomentumPayload.TYPE") : "Momentum payload must be registered and synced";
		assert Files.exists(Path.of(root, "src/client/java/jujutsu/mod/client/character/ClientResonantMomentum.java"));
		assert Files.exists(Path.of(root, "src/client/java/jujutsu/mod/client/hud/ResonantMomentumHud.java"));
	}

	private static String read(String path) {
		try { return Files.readString(Path.of(path)); }
		catch (Exception exception) { throw new AssertionError(path, exception); }
	}
}
