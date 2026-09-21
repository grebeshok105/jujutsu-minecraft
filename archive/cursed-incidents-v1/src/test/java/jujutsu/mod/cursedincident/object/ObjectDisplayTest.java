package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class ObjectDisplayTest {
    @Test
    void unknownKnowledgeNeverLeaksTheConcreteType() {
        CursedObjectState unknown = state(KnowledgeLevel.UNKNOWN);
        Component name = ObjectDisplay.nameFor(unknown);
        assertEquals(Component.translatable("item.jujutsumod.cursed_object.unknown"), name);
        assertFalse(name.getString().toLowerCase().contains("sukuna"));
        assertTrue(ObjectDisplay.loreFor(unknown).isEmpty());
    }

    @Test
    void nameKnowledgeUnlocksTypeNameAndProgressiveLore() {
        CursedObjectState named = state(KnowledgeLevel.NAME);
        assertEquals(Component.translatable("item.jujutsumod.cursed_object.type.sukuna_finger"),
                ObjectDisplay.nameFor(named));
        assertTrue(ObjectDisplay.loreFor(named).size() > ObjectDisplay.loreFor(state(KnowledgeLevel.ROUGH_DANGER)).size());
        assertTrue(ObjectDisplay.loreFor(named).size() >= 4);
    }

    private static CursedObjectState state(KnowledgeLevel knowledge) {
        return new CursedObjectState(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "sukuna_finger", 1, 0L, false, 0, 0, knowledge, 0L);
    }
}
