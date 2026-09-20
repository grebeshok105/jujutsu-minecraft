package jujutsu.mod.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker bridge to {@link LivingEntityRenderer#addLayer}: mixin @Shadow resolution does not
 * search superclasses, so a mixin targeting {@code PlayerRenderer} cannot shadow the method
 * declared on {@code LivingEntityRenderer}. The invoker lives on the declaring class instead.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
	@Invoker("addLayer")
	boolean jujutsumod$invokeAddLayer(RenderLayer<?, ?> layer);
}
