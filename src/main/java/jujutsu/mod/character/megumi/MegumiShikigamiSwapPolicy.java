package jujutsu.mod.character.megumi;

import java.util.Set;

/**
 * Pure decision table for the shikigami technique key ({@code R}) under coexistence (issue #107
 * D1): every summon type is additive, so the key has exactly two readings — recall the selected
 * shikigami when it is already out, or summon it when it is not. A live Nue never stops a Toad
 * summon any more, and there is no swap branch left to model.
 */
public final class MegumiShikigamiSwapPolicy {
	private MegumiShikigamiSwapPolicy() {}

	public enum Action {
		/** The selected shikigami is already out: the key recalls it, at its own recall price. */
		RECALL_SELF,
		/** The selected shikigami is not out: the key summons it. A summon never starts a cooldown. */
		SUMMON
	}

	/**
	 * @param selected    the player's current shikigami selection
	 * @param activeTypes the types of this runtime's live packs (issue #107: at most one per type)
	 * @param dogActive   whether a Divine Dog pack is currently out
	 */
	public static Action decide(MegumiShikigami selected, Set<MegumiShikigami> activeTypes, boolean dogActive) {
		boolean selectedIsOut = selected == MegumiShikigami.DOGS
				? dogActive
				: activeTypes.contains(selected);
		return selectedIsOut ? Action.RECALL_SELF : Action.SUMMON;
	}
}
