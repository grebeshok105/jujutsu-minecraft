package jujutsu.mod.client.vfx.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShadowWorldEffectsTest {
	private static final float EPSILON = 1.0E-6f;

	@Test
	void openingRadiusExpandsMonotonically() {
		assertEquals(0.26f, ShadowWorldEffects.shadowPoolRadius(true, 0.0f), EPSILON);
		assertEquals(0.94f, ShadowWorldEffects.shadowPoolRadius(true, 1.0f), EPSILON);
		float previous = ShadowWorldEffects.shadowPoolRadius(true, 0.0f);
		for (int step = 1; step <= 20; step++) {
			float radius = ShadowWorldEffects.shadowPoolRadius(true, step / 20.0f);
			assertTrue(radius >= previous, "opening radius must not decrease");
			previous = radius;
		}
	}

	@Test
	void closingRadiusContractsMonotonically() {
		assertEquals(0.94f, ShadowWorldEffects.shadowPoolRadius(false, 0.0f), EPSILON);
		assertEquals(0.26f, ShadowWorldEffects.shadowPoolRadius(false, 1.0f), EPSILON);
		float previous = ShadowWorldEffects.shadowPoolRadius(false, 0.0f);
		for (int step = 1; step <= 20; step++) {
			float radius = ShadowWorldEffects.shadowPoolRadius(false, step / 20.0f);
			assertTrue(radius <= previous, "closing radius must not increase");
			previous = radius;
		}
	}

	@Test
	void opacityCurvesMatchOpenAndCloseContracts() {
		assertEquals(0.88f, ShadowWorldEffects.shadowPoolOpacity(true, 0.0f), EPSILON);
		assertEquals(0.96f, ShadowWorldEffects.shadowPoolOpacity(true, 1.0f), EPSILON);
		assertEquals(0.96f, ShadowWorldEffects.shadowPoolOpacity(false, 0.0f), EPSILON);
		assertEquals(0.0f, ShadowWorldEffects.shadowPoolOpacity(false, 1.0f), EPSILON);
	}

	@Test
	void progressIsClampedForBothCurves() {
		assertEquals(ShadowWorldEffects.shadowPoolRadius(true, 0.0f), ShadowWorldEffects.shadowPoolRadius(true, -1.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolRadius(false, 1.0f), ShadowWorldEffects.shadowPoolRadius(false, 2.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolOpacity(true, 0.0f), ShadowWorldEffects.shadowPoolOpacity(true, -1.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolOpacity(false, 1.0f), ShadowWorldEffects.shadowPoolOpacity(false, 2.0f), EPSILON);
	}

	@Test
	void trapPoolSweepIsVoidBlackAliveAndDissolvesOnClose() {
		Vec3 center = Vec3.ZERO;
		float radius = 2.6f;

		RecordingConsumer opening = new RecordingConsumer();
		ShadowWorldEffects.renderShadowTrapPool(opening.consumer(), center, 0.0f, radius, true);
		RecordingConsumer unfurled = new RecordingConsumer();
		ShadowWorldEffects.renderShadowTrapPool(unfurled.consumer(), center, 1.0f, radius, true);
		RecordingConsumer closing = new RecordingConsumer();
		ShadowWorldEffects.renderShadowTrapPool(closing.consumer(), center, 0.5f, radius, false);
		RecordingConsumer closed = new RecordingConsumer();
		ShadowWorldEffects.renderShadowTrapPool(closed.consumer(), center, 1.0f, radius, false);

		// While the pool lives (the whole unfurl), every vertex is opaque void black — a hole.
		for (RecordingConsumer recording : new RecordingConsumer[]{opening, unfurled}) {
			assertEquals(96, recording.vertices.size());
			for (Vertex vertex : recording.vertices) {
				assertEquals(0, vertex.red());
				assertEquals(0, vertex.green());
				assertEquals(0, vertex.blue());
				assertEquals(255, vertex.alpha());
			}
		}
		// The close dissolves instead of blinking out: smoothstep midpoint, fully gone at the end,
		// still pure black while any of it remains.
		assertEquals(96, closing.vertices.size());
		for (Vertex vertex : closing.vertices) {
			assertEquals(0, vertex.red());
			assertEquals(0, vertex.green());
			assertEquals(0, vertex.blue());
			assertEquals(128, vertex.alpha());
		}
		for (Vertex vertex : closed.vertices) {
			assertEquals(0, vertex.alpha());
		}
		// The unfurl/collapse radius animation survives: 26% -> full -> 63% on close.
		assertEquals(0.26f * radius, opening.vertices.get(1).x(), EPSILON);
		assertEquals(radius, unfurled.vertices.get(1).x(), EPSILON);
		assertEquals(0.63f * radius, closing.vertices.get(1).x(), EPSILON);
	}

	@Test
	void trapZoneIsFullyOpaqueVoidBlackAtAnyProgress() {
		Vec3 center = Vec3.ZERO;
		float radius = 2.6f;
		for (float progress : new float[]{0.0f, 0.37f, 1.0f}) {
			RecordingConsumer recording = new RecordingConsumer();
			ShadowWorldEffects.renderShadowTrapPool(recording.consumer(), center, progress, radius);
			assertEquals(96, recording.vertices.size());
			for (Vertex vertex : recording.vertices) {
				assertEquals(0, vertex.red());
				assertEquals(0, vertex.green());
				assertEquals(0, vertex.blue());
				assertEquals(255, vertex.alpha());
			}
			assertEquals(radius, recording.vertices.get(1).x(), EPSILON);
		}
	}

	@Test
	void summonPoolStillFadesOutAtVertexLevel() {
		RecordingConsumer recording = new RecordingConsumer();
		ShadowWorldEffects.renderMegumiShadowPool(recording.consumer(), Vec3.ZERO, 1.0f, false);
		assertEquals(96, recording.vertices.size());
		for (Vertex vertex : recording.vertices) {
			assertEquals(0, vertex.alpha(), "the dogs' decorative pool must keep fading out");
		}
	}

	private record Vertex(float x, float y, float z, int red, int green, int blue, int alpha) {}

	private static final class RecordingConsumer implements InvocationHandler {
		private final List<Vertex> vertices = new ArrayList<>();
		private final VertexConsumer proxy = (VertexConsumer) Proxy.newProxyInstance(
				VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, this);

		private VertexConsumer consumer() {
			return proxy;
		}

		@Override
		public Object invoke(Object object, Method method, Object[] args) {
			if (method.getName().equals("addVertex") && args != null && args.length == 3) {
				vertices.add(new Vertex(((Number) args[0]).floatValue(), ((Number) args[1]).floatValue(),
						((Number) args[2]).floatValue(), 0, 0, 0, 0));
				return proxy;
			}
			if (method.getName().equals("setColor") && args != null && args.length == 4) {
				int last = vertices.size() - 1;
				vertices.set(last, new Vertex(vertices.get(last).x(), vertices.get(last).y(), vertices.get(last).z(),
						((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
						((Number) args[2]).intValue(), ((Number) args[3]).intValue()));
				return proxy;
			}
			if (method.getReturnType().isAssignableFrom(VertexConsumer.class)) {
				return proxy;
			}
			return null;
		}
	}
}
