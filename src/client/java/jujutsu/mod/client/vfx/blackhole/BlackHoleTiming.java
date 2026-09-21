package jujutsu.mod.client.vfx.blackhole;

/**
 * Pure-math timeline of a single black hole: prelude, appearance, stable, disappearance, aftermath.
 *
 * <p>Mirrors {@code DomainSphereTiming}: no Minecraft types, every envelope is a function of
 * {@code ageTicks}, so the whole lifecycle is unit-testable without a GL context. The shader and
 * the sound driver consume only these envelopes — they never re-derive phase logic themselves.
 *
 * <p>Phase lengths (ticks): prelude 18 (0.9 s), appearance 70 (3.5 s), stable configurable
 * (default 140 = 7 s), disappearance 12 (0.6 s — a coordinated implosion: the object collapses
 * over ~8 ticks while the cosmos is sucked inward and the jolt bends the frame), aftermath 50
 * (2.5 s, of which the first 40 are the mandated ~2 s of near-total silence).
 */
public record BlackHoleTiming(int stableTicks, long seed) {
	public static final int PRELUDE_TICKS = 18;
	public static final int APPEAR_TICKS = 70;
	public static final int DISAPPEAR_TICKS = 12;
	public static final int AFTERMATH_TICKS = 50;
	public static final int SILENCE_TICKS = 40;
	public static final int DEFAULT_STABLE_TICKS = 140;
	public static final int MIN_STABLE_TICKS = 20;
	public static final int MAX_STABLE_TICKS = 1200;

	public enum Phase {
		PRELUDE,
		APPEAR,
		STABLE,
		DISAPPEAR,
		AFTERMATH,
		EXPIRED
	}

	public BlackHoleTiming {
		stableTicks = Math.max(MIN_STABLE_TICKS, Math.min(MAX_STABLE_TICKS, stableTicks));
	}

	public static BlackHoleTiming defaults(long seed) {
		return new BlackHoleTiming(DEFAULT_STABLE_TICKS, seed);
	}

	public int totalTicks() {
		return PRELUDE_TICKS + APPEAR_TICKS + stableTicks + DISAPPEAR_TICKS + AFTERMATH_TICKS;
	}

	public int appearStart() {
		return PRELUDE_TICKS;
	}

	public int stableStart() {
		return PRELUDE_TICKS + APPEAR_TICKS;
	}

	public int disappearStart() {
		return PRELUDE_TICKS + APPEAR_TICKS + stableTicks;
	}

	public int aftermathStart() {
		return PRELUDE_TICKS + APPEAR_TICKS + stableTicks + DISAPPEAR_TICKS;
	}

	public Phase phase(float ageTicks) {
		if (ageTicks < PRELUDE_TICKS) {
			return Phase.PRELUDE;
		}
		if (ageTicks < stableStart()) {
			return Phase.APPEAR;
		}
		if (ageTicks < disappearStart()) {
			return Phase.STABLE;
		}
		if (ageTicks < aftermathStart()) {
			return Phase.DISAPPEAR;
		}
		if (ageTicks < totalTicks()) {
			return Phase.AFTERMATH;
		}
		return Phase.EXPIRED;
	}

	public boolean isExpired(float ageTicks) {
		return ageTicks >= totalTicks();
	}

	/**
	 * Master intensity of the whole phenomenon, 0..1: ramps through the prelude, slams to full at
	 * the appearance (the hole must feel complete almost immediately), holds through the stable
	 * phase, cuts at the disappearance jolt, and bleeds off through the aftermath.
	 */
	public float intensity(float ageTicks) {
		switch (phase(ageTicks)) {
			case PRELUDE -> {
				return 0.22f * smooth01(ageTicks / PRELUDE_TICKS);
			}
			case APPEAR -> {
				float p = (ageTicks - PRELUDE_TICKS) / APPEAR_TICKS;
				// 0.22 -> 1.0 over the first 18% of the appearance, then flat: the reveal is a snap,
				// not a build.
				float ramp = smooth01(Math.min(1.0f, p / 0.18f));
				return 0.22f + 0.78f * ramp;
			}
			case STABLE -> {
				return 1.0f;
			}
			case DISAPPEAR -> {
				// The implosion keeps the world-effect alive while the object collapses —
				// cosmos, vignette and shade die WITH the hole, not before it.
				float c = collapse(ageTicks);
				return 0.25f + 0.75f * c;
			}
			case AFTERMATH -> {
				float p = (ageTicks - aftermathStart()) / AFTERMATH_TICKS;
				return 0.16f * (1.0f - smooth01(p));
			}
			default -> {
				return 0.0f;
			}
		}
	}

	/**
	 * Screen-space lensing strength multiplier. Follows {@link #intensity} but adds the stable-phase
	 * bursts and the disappearance jolt spike on top, so the warp itself carries the irregular
	 * events instead of a separate channel.
	 */
	public float lensStrength(float ageTicks) {
		float base = intensity(ageTicks);
		if (phase(ageTicks) == Phase.DISAPPEAR) {
			// The implosion sucks space inward: the pull peaks with the collapse, not after it.
			return jolt(ageTicks) * 1.6f + collapse(ageTicks) * 0.4f;
		}
		if (phase(ageTicks) == Phase.AFTERMATH) {
			return jolt(ageTicks) * 0.8f;
		}
		return base * (1.0f + 0.55f * burstAt(ageTicks)) * pulsation(ageTicks);
	}
	/** Accretion-disk luminance multiplier: identical envelope, bursts brighten the disk too. */
	public float diskIntensity(float ageTicks) {
		if (phase(ageTicks) == Phase.DISAPPEAR) {
			// Implosion flash: the disk flares as it collapses, then dies with it.
			float c = collapse(ageTicks);
			return c * (1.0f + 1.5f * (1.0f - c));
		}
		return intensity(ageTicks) * (1.0f + 0.8f * burstAt(ageTicks));
	}

	/**
	 * Object collapse, 1→0: during APPEAR the hole grows in with the reveal ramp (snap, not a
	 * build); during DISAPPEAR it implodes over ~8 ticks — fast enough to read as a snap, slow
	 * enough to see the disk and shadow shrink into the point. Outside those phases the object
	 * is always whole.
	 */
	public float collapse(float ageTicks) {
		if (phase(ageTicks) == Phase.APPEAR) {
			float p = (ageTicks - PRELUDE_TICKS) / APPEAR_TICKS;
			return Math.max(0.15f, smooth01(Math.min(1.0f, p / 0.18f)));
		}
		if (phase(ageTicks) == Phase.DISAPPEAR) {
			float p = (ageTicks - disappearStart()) / 8.0f;
			return 1.0f - smooth01(Math.min(1.0f, p));
		}
		if (phase(ageTicks) == Phase.AFTERMATH || phase(ageTicks) == Phase.EXPIRED) {
			// The object stays gone — the aftermath is only the spatial shimmer, never the dome.
			return 0.0f;
		}
		return 1.0f;
	}

	/** World desaturation amount, 0..1: the world dies toward monochrome as the hole asserts itself. */
	public float desaturation(float ageTicks) {
		return Math.min(1.0f, intensity(ageTicks) * 0.92f);
	}

	/**
	 * Disappearance jolt, 0..1..0 over {@link #DISAPPEAR_TICKS}: a single sharp spatial
	 * displacement spike peaking at ~35% through the window — the implosion kick. In AFTERMATH
	 * it degrades into a decaying shimmer so the emptied point keeps breathing for a moment
	 * instead of snapping back to a clean frame.
	 */
	public float jolt(float ageTicks) {
		if (phase(ageTicks) == Phase.DISAPPEAR) {
			float p = (ageTicks - disappearStart()) / DISAPPEAR_TICKS;
			float up = smooth01(Math.min(1.0f, p / 0.35f));
			float down = 1.0f - smooth01(Math.max(0.0f, (p - 0.35f) / 0.65f));
			return up * down;
		}
		if (phase(ageTicks) == Phase.AFTERMATH) {
			float p = (ageTicks - aftermathStart()) / AFTERMATH_TICKS;
			return 0.30f * (1.0f - smooth01(p));
		}
		return 0.0f;
	}

	/**
	 * Slow continuous breathing, 0.94..1.06: two incommensurate sines so the motion never reads as a
	 * short loop.
	 */
	public float pulsation(float ageTicks) {
		float t = ageTicks * 0.05f;
		return 1.0f + 0.04f * (float) Math.sin(t * 1.7 + 1.3) + 0.02f * (float) Math.sin(t * 0.61 + 4.1);
	}

	/**
	 * Irregular stable-phase bursts, 0..1: a deterministic schedule derived from {@code seed}.
	 * Bursts are spaced 34..118 ticks apart with random amplitude and width, so no periodicity is
	 * ever perceivable. Outside the stable phase the schedule still evaluates but is gated to zero.
	 */
	public float burstAt(float ageTicks) {
		if (phase(ageTicks) != Phase.STABLE) {
			return 0.0f;
		}
		float local = ageTicks - stableStart();
		float sum = 0.0f;
		// Walk the schedule; at most a handful of bursts can overlap a given instant.
		float cursor = firstBurstOffset();
		for (int i = 0; i < 32 && cursor < local + 130.0f; i++) {
			float width = 10.0f + 14.0f * hash01(i * 2 + 1);
			float amplitude = 0.35f + 0.65f * hash01(i * 2 + 2);
			float d = (local - cursor) / width;
			if (d > 0.0f && d < 1.0f) {
				sum += amplitude * (float) Math.sin(d * Math.PI);
			}
			cursor += 34.0f + 84.0f * hash01(i * 2 + 3);
		}
		return Math.min(1.0f, sum);
	}

	private float firstBurstOffset() {
		return 24.0f + 40.0f * hash01(0);
	}

	/** Deterministic per-burst hash of the seed and the schedule index, 0..1. */
	private float hash01(int n) {
		long h = seed ^ (0x9E3779B97F4A7C15L * (n + 1));
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h & 0xFFFFFF) / 16777216.0f;
	}

	/**
	 * World-audio duck amount, 0..1: how strongly ordinary Minecraft sound is suppressed. Ramps in
	 * the prelude, holds through the stable phase, becomes total during the silence window, then
	 * releases over the rest of the aftermath.
	 */
	public float duckAmount(float ageTicks) {
		switch (phase(ageTicks)) {
			case PRELUDE -> {
				return 0.85f * smooth01(ageTicks / PRELUDE_TICKS);
			}
			case APPEAR, STABLE -> {
				return 0.9f;
			}
			case DISAPPEAR -> {
				return 1.0f;
			}
			case AFTERMATH -> {
				float local = ageTicks - aftermathStart();
				if (local < SILENCE_TICKS) {
					return 1.0f;
				}
				float p = (local - SILENCE_TICKS) / (AFTERMATH_TICKS - SILENCE_TICKS);
				return 1.0f - smooth01(p);
			}
			default -> {
				return 0.0f;
			}
		}
	}

	/** True while the mandated ~2 s post-disappearance silence holds (plus the jolt itself). */
	public boolean silenceActive(float ageTicks) {
		Phase p = phase(ageTicks);
		if (p == Phase.DISAPPEAR) {
			return true;
		}
		return p == Phase.AFTERMATH && (ageTicks - aftermathStart()) < SILENCE_TICKS;
	}

	private static float smooth01(float x) {
		float c = Math.max(0.0f, Math.min(1.0f, x));
		return c * c * (3.0f - 2.0f * c);
	}
}
