package jujutsu.mod.client.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/**
 * Shared seam that lets a vessel attach a render layer to vanilla's player renderer without the
 * mixin naming the vessel's layer type. A vessel registers a factory from its client hooks; the
 * shared {@code PlayerRendererWingsMixin} asks this registry for every layer to add.
 */
public final class PlayerRenderLayerRegistry {
	private static final List<Function<RenderLayerParent<PlayerRenderState, PlayerModel>,
			RenderLayer<PlayerRenderState, PlayerModel>>> FACTORIES = new CopyOnWriteArrayList<>();

	private PlayerRenderLayerRegistry() {}

	public static void register(
			Function<RenderLayerParent<PlayerRenderState, PlayerModel>,
					RenderLayer<PlayerRenderState, PlayerModel>> factory) {
		FACTORIES.add(factory);
	}

	public static List<RenderLayer<PlayerRenderState, PlayerModel>> createLayers(
			RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
		return FACTORIES.stream().map(factory -> factory.apply(parent)).toList();
	}
}
