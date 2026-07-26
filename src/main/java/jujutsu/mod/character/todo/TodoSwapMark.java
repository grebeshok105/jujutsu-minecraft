package jujutsu.mod.character.todo;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown Boogie Woogie mark. Two forms with genuinely different lifetimes, kept in one record so no
 * cleanup path can handle one and forget the other.
 *
 * <ul>
 * <li>{@link Form#POSITION} — the marker landed on a block. {@code entityId} is the resting projectile,
 *     which <em>is</em> the mark and must be discarded when the mark ends.
 * <li>{@link Form#ENTITY} — the marker struck a body. The projectile is already gone; {@code entityId}
 *     and {@code entityUuid} are the marked body, and {@code glowApplied} records whether the glow is
 *     ours to switch off. It is false when the body was already glowing, so ending a mark can never
 *     extinguish someone else's.
 * </ul>
 */
public record TodoSwapMark(
		Form form,
		ResourceKey<Level> dimension,
		Vec3 position,
		int entityId,
		UUID entityUuid,
		boolean glowApplied,
		long expiresAtGameTime
) {
	public enum Form {
		POSITION,
		ENTITY
	}

	public static TodoSwapMark atPosition(ResourceKey<Level> dimension, Vec3 position, int markerEntityId, long expiresAtGameTime) {
		return new TodoSwapMark(Form.POSITION, dimension, position, markerEntityId, null, false, expiresAtGameTime);
	}

	public static TodoSwapMark onEntity(
			ResourceKey<Level> dimension,
			Vec3 position,
			int entityId,
			UUID entityUuid,
			boolean glowApplied,
			long expiresAtGameTime
	) {
		return new TodoSwapMark(Form.ENTITY, dimension, position, entityId, entityUuid, glowApplied, expiresAtGameTime);
	}

	public boolean isExpired(long gameTime) {
		return gameTime >= expiresAtGameTime;
	}

	public boolean isIn(ResourceKey<Level> dimension) {
		return this.dimension.equals(dimension);
	}

	/** A position mark answers with where the projectile came to rest; an entity mark follows its body. */
	public Vec3 destination(Vec3 livePosition) {
		return form == Form.ENTITY && livePosition != null ? livePosition : position;
	}
}
