package jujutsu.mod.vfx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VfxFactoryMigrationContractTest {
	private static final List<String> MIGRATED_RUNTIME_CLASSES = List.of(
			"jujutsu.mod.character.megumi.MegumiSummonRuntime",
			"jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime",
			"jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime",
			"jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraRuntime",
			"jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime",
			"jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime",
			"jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime",
			"jujutsu.mod.character.todo.TodoBlackFlashRuntime",
			"jujutsu.mod.character.todo.TodoBoogieWoogieRuntime",
			"jujutsu.mod.character.todo.TodoFakeClapRuntime",
			"jujutsu.mod.character.todo.TodoPairSwapRuntime",
			"jujutsu.mod.character.todo.TodoSwapMomentumRuntime");

	@Test
	void migratedRuntimesUseTransportFactories() {
		JavaClasses production = productionClasses();
		for (String className : MIGRATED_RUNTIME_CLASSES) {
			JavaClass runtime = production.get(className);
			assertTrue(production.stream()
					.filter(type -> type.getName().equals(className) || type.getName().startsWith(className + "$"))
					.flatMap(type -> type.getMethodCallsFromSelf().stream())
					.anyMatch(call -> call.getTargetOwner().getName().equals(VfxCues.class.getName())),
					"missing VfxCues call in " + className);

			for (JavaClass codeOwner : production.stream()
					.filter(type -> type.getName().equals(className) || type.getName().startsWith(className + "$"))
					.toList()) {
				for (JavaMethod method : codeOwner.getMethods()) {
					for (JavaConstructorCall call : method.getConstructorCallsFromSelf()) {
						if (!call.getTargetOwner().getName().equals(VfxCue.class.getName())) {
							continue;
						}
						assertTrue(isAllowedOverloadedTodoConstructor(className, method),
								() -> "direct generic construction remains in " + method.getFullName());
					}
				}
			}
		}
	}

	@Test
	void overloadedTodoPayloadsRemainExplicitAndLocal() {
		JavaClass runtime = productionClasses().get("jujutsu.mod.character.todo.TodoBoogieWoogieRuntime");
		assertTrue(hasVfxCueConstructor(runtime, "broadcastAfterimage"));
		assertTrue(hasVfxCueConstructor(runtime, "broadcastArrival"));
		assertFalse(hasVfxCueConstructor(runtime, "emitClapPerformance"));
		assertFalse(hasVfxCueConstructor(runtime, "broadcastSwapEndpoint"));
	}

	private static boolean isAllowedOverloadedTodoConstructor(String className, JavaMethod method) {
		return className.equals("jujutsu.mod.character.todo.TodoBoogieWoogieRuntime")
				&& Set.of("broadcastAfterimage", "broadcastArrival").contains(method.getName());
	}

	private static boolean hasVfxCueConstructor(JavaClass runtime, String methodName) {
		return runtime.getMethods().stream()
				.filter(method -> method.getName().equals(methodName))
				.flatMap(method -> method.getConstructorCallsFromSelf().stream())
				.anyMatch(call -> call.getTargetOwner().getName().equals(VfxCue.class.getName()));
	}

	private static JavaClasses productionClasses() {
		Path output = Path.of(System.getProperty("vfx.main.classes", "build/classes/java/main"));
		return new ClassFileImporter().importPath(output);
	}
}
