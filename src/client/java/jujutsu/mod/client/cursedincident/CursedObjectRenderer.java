package jujutsu.mod.client.cursedincident;

import java.util.function.Consumer;

import jujutsu.mod.cursedincident.object.CursedObjectItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** GeckoLib 5 item renderer; captures the concrete stack for per-type model/texture lookup. */
public final class CursedObjectRenderer extends GeoItemRenderer<CursedObjectItem> {
    public CursedObjectRenderer() {
        super(new CursedObjectGeoModel());
    }

    @Override
    public GeoRenderState captureDefaultRenderState(CursedObjectItem animatable, RenderData renderData,
            GeoRenderState renderState, float partialTick) {
        GeoRenderState captured = super.captureDefaultRenderState(animatable, renderData, renderState, partialTick);
        captured.addGeckolibData(CursedObjectGeoModel.ITEM_STACK, renderData.itemStack());
        return captured;
    }

    /**
     * Per-type scale, applied uniformly in every render context (gui/ground/hand) — the
     * type table owns the number, the renderer only applies it.
     */
    @Override
    public void scaleModelForRender(GeoRenderState renderState, float widthScale, float heightScale,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            software.bernie.geckolib.cache.object.BakedGeoModel model, boolean isReRender) {
        float scale = (float) scaleFor(renderState.getOrDefaultGeckolibData(
                CursedObjectGeoModel.ITEM_STACK, net.minecraft.world.item.ItemStack.EMPTY));
        super.scaleModelForRender(renderState, widthScale * scale, heightScale * scale, poseStack, model, isReRender);
    }

    /** Pure lookup used by the renderer and by tests. */
    public static double scaleFor(net.minecraft.world.item.ItemStack stack) {
        jujutsu.mod.cursedincident.object.CursedObjectState state = CursedObjectItem.state(stack);
        jujutsu.mod.cursedincident.object.CursedObjectType type =
                state == null ? null : jujutsu.mod.cursedincident.object.CursedObjectRegistry.byId(state.typeId());
        return type == null ? 1.0 : type.renderScale();
    }

    /** Called by the client incident bootstrap after the shared VFX director exists. */
    public static void install() {
        CursedObjectItem.setRendererFactory(CursedObjectRenderer::provider);
    }

    public static GeoRenderProvider provider() {
        return new GeoRenderProvider() {
            private CursedObjectRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new CursedObjectRenderer();
                }
                return renderer;
            }
        };
    }
}
