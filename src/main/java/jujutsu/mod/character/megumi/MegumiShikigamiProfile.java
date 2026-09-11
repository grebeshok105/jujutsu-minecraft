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

	// --- Rabbit Escape ---
	public static final int RABBITS_RECALL_COOLDOWN_TICKS = 120;
	public static final int RABBITS_DEATH_COOLDOWN_TICKS = 200;

	// --- Max Elephant ---
	public static final int ELEPHANT_RECALL_COOLDOWN_TICKS = 260;
	public static final int ELEPHANT_DEATH_COOLDOWN_TICKS = 600;
}