package com.evolt.teamecon.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "teamecon", value = Dist.CLIENT)
public final class ClientSessionEvents {
    private ClientSessionEvents() {}
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TeamBoardHud.clear();
        CasinoScreenData.clear(); ClientShopCache.clear(); ClientPriceCache.clear();
        ScratchCardScreen.clear();
        ClientCasinoProgression.clear();
    }
}
