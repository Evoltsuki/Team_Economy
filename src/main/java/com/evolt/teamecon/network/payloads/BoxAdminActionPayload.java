package com.evolt.teamecon.network.payloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record BoxAdminActionPayload(int containerId,long sequence,long revision,int action,String id,String json) implements CustomPacketPayload {
    public static final Type<BoxAdminActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon","box_admin_action"));
    public static final StreamCodec<FriendlyByteBuf,BoxAdminActionPayload> STREAM_CODEC=StreamCodec.of(BoxAdminActionPayload::write,BoxAdminActionPayload::new);
    public BoxAdminActionPayload(FriendlyByteBuf b){this(b.readVarInt(),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readUtf(32),b.readUtf(24000));}
    public static void write(FriendlyByteBuf b,BoxAdminActionPayload p){b.writeVarInt(p.containerId);b.writeVarLong(p.sequence);b.writeVarLong(p.revision);b.writeVarInt(p.action);b.writeUtf(p.id,32);b.writeUtf(p.json,24000);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
