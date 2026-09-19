package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class CursedObjectResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/jujutsumod").toAbsolutePath();

    @Test
    void everyNaturalTypeHasGeoAndTwoTextureLooks() throws IOException {
        Set<String> modelIds = new HashSet<>();
        int looks = 0;
        for (CursedObjectType type : CursedObjectRegistry.naturalTypes()) {
            Path geo = ASSETS.resolve("geckolib/models/" + type.geoModel() + ".geo.json");
            assertTrue(Files.exists(geo), "missing geo model " + geo);
            JsonObject json = JsonParser.parseString(Files.readString(geo)).getAsJsonObject();
            String identifier = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonObject("description").get("identifier").getAsString();
            assertTrue(modelIds.add(identifier), "duplicate geometry id " + identifier);
            for (int variant = 0; variant < 2; variant++) {
                Path texture = ASSETS.resolve("textures/item/" + type.textureResourceId(variant) + ".png");
                BufferedImage image = ImageIO.read(texture.toFile());
                assertNotNull(image, "unreadable texture " + texture);
                assertEquals(64, image.getWidth(), "object textures use the shared 64px atlas width");
                assertEquals(64, image.getHeight(), "object textures use the shared 64px atlas height");
                looks++;
            }
        }
        assertEquals(8, modelIds.size(), "one distinct GeckoLib model per natural object type");
        assertTrue(looks >= 12, "the first release needs at least twelve model+texture looks");
    }

    @Test
    void itemDefinitionsResolveForObjectAndAllTalismanTiers() throws IOException {
        Path objectDefinition = ASSETS.resolve("items/cursed_object.json");
        assertTrue(Files.readString(objectDefinition).contains("geckolib:geckolib"));
        assertTrue(Files.readString(ASSETS.resolve("models/item/cursed_object.json")).contains("builtin/entity"));
        for (String item : new String[] {"sealing_talisman", "inscribed_talisman", "prismatic_talisman"}) {
            Path definition = ASSETS.resolve("items/" + item + ".json");
            Path model = ASSETS.resolve("models/item/" + item + ".json");
            Path texture = ASSETS.resolve("textures/item/" + item + ".png");
            assertTrue(Files.exists(definition), "missing talisman definition " + definition);
            assertTrue(Files.exists(model), "missing talisman model " + model);
            assertTrue(Files.readString(model).contains("jujutsumod:item/" + item));
            assertNotNull(ImageIO.read(texture.toFile()), "unreadable talisman texture " + texture);
        }
    }
}
