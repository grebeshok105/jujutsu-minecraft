package jujutsu.mod.cursedincident.object;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.RandomSource;

/**
 * Data-driven cursed-object table and the in-memory index used while enforcing
 * per-type instance limits.  The physical stack remains the persisted source of truth.
 */
public final class CursedObjectRegistry {
    public static final CursedObjectType SUKUNA_FINGER = new CursedObjectType(
            "sukuna_finger", "cursed_object_sukuna_finger", "cursed_object_sukuna_finger",
            1, 1, 20, false, 2.0, Set.of("elongated", "canon", "unique"), true);
    public static final CursedObjectType CURSED_NAIL = new CursedObjectType(
            "cursed_nail", "cursed_object_cursed_nail", "cursed_object_cursed_nail",
            1, 5, -1, true, 1.5, Set.of("elongated", "metal"));
    public static final CursedObjectType CURSED_DOLL = new CursedObjectType(
            "cursed_doll", "cursed_object_cursed_doll", "cursed_object_cursed_doll",
            1, 5, -1, true, 2.0, Set.of("humanoid", "wood"));
    public static final CursedObjectType CURSED_EYE = new CursedObjectType(
            "cursed_eye", "cursed_object_cursed_eye", "cursed_object_cursed_eye",
            1, 5, -1, true, 1.25, Set.of("orbital", "organic"));
    public static final CursedObjectType CURSED_COIN = new CursedObjectType(
            "cursed_coin", "cursed_object_cursed_coin", "cursed_object_cursed_coin",
            2, 5, -1, true, 1.0, Set.of("planar", "metal"));
    public static final CursedObjectType CURSED_IDOL = new CursedObjectType(
            "cursed_idol", "cursed_object_cursed_idol", "cursed_object_cursed_idol",
            1, 5, -1, true, 2.5, Set.of("humanoid", "totem", "stone"));
    public static final CursedObjectType CURSED_MASK = new CursedObjectType(
            "cursed_mask", "cursed_object_cursed_mask", "cursed_object_cursed_mask",
            1, 5, -1, true, 1.5, Set.of("planar", "humanoid"));
    public static final CursedObjectType CURSED_CHAIN = new CursedObjectType(
            "cursed_chain", "cursed_object_cursed_chain", "cursed_object_cursed_chain",
            2, 5, -1, true, 2.0, Set.of("elongated", "metal", "link"));

    /** Test-only profile; never returned by {@link #randomType(RandomSource, int)}. */
    public static final CursedObjectType QA_PROBE = new CursedObjectType(
            "qa_probe", "cursed_object_qa_probe", "cursed_object_qa_probe",
            1, 5, -1, true, 1.0, Set.of("qa"));

    private static final List<CursedObjectType> NATURAL_TYPES = List.of(
            SUKUNA_FINGER, CURSED_NAIL, CURSED_DOLL, CURSED_EYE,
            CURSED_COIN, CURSED_IDOL, CURSED_MASK, CURSED_CHAIN);
    private static final List<CursedObjectType> ALL_TYPES = List.of(
            SUKUNA_FINGER, CURSED_NAIL, CURSED_DOLL, CURSED_EYE,
            CURSED_COIN, CURSED_IDOL, CURSED_MASK, CURSED_CHAIN, QA_PROBE);
    private static final Map<UUID, String> LIVE_INSTANCES = new ConcurrentHashMap<>();
    private static volatile jujutsu.mod.cursedincident.persist.IncidentSavedData boundData;

    private CursedObjectRegistry() {
    }

    /**
     * Binds the durable object index so caps survive a registry restart: every minted stack
     * is remembered in {@code IncidentSavedData.knownObjects}, and {@link #canMint} counts
     * live instances plus persisted object-source records plus that index.
     */
    public static void bind(jujutsu.mod.cursedincident.persist.IncidentSavedData data) {
        boundData = data;
    }

    public static List<CursedObjectType> naturalTypes() {
        return NATURAL_TYPES;
    }

    public static List<CursedObjectType> all() {
        return ALL_TYPES;
    }

    public static List<CursedObjectType> types() {
        return NATURAL_TYPES;
    }

    public static CursedObjectType byId(String id) {
        if (id == null) {
            return null;
        }
        for (CursedObjectType type : ALL_TYPES) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static CursedObjectType require(String id) {
        CursedObjectType type = byId(id);
        if (type == null) {
            throw new IllegalArgumentException("unknown cursed object type " + id);
        }
        return type;
    }

    /** Picks only natural profiles and prefers profiles accepting the requested grade. */
    public static CursedObjectType randomType(RandomSource random, int grade) {
        RandomSource source = random == null ? RandomSource.create() : random;
        int accepted = 0;
        for (CursedObjectType type : NATURAL_TYPES) {
            if (type.acceptsGrade(grade)) {
                accepted++;
            }
        }
        if (accepted == 0) {
            accepted = NATURAL_TYPES.size();
            int pick = source.nextInt(accepted);
            return NATURAL_TYPES.get(pick);
        }
        int pick = source.nextInt(accepted);
        for (CursedObjectType type : NATURAL_TYPES) {
            if (type.acceptsGrade(grade) && pick-- == 0) {
                return type;
            }
        }
        return NATURAL_TYPES.getFirst();
    }

    public static int instanceCount(String typeId) {
        int count = 0;
        for (String liveType : LIVE_INSTANCES.values()) {
            if (liveType.equals(typeId)) {
                count++;
            }
        }
        return count;
    }
    public static boolean canMint(String typeId) {
        CursedObjectType type = byId(typeId);
        return type != null && canMint(type);
    }

    public static boolean canMint(CursedObjectType type) {
        return type != null && (type.unlimited() || durableInstanceCount(type) < type.maxInstances());
    }

    /**
     * Counts every physical copy the world can still contain: live registrations plus the
     * durable index (minted stacks not yet re-observed after a restart) plus persisted
     * object-source incident records of the type.
     */
    private static int durableInstanceCount(CursedObjectType type) {
        Set<UUID> ids = new HashSet<>();
        for (Map.Entry<UUID, String> entry : LIVE_INSTANCES.entrySet()) {
            if (type.id().equals(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        jujutsu.mod.cursedincident.persist.IncidentSavedData data = boundData;
        if (data != null) {
            for (Map.Entry<UUID, String> entry : data.knownObjects().entrySet()) {
                if (type.id().equals(entry.getValue()) && !data.isVoided(entry.getKey())) {
                    ids.add(entry.getKey());
                }
            }
            for (jujutsu.mod.cursedincident.IncidentRecord record : data.incidents().values()) {
                if (record != null && !record.scarred && record.objectInstanceId != null
                        && type.id().equals(record.objectTypeId) && !data.isVoided(record.objectInstanceId)) {
                    ids.add(record.objectInstanceId);
                }
            }
        }
        return ids.size();
    }

    /** Atomically reserves one instance id, enforcing the type's durable cap. */
    public static boolean registerInstance(CursedObjectState state) {
        if (state == null) {
            return false;
        }
        CursedObjectType type = byId(state.typeId());
        if (type == null) {
            return false;
        }
        synchronized (LIVE_INSTANCES) {
            if (LIVE_INSTANCES.containsKey(state.instanceId())) {
                return true;
            }
            if (!type.unlimited() && durableInstanceCount(type) >= type.maxInstances()) {
                return false;
            }
            LIVE_INSTANCES.put(state.instanceId(), type.id());
            jujutsu.mod.cursedincident.persist.IncidentSavedData data = boundData;
            if (data != null) {
                data.rememberObject(state.instanceId(), type.id());
            }
            return true;
        }
    }

    public static boolean registerInstance(UUID instanceId, String typeId) {
        CursedObjectType type = byId(typeId);
        return type != null && registerInstance(CursedObjectState.fresh(instanceId, type.id(), type.gradeMin(), 0L));
    }

    public static void unregisterInstance(UUID instanceId) {
        if (instanceId != null) {
            LIVE_INSTANCES.remove(instanceId);
            jujutsu.mod.cursedincident.persist.IncidentSavedData data = boundData;
            if (data != null) {
                data.forgetKnownObject(instanceId);
            }
        }
    }

    public static Set<UUID> instanceIds() {
        return Set.copyOf(LIVE_INSTANCES.keySet());
    }

    public static void clearInstances() {
        LIVE_INSTANCES.clear();
    }

    /** Test/support helper for rebuilding the runtime index from persisted stacks. */
    public static int registerAll(Iterable<CursedObjectState> states) {
        int registered = 0;
        for (CursedObjectState state : states) {
            if (registerInstance(state)) {
                registered++;
            }
        }
        return registered;
    }
}
