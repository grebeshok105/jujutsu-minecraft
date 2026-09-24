package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import jujutsu.mod.character.megumi.MegumiDeerPolicy.WoundedFacts;
import jujutsu.mod.character.megumi.MegumiDeerPolicy.WoundedKind;
import jujutsu.mod.registry.JujutsuEffects;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MegumiDeerPolicyTest {

	/** A bare MobEffect stand-in for "any vanilla effect" — the registry does not matter here. */
	private static final class TestEffect extends MobEffect {
		private TestEffect(MobEffectCategory category) {
			super(category, 0);
		}
	}

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static WoundedFacts facts(WoundedKind kind, double missingFraction) {
		return facts(kind, missingFraction, 4.0, false);
	}

	private static WoundedFacts facts(WoundedKind kind, double missingFraction, double distanceSqr) {
		return facts(kind, missingFraction, distanceSqr, false);
	}

	private static WoundedFacts facts(WoundedKind kind, double missingFraction, double distanceSqr,
			boolean carriesCleansable) {
		return new WoundedFacts(UUID.randomUUID(), kind, missingFraction, distanceSqr, carriesCleansable);
	}

	// — Heal priority ordering ———————————————————————————————————————————

	@Test
	void healPriorityOrdersOwnerThenShikigamiThenAllyThenSelf() {
		WoundedFacts owner = facts(WoundedKind.OWNER, 0.2);
		WoundedKind[] lanes = {WoundedKind.SELF, WoundedKind.ALLY, WoundedKind.OWN_SHIKIGAMI};
		for (WoundedKind lane : lanes) {
			WoundedFacts other = facts(lane, 1.0); // even a dying lane loses to a grazed owner
			assertEquals(owner.uuid(), MegumiDeerPolicy.healPriority(List.of(owner, other)).uuid(),
					"a wounded owner outranks " + lane + " regardless of wound depth");
		}
		WoundedFacts body = facts(WoundedKind.OWN_SHIKIGAMI, 0.1);
		WoundedFacts ally = facts(WoundedKind.ALLY, 1.0);
		WoundedFacts self = facts(WoundedKind.SELF, 1.0);
		assertEquals(body.uuid(),
				MegumiDeerPolicy.healPriority(List.of(self, ally, body)).uuid(),
				"a wounded own-shikigami outranks allies and the deer itself");
		assertEquals(ally.uuid(),
				MegumiDeerPolicy.healPriority(List.of(self, ally)).uuid(),
				"a wounded ally outranks the deer's own wounds");
		assertEquals(self.uuid(),
				MegumiDeerPolicy.healPriority(List.of(self)).uuid(),
				"the deer still heals itself when nobody else needs it");
	}

	@Test
	void healPriorityBreaksTiesByMissingFractionThenDistance() {
		WoundedFacts grazed = facts(WoundedKind.OWN_SHIKIGAMI, 0.2);
		WoundedFacts mauled = facts(WoundedKind.OWN_SHIKIGAMI, 0.9);
		assertEquals(mauled.uuid(),
				MegumiDeerPolicy.healPriority(List.of(grazed, mauled)).uuid(),
				"inside a lane the deeper wound wins");

		WoundedFacts near = facts(WoundedKind.OWN_SHIKIGAMI, 0.5, 4.0);
		WoundedFacts far = facts(WoundedKind.OWN_SHIKIGAMI, 0.5, 100.0);
		assertEquals(near.uuid(),
				MegumiDeerPolicy.healPriority(List.of(far, near)).uuid(),
				"equal wounds resolve to the nearer body (a deterministic pick, not a scan artifact)");
	}

	@Test
	void healPriorityRefusesTheUnwoundedAndTheEmptyField() {
		assertNull(MegumiDeerPolicy.healPriority(List.of()),
				"an empty scan yields no pulse");
		assertNull(MegumiDeerPolicy.healPriority(List.of(
				facts(WoundedKind.OWNER, 0.0), facts(WoundedKind.OWN_SHIKIGAMI, 0.0),
				facts(WoundedKind.ALLY, 0.0), facts(WoundedKind.SELF, 0.0))),
				"a field at full health yields no pulse — healing is never spent on the healthy");
	}

	// — Pulse scaling ————————————————————————————————————————————————————

	@Test
	void healPulseScalesWithMissingFractionBetweenThePinnedBounds() {
		assertEquals(2.0f, MegumiDeerPolicy.healPulse(0.0, WoundedKind.OWNER),
				"a scratch still pays the floor heal");
		assertEquals(6.0f, MegumiDeerPolicy.healPulse(1.0, WoundedKind.OWNER),
				"a dying friendly gets the ceiling heal");
		assertEquals(4.0f, MegumiDeerPolicy.healPulse(0.5, WoundedKind.OWN_SHIKIGAMI),
				"a half-wounded body sits mid-band");
		assertEquals(6.0f, MegumiDeerPolicy.healPulse(1.5, WoundedKind.OWNER),
				"a fraction above 1.0 clamps at the ceiling instead of overshooting");
	}

	@Test
	void healPulseHalvesItselfOnSelf() {
		assertEquals(1.0f, MegumiDeerPolicy.healPulse(0.0, WoundedKind.SELF),
				"self-heal floor is half the friendly floor");
		assertEquals(3.0f, MegumiDeerPolicy.healPulse(1.0, WoundedKind.SELF),
				"self-heal ceiling is half the friendly ceiling — the support spends more on others");
	}

	// — Cadence ——————————————————————————————————————————————————————————

	@Test
	void pulseTimingKeepsScanActionAndCooldownDistinct() {
		assertFalse(MegumiDeerPolicy.healDue(39, 40), "the scan waits for its deadline");
		assertTrue(MegumiDeerPolicy.healDue(40, 40), "the scan fires at its deadline");
		assertTrue(MegumiDeerPolicy.healDue(41, 40), "a late tick still counts (poll-until cadence)");
		assertFalse(MegumiDeerPolicy.cleanseDue(59, 60), "the cleanse runs on its own slower clock");
		assertTrue(MegumiDeerPolicy.cleanseDue(60, 60));
		assertEquals(40, MegumiShikigamiProfile.DEER_HEAL_SCAN_TICKS, "heal scan cadence pin");
		assertEquals(10, MegumiShikigamiProfile.DEER_HEAL_ACTION_TICKS,
				"the pulse is a ten-tick channel, not an instant");
		assertEquals(60, MegumiShikigamiProfile.DEER_CLEANSE_SCAN_TICKS, "cleanse scan cadence pin");
		assertEquals(40, MegumiShikigamiProfile.DEER_ANTLER_COOLDOWN_TICKS, "antler cooldown pin");
		assertTrue(MegumiDeerPolicy.shoveReady(40, 40) && !MegumiDeerPolicy.shoveReady(39, 40),
				"the antler cooldown frees exactly at its deadline");
	}

	// — Cleanse allowlist ————————————————————————————————————————————————

	@Test
	void cleansableStripsRealHostileDebuffsOnly() {
		assertTrue(MegumiDeerPolicy.cleansable(new TestEffect(MobEffectCategory.HARMFUL)),
				"a vanilla hostile debuff is fair game");
		assertFalse(MegumiDeerPolicy.cleansable(new TestEffect(MobEffectCategory.BENEFICIAL)),
				"a vanilla buff is never touched");
		assertFalse(MegumiDeerPolicy.cleansable(new TestEffect(MobEffectCategory.NEUTRAL)),
				"a vanilla neutral effect is never touched");
	}

	@Test
	void cleansableClassifiesEveryModEffectByTheTable() {
		assertTrue(MegumiDeerPolicy.cleansable(JujutsuEffects.MEGUMI_SOAKED.value()),
				"SOAKED is a hostile debuff enemies paint on victims — stripping it is the cleanse's job");
		assertTrue(MegumiDeerPolicy.cleansable(JujutsuEffects.CURSED_FEAR.value()),
				"CURSED_FEAR is a hostile debuff on the victim — cleansable");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.MEGUMI_SHADOW_GRIP.value()),
				"allowlist reclassification: SHADOW_GRIP is never cleansed — the trap re-applies it"
						+ " every tick, so stripping it only lies to the client for one frame");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.GRIPPED.value()),
				"allowlist reclassification: GRIPPED is the hold authority channel — stripping it"
						+ " desyncs the held victim's client");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.MEGUMI_NUE_WINGS.value()),
				"allowlist reclassification: NUE_WINGS is a beneficial owner gate, not a debuff");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.MEGUMI_TOAD_TONGUE.value()),
				"allowlist reclassification: TOAD_TONGUE is the toad's grab channel");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.RESONANT_MOMENTUM.value()),
				"allowlist reclassification: RESONANT_MOMENTUM is a Todo internal");
		assertFalse(MegumiDeerPolicy.cleansable(JujutsuEffects.TODO_SWAP_MOMENTUM.value()),
				"allowlist reclassification: TODO_SWAP_MOMENTUM is a Todo internal");
	}

	@Test
	void cleansePriorityPicksTheMostImportantCarrier() {
		WoundedFacts self = facts(WoundedKind.SELF, 0.0, 0.0, true);
		WoundedFacts owner = facts(WoundedKind.OWNER, 0.0, 25.0, true);
		assertEquals(owner.uuid(),
				MegumiDeerPolicy.cleansePriority(List.of(self, owner)).uuid(),
				"a gripped owner outranks a gripped deer");
		assertNull(MegumiDeerPolicy.cleansePriority(List.of(facts(WoundedKind.OWNER, 0.9))),
				"a wounded friend carrying nothing cleansable is the heal's job, not the cleanse's");
	}

	// — Interpose ————————————————————————————————————————————————————————

	@Test
	void interposePointSitsOnTheOwnerThreatLineAtRadius() {
		Vec3 owner = new Vec3(10.0, 64.0, 10.0);
		Vec3 threat = new Vec3(10.0, 70.0, 18.0);
		Vec3 point = MegumiDeerPolicy.interposePoint(owner, threat, 3.0);
		assertEquals(10.0, point.x, 1.0E-6, "the anchor stays on the owner→threat line");
		assertEquals(13.0, point.z, 1.0E-6, "the anchor is the radius out from the owner");
		assertEquals(64.0, point.y, 1.0E-6, "the anchor stays on the owner's ground plane");
	}

	@Test
	void interposePointNeverOvershootsAPointBlankThreat() {
		Vec3 owner = new Vec3(0.0, 64.0, 0.0);
		Vec3 threat = new Vec3(0.0, 64.0, 1.5);
		Vec3 point = MegumiDeerPolicy.interposePoint(owner, threat, 3.0);
		assertEquals(1.5, point.distanceTo(owner), 1.0E-6,
				"a threat inside the radius pulls the anchor to the threat, not past it");
		assertEquals(0.0, MegumiDeerPolicy.interposePoint(owner, owner, 3.0).distanceTo(owner),
				"a threat stacked on the owner degenerates to the owner position, not NaN");
	}

	@Test
	void antlerKnockbackPointsAwayFromTheDeer() {
		Vec3 deer = new Vec3(0.0, 64.0, 0.0);
		Vec3 attacker = new Vec3(0.0, 64.0, 2.0);
		Vec3 args = MegumiDeerPolicy.antlerKnockback(deer, attacker);
		// LivingEntity.knockback pushes OPPOSITE its args: feeding (deer-attacker) lands +Z on it.
		assertTrue(args.z < 0.0, "the fed vector points deer-ward so the attacker flies -args");
		assertEquals(0.0, args.y, "the shove is horizontal — the vertical lift is vanilla's");
	}
}
