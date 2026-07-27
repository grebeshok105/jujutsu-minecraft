package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;

/** Megumi's server definition; gameplay hooks are added with the Divine Dog runtime. */
public final class MegumiDefinition implements CharacterDefinition {
	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.MEGUMI;
	}

	@Override
	public boolean tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		return MegumiAbilityRouter.tryCast(player, slot, notify);
	}
}
