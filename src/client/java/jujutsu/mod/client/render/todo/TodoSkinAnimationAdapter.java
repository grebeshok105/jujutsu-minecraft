package jujutsu.mod.client.render.todo;

import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;

/** Adds Todo's deterministic locomotion variant to the shared skin bridge. */
public final class TodoSkinAnimationAdapter extends CharacterSkinAnimationAdapter<TodoPlayerGeoAnimatable> {
	private final WeakHashMap<AbstractClientPlayer, SwingState> swingStates = new WeakHashMap<>();

	public TodoSkinAnimationAdapter() {
		super(TodoPlayerGeoAnimatable.INSTANCE, new TodoSkinAnimationModel());
	}

	@Override
	protected void addSkinAnimationData(TodoPlayerGeoAnimatable animatable, AbstractClientPlayer player,
			GeoRenderState renderState) {
		SwingState state = swingStates.computeIfAbsent(player, ignored -> new SwingState());
		boolean newSwing = player.swinging && (!state.swinging || player.swingTime < state.lastSwingTime);
		if (newSwing) {
			animatable.triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player),
					"todo_actions", "attack");
		}
		state.swinging = player.swinging;
		state.lastSwingTime = player.swingTime;
		renderState.addGeckolibData(TodoPlayerGeoAnimatable.LOCOMOTION_VARIANT,
				Math.floorMod(player.tickCount / 120 + player.getId(), 2));
	}

	private static final class SwingState {
		private boolean swinging;
		private int lastSwingTime = -1;
	}
}
