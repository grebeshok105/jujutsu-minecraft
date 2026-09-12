package jujutsu.mod.character.megumi;

/** Centralized tuning for the four new Ten Shadows shikigami (Nue / Toad / Rabbit Escape / Max Elephant). */
public final class MegumiShikigamiProfile {
	private MegumiShikigamiProfile() {}

	// --- shared summon/sic surface ---
	public static final double SIC_RANGE = 20.0;
	public static final int SIC_COOLDOWN_TICKS = 30;

	// --- cooldown table (one row per type; DOGS mirrors the dog runtime values for reference only) ---
	public static int recallCooldownTicks(MegumiShikigami type) {
		return switch (type) {
			case DOGS -> 240;
			case NUE -> NUE_RECALL_COOLDOWN_TICKS;
			case TOAD -> TOAD_RECALL_COOLDOWN_TICKS;
			case RABBITS -> RABBITS_RECALL_COOLDOWN_TICKS;
			case ELEPHANT -> ELEPHANT_RECALL_COOLDOWN_TICKS;
		};
	}

	public static int deathCooldownTicks(MegumiShikigami type) {
		return switch (type) {
			case DOGS -> 600;
			case NUE -> NUE_DEATH_COOLDOWN_TICKS;
			case TOAD -> TOAD_DEATH_COOLDOWN_TICKS;
			case RABBITS -> RABBITS_DEATH_COOLDOWN_TICKS;
			case ELEPHANT -> ELEPHANT_DEATH_COOLDOWN_TICKS;
		};
	}

	public static int maxRecallCooldownTicks() {
		int max = 0;
		for (MegumiShikigami type : MegumiShikigami.values()) {
			max = Math.max(max, recallCooldownTicks(type));
		}
		return max;
	}

	public static int maxDeathCooldownTicks() {
		int max = 0;
		for (MegumiShikigami type : MegumiShikigami.values()) {
			max = Math.max(max, deathCooldownTicks(type));
		}
		return max;
	}

	// --- Nue ---
	public static final double NUE_HEALTH = 24.0;
	public static final double NUE_ATTACK_DAMAGE = 4.0;
	public static final double NUE_MOVEMENT_SPEED = 0.38;
	public static final double NUE_FOLLOW_START_DISTANCE = 8.0;
	public static final double NUE_FOLLOW_STOP_DISTANCE = 3.0;
	public static final int NUE_MATERIALIZE_TICKS = 16;
	public static final int NUE_RECALL_TICKS = 12;
	public static final int NUE_RECALL_COOLDOWN_TICKS = 240;
	public static final int NUE_DEATH_COOLDOWN_TICKS = 400;
	public static final double NUE_DIVE_TRIGGER_RANGE = 24.0;
	public static final double NUE_DIVE_SPEED = 0.55;
	public static final int NUE_DIVE_TIMEOUT_TICKS = 40;
	public static final double NUE_IMPACT_RADIUS = 1.6;
	public static final double NUE_DIVE_DAMAGE = 5.0;
	public static final int NUE_CHARGE_COOLDOWN_TICKS = 60;
	/** How long the client holds the whole-body impact clip after a dive lands. */
	public static final int NUE_ATTACK_ACTION_TICKS = 10;
	public static final int NUE_STUN_TICKS = 12;
	public static final int NUE_STUN_TICKS_SOAKED = 20;
	public static final int NUE_SLOW_TICKS = 40;
	public static final int NUE_SLOW_TICKS_SOAKED = 60;
	public static final double NUE_SOAKED_DAMAGE_MULTIPLIER = 1.5;
	/** Nue hovers this far above the owner's head while off-command. */
	public static final double NUE_HOVER_HEIGHT = 3.0;

	// --- Toad (rows for the cooldown table + tongue; full block owns the rest) ---
	public static final int TOAD_RECALL_COOLDOWN_TICKS = 240;
	public static final int TOAD_DEATH_COOLDOWN_TICKS = 400;
	public static final double TOAD_HEALTH = 80.0;
	public static final double TOAD_ATTACK_DAMAGE = 4.0;
	public static final double TOAD_SPEED = 0.22;
	public static final double TOAD_FOLLOW_START = 6.0;
	public static final double TOAD_FOLLOW_STOP = 2.5;
	public static final int TOAD_MATERIALIZE_TICKS = 16;
	public static final int TOAD_RECALL_TICKS = 12;
	public static final double TOAD_TONGUE_RANGE = 12.0;
	public static final int TOAD_TONGUE_WINDUP_TICKS = 6;
	public static final int TOAD_TONGUE_COOLDOWN_TICKS = 100;
	public static final double TOAD_TONGUE_DAMAGE = 3.0;
	public static final double TOAD_TONGUE_PULL_SPEED = 0.65;
	public static final double TOAD_TONGUE_PULL_UP = 0.25;
	public static final int TOAD_TONGUE_STAGGER_TICKS = 8;

	// --- Rabbit Escape ---
	public static final int RABBITS_RECALL_COOLDOWN_TICKS = 120;
	public static final int RABBITS_DEATH_COOLDOWN_TICKS = 200;
	public static final int RABBITS_SWARM_SIZE = 10;
	public static final double RABBIT_HEALTH = 4.0;
	public static final double RABBIT_ATTACK_DAMAGE = 0.0;
	public static final double RABBIT_MOVEMENT_SPEED = 0.32;
	public static final double RABBITS_SPAWN_RADIUS = 2.2;
	public static final int RABBITS_MATERIALIZE_TICKS = 10;
	public static final int RABBITS_RECALL_TICKS = 10;
	public static final int RABBITS_LIFETIME_TICKS = 300;
	public static final int RABBITS_RESPAWN_INTERVAL_TICKS = 20;
	public static final int RABBITS_RESPAWN_BATCH = 2;
	public static final int RABBITS_BUMP_PERIOD_TICKS = 10;
	public static final double RABBITS_BUMP_RADIUS = 1.4;
	public static final double RABBITS_BUMP_KNOCKBACK = 0.35;
	public static final int RABBITS_BUMP_SLOWNESS_TICKS = 20;
	public static final int RABBIT_HOP_INTERVAL_TICKS = 10;

	// --- Max Elephant ---
	public static final int ELEPHANT_RECALL_COOLDOWN_TICKS = 260;
	public static final int ELEPHANT_DEATH_COOLDOWN_TICKS = 600;
	public static final double ELEPHANT_HEALTH = 120.0;
	public static final double ELEPHANT_ATTACK_DAMAGE = 6.0;
	public static final double ELEPHANT_MOVEMENT_SPEED = 0.18;
	public static final double ELEPHANT_FOLLOW_START_DISTANCE = 8.0;
	public static final double ELEPHANT_FOLLOW_STOP_DISTANCE = 4.0;
	public static final int ELEPHANT_MATERIALIZE_TICKS = 30;
	public static final int ELEPHANT_RECALL_TICKS = 16;
	public static final int ELEPHANT_JET_WINDUP_TICKS = 8;
	public static final int ELEPHANT_JET_DURATION_TICKS = 40;
	public static final int ELEPHANT_JET_PULSE_TICKS = 2;
	public static final double ELEPHANT_JET_LENGTH = 12.0;
	public static final double ELEPHANT_JET_HALF_WIDTH = 1.4;
	public static final double ELEPHANT_JET_DAMAGE = 1.0;
	public static final double ELEPHANT_JET_KNOCKBACK = 0.5;
	public static final int ELEPHANT_JET_SOAK_TICKS = 100;
	public static final int ELEPHANT_JET_COOLDOWN_TICKS = 220;
	/** Trunk origin sits this far in front of the elephant's eyes (geometry, kept out of the brain). */
	public static final double ELEPHANT_TRUNK_FORWARD = 1.2;
}