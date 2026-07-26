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
 *     which <em>is</em> the mark and must be discarded when the mark ends. It has <b>no clock</b>: a
 *     landed mark is an anchor, and the swap onto it does not spend it.
 * <li>{@link Form#ENTITY} — the marker struck a body. The projectile is already gone; {@code entityId}
 *     and {@code entityUuid} are the marked body, and {@code glowApplied} records whether the glow is
 *     ours to switch off. It is false when the body was already glowing, so ending a mark can never
 *     extinguish someone else's. This form keeps its ten seconds and is consumed by the swap it enables.
 * </ul>
 *
 * <p><b>What "permanent" means here, exactly.</b> A landed mark is permanent until it is explicitly
 * cleared or its marker is lost, but it is <b>not persistent between server sessions</b>. It ends on
 * death, on changing vessel, on changing dimension, on disconnect, on server stop, and when the
 * projectile goes missing from a loaded chunk. {@link #NEVER} is the absence of a timer, not eternity;
 * do not "fix" the missing persistence, it is the decision.
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
	/** A landed mark has no clock. The absence of a timer, not a date far in the future. */
	public static final long NEVER = Long.MAX_VALUE;

	public enum Form {
		POSITION,
		ENTITY
	}

	/** The lifetimes are not interchangeable, so neither form can be built holding the other's. */
	public TodoSwapMark {
		if (form == Form.POSITION && expiresAtGameTime != NEVER) {
			throw new IllegalArgumentException("A landed mark does not expire");
		}
		if (form == Form.ENTITY && expiresAtGameTime == NEVER) {
			throw new IllegalArgumentException("A body mark must expire");
		}
	}

	/** No expiry parameter: giving a landed mark a clock should not compile, not merely be wrong. */
	public static TodoSwapMark atPosition(ResourceKey<Level> dimension, Vec3 position, int markerEntityId) {
		return new TodoSwapMark(Form.POSITION, dimension, position, markerEntityId, null, false, NEVER);
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
		return expiresAtGameTime != NEVER && gameTime >= expiresAtGameTime;
	}

	public boolean isIn(ResourceKey<Level> dimension) {
		return this.dimension.equals(dimension);
	}

	/** A position mark answers with where the projectile came to rest; an entity mark follows its body. */
	public Vec3 destination(Vec3 livePosition) {
		return form == Form.ENTITY && livePosition != null ? livePosition : position;
	}
}
