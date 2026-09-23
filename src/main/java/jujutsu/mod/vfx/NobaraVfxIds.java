package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

public final class NobaraVfxIds {
	public static final ResourceLocation HAMMER = id("hammer");
	public static final ResourceLocation IMPACT = id("impact");
	public static final ResourceLocation IMPACT_SOUND = id("impact_sound");
	public static final ResourceLocation DETONATE = id("detonate");
	public static final ResourceLocation FIRST_PERSON_SNAP = id("first_person_snap");
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
	public static final ResourceLocation MEGA_NAIL_CHARGE = id("mega_nail_charge");
	public static final ResourceLocation DEEPLY_ANCHORED = id("deeply_anchored");
	public static final ResourceLocation REMNANT_EXTRACT = id("remnant_extract");
	public static final ResourceLocation RITUAL_WINDUP = id("ritual_windup");
	public static final ResourceLocation RESONANCE_LINK = id("resonance_link");
	public static final ResourceLocation MEGA_GATHER = id("mega_gather");
	public static final ResourceLocation HAIRPIN_LINK = id("hairpin_link");
	public static final int CASTER_HAIRPIN_DIRECTED = 1;
	public static final int CASTER_NAIL_PREPARE = 2;
	public static final int CASTER_NAIL_TRAP = 3;
	public static final int CASTER_HAMMER_EMBEDDED = 4;
	public static final int CASTER_MEGA_NAIL = 5;
	public static final int CASTER_REMNANT_EXTRACT = 6;
	public static final int CASTER_RESONANCE_RITUAL = 7;
	public static final Set<ResourceLocation> LIVE = Set.of(
			HAMMER, IMPACT, IMPACT_SOUND, DETONATE, FIRST_PERSON_SNAP,
			RITUAL_BIND, DOLL_STRIKE, RESONANCE_RELEASE, HAMMER_HORIZONTAL, HAMMER_OVERHEAD, HAMMER_NAIL_LAUNCH,
			BLACK_FLASH, SELF_RESONANCE, NAIL_DEEPEN, NAIL_TRAP_PLACED, NAIL_TRAP_ARMED, NAIL_TRAP_COLLAPSE,
			NAIL_TRAP_IMPACT, CASTER_ACTION, MEGA_NAIL_STRIKE, MEGA_NAIL_CHARGE, DEEPLY_ANCHORED,
			REMNANT_EXTRACT, RITUAL_WINDUP, RESONANCE_LINK, MEGA_GATHER, HAIRPIN_LINK);

	public static final Set<ResourceLocation> PLANNED = Set.of();

	private NobaraVfxIds() {}

	private static ResourceLocation id(String path) {
		return JujutsuMod.id("nobara/" + path);
	}
}
