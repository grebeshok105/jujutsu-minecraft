package jujutsu.mod.registry;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkResonanceRemnant;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class JujutsuDataComponents {
	public static final DataComponentType<ProjectJjkResonanceRemnant> RESONANCE_TARGET = DataComponentType
			.<ProjectJjkResonanceRemnant>builder()
			.persistent(ProjectJjkResonanceRemnant.CODEC)
			.networkSynchronized(ProjectJjkResonanceRemnant.STREAM_CODEC)
			.build();

	private JujutsuDataComponents() {}

	public static void register() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JujutsuMod.id("resonance_target"), RESONANCE_TARGET);
	}
}
