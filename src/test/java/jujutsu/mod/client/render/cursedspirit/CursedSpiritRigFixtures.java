package jujutsu.mod.client.render.cursedspirit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritBludAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritButcherAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritFloatingCurseAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritGulberAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritGuzzlerAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritKelvinAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritProwlerAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritWalkingBedAnimations;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritWistiverAnimations;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritBludModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritButcherModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritFloatingCurseModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritGulberModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritGuzzlerModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritKelvinModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritProwlerModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritWalkingBedModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritWistiverModel;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Frozen port literals shared by the two cursed-spirit contract tests: variant to
 * animation class, variant to body-layer supplier, and the exact clip bone sets from
 * the implementation plan's frozen clip bone map.
 */
final class CursedSpiritRigFixtures {
	private CursedSpiritRigFixtures() {
	}

	static final Map<CursedSpiritVariant, Class<?>> ANIMATIONS = new EnumMap<>(CursedSpiritVariant.class);
	static final Map<CursedSpiritVariant, Supplier<LayerDefinition>> LAYERS =
			new EnumMap<>(CursedSpiritVariant.class);
	/** Variant to clip name to exact bone-name set (frozen clip bone map). */
	static final Map<CursedSpiritVariant, Map<String, Set<String>>> BONES =
			new EnumMap<>(CursedSpiritVariant.class);
	/** Clips that are authored non-looping, as {@code "VARIANT/CLIP"}. */
	static final Set<String> NON_LOOPING = Set.of(
			"PROWLER/ATTACK",
			"FLOATING_CURSE/ATTACK",
			"KELVIN/ATTACK",
			"GUZZLER/ATTACK",
			"BLUD/ATTACK");

	static {
		ANIMATIONS.put(CursedSpiritVariant.PROWLER, CursedSpiritProwlerAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.FLOATING_CURSE, CursedSpiritFloatingCurseAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.GULBER, CursedSpiritGulberAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.KELVIN, CursedSpiritKelvinAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.BUTCHER, CursedSpiritButcherAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.GUZZLER, CursedSpiritGuzzlerAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.BLUD, CursedSpiritBludAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.WALKING_BED, CursedSpiritWalkingBedAnimations.class);
		ANIMATIONS.put(CursedSpiritVariant.WISTIVER, CursedSpiritWistiverAnimations.class);

		LAYERS.put(CursedSpiritVariant.PROWLER, CursedSpiritProwlerModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.FLOATING_CURSE, CursedSpiritFloatingCurseModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.GULBER, CursedSpiritGulberModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.KELVIN, CursedSpiritKelvinModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.BUTCHER, CursedSpiritButcherModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.GUZZLER, CursedSpiritGuzzlerModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.BLUD, CursedSpiritBludModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.WALKING_BED, CursedSpiritWalkingBedModel::createBodyLayer);
		LAYERS.put(CursedSpiritVariant.WISTIVER, CursedSpiritWistiverModel::createBodyLayer);

		put(CursedSpiritVariant.PROWLER, "IDLE", "body", "head", "in_left_leg", "in_right_leg", "left_arm",
				"right_arm");
		put(CursedSpiritVariant.PROWLER, "WALK", "body", "head", "in_left_leg", "in_right_leg", "left_arm",
				"right_arm");
		put(CursedSpiritVariant.PROWLER, "ATTACK", "body", "head", "in_left_leg", "in_right_leg", "left_arm",
				"right_arm");
		put(CursedSpiritVariant.PROWLER, "SCREAMER", "body", "flatter", "head", "left_arm", "right_arm");

		put(CursedSpiritVariant.FLOATING_CURSE, "IDLE", "body", "bone", "head");
		put(CursedSpiritVariant.FLOATING_CURSE, "ATTACK", "body", "head", "spine");
		put(CursedSpiritVariant.FLOATING_CURSE, "SCREAMER", "body", "flater", "head");

		put(CursedSpiritVariant.GULBER, "IDLE", "body2", "head");
		put(CursedSpiritVariant.GULBER, "WALK", "body2", "head", "left_foot", "left_foot2", "right_foot",
				"right_foot2");
		put(CursedSpiritVariant.GULBER, "ATTACK", "body", "body2", "bone", "head", "left_foot", "left_foot2",
				"right_foot", "right_foot2");

		put(CursedSpiritVariant.KELVIN, "IDLE", "body", "head", "leftarm", "rightarm");
		put(CursedSpiritVariant.KELVIN, "WALK", "body", "head", "left_leg", "leftarm", "right_leg", "rightarm");
		put(CursedSpiritVariant.KELVIN, "ATTACK", "body", "bone6", "bone8", "head", "left_leg", "leftarm",
				"rightarm");
		put(CursedSpiritVariant.KELVIN, "SCREAMER", "body", "bone4", "flater", "head", "leftarm", "left_leg",
				"rightarm", "right_leg");

		put(CursedSpiritVariant.BUTCHER, "IDLE", "body", "head", "left_arm", "right_arm");
		put(CursedSpiritVariant.BUTCHER, "WALK", "body", "bone", "head", "left_arm", "left_leg", "right_arm",
				"right_leg");
		put(CursedSpiritVariant.BUTCHER, "ATTACK", "body", "bone", "flater", "head", "left_arm", "left_leg",
				"right_arm");
		put(CursedSpiritVariant.BUTCHER, "SCREAMER", "body", "flater", "head", "left_arm", "left_leg",
				"right_arm", "right_leg");

		put(CursedSpiritVariant.GUZZLER, "IDLE", "body2", "jaw", "left_arm");
		put(CursedSpiritVariant.GUZZLER, "WALK", "body2", "jaw", "left_arm", "left_leg", "right_arm",
				"right_leg");
		put(CursedSpiritVariant.GUZZLER, "ATTACK", "body2", "jaw", "left_arm", "right_arm");

		put(CursedSpiritVariant.BLUD, "IDLE", "bone2", "bone4", "chest", "head", "right_arm", "stain");
		put(CursedSpiritVariant.BLUD, "WALK", "bone", "chest", "head", "left_arm", "stain");
		put(CursedSpiritVariant.BLUD, "ATTACK", "bone", "bone2", "chest", "head");
		put(CursedSpiritVariant.BLUD, "SCREAMER", "bone", "bone2", "bone6", "chest", "flater", "head",
				"left_arm", "right_arm", "stain");

		put(CursedSpiritVariant.WALKING_BED, "IDLE", "body", "bone7", "bone9", "head", "head2", "left_arm",
				"right_arm");
		put(CursedSpiritVariant.WALKING_BED, "WALK", "body", "left_arm", "left_leg", "right_arm", "right_leg");
		put(CursedSpiritVariant.WALKING_BED, "ATTACK", "arm", "arm4", "body", "bone", "bone6", "flatter",
				"head", "head2", "left_arm", "left_leg", "right_arm");
		put(CursedSpiritVariant.WALKING_BED, "SCREAMER", "body", "flatter", "head", "left_arm", "left_leg",
				"right_arm", "right_leg");

		put(CursedSpiritVariant.WISTIVER, "IDLE", "bod", "bone2", "head", "left_arm", "right_arm");
		put(CursedSpiritVariant.WISTIVER, "WALK", "body");
		put(CursedSpiritVariant.WISTIVER, "ATTACK", "bod", "bone", "bone2", "head", "jaw", "jaw2", "left_arm",
				"left_arm2", "left_front_arm", "right_arm", "right_arm2", "right_front_arm", "top_jaw",
				"top_jaw2");
		put(CursedSpiritVariant.WISTIVER, "SCREAMER", "bod", "flater", "head", "left_arm", "right_arm");
		put(CursedSpiritVariant.WISTIVER, "GRAVE", "bod", "head", "jaw", "left_arm", "left_front_arm",
				"right_arm", "top_jaw");
	}

	private static void put(CursedSpiritVariant variant, String clip, String... bones) {
		BONES.computeIfAbsent(variant, key -> new LinkedHashMap<>())
				.put(clip, new LinkedHashSet<>(Set.of(bones)));
	}

	/** All {@code AnimationDefinition} constants of an animation class, by field name. */
	static Map<String, AnimationDefinition> clipsOf(Class<?> animations) {
		Map<String, AnimationDefinition> clips = new LinkedHashMap<>();
		for (Field field : animations.getFields()) {
			if (field.getType() == AnimationDefinition.class
					&& Modifier.isStatic(field.getModifiers())) {
				try {
					clips.put(field.getName(), (AnimationDefinition) field.get(null));
				} catch (IllegalAccessException e) {
					throw new AssertionError(e);
				}
			}
		}
		return clips;
	}
}
