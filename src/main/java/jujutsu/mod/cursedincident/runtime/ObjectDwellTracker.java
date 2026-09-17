package jujutsu.mod.cursedincident.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jujutsu.mod.cursedincident.DwellProvider;
import jujutsu.mod.cursedincident.object.CursedObjectItem;
import jujutsu.mod.cursedincident.object.CursedObjectRegistry;
import jujutsu.mod.cursedincident.object.CursedObjectState;
import jujutsu.mod.cursedincident.object.CursedObjectType;
import jujutsu.mod.cursedincident.object.SealState;
import jujutsu.mod.registry.JujutsuDataComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks where unsealed cursed-object instances dwell.  The map is deliberately runtime-only;
 * the instance component and incident SavedData carry persistence, while this tracker is rebuilt
 * from loaded item entities and player/container observations.
 */
public final class ObjectDwellTracker implements DwellProvider {
    public static final long DEFAULT_DWELL_TICKS = 24_000L;
    public static final long POSITION_POLL_PERIOD = 100L;

    private static final Map<UUID, TrackedObject> TRACKED = new ConcurrentHashMap<>();
    private static volatile boolean hooksRegistered;
    private static volatile long lastObservedGameTime;

    private static final class TrackedObject {
        final UUID instanceId;
        CursedObjectState state;
        CursedObjectType type;
        BlockPos anchor;
        BlockPos lastPosition;
        BlockPos dwellCenter;
        long accumulatedTicks;
        long lastGameTime;
        long lastDecayGameTime;
        ItemStack stack;
        ItemEntity entity;
        ServerLevel level;
        boolean awaitingReload;
        boolean dead;

        TrackedObject(CursedObjectState state) {
            this.instanceId = state.instanceId();
            this.state = state;
            this.type = CursedObjectRegistry.byId(state.typeId());
            this.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
            this.lastDecayGameTime = state.mintedGameTime();
        }
    }

    public static void registerServerHooks() {
        if (hooksRegistered) {
            return;
        }
        hooksRegistered = true;
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item) {
                noteWorldItem(item);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item) {
                onUnload(item);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ObjectDwellTracker::serverTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static void noteCarried(ItemStack stack, ServerPlayer player) {
        if (player != null && player.level() instanceof ServerLevel level) {
            CursedObjectState state = state(stack);
            if (state != null) {
                lastObservedGameTime = Math.max(lastObservedGameTime, level.getGameTime());
                TrackedObject tracked = observe(state, stack, level, player.blockPosition(), level.getGameTime(), null);
                tracked.entity = null;
                tracked.awaitingReload = true;
            }
        }
    }

    public static void noteWorldItem(ItemEntity item) {
        if (item == null || !(item.level() instanceof ServerLevel level)) {
            return;
        }
        CursedObjectState state = state(item.getItem());
        if (state == null) {
            return;
        }
        item.setUnlimitedLifetime();
        item.setNoPickUpDelay();
        lastObservedGameTime = Math.max(lastObservedGameTime, level.getGameTime());
        TrackedObject tracked = observe(state, item.getItem(), level, item.blockPosition(), level.getGameTime(), item);
        tracked.entity = item;
        tracked.awaitingReload = false;
        tracked.dead = false;
    }

    /** Called by the infection sink for a physical block-entity container. */
    @Override
    public void noteContainer(BlockPos containerPos, ItemStack stack) {
        CursedObjectState state = state(stack);
        if (state == null || containerPos == null) {
            return;
        }
        TrackedObject prior = TRACKED.get(state.instanceId());
        long now = Math.max(lastObservedGameTime, prior == null ? 0L : prior.lastGameTime + 1L);
        TrackedObject tracked = observe(state, stack, prior == null ? null : prior.level,
                containerPos, now, null);
        tracked.entity = null;
        tracked.awaitingReload = true;
    }

    @Override
    public BlockPos dwellCenterOf(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        if (tracked == null || tracked.dead || tracked.state.sealed()) {
            return null;
        }
        return tracked.dwellCenter;
    }

    @Override
    public boolean isSealed(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        return tracked != null && tracked.state.sealed();
    }

    public static BlockPos lastPosition(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        return tracked == null ? null : tracked.lastPosition;
    }

    public static long accumulatedTicks(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        return tracked == null ? 0L : tracked.accumulatedTicks;
    }

    public static int trackedCount() {
        return TRACKED.size();
    }

    public static void forget(UUID objectInstanceId) {
        if (objectInstanceId != null) {
            TRACKED.remove(objectInstanceId);
            CursedObjectRegistry.unregisterInstance(objectInstanceId);
        }
    }

    public static void clear() {
        TRACKED.clear();
        CursedObjectRegistry.clearInstances();
        lastObservedGameTime = 0L;
    }
    /** Refreshes the runtime view immediately after a stack component mutation. */
    @Override
    public void applySealState(UUID objectInstanceId, boolean sealed, int sealTier, int sealIntegrity,
            jujutsu.mod.cursedincident.KnowledgeLevel knowledge) {
        if (objectInstanceId == null) {
            return;
        }
        TrackedObject tracked = TRACKED.get(objectInstanceId);
        if (tracked == null || tracked.state == null) {
            return;
        }
        CursedObjectState current = tracked.state;
        CursedObjectState updated = new CursedObjectState(current.instanceId(), current.typeId(), current.grade(),
                current.mintedGameTime(), sealed, Math.max(0, sealTier), Math.max(0, sealIntegrity),
                knowledge == null ? current.knowledge() : knowledge, current.accumulatedTicks());
        if (tracked.stack != null && !tracked.stack.isEmpty()) {
            tracked.stack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, updated);
        }
        tracked.state = updated;
        tracked.accumulatedTicks = Math.max(0L, updated.accumulatedTicks());
        tracked.lastDecayGameTime = tracked.level == null
                ? tracked.lastDecayGameTime
                : tracked.level.getGameTime();
        if (sealed) {
            tracked.dwellCenter = null;
        }
    }

    public static void refresh(ItemStack stack) {
        CursedObjectState state = state(stack);
        if (state == null) {
            return;
        }
        TrackedObject tracked = TRACKED.get(state.instanceId());
        if (tracked == null) {
            return;
        }
        tracked.state = state;
        tracked.stack = stack;
        tracked.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
        if (state.sealed()) {
            tracked.dwellCenter = null;
        }
    }


    private static CursedObjectState state(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(JujutsuDataComponents.CURSED_OBJECT_STATE);
    }

    private static TrackedObject observe(CursedObjectState state, ItemStack stack, ServerLevel level,
            BlockPos position, long now, ItemEntity entity) {
        TrackedObject tracked = TRACKED.computeIfAbsent(state.instanceId(), ignored -> new TrackedObject(state));
        CursedObjectType type = CursedObjectRegistry.byId(state.typeId());
        tracked.type = type;
        tracked.state = state;
        tracked.stack = stack;
        if (stack != null) {
            CursedObjectItem.applyTypeComponents(stack);
        }
        tracked.level = level == null ? tracked.level : level;
        if (tracked.lastGameTime == 0L && tracked.anchor == null) {
            tracked.lastGameTime = now;
            tracked.lastDecayGameTime = Math.max(state.mintedGameTime(), now);
            tracked.anchor = position;
            tracked.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
        } else if (position != null && type != null && !state.sealed()) {
            DwellAccumulator.Result result = DwellAccumulator.update(
                    tracked.anchor, position, tracked.lastGameTime, tracked.accumulatedTicks, now,
                    DEFAULT_DWELL_TICKS, type.dwellRadius());
            if (!result.anchor().equals(tracked.anchor)) {
                tracked.dwellCenter = null;
            }
            if (result.dwellCenter() != null) {
                tracked.dwellCenter = result.dwellCenter();
            }
            tracked.anchor = result.anchor();
            tracked.accumulatedTicks = result.accumulatedTicks();
            tracked.lastGameTime = result.lastGameTime();
            CursedObjectState updated = state.withAccumulatedTicks(tracked.accumulatedTicks);
            if (!updated.equals(stack.get(JujutsuDataComponents.CURSED_OBJECT_STATE))) {
                stack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, updated);
                tracked.state = updated;
            }
        } else {
            tracked.lastGameTime = Math.max(tracked.lastGameTime, now);
            tracked.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
            tracked.dwellCenter = null;
        }
        applySealDecay(tracked, now);
        if (entity != null) {
            tracked.entity = entity;
        }
        CursedObjectRegistry.registerInstance(tracked.state);
        return tracked;
    }

    private static void applySealDecay(TrackedObject tracked, long now) {
        CursedObjectState state = tracked.state;
        if (!state.sealed() || state.sealTier() <= 0 || now <= tracked.lastDecayGameTime) {
            return;
        }
        long days = (now - tracked.lastDecayGameTime) / DEFAULT_DWELL_TICKS;
        if (days <= 0L) {
            return;
        }
        int decay = SealState.decayPerDay(state.sealTier());
        int integrity = Math.max(0, state.sealIntegrity() - (int) Math.min(Integer.MAX_VALUE, days * decay));
        CursedObjectState updated = state.withSeal(integrity > 0, state.sealTier(), integrity);
        tracked.stack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, updated);
        tracked.state = updated;
        tracked.lastDecayGameTime += days * DEFAULT_DWELL_TICKS;
    }

    private static void onUnload(ItemEntity item) {
        CursedObjectState state = state(item.getItem());
        if (state == null) {
            return;
        }
        TrackedObject tracked = TRACKED.get(state.instanceId());
        if (tracked == null || tracked.entity != item) {
            return;
        }
        tracked.lastPosition = item.blockPosition();
        tracked.stack = item.getItem();
        Entity.RemovalReason reason = item.getRemovalReason();
        tracked.entity = null;
        tracked.awaitingReload = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        tracked.dead = !tracked.awaitingReload;
    }

    private static void serverTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            lastObservedGameTime = Math.max(lastObservedGameTime, now);
            for (TrackedObject tracked : TRACKED.values()) {
                if (tracked.level != level) {
                    continue;
                }
                tickTracked(level, tracked, now);
            }
        }
    }

    private static void tickTracked(ServerLevel level, TrackedObject tracked, long now) {
        ItemEntity entity = tracked.entity;
        if (entity != null && entity.isRemoved()) {
            onUnload(entity);
            entity = tracked.entity;
        }
        if (entity != null) {
            if (now - tracked.lastGameTime >= POSITION_POLL_PERIOD) {
                noteWorldItem(entity);
            }
            return;
        }
        if (tracked.awaitingReload || tracked.dead && (tracked.type == null || tracked.type.destructible())) {
            if (tracked.dead && (tracked.type == null || tracked.type.destructible())) {
                forget(tracked.instanceId);
            }
            return;
        }
        if (tracked.type != null && tracked.type.indestructible() && tracked.lastPosition != null && tracked.stack != null) {
            ItemEntity respawned = new ItemEntity(level, tracked.lastPosition.getX() + 0.5,
                    tracked.lastPosition.getY() + 0.5, tracked.lastPosition.getZ() + 0.5, tracked.stack.copy());
            respawned.setUnlimitedLifetime();
            respawned.setNoPickUpDelay();
            if (level.addFreshEntity(respawned)) {
                tracked.entity = respawned;
                tracked.dead = false;
                tracked.awaitingReload = false;
                noteWorldItem(respawned);
            }
        }
    }
}
