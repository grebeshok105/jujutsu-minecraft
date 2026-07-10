package jujutsu.mod.client.render.nobara;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
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
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class NobaraPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final NobaraPlayerGeoAnimatable INSTANCE = new NobaraPlayerGeoAnimatable();
	private static final String BASE_CONTROLLER = "nobara_player_base";
	private static final float WALK_ANIMATION_THRESHOLD = 0.035f;
	private static final double WALK_VELOCITY_THRESHOLD_SQR = 0.0016;
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.018;
	private static final RawAnimation IDLE = loop("animation.player_model.idle");
	private static final RawAnimation WALK = loop("animation.player_model.walk");
	private static final RawAnimation RUN = loop("animation.player_model.run");
	private static final RawAnimation IDLE_2 = play("animation.player_model.idle2");
	private static final RawAnimation WALK_2 = play("animation.player_model.walk2");
	private static final RawAnimation ONE_TWO = play("animation.player_model.one_two");
	private static final RawAnimation ATTACK_1 = play("animation.player_model.attack1");
	private static final RawAnimation ATTACK_2 = play("animation.player_model.attack2");
	private static final RawAnimation ATTACK_3 = play("animation.player_model.attack3");
	private static final RawAnimation SNAP = play("animation.player_model.snap");
	private static final RawAnimation SPELL_1 = play("animation.player_model.spell1");
	private static final RawAnimation SPELL_2 = play("animation.player_model.spell2");
	private static final RawAnimation SPELL_3 = play("animation.player_model.spell3");
	private static final RawAnimation SPELL_4 = play("animation.player_model.spell4");
	private static final RawAnimation SPELL_5 = play("animation.player_model.spell5");
	private static final RawAnimation SWIPE_1 = play("animation.player_model.swipe1");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private NobaraPlayerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<NobaraPlayerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation)
				.triggerableAnim("idle2", IDLE_2)
				.triggerableAnim("walk2", WALK_2)
				.triggerableAnim("one_two", ONE_TWO)
				.triggerableAnim("attack1", ATTACK_1)
				.triggerableAnim("attack2", ATTACK_2)
				.triggerableAnim("attack3", ATTACK_3)
				.triggerableAnim("snap", SNAP)
				.triggerableAnim("spell1", SPELL_1)
				.triggerableAnim("spell2", SPELL_2)
				.triggerableAnim("spell3", SPELL_3)
				.triggerableAnim("spell4", SPELL_4)
				.triggerableAnim("spell5", SPELL_5)
				.triggerableAnim("swipe1", SWIPE_1));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	static float headLookWeight(AnimationState<NobaraPlayerGeoAnimatable> state, PlayerRenderState playerState) {
		float weight = 1.0f;
		if (playerState.swinging || playerState.attackTime > 0.05f || playerState.isUsingItem) {
			weight *= 0.35f;
		}
		if (headKeyframedActionIsPlaying(state)) {
			weight *= 0.25f;
		}
		if (playerState.walkAnimationSpeed > 0.82f) {
			weight *= 0.55f;
		} else if (playerState.walkAnimationSpeed > 0.08f) {
			weight *= 0.72f;
		}
		return weight;
	}

	private PlayState baseAnimation(AnimationTest<NobaraPlayerGeoAnimatable> state) {
		GeoRenderState renderState = state.renderState();
		if (renderState instanceof PlayerRenderState playerState) {
			if (playerState.swinging || playerState.attackTime > 0.05f) {
				return state.setAndContinue(ATTACK_1);
			}
		}
		Movement movement = movement(state, renderState);
		if (!movement.moving()) {
			return state.setAndContinue(IDLE);
		}
		if (movement.running()) {
			return state.setAndContinue(RUN);
		}
		return state.setAndContinue(WALK);
	}

	private static Movement movement(AnimationTest<NobaraPlayerGeoAnimatable> state, GeoRenderState renderState) {
		float walkSpeed = renderState instanceof PlayerRenderState playerState ? playerState.walkAnimationSpeed : 0.0f;
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
		boolean sprinting = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.SPRINTING, false));
		boolean moving = state.isMoving() || walkSpeed > WALK_ANIMATION_THRESHOLD || horizontalSpeedSqr > WALK_VELOCITY_THRESHOLD_SQR;
		boolean running = moving && (sprinting || walkSpeed > 0.82f || horizontalSpeedSqr > RUN_VELOCITY_THRESHOLD_SQR);
		return new Movement(moving, running);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}

	static boolean headKeyframedActionIsPlaying(AnimationState<NobaraPlayerGeoAnimatable> state) {
		AnimationController<NobaraPlayerGeoAnimatable> controller = state.manager().getAnimationControllers().get(BASE_CONTROLLER);
		return controller != null && (headKeyframedAction(controller.getTriggeredAnimation())
				|| headKeyframedAction(controller.getCurrentRawAnimation()));
	}

	private static boolean headKeyframedAction(RawAnimation animation) {
		return animation == ONE_TWO || animation == ATTACK_1 || animation == ATTACK_2 || animation == ATTACK_3
				|| animation == SNAP || animation == SPELL_1 || animation == SPELL_2 || animation == SPELL_3
				|| animation == SPELL_4 || animation == SPELL_5 || animation == SWIPE_1;
	}

	private record Movement(boolean moving, boolean running) {}
}
