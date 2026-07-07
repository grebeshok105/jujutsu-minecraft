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
	public static final SoundEvent PROJECTJJK_SNAP = create("projectjjk.snap");
	public static final SoundEvent PROJECTJJK_SPELL_SHOT = create("projectjjk.spell_shot");
	public static final SoundEvent PROJECTJJK_WHOOSH_HIT = create("projectjjk.whoosh_hit");
	public static final SoundEvent PROJECTJJK_CINEMATIC_WHOOSH = create("projectjjk.cinematic_whoosh");
	public static final SoundEvent PROJECTJJK_EXPLODE = create("projectjjk.explode");
	public static final SoundEvent PROJECTJJK_IMPLODE = create("projectjjk.implode");
	public static final SoundEvent PROJECTJJK_DEEP_EXPLOSION = create("projectjjk.deep_explosion");
	public static final SoundEvent PROJECTJJK_BLACK_FLASH_IMPACT = create("projectjjk.black_flash_impact");
	public static final SoundEvent PROJECTJJK_BLACK_FLASH_IMPACT_2 = create("projectjjk.black_flash_impact2");
	public static final SoundEvent PROJECTJJK_GOO_FOLEY = create("projectjjk.goo_foley");

	private JujutsuSounds() {}

	public static void register() {
		register("hairpin.prep", HAIRPIN_PREP);
		register("hairpin.hammer_snap", HAIRPIN_HAMMER_SNAP);
		register("hairpin.nail_ignite", HAIRPIN_NAIL_IGNITE);
		register("hairpin.bloom", HAIRPIN_BLOOM);
		register("hairpin.afterglow", HAIRPIN_AFTERGLOW);
		register("projectjjk.snap", PROJECTJJK_SNAP);
		register("projectjjk.spell_shot", PROJECTJJK_SPELL_SHOT);
		register("projectjjk.whoosh_hit", PROJECTJJK_WHOOSH_HIT);
		register("projectjjk.cinematic_whoosh", PROJECTJJK_CINEMATIC_WHOOSH);
		register("projectjjk.explode", PROJECTJJK_EXPLODE);
		register("projectjjk.implode", PROJECTJJK_IMPLODE);
		register("projectjjk.deep_explosion", PROJECTJJK_DEEP_EXPLOSION);
		register("projectjjk.black_flash_impact", PROJECTJJK_BLACK_FLASH_IMPACT);
		register("projectjjk.black_flash_impact2", PROJECTJJK_BLACK_FLASH_IMPACT_2);
		register("projectjjk.goo_foley", PROJECTJJK_GOO_FOLEY);
	}

	private static SoundEvent create(String path) {
		return SoundEvent.createVariableRangeEvent(JujutsuMod.id(path));
	}

	private static void register(String path, SoundEvent soundEvent) {
		Registry.register(BuiltInRegistries.SOUND_EVENT, JujutsuMod.id(path), soundEvent);
	}
}
