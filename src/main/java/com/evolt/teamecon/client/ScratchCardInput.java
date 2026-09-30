package com.evolt.teamecon.client;

import com.evolt.teamecon.network.payloads.ScratchActionPayload;
import com.evolt.teamecon.scratch.ScratchCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid="teamecon",value=Dist.CLIENT)
public final class ScratchCardInput {
    private ScratchCardInput(){}
    @SubscribeEvent public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||mc.screen!=null||mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof ScratchCardItem))return;
        event.setCanceled(true);event.setSwingHand(false);
        var serial=ScratchCardItem.serial(mc.player.getMainHandItem());
        if(serial==null){mc.player.displayClientMessage(Component.translatable("scratch.teamecon.blank"),true);return;}
        ClientPayloadSender.sendToServer(new ScratchActionPayload(true,0,serial));
    }
}
