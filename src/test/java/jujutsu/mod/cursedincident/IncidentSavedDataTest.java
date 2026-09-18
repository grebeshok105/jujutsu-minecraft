package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.UUID;

import jujutsu.mod.cursedincident.persist.IncidentNbt;
import jujutsu.mod.cursedincident.persist.IncidentSavedData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** R54/R59/R60 — registry, pressure and corrupt-entry isolation. */
class IncidentSavedDataTest {
	private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void registryAndPressureRoundTrip() {
		IncidentRecord record = record();
		IncidentSavedData before = new IncidentSavedData(Map.of(record.id, record), 12);
		var encoded = IncidentSavedData.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow();
		IncidentSavedData after = IncidentSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
		assertEquals(12, after.pressure());
		assertEquals(record.id, after.get(record.id).id);
		assertEquals(record.center, after.get(record.id).center);
	}

	@Test
	void mutationsMarkSavedDataDirty() {
		IncidentSavedData data = new IncidentSavedData();
		assertFalse(data.isDirty());
		data.put(record());
		assertTrue(data.isDirty());
		data.setDirty(false);
		data.setPressure(3);
		assertTrue(data.isDirty());
	}

	@Test
	void corruptEntryIsDroppedWithoutDroppingHealthyEntries() {
		IncidentRecord healthy = record();
		JsonObject root = IncidentSavedData.CODEC.encodeStart(JsonOps.INSTANCE,
				new IncidentSavedData(Map.of(healthy.id, healthy), 4)).result().orElseThrow().getAsJsonObject();
		JsonObject incidents = root.getAsJsonObject(IncidentNbt.INCIDENTS);
		incidents.add("22222222-2222-2222-2222-222222222222", new JsonObject());
		IncidentSavedData decoded = IncidentSavedData.CODEC.parse(JsonOps.INSTANCE, root).result().orElseThrow();
		assertEquals(1, decoded.incidents().size());
		assertTrue(decoded.incidents().containsKey(ID));
	}

	private static IncidentRecord record() {
		IncidentRecord record = new IncidentRecord();
		record.id = ID;
		record.center = net.minecraft.core.BlockPos.ZERO;
		record.templateId = "blight";
		return record;
	}
}
