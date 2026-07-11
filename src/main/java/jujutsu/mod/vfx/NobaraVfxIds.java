package jujutsu.mod.vfx;

import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

public final class NobaraVfxIds {
	public static final ResourceLocation HAMMER = id("hammer");
	public static final ResourceLocation IMPACT = id("impact");
	public static final ResourceLocation IMPACT_SOUND = id("impact_sound");
	public static final ResourceLocation RESONANCE_CHANNEL = id("resonance_channel");
	public static final ResourceLocation RESONANCE_STRIKE = id("resonance_strike");
	public static final ResourceLocation LINK_BIND = id("link_bind");
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
	public static final ResourceLocation EMBEDDED_NAIL_DRIVE = id("embedded_nail_drive");
	public static final ResourceLocation BLACK_FLASH = id("black_flash");
	public static final ResourceLocation SELF_RESONANCE = id("self_resonance");
	public static final ResourceLocation NAIL_DEEPEN = id("nail_deepen");
	public static final ResourceLocation NAIL_TRAP_PLACED = id("nail_trap_placed");
	public static final ResourceLocation NAIL_TRAP_ARMED = id("nail_trap_armed");
	public static final ResourceLocation NAIL_TRAP_COLLAPSE = id("nail_trap_collapse");
	public static final ResourceLocation NAIL_TRAP_IMPACT = id("nail_trap_impact");
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
