package jujutsu.mod.cursedspirit.perception;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterSelectionView;
import jujutsu.mod.character.JujutsuCharacters;

/**
 * The single decision point for curse perception (issue #80, contract C1).
 *
 * <p>Two load-bearing rules, both pinned by tests:
 * <ul>
 *   <li><b>"Non-mage" exists only for players.</b> A non-player ({@code perceives}) is always
 *   {@code true}: otherwise a curse would stop interacting with the world (other mobs, other
 *   curses, arrows) the moment a data lookup failed. Gates apply to the pair
 *   (non-perceiving <b>player</b>) ↔ (curse subject) and never to curse↔curse, curse↔mob or
 *   curse↔block pairs.</li>
 *   <li><b>{@link #isSubject} is the only subject test.</b> Marker interface or the
 *   {@code jujutsumod:curse_perception_subject} entity-type tag — every gate (target, damage,
 *   melee, physics, tracking, sound, render, VFX) reads this method, never
 *   {@code instanceof CursedSpiritEntity}, so future curse projectiles/zones inherit the rule
 *   without gate changes.</li>
 * </ul>
 */
public final class CursePerception {
	/** Data-driven half of subjecthood; the marker half is {@link CursePerceptionSubject}. */
	public static final TagKey<EntityType<?>> SUBJECT_TAG =
			TagKey.create(Registries.ENTITY_TYPE, JujutsuMod.id("curse_perception_subject"));

	private CursePerception() {}

	/** Marker or tag — the one subject test every gate uses. */
	public static boolean isSubject(Entity entity) {
		if (entity == null) {
			return false;
		}
		// Marker first: a single instanceof short-circuits the tag lookup on the hot paths
		// (doPush runs per living pair per tick). The tag is the extension point for future
		// subjects that cannot implement the marker.
		return entity instanceof CursePerceptionSubject || entity.getType().is(SUBJECT_TAG);
	}

	/**
	 * Whether the entity perceives curses at all (sees, hears, is tracked by them).
	 * Non-players are always {@code true}; players read their vessel's flags through the
	 * authoritative selection on the server and the mirrored selection on the client.
	 */
	public static boolean perceives(Entity entity) {
		if (entity instanceof Player player) {
			return JujutsuCharacters.definition(CharacterSelectionView.of(player))
					.cursePerception().perceiveCurses();
		}
		return true;
	}

	/**
	 * Whether the entity may fight curses (be acquired, damage, be damaged, collide).
	 * Today every perceiving vessel also interacts; the fields stay separate for #83.
	 */
	public static boolean canInteract(Entity entity) {
		if (entity instanceof Player player) {
			return JujutsuCharacters.definition(CharacterSelectionView.of(player))
					.cursePerception().interactWithCurses();
		}
		return true;
	}

	/**
	 * The pair rule every gate reduces to: {@code false} exactly when one side is a
	 * non-perceiving player and the other is a curse subject.
	 */
	public static boolean mayTouch(Entity actor, Entity subject) {
		return !excluded(actor, subject) && !excluded(subject, actor);
	}

	private static boolean excluded(Entity actor, Entity other) {
		return actor instanceof Player && !perceives(actor) && isSubject(other);
	}
}
