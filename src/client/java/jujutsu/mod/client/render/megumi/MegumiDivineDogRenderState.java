package jujutsu.mod.client.render.megumi;

import net.minecraft.client.renderer.entity.state.WolfRenderState;
import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** Vanilla wolf render state plus Megumi's synchronized transient presentation. */
public final class MegumiDivineDogRenderState extends WolfRenderState {
	public MegumiDogPresentationPolicy.Phase phase = MegumiDogPresentationPolicy.Phase.ACTIVE;
	public float progress = 1.0f;
	public float verticalOffset;
}
