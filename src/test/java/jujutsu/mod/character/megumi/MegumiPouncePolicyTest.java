package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MegumiPouncePolicyTest {
	private static final Path RUNTIME = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java");
	private static final Path TARGET_RESOLVER = Path.of(
			"src/main/java/jujutsu/mod/combat/TargetResolver.java");
	private static final Path DOG_ENTITY = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiDivineDogEntity.java");
	private static final Path VFX_IDS = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/vfx/MegumiVfxIds.java");
	private static final Path VFX_RECIPES = Path.of(
			"src/client/java/jujutsu/mod/client/character/megumi/vfx/MegumiVfxRecipes.java");

	@Test
	void launchRequiresTheExactValidSicTargetAndEveryServerGate() {
		assertTrue(MegumiPouncePolicy.canLaunch(facts(3.0, true)));
		assertTrue(MegumiPouncePolicy.canLaunch(facts(8.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(facts(2.99, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(facts(8.01, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(facts(5.0, false)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				false, true, true, true, true, true, 5.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				true, false, true, true, true, true, 5.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				true, true, false, true, true, true, 5.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				true, true, true, false, true, true, 5.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				true, true, true, true, false, true, 5.0, true)));
		assertFalse(MegumiPouncePolicy.canLaunch(new MegumiPouncePolicy.LaunchFacts(
				true, true, true, true, true, true, 5.0, false)));
	}

	@Test
	void impactIsOneOwnerAttributedHitAndFeedbackRequiresAcceptedDamage() throws Exception {
		String runtime = Files.readString(RUNTIME);
		String impact = runtime.substring(
				runtime.indexOf("private static void resolvePounceImpact"),
				runtime.indexOf("private static LivingEntity resolveLiving"));
		assertEquals(1, occurrences(impact, ".hurtServer("),
				"A pounce must apply one combined damage instance");
		assertTrue(impact.contains("level.damageSources().playerAttack(owner)"),
				"Pounce damage must credit Megumi");
		assertTrue(impact.contains(
				"MegumiProfile.DOG_ATTACK_DAMAGE + MegumiProfile.POUNCE_BONUS_DAMAGE"),
				"The one pounce hit must combine base and bonus damage");
		int acceptedDamage = impact.indexOf("if (!target.hurtServer(");
		assertTrue(acceptedDamage >= 0);
		assertTrue(impact.indexOf("CombatStagger.GLOBAL.apply", acceptedDamage) > acceptedDamage);
		assertTrue(impact.indexOf("JujutsuSounds.PROJECTJJK_AEC_BOOM", acceptedDamage) > acceptedDamage);
		assertTrue(impact.indexOf("MegumiVfxIds.DOGS_POUNCE", acceptedDamage) > acceptedDamage);
		assertFalse(Files.readString(TARGET_RESOLVER).contains("MegumiPounce"),
				"The shared TargetResolver must remain vessel-agnostic");
	}

	@Test
	void pounceStateIsPerDogServerOnlyAndTheImpactRecipeIsRegistered() throws Exception {
		String entity = Files.readString(DOG_ENTITY);
		assertTrue(entity.contains("private UUID sicTargetUuid;"));
		assertTrue(entity.contains("private UUID pounceTargetUuid;"));
		assertTrue(entity.contains("private long nextPounceReadyGameTime;"));
		assertTrue(entity.contains("private long pounceDeadlineGameTime;"));
		assertEquals(2, occurrences(entity, "SynchedEntityData.defineId("),
				"Pounce identity and deadlines must not add synchronized entity data");

		assertTrue(Files.readString(VFX_IDS).contains("DOGS_POUNCE"));
		String recipes = Files.readString(VFX_RECIPES);
		assertTrue(recipes.contains(
				"VfxDirector.register(MegumiVfxIds.DOGS_POUNCE, MegumiVfxRecipes::pounce)"));
		String pounce = recipes.substring(
				recipes.indexOf("private static VfxInstance pounce"),
				recipes.indexOf("private static RandomSource random"));
		assertTrue(pounce.contains("context.resolveOrigin(cue)"),
				"Pounce presentation must remain anchored to the confirmed target");
		assertTrue(pounce.contains("context.ring("));
		assertTrue(pounce.contains("context.burst("));
	}

	@Test
	void deadlineAndTimeoutArePerDogAndIndependent() {
		assertTrue(MegumiPouncePolicy.deadlineReady(100L, 100L));
		assertFalse(MegumiPouncePolicy.deadlineReady(100L, 101L));
		assertFalse(MegumiPouncePolicy.timedOut(116L, 116L));
		assertTrue(MegumiPouncePolicy.timedOut(117L, 116L));
		assertTrue(MegumiPouncePolicy.deadlineReady(100L, 80L), "sibling A may be ready");
		assertFalse(MegumiPouncePolicy.deadlineReady(100L, 140L), "sibling B may still be cooling down");
	}

	@Test
	void inFlightInvalidationChoosesExactlyHowToCancel() {
		assertAction(MegumiPouncePolicy.InFlightAction.CONTINUE, inFlightFacts());
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(false, true, true, true, true, true, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(true, false, true, true, true, true, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(true, true, false, true, true, true, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(true, true, true, false, true, true, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(true, true, true, true, false, true, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(true, true, true, true, true, false, true, false));
		assertAction(MegumiPouncePolicy.InFlightAction.FINISH_POUNCE,
				new MegumiPouncePolicy.InFlightFacts(true, true, true, true, true, true, false, false));
		assertAction(MegumiPouncePolicy.InFlightAction.FINISH_POUNCE,
				new MegumiPouncePolicy.InFlightFacts(true, true, true, true, true, true, true, true));
		assertAction(MegumiPouncePolicy.InFlightAction.CLEAR_SIC,
				new MegumiPouncePolicy.InFlightFacts(false, true, true, true, true, true, true, true));
	}

	private static MegumiPouncePolicy.LaunchFacts facts(double distance, boolean lineOfSight) {
		return new MegumiPouncePolicy.LaunchFacts(
				true, true, true, true, true, lineOfSight, distance, true);
	}

	private static MegumiPouncePolicy.InFlightFacts inFlightFacts() {
		return new MegumiPouncePolicy.InFlightFacts(true, true, true, true, true, true, true, false);
	}

	private static void assertAction(
			MegumiPouncePolicy.InFlightAction expected, MegumiPouncePolicy.InFlightFacts facts) {
		assertEquals(expected, MegumiPouncePolicy.inFlightAction(facts));
	}

	private static int occurrences(String source, String needle) {
		return (source.length() - source.replace(needle, "").length()) / needle.length();
	}
}
