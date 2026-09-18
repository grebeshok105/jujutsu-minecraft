package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class CursedObjectRegistryTest {
    @AfterEach
    void clearRuntimeIndex() {
        CursedObjectRegistry.clearInstances();
    }

    @Test
    void naturalTableHasEightProfilesAndAtLeastThreeShapeFamilies() {
        assertEquals(8, CursedObjectRegistry.naturalTypes().size());
        assertEquals(9, CursedObjectRegistry.all().size());
        assertTrue(CursedObjectRegistry.naturalTypes().stream().map(CursedObjectType::tags)
                .flatMap(java.util.Set::stream).distinct().count() >= 3);
        assertTrue(CursedObjectRegistry.SUKUNA_FINGER.canonical());
        assertFalse(CursedObjectRegistry.SUKUNA_FINGER.destructible());
        assertEquals(20, CursedObjectRegistry.SUKUNA_FINGER.maxInstances());
        assertNotNull(CursedObjectRegistry.byId("cursed_mask"));
    }

    @Test
    void qaProfileIsNotReturnedByNaturalRolls() {
        for (int seed = 0; seed < 256; seed++) {
            CursedObjectType rolled = CursedObjectRegistry.randomType(RandomSource.create(seed), 3);
            assertTrue(rolled != CursedObjectRegistry.QA_PROBE);
            assertTrue(CursedObjectRegistry.naturalTypes().contains(rolled));
        }
    }

    @Test
    void uniqueLimitRefusesTwentyFirstFingerWithoutChangingEarlierInstances() {
        for (int index = 0; index < 20; index++) {
            UUID id = UUID.nameUUIDFromBytes(("finger-" + index).getBytes(StandardCharsets.UTF_8));
            assertTrue(CursedObjectRegistry.registerInstance(new CursedObjectState(id,
                    "sukuna_finger", 1, index, false, 0, 0, KnowledgeLevel.UNKNOWN, 0L)));
        }
        assertEquals(20, CursedObjectRegistry.instanceCount("sukuna_finger"));
        CursedObjectState refused = new CursedObjectState(UUID.randomUUID(), "sukuna_finger", 1,
                21L, false, 0, 0, KnowledgeLevel.UNKNOWN, 0L);
        assertFalse(CursedObjectRegistry.registerInstance(refused));
        assertEquals(20, CursedObjectRegistry.instanceCount("sukuna_finger"));
        assertTrue(CursedObjectRegistry.canMint("cursed_nail"));
    }

    @Test
    void durableIndexCountsTowardTheCapAfterRegistryRestart() {
        // Simulates a restart: the live index is empty, but twenty minted fingers survive
        // in the durable knownObjects index — minting a twenty-first must be refused.
        jujutsu.mod.cursedincident.persist.IncidentSavedData data =
                new jujutsu.mod.cursedincident.persist.IncidentSavedData();
        for (int index = 0; index < 20; index++) {
            data.rememberObject(UUID.nameUUIDFromBytes(("known-" + index).getBytes(StandardCharsets.UTF_8)),
                    "sukuna_finger");
        }
        CursedObjectRegistry.bind(data);
        try {
            assertFalse(CursedObjectRegistry.canMint(CursedObjectRegistry.SUKUNA_FINGER));
            assertFalse(CursedObjectRegistry.registerInstance(new CursedObjectState(UUID.randomUUID(),
                    "sukuna_finger", 1, 0L, false, 0, 0, KnowledgeLevel.UNKNOWN, 0L)));
            assertTrue(CursedObjectRegistry.canMint("cursed_nail"));
        } finally {
            CursedObjectRegistry.bind(null);
        }
    }

    @Test
    void persistedObjectSourceRecordsCountTowardTheCap() {
        jujutsu.mod.cursedincident.persist.IncidentSavedData data =
                new jujutsu.mod.cursedincident.persist.IncidentSavedData();
        for (int index = 0; index < 20; index++) {
            jujutsu.mod.cursedincident.IncidentRecord record = new jujutsu.mod.cursedincident.IncidentRecord();
            record.id = UUID.nameUUIDFromBytes(("incident-" + index).getBytes(StandardCharsets.UTF_8));
            record.objectInstanceId = UUID.nameUUIDFromBytes(("object-" + index).getBytes(StandardCharsets.UTF_8));
            record.objectTypeId = "sukuna_finger";
            data.put(record);
        }
        CursedObjectRegistry.bind(data);
        try {
            assertFalse(CursedObjectRegistry.canMint(CursedObjectRegistry.SUKUNA_FINGER));
        } finally {
            CursedObjectRegistry.bind(null);
        }
    }

    @Test
    void mintedInstancesEnterTheDurableIndex() {
        jujutsu.mod.cursedincident.persist.IncidentSavedData data =
                new jujutsu.mod.cursedincident.persist.IncidentSavedData();
        CursedObjectRegistry.bind(data);
        try {
            UUID id = UUID.randomUUID();
            assertTrue(CursedObjectRegistry.registerInstance(new CursedObjectState(id,
                    "cursed_nail", 1, 0L, false, 0, 0, KnowledgeLevel.UNKNOWN, 0L)));
            assertEquals("cursed_nail", data.knownObjectType(id));
            CursedObjectRegistry.unregisterInstance(id);
            assertEquals(null, data.knownObjectType(id));
        } finally {
            CursedObjectRegistry.bind(null);
        }
    }
}
