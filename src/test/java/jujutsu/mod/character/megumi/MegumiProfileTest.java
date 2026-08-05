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

	@Test
	void shadowKitBaselineMatchesTheApprovedSlice() {
		assertEquals(20.0, MegumiProfile.SHADOW_TRAP_RANGE);
		assertEquals(2.6, MegumiProfile.SHADOW_TRAP_RADIUS);
		assertEquals(100, MegumiProfile.SHADOW_TRAP_DURATION_TICKS);
		assertEquals(200, MegumiProfile.SHADOW_TRAP_COOLDOWN_TICKS);
		assertEquals(8, MegumiProfile.SHADOW_TRAP_GRIP_REFRESH_TICKS);
		assertEquals(-0.75, MegumiProfile.SHADOW_GRIP_SPEED_MULTIPLIER);
		assertEquals(-1.0, MegumiProfile.SHADOW_GRIP_JUMP_MULTIPLIER);
		assertEquals(40, MegumiProfile.SHADOW_TRAP_ZONE_PULSE_TICKS);
		assertEquals(20.0, MegumiProfile.SHADOW_STEP_TARGET_RANGE);
		assertEquals(24.0, MegumiProfile.SHADOW_STEP_RANGE);
		assertEquals(1.75, MegumiProfile.BACKSTEP_DISTANCE);
		assertEquals(8, MegumiProfile.SHADOW_SINK_TICKS);
		assertEquals(4, MegumiProfile.SHADOW_HIDDEN_TICKS);
		assertEquals(6, MegumiProfile.SHADOW_EMERGE_TICKS);
		assertEquals(50, MegumiProfile.SUBMERGE_MAX_TICKS);
		assertEquals(5, MegumiProfile.SHADOW_RIPPLE_PERIOD_TICKS);
		assertEquals(120, MegumiProfile.SHADOW_STEP_COOLDOWN_TICKS);
		assertEquals(200, MegumiProfile.SUBMERGE_COOLDOWN_TICKS);
		assertEquals(3.0, MegumiProfile.EMERGE_SEARCH_RADIUS);
	}

	@Test
	void shadowKitInvariantsHold() {
		assertTrue(MegumiProfile.SHADOW_TRAP_COOLDOWN_TICKS > MegumiProfile.SHADOW_TRAP_DURATION_TICKS,
				"one live trap per owner: the cooldown must outlive the pool");
		assertEquals(50, MegumiProfile.SUBMERGE_MAX_TICKS, "the deep submerge must have a hard ceiling");
		assertTrue(MegumiProfile.SHADOW_SINK_TICKS > 0 && MegumiProfile.SHADOW_HIDDEN_TICKS > 0
						&& MegumiProfile.SHADOW_EMERGE_TICKS > 0,
				"every phase must take at least one tick");
		assertEquals(-1.0, MegumiProfile.SHADOW_GRIP_JUMP_MULTIPLIER,
				"jumping must be fully suppressed, not merely weakened");
		assertTrue(MegumiProfile.SHADOW_GRIP_SPEED_MULTIPLIER < 0.0
						&& MegumiProfile.SHADOW_GRIP_SPEED_MULTIPLIER > -1.0,
				"the grip slows a body but never stops it");
		assertTrue(MegumiProfile.SUBMERGE_COOLDOWN_TICKS > MegumiProfile.SHADOW_STEP_COOLDOWN_TICKS,
				"the deep submerge must cost more than a tap step");
	}

	@Test
	void dropBaselineMatchesTheApprovedSlice() {
		assertEquals(20.0, MegumiProfile.DROP_RANGE);
		assertEquals(4.0, MegumiProfile.DROP_ZONE_HEIGHT_BLOCKS);
		assertEquals(1.2, MegumiProfile.DROP_ZONE_RADIUS);
		assertEquals(20, MegumiProfile.DROP_TELEGRAPH_TICKS);
		assertEquals(5, MegumiProfile.DROP_ZONE_PULSE_TICKS);
		assertEquals(60, MegumiProfile.DROP_COOLDOWN_TICKS);
		assertEquals(1.0f, MegumiProfile.DROP_SOFT_DAMAGE_PER_BLOCK);
		assertEquals(5, MegumiProfile.DROP_SOFT_DAMAGE_MAX);
		assertEquals(30, MegumiProfile.DROP_WEIGHT_SAND);
		assertEquals(25, MegumiProfile.DROP_WEIGHT_GRAVEL);
		assertEquals(25, MegumiProfile.DROP_WEIGHT_CLAY);
		assertEquals(20, MegumiProfile.DROP_WEIGHT_ANVIL);
		assertEquals(1, MegumiProfile.DROP_MIN_BLOCKS);
		assertEquals(3, MegumiProfile.DROP_MAX_BLOCKS);
		assertEquals(0.9, MegumiProfile.DROP_SCATTER_RADIUS);
	}

	@Test
	void dropInvariantsHold() {
		assertEquals(100, MegumiProfile.DROP_WEIGHT_SAND + MegumiProfile.DROP_WEIGHT_GRAVEL
						+ MegumiProfile.DROP_WEIGHT_CLAY + MegumiProfile.DROP_WEIGHT_ANVIL,
				"the block weights must partition the pick table");
		assertTrue(MegumiProfile.DROP_TELEGRAPH_TICKS < MegumiProfile.DROP_COOLDOWN_TICKS,
				"the telegraph must finish long before the cooldown clears");
		assertTrue(MegumiProfile.DROP_ZONE_RADIUS < MegumiProfile.SHADOW_TRAP_RADIUS,
				"the drop zone must be a tight disc under the trap pool");
		assertTrue(MegumiProfile.DROP_MIN_BLOCKS >= 1
						&& MegumiProfile.DROP_MIN_BLOCKS <= MegumiProfile.DROP_MAX_BLOCKS,
				"the volley range must be a real range starting at one block");
		assertTrue(MegumiProfile.DROP_SCATTER_RADIUS < MegumiProfile.DROP_ZONE_RADIUS,
				"scattered blocks must spawn inside the telegraphed disc");
	}
}
