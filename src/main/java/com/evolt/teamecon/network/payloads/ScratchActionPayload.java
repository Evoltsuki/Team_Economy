package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ScratchActionPayload(boolean open,int containerId,UUID serial) implements CustomPacketPayload {
    public static final Type<ScratchActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","scratch_action"));
    public static final StreamCodec<FriendlyByteBuf,ScratchActionPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeBoolean(p.open);b.writeVarInt(p.containerId);b.writeUUID(p.serial);},
            b->new ScratchActionPayload(b.readBoolean(),b.readVarInt(),b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
