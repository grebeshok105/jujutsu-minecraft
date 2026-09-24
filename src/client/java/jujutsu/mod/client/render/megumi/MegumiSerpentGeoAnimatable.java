package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import jujutsu.mod.registry.JujutsuEntities;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Client-side singleton animatable for the imported Great Serpent model. */
public final class MegumiSerpentGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiSerpentGeoAnimatable INSTANCE = new MegumiSerpentGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_serpent_base";
	private static final String ACTION_CONTROLLER = "megumi_serpent_action";
	private static final RawAnimation IDLE = loop("animation.megumi_serpent.idle");
	private static final RawAnimation SLITHER = loop("animation.megumi_serpent.slither");
	private static final RawAnimation AMBUSH = play("animation.megumi_serpent.ambush");
	private static final RawAnimation BIND = play("animation.megumi_serpent.bind");
	private static final RawAnimation RELEASE = play("animation.megumi_serpent.release");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiSerpentGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	public void triggerAction(Entity serpent, String action) {
		triggerAnim(serpent, serpent.getId(), ACTION_CONTROLLER, action);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_SERPENT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiSerpentGeoAnimatable>(BASE_CONTROLLER, 4,
				this::baseAnimation));
		controllers.add(new AnimationController<MegumiSerpentGeoAnimatable>(ACTION_CONTROLLER, 2,
				state -> PlayState.STOP)
					.triggerableAnim("ambush", AMBUSH)
					.triggerableAnim("bind", BIND)
					.triggerableAnim("release", RELEASE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiSerpentGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState serpent)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiSerpentAnimationPolicy.Clip clip = MegumiSerpentAnimationPolicy.serpent(
				serpent.phase, moving, serpent.actionActive);
		if (clip == MegumiSerpentAnimationPolicy.Clip.RISE
				|| clip == MegumiSerpentAnimationPolicy.Clip.SLITHER) {
			return state.setAndContinue(SLITHER);
		}
		if (clip == MegumiSerpentAnimationPolicy.Clip.IDLE) {
			return state.setAndContinue(IDLE);
		}
		return PlayState.STOP;
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}
}
