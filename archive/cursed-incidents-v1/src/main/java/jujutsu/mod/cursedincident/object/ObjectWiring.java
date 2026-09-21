package jujutsu.mod.cursedincident.object;

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;

/** B2 registration seam; main calls this from JujutsuMod after item/component registration. */
public final class ObjectWiring {
    private static boolean registered;
    private static ObjectDwellTracker tracker;

    private ObjectWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        tracker = new ObjectDwellTracker();
        IncidentControl.bindObjectSpawner(ObjectSpawnerImpl.INSTANCE);
        IncidentControl.bindDwellProvider(tracker);
        ObjectDwellTracker.registerServerHooks();
        registered = true;
    }

    public static ObjectDwellTracker tracker() {
        if (!registered) {
            register();
        }
        return tracker;
    }
}
