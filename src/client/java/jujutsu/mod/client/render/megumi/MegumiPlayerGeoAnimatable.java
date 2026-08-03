package jujutsu.mod.client.render.megumi;

import java.util.Arrays;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
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
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class MegumiPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiPlayerGeoAnimatable INSTANCE = new MegumiPlayerGeoAnimatable();
	public static final DataTicket<Integer> MELEE_VARIANT = DataTicket.create("megumi_melee_variant", Integer.class);
	public static final DataTicket<Boolean> COMBAT_IDLE = DataTicket.create("megumi_combat_idle", Boolean.class);
	public static final int MELEE_VARIANT_COUNT = 3;
	private static final String BASE_CONTROLLER = "megumi_player_base";
	private static final String ACTION_CONTROLLER = "megumi_actions";
	private static final String SUMMON_ANIM = "summon_divine_dogs";
	private static final float WALK_ANIMATION_THRESHOLD = 0.035f;
	private static final double WALK_VELOCITY_THRESHOLD_SQR = 0.0016;
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.018;
	private static final RawAnimation IDLE = loop("animation.megumi_fushiguro.idle");
	private static final RawAnimation WALK = loop("animation.megumi_fushiguro.walk");
	private static final RawAnimation RUN = loop("animation.megumi_fushiguro.run");
	private static final RawAnimation COMBAT_IDLE_ANIMATION = loop("animation.megumi_fushiguro.combat_idle");
	private static final RawAnimation[] MELEE = {
			play("animation.megumi_fushiguro.punch_1"),
			play("animation.megumi_fushiguro.punch_2"),
			play("animation.megumi_fushiguro.kick")
	};
	private static final RawAnimation SUMMON = play("animation.megumi_fushiguro.summon_divine_dogs");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiPlayerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	public void triggerSummon(Entity player) {
		triggerAnim(player, ACTION_CONTROLLER, SUMMON_ANIM);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiPlayerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiPlayerGeoAnimatable>(ACTION_CONTROLLER, 1, state -> PlayState.STOP)
				.triggerableAnim(SUMMON_ANIM, SUMMON));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiPlayerGeoAnimatable> state) {
		GeoRenderState renderState = state.renderState();
		if (renderState instanceof PlayerRenderState playerState
				&& (playerState.swinging || playerState.attackTime > 0.05f)) {
			int variant = renderState.getOrDefaultGeckolibData(MELEE_VARIANT, 0);
			return state.setAndContinue(MELEE[Math.floorMod(variant, MELEE.length)]);
		}
		if (Boolean.TRUE.equals(renderState.getOrDefaultGeckolibData(COMBAT_IDLE, false))) {
			return state.setAndContinue(COMBAT_IDLE_ANIMATION);
		}
		Movement movement = movement(state, renderState);
		if (!movement.moving()) {
			return state.setAndContinue(IDLE);
		}
		return state.setAndContinue(movement.running() ? RUN : WALK);
	}

	private static Movement movement(AnimationTest<MegumiPlayerGeoAnimatable> state, GeoRenderState renderState) {
		float walkSpeed = renderState instanceof PlayerRenderState playerState ? playerState.walkAnimationSpeed : 0.0f;
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
		boolean moving = state.isMoving() || walkSpeed > WALK_ANIMATION_THRESHOLD
				|| horizontalSpeedSqr > WALK_VELOCITY_THRESHOLD_SQR;
		boolean running = moving && (Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.SPRINTING, false))
				|| walkSpeed > 0.82f || horizontalSpeedSqr > RUN_VELOCITY_THRESHOLD_SQR);
		return new Movement(moving, running);
	}

	static boolean actionKeyframedIsPlaying(AnimationState<MegumiPlayerGeoAnimatable> state) {
		AnimationController<MegumiPlayerGeoAnimatable> base = state.manager().getAnimationControllers().get(BASE_CONTROLLER);
		AnimationController<MegumiPlayerGeoAnimatable> action = state.manager().getAnimationControllers().get(ACTION_CONTROLLER);
		return isMelee(base == null ? null : base.getCurrentRawAnimation())
				|| isMelee(base == null ? null : base.getTriggeredAnimation())
				|| isCombatIdle(base == null ? null : base.getCurrentRawAnimation())
				|| isCombatIdle(base == null ? null : base.getTriggeredAnimation())
				|| isSummon(action == null ? null : action.getCurrentRawAnimation())
				|| isSummon(action == null ? null : action.getTriggeredAnimation());
	}

	private static boolean isMelee(RawAnimation animation) {
		return Arrays.stream(MELEE).anyMatch(candidate -> candidate == animation);
	}

	private static boolean isCombatIdle(RawAnimation animation) {
		return animation == COMBAT_IDLE_ANIMATION;
	}

	private static boolean isSummon(RawAnimation animation) {
		return animation == SUMMON;
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}

	private record Movement(boolean moving, boolean running) {}
}
