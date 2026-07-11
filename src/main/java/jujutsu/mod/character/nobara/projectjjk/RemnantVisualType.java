package jujutsu.mod.character.nobara.projectjjk;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum RemnantVisualType implements StringRepresentable {
	FLESH,
	TOKEN,
	CURSE;

	public static final Codec<RemnantVisualType> CODEC = StringRepresentable.fromEnum(RemnantVisualType::values);
	public static final StreamCodec<RegistryFriendlyByteBuf, RemnantVisualType> STREAM_CODEC = StreamCodec.of(
			(buffer, value) -> buffer.writeEnum(value),
			buffer -> buffer.readEnum(RemnantVisualType.class)
	);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	static RemnantVisualType classify(boolean curseTagged, boolean animal) {
		if (curseTagged) {
			return CURSE;
		}
		return animal ? FLESH : TOKEN;
	}
}
