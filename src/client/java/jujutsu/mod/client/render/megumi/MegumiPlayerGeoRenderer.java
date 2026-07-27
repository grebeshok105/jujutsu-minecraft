package jujutsu.mod.client.render.megumi;

import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterPlayerGeoRenderer;

public final class MegumiPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterPlayerGeoRenderer<MegumiPlayerGeoAnimatable, R> {
	private final WeakHashMap<AbstractClientPlayer, SwingState> swingStates = new WeakHashMap<>();

	public MegumiPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new MegumiPlayerGeoModel(), MegumiPlayerGeoAnimatable.INSTANCE);
		addRenderLayer(new MegumiHeldItemLayer<>(this));
		withScale(1.0f, 1.0f);
	}

	@Override
	public void addRenderData(MegumiPlayerGeoAnimatable animatable, AbstractClientPlayer player, R renderState) {
		super.addRenderData(animatable, player, renderState);
		SwingState state = swingStates.computeIfAbsent(player, ignored -> new SwingState());
		boolean newSwing = player.swinging
				&& (!state.swinging || player.swingTime < state.lastSwingTime);
		if (newSwing) {
			state.variant = (state.variant + 1) % MegumiPlayerGeoAnimatable.MELEE_VARIANT_COUNT;
		}
		state.swinging = player.swinging;
		state.lastSwingTime = player.swingTime;
		renderState.addGeckolibData(MegumiPlayerGeoAnimatable.MELEE_VARIANT, Math.max(0, state.variant));
	}

	private static final class SwingState {
		private boolean swinging;
		private int lastSwingTime = -1;
		private int variant = -1;
	}
}
