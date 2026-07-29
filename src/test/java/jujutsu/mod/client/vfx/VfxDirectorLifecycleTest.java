package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class VfxDirectorLifecycleTest {
	private static final VfxCue CUE = new VfxCue(
			NobaraVfxIds.HAMMER, Vec3.ZERO, VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 100L, 1L, Vec3.ZERO);

	@Test
	void acceptedCueCreatesAndStartsOnceAtZeroAge() {
		Recording recording = new Recording(20);

		boolean started = VfxDirector.startResolvedCue(CUE, recording, 100L, null);

		assertTrue(started);
		assertEquals(1, recording.createCalls);
		assertEquals(1, recording.startCalls);
		assertEquals(0.0f, recording.initialAgeTicks);
	}

	@Test
	void lateValidCueStartsOnceAtItsTrueAge() {
		Recording recording = new Recording(20);

		boolean started = VfxDirector.startResolvedCue(CUE, recording, 107L, null);

		assertTrue(started);
		assertEquals(1, recording.createCalls);
		assertEquals(1, recording.startCalls);
		assertEquals(7.0f, recording.initialAgeTicks);
	}

	@Test
	void cueAtExpiryBoundaryCreatesButNeverStarts() {
		Recording recording = new Recording(20);

		boolean started = VfxDirector.startResolvedCue(CUE, recording, 120L, null);

		assertFalse(started);
		assertEquals(1, recording.createCalls);
		assertEquals(0, recording.startCalls);
	}

	@Test
	void oneTickBeforeExpiryStillStartsAtAgeNineteen() {
		Recording recording = new Recording(20);

		boolean started = VfxDirector.startResolvedCue(CUE, recording, 119L, null);

		assertTrue(started);
		assertEquals(1, recording.startCalls);
		assertEquals(19.0f, recording.initialAgeTicks);
	}

	@Test
	void futureCueStartsWithClampedZeroAge() {
		Recording recording = new Recording(20);

		boolean started = VfxDirector.startResolvedCue(CUE, recording, 100L - 5L, null);

		assertTrue(started);
		assertEquals(1, recording.startCalls);
		assertEquals(0.0f, recording.initialAgeTicks);
	}

	private static final class Recording implements VfxRecipe {
		private final int durationTicks;
		private int createCalls;
		private int startCalls;
		private float initialAgeTicks = -1.0f;

		private Recording(int durationTicks) {
			this.durationTicks = durationTicks;
		}

		@Override
		public VfxInstance create(VfxCue cue) {
			createCalls++;
			return new VfxInstance() {
				@Override
				public int durationTicks() {
					return durationTicks;
				}

				@Override
				public void start(VfxContext context, float initialAgeTicks) {
					startCalls++;
					Recording.this.initialAgeTicks = initialAgeTicks;
				}
			};
		}
	}
}
