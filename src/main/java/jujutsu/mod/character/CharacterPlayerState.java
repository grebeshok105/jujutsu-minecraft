package jujutsu.mod.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persistent player-owned character selection and one-time starter claims. */
public record CharacterPlayerState(String selectedCharacterId, Set<String> claimedStarterCharacters) {
	private static final Codec<Set<String>> CLAIMED_STARTERS_CODEC = Codec.STRING.listOf().xmap(
			values -> Set.copyOf(values),
			values -> values.stream().sorted().toList()
	);
	public static final Codec<CharacterPlayerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("selected_character", JujutsuCharacter.NONE.id()).forGetter(CharacterPlayerState::selectedCharacterId),
			CLAIMED_STARTERS_CODEC.optionalFieldOf("claimed_starter_characters", Set.of()).forGetter(CharacterPlayerState::claimedStarterCharacters)
	).apply(instance, CharacterPlayerState::new));
	public static final CharacterPlayerState DEFAULT = new CharacterPlayerState(JujutsuCharacter.NONE.id(), Set.of());

	public CharacterPlayerState {
		selectedCharacterId = JujutsuCharacter.byId(selectedCharacterId).id();
		claimedStarterCharacters = claimedStarterCharacters == null ? Set.of() : Set.copyOf(claimedStarterCharacters);
	}

	public JujutsuCharacter selectedCharacter() {
		return JujutsuCharacter.byId(selectedCharacterId);
	}

	public boolean hasClaimedStarter(JujutsuCharacter character) {
		return claimedStarterCharacters.contains(character.id());
	}

	public CharacterPlayerState withSelectedCharacter(JujutsuCharacter character) {
		return new CharacterPlayerState(character.id(), claimedStarterCharacters);
	}

	public CharacterPlayerState claimStarter(JujutsuCharacter character) {
		if (hasClaimedStarter(character)) {
			return this;
		}
		Set<String> updated = new LinkedHashSet<>(claimedStarterCharacters);
		updated.add(character.id());
		return new CharacterPlayerState(selectedCharacterId, updated);
	}
}
