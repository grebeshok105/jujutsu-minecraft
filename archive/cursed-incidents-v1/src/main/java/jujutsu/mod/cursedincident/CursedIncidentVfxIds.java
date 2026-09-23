package jujutsu.mod.cursedincident;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

/** VFX identifiers owned by the cursed-incident world slice. */
public final class CursedIncidentVfxIds {
	public static final ResourceLocation ZONE_AMBIENT = JujutsuMod.id("incident/zone_ambient");
	public static final ResourceLocation STAGE_PULSE = JujutsuMod.id("incident/stage_pulse");
	public static final ResourceLocation SEAL_APPLIED = JujutsuMod.id("incident/seal_applied");
	public static final ResourceLocation SEAL_DEGRADE = JujutsuMod.id("incident/seal_degrade");
	public static final ResourceLocation SEAL_BREAK = JujutsuMod.id("incident/seal_break");
	public static final ResourceLocation SECONDARY_BIRTH = JujutsuMod.id("incident/secondary_birth");

	public static final Set<ResourceLocation> LIVE = Set.of(
			ZONE_AMBIENT, STAGE_PULSE, SEAL_APPLIED, SEAL_DEGRADE, SEAL_BREAK, SECONDARY_BIRTH);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	/** Physical signs are visible to all nearby players, including non-mages. */
	public static final Set<ResourceLocation> PHYSICAL =
			Set.of(STAGE_PULSE, SEAL_APPLIED, SEAL_BREAK, SECONDARY_BIRTH);
	/** Curse-derived ambience is filtered through the perception predicate. */
	public static final Set<ResourceLocation> CURSE = Set.of(ZONE_AMBIENT, SEAL_DEGRADE);

	public static final Double VFX_DELIVERY_RADIUS = 64.0;

	private CursedIncidentVfxIds() {
	}
}
