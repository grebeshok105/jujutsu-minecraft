package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraActions;

public record NobaraActionPayload(int action) implements CustomPacketPayload {
	public static final int HAIRPIN_ENLARGE = ProjectJjkNobaraActions.HAIRPIN_ENLARGE;
	public static final int HAIRPIN_EXPLOSION = ProjectJjkNobaraActions.HAIRPIN_EXPLOSION;
	public static final int NAIL_LAUNCH_EXPLOSIVE = ProjectJjkNobaraActions.NAIL_LAUNCH_EXPLOSIVE;
	public static final int HAMMER_CONTEXT = ProjectJjkNobaraActions.HAMMER_CONTEXT;
	public static final int SELF_RESONANCE = ProjectJjkNobaraActions.SELF_RESONANCE;
	public static final Type<NobaraActionPayload> TYPE = new Type<>(JujutsuMod.id("nobara_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NobaraActionPayload> STREAM_CODEC = CustomPacketPayload.codec(
			NobaraActionPayload::write,
			NobaraActionPayload::read
	);

	private static NobaraActionPayload read(RegistryFriendlyByteBuf buffer) {
		return new NobaraActionPayload(buffer.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(action);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
