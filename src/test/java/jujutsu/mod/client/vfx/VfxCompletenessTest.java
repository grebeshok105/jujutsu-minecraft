package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.client.character.megumi.vfx.MegumiVfxRecipes;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.TodoVfxIds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		VfxDirector.register(NobaraVfxIds.HAMMER, cue -> null);
		assertThrows(IllegalStateException.class, () -> VfxDirector.register(NobaraVfxIds.HAMMER, cue -> null));
	}

	@Test
	void plannedIdsRemainVisibleWithoutBeingRequired() {
		Set<ResourceLocation> planned = plannedIds();
		assertTrue(liveIds().stream().noneMatch(planned::contains));
		assertTrue(planned.stream().allMatch(id -> ownerCount(id) == 1));
	}

	@Test
	void compiledProductionEmittersCoverEveryLiveId() {
		JavaClasses production = new ClassFileImporter().importPath(Path.of("build/classes/java/main"));
		Map<ResourceLocation, Set<String>> emitters = new HashMap<>();
		Map<String, ResourceLocation> fields = idFields();

		for (JavaClass javaClass : production) {
			if (javaClass.getName().endsWith("VfxIds")) {
				continue;
			}
			boolean transportPath = javaClass.getCodeUnitCallsFromSelf().stream().anyMatch(call -> {
				String owner = call.getTargetOwner().getName();
				return owner.equals("jujutsu.mod.vfx.VfxCue")
						|| owner.equals("jujutsu.mod.vfx.VfxCues")
						|| (owner.equals("jujutsu.mod.network.JujutsuNetworking")
							&& (call.getName().equals("broadcastVfxCue") || call.getName().equals("sendVfxCue")));
			});
			if (!transportPath) {
				continue;
			}
			for (JavaFieldAccess access : javaClass.getFieldAccessesFromSelf()) {
				ResourceLocation id = fields.get(access.getTargetOwner().getName() + "." + access.getName());
				if (id != null) {
					emitters.computeIfAbsent(id, ignored -> new HashSet<>()).add(javaClass.getName());
				}
			}
		}

		assertEquals(liveIds(), emitters.keySet(), "production emitter coverage");
		assertTrue(emitters.values().stream().allMatch(value -> !value.isEmpty()));
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

	private record Owner(String name, Set<ResourceLocation> live, Set<ResourceLocation> planned) {}
}
