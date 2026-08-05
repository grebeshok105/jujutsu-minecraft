package jujutsu.mod.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import io.netty.buffer.Unpooled;
import jujutsu.mod.network.VfxCuePayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class VfxCueTest {
	private static final Vec3 ORIGIN = new Vec3(12.5, 64.25, -8.0);
	private static final Vec3 OFFSET = new Vec3(0.25, 1.5, -0.75);
	private static final int INTENSITY = 7;
	private static final long GAME_TIME = 900L;
	private static final long SEED = -12345L;

	@Test
	void worldFixedCueRoundTripsAllEightFields() {
		Vec3 inputDirection = new Vec3(8.0, 3.0, -4.0);
		VfxCue expected = new VfxCue(
				NobaraVfxIds.ENLARGE, ORIGIN, VfxCue.NO_ANCHOR, OFFSET, INTENSITY, GAME_TIME, SEED, inputDirection);

		VfxCue actual = roundTrip(expected);

		assertFieldsEqual(expected, actual);
		assertEquals(inputDirection.normalize(), expected.direction());
		assertEquals(1.0, actual.direction().length(), 1.0E-9);
		assertEquals(VfxCue.NO_ANCHOR, actual.anchorEntityId());
	}

	@Test
	void anchoredCueRoundTripsAllEightFields() {
		VfxCue expected = new VfxCue(
				TodoVfxIds.PAIR_MARK, new Vec3(-4.5, 70.0, 2.25), 91,
				new Vec3(0.0, 1.62, 0.0), 3, 321L, 9876L, new Vec3(0.0, 4.0, 0.0));

		VfxCue actual = roundTrip(expected);

		assertFieldsEqual(expected, actual);
		assertEquals(new Vec3(0.0, 1.0, 0.0), actual.direction());
	}

	@Test
	void casterActionCueKeepsCasterAnchorAndActionVariant() {
		VfxCue expected = new VfxCue(
				NobaraVfxIds.CASTER_ACTION, ORIGIN, 17, Vec3.ZERO, NobaraVfxIds.CASTER_HAIRPIN_DIRECTED,
				GAME_TIME, SEED, Vec3.ZERO);

		VfxCue actual = roundTrip(expected);

		assertFieldsEqual(expected, actual);
		assertEquals(17, actual.anchorEntityId());
		assertEquals(NobaraVfxIds.CASTER_HAIRPIN_DIRECTED, actual.intensity());
	}

	@Test
	void zeroDirectionRoundTripsAsZeroWithoutNaN() {
		VfxCue actual = roundTrip(new VfxCue(
				NobaraVfxIds.EXPLOSION, ORIGIN, VfxCue.NO_ANCHOR, Vec3.ZERO, 8, 321L, SEED, Vec3.ZERO));

		assertEquals(Vec3.ZERO, actual.direction());
		assertFalse(Double.isNaN(actual.direction().x));
		assertFalse(Double.isNaN(actual.direction().y));
		assertFalse(Double.isNaN(actual.direction().z));
	}

	@Test
	void liveWireStringsRemainStable() {
		assertEquals(Set.of(
				"nobara/hammer", "nobara/impact", "nobara/impact_sound", "nobara/detonate", "nobara/enlarge",
				"nobara/explosion", "nobara/first_person_snap", "nobara/remnant_drop", "nobara/ritual_bind",
				"nobara/doll_strike", "nobara/resonance_release", "nobara/hammer_horizontal", "nobara/hammer_overhead",
				"nobara/hammer_nail_launch", "nobara/black_flash", "nobara/self_resonance", "nobara/nail_deepen",
				"nobara/nail_trap_placed", "nobara/nail_trap_armed", "nobara/nail_trap_collapse", "nobara/nail_trap_impact",
				"nobara/caster_action"),
				paths(NobaraVfxIds.LIVE));
		assertEquals(Set.of(
				"todo/boogie_woogie", "todo/swap_endpoint", "todo/feint_tell", "todo/pair_mark",
				"todo/swap_afterimage", "todo/swap_arrival", "todo/momentum_strike"), paths(TodoVfxIds.LIVE));
		assertEquals(Set.of(
				"megumi/dogs_summon_body", "megumi/dogs_summon", "megumi/dogs_recall", "megumi/dogs_sic",
				"megumi/dogs_pounce"), paths(MegumiVfxIds.LIVE));
	}

	@Test
	void plannedSetsAreEmptyForTheCurrentSlice() {
		assertTrue(NobaraVfxIds.PLANNED.isEmpty());
		assertTrue(TodoVfxIds.PLANNED.isEmpty());
		assertTrue(MegumiVfxIds.PLANNED.isEmpty());
	}

	private static VfxCue roundTrip(VfxCue expected) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			VfxCuePayload.STREAM_CODEC.encode(buffer, new VfxCuePayload(expected));
			VfxCue actual = VfxCuePayload.STREAM_CODEC.decode(buffer).cue();
			assertEquals(0, buffer.readableBytes());
			return actual;
		} finally {
			buffer.release();
		}
	}

	private static void assertFieldsEqual(VfxCue expected, VfxCue actual) {
		assertEquals(expected.effectId(), actual.effectId());
		assertEquals(expected.origin(), actual.origin());
		assertEquals(expected.anchorEntityId(), actual.anchorEntityId());
		assertEquals(expected.anchorOffset(), actual.anchorOffset());
		assertEquals(expected.intensity(), actual.intensity());
		assertEquals(expected.startGameTime(), actual.startGameTime());
		assertEquals(expected.seed(), actual.seed());
		assertEquals(expected.direction(), actual.direction());
	}

	private static Set<String> paths(Set<net.minecraft.resources.ResourceLocation> ids) {
		return ids.stream().map(net.minecraft.resources.ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet());
	}
}
