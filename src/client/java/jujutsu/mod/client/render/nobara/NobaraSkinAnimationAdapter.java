package jujutsu.mod.client.render.nobara;

import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;

/** Per-player presentation state for Nobara's locomotion variants and melee sequence. */
public final class NobaraSkinAnimationAdapter extends CharacterSkinAnimationAdapter<NobaraPlayerGeoAnimatable> {
	private final WeakHashMap<AbstractClientPlayer, SwingState> swingStates = new WeakHashMap<>();

	public NobaraSkinAnimationAdapter() {
		super(NobaraPlayerGeoAnimatable.INSTANCE, new NobaraSkinAnimationModel());
	}

	@Override
	protected void addSkinAnimationData(NobaraPlayerGeoAnimatable animatable, AbstractClientPlayer player,
			GeoRenderState renderState) {
		SwingState state = swingStates.computeIfAbsent(player, ignored -> new SwingState());
		boolean newSwing = player.swinging && (!state.swinging || player.swingTime < state.lastSwingTime);
		if (newSwing) {
			state.variant = Math.floorMod(state.variant + 1, 3);
			String triggerName = switch (Math.floorMod(state.variant, 3)) {
				case 1 -> "attack2";
				case 2 -> "attack3";
				default -> "attack1";
			};
			animatable.restartMeleeTrigger(player, triggerName);
		}
		state.swinging = player.swinging;
		state.lastSwingTime = player.swingTime;
		renderState.addGeckolibData(NobaraPlayerGeoAnimatable.LOCOMOTION_VARIANT,
				Math.floorMod(player.tickCount / 120 + player.getId(), 2));
	}

	private static final class SwingState {
		private boolean swinging;
		private int lastSwingTime = -1;
		private int variant = -1;
	}
}
