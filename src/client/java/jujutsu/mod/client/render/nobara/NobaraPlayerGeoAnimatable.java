package jujutsu.mod.client.render.nobara;

import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

public final class NobaraPlayerGeoAnimatable implements GeoReplacedEntity {
	public static final NobaraPlayerGeoAnimatable INSTANCE = new NobaraPlayerGeoAnimatable();
	private static final String BASE_CONTROLLER = "nobara_player_base";
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

	private PlayState baseAnimation(AnimationTest<NobaraPlayerGeoAnimatable> state) {
		GeoRenderState renderState = state.renderState();
		if (renderState instanceof PlayerRenderState playerState) {
			if (playerState.swinging || playerState.attackTime > 0.05f) {
				return state.setAndContinue(ATTACK_1);
			}
			if (playerState.speedValue > 0.82f || playerState.walkAnimationSpeed > 0.82f) {
				return state.setAndContinue(RUN);
			}
			if (playerState.walkAnimationSpeed > 0.035f || playerState.speedValue > 0.035f) {
				return state.setAndContinue(WALK);
			}
		}
		return state.setAndContinue(IDLE);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}
}
