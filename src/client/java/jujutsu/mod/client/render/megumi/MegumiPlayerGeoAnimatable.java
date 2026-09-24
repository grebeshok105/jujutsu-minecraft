package jujutsu.mod.client.render.megumi;

import java.util.Arrays;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
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

public final class MegumiPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiPlayerGeoAnimatable INSTANCE = new MegumiPlayerGeoAnimatable();
	public static final DataTicket<Integer> MELEE_VARIANT = DataTicket.create("megumi_melee_variant", Integer.class);
	public static final DataTicket<Boolean> COMBAT_IDLE = DataTicket.create("megumi_combat_idle", Boolean.class);
	public static final int MELEE_VARIANT_COUNT = 3;
	private static final String BASE_CONTROLLER = "megumi_player_base";
	private static final String ACTION_CONTROLLER = "megumi_actions";
	private static final String SUMMON_ANIM = "summon_divine_dogs";
	private static final String SUMMON_SERPENT_ANIM = "summon_serpent";
	private static final String SUMMON_DEER_ANIM = "summon_deer";
	private static final String SUMMON_OX_ANIM = "summon_ox";
	private static final String SUMMON_TIGER_ANIM = "summon_tiger";
	private static final String SHADOW_DIVE_ANIM = "shadow_dive";
	private static final String SHADOW_EMERGE_ANIM = "shadow_emerge";
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
	private static final RawAnimation SUMMON_SERPENT = play("animation.megumi_fushiguro.summon_serpent");
	private static final RawAnimation SUMMON_DEER = play("animation.megumi_fushiguro.summon_deer");
	private static final RawAnimation SUMMON_OX = play("animation.megumi_fushiguro.summon_ox");
	private static final RawAnimation SUMMON_TIGER = play("animation.megumi_fushiguro.summon_tiger");
	private static final RawAnimation SHADOW_DIVE = play("animation.megumi_fushiguro.shadow_dive");
	private static final RawAnimation SHADOW_EMERGE = play("animation.megumi_fushiguro.shadow_emerge");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiPlayerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	public void triggerSummon(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SUMMON_ANIM);
	}

	public void triggerSummonSerpent(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SUMMON_SERPENT_ANIM);
	}

	public void triggerSummonDeer(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SUMMON_DEER_ANIM);
	}

	public void triggerSummonOx(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SUMMON_OX_ANIM);
	}

	public void triggerSummonTiger(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SUMMON_TIGER_ANIM);
	}

	public void triggerShadowDive(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SHADOW_DIVE_ANIM);
	}

	public void triggerShadowEmerge(Entity player) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, SHADOW_EMERGE_ANIM);
	}


	public void restartMeleeTrigger(Entity player, String triggerName) {
		long instanceId = CharacterSkinAnimationAdapter.playerTriggerInstanceId(player);
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(instanceId);
		if (manager != null) {
			AnimationController<?> controller = manager.getAnimationControllers().get(ACTION_CONTROLLER);
			if (controller != null) {
				controller.forceAnimationReset();
				controller.tryTriggerAnimation(triggerName);
			}
		}
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiPlayerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiPlayerGeoAnimatable>(ACTION_CONTROLLER, 1, state -> PlayState.STOP)
				.triggerableAnim("punch_1", MELEE[0])
				.triggerableAnim("punch_2", MELEE[1])
				.triggerableAnim("kick", MELEE[2])
				.triggerableAnim(SUMMON_ANIM, SUMMON)
				.triggerableAnim(SUMMON_SERPENT_ANIM, SUMMON_SERPENT)
				.triggerableAnim(SUMMON_DEER_ANIM, SUMMON_DEER)
				.triggerableAnim(SUMMON_OX_ANIM, SUMMON_OX)
				.triggerableAnim(SUMMON_TIGER_ANIM, SUMMON_TIGER)
				.triggerableAnim(SHADOW_DIVE_ANIM, SHADOW_DIVE)
				.triggerableAnim(SHADOW_EMERGE_ANIM, SHADOW_EMERGE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiPlayerGeoAnimatable> state) {
		GeoRenderState renderState = state.renderState();
		Movement movement = movement(state, renderState);
		if (Boolean.TRUE.equals(renderState.getOrDefaultGeckolibData(COMBAT_IDLE, false))
				&& !movement.moving()) {
			return state.setAndContinue(COMBAT_IDLE_ANIMATION);
		}
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
		AnimationController<MegumiPlayerGeoAnimatable> action = state.manager().getAnimationControllers().get(ACTION_CONTROLLER);
		AnimationController<MegumiPlayerGeoAnimatable> base = state.manager().getAnimationControllers().get(BASE_CONTROLLER);
		return isMelee(action == null ? null : action.getCurrentRawAnimation())
				|| isMelee(action == null ? null : action.getTriggeredAnimation())
				|| isCombatIdle(base == null ? null : base.getCurrentRawAnimation())
				|| isCombatIdle(base == null ? null : base.getTriggeredAnimation())
				|| isSummon(action == null ? null : action.getCurrentRawAnimation())
				|| isSummon(action == null ? null : action.getTriggeredAnimation())
				|| isShadowDive(action == null ? null : action.getCurrentRawAnimation())
				|| isShadowDive(action == null ? null : action.getTriggeredAnimation())
				|| isShadowEmerge(action == null ? null : action.getCurrentRawAnimation())
				|| isShadowEmerge(action == null ? null : action.getTriggeredAnimation());
	}

	private static boolean isMelee(RawAnimation animation) {
		return Arrays.stream(MELEE).anyMatch(candidate -> candidate == animation);
	}

	private static boolean isCombatIdle(RawAnimation animation) {
		return animation == COMBAT_IDLE_ANIMATION;
	}

	private static boolean isSummon(RawAnimation animation) {
		return animation == SUMMON || animation == SUMMON_SERPENT || animation == SUMMON_DEER
				|| animation == SUMMON_OX || animation == SUMMON_TIGER;
	}

	private static boolean isShadowDive(RawAnimation animation) {
		return animation == SHADOW_DIVE;
	}

	private static boolean isShadowEmerge(RawAnimation animation) {
		return animation == SHADOW_EMERGE;
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}

	private record Movement(boolean moving, boolean running) {}
}
