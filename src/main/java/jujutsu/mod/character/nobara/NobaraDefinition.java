package jujutsu.mod.character.nobara;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.cursedspirit.perception.PerceptionFlags;
import jujutsu.mod.character.nobara.projectjjk.HairpinRuntime;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorLifecycle;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorRegistry;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraActionGuard;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraTeardown;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraLoadout;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkMegaNailRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;

/** Nobara on the server: no attribute modifiers, a starter kit, and all five slots filled. */
public final class NobaraDefinition implements CharacterDefinition {
	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.NOBARA;
	}

	@Override
	public PerceptionFlags cursePerception() {
		return PerceptionFlags.PERCEIVER;
	}

	@Override
	public AbilityResult tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		return NobaraAbilityRouter.tryCast(player, slot, notify);
	}

	/** Registration order within her own group is the order mod init used, and some of these share events. */
	@Override
	public void registerServerHooks() {
		HairpinRuntime.register();
		ProjectJjkMegaNailRuntime.register();
		ProjectJjkStrawDollRuntime.register();
		NailAnchorRegistry.register();
		NailAnchorLifecycle.register();
		NobaraTeardown.register();
		NobaraHammerCombatRuntime.register();
		NobaraActionGuard.register();
		SelfResonanceRuntime.register();
		NailTrapRuntime.register();
	}

	/**
	 * Self Resonance is the only cast that asks a question, so she is the only vessel that answers one.
	 * The runtime still validates the link itself — this hook decides who may be heard, not what is true.
	 */
	@Override
	public boolean selectCurseLink(ServerPlayer player, UUID linkId) {
		return SelfResonanceRuntime.select(player, linkId);
	}

	@Override
	public void onSelected(ServerPlayer player) {
		// Idempotent: it only fills a missing hammer, doll or nails. Running it on every select and not
		// just the first is deliberate, so re-selecting her restores a kit lost to death or a switch.
		ProjectJjkNobaraLoadout.ensureStarterTools(player);
	}

	@Override
	public void onDeselected(ServerPlayer player, JujutsuCharacter incoming) {
		NobaraTeardown.onCastStateLost(player);
	}
}
