package jujutsu.mod.client.character.megumi.selector;

import jujutsu.mod.registry.JujutsuSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

/**
 * Sound half of the selector's feedback (issue #109): the four tactile roles the design contract
 * demands -- open, hover, confirm, reject.
 *
 * <p>The samples are kept deliberately short (45-120 ms, see {@code tools/synth_selector_sounds.py})
 * and this class only routes them through the UI channel, so a player clicking through several
 * shikigami in one hold hears each click answered instead of a queue draining after the fact. Hover
 * is the quietest row because it fires on every slot boundary crossing, and it carries a small pitch
 * scatter so a fast sweep across the strip does not turn into one machine-gunned sample.
 */
public final class MegumiSelectorFeedback implements SelectorFeedback {
	private static final float OPEN_VOLUME = 1.0f;
	/** Below the other three: this is the only role that can repeat several times per second. */
	private static final float HOVER_VOLUME = 0.7f;
	private static final float SELECT_VOLUME = 1.0f;
	private static final float REJECT_VOLUME = 0.85f;
	/** +/-3% scatter on the hover tick -- audible as variety, not as a melody. */
	private static final float HOVER_PITCH_JITTER = 0.03f;

	private final RandomSource random = RandomSource.create();

	@Override
	public void open() {
		play(JujutsuSounds.MEGUMI_SELECTOR_OPEN, 1.0f, OPEN_VOLUME);
	}

	@Override
	public void hover() {
		play(JujutsuSounds.MEGUMI_SELECTOR_HOVER, 1.0f + hoverJitter(), HOVER_VOLUME);
	}

	@Override
	public void select() {
		play(JujutsuSounds.MEGUMI_SELECTOR_SELECT, 1.0f, SELECT_VOLUME);
	}

	@Override
	public void reject() {
		play(JujutsuSounds.MEGUMI_SELECTOR_REJECT, 1.0f, REJECT_VOLUME);
	}

	private float hoverJitter() {
		return (random.nextFloat() * 2.0f - 1.0f) * HOVER_PITCH_JITTER;
	}

	private static void play(SoundEvent event, float pitch, float volume) {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}
}
