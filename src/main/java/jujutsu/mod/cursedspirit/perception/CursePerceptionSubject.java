package jujutsu.mod.cursedspirit.perception;

/**
 * Marks an entity as a curse-perception subject: a cursed spirit, its projectile, its zone, or any
 * future curse-side body that must not exist for non-perceiving players (issue #80).
 *
 * <p>The marker is the compile-time half of subjecthood. The data-driven half is the
 * {@code jujutsumod:curse_perception_subject} entity-type tag, read through
 * {@link CursePerception#isSubject}: a future subject that cannot touch this interface (or must
 * not gain a compile dependency on it) joins by tag alone, with no gate changes.
 */
public interface CursePerceptionSubject {
}
