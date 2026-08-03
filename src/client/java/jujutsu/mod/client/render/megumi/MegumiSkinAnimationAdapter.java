package jujutsu.mod.client.render.megumi;

import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;

/** Megumi's per-player melee clip sequence, moved out of the retired visible Geo renderer. */
public final class MegumiSkinAnimationAdapter extends CharacterSkinAnimationAdapter<MegumiPlayerGeoAnimatable> {
	private final WeakHashMap<AbstractClientPlayer, SwingState> swingStates = new WeakHashMap<>();

	public MegumiSkinAnimationAdapter() {
		super(MegumiPlayerGeoAnimatable.INSTANCE, new MegumiSkinAnimationModel());
	}

	@Override
	protected void addSkinAnimationData(MegumiPlayerGeoAnimatable animatable, AbstractClientPlayer player,
			GeoRenderState renderState) {
		SwingState state = swingStates.computeIfAbsent(player, ignored -> new SwingState());
		boolean newSwing = player.swinging && (!state.swinging || player.swingTime < state.lastSwingTime);
		if (newSwing) {
			state.variant = (state.variant + 1) % MegumiPlayerGeoAnimatable.MELEE_VARIANT_COUNT;
			state.combatIdleUntilTick = player.tickCount + 22;
		}
		state.swinging = player.swinging;
		state.lastSwingTime = player.swingTime;
		renderState.addGeckolibData(MegumiPlayerGeoAnimatable.MELEE_VARIANT, Math.max(0, state.variant));
		renderState.addGeckolibData(MegumiPlayerGeoAnimatable.COMBAT_IDLE,
				!player.swinging && player.tickCount <= state.combatIdleUntilTick);
	}

	private static final class SwingState {
		private boolean swinging;
		private int lastSwingTime = -1;
		private int variant = -1;
		private int combatIdleUntilTick = -1;
	}
}
