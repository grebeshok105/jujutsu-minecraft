package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.client.vfx.megumi.MegumiVfxRecipes;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.TodoVfxIds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

// VfxDirector.RECIPES is process-global; serialize tests that reset and populate it.
@ResourceLock("VfxDirector.RECIPES")
@Execution(ExecutionMode.SAME_THREAD)
final class VfxCompletenessTest {
	private static final List<Owner> OWNERS = List.of(
			new Owner("Nobara", NobaraVfxIds.LIVE, NobaraVfxIds.PLANNED),
			new Owner("Todo", TodoVfxIds.LIVE, TodoVfxIds.PLANNED),
			new Owner("Megumi", MegumiVfxIds.LIVE, MegumiVfxIds.PLANNED));

	@BeforeEach
	void clearBefore() {
		VfxDirector.resetRecipesForTest();
	}

	@AfterEach
	void clearAfter() {
		VfxDirector.resetRecipesForTest();
	}

	@Test
	void everyDeclaredIdHasExactlyOneLifecycleState() {
		Set<ResourceLocation> allIds = new HashSet<>();
		for (Owner owner : OWNERS) {
			assertTrue(Set.copyOf(owner.live()).stream().noneMatch(owner.planned()::contains));
			for (ResourceLocation id : owner.live()) {
				assertTrue(allIds.add(id), "duplicate live id: " + id);
			}
			for (ResourceLocation id : owner.planned()) {
				assertTrue(allIds.add(id), "duplicate planned id: " + id);
			}
		}

		for (Class<?> ownerClass : List.of(NobaraVfxIds.class, TodoVfxIds.class, MegumiVfxIds.class)) {
			for (Field field : ownerClass.getDeclaredFields()) {
				if (field.getType() != ResourceLocation.class || !Modifier.isStatic(field.getModifiers())
						|| !Modifier.isPublic(field.getModifiers())) {
					continue;
				}
				ResourceLocation id = read(field);
				assertEquals(1, countStates(id), ownerClass.getSimpleName() + "." + field.getName());
			}
		}
		assertEquals(33, allIds.size());
	}

	@Test
	void realRecipePacksRegisterEveryLiveIdExactlyOnce() {
		NobaraVfxRecipes.register();
		TodoVfxRecipes.register();
		MegumiVfxRecipes.register();

		Set<ResourceLocation> live = liveIds();
		assertEquals(live, VfxDirector.registeredRecipeIdsForTest());
		assertEquals(33, VfxDirector.registeredRecipeIdsForTest().size());
		for (ResourceLocation id : VfxDirector.registeredRecipeIdsForTest()) {
			assertEquals(1, ownerCount(id), "recipe owner count for " + id);
			assertFalse(plannedIds().contains(id));
		}
	}

	@Test
	void duplicateRegistrationIsStillHardFailure() {
		VfxDirector.register(NobaraVfxIds.HAMMER, cue -> VfxInstance.of(1, (context, age) -> {}));
		assertThrows(IllegalStateException.class,
				() -> VfxDirector.register(NobaraVfxIds.HAMMER, cue -> VfxInstance.of(1, (context, age) -> {})));
	}

	@Test
	void plannedIdsRemainVisibleWithoutBeingRequired() {
		Set<ResourceLocation> planned = plannedIds();
		assertTrue(liveIds().stream().noneMatch(planned::contains));
		assertTrue(planned.stream().allMatch(id -> ownerCount(id) == 1));
	}

	@Test
	void compiledProductionEmittersCoverEveryLiveId() {
		Map<ResourceLocation, Set<String>> emitters = productionEmitterMethods();
		assertEquals(liveIds(), emitters.keySet(), "production emitter coverage");
		assertTrue(emitters.values().stream().allMatch(value -> !value.isEmpty()));
	}

	/**
	 * Returns every finite delivery radius observed on a production path for each live id. An empty set
	 * means the id only uses direct {@code sendVfxCue}; multiple values preserve multi-site ownership.
	 */
	static Map<ResourceLocation, Set<Double>> productionDeliveryRadii() {
		JavaClasses production = productionClasses();
		Map<String, JavaMethod> methods = methods(production);
		Map<String, ResourceLocation> fields = idFields();
		Map<ResourceLocation, Set<Double>> radii = new HashMap<>();
		for (JavaMethod method : methods.values()) {
			List<IdReference> ids = referencedIdAccesses(method, fields);
			if (ids.isEmpty()) {
				continue;
			}
			Map<ResourceLocation, Set<Double>> methodRadii = directDeliveryRadii(method, ids);
			if (methodRadii.isEmpty()) {
				DeliveryPath path = networkDeliveryPath(method, methods);
				if (path.reachesTransport()) {
					for (IdReference id : ids) {
						radii.computeIfAbsent(id.id(), ignored -> new HashSet<>()).addAll(path.radii());
					}
				}
				continue;
			}
			for (Map.Entry<ResourceLocation, Set<Double>> entry : methodRadii.entrySet()) {
				radii.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
			}
		}
		return radii;
	}

	private static Map<ResourceLocation, Set<String>> productionEmitterMethods() {
		JavaClasses production = productionClasses();
		Map<String, JavaMethod> methods = methods(production);
		Map<String, ResourceLocation> fields = idFields();
		Map<ResourceLocation, Set<String>> emitters = new HashMap<>();
		for (JavaMethod method : methods.values()) {
			Set<ResourceLocation> ids = referencedIds(method, fields);
			if (ids.isEmpty() || !deliveryPath(method, methods, new HashSet<>()).reachesTransport()) {
				continue;
			}
			for (ResourceLocation id : ids) {
				emitters.computeIfAbsent(id, ignored -> new HashSet<>()).add(method.getFullName());
			}
		}
		return emitters;
	}

	private static JavaClasses productionClasses() {
		String classesPath = System.getProperty("vfx.main.classes", "build/classes/java/main");
		return new ClassFileImporter().importPath(Path.of(classesPath));
	}

	private static Map<String, JavaMethod> methods(JavaClasses production) {
		Map<String, JavaMethod> methods = new HashMap<>();
		for (JavaClass javaClass : production) {
			for (JavaMethod method : javaClass.getMethods()) {
				methods.put(method.getFullName(), method);
			}
		}
		return methods;
	}

	private static Set<ResourceLocation> referencedIds(JavaMethod method, Map<String, ResourceLocation> fields) {
		Set<ResourceLocation> ids = new HashSet<>();
		for (JavaFieldAccess access : method.getFieldAccesses()) {
			ResourceLocation id = fields.get(access.getTargetOwner().getName() + "." + access.getName());
			if (id != null) {
				ids.add(id);
			}
		}
		return ids;
	}

	private static List<IdReference> referencedIdAccesses(JavaMethod method, Map<String, ResourceLocation> fields) {
		List<IdReference> ids = new java.util.ArrayList<>();
		for (JavaFieldAccess access : method.getFieldAccesses()) {
			ResourceLocation id = fields.get(access.getTargetOwner().getName() + "." + access.getName());
			if (id != null) {
				ids.add(new IdReference(id, access.getLineNumber()));
			}
		}
		return ids;
	}

	private static Map<ResourceLocation, Set<Double>> directDeliveryRadii(
			JavaMethod method, List<IdReference> ids) {
		List<JavaCall<?>> networkCalls = method.getCallsFromSelf().stream()
				.filter(call -> call.getTargetOwner().getName().equals("jujutsu.mod.network.JujutsuNetworking"))
				.filter(call -> call.getName().equals("broadcastVfxCue") || call.getName().equals("sendVfxCue"))
				.toList();
		if (networkCalls.isEmpty()) {
			return Map.of();
		}
		Set<Double> fields = deliveryRadiusFields(method);
		Map<ResourceLocation, Set<Double>> radii = new HashMap<>();
		for (IdReference id : ids) {
			JavaCall<?> nearest = networkCalls.stream()
					.min(java.util.Comparator.comparingInt(call -> Math.abs(call.getLineNumber() - id.lineNumber())))
					.orElseThrow();
			radii.put(id.id(), nearest.getName().equals("broadcastVfxCue") ? fields : Set.of());
		}
		return radii;
	}

	private static DeliveryPath networkDeliveryPath(JavaMethod start, Map<String, JavaMethod> methods) {
		Map<String, Set<JavaMethod>> callers = new HashMap<>();
		for (JavaMethod method : methods.values()) {
			for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
				JavaMethod target = resolveTarget(call, methods);
				if (target != null) {
					callers.computeIfAbsent(target.getFullName(), ignored -> new HashSet<>()).add(method);
				}
			}
		}
		DeliveryPath forward = walkNetwork(start, methods, callers, false);
		return forward.reachesTransport() ? forward : walkNetwork(start, methods, callers, true);
	}

	private static DeliveryPath walkNetwork(
			JavaMethod start, Map<String, JavaMethod> methods, Map<String, Set<JavaMethod>> callers, boolean reverse) {
		Set<String> visited = new HashSet<>();
		java.util.ArrayDeque<JavaMethod> pending = new java.util.ArrayDeque<>();
		pending.add(start);
		Set<Double> radii = new HashSet<>();
		boolean reachesTransport = false;
		while (!pending.isEmpty()) {
			JavaMethod method = pending.removeFirst();
			if (!visited.add(method.getFullName())) {
				continue;
			}
			if (hasNetworkCall(method)) {
				reachesTransport = true;
				radii.addAll(deliveryRadiusFields(method));
				continue;
			}
			if (reverse) {
				pending.addAll(callers.getOrDefault(method.getFullName(), Set.of()));
			} else {
				for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
					JavaMethod target = resolveTarget(call, methods);
					if (target != null) {
						pending.addLast(target);
					}
				}
			}
		}
		return reachesTransport ? new DeliveryPath(true, radii) : DeliveryPath.NONE;
	}

	private static JavaMethod resolveTarget(JavaMethodCall call, Map<String, JavaMethod> methods) {
		return call.getTarget().resolveMember()
				.filter(JavaMethod.class::isInstance)
				.map(JavaMethod.class::cast)
				.map(candidate -> methods.get(candidate.getFullName()))
				.orElse(null);
	}

	private static DeliveryPath deliveryPath(
			JavaMethod method, Map<String, JavaMethod> methods, Set<String> visiting) {
		if (!visiting.add(method.getFullName())) {
			return DeliveryPath.NONE;
		}
		Set<Double> radii = deliveryRadiusFields(method);
		boolean reachesTransport = hasTransportCall(method);
		for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
			JavaMethod target = call.getTarget().resolveMember()
					.filter(JavaMethod.class::isInstance)
					.map(JavaMethod.class::cast)
					.map(candidate -> methods.get(candidate.getFullName()))
					.orElse(null);
			if (target == null) {
				continue;
			}
			DeliveryPath child = deliveryPath(target, methods, new HashSet<>(visiting));
			if (child.reachesTransport()) {
				reachesTransport = true;
				radii.addAll(child.radii());
			}
		}
		return reachesTransport ? new DeliveryPath(true, radii) : DeliveryPath.NONE;
	}

	private static boolean hasTransportCall(JavaMethod method) {
		return method.getCallsFromSelf().stream().anyMatch(call -> {
			String owner = call.getTargetOwner().getName();
			return owner.equals("jujutsu.mod.vfx.VfxCue")
					|| owner.equals("jujutsu.mod.vfx.VfxCues")
					|| (owner.equals("jujutsu.mod.network.JujutsuNetworking")
						&& (call.getName().equals("broadcastVfxCue") || call.getName().equals("sendVfxCue")));
		});
	}

	private static boolean hasNetworkCall(JavaMethod method) {
		return method.getCallsFromSelf().stream().anyMatch(call -> {
			String owner = call.getTargetOwner().getName();
			return owner.equals("jujutsu.mod.network.JujutsuNetworking")
					&& (call.getName().equals("broadcastVfxCue") || call.getName().equals("sendVfxCue"));
		});
	}

	private static Set<Double> deliveryRadiusFields(JavaMethod method) {
		Set<Double> radii = new HashSet<>();
		for (JavaFieldAccess access : method.getFieldAccesses()) {
			String fieldName = access.getName();
			if (!fieldName.endsWith("RADIUS")
					|| (!fieldName.contains("VFX") && !fieldName.contains("CUE") && !fieldName.contains("IMPULSE"))) {
				continue;
			}
			try {
				Class<?> owner = Class.forName(access.getTargetOwner().getName());
				Field field = owner.getDeclaredField(fieldName);
				if (!field.trySetAccessible() || !Number.class.isAssignableFrom(field.getType())) {
					continue;
				}
				radii.add(((Number) field.get(null)).doubleValue());
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("Cannot read delivery radius " + access.getDescription(), exception);
			}
		}
		return radii;
	}

	private static Set<ResourceLocation> liveIds() {
		Set<ResourceLocation> ids = new HashSet<>();
		OWNERS.forEach(owner -> ids.addAll(owner.live()));
		return ids;
	}

	private static Set<ResourceLocation> plannedIds() {
		Set<ResourceLocation> ids = new HashSet<>();
		OWNERS.forEach(owner -> ids.addAll(owner.planned()));
		return ids;
	}

	private static int ownerCount(ResourceLocation id) {
		return (int) OWNERS.stream().filter(owner -> owner.live().contains(id) || owner.planned().contains(id)).count();
	}

	private static int countStates(ResourceLocation id) {
		return (int) OWNERS.stream().mapToInt(owner ->
				(owner.live().contains(id) ? 1 : 0) + (owner.planned().contains(id) ? 1 : 0)).sum();
	}

	private static Map<String, ResourceLocation> idFields() {
		Map<String, ResourceLocation> fields = new HashMap<>();
		for (Class<?> owner : List.of(NobaraVfxIds.class, TodoVfxIds.class, MegumiVfxIds.class)) {
			for (Field field : owner.getDeclaredFields()) {
				if (field.getType() == ResourceLocation.class && Modifier.isStatic(field.getModifiers())
						&& Modifier.isPublic(field.getModifiers())) {
					fields.put(owner.getName() + "." + field.getName(), read(field));
				}
			}
		}
		return fields;
	}

	private static ResourceLocation read(Field field) {
		try {
			return (ResourceLocation) field.get(null);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
	}

	private record DeliveryPath(boolean reachesTransport, Set<Double> radii) {
		private static final DeliveryPath NONE = new DeliveryPath(false, Set.of());
	}

	private record IdReference(ResourceLocation id, int lineNumber) {}

	private record Owner(String name, Set<ResourceLocation> live, Set<ResourceLocation> planned) {}
}
