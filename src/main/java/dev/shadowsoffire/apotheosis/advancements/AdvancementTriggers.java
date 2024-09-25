package dev.shadowsoffire.apotheosis.advancements;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

public class AdvancementTriggers {

    public static final GemCutTrigger GEM_CUT = new GemCutTrigger();

    public static void init() {
        CriteriaTriggers.CRITERIA.remove(new ResourceLocation("inventory_changed"));
        CriteriaTriggers.INVENTORY_CHANGED = CriteriaTriggers.register(new ExtendedInvTrigger());
        CriteriaTriggers.register(AdvancementTriggers.GEM_CUT);
    }

}
