package jujutsu.mod.cursedincident.object;

import java.util.Objects;
import java.util.Set;

/** Data-only profile for one cursed-object family. */
public record CursedObjectType(
        String id,
        String geoModel,
        String texture,
        int gradeMin,
        int gradeMax,
        int maxInstances,
        boolean destructible,
        double dwellRadius,
        Set<String> tags,
        boolean canonical,
        double renderScale) {

    /** Compatibility constructor for profiles that are not canon entries. */
    public CursedObjectType(String id, String geoModel, String texture, int gradeMin, int gradeMax,
            int maxInstances, boolean destructible, double dwellRadius, Set<String> tags) {
        this(id, geoModel, texture, gradeMin, gradeMax, maxInstances, destructible, dwellRadius, tags, false, 1.0);
    }

    /** Compatibility constructor for canon entries without an explicit render scale. */
    public CursedObjectType(String id, String geoModel, String texture, int gradeMin, int gradeMax,
            int maxInstances, boolean destructible, double dwellRadius, Set<String> tags,
            boolean canonical) {
        this(id, geoModel, texture, gradeMin, gradeMax, maxInstances, destructible, dwellRadius, tags,
                canonical, 1.0);
    }

    public CursedObjectType {
        id = Objects.requireNonNull(id, "id");
        geoModel = Objects.requireNonNull(geoModel, "geoModel");
        texture = Objects.requireNonNull(texture, "texture");
        if (id.isBlank() || geoModel.isBlank() || texture.isBlank()) {
            throw new IllegalArgumentException("cursed object ids and resources must not be blank");
        }
        gradeMin = Math.max(1, Math.min(5, gradeMin));
        gradeMax = Math.max(gradeMin, Math.min(5, gradeMax));
        if (maxInstances < -1) {
            throw new IllegalArgumentException("maxInstances must be -1 or non-negative");
        }
        dwellRadius = Math.max(0.0, dwellRadius);
        renderScale = Double.isFinite(renderScale) && renderScale > 0.0 ? renderScale : 1.0;
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
    }

    public boolean acceptsGrade(int grade) {
        return grade >= gradeMin && grade <= gradeMax;
    }

    public boolean unlimited() {
        return maxInstances < 0;
    }

    public boolean indestructible() {
        return !destructible;
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag);
    }

    public String modelResourceId() {
        return geoModel;
    }

    public String textureResourceId(int variant) {
        return texture + "_" + Math.max(0, variant);
    }
}
