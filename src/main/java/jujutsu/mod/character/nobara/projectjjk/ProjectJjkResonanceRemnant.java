package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ProjectJjkResonanceRemnant(UUID targetId, ResourceLocation dimension, String targetName) {
	public static final Codec<ProjectJjkResonanceRemnant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("target_id").forGetter(ProjectJjkResonanceRemnant::targetId),
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(ProjectJjkResonanceRemnant::dimension),
			Codec.STRING.fieldOf("target_name").forGetter(ProjectJjkResonanceRemnant::targetName)
	).apply(instance, ProjectJjkResonanceRemnant::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProjectJjkResonanceRemnant> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC,
			ProjectJjkResonanceRemnant::targetId,
			ResourceLocation.STREAM_CODEC,
			ProjectJjkResonanceRemnant::dimension,
			ByteBufCodecs.STRING_UTF8,
			ProjectJjkResonanceRemnant::targetName,
			ProjectJjkResonanceRemnant::new
	);
}
