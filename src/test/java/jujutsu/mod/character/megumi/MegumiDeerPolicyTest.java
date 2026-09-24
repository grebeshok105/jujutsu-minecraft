package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.registry.JujutsuEffects;

class MegumiDeerPolicyTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		JujutsuEffects.register();
	}


	@Test
	void heavyOwnerOutranksHeavyShikigamiAndLesserWounds() {
		var heavyOwner = facts("00000000-0000-0000-0000-000000000001", true, false,
				4.0f, 20.0f, 1.0);
		var heavyShikigami = facts("00000000-0000-0000-0000-000000000002", false, true,
				1.0f, 100.0f, 0.1);
		var lesserOwner = facts("00000000-0000-0000-0000-000000000003", true, false,
				16.0f, 20.0f, 0.2);

		assertTrue(MegumiDeerPolicy.priority(heavyOwner) > MegumiDeerPolicy.priority(heavyShikigami));
		assertTrue(MegumiDeerPolicy.priority(heavyShikigami) > MegumiDeerPolicy.priority(lesserOwner));
		assertEquals(heavyOwner, MegumiDeerPolicy.choose(List.of(lesserOwner, heavyShikigami, heavyOwner)).orElseThrow());
	}

	@Test
	void equallyRankedRecipientsUseWoundFractionThenDistanceThenUuid() {
		var moreWounded = facts("00000000-0000-0000-0000-000000000004", false, true,
				5.0f, 20.0f, 8.0);
		var lessWounded = facts("00000000-0000-0000-0000-000000000001", false, true,
				10.0f, 20.0f, 1.0);
		assertEquals(moreWounded,
				MegumiDeerPolicy.choose(List.of(lessWounded, moreWounded)).orElseThrow());

		var farther = facts("00000000-0000-0000-0000-000000000001", false, true,
				10.0f, 20.0f, 3.0);
		var nearer = facts("00000000-0000-0000-0000-000000000004", false, true,
				10.0f, 20.0f, 1.0);
		assertEquals(nearer, MegumiDeerPolicy.choose(List.of(farther, nearer)).orElseThrow());

		var lowerUuid = facts("00000000-0000-0000-0000-000000000001", false, true,
				10.0f, 20.0f, 1.0);
		var higherUuid = facts("00000000-0000-0000-0000-000000000004", false, true,
				10.0f, 20.0f, 1.0);
		assertEquals(lowerUuid, MegumiDeerPolicy.choose(List.of(higherUuid, lowerUuid)).orElseThrow());
	}

	@Test
	void onlyLiveOwnedSameLevelWoundedRecipientsInsideSupportRangeAreEligible() {
		assertTrue(MegumiDeerPolicy.eligibleRecipient(facts("00000000-0000-0000-0000-000000000001",
				false, true, 10.0f, 20.0f, 1.0)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(new MegumiDeerPolicy.RecipientFacts(
				UUID.randomUUID(), false, false, true, true, false, true, 10.0f, 20.0f, 1.0)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(new MegumiDeerPolicy.RecipientFacts(
				UUID.randomUUID(), true, true, true, true, false, true, 10.0f, 20.0f, 1.0)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(new MegumiDeerPolicy.RecipientFacts(
				UUID.randomUUID(), true, false, false, true, false, true, 10.0f, 20.0f, 1.0)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(new MegumiDeerPolicy.RecipientFacts(
				UUID.randomUUID(), true, false, true, false, false, true, 10.0f, 20.0f, 1.0)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(facts("00000000-0000-0000-0000-000000000002",
				false, true, 10.0f, 20.0f, MegumiShikigamiProfile.DEER_SUPPORT_RADIUS + 0.01)));
		assertFalse(MegumiDeerPolicy.eligibleRecipient(facts("00000000-0000-0000-0000-000000000003",
				false, true, 20.0f, 20.0f, 1.0)));
	}

	@Test
	void emptyOrFullyHealthyScansChooseNoRecipient() {
		assertTrue(MegumiDeerPolicy.choose(List.of()).isEmpty());
		var healthy = facts("00000000-0000-0000-0000-000000000001", false, true,
				20.0f, 20.0f, 1.0);
		assertTrue(MegumiDeerPolicy.choose(List.of(healthy)).isEmpty());
	}

	@Test
	void healingIsClampedToMissingHealthAndNeverGoesNegative() {
		assertEquals(6.0f, MegumiDeerPolicy.healAmount(8.0f, 20.0f, 6.0f));
		assertEquals(2.0f, MegumiDeerPolicy.healAmount(18.0f, 20.0f, 6.0f));
		assertEquals(0.0f, MegumiDeerPolicy.healAmount(20.0f, 20.0f, 6.0f));
		assertEquals(0.0f, MegumiDeerPolicy.healAmount(10.0f, 20.0f, -1.0f));
	}

	@Test
	void allowlistCleansesExactlyTheSpecifiedVanillaEffects() {
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.BLINDNESS, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.POISON, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.WITHER, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.WEAKNESS, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.MINING_FATIGUE, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.HUNGER, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.LEVITATION, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.UNLUCK, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.BAD_OMEN, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.DARKNESS, false));
		assertTrue(MegumiDeerPolicy.cleanseable(MobEffects.NAUSEA, false));
		assertFalse(MegumiDeerPolicy.cleanseable(MobEffects.DARKNESS, true));
		assertFalse(MegumiDeerPolicy.cleanseable(MobEffects.NAUSEA, true));
	}

	@Test
	void authorityMarkersAndSlowGripAreNeverCleanseable() {
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.GRIPPED, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.CURSED_FEAR, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.MEGUMI_SHADOW_GRIP, false));
		assertFalse(MegumiDeerPolicy.cleanseable(MobEffects.SLOWNESS, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.MEGUMI_NUE_WINGS, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.MEGUMI_TOAD_TONGUE, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.MEGUMI_SOAKED, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.RESONANT_MOMENTUM, false));
		assertFalse(MegumiDeerPolicy.cleanseable(JujutsuEffects.TODO_SWAP_MOMENTUM, false));
	}

	@Test
	void scanTimingIsInclusiveAtTheScheduledTick() {
		assertFalse(MegumiDeerPolicy.scanDue(19L, 20L));
		assertTrue(MegumiDeerPolicy.scanDue(20L, 20L));
		assertTrue(MegumiDeerPolicy.scanDue(21L, 20L));
	}

	@Test
	void woundedDeerCanReceiveItsOwnPulse() {
		var deer = facts("00000000-0000-0000-0000-000000000001", false, true,
				10.0f, 20.0f, 0.0);
		assertTrue(MegumiDeerPolicy.eligibleRecipient(deer));
		assertEquals(deer, MegumiDeerPolicy.choose(List.of(deer)).orElseThrow());
	}

	private static MegumiDeerPolicy.RecipientFacts facts(String uuid, boolean owner,
			boolean shikigami, float health, float maxHealth, double distance) {
		return new MegumiDeerPolicy.RecipientFacts(UUID.fromString(uuid), true, false, true,
				true, owner, shikigami, health, maxHealth, distance);
	}
}
