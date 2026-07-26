package jujutsu.mod.character.nobara;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.EmbeddedNailRegistry;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorLifecycle;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraActionGuard;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraLoadout;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;

/** Nobara on the server: no attribute modifiers, a starter kit, and all five slots filled. */
public final class NobaraDefinition implements CharacterDefinition {
	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.NOBARA;
	}

	@Override
	public boolean tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		return NobaraAbilityRouter.tryCast(player, slot, notify);
	}

	/** Registration order within her own group is the order mod init used, and some of these share events. */
	@Override
	public void registerServerHooks() {
		ProjectJjkRitualRuntime.register();
		ProjectJjkStrawDollRuntime.register();
		EmbeddedNailRegistry.register();
		NailAnchorLifecycle.register();
		NobaraHammerCombatRuntime.register();
		NobaraActionGuard.register();
		SelfResonanceRuntime.register();
		NailTrapRuntime.register();
	}

	@Override
	public void onSelected(ServerPlayer player) {
		// Idempotent: it only fills a missing hammer, doll or nails. Running it on every select and not
		// just the first is deliberate, so re-selecting her restores a kit lost to death or a switch.
		ProjectJjkNobaraLoadout.ensureStarterTools(player);
	}
}
