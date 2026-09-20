package jujutsu.mod.client.vfx.megumi;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;

/** Client-owned lifecycle for directed Nue shock arcs. */
public final class NueArcState {
	public static final int TTL_TICKS = 8;
	private static final int MAX_ARCS = 48;
	private static final NueArcState SHARED = new NueArcState();

	NueArcState() {}
	private final List<Arc> arcs = new ArrayList<>();

	public static NueArcState shared() {
		return SHARED;
	}

	/** Registers only the live Nue shock cue; the target point is deliberately copied once. */
	public void registerCue(VfxCue cue) {
		if (cue == null || !MegumiVfxIds.NUE_SHOCK.equals(cue.effectId())
				|| cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		arcs.add(new Arc(cue.anchorEntityId(), cue.origin(), cue.seed(), cue.startGameTime()));
		if (arcs.size() > MAX_ARCS) {
			arcs.remove(0);
		}
	}

	/** Returns a stable snapshot and evicts arcs at the end of their eight-tick presentation window. */
	public List<Arc> activeAt(long gameTime) {
		arcs.removeIf(arc -> arc.expiredAt(gameTime));
		return List.copyOf(arcs);
	}

	public void clear() {
		arcs.clear();
	}

	public record Arc(int nueEntityId, Vec3 targetPoint, long seed, long startGameTime) {
		public boolean expiredAt(long gameTime) {
			return gameTime - startGameTime >= TTL_TICKS;
		}

		public float ageAt(long gameTime, float partialTick) {
			return Math.max(0.0f, gameTime - startGameTime + partialTick);
		}
	}
}
