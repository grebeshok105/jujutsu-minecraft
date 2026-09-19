package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Megumi partial payload registration owned by the wings implementation. */
public final class MegumiPartialPayloads {
	private MegumiPartialPayloads() {}

	/** Registers the wings payload; B2 registers the tongue payload in its own seam. */
	public static void registerAll() {
		PayloadTypeRegistry.playS2C().register(MegumiWingsStatePayload.TYPE, MegumiWingsStatePayload.STREAM_CODEC);
	}
}
