package dev.shadowsoffire.apotheosis.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.DoubleStream;

import javax.annotation.Nullable;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.ench.asm.EnchHooks;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.network.chat.*;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ItemStack.class, priority = 1200)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    public void apoth_affixItemName(CallbackInfoReturnable<Component> cir) {
        ItemStack ths = (ItemStack) (Object) this;
        CompoundTag afxData = ths.getTagElement(AffixHelper.AFFIX_DATA);
        if (afxData != null && afxData.contains(AffixHelper.NAME, 8)) {
            try {
                Component component = AffixHelper.getName(ths);
                if (component.getContents() instanceof TranslatableContents tContents) {
                    int idx = "misc.apotheosis.affix_name.four".equals(tContents.getKey()) ? 2 : 1;
                    tContents.getArgs()[idx] = cir.getReturnValue();
                    cir.setReturnValue(component);
                }
                else afxData.remove(AffixHelper.NAME);
            }
            catch (Exception exception) {
                afxData.remove(AffixHelper.NAME);
            }
        }

        DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(afxData);
        if (rarity.isBound()) {
            Component recolored = cir.getReturnValue().copy().withStyle(s -> s.withColor(rarity.get().getColor()));
            cir.setReturnValue(recolored);
        }
    }

    @ModifyVariable(at = @At(value = "INVOKE", target = "net/minecraft/world/item/ItemStack.getDamageValue()I"), method = "hurt", argsOnly = true, ordinal = 0)
    public int swapDura(int amount, int amountCopy, RandomSource pRandom, @Nullable ServerPlayer pUser) {
        int blocked = 0;
        DoubleStream socketBonuses = SocketHelper.getGems((ItemStack) (Object) this).getDurabilityBonusPercentage(pUser);
        DoubleStream afxBonuses = AffixHelper.streamAffixes((ItemStack) (Object) this).mapToDouble(inst -> inst.getDurabilityBonusPercentage(pUser));
        DoubleStream bonuses = DoubleStream.concat(socketBonuses, afxBonuses);
        double chance = bonuses.reduce(0, (res, ele) -> res + (1 - res) * ele);

        int delta = 1;
        if (chance < 0) {
            delta = -1;
            chance = -chance;
        }

        if (chance > 0) {
            for (int i = 0; i < amount; i++) {
                if (pRandom.nextFloat() <= chance) blocked += delta;
            }
        }
        return amount - blocked;
    }

    @Redirect(method = "getTooltipLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;appendEnchantmentNames(Ljava/util/List;Lnet/minecraft/nbt/ListTag;)V"))
    public void apoth_enchTooltipRewrite(List<Component> tooltip, ListTag tagEnchants) {
        ItemStack ths = (ItemStack) (Object) this;
        Map<Enchantment, Integer> realLevels = new HashMap<>(ths.getAllEnchantments());
        List<Component> enchTooltips = new ArrayList<>();

        for (int i = tagEnchants.size() - 1; i >= 0; i--) {
            CompoundTag compoundtag = tagEnchants.getCompound(i);
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(EnchantmentHelper.getEnchantmentId(compoundtag));
            if (ench == null || !realLevels.containsKey(ench)) continue;

            int nbtLevel = EnchantmentHelper.getEnchantmentLevel(compoundtag);
            int realLevel = realLevels.remove(ench);

            if (nbtLevel == realLevel) {
                enchTooltips.add(ench.getFullname(EnchantmentHelper.getEnchantmentLevel(compoundtag)));
            } else {
                appendModifiedEnchTooltip(enchTooltips, ench, realLevel, nbtLevel);
            }
        }

        Collections.reverse(enchTooltips);
        tooltip.addAll(enchTooltips);

        for (Map.Entry<Enchantment, Integer> real : realLevels.entrySet()) {
            if (real.getValue() > 0) appendModifiedEnchTooltip(tooltip, real.getKey(), real.getValue(), 0);
        }
    }

    // Integration from the improved damage mod
    @Inject(method = "getBarWidth", at = @At("RETURN"), cancellable = true)
    public void getBarWidth(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        cir.setReturnValue(stack.getOrCreateTag().getBoolean("broken")
                ? 13
                : Math.round(13.0F - (float) stack.getDamageValue() * 13.0F / (float) stack.getMaxDamage()));
    }

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true)
    public void getBarColor(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        if (stack.getOrCreateTag().getBoolean("broken")) cir.setReturnValue(Mth.hsvToRgb(0.001f, 1, 1));
        else {
            float stackMaxDamage = stack.getMaxDamage();
            float f = Math.max(0.0F, (stackMaxDamage - (float) stack.getDamageValue()) / stackMaxDamage);
            cir.setReturnValue(Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F));
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public float getDestroySpeed(BlockState p_41692_) {
        ItemStack stack = ((ItemStack) (Object) this);
        float realSpeed = stack.getItem().getDestroySpeed(stack, p_41692_);

        if (!stack.isDamageableItem()) return realSpeed;

        if (stack.getOrCreateTag().getBoolean("broken")) {
            return 0.5f;
        }

        double start = 0.2f;
        double progress = (double) stack.getDamageValue() / (double) stack.getMaxDamage();
        double flatPenalty = 0;
        double speedMultiplier = 1.0f;

        if (progress <= 0.0f){
            speedMultiplier = 0.0001f;
        }
        else if (progress > 0.5f) {
            flatPenalty = 1.0f;
            speedMultiplier = 0.5f;
        }

        if (progress < start) return stack.getItem().getDestroySpeed(stack, p_41692_);
        double penalty = 1 - 0.3 * progress;

        float finalSpeed = (float) ((realSpeed * penalty - flatPenalty)* speedMultiplier) ;
        float airSpeed = Items.AIR.getDestroySpeed(ItemStack.EMPTY, p_41692_);

        return Math.max(finalSpeed, airSpeed);
    }

    @Inject(method = "hurt", at = @At(value = "RETURN"), cancellable = true)
    public void hurt(int p_41630_, RandomSource p_41631_, ServerPlayer p_41632_, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        if (cir.getReturnValue()) {
            if (!stack.getOrCreateTag().getBoolean("broken")) {
                stack.getOrCreateTag().putBoolean("broken", true);
            } else {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "hurtAndBreak", at = @At(value = "HEAD"), cancellable = true)
    public <T extends LivingEntity> void hurtAndBreak(int i, T livingEntity, Consumer<T> consumer, CallbackInfo ci) {
        ci.cancel();

        ItemStack stack = ((ItemStack) (Object) this);

        if (!livingEntity.level().isClientSide && (!(livingEntity instanceof Player) || !((Player)livingEntity).getAbilities().instabuild)) {
            if (stack.isDamageableItem()) {
                if (stack.hurt(i, livingEntity.getRandom(), livingEntity instanceof ServerPlayer ? (ServerPlayer)livingEntity : null)) {
                    consumer.accept(livingEntity);
                    Item item = stack.getItem();

                    if (livingEntity instanceof Player) {
                        ((Player)livingEntity).awardStat(Stats.ITEM_BROKEN.get(item));
                    }
                }

            }
        }
    }

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    public void getMaxDamage(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) != 0)
            cir.setReturnValue(cir.getReturnValue() * 2);
    }

    // Auxiliary method to handle appending modified enchantments
    private static void appendModifiedEnchTooltip(List<Component> tooltip, Enchantment ench, int realLevel, int nbtLevel) {
        MutableComponent mc = ench.getFullname(realLevel).copy();
        mc.getSiblings().clear();
        Component nbtLevelComp = Component.translatable("enchantment.level." + nbtLevel);
        Component realLevelComp = Component.translatable("enchantment.level." + realLevel);
        if (realLevel != 1 || EnchHooks.getMaxLevel(ench) != 1) mc.append(CommonComponents.SPACE).append(realLevelComp);

        int diff = realLevel - nbtLevel;
        char sign = diff > 0 ? '+' : '-';
        Component diffComp = Component.translatable("(%s " + sign + " %s)", nbtLevelComp, Component.translatable("enchantment.level." + Math.abs(diff))).withStyle(ChatFormatting.DARK_GRAY);
        mc.append(CommonComponents.SPACE).append(diffComp);
        if (realLevel == 0) {
            mc.withStyle(ChatFormatting.DARK_GRAY);
        }
        tooltip.add(mc);
    }
}