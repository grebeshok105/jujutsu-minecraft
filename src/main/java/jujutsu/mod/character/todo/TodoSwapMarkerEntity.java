package jujutsu.mod.character.todo;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;

/**
 * Todo's thrown swap marker. Vanilla owns the flight: server-authoritative movement, client
 * interpolation, hit detection and tracking all come from {@link ThrowableItemProjectile}.
 *
 * <p>A block hit keeps this entity alive — the resting projectile <em>is</em> the mark, and
 * {@link TodoSwapMarks} discards it when the mark ends. An entity hit removes it instead and puts the
 * mark on the body it struck. Those are the two lifetimes the mark record exists to keep straight.
 */
public class TodoSwapMarkerEntity extends ThrowableItemProjectile {
	private int flightTicks;
	private boolean landed;
	/**
	 * Captured at the throw so a sweep can still find this after the thrower is gone. {@code getOwner()}
	 * resolves a live entity and returns null once it is not one, which is precisely the moment
	 * {@link TodoStateLifecycle} needs to identify what to discard.
	 */
	private UUID thrownBy;

	public TodoSwapMarkerEntity(EntityType<? extends TodoSwapMarkerEntity> type, Level level) {
		super(type, level);
	}

	public TodoSwapMarkerEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(JujutsuEntities.TODO_SWAP_MARKER, owner, level, stack);
		this.thrownBy = owner.getUUID();
	}

	/** The thrower's id, or null on a client-constructed copy that never saw the throw. */
	UUID thrownBy() {
		return thrownBy;
	}

	@Override
	protected Item getDefaultItem() {
		return JujutsuItems.TODO_SWAP_MARKER;
	}

	@Override
	public void tick() {
		if (landed) {
			// A resting mark takes no physics and must not re-enter hit detection. Its lifetime belongs
			// to TodoSwapMarks, which discards it; nothing here counts a second clock against it.
			return;
		}
		flightTicks++;
		if (!level().isClientSide && flightTicks > TodoProfile.MARKER_FLIGHT_TICKS) {
			discard();
			return;
		}
		super.tick();
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		// Nudge out along the struck face so the marker rests visibly against the surface, not inside it.
		Direction face = hit.getDirection();
		Vec3 rest = hit.getLocation().add(
				face.getStepX() * TodoProfile.MARKER_SURFACE_OFFSET,
				face.getStepY() * TodoProfile.MARKER_SURFACE_OFFSET,
				face.getStepZ() * TodoProfile.MARKER_SURFACE_OFFSET);
		// Landing is settled on both sides for every client that witnessed the hit, because `landed` is not
		// synched: if only the server stopped the physics, a witnessing client would keep applying gravity
		// between position updates and the resting marker would sag and snap back once per interval.
		// A client that enters tracking range later constructs this with `landed = false` and never sees
		// onHitBlock -- harmless, because zero delta plus the synched noGravity flag means no movement, and
		// a landed mark now outlives its old ten-second window often enough for that to be the normal case.
		landed = true;
		setDeltaMovement(Vec3.ZERO);
		setNoGravity(true);
		snapTo(rest.x, rest.y, rest.z);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = todoOwner();
		if (owner == null) {
			discard();
			return;
		}
		TodoSwapMarks.mark(level, owner.getUUID(), TodoSwapMark.atPosition(level.dimension(), rest, getId()));
	}

	/**
	 * The thrower, but only while he is still Todo.
	 *
	 * <p>The throw is gated on the vessel and the landing was not, so switching vessel inside the flight
	 * window let this create a mark after the leaving-the-vessel teardown had already run — a mark in the
	 * world belonging to a player who is not Todo, which is the shape E12 was closed to prevent.
	 * Re-reading the selection here rather than trusting the throw is what makes the gate hold for the
	 * whole flight instead of only its first tick.
	 */
	private ServerPlayer todoOwner() {
		return getOwner() instanceof ServerPlayer owner
				&& CharacterSelectionManager.selected(owner) == JujutsuCharacter.TODO
				? owner
				: null;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = todoOwner();
		if (owner == null
				|| !(hit.getEntity() instanceof LivingEntity struck)
				|| !TodoBoogieWoogieRuntime.isEligibleTarget(owner, struck)) {
			discard();
			return;
		}
		// The release-then-read-glow order lives in TodoSwapMarks, shared with the ability that marks a
		// body without a throw. Two copies of that order would eventually stop matching.
		TodoSwapMarks.markBody(level, owner.getUUID(), struck);
		discard();
	}
}
