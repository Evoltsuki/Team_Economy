package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.scratch.ScratchCardItem;
import com.evolt.teamecon.scratch.ScratchCardMenu;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ScratchNetwork {
    private ScratchNetwork(){}
    public static void handle(ScratchActionPayload payload,IPayloadContext context) {
        context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator())return;
            var mod=TeamEconomyMod.get();if(mod.scratchCards()==null)return;
            if(payload.open()) {
                if(player.containerMenu!=player.inventoryMenu || !payload.serial().equals(ScratchCardItem.serial(player.getMainHandItem())))return;
                mod.scratchCards().open(player);
            } else {
                if(!(player.containerMenu instanceof ScratchCardMenu menu)||menu.containerId!=payload.containerId())return;
                long paid=mod.scratchCards().reveal(player,payload.serial());
                long balance=mod.economy().manager().getBalance(TeamUtil.walletKey(player.getServer(),player.getUUID()));
                PacketDistributor.sendToPlayer(player,new ScratchResultPayload(menu.containerId,payload.serial(),paid,balance));
            }
        });
    }
}
