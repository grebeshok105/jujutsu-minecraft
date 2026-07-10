package jujutsu.mod.client.render.nobara.doll;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class ProjectJjkStrawDollModel extends GeoModel<ProjectJjkStrawDollItem> {
	private static final ResourceLocation MODEL = JujutsuMod.id("straw_doll");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/item/straw_doll.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("straw_doll");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(ProjectJjkStrawDollItem animatable) {
		return ANIMATIONS;
	}
}
