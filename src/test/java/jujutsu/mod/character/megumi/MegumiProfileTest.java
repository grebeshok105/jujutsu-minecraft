package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MegumiProfileTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void divineDogBaselineMatchesTheApprovedSlice() {
		assertEquals(60.0, MegumiProfile.DOG_HEALTH);
		assertEquals(3.0, MegumiProfile.DOG_ATTACK_DAMAGE);
		assertEquals(0.34, MegumiProfile.DOG_MOVEMENT_SPEED);
		assertEquals(1.0, MegumiProfile.NAVIGATION_SPEED_MODIFIER);
		assertEquals(10.0, MegumiProfile.FOLLOW_START_DISTANCE);
		assertEquals(2.0, MegumiProfile.FOLLOW_STOP_DISTANCE);
		assertEquals(32.0, MegumiProfile.LEASH_DISTANCE);
		assertEquals(3.0, MegumiProfile.LEASH_SAFE_SEARCH_RADIUS);
		assertEquals(10, MegumiProfile.LEASH_RETRY_TICKS);
		assertEquals(20.0, MegumiProfile.SIC_RANGE);
		assertEquals(30, MegumiProfile.SIC_COOLDOWN_TICKS);
		assertEquals(3.0, MegumiProfile.POUNCE_MIN_RANGE);
		assertEquals(8.0, MegumiProfile.POUNCE_MAX_RANGE);
		assertEquals(80, MegumiProfile.POUNCE_COOLDOWN_TICKS);
		assertEquals(16, MegumiProfile.POUNCE_TIMEOUT_TICKS);
		assertEquals(0.92, MegumiProfile.POUNCE_HORIZONTAL_SPEED);
		assertEquals(0.42, MegumiProfile.POUNCE_VERTICAL_SPEED);
		assertEquals(0.58, MegumiProfile.POUNCE_MAX_VERTICAL_SPEED);
		assertEquals(2.0f, MegumiProfile.POUNCE_BONUS_DAMAGE);
		assertEquals(2.4, MegumiProfile.POUNCE_KNOCKBACK);
		assertEquals(6, MegumiProfile.POUNCE_STAGGER_TICKS);
		assertEquals(16, MegumiProfile.DOG_MATERIALIZATION_TICKS);
		assertEquals(12, MegumiProfile.DOG_RECALL_TICKS);
		assertEquals(240, MegumiProfile.RECALL_COOLDOWN_TICKS);
		assertEquals(600, MegumiProfile.PACK_DEATH_COOLDOWN_TICKS);
		assertTrue(MegumiProfile.FOLLOW_STOP_DISTANCE < MegumiProfile.FOLLOW_START_DISTANCE);
		assertTrue(MegumiProfile.POUNCE_MIN_RANGE < MegumiProfile.POUNCE_MAX_RANGE);
		assertTrue(MegumiProfile.RECALL_COOLDOWN_TICKS < MegumiProfile.PACK_DEATH_COOLDOWN_TICKS);
	}

	@Test
	void registeredAttributeSupplierUsesTheSameProfileValues() {
		AttributeSupplier attributes = MegumiDefinition.createDivineDogAttributes().build();
		assertEquals(MegumiProfile.DOG_HEALTH, attributes.getValue(Attributes.MAX_HEALTH));
		assertEquals(MegumiProfile.DOG_ATTACK_DAMAGE, attributes.getValue(Attributes.ATTACK_DAMAGE));
		assertEquals(MegumiProfile.DOG_MOVEMENT_SPEED, attributes.getValue(Attributes.MOVEMENT_SPEED));
	}
}
