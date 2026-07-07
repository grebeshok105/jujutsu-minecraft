package jujutsu.mod.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;

public record PreparedNailsPayload(
		int seed,
		int playerEntityId,
		int nailCount,
		long startGameTime,
		double directionX,
		double directionY,
		double directionZ,
		double nail0X,
		double nail0Y,
		double nail0Z,
		double nail1X,
		double nail1Y,
		double nail1Z,
		double nail2X,
		double nail2Y,
		double nail2Z,
		double nail3X,
		double nail3Y,
		double nail3Z
) implements CustomPacketPayload {
	public static final Type<PreparedNailsPayload> TYPE = new Type<>(JujutsuMod.id("prepared_nails"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PreparedNailsPayload> STREAM_CODEC = CustomPacketPayload.codec(
			PreparedNailsPayload::write,
			PreparedNailsPayload::read
	);

	public static PreparedNailsPayload create(int seed, int playerEntityId, int nailCount, long startGameTime, List<Vec3> nails) {
		return create(seed, playerEntityId, nailCount, startGameTime, nails, Vec3.ZERO);
	}

	public static PreparedNailsPayload create(int seed, int playerEntityId, int nailCount, long startGameTime, List<Vec3> nails, Vec3 direction) {
		int clampedCount = Math.max(0, Math.min(4, Math.min(nailCount, nails.size())));
		Vec3 nail0 = nailOrZero(nails, 0);
		Vec3 nail1 = nailOrZero(nails, 1);
		Vec3 nail2 = nailOrZero(nails, 2);
		Vec3 nail3 = nailOrZero(nails, 3);
		Vec3 facing = safeDirection(direction);
		return new PreparedNailsPayload(
				seed,
				playerEntityId,
				clampedCount,
				startGameTime,
				facing.x,
				facing.y,
				facing.z,
				nail0.x,
				nail0.y,
				nail0.z,
				nail1.x,
				nail1.y,
				nail1.z,
				nail2.x,
				nail2.y,
				nail2.z,
				nail3.x,
				nail3.y,
				nail3.z
		);
	}

	private static PreparedNailsPayload read(RegistryFriendlyByteBuf buffer) {
		return new PreparedNailsPayload(
				buffer.readInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readLong(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble()
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(seed);
		buffer.writeVarInt(playerEntityId);
		buffer.writeVarInt(nailCount);
		buffer.writeLong(startGameTime);
		buffer.writeDouble(directionX);
		buffer.writeDouble(directionY);
		buffer.writeDouble(directionZ);
		buffer.writeDouble(nail0X);
		buffer.writeDouble(nail0Y);
		buffer.writeDouble(nail0Z);
		buffer.writeDouble(nail1X);
		buffer.writeDouble(nail1Y);
		buffer.writeDouble(nail1Z);
		buffer.writeDouble(nail2X);
		buffer.writeDouble(nail2Y);
		buffer.writeDouble(nail2Z);
		buffer.writeDouble(nail3X);
		buffer.writeDouble(nail3Y);
		buffer.writeDouble(nail3Z);
	}

	public List<Vec3> nails() {
		List<Vec3> nails = List.of(
				new Vec3(nail0X, nail0Y, nail0Z),
				new Vec3(nail1X, nail1Y, nail1Z),
				new Vec3(nail2X, nail2Y, nail2Z),
				new Vec3(nail3X, nail3Y, nail3Z)
		);
		return nails.subList(0, Math.max(0, Math.min(nailCount, nails.size())));
	}

	public Vec3 direction() {
		return safeDirection(new Vec3(directionX, directionY, directionZ));
	}

	private static Vec3 nailOrZero(List<Vec3> nails, int index) {
		return index < nails.size() ? nails.get(index) : Vec3.ZERO;
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
