package com.evolt.teamecon.qa;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Foreign-namespace item for actual purchase/recycling integration, never included in a release. */
@EventBusSubscriber(modid="teamecon", bus=EventBusSubscriber.Bus.MOD)
public final class PricingFixture {
    @SubscribeEvent public static void register(RegisterEvent event) {
        if (Boolean.getBoolean("teamecon.visualQa")) event.register(Registries.ITEM, helper ->
                helper.register(ResourceLocation.parse("teamecon_qa:pricing_sample"), new Item(new Item.Properties())));
    }
}
