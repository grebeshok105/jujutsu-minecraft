package jujutsu.mod.character;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public final class CharacterPlayerStateTest {
	private CharacterPlayerStateTest() {}

	public static void main(String[] args) {
		CharacterPlayerState initial = CharacterPlayerState.DEFAULT;
		assert initial.selectedCharacter() == JujutsuCharacter.NONE : "New players must start without a selected character";
		assert !initial.hasClaimedStarter(JujutsuCharacter.NOBARA) : "New players must be eligible for the Nobara starter once";

		CharacterPlayerState selected = initial.withSelectedCharacter(JujutsuCharacter.NOBARA);
		assert selected.selectedCharacter() == JujutsuCharacter.NOBARA : "Character selection must be retained in persistent state";
		assert !selected.hasClaimedStarter(JujutsuCharacter.NOBARA) : "Selecting and claiming must remain separate operations";

		CharacterPlayerState claimed = selected.claimStarter(JujutsuCharacter.NOBARA);
		assert claimed.hasClaimedStarter(JujutsuCharacter.NOBARA) : "Starter claim must be remembered";
		assert claimed.claimStarter(JujutsuCharacter.NOBARA).equals(claimed) : "Repeated starter claims must be idempotent";

		CharacterPlayerState deselected = claimed.withSelectedCharacter(JujutsuCharacter.NONE);
		assert deselected.selectedCharacter() == JujutsuCharacter.NONE : "None must remain a valid persisted selection";
		assert deselected.hasClaimedStarter(JujutsuCharacter.NOBARA) : "Deselecting must not reset one-time starter claims";

		JsonElement encoded = CharacterPlayerState.CODEC.encodeStart(JsonOps.INSTANCE, claimed)
				.getOrThrow(error -> new AssertionError("Failed to encode character state: " + error));
		CharacterPlayerState decoded = CharacterPlayerState.CODEC.parse(JsonOps.INSTANCE, encoded)
				.getOrThrow(error -> new AssertionError("Failed to decode character state: " + error));
		assert decoded.equals(claimed) : "Persistent character state codec must round-trip";

		CharacterPlayerState sanitized = new CharacterPlayerState("unknown_character", java.util.Set.of());
		assert sanitized.selectedCharacter() == JujutsuCharacter.NONE : "Unknown persisted character ids must safely fall back to None";
	}
}
