package jujutsu.mod.character.megumi;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.registry.JujutsuEntities;

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

	@Override
	public void registerServerHooks() {
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_DIVINE_DOG,
				MegumiDivineDogEntity.createAttributes()
						.add(Attributes.MAX_HEALTH, MegumiProfile.DOG_HEALTH)
						.add(Attributes.ATTACK_DAMAGE, MegumiProfile.DOG_ATTACK_DAMAGE)
						.add(Attributes.MOVEMENT_SPEED, MegumiProfile.DOG_MOVEMENT_SPEED));
	}
}
