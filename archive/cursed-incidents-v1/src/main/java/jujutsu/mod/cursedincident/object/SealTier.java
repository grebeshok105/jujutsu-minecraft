package jujutsu.mod.cursedincident.object;

import java.util.Arrays;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Physical talisman tiers.  Required-grade policy stays in B1's SealPolicy. */
public enum SealTier {
    TALISMAN(1),
    INSCRIBED(2),
    PRISMATIC(3);

    public static final Codec<SealTier> CODEC = Codec.INT.xmap(SealTier::byValue, SealTier::value);
    public static final StreamCodec<RegistryFriendlyByteBuf, SealTier> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.value),
            buffer -> SealTier.byValue(buffer.readVarInt()));

    private final int value;

    SealTier(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public int tier() {
        return value;
    }

    public boolean meets(SealTier required) {
        return required != null && value >= required.value;
    }

    public boolean meets(int required) {
        return value >= required;
    }

    public static SealTier byValue(int value) {
        return Arrays.stream(values()).filter(tier -> tier.value == value).findFirst().orElse(TALISMAN);
    }

    public static SealTier fromInt(int value) {
        return byValue(value);
    }
}
