package jujutsu.mod.client.character.megumi.selector;

/**
 * The strip's four feedback roles, as the design spec names them: open, hover/move, confirm, reject.
 *
 * <p>The screen depends on this interface, never on the audio implementation, so the strip stays
 * testable headlessly and a silent build (or a future non-audio feedback pack) can be injected by
 * handing the screen {@link #NONE}.
 */
public interface SelectorFeedback {
	/** The strip is about to appear. */
	void open();

	/** The cursor moved onto a different entry. Fired on change only — never per frame. */
	void hover();

	/** A click landed on a selectable entry. */
	void select();

	/** A click landed on an entry that cannot be selected right now. */
	void reject();

	/** The no-op implementation, for tests and headless runs. */
	SelectorFeedback NONE = new SelectorFeedback() {
		@Override
		public void open() {
		}

		@Override
		public void hover() {
		}

		@Override
		public void select() {
		}

		@Override
		public void reject() {
		}
	};
}
