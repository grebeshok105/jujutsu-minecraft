package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.MethodVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.Test;

// VfxDirector.RECIPES is process-global; serialize tests that reset and populate it.
@ResourceLock("VfxDirector.RECIPES")
@Execution(ExecutionMode.SAME_THREAD)
final class VfxDurationContractTest {
	@BeforeEach
	void clearBefore() {
		VfxDirector.resetRecipesForTest();
	}

	@AfterEach
	void clearAfter() {
		VfxDirector.resetRecipesForTest();
	}

	@Test
	void equalLifetimeUsesOneNamedValueForRecipeAndRetainedWorldState() {
		NobaraVfxRecipes.register();
		VfxCue cue = new VfxCue(jujutsu.mod.vfx.NobaraVfxIds.HAMMER, Vec3.ZERO, VfxCue.NO_ANCHOR,
				Vec3.ZERO, 1, 0L, 1L, Vec3.ZERO);

		int hammerDuration = VfxDirector.recipeForTest(jujutsu.mod.vfx.NobaraVfxIds.HAMMER).create(cue).durationTicks();
		assertEquals(NobaraVfxRecipes.HAMMER_DURATION_TICKS, hammerDuration);
		assertTrue(hammerDuration > 0);
		DurationSnapshot hammer = readDurations("hammer");
		assertEquals(hammer.recipeDuration(), hammer.worldImpactDurations().get(0),
				"hammer recipe and retained world impact must share one duration operand");
	}

	@Test
	void blackFlashKeepsIntentionalLongRecipeAndShortRetainedImpact() {
		DurationSnapshot blackFlash = readDurations("blackFlash");
		assertTrue(blackFlash.recipeDuration() > blackFlash.worldImpactDurations().get(0),
				"Black Flash recipe lifetime must outlive its retained world impact");
	}

	private static DurationSnapshot readDurations(String methodName) {
		String resource = "/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.class";
		try (InputStream stream = VfxDurationContractTest.class.getResourceAsStream(resource)) {
			assertNotNull(stream, "compiled Nobara recipe class must be available");
			DurationVisitor visitor = new DurationVisitor(methodName);
			new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return visitor.snapshot();
		} catch (IOException exception) {
			throw new AssertionError("cannot read compiled Nobara recipes", exception);
		}
	}

	private record DurationSnapshot(int recipeDuration, List<Integer> worldImpactDurations) {}

	private static final class DurationVisitor extends ClassVisitor {
		private final String methodName;
		private Integer recipeDuration;
		private final List<Integer> worldImpactDurations = new ArrayList<>();

		private DurationVisitor(String methodName) {
			super(Opcodes.ASM9);
			this.methodName = methodName;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (!name.equals(methodName) && !name.startsWith("lambda$" + methodName + "$")) {
				return null;
			}
			return new MethodVisitor(api) {
				private Integer lastInt;

				@Override
				public void visitInsn(int opcode) {
					lastInt = switch (opcode) {
						case Opcodes.ICONST_M1 -> -1;
						case Opcodes.ICONST_0 -> 0;
						case Opcodes.ICONST_1 -> 1;
						case Opcodes.ICONST_2 -> 2;
						case Opcodes.ICONST_3 -> 3;
						case Opcodes.ICONST_4 -> 4;
						case Opcodes.ICONST_5 -> 5;
						default -> null;
					};
				}

				@Override
				public void visitIntInsn(int opcode, int operand) {
					lastInt = opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ? operand : null;
				}

				@Override
				public void visitLdcInsn(Object value) {
					lastInt = value instanceof Integer integer ? integer : null;
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
					if (owner.equals("jujutsu/mod/client/vfx/VfxInstance") && name.equals("of")) {
						recipeDuration = lastInt;
					} else if (owner.equals("jujutsu/mod/client/vfx/VfxWorldChannel") && name.equals("triggerImpact")) {
						if (lastInt != null) {
							worldImpactDurations.add(lastInt);
						}
					}
					lastInt = null;
				}
			};
		}

		private DurationSnapshot snapshot() {
			assertNotNull(recipeDuration, "recipe duration operand must be discoverable");
			assertEquals(1, worldImpactDurations.size(), "one retained world impact is expected");
			return new DurationSnapshot(recipeDuration, List.copyOf(worldImpactDurations));
		}
	}
}
