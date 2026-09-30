package com.evolt.teamecon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * Client-only sender for machine actions. Kept separate so common code never loads a
 * client class on a dedicated server.
 */
public final class ClientPayloadSender {

    private ClientPayloadSender() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
