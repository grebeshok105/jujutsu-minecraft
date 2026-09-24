package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.vfx.MegumiVfxIds;

/** One transient Great Serpent body: a follower whose sic-mark response is an ambush bind, not melee. */
public final class MegumiSerpentEntity extends MegumiShikigamiEntity {
	private MegumiSerpentPolicy.State serpentState = MegumiSerpentPolicy.State.FOLLOW_READY;
	private UUID ambushTargetUuid;
	private UUID bindTargetUuid;
	private long stateDeadlineGameTime;
	private Vec3 safeReturnPosition;

	public MegumiSerpentEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.SERPENT_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.SERPENT_MOVEMENT_SPEED)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.SERPENT;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.SERPENT_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.SERPENT_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.SERPENT_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// Deliberately no melee or owner-retaliation goals: only a valid pack mark can start an ambush.
	}

	@Override
	protected void onActivated() {
		playShadowOpenSound();
		playSpatial(SoundEvents.SPIDER_AMBIENT, 0.48f, 0.72f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	public MegumiSerpentPolicy.State serpentState() {
		return serpentState;
	}

	@Override
	public boolean suppressesLeash() {
		// A bound victim must not be dragged across the map: the shared leash teleport would
		// move the serpent to the owner and mask the bind-distance release. While the serpent
		// holds, the bind leash (SERPENT_BIND_LEASH) is the only distance rule.
		return serpentState == MegumiSerpentPolicy.State.BIND;
	}

	public UUID ambushTargetUuid() {
		return ambushTargetUuid;
	}

	/** Server-side coordinator intent; non-null only while this body is in BIND. */
	public UUID bindTargetUuid() {
		return serpentState == MegumiSerpentPolicy.State.BIND ? bindTargetUuid : null;
	}

	public long stateDeadlineGameTime() {
		return stateDeadlineGameTime;
	}
	@Override
	void assignAutonomousTarget(LivingEntity target) {
		if (serpentState != MegumiSerpentPolicy.State.FOLLOW_READY
				&& serpentState != MegumiSerpentPolicy.State.RECOVERY) {
			return;
		}
		super.assignAutonomousTarget(target);
	}

	Vec3 safeReturnPosition() {
		return safeReturnPosition;
	}

	void beginPrepareAmbush(LivingEntity target, long gameTime) {
		if (target == null || !transition(MegumiSerpentPolicy.Event.MARK_READY,
				gameTime + MegumiShikigamiProfile.SERPENT_PREPARE_TICKS)) {
			return;
		}
		ambushTargetUuid = target.getUUID();
		beginAction(MegumiShikigamiProfile.SERPENT_PREPARE_TICKS);
	}

	boolean beginSubmerge(long gameTime) {
		if (!transition(MegumiSerpentPolicy.Event.PREPARE_COMPLETE,
				gameTime + MegumiShikigamiProfile.SERPENT_SUBMERGE_TICKS)) {
			return false;
		}
		safeReturnPosition = position();
		beginAction(MegumiShikigamiProfile.SERPENT_SUBMERGE_TICKS);
		return true;
	}

	boolean beginEmerge(Vec3 destination, LivingEntity target, long gameTime) {
		if (destination == null || target == null || serpentState != MegumiSerpentPolicy.State.SUBMERGED) {
			return false;
		}
		teleportTo((ServerLevel) level(), destination.x, destination.y, destination.z,
				java.util.Set.of(), yawTowards(destination, target.position()), getXRot(), false);
		setDeltaMovement(Vec3.ZERO);
		if (!transition(MegumiSerpentPolicy.Event.SUBMERGE_COMPLETE,
				gameTime + MegumiShikigamiProfile.SERPENT_EMERGE_TICKS)) {
			return false;
		}
		beginAction(MegumiShikigamiProfile.SERPENT_EMERGE_TICKS);
		return true;
	}

	boolean beginBind(LivingEntity target, long gameTime) {
		if (target == null || serpentState != MegumiSerpentPolicy.State.EMERGE) {
			return false;
		}
		bindTargetUuid = target.getUUID();
		if (!transition(MegumiSerpentPolicy.Event.EMERGE_BINDABLE,
				gameTime + MegumiShikigamiProfile.SERPENT_BIND_TICKS)) {
			bindTargetUuid = null;
			return false;
		}
		beginAction(MegumiShikigamiProfile.SERPENT_BIND_TICKS);
		return true;
	}

	/** Enter RELEASE once, clear the public intent immediately, and return the UUID to release. */
	UUID beginRelease(MegumiSerpentPolicy.Event event, long gameTime) {
		UUID victimUuid = bindTargetUuid;
		if (serpentState != MegumiSerpentPolicy.State.BIND
				|| !transition(event, gameTime + 1L)) {
			return null;
		}
		return victimUuid;
	}

	void beginRecovery(long gameTime) {
		if (!transition(MegumiSerpentPolicy.Event.TARGET_INVALID,
				gameTime + MegumiShikigamiProfile.SERPENT_RECOVERY_TICKS)) {
			return;
		}
	}

	void finishRelease(long gameTime) {
		transition(MegumiSerpentPolicy.Event.RELEASE_COMPLETE,
				gameTime + MegumiShikigamiProfile.SERPENT_RECOVERY_TICKS);
	}

	void finishRecovery(long gameTime) {
		transition(MegumiSerpentPolicy.Event.RECOVERY_COMPLETE, gameTime);
	}

	private boolean transition(MegumiSerpentPolicy.Event event, long deadline) {
		MegumiSerpentPolicy.State next = MegumiSerpentPolicy.nextState(serpentState, event);
		if (next == serpentState) {
			return false;
		}
		MegumiSerpentPolicy.State previous = serpentState;
		serpentState = next;
		stateDeadlineGameTime = deadline;
		if (next != MegumiSerpentPolicy.State.BIND) {
			bindTargetUuid = null;
		}
		if (next == MegumiSerpentPolicy.State.FOLLOW_READY
				|| next == MegumiSerpentPolicy.State.RECOVERY) {
			ambushTargetUuid = null;
		}
		applyState(previous);
		return true;
	}

	private void applyState(MegumiSerpentPolicy.State previous) {
		boolean submerged = serpentState == MegumiSerpentPolicy.State.SUBMERGED;
		setInvisible(submerged || hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY));
		setNoGravity(submerged);
		boolean suspended = serpentState != MegumiSerpentPolicy.State.FOLLOW_READY;
		setNoAi(suspended || !combatEnabled());
		if (suspended) {
			getNavigation().stop();
			setDeltaMovement(Vec3.ZERO);
		}
		if (previous == MegumiSerpentPolicy.State.SUBMERGED && !submerged) {
			setDeltaMovement(Vec3.ZERO);
		}
	}

	@Override
	void beginRecall() {
		if (!level().isClientSide()) {
			releaseHeldVictim(MegumiSerpentPolicy.Event.MANUAL_RECALL);
		}
		super.beginRecall();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) {
			MegumiSerpentPolicy.Event event = reason == Entity.RemovalReason.KILLED
					? MegumiSerpentPolicy.Event.SERPENT_DEATH
					: MegumiSerpentPolicy.Event.SERPENT_REMOVED;
			releaseHeldVictim(event);
		}
		super.remove(reason);
	}

	/** Idempotent teardown seam for recall, death, unload, and server disposal. */
	private void releaseHeldVictim(MegumiSerpentPolicy.Event event) {
		UUID victimUuid = bindTargetUuid;
		if (victimUuid != null) {
			releaseTrackedVictim(victimUuid);
			broadcastReleaseCue(victimUuid);
		}
		if (serpentState == MegumiSerpentPolicy.State.BIND) {
			transition(event, level().getGameTime() + 1L);
		} else if (serpentState != MegumiSerpentPolicy.State.FOLLOW_READY
				&& serpentState != MegumiSerpentPolicy.State.RECOVERY) {
			transition(MegumiSerpentPolicy.Event.TARGET_INVALID,
					level().getGameTime() + MegumiShikigamiProfile.SERPENT_RECOVERY_TICKS);
		}
		bindTargetUuid = null;
		ambushTargetUuid = null;
	}
	private void broadcastReleaseCue(UUID victimUuid) {
		if (!(level() instanceof ServerLevel serverLevel) || ownerUuid() == null) {
			return;
		}
		MinecraftServer server = serverLevel.getServer();
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid());
		if (owner == null || owner.level() != serverLevel) {
			return;
		}
		Vec3 origin = position();
		if (victimUuid != null) {
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.getEntity(victimUuid) instanceof LivingEntity victim) {
					origin = victim.position();
					break;
				}
			}
		}
		MegumiShikigamiRuntime.broadcastCue(serverLevel, owner, MegumiVfxIds.SERPENT_RELEASE,
				origin, getId(), Vec3.ZERO);
	}

	/** Clears the pair by UUID even if the victim left this level; never strips another holder's pin. */
	private void releaseTrackedVictim(UUID victimUuid) {
		UUID currentHolder = HeldVictimRegistry.holderUuid(victimUuid);
		if (currentHolder != null && !getUUID().equals(currentHolder)) {
			return;
		}
		MinecraftServer server = getServer();
		if (server != null) {
			for (ServerLevel serverLevel : server.getAllLevels()) {
				if (serverLevel.getEntity(victimUuid) instanceof LivingEntity victim) {
					HoldSupport.release(victim);
					return;
				}
			}
			ServerPlayer online = server.getPlayerList().getPlayer(victimUuid);
			if (online != null) {
				HoldSupport.release(online);
				return;
			}
		}
		HeldVictimRegistry.release(victimUuid);
	}
}
