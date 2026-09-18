package jujutsu.mod.client.character.megumi.selector;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;

/**
 * The strip's decision rules, kept pure so the spec's click contract is testable without a game.
 *
 * <p>Two answers live here and nowhere else:
 *
 * <ul>
 *   <li>{@link #resolveClick} — can this state be selected? {@code READY} and {@code SUMMONED} can:
 *       the spec is explicit that an already-summoned shikigami stays selectable and that "summoned"
 *       is a marker, not a block. Every other state refuses the click and leaves the strip open.</li>
 *   <li>{@link #flags} — the visual precedence table. A slot can carry several states at once, and the
 *       rules are: {@code active} and {@code hovered} never hide the state marker, an unavailable slot
 *       never reports itself as available because the cursor is on it, and every blocked state keeps its
 *       own named marker instead of collapsing into one generic grey.</li>
 * </ul>
 *
 * <p>There is deliberately no close result: nothing a click can do dismisses the strip. Per the spec,
 * the selector key's release is the single close gesture.
 */
public final class ShikigamiSelectorState {
	/** What a left click on one slot means. */
	public enum ClickResult {
		/** The click selects this entry and the strip stays open. */
		SELECT,
		/** The click changes nothing, gives restrained feedback, and the strip stays open. */
		REJECT,
		/** No slot was hit — the click is swallowed. */
		NONE
	}

	/** This slot is the player's current active shikigami. */
	public static final String ACTIVE = "active";
	/** The cursor is on this slot. */
	public static final String HOVERED = "hovered";
	/** The entry can be selected right now. */
	public static final String AVAILABLE = "available";
	/** The entry is out in the world right now — a marker, never a block. */
	public static final String SUMMONED = "summoned";
	/** The entry is recovering; the click is refused. */
	public static final String COOLDOWN = "cooldown";
	/** The entry is not unlocked/tamed yet; the click is refused. */
	public static final String LOCKED = "locked";
	/** The entry is permanently destroyed; the click is refused. */
	public static final String DESTROYED = "destroyed";
	/** The entry is temporarily unavailable; the click is refused. */
	public static final String TEMPORARY = "temporary";

	private ShikigamiSelectorState() {}

	/** The verdict for one click, from the clicked slot's state alone. */
	public static ClickResult resolveClick(MegumiShikigamiSlotState state) {
		return switch (state) {
			case READY, SUMMONED -> ClickResult.SELECT;
			case COOLDOWN, LOCKED, DESTROYED, TEMPORARY -> ClickResult.REJECT;
		};
	}

	/**
	 * The flags the slot view draws from, in a fixed order. Precedence, stated once: {@code active} and
	 * {@code hovered} are additive and never suppress the state marker; {@code available} appears only
	 * for the two selectable states; each blocked state contributes exactly its own marker flag.
	 */
	public static Set<String> flags(boolean active, boolean hovered, MegumiShikigamiSlotState state) {
		Set<String> flags = new LinkedHashSet<>();
		if (active) {
			flags.add(ACTIVE);
		}
		if (hovered) {
			flags.add(HOVERED);
		}
		switch (state) {
			case READY -> flags.add(AVAILABLE);
			case SUMMONED -> {
				flags.add(AVAILABLE);
				flags.add(SUMMONED);
			}
			case COOLDOWN -> flags.add(COOLDOWN);
			case LOCKED -> flags.add(LOCKED);
			case DESTROYED -> flags.add(DESTROYED);
			case TEMPORARY -> flags.add(TEMPORARY);
		}
		return Collections.unmodifiableSet(flags);
	}

	/**
	 * The lang key naming why this entry cannot be selected, or {@code null} when it can. {@code SUMMONED}
	 * gets a label of its own because the spec wants the summoned state legible without opening another
	 * menu — and it is not a refusal, which is why it does not appear in {@link #resolveClick}.
	 */
	public static String stateLabelKey(MegumiShikigamiSlotState state) {
		return switch (state) {
			case READY -> null;
			case SUMMONED -> "selector.jujutsumod.megumi.state.summoned";
			case COOLDOWN -> "selector.jujutsumod.megumi.state.cooldown";
			case LOCKED -> "selector.jujutsumod.megumi.state.locked";
			case DESTROYED -> "selector.jujutsumod.megumi.state.destroyed";
			case TEMPORARY -> "selector.jujutsumod.megumi.state.temporary";
		};
	}
}
