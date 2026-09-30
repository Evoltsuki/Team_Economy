package com.evolt.teamecon.network.payloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record TeamBoardPayload(String json) implements CustomPacketPayload {
    public static final Type<TeamBoardPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","team_board"));
    public static final StreamCodec<FriendlyByteBuf,TeamBoardPayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new TeamBoardPayload(b.readUtf(65536)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
