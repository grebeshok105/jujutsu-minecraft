package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

public final class NobaraVfxIds {
	public static final ResourceLocation HAMMER = id("hammer");
	public static final ResourceLocation IMPACT = id("impact");
	public static final ResourceLocation IMPACT_SOUND = id("impact_sound");
	public static final ResourceLocation DETONATE = id("detonate");
	public static final ResourceLocation ENLARGE = id("enlarge");
	public static final ResourceLocation EXPLOSION = id("explosion");
	public static final ResourceLocation FIRST_PERSON_SNAP = id("first_person_snap");
	public static final ResourceLocation REMNANT_DROP = id("remnant_drop");
	public static final ResourceLocation RITUAL_BIND = id("ritual_bind");
	public static final ResourceLocation DOLL_STRIKE = id("doll_strike");
	public static final ResourceLocation RESONANCE_RELEASE = id("resonance_release");
	public static final ResourceLocation HAMMER_HORIZONTAL = id("hammer_horizontal");
	public static final ResourceLocation HAMMER_OVERHEAD = id("hammer_overhead");
	public static final ResourceLocation HAMMER_NAIL_LAUNCH = id("hammer_nail_launch");
	public static final ResourceLocation BLACK_FLASH = id("black_flash");
	public static final ResourceLocation SELF_RESONANCE = id("self_resonance");
	public static final ResourceLocation NAIL_DEEPEN = id("nail_deepen");
	public static final ResourceLocation NAIL_TRAP_PLACED = id("nail_trap_placed");
	public static final ResourceLocation NAIL_TRAP_ARMED = id("nail_trap_armed");
	public static final ResourceLocation NAIL_TRAP_COLLAPSE = id("nail_trap_collapse");
	public static final ResourceLocation NAIL_TRAP_IMPACT = id("nail_trap_impact");
	/** Server-confirmed caster-only presentation anchor for abilities whose world cue is target-fixed. */
	public static final ResourceLocation CASTER_ACTION = id("caster_action");
	public static final ResourceLocation MEGA_NAIL_STRIKE = id("mega_nail_strike");
	public static final int CASTER_HAIRPIN_DIRECTED = 1;
	public static final int CASTER_NAIL_TRAP = 3;
	public static final int CASTER_HAMMER_EMBEDDED = 4;
	public static final int CASTER_MEGA_NAIL = 5;
	public static final Set<ResourceLocation> LIVE = Set.of(
			HAMMER, IMPACT, IMPACT_SOUND, DETONATE, ENLARGE, EXPLOSION, FIRST_PERSON_SNAP, REMNANT_DROP,
			RITUAL_BIND, DOLL_STRIKE, RESONANCE_RELEASE, HAMMER_HORIZONTAL, HAMMER_OVERHEAD, HAMMER_NAIL_LAUNCH,
			BLACK_FLASH, SELF_RESONANCE, NAIL_DEEPEN, NAIL_TRAP_PLACED, NAIL_TRAP_ARMED, NAIL_TRAP_COLLAPSE,
		NAIL_TRAP_IMPACT, CASTER_ACTION, MEGA_NAIL_STRIKE);
	public static final Set<ResourceLocation> PLANNED = Set.of();
	private static final int HAIRPIN_FINALE_FLAG = 8;

	private NobaraVfxIds() {}

	public static int hairpinExplosionIntensity(int depth, boolean finale) {
		return Math.max(1, Math.min(3, depth)) | (finale ? HAIRPIN_FINALE_FLAG : 0);
	}

	public static int hairpinExplosionDepth(int intensity) { return Math.max(1, Math.min(3, intensity & 7)); }
	public static boolean isHairpinFinale(int intensity) { return (intensity & HAIRPIN_FINALE_FLAG) != 0; }

	private static ResourceLocation id(String path) {
		return JujutsuMod.id("nobara/" + path);
	}
}
