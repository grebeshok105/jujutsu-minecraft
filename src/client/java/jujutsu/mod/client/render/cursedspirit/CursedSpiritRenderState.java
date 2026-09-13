package jujutsu.mod.client.render.cursedspirit;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Render state for all cursed-spirit bodies: the presentation variant plus the three
 * pack-driven animation states, copied from the entity in
 * {@link CursedSpiritRenderer#extractRenderState} (vanilla Warden pattern).
 */
public class CursedSpiritRenderState extends LivingEntityRenderState {
	public CursedSpiritVariant variant = CursedSpiritVariant.PROWLER;
	/** Issue #80: whether the source body is a curse subject (for {@code CurseRenderGate}). */
	public boolean curseSubject = true;
	public final AnimationState idle = new AnimationState();
	public final AnimationState attack = new AnimationState();
	public final AnimationState scream = new AnimationState();
}
