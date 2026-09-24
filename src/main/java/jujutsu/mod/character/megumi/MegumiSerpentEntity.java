package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * One transient Great Serpent body: an ambusher that sinks under a target, coils it, and opens the
 * fight for the rest of the pack. The bind lifecycle is owned by {@link MegumiSerpentBrain}; this
 * entity only carries the hold/release plumbing and the summoned-body contract.
 */
public final class MegumiSerpentEntity extends MegumiShikigamiEntity {

	/** The brain-owned state machine (plan §C): the fields below are the committed ambush/bind. */
	public enum SerpentState {
		FOLLOW,
		READY,
		PREPARE_AMBUSH,
		SUBMERGED,
		EMERGE,
		BIND,
		RELEASE,
		RECOVERY
	}

	/** Action-clip indices the client reads: submerge/emerge/bind/release plus the sunken idle. */
	public static final int ACTION_SUBMERGE = 1;
	public static final int ACTION_EMERGE = 2;
	public static final int ACTION_BIND = 3;
	public static final int ACTION_RELEASE = 4;
	public static final int ACTION_SUBMERGED = 5;

	private static final EntityDataAccessor<Integer> DATA_ACTION =
			SynchedEntityData.defineId(MegumiSerpentEntity.class, EntityDataSerializers.INT);

	public MegumiSerpentEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	// --- ambush/bind state, driven by MegumiSerpentBrain ---------------------------------------
	// All of it is transient on purpose: shikigami bodies are registered .noSave(), so a bind
	// never outlives its body and needs no NBT. The state also lives in its own fields, never in
	// the sic mark: beginRecall/setPresentationPhase call clearSicCommand(), which would silently
	// end a bind.

	private SerpentState state = SerpentState.FOLLOW;
	private int stateTicks;
	/** The target the committed ambush is diving under (a sic mark or the body's own pick). */
	private UUID ambushTargetUuid;
	/** The victim currently coiled, or null. */
	private UUID bindVictimUuid;
	private long bindEndGameTime;
	/** The SafeBodyPlacement-validated point the EMERGE tick teleports to. */
	private Vec3 emergePoint;
	/** Consecutive SUBMERGED ticks whose re-validation found the emerge point unplaceable. */
	private int emergePointInvalidTicks;
	private long nextAmbushScanGameTime;

	public SerpentState state() {
		return state;
	}

	public int stateTicks() {
		return stateTicks;
	}

	/** Enters {@code next} and resets its tick counter; the brain owns all transitions. */
	void setState(SerpentState next) {
		state = next;
		stateTicks = 0;
	}

	void tickState() {
		stateTicks++;
	}

	/** The committed ambush target's id — the coordinator reads it as this body's intent. */
	public UUID ambushTargetUuid() {
		return ambushTargetUuid;
	}

	/** Commits the ambush to {@code target} and records the placement-checked surface point. */
	void beginAmbush(LivingEntity target, Vec3 emergePoint) {
		ambushTargetUuid = target.getUUID();
		this.emergePoint = emergePoint;
		emergePointInvalidTicks = 0;
	}

	void clearAmbush() {
		ambushTargetUuid = null;
		emergePoint = null;
		emergePointInvalidTicks = 0;
	}

	/** The bound victim's id — the coordinator reads it as this body's committed intent. */
	public UUID bindVictimUuid() {
		return bindVictimUuid;
	}

	public long bindEndGameTime() {
		return bindEndGameTime;
	}

	public boolean isBinding() {
		return bindVictimUuid != null;
	}

	void beginBind(LivingEntity victim, long endGameTime) {
		bindVictimUuid = victim.getUUID();
		bindEndGameTime = endGameTime;
		ambushTargetUuid = null;
		emergePoint = null;
		emergePointInvalidTicks = 0;
	}

	/** Ends the bind (release, recall, death) without touching the victim. */
	void clearBind() {
		bindVictimUuid = null;
		bindEndGameTime = 0L;
	}

	Vec3 emergePoint() {
		return emergePoint;
	}

	int emergePointInvalidTicks() {
		return emergePointInvalidTicks;
	}

	void markEmergePointInvalid() {
		emergePointInvalidTicks++;
	}

	void clearEmergePointInvalid() {
		emergePointInvalidTicks = 0;
	}

	long nextAmbushScanGameTime() {
		return nextAmbushScanGameTime;
	}

	void markAmbushScan(long untilGameTime) {
		nextAmbushScanGameTime = untilGameTime;
	}

	/** Which action clip the client should hold: 0=none, 1=submerge, 2=emerge, 3=bind, 4=release, 5=submerged. */
	public int presentationAction() {
		return entityData.get(DATA_ACTION);
	}

	void setPresentationAction(int action) {
		entityData.set(DATA_ACTION, action);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.SERPENT_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.SERPENT_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.SERPENT_SPEED)
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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_START,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: the serpent never swings — binding is its whole attack.
		// No OwnerHurtByTargetGoal and no OwnerHurtTargetGoal either: both set a target straight
		// from the owner's own fight, bypassing the pack's priorities and stealing a manual
		// sic's mark (issue #76). Target acquisition belongs to the sic command, the
		// retaliation pass and the coordinator.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.SHULKER_AMBIENT, 0.5f, 0.8f);
		// The pack's coordinator writes autonomous marks on its own cadence (a few ticks); the
		// body's own scan is the fallback for when no mark arrives at all. If it fired on the
		// first active tick it would pre-empt every coordinator pick forever, so the first
		// autonomous window waits one scan interval.
		markAmbushScan(level().getGameTime() + MegumiShikigamiProfile.SERPENT_AMBUSH_SCAN_TICKS);
	}

	/** The bind is control, not damage: a bound victim takes no melee from the body that coils it. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (isBinding() && target.getUUID().equals(bindVictimUuid)) {
			return false;
		}
		return super.doHurtTarget(level, target);
	}

	/** Nothing the bound victim does can hurt its holder (mirrors the toad's R12 refusal). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && isBinding() && attacker.getUUID().equals(bindVictimUuid)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	void beginRecall() {
		releaseBindVictim();
		super.beginRecall();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) {
			releaseBindVictim();
		}
		super.remove(reason);
	}

	/** Frees whoever is bound — recall, death and removal all funnel through here. */
	void releaseBindVictim() {
		// Release by UUID, not by resolved entity: an unloaded/dimension-hopped victim is
		// unresolvable but its registry pair must still drop, or the UUID stays held forever.
		if (bindVictimUuid != null) {
			HeldVictimRegistry.release(bindVictimUuid);
			if (level() instanceof ServerLevel serverLevel) {
				if (serverLevel.getEntity(bindVictimUuid) instanceof LivingEntity victim) {
					victim.removeEffect(JujutsuEffects.GRIPPED);
				} else if (serverLevel.getServer().getPlayerList().getPlayer(bindVictimUuid)
						instanceof ServerPlayer remote) {
					// A victim who changed dimension before the release is invisible to this
					// level but still wears GRIPPED — clear it server-wide or the marker
					// lingers on the destination player for its refresh window.
					remote.removeEffect(JujutsuEffects.GRIPPED);
				}
			}
		}
		clearBind();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SHULKER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.SHULKER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SHULKER_DEATH;
	}
}
