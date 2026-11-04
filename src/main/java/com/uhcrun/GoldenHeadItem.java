package com.uhcrun;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cabeza dorada: Regeneración II (10 s) + Absorción I (2 min). */
public class GoldenHeadItem extends Item {
    public GoldenHeadItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
