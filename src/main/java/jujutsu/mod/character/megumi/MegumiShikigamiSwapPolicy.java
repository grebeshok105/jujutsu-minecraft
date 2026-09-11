package jujutsu.mod.character.megumi;

/**
 * Pure decision table for the shikigami technique key ({@code R}). The caller executes the returned
 * action; this class only decides which one applies so the branch order is pinned by a unit test
 * instead of living implicitly in the runtime.
 */
public final class MegumiShikigamiSwapPolicy {
	private MegumiShikigamiSwapPolicy() {}

	public enum Action {
		/** The dogs answer the key themselves (toggle, with their own recall/cooldown rules). */
		DELEGATE_DOGS,
		/** The selected shikigami is the active one: manual recall, which costs its recall cooldown. */
		RECALL_SELF,
		/** A different body is active: recall it for free, then summon the selection. */
		RECALL_OTHER_THEN_SUMMON,
		/** Nothing active: summon the selection. Summons never start a cooldown. */
		SUMMON
	}

	/**
	 * @param selected        the player's current shikigami selection
	 * @param activeTypeOrNull the type of this runtime's active pack, or null when nothing is out
	 * @param dogActive       whether a Divine Dog pack is currently active
	 */
	public static Action decide(MegumiShikigami selected, MegumiShikigami activeTypeOrNull, boolean dogActive) {
		if (selected == MegumiShikigami.DOGS) {
			return Action.DELEGATE_DOGS;
		}
		if (activeTypeOrNull == selected) {
			return Action.RECALL_SELF;
		}
		if (activeTypeOrNull != null) {
			return Action.RECALL_OTHER_THEN_SUMMON;
		}
		return dogActive ? Action.RECALL_OTHER_THEN_SUMMON : Action.SUMMON;
	}
}