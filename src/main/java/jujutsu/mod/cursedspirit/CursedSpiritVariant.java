package jujutsu.mod.cursedspirit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.sounds.SoundEvent;
import jujutsu.mod.registry.JujutsuSounds;

/**
 * The nine frozen presentation variants. A variant owns look and voice only — tier, stats and AI
 * parameters always come from {@link CursedSpiritTier} / {@link CursedSpiritProfile}.
 */
public enum CursedSpiritVariant {
	PROWLER(CursedSpiritTier.LESSER, 3, 0.85f, 0.0f,
			JujutsuSounds.CURSED_PROWLER_AMBIENT, JujutsuSounds.CURSED_PROWLER_HURT,
			JujutsuSounds.CURSED_PROWLER_DEATH, JujutsuSounds.CURSED_PROWLER_SCREAM),
	FLOATING_CURSE(CursedSpiritTier.LESSER, 2, 0.8f, 0.35f,
			JujutsuSounds.CURSED_FLOATING_CURSE_AMBIENT, JujutsuSounds.CURSED_FLOATING_CURSE_HURT,
			JujutsuSounds.CURSED_FLOATING_CURSE_DEATH, JujutsuSounds.CURSED_FLOATING_CURSE_SCREAM),
	GULBER(CursedSpiritTier.LESSER, 2, 0.9f, 0.0f,
			JujutsuSounds.CURSED_GULBER_AMBIENT, JujutsuSounds.CURSED_GULBER_HURT,
			JujutsuSounds.CURSED_GULBER_DEATH, null),
	KELVIN(CursedSpiritTier.COMMON, 3, 0.95f, 0.0f,
			JujutsuSounds.CURSED_KELVIN_AMBIENT, JujutsuSounds.CURSED_KELVIN_HURT,
			JujutsuSounds.CURSED_KELVIN_DEATH, JujutsuSounds.CURSED_KELVIN_SCREAM),
	BUTCHER(CursedSpiritTier.COMMON, 2, 0.9f, 0.0f,
			JujutsuSounds.CURSED_BUTCHER_AMBIENT, JujutsuSounds.CURSED_BUTCHER_HURT,
			JujutsuSounds.CURSED_BUTCHER_DEATH, JujutsuSounds.CURSED_BUTCHER_SCREAM),
	GUZZLER(CursedSpiritTier.COMMON, 2, 1.0f, 0.0f,
			JujutsuSounds.CURSED_GUZZLER_AMBIENT, JujutsuSounds.CURSED_GUZZLER_HURT,
			JujutsuSounds.CURSED_GUZZLER_DEATH, null),
	BLUD(CursedSpiritTier.COMMON, 1, 0.9f, 0.0f,
			JujutsuSounds.CURSED_BLUD_AMBIENT, JujutsuSounds.CURSED_BLUD_HURT,
			JujutsuSounds.CURSED_BLUD_DEATH, JujutsuSounds.CURSED_BLUD_SCREAM),
	WALKING_BED(CursedSpiritTier.GREATER, 1, 0.9f, 0.0f,
			JujutsuSounds.CURSED_WALKING_BED_AMBIENT, JujutsuSounds.CURSED_WALKING_BED_HURT,
			JujutsuSounds.CURSED_WALKING_BED_DEATH, JujutsuSounds.CURSED_WALKING_BED_SCREAM),
	WISTIVER(CursedSpiritTier.GREATER, 1, 0.85f, 0.0f,
			JujutsuSounds.CURSED_WISTIVER_AMBIENT, JujutsuSounds.CURSED_WISTIVER_HURT,
			JujutsuSounds.CURSED_WISTIVER_DEATH, JujutsuSounds.CURSED_WISTIVER_SCREAM);

	private final CursedSpiritTier tier;
	private final int weight;
	private final float renderScale;
	private final float renderOffsetY;
	private final SoundEvent ambientSound;
	private final SoundEvent hurtSound;
	private final SoundEvent deathSound;
	private final SoundEvent screamSound;

	CursedSpiritVariant(CursedSpiritTier tier, int weight, float renderScale, float renderOffsetY,
			SoundEvent ambientSound, SoundEvent hurtSound, SoundEvent deathSound, SoundEvent screamSound) {
		this.tier = tier;
		this.weight = weight;
		this.renderScale = renderScale;
		this.renderOffsetY = renderOffsetY;
		this.ambientSound = ambientSound;
		this.hurtSound = hurtSound;
		this.deathSound = deathSound;
		this.screamSound = screamSound;
	}

	public CursedSpiritTier tier() {
		return tier;
	}

	/** Lowercase id, e.g. {@code "prowler"} — also the NBT persistence id. */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Weighted roll inside the tier. */
	public int weight() {
		return weight;
	}

	/** Client presentation scale (initial; tuned by eye in Block 3). */
	public float renderScale() {
		return renderScale;
	}

	/** Client presentation vertical offset (only the floater hovers). */
	public float renderOffsetY() {
		return renderOffsetY;
	}

	public String texture() {
		return "textures/entity/cursed/cursed_" + id() + ".png";
	}

	public SoundEvent ambientSound() {
		return ambientSound;
	}

	public SoundEvent hurtSound() {
		return hurtSound;
	}

	public SoundEvent deathSound() {
		return deathSound;
	}

	/** Nullable: GULBER and GUZZLER have no scream channel in the frozen table. */
	public SoundEvent screamSound() {
		return screamSound;
	}

	/** All variants of one tier, in roster order. */
	public static List<CursedSpiritVariant> variantsOf(CursedSpiritTier tier) {
		List<CursedSpiritVariant> variants = new ArrayList<>();
		for (CursedSpiritVariant variant : values()) {
			if (variant.tier == tier) {
				variants.add(variant);
			}
		}
		return variants;
	}

	/** Lookup by {@link #id()}; empty when unknown (the entity re-rolls instead). */
	public static java.util.Optional<CursedSpiritVariant> byId(String id) {
		for (CursedSpiritVariant variant : values()) {
			if (variant.id().equals(id)) {
				return java.util.Optional.of(variant);
			}
		}
		return java.util.Optional.empty();
	}
}
