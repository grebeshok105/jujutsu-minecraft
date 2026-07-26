package jujutsu.mod.client.character;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.network.AbilityCooldownPayload;

/** Client-side mirror of server-confirmed cooldowns used only to suppress redundant ability input. */
public final class ClientAbilityCooldowns {
	private static final Map<Key, Long> READY_AT = new HashMap<>();

	private ClientAbilityCooldowns() {}

	public static void apply(AbilityCooldownPayload payload) {
		JujutsuCharacter character = JujutsuCharacter.byId(payload.characterId());
		CharacterAbility ability = CharacterAbility.byNetworkId(payload.abilityId());
		Minecraft client = Minecraft.getInstance();
		if (ability == null || client.level == null) {
			return;
		}
		READY_AT.put(new Key(character, ability), client.level.getGameTime() + Math.max(0, payload.remainingTicks()));
	}

	public static boolean isReady(JujutsuCharacter character, CharacterAbility ability) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		return client.level.getGameTime() >= READY_AT.getOrDefault(new Key(character, ability), 0L);
	}

	public static void clear() {
		READY_AT.clear();
	}

	private record Key(JujutsuCharacter character, CharacterAbility ability) {}
}
