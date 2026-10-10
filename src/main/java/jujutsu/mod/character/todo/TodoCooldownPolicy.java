package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.network.JujutsuNetworking;

/** Todo-owned cooldown arming policy; Revised softens only future arms. */
public final class TodoCooldownPolicy {
	private TodoCooldownPolicy() {}

	public static void arm(ServerPlayer player, CharacterAbility slot, int baseTicks) {
		if (player == null || slot == null) {
			return;
		}
		int effective = effectiveTicks(TodoRhythmRuntime.isRevised(player), baseTicks);
		CharacterAbilityCooldowns.start(player, slot, effective);
		JujutsuNetworking.sendAbilityCooldown(player, slot, effective);
	}

	/** Pure arithmetic seam used by the rhythm tests. */
	public static int effectiveTicks(boolean revised, int baseTicks) {
		int base = Math.max(0, baseTicks);
		if (!revised) {
			return base;
		}
		long scaled = Math.round(base * TodoProfile.REVISED_COOLDOWN_SCALE);
		return Math.max(TodoProfile.REVISED_MIN_COOLDOWN_TICKS, (int) Math.min(Integer.MAX_VALUE, scaled));
	}
}
