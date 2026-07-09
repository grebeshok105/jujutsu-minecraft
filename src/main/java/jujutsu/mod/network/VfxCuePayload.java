package jujutsu.mod.network;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VfxCuePayload(VfxCue cue) implements CustomPacketPayload {
	public static final Type<VfxCuePayload> TYPE = new Type<>(JujutsuMod.id("vfx_cue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VfxCuePayload> STREAM_CODEC = CustomPacketPayload.codec(
			VfxCuePayload::write,
			VfxCuePayload::read
	);

	private static VfxCuePayload read(RegistryFriendlyByteBuf buffer) {
		return new VfxCuePayload(new VfxCue(
				buffer.readResourceLocation(),
				buffer.readVec3(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readLong(),
				buffer.readLong()
		));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeResourceLocation(cue.effectId());
		buffer.writeVec3(cue.origin());
		buffer.writeVarInt(cue.anchorEntityId());
		buffer.writeVarInt(cue.intensity());
		buffer.writeLong(cue.startGameTime());
		buffer.writeLong(cue.seed());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
