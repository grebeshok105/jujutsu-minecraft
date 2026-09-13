package jujutsu.mod.cursedspirit;

/**
 * Server-side grade-aura policy (R59): the strength hint readable from afar.
 * Pure data — the client emitter ({@code CursedSpiritAura}) reads this, the
 * server syncs only {@code DATA_GRADE} (never HP/damage/speed numbers).
 *
 * <p><b>BALANCE:</b> densities, colours, sizes and the soul-flame cadence
 * below are the spike's opening bid, not the verdict. The plan deliberately
 * leaves the final look open: spike → mandatory live review (three grades
 * side by side, frames; pass = grade-3 density ≥ 2× grade-5 from 16 blocks
 * and grades told apart by colour) → decision recorded in
 * {@code block-2-report.md}. The live lane belongs to Main; until the
 * verdict lands every number here stays {@code BALANCE}.
 */
public final class CursedSpiritAuraRuntime {
	private CursedSpiritAuraRuntime() {}

	/**
	 * The aura tier: 0 (grade 5, nearly snuffed) · 1 (grade 4, visibly
	 * stronger) · 2 (grade 3, bright). The R59 acceptance oracle alongside
	 * the live frames.
	 */
	public static int auraTier(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 0;
			case GRADE_4 -> 1;
			case GRADE_3 -> 2;
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has no aura design yet (grades 2-1 are out of v1 scope)");
		};
	}

	/** BALANCE: client particles per tick per body: 1 / 2 / 3. */
	public static int particlesPerTick(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 1;
			case GRADE_4 -> 2;
			case GRADE_3 -> 3;
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has no aura design yet (grades 2-1 are out of v1 scope)");
		};
	}

	/** BALANCE: dust colour per grade (ARGB): dull moss / strong teal / bright blue. */
	public static int primaryColorArgb(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 0xFF77806B;
			case GRADE_4 -> 0xFF4FA08A;
			case GRADE_3 -> 0xFF4D7DFF;
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has no aura design yet (grades 2-1 are out of v1 scope)");
		};
	}

	/** BALANCE: dust size per grade. */
	public static float particleSize(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 0.7f;
			case GRADE_4 -> 0.9f;
			case GRADE_3 -> 1.2f;
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has no aura design yet (grades 2-1 are out of v1 scope)");
		};
	}

	/**
	 * BALANCE: every Nth tick the emitter adds a rare
	 * {@code SOUL_FIRE_FLAME} spark; 0 = never. Only grade 3 earns one.
	 */
	public static int soulFlameEveryTicks(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 0;
			case GRADE_4 -> 0;
			case GRADE_3 -> 12;
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has no aura design yet (grades 2-1 are out of v1 scope)");
		};
	}
}
