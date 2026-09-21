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
	public static final double DISK_INNER_RADIUS = 6.5;
	/** Disk outer edge: ~30 blocks radius → the ring reaches ~2.2x past the capture shadow,
	 * so the equatorial band visibly wraps the dome instead of hiding inside it. */
	public static final double DISK_OUTER_RADIUS = 30.0;
	/** Half-thickness of the disk slab — thin enough that the band reads as a crisp line. */
	public static final double DISK_HALF_THICKNESS = 0.22;

	/** Spawn distance ahead of the player's eye, in blocks. */
	public static final double SPAWN_DISTANCE = 30.0;
	public static final double DISK_TILT_RADIANS = Math.toRadians(20.0);
}
