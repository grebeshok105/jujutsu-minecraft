package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
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

/**
 * One transient Great Serpent body: an ambusher that sinks under a target, coils it, and opens the
 * fight for the rest of the pack. The bind lifecycle is owned by {@link MegumiSerpentBrain}; this
 * entity only carries the hold/release plumbing and the summoned-body contract.
 */
public final class MegumiSerpentEntity extends MegumiShikigamiEntity {

	public MegumiSerpentEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	// --- ambush/bind state, driven by MegumiSerpentBrain (worker island fills these) ---

	UUID bindVictimUuid() {
		return null;
	}

	UUID ambushTargetUuid() {
		return null;
	}

	/** Action-clip indices the client reads: submerge/emerge/bind/release plus the sunken idle. */
	public static final int ACTION_SUBMERGE = 1;
	public static final int ACTION_EMERGE = 2;
	public static final int ACTION_BIND = 3;
	public static final int ACTION_RELEASE = 4;
	public static final int ACTION_SUBMERGED = 5;

	/** Which action clip the client should hold: 0=none, 1=submerge, 2=emerge, 3=bind, 4=release, 5=submerged. */
	public int presentationAction() {
		return 0;
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
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_START,
				(float) MegumiShikigamiProfile.SERPENT_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: the serpent never swings — binding is its whole attack.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.SILVERFISH_AMBIENT, 0.5f, 0.8f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}
}
