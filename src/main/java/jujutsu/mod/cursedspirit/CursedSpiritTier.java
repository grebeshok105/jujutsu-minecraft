package jujutsu.mod.cursedspirit;

/**
 * The three hostile cursed-spirit gameplay tiers. Gameplay (stats, AI parameters, spawn rows) is
 * tier-owned; presentation (model, texture, animations, sounds) is chosen per variant inside the
 * tier — no per-variant gameplay.
 */
public enum CursedSpiritTier {
	LESSER,
	COMMON,
	GREATER
}
