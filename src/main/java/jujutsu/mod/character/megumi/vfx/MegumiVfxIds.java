package jujutsu.mod.character.megumi.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/** Typed VFX ids owned by Megumi's Divine Dogs slice. */
public final class MegumiVfxIds {
	public static final ResourceLocation DOGS_SUMMON_BODY = JujutsuMod.id("megumi/dogs_summon_body");
	public static final ResourceLocation DOGS_SUMMON = JujutsuMod.id("megumi/dogs_summon");
	public static final ResourceLocation DOGS_RECALL = JujutsuMod.id("megumi/dogs_recall");
	public static final ResourceLocation DOGS_SIC = JujutsuMod.id("megumi/dogs_sic");
	public static final ResourceLocation DOGS_POUNCE = JujutsuMod.id("megumi/dogs_pounce");
	public static final Set<ResourceLocation> LIVE = Set.of(
			DOGS_SUMMON_BODY, DOGS_SUMMON, DOGS_RECALL, DOGS_SIC, DOGS_POUNCE);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	private MegumiVfxIds() {}
}
