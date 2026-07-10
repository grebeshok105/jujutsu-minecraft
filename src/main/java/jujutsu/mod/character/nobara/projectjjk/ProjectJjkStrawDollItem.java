package jujutsu.mod.character.nobara.projectjjk;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ProjectJjkStrawDollItem extends Item implements GeoItem {
	private static final String CONTROLLER = "straw_doll";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.straw_doll.idle");
	private static final RawAnimation RITUAL_RAISE = RawAnimation.begin().thenPlay("animation.straw_doll.ritual_raise");
	private static final RawAnimation IMPACT_RELEASE = RawAnimation.begin()
			.thenPlay("animation.straw_doll.impact")
			.thenPlay("animation.straw_doll.release");
	private static final RawAnimation RELEASE = RawAnimation.begin().thenPlay("animation.straw_doll.release");
	private static Supplier<GeoRenderProvider> rendererFactory = () -> GeoRenderProvider.DEFAULT;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public ProjectJjkStrawDollItem(Properties properties) {
		super(properties);
		GeoItem.registerSyncedAnimatable(this);
	}

	public static void setRendererFactory(Supplier<GeoRenderProvider> factory) {
		rendererFactory = Objects.requireNonNull(factory, "factory");
	}

	public void triggerRitual(ServerPlayer player, ItemStack stack) {
		trigger(player, stack, "ritual_raise");
	}

	public void triggerImpact(ServerPlayer player, ItemStack stack) {
		trigger(player, stack, "impact");
	}

	private void trigger(ServerPlayer player, ItemStack stack, String animation) {
		if (player.level() instanceof ServerLevel level && stack.is(this)) {
			triggerAnim(player, GeoItem.getOrAssignId(stack, level), CONTROLLER, animation);
		}
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(rendererFactory.get());
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<ProjectJjkStrawDollItem>(CONTROLLER, 3, state -> state.setAndContinue(IDLE))
				.triggerableAnim("ritual_raise", RITUAL_RAISE)
				.triggerableAnim("impact", IMPACT_RELEASE)
				.triggerableAnim("release", RELEASE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
