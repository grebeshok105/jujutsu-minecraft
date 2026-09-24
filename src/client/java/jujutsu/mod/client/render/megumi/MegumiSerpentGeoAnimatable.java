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

/** Client-side animatable driving the Great Serpent render. */
public final class MegumiSerpentGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiSerpentGeoAnimatable INSTANCE = new MegumiSerpentGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_serpent_base";
	private static final String ACTION_CONTROLLER = "megumi_serpent_action";
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_serpent.idle");
	private static final RawAnimation SLITHER = loop("animation.megumi_serpent.slither");
	private static final RawAnimation SUBMERGE = play("animation.megumi_serpent.submerge");
	private static final RawAnimation SUBMERGED_IDLE = loop("animation.megumi_serpent.submerged_idle");
	private static final RawAnimation EMERGE = play("animation.megumi_serpent.emerge");
	private static final RawAnimation BIND = loop("animation.megumi_serpent.bind");
	private static final RawAnimation RELEASE = play("animation.megumi_serpent.release");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiSerpentGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_SERPENT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiSerpentGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiSerpentGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
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
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.serpent(
				serpent.phase, moving, serpent.actionIndex);
		return state.setAndContinue(switch (clip) {
			case RISE -> SLITHER;
			case SINK -> IDLE;
			case ACTION -> SLITHER;
			default -> moving ? SLITHER : IDLE;
		});
	}

	/**
	 * The action layer owns the coil sequence: 1=submerge, 2=emerge, 3=bind, 4=release,
	 * 5=submerged_idle. The serpent stays visible while submerged — a sunken shadow form whose
	 * loop is this clip, with the sink offset applied in the renderer.
	 */
	private PlayState actionAnimation(AnimationTest<MegumiSerpentGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState serpent)) {
			return PlayState.STOP;
		}
		RawAnimation action = switch (serpent.actionIndex) {
			case 1 -> SUBMERGE;
			case 2 -> EMERGE;
			case 3 -> BIND;
			case 4 -> RELEASE;
			case 5 -> SUBMERGED_IDLE;
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
