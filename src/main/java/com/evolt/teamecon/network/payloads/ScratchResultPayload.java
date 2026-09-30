package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ScratchResultPayload(int containerId,UUID serial,long amount,long balance) implements CustomPacketPayload {
    public static final Type<ScratchResultPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","scratch_result"));
    public static final StreamCodec<FriendlyByteBuf,ScratchResultPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.containerId);b.writeUUID(p.serial);b.writeLong(p.amount);b.writeVarLong(p.balance);},
            b->new ScratchResultPayload(b.readVarInt(),b.readUUID(),b.readLong(),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
