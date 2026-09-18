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
