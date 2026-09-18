package jujutsu.mod.cursedincident.object;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.policy.SealPolicy;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;
import jujutsu.mod.registry.JujutsuDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.TooltipDisplay;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** One GeoItem implementation shared by every data-driven cursed-object type. */
public final class CursedObjectItem extends Item implements GeoItem {
    public record SealResult(boolean ok, int requiredTier, String reason, CursedObjectState state) {
    }

    private static final String CONTROLLER = "cursed_object";
    private static Supplier<GeoRenderProvider> rendererFactory = () -> GeoRenderProvider.DEFAULT;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CursedObjectItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    public static void setRendererFactory(Supplier<GeoRenderProvider> factory) {
        rendererFactory = Objects.requireNonNull(factory, "factory");
    }

    public static CursedObjectState state(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(JujutsuDataComponents.CURSED_OBJECT_STATE);
    }
    public static CursedObjectState getState(ItemStack stack) {
        return state(stack);
    }

    public static void setState(ItemStack stack, CursedObjectState next) {
        if (stack != null && !stack.isEmpty() && next != null) {
            stack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, next);
            ObjectDwellTracker.refresh(stack);
        }
    }


    public static boolean isCursedObject(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CursedObjectItem
                && state(stack) != null;
    }

    public static ItemStack stack(CursedObjectState state) {
        ItemStack stack = new ItemStack(jujutsu.mod.registry.JujutsuItems.CURSED_OBJECT);
        stack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, state);
        applyTypeComponents(stack);
        return stack;
    }

    /** Adds per-instance components whose values depend on the data-driven object type. */
    public static void applyTypeComponents(ItemStack stack) {
        CursedObjectState state = state(stack);
        CursedObjectType type = state == null ? null : CursedObjectRegistry.byId(state.typeId());
        if (type != null && type.indestructible()) {
            stack.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
        } else if (stack != null && !stack.isEmpty()) {
            stack.remove(DataComponents.DAMAGE_RESISTANT);
        }
    }

    /** Applies a physical talisman directly to the authoritative stack component. */
    public static SealResult trySeal(ItemStack stack, int tier) {
        CursedObjectState current = state(stack);
        if (current == null) {
            return new SealResult(false, 0, "not_cursed_object", null);
        }
        int required = SealPolicy.requiredTier(current.grade());
        if (tier < required) {
            return new SealResult(false, required, "insufficient_tier", current);
        }
        int appliedTier = Math.max(1, Math.min(3, tier));
        CursedObjectState sealed = current.withSeal(true, appliedTier, SealState.integrityMax(appliedTier));
        setState(stack, sealed);
        IncidentControl.syncSealFromComponent(sealed.instanceId());
        return new SealResult(true, required, "sealed", sealed);
    }

    public static boolean seal(ItemStack stack, int tier) {
        return trySeal(stack, tier).ok();
    }

    public static boolean seal(ItemStack stack, SealTier tier) {
        return tier != null && seal(stack, tier.value());
    }

    public static boolean unseal(ItemStack stack) {
        CursedObjectState current = state(stack);
        if (current == null || !current.sealed()) {
            return false;
        }
        CursedObjectState unsealed = current.withSeal(false, current.sealTier(), current.sealIntegrity());
        setState(stack, unsealed);
        IncidentControl.syncSealFromComponent(unsealed.instanceId());
        return true;
    }

    /**
     * Single authoritative seal-damage contract: when the stack is an incident source the
     * damage routes through {@link IncidentControl#damageSeal} (record + write-back +
     * catastrophic roll); standalone stacks apply the same SealPolicy math locally.
     */
    public static CursedObjectState damageSeal(ItemStack stack, int amount) {
        CursedObjectState current = state(stack);
        if (current == null || !current.sealed() || amount <= 0) {
            return current;
        }
        jujutsu.mod.cursedincident.IncidentRecord owner = IncidentControl.recordForObject(current.instanceId());
        if (owner != null) {
            IncidentControl.damageSeal(owner.id, amount);
            CursedObjectState synced = state(stack);
            return synced == null ? current : synced;
        }
        int tier = Math.max(SealPolicy.MIN_TIER, Math.min(SealPolicy.MAX_TIER, current.sealTier()));
        long rollSeed = current.instanceId().getMostSignificantBits() ^ current.sealIntegrity();
        boolean catastrophic = SealPolicy.maybeCatastrophicFail(
                net.minecraft.util.RandomSource.create(rollSeed), tier, current.sealIntegrity());
        int integrity = catastrophic ? 0 : Math.max(0, current.sealIntegrity() - amount);
        CursedObjectState damaged = current.withSeal(integrity > 0, current.sealTier(), integrity);
        setState(stack, damaged);
        IncidentControl.syncSealFromComponent(damaged.instanceId());
        return damaged;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ObjectDisplay.nameFor(state(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        ObjectDisplay.loreFor(state(stack)).forEach(tooltip);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (entity instanceof ServerPlayer player) {
            ObjectDwellTracker.noteCarried(stack, player);
        }
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(rendererFactory.get());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CursedObjectItem>(CONTROLLER, 0,
                state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
