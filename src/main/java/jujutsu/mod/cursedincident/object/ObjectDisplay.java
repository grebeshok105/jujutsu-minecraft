package jujutsu.mod.cursedincident.object;

import java.util.List;

import jujutsu.mod.cursedincident.KnowledgeLevel;
import net.minecraft.network.chat.Component;

/** Player-facing names and lore, intentionally driven only by knowledge data. */
public final class ObjectDisplay {
    private ObjectDisplay() {
    }

    public static Component nameFor(CursedObjectState state) {
        if (state == null || state.knowledge() == null || !state.knowledge().atLeast(KnowledgeLevel.NAME)) {
            return Component.translatable("item.jujutsumod.cursed_object.unknown");
        }
        CursedObjectType type = CursedObjectRegistry.byId(state.typeId());
        if (type == null || type == CursedObjectRegistry.QA_PROBE) {
            return Component.translatable("item.jujutsumod.cursed_object.unknown");
        }
        return Component.translatable("item.jujutsumod.cursed_object.type." + type.id());
    }

    public static String nameKeyFor(CursedObjectState state) {
        return nameFor(state).getString();
    }

    public static List<Component> loreFor(CursedObjectState state) {
        if (state == null) {
            return List.of();
        }
        KnowledgeLevel knowledge = state.knowledge() == null ? KnowledgeLevel.UNKNOWN : state.knowledge();
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
        if (knowledge.atLeast(KnowledgeLevel.ROUGH_DANGER)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.danger"));
        }
        if (knowledge.atLeast(KnowledgeLevel.EFFECT_TYPE)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.effect"));
        }
        if (knowledge.atLeast(KnowledgeLevel.PROPERTIES)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.grade", state.grade()));
        }
        if (knowledge.atLeast(KnowledgeLevel.ORIGIN)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.origin", state.typeId()));
        }
        if (knowledge.atLeast(KnowledgeLevel.SEALING_METHODS)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.sealing", state.sealTier()));
        }
        if (knowledge.atLeast(KnowledgeLevel.RESTRICTIONS)) {
            lore.add(Component.translatable("tooltip.jujutsumod.cursed_object.restrictions"));
        }
        return List.copyOf(lore);
    }

    public static List<Component> loreLines(CursedObjectState state) {
        return loreFor(state);
    }
}
