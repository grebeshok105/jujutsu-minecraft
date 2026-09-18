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

	/**
	 * What a body's own expiry costs, if it has one. Only Rabbit Escape ages out — the swarm spending
	 * its lifetime is its dismissal — so the other types answer zero and the row exists as the single
	 * place to price that dismissal separately from a manual recall.
	 */
	public static int expiryCooldownTicks(MegumiShikigami type) {
		return switch (type) {
			case RABBITS -> RABBITS_EXPIRY_COOLDOWN_TICKS;
			case DOGS, NUE, TOAD, ELEPHANT -> 0;
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

	// --- Toad (grab kit: the tongue is the visual, the grab is the mechanic) ---
	public static final int TOAD_RECALL_COOLDOWN_TICKS = 240;
	public static final int TOAD_DEATH_COOLDOWN_TICKS = 400;
	public static final double TOAD_HEALTH = 80.0;
	public static final double TOAD_ATTACK_DAMAGE = 4.0;
	public static final double TOAD_SPEED = 0.22;
	public static final double TOAD_FOLLOW_START = 6.0;
	public static final double TOAD_FOLLOW_STOP = 2.5;
	public static final int TOAD_MATERIALIZE_TICKS = 16;
	public static final int TOAD_RECALL_TICKS = 12;
	/** Rollback switch: false turns grabbing off without touching the brain. */
	public static final boolean TOAD_GRAB_ENABLED = true;
	public static final double TOAD_GRAB_RANGE = 12.0;
	public static final int TOAD_GRAB_WINDUP_TICKS = 6;
	public static final int TOAD_GRAB_HOLD_BASE = 90;
	public static final int TOAD_GRAB_HOLD_MIN = 60;
	public static final int TOAD_GRAB_HOLD_MAX = 100;
	/** Ticks of hold removed per point of the victim's max health. */
	public static final double TOAD_GRAB_HOLD_HP_PENALTY = 0.20;
	/** Ticks of hold removed per block of the victim's hitbox volume (players are exempt). */
	public static final double TOAD_GRAB_HOLD_SIZE_PENALTY = 6.0;
	public static final int TOAD_GRAB_COOLDOWN_TICKS = 100;
	/** The grab breaks when the body is dragged further than this from its owner. */
	public static final double TOAD_GRAB_BIND_RANGE = 16.0;
	public static final double TOAD_THROW_SPEED = 1.6;
	public static final double TOAD_THROW_LIFT = 0.35;
	/** Stagger applied on the throw, before the toss velocity — it makes the launch read as a hit. */
	public static final int TOAD_THROW_STAGGER_TICKS = 8;
	/** The held victim hangs this far in front of the toad. */
	public static final double TOAD_GRIP_OFFSET = 1.2;

	// --- Rabbit Escape ---
	public static final int RABBITS_RECALL_COOLDOWN_TICKS = 120;
	public static final int RABBITS_DEATH_COOLDOWN_TICKS = 200;
	public static final int RABBITS_EXPIRY_COOLDOWN_TICKS = 120;
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
	/**
	 * Locomotion for the swarm (issue #78): the bodies spawn inside the FollowOwner stop radius, so
	 * without a drift driver they never receive a wanted position and only animate a hop in place.
	 * Every interval a body picks a fresh point on a ring around its owner.
	 */
	public static final int RABBIT_DRIFT_INTERVAL_TICKS = 30;
	public static final double RABBIT_DRIFT_MIN_RADIUS = 1.2;
	public static final double RABBIT_DRIFT_MAX_RADIUS = 3.6;
	/**
	 * Past this distance from the owner a body reunites instead of drifting. Chasing a mark is not
	 * leashed by it: a mark is placed by the owner's sic or by the retaliation pass, and a body that
	 * refused to cross this line would ignore an order it was given.
	 */
	public static final double RABBIT_DRIFT_LEASH = 9.0;
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
	/** Every how many ticks the walking body sweeps the ground it is standing on. */
	public static final int ELEPHANT_FOOTPRINT_PERIOD_TICKS = 10;
	/** Horizontal speed below which the body counts as standing still (no sweep while parked). */
	public static final double ELEPHANT_FOOTPRINT_MIN_SPEED = 0.05;
	/** Blocks destroyed per sweep (a budget, not a target: fewer are destroyed when fewer qualify). */
	public static final int ELEPHANT_FOOTPRINT_BUDGET = 4;
	public static final double ELEPHANT_PRESENCE_RADIUS = 3.5;
	public static final int ELEPHANT_PRESENCE_PERIOD_TICKS = 10;
	public static final double ELEPHANT_PRESENCE_DAMAGE = 1.0;
	public static final double ELEPHANT_PRESENCE_KNOCKBACK = 1.1;
	public static final double ELEPHANT_PRESENCE_PUSH = 0.7;
	/** How long a hit on the owner (or the pack) keeps marking its author hostile. */
	public static final int ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS = 100;

	// --- coordination (issue #107): the shared combat context and the soft target-assignment weights ---
	/** One shared combat-context scan per owner per this many ticks — the scale answer for crowds. */
	public static final int COORDINATION_SCAN_TICKS = 5;
	/** Spec §16: full autonomy inside this radius of the owner. */
	public static final double AUTONOMY_RADIUS = 50.0;
	/** Past this distance a body drops its self-placed mark and walks home; manual marks are exempt. */
	public static final double RETURN_RADIUS = 60.0;
	/** Per-block distance cost: nearer candidates are preferred, all else equal. */
	public static final double COORD_DISTANCE_WEIGHT = 0.02;
	/** Danger weight: a tougher candidate scores higher, capped so it never pulls the whole pack. */
	public static final double COORD_DANGER_WEIGHT = 0.5;
	/** State-match bonuses: a soaked or held victim is the opening another body created (§8). */
	public static final double COORD_SOAKED_BONUS = 0.8;
	public static final double COORD_HELD_BONUS = 0.6;
	/** An ally's committed action on a target raises its worth — the pile-on signal of §11. */
	public static final double COORD_INTENT_BONUS = 1.5;
	/** A body under attack boosts its aggressor's worth for the whole pack (§14). The owner's own
	 * threat needs no factor: the retaliation pass marks it on every body before the coordinator
	 * runs, so a weight term could never decide a pick. */
	public static final double COORD_ALLY_THREAT_FACTOR = 1.2;
	/** A candidate another body already marks keeps this fraction of its score (soft, never a veto). */
	public static final double COORD_OCCUPANCY_FACTOR = 0.25;
	/** A challenger must beat the current mark by this factor — no per-scan thrash (§10). */
	public static final double COORD_HYSTERESIS = 1.25;
	/** Bounded randomness so equal situations can resolve differently (§10, R19). */
	public static final double COORD_JITTER = 0.15;

	// --- failure memory (issue #107 §15) ---
	/** How long a failed action stays de-weighted. */
	public static final long FAILURE_WINDOW_TICKS = 100;
	/** Weight never drops below this — a failed action is de-preferred, never banned. */
	public static final double FAILURE_FLOOR = 0.3;
	/** One fresh failure costs this much weight; two inside the window sit at the floor. */
	public static final double FAILURE_PENALTY = 0.6;
	/** Below this weight the pounce launch skips the attempt entirely this tick (R16). Production
	 * reads {@link MegumiProfile#POUNCE_RETRY_MIN_WEIGHT} — same gate, one constant. */
}