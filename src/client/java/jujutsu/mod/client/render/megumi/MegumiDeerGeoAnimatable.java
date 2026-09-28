package jujutsu.mod.client.render.megumi;

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

/** Client-side animatable driving the Round Deer render. */
public final class MegumiDeerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiDeerGeoAnimatable INSTANCE = new MegumiDeerGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_deer_base";
	private static final String ACTION_CONTROLLER = "megumi_deer_action";
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_deer.idle");
	private static final RawAnimation WALK = loop("animation.megumi_deer.walk");
	private static final RawAnimation PULSE = play("animation.megumi_deer.pulse");
	private static final RawAnimation SHOVE = play("animation.megumi_deer.shove");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiDeerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_DEER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiDeerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiDeerGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiDeerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState deer)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.deer(
				deer.phase, moving, deer.actionIndex);
		return state.setAndContinue(switch (clip) {
			case RISE -> WALK;
			case SINK -> IDLE;
			case ACTION -> WALK;
			default -> moving ? WALK : IDLE;
		});
	}

	/** The action layer owns the support beats: 1=heal pulse, 2=antler shove. */
	private PlayState actionAnimation(AnimationTest<MegumiDeerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState deer)) {
			return PlayState.STOP;
		}
		RawAnimation action = switch (deer.actionIndex) {
			case 1 -> PULSE;
			case 2 -> SHOVE;
			default -> null;
		};
		if (action == null) {
			return PlayState.STOP;
		}
		if (!state.isCurrentAnimation(action)) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(action);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}
}
