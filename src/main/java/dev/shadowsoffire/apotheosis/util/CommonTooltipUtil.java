package dev.shadowsoffire.apotheosis.util;

import java.util.function.Consumer;

import com.google.common.base.Predicates;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apothic_attributes.api.IFormattableAttribute;
import dev.shadowsoffire.attributeslib.AttributesLib;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.neoforged.fml.loading.FMLEnvironment;

public class CommonTooltipUtil {

    public static void appendBossData(Level level, LivingEntity entity, Consumer<Component> tooltip) {
        DynamicHolder<LootRarity> rarity = RarityRegistry.byLegacyId(entity.getPersistentData().getString("apoth.rarity"));
        if (!rarity.isBound()) return;
        tooltip.accept(Component.translatable("info.apotheosis.boss", rarity.get().toComponent()).withStyle(ChatFormatting.GRAY));
        if (FMLEnvironment.production) return;
        tooltip.accept(CommonComponents.EMPTY);
        tooltip.accept(Component.translatable("info.apotheosis.boss_modifiers").withStyle(ChatFormatting.GRAY));
        AttributeMap map = entity.getAttributes();
        ForgeRegistries.ATTRIBUTES.getValues().stream().map(map::getInstance).filter(Predicates.notNull()).forEach(inst -> {
            for (AttributeModifier modif : inst.getModifiers()) {
                if (modif.getName().startsWith("placebo_random_modifier_")) {
                    tooltip.accept(IFormattableAttribute.toComponent(inst.getAttribute(), modif, AttributesLib.getTooltipFlag()));
                }
            }
        });
    }

}
