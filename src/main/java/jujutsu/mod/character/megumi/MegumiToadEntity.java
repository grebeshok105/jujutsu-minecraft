package jujutsu.mod.character.megumi;

import java.util.UUID;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEffects;

/** One transient Toad body: a ground walker whose sic command answers with a tongue grab. */
public final class MegumiToadEntity extends MegumiShikigamiEntity {
	public MegumiToadEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.TOAD_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.TOAD_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.TOAD_SPEED)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.TOAD;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.TOAD_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.TOAD_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.TOAD_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.TOAD_FOLLOW_START,
				(float) MegumiShikigamiProfile.TOAD_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No OwnerHurtByTargetGoal and no OwnerHurtTargetGoal: both set a target straight from the
		// owner's own fight — the first from whoever hit the owner, the second from whoever the
		// owner hit — bypassing the pack's priorities and stealing a manual sic's mark (issue #76).
		// Target acquisition belongs to the sic command and the retaliation pass, which know
		// eligibility, the sic's precedence and how to re-mark without cancelling a leap.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.FROG_LONG_JUMP, 0.55f, 0.95f);
		playSpatial(SoundEvents.FROG_AMBIENT, 0.5f, 1.0f);
	}

	// --- The grab (issue #79) -----------------------------------------------------------------
	// All of it is transient on purpose: shikigami bodies are registered .noSave(), so a hold never
	// outlives its body and needs no NBT. The state also lives in its own fields, never in the sic
	// mark: beginRecall/setPresentationPhase call clearSicCommand(), which would silently end a hold.

	/** The target the current windup is committed to (a sic mark or the body's own pick). */
	private UUID grabIntentUuid;
	/** The victim currently pinned, or null. */
	private UUID grabbedUuid;
	private long grabEndGameTime;
	private boolean grabbedIsPlayer;
	private UUID throwFlashUuid;
	private long throwFlashUntil;
	private long nextGrabScanGameTime;

	UUID grabIntentUuid() {
		return grabIntentUuid;
	}
	void beginGrabIntent(LivingEntity target) {
		grabIntentUuid = target.getUUID();
	}

	void clearGrabIntent() {
		grabIntentUuid = null;
	}

	public UUID grabbedUuid() {
		return grabbedUuid;
	}

	public long grabEndGameTime() {
		return grabEndGameTime;
	}

	public boolean grabbedIsPlayer() {
		return grabbedIsPlayer;
	}

	public boolean isHolding() {
		return grabbedUuid != null;
	}

	void beginGrab(LivingEntity victim, long endGameTime) {
		grabbedUuid = victim.getUUID();
		grabEndGameTime = endGameTime;
		grabbedIsPlayer = victim instanceof Player;
		grabIntentUuid = null;
	}

	/** Ends the hold (throw, break, recall, death) without touching the victim. */
	void clearGrab() {
		grabbedUuid = null;
		grabbedIsPlayer = false;
		grabEndGameTime = 0L;
		grabIntentUuid = null;
	}

	void markThrown(LivingEntity victim, long untilGameTime) {
		throwFlashUuid = victim.getUUID();
		throwFlashUntil = untilGameTime;
	}

	public UUID throwFlashUuid() {
		return throwFlashUuid;
	}

	public long throwFlashUntil() {
		return throwFlashUntil;
	}

	public long nextGrabScanGameTime() {
		return nextGrabScanGameTime;
	}

	void markGrabScan(long untilGameTime) {
		nextGrabScanGameTime = untilGameTime;
	}

	/** The grab is control, not damage: a held victim takes no melee from the body that holds it. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (isHolding() && target.getUUID().equals(grabbedUuid)) {
			return false;
		}
		return super.doHurtTarget(level, target);
	}

	/** Nothing the held victim does can hurt its holder (R12). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && isHolding() && attacker.getUUID().equals(grabbedUuid)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	void beginRecall() {
		releaseHeldVictim();
		super.beginRecall();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) {
			releaseHeldVictim();
		}
		super.remove(reason);
	}

	/** Frees whoever is held — recall, death and removal all funnel through here. */
	private void releaseHeldVictim() {
		// Release by UUID, not by resolved entity: an unloaded/dimension-hopped victim is
		// unresolvable but its registry pair must still drop, or the UUID stays held forever.
		if (grabbedUuid != null) {
			HeldVictimRegistry.release(grabbedUuid);
			if (level() instanceof ServerLevel serverLevel) {
				if (serverLevel.getEntity(grabbedUuid) instanceof LivingEntity victim) {
					victim.removeEffect(JujutsuEffects.GRIPPED);
				} else if (serverLevel.getServer().getPlayerList().getPlayer(grabbedUuid)
						instanceof ServerPlayer remote) {
					// A victim who changed dimension before the release is invisible to this
					// level but still wears GRIPPED — clear it server-wide or the marker
					// lingers on the destination player for its refresh window.
					remote.removeEffect(JujutsuEffects.GRIPPED);
				}
			}
		}
		clearGrab();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}
}
