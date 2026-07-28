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

	/** Read-only view of the existing server-confirmed deadline. */
	public static int remainingTicks(JujutsuCharacter character, CharacterAbility ability) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 0;
		}
		return remainingTicks(READY_AT.getOrDefault(new Key(character, ability), 0L),
				client.level.getGameTime());
	}

	static int remainingTicks(long readyAt, long gameTime) {
		if (readyAt <= gameTime) {
			return 0;
		}
		long remaining = readyAt - gameTime;
		return (int) Math.min(Integer.MAX_VALUE, remaining);
	}

	public static void clear() {
		READY_AT.clear();
	}

	private record Key(JujutsuCharacter character, CharacterAbility ability) {}
}
