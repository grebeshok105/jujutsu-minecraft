package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import jujutsu.mod.client.vfx.blackhole.BlackHoleTiming;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Headless lifecycle of the single-slot channel: one hole at a time, retrigger refused, forced
 * removal frees the slot. GL paths are not exercised — the channel constructor is GPU-free by
 * contract (same split as the domain sphere).
 */
class VfxBlackHoleChannelTest {
	private static final Vec3 NORMAL = new Vec3(0.0, 1.0, 0.0);

	@Test
	void firstTriggerStartsAHole() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		assertTrue(channel.tryTrigger(cue(0L), BlackHoleTiming.defaults(1L), NORMAL, 0.0f));
		assertNotNull(channel.active());
		assertTrue(channel.hasActive());
	}

	@Test
	void secondTriggerWhileAliveIsRefused() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		assertTrue(channel.tryTrigger(cue(0L), BlackHoleTiming.defaults(1L), NORMAL, 0.0f));
		assertFalse(channel.tryTrigger(cue(1L), BlackHoleTiming.defaults(2L), NORMAL, 0.0f),
				"a second hole must never spawn while one is alive");
		assertEquals(1, countActive(channel));
	}

	@Test
	void forceRemoveFreesTheSlot() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		channel.tryTrigger(cue(0L), BlackHoleTiming.defaults(1L), NORMAL, 0.0f);
		channel.forceRemove(net.minecraft.client.Minecraft.getInstance());
		assertNull(channel.active());
		assertTrue(channel.tryTrigger(cue(2L), BlackHoleTiming.defaults(3L), NORMAL, 0.0f));
	}

	@Test
	void nonFiniteOriginIsDropped() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		VfxCue bad = new VfxCue(ResourceLocation.fromNamespaceAndPath("jujutsumod", "black_hole"),
				new Vec3(Double.NaN, 0.0, 0.0), VfxCue.NO_ANCHOR, Vec3.ZERO, 140, 0L, 1L, Vec3.ZERO);
		assertFalse(channel.tryTrigger(bad, BlackHoleTiming.defaults(1L), NORMAL, 0.0f));
		assertNull(channel.active());
	}

	@Test
	void duckAmountIsZeroWithNoHole() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		assertEquals(0.0f, channel.duckAmount(), 1.0e-6f);
		assertEquals(0.0f, channel.hudWarpActive(), 1.0e-6f);
	}

	@Test
	void constructorAndCloseAreGlFree() {
		VfxBlackHoleChannel channel = new VfxBlackHoleChannel();
		channel.tryTrigger(cue(0L), BlackHoleTiming.defaults(1L), NORMAL, 0.0f);
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(channel::close);
		assertNull(channel.active());
	}

	private static int countActive(VfxBlackHoleChannel channel) {
		return channel.hasActive() ? 1 : 0;
	}

	private static VfxCue cue(long startGameTime) {
		return VfxCues.worldFixed(
				ResourceLocation.fromNamespaceAndPath("jujutsumod", "black_hole"),
				new Vec3(0.0, 64.0, 0.0), 140, startGameTime, 1L);
	}
}
