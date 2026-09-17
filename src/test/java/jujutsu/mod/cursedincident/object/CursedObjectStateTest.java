package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class CursedObjectStateTest {
    private static final UUID INSTANCE = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    @Test
    void persistentCodecKeepsEveryDistinctField() {
        CursedObjectState sent = new CursedObjectState(INSTANCE, "cursed_mask", 2, 1234L,
                true, 2, 173, KnowledgeLevel.ORIGIN, 9876L);
        JsonElement encoded = CursedObjectState.CODEC.encodeStart(JsonOps.INSTANCE, sent).getOrThrow();
        CursedObjectState received = CursedObjectState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(sent, received, "the item component must round-trip through its persistent codec");
    }

    @Test
    void networkCodecConsumesExactlyTheEncodedState() {
        CursedObjectState sent = new CursedObjectState(INSTANCE, "cursed_chain", 4, 77L,
                false, 3, 211, KnowledgeLevel.SEALING_METHODS, 321L);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CursedObjectState.STREAM_CODEC.encode(buffer, sent);
        CursedObjectState received = CursedObjectState.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "the component decoder must consume its complete payload");
        assertEquals(sent, received);
        assertNotNull(CursedObjectState.CODEC);
        assertNotNull(CursedObjectState.STREAM_CODEC);
    }
}
