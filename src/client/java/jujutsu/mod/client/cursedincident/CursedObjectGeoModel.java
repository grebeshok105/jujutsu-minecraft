package jujutsu.mod.client.cursedincident;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.cursedincident.object.CursedObjectItem;
import jujutsu.mod.cursedincident.object.CursedObjectRegistry;
import jujutsu.mod.cursedincident.object.CursedObjectState;
import jujutsu.mod.cursedincident.object.CursedObjectType;
import jujutsu.mod.registry.JujutsuDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Resolves the type model and one of its deterministic texture looks per item stack. */
public final class CursedObjectGeoModel extends GeoModel<CursedObjectItem> {
    public static final DataTicket<ItemStack> ITEM_STACK =
            DataTicket.create("jujutsumod_cursed_object_stack", ItemStack.class);
    private static final String FALLBACK_MODEL = "cursed_object_cursed_doll";

    @Override
    public ResourceLocation getModelResource(GeoRenderState renderState) {
        CursedObjectType type = renderableType(typeFrom(renderState));
        return JujutsuMod.id(type == null ? FALLBACK_MODEL : type.geoModel());
    }

    @Override
    public ResourceLocation getTextureResource(GeoRenderState renderState) {
        CursedObjectState state = stateFrom(renderState);
        CursedObjectType type = renderableType(state == null ? null : CursedObjectRegistry.byId(state.typeId()));
        if (type == null) {
            return JujutsuMod.id("textures/item/cursed_object_cursed_doll_0.png");
        }
        int variant = state.instanceId().hashCode() & 1;
        return JujutsuMod.id("textures/item/" + type.textureResourceId(variant) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(CursedObjectItem animatable) {
        return JujutsuMod.id("cursed_object");
    }

    private static CursedObjectType typeFrom(GeoRenderState renderState) {
        CursedObjectState state = stateFrom(renderState);
        return state == null ? null : CursedObjectRegistry.byId(state.typeId());
    }

    private static CursedObjectState stateFrom(GeoRenderState renderState) {
        ItemStack stack = renderState.getOrDefaultGeckolibData(ITEM_STACK, ItemStack.EMPTY);
        return stack.isEmpty() ? null : stack.get(JujutsuDataComponents.CURSED_OBJECT_STATE);
    }

    private static CursedObjectType renderableType(CursedObjectType type) {
        return type == null || type == CursedObjectRegistry.QA_PROBE ? null : type;
    }
}
