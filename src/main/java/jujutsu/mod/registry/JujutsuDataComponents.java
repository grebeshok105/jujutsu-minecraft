package jujutsu.mod.registry;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkResonanceRemnant;
import jujutsu.mod.character.nobara.projectjjk.RemnantVisualType;
import jujutsu.mod.cursedincident.object.CursedObjectState;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class JujutsuDataComponents {
	public static final DataComponentType<CursedObjectState> CURSED_OBJECT_STATE = DataComponentType
			.<CursedObjectState>builder()
			.persistent(CursedObjectState.CODEC)
			.networkSynchronized(CursedObjectState.STREAM_CODEC)
			.build();
	public static final DataComponentType<ProjectJjkResonanceRemnant> RESONANCE_TARGET = DataComponentType
			.<ProjectJjkResonanceRemnant>builder()
			.persistent(ProjectJjkResonanceRemnant.CODEC)
			.networkSynchronized(ProjectJjkResonanceRemnant.STREAM_CODEC)
			.build();
	public static final DataComponentType<RemnantVisualType> RESONANCE_REMNANT_VISUAL = DataComponentType
			.<RemnantVisualType>builder()
			.persistent(RemnantVisualType.CODEC)
			.networkSynchronized(RemnantVisualType.STREAM_CODEC)
			.build();

	private JujutsuDataComponents() {}

	public static void register() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JujutsuMod.id("resonance_target"), RESONANCE_TARGET);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JujutsuMod.id("resonance_remnant_visual"), RESONANCE_REMNANT_VISUAL);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JujutsuMod.id("cursed_object_state"), CURSED_OBJECT_STATE);
	}
}
