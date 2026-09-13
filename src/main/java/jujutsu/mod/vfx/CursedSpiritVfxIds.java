package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/** Typed VFX ids owned by the cursed-spirit ability slice (Block 3, #86): one cue per ability. */
public final class CursedSpiritVfxIds {
	/** Dash telegraph burst at launch. */
	public static final ResourceLocation DASH = JujutsuMod.id("curse/dash");
	/** Slam shockwave ring at landing. */
	public static final ResourceLocation SLAM = JujutsuMod.id("curse/slam");
	/** Acid glob in flight (launch puff). */
	public static final ResourceLocation ACID_SPIT = JujutsuMod.id("curse/acid_spit");
	/** Acid zone pulse, re-emitted by the zone runtime. */
	public static final ResourceLocation ACID_ZONE = JujutsuMod.id("curse/acid_zone");
	/** Runner grab flash and carry marker. */
	public static final ResourceLocation RUNNER = JujutsuMod.id("curse/runner");
	/** Fear cast shudder on the victim. */
	public static final ResourceLocation FEAR = JujutsuMod.id("curse/fear");
	/** Regen pulse while the HoT runs. */
	public static final ResourceLocation REGEN = JujutsuMod.id("curse/regen");
	/** Armor absorption flash on a blocked hit. */
	public static final ResourceLocation ARMOR = JujutsuMod.id("curse/armor");
	/** Berserk latch flare. */
	public static final ResourceLocation BERSERK = JujutsuMod.id("curse/berserk");

	public static final Set<ResourceLocation> LIVE = Set.of(
			DASH, SLAM, ACID_SPIT, ACID_ZONE, RUNNER, FEAR, REGEN, ARMOR, BERSERK);
	public static final Set<ResourceLocation> PLANNED = Set.of();


	/**
	 * Delivery radius for curse ability cues. Boxed ({@link Double}, not {@code double}) on
	 * purpose: the ArchUnit delivery probe only reads {@link Number} fields.
	 */
	public static final Double VFX_DELIVERY_RADIUS = 48.0;

	private CursedSpiritVfxIds() {}
}
