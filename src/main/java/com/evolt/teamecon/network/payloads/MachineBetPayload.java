package com.evolt.teamecon.network.payloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MachineBetPayload(int containerId,int factor) implements CustomPacketPayload {
    public static final Type<MachineBetPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","machine_bet"));
    public static final StreamCodec<FriendlyByteBuf,MachineBetPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.containerId);b.writeVarInt(p.factor);},b->new MachineBetPayload(b.readVarInt(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
