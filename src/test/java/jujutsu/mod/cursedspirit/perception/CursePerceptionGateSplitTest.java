package jujutsu.mod.cursedspirit.perception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Post-merge review (perceives vs canInteract seam): interaction gates read the
 * interaction right, sensory gates keep the perception right. Headless JUnit cannot
 * fabricate a (perceives, not-interacts) vessel — no such vessel exists yet and the
 * registry is enum-keyed — so these are source-level pins on the exact gate bodies, the
 * same technique {@code CurseRenderGateTest} uses for the render gate.
 *
 * <p>Red-proof: flip any listed gate back to {@code perceives} (or re-point
 * {@code interacts} at {@code perceives}) and the matching assertion goes red.
 */
final class CursePerceptionGateSplitTest {
	private static final Path PERCEPTION =
			Path.of("src/main/java/jujutsu/mod/cursedspirit/perception/CursePerception.java");
	private static final Path GATES = Path.of(
			"src/main/java/jujutsu/mod/cursedspirit/perception/CursedSpiritInteractionGates.java");
	private static final Path ENTITY =
			Path.of("src/main/java/jujutsu/mod/cursedspirit/CursedSpiritEntity.java");
	private static final Path PUSH_MIXIN =
			Path.of("src/main/java/jujutsu/mod/mixin/CursedSpiritPushMixin.java");
	private static final Path TRACKING_MIXIN =
			Path.of("src/main/java/jujutsu/mod/mixin/CursedSpiritTrackingMixin.java");

	private static String methodBody(Path file, String signature) throws Exception {
		String src = Files.readString(file);
		int at = src.indexOf(signature);
		assertTrue(at >= 0, () -> "missing " + signature + " in " + file);
		return src.substring(at);
	}

	/** The two pair rules exist and each pins its own right — no shared blurry helper. */
	@Test
	void pairRulesPinDistinctRights() throws Exception {
		String mayTouch = methodBody(PERCEPTION, "public static boolean mayTouch");
		assertTrue(mayTouch.contains("CursePerception::perceives"),
				"mayTouch is the sensory rule and must read perceives");
		String interacts = methodBody(PERCEPTION, "public static boolean interacts");
		assertTrue(interacts.contains("CursePerception::canInteract"),
				"interacts is the contact rule and must read canInteract");
		assertFalse(interacts.contains("perceives"), "interacts must not read perceives");
	}

	/** Damage both ways and the melee swing are interaction gates, not sensory ones. */
	@Test
	void damageAndSwingGatesReadInteractionRight() throws Exception {
		String src = Files.readString(GATES);
		assertTrue(src.contains("CursePerception.canInteract"), "gates must read canInteract");
		assertFalse(src.contains("CursePerception.perceives"),
				"no sensory check may remain in the damage/swing gates");
	}

	/** Indirect damage resolves the owner chain before judging the attacker. */
	@Test
	void damageGateWalksTheOwnerChain() throws Exception {
		String allowDamage = methodBody(GATES, "static boolean allowDamage");
		assertTrue(allowDamage.contains("CursePerception.responsibleParty"),
				"projectile/owned damage must resolve its responsible player");
		String responsible = methodBody(PERCEPTION, "public static Entity responsibleParty");
		assertTrue(responsible.contains("Projectile"), "the chain must unwrap projectiles");
		assertTrue(responsible.contains("OwnableEntity"), "the chain must unwrap owned bodies");
	}

	/** Target choke, collision and the tick-drop are contact gates. */
	@Test
	void entityContactGatesReadInteracts() throws Exception {
		String setTarget = methodBody(ENTITY, "public void setTarget");
		assertTrue(setTarget.contains("CursePerception.interacts"),
				"setTarget choke must read interacts");
		String collide = methodBody(ENTITY, "public boolean canCollideWith");
		assertTrue(collide.contains("CursePerception.interacts"),
				"collision must read interacts");
		String tick = methodBody(ENTITY, "public void tick()");
		assertTrue(tick.contains("CursePerception.interacts"),
				"the live-target drop must read interacts");
		String push = Files.readString(PUSH_MIXIN);
		assertTrue(push.contains("CursePerception.interacts"),
				"the push sink must read interacts");
	}

	/** Tracking stays sensory: it answers 'may this player know the body exists'. */
	@Test
	void trackingGateKeepsPerceives() throws Exception {
		String src = Files.readString(TRACKING_MIXIN);
		assertTrue(src.contains("CursePerception.perceives(player)"),
				"tracking is a sensory gate and must keep perceives");
	}
}
