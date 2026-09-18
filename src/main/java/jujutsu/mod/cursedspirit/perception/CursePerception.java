package jujutsu.mod.cursedspirit.perception;

import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
	 * Non-players are always {@code true}; server players may receive the authoritative
	 * incident override, while local clients read their mirrored bit in client render gates.
	 */
	public static boolean perceives(Entity entity) {
		if (entity instanceof Player player) {
			boolean vesselPerceives = JujutsuCharacters.definition(CharacterSelectionView.of(player))
					.cursePerception().perceiveCurses();
			if (player instanceof ServerPlayer) {
				return vesselPerceives
						|| jujutsu.mod.cursedincident.IncidentPerceptionBridge.perceivesOverride(player);
			}
			return vesselPerceives;
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
	 * The sensory pair rule: {@code false} exactly when one side is a non-perceiving
	 * player and the other is a curse subject. This is the SEE/HEAR right only — use it
	 * for sensory premises (ability VFX/target acquisition inside effects). Anything that
	 * can hurt, displace, acquire or be swung at must read {@link #interacts} instead.
	 */
	public static boolean mayTouch(Entity actor, Entity subject) {
		return !excluded(actor, subject, CursePerception::perceives)
				&& !excluded(subject, actor, CursePerception::perceives);
	}

	/**
	 * The interaction pair rule: {@code false} exactly when one side is a player without
	 * the interaction right and the other is a curse subject. Every contact gate —
	 * damage in both directions, melee swings, push, collision, target acquisition and
	 * retaliation — reduces to this, so a (perceives, not-interacts) vessel can see the
	 * curse but never fight it in either direction.
	 */
	public static boolean interacts(Entity actor, Entity subject) {
		return !excluded(actor, subject, CursePerception::canInteract)
				&& !excluded(subject, actor, CursePerception::canInteract);
	}

	private static boolean excluded(Entity actor, Entity other, Predicate<Entity> right) {
		return actor instanceof Player && !right.test(actor) && isSubject(other);
	}

	/**
	 * Resolves the accountable party behind an entity: projectiles defer to their owner,
	 * ownable bodies (tamed pets, summons) to theirs, until a {@link Player} or a root
	 * non-ownable entity is reached — the loop is depth-capped so a cyclic owner graph
	 * can never hang a damage check. A {@code null} input (unowned projectile, dispenser
	 * arrow) stays {@code null}: no player is responsible for it.
	 */
	public static Entity responsibleParty(Entity entity) {
		Entity current = entity;
		for (int hop = 0; current != null && !(current instanceof Player)
				&& hop < OWNER_CHAIN_LIMIT; hop++) {
			Entity next = ownerOf(current);
			if (next == current) {
				break;
			}
			current = next;
		}
		return current;
	}

	private static Entity ownerOf(Entity entity) {
		if (entity instanceof Projectile projectile) {
			return projectile.getOwner();
		}
		if (entity instanceof OwnableEntity ownable) {
			return ownable.getOwner();
		}
		return null;
	}

	private static final int OWNER_CHAIN_LIMIT = 8;
}
