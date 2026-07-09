package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;

public record ProjectJjkNobaraImpulsePayload(
		int kind,
		int nailCount,
		double x,
		double y,
		double z,
		long gameTime
) implements CustomPacketPayload {
	public static final int HAMMER = 0;
	public static final int IMPACT = 1;
	public static final int IMPACT_SOUND = 2;
	public static final int RESONANCE_CHANNEL = 3;
	public static final int RESONANCE_STRIKE = 4;
	public static final int LINK_BIND = 5;
	public static final int DETONATE = 6;
	public static final int HAIRPIN_ENLARGE = 7;
	public static final int HAIRPIN_EXPLOSION = 8;
	public static final int FP_SNAP = 9;
	public static final Type<ProjectJjkNobaraImpulsePayload> TYPE = new Type<>(JujutsuMod.id("projectjjk_nobara_impulse"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProjectJjkNobaraImpulsePayload> STREAM_CODEC = CustomPacketPayload.codec(
			ProjectJjkNobaraImpulsePayload::write,
			ProjectJjkNobaraImpulsePayload::read
	);

	public Vec3 origin() {
		return new Vec3(x, y, z);
	}

	private static ProjectJjkNobaraImpulsePayload read(RegistryFriendlyByteBuf buffer) {
		return new ProjectJjkNobaraImpulsePayload(
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readLong()
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(kind);
		buffer.writeVarInt(nailCount);
		buffer.writeDouble(x);
		buffer.writeDouble(y);
		buffer.writeDouble(z);
		buffer.writeLong(gameTime);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
