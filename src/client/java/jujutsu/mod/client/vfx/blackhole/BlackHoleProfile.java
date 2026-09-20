package jujutsu.mod.client.vfx.blackhole;

/**
 * The one directed preset: every visual constant of the black hole lives here so the scene is
 * tuned in a single place. Not a config system — a score sheet.
 */
public final class BlackHoleProfile {
	private BlackHoleProfile() {}

	/** Event-horizon radius in blocks. The capture shadow reads ~2.6x wider — ~27 blocks across. */
	public static final double HORIZON_RADIUS = 5.2;
	/** Disk inner edge (just outside the photon ring). */
	public static final double DISK_INNER_RADIUS = 6.2;
	/** Disk outer edge: ~13 blocks radius → ~26 blocks of luminous span. */
	public static final double DISK_OUTER_RADIUS = 13.0;
	/** Half-thickness of the disk slab. */
	public static final double DISK_HALF_THICKNESS = 0.55;

	/** Spawn distance ahead of the player's eye, in blocks. */
	public static final double SPAWN_DISTANCE = 30.0;
	/** Disk tilt off the vertical axis, radians — near-horizontal disk read edge-on, Gargantua-style. */
	public static final double DISK_TILT_RADIANS = Math.toRadians(14.0);
}
