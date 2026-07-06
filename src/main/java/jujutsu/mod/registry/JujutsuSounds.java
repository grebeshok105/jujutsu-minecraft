package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import jujutsu.mod.JujutsuMod;

public final class JujutsuSounds {
	public static final SoundEvent HAIRPIN_PREP = create("hairpin.prep");
	public static final SoundEvent HAIRPIN_HAMMER_SNAP = create("hairpin.hammer_snap");
	public static final SoundEvent HAIRPIN_NAIL_IGNITE = create("hairpin.nail_ignite");
	public static final SoundEvent HAIRPIN_BLOOM = create("hairpin.bloom");
	public static final SoundEvent HAIRPIN_AFTERGLOW = create("hairpin.afterglow");

	private JujutsuSounds() {}

	public static void register() {
		register("hairpin.prep", HAIRPIN_PREP);
		register("hairpin.hammer_snap", HAIRPIN_HAMMER_SNAP);
		register("hairpin.nail_ignite", HAIRPIN_NAIL_IGNITE);
		register("hairpin.bloom", HAIRPIN_BLOOM);
		register("hairpin.afterglow", HAIRPIN_AFTERGLOW);
	}

	private static SoundEvent create(String path) {
		return SoundEvent.createVariableRangeEvent(JujutsuMod.id(path));
	}

	private static void register(String path, SoundEvent soundEvent) {
		Registry.register(BuiltInRegistries.SOUND_EVENT, JujutsuMod.id(path), soundEvent);
	}
}
