package com.evolt.teamecon.client;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.init.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registration: the casino screen and anything else that must not load on a
 * dedicated server.
 */
@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void enqueueCompatibility(net.neoforged.fml.event.lifecycle.InterModEnqueueEvent event) {
        if (!net.neoforged.fml.ModList.get().isLoaded("acceleratedrendering")) return;
        // Accelerated Rendering 1.0.14 loses world text and repeated item meshes in
        // our dynamic block-entity renderers with Iris. Its supported IMC filter
        // restores the normal entity/item/text pipelines just for these two types.
        // No hard dependency, global configuration changes or per-frame reflection.
        net.neoforged.fml.InterModComms.sendTo("acceleratedrendering", "block_entity_type_blacklist",
                ModRegistries.CASINO_MACHINE_ENTITY::get);
        net.neoforged.fml.InterModComms.sendTo("acceleratedrendering", "block_entity_type_blacklist",
                ModRegistries.SHOP_MACHINE_ENTITY::get);
        TeamEconomyMod.LOGGER.info("Registered machine rendering compatibility with Accelerated Rendering");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TeamEconomyMod.LOGGER.info("Team Economy client setup");
        event.enqueueWork(()->{
            if(!net.neoforged.fml.ModList.get().isLoaded("ftblibrary"))return;
            try {
                // FTB's public per-screen exclusion API, without making its client classes mandatory.
                Class<?> api=Class.forName("dev.ftb.mods.ftblibrary.api.client.FTBLibraryClientApi");
                Object instance=api.getMethod("get").invoke(null);
                api.getMethod("addSidebarScreenBlacklist",String[].class).invoke(instance,(Object)new String[]{
                        CasinoScreen.class.getName(),ShopScreen.class.getName(),BlindBoxScreen.class.getName(),ScratchCardScreen.class.getName(),MachineBetScreen.class.getName()});
            } catch(ReflectiveOperationException ex){TeamEconomyMod.LOGGER.warn("Could not exclude casino screens from FTB sidebar",ex);}
        });
    }

    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.CASINO_MACHINE_ENTITY.value(),
                CasinoMachineRenderer::new);
        event.registerBlockEntityRenderer(ModRegistries.SHOP_MACHINE_ENTITY.value(),
                context -> new ShopMachineRenderer());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.CASINO_MENU.value(), CasinoScreen::new);
        event.register(ModRegistries.MACHINE_BET_MENU.value(), MachineBetScreen::new);
        event.<com.evolt.teamecon.shop.ShopMenu, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.evolt.teamecon.shop.ShopMenu>>register(ModRegistries.SHOP_MENU.value(), (menu, inventory, title) -> menu.boxesOnly()
                ? new BlindBoxScreen(menu, inventory, title) : new ShopScreen(menu, inventory, title));
        event.register(ModRegistries.SCRATCH_MENU.value(), ScratchCardScreen::new);
    }
}
