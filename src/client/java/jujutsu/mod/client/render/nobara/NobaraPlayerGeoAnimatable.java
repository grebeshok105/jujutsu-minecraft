package jujutsu.mod.client.render.nobara;

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

public final class NobaraPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final NobaraPlayerGeoAnimatable INSTANCE = new NobaraPlayerGeoAnimatable();
	public static final DataTicket<Integer> MELEE_VARIANT = DataTicket.create("nobara_melee_variant", Integer.class);
	public static final DataTicket<Integer> LOCOMOTION_VARIANT = DataTicket.create("nobara_locomotion_variant", Integer.class);
	private static final String BASE_CONTROLLER = "nobara_player_base";
	private static final String ACTION_CONTROLLER = "nobara_combat_actions";
	private static final float WALK_ANIMATION_THRESHOLD = 0.035f;
	private static final double WALK_VELOCITY_THRESHOLD_SQR = 0.0016;
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.018;
	private static final RawAnimation IDLE = loop("animation.player_model.idle");
	private static final RawAnimation WALK = loop("animation.player_model.walk");
	private static final RawAnimation RUN = loop("animation.player_model.run");
	private static final RawAnimation IDLE_2 = loop("animation.player_model.idle2");
	private static final RawAnimation WALK_2 = loop("animation.player_model.walk2");
	private static final RawAnimation ATTACK_1 = play("animation.player_model.attack1");
	private static final RawAnimation ATTACK_2 = play("animation.player_model.attack2");
	private static final RawAnimation ATTACK_3 = play("animation.player_model.attack3");
	private static final RawAnimation SNAP = play("animation.player_model.snap");
	private static final RawAnimation SPELL_1 = play("animation.player_model.spell1");
	private static final RawAnimation SPELL_3 = play("animation.player_model.spell3");
	private static final RawAnimation HAMMER_HORIZONTAL = play("animation.player_model.hammer_horizontal");
	private static final RawAnimation HAMMER_OVERHEAD = play("animation.player_model.hammer_overhead");
	private static final RawAnimation HAMMER_NAIL_LAUNCH = play("animation.player_model.hammer_nail_launch");
	private static final RawAnimation HAMMER_EMBEDDED_DRIVE = play("animation.player_model.hammer_embedded_drive");
	private static final RawAnimation HAMMER_DOLL_STRIKE = play("animation.player_model.hammer_doll_strike");
	private static final RawAnimation SELF_RESONANCE = play("animation.player_model.self_resonance");
	private static final RawAnimation BLACK_FLASH = play("animation.player_model.black_flash");
	private static final RawAnimation NAIL_PREPARE = play("animation.player_model.nail_prepare");
	private static final RawAnimation NAIL_TRAP_PLACE = play("animation.player_model.nail_trap_place");
	private static final RawAnimation HAIRPIN_ACTIVATE = play("animation.player_model.hairpin_activate");
	private static final RawAnimation MEGA_NAIL_SETUP = play("animation.player_model.mega_nail_setup");
	private static final RawAnimation MEGA_NAIL_CHARGE = play("animation.player_model.mega_nail_charge");
	private static final RawAnimation MEGA_NAIL_RELEASE = play("animation.player_model.mega_nail_release");
	private static final RawAnimation REMNANT_EXTRACT = play("animation.player_model.remnant_extract");
	private static final RawAnimation RESONANCE_RITUAL = play("animation.player_model.resonance_ritual");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private NobaraPlayerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	public void triggerAction(net.minecraft.world.entity.Entity player, String animation) {
		triggerAnim(player, CharacterSkinAnimationAdapter.playerTriggerInstanceId(player), ACTION_CONTROLLER, animation);
	}

	public void restartMeleeTrigger(net.minecraft.world.entity.Entity player, String triggerName) {
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
		controllers.add(new AnimationController<NobaraPlayerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<NobaraPlayerGeoAnimatable>(ACTION_CONTROLLER, 1, state -> PlayState.STOP)
				.triggerableAnim("attack1", ATTACK_1)
				.triggerableAnim("attack2", ATTACK_2)
				.triggerableAnim("attack3", ATTACK_3)
				.triggerableAnim("snap", SNAP)
				.triggerableAnim("spell1", SPELL_1)
				.triggerableAnim("spell3", SPELL_3)
				.triggerableAnim("hammer_horizontal", HAMMER_HORIZONTAL)
				.triggerableAnim("hammer_overhead", HAMMER_OVERHEAD)
				.triggerableAnim("hammer_nail_launch", HAMMER_NAIL_LAUNCH)
				.triggerableAnim("hammer_embedded_drive", HAMMER_EMBEDDED_DRIVE)
				.triggerableAnim("hammer_doll_strike", HAMMER_DOLL_STRIKE)
				.triggerableAnim("self_resonance", SELF_RESONANCE)
				.triggerableAnim("black_flash", BLACK_FLASH)
				.triggerableAnim("nail_prepare", NAIL_PREPARE)
				.triggerableAnim("nail_trap_place", NAIL_TRAP_PLACE)
				.triggerableAnim("hairpin_activate", HAIRPIN_ACTIVATE)
				.triggerableAnim("mega_nail_setup", MEGA_NAIL_SETUP)
				.triggerableAnim("mega_nail_charge", MEGA_NAIL_CHARGE)
				.triggerableAnim("mega_nail_release", MEGA_NAIL_RELEASE)
				.triggerableAnim("remnant_extract", REMNANT_EXTRACT)
				.triggerableAnim("resonance_ritual", RESONANCE_RITUAL));
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
		AnimationController<NobaraPlayerGeoAnimatable> actionController = state.manager().getAnimationControllers().get(ACTION_CONTROLLER);
		return controller != null && (headKeyframedAction(controller.getTriggeredAnimation()) || headKeyframedAction(controller.getCurrentRawAnimation()))
				|| actionController != null && (headKeyframedAction(actionController.getTriggeredAnimation()) || headKeyframedAction(actionController.getCurrentRawAnimation()));
	}

	private static boolean headKeyframedAction(RawAnimation animation) {
		return animation == ATTACK_1 || animation == ATTACK_2 || animation == ATTACK_3
				|| animation == SNAP || animation == SPELL_1 || animation == SPELL_3
				|| animation == HAMMER_HORIZONTAL || animation == HAMMER_OVERHEAD || animation == HAMMER_NAIL_LAUNCH
				|| animation == HAMMER_EMBEDDED_DRIVE || animation == HAMMER_DOLL_STRIKE || animation == SELF_RESONANCE
				|| animation == BLACK_FLASH || animation == NAIL_PREPARE || animation == NAIL_TRAP_PLACE
				|| animation == HAIRPIN_ACTIVATE || animation == MEGA_NAIL_SETUP || animation == MEGA_NAIL_CHARGE
				|| animation == MEGA_NAIL_RELEASE || animation == REMNANT_EXTRACT || animation == RESONANCE_RITUAL;
	}

	private record Movement(boolean moving, boolean running) {}
}
