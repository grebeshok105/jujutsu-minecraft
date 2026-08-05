package jujutsu.mod.character.todo;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The server's handle on one owner's live thrown stone.
 *
 * <p>Identity is the entity UUID plus the dimension it was thrown in — resolution never crosses
 * dimensions and never happens by entity id (cues that need an anchor id read it off the live
 * entity at emit time). The throw tick anchors the lifetime clock so an expiry sweep needs no
 * per-tick counter on the ref.
 */
public record TodoStoneRef(UUID entityUuid, ResourceKey<Level> dimension, long thrownAtGameTime) {}
