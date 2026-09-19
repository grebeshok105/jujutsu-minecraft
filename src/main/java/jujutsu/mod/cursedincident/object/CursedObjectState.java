package jujutsu.mod.cursedincident.object;

import java.util.Objects;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Persistent state carried by one physical cursed-object stack.
 *
 * <p>The stack, rather than an incident record, is the authority for object identity,
 * sealing and accumulated dwell history.  The incident store mirrors the values it
 * needs for inspection, so moving an object through an inventory never loses its id.
 */
public record CursedObjectState(
        UUID instanceId,
        String typeId,
        int grade,
        long mintedGameTime,
        boolean sealed,
        int sealTier,
        int sealIntegrity,
        KnowledgeLevel knowledge,
        long accumulatedTicks,
        long lastDecayGameTime) {

    private static final long MISSING_DECAY_ANCHOR = Long.MIN_VALUE;

    private static final Codec<KnowledgeLevel> KNOWLEDGE_CODEC = Codec.STRING.xmap(
            value -> Objects.requireNonNullElse(KnowledgeLevel.byName(value), KnowledgeLevel.UNKNOWN),
            KnowledgeLevel::wireName);

    private static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeLevel> KNOWLEDGE_STREAM_CODEC =
            StreamCodec.of(
                    (buffer, value) -> buffer.writeUtf(value.wireName()),
                    buffer -> Objects.requireNonNullElse(KnowledgeLevel.byName(buffer.readUtf()), KnowledgeLevel.UNKNOWN));

    public static final Codec<CursedObjectState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("instance_id").forGetter(CursedObjectState::instanceId),
            Codec.STRING.fieldOf("type_id").forGetter(CursedObjectState::typeId),
            Codec.INT.fieldOf("grade").forGetter(CursedObjectState::grade),
            Codec.LONG.fieldOf("minted_game_time").forGetter(CursedObjectState::mintedGameTime),
            Codec.BOOL.fieldOf("sealed").forGetter(CursedObjectState::sealed),
            Codec.INT.fieldOf("seal_tier").forGetter(CursedObjectState::sealTier),
            Codec.INT.fieldOf("seal_integrity").forGetter(CursedObjectState::sealIntegrity),
            KNOWLEDGE_CODEC.fieldOf("knowledge").forGetter(CursedObjectState::knowledge),
            Codec.LONG.fieldOf("accumulated_ticks").forGetter(CursedObjectState::accumulatedTicks),
            // Stacks created before the durable decay cursor existed resume from mint time.
            Codec.LONG.optionalFieldOf("last_decay_game_time", MISSING_DECAY_ANCHOR)
                    .forGetter(CursedObjectState::lastDecayGameTime)
    ).apply(instance, CursedObjectState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CursedObjectState> STREAM_CODEC =
            StreamCodec.of(
                    (buf, state) -> {
                        UUIDUtil.STREAM_CODEC.encode(buf, state.instanceId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, state.typeId());
                        ByteBufCodecs.VAR_INT.encode(buf, state.grade());
                        ByteBufCodecs.VAR_LONG.encode(buf, state.mintedGameTime());
                        ByteBufCodecs.BOOL.encode(buf, state.sealed());
                        ByteBufCodecs.VAR_INT.encode(buf, state.sealTier());
                        ByteBufCodecs.VAR_INT.encode(buf, state.sealIntegrity());
                        KNOWLEDGE_STREAM_CODEC.encode(buf, state.knowledge());
                        ByteBufCodecs.VAR_LONG.encode(buf, state.accumulatedTicks());
                        ByteBufCodecs.VAR_LONG.encode(buf, state.lastDecayGameTime());
                    },
                    buf -> new CursedObjectState(
                            UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            KNOWLEDGE_STREAM_CODEC.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf)));

    /** Compatibility constructor for stacks written before the decay anchor was added. */
    public CursedObjectState(UUID instanceId, String typeId, int grade, long mintedGameTime,
            boolean sealed, int sealTier, int sealIntegrity, KnowledgeLevel knowledge, long accumulatedTicks) {
        this(instanceId, typeId, grade, mintedGameTime, sealed, sealTier, sealIntegrity, knowledge,
                accumulatedTicks, mintedGameTime);
    }

    public CursedObjectState {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        typeId = Objects.requireNonNull(typeId, "typeId");
        knowledge = Objects.requireNonNullElse(knowledge, KnowledgeLevel.UNKNOWN);
        if (lastDecayGameTime == MISSING_DECAY_ANCHOR) {
            lastDecayGameTime = mintedGameTime;
        }
    }

    public static CursedObjectState fresh(UUID instanceId, String typeId, int grade, long mintedGameTime) {
        return new CursedObjectState(instanceId, typeId, grade, mintedGameTime,
                false, 0, 0, KnowledgeLevel.UNKNOWN, 0L, mintedGameTime);
    }

    public CursedObjectState withSeal(boolean sealed, int sealTier, int sealIntegrity) {
        return new CursedObjectState(instanceId, typeId, grade, mintedGameTime, sealed,
                Math.max(0, sealTier), Math.max(0, sealIntegrity), knowledge, accumulatedTicks,
                lastDecayGameTime);
    }

    public CursedObjectState withKnowledge(KnowledgeLevel next) {
        return new CursedObjectState(instanceId, typeId, grade, mintedGameTime, sealed,
                sealTier, sealIntegrity, next, accumulatedTicks, lastDecayGameTime);
    }

    public CursedObjectState withAccumulatedTicks(long ticks) {
        return new CursedObjectState(instanceId, typeId, grade, mintedGameTime, sealed,
                sealTier, sealIntegrity, knowledge, Math.max(0L, ticks), lastDecayGameTime);
    }

    public CursedObjectState withLastDecayGameTime(long gameTime) {
        return new CursedObjectState(instanceId, typeId, grade, mintedGameTime, sealed,
                sealTier, sealIntegrity, knowledge, accumulatedTicks, gameTime);
    }
}
