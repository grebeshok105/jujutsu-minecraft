package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import jujutsu.mod.client.vfx.domain.DomainSphereTiming;
import jujutsu.mod.vfx.VfxCue;

/**
 * The channel is deliberately constructible without a GL context: the renderer it owns must stay
 * GPU-free until the first {@code render()} call. {@code render()} itself is not exercised here —
 * it needs a live {@code WorldRenderContext}; the retained-window assertions below read the channel's
 * own list instead, so eviction order is proven rather than inferred from the eviction expression.
 */
class VfxDomainSphereChannelTest {
	private static final DomainSphereTiming TIMING = DomainSphereTiming.defaults(30.0);

	@Test
	void triggerKeepsOneActiveSphere() {
		VfxDomainSphereChannel channel = new VfxDomainSphereChannel();
		assertEquals(0, channel.activeCount());
		channel.triggerSphere(cue(0L), TIMING);
		assertEquals(1, channel.activeCount());
	}

	@Test
	void fifthTriggerEvictsTheOldestSphere() {
		VfxDomainSphereChannel channel = new VfxDomainSphereChannel();
		for (long startGameTime = 0L; startGameTime < 5L; startGameTime++) {
			channel.triggerSphere(cue(startGameTime), TIMING);
		}
		assertEquals(4, channel.activeCount());
		assertEquals(List.of(1L, 2L, 3L, 4L), retainedStartGameTimes(channel),
				"the retained window must be the four newest spheres, oldest evicted");
	}

	@Test
	void clearDropsEveryActiveSphere() {
		VfxDomainSphereChannel channel = new VfxDomainSphereChannel();
		channel.triggerSphere(cue(0L), TIMING);
		channel.triggerSphere(cue(1L), TIMING);
		channel.clear();
		assertEquals(0, channel.activeCount());
	}

	@Test
	void resetSessionClearsAndAcceptsTriggersAgain() {
		VfxDomainSphereChannel channel = new VfxDomainSphereChannel();
		channel.triggerSphere(cue(0L), TIMING);
		channel.resetSession();
		assertEquals(0, channel.activeCount());
		channel.triggerSphere(cue(1L), TIMING);
		assertEquals(1, channel.activeCount(), "resetSession must re-enable the session");
	}

	@Test
	void constructorAndCloseAreGlFreeAndSafeWithoutRender() {
		VfxDomainSphereChannel channel = new VfxDomainSphereChannel();
		channel.triggerSphere(cue(0L), TIMING);
		assertDoesNotThrow(channel::close);
		assertEquals(0, channel.activeCount(), "close must not leave spheres to be rendered after shutdown");
	}

	private static List<Long> retainedStartGameTimes(VfxDomainSphereChannel channel) {
		return retainedSpheres(channel).stream()
				.map(active -> accessor(active, "cue"))
				.map(VfxCue.class::cast)
				.map(VfxCue::startGameTime)
				.toList();
	}

	private static List<?> retainedSpheres(VfxDomainSphereChannel channel) {
		try {
			Field field = VfxDomainSphereChannel.class.getDeclaredField("activeSpheres");
			field.setAccessible(true);
			return List.copyOf((List<?>) field.get(channel));
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot read VfxDomainSphereChannel.activeSpheres", error);
		}
	}

	private static Object accessor(Object target, String name) {
		try {
			Method method = target.getClass().getDeclaredMethod(name);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot read " + name + " from " + target.getClass(), error);
		}
	}

	private static VfxCue cue(long startGameTime) {
		return new VfxCue(ResourceLocation.parse("jujutsumod:test/domain-sphere"), Vec3.ZERO,
				VfxCue.NO_ANCHOR, Vec3.ZERO, 30, startGameTime, startGameTime, Vec3.ZERO);
	}
}
