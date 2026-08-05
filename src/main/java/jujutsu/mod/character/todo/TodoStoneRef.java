package jujutsu.mod.character.todo;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The server's handle on one owner's live thrown stone.
 *
 * <p>Identity is the entity UUID plus the dimension it was thrown in — the entity id travels only
 * inside VFX cues, never resolves the entity, and never outlives the tick it was captured on. The
 * throw tick anchors the lifetime clock so an expiry sweep needs no per-tick counter on the ref.
 */
public record TodoStoneRef(UUID entityUuid, int entityId, ResourceKey<Level> dimension, long thrownAtGameTime) {}
