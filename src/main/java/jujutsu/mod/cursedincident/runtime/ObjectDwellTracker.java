package jujutsu.mod.cursedincident.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jujutsu.mod.cursedincident.DwellProvider;
import jujutsu.mod.cursedincident.IncidentControl;
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
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

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
        BlockPos containerPos;
        BlockPos dwellContainer;
        long accumulatedTicks;
        long lastGameTime;
        long lastDecayGameTime;
        ItemStack stack;
        ItemEntity entity;
        ServerLevel level;
        Entity.RemovalReason removalReason;
        boolean awaitingReload;
        boolean dead;

        TrackedObject(CursedObjectState state) {
            this.instanceId = state.instanceId();
            this.state = state;
            this.type = CursedObjectRegistry.byId(state.typeId());
            this.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
            // The durable decay anchor survives restarts; stacks minted before the field
            // existed fall back to mint time via the record's compact constructor.
            this.lastDecayGameTime = state.lastDecayGameTime();
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
        // Gameplay seal damage: attacking a sealed cursed-object item entity damages the
        // seal through the single authoritative contract instead of the item (C16).
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (player, level, hand, entity, hitResult) -> {
                    if (level instanceof ServerLevel && entity instanceof ItemEntity item) {
                        CursedObjectState state = state(item.getItem());
                        if (state != null && state.sealed()) {
                            int amount = Math.max(1, (int) Math.ceil(player.getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));
                            CursedObjectItem.damageSeal(item.getItem(), amount);
                            return net.minecraft.world.InteractionResult.FAIL;
                        }
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
        // Opening a container anywhere scans it for tracked/voided objects — discovery is
        // not limited to the incident zone (C6).
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
                (player, level, hand, hitResult) -> {
                    if (level instanceof ServerLevel serverLevel && hitResult != null) {
                        BlockEntity blockEntity = serverLevel.getBlockEntity(hitResult.getBlockPos());
                        if (blockEntity instanceof Container container) {
                            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                                ItemStack stack = container.getItem(slot);
                                if (!stack.isEmpty()) {
                                    jujutsu.mod.cursedincident.object.ObjectWiring.tracker()
                                            .noteContainer(serverLevel, hitResult.getBlockPos(), stack);
                                }
                            }
                        }
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
    }

    public static void noteCarried(ItemStack stack, ServerPlayer player) {
        if (player != null && player.level() instanceof ServerLevel level) {
            CursedObjectState state = state(stack);
            if (state != null) {
                lastObservedGameTime = Math.max(lastObservedGameTime, level.getGameTime());
                TrackedObject tracked = observe(state, stack, level, player.blockPosition(), level.getGameTime(),
                        null, null);
                if (tracked == null) {
                    return;
                }
                tracked.entity = null;
                tracked.containerPos = null;
                tracked.dwellContainer = null;
                tracked.removalReason = null;
                tracked.awaitingReload = true;
                tracked.dead = false;
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
        TrackedObject tracked = observe(state, item.getItem(), level, item.blockPosition(), level.getGameTime(),
                item, null);
        if (tracked == null) {
            return;
        }
        tracked.entity = item;
        tracked.containerPos = null;
        tracked.dwellContainer = null;
        tracked.removalReason = null;
        tracked.awaitingReload = false;
        tracked.dead = false;
    }
    /** Called by the infection sink and the container-open hook for a physical container. */
    @Override
    public void noteContainer(ServerLevel level, BlockPos containerPos, ItemStack stack) {
        CursedObjectState state = state(stack);
        if (state == null || containerPos == null) {
            return;
        }
        TrackedObject prior = TRACKED.get(state.instanceId());
        long now = level != null
                ? level.getGameTime()
                : Math.max(lastObservedGameTime, prior == null ? 0L : prior.lastGameTime + 1L);
        TrackedObject tracked = observe(state, stack, level == null ? (prior == null ? null : prior.level) : level,
                containerPos, now, null, containerPos);
        if (tracked == null) {
            return;
        }
        tracked.entity = null;
        tracked.stack = resolveLiveStack(tracked);
        tracked.removalReason = null;
        tracked.awaitingReload = true;
        tracked.dead = false;
    }

    @Override
    public Set<BlockPos> knownContainerPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (TrackedObject tracked : TRACKED.values()) {
            if (tracked != null && !tracked.dead && tracked.containerPos != null) {
                positions.add(tracked.containerPos);
            }
        }
        return positions;
    }

    @Override
    public BlockPos dwellCenterOf(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        if (tracked == null || tracked.dead || tracked.state == null || tracked.state.sealed()) {
            return null;
        }
        return tracked.dwellCenter;
    }

    @Override
    public BlockPos containerOf(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        if (tracked == null || tracked.dead || tracked.state == null || tracked.state.sealed()
                || tracked.dwellCenter == null || tracked.dwellContainer == null) {
            return null;
        }
        return tracked.dwellContainer;
    }

    @Override
    public boolean isSealed(UUID objectInstanceId) {
        return sealSnapshot(objectInstanceId).sealed();
    }

    @Override
    public SealSnapshot sealSnapshot(UUID objectInstanceId) {
        TrackedObject tracked = objectInstanceId == null ? null : TRACKED.get(objectInstanceId);
        if (tracked == null || tracked.state == null) {
            return EMPTY_SEAL;
        }
        CursedObjectState state = tracked.state;
        return new SealSnapshot(state.sealed(), state.sealTier(), state.sealIntegrity(), state.knowledge());
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
        if (objectInstanceId == null) {
            return;
        }
        TrackedObject tracked = TRACKED.remove(objectInstanceId);
        if (tracked != null && tracked.entity != null && !tracked.entity.isRemoved()) {
            tracked.entity.discard();
        }
        CursedObjectRegistry.unregisterInstance(objectInstanceId);
    }

    /**
     * Removes every reachable physical copy of the object — the live item entity, stacks in
     * online players' inventories and the tracked container — and marks the id voided so a
     * late-observed leftover stack is destroyed on sight instead of resurrecting the source.
     */
    public static void forgetEverywhere(UUID objectInstanceId) {
        if (objectInstanceId == null) {
            return;
        }
        TrackedObject tracked = TRACKED.get(objectInstanceId);
        if (tracked != null) {
            if (tracked.entity != null && !tracked.entity.isRemoved()) {
                tracked.entity.discard();
            }
            if (tracked.level != null) {
                removeFromOnlineInventories(tracked.level.getServer(), objectInstanceId);
                removeFromContainer(tracked.level, tracked, objectInstanceId);
            }
        }
        forget(objectInstanceId);
        IncidentControl.voidObject(objectInstanceId);
    }

    private static void removeFromOnlineInventories(MinecraftServer server, UUID objectInstanceId) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Container inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack candidate = inventory.getItem(slot);
                CursedObjectState candidateState = state(candidate);
                if (candidateState != null && objectInstanceId.equals(candidateState.instanceId())) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private static void removeFromContainer(ServerLevel level, TrackedObject tracked, UUID objectInstanceId) {
        if (level == null || tracked == null || tracked.containerPos == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(tracked.containerPos);
        if (!(blockEntity instanceof Container container)) {
            return;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack candidate = container.getItem(slot);
            CursedObjectState candidateState = state(candidate);
            if (candidateState != null && objectInstanceId.equals(candidateState.instanceId())) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        blockEntity.setChanged();
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
        ItemStack liveStack = resolveLiveStack(tracked);
        if (liveStack != null && !liveStack.isEmpty()) {
            CursedObjectState liveState = state(liveStack);
            if (liveState != null && objectInstanceId.equals(liveState.instanceId())) {
                liveStack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, updated);
                tracked.stack = liveStack;
            }
        }
        tracked.state = updated;
        tracked.accumulatedTicks = Math.max(0L, updated.accumulatedTicks());
        tracked.lastDecayGameTime = tracked.level == null
                ? tracked.lastDecayGameTime
                : tracked.level.getGameTime();
        if (sealed) {
            tracked.dwellCenter = null;
            tracked.dwellContainer = null;
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
        tracked.stack = tracked.containerPos == null ? stack : resolveLiveStack(tracked);
        tracked.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
        if (state.sealed()) {
            tracked.dwellCenter = null;
            tracked.dwellContainer = null;
        }
    }

    private static CursedObjectState state(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(JujutsuDataComponents.CURSED_OBJECT_STATE);
    }

    private static TrackedObject observe(CursedObjectState state, ItemStack stack, ServerLevel level,
            BlockPos position, long now, ItemEntity entity, BlockPos containerPos) {
        // A voided id must never resurrect: destroy the observed stack/entity on sight.
        if (IncidentControl.isVoided(state.instanceId())) {
            destroyObserved(stack, entity, level);
            return null;
        }
        TrackedObject tracked = TRACKED.computeIfAbsent(state.instanceId(), ignored -> new TrackedObject(state));
        boolean fromContainer = containerPos != null;
        tracked.containerPos = fromContainer ? containerPos.immutable() : null;
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
            // The durable anchor decides the owed decay — including time the object spent
            // unobserved (offline inventory, unloaded chunk, restart). Clamping to `now`
            // here would forgive the whole interval on first sight (issue #110 C9).
            tracked.lastDecayGameTime = state.lastDecayGameTime();
            tracked.anchor = position;
            tracked.accumulatedTicks = Math.max(0L, state.accumulatedTicks());
        } else if (position != null && type != null && !state.sealed()) {
            // Per-incident dwell requirement wins over the default when this object is
            // an incident source (SpawnRequest.dwellTicksRequired override, R43/R44).
            long required = DEFAULT_DWELL_TICKS;
            jujutsu.mod.cursedincident.IncidentRecord owner =
                    IncidentControl.recordForObject(state.instanceId());
            if (owner != null && owner.params != null && owner.params.dwellTicksRequired() >= 0L) {
                required = owner.params.dwellTicksRequired();
            }
            DwellAccumulator.Result result = DwellAccumulator.update(
                    tracked.anchor, position, tracked.lastGameTime, tracked.accumulatedTicks, now,
                    required, type.dwellRadius());
            if (!result.anchor().equals(tracked.anchor)) {
                tracked.dwellCenter = null;
                tracked.dwellContainer = null;
            }
            if (result.dwellCenter() != null) {
                tracked.dwellCenter = result.dwellCenter();
                tracked.dwellContainer = fromContainer && tracked.dwellCenter.equals(tracked.containerPos)
                        ? tracked.containerPos
                        : null;
            } else if (!fromContainer) {
                tracked.dwellContainer = null;
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
            tracked.dwellContainer = null;
        }
        applySealDecay(tracked, now);
        if (entity != null) {
            tracked.entity = entity;
        }
        // A late-observed stack that violates the durable cap is destroyed, not silently
        // registered — the world must not keep an over-cap physical copy (issue #110 C10).
        if (!CursedObjectRegistry.registerInstance(tracked.state)) {
            TRACKED.remove(tracked.instanceId);
            destroyObserved(stack, entity, level);
            return null;
        }
        return tracked;
    }

    private static void destroyObserved(ItemStack stack, ItemEntity entity, ServerLevel level) {
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
            return;
        }
        if (stack != null && !stack.isEmpty()) {
            stack.setCount(0);
        }
    }

    private static ItemStack resolveLiveStack(TrackedObject tracked) {
        if (tracked.containerPos != null) {
            if (tracked.level == null) {
                // noteContainer's seam predates a level argument; when this is the
                // first observation, its supplied stack is the live container entry.
                return tracked.stack;
            }
            BlockEntity blockEntity = tracked.level.getBlockEntity(tracked.containerPos);
            if (!(blockEntity instanceof Container container)) {
                return null;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack candidate = container.getItem(slot);
                CursedObjectState candidateState = state(candidate);
                if (candidateState != null && tracked.instanceId.equals(candidateState.instanceId())) {
                    return candidate;
                }
            }
            return null;
        }
        if (tracked.entity != null && !tracked.entity.isRemoved()) {
            return tracked.entity.getItem();
        }
        return tracked.stack;
    }

    private static void applySealDecay(TrackedObject tracked, long now) {
        CursedObjectState state = tracked.state;
        if (state == null || !state.sealed() || state.sealTier() <= 0 || now <= tracked.lastDecayGameTime) {
            return;
        }
        ItemStack liveStack = resolveLiveStack(tracked);
        if (liveStack == null || liveStack.isEmpty()) {
            return;
        }
        CursedObjectState liveState = state(liveStack);
        if (liveState == null || !tracked.instanceId.equals(liveState.instanceId())) {
            return;
        }
        state = liveState;
        tracked.state = state;
        tracked.stack = liveStack;
        long days = (now - tracked.lastDecayGameTime) / DEFAULT_DWELL_TICKS;
        if (days <= 0L) {
            return;
        }
        int decay = SealState.decayPerDay(state.sealTier());
        int integrity = Math.max(0, state.sealIntegrity() - (int) Math.min(Integer.MAX_VALUE, days * decay));
        long anchor = tracked.lastDecayGameTime + days * DEFAULT_DWELL_TICKS;
        CursedObjectState updated = state
                .withSeal(integrity > 0, state.sealTier(), integrity)
                .withLastDecayGameTime(anchor);
        liveStack.set(JujutsuDataComponents.CURSED_OBJECT_STATE, updated);
        tracked.stack = liveStack;
        tracked.state = updated;
        tracked.lastDecayGameTime = anchor;
        IncidentControl.syncSealFromComponent(updated.instanceId());
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
        tracked.removalReason = reason;
        tracked.entity = null;
        tracked.containerPos = null;
        tracked.dwellContainer = null;
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
        // Seal decay is a physical lifecycle, not zone work: it keeps running while the
        // object sits in a container, is carried, or waits for its chunk to reload — a
        // sealed record never reaches tickZone, so this is the only decay driver (C8).
        applySealDecay(tracked, now);
        // DISCARDED means a player picked the object up — it is carried, not destroyed;
        // only genuine destruction (kill/void/lava → KILLED/other reasons) ceases the incident.
        if (tracked.dead && tracked.removalReason != Entity.RemovalReason.DISCARDED
                && (tracked.type == null || tracked.type.destructible())) {
            IncidentControl.onSourceDestroyed(tracked.instanceId);
            forget(tracked.instanceId);
            return;
        }
        if (tracked.awaitingReload || tracked.removalReason == Entity.RemovalReason.DISCARDED) {
            return;
        }
        if (tracked.type != null && tracked.type.indestructible() && tracked.lastPosition != null && tracked.stack != null) {
            ItemEntity respawned = new ItemEntity(level, tracked.lastPosition.getX() + 0.5,
                    tracked.lastPosition.getY() + 0.5, tracked.lastPosition.getZ() + 0.5, tracked.stack.copy());
            respawned.setUnlimitedLifetime();
            respawned.setNoPickUpDelay();
            if (level.addFreshEntity(respawned)) {
                tracked.entity = respawned;
                tracked.removalReason = null;
                tracked.dead = false;
                tracked.awaitingReload = false;
                noteWorldItem(respawned);
            }
        }
    }
}
