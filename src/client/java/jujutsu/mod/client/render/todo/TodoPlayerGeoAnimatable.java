package jujutsu.mod.client.render.todo;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** GeckoLib replaced-player animatable for Aoi Todo. */
public final class TodoPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final TodoPlayerGeoAnimatable INSTANCE = new TodoPlayerGeoAnimatable();
	public static final String BOOGIE_WOOGIE_ANIM = "boogie_woogie";
	public static final DataTicket<Integer> LOCOMOTION_VARIANT = DataTicket.create("todo_locomotion_variant", Integer.class);
	private static final String BASE_CONTROLLER = "todo_player_base";
	private static final String ACTION_CONTROLLER = "todo_actions";
	private static final float WALK_ANIMATION_THRESHOLD = 0.035f;
	private static final double WALK_VELOCITY_THRESHOLD_SQR = 0.0016;
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.018;
	private static final RawAnimation IDLE = loop("animation.todo_aoi.idle");
	private static final RawAnimation IDLE_2 = loop("animation.todo_aoi.idle2");
	private static final RawAnimation WALK = loop("animation.todo_aoi.walk");
	private static final RawAnimation WALK_2 = loop("animation.todo_aoi.walk2");
	private static final RawAnimation RUN = loop("animation.todo_aoi.run");
	private static final RawAnimation ATTACK = play("animation.todo_aoi.attack");
	private static final RawAnimation BOOGIE_WOOGIE = play("ability.boogie_woogie");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private TodoPlayerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	public void triggerAction(net.minecraft.world.entity.Entity player, String animation) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, animation);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<TodoPlayerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<TodoPlayerGeoAnimatable>(ACTION_CONTROLLER, 1, state -> PlayState.STOP)
				.triggerableAnim("attack", ATTACK)
				.triggerableAnim(BOOGIE_WOOGIE_ANIM, BOOGIE_WOOGIE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	static float headLookWeight(AnimationState<TodoPlayerGeoAnimatable> state, PlayerRenderState playerState) {
		// Keep head look readable; clap keys no longer drive head, so only mild damp during action.
		float weight = 1.0f;
		if (playerState.swinging || playerState.attackTime > 0.05f || playerState.isUsingItem) {
			weight *= 0.55f;
		}
		if (actionKeyframedIsPlaying(state)) {
			weight *= 0.65f;
		}
		if (playerState.walkAnimationSpeed > 0.82f) {
			weight *= 0.7f;
		} else if (playerState.walkAnimationSpeed > 0.08f) {
			weight *= 0.85f;
		}
		return weight;
	}

	private PlayState baseAnimation(AnimationTest<TodoPlayerGeoAnimatable> state) {
		GeoRenderState renderState = state.renderState();
		Movement movement = movement(state, renderState);
		boolean alternate = Math.floorMod(renderState.getOrDefaultGeckolibData(LOCOMOTION_VARIANT, 0), 2) == 1;
		if (!movement.moving()) {
			return state.setAndContinue(alternate ? IDLE_2 : IDLE);
		}
		if (movement.running()) {
			return state.setAndContinue(RUN);
		}
		return state.setAndContinue(alternate ? WALK_2 : WALK);
	}

	private static Movement movement(AnimationTest<TodoPlayerGeoAnimatable> state, GeoRenderState renderState) {
		float walkSpeed = renderState instanceof PlayerRenderState playerState ? playerState.walkAnimationSpeed : 0.0f;
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
		boolean moving = state.isMoving() || walkSpeed > WALK_ANIMATION_THRESHOLD || horizontalSpeedSqr > WALK_VELOCITY_THRESHOLD_SQR;
		boolean running = moving && (Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.SPRINTING, false))
				|| walkSpeed > 0.82f
				|| horizontalSpeedSqr > RUN_VELOCITY_THRESHOLD_SQR);
		return new Movement(moving, running);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}

	static boolean actionKeyframedIsPlaying(AnimationState<TodoPlayerGeoAnimatable> state) {
		AnimationController<TodoPlayerGeoAnimatable> base = state.manager().getAnimationControllers().get(BASE_CONTROLLER);
		AnimationController<TodoPlayerGeoAnimatable> action = state.manager().getAnimationControllers().get(ACTION_CONTROLLER);
		return isActionClip(base) || isActionClip(action);
	}

	private static boolean isActionClip(AnimationController<TodoPlayerGeoAnimatable> controller) {
		if (controller == null) {
			return false;
		}
		return isActionClip(controller.getTriggeredAnimation()) || isActionClip(controller.getCurrentRawAnimation());
	}

	private static boolean isActionClip(RawAnimation animation) {
		return animation == ATTACK || animation == BOOGIE_WOOGIE;
	}

	private record Movement(boolean moving, boolean running) {}
}
