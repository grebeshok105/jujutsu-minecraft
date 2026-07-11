package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

public final class RemnantVisualTypeTest {
	private RemnantVisualTypeTest() {}

	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		assert RemnantVisualType.classify(true, true) == RemnantVisualType.CURSE
				: "curse tag must override organic classification";
		assert RemnantVisualType.classify(false, true) == RemnantVisualType.FLESH
				: "animals and organic non-humanoids should use flesh";
		assert RemnantVisualType.classify(false, false) == RemnantVisualType.TOKEN
				: "players, villagers, golems, and unknown entities should use token";

		ProjectJjkResonanceRemnant current = new ProjectJjkResonanceRemnant(
				UUID.fromString("fd520a12-493d-47fe-b284-802ff63e6970"),
				ResourceLocation.parse("minecraft:overworld"),
				Component.literal("Old target"),
				RemnantVisualType.FLESH
		);
		JsonElement encoded = ProjectJjkResonanceRemnant.CODEC.encodeStart(JsonOps.INSTANCE, current).getOrThrow();
		encoded.getAsJsonObject().remove("visual_type");
		ProjectJjkResonanceRemnant legacy = ProjectJjkResonanceRemnant.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		assert legacy.visualType() == RemnantVisualType.TOKEN : "legacy remnant data must default to TOKEN";
		System.out.println("RemnantVisualTypeTest passed");
	}
}
