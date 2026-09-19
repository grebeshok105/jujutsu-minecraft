package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registration seam for Megumi's tongue payload; shared networking wires this at integration. */
public final class MegumiTonguePayloads {
	private MegumiTonguePayloads() {}

	public static void registerAll() {
		PayloadTypeRegistry.playS2C().register(
				MegumiTongueStatePayload.TYPE, MegumiTongueStatePayload.STREAM_CODEC);
	}
}
