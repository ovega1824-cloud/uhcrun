package com.uhcrun;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(UhcRun.MODID)
public class UhcRun {
    public static final String MODID = "uhcrun";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> GOLDEN_HEAD = ITEMS.register("golden_head",
            () -> new GoldenHeadItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(1.2F)
                            .alwaysEdible()
                            .build())));

    /** Bloques cuyo drop se entrega ya cocinado (cutclean). */
    public static final TagKey<Block> CUT_ORES =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "cut_ores"));

    @SuppressWarnings("removal")
    public UhcRun() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreative);

        UhcSettings.load();
        MinecraftForge.EVENT_BUS.register(new UhcEvents());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(GOLDEN_HEAD.get());
        }
    }
}
