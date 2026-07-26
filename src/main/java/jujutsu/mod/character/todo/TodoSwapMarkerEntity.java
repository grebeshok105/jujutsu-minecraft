package jujutsu.mod.character.todo;

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

	public TodoSwapMarkerEntity(EntityType<? extends TodoSwapMarkerEntity> type, Level level) {
		super(type, level);
	}

	public TodoSwapMarkerEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(JujutsuEntities.TODO_SWAP_MARKER, owner, level, stack);
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
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (!(getOwner() instanceof LivingEntity owner)) {
			discard();
			return;
		}
		// Nudge out along the struck face so the marker rests visibly against the surface, not inside it.
		Direction face = hit.getDirection();
		Vec3 rest = hit.getLocation().add(
				face.getStepX() * TodoProfile.MARKER_SURFACE_OFFSET,
				face.getStepY() * TodoProfile.MARKER_SURFACE_OFFSET,
				face.getStepZ() * TodoProfile.MARKER_SURFACE_OFFSET);
		landed = true;
		setDeltaMovement(Vec3.ZERO);
		setNoGravity(true);
		snapTo(rest.x, rest.y, rest.z);
		TodoSwapMarks.mark(level, owner.getUUID(),
				TodoSwapMark.atPosition(level.dimension(), rest, getId(), level.getGameTime() + TodoProfile.MARKER_MARK_TTL_TICKS));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (!(getOwner() instanceof ServerPlayer owner)
				|| !(hit.getEntity() instanceof LivingEntity struck)
				|| !TodoBoogieWoogieRuntime.isEligibleTarget(owner, struck)) {
			discard();
			return;
		}
		// Only clear a glow we switched on, so marking never extinguishes another system's highlight.
		boolean glowApplied = !struck.hasGlowingTag();
		if (glowApplied) {
			struck.setGlowingTag(true);
		}
		TodoSwapMarks.mark(level, owner.getUUID(), TodoSwapMark.onEntity(level.dimension(), struck.position(),
				struck.getId(), struck.getUUID(), glowApplied, level.getGameTime() + TodoProfile.MARKER_MARK_TTL_TICKS));
		discard();
	}
}
