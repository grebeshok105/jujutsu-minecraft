package jujutsu.mod.character.todo;

import jujutsu.mod.character.CharacterAbility;

/** The five successful swaps exposed by Todo's unified node pipeline. */
public enum SwapKind {
	AIMED(CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS, true),
	PAIR(CharacterAbility.SECONDARY, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS, false),
	TRIPLE(CharacterAbility.SECONDARY_SNEAK, TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS, false),
	STONE_SELF(CharacterAbility.TERTIARY, TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS, true),
	STONE_TARGET(CharacterAbility.TERTIARY_SNEAK, TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS, false);

	private final CharacterAbility slot;
	private final int baseCooldownTicks;
	private final boolean grantsMomentum;

	SwapKind(CharacterAbility slot, int baseCooldownTicks, boolean grantsMomentum) {
		this.slot = slot;
		this.baseCooldownTicks = baseCooldownTicks;
		this.grantsMomentum = grantsMomentum;
	}

	public CharacterAbility slot() {
		return slot;
	}

	public int baseCooldownTicks() {
		return baseCooldownTicks;
	}

	public boolean grantsMomentum() {
		return grantsMomentum;
	}
}
