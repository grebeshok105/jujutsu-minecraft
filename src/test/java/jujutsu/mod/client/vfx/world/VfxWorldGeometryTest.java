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

class VfxWorldGeometryTest {
	private static final double EPSILON = 1.0E-6;

	@Test
	void directionalBasisUsesSafeUnitOrthogonalAxes() {
		Vec3[] zero = VfxWorldGeometry.directionalBasis(Vec3.ZERO);
		assertEquals(VfxWorldGeometry.EAST, zero[0]);
		assertEquals(VfxWorldGeometry.UP, zero[1]);

		Vec3 forward = new Vec3(0.4, 0.8, 0.2);
		Vec3[] basis = VfxWorldGeometry.directionalBasis(forward);
		assertUnit(basis[0]);
		assertUnit(basis[1]);
		assertPerpendicular(basis[0], forward);
		assertPerpendicular(basis[1], forward);
		assertPerpendicular(basis[0], basis[1]);

		Vec3[] nearVertical = VfxWorldGeometry.directionalBasis(new Vec3(0.01, 1.0, 0.01));
		assertUnit(nearVertical[0]);
		assertUnit(nearVertical[1]);
		assertTrue(Math.abs(nearVertical[0].dot(new Vec3(-1.0, 0.0, 0.0))) > 0.99,
				"near-vertical forward must use the NORTH fallback basis");
		assertFinite(nearVertical[0]);
		assertFinite(nearVertical[1]);
	}

	@Test
	void sideVectorUsesPerpendicularWidthAndFallbacks() {
		Vec3 direction = new Vec3(0.3, 0.8, -0.2);
		Vec3 side = VfxWorldGeometry.sideVector(direction, new Vec3(0.2, 0.4, 0.5), 0.37f);
		assertEquals(0.37, side.length(), EPSILON);
		assertPerpendicular(side, direction);
		assertFinite(side);

		Vec3 zeroDirection = VfxWorldGeometry.sideVector(Vec3.ZERO, Vec3.ZERO, 0.5f);
		assertEquals(0.5, zeroDirection.length(), EPSILON);
		assertPerpendicular(zeroDirection, VfxWorldGeometry.UP);
		assertFinite(zeroDirection);

		Vec3 zeroView = VfxWorldGeometry.sideVector(VfxWorldGeometry.NORTH, Vec3.ZERO, 0.5f);
		assertEquals(0.5, zeroView.length(), EPSILON);
		assertPerpendicular(zeroView, VfxWorldGeometry.NORTH);
		assertFinite(zeroView);

		Vec3 parallel = VfxWorldGeometry.sideVector(VfxWorldGeometry.EAST, VfxWorldGeometry.EAST, 0.5f);
		assertEquals(0.5, parallel.length(), EPSILON);
		assertPerpendicular(parallel, VfxWorldGeometry.EAST);
		assertFinite(parallel);
	}

	@Test
	void addRibbonEmitsTwoQuadsInTheExistingOrderAndColor() {
		RecordingConsumer recording = new RecordingConsumer();
		VfxWorldGeometry.addRibbon(recording.consumer(), Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(1, 0, 0), 12, 34, 56, 78);

		assertEquals(8, recording.vertices.size());
		assertVertex(recording.vertices.get(0), -1, 0, 0);
		assertVertex(recording.vertices.get(1), -1, 1, 0);
		assertVertex(recording.vertices.get(2), 1, 1, 0);
		assertVertex(recording.vertices.get(3), 1, 0, 0);
		assertVertex(recording.vertices.get(4), 1, 0, 0);
		assertVertex(recording.vertices.get(5), 1, 1, 0);
		assertVertex(recording.vertices.get(6), -1, 1, 0);
		assertVertex(recording.vertices.get(7), -1, 0, 0);
		for (Vertex vertex : recording.vertices) {
			assertEquals(12, vertex.red());
			assertEquals(34, vertex.green());
			assertEquals(56, vertex.blue());
			assertEquals(78, vertex.alpha());
		}
	}

	private static void assertUnit(Vec3 vector) {
		assertEquals(1.0, vector.length(), EPSILON);
	}

	private static void assertPerpendicular(Vec3 first, Vec3 second) {
		assertEquals(0.0, first.dot(second), EPSILON);
	}

	private static void assertFinite(Vec3 vector) {
		assertTrue(Double.isFinite(vector.x));
		assertTrue(Double.isFinite(vector.y));
		assertTrue(Double.isFinite(vector.z));
	}

	private static void assertVertex(Vertex vertex, double x, double y, double z) {
		assertEquals(x, vertex.x(), 0.0);
		assertEquals(y, vertex.y(), 0.0);
		assertEquals(z, vertex.z(), 0.0);
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
