package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
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

/** Client-side animatable driving the Piercing Ox render. */
public final class MegumiOxGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiOxGeoAnimatable INSTANCE = new MegumiOxGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_ox_base";
	private static final String ACTION_CONTROLLER = "megumi_ox_action";
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_ox.idle");
	private static final RawAnimation WALK = loop("animation.megumi_ox.walk");
	private static final RawAnimation RUN = loop("animation.megumi_ox.run");
	private static final RawAnimation WINDUP = play("animation.megumi_ox.windup");
	private static final RawAnimation CHARGE = loop("animation.megumi_ox.charge");
	private static final RawAnimation IMPACT = play("animation.megumi_ox.impact");
	private static final RawAnimation RECOVER = play("animation.megumi_ox.recover");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiOxGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_OX;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiOxGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiOxGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiOxGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState ox)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		boolean running = MegumiShikigamiAnimationPolicy.isRunning(velocity.x, velocity.z);
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.ox(
				ox.phase, moving, running, ox.actionIndex);
		return state.setAndContinue(switch (clip) {
			case RISE -> WALK;
			case SINK -> IDLE;
			case RUN -> RUN;
			case ACTION -> RUN;
			default -> moving ? WALK : IDLE;
		});
	}

	/** The action layer owns the committed sequence: 1=windup, 2=charge, 3=impact, 4=recover. */
	private PlayState actionAnimation(AnimationTest<MegumiOxGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState ox)) {
			return PlayState.STOP;
		}
		RawAnimation action = switch (ox.actionIndex) {
			case 1 -> WINDUP;
			case 2 -> CHARGE;
			case 3 -> IMPACT;
			case 4 -> RECOVER;
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
