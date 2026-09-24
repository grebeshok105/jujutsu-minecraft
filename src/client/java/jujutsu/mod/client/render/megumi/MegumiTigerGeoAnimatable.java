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

/** Client-side animatable driving the Tiger Funeral render. */
public final class MegumiTigerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiTigerGeoAnimatable INSTANCE = new MegumiTigerGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_tiger_base";
	private static final String ACTION_CONTROLLER = "megumi_tiger_action";
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_tiger.idle");
	private static final RawAnimation WALK = loop("animation.megumi_tiger.walk");
	private static final RawAnimation STALK = loop("animation.megumi_tiger.stalk");
	private static final RawAnimation COMBO_WINDUP = play("animation.megumi_tiger.combo_windup");
	private static final RawAnimation STRIKE_1 = play("animation.megumi_tiger.strike_1");
	private static final RawAnimation STRIKE_2 = play("animation.megumi_tiger.strike_2");
	private static final RawAnimation FINISHER = play("animation.megumi_tiger.finisher");
	private static final RawAnimation RECOVER = play("animation.megumi_tiger.recover");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiTigerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_TIGER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiTigerGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiTigerGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiTigerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState tiger)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.tiger(
				tiger.phase, moving, tiger.actionIndex);
		return state.setAndContinue(switch (clip) {
			case RISE -> WALK;
			case SINK -> IDLE;
			case ACTION -> STALK;
			default -> moving ? WALK : IDLE;
		});
	}

	/** The action layer owns the whole combo: 1=windup, 2/3/4=strikes, 5=recover. */
	private PlayState actionAnimation(AnimationTest<MegumiTigerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState tiger)) {
			return PlayState.STOP;
		}
		RawAnimation action = switch (tiger.actionIndex) {
			case 1 -> COMBO_WINDUP;
			case 2 -> STRIKE_1;
			case 3 -> STRIKE_2;
			case 4 -> FINISHER;
			case 5 -> RECOVER;
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
