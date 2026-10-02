package com.evolt.teamecon.network.payloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record BoxAdminSyncPayload(int containerId,long sequence,long revision,boolean editable,String headers,String pool,String message) implements CustomPacketPayload {
    public static final Type<BoxAdminSyncPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","box_admin_sync"));
    public static final StreamCodec<FriendlyByteBuf,BoxAdminSyncPayload> STREAM_CODEC=StreamCodec.of(BoxAdminSyncPayload::write,BoxAdminSyncPayload::new);
    public BoxAdminSyncPayload(FriendlyByteBuf b){this(b.readVarInt(),b.readVarLong(),b.readVarLong(),b.readBoolean(),b.readUtf(16000),b.readUtf(24000),b.readUtf(64));}
    public static void write(FriendlyByteBuf b,BoxAdminSyncPayload p){b.writeVarInt(p.containerId);b.writeVarLong(p.sequence);b.writeVarLong(p.revision);b.writeBoolean(p.editable);b.writeUtf(p.headers,16000);b.writeUtf(p.pool,24000);b.writeUtf(p.message,64);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
